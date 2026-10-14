package es.aviferdev.datopublico.feature.search

import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.data.SearchRepository
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult
import es.aviferdev.datopublico.feature.search.domain.SearchPublications
import es.aviferdev.datopublico.feature.search.domain.SearchResults
import es.aviferdev.datopublico.feature.search.ui.SearchResultState
import es.aviferdev.datopublico.feature.search.ui.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests del `SearchViewModel`: el estado inicial, que la consulta en blanco no
 * llama al repositorio, el paso por `Loading` hasta `Content`/`Empty`/`Error`, el
 * reintento de la última búsqueda, la omisión de fechas inválidas y la limpieza de
 * filtros.
 *
 * `viewModelScope` usa el dispatcher `Main`, así que se sustituye por un
 * `StandardTestDispatcher` (y se restaura al terminar). Corren en
 * `:composeApp:jvmTest` con repositorios dobles; no abren sockets ni tocan la
 * base de datos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() {
        val viewModel = viewModelWith(SampleSearchRepository())

        assertEquals(SearchResultState.Idle, viewModel.uiState.value.result)
    }

    @Test
    fun blankQuerySubmitKeepsIdleAndDoesNotSearch() = runTest(mainDispatcher) {
        val repository = CountingSearchRepository(SampleSearchRepository())
        val viewModel = viewModelWith(repository)
        viewModel.onQueryChange("   ")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(0, repository.searches)
        assertEquals(SearchResultState.Idle, viewModel.uiState.value.result)
    }

    @Test
    fun validSubmitEndsInContent() = runTest(mainDispatcher) {
        val viewModel = viewModelWith(SampleSearchRepository())
        viewModel.onQueryChange("becas")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertIs<SearchResultState.Content>(viewModel.uiState.value.result)
    }

    @Test
    fun emptyResultsEndInEmpty() = runTest(mainDispatcher) {
        val viewModel = viewModelWith(EmptySearchRepository())
        viewModel.onQueryChange("nada")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(SearchResultState.Empty, viewModel.uiState.value.result)
    }

    @Test
    fun failureEndsInError() = runTest(mainDispatcher) {
        val viewModel = viewModelWith(FailingSearchRepository())
        viewModel.onQueryChange("becas")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(SearchResultState.Error, viewModel.uiState.value.result)
    }

    @Test
    fun submitPassesThroughLoadingBeforeRequestingData() = runTest(mainDispatcher) {
        var observedState: SearchResultState? = null
        lateinit var viewModel: SearchViewModel
        val repository = object : SearchRepository {
            override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult {
                observedState = viewModel.uiState.value.result
                return SearchLoadResult.Success(SearchResults(query = query, publications = emptyList()))
            }
        }
        viewModel = viewModelWith(repository)
        viewModel.onQueryChange("becas")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(SearchResultState.Loading, observedState)
    }

    @Test
    fun retryRepeatsLastSearchAndEndsInContent() = runTest(mainDispatcher) {
        val viewModel = viewModelWith(FailOnceSearchRepository(SampleSearchRepository()))
        viewModel.onQueryChange("becas")

        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(SearchResultState.Error, viewModel.uiState.value.result)

        viewModel.onRetry()
        advanceUntilIdle()

        assertIs<SearchResultState.Content>(viewModel.uiState.value.result)
    }

    @Test
    fun invalidDatesAreOmittedFromTheFilters() = runTest(mainDispatcher) {
        val repository = CapturingSearchRepository()
        val viewModel = viewModelWith(repository)
        viewModel.onQueryChange("becas")
        viewModel.onFromDateChange("01/01/2026")
        viewModel.onToDateChange("2026-12-31")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertNull(repository.filters?.fromDate)
        assertEquals("2026-12-31", repository.filters?.toDate)
    }

    @Test
    fun clearFiltersResetsCategoryAndDatesButKeepsQuery() {
        val viewModel = viewModelWith(SampleSearchRepository())
        viewModel.onQueryChange("becas")
        viewModel.onCategorySelected(PublicationCategory.GRANTS_AND_SUBSIDIES)
        viewModel.onFromDateChange("2026-01-01")
        viewModel.onToDateChange("2026-12-31")

        viewModel.onClearFilters()

        val state = viewModel.uiState.value
        assertNull(state.category)
        assertEquals("", state.fromDate)
        assertEquals("", state.toDate)
        assertEquals("becas", state.query)
    }

    /** ViewModel sobre el caso de uso con el repositorio indicado. */
    private fun viewModelWith(repository: SearchRepository): SearchViewModel =
        SearchViewModel(SearchPublications(repository))
}

/** Repositorio doble: devuelve siempre un resultado con una publicación. */
private class SampleSearchRepository : SearchRepository {
    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult =
        SearchLoadResult.Success(
            SearchResults(query = query, publications = listOf(samplePublication())),
        )
}

/** Repositorio doble: devuelve siempre un resultado sin publicaciones. */
private class EmptySearchRepository : SearchRepository {
    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult =
        SearchLoadResult.Success(SearchResults(query = query, publications = emptyList()))
}

/** Repositorio doble: devuelve siempre un fallo tipado. */
private class FailingSearchRepository : SearchRepository {
    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult =
        SearchLoadResult.Failure
}

/** Repositorio doble que cuenta cuántas búsquedas se le pidieron. */
private class CountingSearchRepository(private val delegate: SearchRepository) : SearchRepository {
    var searches = 0

    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult {
        searches += 1
        return delegate.search(query, filters)
    }
}

/** Repositorio doble: falla la primera vez y delega en el real a partir de ahí. */
private class FailOnceSearchRepository(private val delegate: SearchRepository) : SearchRepository {
    private var attempts = 0

    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult {
        attempts += 1
        val result = if (attempts == 1) SearchLoadResult.Failure else delegate.search(query, filters)
        return result
    }
}

/** Repositorio doble que captura los filtros con los que se le llamó. */
private class CapturingSearchRepository : SearchRepository {
    var filters: SearchFilters? = null

    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult {
        this.filters = filters
        return SearchLoadResult.Success(SearchResults(query = query, publications = emptyList()))
    }
}

/** Publicación de ejemplo para los repositorios dobles. */
private fun samplePublication(): Publication = Publication(
    id = "BOE-A-2026-31001",
    title = "Becas para jóvenes investigadores.",
    category = PublicationCategory.GRANTS_AND_SUBSIDIES,
    publishedDate = "2026-09-20",
    shortSummary = "Becas de movilidad para jóvenes investigadores.",
    isHighlighted = false,
    highlightReasons = emptyList(),
    deadline = null,
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31001",
)
