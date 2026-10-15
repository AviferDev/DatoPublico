// El fichero agrupa la pantalla del feed, sus sub-composables y una `@Preview`
// por cada uno: la combinación de «una preview por composable» y «máximo 40
// líneas por función» excede el umbral configurado de `TooManyFunctions` (15).
// Se acota la excepción a este fichero.
@file:Suppress("TooManyFunctions")

package es.aviferdev.datopublico.feature.feed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.PublicationCard
import es.aviferdev.datopublico.design.components.previewHighlightedPublication
import es.aviferdev.datopublico.design.components.previewPublications
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_empty
import es.aviferdev.datopublico.resources.cd_error
import es.aviferdev.datopublico.resources.feed_ai_callout_action
import es.aviferdev.datopublico.resources.feed_ai_callout_message
import es.aviferdev.datopublico.resources.feed_ai_callout_title
import es.aviferdev.datopublico.resources.feed_empty_message
import es.aviferdev.datopublico.resources.feed_empty_title
import es.aviferdev.datopublico.resources.feed_error_message
import es.aviferdev.datopublico.resources.feed_error_title
import es.aviferdev.datopublico.resources.feed_loading
import es.aviferdev.datopublico.resources.feed_publications_title
import es.aviferdev.datopublico.resources.feed_retry
import es.aviferdev.datopublico.resources.feed_status_count
import es.aviferdev.datopublico.resources.feed_status_edition
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** del feed diario: pinta el estado recibido y delega las
 * acciones. No hace IO ni conoce el repositorio.
 *
 * No pinta cabecera propia: la marca, el chat y la búsqueda viven en el chasis
 * global (`AppChrome`). El chat se ofrece además como llamada a la acción al
 * final de la lista.
 *
 * @param state estado actual de la pantalla.
 * @param onRetry acción a ejecutar al pulsar «Reintentar».
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación.
 * @param onOpenChat acción al pulsar la llamada a la acción de chat.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun FeedScreen(
    state: FeedUiState,
    onRetry: () -> Unit,
    onPublicationClick: (String) -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            FeedUiState.Loading -> LoadingView()
            is FeedUiState.Content -> FeedContent(
                feed = state.feed,
                onPublicationClick = onPublicationClick,
                onOpenChat = onOpenChat,
            )
            FeedUiState.Empty -> EmptyView()
            FeedUiState.Error -> ErrorView(onRetry = onRetry)
        }
    }
}

/**
 * Contenido del feed con scroll: tira de estado, bloque de destacados (si los
 * hay), lista de publicaciones del día y llamada a la acción de chat.
 *
 * @param feed feed que se muestra.
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación.
 * @param onOpenChat acción al pulsar la llamada a la acción de chat.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun FeedContent(
    feed: DailyFeed,
    onPublicationClick: (String) -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeedStatusStrip(feed = feed)
        if (feed.highlights.isNotEmpty()) {
            HighlightsSection(highlights = feed.highlights, onPublicationClick = onPublicationClick)
        }
        PublicationsHeader()
        feed.publications.forEach { publication ->
            PublicationCard(
                publication = publication,
                onClick = { onPublicationClick(publication.id) },
            )
        }
        AiChatCallout(onOpenChat = onOpenChat)
    }
}

/**
 * Tira de estado del día: fecha y edición del BOE a la izquierda y el recuento
 * de disposiciones en una pastilla a la derecha.
 *
 * @param feed feed del día; aporta la fecha y el número de publicaciones.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun FeedStatusStrip(feed: DailyFeed, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = formatIsoDate(feed.date) + " · " + stringResource(Res.string.feed_status_edition),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        StatusCountPill(count = feed.publications.size)
    }
}

/**
 * Pastilla con el recuento de disposiciones del día: punto de acento y cifra.
 *
 * @param count número de publicaciones del feed.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun StatusCountPill(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = MaterialTheme.colorScheme.secondary, shape = CircleShape),
            )
            Text(
                text = stringResource(Res.string.feed_status_count, count),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Cabecera del bloque de publicaciones del día: icono y título de sección. No se
 * ofrecen filtros: el feed no filtra, así que no se pintan controles muertos.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun PublicationsHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.List,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(Res.string.feed_publications_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Llamada a la acción de **ayuda con IA** al final del feed: círculo con icono,
 * título, mensaje y enlace al chat.
 *
 * @param onOpenChat acción al pulsar el enlace de consulta.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun AiChatCallout(onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
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
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.feed_ai_callout_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.feed_ai_callout_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AiChatCalloutAction(onClick = onOpenChat)
            }
        }
    }
}

/**
 * Enlace de la llamada a la acción de chat: texto y flecha, clicable.
 *
 * @param onClick acción al pulsar el enlace.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun AiChatCalloutAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.feed_ai_callout_action),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Estado de carga: indicador centrado, sin error ni contenido. */
@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.feed_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/** Estado vacío: icono y mensaje que explican que hoy no hay publicaciones. */
@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.cd_empty),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.feed_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.feed_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Estado de error: icono, mensaje y botón «Reintentar». El fallo llega modelado
 * como [FeedUiState.Error], nunca como excepción, así que la app no se cierra.
 *
 * @param onRetry acción de reintento.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = stringResource(Res.string.cd_error),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.feed_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.feed_error_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(Res.string.feed_retry))
        }
    }
}

/** Feed de ejemplo, solo para las `@Preview` de la pantalla. */
private fun previewDailyFeed(): DailyFeed = DailyFeed(
    date = "2026-10-13",
    highlights = listOf(previewHighlightedPublication()),
    publications = previewPublications(),
)

/** Previsualización del estado de carga. */
@Preview
@Composable
private fun FeedScreenLoadingPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FeedScreen(
                state = FeedUiState.Loading,
                onRetry = {},
                onPublicationClick = {},
                onOpenChat = {},
            )
        }
    }
}

/** Previsualización del estado con contenido. */
@Preview
@Composable
private fun FeedScreenContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FeedScreen(
                state = FeedUiState.Content(feed = previewDailyFeed()),
                onRetry = {},
                onPublicationClick = {},
                onOpenChat = {},
            )
        }
    }
}

/** Previsualización del estado vacío. */
@Preview
@Composable
private fun EmptyViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EmptyView()
        }
    }
}

/** Previsualización del estado de error con reintento. */
@Preview
@Composable
private fun ErrorViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorView(onRetry = {})
        }
    }
}

/** Previsualización del contenido del feed aislado de la pantalla. */
@Preview
@Composable
private fun FeedContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FeedContent(
                feed = previewDailyFeed(),
                onPublicationClick = {},
                onOpenChat = {},
            )
        }
    }
}

/** Previsualización de la tira de estado del día. */
@Preview
@Composable
private fun FeedStatusStripPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FeedStatusStrip(feed = previewDailyFeed(), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la pastilla de recuento de disposiciones. */
@Preview
@Composable
private fun StatusCountPillPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StatusCountPill(count = 42, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la cabecera del bloque de publicaciones. */
@Preview
@Composable
private fun PublicationsHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PublicationsHeader(modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la llamada a la acción de chat. */
@Preview
@Composable
private fun AiChatCalloutPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiChatCallout(onOpenChat = {}, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del enlace de la llamada a la acción de chat. */
@Preview
@Composable
private fun AiChatCalloutActionPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiChatCalloutAction(onClick = {}, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del estado de carga aislado de la pantalla. */
@Preview
@Composable
private fun LoadingViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LoadingView()
        }
    }
}
