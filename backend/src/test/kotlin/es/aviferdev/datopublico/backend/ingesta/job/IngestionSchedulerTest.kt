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
 * Test de gate del [IngestionScheduler] con **tiempo virtual** (escenario 5): el
 * reloj y la espera son inyectables, así que no se espera en real. Se comprueba
 * que dispara una vez por ventana y que un fallo del job no mata el bucle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestionSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    @Test
    fun `fires once per window and survives a job failure`() = runTest {
        val executions = mutableListOf<LocalDate>()
        val job = object : IngestionJob {
            override suspend fun run(date: LocalDate): IngestionResult {
                executions += date
                if (executions.size == 1) {
                    throw IllegalStateException("fallo simulado del job")
                }
                return IngestionResult(date, totalEntries = 0, saved = 0, failed = 0)
            }
        }
        val scheduler = IngestionScheduler(
            schedule = IngestionSchedule(
                times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
                zone = zone,
            ),
            job = job,
            zone = zone,
            now = { Instant.ofEpochMilli(testScheduler.currentTime) },
            delay = { millis -> delay(millis) },
        )

        val loop = scheduler.start(this)
        runCurrent()
        advanceTimeBy(WINDOW_OFFSET_MS)
        runCurrent()
        advanceTimeBy(WINDOW_OFFSET_MS)
        runCurrent()
        loop.cancel()

        assertEquals(2, executions.size, "una ejecución por ventana")
        assertEquals(executions[0], executions[1])
    }

    private companion object {
        /**
         * Separación en milisegundos entre las ventanas 09:30 y 18:00 (8,5 h). El
         * reloj virtual arranca en el epoch, así que la primera ventana cae en la
         * mañana del 1 de enero de 1970 en `Europe/Madrid`.
         */
        const val WINDOW_OFFSET_MS = 30_600_000L
    }
}
