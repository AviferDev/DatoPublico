package es.aviferdev.datopublico.feature.feed.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.ClientApiErrorDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cliente de la API del **feed diario** (FT00035).
 *
 * Llama a `GET {baseUrl}/v1/feed?fecha=<ISO>` con el [HttpClient] común y traduce
 * el resultado a un [ApiResult] **tipado**, sin propagar excepciones a la capa de
 * datos: un `200` decodifica el cuerpo; un no-2xx intenta leer el cuerpo de error
 * tipado ([ClientApiErrorDto]) y devuelve [ApiFailure.Server]; un fallo de
 * transporte devuelve [ApiFailure.Network] y un cuerpo ilegible,
 * [ApiFailure.InvalidBody]. [CancellationException] se **re-lanza** para no romper
 * la cancelación de corrutinas.
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
        return response?.toApiResult() ?: ApiResult.Failure(ApiFailure.Network)
    }

    /** Traduce la respuesta HTTP a un resultado tipado según su estado. */
    private suspend fun HttpResponse.toApiResult(): ApiResult<FeedResponseDto> {
        val result = if (status.isSuccess()) decodeFeed() else ApiResult.Failure(serverFailure())
        return result
    }

    /** Decodifica el cuerpo `200` o marca [ApiFailure.InvalidBody]. */
    private suspend fun HttpResponse.decodeFeed(): ApiResult<FeedResponseDto> {
        val result = try {
            ApiResult.Success(body<FeedResponseDto>())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ApiResult.Failure(ApiFailure.InvalidBody)
        }
        return result
    }

    /** Lee el cuerpo de error tipado del estado no-2xx, tolerante a ausencia. */
    private suspend fun HttpResponse.serverFailure(): ApiFailure {
        val code = try {
            body<ClientApiErrorDto>().code
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        return ApiFailure.Server(status.value, code)
    }

    companion object {
        /** Ruta versionada del feed diario. */
        const val FEED_PATH: String = "/v1/feed"
    }
}
