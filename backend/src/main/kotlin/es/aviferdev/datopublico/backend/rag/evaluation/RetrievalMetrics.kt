package es.aviferdev.datopublico.backend.rag.evaluation

/**
 * Referencia **estable** de un fragmento dentro del corpus de evaluación: la
 * publicación oficial y la posición (`order`) que le asigna el chunker
 * determinista ([es.aviferdev.datopublico.backend.rag.chunking.ArticleChunker]).
 *
 * Es un **modelo de valor interno** del backend: **no** es `@Serializable` ni un
 * DTO de transporte (el golden set lo expone con su propio DTO), por eso va **sin
 * sufijo** (`CONSTRAINTS.md` §«Nombres y contratos»).
 *
 * @property publicationId identificador oficial de la publicación (p. ej. `EVAL-1`).
 * @property order posición del fragmento dentro de la publicación (contigua desde `0`).
 */
data class FragmentRef(
    val publicationId: String,
    val order: Int,
)

/**
 * Métricas **puras** de evaluación de la recuperación (FT00018): `precision@k`,
 * `recall@k` y *Mean Reciprocal Rank* (MRR).
 *
 * No tiene estado ni dependencias (sin red, BD, reloj ni aleatoriedad): recibe las
 * listas de fragmentos recuperados y el conjunto de relevantes y devuelve un
 * `Double`. Los casos límite están documentados por función y son los que cubre
 * `RetrievalMetricsTest`.
 */
object RetrievalMetrics {

    /**
     * **Precisión@k**: proporción de aciertos entre los `k` primeros recuperados.
     *
     * Definición: `aciertos(top-k) / min(k, recuperados)`. Es `0.0` si no hay
     * recuperados; `k` puede ser mayor que el número de recuperados (el
     * denominador se recorta). Si no hay ningún acierto, también es `0.0`.
     *
     * @param retrieved fragmentos recuperados, en orden de relevancia descendente.
     * @param relevant conjunto de fragmentos considerados relevantes.
     * @param k número de posiciones de la ventana (`>= 1`).
     * @return precisión en `[0, 1]`.
     * @throws IllegalArgumentException si `k < 1` (fail-fast).
     */
    fun precisionAtK(retrieved: List<FragmentRef>, relevant: Set<FragmentRef>, k: Int): Double {
        require(k >= 1) { "k debe ser >= 1, pero es $k." }
        val topK = retrieved.take(k)
        return if (topK.isEmpty()) 0.0 else topK.count { ref -> ref in relevant }.toDouble() / topK.size
    }

    /**
     * **Recall@k**: proporción de relevantes que aparecen entre los `k` primeros.
     *
     * Definición: `aciertos(top-k) / |relevantes|`. Es `0.0` si no hay relevantes
     * (el conjunto esperado está vacío); si no hay aciertos en la ventana, también
     * es `0.0`.
     *
     * @param retrieved fragmentos recuperados, en orden de relevancia descendente.
     * @param relevant conjunto de fragmentos considerados relevantes.
     * @param k número de posiciones de la ventana (`>= 1`).
     * @return cobertura en `[0, 1]`.
     * @throws IllegalArgumentException si `k < 1` (fail-fast).
     */
    fun recallAtK(retrieved: List<FragmentRef>, relevant: Set<FragmentRef>, k: Int): Double {
        require(k >= 1) { "k debe ser >= 1, pero es $k." }
        val hits = retrieved.take(k).count { ref -> ref in relevant }
        return if (relevant.isEmpty()) 0.0 else hits.toDouble() / relevant.size
    }

    /**
     * ***Mean Reciprocal Rank*** de una única consulta: `1 / posición` del primer
     * acierto (1-based). Es `0.0` si no hay ningún acierto.
     *
     * @param retrieved fragmentos recuperados, en orden de relevancia descendente.
     * @param relevant conjunto de fragmentos considerados relevantes.
     * @return recíproco de la posición del primer acierto en `[0, 1]`.
     */
    fun reciprocalRank(retrieved: List<FragmentRef>, relevant: Set<FragmentRef>): Double {
        val index = retrieved.indexOfFirst { ref -> ref in relevant }
        return if (index < 0) 0.0 else 1.0 / (index + 1)
    }
}
