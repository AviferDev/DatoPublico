package es.aviferdev.datopublico.backend.rag.evaluation

import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger

/**
 * Eventos de log **estructurados** de la evaluación de recuperación (FT00018).
 *
 * Emite una línea JSON con campos de primer nivel (no dentro de `message`) para
 * que un monitor filtre por la clave estable `event`. Los mapeos de campos
 * ([startedFields], [completedFields]) son **puros** y son la unidad verificable
 * sin Logback; la emisión fija el nivel `INFO`. Sin telemetría ni destino externo:
 * el canal es el stdout del proceso (mismo patrón que `BackfillLog`).
 */
object RetrievalEvaluationLog {
    /** Evento de arranque de una evaluación. */
    const val EVENT_STARTED: String = "retrieval.evaluation.started"

    /** Evento de evaluación completada. */
    const val EVENT_COMPLETED: String = "retrieval.evaluation.completed"

    /** Clave del identificador de evento (estable, para filtrar en el monitor). */
    const val FIELD_EVENT: String = "event"

    /** Versión del golden set evaluado. */
    const val FIELD_DATASET_VERSION: String = "dataset_version"

    /** Ventana de evaluación (`k`). */
    const val FIELD_K: String = "k"

    /** Número de consultas evaluadas. */
    const val FIELD_QUERIES: String = "queries"

    /** Media de `precision@k`. */
    const val FIELD_PRECISION: String = "precision"

    /** Media de `recall@k`. */
    const val FIELD_RECALL: String = "recall"

    /** Media del MRR. */
    const val FIELD_MRR: String = "mrr"

    /** Campos del evento de arranque. */
    fun startedFields(datasetVersion: String, k: Int, queries: Int): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_STARTED,
        FIELD_DATASET_VERSION to datasetVersion,
        FIELD_K to k,
        FIELD_QUERIES to queries,
    )

    /** Campos del evento de evaluación completada (métricas agregadas). */
    fun completedFields(
        result: RetrievalEvaluationResult,
        datasetVersion: String,
    ): Map<String, Any> = mapOf(
        FIELD_EVENT to EVENT_COMPLETED,
        FIELD_DATASET_VERSION to datasetVersion,
        FIELD_K to result.k,
        FIELD_QUERIES to result.queryCount,
        FIELD_PRECISION to result.meanPrecision,
        FIELD_RECALL to result.meanRecall,
        FIELD_MRR to result.meanMrr,
    )

    /** Emite el evento de arranque en [logger]. */
    fun logStarted(logger: Logger, datasetVersion: String, k: Int, queries: Int) {
        logger.info(
            "Evaluación de recuperación iniciada",
            StructuredArguments.entries(startedFields(datasetVersion, k, queries)),
        )
    }

    /** Emite el evento de evaluación completada en [logger]. */
    fun logCompleted(logger: Logger, result: RetrievalEvaluationResult, datasetVersion: String) {
        logger.info(
            "Evaluación de recuperación completada",
            StructuredArguments.entries(completedFields(result, datasetVersion)),
        )
    }
}
