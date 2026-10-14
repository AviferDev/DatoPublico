package es.aviferdev.datopublico.feature.feed.ui

import es.aviferdev.datopublico.feature.feed.domain.DailyFeed

/**
 * Estado de la pantalla del feed (UDF: estado inmutable que la UI sólo lee).
 *
 * Lo expone `FeedViewModel` como `StateFlow` (FT00034) y lo pinta `FeedScreen`.
 */
sealed interface FeedUiState {
    /** Se está cargando el feed. */
    data object Loading : FeedUiState

    /** Hay contenido que mostrar. */
    data class Content(val feed: DailyFeed) : FeedUiState

    /** El día no tiene publicaciones publicadas. */
    data object Empty : FeedUiState

    /** La carga falló; se ofrece reintentar. */
    data object Error : FeedUiState
}
