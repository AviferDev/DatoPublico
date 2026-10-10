package es.aviferdev.datopublico.backend.ingesta.job

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Calcula el siguiente disparo del job a partir de una lista de horas y una zona.
 *
 * Es **puro y sin E/S**: recibe el instante actual y devuelve el próximo, lo que
 * hace verificable el horario (09:30 + segunda pasada) sin esperar tiempo real.
 *
 * @property times horas de disparo diarias; debe haber al menos una.
 * @property zone zona en la que se interpretan [times].
 */
class IngestionSchedule(
    private val times: List<LocalTime>,
    private val zone: ZoneId,
) {
    init {
        require(times.isNotEmpty()) { "El horario de ingesta necesita al menos una hora." }
    }

    /**
     * Devuelve el siguiente instante **estrictamente posterior** a [now] que cae
     * en alguna de las horas configuradas (hoy o, si ya pasaron todas, mañana).
     *
     * El cálculo es estricto: un instante **igual** a una hora configurada se
     * descarta; p. ej. si `now` es 09:30, el siguiente disparo es 18:00.
     *
     * @param now instante de referencia, en cualquier zona (se normaliza a
     *   [zone]).
     */
    fun nextRun(now: ZonedDateTime): ZonedDateTime {
        val inZone = now.withZoneSameInstant(zone)
        val date = inZone.toLocalDate()
        val candidate = times.sorted()
            .firstNotNullOfOrNull { time ->
                date.atTime(time).atZone(zone).takeIf { it.isAfter(inZone) }
            }
        return candidate ?: date.plusDays(1).atTime(times.min()).atZone(zone)
    }
}
