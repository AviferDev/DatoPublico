package es.aviferdev.datopublico.backend.rag.embeddings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de [EmbeddingConfig]: resolución de `EMBEDDING_*` desde el entorno con
 * **fail-fast** (escenario 5 del spec), sin descargar ni versionar artefactos.
 */
class EmbeddingConfigTest {

    @Test
    fun `resolves the paths and the default max tokens`() {
        val model = tempFile()
        val tokenizer = tempFile()

        val config = EmbeddingConfig.fromEnv(
            mapOf(
                EmbeddingConfig.MODEL_PATH_KEY to model.toString(),
                EmbeddingConfig.TOKENIZER_PATH_KEY to tokenizer.toString(),
            )
        )

        assertEquals(model, config.modelPath)
        assertEquals(tokenizer, config.tokenizerPath)
        assertEquals(EmbeddingConfig.DEFAULT_MAX_TOKENS, config.maxTokens)
    }

    @Test
    fun `accepts a configured max tokens value`() {
        val config = EmbeddingConfig.fromEnv(
            mapOf(
                EmbeddingConfig.MODEL_PATH_KEY to tempFile().toString(),
                EmbeddingConfig.TOKENIZER_PATH_KEY to tempFile().toString(),
                EmbeddingConfig.MAX_TOKENS_KEY to "256",
            )
        )

        assertEquals(256, config.maxTokens)
    }

    @Test
    fun `fails when the model path is missing`() {
        val error = assertFailsWith<EmbeddingException> {
            EmbeddingConfig.fromEnv(mapOf(EmbeddingConfig.TOKENIZER_PATH_KEY to tempFile().toString()))
        }

        assertTrue(error.message.orEmpty().contains(EmbeddingConfig.MODEL_PATH_KEY))
    }

    @Test
    fun `fails when the artifact does not exist`() {
        val error = assertFailsWith<EmbeddingException> {
            EmbeddingConfig.fromEnv(
                mapOf(
                    EmbeddingConfig.MODEL_PATH_KEY to "/ruta/inexistente/model.onnx",
                    EmbeddingConfig.TOKENIZER_PATH_KEY to tempFile().toString(),
                )
            )
        }

        assertTrue(error.message.orEmpty().contains(EmbeddingConfig.MODEL_PATH_KEY))
    }

    @Test
    fun `fails when max tokens is not a positive integer`() {
        val error = assertFailsWith<EmbeddingException> {
            EmbeddingConfig.fromEnv(
                mapOf(
                    EmbeddingConfig.MODEL_PATH_KEY to tempFile().toString(),
                    EmbeddingConfig.TOKENIZER_PATH_KEY to tempFile().toString(),
                    EmbeddingConfig.MAX_TOKENS_KEY to "cero",
                )
            )
        }

        assertTrue(error.message.orEmpty().contains(EmbeddingConfig.MAX_TOKENS_KEY))
    }

    /** Crea un fichero temporal no vacío que se borra al terminar la JVM. */
    private fun tempFile(): Path =
        Files.createTempFile("embedding-config", ".bin").also { it.toFile().deleteOnExit() }
}
