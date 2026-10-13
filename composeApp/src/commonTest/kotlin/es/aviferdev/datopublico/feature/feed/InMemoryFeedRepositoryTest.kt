package es.aviferdev.datopublico.feature.feed

import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedMode
import es.aviferdev.datopublico.feature.feed.data.InMemoryFeedRepository
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests del repositorio en memoria de ejemplo: contenido determinista (destacados
 * con motivos, categorías variadas y convocatorias con plazo), los modos vacío y
 * fallo, y el formateo puro de fechas que usan las tarjetas.
 */
class InMemoryFeedRepositoryTest {
    @Test
    fun sampleModeReturnsHighlightsWithReasons() = runTest {
        val result = sampleRepository().load()

        assertIs<FeedLoadResult.Success>(result)
        assertTrue(result.feed.highlights.isNotEmpty())
        assertTrue(result.feed.highlights.all { publication -> publication.highlightReasons.isNotEmpty() })
    }

    @Test
    fun sampleModeCoversSeveralCategories() = runTest {
        val result = sampleRepository().load()

        assertIs<FeedLoadResult.Success>(result)
        val categories = result.feed.publications.map { publication -> publication.category }.distinct()
        assertTrue(categories.size >= 3)
    }

    @Test
    fun sampleModeHasDeadlinesAndPublicationsWithoutThem() = runTest {
        val result = sampleRepository().load()

        assertIs<FeedLoadResult.Success>(result)
        assertTrue(result.feed.publications.any { publication -> publication.deadline != null })
        assertTrue(result.feed.publications.any { publication -> publication.deadline == null })
    }

    @Test
    fun sampleModeUsesTheFixedSampleDate() = runTest {
        val result = sampleRepository().load()

        assertIs<FeedLoadResult.Success>(result)
        assertEquals("2026-10-13", result.feed.date)
    }

    @Test
    fun emptyModeReturnsSuccessWithoutContent() = runTest {
        val result = InMemoryFeedRepository(mode = InMemoryFeedMode.EMPTY, latencyMillis = 0).load()

        assertEquals(FeedLoadResult.Success(DailyFeed(date = "2026-10-13")), result)
    }

    @Test
    fun unavailableModeReturnsFailure() = runTest {
        val result = InMemoryFeedRepository(mode = InMemoryFeedMode.UNAVAILABLE, latencyMillis = 0).load()

        assertEquals(FeedLoadResult.Failure, result)
    }

    @Test
    fun isoDatesAreFormattedAsDayMonthYear() {
        assertEquals("30/10/2026", formatIsoDate("2026-10-30"))
    }

    @Test
    fun unparseableDatesAreReturnedUnchanged() {
        assertEquals("sin-fecha", formatIsoDate("sin-fecha"))
    }

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): InMemoryFeedRepository =
        InMemoryFeedRepository(mode = InMemoryFeedMode.SAMPLE, latencyMillis = 0)
}
