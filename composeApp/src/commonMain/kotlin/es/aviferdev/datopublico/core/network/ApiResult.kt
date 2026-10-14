package es.aviferdev.datopublico.core.network

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resultado **tipado** de una llamada al cliente HTTP (FT00035).
 *
 * Modela el éxito y el fallo como **valores**, nunca como excepciones: la capa de
 * datos traduce cualquier incidencia a [Failure] con un motivo [ApiFailure], de
 * modo que un fallo de red o un cuerpo inválido se conviertan en estado de UI (o
 * en un respaldo de caché) y la app **nunca** se cierre.
 *
 * @param T tipo de dato devuelto en caso de éxito.
 */
sealed interface ApiResult<out T> {
    /** La llamada se completó y el cuerpo se decodificó ([data]). */
    data class Success<T>(val data: T) : ApiResult<T>

    /** La llamada no produjo un dato utilizable ([reason]). */
    data class Failure(val reason: ApiFailure) : ApiResult<Nothing>
}

/**
 * Motivo **tipado** de un fallo del cliente HTTP.
 *
 * Es un valor interno (no es DTO de transporte): describe la clase de fallo sin
 * exponer excepciones ni detalles del motor de red.
 */
sealed interface ApiFailure {
    /** Fallo de transporte: sin conexión, timeout o error del motor. */
    data object Network : ApiFailure

    /**
     * El servidor respondió un estado no-2xx.
     *
     * @property status código HTTP (p. ej. `400`, `404`, `429`, `500`, `502`).
     * @property code token `snake_case` de `ApiErrorCodeDto` si el cuerpo tipado
     *   se pudo leer; `null` si no.
     */
    data class Server(val status: Int, val code: String? = null) : ApiFailure

    /** La respuesta era 2xx pero el cuerpo no se pudo decodificar. */
    data object InvalidBody : ApiFailure
}

/**
 * Traducción **común** de una respuesta HTTP a un [ApiResult] tipado
 * (FT00035/FT00036).
 *
 * Factoriza el manejo compartido por los clientes de API del cliente (feed y
 * búsqueda) para no duplicarlo: un `2xx` decodifica el cuerpo; un estado no-2xx
 * intenta leer el cuerpo de error tipado ([ClientApiErrorDto]) y devuelve
 * [ApiFailure.Server]; un cuerpo ilegible, [ApiFailure.InvalidBody].
 * [CancellationException] se **re-lanza** para no romper la cancelación de
 * corrutinas (regla MUST «reutilizar antes de escribir»).
 *
 * @param T tipo del cuerpo decodificado en caso de éxito.
 * @return el cuerpo decodificado o un fallo tipado; nunca lanza salvo
 *   cancelación.
 */
internal suspend inline fun <reified T> HttpResponse.toApiResult(): ApiResult<T> {
    val result = if (status.isSuccess()) {
        try {
            ApiResult.Success(body<T>())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ApiResult.Failure(ApiFailure.InvalidBody)
        }
    } else {
        ApiResult.Failure(serverFailure())
    }
    return result
}

/**
 * Lee el cuerpo de error tipado de un estado no-2xx, tolerante a su ausencia.
 *
 * @return el fallo de servidor con el código HTTP y, si se pudo leer, el token
 *   `snake_case` del cuerpo tipado; `code` nulo en caso contrario.
 */
internal suspend fun HttpResponse.serverFailure(): ApiFailure {
    val code = try {
        body<ClientApiErrorDto>().code
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
    return ApiFailure.Server(status.value, code)
}
