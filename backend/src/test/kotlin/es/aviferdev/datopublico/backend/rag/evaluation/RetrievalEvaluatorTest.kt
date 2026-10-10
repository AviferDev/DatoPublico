package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [RetrievalEvaluator] con un [Retriever] doble
 * determinista (escenario 4 del spec): recupera una vez por consulta con su
 * `filter` y su `k`, calcula las métricas y agrega las medias sin `NaN`.
 */
class RetrievalEvaluatorTest {

    @Test
    fun `evaluates each query with its filter and k and aggregates the means`() {
        val refA = FragmentRef("EVAL-1", 0)
        val refB = FragmentRef("EVAL-3", 1)
        val retriever = RecordingRetriever(
            mapOf("uno" to listOf(match(refA)), "dos" to listOf(match(refB))),
        )
        val dataset = dataset(
            queries = listOf(
                query("q-1", "uno", setOf(refA), SearchFilter(section = SeccionBoeDto.I)),
                query("q-2", "dos", setOf(refB), SearchFilter(section = SeccionBoeDto.III)),
            ),
            k = 3,
        )

        val result = RetrievalEvaluator(retriever).evaluate(dataset)

        assertEquals(3, result.k)
        assertEquals(2, result.queryCount)
        assertEquals(1.0, result.meanPrecision, TOLERANCE)
        assertEquals(1.0, result.meanRecall, TOLERANCE)
        assertEquals(1.0, result.meanMrr, TOLERANCE)
        assertEquals(listOf("q-1", "q-2"), result.perQuery.map { row -> row.queryId })
        assertEquals(
            listOf(
                RetrieverCall("uno", SearchFilter(section = SeccionBoeDto.I), 3),
                RetrieverCall("dos", SearchFilter(section = SeccionBoeDto.III), 3),
            ),
            retriever.calls,
        )
    }

    @Test
    fun `computes partial precision recall and reciprocal rank per query`() {
        val refA = FragmentRef("EVAL-1", 0)
        val other = FragmentRef("EVAL-2", 0)
        val retriever = RecordingRetriever(mapOf("uno" to listOf(match(other), match(refA))))
        val dataset = dataset(queries = listOf(query("q-1", "uno", setOf(refA))), k = 3)

        val result = RetrievalEvaluator(retriever).evaluate(dataset)
        val row = result.perQuery.single()

        assertEquals(0.5, row.precision, TOLERANCE)
        assertEquals(1.0, row.recall, TOLERANCE)
        assertEquals(0.5, row.reciprocalRank, TOLERANCE)
        assertEquals(listOf(other, refA), row.retrieved)
    }

    @Test
    fun `an empty query list yields zero means without NaN`() {
        val result = RetrievalEvaluator(RecordingRetriever(emptyMap())).evaluate(dataset(queries = emptyList()))

        assertEquals(0, result.queryCount)
        assertEquals(0.0, result.meanPrecision, TOLERANCE)
        assertEquals(0.0, result.meanRecall, TOLERANCE)
        assertEquals(0.0, result.meanMrr, TOLERANCE)
    }

    @Test
    fun `a non positive window fails fast before retrieving`() {
        val retriever = RecordingRetriever(emptyMap())
        val dataset = dataset(queries = listOf(query("q-1", "uno", emptySet())))

        assertFailsWith<IllegalArgumentException> { RetrievalEvaluator(retriever).evaluate(dataset, k = 0) }

        assertTrue(retriever.calls.isEmpty(), "no debe recuperar si la ventana es inválida")
    }

    @Test
    fun `an explicit k overrides the dataset window`() {
        val retriever = RecordingRetriever(mapOf("uno" to emptyList()))
        val dataset = dataset(queries = listOf(query("q-1", "uno", emptySet())), k = 5)

        RetrievalEvaluator(retriever).evaluate(dataset, k = 2)

        assertEquals(2, retriever.calls.single().limit)
    }

    /** Dataset mínimo de evaluación; el evaluador no usa el corpus. */
    private fun dataset(queries: List<RetrievalQuery>, k: Int = 3): RetrievalGoldenSet = RetrievalGoldenSet(
        version = "test",
        k = k,
        corpus = emptyList(),
        queries = queries,
    )

    /** Consulta interna de prueba. */
    private fun query(
        id: String,
        text: String,
        relevant: Set<FragmentRef>,
        filter: SearchFilter = SearchFilter(),
    ): RetrievalQuery = RetrievalQuery(id = id, text = text, filter = filter, relevant = relevant)

    /** Coincidencia de prueba para una referencia dada. */
    private fun match(ref: FragmentRef): FragmentMatch = FragmentMatch(
        fragment = FragmentEntity(
            publicationId = ref.publicationId,
            order = ref.order,
            reference = "Artículo",
            content = "Contenido ${ref.publicationId}-${ref.order}",
        ),
        distance = 0.1,
    )

    private companion object {
        /** Tolerancia de comparación de `Double`. */
        const val TOLERANCE = 1e-9
    }
}

/** Llamada registrada a [Retriever.retrieve]. */
private data class RetrieverCall(val query: String, val filter: SearchFilter, val limit: Int)

/** Doble determinista de [Retriever]: responde por texto de consulta y registra las llamadas. */
private class RecordingRetriever(
    private val responses: Map<String, List<FragmentMatch>>,
) : Retriever {
    /** Llamadas recibidas, en orden. */
    val calls = mutableListOf<RetrieverCall>()

    override fun retrieve(query: String, filter: SearchFilter, limit: Int): List<FragmentMatch> {
        calls.add(RetrieverCall(query, filter, limit))
        return responses[query].orEmpty()
    }
}
