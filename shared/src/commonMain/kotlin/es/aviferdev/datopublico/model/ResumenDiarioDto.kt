package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feed diario de publicaciones del BOE.
 *
 * [fecha] es una fecha ISO-8601 como `String` (sin `java.time`). [publicaciones]
 * es la **página** solicitada; [highlights] son los destacados del día completo
 * (calculados *on-the-fly*, nunca por página); [total]/[totalPages] reflejan el
 * día completo aunque solo se sirva una página.
 *
 * Los campos nuevos son **aditivos y con default** (FT00026): el wire existente
 * sigue decodificando. El tamaño de página por defecto ([pageSize]) y su máximo
 * los valida el backend; aquí solo se modelan.
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
    @SerialName("destacados")
    val highlights: List<HighlightDto> = emptyList(),
    @SerialName("pagina")
    val page: Int = 1,
    @SerialName("tamanoPagina")
    val pageSize: Int = 20,
    @SerialName("total")
    val total: Int = 0,
    @SerialName("totalPaginas")
    val totalPages: Int = 0,
)
