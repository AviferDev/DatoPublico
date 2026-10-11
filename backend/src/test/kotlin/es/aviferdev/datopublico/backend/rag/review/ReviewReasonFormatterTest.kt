package es.aviferdev.datopublico.backend.rag.review

import es.aviferdev.datopublico.backend.rag.guardrails.GuardrailDecision
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailResult
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [ReviewReasonFormatter]: une las violaciones de
 * contenido, cae en [ReviewReasonFormatter.LOW_GROUNDING] sin violaciones y nunca
 * devuelve una cadena vacía.
 */
class ReviewReasonFormatterTest {

    @Test
    fun `joins the content violations in order`() {
        val result = resultWith(
            SummaryGuardrailViolation.UNSUPPORTED_CLAIM,
            SummaryGuardrailViolation.UNSUPPORTED_FIGURE,
        )

        assertEquals("UNSUPPORTED_CLAIM,UNSUPPORTED_FIGURE", ReviewReasonFormatter.of(result))
    }

    @Test
    fun `falls back to low grounding without content violations`() {
        val result = resultWith()

        assertEquals(ReviewReasonFormatter.LOW_GROUNDING, ReviewReasonFormatter.of(result))
        assertEquals("LOW_GROUNDING", ReviewReasonFormatter.of(result))
    }

    @Test
    fun `never returns an empty reason`() {
        assertTrue(ReviewReasonFormatter.of(resultWith()).isNotEmpty())
    }

    private fun resultWith(
        vararg violations: SummaryGuardrailViolation,
    ): SummaryGuardrailResult = SummaryGuardrailResult(
        decision = GuardrailDecision.NEEDS_REVIEW,
        contractViolations = emptyList(),
        guardrailViolations = violations.toList(),
        groundingScore = 0.6,
    )
}
