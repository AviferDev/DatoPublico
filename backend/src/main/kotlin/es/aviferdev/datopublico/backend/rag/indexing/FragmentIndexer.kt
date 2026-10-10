package es.aviferdev.datopublico.backend.rag.indexing

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.persistence.toEntity
import es.aviferdev.datopublico.backend.rag.chunking.ArticleChunker
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider

/**
 * **Indexado mínimo** de una publicación (FT00017): la fragmenta, persiste cada
 * fragmento y guarda su embedding de *pasaje*.
 *
 * Cierra el aplazamiento de FT00016 («la generación real de embeddings y la
 * composición proveedor + repositorio»), pero es una **librería sin wiring**: no
 * indexa el corpus completo, no hace *batching* de inferencia, no usa una
 * transacción multi-repositorio y **no** se construye en `Application.kt` ni en el
 * job/backfill (eso es operación, feature posterior).
 *
 * Es **idempotente** por el *upsert* de `(publicacion_id, orden)` de
 * [FragmentRepository.save]: reindexar la misma publicación sobrescribe la fila y
 * su embedding sin duplicar.
 *
 * @param embeddingProvider proveedor que vectoriza cada fragmento con
 *   [EmbeddingProvider.embedPassage] (prefijo `passage: `).
 * @param fragmentRepository repositorio donde se guardan fragmento y embedding.
 * @param chunker fragmentador puro ([ArticleChunker]); inyectable para tests.
 */
class FragmentIndexer(
    private val embeddingProvider: EmbeddingProvider,
    private val fragmentRepository: FragmentRepository,
    private val chunker: ArticleChunker = ArticleChunker(),
) {

    /**
     * Indexa [publication] y devuelve el número de fragmentos procesados.
     *
     * Por cada [es.aviferdev.datopublico.backend.rag.Fragment] del chunker:
     * `save(fragment.toEntity())` (crea/actualiza la fila) y
     * `saveEmbedding(publicationId, order, embedPassage(content))` (guarda el
     * vector del fragmento). Una publicación sin texto produce `0` fragmentos y
     * **no** lanza excepción.
     *
     * @return número de fragmentos indexados.
     */
    fun index(publication: Publicacion): Int {
        val fragments = chunker.chunk(publication)
        fragments.forEach { fragment ->
            fragmentRepository.save(fragment.toEntity())
            val embedding = embeddingProvider.embedPassage(fragment.content)
            fragmentRepository.saveEmbedding(fragment.publicationId, fragment.order, embedding)
        }
        return fragments.size
    }
}
