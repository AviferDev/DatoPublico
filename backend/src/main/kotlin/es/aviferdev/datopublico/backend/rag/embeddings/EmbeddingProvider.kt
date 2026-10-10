package es.aviferdev.datopublico.backend.rag.embeddings

/**
 * Proveedor intercambiable de embeddings de texto.
 *
 * Es la frontera que consumirán FT00016 (indexado) y FT00017 (recuperación): el
 * resto del código no conoce ONNX ni el modelo concreto. La implementación por
 * defecto es [E5EmbeddingProvider] (`intfloat/multilingual-e5-small` int8 vía
 * ONNX Runtime en CPU), pero cambiar a `-base` (768 dims) solo exige otra
 * implementación (y rediseñar la columna vectorial).
 *
 * Contrato:
 * - [dimensions] es fijo por proveedor (384 para E5 `-small`).
 * - Los vectores están **normalizados** (norma L2 ≈1).
 * - [embedQuery] y [embedPassage] aplican los prefijos obligatorios del modelo
 *   (`query: `/`passage: `) para que la calidad no caiga.
 * - La inferencia es **local y síncrona** (CPU); no bloquea por red.
 * - El proveedor es `AutoCloseable`: libera la sesión ONNX y el tokenizador.
 */
interface EmbeddingProvider : AutoCloseable {
    /** Dimensión de los vectores que devuelve el proveedor. */
    val dimensions: Int

    /**
     * Vectoriza [text] como **consulta** (prefijo `query: `).
     *
     * @return vector normalizado de longitud [dimensions].
     * @throws EmbeddingException si la salida del modelo no tiene [dimensions].
     */
    fun embedQuery(text: String): FloatArray

    /**
     * Vectoriza [text] como **fragmento** (prefijo `passage: `).
     *
     * @return vector normalizado de longitud [dimensions].
     * @throws EmbeddingException si la salida del modelo no tiene [dimensions].
     */
    fun embedPassage(text: String): FloatArray

    /**
     * Vectoriza una lista de fragmentos en orden.
     *
     * Implementación por defecto secuencial (`map`); el *batching* real de
     * inferencia es una optimización diferida (FT00016).
     *
     * @return un vector por texto, en el mismo orden.
     */
    fun embedPassages(texts: List<String>): List<FloatArray> = texts.map { embedPassage(it) }
}
