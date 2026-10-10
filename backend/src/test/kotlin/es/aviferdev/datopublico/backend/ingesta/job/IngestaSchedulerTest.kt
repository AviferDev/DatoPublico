package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Test de gate del [IngestaScheduler] con **tiempo virtual** (escenario 5): el
 * reloj y la espera son inyectables, así que no se espera en real. Se comprueba
 * que dispara una vez por ventana y que un fallo del job no mata el bucle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestaSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    @Test
    fun `dispara una vez por ventana y sobrevive a un fallo del job`() = runTest {
        val ejecuciones = mutableListOf<LocalDate>()
        val job = object : IngestaJob {
            override suspend fun ejecutar(fecha: LocalDate): IngestaResult {
                ejecuciones += fecha
                if (ejecuciones.size == 1) {
                    throw IllegalStateException("fallo simulado del job")
                }
                return IngestaResult(fecha, totalEntradas = 0, guardadas = 0, fallidas = 0)
            }
        }
        val scheduler = IngestaScheduler(
            schedule = IngestaSchedule(
                times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
                zone = zone,
            ),
            job = job,
            zone = zone,
            now = { Instant.ofEpochMilli(testScheduler.currentTime) },
            delay = { millis -> delay(millis) },
        )

        val bucle = scheduler.iniciar(this)
        runCurrent()
        advanceTimeBy(DESFASE_VENTANA_MS)
        runCurrent()
        advanceTimeBy(DESFASE_VENTANA_MS)
        runCurrent()
        bucle.cancel()

        assertEquals(2, ejecuciones.size, "una ejecución por ventana")
        assertEquals(ejecuciones[0], ejecuciones[1])
    }

    private companion object {
        /**
         * Separación en milisegundos entre las ventanas 09:30 y 18:00 (8,5 h). El
         * reloj virtual arranca en el epoch, así que la primera ventana cae en la
         * mañana del 1 de enero de 1970 en `Europe/Madrid`.
         */
        const val DESFASE_VENTANA_MS = 30_600_000L
    }
}
