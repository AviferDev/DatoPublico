package es.aviferdev.datopublico.backend.feed

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.relevance.Highlight
import es.aviferdev.datopublico.backend.relevance.HighlightReason
import es.aviferdev.datopublico.model.HighlightDto
import es.aviferdev.datopublico.model.HighlightReasonDto
import es.aviferdev.datopublico.model.PublicacionDto
import es.aviferdev.datopublico.model.ResumenDto

/**
 * Mapeo **puro** (sin red, base de datos ni Ktor) del dominio al contrato de
 * `:shared` del feed diario (FT00026).
 *
 * Reutiliza `Publicacion`/`SummaryEntity`/`Highlight` tal cual y **no** conoce los
 * repositorios. Es reutilizable por `api-detalle-norma` (FT00027) para el mapeo de
 * la publicación.
 *
 * **Trazabilidad**: la [PublicacionDto.urlOficial] y la
 * [ResumenDto.fuenteOficial] siempre viajan; el `avisoIA` viaja tal cual lo
 * persistió la disposición (el aviso **visible** es FT00033).
 */
object FeedDtoMapper {

    /**
     * Proyecta una [publication] y su [summary] publicado al DTO del feed.
     *
     * @throws IllegalArgumentException si [publication] no tiene categoría
     *   (dato corrupto: el repositorio ya lo excluye; aquí es fail-fast).
     */
    fun toPublicacionDto(publication: Publicacion, summary: SummaryEntity): PublicacionDto =
        PublicacionDto(
            id = publication.id,
            titulo = publication.titulo,
            fechaPublicacion = publication.fechaPublicacion,
            organismo = publication.organismo,
            seccion = publication.seccion,
            epigrafe = publication.epigrafe,
            categoria = requireNotNull(publication.categoria) {
                "La publicación ${publication.id} no tiene categoría (dato corrupto)."
            },
            urlOficial = publication.urlOficial,
            resumen = toResumenDto(summary),
            plazo = publication.plazo,
        )

    /**
     * Proyecta el resumen persistido al DTO ciudadano.
     *
     * `plazo` queda `null`: la tabla `resumen` no guarda plazo; el plazo fiable
     * viaja en [PublicacionDto.plazo].
     */
    fun toResumenDto(summary: SummaryEntity): ResumenDto = ResumenDto(
        queCambia = summary.whatChanges,
        aQuienAfecta = summary.whomItAffects,
        cifrasClave = summary.keyFigures,
        fuenteOficial = summary.officialSource,
        avisoIA = summary.aiNotice,
        plazo = null,
    )

    /**
     * Proyecta un [highlight] del ranker a su DTO de wire, con los motivos
     * objetivos y **sin** exponer el `score` numérico.
     */
    fun toHighlightDto(highlight: Highlight, summary: SummaryEntity): HighlightDto = HighlightDto(
        publication = toPublicacionDto(highlight.publication, summary),
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
