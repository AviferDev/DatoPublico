package es.aviferdev.datopublico.backend.rag.embeddings

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.nio.file.Path

/**
 * [EmbeddingModel] real sobre una sesión de **ONNX Runtime en CPU**.
 *
 * Crea los tensores `input_ids` y `attention_mask` (`int64`, `[1, seq]`) y, si el
 * export lo declara, `token_type_ids` (ceros, `[1, seq]`; algunos exports
 * BERT-like de E5 lo exigen). Ejecuta el `.onnx` y lee su salida. Soporta los dos
 * contratos de export:
 * - `last_hidden_state` (`[1, seq, dims]`) → devuelve las `seq` filas para el
 *   *mean pooling* del proveedor.
 * - `sentence_embedding` (`[1, dims]`) → devuelve esa única fila, que el *pooling*
 *   deja tal cual.
 *
 * Si el modelo expone ambos, se prefiere `last_hidden_state` (el *pooling* con
 * máscara es el recomendado por E5). Valida que la última dimensión sea
 * [dimensions] y falla en claro si no.
 *
 * La sesión se libera con [close] (idempotente); el [OrtEnvironment] es un
 * *singleton* compartido y **no** se cierra aquí.
 */
internal class OnnxEmbeddingModel private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    override val dimensions: Int,
    private val outputName: String,
    private val inputNames: Set<String>,
) : EmbeddingModel {

    override fun forward(inputIds: LongArray, attentionMask: LongArray): Array<FloatArray> {
        val tensors = buildInputs(inputIds, attentionMask)
        val result = try {
            session.run(tensors, setOf(outputName))
        } finally {
            tensors.values.forEach { tensor -> tensor.close() }
        }
        return try {
            readRows(result)
        } finally {
            result.close()
        }
    }

    /** Tensores de entrada declarados por el modelo (con `token_type_ids` si aplica). */
    private fun buildInputs(inputIds: LongArray, attentionMask: LongArray): Map<String, OnnxTensor> =
        buildMap {
            put(INPUT_IDS, tensorOf(INPUT_IDS, inputIds))
            put(ATTENTION_MASK, tensorOf(ATTENTION_MASK, attentionMask))
            if (TOKEN_TYPE_IDS in inputNames) {
                put(TOKEN_TYPE_IDS, tensorOf(TOKEN_TYPE_IDS, LongArray(inputIds.size)))
            }
        }

    /** Crea un tensor `int64` `[1, size]` con los [values] dados. */
    private fun tensorOf(name: String, values: LongArray): OnnxTensor = try {
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(values),
            longArrayOf(1L, values.size.toLong()),
        )
    } catch (error: OrtException) {
        throw EmbeddingException("No se pudo crear el tensor '$name' de embeddings", error)
    }

    /** Lee y valida las filas de la salida [result] según el contrato del export. */
    private fun readRows(result: OrtSession.Result): Array<FloatArray> {
        val rows = try {
            val value = result.get(0).value
            if (outputName == SENTENCE_EMBEDDING) pooledRows(value) else hiddenRows(value)
        } catch (error: OrtException) {
            throw EmbeddingException("Salida ilegible del modelo de embeddings", error)
        }
        return validated(rows)
    }

    /** Filas de `last_hidden_state` `[1, seq, dims]` → `[seq, dims]`. */
    @Suppress("UNCHECKED_CAST")
    private fun hiddenRows(value: Any): Array<FloatArray> {
        val batch = value as? Array<Array<FloatArray>>
            ?: throw EmbeddingException(
                "Salida inesperada del modelo: se esperaba last_hidden_state [1, seq, dims]."
            )
        return batch.firstOrNull()
            ?: throw EmbeddingException("Salida vacía del modelo de embeddings.")
    }

    /** Filas de `sentence_embedding` `[1, dims]` → `[1, dims]`. */
    @Suppress("UNCHECKED_CAST")
    private fun pooledRows(value: Any): Array<FloatArray> = value as? Array<FloatArray>
        ?: throw EmbeddingException(
            "Salida inesperada del modelo: se esperaba sentence_embedding [1, dims]."
        )

    /** Comprueba que la última dimensión coincide con [dimensions]. */
    private fun validated(rows: Array<FloatArray>): Array<FloatArray> {
        val dims = rows.firstOrNull()?.size ?: 0
        if (dims != dimensions) {
            throw EmbeddingException(
                "El modelo de embeddings devolvió $dims dimensiones; se esperaban $dimensions."
            )
        }
        return rows
    }

    /** Cierra la sesión ONNX (idempotente). */
    override fun close() {
        session.close()
    }

    companion object {
        /**
         * Carga el modelo `.onnx` de [modelPath] y lo prepara para inferencia.
         *
         * @param dimensions dimensión esperada de la salida (default
         *   [E5EmbeddingProvider.DIMENSIONS]).
         * @throws EmbeddingException si el fichero no es cargable o no expone una
         *   salida soportada (fail-fast con mensaje claro).
         */
        fun from(modelPath: Path, dimensions: Int = E5EmbeddingProvider.DIMENSIONS): OnnxEmbeddingModel {
            val environment = OrtEnvironment.getEnvironment()
            val session = createSession(environment, modelPath)
            return try {
                build(environment, session, dimensions)
            } catch (error: Exception) {
                session.close()
                throw error
            }
        }

        private fun createSession(environment: OrtEnvironment, modelPath: Path): OrtSession = try {
            environment.createSession(modelPath.toString(), OrtSession.SessionOptions())
        } catch (error: OrtException) {
            throw EmbeddingException("No se pudo cargar el modelo ONNX en '$modelPath'", error)
        }

        private fun build(
            environment: OrtEnvironment,
            session: OrtSession,
            dimensions: Int,
        ): OnnxEmbeddingModel = OnnxEmbeddingModel(
            environment = environment,
            session = session,
            dimensions = dimensions,
            outputName = outputNameOf(session.outputNames),
            inputNames = session.inputNames,
        )

        /** Nombre de la salida a leer: prefiere `last_hidden_state` si están las dos. */
        private fun outputNameOf(names: Set<String>): String = when {
            LAST_HIDDEN_STATE in names -> LAST_HIDDEN_STATE
            SENTENCE_EMBEDDING in names -> SENTENCE_EMBEDDING
            else -> throw EmbeddingException(
                "El modelo ONNX no expone '$LAST_HIDDEN_STATE' ni '$SENTENCE_EMBEDDING'."
            )
        }

        /** Entrada de identificadores de token. */
        private const val INPUT_IDS = "input_ids"

        /** Entrada de máscara de atención. */
        private const val ATTENTION_MASK = "attention_mask"

        /** Entrada opcional de tipo de token (ceros); algunos exports la exigen. */
        private const val TOKEN_TYPE_IDS = "token_type_ids"

        /** Salida de estados ocultos por token. */
        private const val LAST_HIDDEN_STATE = "last_hidden_state"

        /** Salida ya agrupada por secuencia. */
        private const val SENTENCE_EMBEDDING = "sentence_embedding"
    }
}
