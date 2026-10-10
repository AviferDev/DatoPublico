package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Combinador puro sumario + XML → [Publicacion] (sin red).
 *
 * Verifica el mapeo de campos, la tolerancia a `documento = null` y el fallback
 * de organismo/PDF desde el XML. Comprueba que `categoria` queda `null` (FT00012).
 */
class PublicacionParserTest {

    private val parser = PublicacionParser()

    @Test
    fun `mapea una entrada del sumario y su documento sin asignar categoria`() {
        val publicacion = parser.parsear(entrada(), documento())

        assertEquals("BOE-A-1", publicacion.id)
        assertEquals("Título de prueba", publicacion.titulo)
        assertEquals("2026-10-09", publicacion.fechaPublicacion)
        assertEquals(SeccionBoeDto.I, publicacion.seccion)
        assertEquals("Epígrafe", publicacion.epigrafe)
        assertEquals("Texto oficial completo.", publicacion.texto)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-1", publicacion.urlOficial)
        assertEquals(URL_XML, publicacion.urlXml)
        assertEquals("Real Decreto", publicacion.rango)
        assertNull(publicacion.categoria, "La categoría es de FT00012 y debe quedar sin asignar")
    }

    @Test
    fun `sin documento produce una publicacion valida con texto y rango nulos`() {
        val publicacion = parser.parsear(entrada(), null)

        assertNull(publicacion.texto)
        assertNull(publicacion.rango)
        assertEquals("BOE-A-1", publicacion.id)
        assertTrue(publicacion.urlOficial.isNotBlank())
        assertEquals("MINISTERIO DEL SUMARIO", publicacion.organismo)
    }

    @Test
    fun `prioriza el organismo y el pdf del sumario frente a los del xml`() {
        val publicacion = parser.parsear(entrada(), documento())

        assertEquals("MINISTERIO DEL SUMARIO", publicacion.organismo)
        assertEquals(URL_PDF_SUMARIO, publicacion.urlPdf)
    }

    @Test
    fun `completa organismo y pdf desde el xml si el sumario no los trae`() {
        val publicacion = parser.parsear(
            entrada(organismo = null, urlPdf = null),
            documento(),
        )

        assertEquals("MINISTERIO DEL XML", publicacion.organismo)
        assertEquals(URL_PDF_XML, publicacion.urlPdf)
    }

    private fun entrada(
        organismo: String? = "MINISTERIO DEL SUMARIO",
        urlPdf: String? = URL_PDF_SUMARIO,
    ): EntradaSumario = EntradaSumario(
        identificador = "BOE-A-1",
        control = "2026/1",
        titulo = "Título de prueba",
        fechaPublicacion = "2026-10-09",
        seccion = SeccionBoeDto.I,
        organismo = organismo,
        epigrafe = "Epígrafe",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
        urlXml = URL_XML,
        urlPdf = urlPdf,
    )

    private fun documento(): DocumentoBoe = DocumentoBoe(
        texto = "Texto oficial completo.",
        metadatos = MetadatosBoe(
            rango = "Real Decreto",
            departamento = "MINISTERIO DEL XML",
            fechaDisposicion = "20261007",
            numeroOficial = "814/2026",
            urlPdf = URL_PDF_XML,
            origenLegislativo = "Estatal",
        ),
    )

    private companion object {
        const val URL_XML = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-1"
        const val URL_PDF_SUMARIO = "https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-1.pdf"
        const val URL_PDF_XML = "https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-1.pdf.xml"
    }
}
