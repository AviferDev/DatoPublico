package es.aviferdev.datopublico.validation

import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.ResumenDto

/**
 * Valida el **contrato** del resumen ciudadano (no su veracidad).
 *
 * Comprueba los invariantes estructurales que todo resumen debe cumplir: fuente
 * oficial obligatoria, secciones «¿Qué cambia?» y «¿A quién afecta?» no vacías,
 * cifras clave sin entradas en blanco y, para la variante de empleo/becas, plazo
 * con fecha límite. **No lanza**: acumula las violaciones en el resultado, porque
 * un resumen inválido es un estado esperado (lo consumirán FT00022/FT00023), no un
 * error de programación.
 *
 * Es **puro**: sin red, base de datos, reloj ni estado. La **alcanzabilidad** del
 * enlace y la veracidad de las afirmaciones son guardrails de FT00022, no de este
 * validador.
 */
object CitizenSummaryValidator {
    /**
     * Valida [summary] según la [category] de su publicación.
     *
     * @param summary resumen ciudadano a validar.
     * @param category categoría curada de la publicación de origen.
     * @return el resultado con las violaciones detectadas; vacío si es válido.
     */
    fun validate(summary: ResumenDto, category: CategoriaDto): CitizenSummaryValidation {
        val variant = CitizenSummaryVariantResolver.variantFor(category)
        val violations = buildList {
            if (!hasAbsoluteSource(summary.fuenteOficial)) {
                add(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE)
            }
            if (summary.queCambia.isBlank()) {
                add(CitizenSummaryViolation.MISSING_WHAT_CHANGES)
            }
            if (summary.aQuienAfecta.isBlank()) {
                add(CitizenSummaryViolation.MISSING_WHO_IS_AFFECTED)
            }
            if (summary.cifrasClave.any { figure -> figure.isBlank() }) {
                add(CitizenSummaryViolation.BLANK_KEY_FIGURE)
            }
            if (variant == CitizenSummaryVariant.EMPLOYMENT_OR_SCHOLARSHIP && !hasDeadline(summary.plazo)) {
                add(CitizenSummaryViolation.MISSING_DEADLINE)
            }
        }
        return CitizenSummaryValidation(violations)
    }

    /** `true` si [source] no está en blanco y es un enlace absoluto `http(s)://`. */
    private fun hasAbsoluteSource(source: String): Boolean {
        val trimmed = source.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    /** `true` si [deadline] existe y su fecha límite no está en blanco. */
    private fun hasDeadline(deadline: PlazoDto?): Boolean =
        deadline?.fechaLimite?.isNotBlank() == true
}
