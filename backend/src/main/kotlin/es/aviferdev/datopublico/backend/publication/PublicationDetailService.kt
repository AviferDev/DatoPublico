package es.aviferdev.datopublico.backend.publication

import es.aviferdev.datopublico.backend.feed.FeedDtoMapper
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.backend.persistence.SummaryRepository
import es.aviferdev.datopublico.backend.rag.review.SummaryState
import es.aviferdev.datopublico.model.PublicationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Servicio de aplicación del **detalle de una publicación** por su identificador
 * oficial del BOE (FT00027).
 *
 * Compone los repositorios de lectura ya existentes ([PublicationRepository] y
 * [SummaryRepository]) y **reutiliza** el mapeo puro [FeedDtoMapper] de FT00026:
 * el detalle devuelve el **mismo** `PublicationDto` que el feed. **Solo** se sirve
 * una publicación cuyo resumen está `publicado` (token [SummaryState.PUBLISHED]
 * que escribe FT00023); en cualquier otro caso el detalle es `null` (⇒ `404`).
 *
 * No conoce Ktor: la ruta delgada (`plugins/PublicationDetail.kt`) valida el
 * identificador y traduce los fallos a `400`/`404`. **No** inventa categoría: una
 * publicación sin categoría (dato corrupto) se trata como no servible.
 *
 * @param publicationRepository lectura de la publicación por `id` (clave primaria).
 * @param summaryRepository lectura del resumen 1:1 de la publicación (clave ajena).
 */
class PublicationDetailService(
    private val publicationRepository: PublicationRepository,
    private val summaryRepository: SummaryRepository,
) {

    /**
     * Devuelve el detalle de la publicación [id], o `null` si no debe servirse.
     *
     * Hace las dos lecturas (PK y FK, sin `JOIN` nuevo; el volumen es de una fila)
     * en **una sola** entrada a [Dispatchers.IO]. Devuelve `null` si la publicación
     * no existe, si su `categoria` es `null` (dato corrupto) o si no tiene un
     * resumen `estado = "publicado"`; en caso contrario mapea con [FeedDtoMapper].
     *
     * @param id identificador oficial del BOE (`BOE-A-2026-20979`).
     * @return el [PublicationDto] completo, o `null` (la ruta responde `404`).
     */
    suspend fun detail(id: String): PublicationDto? = withContext(Dispatchers.IO) {
        val publication = publicationRepository.findById(id)
        val summary = summaryRepository.findByPublication(id)
        val servable = publication?.takeIf { item -> item.category != null }
        val published = summary?.takeIf { item -> item.status == SummaryState.PUBLISHED.persistedValue }
        if (servable == null || published == null) {
            null
        } else {
            FeedDtoMapper.toPublicacionDto(servable, published)
        }
    }

    companion object {
        /**
         * Longitud máxima aceptada del identificador en el path (anti-abuso).
         *
         * No valida el **formato** del identificador del BOE (es opaco: la
         * existencia decide `200`/`404`); la ruta responde `400` si el `id` está en
         * blanco o la supera.
         */
        const val MAX_ID_LENGTH = 128
    }
}
