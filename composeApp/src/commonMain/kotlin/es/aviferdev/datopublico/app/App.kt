package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import es.aviferdev.datopublico.core.network.createDatoPublicoHttpClient
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.data.HttpFeedRepository
import es.aviferdev.datopublico.feature.feed.data.cache.InMemoryFeedCache
import es.aviferdev.datopublico.feature.feed.data.remote.FeedApi
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.feedViewModelFactory
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla compartida por las tres plataformas (Android, iOS y Web): aplica el
 * tema del cliente y monta el **grafo de navegación**.
 *
 * El *composition root* es **manual** (sin framework de DI): aquí se construye,
 * de forma **app-scoped**, el cliente HTTP común (`ktor-client`), la API del feed,
 * la cache offline en memoria y el repositorio HTTP, y se inyectan en la *factory*
 * del `FeedViewModel` (vía `GetDailyFeed`). La URL base la resuelve la plataforma
 * (`expect/actual`), no este código común. El detalle real es FT00033 (aquí solo
 * hay un *placeholder* de navegación).
 *
 * El contenido se pinta con `safeDrawingPadding()` para no quedar bajo las barras
 * del sistema (en Android el modo *edge-to-edge* está forzado por `targetSdk 35`).
 */
@Preview
@Composable
fun App() {
    val factory = remember {
        val httpClient = createDatoPublicoHttpClient()
        val feedApi = FeedApi(httpClient = httpClient)
        val repository = HttpFeedRepository(feedApi = feedApi, cache = InMemoryFeedCache())
        feedViewModelFactory(GetDailyFeed(repository))
    }
    DatoPublicoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavHost(
                feedViewModelFactory = factory,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}
