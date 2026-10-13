package es.aviferdev.datopublico.feature.feed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.PublicationCard
import es.aviferdev.datopublico.design.components.previewPublications
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.feed_highlights_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Bloque **Destacados del día**: título de sección y las tarjetas de las
 * publicaciones destacadas, cada una con su **motivo** (badge de destacado que
 * pinta [PublicationCard]).
 *
 * @param highlights publicaciones destacadas del día; el llamante omite la
 *   sección si la lista está vacía.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun HighlightsSection(highlights: List<Publication>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.feed_highlights_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        highlights.forEach { publication -> PublicationCard(publication = publication) }
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
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
