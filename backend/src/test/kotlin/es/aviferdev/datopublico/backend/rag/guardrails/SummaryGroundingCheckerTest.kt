package es.aviferdev.datopublico.backend.rag.guardrails

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.model.SummaryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [SummaryGroundingChecker]: afirmación
 * sustentada/no sustentada, cifra fuera del contexto y contexto vacío (escenarios
 * 2, 3 y 8 del spec).
 */
class SummaryGroundingCheckerTest {

    @Test
    fun `a fully supported summary scores one without violations`() {
        val assessment = SummaryGroundingChecker.check(supportedSummary(), context())

        assertEquals(1.0, assessment.score)
        assertTrue(assessment.violations.isEmpty(), assessment.violations.toString())
    }

    @Test
    fun `a claim outside the context is unsupported`() {
        val summary = supportedSummary()
            .copy(whatChanges = "Se aumenta el plazo de solicitud. Se reduce el impuesto especial")

        val assessment = SummaryGroundingChecker.check(summary, context())

        assertTrue(assessment.score < 1.0)
        assertTrue(assessment.violations.contains(SummaryGuardrailViolation.UNSUPPORTED_CLAIM))
    }

    @Test
    fun `a figure outside the context is unsupported`() {
        val summary = supportedSummary().copy(keyFigures = listOf("5000 euros"))

        val assessment = SummaryGroundingChecker.check(summary, context())

        assertTrue(assessment.violations.contains(SummaryGuardrailViolation.UNSUPPORTED_FIGURE))
    }

    @Test
    fun `an empty context scores zero`() {
        val assessment = SummaryGroundingChecker.check(supportedSummary(), emptyList())

        assertEquals(0.0, assessment.score)
        assertTrue(assessment.violations.contains(SummaryGuardrailViolation.UNSUPPORTED_CLAIM))
    }

    @Test
    fun `a summary without claims scores one`() {
        val summary = SummaryDto(
            whatChanges = "",
            whoIsAffected = "",
            keyFigures = emptyList(),
            officialSource = URL,
        )

        val assessment = SummaryGroundingChecker.check(summary, context())

        assertEquals(1.0, assessment.score)
        assertTrue(assessment.violations.isEmpty())
    }

    private fun supportedSummary(): SummaryDto = SummaryDto(
        whatChanges = "Se aumenta el plazo de solicitud",
        whoIsAffected = "A las personas beneficiarias",
        keyFigures = listOf("1000 euros"),
        officialSource = URL,
    )

    private fun context(): List<FragmentEntity> = listOf(
        FragmentEntity(
            publicationId = "BOE-A-2026-1",
            order = 0,
            reference = "Artículo 1",
            content = CONTEXT_TEXT,
        )
    )

    private companion object {
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

        const val CONTEXT_TEXT =
            "Se aumenta el plazo de solicitud para las personas beneficiarias con una cuantía de 1000 euros."
    }
}
