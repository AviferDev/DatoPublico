package es.aviferdev.datopublico.backend.ingestion.publication

import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Combinador puro sumario + XML → [Publication] (sin red).
 *
 * Verifica el mapeo de campos, la tolerancia a `documento = null` y el fallback
 * de organismo/PDF desde el XML. Comprueba además que el parser **clasifica** la
 * publicación y le asigna el **plazo** (FT00012) con sus colaboradores por defecto.
 */
class PublicationParserTest {

    private val parser = PublicationParser()

    @Test
    fun `mapea una entry del summary y usa la category por defecto de la section`() {
        val publication = parser.parse(entry(), document())

        assertEquals("BOE-A-1", publication.id)
        assertEquals("Título de prueba", publication.title)
        assertEquals("2026-10-09", publication.publicationDate)
        assertEquals(BoeSectionDto.I, publication.section)
        assertEquals("Epígrafe", publication.heading)
        assertEquals("Texto oficial completo.", publication.body)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-1", publication.officialUrl)
        assertEquals(URL_XML, publication.xmlUrl)
        assertEquals("Real Decreto", publication.rank)
        assertEquals(CategoryDto.NORMS_AND_LEGISLATION, publication.category)
        assertNull(publication.deadline, "El texto de prueba no es una convocatoria con plazo")
    }

    @Test
    fun `clasifica por el heading y extrae el deadline del body de la convocatoria`() {
        val publication = parser.parse(
            entry(heading = "Subvenciones", publicationDate = "2024-01-02"),
            document(body = TEXTO_CONVOCATORIA_CON_PLAZO),
        )

        assertEquals(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID, publication.category)
        assertEquals("2024-01-23", publication.deadline?.deadlineDate)
    }

    @Test
    fun `sin document produce una publication valida con body y rank nulos`() {
        val publication = parser.parse(entry(), null)

        assertNull(publication.body)
        assertNull(publication.rank)
        assertNull(publication.deadline)
        assertEquals("BOE-A-1", publication.id)
        assertTrue(publication.officialUrl.isNotBlank())
        assertEquals("MINISTERIO DEL SUMARIO", publication.issuingBody)
    }

    @Test
    fun `prioriza el issuingBody y el pdf del summary frente a los del xml`() {
        val publication = parser.parse(entry(), document())

        assertEquals("MINISTERIO DEL SUMARIO", publication.issuingBody)
        assertEquals(URL_PDF_SUMARIO, publication.pdfUrl)
    }

    @Test
    fun `completa issuingBody y pdf desde el xml si el summary no los trae`() {
        val publication = parser.parse(
            entry(issuingBody = null, pdfUrl = null),
            document(),
        )

        assertEquals("MINISTERIO DEL XML", publication.issuingBody)
        assertEquals(URL_PDF_XML, publication.pdfUrl)
    }

    private fun entry(
        issuingBody: String? = "MINISTERIO DEL SUMARIO",
        pdfUrl: String? = URL_PDF_SUMARIO,
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
        xmlUrl = URL_XML,
        pdfUrl = pdfUrl,
    )

    private fun document(body: String = "Texto oficial completo."): BoeDocument = BoeDocument(
        body = body,
        metadata = BoeMetadata(
            rank = "Real Decreto",
            department = "MINISTERIO DEL XML",
            dispositionDate = "20261007",
            officialNumber = "814/2026",
            pdfUrl = URL_PDF_XML,
            legislativeOrigin = "Estatal",
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
