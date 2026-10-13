package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.infra.RateLimitSettings
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName

/**
 * Nombres de los dos limitadores de la API (FT00030).
 *
 * [GENERAL] es el cupo de los endpoints de dominio y [CHAT] el del chat RAG (más
 * estricto, porque es lo más caro por consulta). Las rutas se envuelven con
 * `Route.rateLimit(...)` usando estos nombres.
 */
object ApiRateLimiter {
    /** Cupo general de los endpoints de dominio. */
    val GENERAL: RateLimitName = RateLimitName("api")

    /** Cupo específico (más estricto) del chat RAG. */
    val CHAT: RateLimitName = RateLimitName("chat")
}

/**
 * Configura el **rate limiting por IP** (FT00030) con dos limitadores nombrados
 * ([ApiRateLimiter.GENERAL] y [ApiRateLimiter.CHAT]) en **memoria** (por
 * instancia): el backend es un único proceso, así que no se usa un almacén
 * compartido (Redis/tabla) ni se confía en cabeceras de proxy (FT00041).
 *
 * El plugin oficial `RateLimit` implementa un *token bucket* por clave: la clave es
 * la **IP** del cliente (`request.origin.remoteHost`). Añade las cabeceras
 * `X-RateLimit-Limit/Remaining/Reset` a las peticiones permitidas y `Retry-After` a
 * las rechazadas (`429`). `/health` y `/openapi.json` quedan **fuera** (son
 * operacionales y deben seguir accesibles para monitorización).
 *
 * @param settings cupos y ventanas general y del chat.
 */
fun Application.configureRateLimit(settings: RateLimitSettings) {
    install(RateLimit) {
        register(ApiRateLimiter.GENERAL) {
            rateLimiter(settings.requests, settings.window)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(ApiRateLimiter.CHAT) {
            rateLimiter(settings.chatRequests, settings.chatWindow)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
