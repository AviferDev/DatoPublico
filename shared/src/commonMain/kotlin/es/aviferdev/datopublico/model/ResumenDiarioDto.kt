package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feed diario de publicaciones del BOE.
 *
 * [fecha] es una fecha ISO-8601 como `String` (sin `java.time`).
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class ResumenDiarioDto(
    @SerialName("fecha")
    val fecha: String,
    @SerialName("publicaciones")
    val publicaciones: List<PublicacionDto> = emptyList(),
)
