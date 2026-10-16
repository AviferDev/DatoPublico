package es.aviferdev.datopublico.backend.search

import es.aviferdev.datopublico.backend.feed.FeedDtoMapper
import es.aviferdev.datopublico.backend.persistence.FeedItem
import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.PublicationDto
import es.aviferdev.datopublico.model.SearchResultsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Servicio de aplicación de la **búsqueda** sobre el corpus del BOE (FT00028).
 *
 * Compone la recuperación híbrida ([HybridSearch], FT00017: vectoriza la consulta
 * con `embedQuery` y consulta por similitud con los filtros de metadatos) con la
 * lectura de publicaciones **publicadas** ([FeedRepository]) para devolver una
 * lista **top-k** de publicaciones deduplicadas y ordenadas por relevancia.
 *
 * La unidad de resultado es la **publicación** (no el fragmento): cada resultado es
 * el **mismo** `PublicationDto` del feed, con su resumen `publicado`, su categoría
 * y su enlace oficial (trazabilidad; ADR 0021/0022). **Nunca** se sirve una
 * publicación sin resumen publicado: el filtro `estado = 'publicado'` vive en el
 * repositorio.
 *
 * No conoce Ktor: la ruta delgada (`plugins/Search.kt`) valida los parámetros y
 * traduce los fallos a `400`. La inferencia ONNX y la lectura JDBC (bloqueantes)
 * se ejecutan fuera del hilo de eventos (`Dispatchers.IO`).
 *
 * @param hybridSearch recuperación híbrida: vectoriza la consulta y consulta el
 *   índice de fragmentos con filtros.
 * @param feedRepository lectura de las publicaciones con resumen publicado.
 */
class SearchService(
    private val hybridSearch: HybridSearch,
    private val feedRepository: FeedRepository,
) {

    /**
     * Busca en el corpus las publicaciones más relevantes para [query].
     *
     * @param query texto de la consulta (no puede estar en blanco).
     * @param category categoría curada opcional (filtro `AND`).
     * @param publishedFrom fecha ISO-8601 **inclusiva** mínima de publicación.
     * @param publishedTo fecha ISO-8601 **inclusiva** máxima de publicación.
     * @param limit número máximo de publicaciones (`1..MAX_LIMIT`).
     * @return el resultado con el eco de [query] y las publicaciones ordenadas por
     *   relevancia; lista vacía si no hay coincidencias publicadas.
     * @throws IllegalArgumentException si [query] está en blanco o [limit] fuera de
     *   rango (fail-fast; la ruta lo traduce a `400`).
     */
    suspend fun search(
        query: String,
        category: CategoryDto? = null,
        publishedFrom: String? = null,
        publishedTo: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): SearchResultsDto {
        require(query.isNotBlank()) { "La consulta no puede estar en blanco." }
        require(limit in 1..MAX_LIMIT) {
            "El límite debe estar entre 1 y $MAX_LIMIT (recibido: $limit)."
        }
        val filter = SearchFilter(
            publishedFrom = publishedFrom,
            publishedTo = publishedTo,
            category = category,
        )
        val rankedIds = rankedPublicationIds(query, filter, limit)
        val items = withContext(Dispatchers.IO) { feedRepository.listPublishedByIds(rankedIds) }
        return SearchResultsDto(query = query, results = orderedResults(items, rankedIds))
    }

    /** Recupera y deduplica por publicación los identificadores más relevantes. */
    private suspend fun rankedPublicationIds(
        query: String,
        filter: SearchFilter,
        limit: Int,
    ): List<String> =
        withContext(Dispatchers.IO) {
            hybridSearch.search(query, filter, candidateLimit(limit))
        }.map { match -> match.fragment.publicationId }.distinct().take(limit)

    /**
     * Reordena las publicaciones por el orden de relevancia de [rankedIds] y
     * descarta las que el repositorio no devolvió (resumen no publicado o
     * categoría nula) reutilizando el mapeo puro `FeedDtoMapper`.
     */
    private fun orderedResults(items: List<FeedItem>, rankedIds: List<String>): List<PublicationDto> {
        val itemsById = items.associateBy { item -> item.publication.id }
        return rankedIds.mapNotNull { id ->
            itemsById[id]?.let { item -> FeedDtoMapper.toPublicacionDto(item.publication, item.summary) }
        }
    }

    /** Ventana de recuperación: más candidatos que `limit` para absorber el recorte por publicación. */
    private fun candidateLimit(limit: Int): Int = limit * CANDIDATE_FACTOR

    companion object {
        /** Número de resultados por defecto (alineado con `HybridSearch.DEFAULT_LIMIT`). */
        const val DEFAULT_LIMIT = 10

        /** Número máximo de resultados que la búsqueda admite. */
        const val MAX_LIMIT = 50

        /**
         * Factor de sobre-recuperación: se piden `limit * CANDIDATE_FACTOR`
         * fragmentos para que la deduplicación por publicación y el filtro
         * `estado = 'publicado'` no dejen por debajo de [DEFAULT_LIMIT] resultados
         * distintos. Es una **heurística** documentada (ADR 0023).
         */
        const val CANDIDATE_FACTOR = 5
    }
}
