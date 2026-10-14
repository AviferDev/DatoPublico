package es.aviferdev.datopublico.feature.detail.data.remote

import es.aviferdev.datopublico.feature.detail.domain.CitizenSummary
import es.aviferdev.datopublico.feature.detail.domain.PublicationDetail
import es.aviferdev.datopublico.feature.feed.data.remote.toDomain
import es.aviferdev.datopublico.feature.feed.domain.Deadline

/**
 * Mapeo **puro** (sin red ni estado) del DTO de wire del detalle al modelo de
 * dominio [PublicationDetail] (FT00035).
 *
 * Reutiliza la traducción de la **categoría** del feed ([toDomain]) y compone el
 * resumen ciudadano y el plazo. **No lanza**: los campos ausentes usan valores
 * seguros (textos vacíos, sin plazo).
 */

/**
 * Proyecta el DTO del detalle a [PublicationDetail].
 *
 * @return el detalle de dominio listo para la UI.
 */
internal fun DetailPublicationDto.toPublicationDetail(): PublicationDetail = PublicationDetail(
    id = id,
    title = title,
    category = category.toDomain(),
    publishedDate = publishedDate,
    summary = CitizenSummary(
        whatChanges = summary?.whatChanges.orEmpty(),
        whoIsAffected = summary?.whoIsAffected.orEmpty(),
        keyFigures = summary?.keyFigures.orEmpty(),
    ),
    deadline = deadline?.let { dto -> Deadline(limitDate = dto.limitDate) },
    officialSourceUrl = officialUrl,
    showsAiNotice = summary?.aiNotice ?: true,
)
