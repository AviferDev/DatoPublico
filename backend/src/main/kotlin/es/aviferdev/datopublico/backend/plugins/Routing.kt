package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.publication.PublicationDetailService
import es.aviferdev.datopublico.backend.search.SearchService
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Rutas del backend. Registra **siempre** las operacionales (el healthcheck
 * `/health` y el documento `/openapi.json`) y, bajo el prefijo de versión `/v1`,
 * las rutas de dominio cuyas dependencias existen: desde FT00026, `/v1/feed` si
 * [feedService] no es `null`; desde FT00027, `/v1/publicacion/{id}` si
 * [detailService] no es `null`; desde FT00028, `/v1/buscar` si [searchService] no
 * es `null`; desde FT00031, `/v1/chat` si [chatService] no es `null` (patrón
 * «solo las rutas cuyas dependencias existen»).
 *
 * El **versionado del path** (FT00029) solo **prefija** `/v1` a las rutas de
 * dominio: no cambia métodos, parámetros ni semántica. `/health` y
 * `/openapi.json` quedan **fuera** del prefijo por ser operacionales y estables.
 *
 * @param feedService servicio del feed diario, o `null` si no hay base de datos.
 * @param detailService servicio del detalle de publicación, o `null` si no hay
 *   base de datos.
 * @param searchService servicio de búsqueda, o `null` si no hay base de datos o
 *   proveedor de embeddings.
 * @param chatService servicio del chat RAG, o `null` si falta la base de datos, el
 *   proveedor de embeddings o el proveedor de texto (la ruta no se registra).
 * @param applyRateLimits si es `true` (FT00030), las rutas de dominio se envuelven
 *   en su limitador (`GENERAL` y, el chat, `CHAT`); por defecto es `false` para no
 *   invocar `rateLimit(...)` sin el plugin `RateLimit` instalado (los tests de ruta
 *   existentes no lo instalan). `/health` y `/openapi.json` quedan **fuera**.
 */
fun Application.configureRouting(
    feedService: DailyFeedService? = null,
    detailService: PublicationDetailService? = null,
    searchService: SearchService? = null,
    chatService: ChatService? = null,
    applyRateLimits: Boolean = false,
) {
    routing {
        healthRoutes()
        openApiRoutes()
        route("/v1") {
            if (feedService != null) {
                rateLimited(applyRateLimits, ApiRateLimiter.GENERAL) { feedRoutes(feedService) }
            }
            if (detailService != null) {
                rateLimited(applyRateLimits, ApiRateLimiter.GENERAL) { publicationDetailRoutes(detailService) }
            }
            if (searchService != null) {
                rateLimited(applyRateLimits, ApiRateLimiter.GENERAL) { searchRoutes(searchService) }
            }
            if (chatService != null) {
                rateLimited(applyRateLimits, ApiRateLimiter.CHAT) { chatRoutes(chatService) }
            }
        }
    }
}

/**
 * Registra [build] bajo el limitador [name] si [applyRateLimits] está activo; si
 * no, lo registra **sin** rate limiting (comportamiento previo a FT00030).
 *
 * `Route.rateLimit(...)` **falla en el arranque** si el plugin `RateLimit` no está
 * instalado, de ahí el valor por defecto `false` de [configureRouting]: así los
 * tests de ruta existentes (que no instalan el plugin) siguen verdes.
 */
private fun Route.rateLimited(applyRateLimits: Boolean, name: RateLimitName, build: Route.() -> Unit) {
    if (applyRateLimits) {
        rateLimit(name, build)
    } else {
        build()
    }
}
