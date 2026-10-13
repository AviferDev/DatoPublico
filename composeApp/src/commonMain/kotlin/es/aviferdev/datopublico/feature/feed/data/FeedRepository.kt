package es.aviferdev.datopublico.feature.feed.data

import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult

/**
 * Puerto de datos del feed diario.
 *
 * Es la frontera que implementará FT00035 con el cliente HTTP real y la caché
 * offline. Devuelve un resultado **tipado** ([FeedLoadResult]) en vez de lanzar
 * excepciones, para que los fallos se modelen como estado de UI.
 */
interface FeedRepository {
    /**
     * Carga el feed diario.
     *
     * @return el feed obtenido o un fallo tipado; nunca propaga excepciones a la
     *   UI.
     */
    suspend fun load(): FeedLoadResult
}
