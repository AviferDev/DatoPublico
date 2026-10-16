package es.aviferdev.datopublico.validation

import es.aviferdev.datopublico.model.CategoryDto

/**
 * Variante del contrato del resumen ciudadano.
 *
 * No es un tipo de wire: no lleva sufijo `Dto` ni `@Serializable`. Solo distingue
 * el resumen **general** del que exige **plazo** (empleo público y becas).
 */
enum class CitizenSummaryVariant {
    /** Resumen de cualquier categoría que no exige plazo. */
    GENERAL,

    /** Resumen de empleo público o becas/subvenciones: exige plazo. */
    EMPLOYMENT_OR_SCHOLARSHIP,
}

/**
 * Resuelve la [CitizenSummaryVariant] de una categoría de publicación.
 *
 * Es **puro** y sin estado: la misma categoría devuelve siempre la misma variante.
 */
object CitizenSummaryVariantResolver {
    /**
     * Devuelve la variante del resumen para [category].
     *
     * `PUBLIC_EMPLOYMENT_AND_JOB_OFFERS` y `SCHOLARSHIPS_GRANTS_AND_AID` usan la
     * variante que exige plazo; el resto de categorías usan la variante general.
     *
     * @param category categoría curada de la publicación de origen.
     * @return la variante del contrato del resumen ciudadano.
     */
    fun variantFor(category: CategoryDto): CitizenSummaryVariant = when (category) {
        CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
        CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID -> CitizenSummaryVariant.EMPLOYMENT_OR_SCHOLARSHIP
        else -> CitizenSummaryVariant.GENERAL
    }
}
