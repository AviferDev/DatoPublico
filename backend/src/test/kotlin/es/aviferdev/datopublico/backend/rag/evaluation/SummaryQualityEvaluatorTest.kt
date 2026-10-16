package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryResult
import es.aviferdev.datopublico.backend.rag.summary.SummaryGenerationDto
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.SummaryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red ni BD) de [SummaryQualityEvaluator] con un
 * [SummaryGenerator] doble determinista (escenarios 4 y 5 del spec): genera una vez
 * por entrada con el límite por defecto, calcula el F1 de cada campo y el *recall*
 * de cifras, deriva el acierto, agrega las medias sin `NaN` y **no lanza** ante un
 * resumen inválido (lo cuenta como fallo).
 */
class SummaryQualityEvaluatorTest {

    @Test
    fun `evaluates every entry once with the default limit and aggregates`() = runTest {
        val generator = ScriptedSummaryGenerator(
            mapOf(
                ENTRY_A to valid("Se convocan 2.000 ayudas para practicas formativas", "Estudiantes de master"),
                ENTRY_B to valid("otra cosa distinta", "respuesta equivocada"),
            )
        )
        val dataset = dataset(
            entry(ENTRY_A, reference("Se convocan 2.000 ayudas para practicas formativas", "Estudiantes de master", listOf("2.000 ayudas"))),
            entry(ENTRY_B, reference("becas ayudas empresas", "docentes investigadores")),
        )

        val result = SummaryQualityEvaluator(generator).evaluate(dataset)

        assertEquals(2, result.entryCount)
        assertEquals(0.5, result.meanWhatChangesF1, TOLERANCE)
        assertEquals(0.5, result.meanWhoIsAffectedF1, TOLERANCE)
        assertEquals(1.0, result.meanFiguresRecall, TOLERANCE)
        assertEquals(0.5, result.hitRate, TOLERANCE)
        assertEquals(0, result.invalidCount)
        assertEquals(listOf(ENTRY_A, ENTRY_B), result.perEntry.map { row -> row.entryId })
        assertTrue(result.perEntry.first().hit)
        assertFalse(result.perEntry.last().hit)
        assertEquals(
            listOf(
                GeneratorCall(ENTRY_A, SummaryQualityEvaluator.DEFAULT_LIMIT),
                GeneratorCall(ENTRY_B, SummaryQualityEvaluator.DEFAULT_LIMIT),
            ),
            generator.calls,
        )
    }

    @Test
    fun `computes the figures recall per entry against its reference`() = runTest {
        val generator = ScriptedSummaryGenerator(
            mapOf(ENTRY_A to valid("Se convocan 2.000 ayudas", "Estudiantes de master", listOf("6 meses")))
        )
        val dataset = dataset(entry(ENTRY_A, reference("Se convocan 2.000 ayudas", "Estudiantes de master", listOf("2.000", "6"))))

        val result = SummaryQualityEvaluator(generator).evaluate(dataset)

        assertEquals(1.0, result.perEntry.single().figuresRecall, TOLERANCE)
    }

    @Test
    fun `an invalid summary counts as a failure without throwing`() = runTest {
        val generator = ScriptedSummaryGenerator(mapOf(ENTRY_A to CitizenSummaryResult.Invalid(emptyList())))
        val dataset = dataset(entry(ENTRY_A, reference("Se convocan ayudas", "Estudiantes de master")))

        val result = SummaryQualityEvaluator(generator).evaluate(dataset)

        val row = result.perEntry.single()
        assertEquals(1, result.entryCount)
        assertEquals(1, result.invalidCount)
        assertEquals(0.0, result.meanWhatChangesF1, TOLERANCE)
        assertEquals(0.0, result.meanWhoIsAffectedF1, TOLERANCE)
        assertEquals(0.0, result.meanFiguresRecall, TOLERANCE)
        assertEquals(0.0, result.hitRate, TOLERANCE)
        assertTrue(row.invalid)
        assertFalse(row.hit)
    }

    @Test
    fun `a hit requires both fields to reach the threshold`() = runTest {
        val generator = ScriptedSummaryGenerator(
            mapOf(ENTRY_A to valid("Se convocan ayudas", "algo distinto"))
        )
        val dataset = dataset(entry(ENTRY_A, reference("Se convocan ayudas", "estudiantes de master")))

        val result = SummaryQualityEvaluator(generator).evaluate(dataset)

        assertEquals(1.0, result.meanWhatChangesF1, TOLERANCE)
        assertEquals(0.0, result.meanWhoIsAffectedF1, TOLERANCE)
        assertEquals(0.0, result.hitRate, TOLERANCE)
    }

