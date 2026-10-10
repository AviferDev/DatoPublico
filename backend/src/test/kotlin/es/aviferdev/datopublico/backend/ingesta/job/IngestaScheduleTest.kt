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
class IngestaScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val schedule = IngestaSchedule(
        times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
        zone = zone,
    )

    @Test
    fun `desde las 09_00 el siguiente es 09_30 del mismo dia`() {
        assertEquals(at(9, 30), schedule.nextRun(at(9, 0)))
    }

    @Test
    fun `desde las 09_31 el siguiente es 18_00`() {
        assertEquals(at(18, 0), schedule.nextRun(at(9, 31)))
    }

    @Test
    fun `desde las 18_01 el siguiente es 09_30 del dia siguiente`() {
        assertEquals(at(9, 30).plusDays(1), schedule.nextRun(at(18, 1)))
    }

    @Test
    fun `el calculo es estricto y descarta el instante igual a now`() {
        assertEquals(at(18, 0), schedule.nextRun(at(9, 30)))
        assertEquals(at(9, 30).plusDays(1), schedule.nextRun(at(18, 0)))
    }

    @Test
    fun `la lista de horas se ordena antes de calcular`() {
        val desordenado = IngestaSchedule(
            times = listOf(LocalTime.of(18, 0), LocalTime.of(9, 30)),
            zone = zone,
        )

        assertEquals(at(9, 30), desordenado.nextRun(at(9, 0)))
    }

    /** Instante de referencia del 9 de octubre de 2026 en la zona del horario. */
    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 10, 9, hour, minute, 0, 0, zone)
}
