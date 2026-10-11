package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.ResumenDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests **sin base de datos** del codec JSON puro de la caché (gate): el
 * `ResumenDto` guardado en la columna `resumen` hace *round-trip* con todos sus
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
        val summary = fullSummary().copy(plazo = null)

        val retrieved = decodeSummaryCache(encodeSummaryCache(summary))

        assertEquals(summary, retrieved)
    }

    @Test
    fun `fails fast on a malformed json`() {
        assertFailsWith<Exception> { decodeSummaryCache("no es json") }
    }

    private fun fullSummary(): ResumenDto = ResumenDto(
        queCambia = "Cambia el plazo",
        aQuienAfecta = "A la ciudadanía",
        cifrasClave = listOf("30 días", "1.000 euros"),
        fuenteOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        avisoIA = true,
        plazo = PlazoDto(fechaLimite = "2026-11-30", descripcion = "Diez días hábiles"),
    )
}
