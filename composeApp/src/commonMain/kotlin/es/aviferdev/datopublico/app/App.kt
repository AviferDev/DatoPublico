package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.ui.feedViewModelFactory
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla compartida por las tres plataformas (Android, iOS y Web): aplica el
 * tema del cliente y monta el **grafo de navegación**.
 *
 * El *composition root* es **manual** (sin framework de DI): aquí se construye la
 * *factory* del `FeedViewModel` sobre `GetDailyFeed` y el repositorio en memoria
 * de ejemplo. El repositorio HTTP real y la caché son FT00035; el detalle real,
 * FT00033 (aquí solo hay un *placeholder* de navegación).
 *
 * El contenido se pinta con `safeDrawingPadding()` para no quedar bajo las barras
 * del sistema (en Android el modo *edge-to-edge* está forzado por `targetSdk 35`).
 */
@Preview
@Composable
fun App() {
    val factory = remember { feedViewModelFactory() }
    DatoPublicoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavHost(
                feedViewModelFactory = factory,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}
