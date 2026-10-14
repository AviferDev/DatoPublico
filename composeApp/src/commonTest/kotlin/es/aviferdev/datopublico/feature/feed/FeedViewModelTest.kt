package es.aviferdev.datopublico.feature.feed

import es.aviferdev.datopublico.feature.feed.data.FeedRepository
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedMode
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.FeedUiState
import es.aviferdev.datopublico.feature.feed.ui.FeedViewModel
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

/**
 * Tests del `FeedViewModel`: la traducción del resultado tipado del caso de uso
 * a los cuatro estados de la pantalla, el paso por `Loading`, el reintento y la
 * conservación del estado (una sola carga).
 *
 * `viewModelScope` usa el dispatcher `Main`, así que se sustituye por un
 * `StandardTestDispatcher` (y se restaura al terminar). Corren en
 * `:composeApp:jvmTest` (y en Android/iOS) con repositorios dobles; no abren
 * sockets ni tocan la base de datos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
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
    fun initialStateIsLoading() {
        val viewModel = FeedViewModel(GetDailyFeed(sampleRepository()))

        assertEquals(FeedUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun loadWithSampleFeedEndsInContent() = runTest(mainDispatcher) {
        val viewModel = FeedViewModel(GetDailyFeed(sampleRepository()))

        advanceUntilIdle()

        assertIs<FeedUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun loadWithEmptyDayEndsInEmpty() = runTest(mainDispatcher) {
        val viewModel = FeedViewModel(GetDailyFeed(repositoryFor(InMemoryFeedMode.EMPTY)))

        advanceUntilIdle()

        assertIs<FeedUiState.Empty>(viewModel.uiState.value)
    }

    @Test
    fun loadWithUnavailableRepositoryEndsInError() = runTest(mainDispatcher) {
        val viewModel = FeedViewModel(GetDailyFeed(repositoryFor(InMemoryFeedMode.UNAVAILABLE)))

        advanceUntilIdle()

        assertEquals(FeedUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun loadPassesThroughLoadingBeforeRequestingData() = runTest(mainDispatcher) {
        var observedState: FeedUiState? = null
        lateinit var viewModel: FeedViewModel
        val repository = object : FeedRepository {
            override suspend fun load(): FeedLoadResult {
                observedState = viewModel.uiState.value
                return FeedLoadResult.Success(DailyFeed(date = "2026-10-13"))
            }
        }
        viewModel = FeedViewModel(GetDailyFeed(repository))

        advanceUntilIdle()

        assertEquals(FeedUiState.Loading, observedState)
        assertIs<FeedUiState.Empty>(viewModel.uiState.value)
    }

    @Test
    fun retryAfterFailureEndsInContent() = runTest(mainDispatcher) {
        val repository = FailOnceFeedRepository(delegate = sampleRepository())
        val viewModel = FeedViewModel(GetDailyFeed(repository))

        advanceUntilIdle()
        assertEquals(FeedUiState.Error, viewModel.uiState.value)

        viewModel.onRetry()
        advanceUntilIdle()

        assertIs<FeedUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun loadsOnceAndKeepsContentWithoutReload() = runTest(mainDispatcher) {
        val repository = CountingFeedRepository(delegate = sampleRepository())
        val viewModel = FeedViewModel(GetDailyFeed(repository))

        advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(1, repository.loads)
        assertIs<FeedUiState.Content>(viewModel.uiState.value)
    }

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): FeedRepository = repositoryFor(InMemoryFeedMode.SAMPLE)

    /** Repositorio en memoria en el modo indicado, sin latencia. */
    private fun repositoryFor(mode: InMemoryFeedMode): FeedRepository =
        InMemoryFeedRepository(mode = mode, latencyMillis = 0)
}

/** Repositorio doble: falla la primera vez y delega en el real a partir de ahí. */
private class FailOnceFeedRepository(private val delegate: FeedRepository) : FeedRepository {
    private var attempts = 0

    override suspend fun load(): FeedLoadResult {
        attempts += 1
        val result = if (attempts == 1) FeedLoadResult.Failure else delegate.load()
        return result
    }
}

/** Repositorio doble que cuenta cuántas veces se le pidió el feed. */
private class CountingFeedRepository(private val delegate: FeedRepository) : FeedRepository {
    var loads = 0

    override suspend fun load(): FeedLoadResult {
        loads += 1
        return delegate.load()
    }
}
