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
 * de forma **app-scoped**, el cliente HTTP común (`ktor-client`) y se inyectan las
 * *factories* de los ViewModels. Para el feed se construyen la API (`GET /v1/feed`),
 * la cache offline en memoria y el repositorio HTTP; para el chat, la API
 * (`POST /v1/chat`) y su repositorio HTTP ([HttpChatRepository]); los repositorios
 * **en memoria** quedan para `@Preview` y tests. La URL base la resuelve la
 * plataforma (`expect/actual`), no este código común. El grafo ya monta la pantalla
 * de detalle real (FT00033), que hoy lee de un repositorio en memoria (su cliente
 * HTTP es una feature posterior).
 *
 * El contenido se pinta con `safeDrawingPadding()` para no quedar bajo las barras
 * del sistema (en Android el modo *edge-to-edge* está forzado por `targetSdk 35`).
 */
@Preview
@Composable
fun App() {
    val httpClient = remember { createDatoPublicoHttpClient() }
    val feedFactory = remember {
        val feedApi = FeedApi(httpClient = httpClient)
        val repository = HttpFeedRepository(feedApi = feedApi, cache = InMemoryFeedCache())
        feedViewModelFactory(GetDailyFeed(repository))
    }
    val chatFactory = remember {
        ChatViewModel.factory(HttpChatRepository(ChatApi(httpClient = httpClient)))
    }
    DatoPublicoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavHost(
                feedViewModelFactory = feedFactory,
                chatViewModelFactory = chatFactory,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}
