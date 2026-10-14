package es.aviferdev.datopublico.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aviferdev.datopublico.feature.chat.data.ChatRepository
import es.aviferdev.datopublico.feature.chat.data.InMemoryChatRepository
import es.aviferdev.datopublico.feature.chat.domain.AskChatQuestion
import es.aviferdev.datopublico.feature.chat.domain.ChatAnswer
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de chat (UDF: estado inmutable que la UI sólo lee).
 *
 * Lo expone [ChatViewModel] como `StateFlow` (patrón FT00034) y lo pinta
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

/**
 * ViewModel del chat RAG: expone el estado de la pantalla como un [StateFlow]
 * inmutable (UDF) que la UI sólo lee.
 *
 * Sigue el patrón de FT00034 (`XViewModel` + `StateFlow` + eventos como métodos).
 * Se usa `MutableStateFlow` + `asStateFlow()` (y no `stateIn`) porque el estado
 * nace de **eventos del usuario** (escribir y enviar), no de un flujo frío que
 * haya que compartir; `asStateFlow()` mantiene el contrato `StateFlow` y el UDF.
 *
 * Toda consulta pasa por el caso de uso [askChatQuestion]; la UI nunca llama al
 * repositorio directamente.
 *
 * @param askChatQuestion caso de uso que envía la consulta al chat.
 */
class ChatViewModel(private val askChatQuestion: AskChatQuestion) : ViewModel() {
    /** Estado interno mutable; sólo se expone su versión inmutable. */
    private val mutableState = MutableStateFlow(ChatUiState())

    /**
     * Estado actual de la pantalla; la UI lo recolecta con
     * `collectAsStateWithLifecycle()` y nunca lo muta.
     */
    val uiState: StateFlow<ChatUiState> = mutableState.asStateFlow()

    /**
     * Evento de cambio del campo de entrada.
     *
     * @param value nuevo texto del campo.
     */
    fun onQueryChange(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    /**
     * Evento de envío: ignora la consulta en blanco o que supere
     * [AskChatQuestion.MAX_QUERY_LENGTH]; si es válida, pasa por
     * [ChatResponseState.Loading] y lanza el caso de uso.
     */
    fun onSubmit() {
        val question = mutableState.value.query
        if (question.isNotBlank() && question.length <= AskChatQuestion.MAX_QUERY_LENGTH) {
            submit(question)
        }
    }

    /** Evento de reintento: reenvía la pregunta del estado actual, si la hay. */
    fun onRetry() {
        val question = mutableState.value.response.questionOrNull()
        if (question != null) {
            submit(question)
        }
    }

    /** Limpia el campo, pasa a [ChatResponseState.Loading] y consulta el chat. */
    private fun submit(question: String) {
        mutableState.value = ChatUiState(response = ChatResponseState.Loading(question))
        viewModelScope.launch {
            val result = askChatQuestion(question)
            mutableState.value = mutableState.value.withResult(question, result)
        }
    }

    companion object {
        /**
         * Factory **manual** del [ChatViewModel] (DI por constructor, sin
         * framework). El *composition root* (`App()`) inyecta el repositorio HTTP
         * real; el valor por defecto (repositorio en memoria de ejemplo) sirve a
         * `@Preview` y a los tests.
         *
         * @param repository puerto del chat a usar; por defecto, datos de ejemplo.
         * @return factory lista para `viewModel(factory = …)`.
         */
        fun factory(repository: ChatRepository = InMemoryChatRepository()): ViewModelProvider.Factory =
            viewModelFactory { initializer { ChatViewModel(AskChatQuestion(repository)) } }
    }
}

/**
 * Pregunta enviada del estado actual, o `null` en [ChatResponseState.Idle].
 *
 * @return la pregunta que se está mostrando, para poder reintentarla.
 */
private fun ChatResponseState.questionOrNull(): String? = when (this) {
    ChatResponseState.Idle -> null
    is ChatResponseState.Loading -> question
    is ChatResponseState.Content -> question
    is ChatResponseState.Error -> question
}

/**
 * Aplica el resultado tipado del caso de uso al estado, conservando la pregunta.
 *
 * @param question pregunta enviada.
 * @param result resultado del caso de uso.
 * @return el estado con contenido o con error.
 */
private fun ChatUiState.withResult(question: String, result: ChatLoadResult): ChatUiState = when (result) {
    is ChatLoadResult.Success -> copy(response = ChatResponseState.Content(question, result.answer))
    ChatLoadResult.Failure -> copy(response = ChatResponseState.Error(question))
}
