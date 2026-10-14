package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import es.aviferdev.datopublico.design.components.isHttpUrl
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.domain.ChatCitation
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_official_source
import es.aviferdev.datopublico.resources.chat_citation_source
import es.aviferdev.datopublico.resources.chat_citations_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Lista de **citas a la fuente oficial** de una respuesta.
 *
 * Cada cita es una superficie clicable que abre la URL oficial del BOE con
 * `LocalUriHandler`; una URL que no sea `http(s)://` deja el enlace
 * **deshabilitado** sin provocar ningún *crash* (reutiliza el helper
 * [isHttpUrl] de `design/components`). Si no hay citas (`sin_fundamento`), el
 * bloque no se pinta.
 *
 * @param citations citas a mostrar; puede estar vacía.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatCitations(citations: List<ChatCitation>, modifier: Modifier = Modifier) {
    if (citations.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.chat_citations_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            citations.forEach { citation -> ChatCitationItem(citation = citation) }
        }
    }
}

/**
 * Cita individual: título de la publicación, etiqueta de fuente oficial e icono
 * de enlace externo.
 *
 * @param citation cita a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatCitationItem(citation: ChatCitation, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val isOpenable = isHttpUrl(citation.officialUrl)
    Surface(
        onClick = { uriHandler.openUri(citation.officialUrl) },
        enabled = isOpenable,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
            Column {
                Text(text = citation.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(Res.string.chat_citation_source),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Cita de ejemplo para las `@Preview`. */
private fun previewCitation(officialUrl: String = PREVIEW_OFFICIAL_URL): ChatCitation =
    ChatCitation(
        publicationId = "BOE-A-2026-21002",
        title = "Resolución de 9 de octubre de 2026, de la Secretaría de Estado de Función Pública.",
        officialUrl = officialUrl,
    )

/** Previsualización de la lista de citas. */
@Preview
@Composable
private fun ChatCitationsPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatCitations(citations = listOf(previewCitation()), modifier = Modifier.padding(8.dp))
        }
    }
}

/**
 * Previsualización de una cita con URL no navegable (queda deshabilitada).
 *
 * Se aporta un [UriHandler] de ejemplo para no depender del de la plataforma.
 */
@Preview
@Composable
private fun ChatCitationItemInvalidUrlPreview() {
    val previewUriHandler = object : UriHandler {
        override fun openUri(uri: String) = Unit
    }
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
                ChatCitationItem(
                    citation = previewCitation(officialUrl = "no-es-una-url"),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** URL oficial de ejemplo para las `@Preview`. */
private const val PREVIEW_OFFICIAL_URL: String =
    "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002"
