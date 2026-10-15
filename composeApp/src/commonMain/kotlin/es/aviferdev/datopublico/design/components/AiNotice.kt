package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_ai_notice
import es.aviferdev.datopublico.resources.detail_ai_notice
import es.aviferdev.datopublico.resources.detail_ai_notice_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Aviso de contenido **generado por IA**: banda sobria y visible (mockup), no un
 * banner agresivo.
 *
 * Es una regla MUST del producto que el aviso sea visible cuando el contenido lo
 * genera la IA (ver `CONSTRAINTS.md` §«Producto y datos»). El componente pinta un
 * icono y el texto; el título es **opcional** (el detalle lo usa, el chat no).
 *
 * @param text texto del aviso; por defecto, el del resumen ciudadano del detalle.
 * @param title título opcional del aviso; `null` si no procede.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun AiNotice(
    text: String = stringResource(Res.string.detail_ai_notice),
    title: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(Res.string.cd_ai_notice),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Previsualización del aviso de IA con título (variante del detalle). */
@Preview
@Composable
private fun AiNoticePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiNotice(
                title = stringResource(Res.string.detail_ai_notice_title),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del aviso de IA sin título (variante del chat). */
@Preview
@Composable
private fun AiNoticePlainPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiNotice(modifier = Modifier.padding(8.dp))
        }
    }
}
