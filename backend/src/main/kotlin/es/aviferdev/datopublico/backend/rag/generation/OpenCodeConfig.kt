package es.aviferdev.datopublico.backend.rag.generation

/**
 * Configuración del proveedor de generación OpenCode resuelta desde el entorno.
 *
 * **No** se cablea en el arranque: la construye explícitamente quien va a usar el
 * proveedor. La **clave** vive solo en el servidor (variable de entorno) y nunca
 * se versiona; su ausencia es **fail-fast** ([fromEnv]), sin valor por defecto.
 *
 * @property apiKey clave de OpenCode (`OPENCODE_API_KEY`, obligatoria).
 * @property baseUrl base de la API OpenAI-compatible (`OPENCODE_BASE_URL`).
 * @property model identificador del modelo (`OPENCODE_MODEL`).
 * @property temperature temperatura de muestreo (`OPENCODE_TEMPERATURE`).
 */
internal data class OpenCodeConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
) {
    companion object {
        /** Clave de OpenCode; obligatoria en el servidor. */
        const val API_KEY_ENV = "OPENCODE_API_KEY"

        /** Base de la API OpenAI-compatible de OpenCode. */
        const val BASE_URL_ENV = "OPENCODE_BASE_URL"

        /** Modelo de generación. */
        const val MODEL_ENV = "OPENCODE_MODEL"

        /** Temperatura de muestreo. */
        const val TEMPERATURE_ENV = "OPENCODE_TEMPERATURE"

        /** Base por defecto de OpenCode Go (Zen). */
        const val DEFAULT_BASE_URL = "https://opencode.ai/zen/go/v1"

        /** Modelo por defecto: DeepSeek rápido y económico. */
        const val DEFAULT_MODEL = "deepseek-v4.1-flash"

        /** Temperatura por defecto: respuestas deterministas para resúmenes. */
        const val DEFAULT_TEMPERATURE = 0.0

        /**
         * Resuelve la configuración desde [env] (`System.getenv()` por defecto).
         *
         * @throws TextGenerationException si falta [API_KEY_ENV] o si
         *   [TEMPERATURE_ENV] no es un número finito `>= 0` (fail-fast con mensaje
         *   claro, sin *fallback* a una clave por defecto).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): OpenCodeConfig = OpenCodeConfig(
            apiKey = requiredApiKey(env),
            baseUrl = optional(env, BASE_URL_ENV) ?: DEFAULT_BASE_URL,
            model = optional(env, MODEL_ENV) ?: DEFAULT_MODEL,
            temperature = parseTemperature(env),
        )

        /** Clave obligatoria de [API_KEY_ENV] con fail-fast si falta o está vacía. */
        private fun requiredApiKey(env: Map<String, String>): String =
            optional(env, API_KEY_ENV)
                ?: throw TextGenerationException(
                    "Falta $API_KEY_ENV: define la clave de OpenCode en el servidor " +
                        "(variable de entorno; nunca se versiona)."
                )

        /** Valor no vacío de [key], o `null` si falta o está en blanco. */
        private fun optional(env: Map<String, String>, key: String): String? =
            env[key]?.trim()?.takeIf { value -> value.isNotBlank() }

        /** `OPENCODE_TEMPERATURE`: número finito `>= 0` (default [DEFAULT_TEMPERATURE]). */
        private fun parseTemperature(env: Map<String, String>): Double {
            val raw = optional(env, TEMPERATURE_ENV)
            val value = raw?.toDoubleOrNull()
            if (raw != null && !isValidTemperature(value)) {
                throw TextGenerationException(
                    "Valor inválido de $TEMPERATURE_ENV: '$raw'. Usa un número finito >= 0."
                )
            }
            return value ?: DEFAULT_TEMPERATURE
        }

        /** `true` si [value] es un número finito `>= 0`. */
        private fun isValidTemperature(value: Double?): Boolean =
            value != null && value.isFinite() && value >= 0.0
    }
}
