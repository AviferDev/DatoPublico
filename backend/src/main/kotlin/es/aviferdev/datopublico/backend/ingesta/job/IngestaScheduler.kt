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
 * Bucle de corrutina que dispara [IngestaJob] en las horas de [IngestaSchedule].
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
class IngestaScheduler(
    private val schedule: IngestaSchedule,
    private val job: IngestaJob,
    private val zone: ZoneId,
    private val now: () -> Instant = Instant::now,
    private val delay: suspend (Long) -> Unit = { millis -> coroutinesDelay(millis) },
) {
    private val logger = LoggerFactory.getLogger(IngestaScheduler::class.java)

    /**
     * Lanza el bucle en [scope] y devuelve su [Job].
     *
     * El bucle se detiene al cancelar [scope] (o el [Job] devuelto).
     */
    fun iniciar(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val ahora = ZonedDateTime.ofInstant(now(), zone)
            val proximo = schedule.nextRun(ahora)
            delay(Duration.between(ahora, proximo).toMillis().coerceAtLeast(0))
            ejecutarVentana(proximo)
        }
    }

    /** Ejecuta el job para la fecha local de [instante] y registra el resumen. */
    private suspend fun ejecutarVentana(instante: ZonedDateTime) {
        val fecha = instante.withZoneSameInstant(zone).toLocalDate()
        val resultado = try {
            job.ejecutar(fecha)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logger.warn("Ingesta de {} falló: {}", fecha, error.message)
            null
        }
        resultado?.let { resumen -> registrarResumen(resumen) }
    }

    /** Deja un log-resumen mínimo de la ejecución (sin observabilidad rica). */
    private fun registrarResumen(resumen: IngestaResult) {
        logger.info(
            "Ingesta de {}: total={}, guardadas={}, fallidas={}, error={}",
            resumen.fecha,
            resumen.totalEntradas,
            resumen.guardadas,
            resumen.fallidas,
            resumen.error,
        )
    }
}
