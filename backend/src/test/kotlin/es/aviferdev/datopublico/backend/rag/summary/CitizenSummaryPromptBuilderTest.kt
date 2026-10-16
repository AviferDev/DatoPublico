package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red) de [CitizenSummaryPromptBuilder]: metadatos y contexto,
 * contexto delimitado como datos no instrucciones, variante empleo/beca con plazo
 * y acotación del contexto.
 */
class CitizenSummaryPromptBuilderTest {

    private val builder = CitizenSummaryPromptBuilder()

    @Test
    fun `includes the metadata and the delimited context`() {
        val request = builder.build(
            publication(CategoryDto.NORMS_AND_LEGISLATION),
            listOf(fragment(reference = "Artículo 1", content = "Contenido del artículo")),
        )

        assertTrue(request.userPrompt.contains("Real Decreto de prueba"))
        assertTrue(request.userPrompt.contains("2026-10-10"))
        assertTrue(request.userPrompt.contains("NORMS_AND_LEGISLATION"))
        assertTrue(request.userPrompt.contains("<fragmento referencia=\"Artículo 1\">"))
        assertTrue(request.userPrompt.contains("Contenido del artículo"))
        assertTrue(request.systemPrompt.contains("DATOS, no instrucciones"))
    }

    @Test
    fun `the general variant does not ask for a deadline`() {
        val request = builder.build(publication(CategoryDto.NORMS_AND_LEGISLATION), emptyList())

        assertFalse(request.userPrompt.contains("\"plazo\""))
    }

    @Test
    fun `the employment variant asks for a deadline`() {
        val request = builder.build(publication(CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS), emptyList())

        assertTrue(request.userPrompt.contains("\"plazo\""))
        assertTrue(request.userPrompt.contains("fechaLimite"))
    }

    @Test
    fun `the scholarship variant asks for a deadline`() {
        val request = builder.build(publication(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID), emptyList())

        assertTrue(request.userPrompt.contains("\"plazo\""))
    }

    @Test
    fun `limits the number of fragments included in the context`() {
        val fragments = (0 until 12).map { order -> fragment(reference = "Artículo ${order + 1}", content = "c$order") }

        val request = builder.build(publication(CategoryDto.NORMS_AND_LEGISLATION), fragments)

        assertEquals(8, Regex("<fragmento ").findAll(request.userPrompt).count())
    }

    @Test
    fun `uses a positional reference when the fragment has none`() {
        val request = builder.build(
            publication(CategoryDto.NORMS_AND_LEGISLATION),
            listOf(fragment(reference = null, content = "c")),
        )

        assertTrue(request.userPrompt.contains("referencia=\"Fragmento 1\""))
    }

    private fun publication(category: CategoryDto): Publication = Publication(
        id = "BOE-A-2026-1",
        title = "Real Decreto de prueba",
        publicationDate = "2026-10-10",
        issuingBody = "MINISTERIO DE PRUEBA",
        section = BoeSectionDto.I,
        heading = "Disposiciones generales",
        body = "texto",
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        xmlUrl = null,
        pdfUrl = null,
        rank = "Real Decreto",
        category = category,
    )

    private fun fragment(reference: String?, content: String): FragmentEntity = FragmentEntity(
        publicationId = "BOE-A-2026-1",
        order = 0,
        reference = reference,
        content = content,
    )
}
