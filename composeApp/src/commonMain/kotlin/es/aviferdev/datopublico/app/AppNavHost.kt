package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.ui.ChatRoute
import es.aviferdev.datopublico.feature.chat.ui.ChatViewModel
import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailRepository
import es.aviferdev.datopublico.feature.detail.domain.GetPublicationDetail
import es.aviferdev.datopublico.feature.detail.ui.DetailRoute
import es.aviferdev.datopublico.feature.detail.ui.DetailStateHolder
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.FeedRoute
import es.aviferdev.datopublico.feature.feed.ui.FeedViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Grafo de navegación del cliente: **feed** ↔ **detalle** y **chat**.
 *
 * La UI es común a las tres plataformas (`commonMain`), así que cada plataforma
 * solo aporta su *entrypoint* y la librería oficial de navegación resuelve el
 * atrás del sistema/navegador. El [FeedViewModel] y el `ChatViewModel` quedan
 * *scopeados* a su entrada de *back stack*: al volver del detalle o del chat, el
 * feed reaparece con su estado ya cargado.
 *
 * @param feedViewModelFactory factory manual del [FeedViewModel] (DI por
 *   constructor).
 * @param chatViewModelFactory factory manual del `ChatViewModel`; por defecto usa
 *   el repositorio en memoria (el *composition root* inyecta el HTTP real).
 * @param modifier modificador de layout del llamante.
 */
@Suppress("FunctionNaming")
@Composable
fun AppNavHost(
    feedViewModelFactory: ViewModelProvider.Factory,
    chatViewModelFactory: ViewModelProvider.Factory = ChatViewModel.factory(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.FEED,
        modifier = modifier,
    ) {
        composable(AppRoutes.FEED) {
            FeedRoute(
                viewModel = viewModel(factory = feedViewModelFactory),
                onPublicationClick = { publicationId ->
                    navController.navigate(AppRoutes.detail(publicationId))
                },
                onOpenChat = { navController.navigate(AppRoutes.CHAT) },
            )
        }
        detailDestination(navController = navController)
        chatDestination(navController = navController, chatViewModelFactory = chatViewModelFactory)
    }
}

/**
 * Destino de **chat** del grafo: monta la pantalla de chat RAG con su atrás.
 *
 * @param navController controlador del grafo, para el atrás.
 * @param chatViewModelFactory factory del `ChatViewModel` a usar.
 */
private fun NavGraphBuilder.chatDestination(
    navController: NavHostController,
    chatViewModelFactory: ViewModelProvider.Factory,
) {
    composable(AppRoutes.CHAT) {
        ChatRoute(
            onBack = { navController.popBackStack() },
            viewModel = viewModel(factory = chatViewModelFactory),
        )
    }
}

/**
 * Destino de **detalle** del grafo: lee el `id` de la publicación del argumento
 * de ruta (nunca con `!!`; si faltara se usa cadena vacía para no provocar un
 * *crash*) y monta la **pantalla de detalle real** (FT00033) a través de
 * [DetailRoute].
 *
 * El [DetailStateHolder] se construye con el repositorio **en memoria**: el
 * cliente HTTP real del detalle es una feature posterior. Se recrea solo cuando
 * cambia el `id`, así que volver al feed y abrir otra publicación recarga su
 * detalle.
 *
 * @param navController controlador del grafo, para el atrás.
 */
private fun NavGraphBuilder.detailDestination(navController: NavHostController) {
    composable(
        route = AppRoutes.DETAIL,
        arguments = listOf(navArgument(AppRoutes.ARG_PUBLICATION_ID) { type = NavType.StringType }),
    ) { entry ->
        val id = entry.arguments?.getString(AppRoutes.ARG_PUBLICATION_ID).orEmpty()
        val stateHolder = remember(id) {
            DetailStateHolder(
                id = id,
                getPublicationDetail = GetPublicationDetail(InMemoryDetailRepository()),
            )
        }
        DetailRoute(stateHolder = stateHolder, onBack = { navController.popBackStack() })
    }
}

/** Factory de *preview* del feed, sin latencia para que la previsualización responda. */
private fun previewFeedViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer { FeedViewModel(GetDailyFeed(InMemoryFeedRepository(latencyMillis = 0))) }
}

/** Previsualización del grafo completo (feed → detalle) con datos de ejemplo. */
@Suppress("FunctionNaming")
@Preview
@Composable
private fun AppNavHostPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AppNavHost(
                feedViewModelFactory = previewFeedViewModelFactory(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
