package es.aviferdev.datopublico.feature.feed.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.toApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cliente de la API del **feed diario** (FT00035).
 *
 * Llama a `GET {baseUrl}/v1/feed?fecha=<ISO>` con el [HttpClient] común y traduce
 * el resultado a un [ApiResult] **tipado**, sin propagar excepciones a la capa de
 * datos: la traducción de la respuesta (éxito decodificado, error de servidor
 * tipado o cuerpo ilegible) vive en `core/network` ([toApiResult]) y se reutiliza
 * entre clientes de API; un fallo de transporte se traduce aquí a
 * [ApiFailure.Network]. [CancellationException] se **re-lanza** para no romper la
 * cancelación de corrutinas.
 *
 * No conoce el dominio: solo entrega el DTO de wire; el mapeo es
 * [toDailyFeed].
 *
 * @param httpClient cliente HTTP común (MockEngine en los tests).
 * @param config URL base y timeout de las peticiones.
 */
class FeedApi(
    private val httpClient: HttpClient,
    private val config: ApiConfig = ApiConfig(),
) {

    /**
     * Pide el feed del día [date].
     *
     * @param date fecha ISO-8601 (`aaaa-mm-dd`).
     * @return el DTO del feed, o un fallo tipado si la red, el estado HTTP o el
     *   cuerpo fallan; nunca lanza (salvo cancelación).
     */
    suspend fun dailyFeed(date: String): ApiResult<FeedResponseDto> {
        val response = try {
            httpClient.get(config.baseUrl + FEED_PATH) { parameter("fecha", date) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        val result: ApiResult<FeedResponseDto> = if (response == null) {
            ApiResult.Failure(ApiFailure.Network)
        } else {
            response.toApiResult()
        }
        return result
    }

    companion object {
        /** Ruta versionada del feed diario. */
        const val FEED_PATH: String = "/v1/feed"
    }
}
