package es.aviferdev.datopublico.feature.feed.data.cache

import es.aviferdev.datopublico.feature.feed.domain.DailyFeed

/**
 * Implementación **en memoria** de [FeedCache] (FT00035).
 *
 * Guarda el **último** feed de la sesión; no persiste entre arranques (la cache
 * persistente es un follow-up). Es deliberadamente simple y sin datos personales:
 * solo contenido público del BOE. La escritura es idempotente (sobrescribe).
 */
class InMemoryFeedCache : FeedCache {
    private var cachedFeed: DailyFeed? = null

    override suspend fun read(): DailyFeed? = cachedFeed

    override suspend fun write(feed: DailyFeed) {
        cachedFeed = feed
    }
}
