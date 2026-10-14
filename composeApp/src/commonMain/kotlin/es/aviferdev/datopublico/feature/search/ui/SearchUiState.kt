package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.runtime.Immutable
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory

/**
 * Estado de la pantalla de búsqueda (UDF: estado inmutable que la UI sólo lee).
 *
 * Reúne en un único valor la **entrada** del usuario (consulta y filtros) y el
 * **resultado** de la última búsqueda. Lo expone `SearchViewModel` como
 * `StateFlow` y lo pinta `SearchScreen`.
 *
 * @property query consulta escrita por el usuario.
 * @property category categoría seleccionada; `null` significa «Todas».
 * @property fromDate fecha `desde` en crudo (ISO si es válida); cadena vacía si no
 *   se ha indicado.
 * @property toDate fecha `hasta` en crudo (ISO si es válida); cadena vacía si no
 *   se ha indicado.
 * @property result estado del resultado de la última búsqueda.
 */
@Immutable
data class SearchUiState(
    val query: String = "",
    val category: PublicationCategory? = null,
    val fromDate: String = "",
    val toDate: String = "",
    val result: SearchResultState = SearchResultState.Idle,
)

/**
 * Resultado de la búsqueda dentro de [SearchUiState].
 *
 * Modela los cinco estados de la pantalla como valores (sin excepciones):
 * inicial, carga, contenido, «sin resultados» y error.
 */
sealed interface SearchResultState {
    /** Aún no se ha buscado: se muestra la invitación a buscar. */
    data object Idle : SearchResultState

    /** Se está buscando: se muestra el indicador de carga. */
    data object Loading : SearchResultState

    /** Hay resultados que mostrar. */
    data class Content(val publications: List<Publication>) : SearchResultState

    /** La búsqueda no encontró resultados: estado «sin resultados». */
    data object Empty : SearchResultState

    /** La búsqueda falló; se ofrece reintentar. */
    data object Error : SearchResultState
}
