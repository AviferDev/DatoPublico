package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Test de gate del [IngestionWatchdog] con **tiempo virtual** (escenario 7 del
 * spec): con solo la ventana de 09:30 intentada, al avanzar más allá de
 * `18:00 + grace` el sumidero recibe **una única** alerta para las 18:00 aunque
 * se ejecuten varios ciclos. Se comprueba además que el bucle no muere si el
 * sumidero falla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestionWatchdogTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val grace: Duration = Duration.ofMinutes(30)

    @Test
    fun `alerts once per missed window across several ticks`() = runTest {
        val windows = windowsOfFirstDay()
        val runState = IngestionRunState().apply { markAttempted(windows[0].toInstant()) }
        val alerts = mutableListOf<ZonedDateTime>()
        val watchdog = watchdog(runState = runState, sink = recording(alerts))

        val loop = watchdog.start(this)
        advanceTimeBy(ADVANCE_MS)
        runCurrent()
        loop.cancel()

        assertEquals(listOf(windows[1]), alerts)
    }

    @Test
    fun `keeps running when the sink throws`() = runTest {
        val window = windowsOfFirstDay()[0].toInstant()
        val runState = IngestionRunState().apply { markAttempted(window) }
        val failing = IngestionAlertSink { throw IllegalStateException("sumidero caído") }
        val watchdog = watchdog(runState = runState, sink = failing)

        val loop = watchdog.start(this)
        advanceTimeBy(ADVANCE_MS)
        runCurrent()

        assertTrue(loop.isActive)
        loop.cancel()
    }

    /** Construye un vigilante con reloj y espera virtuales y arranque en el epoch. */
    private fun TestScope.watchdog(
        runState: IngestionRunState,
        sink: IngestionAlertSink,
    ): IngestionWatchdog = IngestionWatchdog(
        schedule = schedule(),
        runState = runState,
        grace = grace,
        sink = sink,
        startedAt = Instant.EPOCH,
        now = { Instant.ofEpochMilli(testScheduler.currentTime) },
        delay = { millis -> delay(millis) },
    )

    /** Sumidero de prueba que acumula las ventanas alertadas. */
    private fun recording(alerts: MutableList<ZonedDateTime>): IngestionAlertSink =
        IngestionAlertSink { window -> alerts += window }

    private fun schedule(): IngestionSchedule = IngestionSchedule(
        times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
        zone = zone,
    )

    /** Ventanas del 1 de enero de 1970, día del reloj virtual (epoch) en Madrid. */
    private fun windowsOfFirstDay(): List<ZonedDateTime> =
        schedule().runsOn(LocalDate.of(1970, 1, 1))

    private companion object {
        /** Avance de 20 h: cubre la ventana de 18:00 + gracia con varios ciclos. */
        const val ADVANCE_MS = 72_000_000L
    }
}
