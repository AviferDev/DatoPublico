package es.aviferdev.datopublico.backend.rag.evaluation

import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger

/**
 * Eventos de log **estructurados** de la evaluación de resúmenes (FT00024).
 *
 * Emite una línea JSON con campos de primer nivel (no dentro de `message`) para
 * que un monitor filtre por la clave estable `event`. Los mapeos de campos
 * ([startedFields], [completedFields]) son **puros** y son la unidad verificable
 * sin Logback; la emisión fija el nivel `INFO`. Sin telemetría ni destino externo:
 * el canal es el stdout del proceso (mismo patrón que `RetrievalEvaluationLog`).
 */
object SummaryEvaluationLog {
    /** Evento de arranque de una evaluación de resúmenes. */
    const val EVENT_STARTED: String = "summary.evaluation.started"

    /** Evento de evaluación de resúmenes completada. */
    const val EVENT_COMPLETED: String = "summary.evaluation.completed"

    /** Clave del identificador de evento (estable, para filtrar en el monitor). */
    const val FIELD_EVENT: String = "event"

    /** Versión del golden set evaluado. */
    const val FIELD_DATASET_VERSION: String = "dataset_version"

    /** Número de entradas evaluadas. */
    const val FIELD_ENTRIES: String = "entries"

    /** Media del F1 de «¿Qué cambia?». */
    const val FIELD_WHAT_CHANGES_F1: String = "what_changes_f1"

    /** Media del F1 de «¿A quién afecta?». */
    const val FIELD_WHO_IS_AFFECTED_F1: String = "who_is_affected_f1"

    /** Media del *recall* de cifras. */
    const val FIELD_FIGURES_RECALL: String = "figures_recall"

    /** Tasa de acierto (fracción de entradas con ambos F1 por encima del umbral). */
    const val FIELD_HIT_RATE: String = "hit_rate"

    /** Número de resúmenes inválidos (contados como fallo). */
    const val FIELD_INVALID: String = "invalid"

    /** Campos del evento de arranque. */
    fun startedFields(datasetVersion: String, entries: Int): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_STARTED,
        FIELD_DATASET_VERSION to datasetVersion,
        FIELD_ENTRIES to entries,
    )

    /** Campos del evento de evaluación completada (métricas agregadas). */
    fun completedFields(result: SummaryQualityResult, datasetVersion: String): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_COMPLETED,
        FIELD_DATASET_VERSION to datasetVersion,
        FIELD_ENTRIES to result.entryCount,
        FIELD_WHAT_CHANGES_F1 to result.meanWhatChangesF1,
        FIELD_WHO_IS_AFFECTED_F1 to result.meanWhoIsAffectedF1,
        FIELD_FIGURES_RECALL to result.meanFiguresRecall,
        FIELD_HIT_RATE to result.hitRate,
        FIELD_INVALID to result.invalidCount,
    )

    /** Emite el evento de arranque en [logger]. */
    fun logStarted(logger: Logger, datasetVersion: String, entries: Int) {
        logger.info(
            "Evaluación de resúmenes iniciada",
            StructuredArguments.entries(startedFields(datasetVersion, entries)),
        )
    }

    /** Emite el evento de evaluación completada en [logger]. */
    fun logCompleted(logger: Logger, result: SummaryQualityResult, datasetVersion: String) {
        logger.info(
            "Evaluación de resúmenes completada",
            StructuredArguments.entries(completedFields(result, datasetVersion)),
        )
    }
}
