package es.aviferdev.datopublico.backend.ingestion.publication

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementación Ktor de [BoeTextClient] con `HttpClient` **inyectado**.
 *
 * Hace `GET <urlXml>` (la URL es **absoluta**, no hay base URL) con cabecera
 * `Accept: application/xml` y decodifica el cuerpo como **UTF-8**. `404` se
 * traduce a `null` (publicación sin texto XML); cualquier otro estado, fallo de
 * red o cuerpo ilegible lanza [BoeTextException] (nunca datos parciales).
 *
 * @param httpClient cliente de salida ya configurado (`expectSuccess = false`).
 * @param parser parser del XML del BOE; inyectable para los tests.
 */
class BoeTextHttpClient(
    private val httpClient: HttpClient,
    private val parser: BoeXmlParser = BoeXmlParser(),
) : BoeTextClient {

    override suspend fun fetchDocument(xmlUrl: String): BoeDocument? {
        val response = execute(xmlUrl)
        return when (response.status) {
            HttpStatusCode.OK -> parse(xmlUrl, response)
            HttpStatusCode.NotFound -> null
            else -> throw BoeTextException(
                "El BOE respondió ${response.status.value} al pedir el texto de $xmlUrl"
            )
        }
    }

    /** Lanza [BoeTextException] con la URL como contexto ante cualquier fallo de red. */
    private suspend fun execute(xmlUrl: String): HttpResponse = try {
        httpClient.get(xmlUrl) {
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw BoeTextException("No se pudo consultar el texto del BOE para $xmlUrl", error)
    }

    /** Decodifica el cuerpo UTF-8 y lo parsea; envuelve cualquier cuerpo ilegible. */
    private suspend fun parse(xmlUrl: String, response: HttpResponse): BoeDocument = try {
        parser.parse(response.body<ByteArray>().decodeToString())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw BoeTextException("Respuesta ilegible del texto del BOE para $xmlUrl", error)
    }
}

/**
 * Crea el [HttpClient] por defecto del texto del BOE: motor **CIO**,
 * `expectSuccess = false` para tratar a mano el `404` y el resto de estados, y
 * **timeouts explícitos** (la descarga forma parte de la ventana del job y no
 * debe colgarse indefinidamente).
 *
 * No fija base URL: la URL del XML (`xml.php`) es absoluta en el sumario.
 */
fun boeTextHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MS
        socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MS
        connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MS
    }
}

/** Tiempo máximo por petición del texto XML (ms). */
private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L

/** Tiempo máximo de inactividad del socket del texto XML (ms). */
private const val HTTP_SOCKET_TIMEOUT_MS = 30_000L

/** Tiempo máximo para establecer la conexión con el BOE (ms). */
private const val HTTP_CONNECT_TIMEOUT_MS = 10_000L
