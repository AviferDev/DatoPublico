package es.aviferdev.datopublico.backend.rag.indexing

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.chunking.ArticleChunker
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [FragmentIndexer] con **dobles deterministas**:
 * fragmenta la publicación, persiste cada fragmento y guarda su embedding de
 * *pasaje* ([EmbeddingProvider.embedPassage]); es idempotente por el *upsert* de
 * `(publicacion_id, orden)` (escenario 6 del spec).
 */
class FragmentIndexerTest {

    private val publication = Publicacion(
        id = "BOE-A-2026-0001",
        titulo = "Resolución de prueba",
        fechaPublicacion = "2026-10-09",
        organismo = null,
        seccion = SeccionBoeDto.I,
        epigrafe = null,
        texto = "Artículo 1\nContenido uno.\nArtículo 2\nContenido dos.",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-0001",
        urlXml = null,
        urlPdf = null,
        rango = null,
    )

    @Test
    fun `index persists each fragment and its passage embedding`() {
        val provider = PassageRecordingEmbeddingProvider()
        val repository = RecordingFragmentRepository()
        val indexer = FragmentIndexer(provider, repository, ArticleChunker())

        val count = indexer.index(publication)

        assertEquals(2, count)
        assertEquals(listOf(0, 1), repository.savedFragments.map { fragment -> fragment.order })
        assertEquals(listOf("Artículo 1\nContenido uno.", "Artículo 2\nContenido dos."), provider.passages)
        assertEquals(setOf(0, 1), repository.embeddings.keys.map { key -> key.second }.toSet())
        repository.savedFragments.forEach { fragment ->
            val embedding = repository.embeddings[publication.id to fragment.order]
            assertTrue(embedding != null, "cada fragmento guardado debe tener su embedding")
            assertContentEquals(provider.embeddingFor(fragment.order), embedding)
        }
    }

    @Test
    fun `index is idempotent by publication and order`() {
        val provider = PassageRecordingEmbeddingProvider()
        val repository = RecordingFragmentRepository()
        val indexer = FragmentIndexer(provider, repository, ArticleChunker())

        indexer.index(publication)
        indexer.index(publication)

        assertEquals(4, repository.savedFragments.size, "el indexado se ejecutó dos veces")
        assertEquals(2, repository.upserted.size, "el upsert no debe duplicar por (publicacion_id, orden)")
        assertEquals(2, repository.embeddings.size, "el embedding se sobrescribe por (publicacion_id, orden)")
        assertEquals(setOf(publication.id to 0, publication.id to 1), repository.upserted.keys)
    }

    @Test
    fun `index returns zero and saves nothing without text`() {
        val provider = PassageRecordingEmbeddingProvider()
        val repository = RecordingFragmentRepository()
        val indexer = FragmentIndexer(provider, repository, ArticleChunker())

        val count = indexer.index(publication.copy(texto = null))

        assertEquals(0, count)
        assertTrue(repository.savedFragments.isEmpty(), "no debe guardar fragmentos sin texto")
        assertTrue(repository.embeddings.isEmpty(), "no debe vectorizar sin texto")
    }

    /** Doble de [EmbeddingProvider]: vector unitario determinista por *pasaje* y registra los textos. */
    private class PassageRecordingEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = DIMENSIONS

        /** Textos de fragmento recibidos por [embedPassage], en orden. */
        val passages = mutableListOf<String>()

        override fun embedQuery(text: String): FloatArray = vector(0)

        override fun embedPassage(text: String): FloatArray {
            passages.add(text)
            return vector(passages.size)
        }

        /** Vector del *pasaje* número [position] (1-based), determinista. */
        fun embeddingFor(position: Int): FloatArray = vector(position + 1)

        override fun close() = Unit

        private fun vector(seed: Int): FloatArray =
            FloatArray(DIMENSIONS) { index -> if (index == seed % DIMENSIONS) 1f else 0f }

        private companion object {
            const val DIMENSIONS = 3
        }
    }

    /** Doble de [FragmentRepository]: emula el *upsert* y guarda embeddings por clave. */
    private class RecordingFragmentRepository : FragmentRepository {
        /** Fragmentos recibidos por [save], en orden (incluye repeticiones). */
        val savedFragments = mutableListOf<FragmentEntity>()

        /** Estado *upsert* por `(publicacion_id, orden)`. */
        val upserted = mutableMapOf<Pair<String, Int>, FragmentEntity>()

        /** Embeddings guardados por `(publicacion_id, orden)`. */
        val embeddings = mutableMapOf<Pair<String, Int>, FloatArray>()

        override fun save(fragment: FragmentEntity): FragmentEntity {
            savedFragments.add(fragment)
            upserted[fragment.publicationId to fragment.order] = fragment
            return fragment
        }

        override fun listByPublication(publicationId: String): List<FragmentEntity> =
            upserted.filterKeys { key -> key.first == publicationId }.values.toList()

        override fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean {
            embeddings[publicationId to order] = embedding
            return true
        }

        override fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch> =
            findNearest(queryEmbedding, SearchFilter(), limit)

        override fun findNearest(
            queryEmbedding: FloatArray,
            filter: SearchFilter,
            limit: Int,
        ): List<FragmentMatch> = emptyList()

        override fun findEmbedding(publicationId: String, order: Int): FloatArray? =
            embeddings[publicationId to order]
    }
}
