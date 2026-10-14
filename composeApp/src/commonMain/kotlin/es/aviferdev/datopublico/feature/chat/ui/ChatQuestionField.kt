package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.domain.AskChatQuestion
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_chat_send
import es.aviferdev.datopublico.resources.chat_question_label
import es.aviferdev.datopublico.resources.chat_question_placeholder
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Campo de entrada de la consulta: multilínea, con etiqueta y placeholder de
 * recursos y un botón de envío.
 *
 * El botón queda **deshabilitado** si la consulta está en blanco o supera
 * [AskChatQuestion.MAX_QUERY_LENGTH], de modo que la entrada inválida no lanza
 * ninguna petición ni provoca un *crash*.
 *
 * @param query texto actual del campo (UDF: la UI sólo lo lee).
 * @param onQueryChange acción al cambiar el texto.
 * @param onSubmit acción al pulsar enviar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatQuestionField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSendEnabled = query.isNotBlank() && query.length <= AskChatQuestion.MAX_QUERY_LENGTH
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = stringResource(Res.string.chat_question_label)) },
        placeholder = { Text(text = stringResource(Res.string.chat_question_placeholder)) },
        maxLines = 4,
        trailingIcon = {
            IconButton(onClick = onSubmit, enabled = isSendEnabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(Res.string.cd_chat_send),
                )
            }
        },
    )
}

/** Previsualización del campo vacío (envío deshabilitado). */
@Preview
@Composable
private fun ChatQuestionFieldEmptyPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatQuestionField(query = "", onQueryChange = {}, onSubmit = {}, modifier = Modifier.padding(8.dp))
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
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
