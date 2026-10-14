package es.aviferdev.datopublico.feature.detail.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.toApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cliente de la API del **detalle de publicación** (FT00035).
 *
 * Llama a `GET {baseUrl}/v1/publicacion/{id}` con el [HttpClient] común y traduce
 * el resultado a un [ApiResult] **tipado**: la traducción de la respuesta vive en
 * `core/network` ([toApiResult]) y se reutiliza entre clientes; un fallo de
 * transporte se traduce aquí a [ApiFailure.Network] y [CancellationException] se
 * **re-lanza** para no romper la cancelación de corrutinas.
 *
 * No conoce el dominio: entrega el DTO de wire; el mapeo es [toPublicationDetail].
 *
 * @param httpClient cliente HTTP común (MockEngine en los tests).
 * @param config URL base y timeout de las peticiones.
 */
class DetailApi(
    private val httpClient: HttpClient,
    private val config: ApiConfig = ApiConfig(),
) {

    /**
     * Pide el detalle de la publicación [id].
     *
     * @param id identificador oficial del BOE.
     * @return el DTO de detalle, o un fallo tipado si la red, el estado HTTP
     *   (p. ej. `404`) o el cuerpo fallan; nunca lanza (salvo cancelación).
     */
    suspend fun detail(id: String): ApiResult<DetailPublicationDto> {
        val response = try {
            httpClient.get(config.baseUrl + DETAIL_PATH + "/" + id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        val result: ApiResult<DetailPublicationDto> = if (response == null) {
            ApiResult.Failure(ApiFailure.Network)
        } else {
            response.toApiResult()
        }
        return result
    }

    companion object {
        /** Ruta versionada del detalle de publicación. */
        const val DETAIL_PATH: String = "/v1/publicacion"
    }
}
