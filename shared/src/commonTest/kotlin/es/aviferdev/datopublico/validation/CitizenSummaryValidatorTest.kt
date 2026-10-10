package es.aviferdev.datopublico.validation

import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.ResumenDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CitizenSummaryValidatorTest {

    private val validSummary = ResumenDto(
        queCambia = "Se convocan 120 plazas de auxiliar administrativo.",
        aQuienAfecta = "Personas con título de ESO o equivalente.",
        cifrasClave = listOf("120 plazas", "20 días hábiles"),
        fuenteOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        plazo = PlazoDto(fechaLimite = "2026-10-30"),
    )

    @Test
    fun blankOfficialSourceFails() {
        val summary = validSummary.copy(fuenteOficial = "   ")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertFalse(result.isValid)
        assertEquals(listOf(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE), result.violations)
    }

    @Test
    fun sourceWithoutAbsoluteSchemeFails() {
        val summary = validSummary.copy(fuenteOficial = "www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE), result.violations)
    }

    @Test
    fun employmentSummaryWithoutDeadlineFails() {
        val summary = validSummary.copy(plazo = null)

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO)

        assertFalse(result.isValid)
        assertEquals(listOf(CitizenSummaryViolation.MISSING_DEADLINE), result.violations)
    }

    @Test
    fun scholarshipSummaryWithBlankDeadlineFails() {
        val summary = validSummary.copy(plazo = PlazoDto(fechaLimite = "  "))

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_DEADLINE), result.violations)
    }

    @Test
    fun generalNormWithoutDeadlineIsValid() {
        val summary = validSummary.copy(plazo = null)

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertTrue(result.isValid)
        assertEquals(emptyList(), result.violations)
    }

    @Test
    fun blankWhatChangesFails() {
        val summary = validSummary.copy(queCambia = "")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_WHAT_CHANGES), result.violations)
    }

    @Test
    fun blankWhoIsAffectedFails() {
        val summary = validSummary.copy(aQuienAfecta = "   ")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_WHO_IS_AFFECTED), result.violations)
    }

    @Test
    fun blankKeyFigureFails() {
        val summary = validSummary.copy(cifrasClave = listOf("120 plazas", ""))

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.BLANK_KEY_FIGURE), result.violations)
    }

    @Test
    fun emptyKeyFiguresAreAllowed() {
        val summary = validSummary.copy(cifrasClave = emptyList())

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertTrue(result.isValid)
    }

    @Test
    fun violationsAccumulate() {
        val summary = validSummary.copy(queCambia = "", aQuienAfecta = "", fuenteOficial = "")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(
            listOf(
                CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE,
                CitizenSummaryViolation.MISSING_WHAT_CHANGES,
                CitizenSummaryViolation.MISSING_WHO_IS_AFFECTED,
            ),
            result.violations,
        )
    }

    @Test
    fun employmentAndScholarshipUseDeadlineVariant() {
        val categories = listOf(
            CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        )

        val variants = categories.map { category -> CitizenSummaryVariantResolver.variantFor(category) }

        assertEquals(
            listOf(
                CitizenSummaryVariant.EMPLOYMENT_OR_SCHOLARSHIP,
                CitizenSummaryVariant.EMPLOYMENT_OR_SCHOLARSHIP,
            ),
            variants,
        )
    }

    @Test
    fun remainingCategoriesUseGeneralVariant() {
        val deadlineCategories = setOf(
            CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        )

        val variants = (CategoriaDto.entries - deadlineCategories)
            .map { category -> CitizenSummaryVariantResolver.variantFor(category) }

        assertTrue(variants.all { variant -> variant == CitizenSummaryVariant.GENERAL })
    }
}
