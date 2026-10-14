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
import es.aviferdev.datopublico.feature.chat.data.HttpChatRepository
import es.aviferdev.datopublico.feature.chat.data.remote.ChatApi
import es.aviferdev.datopublico.feature.chat.ui.ChatViewModel
import es.aviferdev.datopublico.feature.detail.data.HttpDetailRepository
import es.aviferdev.datopublico.feature.detail.data.remote.DetailApi
import es.aviferdev.datopublico.feature.feed.data.HttpFeedRepository
import es.aviferdev.datopublico.feature.feed.data.cache.InMemoryFeedCache
import es.aviferdev.datopublico.feature.feed.data.remote.FeedApi
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.feedViewModelFactory
import es.aviferdev.datopublico.feature.search.data.HttpSearchRepository
import es.aviferdev.datopublico.feature.search.data.remote.SearchApi
import es.aviferdev.datopublico.feature.search.domain.SearchPublications
import es.aviferdev.datopublico.feature.search.ui.searchViewModelFactory
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla compartida por las tres plataformas (Android, iOS y Web): aplica el
 * tema del cliente y monta el **grafo de navegación**.
 *
 * El *composition root* es **manual** (sin framework de DI): aquí se construye,
 * de forma **app-scoped**, un único cliente HTTP común (`ktor-client`) compartido
 * por las APIs del **feed** (FT00035), la **búsqueda** (FT00036) y el **chat**
 * (FT00037), sus repositorios HTTP y las *factories* de sus ViewModels (vía
 * `GetDailyFeed`, `SearchPublications` y `ChatViewModel`). Los repositorios **en
 * memoria** quedan para `@Preview` y tests. La URL base la resuelve la plataforma
 * (`expect/actual`), no este código común. El grafo ya monta la pantalla de detalle
 * real (FT00033), que hoy lee de un repositorio en memoria (su cliente HTTP es una
 * feature posterior).
 *
 * El contenido se pinta con `safeDrawingPadding()` para no quedar bajo las barras
 * del sistema (en Android el modo *edge-to-edge* está forzado por `targetSdk 35`).
 */
@Preview
@Composable
fun App() {
    val httpClient = remember { createDatoPublicoHttpClient() }
    val feedFactory = remember {
        val repository = HttpFeedRepository(
            feedApi = FeedApi(httpClient = httpClient),
            cache = InMemoryFeedCache(),
        )
        feedViewModelFactory(GetDailyFeed(repository))
    }
    val searchFactory = remember {
        searchViewModelFactory(
            SearchPublications(HttpSearchRepository(searchApi = SearchApi(httpClient = httpClient))),
        )
    }
    val chatFactory = remember {
        ChatViewModel.factory(HttpChatRepository(ChatApi(httpClient = httpClient)))
    }
    val detailRepository = remember {
        HttpDetailRepository(DetailApi(httpClient = httpClient))
    }
    DatoPublicoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavHost(
                feedViewModelFactory = feedFactory,
                searchViewModelFactory = searchFactory,
                chatViewModelFactory = chatFactory,
                detailRepository = detailRepository,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}
