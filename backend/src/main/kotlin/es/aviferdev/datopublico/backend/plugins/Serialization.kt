package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/** JSON de Ktor con la configuración compartida de `:shared`. */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(DatoPublicoJson)
    }
}
