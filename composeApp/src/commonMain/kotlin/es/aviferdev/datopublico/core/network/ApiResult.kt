package es.aviferdev.datopublico.core.network

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
