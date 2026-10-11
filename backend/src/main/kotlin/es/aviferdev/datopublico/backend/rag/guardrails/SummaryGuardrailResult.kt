package es.aviferdev.datopublico.backend.rag.guardrails

import es.aviferdev.datopublico.validation.CitizenSummaryViolation

/**
 * Resultado **interno** de evaluar los guardrails de un resumen ciudadano
 * (sin sufijo: no es un DTO de transporte).
 *
 * Separa las violaciones de **contrato** ([contractViolations], de
 * `CitizenSummaryValidator`, FT00019) de las de **contenido**
 * ([guardrailViolations], de esta feature) y expone la decisión y la
 * **puntuación de grounding** `[0,1]` que FT00023 usará como señal de confianza.
 *
 * @property decision desenlace tipado del guardrail.
 * @property contractViolations violaciones estructurales del contrato; vacía si el
 *   contrato es válido.
 * @property guardrailViolations violaciones de fuente o grounding; vacía si no hay
 *   ninguna.
 * @property groundingScore fracción de afirmaciones sustentadas por el contexto
 *   (`[0,1]`); `0.0` si no hay contexto.
 */
data class SummaryGuardrailResult(
    val decision: GuardrailDecision,
    val contractViolations: List<CitizenSummaryViolation>,
    val guardrailViolations: List<SummaryGuardrailViolation>,
    val groundingScore: Double,
) {
    /** `true` solo si la decisión es [GuardrailDecision.PUBLISHABLE]. */
    val isPublishable: Boolean get() = decision == GuardrailDecision.PUBLISHABLE
}
