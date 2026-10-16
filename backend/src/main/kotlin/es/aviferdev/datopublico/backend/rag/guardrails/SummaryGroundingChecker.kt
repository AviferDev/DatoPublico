package es.aviferdev.datopublico.backend.rag.guardrails

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.model.SummaryDto

/**
 * Evaluación de **grounding** de un resumen contra el contexto recuperado.
 *
 * @property score fracción de afirmaciones sustentadas por el contexto (`[0,1]`);
 *   `1.0` si el resumen no tiene afirmaciones.
 * @property violations violaciones de grounding detectadas; vacía si todo está
 *   sustentado.
 */
data class GroundingAssessment(
    val score: Double,
    val violations: List<SummaryGuardrailViolation>,
)

/**
 * Verificación **pura** de que las afirmaciones de un resumen ciudadano están
 * sustentadas por el contexto recuperado (FT00022).
 *
 * Es un `object` sin red, BD, reloj ni estado: la misma entrada produce siempre el
 * mismo resultado. Considera **afirmaciones** las oraciones de «¿Qué cambia?» y
 * «¿A quién afecta?» más cada entrada no vacía de «Cifras clave», y comprueba que
 * sus términos de contenido aparezcan en el contexto ([SummaryTextNormalizer]).
 *
 * Límite documentado: el grounding es **léxico** y el umbral [MIN_CLAIM_SUPPORT]
 * es **conservador**; una paráfrasis válida puede no alcanzarlo y acabar en
 * revisión o descarte (falso negativo asumido: nunca se publica una posible
 * alucinación).
 */
object SummaryGroundingChecker {

    /**
     * Fracción mínima de términos de contenido de una afirmación que deben
     * aparecer en el contexto para considerarla sustentada.
     */
    const val MIN_CLAIM_SUPPORT: Double = 0.6

    /**
     * Evalúa el grounding de [summary] contra [context].
     *
     * @param summary resumen ciudadano a verificar.
     * @param context fragmentos recuperados que sustentan la generación.
     * @return la puntuación de grounding y las violaciones detectadas.
     */
    fun check(summary: SummaryDto, context: List<FragmentEntity>): GroundingAssessment {
        val contextTokens = context.flatMap { fragment -> fragmentContentTokens(fragment) }.toSet()
        val contextFigures = context.flatMap { fragment -> fragmentFigures(fragment) }.toSet()
        val claims = claimsOf(summary)
        val violations = violationsOf(claims, contextTokens, contextFigures)
        return GroundingAssessment(scoreOf(claims, contextTokens, contextFigures), violations)
    }

    /** Afirmaciones del resumen: oraciones de las dos secciones y cada cifra clave. */
    private fun claimsOf(summary: SummaryDto): List<String> =
        SummaryTextNormalizer.sentences(summary.whatChanges) +
            SummaryTextNormalizer.sentences(summary.whoIsAffected) +
            summary.keyFigures.filter { figure -> figure.isNotBlank() }

    /** Fracción de afirmaciones sustentadas; `1.0` si no hay afirmaciones. */
    private fun scoreOf(claims: List<String>, contextTokens: Set<String>, contextFigures: Set<String>): Double =
        if (claims.isEmpty()) {
            1.0
        } else {
            claims.count { claim -> isSupported(claim, contextTokens, contextFigures) }.toDouble() / claims.size
        }

    /**
     * Una afirmación está sustentada si **todas** sus cifras están en el contexto y
     * la fracción de sus términos de contenido presentes alcanza
     * [MIN_CLAIM_SUPPORT]. Una afirmación sin términos de contenido (solo palabras
     * vacías) cuenta como sustentada.
     */
    private fun isSupported(claim: String, contextTokens: Set<String>, contextFigures: Set<String>): Boolean {
        val claimTokens = SummaryTextNormalizer.contentTokens(claim)
        val claimFigures = SummaryTextNormalizer.figures(claim)
        return (claimTokens.isEmpty() || supportRatio(claimTokens, contextTokens) >= MIN_CLAIM_SUPPORT) &&
            claimFigures.all { figure -> figure in contextFigures }
    }

    /** Fracción de términos de contenido de la afirmación presentes en el contexto. */
    private fun supportRatio(claimTokens: List<String>, contextTokens: Set<String>): Double =
        claimTokens.count { token -> token in contextTokens }.toDouble() / claimTokens.size

    /** Violaciones de grounding: cifra fuera del contexto y/o afirmación no sustentada. */
    private fun violationsOf(
        claims: List<String>,
        contextTokens: Set<String>,
        contextFigures: Set<String>,
    ): List<SummaryGuardrailViolation> = buildList {
        if (claims.any { claim -> hasUnsupportedFigure(claim, contextFigures) }) {
            add(SummaryGuardrailViolation.UNSUPPORTED_FIGURE)
        }
        if (claims.any { claim -> hasUnsupportedContent(claim, contextTokens) }) {
            add(SummaryGuardrailViolation.UNSUPPORTED_CLAIM)
        }
    }

    /** `true` si la afirmación tiene alguna cifra que no está en el contexto. */
    private fun hasUnsupportedFigure(claim: String, contextFigures: Set<String>): Boolean =
        SummaryTextNormalizer.figures(claim).any { figure -> figure !in contextFigures }

    /** `true` si los términos de contenido de la afirmación no alcanzan el umbral. */
    private fun hasUnsupportedContent(claim: String, contextTokens: Set<String>): Boolean {
        val claimTokens = SummaryTextNormalizer.contentTokens(claim)
        return claimTokens.isNotEmpty() && supportRatio(claimTokens, contextTokens) < MIN_CLAIM_SUPPORT
    }
}

/** Términos de contenido de un fragmento (su referencia, si la hay, y su contenido). */
private fun fragmentContentTokens(fragment: FragmentEntity): List<String> =
    SummaryTextNormalizer.contentTokens(fragmentText(fragment))

/** Cifras de un fragmento (su referencia, si la hay, y su contenido). */
private fun fragmentFigures(fragment: FragmentEntity): List<String> =
    SummaryTextNormalizer.figures(fragmentText(fragment))

/** Texto verificable de un fragmento: su referencia (si la hay) y su contenido. */
private fun fragmentText(fragment: FragmentEntity): String =
    "${fragment.reference.orEmpty()} ${fragment.content}"
