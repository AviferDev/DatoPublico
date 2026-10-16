package es.aviferdev.datopublico.backend.ingesta.publication

import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry
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
    fun `mapea una entry del summary y usa la category por defecto de la section`() {
        val publication = parser.parsear(entry(), documento())

        assertEquals("BOE-A-1", publication.id)
        assertEquals("Título de prueba", publication.title)
        assertEquals("2026-10-09", publication.publicationDate)
        assertEquals(BoeSectionDto.I, publication.section)
        assertEquals("Epígrafe", publication.heading)
        assertEquals("Texto oficial completo.", publication.texto)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-1", publication.officialUrl)
        assertEquals(URL_XML, publication.urlXml)
        assertEquals("Real Decreto", publication.rango)
        assertEquals(CategoryDto.NORMS_AND_LEGISLATION, publication.category)
        assertNull(publication.deadline, "El texto de prueba no es una convocatoria con plazo")
    }

    @Test
    fun `clasifica por el heading y extrae el deadline del texto de la convocatoria`() {
        val publication = parser.parsear(
            entry(heading = "Subvenciones", publicationDate = "2024-01-02"),
            documento(texto = TEXTO_CONVOCATORIA_CON_PLAZO),
        )

        assertEquals(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID, publication.category)
        assertEquals("2024-01-23", publication.deadline?.deadlineDate)
    }

    @Test
    fun `sin documento produce una publication valida con texto y rango nulos`() {
        val publication = parser.parsear(entry(), null)

        assertNull(publication.texto)
        assertNull(publication.rango)
        assertNull(publication.deadline)
        assertEquals("BOE-A-1", publication.id)
        assertTrue(publication.officialUrl.isNotBlank())
        assertEquals("MINISTERIO DEL SUMARIO", publication.issuingBody)
    }

    @Test
    fun `prioriza el issuingBody y el pdf del summary frente a los del xml`() {
        val publication = parser.parsear(entry(), documento())

        assertEquals("MINISTERIO DEL SUMARIO", publication.issuingBody)
        assertEquals(URL_PDF_SUMARIO, publication.urlPdf)
    }

    @Test
    fun `completa issuingBody y pdf desde el xml si el summary no los trae`() {
        val publication = parser.parsear(
            entry(issuingBody = null, urlPdf = null),
            documento(),
        )

        assertEquals("MINISTERIO DEL XML", publication.issuingBody)
        assertEquals(URL_PDF_XML, publication.urlPdf)
    }

    private fun entry(
        issuingBody: String? = "MINISTERIO DEL SUMARIO",
        urlPdf: String? = URL_PDF_SUMARIO,
        heading: String? = "Epígrafe",
        publicationDate: String = "2026-10-09",
    ): SummaryEntry = SummaryEntry(
        identifier = "BOE-A-1",
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
        metadata = MetadatosBoe(
            rango = "Real Decreto",
            department = "MINISTERIO DEL XML",
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
