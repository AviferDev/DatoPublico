package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.publication.PublicationDetailService
import es.aviferdev.datopublico.backend.search.SearchService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * Rutas del backend. Registra el healthcheck (`/health`) **siempre** y las rutas
 * cuyas dependencias existen: desde FT00026, `/feed` si [feedService] no es
 * `null`; desde FT00027, `/publicacion/{id}` si [detailService] no es `null`;
 * desde FT00028, `/buscar` si [searchService] no es `null`; desde FT00031, `/chat`
 * si [chatService] no es `null` (patrón «solo las rutas cuyas dependencias
 * existen»).
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
