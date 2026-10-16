package es.aviferdev.datopublico.backend.ingestion.categorization

import es.aviferdev.datopublico.backend.ingestion.publication.BoeXmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Extractor acotado del plazo de solicitud (FT00012), sin red ni base de datos.
 *
 * Los casos positivos (II.B y III) y negativos (I, II.A y V.B) usan el **texto
 * real de los fixtures XML ya versionados**: no se inventan datos. Escenarios 2,
 * 3 y 5 del spec.
 */
class DeadlineExtractorTest {

    private val extractor = DeadlineExtractor()

    @Test
    fun `extrae el deadline de dias habiles de la convocatoria real de II_B`() {
        val deadline = assertNotNull(
            extractor.extract(body("texto-IIB-2024-93.xml"), PUBLISHED_2024_01_02),
            "la convocatoria de II.B debe exponer plazo",
        )

        assertEquals("2024-01-23", deadline.deadlineDate)
        assertTrue(assertNotNull(deadline.description).contains("quince días hábiles"), "descripción del plazo")
    }

    @Test
    fun `extrae el deadline de dias habiles de la convocatoria real de III`() {
        val deadline = assertNotNull(
            extractor.extract(body("texto-III-2024-117.xml"), PUBLISHED_2024_01_02),
            "la convocatoria de III debe exponer plazo",
        )

        assertEquals("2024-01-23", deadline.deadlineDate)
    }

    @Test
    fun `no extrae deadline de la disposicion real de I`() {
        assertNull(extractor.extract(body("texto-I-2026-20979.xml"), "2026-10-09"))
    }

    @Test
    fun `no extrae deadline de la resolucion real de II_A`() {
        assertNull(extractor.extract(body("texto-IIA-2024-87.xml"), PUBLISHED_2024_01_02))
    }

    @Test
    fun `no extrae deadline de la informacion publica real de V_B`() {
        assertNull(extractor.extract(body("texto-VB-2024-76.xml"), PUBLISHED_2024_01_02))
    }

    @Test
    fun `sin body devuelve un deadline vacio`() {
        assertNull(extractor.extract(null, PUBLISHED_2024_01_02))
        assertNull(extractor.extract("   ", PUBLISHED_2024_01_02))
    }

    @Test
    fun `calcula el deadline en dias naturales`() {
        val deadline = assertNotNull(
            extractor.extract(
                "El plazo de presentación de solicitudes será de diez días naturales a contar " +
                    "desde el día siguiente al de la publicación de la convocatoria.",
                PUBLISHED_2024_01_02,
            ),
        )

        assertEquals("2024-01-12", deadline.deadlineDate)
    }

    @Test
    fun `extrae una date explicita con contexto de solicitud`() {
        val deadline = assertNotNull(
            extractor.extract(
                "Las solicitudes se presentarán hasta el 15 de marzo de 2024.",
                PUBLISHED_2024_01_02,
            ),
        )

        assertEquals("2024-03-15", deadline.deadlineDate)
    }

    @Test
    fun `descarta el deadline cuando el contexto lo excluye`() {
        assertNull(
            extractor.extract(
                "Para presentar solicitud de recurso, el plazo de quince días hábiles a contar " +
                    "desde el día siguiente al de la publicación.",
                PUBLISHED_2024_01_02,
            ),
        )
        assertNull(
            extractor.extract(
                "El plazo de ejecución será de quince días hábiles a contar desde el día siguiente " +
                    "al de la publicación.",
                PUBLISHED_2024_01_02,
            ),
        )
    }

    @Test
    fun `es determinista para la misma entry`() {
        val body = body("texto-IIB-2024-93.xml")

        val primera = extractor.extract(body, PUBLISHED_2024_01_02)
        val segunda = extractor.extract(body, PUBLISHED_2024_01_02)

        assertEquals(primera, segunda)
    }

    /** Texto normalizado del fixture real, parseado con el parser puro del XML. */
    private fun body(fixture: String): String =
        assertNotNull(BoeXmlParser().parse(recurso(fixture)).body, "$fixture sin texto")

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        /** Fecha de publicación de la muestra real del 2024-01-02. */
        const val PUBLISHED_2024_01_02 = "2024-01-02"
    }
}
