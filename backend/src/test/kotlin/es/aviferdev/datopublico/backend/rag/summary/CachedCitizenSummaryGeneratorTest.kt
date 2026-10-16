package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.persistence.GenerationUsageEntity
import es.aviferdev.datopublico.backend.persistence.GenerationUsageRepository
import es.aviferdev.datopublico.backend.persistence.SummaryCacheEntity
import es.aviferdev.datopublico.backend.persistence.SummaryCacheRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.GenerationRequest
import es.aviferdev.datopublico.backend.rag.generation.GenerationResponse
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.TokenUsage
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SummaryDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.validation.CitizenSummaryViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red ni BD) de [CachedCitizenSummaryGenerator] con **dobles
 * deterministas** (escenarios 1–4 del spec): acierto que no llama al proveedor,
 * fallo que cachea y registra tokens, inválido que no cachea y cambio de
 * modelo/versión de prompt que invalida la caché.
 */
class CachedCitizenSummaryGeneratorTest {

    private val url = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

    @Test
    fun `a cache hit returns the cached summary without calling the provider`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_JSON)
        val cache = FakeSummaryCacheRepository().apply {
            put(SummaryCacheEntity(KEY, PUBLICATION_ID, MODEL, PROMPT_VERSION, cachedSummary()))
        }
        val usage = FakeGenerationUsageRepository()
        val generator = cachedGenerator(provider, cache, usage)

        val result = generator.generate(publication())

        assertTrue(result.cacheHit)
        val valid = assertIs<CitizenSummaryResult.Valid>(result.result)
        assertEquals("Resumen cacheado", valid.summary.whatChanges)
        assertEquals(0, provider.calls)
        val record = assertIs<GenerationUsageEntity>(usage.records.singleOrNull())
        assertTrue(record.cacheHit)
        assertEquals(0, record.totalTokens)
    }

    @Test
    fun `a cache miss generates stores the summary and records the real tokens`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_JSON, TokenUsage(11, 22, 33))
        val cache = FakeSummaryCacheRepository()
        val usage = FakeGenerationUsageRepository()
        val generator = cachedGenerator(provider, cache, usage)

        val result = generator.generate(publication())

        assertFalse(result.cacheHit)
        assertIs<CitizenSummaryResult.Valid>(result.result)
        assertEquals(1, provider.calls)
        assertEquals("Resumen generado", assertNotNull(cache.find(KEY)).summary.whatChanges)
        val record = assertIs<GenerationUsageEntity>(usage.records.singleOrNull())
        assertFalse(record.cacheHit)
        assertEquals(11, record.promptTokens)
        assertEquals(22, record.completionTokens)
        assertEquals(33, record.totalTokens)
    }

    @Test
    fun `an invalid summary is not cached but its usage is recorded`() = runTest {
        val provider = FakeTextGenerationProvider(JSON_WITHOUT_DEADLINE, TokenUsage(5, 6, 11))
        val cache = FakeSummaryCacheRepository()
        val usage = FakeGenerationUsageRepository()
        val generator = cachedGenerator(provider, cache, usage)

        val result = generator.generate(publication(CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS))

        assertFalse(result.cacheHit)
        val invalid = assertIs<CitizenSummaryResult.Invalid>(result.result)
        assertTrue(invalid.violations.contains(CitizenSummaryViolation.MISSING_DEADLINE))
        assertNull(cache.find(KEY))
        assertEquals(11, assertIs<GenerationUsageEntity>(usage.records.singleOrNull()).totalTokens)
    }

    @Test
    fun `a different model treats the cache as a miss`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_JSON)
        val cache = FakeSummaryCacheRepository().apply {
            put(SummaryCacheEntity(KEY, PUBLICATION_ID, MODEL, PROMPT_VERSION, cachedSummary()))
        }
        val usage = FakeGenerationUsageRepository()
        val generator = CachedCitizenSummaryGenerator(
            generator = citizenGenerator(provider, publication()),
            cache = cache,
            usage = usage,
            model = "otro-modelo",
        )

        val result = generator.generate(publication())

        assertFalse(result.cacheHit)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `a different prompt version treats the cache as a miss`() = runTest {
        val provider = FakeTextGenerationProvider(VALID_JSON)
        val cache = FakeSummaryCacheRepository().apply {
            put(SummaryCacheEntity(KEY, PUBLICATION_ID, MODEL, PROMPT_VERSION, cachedSummary()))
        }
        val usage = FakeGenerationUsageRepository()
        val generator = CachedCitizenSummaryGenerator(
            generator = citizenGenerator(provider, publication()),
            cache = cache,
            usage = usage,
            model = MODEL,
            promptVersion = "v2",
        )

        val result = generator.generate(publication())

        assertFalse(result.cacheHit)
        assertEquals(1, provider.calls)
    }

    private fun cachedGenerator(
        provider: TextGenerationProvider,
        cache: SummaryCacheRepository,
        usage: GenerationUsageRepository,
    ): CachedCitizenSummaryGenerator = CachedCitizenSummaryGenerator(
        generator = citizenGenerator(provider, publication()),
        cache = cache,
        usage = usage,
        model = MODEL,
    )

    private fun citizenGenerator(
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

    private fun cachedSummary(): SummaryDto = SummaryDto(
        whatChanges = "Resumen cacheado",
        whoIsAffected = "A la ciudadanía",
        officialSource = url,
    )

    private fun publication(category: CategoriaDto = CategoriaDto.NORMAS_Y_LEGISLACION): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        title = "Real Decreto de prueba",
        publicationDate = "2026-10-10",
        issuingBody = "MINISTERIO DE PRUEBA",
        section = SeccionBoeDto.I,
        heading = "Disposiciones generales",
        texto = "texto",
        officialUrl = url,
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        category = category,
    )

    /** Doble de [TextGenerationProvider]: devuelve el texto y el uso indicados. */
    private class FakeTextGenerationProvider(
        private val responseText: String,
        private val usage: TokenUsage? = null,
    ) : TextGenerationProvider {
        var calls: Int = 0

        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            calls += 1
            return GenerationResponse(responseText, usage)
        }
    }

    /** Doble en memoria de [SummaryCacheRepository]. */
    private class FakeSummaryCacheRepository : SummaryCacheRepository {
        private val entries = mutableMapOf<String, SummaryCacheEntity>()

        override fun find(cacheKey: String): SummaryCacheEntity? = entries[cacheKey]

        override fun put(entity: SummaryCacheEntity): SummaryCacheEntity {
            entries[entity.cacheKey] = entity
            return entity
        }
    }

    /** Doble en memoria de [GenerationUsageRepository]. */
    private class FakeGenerationUsageRepository : GenerationUsageRepository {
        val records = mutableListOf<GenerationUsageEntity>()

        override fun record(usage: GenerationUsageEntity): GenerationUsageEntity {
            records.add(usage)
            return usage.copy(id = records.size.toLong())
        }

        override fun listByPublication(publicationId: String): List<GenerationUsageEntity> =
            records.filter { record -> record.publicationId == publicationId }

        override fun totalTokens(): Long = records.sumOf { record -> record.totalTokens.toLong() }
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
        const val PUBLICATION_ID = "BOE-A-2026-1"
        const val MODEL = "deepseek-v4.1-flash"
        const val PROMPT_VERSION = "v1"
        const val KEY = "$PUBLICATION_ID|$MODEL|$PROMPT_VERSION"

        const val VALID_JSON =
            """{"queCambia":"Resumen generado","aQuienAfecta":"A la ciudadanía","cifrasClave":["30 días"]}"""

        const val JSON_WITHOUT_DEADLINE =
            """{"queCambia":"Cambia el plazo","aQuienAfecta":"A la ciudadanía","cifrasClave":["30 días"]}"""
    }
}
