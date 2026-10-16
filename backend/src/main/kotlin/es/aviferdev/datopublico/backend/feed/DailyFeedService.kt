package es.aviferdev.datopublico.backend.feed

import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.relevance.HighlightRanker
import es.aviferdev.datopublico.model.DailySummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Servicio de aplicación del **feed diario** (FT00026).
 *
 * Compone el repositorio de lectura ([FeedRepository]) con el ranker puro de
 * destacados ([HighlightRanker], FT00013) para devolver, en una sola llamada
 * paginada, las publicaciones **publicadas** de una fecha con su resumen, su
 * categoría, su enlace oficial y su plazo, más los destacados del día.
 *
 * No conoce Ktor: la ruta delgada (`plugins/Feed.kt`) valida los parámetros y
 * traduce los fallos a `400`. El servicio no filtra por categoría (eso es
 * FT00028) y **nunca** sirve un resumen no publicado: el filtro vive en el
 * repositorio.
 *
 * @param feedRepository lectura de las publicaciones con resumen publicado.
 * @param highlightRanker ranker puro de destacados; por defecto, el de FT00013.
 */
class DailyFeedService(
    private val feedRepository: FeedRepository,
    private val highlightRanker: HighlightRanker = HighlightRanker(),
) {

    /**
     * Devuelve una **página** del feed de [date] más los destacados del día.
     *
     * @param date fecha ISO-8601 (`2026-10-11`).
     * @param page página 1-based.
     * @param pageSize tamaño de página.
     * @return el feed paginado; lista vacía y `total = 0` si no hay publicado.
     * @throws IllegalArgumentException si [page] o [pageSize] están fuera de
     *   rango (fail-fast; la ruta lo traduce a `400`).
     */
    suspend fun feed(
        date: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): DailySummaryDto {
        require(page >= 1) { "La página debe ser >= 1 (recibida: $page)." }
        require(pageSize in 1..MAX_PAGE_SIZE) {
            "El tamaño de página debe estar entre 1 y $MAX_PAGE_SIZE (recibido: $pageSize)."
        }
        val items = withContext(Dispatchers.IO) { feedRepository.listPublishedByDate(date) }
            .sortedBy { item -> item.publication.id }
        val highlights = highlightRanker.rank(items.map { item -> item.publication })
        val summariesById = items.associate { item -> item.publication.id to item.summary }
        val total = items.size
        val pageItems = items.drop((page - 1) * pageSize).take(pageSize)
        return DailySummaryDto(
            date = date,
            publications = pageItems.map { item ->
                FeedDtoMapper.toPublicacionDto(item.publication, item.summary)
            },
            highlights = highlights.mapNotNull { highlight ->
                summariesById[highlight.publication.id]?.let { summary ->
                    FeedDtoMapper.toHighlightDto(highlight, summary)
                }
            },
            page = page,
            pageSize = pageSize,
            total = total,
            totalPages = (total + pageSize - 1) / pageSize,
        )
    }

    companion object {
        /** Tamaño de página por defecto del feed. */
        const val DEFAULT_PAGE_SIZE = 20

        /** Tamaño de página máximo aceptado por el feed. */
        const val MAX_PAGE_SIZE = 100
    }
}
