package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto
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
 * BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*BoeSumarioLiveTest'
 * ```
 */
class BoeSumarioLiveTest {

    @Test
    fun `la API real devuelve 200 y entradas de todas las secciones del BOE`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoeSumarioLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeSumarioHttpClient()
            try {
                val entradas = BoeSumarioHttpClient(httpClient).obtenerSumario(FECHA_CON_TODAS_LAS_SECCIONES)

                assertTrue(
                    entradas.isNotEmpty(),
                    "La API real no devolvió entradas para $FECHA_CON_TODAS_LAS_SECCIONES",
                )
                assertEquals(
                    SeccionBoeDto.entries.toSet(),
                    entradas.map { it.seccion }.toSet(),
                )
                assertTrue(entradas.all { it.identificador.isNotBlank() && it.urlOficial.isNotBlank() })
                assertTrue(entradas.all { it.urlPdf != null })
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
