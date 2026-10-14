package es.aviferdev.datopublico.feature.detail.data

import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.feature.detail.data.remote.DetailApi
import es.aviferdev.datopublico.feature.detail.data.remote.toPublicationDetail
import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult

/**
 * Repositorio del detalle sobre el **cliente HTTP** real (FT00035).
 *
 * Implementa el puerto [DetailRepository]: pide el detalle a [api] y traduce el
 * [ApiResult] tipado a [DetailLoadResult]. Un `404` del servidor es
 * [DetailLoadResult.NotFound] (la publicación no existe o no está publicada);
 * cualquier otro fallo (red, `5xx`, cuerpo ilegible) es [DetailLoadResult.Failure].
 * **Nunca** propaga excepciones hacia la UI.
 *
 * @param api cliente de la API del detalle.
 */
class HttpDetailRepository(private val api: DetailApi) : DetailRepository {

    override suspend fun load(id: String): DetailLoadResult =
        when (val result = api.detail(id)) {
            is ApiResult.Success -> DetailLoadResult.Success(result.data.toPublicationDetail())
            is ApiResult.Failure -> result.reason.toDetailResult()
        }
}

/** Traduce el fallo del cliente al resultado de detalle: `404` ⇒ no encontrada. */
private fun ApiFailure.toDetailResult(): DetailLoadResult =
    if (this is ApiFailure.Server && status == HTTP_NOT_FOUND) {
        DetailLoadResult.NotFound
    } else {
        DetailLoadResult.Failure
    }

/** Código HTTP con el que el backend señala una publicación inexistente/no publicada. */
private const val HTTP_NOT_FOUND = 404
