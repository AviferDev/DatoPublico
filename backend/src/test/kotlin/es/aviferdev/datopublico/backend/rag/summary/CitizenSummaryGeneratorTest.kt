package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.GenerationRequest
import es.aviferdev.datopublico.backend.rag.generation.GenerationResponse
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.TokenUsage
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
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
        val generator = generatorCon(provider, publication(CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS))

        val result = generator.generate(publication(CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS))

        val valid = assertIs<CitizenSummaryResult.Valid>(result)
        assertEquals(url, valid.summary.officialSource)
        assertTrue(valid.summary.aiNotice)
        assertNotNull(valid.summary.deadline)
        assertEquals("2026-11-01", valid.summary.deadline?.deadlineDate)
        assertEquals("Cambia el plazo", valid.summary.whatChanges)
    }

    @Test
    fun `a scholarship summary without deadline is invalid`() = runTest {
        val provider = FakeTextGenerationProvider(JSON_WITHOUT_DEADLINE)
        val generator = generatorCon(provider, publication(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID))

        val result = generator.generate(publication(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID))

        val invalid = assertIs<CitizenSummaryResult.Invalid>(result)
        assertTrue(invalid.violations.contains(CitizenSummaryViolation.MISSING_DEADLINE))
    }

    @Test
    fun `a blank official source is invalid`() = runTest {
        val provider = FakeTextGenerationProvider(JSON_WITHOUT_DEADLINE)
        val publication = publication(CategoryDto.NORMS_AND_LEGISLATION).copy(officialUrl = " ")
        val generator = generatorCon(provider, publication)

        val result = generator.generate(publication)

        val invalid = assertIs<CitizenSummaryResult.Invalid>(result)
        assertTrue(invalid.violations.contains(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE))
    }

    @Test
    fun `the provider receives the built prompt with the context`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_EMPLOYMENT_JSON)
        val publication = publication(CategoryDto.NORMS_AND_LEGISLATION)
        val generator = generatorCon(provider, publication)

        generator.generate(publication)

        val request = assertNotNull(provider.lastRequest)
        assertEquals(1, provider.calls)
        assertTrue(request.userPrompt.contains("Real Decreto de prueba"))
        assertTrue(request.userPrompt.contains("<fragmento referencia=\"Artículo 1\">"))
    }

    @Test
    fun `propagates the provider token usage into a valid result`() = runTest {
        val usage = TokenUsage(promptTokens = 10, completionTokens = 20, totalTokens = 30)
        val provider = FakeTextGenerationProvider(VALID_EMPLOYMENT_JSON, usage)
        val generator = generatorCon(provider, publication(CategoryDto.NORMS_AND_LEGISLATION))

        val result = generator.generate(publication(CategoryDto.NORMS_AND_LEGISLATION))

        val valid = assertIs<CitizenSummaryResult.Valid>(result)
        assertEquals(usage, valid.usage)
    }

    @Test
    fun `propagates the provider token usage into an invalid result`() = runTest {
        val usage = TokenUsage(promptTokens = 7, completionTokens = 8, totalTokens = 15)
        val provider = FakeTextGenerationProvider(JSON_WITHOUT_DEADLINE, usage)
        val publication = publication(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID)
        val generator = generatorCon(provider, publication)

        val result = generator.generate(publication)

        val invalid = assertIs<CitizenSummaryResult.Invalid>(result)
        assertEquals(usage, invalid.usage)
    }

    @Test
    fun `a publication without category fails fast before invoking the provider`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_EMPLOYMENT_JSON)
        val publication = publication(CategoryDto.NORMS_AND_LEGISLATION).copy(category = null)
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

    private fun publication(category: CategoryDto?): Publicacion = Publicacion(
        id = "BOE-A-2026-1",
        title = "Real Decreto de prueba",
        publicationDate = "2026-10-10",
        issuingBody = "MINISTERIO DE PRUEBA",
        section = BoeSectionDto.I,
        heading = "Disposiciones generales",
        texto = "texto",
        officialUrl = url,
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        category = category,
    )

    /** Doble de [TextGenerationProvider]: devuelve [responseText]/[usage] y registra la petición. */
    private class FakeTextGenerationProvider(
        private val responseText: String,
        private val usage: TokenUsage? = null,
    ) : TextGenerationProvider {
        var lastRequest: GenerationRequest? = null
        var calls: Int = 0

        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            lastRequest = request
            calls += 1
            return GenerationResponse(responseText, usage)
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
