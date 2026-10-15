package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.domain.ChatAnswer
import es.aviferdev.datopublico.feature.chat.domain.ChatCitation
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_chat_privacy
import es.aviferdev.datopublico.resources.cd_chat_suggestion
import es.aviferdev.datopublico.resources.chat_privacy_notice
import es.aviferdev.datopublico.resources.chat_status_answered
import es.aviferdev.datopublico.resources.chat_status_refuted
import es.aviferdev.datopublico.resources.chat_status_unfounded
import es.aviferdev.datopublico.resources.chat_synthesis_title
import es.aviferdev.datopublico.resources.chat_unfounded_message
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Piezas del **bloque de respuesta** del chat: la tarjeta con la síntesis, el
 * aviso de privacidad y las consultas frecuentes sugeridas. Todas leen sus colores
 * de `MaterialTheme.colorScheme.*` y sus textos de recursos i18n.
 *
 * La **tarjeta** muestra una pastilla de estado (icono + texto, **no** sólo color),
 * el título de la síntesis en lenguaje claro y el texto de la respuesta. Si el
 * estado es [ChatStatus.UNFOUNDED] añade una nota para reformular; **no** es un
 * error.
 *
 * @param answer respuesta a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatAnswerCard(answer: ChatAnswer, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatStatusPill(status = answer.status)
            ChatSynthesisBlock(
                text = answer.text,
                showUnfoundedMessage = answer.status == ChatStatus.UNFOUNDED,
            )
        }
    }
}

/**
 * Pastilla de estado de la respuesta: el tono depende del estado (`primaryContainer`
 * para `respondida`, `errorContainer` para `refutada` y `surfaceVariant` para
 * `sin_fundamento`, que **no** es un error).
 *
 * @param status estado de la respuesta.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatStatusPill(status: ChatStatus, modifier: Modifier = Modifier) {
    val containerColor = when (status) {
        ChatStatus.ANSWERED -> MaterialTheme.colorScheme.primaryContainer
        ChatStatus.REFUTED -> MaterialTheme.colorScheme.errorContainer
        ChatStatus.UNFOUNDED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        ChatStatus.ANSWERED -> MaterialTheme.colorScheme.onPrimaryContainer
        ChatStatus.REFUTED -> MaterialTheme.colorScheme.onErrorContainer
        ChatStatus.UNFOUNDED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = chatStatusIcon(status),
                contentDescription = stringResource(chatStatusLabel(status)),
                modifier = Modifier.size(16.dp),
            )
            Text(text = stringResource(chatStatusLabel(status)), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Título «Síntesis en lenguaje claro» y el texto de la respuesta; añade la nota de
 * reformulación cuando la respuesta no tiene fundamento en el BOE.
 *
 * @param text texto de la respuesta.
 * @param showUnfoundedMessage `true` si procede mostrar la nota de `sin_fundamento`.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatSynthesisBlock(
    text: String,
    showUnfoundedMessage: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.chat_synthesis_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (showUnfoundedMessage) {
            Text(
                text = stringResource(Res.string.chat_unfounded_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Aviso de **privacidad** del chat: banda sobria que recuerda que no se almacena
 * historial ni datos personales. Se comparte con la pantalla
 * ([ChatScreen]) por eso es `internal` (la UI de la feature vive en el módulo).
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun ChatPrivacyNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(Res.string.cd_chat_privacy),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(Res.string.chat_privacy_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Consulta frecuente sugerida: superficie clicable que rellena el campo de entrada
 * ([onClick] sólo cambia el texto; no envía la consulta). Se comparte con la
 * pantalla ([ChatScreen]) por eso es `internal`.
 *
 * @param text texto de la consulta sugerida.
 * @param onClick acción al pulsarla.
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun ChatSuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.cd_chat_suggestion),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Icono del estado (Material Icons del set core). */
private fun chatStatusIcon(status: ChatStatus): ImageVector = when (status) {
    ChatStatus.ANSWERED -> Icons.Outlined.CheckCircle
    ChatStatus.REFUTED -> Icons.Outlined.Warning
    ChatStatus.UNFOUNDED -> Icons.Outlined.Info
}

/** Texto de recurso de la etiqueta del estado. */
private fun chatStatusLabel(status: ChatStatus): StringResource = when (status) {
    ChatStatus.ANSWERED -> Res.string.chat_status_answered
    ChatStatus.REFUTED -> Res.string.chat_status_refuted
    ChatStatus.UNFOUNDED -> Res.string.chat_status_unfounded
}

/**
 * Respuesta de ejemplo para las `@Preview` y los estados de la pantalla.
 *
 * Es determinista y **no** depende del repositorio.
 *
 * @param status estado a representar.
 * @return una respuesta de ejemplo con citas en `respondida`/`refutada`.
 */
internal fun previewAnswer(status: ChatStatus): ChatAnswer = ChatAnswer(
    status = status,
    text = when (status) {
        ChatStatus.ANSWERED -> "La convocatoria ofrece 1.250 plazas de acceso libre con un plazo de 20 días hábiles."
        ChatStatus.REFUTED -> "El BOE no respalda esa afirmación: la convocatoria exige titulación de bachillerato."
        ChatStatus.UNFOUNDED -> "El BOE no contiene información que permita confirmar ni refutar esta consulta."
    },
    citations = when (status) {
        ChatStatus.UNFOUNDED -> emptyList()
        else -> listOf(
            ChatCitation(
                publicationId = "BOE-A-2026-21002",
                title = "Resolución de 9 de octubre de 2026, de la Secretaría de Estado de Función Pública.",
                officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
            ),
        )
    },
)

/** Previsualización de la respuesta `respondida`. */
@Preview
@Composable
private fun ChatAnswerCardAnsweredPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatAnswerCard(answer = previewAnswer(ChatStatus.ANSWERED), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la respuesta `refutada`. */
@Preview
@Composable
private fun ChatAnswerCardRefutedPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatAnswerCard(answer = previewAnswer(ChatStatus.REFUTED), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la respuesta `sin_fundamento` (no es un error). */
@Preview
@Composable
private fun ChatAnswerCardUnfoundedPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatAnswerCard(answer = previewAnswer(ChatStatus.UNFOUNDED), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del aviso de privacidad del chat. */
@Preview
@Composable
private fun ChatPrivacyNoticePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatPrivacyNotice(modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de una consulta frecuente sugerida. */
@Preview
@Composable
private fun ChatSuggestionChipPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatSuggestionChip(
                text = "¿Se han publicado ayudas o subvenciones para placas solares?",
                onClick = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
