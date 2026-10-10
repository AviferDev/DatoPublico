package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
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
            publication(CategoriaDto.NORMAS_Y_LEGISLACION),
            listOf(fragment(reference = "Artículo 1", content = "Contenido del artículo")),
        )

        assertTrue(request.userPrompt.contains("Real Decreto de prueba"))
        assertTrue(request.userPrompt.contains("2026-10-10"))
        assertTrue(request.userPrompt.contains("NORMAS_Y_LEGISLACION"))
        assertTrue(request.userPrompt.contains("<fragmento referencia=\"Artículo 1\">"))
        assertTrue(request.userPrompt.contains("Contenido del artículo"))
        assertTrue(request.systemPrompt.contains("DATOS, no instrucciones"))
    }

    @Test
    fun `the general variant does not ask for a deadline`() {
        val request = builder.build(publication(CategoriaDto.NORMAS_Y_LEGISLACION), emptyList())

        assertFalse(request.userPrompt.contains("\"plazo\""))
    }

    @Test
    fun `the employment variant asks for a deadline`() {
        val request = builder.build(publication(CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO), emptyList())

        assertTrue(request.userPrompt.contains("\"plazo\""))
        assertTrue(request.userPrompt.contains("fechaLimite"))
    }

    @Test
    fun `the scholarship variant asks for a deadline`() {
        val request = builder.build(publication(CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS), emptyList())

        assertTrue(request.userPrompt.contains("\"plazo\""))
    }

    @Test
    fun `limits the number of fragments included in the context`() {
        val fragments = (0 until 12).map { order -> fragment(reference = "Artículo ${order + 1}", content = "c$order") }

        val request = builder.build(publication(CategoriaDto.NORMAS_Y_LEGISLACION), fragments)

        assertEquals(8, Regex("<fragmento ").findAll(request.userPrompt).count())
    }

    @Test
    fun `uses a positional reference when the fragment has none`() {
        val request = builder.build(
            publication(CategoriaDto.NORMAS_Y_LEGISLACION),
            listOf(fragment(reference = null, content = "c")),
        )

        assertTrue(request.userPrompt.contains("referencia=\"Fragmento 1\""))
    }

    private fun publication(categoria: CategoriaDto): Publicacion = Publicacion(
        id = "BOE-A-2026-1",
        titulo = "Real Decreto de prueba",
        fechaPublicacion = "2026-10-10",
        organismo = "MINISTERIO DE PRUEBA",
        seccion = SeccionBoeDto.I,
        epigrafe = "Disposiciones generales",
        texto = "texto",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        categoria = categoria,
    )

    private fun fragment(reference: String?, content: String): FragmentEntity = FragmentEntity(
        publicationId = "BOE-A-2026-1",
        order = 0,
        reference = reference,
        content = content,
    )
}
