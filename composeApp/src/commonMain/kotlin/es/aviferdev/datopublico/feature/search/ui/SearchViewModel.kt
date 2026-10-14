package es.aviferdev.datopublico.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.data.InMemorySearchRepository
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult
import es.aviferdev.datopublico.feature.search.domain.SearchPublications
import es.aviferdev.datopublico.feature.search.domain.SearchResults
import es.aviferdev.datopublico.feature.search.domain.isIsoDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la búsqueda: expone el estado de la pantalla como un
 * [StateFlow] inmutable (UDF) que la UI sólo lee.
 *
 * La entrada del usuario (consulta y filtros) es **imperativa**, así que el estado
 * se modela con un `MutableStateFlow` expuesto como `StateFlow` (y no con
 * `stateIn(...)`, que encaja con fuentes derivadas; ver ADR 0031). Los eventos son
 * métodos ([onSubmit], [onRetry]…), nunca estado mutable expuesto.
 *
 * Reglas de seguridad: una consulta en blanco **no** llama al repositorio (se
 * queda en [SearchResultState.Idle]); las fechas que no son ISO se **omiten** de
 * los filtros sin provocar un error; un fallo llega **tipado** y se muestra como
 * [SearchResultState.Error] con reintento.
 *
 * @param searchPublications caso de uso de la búsqueda; la UI nunca llama al
 *   repositorio directamente.
 */
class SearchViewModel(private val searchPublications: SearchPublications) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())

    /**
     * Estado actual de la pantalla; la UI lo recolecta con
     * `collectAsStateWithLifecycle()` y nunca lo muta.
     */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Última búsqueda lanzada, para repetirla en [onRetry]. */
    private var lastSearch: SearchRequest? = null

    /** Evento: cambia la consulta escrita. */
    fun onQueryChange(query: String) {
        _uiState.update { state -> state.copy(query = query) }
    }

    /** Evento: selecciona una categoría (o «Todas» con `null`). */
    fun onCategorySelected(category: PublicationCategory?) {
        _uiState.update { state -> state.copy(category = category) }
    }

    /** Evento: cambia la fecha `desde`. */
    fun onFromDateChange(fromDate: String) {
        _uiState.update { state -> state.copy(fromDate = fromDate) }
    }

    /** Evento: cambia la fecha `hasta`. */
    fun onToDateChange(toDate: String) {
        _uiState.update { state -> state.copy(toDate = toDate) }
    }

    /** Evento: limpia los filtros (categoría y fechas), sin tocar la consulta. */
    fun onClearFilters() {
        _uiState.update { state -> state.copy(category = null, fromDate = "", toDate = "") }
    }

    /**
     * Evento: envía la búsqueda. Con la consulta en blanco no llama al repositorio
     * y mantiene el estado inicial.
     */
    fun onSubmit() {
        val state = _uiState.value
        if (state.query.isBlank()) {
            _uiState.update { current -> current.copy(result = SearchResultState.Idle) }
        } else {
            launchSearch(SearchRequest(query = state.query, filters = filtersOf(state)))
        }
    }

    /** Evento: repite la **última** búsqueda (tras un error). */
    fun onRetry() {
        lastSearch?.let { request -> launchSearch(request) }
    }

    /** Pasa a `Loading` y lanza la búsqueda en el `viewModelScope`. */
    private fun launchSearch(request: SearchRequest) {
        lastSearch = request
        _uiState.update { state -> state.copy(result = SearchResultState.Loading) }
        viewModelScope.launch {
            val result = searchPublications(request.query, request.filters)
            applyResult(result)
        }
    }

    /** Traduce el resultado tipado del caso de uso al estado de la pantalla. */
    private suspend fun applyResult(result: SearchLoadResult) {
        val next = when (result) {
            is SearchLoadResult.Success -> resultFor(result.results)
            SearchLoadResult.Failure -> SearchResultState.Error
        }
        _uiState.update { state -> state.copy(result = next) }
    }

    /**
     * Construye los filtros a enviar: la categoría y solo las fechas que son
     * ISO-8601 válidas (las inválidas se **omiten**, sin enviarse).
     */
    private fun filtersOf(state: SearchUiState): SearchFilters = SearchFilters(
        category = state.category,
        fromDate = state.fromDate.takeIf { value -> isIsoDate(value) },
        toDate = state.toDate.takeIf { value -> isIsoDate(value) },
    )

    /**
     * Traduce los resultados recibidos al estado de contenido o «sin resultados»:
     * una búsqueda sin coincidencias es [SearchResultState.Empty], no una lista
     * vacía sin explicación.
     */
    private fun resultFor(results: SearchResults): SearchResultState {
        val state = if (results.publications.isEmpty()) {
            SearchResultState.Empty
        } else {
            SearchResultState.Content(results.publications)
        }
        return state
    }
}

/**
 * Consulta y filtros de una búsqueda, para poder repetirla en el reintento.
 *
 * @property query término de búsqueda.
 * @property filters filtros ya saneados (fechas ISO u omitidas).
 */
private data class SearchRequest(val query: String, val filters: SearchFilters)

/**
 * Factory **manual** del [SearchViewModel] (DI por constructor, sin framework):
 * construye el caso de uso sobre el repositorio indicado. El *composition root*
 * (`App()`) inyecta el repositorio HTTP real ([es.aviferdev.datopublico.feature.search.data.HttpSearchRepository]);
 * el valor por defecto (repositorio en memoria de ejemplo) sirve a `@Preview` y a
 * los tests.
 *
 * @param searchPublications caso de uso de la búsqueda a usar; por defecto, datos
 *   de ejemplo.
 * @return factory lista para `viewModel(factory = …)`.
 */
fun searchViewModelFactory(
    searchPublications: SearchPublications = SearchPublications(InMemorySearchRepository()),
): ViewModelProvider.Factory = viewModelFactory {
    initializer { SearchViewModel(searchPublications) }
}
