package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.Arrangement
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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Aviso de contenido **generado por IA**: banda sobria y visible (`DESIGN.md`),
 * no un banner agresivo.
 *
 * Es una regla MUST del producto que el aviso sea visible cuando el contenido lo
 * genera la IA (ver `CONSTRAINTS.md` §«Producto y datos»). El componente no tiene
 * estado ni lógica: pinta el texto recibido (por defecto, el del resumen; el chat
 * pasa el suyo). Lo reutiliza el chat (FT00037).
 *
 * @param text texto del aviso; por defecto, el del resumen ciudadano del detalle.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun AiNotice(
    text: String = stringResource(Res.string.detail_ai_notice),
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(Res.string.cd_ai_notice),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Previsualización del aviso de IA. */
@Preview
@Composable
private fun AiNoticePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiNotice(modifier = Modifier.padding(8.dp))
        }
    }
}
