package es.aviferdev.datopublico.backend.rag.guardrails

/**
 * Violación de **contenido** detectada por los guardrails anti-alucinación
 * (FT00022).
 *
 * Enum **interno** (sin `Dto`, no `@Serializable`): complementa las violaciones de
 * **contrato** de `CitizenSummaryValidator` (FT00019). El guardrail **acumula** las
 * violaciones y **no lanza**: un resumen no publicable es un resultado esperado.
 */
enum class SummaryGuardrailViolation {
    /** La fuente no es https, no apunta al BOE o no coincide con la publicación. */
    SOURCE_NOT_OFFICIAL,

    /** La fuente es estructuralmente válida pero no respondió 2xx/3xx. */
    SOURCE_NOT_REACHABLE,

    /** No hay contexto recuperado contra el que verificar el resumen. */
    NO_CONTEXT,

    /** Alguna afirmación no está sustentada por el contexto recuperado. */
    UNSUPPORTED_CLAIM,

    /** Alguna cifra del resumen no aparece en el contexto recuperado. */
    UNSUPPORTED_FIGURE,
}
