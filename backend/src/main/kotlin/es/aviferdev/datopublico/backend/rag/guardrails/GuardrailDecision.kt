package es.aviferdev.datopublico.backend.rag.guardrails

/**
 * Decisión **tipada** de los guardrails anti-alucinación (FT00022).
 *
 * Es el desenlace que el wiring (FT00026+) usará **después** de generar el resumen
 * y **antes** de publicarlo: publicar, enviar a la cola de revisión (FT00023) o
 * descartar. El guardrail **no** persiste nada; solo expone la decisión.
 */
enum class GuardrailDecision {
    /** El resumen cumple contrato, fuente y grounding: se puede publicar. */
    PUBLISHABLE,

    /**
     * El resumen cumple contrato y fuente pero su grounding es de **baja
     * confianza**: no se publica y FT00023 lo encolará para revisión.
     */
    NEEDS_REVIEW,

    /**
     * El resumen no es recuperable: contrato roto, fuente no oficial/no alcanzable
     * o grounding por debajo del umbral de descarte. **No** se publica.
     */
    DISCARDED,
}
