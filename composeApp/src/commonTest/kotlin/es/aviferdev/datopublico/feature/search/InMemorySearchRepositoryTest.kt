package es.aviferdev.datopublico.feature.search

import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.data.InMemorySearchMode
import es.aviferdev.datopublico.feature.search.data.InMemorySearchRepository
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult
import es.aviferdev.datopublico.feature.search.domain.isIsoDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests del repositorio en memoria de la búsqueda: filtrado por texto (sin
 * mayúsculas), por categoría y por rango de fechas inclusivo, los modos vacío y
 * fallo, y la validación pura de fechas ISO.
 */
class InMemorySearchRepositoryTest {

    @Test
    fun sampleModeFiltersByTextIgnoringCase() = runTest {
        val result = sampleRepository().search("BECAS", SearchFilters())

        assertIs<SearchLoadResult.Success>(result)
        assertTrue(result.results.publications.isNotEmpty())
        assertTrue(
            result.results.publications.all { publication ->
                publication.title.lowercase().contains("becas") ||
                    publication.shortSummary.lowercase().contains("becas")
            },
        )
    }

    @Test
    fun sampleModeFiltersByCategory() = runTest {
        val filters = SearchFilters(category = PublicationCategory.GRANTS_AND_SUBSIDIES)
        val result = sampleRepository().search("becas", filters)

        assertIs<SearchLoadResult.Success>(result)
        assertTrue(result.results.publications.isNotEmpty())
        assertTrue(
            result.results.publications.all { publication ->
                publication.category == PublicationCategory.GRANTS_AND_SUBSIDIES
            },
        )
    }

    @Test
    fun sampleModeFiltersByInclusiveDateRange() = runTest {
        val filters = SearchFilters(fromDate = "2026-03-01", toDate = "2026-06-30")
        val result = sampleRepository().search("", filters)

        assertIs<SearchLoadResult.Success>(result)
        assertTrue(result.results.publications.isNotEmpty())
        assertTrue(
            result.results.publications.all { publication ->
                publication.publishedDate >= "2026-03-01" && publication.publishedDate <= "2026-06-30"
            },
        )
    }

    @Test
    fun sampleModeWithoutMatchesReturnsEmptyList() = runTest {
        val result = sampleRepository().search("zzzzzz", SearchFilters())

        assertIs<SearchLoadResult.Success>(result)
        assertTrue(result.results.publications.isEmpty())
    }

    @Test
    fun emptyModeReturnsSuccessWithoutResults() = runTest {
        val result = InMemorySearchRepository(mode = InMemorySearchMode.EMPTY, latencyMillis = 0)
            .search("becas", SearchFilters())

        assertIs<SearchLoadResult.Success>(result)
        assertTrue(result.results.publications.isEmpty())
    }

    @Test
    fun unavailableModeReturnsFailure() = runTest {
        val result = InMemorySearchRepository(mode = InMemorySearchMode.UNAVAILABLE, latencyMillis = 0)
            .search("becas", SearchFilters())

        assertEquals(SearchLoadResult.Failure, result)
    }

    @Test
    fun isoDateAcceptsWellFormedDates() {
        assertTrue(isIsoDate("2026-10-13"))
    }

    @Test
    fun isoDateRejectsMalformedDates() {
        assertFalse(isIsoDate("13/10/2026"))
        assertFalse(isIsoDate("2026-10"))
        assertFalse(isIsoDate(""))
    }

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): InMemorySearchRepository =
        InMemorySearchRepository(mode = InMemorySearchMode.SAMPLE, latencyMillis = 0)
}
