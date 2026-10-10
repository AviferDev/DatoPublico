package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay as coroutinesDelay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Vigilante interno que alerta cuando una ventana de ingesta se queda sin
 * ejecutar.
 *
 * Es un bucle de corrutina propio (sin el plugin de *scheduling* de Ktor) que
 * cada [interval] calcula, con [MissedIngestionDetector], las ventanas perdidas y
 * llama a [IngestionAlertSink.alert] **una sola vez por ventana** (deduplicación
 * por instante). El reloj ([now]) y la espera ([delay]) son inyectables para
 * probarlo con **tiempo virtual**.
 *
 * Una excepción en un ciclo **no** mata el bucle: se registra y se continúa. Si
 * el proceso no está en marcha no puede alertar; la recuperación de una ventana
 * perdida es del backfill (FT00011).
 *
 * @param schedule horario configurado (fija las ventanas y su zona).
 * @param runState ventanas ya intentadas por el scheduler.
 * @param grace margen tras una ventana antes de considerarla perdida.
 * @param sink destino de la alerta; por defecto un log estructurado `ERROR`.
 * @param interval periodo entre comprobaciones.
 * @param startedAt instante de arranque; las ventanas anteriores no se reclaman.
 * @param now fuente del instante actual; por defecto [Instant.now].
 * @param delay espera inyectable; por defecto `kotlinx.coroutines.delay`.
 */
class IngestionWatchdog(
    private val schedule: IngestionSchedule,
    private val runState: IngestionRunState,
    private val grace: Duration,
    private val sink: IngestionAlertSink = LoggingAlertSink(grace),
    private val interval: Duration = DEFAULT_INTERVAL,
    private val startedAt: Instant = Instant.now(),
    private val now: () -> Instant = Instant::now,
    private val delay: suspend (Long) -> Unit = { millis -> coroutinesDelay(millis) },
) {
    private val logger = LoggerFactory.getLogger(IngestionWatchdog::class.java)
    private val alerted: MutableSet<Instant> = ConcurrentHashMap.newKeySet()

    /**
     * Lanza el bucle del vigilante en [scope] y devuelve su [Job].
     *
     * El bucle se detiene al cancelar [scope] o el [Job] devuelto.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(interval.toMillis())
            tick()
        }
    }

    /** Ejecuta un ciclo protegiendo el bucle: un fallo no lo cancela. */
    private fun tick() {
        try {
            alertMissed()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logger.warn("Vigilante de ingesta: fallo al comprobar ventanas: {}", error.message)
        }
    }

    /** Calcula y emite, sin repetir, la alerta de cada ventana perdida. */
    private fun alertMissed() {
        val current = ZonedDateTime.ofInstant(now(), schedule.zone)
        MissedIngestionDetector.detect(
            now = current,
            startedAt = startedAt,
            schedule = schedule,
            isAttempted = runState::isAttempted,
            grace = grace,
        ).forEach { window ->
            if (alerted.add(window.toInstant())) {
                sink.alert(window)
            }
        }
    }

    companion object {
        /** Periodo por defecto entre comprobaciones (5 minutos). */
        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
