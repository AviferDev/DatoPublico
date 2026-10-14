package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.AiNotice
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.domain.ChatAnswer
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_back
import es.aviferdev.datopublico.resources.cd_chat_error
import es.aviferdev.datopublico.resources.cd_chat_intro
import es.aviferdev.datopublico.resources.chat_ai_notice
import es.aviferdev.datopublico.resources.chat_error_message
import es.aviferdev.datopublico.resources.chat_error_title
import es.aviferdev.datopublico.resources.chat_intro
import es.aviferdev.datopublico.resources.chat_loading
import es.aviferdev.datopublico.resources.chat_question_sent
import es.aviferdev.datopublico.resources.chat_retry
import es.aviferdev.datopublico.resources.chat_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** del chat RAG: pinta el estado recibido y delega los
 * eventos ([onQueryChange]/[onSubmit]/[onRetry]/[onBack]) en el llamante. No hace
 * IO ni conoce el repositorio.
 *
 * La cabecera con **atrás** vive aquí (siempre visible) y no se duplica en cada
 * estado. El contenido se decide con un `when` sobre [ChatUiState.response]:
 * invitación, carga, respuesta (con citas y aviso de IA) o error con reintento.
 *
 * @param state estado actual de la pantalla.
 * @param onQueryChange acción al cambiar el campo de entrada.
 * @param onSubmit acción al enviar la consulta.
 * @param onRetry acción al reintentar tras un error.
 * @param onBack acción al pulsar «volver».
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ChatHeader(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChatQuestionField(query = state.query, onQueryChange = onQueryChange, onSubmit = onSubmit)
            when (val response = state.response) {
                ChatResponseState.Idle -> ChatIntro()
                is ChatResponseState.Loading -> ChatLoading(question = response.question)
                is ChatResponseState.Content -> ChatAnswerContent(
                    question = response.question,
                    answer = response.answer,
                )
                is ChatResponseState.Error -> ChatError(question = response.question, onRetry = onRetry)
            }
        }
    }
}

/**
 * Cabecera del chat: botón de **atrás** y título.
 *
 * @param onBack acción al pulsar «volver».
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(Res.string.chat_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * Estado inicial: invitación a preguntar o a pegar una afirmación.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(Res.string.cd_chat_intro),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.chat_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Estado de carga: la pregunta enviada y un indicador.
 *
 * @param question pregunta enviada.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatLoading(question: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatQuestionBubble(question = question)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(Res.string.chat_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Pregunta enviada, mostrada como burbuja para dar contexto a la respuesta.
 *
 * @param question pregunta enviada.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatQuestionBubble(question: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(Res.string.chat_question_sent), style = MaterialTheme.typography.labelMedium)
            Text(text = question, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Contenido de una respuesta: pregunta, tarjeta de respuesta, citas y aviso de IA.
 *
 * @param question pregunta enviada.
 * @param answer respuesta a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatAnswerContent(question: String, answer: ChatAnswer, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatQuestionBubble(question = question)
        ChatAnswerCard(answer = answer)
        ChatCitations(citations = answer.citations)
        AiNotice(text = stringResource(Res.string.chat_ai_notice))
    }
}

/**
 * Estado de error: la pregunta enviada, un mensaje y el botón «Reintentar». El
 * fallo llega modelado como [ChatResponseState.Error], nunca como excepción, así
 * que la app no se cierra.
 *
 * @param question pregunta enviada.
 * @param onRetry acción de reintento.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatError(question: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatQuestionBubble(question = question)
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = stringResource(Res.string.cd_chat_error),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = stringResource(Res.string.chat_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.chat_error_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(Res.string.chat_retry))
        }
    }
}

/** Previsualización del estado inicial (invitación a preguntar). */
@Preview
@Composable
private fun ChatScreenIdlePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(
                state = ChatUiState(),
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onBack = {},
            )
        }
    }
}

/** Previsualización del estado de carga. */
@Preview
@Composable
private fun ChatLoadingPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatLoading(question = "¿Cuál es el plazo de solicitud?", modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del estado de error con reintento. */
@Preview
@Composable
private fun ChatErrorPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatError(question = "¿Cuál es el plazo de solicitud?", onRetry = {}, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la cabecera del chat. */
@Preview
@Composable
private fun ChatHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatHeader(onBack = {})
        }
    }
}

/** Previsualización de la invitación inicial. */
@Preview
@Composable
private fun ChatIntroPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatIntro(modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la burbuja de la pregunta enviada. */
@Preview
@Composable
private fun ChatQuestionBubblePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionBubble(question = "¿Cuál es el plazo de solicitud?", modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del contenido de una respuesta `respondida`. */
@Preview
@Composable
private fun ChatAnswerContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatAnswerContent(
                question = "¿Cuál es el plazo de solicitud?",
                answer = previewAnswer(ChatStatus.ANSWERED),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
