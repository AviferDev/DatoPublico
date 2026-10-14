package es.aviferdev.datopublico.feature.search.domain

import es.aviferdev.datopublico.feature.search.data.SearchRepository

/**
 * Caso de uso de la **búsqueda**: único camino por el que la UI (a través del
 * `SearchViewModel`) pide resultados al repositorio.
 *
 * @param repository puerto de datos de la búsqueda.
 */
class SearchPublications(private val repository: SearchRepository) {

    /**
     * Ejecuta la búsqueda de [query] con [filters].
     *
     * Es *fail-fast* si la consulta está en blanco (espeja el `400` del endpoint):
     * el ViewModel **nunca** llama con consulta en blanco, así que no es un camino
     * de error de la app sino una guarda de programación.
     *
     * @param query término de búsqueda; no puede estar en blanco.
     * @param filters filtros opcionales de categoría y rango de fechas.
     * @return los resultados o un fallo tipado; nunca propaga excepciones de red
     *   a la UI.
     * @throws IllegalArgumentException si [query] está en blanco.
     */
    suspend operator fun invoke(query: String, filters: SearchFilters): SearchLoadResult {
        require(query.isNotBlank()) { "La consulta de búsqueda no puede estar en blanco." }
        return repository.search(query.trim(), filters)
    }
}
