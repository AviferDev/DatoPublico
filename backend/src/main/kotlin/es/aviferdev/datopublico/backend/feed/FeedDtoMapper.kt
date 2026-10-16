package es.aviferdev.datopublico.backend.feed

import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.relevance.Highlight
import es.aviferdev.datopublico.backend.relevance.HighlightReason
import es.aviferdev.datopublico.model.HighlightDto
import es.aviferdev.datopublico.model.HighlightReasonDto
import es.aviferdev.datopublico.model.PublicationDto
import es.aviferdev.datopublico.model.SummaryDto

/**
 * Mapeo **puro** (sin red, base de datos ni Ktor) del dominio al contrato de
 * `:shared` del feed diario (FT00026).
 *
 * Reutiliza `Publication`/`SummaryEntity`/`Highlight` tal cual y **no** conoce los
 * repositorios. Es reutilizable por `api-detalle-norma` (FT00027) para el mapeo de
 * la publicación.
 *
 * **Trazabilidad**: la [PublicationDto.urlOficial] y la
 * [SummaryDto.fuenteOficial] siempre viajan; el `avisoIA` viaja tal cual lo
 * persistió la disposición (el aviso **visible** es FT00033).
 */
object FeedDtoMapper {

    /**
     * Proyecta una [publication] y su [summary] publicado al DTO del feed.
     *
     * @throws IllegalArgumentException si [publication] no tiene categoría
     *   (dato corrupto: el repositorio ya lo excluye; aquí es fail-fast).
     */
    fun toPublicationDto(publication: Publication, summary: SummaryEntity): PublicationDto =
        PublicationDto(
            id = publication.id,
            title = publication.title,
            publicationDate = publication.publicationDate,
            issuingBody = publication.issuingBody,
            section = publication.section,
            heading = publication.heading,
            category = requireNotNull(publication.category) {
                "La publicación ${publication.id} no tiene categoría (dato corrupto)."
            },
            officialUrl = publication.officialUrl,
            summary = toSummaryDto(summary),
            deadline = publication.deadline,
        )

    /**
     * Proyecta el resumen persistido al DTO ciudadano.
     *
     * `plazo` queda `null`: la tabla `resumen` no guarda plazo; el plazo fiable
     * viaja en [PublicationDto.plazo].
     */
    fun toSummaryDto(summary: SummaryEntity): SummaryDto = SummaryDto(
        whatChanges = summary.whatChanges,
        whoIsAffected = summary.whomItAffects,
        keyFigures = summary.keyFigures,
        officialSource = summary.officialSource,
        aiNotice = summary.aiNotice,
        deadline = null,
    )

    /**
     * Proyecta un [highlight] del ranker a su DTO de wire, con los motivos
     * objetivos y **sin** exponer el `score` numérico.
     */
    fun toHighlightDto(highlight: Highlight, summary: SummaryEntity): HighlightDto = HighlightDto(
        publication = toPublicationDto(highlight.publication, summary),
        reasons = highlight.reasons.map { reason -> toHighlightReasonDto(reason) },
    )

    /** Traduce el vocabulario interno de motivos al enum de wire (exhaustivo). */
    fun toHighlightReasonDto(reason: HighlightReason): HighlightReasonDto = when (reason) {
        HighlightReason.NORMATIVE_SECTION -> HighlightReasonDto.NORMATIVE_SECTION
        HighlightReason.NORMATIVE_RANK -> HighlightReasonDto.NORMATIVE_RANK
        HighlightReason.THEMATIC_KEYWORD -> HighlightReasonDto.THEMATIC_KEYWORD
        HighlightReason.OPEN_DEADLINE -> HighlightReasonDto.OPEN_DEADLINE
        HighlightReason.RELEVANT_BODY -> HighlightReasonDto.RELEVANT_BODY
    }
}
