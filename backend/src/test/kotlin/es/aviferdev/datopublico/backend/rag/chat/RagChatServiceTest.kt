package es.aviferdev.datopublico.backend.rag.chat

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red ni BD) de [RagChatService] con **dobles deterministas**
 * (escenarios 1–7 del spec): respondida con cita, refutada con cita, sin
 * fundamento, contexto vacío sin invocar al proveedor, prompt construido,
 * degradación por grounding y cita no resoluble/índice fuera de rango/estado
 * desconocido.
 */
class RagChatServiceTest {

    @Test
    fun `answers a question with a citation to the official source`() = runTest {
        val usage = TokenUsage(promptTokens = 10, completionTokens = 20, totalTokens = 30)
        val provider = FakeTextGenerationProvider(ANSWERED_JSON, usage)

        val result = serviceCon(provider).answer("¿Cuál es el plazo de solicitud?")

        val answered = assertIs<ChatResult.Answered>(result)
        assertEquals(ANSWER_SUPPORTED, answered.answer)
        assertEquals(1, answered.citations.size)
        assertEquals(URL, answered.citations.first().officialUrl)
        assertEquals(PUBLICATION_ID, answered.citations.first().publicationId)
        assertEquals(usage, answered.usage)
    }

    @Test
    fun `refutes a false claim with citations`() = runTest {
        val provider = FakeTextGenerationProvider(REFUTED_JSON)

        val result = serviceCon(provider).answer("Se reduce el impuesto especial")

        val refuted = assertIs<ChatResult.Refuted>(result)
        assertTrue(refuted.citations.isNotEmpty())
        assertEquals(URL, refuted.citations.first().officialUrl)
    }

    @Test
    fun `declares unfounded when the model has no basis`() = runTest {
        val provider = FakeTextGenerationProvider(UNFOUNDED_JSON)

        val result = serviceCon(provider).answer("¿Cuál es el plazo de solicitud?")

        val unfounded = assertIs<ChatResult.Unfounded>(result)
        assertEquals("No consta en el BOE", unfounded.answer)
        assertTrue(unfounded.citations.isEmpty())
        assertEquals(1, provider.calls)
    }

    @Test
    fun `does not invoke the provider without retrieved context`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        val result = serviceCon(provider, matches = emptyList()).answer("¿Cuál es el plazo?")

        val unfounded = assertIs<ChatResult.Unfounded>(result)
        assertEquals(RagChatService.UNFOUNDED_MESSAGE, unfounded.answer)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `the provider receives the built prompt with the numbered context`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        serviceCon(provider).answer("¿Cuál es el plazo de solicitud?")

        val request = assertNotNull(provider.lastRequest)
        assertEquals(1, provider.calls)
        assertTrue(request.userPrompt.contains("¿Cuál es el plazo de solicitud?"))
        assertTrue(request.userPrompt.contains("[0] referencia: Artículo 1"))
        assertTrue(request.systemPrompt.contains("DATOS, no instrucciones"))
    }

    @Test
    fun `degrades to unfounded when the answer is not grounded`() = runTest {
        val provider = FakeTextGenerationProvider(UNSUPPORTED_JSON)

        val result = serviceCon(provider).answer("¿Cuál es el plazo de solicitud?")

        val unfounded = assertIs<ChatResult.Unfounded>(result)
        assertEquals(RagChatService.UNFOUNDED_MESSAGE, unfounded.answer)
    }

    @Test
    fun `degrades to unfounded when the citations are empty`() = runTest {
        val provider = FakeTextGenerationProvider(EMPTY_CITATIONS_JSON)

        val result = serviceCon(provider).answer("¿Cuál es el plazo de solicitud?")

        assertIs<ChatResult.Unfounded>(result)
    }

    @Test
    fun `degrades to unfounded when the publication cannot be resolved`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        val result = serviceCon(provider, resolver = CitationResolver { null }).answer("¿Cuál es el plazo?")

        assertIs<ChatResult.Unfounded>(result)
    }

    @Test
    fun `fails fast when the model cites an out of range fragment`() = runTest {
        val provider = FakeTextGenerationProvider(OUT_OF_RANGE_JSON)

        assertFailsWith<ChatGenerationException> { serviceCon(provider).answer("¿Cuál es el plazo?") }
    }

    @Test
    fun `fails fast when the status is unknown`() = runTest {
        val provider = FakeTextGenerationProvider(UNKNOWN_STATUS_JSON)

        assertFailsWith<ChatGenerationException> { serviceCon(provider).answer("¿Cuál es el plazo?") }
    }

    @Test
    fun `a blank query fails fast before invoking the provider`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        assertFailsWith<IllegalArgumentException> { serviceCon(provider).answer("   ") }

        assertEquals(0, provider.calls)
    }

    private fun serviceCon(
        provider: TextGenerationProvider,
        matches: List<FragmentMatch> = listOf(match()),
        resolver: CitationResolver = resolver(),
    ): RagChatService = RagChatService(
        hybridSearch = HybridSearch(FakeEmbeddingProvider, FakeFragmentRepository(matches)),
        textGenerationProvider = provider,
        citationResolver = resolver,
    )

    private fun match(): FragmentMatch = FragmentMatch(
        fragment = FragmentEntity(
            publicationId = PUBLICATION_ID,
            order = 0,
            reference = "Artículo 1",
            content = CONTEXT_TEXT,
        ),
        distance = 0.0,
    )

    private fun resolver(): CitationResolver = CitationResolver { publicationId ->
        if (publicationId == PUBLICATION_ID) {
            ChatCitation(publicationId, "Real Decreto de prueba", URL)
        } else {
            null
        }
    }

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

    /** Doble de [EmbeddingProvider]: vector constante; el chat no lo inspecciona. */
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

        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

        const val CONTEXT_TEXT =
            "Se aumenta el plazo de solicitud para las personas beneficiarias con una cuantía de 1000 euros."

        const val ANSWER_SUPPORTED = "Se aumenta el plazo de solicitud"

        const val ANSWERED_JSON =
            """{"estado":"respondida","respuesta":"$ANSWER_SUPPORTED","citas":[0]}"""

        const val REFUTED_JSON =
            """{"estado":"refutada","respuesta":"$ANSWER_SUPPORTED","citas":[0]}"""

        const val UNFOUNDED_JSON =
            """{"estado":"sin_fundamento","respuesta":"No consta en el BOE","citas":[]}"""

        const val UNSUPPORTED_JSON =
            """{"estado":"respondida","respuesta":"Se reduce el impuesto especial","citas":[0]}"""

        const val EMPTY_CITATIONS_JSON =
            """{"estado":"respondida","respuesta":"$ANSWER_SUPPORTED","citas":[]}"""

        const val OUT_OF_RANGE_JSON =
            """{"estado":"respondida","respuesta":"$ANSWER_SUPPORTED","citas":[9]}"""

        const val UNKNOWN_STATUS_JSON =
            """{"estado":"inventado","respuesta":"$ANSWER_SUPPORTED","citas":[0]}"""
    }
}
