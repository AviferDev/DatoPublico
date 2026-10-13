package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.publication.PublicationDetailService
import es.aviferdev.datopublico.backend.search.SearchService
import io.ktor.server.application.Application
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
 */
fun Application.configureRouting(
    feedService: DailyFeedService? = null,
    detailService: PublicationDetailService? = null,
    searchService: SearchService? = null,
    chatService: ChatService? = null,
) {
    routing {
        healthRoutes()
        openApiRoutes()
        route("/v1") {
            if (feedService != null) {
                feedRoutes(feedService)
            }
            if (detailService != null) {
                publicationDetailRoutes(detailService)
            }
            if (searchService != null) {
                searchRoutes(searchService)
            }
            if (chatService != null) {
                chatRoutes(chatService)
            }
        }
    }
}
