package es.aviferdev.datopublico.backend.rag.embeddings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el **modelo E5 real** (escenarios 1, 2, 4 y 7 del
 * spec). Es la evidencia de los dos criterios de `verification` de FT00015.
 *
 * Se salta por defecto (sin `EMBEDDING_LIVE_TEST=1`), de modo que el gate y la CI
 * **no** descargan ni cargan artefactos. Para ejecutarla:
 *
 * ```
 * backend/tools/download-embedding-model.sh
 * EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*E5EmbeddingLiveTest'
 * ```
 *
 * Las rutas se toman de `EMBEDDING_MODEL_PATH`/`EMBEDDING_TOKENIZER_PATH` o, si no
 * están definidas, del directorio por defecto `backend/models/multilingual-e5-small`.
 */
class E5EmbeddingLiveTest {

    @Test
    fun `the real model returns 384 dimensions and ranks related texts`() {
        val artifacts = resolveArtifacts()
        if (System.getenv("EMBEDDING_LIVE_TEST") != "1" || artifacts == null) {
            println(
                "E5EmbeddingLiveTest omitido: descarga el modelo con " +
                    "backend/tools/download-embedding-model.sh y exporta EMBEDDING_LIVE_TEST=1."
            )
        } else {
            val (modelPath, tokenizerPath) = artifacts
            val provider = E5EmbeddingProvider.from(modelPath, tokenizerPath)
            try {
                val related = provider.embedPassage(GRANT_CALL)
                val paraphrase = provider.embedPassage(GRANT_CALL_PARAPHRASE)
                val unrelated = provider.embedPassage(UNRELATED_REGULATION)

                assertEquals(E5EmbeddingProvider.DIMENSIONS, provider.dimensions)
                assertEquals(E5EmbeddingProvider.DIMENSIONS, related.size)
                assertTrue(abs(norm(related) - 1f) < 1e-4f)
                assertTrue(
                    cosine(related, paraphrase) > cosine(related, unrelated),
                    "cos(relacionados) debe superar a cos(no relacionados)",
                )
                assertTrue(
                    cosine(paraphrase, unrelated) < cosine(related, paraphrase),
                    "la paráfrasis debe quedar más cerca que la norma no relacionada",
                )
                println(
                    "E5EmbeddingLiveTest: dims=${related.size} " +
                        "norm=${norm(related)} " +
                        "cos(rel,par)=${cosine(related, paraphrase)} " +
                        "cos(rel,unrel)=${cosine(related, unrelated)} " +
                        "cos(par,unrel)=${cosine(paraphrase, unrelated)}"
                )
            } finally {
                provider.close()
            }
            assertTokenCount(tokenizerPath)
        }
    }

    /** Comprueba el recuento real de tokens de un `passage` (escenario 7). */
    private fun assertTokenCount(tokenizerPath: Path) {
        val tokenizer = E5Tokenizer.from(tokenizerPath, E5EmbeddingProvider.MAX_TOKENS)
        try {
            val tokens = tokenizer.countTokens(E5EmbeddingProvider.PASSAGE_PREFIX + GRANT_CALL)
            println("E5EmbeddingLiveTest: countTokens(passage)=$tokens")
            assertTrue(tokens in 1..E5EmbeddingProvider.MAX_TOKENS, "recuento real fuera de rango: $tokens")
        } finally {
            tokenizer.close()
        }
    }

    /** Resuelve las rutas del modelo/tokenizador desde el entorno o el directorio por defecto. */
    private fun resolveArtifacts(): Pair<Path, Path>? {
        val modelEnv = System.getenv(EmbeddingConfig.MODEL_PATH_KEY)
        val tokenizerEnv = System.getenv(EmbeddingConfig.TOKENIZER_PATH_KEY)
        val fromEnv = modelEnv?.let { model ->
            tokenizerEnv?.let { tokenizer -> Path.of(model) to Path.of(tokenizer) }
        }
        val defaultDir = DEFAULT_DIRS.firstOrNull { dir ->
            Files.isRegularFile(dir.resolve(MODEL_FILE)) && Files.isRegularFile(dir.resolve(TOKENIZER_FILE))
        }
        return fromEnv ?: defaultDir?.let { it.resolve(MODEL_FILE) to it.resolve(TOKENIZER_FILE) }
    }

    /** Norma L2 de [vector]. */
    private fun norm(vector: FloatArray): Float =
        kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()

    /** Similitud coseno de [a] y [b]. */
    private fun cosine(a: FloatArray, b: FloatArray): Float = VectorMath.cosineSimilarity(a, b)

    private companion object {
        const val MODEL_FILE = "model.onnx"
        const val TOKENIZER_FILE = "tokenizer.json"

        /** Rutas relativas posibles según el directorio de trabajo del test. */
        val DEFAULT_DIRS = listOf(
            Path.of("models/multilingual-e5-small"),
            Path.of("backend/models/multilingual-e5-small"),
        )

        /** Convocatoria de ejemplo (dominio BOE). */
        const val GRANT_CALL =
            "Convocatoria de ayudas para la realización de prácticas formativas en empresas " +
                "dirigida a estudiantes universitarios."

        /** Paráfrasis de la misma convocatoria. */
        const val GRANT_CALL_PARAPHRASE =
            "Ayudas para prácticas formativas en empresas: convocatoria y presentación de " +
                "solicitudes por estudiantes universitarios."

        /** Norma sin relación con la convocatoria. */
        const val UNRELATED_REGULATION =
            "Real Decreto por el que se modifica el régimen de infracciones y sanciones en " +
                "materia de tráfico, circulación de vehículos a motor y seguridad vial."
    }
}
