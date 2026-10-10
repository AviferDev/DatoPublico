package es.aviferdev.datopublico.backend.ingesta.job

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test de gate del cálculo del siguiente disparo (escenario 1 del spec), con la
 * ventana `{09:30, 18:00}` en `Europe/Madrid` y sin esperar tiempo real.
 */
class IngestionScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val schedule = IngestionSchedule(
        times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
        zone = zone,
    )

    @Test
    fun `from 09_00 the next run is 09_30 the same day`() {
        assertEquals(at(9, 30), schedule.nextRun(at(9, 0)))
    }

    @Test
    fun `from 09_31 the next run is 18_00`() {
        assertEquals(at(18, 0), schedule.nextRun(at(9, 31)))
    }

    @Test
    fun `from 18_01 the next run is 09_30 the next day`() {
        assertEquals(at(9, 30).plusDays(1), schedule.nextRun(at(18, 1)))
    }

    @Test
    fun `the calculation is strict and discards the instant equal to now`() {
        assertEquals(at(18, 0), schedule.nextRun(at(9, 30)))
        assertEquals(at(9, 30).plusDays(1), schedule.nextRun(at(18, 0)))
    }

    @Test
    fun `the list of times is sorted before computing`() {
        val unordered = IngestionSchedule(
            times = listOf(LocalTime.of(18, 0), LocalTime.of(9, 30)),
            zone = zone,
        )

        assertEquals(at(9, 30), unordered.nextRun(at(9, 0)))
    }

    /** Instante de referencia del 9 de octubre de 2026 en la zona del horario. */
    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 10, 9, hour, minute, 0, 0, zone)
}
