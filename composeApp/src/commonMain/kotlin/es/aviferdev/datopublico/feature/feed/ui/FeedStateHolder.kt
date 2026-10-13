package es.aviferdev.datopublico.feature.feed.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
import es.aviferdev.datopublico.feature.feed.domain.GetDailyFeed

/**
 * Holder **local** del estado del feed.
 *
 * Es deliberadamente una clase normal con `mutableStateOf` (sin `ViewModel`, sin
 * `StateFlow` ni navegación): FT00034 lo sustituirá por un ViewModel con
 * `StateFlow` y `stateIn`. Toda la lógica de carga pasa por el caso de uso
 * [GetDailyFeed]; el holder sólo traduce su resultado tipado a [FeedUiState].
 *
 * @param getDailyFeed caso de uso que obtiene el feed.
 */
class FeedStateHolder(private val getDailyFeed: GetDailyFeed) {
    /** Estado actual de la pantalla; la UI sólo lo lee. */
    var state: FeedUiState by mutableStateOf(FeedUiState.Loading)
        private set

    /**
     * Carga el feed pasando por [FeedUiState.Loading] y dejando el estado
     * resultante: [FeedUiState.Content], [FeedUiState.Empty] o
     * [FeedUiState.Error]. Es idempotente y sirve tanto para la carga inicial
     * como para el reintento.
     */
    suspend fun load() {
        state = FeedUiState.Loading
        state = when (val result = getDailyFeed()) {
            is FeedLoadResult.Success -> stateFor(result.feed)
            FeedLoadResult.Failure -> FeedUiState.Error
        }
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
        val state = if (isEmpty) FeedUiState.Empty else FeedUiState.Content(feed)
        return state
    }
}
