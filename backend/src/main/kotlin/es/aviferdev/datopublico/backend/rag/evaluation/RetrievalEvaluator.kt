package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter

/**
 * Recuperador **inyectable** de la evaluación (FT00018).
 *
 * Es un `fun interface` cuya firma encaja con
 * [es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch.search] (`query`,
 * `filter`, `limit`): la CLI la implementa con una lambda que delega en
 * `HybridSearch`, y los tests usan un doble determinista. Así el evaluador **no**
 * conoce el proveedor de embeddings, el repositorio ni Ktor.
 */
fun interface Retriever {
    /**
     * Recupera hasta [limit] fragmentos para [query] que cumplen [filter].
     *
     * @param query texto de la consulta (no vacío).
     * @param filter filtro de metadatos de la recuperación híbrida.
     * @param limit número máximo de fragmentos (`>= 1`).
     * @return coincidencias ordenadas por relevancia descendente.
     */
    fun retrieve(query: String, filter: SearchFilter, limit: Int): List<FragmentMatch>
}

/**
 * **Evaluador** de la recuperación (FT00018): recupera una vez por consulta del
 * golden set y agrega `precision@k`, `recall@k` y MRR (medias por consulta).
 *
 * Es un servicio **sin BD**: la recuperación la delega en el [Retriever]
 * inyectado, así que es testeable con un doble determinista y sin red.
 *
 * @param retriever recuperador que resuelve cada consulta.
 */
class RetrievalEvaluator(private val retriever: Retriever) {

    /**
     * Evalúa [dataset] con ventana [k] y devuelve el resultado agregado.
     *
     * Recupera **una vez** por consulta (`retriever.retrieve(text, filter, k)`),
     * mapea cada [FragmentMatch] a su [FragmentRef] y calcula las tres métricas con
     * [RetrievalMetrics]. Las medias usan [mean], que devuelve `0.0` para una lista
     * vacía (nunca produce `NaN`).
     *
     * @param dataset golden set validado a evaluar.
     * @param k ventana de evaluación (`>= 1`); por defecto, la del dataset.
     * @return métricas por consulta y sus medias.
     * @throws IllegalArgumentException si `k < 1` (fail-fast antes de recuperar).
     */
    fun evaluate(dataset: RetrievalGoldenSet, k: Int = dataset.k): RetrievalEvaluationResult {
        require(k >= 1) { "La ventana de evaluación debe ser >= 1, pero es $k." }
        val perQuery = dataset.queries.map { query -> evaluateQuery(query, k) }
        return RetrievalEvaluationResult(
            k = k,
            queryCount = perQuery.size,
            meanPrecision = perQuery.map { result -> result.precision }.mean(),
            meanRecall = perQuery.map { result -> result.recall }.mean(),
            meanMrr = perQuery.map { result -> result.reciprocalRank }.mean(),
            perQuery = perQuery,
        )
    }

    /** Recupera [query] y calcula sus tres métricas contra sus relevantes. */
    private fun evaluateQuery(query: RetrievalQuery, k: Int): RetrievalQueryResult {
        val retrieved = retriever.retrieve(query.text, query.filter, k).map { match -> match.toRef() }
        return RetrievalQueryResult(
            queryId = query.id,
            precision = RetrievalMetrics.precisionAtK(retrieved, query.relevant, k),
            recall = RetrievalMetrics.recallAtK(retrieved, query.relevant, k),
            reciprocalRank = RetrievalMetrics.reciprocalRank(retrieved, query.relevant),
            retrieved = retrieved,
        )
    }
}

/**
 * Resultado agregado de una evaluación.
 *
 * @property k ventana usada en la evaluación.
 * @property queryCount número de consultas evaluadas.
 * @property meanPrecision media de `precision@k` (0.0 si no hay consultas).
 * @property meanRecall media de `recall@k` (0.0 si no hay consultas).
 * @property meanMrr media del MRR (0.0 si no hay consultas).
 * @property perQuery resultado de cada consulta, en el orden del dataset.
 */
data class RetrievalEvaluationResult(
    val k: Int,
    val queryCount: Int,
    val meanPrecision: Double,
    val meanRecall: Double,
    val meanMrr: Double,
    val perQuery: List<RetrievalQueryResult>,
)

/**
 * Resultado de una consulta del golden set.
 *
 * @property queryId identificador de la consulta.
 * @property precision precisión@k de la consulta.
 * @property recall recall@k de la consulta.
 * @property reciprocalRank recíproco de la posición del primer acierto.
 * @property retrieved fragmentos recuperados, ya mapeados a [FragmentRef].
 */
data class RetrievalQueryResult(
    val queryId: String,
    val precision: Double,
    val recall: Double,
    val reciprocalRank: Double,
    val retrieved: List<FragmentRef>,
)

/**
 * Mapea una coincidencia de la recuperación a su referencia estable de evaluación.
 *
 * Es una extensión **pura**: solo cierra el encaje de tipos entre la recuperación
 * y las métricas.
 */
internal fun FragmentMatch.toRef(): FragmentRef = FragmentRef(fragment.publicationId, fragment.order)

/**
 * Media aritmética de [this]; devuelve `0.0` para una lista vacía para no producir
 * `NaN` cuando no hay consultas.
 */
internal fun List<Double>.mean(): Double = if (isEmpty()) 0.0 else sum() / size
