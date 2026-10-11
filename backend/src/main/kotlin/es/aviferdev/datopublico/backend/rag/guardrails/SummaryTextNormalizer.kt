package es.aviferdev.datopublico.backend.rag.guardrails

import java.text.Normalizer
import java.util.Locale

/**
 * Normalización de texto **pura** de los guardrails anti-alucinación (FT00022).
 *
 * Es un `object` sin estado ni dependencias: no toca red, BD, reloj ni
 * aleatoriedad, y la misma entrada produce siempre la misma salida. Sirve de base
 * al grounding léxico ([SummaryGroundingChecker]).
 *
 * Límite documentado: la normalización es **léxica** (sin *stemming* ni
 * sinónimos), así que una paráfrasis puede no coincidir con el contexto; se
 * compensa siendo **conservador** (un falso negativo lleva a revisión o descarte,
 * nunca a publicar una posible alucinación).
 */
object SummaryTextNormalizer {

    /**
     * Longitud mínima de un token de contenido.
     *
     * Los tokens más cortos que este umbral (p. ej. `de`, `la`, `en`) se descartan
     * como ruido, **salvo** los que contienen algún dígito (cifras), que se
     * conservan siempre.
     */
    const val MIN_TOKEN_LENGTH: Int = 3

    /** Marcas diacríticas combinantes que se eliminan tras la descomposición NFD. */
    private val ACCENT_MARKS = Regex("\\p{Mn}+")

    /** Delimitadores de oración: punto, punto y coma y salto de línea. */
    private val SENTENCE_DELIMITERS = Regex("[.;\\n]")

    /**
     * Palabras vacías castellanas (lista curada) que no aportan contenido.
     *
     * Se comparan **después** de pasar a minúsculas y quitar acentos, así que se
     * guardan en su forma normalizada (`mas`, `segun`, `esta`…).
     */
    private val STOP_WORDS = setOf(
        "que", "los", "las", "del", "con", "por", "para", "una", "uno", "unos",
        "unas", "como", "sus", "este", "esta", "estos", "estas", "ese", "esa",
        "esos", "esas", "ser", "sera", "seran", "son", "sea", "han", "has", "hay",
        "fue", "era", "sobre", "entre", "desde", "hasta", "segun", "cuando",
        "donde", "todo", "toda", "todos", "todas", "sin", "porque", "cual",
        "cuales", "quien", "quienes", "cuyo", "cuya", "tras", "ante", "bajo",
        "contra", "hacia", "mediante", "durante", "salvo", "excepto", "aunque",
        "pero", "sino", "pues", "asi", "aqui", "alli", "ahora", "entonces",
        "ademas", "les", "nos", "su", "mas",
    )

    /**
     * Tokeniza [text]: minúsculas (`Locale.ROOT`), sin acentos (NFD + descarte de
     * marcas), todo lo no alfanumérico pasa a espacio, colapsa y recorta.
     *
     * @param text texto a tokenizar.
     * @return tokens en orden de aparición; vacío si no hay nada alfanumérico.
     */
    fun tokens(text: String): List<String> {
        val stripped = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(ACCENT_MARKS, "")
        val spaced = stripped.map { character -> if (character.isLetterOrDigit()) character else ' ' }
        return spaced.joinToString(separator = "").split(' ').filter { token -> token.isNotBlank() }
    }

    /**
     * Tokeniza [text] y descarta el ruido: palabras vacías y tokens más cortos que
     * [MIN_TOKEN_LENGTH], **conservando** siempre los que contienen dígitos.
     *
     * @param text texto del que extraer los términos de contenido.
     * @return términos de contenido relevantes.
     */
    fun contentTokens(text: String): List<String> = tokens(text).filter { token ->
        token.any { character -> character.isDigit() } ||
            (token.length >= MIN_TOKEN_LENGTH && token !in STOP_WORDS)
    }

    /**
     * Extrae las **cifras** de [text]: tokens que contienen algún dígito.
     *
     * @param text texto del que extraer las cifras.
     * @return tokens numéricos en orden de aparición.
     */
    fun figures(text: String): List<String> = tokens(text).filter { token ->
        token.any { character -> character.isDigit() }
    }

    /**
     * Divide [text] en oraciones por `.`, `;` y salto de línea, recortando cada
     * una y descartando las vacías.
     *
     * @param text texto a dividir.
     * @return oraciones no vacías en orden de aparición.
     */
    fun sentences(text: String): List<String> = text.split(SENTENCE_DELIMITERS)
        .map { sentence -> sentence.trim() }
        .filter { sentence -> sentence.isNotEmpty() }
}
