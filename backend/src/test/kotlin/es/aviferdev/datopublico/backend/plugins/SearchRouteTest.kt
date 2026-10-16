package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FeedItem
import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.backend.search.SearchService
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SearchResultsDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate de la ruta `GET /v1/buscar` (FT00028) con `testApplication` y
 * dobles en memoria: `200` con el JSON (`q` + `resultados`), `400` por `q`
 * ausente/en blanco, `categoria` inválida, fecha no ISO o `limite` inválido, y
 * `404` cuando el servicio no se registra (sin BD o sin modelo).
 */
class SearchRouteTest {

    @Test
    fun `buscar responds 200 with the echoed query and the published results`() = testApplication {
        installSearch(listOf(item("BOE-A-1")))

        val response = client.get("/v1/buscar?q=becas&categoria=becas_subvenciones_y_ayudas")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val results = DatoPublicoJson.decodeFromString<SearchResultsDto>(response.bodyAsText())
        assertEquals("becas", results.query)
        assertEquals(listOf("BOE-A-1"), results.results.map { publication -> publication.id })
        assertTrue(results.results.first().summary != null)
        assertEquals(CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS, results.results.first().category)
    }

    @Test
    fun `buscar responds 200 with an empty result when nothing matches`() = testApplication {
        installSearch(emptyList())

        val response = client.get("/v1/buscar?q=sin+coincidencias")

        assertEquals(HttpStatusCode.OK, response.status)
        val results = DatoPublicoJson.decodeFromString<SearchResultsDto>(response.bodyAsText())
        assertEquals("sin coincidencias", results.query)
        assertTrue(results.results.isEmpty())
    }

    @Test
    fun `buscar responds 400 when the query is missing or blank`() = testApplication {
        installSearch(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=%20%20").status)
    }

    @Test
    fun `buscar responds 400 when the category token is invalid`() = testApplication {
        installSearch(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&categoria=no_existe").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&categoria=NORMAS_Y_LEGISLACION").status)
    }

    @Test
    fun `buscar responds 400 when a date is not iso`() = testApplication {
        installSearch(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&desde=ayer").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&hasta=2026-13-40").status)
    }

    @Test
    fun `buscar responds 400 when the limit is invalid`() = testApplication {
        installSearch(listOf(item("BOE-A-1")))

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&limite=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&limite=abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/buscar?q=tema&limite=51").status)
    }

    @Test
    fun `buscar responds 404 when the service is not registered`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/buscar?q=tema").status)
    }

    /** Registra serialización + rutas con un servicio de búsqueda sobre dobles en memoria. */
    private fun ApplicationTestBuilder.installSearch(items: List<FeedItem>) {
        application {
            configureSerialization()
            configureRouting(
                searchService = SearchService(
                    hybridSearch = HybridSearch(FakeEmbeddingProvider(), FixedFragmentRepository(items)),
                    feedRepository = InMemoryFeedRepository(items),
                ),
            )
        }
    }

    private fun item(id: String): FeedItem = FeedItem(
        publication = Publicacion(
            id = id,
            title = "Publicación $id",
            publicationDate = DATE,
            issuingBody = "Ministerio de prueba",
            section = SeccionBoeDto.I,
            heading = null,
            texto = null,
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
            urlXml = null,
            urlPdf = null,
            rango = null,
            category = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        ),
        summary = SummaryEntity(
            publicationId = id,
            whatChanges = "Cambia algo en $id",
            whomItAffects = "A la ciudadanía",
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=$id",
            status = "publicado",
        ),
    )

    /** Doble de [FragmentRepository] que devuelve los fragmentos de las publicaciones dadas. */
    private class FixedFragmentRepository(private val items: List<FeedItem>) : FragmentRepository {
        override fun save(fragment: FragmentEntity): FragmentEntity = fragment

        override fun listByPublication(publicationId: String): List<FragmentEntity> = emptyList()

        override fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean = false

        override fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch> =
            findNearest(queryEmbedding, SearchFilter(), limit)

        override fun findNearest(
            queryEmbedding: FloatArray,
            filter: SearchFilter,
            limit: Int,
        ): List<FragmentMatch> =
            items.take(limit).map { item ->
                FragmentMatch(
                    fragment = FragmentEntity(
                        publicationId = item.publication.id,
                        order = 0,
                        reference = null,
                        content = "Contenido de ${item.publication.id}",
                    ),
                    distance = 0.0,
                )
            }

        override fun findEmbedding(publicationId: String, order: Int): FloatArray? = null
    }

    /** Doble de [FeedRepository] que solo conoce las publicaciones con resumen publicado. */
    private class InMemoryFeedRepository(private val items: List<FeedItem>) : FeedRepository {
        override fun listPublishedByDate(publicationDate: String): List<FeedItem> = emptyList()

        override fun listPublishedByIds(ids: List<String>): List<FeedItem> =
            items.filter { item -> item.publication.id in ids }
    }

    /** Doble determinista de [EmbeddingProvider] (no carga el modelo E5). */
    private class FakeEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = 384

        override fun embedQuery(text: String): FloatArray = FloatArray(dimensions)

        override fun embedPassage(text: String): FloatArray = FloatArray(dimensions)

        override fun close() = Unit
    }

    private companion object {
        const val DATE = "2026-10-11"
    }
}
