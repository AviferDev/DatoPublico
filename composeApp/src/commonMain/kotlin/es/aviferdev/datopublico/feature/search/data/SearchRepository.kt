package es.aviferdev.datopublico.feature.search.data

import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult

/**
 * Puerto de datos de la **búsqueda**.
 *
 * Es la frontera que implementa el cliente HTTP real ([HttpSearchRepository]) y,
 * para `@Preview`/tests, el repositorio en memoria ([InMemorySearchRepository]).
 * Devuelve un resultado **tipado** ([SearchLoadResult]) en vez de lanzar
 * excepciones, para que los fallos se modelen como estado de UI.
 */
interface SearchRepository {
    /**
     * Busca las publicaciones que casan con [query] y [filters].
     *
     * @param query término de búsqueda; no vacío (el caso de uso lo garantiza).
     * @param filters filtros opcionales de categoría y rango de fechas.
     * @return los resultados o un fallo tipado; nunca propaga excepciones a la
     *   UI.
     */
    suspend fun search(query: String, filters: SearchFilters): SearchLoadResult
}
