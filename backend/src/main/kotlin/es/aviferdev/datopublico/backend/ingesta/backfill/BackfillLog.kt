package es.aviferdev.datopublico.backend.ingesta.backfill

import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger

/**
 * Eventos de log **estructurados** del backfill.
 *
 * Emite una línea JSON con campos de primer nivel (no dentro de `message`) para
 * que un monitor filtre por la clave estable `event`. Los mapeos de campos
 * ([startedFields], [batchFields], [completedFields], [dateFailedFields]) son
 * **puros** y son la unidad verificable sin Logback; la emisión fija el nivel:
 * `INFO` para el ciclo normal y `WARN` para una fecha fallida (que se reintenta
 * en el siguiente run).
 *
 * Sin telemetría ni destino externo: el canal es el stdout del proceso.
 */
object BackfillLog {
    /** Evento de arranque de un backfill. */
    const val EVENT_RUN_STARTED: String = "backfill.run.started"

    /** Evento de un lote completado. */
    const val EVENT_BATCH_COMPLETED: String = "backfill.batch.completed"

    /** Evento de un backfill completado. */
    const val EVENT_RUN_COMPLETED: String = "backfill.run.completed"

    /** Evento de una fecha fallida (se reintenta en el siguiente run). */
    const val EVENT_DATE_FAILED: String = "backfill.date.failed"

    /** Clave del identificador de evento (estable, para filtrar en el monitor). */
    const val FIELD_EVENT: String = "event"

    /** Primera fecha del rango, ISO-8601. */
    const val FIELD_FROM: String = "from"

    /** Última fecha del rango, ISO-8601. */
    const val FIELD_TO: String = "to"

    /** Número total de fechas del rango. */
    const val FIELD_TOTAL_DATES: String = "total_dates"

    /** Índice (0-based) del lote. */
    const val FIELD_BATCH_INDEX: String = "batch_index"

    /** Fechas del lote. */
    const val FIELD_BATCH_SIZE: String = "batch_size"

    /** Fechas completadas en el lote. */
    const val FIELD_COMPLETED: String = "completed"

    /** Fechas fallidas en el lote o en el run. */
    const val FIELD_FAILED: String = "failed"

    /** Fechas omitidas por estar ya completadas en el checkpoint. */
    const val FIELD_SKIPPED: String = "skipped"

    /** Publicaciones persistidas en el run. */
    const val FIELD_SAVED: String = "saved"

    /** Fecha fallida, ISO-8601. */
    const val FIELD_BACKFILL_DATE: String = "backfill_date"

    /** Motivo del fallo de una fecha. */
    const val FIELD_ERROR: String = "error"

    /** Campos del evento de arranque. */
    fun startedFields(
        config: BackfillConfig,
        totalDates: Int,
        skipped: Int,
    ): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_RUN_STARTED,
        FIELD_FROM to config.from.toString(),
        FIELD_TO to config.to.toString(),
        FIELD_TOTAL_DATES to totalDates,
        FIELD_SKIPPED to skipped,
    )

    /** Campos del evento de lote completado. */
    fun batchFields(
        batchIndex: Int,
        batchSize: Int,
        completed: Int,
        failed: Int,
    ): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_BATCH_COMPLETED,
        FIELD_BATCH_INDEX to batchIndex,
        FIELD_BATCH_SIZE to batchSize,
        FIELD_COMPLETED to completed,
        FIELD_FAILED to failed,
    )

    /** Campos del evento de backfill completado. */
    fun completedFields(result: BackfillResult): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_RUN_COMPLETED,
        FIELD_COMPLETED to result.completed,
        FIELD_FAILED to result.failed,
        FIELD_SKIPPED to result.skipped,
        FIELD_SAVED to result.saved,
    )

    /** Campos del evento de fecha fallida. */
    fun dateFailedFields(date: LocalDate, reason: String): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_DATE_FAILED,
        FIELD_BACKFILL_DATE to date.toString(),
        FIELD_ERROR to reason,
    )

    /** Emite el evento de arranque en [logger]. */
    fun logStarted(logger: Logger, config: BackfillConfig, totalDates: Int, skipped: Int) {
        logger.info(
            "Backfill iniciado",
            StructuredArguments.entries(startedFields(config, totalDates, skipped)),
        )
    }

    /** Emite el evento de lote completado en [logger]. */
    fun logBatchCompleted(
        logger: Logger,
        batchIndex: Int,
        batchSize: Int,
        completed: Int,
        failed: Int,
    ) {
        logger.info(
            "Lote de backfill completado",
            StructuredArguments.entries(batchFields(batchIndex, batchSize, completed, failed)),
        )
    }

    /** Emite el evento de backfill completado en [logger]. */
    fun logCompleted(logger: Logger, result: BackfillResult) {
        logger.info(
            "Backfill completado",
            StructuredArguments.entries(completedFields(result)),
        )
    }

    /** Emite el evento de fecha fallida (nivel `WARN`) en [logger]. */
    fun logDateFailed(logger: Logger, date: LocalDate, reason: String) {
        logger.warn(
            "Fecha de backfill fallida",
            StructuredArguments.entries(dateFailedFields(date, reason)),
        )
    }
}
