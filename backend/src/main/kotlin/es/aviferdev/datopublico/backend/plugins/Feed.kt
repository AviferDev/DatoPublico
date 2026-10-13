package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.feed.DailyFeedService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate

/**
 * `GET /feed?fecha=<ISO>&pagina=<n>&tamanoPagina=<n>` (FT00026).
 *
 * Devuelve el feed diario paginado (publicaciones con resumen publicado,
 * destacados y metadatos de paginación). `fecha` es **obligatoria** y debe ser
 * ISO-8601 (`YYYY-MM-DD`); `pagina` (default `1`) y `tamanoPagina` (default
 * [DailyFeedService.DEFAULT_PAGE_SIZE], máx [DailyFeedService.MAX_PAGE_SIZE])
 * deben ser enteros en rango. Un parámetro inválido responde `400` **sin cuerpo
 * tipado** (el contrato de error es FT00030); un día sin publicado responde `200`
 * con listas vacías.
 */
fun Route.feedRoutes(service: DailyFeedService) {
    get("/feed") {
        val request = FeedRequest.from(call.request.queryParameters)
        if (request == null) {
            call.respond(HttpStatusCode.BadRequest)
        } else {
            call.respond(HttpStatusCode.OK, service.feed(request.date, request.page, request.pageSize))
        }
    }
}

/**
 * Petición del feed ya **validada** (fecha ISO y paginación en rango).
 *
 * Es un valor interno de la capa HTTP (no es DTO de transporte: no se serializa).
 */
internal data class FeedRequest(
    val date: String,
    val page: Int,
    val pageSize: Int,
) {
    companion object {
        /**
         * Valida los parámetros de consulta.
         *
         * @return la petición, o `null` si falta `fecha`, no es ISO-8601 o la
         *   paginación es inválida (la ruta responde `400`).
         */
        fun from(parameters: Parameters): FeedRequest? {
            val date = parameters["fecha"]?.takeIf { raw -> isIsoDate(raw) }
            val page = parsePage(parameters["pagina"])
            val pageSize = parsePageSize(parameters["tamanoPagina"])
            return if (date == null || page == null || pageSize == null) {
                null
            } else {
                FeedRequest(date, page, pageSize)
            }
        }
    }
}

/** Fecha ISO-8601 `YYYY-MM-DD` con 2 dígitos por campo. */
private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

/** `true` si [raw] es una fecha de calendario ISO-8601 válida. */
private fun isIsoDate(raw: String): Boolean =
    ISO_DATE.matches(raw) && runCatching { LocalDate.parse(raw) }.isSuccess

/** Página 1-based o `null` si el parámetro presente no es un entero válido. */
private fun parsePage(raw: String?): Int? {
    val value = if (raw == null) DEFAULT_PAGE else raw.toIntOrNull()
    return value?.takeIf { it >= 1 }
}

/** Tamaño de página en `1..MAX_PAGE_SIZE` o `null` si es inválido. */
private fun parsePageSize(raw: String?): Int? {
    val value = if (raw == null) DailyFeedService.DEFAULT_PAGE_SIZE else raw.toIntOrNull()
    return value?.takeIf { it in 1..DailyFeedService.MAX_PAGE_SIZE }
}

/** Página por defecto del feed. */
private const val DEFAULT_PAGE = 1
