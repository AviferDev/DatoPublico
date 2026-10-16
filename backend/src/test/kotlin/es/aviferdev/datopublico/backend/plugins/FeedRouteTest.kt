package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FeedItem
import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.DailySummaryDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate de la ruta `GET /v1/feed` (FT00026) con `testApplication` y un
 * [FeedRepository] en memoria: `200` con el JSON paginado (fecha, publicaciones,
 * destacados, página), `400` por fecha ausente/mal formada o paginación inválida,
 * `404` cuando el servicio no se registra (sin base de datos) y `404` en la ruta
 * **sin** el prefijo de versión `/v1` (FT00029).
 */
class FeedRouteTest {

    @Test
    fun `feed responds 200 with the paginated json for a valid day`() = testApplication {
        installFeed(listOf(item("BOE-A-1")))

        val response = client.get("/v1/feed?fecha=$DATE")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val feed = DatoPublicoJson.decodeFromString<DailySummaryDto>(response.bodyAsText())
        assertEquals(DATE, feed.date)
        assertEquals(1, feed.page)
        assertEquals(20, feed.pageSize)
        assertEquals(1, feed.total)
        assertEquals(1, feed.totalPages)
        assertEquals(listOf("BOE-A-1"), feed.publications.map { it.id })
        assertTrue(feed.highlights.isNotEmpty())
    }

    @Test
    fun `feed responds 200 with empty lists for a day without content`() = testApplication {
        installFeed(listOf(item("BOE-A-1")))

        val response = client.get("/v1/feed?fecha=2026-01-01")

        assertEquals(HttpStatusCode.OK, response.status)
        val feed = DatoPublicoJson.decodeFromString<DailySummaryDto>(response.bodyAsText())
        assertEquals(0, feed.total)
        assertEquals(0, feed.totalPages)
        assertTrue(feed.publications.isEmpty())
    }

    @Test
    fun `feed responds 400 when the date is missing or not iso`() = testApplication {
        installFeed(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed?fecha=ayer").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed?fecha=2026-13-40").status)
    }

    @Test
    fun `feed responds 400 when the pagination is invalid`() = testApplication {
        installFeed(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed?fecha=$DATE&pagina=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed?fecha=$DATE&pagina=abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed?fecha=$DATE&tamanoPagina=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/feed?fecha=$DATE&tamanoPagina=101").status)
    }

    @Test
    fun `feed responds 404 when the service is not registered`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/feed?fecha=$DATE").status)
    }

    @Test
    fun `feed is only served under the v1 prefix`() = testApplication {
        installFeed(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.OK, client.get("/v1/feed?fecha=$DATE").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/feed?fecha=$DATE").status)
    }

    /** Registra serialización + rutas con un servicio de feed sobre un repositorio en memoria. */
    private fun ApplicationTestBuilder.installFeed(items: List<FeedItem>) {
        application {
            configureSerialization()
            configureRouting(DailyFeedService(InMemoryFeedRepository(items)))
        }
    }

    private fun item(id: String): FeedItem = FeedItem(
        publication = Publicacion(
            id = id,
            title = "Ley de prueba",
            publicationDate = DATE,
            issuingBody = "Jefatura del Estado",
            section = SeccionBoeDto.I,
            heading = null,
            texto = null,
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
            urlXml = null,
            urlPdf = null,
            rango = "Ley",
            category = CategoriaDto.NORMAS_Y_LEGISLACION,
        ),
        summary = SummaryEntity(
            publicationId = id,
            whatChanges = "Cambia algo",
            whomItAffects = "A la ciudadanía",
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=$id",
            status = "publicado",
        ),
    )

    /** Doble en memoria de [FeedRepository]. */
    private class InMemoryFeedRepository(private val items: List<FeedItem>) : FeedRepository {
        override fun listPublishedByDate(publicationDate: String): List<FeedItem> =
            items.filter { item -> item.publication.publicationDate == publicationDate }

        override fun listPublishedByIds(ids: List<String>): List<FeedItem> =
            items.filter { item -> item.publication.id in ids }
    }

    private companion object {
        const val DATE = "2026-10-11"
    }
}
