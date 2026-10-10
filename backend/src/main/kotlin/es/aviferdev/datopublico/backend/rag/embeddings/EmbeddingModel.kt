package es.aviferdev.datopublico.backend.rag.embeddings

/**
 * *Seam* de inferencia del proveedor de embeddings.
 *
 * Abstrae la sesión ONNX real ([OnnxEmbeddingModel]) para poder probar el
 * pipeline con un doble determinista en el gate, sin el `.onnx` (~120 MB).
 */
internal interface EmbeddingModel : AutoCloseable {
    /** Dimensión de la última capa del modelo (384 para E5 `-small`). */
    val dimensions: Int

    /**
     * Ejecuta el *forward* del modelo sobre una secuencia.
     *
     * @param inputIds identificadores de token de la secuencia.
     * @param attentionMask máscara de atención alineada con [inputIds].
     * @return filas a promediar: el *hidden state* `[seq, dims]` o, si el export ya
     *   emite el vector agrupado (`sentence_embedding`), una única fila `[1, dims]`.
     * @throws EmbeddingException si la salida no tiene la dimensión esperada.
     */
    fun forward(inputIds: LongArray, attentionMask: LongArray): Array<FloatArray>
}
