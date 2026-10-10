package es.aviferdev.datopublico.backend.rag.embeddings

import kotlin.math.sqrt

/**
 * Aritmética vectorial **pura y determinista** del pipeline de embeddings.
 *
 * Agrupa las tres operaciones que el proveedor E5 aplica a la salida del modelo
 * ONNX: *mean pooling* con máscara de atención, normalización L2 y similitud
 * coseno. No toca red, ficheros, ONNX ni estado compartido, así que se prueba en
 * el gate sin artefactos y la reutilizará FT00017 (recuperación).
 */
internal object VectorMath {

    /**
     * *Mean pooling* de [rows] (una fila por token de `last_hidden_state`)
     * ponderado por [attentionMask].
     *
     * Solo promedia las filas cuyo valor de máscara es distinto de `0`; los
     * *padding* se ignoran. Si el modelo ya emite el vector agrupado
     * (`sentence_embedding`, una única fila), el resultado es esa misma fila: la
     * máscara del primer token (CLS) vale `1`. Un índice de máscara ausente se
     * trata como incluido.
     *
     * @param rows filas del *hidden state* `[seq, dims]` (o una sola fila si el
     *   export ya viene agrupado).
     * @param attentionMask máscara de atención `[seq]` (`1` = token real).
     * @return vector de longitud `dims`; ceros si no hay filas o ninguna incluida.
     */
    fun meanPool(rows: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val pooled = FloatArray(rows.firstOrNull()?.size ?: 0)
        var included = 0
        rows.forEachIndexed { index, row ->
            if (attentionMask.getOrElse(index) { 1L } != 0L) {
                included += 1
                row.forEachIndexed { position, value -> pooled[position] += value }
            }
        }
        return if (included == 0) pooled else FloatArray(pooled.size) { pooled[it] / included }
    }

    /**
     * Normaliza [vector] a norma L2 ≈1.
     *
     * Un vector de norma nula (o menor que [EPS]) se devuelve **sin dividir**, para
     * no producir `NaN`; es la guarda documentada del contrato E5 (vectores
     * normalizados).
     *
     * @return vector de la misma longitud con norma ≈1, o el original si es nulo.
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm <= EPS) vector else FloatArray(vector.size) { vector[it] / norm }
    }

    /**
     * Similitud coseno de [a] y [b], en `[-1, 1]`.
     *
     * Si alguno de los dos vectores tiene norma nula devuelve `0.0` (sin `NaN`).
     * Los vectores deben tener la misma longitud; se recorren hasta el mínimo de
     * ambas longitudes para no fallar por un desajuste.
     *
     * @return coseno del ángulo entre ambos vectores.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val length = minOf(a.size, b.size)
        val dot = (0 until length).sumOf { (a[it] * b[it]).toDouble() }
        val normA = sqrt(a.sumOf { (it * it).toDouble() })
        val normB = sqrt(b.sumOf { (it * it).toDouble() })
        val denominator = normA * normB
        return if (denominator <= EPS) 0.0f else (dot / denominator).toFloat()
    }

    /** Umbral por debajo del cual una norma se considera nula. */
    private const val EPS = 1e-12
}
