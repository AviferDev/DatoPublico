package es.aviferdev.datopublico.backend.infra

/**
 * Configuración de conexión JDBC a PostgreSQL para los repositorios.
 *
 * Se **deriva** de las variables `POSTGRES_*` (fuente única de verdad, la misma
 * que consumen `docker-compose` y Flyway); no duplica la conexión ni versiona
 * credenciales. [url] apunta a `jdbc:postgresql://host:port/db` y [password]
 * **no tiene valor por defecto**: si falta, [fromEnv] falla (fail-fast) con un
 * mensaje claro, igual que la contraseña de Flyway.
 *
 * Es pura y testeable sin base de datos.
 */
data class ConfiguracionBd(
    /** URL JDBC completa (`jdbc:postgresql://host:port/db`). */
    val url: String,
    /** Usuario de la base de datos. */
    val usuario: String,
    /** Contraseña de la base de datos (obligatoria). */
    val password: String,
) {
    /**
     * Redacta la contraseña para que la configuración no la filtre si acaba en
     * un log o en una traza de error.
     */
    override fun toString(): String =
        "ConfiguracionBd(url=$url, usuario=$usuario, password=***)"

    companion object {
        /** Host por defecto del PostgreSQL local. */
        const val DEFAULT_HOST = "localhost"

        /** Puerto por defecto. */
        const val DEFAULT_PORT = "5432"

        /** Base de datos por defecto. */
        const val DEFAULT_DB = "datopublico"

        /** Usuario por defecto. */
        const val DEFAULT_USER = "datopublico"

        /**
         * Construye la configuración desde un mapa de entorno
         * (`System.getenv()` por defecto) para poder testear sin tocar el entorno
         * real. Host, puerto, base de datos y usuario tienen valores por defecto
         * no sensibles; el puerto se valida si viene definido.
         *
         * @throws IllegalArgumentException si `POSTGRES_PORT` no es un entero
         *   entre 1 y 65535.
         * @throws IllegalStateException si falta `POSTGRES_PASSWORD` (fail-fast).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): ConfiguracionBd {
            val host = primerValor(env, "POSTGRES_HOST") ?: DEFAULT_HOST
            val port = parsePort(primerValor(env, "POSTGRES_PORT"))
            val db = primerValor(env, "POSTGRES_DB") ?: DEFAULT_DB
            val usuario = primerValor(env, "POSTGRES_USER") ?: DEFAULT_USER
            val password = primerValor(env, "POSTGRES_PASSWORD")
                ?: error(
                    "Contraseña de base de datos no definida: define POSTGRES_PASSWORD " +
                        "en .env (o expórtala)"
                )
            return ConfiguracionBd(
                url = "jdbc:postgresql://$host:$port/$db",
                usuario = usuario,
                password = password,
            )
        }

        private fun primerValor(env: Map<String, String>, clave: String): String? =
            env[clave]?.takeIf { it.isNotBlank() }

        private fun parsePort(raw: String?): String {
            val port = raw?.toIntOrNull()
            require(raw == null || (port != null && port in MIN_PORT..MAX_PORT)) {
                "Puerto inválido: '$raw'. Define POSTGRES_PORT como un entero entre " +
                    "$MIN_PORT y $MAX_PORT."
            }
            return raw ?: DEFAULT_PORT
        }

        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
    }
}
