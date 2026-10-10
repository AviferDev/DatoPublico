package es.aviferdev.datopublico.backend.rag.embeddings

/**
 * Entrada tokenizada lista para el modelo: identificadores y máscara de atención.
 *
 * [inputIds] y [attentionMask] tienen la misma longitud (`seq`), ya recortada al
 * presupuesto del modelo. Es un modelo **interno** (ni serializable, ni `Entity`,
 * ni `Ui`), por eso va **sin sufijo**.
 *
 * @property inputIds identificadores de token (`input_ids`, `int64`).
 * @property attentionMask máscara de atención (`1` = token real, `0` = relleno).
 */
internal data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
)

/**
 * *Seam* de tokenización del proveedor de embeddings.
 *
 * Abstrae el tokenizador real (XLM-R de DJL, [E5Tokenizer]) para poder ejercitar
 * el pipeline completo —prefijos, *pooling*, normalización y validación de
 * dimensiones— con un doble determinista en el gate, sin artefactos ni red.
 */
internal interface TextTokenizer : AutoCloseable {
    /**
     * Tokeniza [text] según el modelo E5.
     *
     * @return identificadores y máscara de atención del texto, ya recortado a la
     *   longitud máxima configurada.
     */
    fun tokenize(text: String): TokenizedInput

    /**
     * Número de tokens del modelo que produce [text].
     *
     * Cierra el contador provisional de FT00014: FT00016 lo usará para validar el
     * presupuesto de `<= 512` tokens sin recalcular la estimación por caracteres.
     *
     * @return recuento de tokens (incluidos los especiales de la plantilla).
     */
    fun countTokens(text: String): Int
}
