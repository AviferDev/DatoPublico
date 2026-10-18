package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.HighlightReason
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_official_source
import es.aviferdev.datopublico.resources.feed_card_source_link
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Tarjeta de publicación del feed y de la búsqueda, según el mockup: fila de
 * **identificador + fecha**, **etiqueta de categoría**, badges de **destacado** y
 * **plazo**, **título**, **resumen corto** y enlace a la **fuente oficial**.
 *
 * Sigue el mockup: superficie blanca con radio `md` y borde sutil. Es
 * **clicable** (al pulsarla navega al detalle) y el enlace inferior abre la
 * fuente oficial. Todos los colores se leen del tema.
 *
 * @param publication publicación a mostrar.
 * @param onClick acción al pulsar la tarjeta.
 * @param modifier modificador de layout del llamante.
 * @param sourceLinkLabel etiqueta del enlace a la fuente (la búsqueda usa otra).
 */
@Composable
fun PublicationCard(
    publication: Publication,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLinkLabel: StringResource = Res.string.feed_card_source_link,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        PublicationCardContent(publication = publication, sourceLinkLabel = sourceLinkLabel)
    }
}

/**
 * Contenido de la tarjeta: metadatos → título → resumen → badges → enlace a la
 * fuente, con la jerarquía del mockup.
 *
 * @param publication publicación a mostrar.
 * @param sourceLinkLabel etiqueta del enlace a la fuente oficial.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicationCardContent(publication: Publication, sourceLinkLabel: StringResource) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PublicationMetaRow(publication = publication)
        Text(
            text = publication.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = publication.shortSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        PublicationBadgesRow(publication = publication)
        SourceTextLink(url = publication.officialUrl, label = sourceLinkLabel)
    }
}

/**
 * Fila de metadatos: identificador oficial (`BOE-A-…`) a la izquierda y fecha de
 * publicación (`dd/mm/aaaa`) a la derecha.
 *
 * @param publication publicación a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun PublicationMetaRow(publication: Publication, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = publication.id,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatIsoDate(publication.publishedDate),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Fila de badges de la tarjeta: etiqueta de categoría, motivos del destacado y,
 * si aplica, el plazo.
 *
 * Se reparten en varias líneas (`FlowRow`) para que no desborden la tarjeta en
 * pantallas estrechas.
 *
 * @param publication publicación a mostrar.
 * @param modifier modificador de layout del llamante.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicationBadgesRow(publication: Publication, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryLabel(category = publication.category)
        publication.highlightReasons.forEach { reason -> HighlightBadge(reason = reason) }
        publication.deadline?.let { deadline -> DeadlineBadge(deadline = deadline) }
    }
}

/**
 * Enlace sobrio a la fuente oficial: texto en `primary` con icono de enlace
 * externo. Abre la URL con [LocalUriHandler]; una URL no `http(s)://` deja el
 * enlace **deshabilitado** sin provocar ningún *crash*.
 *
 * @param url URL oficial de la publicación.
 * @param label etiqueta localizada del enlace.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SourceTextLink(url: String, label: StringResource, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val isOpenable = isHttpUrl(url)
    Row(
        modifier = modifier.clickable(enabled = isOpenable) { uriHandler.openUri(url) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
            contentDescription = stringResource(Res.string.cd_official_source),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
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

/** Publicaciones de ejemplo para las `@Preview` de las pantallas. */
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
                onClick = {},
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
                onClick = {},
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
                sourceLinkLabel = Res.string.feed_card_source_link,
            )
        }
    }
}

/** Previsualización de la fila de metadatos (identificador y fecha). */
@Preview
@Composable
private fun PublicationMetaRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationMetaRow(publication = previewHighlightedPublication(), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la fila de badges (categoría, destacado y plazo). */
@Preview
@Composable
private fun PublicationBadgesRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationBadgesRow(publication = previewHighlightedPublication(), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del enlace a la fuente oficial. */
@Preview
@Composable
private fun SourceTextLinkPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SourceTextLink(
                url = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
                label = Res.string.feed_card_source_link,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
