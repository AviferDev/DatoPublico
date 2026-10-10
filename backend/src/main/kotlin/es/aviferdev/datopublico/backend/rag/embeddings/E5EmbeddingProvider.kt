package es.aviferdev.datopublico.backend.rag.embeddings

import java.nio.file.Files
import java.nio.file.Path

/**
 * [EmbeddingProvider] por defecto: `intfloat/multilingual-e5-small` int8 vía
 * **ONNX Runtime en CPU**, con el tokenizador XLM-R de DJL.
 *
 * Compone el *seam* de tokenización ([TextTokenizer]) y el de inferencia
 * ([EmbeddingModel]): tokeniza la entrada con el **prefijo obligatorio**,
 * ejecuta el modelo, hace *mean pooling* con la máscara de atención, **normaliza**
 * el vector (L2) y valida que tenga [dimensions] componentes.
 *
 * El modelo y el tokenizador **no** se descargan aquí: se cargan desde rutas
 * locales con fail-fast ([from]). Es una **librería**: no se cablea en el arranque
 * ni en el job; la consumirán FT00016/FT00017.
 *
 * @param dimensions dimensión de los vectores (384 para E5 `-small`).
 */
class E5EmbeddingProvider internal constructor(
    private val tokenizer: TextTokenizer,
    private val model: EmbeddingModel,
    override val dimensions: Int = DIMENSIONS,
) : EmbeddingProvider {

    private var closed = false

    override fun embedQuery(text: String): FloatArray = embed(QUERY_PREFIX + text)

    override fun embedPassage(text: String): FloatArray = embed(PASSAGE_PREFIX + text)

    /** Pipeline completo de un texto ya prefijado: tokenizar → forward → pooling → normalizar. */
    private fun embed(input: String): FloatArray {
        val tokenized = tokenizer.tokenize(input)
        val hidden = model.forward(tokenized.inputIds, tokenized.attentionMask)
        val pooled = VectorMath.meanPool(hidden, tokenized.attentionMask)
        return validated(VectorMath.l2Normalize(pooled))
    }

    /** Rechaza con error claro una salida cuya dimensión no sea [dimensions]. */
    private fun validated(vector: FloatArray): FloatArray {
        if (vector.size != dimensions) {
            throw EmbeddingException(
                "El proveedor de embeddings produjo ${vector.size} dimensiones; " +
                    "se esperaban $dimensions."
            )
        }
        return vector
    }

    /** Cierra el modelo y el tokenizador; es **idempotente**. */
    override fun close() {
        if (!closed) {
            closed = true
            closeQuietly(model)
            closeQuietly(tokenizer)
        }
    }

    companion object {
        /** Prefijo obligatorio de las consultas del modelo E5. */
        const val QUERY_PREFIX = "query: "

        /** Prefijo obligatorio de los fragmentos del modelo E5. */
        const val PASSAGE_PREFIX = "passage: "

        /** Dimensión de la salida de `multilingual-e5-small`. */
        const val DIMENSIONS = 384

        /** Contexto máximo del modelo E5 (tokens). */
        const val MAX_TOKENS = 512

        /**
         * Carga el proveedor real desde las rutas locales del `.onnx` y del
         * `tokenizer.json`.
         *
         * @throws EmbeddingException si falta algún artefacto o no es cargable
         *   (fail-fast con mensaje claro; sin descarga silenciosa).
         */
        fun from(modelPath: Path, tokenizerPath: Path, maxTokens: Int = MAX_TOKENS): E5EmbeddingProvider {
            requireExisting(modelPath, EmbeddingConfig.MODEL_PATH_KEY)
            requireExisting(tokenizerPath, EmbeddingConfig.TOKENIZER_PATH_KEY)
            val tokenizer = E5Tokenizer.from(tokenizerPath, maxTokens)
            val model = OnnxEmbeddingModel.from(modelPath, DIMENSIONS)
            return E5EmbeddingProvider(tokenizer, model, DIMENSIONS)
        }

        /** Comprueba que el artefacto [path] existe antes de intentar cargarlo. */
        private fun requireExisting(path: Path, key: String) {
            if (!Files.isRegularFile(path)) {
                throw EmbeddingException(
                    "El artefacto de $key no existe o no es un fichero: '$path'. " +
                        "Descárgalo con backend/tools/download-embedding-model.sh."
                )
            }
        }

        /** Cierra [resource] sin propagar errores (el cierre no debe enmascarar el resultado). */
        private fun closeQuietly(resource: AutoCloseable) {
            try {
                resource.close()
            } catch (ignored: Exception) {
                // Un fallo al liberar un recurso nativo no debe romper el cierre.
            }
        }
    }
}
