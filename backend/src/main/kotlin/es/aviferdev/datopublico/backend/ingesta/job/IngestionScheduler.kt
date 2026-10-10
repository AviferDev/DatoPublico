package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay as coroutinesDelay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Bucle de corrutina que dispara [IngestionJob] en las horas de [IngestionSchedule].
 *
 * No usa el plugin experimental de *scheduling* de Ktor: es una corrutina propia,
 * sin dependencias nuevas. El reloj ([now]) y la espera ([delay]) son inyectables
 * para poder probar el disparo con **tiempo virtual** sin esperar en real.
 *
 * Una excepción del job **no** mata el bucle: se registra y se continúa con la
 * siguiente ventana. Al despertar se registra un **log-resumen** mínimo (fecha,
 * total, guardadas y fallidas); la observabilidad rica llega con FT00010.
 *
 * @param schedule cálculo del siguiente disparo.
 * @param job orquestador de la ingesta.
 * @param zone zona horaria para fechar la ejecución.
 * @param now fuente del instante actual; por defecto [Instant.now].
 * @param delay espera inyectable; por defecto `kotlinx.coroutines.delay`.
 */
class IngestionScheduler(
    private val schedule: IngestionSchedule,
    private val job: IngestionJob,
    private val zone: ZoneId,
    private val now: () -> Instant = Instant::now,
    private val delay: suspend (Long) -> Unit = { millis -> coroutinesDelay(millis) },
) {
    private val logger = LoggerFactory.getLogger(IngestionScheduler::class.java)

    /**
     * Lanza el bucle en [scope] y devuelve su [Job].
     *
     * El bucle se detiene al cancelar [scope] (o el [Job] devuelto).
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val current = ZonedDateTime.ofInstant(now(), zone)
            val next = schedule.nextRun(current)
            delay(Duration.between(current, next).toMillis().coerceAtLeast(0))
            runWindow(next)
        }
    }

    /** Ejecuta el job para la fecha local de [instant] y registra el resumen. */
    private suspend fun runWindow(instant: ZonedDateTime) {
        val date = instant.withZoneSameInstant(zone).toLocalDate()
        val result = try {
            job.run(date)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logger.warn("Ingesta de {} falló: {}", date, error.message)
            null
        }
        result?.let { summary -> logSummary(summary) }
    }

    /** Deja un log-resumen mínimo de la ejecución (sin observabilidad rica). */
    private fun logSummary(summary: IngestionResult) {
        logger.info(
            "Ingesta de {}: total={}, guardadas={}, fallidas={}, error={}",
            summary.date,
            summary.totalEntries,
            summary.saved,
            summary.failed,
            summary.error,
        )
    }
}
