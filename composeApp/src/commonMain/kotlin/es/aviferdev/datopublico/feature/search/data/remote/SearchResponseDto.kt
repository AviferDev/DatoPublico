package es.aviferdev.datopublico.feature.search.data.remote

import es.aviferdev.datopublico.feature.feed.data.remote.FeedCategoryDto
import es.aviferdev.datopublico.feature.feed.data.remote.FeedPublicationDto
import es.aviferdev.datopublico.feature.feed.data.remote.toPublication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.domain.SearchResults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO de **wire** de la respuesta de `GET /v1/buscar` (FT00036).
 *
 * Es la **capa anti-corrupción** del cliente: espeja el contrato `SearchResultsDto`
 * de `:shared` (eco de la consulta `q` y lista de `resultados`). Cada resultado es
 * el **mismo** `PublicacionDto` del feed, así que se reutiliza
 * [FeedPublicationDto] en vez de declarar un DTO nuevo por ítem (ADR 0021/0023).
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 *
 * @property query eco de la consulta enviada (token `q`).
 * @property results publicaciones resultado, ya ordenadas por relevancia.
 */
@Serializable
data class SearchResponseDto(
    @SerialName("q")
    val query: String = "",
    @SerialName("resultados")
    val results: List<FeedPublicationDto> = emptyList(),
)

/**
 * Mapeo **puro** (sin red ni estado) del contrato de wire de la búsqueda a los
 * modelos de dominio de la búsqueda (FT00036).
 *
 * Reutiliza el mapeo de publicación del feed ([toPublication]) porque los ítems de
 * `/v1/buscar` son el **mismo** `PublicacionDto`: los resultados no traen
 * destacado, así que se mapean con `isHighlighted = false` y sin motivos, pero se
 * conserva el **plazo** si la publicación lo tiene.
 */

/**
 * Proyecta la respuesta de la búsqueda a [SearchResults].
 *
 * @return los resultados de dominio listos para la UI.
 */
internal fun SearchResponseDto.toSearchResults(): SearchResults = SearchResults(
    query = query,
    publications = results.map { publication ->
        publication.toPublication(isHighlighted = false, highlightReasons = emptyList())
    },
)

/**
 * Traduce la categoría de dominio al **token de wire** del parámetro `categoria`.
 *
 * Reutiliza los tokens declarados en [FeedCategoryDto] (única fuente de verdad:
 * sus `@SerialName`), en vez de repetir los literales `snake_case`.
 *
 * @return el token `snake_case` de la categoría (p. ej. `becas_subvenciones_y_ayudas`).
 */
internal fun PublicationCategory.toWireToken(): String = when (this) {
    PublicationCategory.NORMATIVE_REGULATION -> FeedCategoryDto.NORMATIVE_REGULATION
    PublicationCategory.APPOINTMENTS_AND_DISMISSALS -> FeedCategoryDto.APPOINTMENTS_AND_DISMISSALS
    PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS -> FeedCategoryDto.PUBLIC_EMPLOYMENT_AND_EXAMS
    PublicationCategory.GRANTS_AND_SUBSIDIES -> FeedCategoryDto.GRANTS_AND_SUBSIDIES
    PublicationCategory.AWARDS -> FeedCategoryDto.AWARDS
    PublicationCategory.AGREEMENTS_AND_CONVENTIONS -> FeedCategoryDto.AGREEMENTS_AND_CONVENTIONS
    PublicationCategory.EDUCATION_AND_STUDY_PLANS -> FeedCategoryDto.EDUCATION_AND_STUDY_PLANS
    PublicationCategory.ENVIRONMENT -> FeedCategoryDto.ENVIRONMENT
    PublicationCategory.APPEALS_AND_RESOLUTIONS -> FeedCategoryDto.APPEALS_AND_RESOLUTIONS
    PublicationCategory.PUBLIC_INFORMATION_AND_CONCESSIONS ->
        FeedCategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS
    PublicationCategory.OTHER_PROVISIONS_AND_ANNOUNCEMENTS ->
        FeedCategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS
}.wireToken()

/**
 * Token `snake_case` del enum de wire, leído de su descriptor de serialización.
 *
 * @return el valor de `@SerialName` del enum.
 */
private fun FeedCategoryDto.wireToken(): String =
    FeedCategoryDto.serializer().descriptor.getElementName(ordinal)
