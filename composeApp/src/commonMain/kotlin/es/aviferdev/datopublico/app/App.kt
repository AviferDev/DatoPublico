package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.FeedRoute
import es.aviferdev.datopublico.feature.feed.ui.FeedStateHolder
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla compartida por las tres plataformas (Android, iOS y Web): aplica el
 * tema del cliente y monta la **ruta del feed diario**.
 *
 * El *composition root* es **manual** (sin framework de DI): aquí se construye el
 * holder con `GetDailyFeed` sobre el repositorio en memoria de ejemplo. El
 * repositorio HTTP real y la caché son FT00035; la navegación y el ViewModel con
 * `StateFlow`, FT00034.
 *
 * El contenido se pinta con `safeDrawingPadding()` para no quedar bajo las barras
 * del sistema (en Android el modo *edge-to-edge* está forzado por `targetSdk 35`).
 */
@Preview
@Composable
fun App() {
    val stateHolder = remember {
        FeedStateHolder(GetDailyFeed(InMemoryFeedRepository()))
    }
    DatoPublicoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FeedRoute(
                stateHolder = stateHolder,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}
