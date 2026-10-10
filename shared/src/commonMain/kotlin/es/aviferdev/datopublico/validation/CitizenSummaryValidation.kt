package es.aviferdev.datopublico.validation

/**
 * Violación del contrato del resumen ciudadano.
 *
 * Enum **interno** (sin `Dto`, no `@Serializable`): el validador **acumula**
 * violaciones y **no lanza**, porque un resumen inválido es un resultado esperado
 * que consumirán los guardrails (FT00022) y la cola de revisión (FT00023).
 */
enum class CitizenSummaryViolation {
    /** Falta la fuente oficial o no es un enlace absoluto `http(s)://`. */
    MISSING_OFFICIAL_SOURCE,

    /** La sección «¿Qué cambia?» está vacía. */
    MISSING_WHAT_CHANGES,

    /** La sección «¿A quién afecta?» está vacía. */
    MISSING_WHO_IS_AFFECTED,

    /** Alguna entrada de «Cifras clave» está en blanco. */
    BLANK_KEY_FIGURE,

    /** La variante empleo/beca exige plazo con fecha límite no vacía. */
    MISSING_DEADLINE,
}

/**
 * Resultado de validar el contrato de un resumen ciudadano.
 *
 * @property violations violaciones detectadas; vacía si el resumen cumple el
 *   contrato.
 */
data class CitizenSummaryValidation(
    val violations: List<CitizenSummaryViolation>,
) {
    /** `true` si el resumen cumple todos los invariantes del contrato. */
    val isValid: Boolean get() = violations.isEmpty()
}
