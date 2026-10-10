package es.aviferdev.datopublico.backend.rag.embeddings

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests **puros** (sin red ni artefactos) de [VectorMath]: *mean pooling* con
 * máscara, normalización L2 y similitud coseno. Es la base aritmética del
 * pipeline E5 y cubre los escenarios 2 y 4 del spec.
 */
class VectorMathTest {

    @Test
    fun `meanPool averages only the rows included in the mask`() {
        val rows = arrayOf(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), floatArrayOf(9f, 9f))
        val mask = longArrayOf(1, 1, 0)

        val pooled = VectorMath.meanPool(rows, mask)

        assertFloatArrayEquals(floatArrayOf(2f, 3f), pooled)
    }

    @Test
    fun `meanPool returns the grouped row as is`() {
        val grouped = floatArrayOf(0.1f, 0.2f, 0.3f)

        val pooled = VectorMath.meanPool(arrayOf(grouped), longArrayOf(1, 1, 1))

        assertFloatArrayEquals(grouped, pooled)
    }

    @Test
    fun `l2Normalize leaves the vector with unit norm`() {
        val normalized = VectorMath.l2Normalize(floatArrayOf(3f, 4f))

        assertFloatArrayEquals(floatArrayOf(0.6f, 0.8f), normalized)
        assertTrue(abs(norm(normalized) - 1f) < 1e-4f)
    }

    @Test
    fun `l2Normalize returns a zero-norm vector undivided`() {
        val zero = floatArrayOf(0f, 0f, 0f)

        val normalized = VectorMath.l2Normalize(zero)

        assertFloatArrayEquals(zero, normalized)
    }

    @Test
    fun `cosineSimilarity ranks related texts above unrelated ones`() {
        val a = VectorMath.l2Normalize(floatArrayOf(1f, 0f, 0f))
        val related = VectorMath.l2Normalize(floatArrayOf(1f, 0.1f, 0f))
        val orthogonal = VectorMath.l2Normalize(floatArrayOf(0f, 1f, 0f))

        assertTrue(VectorMath.cosineSimilarity(a, related) > VectorMath.cosineSimilarity(a, orthogonal))
        assertEquals(1f, VectorMath.cosineSimilarity(a, a), 1e-4f)
        assertEquals(0f, VectorMath.cosineSimilarity(a, orthogonal), 1e-4f)
    }

    @Test
    fun `cosineSimilarity of a zero vector is zero`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val other = floatArrayOf(1f, 2f, 3f)

        assertEquals(0f, VectorMath.cosineSimilarity(zero, other), 1e-4f)
    }

    /** Norma L2 de [vector]. */
    private fun norm(vector: FloatArray): Float =
        kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()

    /** Compara dos [FloatArray] componente a componente con tolerancia. */
    private fun assertFloatArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], actual[index], 1e-4f)
        }
    }
}
