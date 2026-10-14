package es.aviferdev.datopublico.feature.feed.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.app_title
import es.aviferdev.datopublico.resources.cd_empty
import es.aviferdev.datopublico.resources.cd_error
import es.aviferdev.datopublico.resources.chat_entry
import es.aviferdev.datopublico.resources.feed_empty_message
import es.aviferdev.datopublico.resources.feed_empty_title
import es.aviferdev.datopublico.resources.feed_error_message
import es.aviferdev.datopublico.resources.feed_error_title
import es.aviferdev.datopublico.resources.feed_loading
import es.aviferdev.datopublico.resources.feed_publications_title
import es.aviferdev.datopublico.resources.feed_retry
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** del feed diario: pinta el estado recibido y delega el
 * reintento en [onRetry]. No hace IO ni conoce el repositorio.
 *
 * @param state estado actual de la pantalla.
 * @param onRetry acción a ejecutar al pulsar «Reintentar».
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación.
 * @param onOpenChat acción al pulsar la entrada de chat del encabezado.
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
    Column(modifier = modifier.fillMaxSize()) {
        FeedHeader(
            onOpenChat = onOpenChat,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                FeedUiState.Loading -> LoadingView()
                is FeedUiState.Content -> FeedContent(
                    feed = state.feed,
                    onPublicationClick = onPublicationClick,
                )
                FeedUiState.Empty -> EmptyView()
                FeedUiState.Error -> ErrorView(onRetry = onRetry)
            }
        }
    }
}

/**
 * Encabezado del feed: título de la app y la acción de **chat** («Preguntar al
 * BOE»).
 *
 * Vive **fuera** de la rama de contenido, así que la entrada al chat sigue
 * disponible en los estados de carga, vacío y error (el feed real puede salir
 * vacío hasta que se cablee el pipeline de resumen).
 *
 * Se usa una acción de **texto** (no un icono) porque el set
 * `material-icons-core` no incluye `Chat`/`Forum` y no se añade
 * `material-icons-extended`.
 *
 * @param onOpenChat acción al pulsar la entrada de chat.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun FeedHeader(onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.app_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenChat) {
            Text(text = stringResource(Res.string.chat_entry))
        }
    }
}

/**
 * Contenido del feed: bloque de destacados (si los hay) y lista de publicaciones
 * del día.
 *
 * @param feed feed que se muestra.
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación.
 */
@Composable
private fun FeedContent(
    feed: DailyFeed,
    onPublicationClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (feed.highlights.isNotEmpty()) {
            HighlightsSection(highlights = feed.highlights, onPublicationClick = onPublicationClick)
        }
        Text(
            text = stringResource(Res.string.feed_publications_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        feed.publications.forEach { publication ->
            PublicationCard(
                publication = publication,
                onClick = { onPublicationClick(publication.id) },
            )
        }
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
            tint = MaterialTheme.colorScheme.tertiary,
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
            FeedScreen(state = FeedUiState.Loading, onRetry = {}, onPublicationClick = {}, onOpenChat = {})
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
            FeedContent(feed = previewDailyFeed(), onPublicationClick = {})
        }
    }
}

/** Previsualización del encabezado del feed con la acción de chat. */
@Preview
@Composable
private fun FeedHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FeedHeader(onOpenChat = {}, modifier = Modifier.padding(8.dp))
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
