package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.publication.PublicationDetailService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /publicacion/{id}` (FT00027).
 *
 * Devuelve el detalle de una publicación por su identificador oficial del BOE
 * (`BOE-A-2026-20979`). El detalle sirve **solo** publicaciones con resumen
 * `publicado`: un identificador desconocido o un resumen no publicado responden
 * `404`; un `id` en blanco o más largo que
 * [PublicationDetailService.MAX_ID_LENGTH] responde `400` **sin cuerpo tipado**
 * (el contrato de error es FT00030).
 *
 * La ruta no conoce SQL ni el pipeline del resumen: delega en [service] y solo
 * traduce su resultado a HTTP.
 *
 * @param service servicio del detalle de publicación.
 */
fun Route.publicationDetailRoutes(service: PublicationDetailService) {
    get("/publicacion/{id}") {
        val id = call.parameters["id"]
        if (id == null || id.isBlank() || id.length > PublicationDetailService.MAX_ID_LENGTH) {
            call.respond(HttpStatusCode.BadRequest)
        } else {
            val detail = service.detail(id)
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(HttpStatusCode.OK, detail)
            }
        }
    }
}
