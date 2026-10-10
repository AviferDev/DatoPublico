package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.model.ResumenDto
import es.aviferdev.datopublico.validation.CitizenSummaryViolation

/**
 * Resultado **interno** de generar un resumen ciudadano (sin sufijo: no es un DTO
 * de transporte).
 *
 * Modela los dos desenlaces esperados: un resumen que cumple el contrato de
 * `CitizenSummaryValidator` ([Valid]) o uno que **no se publica** y lleva sus
 * violaciones ([Invalid]). Un resumen inválido **no** es una excepción: lo
 * consumirán los guardrails (FT00022) y la cola de revisión (FT00023).
 */
sealed interface CitizenSummaryResult {
    /**
     * Resumen que cumple el contrato (FT00019) y es publicable.
     *
     * @property summary resumen con la fuente oficial ya inyectada.
     */
    data class Valid(val summary: ResumenDto) : CitizenSummaryResult

    /**
     * Resumen que **no** cumple el contrato y no debe publicarse.
     *
     * @property violations violaciones detectadas por `CitizenSummaryValidator`.
     */
    data class Invalid(val violations: List<CitizenSummaryViolation>) : CitizenSummaryResult
}

/**
 * Error de frontera de la generación del resumen: la salida del proveedor no es
 * interpretable (vacía, sin JSON válido o sin los campos obligatorios).
 *
 * Es **tipado** y distinto de un resumen inválido ([CitizenSummaryResult.Invalid]),
 * que es un resultado de dominio esperado.
 */
class SummaryGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
