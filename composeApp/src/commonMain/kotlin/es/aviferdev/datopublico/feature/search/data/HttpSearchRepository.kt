package es.aviferdev.datopublico.feature.search.data

import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.feature.search.data.remote.SearchApi
import es.aviferdev.datopublico.feature.search.data.remote.toSearchResults
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult

/**
 * Repositorio de la búsqueda sobre el **cliente HTTP** (FT00036).
 *
 * Implementa el puerto [SearchRepository]: pide los resultados a [searchApi] con
 * la consulta y los filtros; en **éxito** mapea el DTO a dominio y devuelve
 * [SearchLoadResult.Success]; en **fallo** devuelve [SearchLoadResult.Failure]
 * (la UI lo muestra como estado de error con reintento). Nunca lanza excepciones
 * hacia la UI.
 *
 * @param searchApi cliente de la API de búsqueda.
 * @param limit tope de resultados por petición (default del endpoint).
 */
class HttpSearchRepository(
    private val searchApi: SearchApi,
    private val limit: Int = DEFAULT_LIMIT,
) : SearchRepository {

    /**
     * Busca [query] con [filters] y mapea el resultado a dominio.
     *
     * @return los resultados, o [SearchLoadResult.Failure] si la llamada falla.
     */
    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult {
        val result = when (val apiResult = searchApi.search(query, filters, limit)) {
            is ApiResult.Success -> SearchLoadResult.Success(apiResult.data.toSearchResults())
            is ApiResult.Failure -> SearchLoadResult.Failure
        }
        return result
    }

    companion object {
        /** Tope de resultados por petición (default del endpoint `/v1/buscar`). */
        const val DEFAULT_LIMIT: Int = 10
    }
}
