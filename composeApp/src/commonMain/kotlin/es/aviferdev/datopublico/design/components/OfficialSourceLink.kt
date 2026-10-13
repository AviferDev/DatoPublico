package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_official_source
import es.aviferdev.datopublico.resources.detail_official_source
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Enlace **destacado** a la fuente oficial del BOE (`DESIGN.md`, «Enlace de
 * fuente oficial: siempre visible y claro»).
 *
 * Es una regla MUST del producto que la fuente oficial esté siempre a un toque
 * (ver `CONSTRAINTS.md` §«Producto y datos»). Abre la URL con
 * [LocalUriHandler], que delega en el navegador del sistema sin código de
 * plataforma; una URL que no sea `http(s)://` **no** provoca ningún *crash*: el
 * enlace queda deshabilitado.
 *
 * @param url URL oficial de la publicación.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun OfficialSourceLink(url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val isOpenable = isHttpUrl(url)
    Surface(
        onClick = { uriHandler.openUri(url) },
        enabled = isOpenable,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                contentDescription = stringResource(Res.string.cd_official_source),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(Res.string.detail_official_source),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Comprueba que la URL sea navegable (`http://` o `https://`).
 *
 * Es una función **pura**: evita abrir esquemas inesperados y, por tanto, un
 * *crash* del `UriHandler` de la plataforma.
 *
 * @param url URL a comprobar.
 * @return `true` si la URL empieza por `http://` o `https://`.
 */
private fun isHttpUrl(url: String): Boolean =
    url.startsWith("https://") || url.startsWith("http://")

/** Previsualización del enlace oficial destacado. */
@Preview
@Composable
private fun OfficialSourceLinkPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OfficialSourceLink(
                url = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/**
 * Previsualización del enlace con una URL no navegable (queda deshabilitado).
 *
 * Se aporta un [UriHandler] de ejemplo para que la previsualización no dependa
 * del `UriHandler` real de la plataforma.
 */
@Preview
@Composable
private fun OfficialSourceLinkInvalidUrlPreview() {
    val previewUriHandler = object : UriHandler {
        override fun openUri(uri: String) = Unit
    }
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
                OfficialSourceLink(url = "no-es-una-url", modifier = Modifier.padding(8.dp))
            }
        }
    }
}
