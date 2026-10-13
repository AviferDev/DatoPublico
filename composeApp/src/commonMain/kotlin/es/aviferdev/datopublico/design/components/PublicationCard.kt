package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.HighlightReason
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Tarjeta de publicación del feed: **título**, **etiqueta de categoría**,
 * **fecha**, **resumen corto** y los badges de **destacado** y **plazo**.
 *
 * Sigue `DESIGN.md`: superficie `surface` con radio `md` y borde sutil, sin
 * `onClick` (el clic hacia el detalle llega con la navegación, FT00034). Todos los
 * colores se leen del tema.
 *
 * @param publication publicación a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun PublicationCard(publication: Publication, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        PublicationCardContent(publication = publication)
    }
}

/**
 * Contenido de la tarjeta: una idea por tarjeta, con jerarquía título → metadatos
 * → resumen → badges.
 *
 * @param publication publicación a mostrar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicationCardContent(publication: Publication) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = publication.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryLabel(category = publication.category)
            Text(
                text = formatIsoDate(publication.publishedDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = publication.shortSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        PublicationBadgesRow(publication = publication)
    }
}

/**
 * Fila de badges de la tarjeta: los motivos del destacado y, si aplica, el plazo.
 *
 * Se reparten en varias líneas (`FlowRow`) para que no desborden la tarjeta en
 * pantallas estrechas. Si no hay ningún badge no se pinta nada.
 *
 * @param publication publicación a mostrar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicationBadgesRow(publication: Publication) {
    if (publication.highlightReasons.isNotEmpty() || publication.deadline != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            publication.highlightReasons.forEach { reason -> HighlightBadge(reason = reason) }
            publication.deadline?.let { deadline -> DeadlineBadge(deadline = deadline) }
        }
    }
}

/** Publicación de ejemplo destacada y con plazo, solo para las `@Preview`. */
internal fun previewHighlightedPublication(): Publication = Publication(
    id = "BOE-A-2026-21002",
    title = "Resolución de 9 de octubre de 2026, de la Secretaría de Estado de Función Pública, " +
        "por la que se convocan pruebas selectivas.",
    category = PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS,
    publishedDate = "2026-10-13",
    shortSummary = "Convocatoria de 1.250 plazas de acceso libre, con baremo de méritos y fase de oposición.",
    isHighlighted = true,
    highlightReasons = listOf(HighlightReason.OPEN_DEADLINE),
    deadline = Deadline(limitDate = "2026-10-30"),
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
)

/** Publicación de ejemplo sin destacado ni plazo, solo para las `@Preview`. */
internal fun previewPlainPublication(): Publication = Publication(
    id = "BOE-A-2026-21004",
    title = "Real Decreto 905/2026, de 13 de octubre, por el que se nombra Subdirector General de Recursos Humanos.",
    category = PublicationCategory.APPOINTMENTS_AND_DISMISSALS,
    publishedDate = "2026-10-13",
    shortSummary = "Nombramiento efectivo desde el día siguiente al de su publicación.",
    isHighlighted = false,
    highlightReasons = emptyList(),
    deadline = null,
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21004",
)

/** Publicaciones de ejemplo para las `@Preview` de la pantalla del feed. */
internal fun previewPublications(): List<Publication> =
    listOf(previewHighlightedPublication(), previewPlainPublication())

/** Previsualización de la tarjeta con destacado y plazo. */
@Preview
@Composable
private fun PublicationCardHighlightedPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationCard(
                publication = previewHighlightedPublication(),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de la tarjeta sin destacado ni plazo. */
@Preview
@Composable
private fun PublicationCardPlainPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationCard(
                publication = previewPlainPublication(),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del contenido de la tarjeta aislado del borde. */
@Preview
@Composable
private fun PublicationCardContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationCardContent(
                publication = previewHighlightedPublication(),
            )
        }
    }
}

/** Previsualización de la fila de badges con motivo y plazo. */
@Preview
@Composable
private fun PublicationBadgesRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationBadgesRow(
                publication = previewHighlightedPublication(),
            )
        }
    }
}
