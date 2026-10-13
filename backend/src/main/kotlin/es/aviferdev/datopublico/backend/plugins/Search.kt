package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.search.SearchService
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate

/**
 * `GET /buscar?q=<consulta>&categoria=<token>&desde=<ISO>&hasta=<ISO>&limite=<n>`
 * (FT00028).
 *
 * Devuelve las publicaciones más relevantes para la consulta (con su resumen
 * publicado), deduplicadas y ordenadas por similitud semántica. `q` es
 * **obligatoria** y no vacía; `categoria` es el **token de wire** de
 * [CategoriaDto] (p. ej. `becas_subvenciones_y_ayudas`, no el nombre del enum);
 * `desde`/`hasta` son fechas ISO-8601 (`YYYY-MM-DD`) **inclusivas** y `limite`
 * (default [SearchService.DEFAULT_LIMIT], máx [SearchService.MAX_LIMIT]) debe ser
 * un entero en rango. Un parámetro inválido responde `400` **sin cuerpo tipado**
 * (el contrato de error es FT00030); una consulta sin resultados responde `200`
 * con `resultados` vacío.
 */
fun Route.searchRoutes(service: SearchService) {
    get("/buscar") {
        val request = SearchRequest.from(call.request.queryParameters)
        if (request == null) {
            call.respond(HttpStatusCode.BadRequest)
        } else {
            call.respond(
                HttpStatusCode.OK,
                service.search(
                    query = request.query,
                    category = request.category,
                    publishedFrom = request.publishedFrom,
                    publishedTo = request.publishedTo,
                    limit = request.limit,
                ),
            )
        }
    }
}

/**
 * Petición de búsqueda ya **validada** (consulta, filtros y límite).
 *
 * Es un valor interno de la capa HTTP (no es DTO de transporte: no se serializa).
 */
internal data class SearchRequest(
    val query: String,
    val category: CategoriaDto?,
    val publishedFrom: String?,
    val publishedTo: String?,
    val limit: Int,
) {
    companion object {
        /**
         * Valida los parámetros de consulta.
         *
         * @return la petición, o `null` si falta `q`/está en blanco, `categoria`
         *   presente pero no es un token válido, `desde`/`hasta` no son ISO-8601 o
         *   `limite` no está en `1..MAX_LIMIT` (la ruta responde `400`).
         */
        fun from(parameters: Parameters): SearchRequest? {
            val query = parameters["q"]?.trim()?.takeIf { raw -> raw.isNotBlank() }
            val category = optional(parameters["categoria"], ::categoryOrNull)
            val from = optional(parameters["desde"], ::dateOrNull)
            val to = optional(parameters["hasta"], ::dateOrNull)
            val limit = optional(parameters["limite"], ::limitOrNull)
            return if (query == null || anyInvalid(category, from, to, limit)) {
                null
            } else {
                SearchRequest(query, category.value, from.value, to.value, limit.value ?: SearchService.DEFAULT_LIMIT)
            }
        }
    }
}

/**
 * Resultado de parsear un parámetro **opcional**: [present] indica si venía y
 * [value] su valor ya validado (`null` si venía pero era inválido).
 */
private data class OptionalParam<out T>(val present: Boolean, val value: T?)

/** `true` si el parámetro venía presente y su valor no es válido. */
private fun OptionalParam<*>.isInvalid(): Boolean = present && value == null

/** `true` si alguno de [parameters] venía presente con un valor inválido. */
private fun anyInvalid(vararg parameters: OptionalParam<*>): Boolean =
    parameters.any { parameter -> parameter.isInvalid() }

/**
 * Parsea [raw] con [parse]: ausente si es `null`, válido si [parse] devuelve algo
 * y presente-inválido en caso contrario.
 */
private fun <T> optional(raw: String?, parse: (String) -> T?): OptionalParam<T> =
    OptionalParam(present = raw != null, value = raw?.let(parse))

/** Resuelve el token de wire de [CategoriaDto] (snake_case) por su contrato de serialización. */
private fun categoryOrNull(token: String): CategoriaDto? =
    runCatching {
        DatoPublicoJson.decodeFromString(CategoriaDto.serializer(), "\"$token\"")
    }.getOrNull()

/** Fecha ISO-8601 `YYYY-MM-DD` con 2 dígitos por campo. */
private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

/** Devuelve [raw] si es una fecha ISO-8601 válida, o `null`. */
private fun dateOrNull(raw: String): String? =
    if (ISO_DATE.matches(raw) && runCatching { LocalDate.parse(raw) }.isSuccess) raw else null

/** Límite en `1..MAX_LIMIT` o `null` si el parámetro presente no es válido. */
private fun limitOrNull(raw: String): Int? =
    raw.toIntOrNull()?.takeIf { value -> value in 1..SearchService.MAX_LIMIT }
