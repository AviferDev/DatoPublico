package es.aviferdev.datopublico.backend.ingesta.backfill

import es.aviferdev.datopublico.backend.ingesta.job.IngestionConfig
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId

/**
 * Configuración del backfill histórico: rango, ritmo y fichero de checkpoint.
 *
 * Es **pura** (no toca red ni base de datos) y se construye desde el entorno
 * `BACKFILL_*` —separadas de las `INGESTA_*` del job diario— para poder testear
 * sin infraestructura. Por defecto cubre el **último año** hasta hoy en la zona
 * de [IngestionConfig] (`Europe/Madrid`) y avanza en lotes de 7 días con una
 * pausa de 500 ms, pensado para la máquina única de 8 GB.
 *
 * @property from primera fecha a procesar (incluida).
 * @property to última fecha a procesar (incluida).
 * @property batchDays días por lote (`BACKFILL_BATCH_DAYS`, > 0).
 * @property delayMillis pausa entre fechas (`BACKFILL_DELAY_MS`, ≥ 0).
 * @property stateFile fichero local del checkpoint (`BACKFILL_STATE_FILE`).
 */
data class BackfillConfig(
    val from: LocalDate,
    val to: LocalDate,
    val batchDays: Int,
    val delayMillis: Long,
    val stateFile: Path,
) {
    /** Rango inclusivo que se va a recorrer. */
    val range: BackfillRange get() = BackfillRange(from, to)

    companion object {
        /** Días por lote por defecto. */
        const val DEFAULT_BATCH_DAYS: Int = 7

        /** Pausa por defecto entre fechas (ms). */
        const val DEFAULT_DELAY_MS: Long = 500L

        /** Fichero de checkpoint por defecto. */
        const val DEFAULT_STATE_FILE: String = ".backfill-state.txt"

        /**
         * Construye la configuración desde un mapa de entorno (`System.getenv()`
         * por defecto) para poder testear sin tocar el entorno real.
         *
         * @param env variables del entorno.
         * @param today día usado como `BACKFILL_TO` por defecto; inyectable para
         *   que los tests no dependan del reloj real.
         * @throws IllegalArgumentException si una fecha tiene formato inválido, si
         *   `FROM` es posterior a `TO`, o si el lote/pausa no son válidos.
         */
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            today: LocalDate = LocalDate.now(ingestionZone(env)),
        ): BackfillConfig {
            val to = parseDate(TO_KEY, firstNonBlank(env, TO_KEY)) ?: today
            val from = parseDate(FROM_KEY, firstNonBlank(env, FROM_KEY)) ?: to.minusYears(1)
            require(!from.isAfter(to)) {
                "Rango de backfill inválido: $FROM_KEY ($from) es posterior a $TO_KEY ($to)."
            }
            return BackfillConfig(
                from = from,
                to = to,
                batchDays = parsePositiveInt(env, BATCH_DAYS_KEY, DEFAULT_BATCH_DAYS),
                delayMillis = parseNonNegativeLong(env, DELAY_MS_KEY, DEFAULT_DELAY_MS),
                stateFile = Path.of(firstNonBlank(env, STATE_FILE_KEY) ?: DEFAULT_STATE_FILE),
            )
        }

        /** Zona horaria de la ingesta, reutilizada para fechar «hoy». */
        private fun ingestionZone(env: Map<String, String>): ZoneId =
            IngestionConfig.fromEnv(env).zone

        private fun firstNonBlank(env: Map<String, String>, key: String): String? =
            env[key]?.trim()?.takeIf { it.isNotBlank() }

        private fun parseDate(key: String, raw: String?): LocalDate? =
            raw?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrElse {
                    throw IllegalArgumentException(
                        "Fecha inválida en $key: '$value'. Usa formato ISO (YYYY-MM-DD)."
                    )
                }
            }

        private fun parsePositiveInt(
            env: Map<String, String>,
            key: String,
            default: Int,
        ): Int {
            val raw = firstNonBlank(env, key)
            val value = raw?.toIntOrNull()
            require(raw == null || (value != null && value > 0)) {
                "Valor inválido de $key: '$raw'. Usa un entero mayor que 0."
            }
            return value ?: default
        }

        private fun parseNonNegativeLong(
            env: Map<String, String>,
            key: String,
            default: Long,
        ): Long {
            val raw = firstNonBlank(env, key)
            val value = raw?.toLongOrNull()
            require(raw == null || (value != null && value >= 0)) {
                "Valor inválido de $key: '$raw'. Usa un entero mayor o igual que 0."
            }
            return value ?: default
        }

        private const val FROM_KEY = "BACKFILL_FROM"
        private const val TO_KEY = "BACKFILL_TO"
        private const val BATCH_DAYS_KEY = "BACKFILL_BATCH_DAYS"
        private const val DELAY_MS_KEY = "BACKFILL_DELAY_MS"
        private const val STATE_FILE_KEY = "BACKFILL_STATE_FILE"
    }
}
