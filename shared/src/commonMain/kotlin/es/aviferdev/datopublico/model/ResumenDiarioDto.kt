package es.aviferdev.datopublico.model

import kotlinx.serialization.Serializable

/**
 * Feed diario de publicaciones del BOE.
 *
 * [fecha] es una fecha ISO-8601 como `String` (sin `java.time`).
 */
@Serializable
data class ResumenDiarioDto(
    val fecha: String,
    val publicaciones: List<PublicacionDto> = emptyList(),
)
