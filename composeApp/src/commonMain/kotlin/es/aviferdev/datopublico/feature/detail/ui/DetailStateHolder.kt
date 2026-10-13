package es.aviferdev.datopublico.feature.detail.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult
import es.aviferdev.datopublico.feature.detail.domain.GetPublicationDetail

/**
 * Holder **local** del estado de la pantalla de detalle.
 *
 * Es deliberadamente una clase normal con `mutableStateOf` (sin `ViewModel`, sin
 * `StateFlow` ni navegación): FT00034 lo sustituirá por un ViewModel con
 * `StateFlow` y `stateIn`. Toda la lógica de carga pasa por el caso de uso
 * [GetPublicationDetail]; el holder sólo traduce su resultado tipado a
 * [DetailUiState].
 *
 * @param id identificador oficial de la publicación a mostrar.
 * @param getPublicationDetail caso de uso que obtiene el detalle.
 */
class DetailStateHolder(
    private val id: String,
    private val getPublicationDetail: GetPublicationDetail,
) {
    /** Estado actual de la pantalla; la UI sólo lo lee. */
    var state: DetailUiState by mutableStateOf(DetailUiState.Loading)
        private set

    /**
     * Carga el detalle pasando por [DetailUiState.Loading] y dejando el estado
     * resultante: [DetailUiState.Content], [DetailUiState.NotFound] o
     * [DetailUiState.Error]. Es idempotente y sirve tanto para la carga inicial
     * como para el reintento.
     */
    suspend fun load() {
        state = DetailUiState.Loading
        state = when (val result = getPublicationDetail(id)) {
            is DetailLoadResult.Success -> DetailUiState.Content(result.detail)
            DetailLoadResult.NotFound -> DetailUiState.NotFound
            DetailLoadResult.Failure -> DetailUiState.Error
        }
    }
}
