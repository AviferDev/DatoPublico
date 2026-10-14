package es.aviferdev.datopublico.feature.chat.data

import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult

/**
 * Puerto de datos del chat RAG.
 *
 * Es la frontera que implementan el repositorio **en memoria** (placeholder para
 * `@Preview` y tests) y el **HTTP** real ([HttpChatRepository], sobre
 * `core/network`). Devuelve un resultado **tipado** ([ChatLoadResult]) en vez de
 * lanzar excepciones, para que los fallos se modelen como estado de UI.
 */
interface ChatRepository {
    /**
     * Envía una pregunta o afirmación al chat.
     *
     * @param query consulta en lenguaje natural.
     * @return la respuesta fundamentada o un fallo tipado; nunca propaga
     *   excepciones a la UI.
     */
    suspend fun ask(query: String): ChatLoadResult
}
