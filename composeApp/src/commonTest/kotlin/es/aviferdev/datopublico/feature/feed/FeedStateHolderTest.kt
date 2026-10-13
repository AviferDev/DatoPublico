package es.aviferdev.datopublico.feature.feed

import es.aviferdev.datopublico.feature.feed.data.FeedRepository
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedMode
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import es.aviferdev.datopublico.feature.feed.ui.FeedStateHolder
import es.aviferdev.datopublico.feature.feed.ui.FeedUiState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests del holder de estado del feed: la traducción del resultado tipado del
 * caso de uso a los cuatro estados de la pantalla y el paso por `Loading`.
 *
 * Corren en `:composeApp:jvmTest` (y en Android/iOS) con `runTest` y repositorios
 * dobles; no abren sockets ni tocan la base de datos.
 */
class FeedStateHolderTest {
    @Test
    fun initialStateIsLoading() {
        val holder = FeedStateHolder(GetDailyFeed(sampleRepository()))

        assertEquals(FeedUiState.Loading, holder.state)
    }

    @Test
    fun loadWithSampleFeedEndsInContent() = runTest {
        val holder = FeedStateHolder(GetDailyFeed(sampleRepository()))

        holder.load()

        assertIs<FeedUiState.Content>(holder.state)
    }

    @Test
    fun loadWithEmptyDayEndsInEmpty() = runTest {
        val holder = FeedStateHolder(GetDailyFeed(repositoryFor(InMemoryFeedMode.EMPTY)))

        holder.load()

        assertIs<FeedUiState.Empty>(holder.state)
    }

    @Test
    fun loadWithUnavailableRepositoryEndsInError() = runTest {
        val holder = FeedStateHolder(GetDailyFeed(repositoryFor(InMemoryFeedMode.UNAVAILABLE)))

        holder.load()

        assertEquals(FeedUiState.Error, holder.state)
    }

    @Test
    fun loadPassesThroughLoadingBeforeRequestingData() = runTest {
        var holder: FeedStateHolder? = null
        var observedState: FeedUiState? = null
        val repository = object : FeedRepository {
            override suspend fun load(): FeedLoadResult {
                observedState = holder?.state
                return FeedLoadResult.Success(DailyFeed(date = "2026-10-13"))
            }
        }
        val createdHolder = FeedStateHolder(GetDailyFeed(repository))
        holder = createdHolder

        createdHolder.load()

        assertEquals(FeedUiState.Loading, observedState)
        assertIs<FeedUiState.Empty>(createdHolder.state)
    }

    @Test
    fun retryAfterFailureEndsInContent() = runTest {
        val repository = FailOnceFeedRepository(delegate = sampleRepository())
        val holder = FeedStateHolder(GetDailyFeed(repository))

        holder.load()
        assertEquals(FeedUiState.Error, holder.state)

        holder.load()
        assertIs<FeedUiState.Content>(holder.state)
    }

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): FeedRepository =
        repositoryFor(InMemoryFeedMode.SAMPLE)

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
