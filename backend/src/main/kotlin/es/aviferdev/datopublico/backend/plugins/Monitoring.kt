package es.aviferdev.datopublico.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

/** Log de cada petición (método, ruta, estado) con el nivel configurado. */
fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
    }
}
