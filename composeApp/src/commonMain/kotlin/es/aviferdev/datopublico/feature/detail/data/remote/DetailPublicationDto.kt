package es.aviferdev.datopublico.feature.detail.data.remote

import es.aviferdev.datopublico.feature.feed.data.remote.FeedCategoryDto
import es.aviferdev.datopublico.feature.feed.data.remote.FeedDeadlineDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de **wire** del detalle de publicación en el cliente (FT00035).
 *
 * Son la **capa anti-corrupción** del cliente para `GET /v1/publicacion/{id}`:
 * replican el subconjunto del contrato que consume la pantalla de detalle, con
 * los mismos tokens `snake_case`, y se mapean a los modelos de dominio de
 * `feature/detail/domain` ([es.aviferdev.datopublico.feature.detail.domain.PublicationDetail]).
 * La categoría y el plazo **reutilizan** los enums/DTO del feed para no duplicar
 * los tokens de wire. Cada propiedad lleva `@SerialName` explícito (regla MUST de
 * `CONSTRAINTS.md` §«Serialización»).
 */

/**
 * Publicación del detalle tal y como viaja en el contrato.
 *
 * @property id identificador oficial del BOE.
 * @property title título oficial.
 * @property publishedDate fecha de publicación en ISO-8601.
 * @property category categoría curada (token de wire).
 * @property officialUrl enlace a la fuente oficial.
 * @property summary resumen ciudadano completo; `null` si no hay.
 * @property deadline plazo de solicitud; `null` si no aplica.
 */
@Serializable
data class DetailPublicationDto(
    @SerialName("id")
    val id: String,
    @SerialName("titulo")
    val title: String,
    @SerialName("fechaPublicacion")
    val publishedDate: String,
    @SerialName("categoria")
    val category: FeedCategoryDto,
    @SerialName("urlOficial")
    val officialUrl: String = "",
    @SerialName("resumen")
    val summary: DetailSummaryDto? = null,
    @SerialName("plazo")
    val deadline: FeedDeadlineDto? = null,
)

/**
 * Resumen ciudadano de wire del detalle, con los tres bloques y el aviso de IA.
 *
 * @property whatChanges texto de «¿Qué cambia?».
 * @property whoIsAffected texto de «¿A quién afecta?».
 * @property keyFigures cifras clave; puede estar vacía.
 * @property aiNotice `true` si el resumen es generado por IA.
 */
@Serializable
data class DetailSummaryDto(
    @SerialName("queCambia")
    val whatChanges: String = "",
    @SerialName("aQuienAfecta")
    val whoIsAffected: String = "",
    @SerialName("cifrasClave")
    val keyFigures: List<String> = emptyList(),
    @SerialName("avisoIA")
    val aiNotice: Boolean = true,
)
