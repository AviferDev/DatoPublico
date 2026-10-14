package es.aviferdev.datopublico.feature.feed.data.cache

import es.aviferdev.datopublico.feature.feed.domain.DailyFeed

/**
 * Puerto de **cache offline** del feed (FT00035).
 *
 * Guarda el último feed cargado para que, con la red caída, el contenido siga
 * visible. Solo almacena **contenido público del BOE**: nunca datos personales ni
 * de uso. La implementación en memoria ([InMemoryFeedCache]) es la base de una
 * futura cache persistente.
 */
interface FeedCache {
    /**
     * Lee el último feed cacheado.
     *
     * @return el feed guardado, o `null` si todavía no hay nada cacheado.
     */
    suspend fun read(): DailyFeed?

    /**
     * Guarda [feed] como último feed cacheado (sobrescribe el anterior).
     *
     * @param feed feed a cachear.
     */
    suspend fun write(feed: DailyFeed)
}
