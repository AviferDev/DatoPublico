package es.aviferdev.datopublico.feature.chat.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.ClientApiErrorDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cliente de la API del **chat RAG** (FT00037, sobre `core/network` de FT00035).
 *
 * Llama a `POST {baseUrl}/v1/chat` con el [HttpClient] común, enviando la consulta
 * como JSON ([ChatRequestDto]) y traduciendo el resultado a un [ApiResult]
 * **tipado**, sin propagar excepciones a la capa de datos: un `200` decodifica el
 * cuerpo; un no-2xx intenta leer el cuerpo de error tipado ([ClientApiErrorDto]) y
 * devuelve [ApiFailure.Server]; un fallo de transporte devuelve
 * [ApiFailure.Network] y un cuerpo ilegible, [ApiFailure.InvalidBody].
 * [CancellationException] se **re-lanza** para no romper la cancelación.
 *
 * No conoce el dominio: solo entrega el DTO de wire; el mapeo es [toChatAnswer].
 *
 * @param httpClient cliente HTTP común (MockEngine en los tests).
 * @param config URL base y timeout de las peticiones.
 */
class ChatApi(
    private val httpClient: HttpClient,
    private val config: ApiConfig = ApiConfig(),
) {

    /**
     * Envía [query] al chat.
     *
     * @param query consulta en lenguaje natural.
     * @return el DTO de la respuesta, o un fallo tipado si la red, el estado HTTP
     *   o el cuerpo fallan; nunca lanza (salvo cancelación).
     */
    suspend fun ask(query: String): ApiResult<ChatResponseDto> {
        val response = try {
            httpClient.post(config.baseUrl + CHAT_PATH) {
                contentType(ContentType.Application.Json)
                setBody(ChatRequestDto(query))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        return response?.toApiResult() ?: ApiResult.Failure(ApiFailure.Network)
    }

    /** Traduce la respuesta HTTP a un resultado tipado según su estado. */
    private suspend fun HttpResponse.toApiResult(): ApiResult<ChatResponseDto> {
        val result = if (status.isSuccess()) decodeChat() else ApiResult.Failure(serverFailure())
        return result
    }

    /** Decodifica el cuerpo `200` o marca [ApiFailure.InvalidBody]. */
    private suspend fun HttpResponse.decodeChat(): ApiResult<ChatResponseDto> {
        val result = try {
            ApiResult.Success(body<ChatResponseDto>())
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
        /** Ruta versionada del chat RAG. */
        const val CHAT_PATH: String = "/v1/chat"
    }
}
