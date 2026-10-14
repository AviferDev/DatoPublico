package es.aviferdev.datopublico.feature.chat.domain

import es.aviferdev.datopublico.feature.chat.data.ChatRepository

/**
 * Caso de uso: preguntar al chat RAG o verificar una afirmación contra el BOE.
 *
 * La UI (el `ChatViewModel`) llama siempre a este caso de uso y **nunca** al
 * repositorio directamente, según la arquitectura por capas de la skill
 * `compose-multiplatform-architecture`.
 *
 * @param repository puerto de datos del chat.
 */
class AskChatQuestion(private val repository: ChatRepository) {

    /**
     * Envía [query] al chat y devuelve su respuesta tipada.
     *
     * Una consulta **en blanco** o que supere [MAX_QUERY_LENGTH] devuelve
     * [ChatLoadResult.Failure] **sin** llamar al repositorio (guarda defensiva; la
     * UI ya deshabilita el envío, y evita un `400` por longitud).
     *
     * @param query pregunta o afirmación a verificar.
     * @return la respuesta fundamentada o un fallo tipado; nunca propaga
     *   excepciones a la UI.
     */
    suspend operator fun invoke(query: String): ChatLoadResult {
        val result = if (query.isBlank() || query.length > MAX_QUERY_LENGTH) {
            ChatLoadResult.Failure
        } else {
            repository.ask(query)
        }
        return result
    }

    companion object {
        /**
         * Longitud máxima de la consulta, en caracteres.
         *
         * Espeja `ChatService.MAX_QUERY_LENGTH` del backend (FT00031) para no
         * provocar un `400` por longitud desde el cliente.
         */
        const val MAX_QUERY_LENGTH: Int = 1_000
    }
}
