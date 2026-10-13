package es.aviferdev.datopublico.backend.feed

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FeedItem
import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [DailyFeedService] con un [FeedRepository] en
 * memoria (escenarios 1–6 del spec): día mixto paginado, solo publicados, tamaño
 * de página configurable (incluida página fuera de rango), día vacío, parámetros
 * inválidos y destacados deterministas con motivos no vacíos.
 */
class DailyFeedServiceTest {

    private val published = listOf(
        item("BOE-A-3", section = SeccionBoeDto.II_A),
        item("BOE-A-1", section = SeccionBoeDto.I, rank = "Ley", deadline = PlazoDto("2026-10-30")),
        item("BOE-A-2", section = SeccionBoeDto.II_B),
    )

    @Test
    fun `a day with published summaries returns a paginated feed with highlights`() = runTest {
        val feed = service(published).feed(DATE)

        assertEquals(DATE, feed.fecha)
        assertEquals(1, feed.page)
        assertEquals(DailyFeedService.DEFAULT_PAGE_SIZE, feed.pageSize)
        assertEquals(3, feed.total)
        assertEquals(1, feed.totalPages)
        assertEquals(listOf("BOE-A-1", "BOE-A-2", "BOE-A-3"), feed.publicaciones.map { it.id })
        assertTrue(feed.publicaciones.all { publication -> publication.resumen != null })
        assertTrue(feed.publicaciones.all { publication -> publication.urlOficial.isNotBlank() })
        assertEquals(1, feed.highlights.size)
        assertEquals("BOE-A-1", feed.highlights.first().publication.id)
        assertTrue(feed.highlights.first().reasons.isNotEmpty())
        assertEquals(PlazoDto("2026-10-30"), feed.highlights.first().publication.plazo)
    }

    @Test
    fun `only the items the repository returns as published are served`() = runTest {
        val feed = service(listOf(item("BOE-A-1"), item("BOE-A-2"))).feed(DATE)

        assertEquals(2, feed.total)
        assertEquals(listOf("BOE-A-1", "BOE-A-2"), feed.publicaciones.map { it.id })
    }

    @Test
    fun `page two with page size one returns the second item in id order`() = runTest {
        val feed = service(published).feed(DATE, page = 2, pageSize = 1)

        assertEquals(2, feed.page)
        assertEquals(1, feed.pageSize)
        assertEquals(3, feed.total)
        assertEquals(3, feed.totalPages)
        assertEquals(listOf("BOE-A-2"), feed.publicaciones.map { it.id })
    }

    @Test
    fun `a page beyond the last one is empty but keeps the metadata`() = runTest {
        val feed = service(published).feed(DATE, page = 99, pageSize = 1)

        assertEquals(99, feed.page)
        assertTrue(feed.publicaciones.isEmpty())
        assertEquals(3, feed.total)
        assertEquals(3, feed.totalPages)
    }

    @Test
    fun `a day without published content returns an empty feed`() = runTest {
        val feed = service(published).feed("2026-01-01")

        assertEquals(0, feed.total)
        assertEquals(0, feed.totalPages)
        assertTrue(feed.publicaciones.isEmpty())
        assertTrue(feed.highlights.isEmpty())
    }

    @Test
    fun `invalid pagination fails fast`() = runTest {
        val service = service(published)

        assertFailsWith<IllegalArgumentException> { service.feed(DATE, page = 0) }
        assertFailsWith<IllegalArgumentException> { service.feed(DATE, pageSize = 0) }
        assertFailsWith<IllegalArgumentException> {
            service.feed(DATE, pageSize = DailyFeedService.MAX_PAGE_SIZE + 1)
        }
    }

    @Test
    fun `highlights are deterministic and expose non empty reasons`() = runTest {
        val service = service(published.reversed())

        val first = service.feed(DATE)
        val second = service.feed(DATE)

        assertEquals(first, second)
        assertEquals(
            listOf("BOE-A-1", "BOE-A-2", "BOE-A-3"),
            first.publicaciones.map { it.id },
        )
        assertTrue(first.highlights.all { highlight -> highlight.reasons.isNotEmpty() })
        assertNotNull(first.highlights.firstOrNull())
    }

    private fun service(items: List<FeedItem>): DailyFeedService =
        DailyFeedService(InMemoryFeedRepository(items))

    private fun item(
        id: String,
        section: SeccionBoeDto = SeccionBoeDto.II_A,
        rank: String? = null,
        deadline: PlazoDto? = null,
    ): FeedItem = FeedItem(
        publication = Publicacion(
            id = id,
            titulo = "Publicación $id",
            fechaPublicacion = DATE,
            organismo = null,
            seccion = section,
            epigrafe = null,
            texto = null,
            urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
            urlXml = null,
            urlPdf = null,
            rango = rank,
            categoria = CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            plazo = deadline,
        ),
        summary = SummaryEntity(
            publicationId = id,
            whatChanges = "Cambia algo en $id",
            whomItAffects = "A la ciudadanía",
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=$id",
            status = "publicado",
        ),
    )

    /** Doble en memoria de [FeedRepository] filtrado por fecha de publicación. */
    private class InMemoryFeedRepository(private val items: List<FeedItem>) : FeedRepository {
        override fun listPublishedByDate(publicationDate: String): List<FeedItem> =
            items.filter { item -> item.publication.fechaPublicacion == publicationDate }
    }

    private companion object {
        const val DATE = "2026-10-11"
    }
}
