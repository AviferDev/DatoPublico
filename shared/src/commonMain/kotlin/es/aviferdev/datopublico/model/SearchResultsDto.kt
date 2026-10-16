package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resultado de la **búsqueda** sobre el corpus del BOE (FT00028).
 *
 * [query] es el **eco** de la consulta recibida (token de wire `q`) y [results]
 * son las publicaciones más relevantes, ya **deduplicadas** por publicación y
 * ordenadas por similitud semántica descendente. Cada resultado es el **mismo**
 * [PublicationDto] del feed (con su `resumen` publicado, `categoria`, `urlOficial`
 * y `plazo`), no un DTO nuevo por ítem.
 *
 * No expone `total` ni paginación: la búsqueda es **top-k** (ANN), no paginable de
 * forma significativa; el tope lo fija el parámetro `limite` de la petición.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class SearchResultsDto(
    @SerialName("q")
    val query: String,
    @SerialName("resultados")
    val results: List<PublicationDto> = emptyList(),
)
