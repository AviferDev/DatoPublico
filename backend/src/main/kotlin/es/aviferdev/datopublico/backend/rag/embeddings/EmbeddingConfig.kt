package es.aviferdev.datopublico.backend.rag.embeddings

import java.nio.file.Files
import java.nio.file.Path

/**
 * Error de frontera del subsistema de embeddings locales.
 *
 * Se lanza cuando falta o no existe el modelo/tokenizador, cuando el `.onnx` no
 * es cargable o cuando su salida no tiene la dimensión esperada. Es **tipada**
 * para que quien la consuma (job, backfill) distinga el fallo de configuración
 * del de inferencia.
 */
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Configuración del proveedor de embeddings resuelta desde el entorno.
 *
 * **No** se cablea en el arranque: la construye explícitamente quien va a usar el
 * proveedor (FT00016/FT00017). El modelo y el tokenizador **no** se versionan ni
 * se descargan aquí: se aprovisionan fuera del gate
 * (`backend/tools/download-embedding-model.sh`) y se referencian por **ruta
 * absoluta** con fail-fast si no existen.
 *
 * @property modelPath ruta del `.onnx` int8 (`EMBEDDING_MODEL_PATH`).
 * @property tokenizerPath ruta del `tokenizer.json` (`EMBEDDING_TOKENIZER_PATH`).
 * @property maxTokens longitud máxima de secuencia (`EMBEDDING_MAX_TOKENS`).
 */
internal data class EmbeddingConfig(
    val modelPath: Path,
    val tokenizerPath: Path,
    val maxTokens: Int,
) {
    companion object {
        /** Longitud máxima de secuencia del modelo E5 (contexto de 512 tokens). */
        const val DEFAULT_MAX_TOKENS: Int = 512

        /**
         * Resuelve la configuración desde [env] (`System.getenv()` por defecto).
         *
         * @throws EmbeddingException si falta una ruta o el fichero no existe, o si
         *   [MAX_TOKENS_KEY] no es un entero positivo (fail-fast con mensaje claro,
         *   sin *fallback* a una ruta por defecto ni descarga silenciosa).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): EmbeddingConfig = EmbeddingConfig(
            modelPath = requiredFile(env, MODEL_PATH_KEY),
            tokenizerPath = requiredFile(env, TOKENIZER_PATH_KEY),
            maxTokens = parseMaxTokens(env),
        )

        /** Ruta obligatoria de [key] que además debe existir en disco. */
        private fun requiredFile(env: Map<String, String>, key: String): Path {
            val raw = env[key]?.trim()?.takeIf { it.isNotBlank() }
                ?: throw EmbeddingException(
                    "Falta $key: define la ruta del artefacto de embeddings " +
                        "(ver backend/tools/download-embedding-model.sh)."
                )
            val path = Path.of(raw)
            if (!Files.isRegularFile(path)) {
                throw EmbeddingException(
                    "El artefacto de $key no existe o no es un fichero: '$raw'. " +
                        "Descárgalo con backend/tools/download-embedding-model.sh."
                )
            }
            return path
        }

        /** `EMBEDDING_MAX_TOKENS`: entero positivo (default [DEFAULT_MAX_TOKENS]). */
        private fun parseMaxTokens(env: Map<String, String>): Int {
            val raw = env[MAX_TOKENS_KEY]?.trim()?.takeIf { it.isNotBlank() }
            val value = raw?.toIntOrNull()
            if (raw != null && (value == null || value <= 0)) {
                throw EmbeddingException(
                    "Valor inválido de $MAX_TOKENS_KEY: '$raw'. Usa un entero mayor que 0."
                )
            }
            return value ?: DEFAULT_MAX_TOKENS
        }

        /** Ruta del modelo ONNX int8. */
        const val MODEL_PATH_KEY = "EMBEDDING_MODEL_PATH"

        /** Ruta del `tokenizer.json` (XLM-R). */
        const val TOKENIZER_PATH_KEY = "EMBEDDING_TOKENIZER_PATH"

        /** Longitud máxima de secuencia configurable. */
        const val MAX_TOKENS_KEY = "EMBEDDING_MAX_TOKENS"
    }
}
