package es.aviferdev.datopublico.backend.rag.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [SummaryGoldenSetLoader] (escenarios 2 y 3 del
 * spec): el recurso versionado real parsea y es coherente, y un dataset inválido
 * falla con `IllegalArgumentException` **sin** abrir conexión ni invocar al
 * generador.
 */
class SummaryGoldenSetTest {

    @Test
    fun `the versioned golden set parses and is coherent`() {
        val dataset = SummaryGoldenSetLoader.load()
        val ids = dataset.entries.map { entry -> entry.id }

        assertTrue(dataset.version.isNotBlank())
        assertTrue(dataset.entries.isNotEmpty())
        assertEquals(ids.toSet().size, ids.size, "los ids deben ser únicos")
        assertTrue(ids.all { id -> id.startsWith(ID_PREFIX) }, "los ids deben usar el prefijo $ID_PREFIX")
        dataset.entries.forEach { entry ->
            assertNotNull(entry.publication.categoria, "la entrada ${entry.id} necesita categoría")
            assertTrue(entry.publication.titulo.isNotBlank(), "la entrada ${entry.id} necesita título")
            assertTrue(entry.publication.texto.orEmpty().isNotBlank(), "la entrada ${entry.id} necesita texto")
            assertTrue(entry.reference.queCambia.isNotBlank(), "la referencia de ${entry.id} necesita «Qué cambia»")
            assertTrue(entry.reference.aQuienAfecta.isNotBlank(), "la referencia de ${entry.id} necesita «A quién afecta»")
        }
    }

    @Test
    fun `the golden set maps every entry to a publication`() {
        val dataset = SummaryGoldenSetLoader.load()
        val entry = dataset.entries.first()

        assertEquals(entry.id, entry.publication.id)
        assertTrue(entry.publication.urlOficial.contains(entry.id))
    }

    @Test
    fun `an empty entry list fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ""))
        }
    }

    @Test
    fun `a blank version fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(version = "  "))
        }
    }

    @Test
    fun `duplicate identifiers fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = "$ENTRY_ITEM, $ENTRY_ITEM"))
        }
    }

    @Test
    fun `an entry without title or text fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ENTRY_ITEM.replace("\"title\": \"T\"", "\"title\": \" \"")))
        }
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ENTRY_ITEM.replace("\"text\": \"Artículo 1\\nTexto.\"", "\"text\": \" \"")))
        }
    }

    @Test
    fun `an entry without category fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ENTRY_ITEM.replace("\"category\": \"becas_subvenciones_y_ayudas\",", "")))
        }
    }

    @Test
    fun `a reference with an empty field fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ENTRY_ITEM.replace("\"queCambia\": \"Q\"", "\"queCambia\": \" \"")))
        }
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ENTRY_ITEM.replace("\"aQuienAfecta\": \"A\"", "\"aQuienAfecta\": \" \"")))
        }
    }

    @Test
    fun `a reference without a required field fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.parse(datasetJson(entries = ENTRY_ITEM.replace("\"aQuienAfecta\": \"A\",", "")))
        }
    }

    @Test
    fun `a missing dataset file fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryGoldenSetLoader.load("/rag/eval/no-existe.json")
        }
    }

    /** Compone un JSON de dataset con las piezas indicadas (para los casos inválidos). */
    private fun datasetJson(
        version: String = "1.0.0",
        entries: String = ENTRY_ITEM,
    ): String = """{ "version": "$version", "entries": [$entries] }"""

    private companion object {
        /** Prefijo reservado de los ids del golden set de resúmenes. */
        const val ID_PREFIX = "EVAL-S"

        /** Entrada mínima válida del dataset de prueba. */
        val ENTRY_ITEM = """
            { "id": "EVAL-S1", "title": "T", "date": "2026-10-09", "section": "III",
              "category": "becas_subvenciones_y_ayudas", "organization": "Org",
              "text": "Artículo 1\nTexto.",
              "reference": { "queCambia": "Q", "aQuienAfecta": "A", "cifrasClave": ["2 ayudas"] } }
        """.trimIndent()
    }
}
