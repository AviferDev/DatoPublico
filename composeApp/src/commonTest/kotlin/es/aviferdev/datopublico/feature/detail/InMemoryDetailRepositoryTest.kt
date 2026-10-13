package es.aviferdev.datopublico.feature.detail

import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailMode
import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailRepository
import es.aviferdev.datopublico.feature.detail.data.sampleDetails
import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del repositorio en memoria del detalle: contenido determinista (una
 * convocatoria con plazo y una norma sin plazo, con resumen estructurado y fuente
 * oficial), los modos no encontrada/fallo y la correspondencia id → detalle.
 */
class InMemoryDetailRepositoryTest {
    @Test
    fun sampleModeReturnsDetailWithDeadlineAndSummary() = runTest {
        val result = sampleRepository().load(CONVOCATION_ID)

        assertIs<DetailLoadResult.Success>(result)
        assertNotNull(result.detail.deadline)
        assertTrue(result.detail.summary.whatChanges.isNotBlank())
        assertTrue(result.detail.summary.whoIsAffected.isNotBlank())
        assertTrue(result.detail.summary.keyFigures.isNotEmpty())
    }

    @Test
    fun sampleModeReturnsDetailWithoutDeadline() = runTest {
        val result = sampleRepository().load(REGULATION_ID)

        assertIs<DetailLoadResult.Success>(result)
        assertNull(result.detail.deadline)
    }

    @Test
    fun sampleModeReturnsNotFoundForUnknownId() = runTest {
        val result = sampleRepository().load("BOE-A-2026-99999")

        assertEquals(DetailLoadResult.NotFound, result)
    }

    @Test
    fun notFoundModeAlwaysReturnsNotFound() = runTest {
        val result = repositoryFor(InMemoryDetailMode.NOT_FOUND).load(CONVOCATION_ID)

        assertEquals(DetailLoadResult.NotFound, result)
    }

    @Test
    fun unavailableModeReturnsFailure() = runTest {
        val result = repositoryFor(InMemoryDetailMode.UNAVAILABLE).load(CONVOCATION_ID)

        assertEquals(DetailLoadResult.Failure, result)
    }

    @Test
    fun sampleDetailsAlwaysShowAiNoticeAndOfficialSource() {
        val details = sampleDetails()

        assertTrue(details.isNotEmpty())
        assertTrue(details.all { detail -> detail.showsAiNotice })
        assertTrue(details.all { detail -> detail.officialSourceUrl.startsWith("https://") })
    }

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): InMemoryDetailRepository =
        repositoryFor(InMemoryDetailMode.SAMPLE)

    /** Repositorio en memoria en el modo indicado, sin latencia. */
    private fun repositoryFor(mode: InMemoryDetailMode): InMemoryDetailRepository =
        InMemoryDetailRepository(mode = mode, latencyMillis = 0)

    private companion object {
        /** Convocatoria de ejemplo con plazo. */
        const val CONVOCATION_ID = "BOE-A-2026-21002"

        /** Norma de ejemplo sin plazo. */
        const val REGULATION_ID = "BOE-A-2026-21001"
    }
}
