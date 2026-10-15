package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.domain.AskChatQuestion
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_chat_clear
import es.aviferdev.datopublico.resources.cd_chat_send
import es.aviferdev.datopublico.resources.chat_char_counter
import es.aviferdev.datopublico.resources.chat_clear
import es.aviferdev.datopublico.resources.chat_question_label
import es.aviferdev.datopublico.resources.chat_question_placeholder
import es.aviferdev.datopublico.resources.chat_submit
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Longitud mínima visible del área de escritura de la consulta. */
private val INPUT_MIN_HEIGHT = 72.dp

/**
 * Campo de entrada de la consulta: tarjeta con la etiqueta, el área de escritura
 * multilínea con placeholder, el contador de caracteres, la acción de limpiar y el
 * botón de envío.
 *
 * El botón de envío queda **deshabilitado** si la consulta está en blanco o supera
 * [AskChatQuestion.MAX_QUERY_LENGTH], de modo que la entrada inválida no lanza
 * ninguna petición ni provoca un *crash*. [onClear] vacía el campo (lo decide el
 * llamante).
 *
 * @param query texto actual del campo (UDF: la UI sólo lo lee).
 * @param onQueryChange acción al cambiar el texto.
 * @param onSubmit acción al pulsar enviar.
 * @param onClear acción al pulsar limpiar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatQuestionField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.chat_question_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChatQuestionBox(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClear = onClear,
            )
        }
    }
}

/**
 * Caja interior del campo: fondo `surfaceContainerLow`, el área de escritura y la
 * fila de acciones.
 *
 * @param query texto actual del campo.
 * @param onQueryChange acción al cambiar el texto.
 * @param onSubmit acción al pulsar enviar.
 * @param onClear acción al pulsar limpiar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatQuestionBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSubmitEnabled = query.isNotBlank() && query.length <= AskChatQuestion.MAX_QUERY_LENGTH
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatQuestionInput(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = INPUT_MIN_HEIGHT),
            )
            ChatQuestionFooter(
                charCount = query.length,
                isSubmitEnabled = isSubmitEnabled,
                onClear = onClear,
                onSubmit = onSubmit,
            )
        }
    }
}

/**
 * Área de escritura multilínea, transparente sobre la caja, con el placeholder
 * cuando está vacía.
 *
 * @param query texto actual del campo.
 * @param onQueryChange acción al cambiar el texto.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatQuestionInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        maxLines = 6,
        decorationBox = { innerTextField ->
            Box {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.chat_question_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * Fila de acciones del campo: contador de caracteres y acción de limpiar a la
 * izquierda, botón de envío a la derecha.
 *
 * @param charCount número de caracteres escritos.
 * @param isSubmitEnabled `true` si la consulta se puede enviar.
 * @param onClear acción al pulsar limpiar.
 * @param onSubmit acción al pulsar enviar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatQuestionFooter(
    charCount: Int,
    isSubmitEnabled: Boolean,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.chat_char_counter, charCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ChatClearButton(onClear = onClear)
        }
        ChatSubmitButton(isSubmitEnabled = isSubmitEnabled, onSubmit = onSubmit)
    }
}

/**
 * Acción de **limpiar** el campo: icono y texto en tono secundario.
 *
 * @param onClear acción al pulsarla.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatClearButton(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CircleShape).clickable(onClick = onClear).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = stringResource(Res.string.cd_chat_clear),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.chat_clear),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Botón de **enviar** la consulta: pastilla `primary`/`onPrimary` con el texto y la
 * flecha, habilitada sólo si la consulta es válida.
 *
 * @param isSubmitEnabled `true` si la consulta se puede enviar.
 * @param onSubmit acción al pulsarlo.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatSubmitButton(isSubmitEnabled: Boolean, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onSubmit,
        enabled = isSubmitEnabled,
        modifier = modifier,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = stringResource(Res.string.chat_submit), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.size(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = stringResource(Res.string.cd_chat_send),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Previsualización del campo vacío (envío deshabilitado). */
@Preview
@Composable
private fun ChatQuestionFieldEmptyPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionField(
                query = "",
                onQueryChange = {},
                onSubmit = {},
                onClear = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del campo con una consulta (envío habilitado). */
@Preview
@Composable
private fun ChatQuestionFieldFilledPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionField(
                query = "¿Cuál es el plazo de solicitud?",
                onQueryChange = {},
                onSubmit = {},
                onClear = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de la caja interior del campo. */
@Preview
@Composable
private fun ChatQuestionBoxPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionBox(
                query = "¿Cuál es el plazo de solicitud?",
                onQueryChange = {},
                onSubmit = {},
                onClear = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del área de escritura con placeholder. */
@Preview
@Composable
private fun ChatQuestionInputPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionInput(
                query = "",
                onQueryChange = {},
                modifier = Modifier.padding(8.dp).fillMaxWidth().heightIn(min = INPUT_MIN_HEIGHT),
            )
        }
    }
}

/** Previsualización de la fila de acciones del campo. */
@Preview
@Composable
private fun ChatQuestionFooterPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionFooter(
                charCount = 118,
                isSubmitEnabled = true,
                onClear = {},
                onSubmit = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de la acción de limpiar. */
@Preview
@Composable
private fun ChatClearButtonPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatClearButton(onClear = {}, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del botón de envío habilitado. */
@Preview
@Composable
private fun ChatSubmitButtonPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatSubmitButton(isSubmitEnabled = true, onSubmit = {}, modifier = Modifier.padding(8.dp))
        }
    }
}
