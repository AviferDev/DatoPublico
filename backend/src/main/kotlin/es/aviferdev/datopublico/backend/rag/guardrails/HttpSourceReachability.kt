package es.aviferdev.datopublico.backend.rag.guardrails

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementación Ktor de [SourceReachability] con `HttpClient` **inyectado**
 * (FT00022).
 *
 * Hace `GET` sobre la URL oficial con `expectSuccess = false` y devuelve `true`
 * solo si el estado es **2xx/3xx**. Un fallo de red o de conexión se traduce a
 * `false` (la fuente no es alcanzable), no a una excepción: la alcanzabilidad es
 * una comprobación, no un error de programación. La cancelación de la corrutina se
 * propaga.
 *
 * @param httpClient cliente HTTP (CIO en producción; `MockEngine` en los tests).
 */
class HttpSourceReachability(private val httpClient: HttpClient) : SourceReachability {

    override suspend fun isReachable(url: String): Boolean {
        val status = statusOf(url)
        return status != null && status in SUCCESS_RANGE
    }

    /** Estado HTTP de [url], o `null` si la petición falla o la corrutina se cancela. */
    private suspend fun statusOf(url: String): Int? = try {
        httpClient.get(url).status.value
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }

    private companion object {
        /** Rango de estados considerados alcanzables (2xx y 3xx). */
        val SUCCESS_RANGE = 200..399
    }
}

/**
 * Crea el [HttpClient] por defecto de la comprobación de alcanzabilidad: motor
 * **CIO** y **timeouts explícitos** (una fuente colgada no debe bloquear el
 * guardrail). `expectSuccess = false` para leer el estado a mano y
 * `followRedirects = false` para que un `3xx` se observe como alcanzable en vez de
 * seguirse automáticamente.
 */
fun sourceReachabilityHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MS
        socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MS
        connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MS
    }
}

/** Tiempo máximo por petición de alcanzabilidad (ms). */
private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L

/** Tiempo máximo de inactividad del socket de alcanzabilidad (ms). */
private const val HTTP_SOCKET_TIMEOUT_MS = 30_000L

/** Tiempo máximo para establecer la conexión con la fuente (ms). */
private const val HTTP_CONNECT_TIMEOUT_MS = 10_000L
