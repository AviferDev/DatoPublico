package es.aviferdev.datopublico.backend.rag.retrieval

import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider

/**
 * **Recuperación híbrida** (FT00017, tarea `0.3-T04`): vectoriza la consulta y
 * recupera los fragmentos más relevantes combinando el filtro SQL por metadatos
 * con la **similitud vectorial**.
 *
 * Es un **servicio de aplicación** que compone el proveedor de embeddings
 * ([EmbeddingProvider], FT00015) y el repositorio de fragmentos
 * ([FragmentRepository], FT00016/FT00017). **No** conoce Ktor, SQL ni ONNX: la
 * vectorización la delega en el proveedor y la consulta filtrada, en el
 * repositorio. Es una **librería sin wiring**: no se construye en `Application.kt`
 * ni en el job; la consumirán la evaluación (FT00018) y el chat (FT00019+).
 *
 * La consulta se vectoriza con [EmbeddingProvider.embedQuery], que aplica el
 * prefijo obligatorio `query: ` del modelo E5; los fragmentos se indexaron con
 * `passage: ` ([es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer]).
 *
 * @param embeddingProvider proveedor que vectoriza la consulta.
 * @param fragmentRepository repositorio que resuelve los vecinos con filtro.
 */
class HybridSearch(
    private val embeddingProvider: EmbeddingProvider,
    private val fragmentRepository: FragmentRepository,
) {

    /**
     * Devuelve los [limit] fragmentos más cercanos a [query] que cumplen [filter],
     * ordenados por distancia coseno ascendente (menor = más relevante).
     *
     * @param query texto de la consulta (no puede estar en blanco).
     * @param filter filtro de metadatos; por defecto, sin filtros (similitud pura).
     * @param limit número máximo de fragmentos (`>= 1`).
     * @return coincidencias con su fragmento y su `distance`.
     * @throws IllegalArgumentException si [query] está en blanco o `limit < 1`
     *   (fail-fast **antes** de vectorizar o tocar la base de datos).
     */
    fun search(
        query: String,
        filter: SearchFilter = SearchFilter(),
        limit: Int = DEFAULT_LIMIT,
    ): List<FragmentMatch> {
        require(query.isNotBlank()) { "La consulta no puede estar en blanco." }
        require(limit >= 1) { "El límite de resultados debe ser >= 1, pero es $limit." }
        val queryEmbedding = embeddingProvider.embedQuery(query)
        return fragmentRepository.findNearest(queryEmbedding, filter, limit)
    }

    private companion object {
        /** Número de fragmentos por defecto si el llamante no lo indica. */
        const val DEFAULT_LIMIT = 10
    }
}
