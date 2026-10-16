package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Combinador puro sumario + XML → [Publicacion] (sin red).
 *
 * Verifica el mapeo de campos, la tolerancia a `documento = null` y el fallback
 * de organismo/PDF desde el XML. Comprueba además que el parser **clasifica** la
 * publicación y le asigna el **plazo** (FT00012) con sus colaboradores por defecto.
 */
class PublicacionParserTest {

    private val parser = PublicacionParser()

    @Test
    fun `mapea una entrada del sumario y usa la category por defecto de la section`() {
        val publicacion = parser.parsear(entrada(), documento())

        assertEquals("BOE-A-1", publicacion.id)
        assertEquals("Título de prueba", publicacion.title)
        assertEquals("2026-10-09", publicacion.publicationDate)
        assertEquals(BoeSectionDto.I, publicacion.section)
        assertEquals("Epígrafe", publicacion.heading)
        assertEquals("Texto oficial completo.", publicacion.texto)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-1", publicacion.officialUrl)
        assertEquals(URL_XML, publicacion.urlXml)
        assertEquals("Real Decreto", publicacion.rango)
        assertEquals(CategoryDto.NORMS_AND_LEGISLATION, publicacion.category)
        assertNull(publicacion.deadline, "El texto de prueba no es una convocatoria con plazo")
    }

    @Test
    fun `clasifica por el heading y extrae el deadline del texto de la convocatoria`() {
        val publicacion = parser.parsear(
            entrada(heading = "Subvenciones", publicationDate = "2024-01-02"),
            documento(texto = TEXTO_CONVOCATORIA_CON_PLAZO),
        )

        assertEquals(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID, publicacion.category)
        assertEquals("2024-01-23", publicacion.deadline?.deadlineDate)
    }

    @Test
    fun `sin documento produce una publicacion valida con texto y rango nulos`() {
        val publicacion = parser.parsear(entrada(), null)

        assertNull(publicacion.texto)
        assertNull(publicacion.rango)
        assertNull(publicacion.deadline)
        assertEquals("BOE-A-1", publicacion.id)
        assertTrue(publicacion.officialUrl.isNotBlank())
        assertEquals("MINISTERIO DEL SUMARIO", publicacion.issuingBody)
    }

    @Test
    fun `prioriza el issuingBody y el pdf del sumario frente a los del xml`() {
        val publicacion = parser.parsear(entrada(), documento())

        assertEquals("MINISTERIO DEL SUMARIO", publicacion.issuingBody)
        assertEquals(URL_PDF_SUMARIO, publicacion.urlPdf)
    }

    @Test
    fun `completa issuingBody y pdf desde el xml si el sumario no los trae`() {
        val publicacion = parser.parsear(
            entrada(issuingBody = null, urlPdf = null),
            documento(),
        )

        assertEquals("MINISTERIO DEL XML", publicacion.issuingBody)
        assertEquals(URL_PDF_XML, publicacion.urlPdf)
    }

    private fun entrada(
        issuingBody: String? = "MINISTERIO DEL SUMARIO",
        urlPdf: String? = URL_PDF_SUMARIO,
        heading: String? = "Epígrafe",
        publicationDate: String = "2026-10-09",
    ): EntradaSumario = EntradaSumario(
        identificador = "BOE-A-1",
        control = "2026/1",
        title = "Título de prueba",
        publicationDate = publicationDate,
        section = BoeSectionDto.I,
        issuingBody = issuingBody,
        heading = heading,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
        urlXml = URL_XML,
        urlPdf = urlPdf,
    )

    private fun documento(texto: String = "Texto oficial completo."): DocumentoBoe = DocumentoBoe(
        texto = texto,
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

        /** Frase real del fixture `texto-IIB-2024-93.xml` (convocatoria con plazo hábil). */
        const val TEXTO_CONVOCATORIA_CON_PLAZO =
            "La documentación se deberá presentar en el plazo de quince días hábiles a contar " +
                "desde el día siguiente al de la publicación del anuncio de esta convocatoria en el BOE."
    }
}
