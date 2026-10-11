package es.aviferdev.datopublico.backend.rag.guardrails

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red) de [HttpSourceReachability] con [MockEngine]:
 * 2xx/3xx ⇒ alcanzable; 404/500/fallo ⇒ no alcanzable (escenario 5 del spec).
 */
class HttpSourceReachabilityTest {

    @Test
    fun `a 200 response is reachable`() = runTest {
        assertTrue(reachability(HttpStatusCode.OK).isReachable(URL))
    }

    @Test
    fun `a 301 response is reachable`() = runTest {
        assertTrue(reachability(HttpStatusCode.MovedPermanently).isReachable(URL))
    }

    @Test
    fun `a 404 response is not reachable`() = runTest {
        assertFalse(reachability(HttpStatusCode.NotFound).isReachable(URL))
    }

    @Test
    fun `a 500 response is not reachable`() = runTest {
        assertFalse(reachability(HttpStatusCode.InternalServerError).isReachable(URL))
    }

    @Test
    fun `a network failure is not reachable`() = runTest {
        val client = HttpClient(MockEngine { throw IOException("sin conexión") }) {
            expectSuccess = false
            followRedirects = false
        }

        assertFalse(HttpSourceReachability(client).isReachable(URL))
    }

    private fun reachability(status: HttpStatusCode): HttpSourceReachability {
        val engine = MockEngine { respond(content = "", status = status) }
        return HttpSourceReachability(HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
        })
    }

    private companion object {
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"
    }
}
