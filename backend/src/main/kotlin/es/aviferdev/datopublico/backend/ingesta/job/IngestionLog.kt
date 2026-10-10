package es.aviferdev.datopublico.backend.ingesta.job

import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger

/**
 * Evento de log **estructurado** de una ejecución del job de ingesta.
 *
 * Emite una línea JSON con campos de primer nivel (no dentro de `message`) para
 * que un monitor de logs pueda filtrar por la clave estable `event`. El mapeo de
 * campos ([runFields]) es **puro** y es la unidad verificable sin Logback; la
 * emisión ([logRun]) fija el nivel: `INFO` si la ejecución se completó, `ERROR`
 * si falló (un fallo del sumario es una «ingesta ausente» que un operador debe
 * ver).
 *
 * Sin telemetría ni destino externo: el canal es el stdout del backend.
 */
object IngestionLog {
    /** Evento de una ejecución completada. */
    const val EVENT_RUN_COMPLETED: String = "ingestion.run.completed"

    /** Evento de una ejecución fallida (p. ej. el sumario no se pudo obtener). */
    const val EVENT_RUN_FAILED: String = "ingestion.run.failed"

    /** Clave del identificador de evento (estable, para filtrar en el monitor). */
    const val FIELD_EVENT: String = "event"

    /** Fecha ingerida en formato ISO-8601 (`YYYY-MM-DD`). */
    const val FIELD_INGESTION_DATE: String = "ingestion_date"

    /** Entradas que devolvió el sumario del BOE. */
    const val FIELD_TOTAL_ENTRIES: String = "total_entries"

    /** Publicaciones persistidas con éxito (*upsert*). */
    const val FIELD_PUBLICATIONS_SAVED: String = "publications_saved"

    /** Entradas que fallaron al parsear o guardar. */
    const val FIELD_PUBLICATIONS_FAILED: String = "publications_failed"

    /** Motivo del fallo del sumario; solo presente cuando [IngestionResult.error] no es nulo. */
    const val FIELD_ERROR: String = "error"

    /**
     * Mapea un [IngestionResult] a los campos estructurados del evento.
     *
     * El evento es [EVENT_RUN_COMPLETED] si [IngestionResult.error] es nulo y
     * [EVENT_RUN_FAILED] en caso contrario. La clave `error` **solo** se incluye
     * cuando hay motivo, para no emitir un `error=null` engañoso.
     *
     * @param result resumen de la ejecución del job.
     */
    fun runFields(result: IngestionResult): Map<String, Any> = buildMap {
        put(FIELD_EVENT, if (result.error == null) EVENT_RUN_COMPLETED else EVENT_RUN_FAILED)
        put(FIELD_INGESTION_DATE, result.date.toString())
        put(FIELD_TOTAL_ENTRIES, result.totalEntries)
        put(FIELD_PUBLICATIONS_SAVED, result.saved)
        put(FIELD_PUBLICATIONS_FAILED, result.failed)
        result.error?.let { reason -> put(FIELD_ERROR, reason) }
    }

    /**
     * Emite el evento estructurado de [result] en [logger].
     *
     * Nivel `INFO` si la ejecución se completó y `ERROR` si falló (con el motivo
     * en el campo `error`). Los campos van como argumentos estructurados, no
     * interpolados en el mensaje.
     *
     * @param logger logger destino (el del scheduler de ingesta).
     * @param result resumen de la ejecución del job.
     */
    fun logRun(logger: Logger, result: IngestionResult) {
        val fields = runFields(result)
        val message = if (result.error == null) "Ingesta completada" else "Ingesta fallida"
        if (result.error == null) {
            logger.info(message, StructuredArguments.entries(fields))
        } else {
            logger.error(message, StructuredArguments.entries(fields))
        }
    }
}
