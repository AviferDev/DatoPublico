package es.aviferdev.datopublico.feature.feed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel del feed diario: expone el estado de la pantalla como un
 * [StateFlow] inmutable (UDF) que la UI sólo lee.
 *
 * Sustituye al holder local `FeedStateHolder` de FT00032: la lógica de carga es
 * la misma (un disparador que pasa por [FeedUiState.Loading] y traduce el
 * resultado tipado de [GetDailyFeed]), pero ahora vive en un ViewModel
 * multiplataforma y el reintento es un **evento** ([onRetry]) y no estado
 * mutable expuesto.
 *
 * Se usa `SharingStarted.Eagerly` (y no `WhileSubscribed`) porque la fuente es
 * **caliente** (repositorio en memoria): el flujo debe seguir vivo aunque la UI
 * deje de recolectarlo, para que al volver del detalle el feed conserve su
 * estado ya cargado sin volver a [FeedUiState.Loading]. Cuando FT00035 exponga
 * un flujo frío/cacheado, se reevaluará (ver ADR 0031).
 *
 * @param getDailyFeed caso de uso que obtiene el feed; la UI nunca llama al
 *   repositorio directamente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(private val getDailyFeed: GetDailyFeed) : ViewModel() {
    /** Disparador de recarga: cada emisión lanza una carga nueva. */
    private val reloadTrigger = MutableStateFlow(0)

    /**
     * Estado actual de la pantalla; la UI lo recolecta con
     * `collectAsStateWithLifecycle()` y nunca lo muta.
     */
    val uiState: StateFlow<FeedUiState> = reloadTrigger
        .flatMapLatest { loadStates() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = FeedUiState.Loading,
        )

    /** Evento de reintento: vuelve a lanzar la carga tras un fallo. */
    fun onRetry() {
        reloadTrigger.value += 1
    }

    /** Flujo de carga: emite [FeedUiState.Loading] y luego el estado resultante. */
    private fun loadStates(): Flow<FeedUiState> = flow {
        emit(FeedUiState.Loading)
        emit(stateFor(getDailyFeed()))
    }

    /** Traduce el resultado tipado del caso de uso a un estado de la pantalla. */
    private fun stateFor(result: FeedLoadResult): FeedUiState = when (result) {
        is FeedLoadResult.Success -> stateFor(result.feed)
        FeedLoadResult.Failure -> FeedUiState.Error
    }

    /**
     * Traduce el feed recibido al estado de contenido o vacío: un día sin
     * publicaciones ni destacados es [FeedUiState.Empty], no una lista vacía sin
     * explicación.
     *
     * @param feed feed devuelto por el caso de uso.
     * @return [FeedUiState.Empty] si no hay nada que mostrar, o
     *   [FeedUiState.Content] en caso contrario.
     */
    private fun stateFor(feed: DailyFeed): FeedUiState {
        val isEmpty = feed.publications.isEmpty() && feed.highlights.isEmpty()
        return if (isEmpty) FeedUiState.Empty else FeedUiState.Content(feed)
    }
}

/**
 * Factory **manual** del [FeedViewModel] (DI por constructor, sin framework):
 * construye el caso de uso sobre el repositorio indicado. El *composition root*
 * (`App()`) inyecta el repositorio HTTP real (FT00035); el valor por defecto
 * (repositorio en memoria de ejemplo) sirve a `@Preview` y a los tests.
 *
 * @param getDailyFeed caso de uso del feed a usar; por defecto, datos de ejemplo.
 * @return factory lista para `viewModel(factory = …)`.
 */
fun feedViewModelFactory(
    getDailyFeed: GetDailyFeed = GetDailyFeed(InMemoryFeedRepository()),
): ViewModelProvider.Factory = viewModelFactory {
    initializer { FeedViewModel(getDailyFeed) }
}
