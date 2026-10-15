package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
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
import es.aviferdev.datopublico.resources.cd_search_trust
import es.aviferdev.datopublico.resources.search_api_badge
import es.aviferdev.datopublico.resources.search_back
import es.aviferdev.datopublico.resources.search_card_link
import es.aviferdev.datopublico.resources.search_subtitle
import es.aviferdev.datopublico.resources.search_title
import es.aviferdev.datopublico.resources.search_trust_message
import es.aviferdev.datopublico.resources.search_trust_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** de la búsqueda: sub-cabecera de contenido (miga de
 * atrás, subtítulo y título con la insignia de la API), el panel de filtros
 * ([SearchFilterBar]) y el área de resultado según el estado. No hace IO ni
 * conoce el repositorio; recibe el estado y delega los eventos.
 *
 * El chasis global ya pinta la barra superior de **marca** y la barra inferior,
 * así que esta pantalla no los repite: solo aporta su sub-cabecera de contenido.
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SearchBreadcrumb(onBack = onBack)
            SearchTitleRow()
        }
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
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SearchResultArea(
                result = state.result,
                query = state.query,
                onRetry = onRetry,
                onPublicationClick = onPublicationClick,
            )
        }
    }
}

/**
 * Miga de navegación de la sub-cabecera: enlace de atrás («← Volver», en
 * `primary`) y el subtítulo separados por un punto tipográfico.
 *
 * @param onBack acción de atrás.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchBreadcrumb(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.search_back),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = stringResource(Res.string.search_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Fila del título: «Buscar en el BOE» a la izquierda y la insignia de la versión
 * de la API del BOE a la derecha.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchTitleRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(Res.string.search_api_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Área de resultado: pinta la vista correspondiente al estado de la búsqueda.
 *
 * @param result estado del resultado.
 * @param query consulta enviada, para el eco de la tira de resumen.
 * @param onRetry acción de reintento.
 * @param onPublicationClick acción al pulsar una tarjeta.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchResultArea(
    result: SearchResultState,
    query: String,
    onRetry: () -> Unit,
    onPublicationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (result) {
            SearchResultState.Idle -> SearchIdleView()
            SearchResultState.Loading -> SearchLoadingView()
            is SearchResultState.Content -> SearchResultsContent(
                query = query,
                publications = result.publications,
                onPublicationClick = onPublicationClick,
            )
            SearchResultState.Empty -> SearchEmptyView()
            SearchResultState.Error -> SearchErrorView(onRetry = onRetry)
        }
    }
}

/**
 * Lista de resultados dentro de un único scroll: la tira de resumen, una tarjeta
 * reutilizada ([PublicationCard]) por publicación (con la etiqueta de enlace de
 * la búsqueda) y, al final, la tarjeta de garantía de autenticidad.
 *
 * @param query consulta enviada, para el eco de la tira de resumen.
 * @param publications resultados a mostrar.
 * @param onPublicationClick acción al pulsar una tarjeta.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchResultsContent(
    query: String,
    publications: List<Publication>,
    onPublicationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchResultsSummary(count = publications.size, query = query)
        publications.forEach { publication ->
            PublicationCard(
                publication = publication,
                onClick = { onPublicationClick(publication.id) },
                sourceLinkLabel = Res.string.search_card_link,
            )
        }
        SearchTrustCard()
    }
}

/**
 * Tarjeta de garantía: recuerda la procedencia oficial de los extractos y el
 * Código Seguro de Verificación. Usa el tono `surfaceContainerLow`.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchTrustCard(modifier: Modifier = Modifier) {
    val contentColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(Res.string.cd_search_trust),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.search_trust_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Text(
                    text = stringResource(Res.string.search_trust_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Estado de ejemplo de las `@Preview`, con la entrada del usuario rellena. */
private fun previewSearchUiState(result: SearchResultState): SearchUiState = SearchUiState(
    query = "becas para jóvenes",
    category = PublicationCategory.GRANTS_AND_SUBSIDIES,
    fromDate = "2026-10-01",
    toDate = "2026-10-13",
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

/** Previsualización de la miga de navegación. */
@Preview
@Composable
private fun SearchBreadcrumbPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchBreadcrumb(onBack = {}, modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la fila del título con la insignia de la API. */
@Preview
@Composable
private fun SearchTitleRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchTitleRow(modifier = Modifier.padding(8.dp))
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
                query = "becas para jóvenes",
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
                query = "becas para jóvenes",
                publications = previewPublications(),
                onPublicationClick = {},
            )
        }
    }
}

/** Previsualización de la tarjeta de garantía de autenticidad. */
@Preview
@Composable
private fun SearchTrustCardPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchTrustCard(modifier = Modifier.padding(8.dp))
        }
    }
}
