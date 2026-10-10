package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test de gate del detector **puro** de ventanas perdidas (escenarios 3–6 del
 * spec) con el horario `{09:30, 18:00}` en `Europe/Madrid` y un reloj fijo: cubre
 * la ventana perdida, la ausencia de falsos positivos, la ventana anterior al
 * arranque y la que aún está dentro de su gracia.
 */
class MissedIngestionDetectorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val date: LocalDate = LocalDate.of(2026, 10, 9)
    private val schedule = IngestionSchedule(
        times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
        zone = zone,
    )
    private val grace: Duration = Duration.ofMinutes(30)

    @Test
    fun `reports the afternoon window when it was not attempted`() {
        val missed = detect(
            now = at(19, 0),
            startedAt = at(8, 0).toInstant(),
            attempted = listOf(at(9, 30)),
        )

        assertEquals(listOf(at(18, 0)), missed)
    }

    @Test
    fun `reports nothing when both windows were attempted`() {
        val missed = detect(
            now = at(19, 0),
            startedAt = at(8, 0).toInstant(),
            attempted = listOf(at(9, 30), at(18, 0)),
        )

        assertEquals(emptyList(), missed)
    }

    @Test
    fun `ignores windows before the process started`() {
        val missed = detect(
            now = at(19, 0),
            startedAt = at(10, 0).toInstant(),
            attempted = emptyList(),
        )

        assertEquals(listOf(at(18, 0)), missed)
    }

    @Test
    fun `ignores a window still inside its grace period`() {
        val missed = detect(
            now = at(18, 15),
            startedAt = at(8, 0).toInstant(),
            attempted = listOf(at(9, 30)),
        )

        assertEquals(emptyList(), missed)
    }

    /** Ejecuta el detector con el horario, la gracia y las ventanas de prueba. */
    private fun detect(
        now: ZonedDateTime,
        startedAt: Instant,
        attempted: List<ZonedDateTime>,
    ): List<ZonedDateTime> {
        val attemptedInstants = attempted.map { window -> window.toInstant() }.toSet()
        return MissedIngestionDetector.detect(
            now = now,
            startedAt = startedAt,
            schedule = schedule,
            isAttempted = { window -> window in attemptedInstants },
            grace = grace,
        )
    }

    /** Instante del 9 de octubre de 2026 a la hora local indicada. */
    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)
}
