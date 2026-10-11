package es.aviferdev.datopublico.backend.rag.review

import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailResult

/**
 * Formateador **puro y determinista** del motivo de revisión (FT00023).
 *
 * Convierte las violaciones de **contenido** de [SummaryGuardrailResult] en el
 * texto que se guarda en `cola_revision.motivo`. Son **nombres de violación en
 * inglés** (diagnóstico interno del operador), **no** texto de UI; la traducción a
 * lenguaje natural, si llega, es de la revisión manual (FT00042+).
 *
 * Nunca devuelve una cadena vacía: si no hay violaciones (caso límite de un
 * `NEEDS_REVIEW` decidido solo por el umbral), usa la constante [LOW_GROUNDING].
 */
object ReviewReasonFormatter {

    /** Motivo de reserva cuando no hay violaciones de contenido que listar. */
    const val LOW_GROUNDING: String = "LOW_GROUNDING"

    /**
     * Construye el motivo a partir de las violaciones de contenido de [result].
     *
     * @param result resultado tipado de los guardrails (FT00022).
     * @return los nombres de `guardrailViolations` unidos por `,` o
     *   [LOW_GROUNDING] si no hay ninguna.
     */
    fun of(result: SummaryGuardrailResult): String {
        val violations = result.guardrailViolations.joinToString(",") { violation -> violation.name }
        return violations.ifEmpty { LOW_GROUNDING }
    }
}
