package es.aviferdev.datopublico.feature.feed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.PublicationCard
import es.aviferdev.datopublico.design.components.previewPublications
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.feed_highlight_card_link
import es.aviferdev.datopublico.resources.feed_highlights_priority
import es.aviferdev.datopublico.resources.feed_highlights_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Bloque **Destacados del día** (mockup): cabecera de sección y las tarjetas de
 * las publicaciones destacadas, cada una con su enlace de bases y requisitos.
 *
 * @param highlights publicaciones destacadas del día; el llamante omite la
 *   sección si la lista está vacía.
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun HighlightsSection(
    highlights: List<Publication>,
    onPublicationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HighlightsHeader()
        highlights.forEach { publication ->
            PublicationCard(
                publication = publication,
                onClick = { onPublicationClick(publication.id) },
                sourceLinkLabel = Res.string.feed_highlight_card_link,
            )
        }
    }
}

/**
 * Cabecera del bloque de destacados: icono y título a la izquierda y la marca
 * «Prioridad cívica» a la derecha.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun HighlightsHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.feed_highlights_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(Res.string.feed_highlights_priority),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Previsualización del bloque de destacados con dos publicaciones de ejemplo. */
@Preview
@Composable
private fun HighlightsSectionPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HighlightsSection(
                highlights = previewPublications(),
                onPublicationClick = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de la cabecera del bloque de destacados. */
@Preview
@Composable
private fun HighlightsHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HighlightsHeader(modifier = Modifier.padding(8.dp))
        }
    }
}
