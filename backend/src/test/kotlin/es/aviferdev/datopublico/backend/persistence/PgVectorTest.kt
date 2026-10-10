package es.aviferdev.datopublico.backend.persistence

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests **puros** (sin red ni base de datos) de [PgVector]: serialización al
 * literal de pgvector, lectura, nulos y validación de dimensión (escenario 4 del
 * spec). Es la conversión que usan `FragmentRepository.saveEmbedding`/`findNearest`.
 */
class PgVectorTest {

    @Test
    fun `toLiteral writes the pgvector format with dot decimals`() {
        val literal = PgVector.toLiteral(vector(0.5f, -0.25f, 1f))

        assertTrue(literal.startsWith("[0.5,-0.25,1.0,"), "literal inesperado: $literal")
        assertTrue(literal.endsWith("]"), "el literal debe cerrar con corchete: $literal")
        assertFalse(literal.contains("0,5"), "no debe usar coma decimal: $literal")
    }

    @Test
    fun `parse round-trips the literal written by toLiteral`() {
        val original = vector(0.5f, -0.25f, 1f, 0.125f, -2f)

        val parsed = PgVector.parse(PgVector.toLiteral(original))

        assertFloatArrayEquals(original, parsed)
    }

    @Test
    fun `parse returns null for null and blank literals`() {
        assertNull(PgVector.parse(null))
        assertNull(PgVector.parse(""))
        assertNull(PgVector.parse("   "))
    }

    @Test
    fun `toLiteral rejects a vector of the wrong dimension`() {
        assertFailsWith<IllegalArgumentException> { PgVector.toLiteral(floatArrayOf(1f, 2f, 3f)) }
    }

    @Test
    fun `parse rejects a literal of the wrong dimension`() {
        assertFailsWith<IllegalArgumentException> { PgVector.parse("[1.0,2.0,3.0]") }
    }

    @Test
    fun `parse rejects a literal with a non numeric component`() {
        val malformed = "[" + List(PgVector.DIMENSIONS) { index -> if (index == 0) "no" else "0.0" }
            .joinToString(",") + "]"

        assertFailsWith<IllegalArgumentException> { PgVector.parse(malformed) }
    }

    @Test
    fun `toLiteral is independent from the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))

            val literal = PgVector.toLiteral(vector(0.5f))

            assertTrue(literal.startsWith("[0.5,"), "en es-ES debe seguir usando punto: $literal")
            assertFalse(literal.contains("0,5"), "en es-ES no debe usar coma decimal: $literal")
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** Vector de [PgVector.DIMENSIONS] con [values] al principio y ceros detrás. */
    private fun vector(vararg values: Float): FloatArray =
        FloatArray(PgVector.DIMENSIONS).also { array ->
            values.forEachIndexed { index, value -> array[index] = value }
        }

    /** Compara dos [FloatArray] componente a componente con tolerancia. */
    private fun assertFloatArrayEquals(expected: FloatArray, actual: FloatArray?) {
        val nonNull = assertNotNull(actual)
        assertEquals(expected.size, nonNull.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], nonNull[index], 1e-6f)
        }
    }
}
