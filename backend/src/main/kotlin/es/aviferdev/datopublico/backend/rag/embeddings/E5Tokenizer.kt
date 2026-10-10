package es.aviferdev.datopublico.backend.rag.embeddings

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import java.io.IOException
import java.nio.file.Path

/**
 * [TextTokenizer] real sobre el tokenizador XLM-R de DJL.
 *
 * Carga un `tokenizer.json` **local** (el de `multilingual-e5-small`, que incluye
 * la conversión SentencePiece/Unigram); DJL empaqueta las librerías nativas
 * `libtokenizers` por plataforma, así que **no** hay llamada de red ni descarga en
 * tiempo de ejecución. Aplica truncado a [maxTokens] y **sin** *padding* (cada
 * secuencia se procesa a su longitud).
 *
 * @property maxTokens longitud máxima de secuencia configurada.
 */
internal class E5Tokenizer private constructor(
    private val tokenizer: HuggingFaceTokenizer,
    private val maxTokens: Int,
) : TextTokenizer {

    override fun tokenize(text: String): TokenizedInput {
        val encoding = tokenizer.encode(text)
        return TokenizedInput(encoding.ids, encoding.attentionMask)
    }

    override fun countTokens(text: String): Int = tokenizer.encode(text).ids.size

    /** Libera los recursos nativos del tokenizador (idempotente). */
    override fun close() {
        tokenizer.close()
    }

    companion object {
        /**
         * Carga el tokenizador desde [tokenizerPath] (fichero `tokenizer.json` o el
         * directorio que lo contiene).
         *
         * @throws EmbeddingException si el fichero no es legible o DJL no puede
         *   inicializar el nativo (fail-fast con mensaje claro).
         */
        fun from(tokenizerPath: Path, maxTokens: Int): E5Tokenizer = try {
            val tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerPath)
                .optMaxLength(maxTokens)
                .optTruncation(true)
                .optPadding(false)
                .build()
            E5Tokenizer(tokenizer, maxTokens)
        } catch (error: IOException) {
            throw EmbeddingException(
                "No se pudo cargar el tokenizador de embeddings en '$tokenizerPath'", error
            )
        }

        /** Longitud máxima por defecto si no se especifica. */
        const val DEFAULT_MAX_TOKENS: Int = EmbeddingConfig.DEFAULT_MAX_TOKENS
    }
}
