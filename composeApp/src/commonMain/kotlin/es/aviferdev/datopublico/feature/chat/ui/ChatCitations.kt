package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.List
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.isHttpUrl
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.domain.ChatCitation
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_official_source
import es.aviferdev.datopublico.resources.chat_citations_title_count
import es.aviferdev.datopublico.resources.chat_citations_validated
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Lista de **citas a la fuente oficial** de una respuesta: cabecera con el título,
 * el número de citas y la marca «Validado», y una tarjeta por cita.
 *
 * Cada cita es una superficie clicable que abre la URL oficial del BOE con
 * `LocalUriHandler`; una URL que no sea `http(s)://` deja la tarjeta
 * **deshabilitada** sin provocar ningún *crash* (reutiliza el helper [isHttpUrl]
 * de `design/components`). Si no hay citas (`sin_fundamento`), el bloque no se
 * pinta.
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
            ChatCitationsHeader(count = citations.size)
            citations.forEach { citation -> ChatCitationItem(citation = citation) }
        }
    }
}

/**
 * Cabecera del bloque de citas: título con el contador entre paréntesis y la marca
 * «Validado» a la derecha.
 *
 * @param count número de citas mostradas.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ChatCitationsHeader(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.chat_citations_title_count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(Res.string.chat_citations_validated),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Cita individual: insignia con el identificador oficial, el título de la
 * publicación y el enlace a la URL oficial.
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CitationIdBadge(publicationId = citation.publicationId)
            Text(
                text = citation.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CitationSourceUrl(url = citation.officialUrl)
        }
    }
}

/**
 * Insignia del identificador oficial de la cita, con el icono de publicación.
 *
 * @param publicationId identificador oficial de la publicación citada.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun CitationIdBadge(publicationId: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                modifier = Modifier.padding(6.dp).size(16.dp),
            )
        }
        Text(
            text = publicationId,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Enlace a la URL oficial de la cita, truncado a una línea, con el icono de enlace
 * externo.
 *
 * @param url URL oficial de la publicación (la tarjeta padre decide si es navegable).
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun CitationSourceUrl(url: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
            contentDescription = stringResource(Res.string.cd_official_source),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
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

/** Previsualización de la cabecera del bloque de citas. */
@Preview
@Composable
private fun ChatCitationsHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatCitationsHeader(count = 1, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de una cita aislada. */
@Preview
@Composable
private fun ChatCitationItemPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatCitationItem(citation = previewCitation(), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la insignia del identificador oficial. */
@Preview
@Composable
private fun CitationIdBadgePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CitationIdBadge(publicationId = "BOE-A-2026-21002", modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del enlace a la URL oficial. */
@Preview
@Composable
private fun CitationSourceUrlPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CitationSourceUrl(url = PREVIEW_OFFICIAL_URL, modifier = Modifier.padding(8.dp))
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
