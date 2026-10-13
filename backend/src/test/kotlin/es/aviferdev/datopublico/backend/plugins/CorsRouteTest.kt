package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.infra.CorsSettings
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de gate de **CORS** (FT00030) con `testApplication` (sin red ni BD): el
 * *preflight* de `/v1/feed` y de `POST /v1/chat` se resuelve (`200` con
 * `Access-Control-Allow-Origin/Methods/Headers`), la petición real incluye
 * `Access-Control-Allow-Origin` y, con `CORS_ALLOWED_ORIGINS` fijado, un origen
 * fuera de la allowlist **no** se permite (ni recibe la cabecera).
 *
 * Nota: Ktor considera `GET`/`POST`/`HEAD` métodos *safelisted* de CORS, así que
 * `Access-Control-Allow-Methods` lista los métodos **extra** (p. ej. `OPTIONS`); el
 * método pedido se valida igualmente (`200`, nunca `403`).
 */
class CorsRouteTest {

    @Test
    fun `preflight of the feed resolves with the cors headers`() = testApplication {
        installCors(CorsSettings.default())

        val response = client.options("/v1/feed") {
            header(HttpHeaders.Origin, ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.headers[HttpHeaders.AccessControlAllowMethods]?.isNotBlank() == true)
    }

    @Test
    fun `preflight of the chat allows the json content type`() = testApplication {
        installCors(CorsSettings.default())

        val response = client.options("/v1/chat") {
            header(HttpHeaders.Origin, ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.headers[HttpHeaders.AccessControlAllowHeaders]?.contains("Content-Type") == true)
    }

    @Test
    fun `real request includes the allow origin header`() = testApplication {
        installCors(CorsSettings.default())

        val response = client.get("/health") { header(HttpHeaders.Origin, ORIGIN) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `allowed origin from the allowlist is served`() = testApplication {
        installCors(CorsSettings(listOf(ORIGIN)))

        val response = client.get("/health") { header(HttpHeaders.Origin, ORIGIN) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `origin outside the allowlist is not allowed`() = testApplication {
        installCors(CorsSettings(listOf(ORIGIN)))

        val response = client.get("/health") { header(HttpHeaders.Origin, "https://ajeno.example") }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    /** Instala serialización, CORS con [settings] y las rutas (sin dependencias). */
    private fun ApplicationTestBuilder.installCors(settings: CorsSettings) {
        application {
            configureSerialization()
            configureCors(settings)
            configureRouting()
        }
    }

    private companion object {
        const val ORIGIN = "https://cliente.example"
    }
}
