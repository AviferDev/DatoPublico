package es.aviferdev.datopublico.feature.detail.ui

import es.aviferdev.datopublico.feature.detail.domain.PublicationDetail

/**
 * Estado de la pantalla de detalle (UDF: estado inmutable que la UI sólo lee).
 *
 * FT00034 sustituirá el holder local por un ViewModel con `StateFlow`; de momento
 * el estado lo expone `DetailStateHolder` y lo pinta `DetailScreen`.
 */
sealed interface DetailUiState {
    /** Se está cargando el detalle. */
    data object Loading : DetailUiState

    /** Hay contenido que mostrar. */
    data class Content(val detail: PublicationDetail) : DetailUiState

    /** La publicación no existe o no está publicada. */
    data object NotFound : DetailUiState

    /** La carga falló; se ofrece reintentar. */
    data object Error : DetailUiState
}
