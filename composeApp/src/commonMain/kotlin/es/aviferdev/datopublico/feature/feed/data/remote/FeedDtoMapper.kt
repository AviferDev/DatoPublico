package es.aviferdev.datopublico.feature.feed.data.remote

import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.HighlightReason
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory

/**
 * Mapeo **puro** (sin red ni estado) del contrato de wire del feed a los modelos
 * de dominio de FT00032 (FT00035).
 *
 * Traduce los tokens `snake_case` ([FeedCategoryDto], [FeedHighlightReasonDto]) a
 * los enums de dominio, deriva el resumen corto de «¿Qué cambia?» y cruza las
 * publicaciones con los destacados para marcar `isHighlighted`/`highlightReasons`.
 * **No lanza**: los campos ausentes usan valores seguros.
 */

/**
 * Proyecta la respuesta del feed a [DailyFeed].
 *
 * Cruza [FeedResponseDto.publications] con [FeedResponseDto.highlights] por `id`:
 * una publicación es destacada si aparece entre los destacados, y hereda sus
 * motivos. Los destacados se mapean siempre como destacados (con sus motivos).
 *
 * @return el feed de dominio listo para la UI.
 */
internal fun FeedResponseDto.toDailyFeed(): DailyFeed {
    val reasonsById = highlights.associate { highlight ->
        highlight.publication.id to highlight.reasons.map { reason -> reason.toDomain() }
    }
    return DailyFeed(
        date = date,
        highlights = highlights.map { highlight ->
            highlight.publication.toPublication(
                isHighlighted = true,
                highlightReasons = reasonsById.getValue(highlight.publication.id),
            )
        },
        publications = publications.map { publication ->
            val reasons = reasonsById[publication.id].orEmpty()
            publication.toPublication(isHighlighted = reasons.isNotEmpty(), highlightReasons = reasons)
        },
    )
}

/**
 * Proyecta una publicación de wire al dominio, con la marca de destacado y sus
 * motivos ya resueltos por [toDailyFeed].
 *
 * @param isHighlighted `true` si el ranker la marcó como destacada.
 * @param highlightReasons motivos objetivos del destacado; vacío si no lo es.
 * @return la publicación de dominio.
 */
internal fun FeedPublicationDto.toPublication(
    isHighlighted: Boolean,
    highlightReasons: List<HighlightReason>,
): Publication = Publication(
    id = id,
    title = title,
    category = category.toDomain(),
    publishedDate = publishedDate,
    shortSummary = summary?.whatChanges.orEmpty(),
    isHighlighted = isHighlighted,
    highlightReasons = highlightReasons,
    deadline = deadline?.let { dto -> Deadline(limitDate = dto.limitDate) },
    officialUrl = officialUrl,
)

/** Traduce el token de categoría de wire al enum de dominio (exhaustivo). */
internal fun FeedCategoryDto.toDomain(): PublicationCategory = when (this) {
    FeedCategoryDto.NORMATIVE_REGULATION -> PublicationCategory.NORMATIVE_REGULATION
    FeedCategoryDto.APPOINTMENTS_AND_DISMISSALS -> PublicationCategory.APPOINTMENTS_AND_DISMISSALS
    FeedCategoryDto.PUBLIC_EMPLOYMENT_AND_EXAMS -> PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS
    FeedCategoryDto.GRANTS_AND_SUBSIDIES -> PublicationCategory.GRANTS_AND_SUBSIDIES
    FeedCategoryDto.AWARDS -> PublicationCategory.AWARDS
    FeedCategoryDto.AGREEMENTS_AND_CONVENTIONS -> PublicationCategory.AGREEMENTS_AND_CONVENTIONS
    FeedCategoryDto.EDUCATION_AND_STUDY_PLANS -> PublicationCategory.EDUCATION_AND_STUDY_PLANS
    FeedCategoryDto.ENVIRONMENT -> PublicationCategory.ENVIRONMENT
    FeedCategoryDto.APPEALS_AND_RESOLUTIONS -> PublicationCategory.APPEALS_AND_RESOLUTIONS
    FeedCategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS -> PublicationCategory.PUBLIC_INFORMATION_AND_CONCESSIONS
    FeedCategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS -> PublicationCategory.OTHER_PROVISIONS_AND_ANNOUNCEMENTS
}

/** Traduce el token de motivo de destacado de wire al enum de dominio. */
internal fun FeedHighlightReasonDto.toDomain(): HighlightReason = when (this) {
    FeedHighlightReasonDto.NORMATIVE_SECTION -> HighlightReason.NORMATIVE_SECTION
    FeedHighlightReasonDto.NORMATIVE_RANK -> HighlightReason.NORMATIVE_RANK
    FeedHighlightReasonDto.THEMATIC_KEYWORD -> HighlightReason.THEMATIC_KEYWORD
    FeedHighlightReasonDto.OPEN_DEADLINE -> HighlightReason.OPEN_DEADLINE
    FeedHighlightReasonDto.RELEVANT_BODY -> HighlightReason.RELEVANT_BODY
}
