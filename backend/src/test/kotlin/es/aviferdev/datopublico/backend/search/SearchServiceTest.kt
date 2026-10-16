package es.aviferdev.datopublico.backend.search

import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.persistence.FeedItem
import es.aviferdev.datopublico.backend.persistence.FeedRepository
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red, BD ni modelo) de [SearchService] con un
 * [FragmentRepository] y un [FeedRepository] en memoria y un doble de
 * [EmbeddingProvider] (escenarios 1–5 del spec): filtro de categoría, rango de
 * fechas inclusivo, solo resúmenes publicados, consulta sin resultados,
 * deduplicación/orden por relevancia, ventana de recuperación y fail-fast.
 */
class SearchServiceTest {

    private val becas = CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID
    private val normas = CategoryDto.NORMS_AND_LEGISLATION

    @Test
    fun `category filter returns only that category with published summaries`() = runTest {
        val specs = listOf(
            spec("BOE-A-1", becas, DATE_1),
            spec("BOE-A-2", normas, DATE_1),
        )
        val items = specs.map { fragment -> published(fragment.publicationId, fragment.category, fragment.date) }

        val result = service(specs, items).search("becas para jóvenes", category = becas)

        assertEquals("becas para jóvenes", result.query)
        assertEquals(listOf("BOE-A-1"), result.results.map { publication -> publication.id })
        assertTrue(result.results.all { publication -> publication.summary != null })
        assertTrue(result.results.all { publication -> publication.officialUrl.isNotBlank() })
        assertEquals(becas, result.results.first().category)
    }

    @Test
    fun `date range is inclusive on both ends`() = runTest {
        val specs = listOf(
            spec("BOE-A-1", becas, "2026-01-01"),
            spec("BOE-A-2", becas, "2026-03-15"),
            spec("BOE-A-3", becas, "2026-06-01"),
        )
        val items = specs.map { fragment -> published(fragment.publicationId, fragment.category, fragment.date) }

        val result = service(specs, items)
            .search("tema", publishedFrom = "2026-01-01", publishedTo = "2026-03-31")

        assertEquals(listOf("BOE-A-1", "BOE-A-2"), result.results.map { publication -> publication.id })
    }

    @Test
    fun `only publications with a published summary are served`() = runTest {
        val specs = listOf(
            spec("BOE-A-PUBLISHED", becas, DATE_1),
            spec("BOE-A-QUEUED", becas, DATE_1),
            spec("BOE-A-PENDING", becas, DATE_1),
        )
        val items = listOf(published("BOE-A-PUBLISHED", becas, DATE_1))

        val result = service(specs, items).search("tema")

        assertEquals(listOf("BOE-A-PUBLISHED"), result.results.map { publication -> publication.id })
    }

    @Test
    fun `a query without matches returns an empty result, not an error`() = runTest {
        val result = service(emptyList(), emptyList()).search("sin coincidencias")

        assertEquals("sin coincidencias", result.query)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `an inverted date range returns an empty result`() = runTest {
        val specs = listOf(spec("BOE-A-1", becas, DATE_1))
        val items = listOf(published("BOE-A-1", becas, DATE_1))

        val result = service(specs, items)
            .search("tema", publishedFrom = "2026-06-01", publishedTo = "2026-01-01")

        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `the same publication is deduplicated preserving the relevance order`() = runTest {
        val specs = listOf(
            spec("BOE-A-2", becas, DATE_1),
            spec("BOE-A-1", becas, DATE_1),
            spec("BOE-A-2", becas, DATE_1),
        )
        val items = listOf(
            published("BOE-A-1", becas, DATE_1),
            published("BOE-A-2", becas, DATE_1),
        )

        val result = service(specs, items).search("tema")

        assertEquals(listOf("BOE-A-2", "BOE-A-1"), result.results.map { publication -> publication.id })
    }

    @Test
    fun `the candidate window allows filling the limit with distinct publications`() = runTest {
        val duplicated = List(6) { spec("BOE-A-1", becas, DATE_1) }
        val specs = duplicated + spec("BOE-A-2", becas, DATE_1)
        val items = listOf(
            published("BOE-A-1", becas, DATE_1),
            published("BOE-A-2", becas, DATE_1),
        )

        val result = service(specs, items).search("tema", limit = 2)

        assertEquals(listOf("BOE-A-1", "BOE-A-2"), result.results.map { publication -> publication.id })
    }

    @Test
    fun `the result never exceeds the requested limit`() = runTest {
        val specs = (1..10).map { index -> spec("BOE-A-$index", becas, DATE_1) }
        val items = specs.map { fragment -> published(fragment.publicationId, fragment.category, fragment.date) }

        val result = service(specs, items).search("tema", limit = 3)

        assertEquals(listOf("BOE-A-1", "BOE-A-2", "BOE-A-3"), result.results.map { publication -> publication.id })
    }

    @Test
    fun `a blank query or an out of range limit fails fast`() = runTest {
        val service = service(emptyList(), emptyList())

        assertFailsWith<IllegalArgumentException> { service.search("   ") }
        assertFailsWith<IllegalArgumentException> { service.search("tema", limit = 0) }
        assertFailsWith<IllegalArgumentException> { service.search("tema", limit = SearchService.MAX_LIMIT + 1) }
    }

    private fun service(specs: List<FragmentSpec>, items: List<FeedItem>): SearchService =
        SearchService(
            hybridSearch = HybridSearch(FakeEmbeddingProvider(), InMemoryFragmentRepository(specs)),
            feedRepository = InMemoryFeedRepository(items),
        )

    private fun spec(publicationId: String, category: CategoryDto, date: String): FragmentSpec =
        FragmentSpec(publicationId, category, date)

    private fun published(publicationId: String, category: CategoryDto, date: String): FeedItem = FeedItem(
        publication = Publicacion(
            id = publicationId,
            title = "Publicación $publicationId",
            publicationDate = date,
            issuingBody = "Ministerio de prueba",
            section = BoeSectionDto.I,
            heading = null,
            texto = null,
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$publicationId",
            urlXml = null,
            urlPdf = null,
            rango = null,
            category = category,
            deadline = DeadlineDto(deadlineDate = "2026-12-31"),
        ),
        summary = SummaryEntity(
            publicationId = publicationId,
            whatChanges = "Cambia algo en $publicationId",
            whomItAffects = "A la ciudadanía",
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=$publicationId",
            status = "publicado",
        ),
    )

    /** Ficha de metadatos usada por el doble para aplicar el filtro de recuperación. */
    private data class FragmentSpec(val publicationId: String, val category: CategoryDto, val date: String)

    /** Doble de [FragmentRepository] que aplica el [SearchFilter] sobre las fichas. */
    private class InMemoryFragmentRepository(private val specs: List<FragmentSpec>) : FragmentRepository {
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
            specs.asSequence()
                .filter { spec -> filter.category == null || spec.category == filter.category }
                .filter { spec -> filter.publishedFrom == null || spec.date >= filter.publishedFrom }
                .filter { spec -> filter.publishedTo == null || spec.date <= filter.publishedTo }
                .take(limit)
                .map { spec -> FragmentMatch(fragment(spec.publicationId), distance = 0.0) }
                .toList()

        override fun findEmbedding(publicationId: String, order: Int): FloatArray? = null

        /** Fragmento de prueba (orden 0) asociado a [publicationId]. */
        private fun fragment(publicationId: String): FragmentEntity = FragmentEntity(
            publicationId = publicationId,
            order = 0,
            reference = null,
            content = "Contenido de $publicationId",
        )
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
        const val DATE_1 = "2026-01-01"
    }
}
