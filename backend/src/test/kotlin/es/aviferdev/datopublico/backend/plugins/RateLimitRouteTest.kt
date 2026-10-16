package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.RateLimitSettings
import es.aviferdev.datopublico.backend.persistence.FeedItem
import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.model.ApiErrorCodeDto
import es.aviferdev.datopublico.model.ApiErrorDto
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import kotlin.time.Duration.Companion.seconds

/**
 * Tests de gate del **rate limiting por IP** (FT00030) con `testApplication` y
 * límites pequeños (sin red ni BD): superar el cupo general devuelve `429` con
 * `Retry-After` y cuerpo `demasiadas_peticiones`; `/v1/chat` agota su cupo **antes**
 * que el general (es el endpoint más caro) y `/health`/`/openapi.json` **no** están
 * limitados. Las peticiones permitidas llevan `X-RateLimit-*`.
 *
 * Reutiliza los dobles deterministas del chat ([testChatService]) para registrar
 * `/v1/chat` sin BD, modelo ni OpenCode.
 */
class RateLimitRouteTest {

    @Test
    fun `general quota returns 429 with retry after and typed body`() = testApplication {
        installLimited(requests = 2, chatRequests = 5)

        assertEquals(HttpStatusCode.OK, feed().status)
        assertEquals(HttpStatusCode.OK, feed().status)
        val response = feed()

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertNotNull(response.headers[HttpHeaders.RetryAfter])
        assertEquals(ApiErrorCodeDto.TOO_MANY_REQUESTS, errorCode(response.bodyAsText()))
    }

    @Test
    fun `allowed request carries the rate limit headers`() = testApplication {
        installLimited(requests = 5, chatRequests = 5)

        val response = feed()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("5", response.headers["X-RateLimit-Limit"])
        assertNotNull(response.headers["X-RateLimit-Remaining"])
    }

    @Test
    fun `chat quota is exhausted before the general one`() = testApplication {
        installLimited(requests = 5, chatRequests = 1)

        assertEquals(HttpStatusCode.OK, postChat().status)
        assertEquals(HttpStatusCode.TooManyRequests, postChat().status)
        assertEquals(HttpStatusCode.OK, feed().status)
    }

    @Test
    fun `health and openapi are not rate limited`() = testApplication {
        installLimited(requests = 1, chatRequests = 1)

        repeat(5) {
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
            assertEquals(HttpStatusCode.OK, client.get("/openapi.json").status)
        }
    }

    /** Instala las rutas de dominio con límites pequeños y el rate limiting activo. */
    private fun ApplicationTestBuilder.installLimited(requests: Int, chatRequests: Int) {
        application {
            configureSerialization()
            configureErrorHandling()
            configureRateLimit(RateLimitSettings(requests, 60.seconds, chatRequests, 60.seconds))
            configureRouting(
                feedService = DailyFeedService(InMemoryFeedRepository(listOf(item("BOE-A-1")))),
                chatService = testChatService(),
                applyRateLimits = true,
            )
        }
    }

    /** Petición al feed con una fecha válida. */
    private suspend fun ApplicationTestBuilder.feed(): HttpResponse = client.get("/v1/feed?fecha=$DATE")

    /** Petición al chat con una consulta válida (contexto vacío ⇒ `200`). */
    private suspend fun ApplicationTestBuilder.postChat(): HttpResponse = client.post("/v1/chat") {
        contentType(ContentType.Application.Json)
        setBody(VALID_QUERY)
    }

    /** Código de error tipado del cuerpo de [body]. */
    private fun errorCode(body: String): ApiErrorCodeDto =
        DatoPublicoJson.decodeFromString(ApiErrorDto.serializer(), body).code

    private fun item(id: String): FeedItem = FeedItem(
        publication = Publicacion(
            id = id,
            title = "Ley de prueba",
            publicationDate = DATE,
            issuingBody = "Jefatura del Estado",
            section = SeccionBoeDto.I,
            heading = null,
            texto = null,
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
            urlXml = null,
            urlPdf = null,
            rango = "Ley",
            category = CategoriaDto.NORMAS_Y_LEGISLACION,
        ),
        summary = SummaryEntity(
            publicationId = id,
            whatChanges = "Cambia algo",
            whomItAffects = "A la ciudadanía",
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=$id",
            status = "publicado",
        ),
    )

    /** Doble en memoria de [FeedRepository]. */
    private class InMemoryFeedRepository(private val items: List<FeedItem>) : FeedRepository {
        override fun listPublishedByDate(publicationDate: String): List<FeedItem> =
            items.filter { item -> item.publication.publicationDate == publicationDate }

        override fun listPublishedByIds(ids: List<String>): List<FeedItem> =
            items.filter { item -> item.publication.id in ids }
    }

    private companion object {
        const val DATE = "2026-10-11"
        const val VALID_QUERY = """{"consulta":"¿Cuál es el plazo de solicitud?"}"""
    }
}
