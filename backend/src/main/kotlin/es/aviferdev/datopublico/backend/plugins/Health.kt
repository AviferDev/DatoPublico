package es.aviferdev.datopublico.backend.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/** Respuesta del healthcheck: endpoint operativo del servidor, no contrato de dominio. */
@Serializable
data class HealthDto(val status: String = "ok")

/** `GET /health` → `200 {"status":"ok"}` (sin depender de la base de datos). */
fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, HealthDto())
    }
}
