package es.aviferdev.datopublico.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Construye el cliente HTTP **común** del cliente (FT00035).
 *
 * No fija motor: Ktor resuelve el del *source set* de cada plataforma (OkHttp en
 * Android, CIO en JVM, Darwin en iOS, JS en Wasm). Los tests construyen su propio
 * cliente con `MockEngine` reutilizando [configureDatoPublicoClient].
 *
 * @param config configuración de URL base y timeout.
 * @return un [HttpClient] listo para la API pública.
 */
fun createDatoPublicoHttpClient(config: ApiConfig = ApiConfig()): HttpClient =
    HttpClient { configureDatoPublicoClient(config) }

/**
 * Configura un cliente Ktor con los ajustes comunes de DatoPublico (FT00035).
 *
 * Se factoriza para que **producción y tests** compartan exactamente la misma
 * configuración (evita duplicar bloques en los tests y mantiene el CPD bajo).
 * Instala `ContentNegotiation` con JSON tolerante a claves desconocidas (el
 * contrato evoluciona de forma aditiva) y un `HttpTimeout` explícito; deja
 * `expectSuccess = false` para que un estado no-2xx llegue como **valor** y lo
 * traduzca el cliente a un fallo tipado, sin excepción.
 *
 * @param config configuración de URL base y timeout.
 */
fun HttpClientConfig<*>.configureDatoPublicoClient(config: ApiConfig = ApiConfig()) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMillis
        connectTimeoutMillis = config.requestTimeoutMillis
        socketTimeoutMillis = config.requestTimeoutMillis
    }
}
