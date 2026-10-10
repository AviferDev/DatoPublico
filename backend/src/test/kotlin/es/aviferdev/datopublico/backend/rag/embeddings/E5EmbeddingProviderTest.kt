package es.aviferdev.datopublico.backend.rag.embeddings

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests del proveedor E5 con **dobles deterministas** de [TextTokenizer] y
 * [EmbeddingModel]: cubren los escenarios 1–4, 6 y 7 del spec (dimensión,
 * ordenación por coseno, prefijos, normalización, cierre idempotente y recuento
 * de tokens) **sin** el modelo real ni red.
 */
class E5EmbeddingProviderTest {

    @Test
    fun `applies the mandatory query and passage prefixes`() {
        val tokenizer = RecordingTokenizer()
        val provider = E5EmbeddingProvider(tokenizer, OneHotModel())

        provider.embedQuery("convocatoria de becas")
        assertEquals("query: convocatoria de becas", tokenizer.lastText)

        provider.embedPassage("convocatoria de becas")
        assertEquals("passage: convocatoria de becas", tokenizer.lastText)
    }

    @Test
    fun `returns normalized vectors of the expected dimension`() {
        val provider = E5EmbeddingProvider(RecordingTokenizer(), OneHotModel())

        val vector = provider.embedPassage("texto de prueba")

        assertEquals(E5EmbeddingProvider.DIMENSIONS, provider.dimensions)
        assertEquals(E5EmbeddingProvider.DIMENSIONS, vector.size)
        assertEquals(1f, norm(vector), 1e-4f)
    }

    @Test
    fun `related texts have higher cosine similarity than unrelated ones`() {
        val provider = E5EmbeddingProvider(RecordingTokenizer(), OneHotModel())

        val related = provider.embedPassage("convocatoria de becas para estudiantes")
        val paraphrase = provider.embedPassage("becas para estudiantes convocatoria")
        val unrelated = provider.embedPassage("sanciones por infracciones de trafico")

        assertTrue(cosine(related, paraphrase) > cosine(related, unrelated))
    }

    @Test
    fun `rejects an output with wrong dimensions with a clear error`() {
        val provider = E5EmbeddingProvider(RecordingTokenizer(), OneHotModel(dims = 7))

        assertFailsWith<EmbeddingException> { provider.embedPassage("texto") }
    }

    @Test
    fun `close is idempotent and releases model and tokenizer`() {
        val tokenizer = RecordingTokenizer()
        val model = OneHotModel()
        val provider = E5EmbeddingProvider(tokenizer, model)

        provider.close()
        provider.close()

        assertTrue(tokenizer.closed)
        assertTrue(model.closed)
    }

    @Test
    fun `embedPassages vectorizes every text in order`() {
        val provider = E5EmbeddingProvider(RecordingTokenizer(), OneHotModel())

        val vectors = provider.embedPassages(listOf("uno", "dos", "tres"))

        assertEquals(3, vectors.size)
        assertTrue(vectors.all { it.size == E5EmbeddingProvider.DIMENSIONS })
    }

    @Test
    fun `the tokenizer counts the text tokens`() {
        val tokenizer = RecordingTokenizer()

        assertEquals(3, tokenizer.countTokens("uno dos tres"))
    }

    /** Norma L2 de [vector]. */
    private fun norm(vector: FloatArray): Float =
        kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()

    /** Similitud coseno de [a] y [b]. */
    private fun cosine(a: FloatArray, b: FloatArray): Float = VectorMath.cosineSimilarity(a, b)

    /**
     * Tokenizador determinista de prueba: parte por palabras y mapea cada una a
     * una coordenada estable; registra el último texto recibido para verificar los
     * prefijos.
     */
    private class RecordingTokenizer : TextTokenizer {
        var lastText: String? = null
        var closed: Boolean = false

        override fun tokenize(text: String): TokenizedInput {
            lastText = text
            val ids = text.lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .map { word -> ((word.hashCode().rem(E5EmbeddingProvider.DIMENSIONS) + E5EmbeddingProvider.DIMENSIONS) % E5EmbeddingProvider.DIMENSIONS).toLong() }
                .toLongArray()
            return TokenizedInput(ids, LongArray(ids.size) { 1L })
        }

        override fun countTokens(text: String): Int =
            text.lowercase().split(Regex("\\s+")).count { it.isNotBlank() }

        override fun close() {
            closed = true
        }
    }

    /**
     * Modelo de prueba: convierte cada identificador en un vector *one-hot* de
     * [dims] componentes, de modo que textos que comparten palabras quedan más
     * cerca (aritmética del pipeline, no semántica del modelo real).
     */
    private class OneHotModel(private val dims: Int = E5EmbeddingProvider.DIMENSIONS) : EmbeddingModel {
        var closed: Boolean = false

        override val dimensions: Int get() = dims

        override fun forward(inputIds: LongArray, attentionMask: LongArray): Array<FloatArray> =
            Array(inputIds.size) { index ->
                FloatArray(dims).also { row ->
                    row[((inputIds[index].toInt() % dims) + dims) % dims] = 1f
                }
            }

        override fun close() {
            closed = true
        }
    }
}
