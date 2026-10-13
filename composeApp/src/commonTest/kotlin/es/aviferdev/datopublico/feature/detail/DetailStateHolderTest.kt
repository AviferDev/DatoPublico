package es.aviferdev.datopublico.feature.detail

import es.aviferdev.datopublico.feature.detail.data.DetailRepository
import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailMode
import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailRepository
import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult
import es.aviferdev.datopublico.feature.detail.domain.GetPublicationDetail
import es.aviferdev.datopublico.feature.detail.ui.DetailStateHolder
import es.aviferdev.datopublico.feature.detail.ui.DetailUiState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests del holder de estado del detalle: la traducción del resultado tipado del
 * caso de uso a los cuatro estados de la pantalla y el paso por `Loading`.
 *
 * Corren en `:composeApp:jvmTest` (y en Android/iOS) con `runTest` y repositorios
 * dobles; no abren sockets ni tocan la base de datos.
 */
class DetailStateHolderTest {
    @Test
    fun initialStateIsLoading() {
        val holder = holderFor(sampleRepository())

        assertEquals(DetailUiState.Loading, holder.state)
    }

    @Test
    fun loadWithSampleEndsInContent() = runTest {
        val holder = holderFor(sampleRepository())

        holder.load()

        assertIs<DetailUiState.Content>(holder.state)
    }

    @Test
    fun loadWithNotFoundModeEndsInNotFound() = runTest {
        val holder = holderFor(repositoryFor(InMemoryDetailMode.NOT_FOUND))

        holder.load()

        assertEquals(DetailUiState.NotFound, holder.state)
    }

    @Test
    fun loadWithUnknownIdEndsInNotFound() = runTest {
        val holder = holderFor(sampleRepository(), id = "BOE-A-2026-99999")

        holder.load()

        assertEquals(DetailUiState.NotFound, holder.state)
    }

    @Test
    fun loadWithUnavailableRepositoryEndsInError() = runTest {
        val holder = holderFor(repositoryFor(InMemoryDetailMode.UNAVAILABLE))

        holder.load()

        assertEquals(DetailUiState.Error, holder.state)
    }

    @Test
    fun loadPassesThroughLoadingBeforeRequestingData() = runTest {
        var holder: DetailStateHolder? = null
        var observedState: DetailUiState? = null
        val repository = object : DetailRepository {
            override suspend fun load(id: String): DetailLoadResult {
                observedState = holder?.state
                return DetailLoadResult.NotFound
            }
        }
        val createdHolder = holderFor(repository)
        holder = createdHolder

        createdHolder.load()

        assertEquals(DetailUiState.Loading, observedState)
        assertEquals(DetailUiState.NotFound, createdHolder.state)
    }

    @Test
    fun retryAfterFailureEndsInContent() = runTest {
        val holder = holderFor(FailOnceDetailRepository(delegate = sampleRepository()))

        holder.load()
        assertEquals(DetailUiState.Error, holder.state)

        holder.load()
        assertIs<DetailUiState.Content>(holder.state)
    }

    /** Holder construido sobre el repositorio indicado y el id de la convocatoria. */
    private fun holderFor(
        repository: DetailRepository,
        id: String = CONVOCATION_ID,
    ): DetailStateHolder = DetailStateHolder(id = id, getPublicationDetail = GetPublicationDetail(repository))

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): DetailRepository =
        repositoryFor(InMemoryDetailMode.SAMPLE)

    /** Repositorio en memoria en el modo indicado, sin latencia. */
    private fun repositoryFor(mode: InMemoryDetailMode): DetailRepository =
        InMemoryDetailRepository(mode = mode, latencyMillis = 0)

    private companion object {
        /** Convocatoria de ejemplo con plazo. */
        const val CONVOCATION_ID = "BOE-A-2026-21002"
    }
}

/** Repositorio doble: falla la primera vez y delega en el real a partir de ahí. */
private class FailOnceDetailRepository(private val delegate: DetailRepository) : DetailRepository {
    private var attempts = 0

    override suspend fun load(id: String): DetailLoadResult {
        attempts += 1
        val result = if (attempts == 1) DetailLoadResult.Failure else delegate.load(id)
        return result
    }
}
