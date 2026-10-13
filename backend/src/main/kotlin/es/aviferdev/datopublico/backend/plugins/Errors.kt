package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.model.ApiErrorCodeDto
import es.aviferdev.datopublico.model.ApiErrorDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlin.coroutines.cancellation.CancellationException

/**
 * Configura el **cuerpo de error tipado** (FT00030) con el plugin oficial
 * `StatusPages`: cualquier error de la API devuelve un [ApiErrorDto]
 * (`{"codigo":…,"mensaje":…}`) con un mensaje **genérico** (en castellano) y
 * **sin** filtrar detalles internos, en lugar de una respuesta sin cuerpo.
 *
 * Mapea los códigos del contrato (`400` → `solicitud_invalida`, `404` →
 * `no_encontrado`, `429` → `demasiadas_peticiones`, `500` → `error_interno`,
 * `502` → `servicio_no_disponible`) **sin tocar las rutas**: los `400`/`404`
 * existentes y los `500`/`502` que produce `Chat.kt` pasan a llevar el cuerpo
 * tipado. El `429` **conserva** la cabecera `Retry-After` que añade el rate
 * limiting. **No** cambia ningún **código** de estado.
 *
 * Un fallo **no controlado** (`exception<Throwable>`) se registra (sin volcar
 * internos al cliente) y responde `500` `error_interno`; `CancellationException`
 * se **relanza** para no interferir con la cancelación de corrutinas.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        status(HttpStatusCode.BadRequest) { call, status ->
            call.respondApiError(status, ApiErrorCodeDto.INVALID_REQUEST)
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondApiError(status, ApiErrorCodeDto.NOT_FOUND)
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respondApiError(status, ApiErrorCodeDto.TOO_MANY_REQUESTS)
        }
        status(HttpStatusCode.InternalServerError) { call, status ->
            call.respondApiError(status, ApiErrorCodeDto.INTERNAL_ERROR)
        }
        status(HttpStatusCode.BadGateway) { call, status ->
            call.respondApiError(status, ApiErrorCodeDto.SERVICE_UNAVAILABLE)
        }
        exception<Throwable> { call, cause -> call.handleUnexpected(cause) }
    }
}

/** Responde [status] con el [ApiErrorDto] cuyo código es [code]. */
private suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, code: ApiErrorCodeDto) {
    respond(status, ApiErrorDto(code, messageOf(code)))
}

/**
 * Registra un fallo no controlado (sin filtrar internos) y responde `500`
 * `error_interno`; relanza [CancellationException] para respetar la cancelación.
 */
private suspend fun ApplicationCall.handleUnexpected(cause: Throwable) {
    if (cause is CancellationException) {
        throw cause
    }
    application.log.error("Error no controlado al atender la petición", cause)
    respondApiError(HttpStatusCode.InternalServerError, ApiErrorCodeDto.INTERNAL_ERROR)
}

/** Mensaje genérico y estable asociado a cada código de error. */
private fun messageOf(code: ApiErrorCodeDto): String = when (code) {
    ApiErrorCodeDto.INVALID_REQUEST -> INVALID_REQUEST_MESSAGE
    ApiErrorCodeDto.NOT_FOUND -> NOT_FOUND_MESSAGE
    ApiErrorCodeDto.TOO_MANY_REQUESTS -> TOO_MANY_REQUESTS_MESSAGE
    ApiErrorCodeDto.INTERNAL_ERROR -> INTERNAL_ERROR_MESSAGE
    ApiErrorCodeDto.SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE_MESSAGE
}

/** Mensaje del error `400` (petición inválida). */
private const val INVALID_REQUEST_MESSAGE = "La petición no es válida."

/** Mensaje del error `404` (recurso no encontrado). */
private const val NOT_FOUND_MESSAGE = "El recurso solicitado no existe."

/** Mensaje del error `429` (límite superado). */
private const val TOO_MANY_REQUESTS_MESSAGE =
    "Se ha superado el límite de peticiones. Inténtalo de nuevo más tarde."

/** Mensaje del error `500` (fallo interno). */
private const val INTERNAL_ERROR_MESSAGE = "Se ha producido un error interno."

/** Mensaje del error `502` (proveedor no disponible). */
private const val SERVICE_UNAVAILABLE_MESSAGE = "El servicio no está disponible temporalmente."
