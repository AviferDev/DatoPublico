package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.ZonedDateTime
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory

/**
 * Canal de alerta cuando una ventana de ingesta se queda sin ejecutar.
 *
 * Es una interfaz inyectable para poder sustituirla en los tests por un sumidero
 * en memoria. La implementación por defecto publica la alerta como log
 * estructurado; **no** hay alertas externas (email, push, webhooks) ni telemetría.
 */
fun interface IngestionAlertSink {
    /**
     * Emite la alerta de la ventana [missed].
     *
     * @param missed ventana que se debió ejecutar y no se intentó.
     */
    fun alert(missed: ZonedDateTime)
}

/**
 * Implementación de [IngestionAlertSink] que emite la alerta en stdout.
 *
 * Publica un log de nivel `ERROR` con el evento estable
 * [EVENT_INGESTION_MISSED] y campos de primer nivel (fecha, ventana y gracia
 * configurada) mediante argumentos estructurados. Un monitor de logs debe
 * vigilar esa clave `event`.
 *
 * @param grace margen configurado que se incluye en la alerta para diagnóstico.
 */
class LoggingAlertSink(private val grace: Duration) : IngestionAlertSink {

    /** Logger dedicado a las alertas de ingesta ausente. */
    private val logger = LoggerFactory.getLogger(LoggingAlertSink::class.java)

    override fun alert(missed: ZonedDateTime) {
        logger.error(
            "Ventana de ingesta perdida",
            StructuredArguments.entries(
                mapOf(
                    FIELD_EVENT to EVENT_INGESTION_MISSED,
                    FIELD_INGESTION_DATE to missed.toLocalDate().toString(),
                    FIELD_WINDOW to missed.toLocalTime().toString(),
                    FIELD_GRACE_MINUTES to grace.toMinutes(),
                )
            ),
        )
    }

    companion object {
        /** Evento de una ventana que pasó sin intentarse. */
        const val EVENT_INGESTION_MISSED: String = "ingestion.missed"

        /** Clave del identificador de evento. */
        const val FIELD_EVENT: String = "event"

        /** Fecha local de la ventana perdida (`YYYY-MM-DD`). */
        const val FIELD_INGESTION_DATE: String = "ingestion_date"

        /** Hora local de la ventana perdida (`HH:mm`). */
        const val FIELD_WINDOW: String = "window"

        /** Margen de gracia configurado, en minutos. */
        const val FIELD_GRACE_MINUTES: String = "grace_minutes"
    }
}
