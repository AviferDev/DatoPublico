package es.aviferdev.datopublico.backend.ingesta.backfill

import es.aviferdev.datopublico.backend.ingesta.job.IngestionJob
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay as coroutinesDelay
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Resultado agregado de una ejecución del backfill.
 *
 * @property completed fechas completadas en **este** run.
 * @property failed fechas que fallaron en este run (se reintentan en el
 *   siguiente).
 * @property skipped fechas omitidas por estar ya completadas en el checkpoint.
 * @property saved publicaciones persistidas en este run (*upsert*).
 */
data class BackfillResult(
    val completed: Int,
    val failed: Int,
    val skipped: Int,
    val saved: Int,
)

/**
 * Orquestador reanudable del backfill: recorre el rango en lotes y reutiliza el
 * [IngestionJob] ya existente (no reimplementa la ingesta).
 *
 * Por cada fecha no completada llama a `job.run(date)` en [ioDispatcher], respeta
 * la pausa [delay] entre fechas y persiste el checkpoint tras cada lote. Una
 * fecha cuenta como **completada** solo si el job no reporta error **y** no deja
 * entradas fallidas; si no, se registra y se reintenta en el siguiente run. El
 * fallo de una fecha **nunca** aborta el run.
 *
 * @param job orquestador de la ingesta diaria (sumario → parser → *upsert*).
 * @param store almacén del checkpoint de progreso.
 * @param delay pausa entre fechas; inyectable para probar con tiempo virtual.
 * @param ioDispatcher dispatcher para el trabajo de E/S (job y checkpoint).
 * @param logger logger de los eventos estructurados.
 */
class BackfillRunner(
    private val job: IngestionJob,
    private val store: BackfillCheckpointStore,
    private val delay: suspend (Long) -> Unit = { millis -> coroutinesDelay(millis) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: Logger = LoggerFactory.getLogger(BackfillRunner::class.java),
) {

    /**
     * Ejecuta el backfill de [config] y devuelve su resumen.
     *
     * Reanuda desde el checkpoint **solo** si su rango coincide con [config]; en
     * caso contrario empieza de cero.
     */
    suspend fun run(config: BackfillConfig): BackfillResult {
        val range = config.range
        val dates = range.dates()
        val completedDates = loadCompleted(config, dates)
        val skipped = completedDates.size
        BackfillLog.logStarted(logger, config, dates.size, skipped)
        val state = RunState()
        for ((index, batch) in range.batches(config.batchDays).withIndex()) {
            processBatch(config, batch, completedDates, index, state)
            persistCheckpoint(config, completedDates)
        }
        val result = BackfillResult(
            completed = state.completed,
            failed = state.failed,
            skipped = skipped,
            saved = state.saved,
        )
        BackfillLog.logCompleted(logger, result)
        return result
    }

    /** Fechas del checkpoint que caen dentro del rango; vacío si no encaja. */
    private suspend fun loadCompleted(
        config: BackfillConfig,
        dates: List<LocalDate>,
    ): MutableSet<LocalDate> {
        val saved = withContext(ioDispatcher) { store.load() }
        val inRange = saved?.takeIf { it.from == config.from && it.to == config.to }
            ?.completedDates.orEmpty()
        return inRange.intersect(dates.toSet()).toMutableSet()
    }

    /** Procesa las fechas pendientes de un lote y emite su evento de cierre. */
    private suspend fun processBatch(
        config: BackfillConfig,
        batch: List<LocalDate>,
        completedDates: MutableSet<LocalDate>,
        index: Int,
        state: RunState,
    ) {
        var batchCompleted = 0
        var batchFailed = 0
        for (date in batch) {
            if (date in completedDates) {
                continue
            }
            pauseBeforeNext(config, state)
            val outcome = runDate(date)
            state.saved += outcome.saved
            if (outcome.completed) {
                completedDates.add(date)
                state.completed += 1
                batchCompleted += 1
            } else {
                state.failed += 1
                batchFailed += 1
                BackfillLog.logDateFailed(logger, date, outcome.reason)
            }
        }
        BackfillLog.logBatchCompleted(logger, index, batch.size, batchCompleted, batchFailed)
    }

    /** Respeta la pausa configurada entre fechas (no antes de la primera). */
    private suspend fun pauseBeforeNext(config: BackfillConfig, state: RunState) {
        if (state.executed > 0) {
            delay(config.delayMillis)
        }
        state.executed += 1
    }

    /** Ejecuta el job para [date] en E/S; nunca propaga el fallo de la fecha. */
    private suspend fun runDate(date: LocalDate): DateOutcome = try {
        val result = withContext(ioDispatcher) { job.run(date) }
        DateOutcome(
            completed = result.error == null && result.failed == 0,
            saved = result.saved,
            reason = result.error ?: "${result.failed} entradas fallidas",
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        DateOutcome(
            completed = false,
            saved = 0,
            reason = error.message ?: "fallo al procesar $date",
        )
    }

    /** Guarda el checkpoint con las fechas completadas hasta ahora. */
    private suspend fun persistCheckpoint(
        config: BackfillConfig,
        completedDates: Set<LocalDate>,
    ) {
        val checkpoint = BackfillCheckpoint(config.from, config.to, completedDates.toSet())
        withContext(ioDispatcher) { store.save(checkpoint) }
    }
}

/** Resultado de una fecha: completada o fallida, con las publicaciones guardadas. */
private data class DateOutcome(
    val completed: Boolean,
    val saved: Int,
    val reason: String,
)

/** Contadores de un run; agrupa el estado mutable del bucle del runner. */
private class RunState {
    var completed: Int = 0
    var failed: Int = 0
    var saved: Int = 0
    var executed: Int = 0
}
