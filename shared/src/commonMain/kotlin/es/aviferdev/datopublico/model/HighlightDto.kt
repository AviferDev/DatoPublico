package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Publicación **destacada** del día dentro del feed diario ([ResumenDiarioDto]).
 *
 * Lleva la [publication] completa (con su resumen, categoría, enlace oficial y
 * plazo) y los [reasons] objetivos que justifican el destacado. **No** se expone
 * la puntuación numérica (`score`): el destacado se explica por sus motivos, no
 * por un ranking crudo (decisión durable en el ADR 0008).
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida con
 * la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class HighlightDto(
    @SerialName("publicacion")
    val publication: PublicacionDto,
    @SerialName("motivos")
    val reasons: List<HighlightReasonDto>,
)
