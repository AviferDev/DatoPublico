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
    fun `la API real devuelve 200 y entries de todas las secciones del BOE`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoeSummaryLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeSummaryHttpClient()
            try {
                val entries = BoeSummaryHttpClient(httpClient).fetchSummary(FECHA_CON_TODAS_LAS_SECCIONES)

                assertTrue(
                    entries.isNotEmpty(),
                    "La API real no devolvió entradas para $FECHA_CON_TODAS_LAS_SECCIONES",
                )
                assertEquals(
                    BoeSectionDto.entries.toSet(),
                    entries.map { it.section }.toSet(),
                )
                assertTrue(entries.all { it.identifier.isNotBlank() && it.officialUrl.isNotBlank() })
                assertTrue(entries.all { it.urlPdf != null })
            } finally {
                httpClient.close()
            }
        }
    }

    private companion object {
        /** 2024-01-09 es un día laborable con las ocho secciones del sumario. */
        val FECHA_CON_TODAS_LAS_SECCIONES: LocalDate = LocalDate.of(2024, 1, 9)
    }
}
