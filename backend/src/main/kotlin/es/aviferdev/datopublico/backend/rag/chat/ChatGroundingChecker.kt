package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGroundingChecker
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryTextNormalizer

/**
 * Guardrail de **grounding léxico** de una respuesta de chat contra el contexto
 * recuperado (FT00025).
 *
 * Es un `object` sin red, BD, reloj ni estado: la misma entrada produce siempre el
 * mismo resultado. Considera **afirmaciones** las oraciones de la respuesta
 * ([SummaryTextNormalizer.sentences]) y comprueba que **todas** estén sustentadas
 * por el contexto: todas sus **cifras** deben aparecer en el contexto y la fracción
 * de sus **términos de contenido** presentes debe alcanzar
 * [SummaryGroundingChecker.MIN_CLAIM_SUPPORT] (= 0.6, constante **reutilizada** de
 * FT00022). Una oración sin términos de contenido (solo palabras vacías) cuenta
 * como sustentada.
 *
 * Límite documentado: el grounding es **léxico** y **conservador** (una paráfrasis
 * válida puede degradar a `sin_fundamento`); el falso negativo **nunca** publica
 * una posible alucinación (fail-safe). El umbral es una constante ajustable sin
 * cambiar la firma.
 */
object ChatGroundingChecker {

    /**
     * Evalúa si **todas** las afirmaciones de [answer] están sustentadas por
     * [context].
     *
     * @param answer respuesta del modelo a verificar.
     * @param context fragmentos recuperados que sustentan la generación.
     * @return `true` si toda afirmación está sustentada; `false` si alguna usa
     *   términos de contenido o cifras que no aparecen en el contexto.
     */
    fun isSupported(answer: String, context: List<FragmentEntity>): Boolean {
        val contextTokens = context.flatMap { fragment -> contentTokensOf(fragment) }.toSet()
        val contextFigures = context.flatMap { fragment -> figuresOf(fragment) }.toSet()
        return SummaryTextNormalizer.sentences(answer).all { sentence ->
            isSentenceSupported(sentence, contextTokens, contextFigures)
        }
    }

    /**
     * Una oración está sustentada si **todas** sus cifras están en el contexto y
     * la fracción de sus términos de contenido presentes alcanza
     * [SummaryGroundingChecker.MIN_CLAIM_SUPPORT].
     */
    private fun isSentenceSupported(
        sentence: String,
        contextTokens: Set<String>,
        contextFigures: Set<String>,
    ): Boolean {
        val sentenceTokens = SummaryTextNormalizer.contentTokens(sentence)
        val sentenceFigures = SummaryTextNormalizer.figures(sentence)
        return (sentenceTokens.isEmpty() || supportRatio(sentenceTokens, contextTokens) >= MIN_SUPPORT) &&
            sentenceFigures.all { figure -> figure in contextFigures }
    }

    /** Fracción de términos de contenido de la oración presentes en el contexto. */
    private fun supportRatio(sentenceTokens: List<String>, contextTokens: Set<String>): Double =
        sentenceTokens.count { token -> token in contextTokens }.toDouble() / sentenceTokens.size

    /** Términos de contenido de un fragmento (su referencia, si la hay, y su contenido). */
    private fun contentTokensOf(fragment: FragmentEntity): List<String> =
        SummaryTextNormalizer.contentTokens(fragmentText(fragment))

    /** Cifras de un fragmento (su referencia, si la hay, y su contenido). */
    private fun figuresOf(fragment: FragmentEntity): List<String> =
        SummaryTextNormalizer.figures(fragmentText(fragment))

    /** Texto verificable de un fragmento: su referencia (si la hay) y su contenido. */
    private fun fragmentText(fragment: FragmentEntity): String =
        "${fragment.reference.orEmpty()} ${fragment.content}"

    /** Umbral reutilizado de FT00022: fracción mínima de términos de contenido. */
    private const val MIN_SUPPORT: Double = SummaryGroundingChecker.MIN_CLAIM_SUPPORT
}
