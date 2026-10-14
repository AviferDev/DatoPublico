package es.aviferdev.datopublico.backend.infra

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuración de **CORS** del backend (FT00030), leída **solo** del entorno.
 *
 * La API es pública y de **solo lectura sin credenciales**: no hay datos de
 * usuario que proteger, así que el valor por defecto es **permisivo**
 * ([allowedOrigins] vacía ⇒ el plugin de CORS permite cualquier origen). Si se
 * define una allowlist, solo se permiten los orígenes listados (allowlist
 * estricta). El origen de producción se fijará en el despliegue (FT00039).
 *
 * @property allowedOrigins orígenes permitidos (vacía ⇒ cualquier origen).
 */
data class CorsSettings(
    val allowedOrigins: List<String>,
) {
    companion object {
        /** Configuración por defecto: sin allowlist (cualquier origen). */
        fun default(): CorsSettings = CorsSettings(emptyList())

        /**
         * Construye la configuración desde un mapa de entorno (`System.getenv()`
         * por defecto) para poder testear sin tocar el entorno real.
         *
         * `CORS_ALLOWED_ORIGINS` es una lista separada por comas; ausente o vacía
         * ⇒ lista vacía (cualquier origen). Se normaliza con `trim` y se
         * descartan las entradas vacías.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): CorsSettings =
            CorsSettings(parseOrigins(env["CORS_ALLOWED_ORIGINS"]))

        /** Divide [raw] por comas, recorta y descarta las entradas vacías. */
        private fun parseOrigins(raw: String?): List<String> =
            raw?.split(",")?.map { origin -> origin.trim() }?.filter { origin -> origin.isNotEmpty() }
                ?: emptyList()
    }
}

/**
 * Configuración del **rate limiting** por IP del backend (FT00030), leída **solo**
 * del entorno.
 *
 * Hay dos cupos **en memoria** (por instancia): uno **general** para los
 * endpoints de dominio ([requests]/[window]) y otro más **estricto** para el chat
 * RAG ([chatRequests]/[chatWindow]), que es lo más caro por consulta. Los
 * endpoints operacionales (`/health`, `/openapi.json`) quedan **exentos**.
 *
 * @property requests peticiones permitidas por IP en la ventana general.
 * @property window ventana del cupo general.
 * @property chatRequests peticiones permitidas por IP en la ventana del chat.
 * @property chatWindow ventana del cupo del chat.
 */
data class RateLimitSettings(
    val requests: Int,
    val window: Duration,
    val chatRequests: Int,
    val chatWindow: Duration,
) {
    companion object {
        /** Cupo general por defecto (peticiones por ventana). */
        const val DEFAULT_REQUESTS = 60

        /** Ventana general por defecto (segundos). */
        const val DEFAULT_WINDOW_SECONDS = 60

        /** Cupo del chat por defecto (peticiones por ventana; más estricto). */
        const val DEFAULT_CHAT_REQUESTS = 6

        /** Ventana del chat por defecto (segundos). */
        const val DEFAULT_CHAT_WINDOW_SECONDS = 60

        /** Configuración por defecto (cupo general 60/60 s; chat 6/60 s). */
        fun default(): RateLimitSettings = RateLimitSettings(
            requests = DEFAULT_REQUESTS,
            window = DEFAULT_WINDOW_SECONDS.seconds,
            chatRequests = DEFAULT_CHAT_REQUESTS,
            chatWindow = DEFAULT_CHAT_WINDOW_SECONDS.seconds,
        )

        /**
         * Construye la configuración desde un mapa de entorno (`System.getenv()`
         * por defecto) para poder testear sin tocar el entorno real.
         *
         * Variables: `RATE_LIMIT_REQUESTS` (60), `RATE_LIMIT_WINDOW_SECONDS` (60),
         * `CHAT_RATE_LIMIT_REQUESTS` (6) y `CHAT_RATE_LIMIT_WINDOW_SECONDS` (60).
         *
         * @throws IllegalArgumentException si un valor no es un entero mayor que 0
         *   (fail-fast, patrón `AppConfig`).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): RateLimitSettings = RateLimitSettings(
            requests = parsePositiveInt(env["RATE_LIMIT_REQUESTS"], "RATE_LIMIT_REQUESTS", DEFAULT_REQUESTS),
            window = parsePositiveInt(
                env["RATE_LIMIT_WINDOW_SECONDS"],
                "RATE_LIMIT_WINDOW_SECONDS",
                DEFAULT_WINDOW_SECONDS,
            ).seconds,
            chatRequests = parsePositiveInt(
                env["CHAT_RATE_LIMIT_REQUESTS"],
                "CHAT_RATE_LIMIT_REQUESTS",
                DEFAULT_CHAT_REQUESTS,
            ),
            chatWindow = parsePositiveInt(
                env["CHAT_RATE_LIMIT_WINDOW_SECONDS"],
                "CHAT_RATE_LIMIT_WINDOW_SECONDS",
                DEFAULT_CHAT_WINDOW_SECONDS,
            ).seconds,
        )

        /**
         * Lee un entero positivo de [raw] (o el [default] si está ausente);
         * *fail-fast* si está presente y no es un entero mayor que 0.
         */
        private fun parsePositiveInt(raw: String?, key: String, default: Int): Int {
            val value = raw?.toIntOrNull()
            require(raw == null || (value != null && value > 0)) {
                "Valor inválido para $key: '$raw'. Define un entero mayor que 0 " +
                    "(por defecto $default)."
            }
            return value ?: default
        }
    }
}

/**
 * Configuración de la **confianza de cabeceras de proxy** del backend (FT00041),
 * leída **solo** del entorno.
 *
 * En producción el backend va detrás de Nginx, que termina TLS y añade las
 * cabeceras `X-Forwarded-*`. Sin confiar en ellas, el rate limiting por IP
 * (FT00030) vería la **IP del proxy** y todos los clientes compartirían cupo. La
 * confianza es **opt-in** ([trustProxyHeaders], por defecto `false`): solo es
 * segura si el backend **nunca** publica puerto (lo garantiza el compose de
 * producción). En desarrollo y en el gate queda apagada y el plugin no se instala.
 *
 * @property trustProxyHeaders si se confía en las cabeceras `X-Forwarded-*`.
 */
data class ProxySettings(
    val trustProxyHeaders: Boolean,
) {
    companion object {
        /** Nombre de la variable de entorno que activa la confianza. */
        const val TRUST_PROXY_HEADERS_ENV = "TRUST_PROXY_HEADERS"

        /** Configuración por defecto: **no** se confía en cabeceras de proxy. */
        fun default(): ProxySettings = ProxySettings(trustProxyHeaders = false)

        /**
         * Construye la configuración desde un mapa de entorno (`System.getenv()`
         * por defecto) para poder testear sin tocar el entorno real.
         *
         * `TRUST_PROXY_HEADERS` admite `true`/`false` (sin distinguir mayúsculas);
         * ausente o en blanco ⇒ `false` (opt-in).
         *
         * @throws IllegalArgumentException si el valor no es un booleano (fail-fast).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): ProxySettings =
            ProxySettings(parseTrust(env[TRUST_PROXY_HEADERS_ENV]))

        /** `TRUST_PROXY_HEADERS` admite `true`/`false`; ausente/en blanco ⇒ `false`. */
        private fun parseTrust(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
            null, "", "false" -> false
            "true" -> true
            else -> throw IllegalArgumentException(
                "Valor inválido de $TRUST_PROXY_HEADERS_ENV: '$raw'. Usa 'true' o 'false'."
            )
        }
    }
}
