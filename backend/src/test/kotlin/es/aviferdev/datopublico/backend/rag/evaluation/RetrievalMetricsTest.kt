package es.aviferdev.datopublico.backend.rag.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests de gate (puros, sin red ni BD) de [RetrievalMetrics] (escenario 1 del
 * spec): precisión, recall y MRR con sus casos límite (`k` mayor que los
 * recuperados, sin relevantes, sin aciertos y acierto en primera posición).
 */
class RetrievalMetricsTest {

    private val refA = FragmentRef("EVAL-1", 0)
    private val refB = FragmentRef("EVAL-1", 1)
    private val refC = FragmentRef("EVAL-2", 0)
    private val refD = FragmentRef("EVAL-2", 1)

    @Test
    fun `precision at k is the hit ratio inside the window`() {
        val retrieved = listOf(refA, refB, refC)
        val relevant = setOf(refA, refB)

        assertEquals(1.0, RetrievalMetrics.precisionAtK(retrieved, relevant, 2), TOLERANCE)
        assertEquals(2.0 / 3.0, RetrievalMetrics.precisionAtK(retrieved, relevant, 3), TOLERANCE)
    }

    @Test
    fun `precision at k caps the window at the number of retrieved fragments`() {
        val retrieved = listOf(refA, refB)

        assertEquals(0.5, RetrievalMetrics.precisionAtK(retrieved, setOf(refA), 5), TOLERANCE)
    }

    @Test
    fun `precision at k is zero without retrieved or without hits`() {
        assertEquals(0.0, RetrievalMetrics.precisionAtK(emptyList(), setOf(refA), 3), TOLERANCE)
        assertEquals(0.0, RetrievalMetrics.precisionAtK(listOf(refC), setOf(refA), 3), TOLERANCE)
    }

    @Test
    fun `recall at k is the fraction of relevant fragments covered`() {
        val retrieved = listOf(refA, refB, refC)
        val relevant = setOf(refA, refB, refC, refD)

        assertEquals(0.5, RetrievalMetrics.recallAtK(retrieved, relevant, 2), TOLERANCE)
        assertEquals(0.75, RetrievalMetrics.recallAtK(retrieved, relevant, 10), TOLERANCE)
    }

    @Test
    fun `recall at k is zero without relevant or without hits`() {
        assertEquals(0.0, RetrievalMetrics.recallAtK(listOf(refA), emptySet(), 3), TOLERANCE)
        assertEquals(0.0, RetrievalMetrics.recallAtK(listOf(refC), setOf(refA), 3), TOLERANCE)
    }

    @Test
    fun `reciprocal rank rewards the first hit position`() {
        val relevant = setOf(refB)

        assertEquals(1.0, RetrievalMetrics.reciprocalRank(listOf(refB, refA), relevant), TOLERANCE)
        assertEquals(0.5, RetrievalMetrics.reciprocalRank(listOf(refA, refB), relevant), TOLERANCE)
        assertEquals(1.0 / 3.0, RetrievalMetrics.reciprocalRank(listOf(refA, refC, refB), relevant), TOLERANCE)
        assertEquals(0.0, RetrievalMetrics.reciprocalRank(listOf(refA, refC), relevant), TOLERANCE)
    }

    @Test
    fun `a non positive window fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalMetrics.precisionAtK(listOf(refA), setOf(refA), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            RetrievalMetrics.recallAtK(listOf(refA), setOf(refA), -1)
        }
    }

    private companion object {
        /** Tolerancia de comparación de `Double`. */
        const val TOLERANCE = 1e-9
    }
}
