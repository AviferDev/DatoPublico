package es.aviferdev.datopublico.backend.chat

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.chat.ChatCitation
import es.aviferdev.datopublico.backend.rag.chat.CitationResolver
import es.aviferdev.datopublico.backend.rag.chat.RagChatService
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.GenerationRequest
import es.aviferdev.datopublico.backend.rag.generation.GenerationResponse
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.ChatStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red ni BD) de [ChatService] con **dobles deterministas**
 * (escenarios 1–3 y validación del 4 del spec): respondida con cita a la fuente
 * oficial, refutada, sin fundamento con citas vacías, contexto vacío sin invocar
 * al proveedor y fail-fast de la consulta en blanco o desmesurada.
 */
class ChatServiceTest {

    @Test
    fun `answers a question with a citation to the official source`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        val response = serviceWith(provider).ask("¿Cuál es el plazo de solicitud?")

        assertEquals("¿Cuál es el plazo de solicitud?", response.query)
        assertEquals(ChatStatusDto.ANSWERED, response.status)
        assertTrue(response.answer.isNotBlank())
        assertEquals(1, response.citations.size)
        assertEquals(PUBLICATION_ID, response.citations.first().publicationId)
        assertEquals(URL, response.citations.first().officialUrl)
    }

    @Test
    fun `refutes a false claim with citations`() = runTest {
        val provider = FakeTextGenerationProvider(REFUTED_JSON)

        val response = serviceWith(provider).ask("Se reduce el impuesto especial")

        assertEquals(ChatStatusDto.REFUTED, response.status)
        assertTrue(response.citations.isNotEmpty())
        assertEquals(URL, response.citations.first().officialUrl)
    }

    @Test
    fun `declares unfounded without citations`() = runTest {
        val provider = FakeTextGenerationProvider(UNFOUNDED_JSON)

        val response = serviceWith(provider).ask("¿Cuál es el plazo de solicitud?")

        assertEquals(ChatStatusDto.UNFOUNDED, response.status)
        assertTrue(response.citations.isEmpty())
    }

    @Test
    fun `does not invoke the provider without retrieved context`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        val response = serviceWith(provider, matches = emptyList()).ask("¿Cuál es el plazo?")

        assertEquals(ChatStatusDto.UNFOUNDED, response.status)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `a blank query fails fast before invoking the provider`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        assertFailsWith<IllegalArgumentException> { serviceWith(provider).ask("   ") }

        assertEquals(0, provider.calls)
    }

    @Test
    fun `an oversized query fails fast`() = runTest {
        val provider = FakeTextGenerationProvider(ANSWERED_JSON)

        assertFailsWith<IllegalArgumentException> {
            serviceWith(provider).ask("a".repeat(ChatService.MAX_QUERY_LENGTH + 1))
        }

        assertEquals(0, provider.calls)
    }

    private fun serviceWith(
        provider: TextGenerationProvider,
        matches: List<FragmentMatch> = listOf(match()),
    ): ChatService = ChatService(
        RagChatService(
            hybridSearch = HybridSearch(FakeEmbeddingProvider, FakeFragmentRepository(matches)),
            textGenerationProvider = provider,
            citationResolver = resolver(),
        ),
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

    /** Doble de [TextGenerationProvider]: devuelve [responseText] y cuenta llamadas. */
    private class FakeTextGenerationProvider(
        private val responseText: String,
    ) : TextGenerationProvider {
        var calls: Int = 0

        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            calls += 1
            return GenerationResponse(responseText)
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
    }
}
