package es.aviferdev.datopublico.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * Rutas del backend. Hoy solo el healthcheck operativo; feed, detalle, búsqueda
 * y chat llegarán con sus features y con sus dependencias disponibles.
 */
fun Application.configureRouting() {
    routing {
        healthRoutes()
    }
}
