package es.aviferdev.datopublico.feature.chat.ui

import es.aviferdev.datopublico.feature.chat.domain.ChatAnswer

/**
 * Estado de la pantalla de chat (UDF: estado inmutable que la UI sólo lee).
 *
 * Lo expone `ChatViewModel` como `StateFlow` (patrón FT00034) y lo pinta
 * `ChatScreen`. El campo de entrada vive en el **estado** ([query]); los estados
 * [ChatResponseState.Loading]/[ChatResponseState.Content]/
 * [ChatResponseState.Error] llevan la **pregunta enviada** para poder mostrarla y
 * reintentar sin depender del campo (que se limpia al enviar).
 *
 * @property query texto actual del campo de entrada.
 * @property response estado de la respuesta del chat.
 */
data class ChatUiState(
    val query: String = "",
    val response: ChatResponseState = ChatResponseState.Idle,
)

/**
 * Estado de la respuesta del chat dentro de [ChatUiState].
 *
 * Es un **solo intercambio** efímero (pregunta + respuesta): no hay historial.
 */
sealed interface ChatResponseState {
    /** Estado inicial: invitación a preguntar. */
    data object Idle : ChatResponseState

    /** Se está consultando al BOE; conserva la pregunta enviada. */
    data class Loading(val question: String) : ChatResponseState

    /** Hay una respuesta fundamentada (con su pregunta). */
    data class Content(val question: String, val answer: ChatAnswer) : ChatResponseState

    /** La consulta falló; conserva la pregunta enviada para reintentar. */
    data class Error(val question: String) : ChatResponseState
}
