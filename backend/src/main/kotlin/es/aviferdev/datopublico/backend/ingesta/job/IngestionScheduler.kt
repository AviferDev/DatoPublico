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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Bucle de corrutina que dispara [IngestionJob] en las horas de [IngestionSchedule].
 *
 * No usa el plugin experimental de *scheduling* de Ktor: es una corrutina propia,
 * sin dependencias nuevas. El reloj ([now]) y la espera ([delay]) son inyectables
 * para poder probar el disparo con **tiempo virtual** sin esperar en real.
 *
 * Antes de ejecutar cada ventana la marca en [runState] (para que el vigilante no
 * la confunda con una ingesta ausente) y, al terminar, emite el **evento
 * estructurado** de la ejecución con [IngestionLog.logRun].
 *
 * Una excepción del job **no** mata el bucle: se registra y se continúa con la
 * siguiente ventana.
 *
 * @param schedule cálculo del siguiente disparo.
 * @param job orquestador de la ingesta.
 * @param zone zona horaria para fechar la ejecución.
 * @param runState ventanas ya intentadas, compartido con el vigilante.
 * @param now fuente del instante actual; por defecto [Instant.now].
 * @param delay espera inyectable; por defecto `kotlinx.coroutines.delay`.
 * @param logger logger de la ejecución; por defecto el del propio scheduler.
 */
class IngestionScheduler(
    private val schedule: IngestionSchedule,
    private val job: IngestionJob,
    private val zone: ZoneId,
    private val runState: IngestionRunState,
    private val now: () -> Instant = Instant::now,
    private val delay: suspend (Long) -> Unit = { millis -> coroutinesDelay(millis) },
    private val logger: Logger = LoggerFactory.getLogger(IngestionScheduler::class.java),
) {

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

    /**
     * Marca la ventana como intentada y ejecuta el job para su fecha local.
     *
     * La marca es **previa** a la ejecución: una pasada larga no debe confundirse
     * con una ingesta ausente. El resultado (o su ausencia por excepción) se
     * registra con el evento estructurado.
     */
    private suspend fun runWindow(window: ZonedDateTime) {
        runState.markAttempted(window.toInstant())
        val date = window.withZoneSameInstant(zone).toLocalDate()
        val result = try {
            job.run(date)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logger.warn("Ingesta de {} falló: {}", date, error.message)
            null
        }
        result?.let { summary -> IngestionLog.logRun(logger, summary) }
    }
}
