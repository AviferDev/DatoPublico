package es.aviferdev.datopublico.backend.rag.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests de gate (sin red ni BD) de [SummaryState] (escenario 5 del spec): cada
 * estado persiste el token del modelo de dominio (`docs/domain-model.md`) y
 * [SummaryState.fromPersisted] invierte el mapeo.
 */
class SummaryStateTest {

    @Test
    fun `each state persists its domain token`() {
        assertEquals("pendiente", SummaryState.PENDING.persistedValue)
        assertEquals("generado", SummaryState.GENERATED.persistedValue)
        assertEquals("validado_por_guardrails", SummaryState.VALIDATED.persistedValue)
        assertEquals("publicado", SummaryState.PUBLISHED.persistedValue)
        assertEquals("en_cola_revision", SummaryState.IN_REVIEW.persistedValue)
        assertEquals("revisado", SummaryState.REVIEWED.persistedValue)
        assertEquals("descartado", SummaryState.DISCARDED.persistedValue)
    }

    @Test
    fun `fromPersisted maps every domain token back to its state`() {
        SummaryState.entries.forEach { state ->
            assertEquals(state, SummaryState.fromPersisted(state.persistedValue))
        }
    }

    @Test
    fun `fromPersisted returns null for an unknown token`() {
        assertNull(SummaryState.fromPersisted("inexistente"))
    }
}
