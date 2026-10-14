package es.aviferdev.datopublico.feature.feed.data.cache

import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests de la cache offline en memoria (FT00035): antes de escribir no hay nada,
 * tras escribir se lee el mismo feed y una nueva escritura sobrescribe.
 *
 * Corren con `runTest` y sin red ni base de datos.
 */
class InMemoryFeedCacheTest {

    @Test
    fun readBeforeWriteReturnsNull() = runTest {
        val cache = InMemoryFeedCache()

        assertNull(cache.read())
    }

    @Test
    fun writeThenReadReturnsTheCachedFeed() = runTest {
        val cache = InMemoryFeedCache()
        val feed = DailyFeed(date = "2026-10-13")

        cache.write(feed)

        assertEquals(feed, cache.read())
    }

    @Test
    fun writeOverwritesThePreviousFeed() = runTest {
        val cache = InMemoryFeedCache()
        val second = DailyFeed(date = "2026-10-14")

        cache.write(DailyFeed(date = "2026-10-13"))
        cache.write(second)

        assertEquals(second, cache.read())
    }
}
