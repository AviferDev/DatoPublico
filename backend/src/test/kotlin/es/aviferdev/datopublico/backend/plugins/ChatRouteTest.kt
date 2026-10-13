package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.chat.ChatCitation
import es.aviferdev.datopublico.backend.rag.chat.CitationResolver
import es.aviferdev.datopublico.backend.rag.chat.RagChatService
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingException
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.GenerationRequest
import es.aviferdev.datopublico.backend.rag.generation.GenerationResponse
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.ChatResponseDto
import es.aviferdev.datopublico.model.ChatStatusDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate de la ruta `POST /v1/chat` (FT00031) con `testApplication` y
 * **dobles deterministas** (sin red ni BD): `200` con el JSON del chat
 * (`consulta`/`estado`/`respuesta`/`citas`), `200` con `sin_fundamento` y citas
 * vacías, `400` por consulta ausente/en blanco/desmesurada o cuerpo ilegible, `404`
 * cuando el servicio no se registra y `502`/`500` por fallo tipado.
 */
class ChatRouteTest {

    @Test
    fun `chat responds 200 with the answered json and its citations`() = testApplication {
        installChat(serviceWith(FakeTextGenerationProvider(ANSWERED_JSON)))

        val response = postChat("""{"consulta":"¿Cuál es el plazo de solicitud?"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = DatoPublicoJson.decodeFromString<ChatResponseDto>(response.bodyAsText())
        assertEquals("¿Cuál es el plazo de solicitud?", body.query)
        assertEquals(ChatStatusDto.ANSWERED, body.status)
        assertTrue(body.answer.isNotBlank())
        assertEquals(1, body.citations.size)
        assertEquals(URL, body.citations.first().officialUrl)
    }

    @Test
    fun `chat responds 200 with unfounded and empty citations`() = testApplication {
        installChat(serviceWith(FakeTextGenerationProvider(UNFOUNDED_JSON)))

        val response = postChat("""{"consulta":"¿Cuál es el plazo de solicitud?"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = DatoPublicoJson.decodeFromString<ChatResponseDto>(response.bodyAsText())
        assertEquals(ChatStatusDto.UNFOUNDED, body.status)
        assertTrue(body.citations.isEmpty())
    }

    @Test
    fun `chat responds 400 when the query is missing, blank or oversized`() = testApplication {
        installChat(serviceWith(FakeTextGenerationProvider(ANSWERED_JSON)))

        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/chat").status)
        assertEquals(HttpStatusCode.BadRequest, postChat("""{"consulta":"   "}""").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            postChat("""{"consulta":"${"a".repeat(ChatService.MAX_QUERY_LENGTH + 1)}"}""").status,
        )
    }

    @Test
    fun `chat responds 400 when the body is not a valid request`() = testApplication {
        installChat(serviceWith(FakeTextGenerationProvider(ANSWERED_JSON)))

        assertEquals(HttpStatusCode.BadRequest, postChat("{").status)
    }

    @Test
    fun `chat responds 404 when the service is not registered`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        assertEquals(HttpStatusCode.NotFound, client.post("/v1/chat").status)
        assertEquals(HttpStatusCode.NotFound, postChat("""{"consulta":"¿Cuál es el plazo?"}""").status)
    }

    @Test
    fun `chat responds 502 when the provider fails`() = testApplication {
        installChat(serviceWith(FailingProvider))

        assertEquals(HttpStatusCode.BadGateway, postChat(VALID_QUERY).status)
    }

    @Test
    fun `chat responds 502 when the model output is not interpretable`() = testApplication {
        installChat(serviceWith(FakeTextGenerationProvider(UNKNOWN_STATUS_JSON)))

        assertEquals(HttpStatusCode.BadGateway, postChat(VALID_QUERY).status)
    }

    @Test
    fun `chat responds 500 when the local inference fails`() = testApplication {
        installChat(serviceWith(FakeTextGenerationProvider(ANSWERED_JSON), embedding = FailingEmbeddingProvider))

        assertEquals(HttpStatusCode.InternalServerError, postChat(VALID_QUERY).status)
    }

    /** Registra serialización + rutas con el servicio del chat sobre dobles en memoria. */
    private fun ApplicationTestBuilder.installChat(service: ChatService) {
        application {
            configureSerialization()
            configureRouting(chatService = service)
        }
    }

    /** Envía una petición `POST /v1/chat` con un cuerpo JSON crudo. */
    private suspend fun ApplicationTestBuilder.postChat(payload: String) = client.post("/v1/chat") {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }

    private fun serviceWith(
        provider: TextGenerationProvider,
        matches: List<FragmentMatch> = listOf(match()),
        embedding: EmbeddingProvider = FakeEmbeddingProvider,
    ): ChatService = ChatService(
        RagChatService(
            hybridSearch = HybridSearch(embedding, FakeFragmentRepository(matches)),
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

    /** Doble de [TextGenerationProvider]: devuelve [responseText]. */
    private class FakeTextGenerationProvider(
        private val responseText: String,
    ) : TextGenerationProvider {
        override suspend fun generate(request: GenerationRequest): GenerationResponse =
            GenerationResponse(responseText)
    }

    /** Doble de [TextGenerationProvider] que falla como el proveedor real. */
    private object FailingProvider : TextGenerationProvider {
        override suspend fun generate(request: GenerationRequest): GenerationResponse =
            throw TextGenerationException("fallo simulado del proveedor")
    }

    /** Doble de [EmbeddingProvider]: vector constante. */
    private object FakeEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = 3

        override fun embedQuery(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun embedPassage(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun close() = Unit
    }

    /** Doble de [EmbeddingProvider] que falla como la inferencia local. */
    private object FailingEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = 3

        override fun embedQuery(text: String): FloatArray =
            throw EmbeddingException("fallo simulado de la inferencia local")

        override fun embedPassage(text: String): FloatArray =
            throw EmbeddingException("fallo simulado de la inferencia local")

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

        const val VALID_QUERY = """{"consulta":"¿Cuál es el plazo de solicitud?"}"""

        const val ANSWERED_JSON =
            """{"estado":"respondida","respuesta":"$ANSWER_SUPPORTED","citas":[0]}"""

        const val UNFOUNDED_JSON =
            """{"estado":"sin_fundamento","respuesta":"No consta en el BOE","citas":[]}"""

        const val UNKNOWN_STATUS_JSON =
            """{"estado":"inventado","respuesta":"$ANSWER_SUPPORTED","citas":[0]}"""
    }
}
