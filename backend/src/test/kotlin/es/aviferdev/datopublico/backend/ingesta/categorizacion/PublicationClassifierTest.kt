package es.aviferdev.datopublico.backend.ingesta.categorizacion

import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioResponseDto
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.ingesta.sumario.toEntradasSumario
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Clasificador curado de publicaciones (FT00012), sin red ni base de datos.
 *
 * La muestra real de las ocho secciones se toma de los **fixtures del sumario ya
 * versionados** (los mismos que ejercita `BoePublicacionParserTest`): no se
 * inventan datos. Escenarios 1, 3 y 4 del spec.
 */
class PublicationClassifierTest {

    private val classifier = PublicationClassifier()

    @Test
    fun `clasifica la muestra real de las ocho secciones por su category esperada`() {
        val entradas = entradasDelSumario()

        EXPECTED_CATEGORIES.forEach { (identificador, category) ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            val actual = classifier.classify(entrada.section, entrada.heading, entrada.title)
            assertEquals(category, actual, "$identificador (sección ${entrada.section})")
        }
    }

    @Test
    fun `conserva el heading real del sumario sin mutarlo`() {
        val entradas = entradasDelSumario()

        EXPECTED_HEADINGS.forEach { (identificador, heading) ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            assertEquals(heading, entrada.heading, "$identificador: epígrafe conservado")
        }
    }

    @Test
    fun `las entradas reales sin heading caen en la category por defecto de su section`() {
        val entradas = entradasDelSumario()

        listOf("BOE-B-2024-1", "BOE-B-2024-92").forEach { identificador ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            assertNull(entrada.heading, "$identificador: sin epígrafe")
            assertEquals(
                CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
                classifier.classify(entrada.section, entrada.heading, entrada.title),
                "$identificador",
            )
        }
    }

    @Test
    fun `los casos ambiguos sin palabras clave usan la category por defecto de la section`() {
        BoeSectionDto.entries.forEach { section ->
            val actual = classifier.classify(section, null, "Anuncio sin palabras clave reconocibles")
            assertEquals(DEFAULTS_POR_SECCION[section], actual, "sección $section")
        }
    }

    @Test
    fun `ante varias reglas aplicables gana la primera del orden documentado`() {
        // «Ayudas» (BECAS) precede en el orden a «premios» (AWARDS).
        val actual = classifier.classify(BoeSectionDto.I, "Ayudas y premios", "Título de la publicación")

        assertEquals(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID, actual)
    }

    @Test
    fun `es determinista para la misma entrada`() {
        val entrada = assertNotNull(entradasDelSumario()["BOE-A-2024-93"], "sin entrada de la convocatoria")

        val primera = classifier.classify(entrada.section, entrada.heading, entrada.title)
        val segunda = classifier.classify(entrada.section, entrada.heading, entrada.title)

        assertEquals(primera, segunda)
    }

    private fun entradasDelSumario(): Map<String, EntradaSumario> =
        listOf(
            "sumario-20261009.json" to LocalDate.of(2026, 10, 9),
            "sumario-20240102.json" to LocalDate.of(2024, 1, 2),
        )
            .flatMap { (nombre, date) ->
                DatoPublicoJson.decodeFromString<BoeSumarioResponseDto>(recurso(nombre)).toEntradasSumario(date)
            }
            .associateBy { it.identificador }

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        /** Identificador real → categoría curada esperada (escenario 1 del spec). */
        val EXPECTED_CATEGORIES: Map<String, CategoryDto> = mapOf(
            "BOE-A-2026-20979" to CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
            "BOE-A-2024-87" to CategoryDto.APPOINTMENTS_AND_DISMISSALS,
            "BOE-A-2024-93" to CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
            "BOE-A-2024-117" to CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
            "BOE-B-2024-1" to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            "BOE-B-2024-25" to CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            "BOE-B-2024-76" to CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            "BOE-B-2024-92" to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
        )

        /** Identificador real → epígrafe del sumario (puede ser `null`). */
        val EXPECTED_HEADINGS: Map<String, String?> = mapOf(
            "BOE-A-2026-20979" to "Subvenciones",
            "BOE-A-2024-87" to "Destinos",
            "BOE-A-2024-93" to "Personal laboral",
            "BOE-A-2024-117" to "Ayudas",
            "BOE-B-2024-1" to null,
            "BOE-B-2024-25" to null,
            "BOE-B-2024-76" to null,
            "BOE-B-2024-92" to null,
        )

        /** Categoría por defecto por sección (misma tabla que el clasificador). */
        val DEFAULTS_POR_SECCION: Map<BoeSectionDto, CategoryDto> = mapOf(
            BoeSectionDto.I to CategoryDto.NORMS_AND_LEGISLATION,
            BoeSectionDto.II_A to CategoryDto.APPOINTMENTS_AND_DISMISSALS,
            BoeSectionDto.II_B to CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
            BoeSectionDto.III to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            BoeSectionDto.IV to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            BoeSectionDto.V_A to CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            BoeSectionDto.V_B to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            BoeSectionDto.V_C to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
        )
    }
}
