package es.aviferdev.datopublico.feature.search.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.toApiResult
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cliente de la API de **búsqueda** (FT00036).
 *
 * Llama a `GET {baseUrl}/v1/buscar` con la consulta obligatoria (`q`) y los
 * filtros **opcionales** (`categoria`, `desde`, `hasta`) más el tope (`limite`),
 * usando el [HttpClient] común. La traducción de la respuesta vive en
 * `core/network` ([toApiResult]); un fallo de transporte se traduce aquí a
 * [ApiFailure.Network] y [CancellationException] se **re-lanza**.
 *
 * No conoce el dominio: entrega el DTO de wire; el mapeo es [toSearchResults].
 *
 * @param httpClient cliente HTTP común (MockEngine en los tests).
 * @param config URL base y timeout de las peticiones.
 */
class SearchApi(
    private val httpClient: HttpClient,
    private val config: ApiConfig = ApiConfig(),
) {

    /**
     * Busca [query] con [filters] y un tope de [limit] resultados.
     *
     * @param query término de búsqueda (no vacío).
     * @param filters filtros opcionales; los ausentes no viajan en la query.
     * @param limit número máximo de resultados.
     * @return el DTO de resultados, o un fallo tipado si la red, el estado HTTP o
     *   el cuerpo fallan; nunca lanza (salvo cancelación).
     */
    suspend fun search(
        query: String,
        filters: SearchFilters,
        limit: Int,
    ): ApiResult<SearchResponseDto> {
        val response = try {
            httpClient.get(config.baseUrl + SEARCH_PATH) {
                parameter("q", query)
                filters.category?.let { category -> parameter("categoria", category.toWireToken()) }
                filters.fromDate?.let { fromDate -> parameter("desde", fromDate) }
                filters.toDate?.let { toDate -> parameter("hasta", toDate) }
                parameter("limite", limit)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        val result: ApiResult<SearchResponseDto> = if (response == null) {
            ApiResult.Failure(ApiFailure.Network)
        } else {
            response.toApiResult()
        }
        return result
    }

    companion object {
        /** Ruta versionada de la búsqueda. */
        const val SEARCH_PATH: String = "/v1/buscar"
    }
}
