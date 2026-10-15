package es.aviferdev.datopublico.feature.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Punto de entrada de la **ruta** del feed: recolecta el estado del
 * [FeedViewModel] y delega en [FeedScreen].
 *
 * Es el único composable con dependencia del ViewModel; [FeedScreen] y sus
 * vistas siguen siendo **stateless** (reciben estado y lambdas). La búsqueda la
 * gestiona el chasis global, así que la ruta ya no expone un `onSearchClick`. El
 * estado lo dispara el propio ViewModel (no hay `LaunchedEffect` de carga aquí)
 * y la UI lo recolecta con `collectAsStateWithLifecycle()`.
 *
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación para navegar a su detalle.
 * @param onOpenChat acción al pulsar la llamada a la acción de chat.
 * @param viewModel ViewModel del feed; por defecto se obtiene de la factory
 *   manual (DI por constructor).
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun FeedRoute(
    onPublicationClick: (String) -> Unit,
    onOpenChat: () -> Unit,
    viewModel: FeedViewModel = viewModel(factory = feedViewModelFactory()),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(
        state = state,
        onRetry = viewModel::onRetry,
        onPublicationClick = onPublicationClick,
        onOpenChat = onOpenChat,
        modifier = modifier,
    )
}

/** Previsualización de la ruta con el repositorio de ejemplo. */
@Preview
@Composable
private fun FeedRoutePreview() {
    DatoPublicoTheme {
        FeedRoute(
            onPublicationClick = {},
            onOpenChat = {},
            viewModel = FeedViewModel(GetDailyFeed(InMemoryFeedRepository(latencyMillis = 0))),
        )
    }
}
