package es.aviferdev.datopublico.backend.ingesta.job

import java.time.LocalTime
import java.time.ZoneId

/**
 * Configuración del job diario de ingesta: horario y zona horaria.
 *
 * Es **pura** (no toca red ni base de datos) y se construye desde el entorno, de
 * modo que es testeable sin infraestructura. Los valores por defecto documentan
 * la ventana acordada: primera pasada a las **09:30** y **segunda pasada a las
 * 18:00** en `Europe/Madrid`; la segunda re-parsea la misma fecha para aplicar
 * las correcciones que publique el BOE.
 *
 * @property enabled si la ingesta está habilitada (`INGESTA_ENABLED`, `true` por
 *   defecto); si es `false`, el job no se programa.
 * @property zone zona IANA del horario (`INGESTA_TIMEZONE`).
 * @property primaryTime hora de la primera pasada (`INGESTA_PRIMARY_TIME`).
 * @property secondaryTime hora de la segunda pasada (`INGESTA_SECONDARY_TIME`).
 */
data class IngestaConfig(
    val enabled: Boolean,
    val zone: ZoneId,
    val primaryTime: LocalTime,
    val secondaryTime: LocalTime,
) {
    /** Horas de disparo diarias, en el orden configurado (primera y segunda). */
    val times: List<LocalTime> get() = listOf(primaryTime, secondaryTime)

    companion object {
        /** Zona por defecto: horario oficial peninsular. */
        const val DEFAULT_TIMEZONE: String = "Europe/Madrid"

        /** Primera pasada por defecto (apertura del BOE). */
        const val DEFAULT_PRIMARY_TIME: String = "09:30"

        /** Segunda pasada por defecto (correcciones de la tarde). */
        const val DEFAULT_SECONDARY_TIME: String = "18:00"

        /**
         * Construye la configuración desde un mapa de entorno (`System.getenv()`
         * por defecto) para poder testear sin tocar el entorno real.
         *
         * @throws IllegalArgumentException si un valor tiene formato inválido
         *   (fail-fast con mensaje claro).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): IngestaConfig = IngestaConfig(
            enabled = parseEnabled(firstNonBlank(env, ENABLED_KEY)),
            zone = parseZone(firstNonBlank(env, TIMEZONE_KEY)),
            primaryTime = parseTime(
                key = PRIMARY_TIME_KEY,
                raw = firstNonBlank(env, PRIMARY_TIME_KEY),
                default = DEFAULT_PRIMARY_TIME,
            ),
            secondaryTime = parseTime(
                key = SECONDARY_TIME_KEY,
                raw = firstNonBlank(env, SECONDARY_TIME_KEY),
                default = DEFAULT_SECONDARY_TIME,
            ),
        )

        private fun firstNonBlank(env: Map<String, String>, key: String): String? =
            env[key]?.trim()?.takeIf { it.isNotBlank() }

        /** `INGESTA_ENABLED` admite `true`/`false` (sin distinguir mayúsculas). */
        private fun parseEnabled(raw: String?): Boolean = when (raw?.lowercase()) {
            null, "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException(
                "Valor inválido de $ENABLED_KEY: '$raw'. Usa 'true' o 'false'."
            )
        }

        private fun parseZone(raw: String?): ZoneId {
            val name = raw ?: DEFAULT_TIMEZONE
            return runCatching { ZoneId.of(name) }.getOrElse {
                throw IllegalArgumentException(
                    "Zona horaria inválida en $TIMEZONE_KEY: '$name'. " +
                        "Usa una zona IANA, p. ej. '$DEFAULT_TIMEZONE'."
                )
            }
        }

        private fun parseTime(key: String, raw: String?, default: String): LocalTime {
            val value = raw ?: default
            return runCatching { LocalTime.parse(value) }.getOrElse {
                throw IllegalArgumentException(
                    "Hora inválida en $key: '$value'. Usa formato HH:mm (p. ej. '09:30')."
                )
            }
        }

        private const val ENABLED_KEY = "INGESTA_ENABLED"
        private const val TIMEZONE_KEY = "INGESTA_TIMEZONE"
        private const val PRIMARY_TIME_KEY = "INGESTA_PRIMARY_TIME"
        private const val SECONDARY_TIME_KEY = "INGESTA_SECONDARY_TIME"
    }
}
