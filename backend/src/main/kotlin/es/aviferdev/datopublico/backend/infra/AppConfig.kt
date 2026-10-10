package es.aviferdev.datopublico.backend.infra

/**
 * Configuración del proceso leída **solo** del entorno, con valores por defecto
 * seguros. Sin secretos: un valor ausente no falla, uno inválido sí (fail-fast).
 */
data class AppConfig(
    val host: String,
    val port: Int,
    val environment: String,
    val logLevel: String,
) {
    companion object {
        const val DEFAULT_HOST = "0.0.0.0"
        const val DEFAULT_PORT = 8080
        const val DEFAULT_ENVIRONMENT = "local"
        const val DEFAULT_LOG_LEVEL = "INFO"

        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535

        /**
         * Construye la configuración desde un mapa de entorno (`System.getenv()`
         * por defecto) para poder testear sin tocar el entorno real.
         *
         * @throws IllegalArgumentException si el puerto no es un entero válido.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig = AppConfig(
            host = firstNonBlank(env, "SERVER_HOST") ?: DEFAULT_HOST,
            port = parsePort(firstNonBlank(env, "SERVER_PORT", "PORT")),
            environment = firstNonBlank(env, "APP_ENV") ?: DEFAULT_ENVIRONMENT,
            logLevel = firstNonBlank(env, "LOG_LEVEL") ?: DEFAULT_LOG_LEVEL,
        )

        private fun firstNonBlank(env: Map<String, String>, vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> env[key]?.takeIf { it.isNotBlank() } }

        private fun parsePort(raw: String?): Int {
            if (raw == null) return DEFAULT_PORT
            val port = raw.toIntOrNull()
            require(port != null && port in MIN_PORT..MAX_PORT) {
                "Puerto inválido: '$raw'. Define SERVER_PORT (o PORT) como un " +
                    "entero entre $MIN_PORT y $MAX_PORT."
            }
            return port
        }
    }
}
