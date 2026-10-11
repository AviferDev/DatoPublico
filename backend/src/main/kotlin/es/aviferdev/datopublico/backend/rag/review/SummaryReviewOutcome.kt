package es.aviferdev.datopublico.backend.rag.review

/**
 * Resultado **interno** de la disposición de un resumen tras los guardrails
 * (FT00023). Va **sin sufijo**: no es un DTO de transporte.
 *
 * Refleja qué se hizo con el resumen y los `id` persistidos, para que el wiring
 * (FT00026+) y los tests puedan comprobarlo. Las tres variantes se corresponden
 * con las tres decisiones de [es.aviferdev.datopublico.backend.rag.guardrails.GuardrailDecision].
 */
sealed interface SummaryReviewOutcome {
    /**
     * El resumen se publicó directamente (decisión `PUBLISHABLE`).
     *
     * @property summaryId `id` del resumen persistido con estado `publicado`.
     */
    data class Published(val summaryId: Long) : SummaryReviewOutcome

    /**
     * El resumen quedó encolado para revisión (decisión `NEEDS_REVIEW`).
     *
     * @property summaryId `id` del resumen persistido con estado `en_cola_revision`.
     * @property reviewId `id` de la entrada `pendiente` de `cola_revision`.
     */
    data class Queued(val summaryId: Long, val reviewId: Long) : SummaryReviewOutcome

    /**
     * El resumen se descartó (decisión `DISCARDED`): ni se publica ni se encola.
     *
     * @property summaryId `id` del resumen persistido con estado `descartado`.
     */
    data class Discarded(val summaryId: Long) : SummaryReviewOutcome
}
