package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.SummaryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests **sin base de datos** del codec JSON puro de la caché (gate): el
 * `SummaryDto` guardado en la columna `resumen` hace *round-trip* con todos sus
 * campos (incluido el plazo) y un JSON inválido falla en claro.
 */
class SummaryCachePersistenceTest {

    @Test
    fun `round trips a full summary`() {
        val summary = fullSummary()

        val retrieved = decodeSummaryCache(encodeSummaryCache(summary))

        assertEquals(summary, retrieved)
    }

    @Test
    fun `round trips a summary without a deadline`() {
        val summary = fullSummary().copy(deadline = null)

        val retrieved = decodeSummaryCache(encodeSummaryCache(summary))

        assertEquals(summary, retrieved)
    }

    @Test
    fun `fails fast on a malformed json`() {
        assertFailsWith<Exception> { decodeSummaryCache("no es json") }
    }

    private fun fullSummary(): SummaryDto = SummaryDto(
        whatChanges = "Cambia el plazo",
        whoIsAffected = "A la ciudadanía",
        keyFigures = listOf("30 días", "1.000 euros"),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        aiNotice = true,
        deadline = DeadlineDto(deadlineDate = "2026-11-30", description = "Diez días hábiles"),
    )
}
