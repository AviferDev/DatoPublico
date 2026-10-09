package es.aviferdev.datopublico.model

import kotlinx.serialization.Serializable

/**
 * Plazo de solicitud de una convocatoria (empleo público, becas o subvenciones).
 *
 * [fechaLimite] es una fecha ISO-8601 como `String`: en `commonMain` no se usa
 * `java.time` para no romper iOS/Wasm. El formateo localizado pertenece a cada
 * plataforma.
 */
@Serializable
data class Plazo(
    val fechaLimite: String,
    val descripcion: String? = null,
)
