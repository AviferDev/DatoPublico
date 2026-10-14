package es.aviferdev.datopublico.feature.feed.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de **wire** del feed diario en el cliente (FT00035).
 *
 * Son la **capa anti-corrupción** del cliente: replican el subconjunto del
 * contrato `GET /v1/feed` que consume el feed, con los mismos tokens
 * `snake_case`, y se mapean a los modelos de dominio de `feature/feed/domain`
 * ([es.aviferdev.datopublico.feature.feed.domain.DailyFeed]). Se mantienen aquí
 * (y no en `:shared`) porque `:composeApp` no depende de `:shared` (ADR 0027) para
 * no romper el gate SDK-free.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */

/**
 * Respuesta de `GET /v1/feed` (contrato de `docs/specs/api-feed-diario.md`).
 *
 * [date] es la fecha ISO-8601 pedida; [publications] es la **página** solicitada;
 * [highlights] son los destacados del día completo; la paginación ([page],
 * [pageSize], [total], [totalPages]) refleja el día completo.
 *
 * @property date fecha del feed en ISO-8601.
 * @property publications publicaciones de la página.
 * @property highlights destacados del día (subconjunto de las publicaciones).
 * @property page página 1-based servida.
 * @property pageSize tamaño de página servido.
 * @property total total de publicaciones del día.
 * @property totalPages total de páginas del día.
 */
@Serializable
data class FeedResponseDto(
    @SerialName("fecha")
    val date: String,
    @SerialName("publicaciones")
    val publications: List<FeedPublicationDto> = emptyList(),
    @SerialName("destacados")
    val highlights: List<FeedHighlightDto> = emptyList(),
    @SerialName("pagina")
    val page: Int = 1,
    @SerialName("tamanoPagina")
    val pageSize: Int = 20,
    @SerialName("total")
    val total: Int = 0,
    @SerialName("totalPaginas")
    val totalPages: Int = 0,
)

/**
 * Publicación de wire del feed.
 *
 * [category] y los motivos de [FeedHighlightDto] viajan como tokens
 * `snake_case`; [summary] y [deadline] son opcionales según la fuente.
 *
 * @property id identificador oficial del BOE.
 * @property title título oficial.
 * @property publishedDate fecha de publicación en ISO-8601.
 * @property category categoría curada (token de wire).
 * @property officialUrl enlace a la fuente oficial.
 * @property summary resumen ciudadano; `null` si no hay.
 * @property deadline plazo de solicitud; `null` si no aplica.
 */
@Serializable
data class FeedPublicationDto(
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
    val summary: FeedSummaryDto? = null,
    @SerialName("plazo")
    val deadline: FeedDeadlineDto? = null,
)

/**
 * Resumen ciudadano de wire, reducido a lo que necesita la tarjeta del feed.
 *
 * Solo se replica «¿Qué cambia?» ([whatChanges]), del que se deriva el resumen
 * corto de la tarjeta; el resto de campos del contrato se ignoran
 * (`ignoreUnknownKeys`).
 *
 * @property whatChanges texto de «¿Qué cambia?».
 */
@Serializable
data class FeedSummaryDto(
    @SerialName("queCambia")
    val whatChanges: String = "",
)

/**
 * Plazo de solicitud de wire.
 *
 * @property limitDate fecha límite en ISO-8601.
 */
@Serializable
data class FeedDeadlineDto(
    @SerialName("fechaLimite")
    val limitDate: String,
)

/**
 * Destacado del día de wire: una publicación y sus motivos objetivos.
 *
 * @property publication publicación destacada completa.
 * @property reasons motivos objetivos del destacado (tokens de wire).
 */
@Serializable
data class FeedHighlightDto(
    @SerialName("publicacion")
    val publication: FeedPublicationDto,
    @SerialName("motivos")
    val reasons: List<FeedHighlightReasonDto> = emptyList(),
)

/**
 * Categoría curada de wire (espejo de `CategoriaDto` de `:shared`).
 *
 * Cada valor declara su token `snake_case`; el mapper lo traduce al enum de
 * dominio `PublicationCategory`.
 */
@Serializable
enum class FeedCategoryDto {
    @SerialName("normas_y_legislacion")
    NORMATIVE_REGULATION,

    @SerialName("nombramientos_y_ceses")
    APPOINTMENTS_AND_DISMISSALS,

    @SerialName("oposiciones_y_empleo_publico")
    PUBLIC_EMPLOYMENT_AND_EXAMS,

    @SerialName("becas_subvenciones_y_ayudas")
    GRANTS_AND_SUBSIDIES,

    @SerialName("premios")
    AWARDS,

    @SerialName("convenios_y_acuerdos")
    AGREEMENTS_AND_CONVENTIONS,

    @SerialName("educacion_y_planes_de_estudio")
    EDUCATION_AND_STUDY_PLANS,

    @SerialName("medio_ambiente")
    ENVIRONMENT,

    @SerialName("recursos_y_resoluciones")
    APPEALS_AND_RESOLUTIONS,

    @SerialName("informacion_publica_y_concesiones")
    PUBLIC_INFORMATION_AND_CONCESSIONS,

    @SerialName("otras_disposiciones_y_anuncios")
    OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
}

/**
 * Motivo de destacado de wire (espejo de `HighlightReasonDto` de `:shared`).
 *
 * Cada valor declara su token `snake_case`; el mapper lo traduce al enum de
 * dominio `HighlightReason`.
 */
@Serializable
enum class FeedHighlightReasonDto {
    @SerialName("seccion_normativa")
    NORMATIVE_SECTION,

    @SerialName("rango_normativo")
    NORMATIVE_RANK,

    @SerialName("categoria_tematica")
    THEMATIC_KEYWORD,

    @SerialName("plazo_abierto")
    OPEN_DEADLINE,

    @SerialName("organismo_relevante")
    RELEVANT_BODY,
}