    @Test
    fun `the configured threshold decides the hit`() = runTest {
        val generator = ScriptedSummaryGenerator(mapOf(ENTRY_A to valid("becas becas ayudas", "becas becas ayudas")))
        val dataset = dataset(entry(ENTRY_A, reference("becas ayudas", "becas ayudas")))

        val strict = SummaryQualityEvaluator(generator, hitThreshold = 0.9).evaluate(dataset)
        val lenient = SummaryQualityEvaluator(generator, hitThreshold = 0.4).evaluate(dataset)

        assertEquals(0.8, strict.meanWhatChangesF1, TOLERANCE)
        assertEquals(0.0, strict.hitRate, TOLERANCE)
        assertEquals(1.0, lenient.hitRate, TOLERANCE)
    }

    @Test
    fun `an empty dataset yields zero metrics without NaN`() = runTest {
        val result = SummaryQualityEvaluator(ScriptedSummaryGenerator(emptyMap())).evaluate(dataset())

        assertEquals(0, result.entryCount)
        assertEquals(0, result.invalidCount)
        assertEquals(0.0, result.meanWhatChangesF1, TOLERANCE)
        assertEquals(0.0, result.meanWhoIsAffectedF1, TOLERANCE)
        assertEquals(0.0, result.meanFiguresRecall, TOLERANCE)
        assertEquals(0.0, result.hitRate, TOLERANCE)
        assertTrue(result.perEntry.isEmpty())
    }

    @Test
    fun `a threshold outside the unit interval fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryQualityEvaluator(ScriptedSummaryGenerator(emptyMap()), hitThreshold = 1.5)
        }
    }

    /** Dataset interno de prueba con las entradas indicadas. */
    private fun dataset(vararg entries: SummaryGoldenSetEntry): SummaryGoldenSet =
        SummaryGoldenSet(version = "test", entries = entries.toList())

    /** Entrada interna de prueba con una publicación mínima. */
    private fun entry(id: String, reference: SummaryGenerationDto): SummaryGoldenSetEntry =
        SummaryGoldenSetEntry(id = id, publication = publication(id), reference = reference)

    /** Publicación mínima con categoría y texto (el evaluador solo usa el id). */
    private fun publication(id: String): Publicacion = Publicacion(
        id = id,
        title = "Título",
        publicationDate = "2026-10-09",
        issuingBody = "Organismo",
        section = BoeSectionDto.III,
        heading = null,
        texto = "Artículo 1\nTexto.",
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
        category = CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
    )

    /** Resumen de referencia de prueba. */
    private fun reference(
        whatChanges: String,
        whoIsAffected: String,
        keyFigures: List<String> = emptyList(),
    ): SummaryGenerationDto = SummaryGenerationDto(
        whatChanges = whatChanges,
        whoIsAffected = whoIsAffected,
        keyFigures = keyFigures,
    )

    /** Resumen válido de prueba con la fuente inyectada. */
    private fun valid(
        whatChanges: String,
        whoIsAffected: String,
        keyFigures: List<String> = emptyList(),
    ): CitizenSummaryResult.Valid = CitizenSummaryResult.Valid(
        SummaryDto(
            whatChanges = whatChanges,
            whoIsAffected = whoIsAffected,
            keyFigures = keyFigures,
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=$ENTRY_A",
            aiNotice = true,
        )
    )

    private companion object {
        /** Identificador de la primera entrada de prueba. */
        const val ENTRY_A = "EVAL-S1"

        /** Identificador de la segunda entrada de prueba. */
        const val ENTRY_B = "EVAL-S2"

        /** Tolerancia de comparación de `Double`. */
        const val TOLERANCE = 1e-9
    }
}

/** Llamada registrada a [SummaryGenerator.generate]. */
private data class GeneratorCall(val publicationId: String, val limit: Int)

/**
 * Doble determinista de [SummaryGenerator]: responde por `id` de publicación y
 * registra las llamadas. Una publicación sin guion devuelve un resumen inválido.
 */
private class ScriptedSummaryGenerator(
    private val results: Map<String, CitizenSummaryResult>,
) : SummaryGenerator {
    /** Llamadas recibidas, en orden. */
    val calls = mutableListOf<GeneratorCall>()

    override suspend fun generate(publication: Publicacion, limit: Int): CitizenSummaryResult {
        calls.add(GeneratorCall(publication.id, limit))
        return results[publication.id] ?: CitizenSummaryResult.Invalid(emptyList())
    }
}
