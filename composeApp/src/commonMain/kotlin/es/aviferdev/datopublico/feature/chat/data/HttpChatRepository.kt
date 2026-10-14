package es.aviferdev.datopublico.feature.chat.data

import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.feature.chat.data.remote.ChatApi
import es.aviferdev.datopublico.feature.chat.data.remote.toChatAnswer
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult

/**
 * Repositorio del chat RAG sobre el **cliente HTTP** (FT00037).
 *
 * Implementa el puerto [ChatRepository] sobre [ChatApi] y `core/network`
 * (FT00035): envía la consulta a `POST /v1/chat` y mapea el DTO de wire a los
 * modelos de dominio. Un fallo (red, estado HTTP o cuerpo) se traduce a
 * [ChatLoadResult.Failure]; nunca lanza excepciones hacia la UI.
 *
 * El repositorio **en memoria** ([InMemoryChatRepository]) queda para `@Preview`
 * y tests.
 *
 * @param chatApi cliente de la API del chat.
 */
class HttpChatRepository(private val chatApi: ChatApi) : ChatRepository {

    /**
     * Envía [query] al chat por HTTP.
     *
     * @param query consulta en lenguaje natural.
     * @return la respuesta fundamentada o [ChatLoadResult.Failure] si la red, el
     *   estado HTTP o el cuerpo fallan.
     */
    override suspend fun ask(query: String): ChatLoadResult {
        val result = when (val apiResult = chatApi.ask(query)) {
            is ApiResult.Success -> ChatLoadResult.Success(apiResult.data.toChatAnswer())
            is ApiResult.Failure -> ChatLoadResult.Failure
        }
        return result
    }
}
