package es.aviferdev.datopublico.validation

import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.SummaryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CitizenSummaryValidatorTest {

    private val validSummary = SummaryDto(
        whatChanges = "Se convocan 120 plazas de auxiliar administrativo.",
        whoIsAffected = "Personas con título de ESO o equivalente.",
        keyFigures = listOf("120 plazas", "20 días hábiles"),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        deadline = DeadlineDto(deadlineDate = "2026-10-30"),
    )

    @Test
    fun blankOfficialSourceFails() {
        val summary = validSummary.copy(officialSource = "   ")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertFalse(result.isValid)
        assertEquals(listOf(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE), result.violations)
    }

    @Test
    fun sourceWithoutAbsoluteSchemeFails() {
        val summary = validSummary.copy(officialSource = "www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_OFFICIAL_SOURCE), result.violations)
    }

    @Test
    fun employmentSummaryWithoutDeadlineFails() {
        val summary = validSummary.copy(deadline = null)

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO)

        assertFalse(result.isValid)
        assertEquals(listOf(CitizenSummaryViolation.MISSING_DEADLINE), result.violations)
    }

    @Test
    fun scholarshipSummaryWithBlankDeadlineFails() {
        val summary = validSummary.copy(deadline = DeadlineDto(deadlineDate = "  "))

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_DEADLINE), result.violations)
    }

    @Test
    fun generalNormWithoutDeadlineIsValid() {
        val summary = validSummary.copy(deadline = null)

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertTrue(result.isValid)
        assertEquals(emptyList(), result.violations)
    }

    @Test
    fun blankWhatChangesFails() {
        val summary = validSummary.copy(whatChanges = "")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_WHAT_CHANGES), result.violations)
    }

    @Test
    fun blankWhoIsAffectedFails() {
        val summary = validSummary.copy(whoIsAffected = "   ")

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.MISSING_WHO_IS_AFFECTED), result.violations)
    }

    @Test
    fun blankKeyFigureFails() {
        val summary = validSummary.copy(keyFigures = listOf("120 plazas", ""))

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertEquals(listOf(CitizenSummaryViolation.BLANK_KEY_FIGURE), result.violations)
    }

    @Test
    fun emptyKeyFiguresAreAllowed() {
        val summary = validSummary.copy(keyFigures = emptyList())

        val result = CitizenSummaryValidator.validate(summary, CategoriaDto.NORMAS_Y_LEGISLACION)

        assertTrue(result.isValid)
    }

    @Test
    fun violationsAccumulate() {
        val summary = validSummary.copy(whatChanges = "", whoIsAffected = "", officialSource = "")

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
