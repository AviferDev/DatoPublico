package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Detecta las **ventanas perdidas** de ingesta de un día.
 *
 * Es **puro y sin E/S**: recibe el instante actual, el arranque del proceso, el
 * horario, el predicado de «ventana intentada» y el margen de gracia, y devuelve
 * las ventanas que se debieron ejecutar y no se intentaron. La ausencia de E/S
 * lo hace determinista y verificable con reloj virtual.
 *
 * Una ventana `w` se considera perdida si:
 * 1. ya venció con su gracia: `w <= now - grace`,
 * 2. era posterior al arranque: `w >= startedAt` (las anteriores no se reclaman,
 *    el proceso no estaba en marcha), y
 * 3. no se intentó: `!isAttempted(w)`.
 */
object MissedIngestionDetector {

    /**
     * Calcula las ventanas perdidas del día local de [now].
     *
     * @param now instante de referencia (su zona fija el día y el orden del
     *   horario).
     * @param startedAt instante en que arrancó el proceso; las ventanas
     *   anteriores no se reclaman.
     * @param schedule horario configurado.
     * @param isAttempted predicado que indica si una ventana ya se intentó.
     * @param grace margen tras la ventana antes de considerarla perdida.
     * @return ventanas perdidas, ordenadas de menor a mayor.
     */
    fun detect(
        now: ZonedDateTime,
        startedAt: Instant,
        schedule: IngestionSchedule,
        isAttempted: (Instant) -> Boolean,
        grace: Duration,
    ): List<ZonedDateTime> {
        val deadline = now.toInstant().minus(grace)
        return schedule.runsOn(now.toLocalDate()).filter { window ->
            val instant = window.toInstant()
            !instant.isAfter(deadline) && !instant.isBefore(startedAt) && !isAttempted(instant)
        }
    }
}
