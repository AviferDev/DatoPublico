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
    fun `la API real devuelve 200 y entradas no vacias de las secciones del corpus`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoeSumarioLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeSumarioHttpClient()
            try {
                val entradas = BoeSumarioHttpClient(httpClient).obtenerSumario(FECHA_LABORABLE)

                assertTrue(entradas.isNotEmpty(), "La API real no devolvió entradas para $FECHA_LABORABLE")
                assertEquals(
                    setOf(
                        SeccionBoeDto.I,
                        SeccionBoeDto.II_A,
                        SeccionBoeDto.II_B,
                        SeccionBoeDto.III,
                        SeccionBoeDto.V_B,
                    ),
                    entradas.map { it.seccion }.toSet(),
                )
                assertTrue(entradas.all { it.identificador.isNotBlank() && it.urlOficial.isNotBlank() })
            } finally {
                httpClient.close()
            }
        }
    }

    private companion object {
        val FECHA_LABORABLE: LocalDate = LocalDate.of(2026, 10, 9)
    }
}
