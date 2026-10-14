package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.PublicationCard
import es.aviferdev.datopublico.design.components.previewPublications
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_back
import es.aviferdev.datopublico.resources.search_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** de la búsqueda: encabezado con atrás, la barra de filtros
 * ([SearchFilterBar]) y el área de resultado según el estado. No hace IO ni conoce
 * el repositorio; recibe el estado y delega los eventos.
 *
 * @param state estado actual de la pantalla.
 * @param onQueryChange evento de cambio de consulta.
 * @param onCategorySelected evento de selección de categoría.
 * @param onFromDateChange evento de cambio de la fecha `desde`.
 * @param onToDateChange evento de cambio de la fecha `hasta`.
 * @param onSubmit evento de envío de la búsqueda.
 * @param onRetry evento de reintento.
 * @param onClearFilters evento de limpieza de filtros.
 * @param onPublicationClick acción al pulsar una tarjeta; recibe el `id` de la
 *   publicación.
 * @param onBack acción de atrás: vuelve al feed.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (PublicationCategory?) -> Unit,
    onFromDateChange: (String) -> Unit,
    onToDateChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onPublicationClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SearchHeader(onBack = onBack)
        SearchFilterBar(
            query = state.query,
            category = state.category,
            fromDate = state.fromDate,
            toDate = state.toDate,
            onQueryChange = onQueryChange,
            onCategorySelected = onCategorySelected,
            onFromDateChange = onFromDateChange,
            onToDateChange = onToDateChange,
            onSubmit = onSubmit,
            onClearFilters = onClearFilters,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SearchResultArea(
                result = state.result,
                onRetry = onRetry,
                onPublicationClick = onPublicationClick,
            )
        }
    }
}

/**
 * Encabezado de la pantalla: botón de atrás y título.
 *
 * @param onBack acción de atrás.
 */
@Composable
private fun SearchHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(Res.string.search_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Área de resultado: pinta la vista correspondiente al estado de la búsqueda.
 *
 * @param result estado del resultado.
 * @param onRetry acción de reintento.
 * @param onPublicationClick acción al pulsar una tarjeta.
 */
@Composable
private fun SearchResultArea(
    result: SearchResultState,
    onRetry: () -> Unit,
    onPublicationClick: (String) -> Unit,
) {
    when (result) {
        SearchResultState.Idle -> SearchIdleView()
        SearchResultState.Loading -> SearchLoadingView()
        is SearchResultState.Content -> SearchResultsContent(
            publications = result.publications,
            onPublicationClick = onPublicationClick,
        )
        SearchResultState.Empty -> SearchEmptyView()
        SearchResultState.Error -> SearchErrorView(onRetry = onRetry)
    }
}

/**
 * Lista de resultados: una tarjeta reutilizada ([PublicationCard]) por publicación,
 * con scroll propio.
 *
 * @param publications resultados a mostrar.
 * @param onPublicationClick acción al pulsar una tarjeta.
 */
@Composable
private fun SearchResultsContent(
    publications: List<Publication>,
    onPublicationClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        publications.forEach { publication ->
            PublicationCard(
                publication = publication,
                onClick = { onPublicationClick(publication.id) },
            )
        }
    }
}

/** Estado de ejemplo de las `@Preview`, con la entrada del usuario rellena. */
private fun previewSearchUiState(result: SearchResultState): SearchUiState = SearchUiState(
    query = "becas para jóvenes",
    category = PublicationCategory.GRANTS_AND_SUBSIDIES,
    fromDate = "2026-01-01",
    toDate = "2026-12-31",
    result = result,
)

/** Previsualización de la pantalla en estado inicial. */
@Preview
@Composable
private fun SearchScreenIdlePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchScreen(
                state = previewSearchUiState(SearchResultState.Idle),
                onQueryChange = {},
                onCategorySelected = {},
                onFromDateChange = {},
                onToDateChange = {},
                onSubmit = {},
                onRetry = {},
                onClearFilters = {},
                onPublicationClick = {},
                onBack = {},
            )
        }
    }
}

/** Previsualización de la pantalla con resultados. */
@Preview
@Composable
private fun SearchScreenContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchScreen(
                state = previewSearchUiState(
                    SearchResultState.Content(previewPublications()),
                ),
                onQueryChange = {},
                onCategorySelected = {},
                onFromDateChange = {},
                onToDateChange = {},
                onSubmit = {},
                onRetry = {},
                onClearFilters = {},
                onPublicationClick = {},
                onBack = {},
            )
        }
    }
}

/** Previsualización del encabezado aislado. */
@Preview
@Composable
private fun SearchHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchHeader(onBack = {})
        }
    }
}

/** Previsualización del área de resultado en estado «sin resultados». */
@Preview
@Composable
private fun SearchResultAreaPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchResultArea(
                result = SearchResultState.Empty,
                onRetry = {},
                onPublicationClick = {},
            )
        }
    }
}

/** Previsualización de la lista de resultados aislada. */
@Preview
@Composable
private fun SearchResultsContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchResultsContent(
                publications = previewPublications(),
                onPublicationClick = {},
            )
        }
    }
}
