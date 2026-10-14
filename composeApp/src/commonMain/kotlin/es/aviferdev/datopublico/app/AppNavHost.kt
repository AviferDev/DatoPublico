package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import es.aviferdev.datopublico.feature.detail.ui.DetailPlaceholderScreen
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.FeedRoute
import es.aviferdev.datopublico.feature.feed.ui.FeedViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Grafo de navegación del cliente: **feed** ↔ **detalle**.
 *
 * La UI es común a las tres plataformas (`commonMain`), así que cada plataforma
 * solo aporta su *entrypoint* y la librería oficial de navegación resuelve el
 * atrás del sistema/navegador. El [FeedViewModel] queda *scopeado* a la entrada
 * de *back stack* del feed: al volver del detalle, el feed reaparece con su
 * estado ya cargado.
 *
 * @param feedViewModelFactory factory manual del [FeedViewModel] (DI por
 *   constructor).
 * @param modifier modificador de layout del llamante.
 */
@Suppress("FunctionNaming")
@Composable
fun AppNavHost(feedViewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
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
            )
        }
        detailDestination(navController = navController)
    }
}

/**
 * Destino de **detalle** del grafo: lee el `id` de la publicación del argumento
 * de ruta (nunca con `!!`; si faltara se usa cadena vacía para no provocar un
 * *crash*) y monta el **placeholder** de navegación.
 *
 * @param navController controlador del grafo, para el atrás.
 */
private fun NavGraphBuilder.detailDestination(navController: NavHostController) {
    composable(
        route = AppRoutes.DETAIL,
        arguments = listOf(navArgument(AppRoutes.ARG_PUBLICATION_ID) { type = NavType.StringType }),
    ) { entry ->
        DetailPlaceholderScreen(
            publicationId = entry.arguments?.getString(AppRoutes.ARG_PUBLICATION_ID).orEmpty(),
            onBack = { navController.popBackStack() },
        )
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
