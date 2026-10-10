package es.aviferdev.datopublico.backend.ingesta.categorizacion

import es.aviferdev.datopublico.backend.ingesta.publicacion.BoeXmlParser
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
    fun `extrae el plazo de dias habiles de la convocatoria real de II_B`() {
        val plazo = assertNotNull(
            extractor.extract(texto("texto-IIB-2024-93.xml"), PUBLISHED_2024_01_02),
            "la convocatoria de II.B debe exponer plazo",
        )

        assertEquals("2024-01-23", plazo.fechaLimite)
        assertTrue(assertNotNull(plazo.descripcion).contains("quince días hábiles"), "descripción del plazo")
    }

    @Test
    fun `extrae el plazo de dias habiles de la convocatoria real de III`() {
        val plazo = assertNotNull(
            extractor.extract(texto("texto-III-2024-117.xml"), PUBLISHED_2024_01_02),
            "la convocatoria de III debe exponer plazo",
        )

        assertEquals("2024-01-23", plazo.fechaLimite)
    }

    @Test
    fun `no extrae plazo de la disposicion real de I`() {
        assertNull(extractor.extract(texto("texto-I-2026-20979.xml"), "2026-10-09"))
    }

    @Test
    fun `no extrae plazo de la resolucion real de II_A`() {
        assertNull(extractor.extract(texto("texto-IIA-2024-87.xml"), PUBLISHED_2024_01_02))
    }

    @Test
    fun `no extrae plazo de la informacion publica real de V_B`() {
        assertNull(extractor.extract(texto("texto-VB-2024-76.xml"), PUBLISHED_2024_01_02))
    }

    @Test
    fun `sin texto devuelve un plazo vacio`() {
        assertNull(extractor.extract(null, PUBLISHED_2024_01_02))
        assertNull(extractor.extract("   ", PUBLISHED_2024_01_02))
    }

    @Test
    fun `calcula el plazo en dias naturales`() {
        val plazo = assertNotNull(
            extractor.extract(
                "El plazo de presentación de solicitudes será de diez días naturales a contar " +
                    "desde el día siguiente al de la publicación de la convocatoria.",
                PUBLISHED_2024_01_02,
            ),
        )

        assertEquals("2024-01-12", plazo.fechaLimite)
    }

    @Test
    fun `extrae una fecha explicita con contexto de solicitud`() {
        val plazo = assertNotNull(
            extractor.extract(
                "Las solicitudes se presentarán hasta el 15 de marzo de 2024.",
                PUBLISHED_2024_01_02,
            ),
        )

        assertEquals("2024-03-15", plazo.fechaLimite)
    }

    @Test
    fun `descarta el plazo cuando el contexto lo excluye`() {
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
    fun `es determinista para la misma entrada`() {
        val texto = texto("texto-IIB-2024-93.xml")

        val primera = extractor.extract(texto, PUBLISHED_2024_01_02)
        val segunda = extractor.extract(texto, PUBLISHED_2024_01_02)

        assertEquals(primera, segunda)
    }

    /** Texto normalizado del fixture real, parseado con el parser puro del XML. */
    private fun texto(fixture: String): String =
        assertNotNull(BoeXmlParser().parse(recurso(fixture)).texto, "$fixture sin texto")

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
