package es.aviferdev.datopublico.backend.rag.retrieval

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.model.CategoriaDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [HybridSearch] con **dobles deterministas**:
 * vectoriza la consulta una sola vez con [EmbeddingProvider.embedQuery] y delega
 * en [FragmentRepository.findNearest] con el vector, el filtro y el límite; valida
 * el fail-fast de consulta en blanco y de límite (escenario 5 del spec).
 */
class HybridSearchTest {

    private val queryVector = floatArrayOf(1f, 0f, 0f)

    @Test
    fun `search vectorizes the query once and delegates with vector filter and limit`() {
        val provider = RecordingEmbeddingProvider(queryVector)
        val repository = RecordingFragmentRepository()
        val match = fragmentMatch(order = 0)
        repository.matches = listOf(match)
        val filter = SearchFilter(category = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS)
        val search = HybridSearch(provider, repository)

        val results = search.search("ayudas para estudiantes", filter, limit = 5)

        assertEquals(listOf("ayudas para estudiantes"), provider.queries)
        assertSame(queryVector, repository.lastQueryEmbedding)
        assertEquals(filter, repository.lastFilter)
        assertEquals(5, repository.lastLimit)
        assertEquals(listOf(match), results)
    }

    @Test
    fun `search defaults to an empty filter and the default limit`() {
        val provider = RecordingEmbeddingProvider(queryVector)
        val repository = RecordingFragmentRepository()
        val search = HybridSearch(provider, repository)

        search.search("consulta")

        assertEquals(SearchFilter(), repository.lastFilter)
        assertEquals(10, repository.lastLimit)
    }

    @Test
    fun `a blank query fails fast before vectorizing or touching the repository`() {
        val provider = RecordingEmbeddingProvider(queryVector)
        val repository = RecordingFragmentRepository()
        val search = HybridSearch(provider, repository)

        assertFailsWith<IllegalArgumentException> { search.search("   ") }

        assertTrue(provider.queries.isEmpty(), "no debe vectorizar una consulta en blanco")
        assertEquals(0, repository.calls)
    }

    @Test
    fun `a non positive limit fails fast before touching the repository`() {
        val provider = RecordingEmbeddingProvider(queryVector)
        val repository = RecordingFragmentRepository()
        val search = HybridSearch(provider, repository)

        assertFailsWith<IllegalArgumentException> { search.search("consulta", limit = 0) }

        assertTrue(provider.queries.isEmpty(), "no debe vectorizar si el límite es inválido")
        assertEquals(0, repository.calls)
    }

    @Test
    fun `search returns the repository matches unchanged`() {
        val provider = RecordingEmbeddingProvider(queryVector)
        val repository = RecordingFragmentRepository()
        val expected = listOf(fragmentMatch(order = 0), fragmentMatch(order = 1))
        repository.matches = expected
        val search = HybridSearch(provider, repository)

        val results = search.search("consulta", limit = 2)

        assertEquals(expected, results)
    }

    private fun fragmentMatch(order: Int): FragmentMatch = FragmentMatch(
        fragment = FragmentEntity(
            publicationId = "BOE-A-2026-0001",
            order = order,
            reference = "Artículo ${order + 1}",
            content = "Contenido ${order + 1}",
        ),
        distance = order.toDouble(),
    )

    /** Doble de [EmbeddingProvider]: devuelve siempre [vector] y registra las consultas. */
    private class RecordingEmbeddingProvider(private val vector: FloatArray) : EmbeddingProvider {
        override val dimensions: Int = vector.size

        /** Consultas recibidas por [embedQuery], en orden. */
        val queries = mutableListOf<String>()

        override fun embedQuery(text: String): FloatArray {
            queries.add(text)
            return vector
        }

        override fun embedPassage(text: String): FloatArray = vector

        override fun close() = Unit
    }

    /** Doble de [FragmentRepository]: registra la última llamada a `findNearest` filtrado. */
    private class RecordingFragmentRepository : FragmentRepository {
        /** Coincidencias que devolverá `findNearest`. */
        var matches: List<FragmentMatch> = emptyList()

        /** Número de llamadas al `findNearest` filtrado. */
        var calls: Int = 0

        var lastQueryEmbedding: FloatArray? = null
        var lastFilter: SearchFilter? = null
        var lastLimit: Int? = null

        override fun save(fragment: FragmentEntity): FragmentEntity = fragment

        override fun listByPublication(publicationId: String): List<FragmentEntity> = emptyList()

        override fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean = true

        override fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch> =
            findNearest(queryEmbedding, SearchFilter(), limit)

        override fun findNearest(
            queryEmbedding: FloatArray,
            filter: SearchFilter,
            limit: Int,
        ): List<FragmentMatch> {
            lastQueryEmbedding = queryEmbedding
            lastFilter = filter
            lastLimit = limit
            calls += 1
            return matches
        }

        override fun findEmbedding(publicationId: String, order: Int): FloatArray? = null
    }
}
