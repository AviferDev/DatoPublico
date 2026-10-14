package es.aviferdev.datopublico.backend.rag.summary

import java.time.LocalDate

/**
 * Configuración del generador one-shot de resúmenes ciudadanos (herramienta de
 * operación, no cableada en el arranque).
 *
 * Es **pura** (no toca red, base de datos ni reloj salvo el `today` inyectable) y
 * se construye desde el entorno `SUMMARY_*`:
 *
 * - `SUMMARY_FROM` / `SUMMARY_TO`: rango **inclusivo** de fechas ISO (`YYYY-MM-DD`);
 *   por defecto, el día de hoy (una sola fecha).
 * - `SUMMARY_LIMIT`: número máximo de publicaciones a generar en la ejecución
 *   (acota el coste de las llamadas al proveedor); por defecto [DEFAULT_LIMIT].
 * - `SUMMARY_DELAY_MS`: pausa entre publicaciones, en milisegundos, para no
 *   saturar la API del proveedor; por defecto [DEFAULT_DELAY_MS].
 *
 * @property from primera fecha a procesar (incluida).
 * @property to última fecha a procesar (incluida).
 * @property limit tope de publicaciones procesadas en esta ejecución (`>= 1`).
 * @property delayMillis pausa entre publicaciones (`>= 0`).
 */
data class GenerateSummariesConfig(
    val from: LocalDate,
    val to: LocalDate,
    val limit: Int,
    val delayMillis: Long,
) {
    /** Fechas del rango, de [from] a [to] inclusive. */
    fun dates(): List<LocalDate> = from.datesUntil(to.plusDays(1)).toList()

    companion object {
        /** Tope por defecto de publicaciones por ejecución. */
        const val DEFAULT_LIMIT: Int = 25

        /** Pausa por defecto entre publicaciones (ms). */
        const val DEFAULT_DELAY_MS: Long = 250L

        /**
         * Construye la configuración desde [env].
         *
         * @param env variables del entorno (`System.getenv()` por defecto).
         * @param today día por defecto de `SUMMARY_FROM`/`SUMMARY_TO`.
         * @return la configuración validada.
         * @throws IllegalArgumentException si una fecha, el tope o la pausa no son
         *   válidos (fail-fast con mensaje claro).
         */
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            today: LocalDate = LocalDate.now(),
        ): GenerateSummariesConfig {
            val from = parseDate(FROM_KEY, firstNonBlank(env, FROM_KEY)) ?: today
            val to = parseDate(TO_KEY, firstNonBlank(env, TO_KEY)) ?: today
            require(!from.isAfter(to)) {
                "Rango de resúmenes inválido: $FROM_KEY ($from) es posterior a $TO_KEY ($to)."
            }
            return GenerateSummariesConfig(
                from = from,
                to = to,
                limit = positiveInt(env, LIMIT_KEY, DEFAULT_LIMIT),
                delayMillis = nonNegativeLong(env, DELAY_KEY, DEFAULT_DELAY_MS),
            )
        }

        /** Primer valor de [key] no vacío, o `null`. */
        private fun firstNonBlank(env: Map<String, String>, key: String): String? =
            env[key]?.trim()?.takeIf { value -> value.isNotEmpty() }

        /** Parsea una fecha ISO, o `null` si [raw] es `null`. */
        private fun parseDate(key: String, raw: String?): LocalDate? =
            raw?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrElse {
                    throw IllegalArgumentException(
                        "Fecha inválida en $key: '$value'. Usa formato ISO (YYYY-MM-DD)."
                    )
                }
            }

        /** Parsea un entero `> 0`, o el [default] si [raw] es `null`. */
        private fun positiveInt(env: Map<String, String>, key: String, default: Int): Int {
            val raw = firstNonBlank(env, key)
            val value = raw?.toIntOrNull() ?: default
            require(value > 0) { invalid(key, raw, "mayor que 0") }
            return value
        }

        /** Parsea un entero `>= 0`, o el [default] si [raw] es `null`. */
        private fun nonNegativeLong(env: Map<String, String>, key: String, default: Long): Long {
            val raw = firstNonBlank(env, key)
            val value = raw?.toLongOrNull() ?: default
            require(value >= 0) { invalid(key, raw, "mayor o igual que 0") }
            return value
        }

        /** Mensaje de error homogéneo para un valor numérico inválido. */
        private fun invalid(key: String, raw: String?, expectation: String): String =
            "Valor inválido de $key: '$raw'. Usa un entero $expectation."

        private const val FROM_KEY = "SUMMARY_FROM"
        private const val TO_KEY = "SUMMARY_TO"
        private const val LIMIT_KEY = "SUMMARY_LIMIT"
        private const val DELAY_KEY = "SUMMARY_DELAY_MS"
    }
}
