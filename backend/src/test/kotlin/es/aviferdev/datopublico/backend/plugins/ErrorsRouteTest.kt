package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.infra.RateLimitSettings
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
import es.aviferdev.datopublico.model.ApiErrorCodeDto
import es.aviferdev.datopublico.model.ApiErrorDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests de gate del **cuerpo de error tipado** (FT00030) con `testApplication`
 * (sin red ni BD): `400`/`404`/`429`/`500`/`502` responden `Content-Type` JSON con
 * `{"codigo":…,"mensaje":…}` ([ApiErrorDto]) y **sin** filtrar detalles internos.
 *
 * Los fallos `500`/`502` se provocan con los **dobles deterministas** del chat
 * ([testChatService]): `EmbeddingException` (inferencia local) → `500` y
 * `TextGenerationException` (proveedor de texto) → `502`, que `Chat.kt` traduce a
 * esos códigos y `StatusPages` envuelve en el cuerpo tipado. El `404` es la ruta de
 * dominio **no registrada** (sin servicio) y el `429` lo produce el rate limiting.
 */
class ErrorsRouteTest {

    @Test
    fun `invalid request returns the typed 400 body`() = testApplication {
        installErrors(chatService = testChatService())

        val response = postChat("{}")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(ApiErrorCodeDto.INVALID_REQUEST, errorCode(response.bodyAsText()))
    }

    @Test
    fun `unregistered domain route returns the typed 404 body`() = testApplication {
        installErrors()

        val response = client.get("/v1/feed?fecha=2026-10-11")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiErrorCodeDto.NOT_FOUND, errorCode(response.bodyAsText()))
    }

    @Test
    fun `exceeding the limit returns the typed 429 body with retry after`() = testApplication {
        installErrors(chatService = testChatService(), applyRateLimits = true, chatRequests = 1)

        assertEquals(HttpStatusCode.OK, postChat(VALID_QUERY).status)
        val response = postChat(VALID_QUERY)

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertNotNull(response.headers[HttpHeaders.RetryAfter])
        assertEquals(ApiErrorCodeDto.TOO_MANY_REQUESTS, errorCode(response.bodyAsText()))
    }

    @Test
    fun `local inference failure returns the typed 500 body without internals`() = testApplication {
        installErrors(chatService = testChatService(embedding = FailingEmbeddingProvider))

        val response = postChat(VALID_QUERY)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ApiErrorCodeDto.INTERNAL_ERROR, errorCode(response.bodyAsText()))
        assertTrue(!response.bodyAsText().contains("fallo simulado"))
    }

    @Test
    fun `provider failure returns the typed 502 body`() = testApplication {
        installErrors(
            chatService = testChatService(
                matches = listOf(matchingFragment()),
                provider = FailingTextGenerationProvider,
            )
        )

        val response = postChat(VALID_QUERY)

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ApiErrorCodeDto.SERVICE_UNAVAILABLE, errorCode(response.bodyAsText()))
    }

    /** Instala serialización, errores, rate limiting opcional y las rutas. */
    private fun ApplicationTestBuilder.installErrors(
        chatService: ChatService? = null,
        applyRateLimits: Boolean = false,
        chatRequests: Int = 1,
    ) {
        application {
            configureSerialization()
            configureErrorHandling()
            if (applyRateLimits) {
                configureRateLimit(RateLimitSettings(1, 60.seconds, chatRequests, 60.seconds))
            }
            configureRouting(chatService = chatService, applyRateLimits = applyRateLimits)
        }
    }

    /** Envía una petición `POST /v1/chat` con un cuerpo JSON crudo. */
    private suspend fun ApplicationTestBuilder.postChat(payload: String) = client.post("/v1/chat") {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }

    /** Código de error tipado del cuerpo de [body]. */
    private fun errorCode(body: String): ApiErrorCodeDto =
        DatoPublicoJson.decodeFromString(ApiErrorDto.serializer(), body).code

    private companion object {
        const val VALID_QUERY = """{"consulta":"¿Cuál es el plazo de solicitud?"}"""
    }
}

/**
 * Doble de [EmbeddingProvider] con vector constante (no usa el modelo E5).
 *
 * Es un **dobles determinista** compartido por los tests de ruta del chat
 * ([ErrorsRouteTest] y [RateLimitRouteTest]), sin red ni ONNX.
 */
internal object FakeEmbeddingProvider : EmbeddingProvider {
    override val dimensions: Int = 3

    override fun embedQuery(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

    override fun embedPassage(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

    override fun close() = Unit
}

/** Doble de [EmbeddingProvider] que falla como la inferencia local (→ `500`). */
internal object FailingEmbeddingProvider : EmbeddingProvider {
    override val dimensions: Int = 3

    override fun embedQuery(text: String): FloatArray = throw EmbeddingException("fallo simulado")

    override fun embedPassage(text: String): FloatArray = throw EmbeddingException("fallo simulado")

    override fun close() = Unit
}

/** Doble de [TextGenerationProvider] que falla como el proveedor real (→ `502`). */
internal object FailingTextGenerationProvider : TextGenerationProvider {
    override suspend fun generate(request: GenerationRequest): GenerationResponse =
        throw TextGenerationException("fallo simulado del proveedor")
}

/** Doble de [TextGenerationProvider] que no debe invocarse con contexto vacío. */
internal object UnusedTextGenerationProvider : TextGenerationProvider {
    override suspend fun generate(request: GenerationRequest): GenerationResponse =
        throw IllegalStateException("El proveedor no debería invocarse con el contexto vacío.")
}

/** Doble de [FragmentRepository]: devuelve siempre [matches]. */
internal class FakeFragmentRepository(private val matches: List<FragmentMatch>) : FragmentRepository {
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

/** Construye un [ChatService] sobre dobles deterministas (sin BD, ONNX ni OpenCode). */
internal fun testChatService(
    embedding: EmbeddingProvider = FakeEmbeddingProvider,
    matches: List<FragmentMatch> = emptyList(),
    provider: TextGenerationProvider = UnusedTextGenerationProvider,
): ChatService = ChatService(
    RagChatService(
        hybridSearch = HybridSearch(embedding, FakeFragmentRepository(matches)),
        textGenerationProvider = provider,
        citationResolver = CitationResolver { publicationId ->
            if (publicationId == PUBLICATION_ID) {
                ChatCitation(publicationId, "Real Decreto de prueba", OFFICIAL_URL)
            } else {
                null
            }
        },
    ),
)

/** Fragmento recuperado de prueba (contexto no vacío para el chat). */
internal fun matchingFragment(): FragmentMatch = FragmentMatch(
    fragment = FragmentEntity(
        publicationId = PUBLICATION_ID,
        order = 0,
        reference = "Artículo 1",
        content = CONTEXT_TEXT,
    ),
    distance = 0.0,
)

/** Identificador de la publicación de prueba. */
internal const val PUBLICATION_ID = "BOE-A-2026-1"

/** URL oficial de la publicación de prueba. */
internal const val OFFICIAL_URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

/** Texto de contexto de prueba (sustenta la respuesta del chat). */
internal const val CONTEXT_TEXT =
    "Se aumenta el plazo de solicitud para las personas beneficiarias con una cuantía de 1000 euros."
