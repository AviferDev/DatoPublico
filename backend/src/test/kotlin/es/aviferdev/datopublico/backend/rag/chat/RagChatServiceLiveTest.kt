package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeTextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.openCodeHttpClient
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** contra el endpoint **real de OpenCode** (escenario 9 del spec):
 * responde una pregunta con fragmentos de prueba **en memoria** (sin PostgreSQL ni
 * ONNX) y comprueba que la respuesta está fundamentada y lleva **cita a la fuente
 * oficial**.
 *
 * Se salta por defecto (sin `OPENCODE_LIVE_TEST=1`), de modo que el gate y la CI
 * **no** abren sockets. Con `OPENCODE_LIVE_TEST=1` pero **sin** `OPENCODE_API_KEY`,
 * el test falla en claro (fail-fast de [OpenCodeConfig.fromEnv]).
 *
 * ```
 * OPENCODE_LIVE_TEST=1 OPENCODE_API_KEY=<clave> \
 *   ./gradlew :backend:test --tests '*RagChatServiceLiveTest'
 * ```
 */
class RagChatServiceLiveTest {

    @Test
    fun `answers a question with a citation against the real OpenCode endpoint`() = runTest {
        if (System.getenv("OPENCODE_LIVE_TEST") != "1") {
            println(
                "RagChatServiceLiveTest omitido: exporta OPENCODE_LIVE_TEST=1 " +
                    "y OPENCODE_API_KEY para ejecutarlo contra OpenCode."
            )
        } else {
            runLive()
        }
    }

    /** Ejecuta la generación real y afirma que hay respuesta citada y fundamentada. */
    private suspend fun runLive() {
        val config = OpenCodeConfig.fromEnv()
        val client = openCodeHttpClient()
        try {
            val service = RagChatService(
                hybridSearch = HybridSearch(FakeEmbeddingProvider, FakeFragmentRepository),
                textGenerationProvider = OpenCodeTextGenerationProvider(client, config),
                citationResolver = CitationResolver { publicationId ->
                    if (publicationId == PUBLICATION_ID) {
                        ChatCitation(publicationId, "Real Decreto de prueba", URL)
                    } else {
                        null
                    }
                },
            )
            val result = service.answer("¿Cuál es el plazo de solicitud?")
            val answered = assertIs<ChatResult.Answered>(result)
            assertTrue(answered.citations.isNotEmpty())
            assertEquals(URL, answered.citations.first().officialUrl)
            println(
                "RagChatServiceLiveTest: answer='${answered.answer}' " +
                    "citations=${answered.citations.size} url=${answered.citations.first().officialUrl}"
            )
        } finally {
            client.close()
        }
    }

    /** Proveedor de embeddings simulado: la recuperación no usa el modelo real. */
    private object FakeEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = 3

        override fun embedQuery(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun embedPassage(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun close() = Unit
    }

    /** Repositorio simulado que devuelve un fragmento de prueba en memoria. */
    private object FakeFragmentRepository : FragmentRepository {
        private val matches = listOf(
            FragmentMatch(
                fragment = FragmentEntity(
                    publicationId = PUBLICATION_ID,
                    order = 0,
                    reference = "Artículo 1",
                    content = CONTEXT,
                ),
                distance = 0.0,
            )
        )

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

        const val CONTEXT =
            "Artículo 1. Objeto. El plazo de solicitud es de 30 días hábiles desde su publicación " +
                "en el Boletín Oficial del Estado."
    }
}
