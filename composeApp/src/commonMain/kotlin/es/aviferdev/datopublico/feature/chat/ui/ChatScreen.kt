// El fichero agrupa la pantalla, sus sub-composables y sus `@Preview`, lo que
// supera el umbral de funciones por fichero de `detekt.yml`.
@file:Suppress("TooManyFunctions")

package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import es.aviferdev.datopublico.resources.chat_back
import es.aviferdev.datopublico.resources.chat_error_message
import es.aviferdev.datopublico.resources.chat_error_title
import es.aviferdev.datopublico.resources.chat_intro
import es.aviferdev.datopublico.resources.chat_loading
import es.aviferdev.datopublico.resources.chat_question_sent
import es.aviferdev.datopublico.resources.chat_rag_active
import es.aviferdev.datopublico.resources.chat_retry
import es.aviferdev.datopublico.resources.chat_subtitle
import es.aviferdev.datopublico.resources.chat_suggested_grants
import es.aviferdev.datopublico.resources.chat_suggested_solar
import es.aviferdev.datopublico.resources.chat_suggested_title
import es.aviferdev.datopublico.resources.chat_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** del chat RAG: pinta el estado recibido y delega los
 * eventos ([onQueryChange]/[onSubmit]/[onRetry]/[onBack]) en el llamante. No hace
 * IO ni conoce el repositorio.
 *
 * La **sub-cabecera de contenido** (atrás, título con pastilla «RAG Activo» y
 * bajada) vive aquí, dentro del contenido: la barra superior y la inferior de la
 * marca las pinta el chasis ([es.aviferdev.datopublico.app.DatoPublicoScaffold]).
 * El cuerpo se decide con un `when` sobre [ChatUiState.response]: invitación,
 * carga, respuesta (tarjeta, citas, aviso de IA, privacidad y sugerencias) o error
 * con reintento.
 *
 * @param state estado actual de la pantalla.
 * @param onQueryChange acción al cambiar el campo de entrada.
 * @param onSubmit acción al enviar la consulta.
 * @param onRetry acción al reintentar tras un error.
 * @param onBack acción al pulsar «volver al Boletín».
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
        ChatContentHeader(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChatQuestionField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClear = { onQueryChange("") },
            )
            when (val response = state.response) {
                ChatResponseState.Idle -> ChatIntro()
                is ChatResponseState.Loading -> ChatLoading(question = response.question)
                is ChatResponseState.Content -> ChatAnswerContent(
                    question = response.question,
                    answer = response.answer,
                    onSuggestionClick = onQueryChange,
                )
                is ChatResponseState.Error -> ChatError(question = response.question, onRetry = onRetry)
            }
        }
    }
}

/**
 * Sub-cabecera de contenido del chat: acción de **volver al Boletín**, título con
 * la pastilla «RAG Activo» y la bajada de la pantalla.
 *
 * @param onBack acción al pulsar «volver al Boletín».
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatContentHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.chat_back),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ChatTitleRow()
        Text(
            text = stringResource(Res.string.chat_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Título de la pantalla junto a la pastilla de estado «RAG Activo».
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatTitleRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.chat_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        RagActivePill()
    }
}

/**
 * Pastilla «RAG Activo»: un punto en color `secondary` sobre una superficie
 * `surfaceContainerLow` (sin animación de latido: no aporta información).
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun RagActivePill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.secondary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = MaterialTheme.colorScheme.secondary, shape = CircleShape),
            )
            Text(text = stringResource(Res.string.chat_rag_active), style = MaterialTheme.typography.labelSmall)
        }
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
 * Pregunta enviada, mostrada como burbuja alineada a la **derecha**: fondo
 * `primaryContainer` y esquina superior derecha recta, para distinguirla de la
 * respuesta del sistema.
 *
 * @param question pregunta enviada.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatQuestionBubble(question: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.chat_question_sent),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(text = question, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Contenido de una respuesta: pregunta, tarjeta de respuesta, citas, aviso de IA,
 * aviso de privacidad y consultas frecuentes sugeridas.
 *
 * Las sugerencias sólo rellenan el campo ([onSuggestionClick]); no envían la
 * consulta, de modo que la persona decide cuándo preguntar.
 *
 * @param question pregunta enviada.
 * @param answer respuesta a mostrar.
 * @param onSuggestionClick acción al pulsar una consulta sugerida; recibe su texto.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatAnswerContent(
    question: String,
    answer: ChatAnswer,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val solar = stringResource(Res.string.chat_suggested_solar)
    val grants = stringResource(Res.string.chat_suggested_grants)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatQuestionBubble(question = question)
        ChatAnswerCard(answer = answer)
        ChatCitations(citations = answer.citations)
        AiNotice(text = stringResource(Res.string.chat_ai_notice))
        ChatPrivacyNotice()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.chat_suggested_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            ChatSuggestionChip(text = solar, onClick = { onSuggestionClick(solar) })
            ChatSuggestionChip(text = grants, onClick = { onSuggestionClick(grants) })
        }
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
            tint = MaterialTheme.colorScheme.error,
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

/** Previsualización del estado con una respuesta fundamentada (mockup). */
@Preview
@Composable
private fun ChatScreenContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(
                state = ChatUiState(
                    response = ChatResponseState.Content(
                        question = "¿Cuál es el plazo de solicitud y cuántas plazas hay en la convocatoria?",
                        answer = previewAnswer(ChatStatus.ANSWERED),
                    ),
                ),
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

/** Previsualización de la sub-cabecera de contenido del chat. */
@Preview
@Composable
private fun ChatContentHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatContentHeader(onBack = {})
        }
    }
}

/** Previsualización del título con la pastilla de estado. */
@Preview
@Composable
private fun ChatTitleRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatTitleRow(modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la pastilla «RAG Activo». */
@Preview
@Composable
private fun RagActivePillPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RagActivePill(modifier = Modifier.padding(8.dp))
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
                onSuggestionClick = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
