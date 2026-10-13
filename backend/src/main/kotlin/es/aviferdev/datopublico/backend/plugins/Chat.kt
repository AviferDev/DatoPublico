package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.rag.chat.ChatGenerationException
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingException
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
import es.aviferdev.datopublico.model.ChatRequestDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.coroutines.cancellation.CancellationException

/**
 * `POST /chat` (FT00031): responde una **pregunta** o verifica una **afirmación**
 * contra el corpus del BOE y devuelve el estado del dominio con las **citas a la
 * fuente oficial**.
 *
 * El cuerpo JSON es [ChatRequestDto] (`{"consulta":"…"}`). Un cuerpo ausente o
 * ilegible, una `consulta` en blanco o más larga que
 * [ChatService.MAX_QUERY_LENGTH] responden `400` **sin cuerpo tipado** (el
 * contrato de error es FT00030). El servicio devuelve `200` con
 * [es.aviferdev.datopublico.model.ChatResponseDto]: `respondida`/`refutada` con
 * **≥1** cita y `sin_fundamento` (no es un error) con `citas` vacía.
 *
 * Los fallos tipados se traducen **sin cuerpo tipado**: un fallo del proveedor
 * ([TextGenerationException]) o una salida no interpretable
 * ([ChatGenerationException]) responden `502`; un fallo de la inferencia local
 * ([EmbeddingException]) responde `500`. La ruta no conoce SQL ni el proveedor:
 * delega en [service] y solo traduce su resultado a HTTP.
 *
 * @param service servicio del chat RAG.
 */
fun Route.chatRoutes(service: ChatService) {
    post("/chat") {
        val request = ChatRequest.from(call.receiveChatRequestOrNull())
        if (request == null) {
            call.respond(HttpStatusCode.BadRequest)
        } else {
            call.respondChatAnswer(service, request.query)
        }
    }
}

/**
 * Petición del chat ya **validada** (consulta no vacía y dentro del máximo).
 *
 * Es un valor interno de la capa HTTP (no es DTO de transporte: no se serializa).
 */
internal data class ChatRequest(
    val query: String,
) {
    companion object {
        /**
         * Valida la consulta de [dto].
         *
         * @return la petición, o `null` si el cuerpo falta o la `consulta` está en
         *   blanco o supera [ChatService.MAX_QUERY_LENGTH] (la ruta responde
         *   `400`).
         */
        fun from(dto: ChatRequestDto?): ChatRequest? {
            val query = dto?.query?.takeIf { raw ->
                raw.isNotBlank() && raw.length <= ChatService.MAX_QUERY_LENGTH
            }
            return query?.let { value -> ChatRequest(value) }
        }
    }
}

/** Lee el cuerpo como [ChatRequestDto]; un cuerpo ausente o ilegible devuelve `null`. */
private suspend fun ApplicationCall.receiveChatRequestOrNull(): ChatRequestDto? = try {
    receiveNullable<ChatRequestDto>()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    null
}

/** Responde la consulta traduciendo los fallos tipados a `502`/`500`. */
private suspend fun ApplicationCall.respondChatAnswer(service: ChatService, query: String) {
    try {
        respond(HttpStatusCode.OK, service.ask(query))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: TextGenerationException) {
        respond(HttpStatusCode.BadGateway)
    } catch (error: ChatGenerationException) {
        respond(HttpStatusCode.BadGateway)
    } catch (error: EmbeddingException) {
        respond(HttpStatusCode.InternalServerError)
    }
}
