package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
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
import es.aviferdev.datopublico.resources.chat_status_answered
import es.aviferdev.datopublico.resources.chat_status_refuted
import es.aviferdev.datopublico.resources.chat_status_unfounded
import es.aviferdev.datopublico.resources.chat_unfounded_message
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Tarjeta de la **respuesta** del chat: etiqueta de estado (icono + texto, **no**
 * sólo color) y el texto de la respuesta.
 *
 * El tono del contenedor depende del estado: neutro para `respondida`
 * (`primaryContainer`), de atención/error para `refutada` (`errorContainer`) y
 * sobrio para `sin_fundamento` (`surfaceVariant`, que **no** es un error). El
 * estado `sin_fundamento` añade una nota que invita a reformular.
 *
 * @param answer respuesta a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatAnswerCard(answer: ChatAnswer, modifier: Modifier = Modifier) {
    val containerColor = when (answer.status) {
        ChatStatus.ANSWERED -> MaterialTheme.colorScheme.primaryContainer
        ChatStatus.REFUTED -> MaterialTheme.colorScheme.errorContainer
        ChatStatus.UNFOUNDED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (answer.status) {
        ChatStatus.ANSWERED -> MaterialTheme.colorScheme.onPrimaryContainer
        ChatStatus.REFUTED -> MaterialTheme.colorScheme.onErrorContainer
        ChatStatus.UNFOUNDED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatStatusLabel(status = answer.status)
            Text(text = answer.text, style = MaterialTheme.typography.bodyLarge)
            if (answer.status == ChatStatus.UNFOUNDED) {
                Text(
                    text = stringResource(Res.string.chat_unfounded_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Etiqueta de estado: icono con `contentDescription` y texto de recurso.
 *
 * @param status estado de la respuesta.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatStatusLabel(status: ChatStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = chatStatusIcon(status),
            contentDescription = stringResource(chatStatusLabel(status)),
            modifier = Modifier.size(20.dp),
        )
        Text(text = stringResource(chatStatusLabel(status)), style = MaterialTheme.typography.labelLarge)
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
