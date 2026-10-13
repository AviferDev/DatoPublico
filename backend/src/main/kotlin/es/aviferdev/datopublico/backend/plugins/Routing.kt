package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.feed.DailyFeedService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * Rutas del backend. Registra el healthcheck (`/health`) **siempre** y las rutas
 * cuyas dependencias existen: desde FT00026, `/feed` si [feedService] no es
 * `null` (patrón «solo las rutas cuyas dependencias existen»). Detalle, búsqueda
 * y chat llegarán con sus features.
 *
 * @param feedService servicio del feed diario, o `null` si no hay base de datos.
 */
fun Application.configureRouting(feedService: DailyFeedService? = null) {
    routing {
        healthRoutes()
        if (feedService != null) {
            feedRoutes(feedService)
        }
    }
}
