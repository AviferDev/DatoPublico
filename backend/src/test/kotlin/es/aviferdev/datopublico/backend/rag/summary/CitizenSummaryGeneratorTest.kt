package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.GenerationRequest
import es.aviferdev.datopublico.backend.rag.generation.GenerationResponse
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.validation.CitizenSummaryViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red ni BD) de [CitizenSummaryGenerator] con **dobles
 * deterministas** (escenarios 1, 3, 4 y 5 del spec): fuente inyectada, resumen
 * inválido tipado, fail-fast sin categoría y proveedor sustituible.
 */
class CitizenSummaryGeneratorTest {

    private val url = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

    @Test
    fun `generates a valid employment summary with the source injected`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_EMPLOYMENT_JSON)
        val generator = generatorCon(provider, publication(CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO))

        val result = generator.generate(publication(CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO))

        val valid = assertIs<CitizenSummaryResult.Valid>(result)
        assertEquals(url, valid.summary.fuenteOficial)
        assertTrue(valid.summary.avisoIA)
        assertNotNull(valid.summary.plazo)
        assertEquals("2026-11-01", valid.summary.plazo?.fechaLimite)
        assertEquals("Cambia el plazo", valid.summary.queCambia)
    }

    @Test
    fun `a scholarship summary without deadline is invalid`() = runTest {
        val provider = FakeTextGenerationProvider(JSON_WITHOUT_DEADLINE)
        val generator = generatorCon(provider, publication(CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS))

        val result = generator.generate(publication(CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS))

        val invalid = assertIs<CitizenSummaryResult.Invalid>(result)
        assertTrue(invalid.violations.contains(CitizenSummaryViolation.MISSING_DEADLINE))
    }

    @Test
    fun `a blank official source is invalid`() = runTest {
        val provider = FakeTextGenerationProvider(JSON_WITHOUT_DEADLINE)
        val publication = publication(CategoriaDto.NORMAS_Y_LEGISLACION).copy(urlOficial = " ")
        val generator = generatorCon(provider, publication)

        val result = generator.generate(publication)

        val invalid = assertIs<CitizenSummaryResult.Invalid>(result)
        assertTrue(invalid.violations.contains(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE))
    }

    @Test
    fun `the provider receives the built prompt with the context`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_EMPLOYMENT_JSON)
        val publication = publication(CategoriaDto.NORMAS_Y_LEGISLACION)
        val generator = generatorCon(provider, publication)

        generator.generate(publication)

        val request = assertNotNull(provider.lastRequest)
        assertEquals(1, provider.calls)
        assertTrue(request.userPrompt.contains("Real Decreto de prueba"))
        assertTrue(request.userPrompt.contains("<fragmento referencia=\"Artículo 1\">"))
    }

    @Test
    fun `a publication without category fails fast before invoking the provider`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_EMPLOYMENT_JSON)
        val publication = publication(CategoriaDto.NORMAS_Y_LEGISLACION).copy(categoria = null)
        val generator = generatorCon(provider, publication)

        assertFailsWith<IllegalArgumentException> { generator.generate(publication) }

        assertEquals(0, provider.calls)
    }

    private fun generatorCon(
        provider: TextGenerationProvider,
        publication: Publicacion,
    ): CitizenSummaryGenerator {
        val matches = listOf(
            FragmentMatch(
                fragment = FragmentEntity(
                    publicationId = publication.id,
                    order = 0,
                    reference = "Artículo 1",
                    content = "Contenido del artículo",
                ),
                distance = 0.0,
            )
        )
        return CitizenSummaryGenerator(
            hybridSearch = HybridSearch(FakeEmbeddingProvider, FakeFragmentRepository(matches)),
            textGenerationProvider = provider,
        )
    }

    private fun publication(categoria: CategoriaDto?): Publicacion = Publicacion(
        id = "BOE-A-2026-1",
        titulo = "Real Decreto de prueba",
        fechaPublicacion = "2026-10-10",
        organismo = "MINISTERIO DE PRUEBA",
        seccion = SeccionBoeDto.I,
        epigrafe = "Disposiciones generales",
        texto = "texto",
        urlOficial = url,
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        categoria = categoria,
    )

    /** Doble de [TextGenerationProvider]: devuelve [responseText] y registra la petición. */
    private class FakeTextGenerationProvider(private val responseText: String) : TextGenerationProvider {
        var lastRequest: GenerationRequest? = null
        var calls: Int = 0

        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            lastRequest = request
            calls += 1
            return GenerationResponse(responseText)
        }
    }

    /** Doble de [EmbeddingProvider]: vector constante; el generador no lo inspecciona. */
    private object FakeEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = 3

        override fun embedQuery(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun embedPassage(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun close() = Unit
    }

    /** Doble de [FragmentRepository]: devuelve siempre [matches]. */
    private class FakeFragmentRepository(private val matches: List<FragmentMatch>) : FragmentRepository {
        override fun save(fragment: FragmentEntity): FragmentEntity = fragment

        override fun listByPublication(publicationId: String): List<FragmentEntity> = emptyList()

        override fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean = true

        override fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch> =
            findNearest(queryEmbedding, SearchFilter(), limit)

        override fun findNearest(
            queryEmbedding: FloatArray,
            filter: SearchFilter,
            limit: Int,
        ): List<FragmentMatch> = matches

        override fun findEmbedding(publicationId: String, order: Int): FloatArray? = null
    }

    private companion object {
        const val VALID_EMPLOYMENT_JSON =
            """{"queCambia":"Cambia el plazo","aQuienAfecta":"A la ciudadanía","cifrasClave":["30 días"],""" +
                """"plazo":{"fechaLimite":"2026-11-01"}}"""

        const val JSON_WITHOUT_DEADLINE =
            """{"queCambia":"Cambia el plazo","aQuienAfecta":"A la ciudadanía","cifrasClave":["30 días"]}"""
    }
}
