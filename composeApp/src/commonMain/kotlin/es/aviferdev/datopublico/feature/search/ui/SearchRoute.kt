package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.search.data.InMemorySearchRepository
import es.aviferdev.datopublico.feature.search.domain.SearchPublications
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Punto de entrada de la **ruta** de la búsqueda: recolecta el estado del
 * [SearchViewModel] y delega en [SearchScreen].
 *
 * Es el único composable con dependencia del ViewModel; [SearchScreen] y sus
 * vistas siguen siendo **stateless**. La UI recolecta con
 * `collectAsStateWithLifecycle()` y los eventos se delegan al ViewModel.
 *
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación para navegar a su detalle.
 * @param onBack acción de atrás: vuelve al feed.
 * @param viewModel ViewModel de la búsqueda; por defecto se obtiene de la factory
 *   manual (DI por constructor).
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun SearchRoute(
    onPublicationClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = searchViewModelFactory()),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        onFromDateChange = viewModel::onFromDateChange,
        onToDateChange = viewModel::onToDateChange,
        onSubmit = viewModel::onSubmit,
        onRetry = viewModel::onRetry,
        onClearFilters = viewModel::onClearFilters,
        onPublicationClick = onPublicationClick,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Previsualización de la ruta con el repositorio de ejemplo. */
@Preview
@Composable
private fun SearchRoutePreview() {
    DatoPublicoTheme {
        SearchRoute(
            onPublicationClick = {},
            onBack = {},
            viewModel = SearchViewModel(
                SearchPublications(InMemorySearchRepository(latencyMillis = 0)),
            ),
        )
    }
}
