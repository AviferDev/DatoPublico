package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.ui.ChatRoute
import es.aviferdev.datopublico.feature.chat.ui.ChatViewModel
import es.aviferdev.datopublico.feature.detail.data.DetailRepository
import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailRepository
import es.aviferdev.datopublico.feature.detail.domain.GetPublicationDetail
import es.aviferdev.datopublico.feature.detail.ui.DetailRoute
import es.aviferdev.datopublico.feature.detail.ui.DetailStateHolder
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.FeedRoute
import es.aviferdev.datopublico.feature.feed.ui.FeedViewModel
import es.aviferdev.datopublico.feature.search.data.InMemorySearchRepository
import es.aviferdev.datopublico.feature.search.domain.SearchPublications
import es.aviferdev.datopublico.feature.search.ui.SearchRoute
import es.aviferdev.datopublico.feature.search.ui.searchViewModelFactory
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Grafo de navegación del cliente envuelto por el **chasis** ([DatoPublicoScaffold]):
 * barra superior e inferior comunes. El **feed**, la **búsqueda** y el **chat**
 * son pestañas con barra inferior; el **detalle** es una pantalla apilada con
 * barra superior de atrás y sin barra inferior.
 *
 * La UI es común a las tres plataformas (`commonMain`), así que cada plataforma
 * solo aporta su *entrypoint* y la librería oficial de navegación resuelve el
 * atrás del sistema/navegador. Cada ViewModel queda *scopeado* a la entrada de
 * *back stack* de su destino: al volver, cada pantalla reaparece con su estado ya
 * cargado.
 *
 * @param feedViewModelFactory factory manual del `FeedViewModel` (DI por
 *   constructor).
 * @param chatViewModelFactory factory manual del `ChatViewModel`; por defecto usa
 *   el repositorio en memoria (el *composition root* inyecta el HTTP real).
 * @param searchViewModelFactory factory manual del `SearchViewModel`; el
 *   *composition root* inyecta el repositorio HTTP real y el valor por defecto
 *   (repositorio en memoria de ejemplo) sirve a las `@Preview`.
 * @param detailRepository repositorio del detalle; el *composition root* inyecta
 *   el cliente HTTP real y el valor por defecto (en memoria) sirve a las
 *   `@Preview`.
 * @param modifier modificador de layout del llamante.
 */
@Suppress("FunctionNaming")
@Composable
fun AppNavHost(
    feedViewModelFactory: ViewModelProvider.Factory,
    chatViewModelFactory: ViewModelProvider.Factory = ChatViewModel.factory(),
    searchViewModelFactory: ViewModelProvider.Factory = searchViewModelFactory(),
    detailRepository: DetailRepository = InMemoryDetailRepository(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    DatoPublicoScaffold(
        currentRoute = currentRoute,
        onNavigate = { route -> navController.navigateToTab(route) },
        onBack = { navController.popBackStack() },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.FEED,
            modifier = modifier.padding(padding),
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
            searchDestination(navController = navController, factory = searchViewModelFactory)
            detailDestination(navController = navController, detailRepository = detailRepository)
            chatDestination(navController = navController, chatViewModelFactory = chatViewModelFactory)
        }
    }
}

/**
 * Navega a una pestaña reutilizando el estado de cada destino: si ya está en la
 * cima no apila otra copia (`launchSingleTop`) y al volver al feed se descartan
 * las pestañas acumuladas.
 *
 * @param route ruta de la pestaña destino.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(AppRoutes.FEED) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
 * Destino de **búsqueda** del grafo: monta la pantalla de búsqueda y conecta sus
 * acciones con el grafo (abrir un resultado → detalle; atrás → volver).
 *
 * @param navController controlador del grafo.
 * @param factory factory del `SearchViewModel`.
 */
private fun NavGraphBuilder.searchDestination(
    navController: NavHostController,
    factory: ViewModelProvider.Factory,
) {
    composable(AppRoutes.SEARCH) {
        SearchRoute(
            viewModel = viewModel(factory = factory),
            onPublicationClick = { publicationId ->
                navController.navigate(AppRoutes.detail(publicationId))
            },
            onBack = { navController.popBackStack() },
        )
    }
}

/**
 * Destino de **detalle** del grafo: lee el `id` de la publicación del argumento
 * de ruta (nunca con `!!`; si faltara se usa cadena vacía para no provocar un
 * *crash*) y monta la pantalla de detalle real a través de [DetailRoute].
 *
 * El [DetailStateHolder] se construye con el [detailRepository] inyectado. Se
 * recrea solo cuando cambia el `id`, así que volver al feed y abrir otra
 * publicación recarga su detalle.
 *
 * @param navController controlador del grafo, para el atrás.
 * @param detailRepository repositorio del detalle a usar.
 */
private fun NavGraphBuilder.detailDestination(
    navController: NavHostController,
    detailRepository: DetailRepository,
) {
    composable(
        route = AppRoutes.DETAIL,
        arguments = listOf(navArgument(AppRoutes.ARG_PUBLICATION_ID) { type = NavType.StringType }),
    ) { entry ->
        val id = entry.arguments?.getString(AppRoutes.ARG_PUBLICATION_ID).orEmpty()
        val stateHolder = remember(id) {
            DetailStateHolder(
                id = id,
                getPublicationDetail = GetPublicationDetail(detailRepository),
            )
        }
        DetailRoute(stateHolder = stateHolder, onBack = { navController.popBackStack() })
    }
}

/** Factory de *preview* del feed, sin latencia para que la previsualización responda. */
private fun previewFeedViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer { FeedViewModel(GetDailyFeed(InMemoryFeedRepository(latencyMillis = 0))) }
}

/** Factory de *preview* de la búsqueda, sin latencia para que la previsualización responda. */
private fun previewSearchViewModelFactory(): ViewModelProvider.Factory =
    searchViewModelFactory(SearchPublications(InMemorySearchRepository(latencyMillis = 0)))

/** Previsualización del grafo completo (feed → búsqueda/detalle) con datos de ejemplo. */
@Suppress("FunctionNaming")
@Preview
@Composable
private fun AppNavHostPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AppNavHost(
                feedViewModelFactory = previewFeedViewModelFactory(),
                searchViewModelFactory = previewSearchViewModelFactory(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
