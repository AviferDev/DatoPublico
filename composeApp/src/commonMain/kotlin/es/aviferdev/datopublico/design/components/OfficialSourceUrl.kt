package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.detail_copied_url
import es.aviferdev.datopublico.resources.detail_copy_url
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * URL oficial **visible** con acción de copia, tal como la muestra el detalle en
 * el mockup: una franja discreta con la dirección truncada y un botón «Copiar
 * enlace».
 *
 * Copia la URL con [LocalClipboardManager] (API común, sin código de
 * plataforma); una URL vacía no copia nada y no provoca ningún *crash*.
 *
 * @param url URL oficial de la publicación.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun OfficialSourceUrl(url: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var isCopied: Boolean by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    if (url.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(url))
                        isCopied = true
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        if (isCopied) Res.string.detail_copied_url else Res.string.detail_copy_url,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Previsualización de la URL visible con su acción de copia. */
@Preview
@Composable
private fun OfficialSourceUrlPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OfficialSourceUrl(
                url = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
                modifier = Modifier.padding(8.dp).width(320.dp),
            )
        }
    }
}
