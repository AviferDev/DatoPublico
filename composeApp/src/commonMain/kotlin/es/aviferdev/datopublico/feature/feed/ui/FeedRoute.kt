package es.aviferdev.datopublico.feature.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Punto de entrada de la **ruta** del feed: mantiene el [FeedStateHolder], lanza
 * la carga inicial y conecta el reintento con la corrutina de composición.
 *
 * Es el único composable con efectos de la feature; [FeedScreen] y sus vistas
 * siguen siendo stateless. FT00034 lo sustituirá por un ViewModel con `StateFlow`
 * y navegación.
 *
 * @param stateHolder holder local del estado del feed.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun FeedRoute(stateHolder: FeedStateHolder, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(stateHolder) { stateHolder.load() }
    FeedScreen(
        state = stateHolder.state,
        onRetry = { scope.launch { stateHolder.load() } },
        modifier = modifier,
    )
}

/** Previsualización de la ruta con el repositorio de ejemplo. */
@Preview
@Composable
private fun FeedRoutePreview() {
    val stateHolder = FeedStateHolder(GetDailyFeed(InMemoryFeedRepository(latencyMillis = 0)))
    DatoPublicoTheme {
        FeedRoute(stateHolder = stateHolder)
    }
}
