package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Plazo de solicitud de una convocatoria (empleo público, becas o subvenciones).
 *
 * [fechaLimite] es una fecha ISO-8601 como `String`: en `commonMain` no se usa
 * `java.time` para no romper iOS/Wasm. El formateo localizado pertenece a cada
 * plataforma.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class PlazoDto(
    @SerialName("fechaLimite")
    val fechaLimite: String,
    @SerialName("descripcion")
    val descripcion: String? = null,
)
