package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.infra.ProxySettings
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests de gate de la **confianza de cabeceras de proxy** (FT00041) con
 * `testApplication` (sin red ni sockets).
 *
 * Con `TRUST_PROXY_HEADERS` activo, el plugin oficial `XForwardedHeaders` toma la
 * IP del cliente de `X-Forwarded-For` (la que usará el rate limiting por IP de
 * FT00030 tras Nginx); con el flag apagado (por defecto) el plugin **no** se
 * instala y la cabecera se ignora.
 */
class ForwardedHeadersRouteTest {

    @Test
    fun `client ip comes from x forwarded for when trusted`() = testApplication {
        application {
            configureForwardedHeaders(ProxySettings(trustProxyHeaders = true))
            routing { clientIpRoute() }
        }

        val response = client.get("/ip") { header("X-Forwarded-For", CLIENT_IP) }

        assertEquals(CLIENT_IP, response.bodyAsText())
    }

    @Test
    fun `x forwarded for is ignored when not trusted`() = testApplication {
        application {
            configureForwardedHeaders(ProxySettings.default())
            routing { clientIpRoute() }
        }

        val response = client.get("/ip") { header("X-Forwarded-For", CLIENT_IP) }

        assertNotEquals(CLIENT_IP, response.bodyAsText())
    }

    /** Ruta de prueba que expone la IP de origen vista por el servidor. */
    private fun Route.clientIpRoute() {
        get("/ip") { call.respond(call.request.origin.remoteHost) }
    }

    private companion object {
        const val CLIENT_IP = "203.0.113.7"
    }
}
