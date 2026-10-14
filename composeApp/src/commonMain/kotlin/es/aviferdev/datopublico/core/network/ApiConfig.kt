package es.aviferdev.datopublico.core.network

/**
 * Configuración del cliente HTTP del cliente (FT00035).
 *
 * Agrupa la **URL base** y el **timeout** de petición en un valor inmutable y
 * **inyectable**: en producción usa los valores por defecto (URL resuelta por
 * plataforma y timeout de 15 s) y en tests/previews se sustituye por una base de
 * prueba. Al ser un `data class`, es trivial construir variantes sin estado
 * global.
 *
 * @property baseUrl URL base de la API, sin barra final; por defecto, la de la
 *   plataforma ([platformApiBaseUrl]).
 * @property requestTimeoutMillis timeout de conexión/petición/socket, en
 *   milisegundos; un valor explícito evita peticiones colgadas.
 */
data class ApiConfig(
    val baseUrl: String = platformApiBaseUrl(),
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) {
    companion object {
        /** Timeout por defecto de las peticiones del cliente (15 s). */
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 15_000L
    }
}
