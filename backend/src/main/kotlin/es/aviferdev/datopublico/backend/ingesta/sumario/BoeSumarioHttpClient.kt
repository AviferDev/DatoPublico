package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

/** Base de la API de datos abiertos del BOE. */
const val BOE_SUMARIO_BASE_URL: String = "https://www.boe.es/datosabiertos/api"

/**
 * Implementación Ktor de [BoeSumarioClient] con `HttpClient` **inyectado**.
 *
 * La URL de la petición es `GET {baseUrl}/boe/sumario/{YYYYMMDD}` con cabecera
 * `Accept: application/json`. `404` se traduce a lista vacía (ese día no hay
 * sumario); cualquier otro estado o fallo de red lanza [BoeSumarioException].
 */
class BoeSumarioHttpClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = BOE_SUMARIO_BASE_URL,
) : BoeSumarioClient {

    override suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario> {
        val response = execute(fecha)
        return when (response.status) {
            HttpStatusCode.OK -> decode(fecha, response)
            HttpStatusCode.NotFound -> emptyList()
            else -> throw BoeSumarioException(
                "El BOE respondió ${response.status.value} al pedir el sumario de $fecha"
            )
        }
    }

    private suspend fun execute(fecha: LocalDate): HttpResponse = try {
        httpClient.get(urlDe(fecha)) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw BoeSumarioException("No se pudo consultar el sumario del BOE para $fecha", error)
    }

    private suspend fun decode(fecha: LocalDate, response: HttpResponse): List<EntradaSumario> = try {
        response.body<BoeSumarioResponseDto>().toEntradasSumario(fecha)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw BoeSumarioException("Respuesta ilegible del sumario del BOE para $fecha", error)
    }

    private fun urlDe(fecha: LocalDate): String =
        "$baseUrl/boe/sumario/${fecha.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}

/**
 * Crea el [HttpClient] por defecto del sumario del BOE: motor **CIO**,
 * `ContentNegotiation` con [DatoPublicoJson] y **timeouts explícitos** (la
 * descarga es parte de la ventana del job y no debe colgarse indefinidamente).
 * `expectSuccess = false` para gestionar el `404` (día sin publicación) y el
 * resto de estados a mano.
 *
 * @param baseUrl base de la API de datos abiertos del BOE.
 */
fun boeSumarioHttpClient(baseUrl: String = BOE_SUMARIO_BASE_URL): HttpClient =
    HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MS
            socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MS
            connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MS
        }
        defaultRequest { url(baseUrl) }
        install(ContentNegotiation) {
            json(DatoPublicoJson)
        }
    }

/** Tiempo máximo por petición del sumario (ms). */
private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L

/** Tiempo máximo de inactividad del socket del sumario (ms). */
private const val HTTP_SOCKET_TIMEOUT_MS = 30_000L

/** Tiempo máximo para establecer la conexión con el BOE (ms). */
private const val HTTP_CONNECT_TIMEOUT_MS = 10_000L
