package es.aviferdev.datopublico.backend.ingesta.publicacion

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementación Ktor de [BoeTextoClient] con `HttpClient` **inyectado**.
 *
 * Hace `GET <urlXml>` (la URL es **absoluta**, no hay base URL) con cabecera
 * `Accept: application/xml` y decodifica el cuerpo como **UTF-8**. `404` se
 * traduce a `null` (publicación sin texto XML); cualquier otro estado, fallo de
 * red o cuerpo ilegible lanza [BoeTextoException] (nunca datos parciales).
 *
 * @param httpClient cliente de salida ya configurado (`expectSuccess = false`).
 * @param parser parser del XML del BOE; inyectable para los tests.
 */
class BoeTextoHttpClient(
    private val httpClient: HttpClient,
    private val parser: BoeXmlParser = BoeXmlParser(),
) : BoeTextoClient {

    override suspend fun obtenerDocumento(urlXml: String): DocumentoBoe? {
        val response = execute(urlXml)
        return when (response.status) {
            HttpStatusCode.OK -> parse(urlXml, response)
            HttpStatusCode.NotFound -> null
            else -> throw BoeTextoException(
                "El BOE respondió ${response.status.value} al pedir el texto de $urlXml"
            )
        }
    }

    /** Lanza [BoeTextoException] con la URL como contexto ante cualquier fallo de red. */
    private suspend fun execute(urlXml: String): HttpResponse = try {
        httpClient.get(urlXml) {
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw BoeTextoException("No se pudo consultar el texto del BOE para $urlXml", error)
    }

    /** Decodifica el cuerpo UTF-8 y lo parsea; envuelve cualquier cuerpo ilegible. */
    private suspend fun parse(urlXml: String, response: HttpResponse): DocumentoBoe = try {
        parser.parse(response.body<ByteArray>().decodeToString())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw BoeTextoException("Respuesta ilegible del texto del BOE para $urlXml", error)
    }
}

/**
 * Crea el [HttpClient] por defecto del texto del BOE: motor **CIO** y
 * `expectSuccess = false` para tratar a mano el `404` y el resto de estados.
 *
 * No fija base URL: la URL del XML (`xml.php`) es absoluta en el sumario.
 */
fun boeTextoHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
}
