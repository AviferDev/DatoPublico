package es.aviferdev.datopublico.serialization

import kotlinx.serialization.json.Json

/**
 * Configuración JSON compartida por backend y cliente.
 *
 * `ignoreUnknownKeys` permite que ambos lados evolucionen el contrato sin romper
 * al otro; `encodeDefaults` hace explícitos los valores por defecto.
 */
val DatoPublicoJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
