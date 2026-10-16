package es.aviferdev.datopublico.backend.ingestion.summary

import es.aviferdev.datopublico.model.BoeSectionDto
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** contra la API real del BOE (escenario 6 del spec).
 *
 * Se salta por defecto (`BOE_LIVE_TEST != 1`) para que el gate y la CI **no**
 * hagan llamadas de red. Para ejecutarla:
 *
 * ```
 * BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*BoeSummaryLiveTest'
 * ```
 */
class BoeSummaryLiveTest {

    @Test
    fun `the real API returns 200 and entries from all BOE sections`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoeSummaryLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeSummaryHttpClient()
            try {
                val entries = BoeSummaryHttpClient(httpClient).fetchSummary(DATE_WITH_ALL_SECTIONS)

                assertTrue(
                    entries.isNotEmpty(),
                    "La API real no devolvió entradas para $DATE_WITH_ALL_SECTIONS",
                )
                assertEquals(
                    BoeSectionDto.entries.toSet(),
                    entries.map { it.section }.toSet(),
                )
                assertTrue(entries.all { it.identifier.isNotBlank() && it.officialUrl.isNotBlank() })
                assertTrue(entries.all { it.pdfUrl != null })
            } finally {
                httpClient.close()
            }
        }
    }

    private companion object {
        /** 2024-01-09 es un día laborable con las ocho secciones del sumario. */
        val DATE_WITH_ALL_SECTIONS: LocalDate = LocalDate.of(2024, 1, 9)
    }
}
