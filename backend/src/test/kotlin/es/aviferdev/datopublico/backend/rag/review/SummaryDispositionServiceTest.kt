package es.aviferdev.datopublico.backend.rag.review

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.ReviewQueueEntity
import es.aviferdev.datopublico.backend.persistence.ReviewQueueRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.persistence.SummaryRepository
import es.aviferdev.datopublico.backend.rag.guardrails.GuardrailDecision
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailResult
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailViolation
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.ResumenDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests de gate (sin red ni BD) de [SummaryDispositionService] con **dobles
 * deterministas** en memoria de `SummaryRepository`/`ReviewQueueRepository`
 * (escenarios 1–4 del spec): publicar sin encolar, encolar sin publicar,
 * descartar sin publicar ni encolar, encolado idempotente y el aviso de IA.
 */
class SummaryDispositionServiceTest {

    private val summaries = InMemorySummaryRepository()
    private val reviewQueue = InMemoryReviewQueueRepository()
    private val service = SummaryDispositionService(summaries, reviewQueue)

    @Test
    fun `a low confidence summary is queued and not published`() {
        val outcome = service.dispose(
            publication(),
            summary(),
            result(GuardrailDecision.NEEDS_REVIEW, listOf(SummaryGuardrailViolation.UNSUPPORTED_CLAIM)),
        )

        val persisted = summaries.findByPublication(PUBLICATION_ID)
        assertEquals(SummaryState.IN_REVIEW.persistedValue, persisted?.status)
        assertEquals(0.6, persisted?.confidence)
        assertIs<SummaryReviewOutcome.Queued>(outcome)
        val pending = reviewQueue.listPending()
        assertEquals(1, pending.size)
        assertEquals("UNSUPPORTED_CLAIM", pending.first().reason)
    }

    @Test
    fun `a publishable summary is published without queueing`() {
        val outcome = service.dispose(
            publication(),
            summary(),
            result(GuardrailDecision.PUBLISHABLE, groundingScore = 0.95),
        )

        val persisted = summaries.findByPublication(PUBLICATION_ID)
        assertEquals(SummaryState.PUBLISHED.persistedValue, persisted?.status)
        assertEquals(0.95, persisted?.confidence)
        assertEquals(true, persisted?.aiNotice)
        assertIs<SummaryReviewOutcome.Published>(outcome)
        assertEquals(0, reviewQueue.listPending().size)
    }

    @Test
    fun `a discarded summary is neither published nor queued`() {
        val outcome = service.dispose(
            publication(),
            summary(),
            result(GuardrailDecision.DISCARDED, groundingScore = 0.2),
        )

        val persisted = summaries.findByPublication(PUBLICATION_ID)
        assertEquals(SummaryState.DISCARDED.persistedValue, persisted?.status)
        assertIs<SummaryReviewOutcome.Discarded>(outcome)
        assertEquals(0, reviewQueue.listPending().size)
    }

    @Test
    fun `queueing the same summary twice keeps a single pending entry`() {
        service.dispose(
            publication(),
            summary(),
            result(GuardrailDecision.NEEDS_REVIEW, listOf(SummaryGuardrailViolation.UNSUPPORTED_CLAIM)),
        )
        service.dispose(
            publication(),
            summary(),
            result(GuardrailDecision.NEEDS_REVIEW, listOf(SummaryGuardrailViolation.UNSUPPORTED_FIGURE)),
        )

        val pending = reviewQueue.listPending()
        assertEquals(1, pending.size)
        assertEquals("UNSUPPORTED_FIGURE", pending.first().reason)
    }

    @Test
    fun `a summary without the ai notice fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            service.dispose(
                publication(),
                summary().copy(avisoIA = false),
                result(GuardrailDecision.PUBLISHABLE, groundingScore = 0.95),
            )
        }
        assertNull(summaries.findByPublication(PUBLICATION_ID))
    }

    private fun result(
        decision: GuardrailDecision,
        violations: List<SummaryGuardrailViolation> = emptyList(),
        groundingScore: Double = 0.6,
    ): SummaryGuardrailResult = SummaryGuardrailResult(
        decision = decision,
        contractViolations = emptyList(),
        guardrailViolations = violations,
        groundingScore = groundingScore,
    )

    private fun publication(): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        titulo = "Real Decreto de prueba",
        fechaPublicacion = DATE,
        organismo = "MINISTERIO DE PRUEBA",
        seccion = SeccionBoeDto.I,
        epigrafe = "Disposiciones generales",
        texto = null,
        urlOficial = URL,
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        categoria = CategoriaDto.NORMAS_Y_LEGISLACION,
    )

    private fun summary(): ResumenDto = ResumenDto(
        queCambia = "Se aumenta el plazo de solicitud",
        aQuienAfecta = "A las personas beneficiarias",
        cifrasClave = listOf("1000 euros"),
        fuenteOficial = URL,
        avisoIA = true,
    )

    /** Doble en memoria de [SummaryRepository] con `id` autoincremental por publicación. */
    private class InMemorySummaryRepository : SummaryRepository {
        private var nextId = 1L
        private val byPublication = mutableMapOf<String, SummaryEntity>()

        override fun save(summary: SummaryEntity): SummaryEntity {
            val id = byPublication[summary.publicationId]?.id ?: nextId++
            val persisted = summary.copy(id = id)
            byPublication[summary.publicationId] = persisted
            return persisted
        }

        override fun findByPublication(publicationId: String): SummaryEntity? = byPublication[publicationId]
    }

    /** Doble en memoria de [ReviewQueueRepository] con *upsert* idempotente pendiente. */
    private class InMemoryReviewQueueRepository : ReviewQueueRepository {
        private var nextId = 1L
        private val entries = mutableListOf<ReviewQueueEntity>()

        override fun save(entry: ReviewQueueEntity): ReviewQueueEntity {
            val existing = entries.firstOrNull { row -> row.summaryId == entry.summaryId && row.status == PENDING }
            val saved = if (existing == null) entry.copy(id = nextId++) else existing.copy(reason = entry.reason)
            if (existing == null) entries.add(saved) else entries[entries.indexOf(existing)] = saved
            return saved
        }

        override fun listBySummary(summaryId: Long): List<ReviewQueueEntity> =
            entries.filter { row -> row.summaryId == summaryId }

        override fun listPending(): List<ReviewQueueEntity> =
            entries.filter { row -> row.status == PENDING }

        private companion object {
            const val PENDING = "pendiente"
        }
    }

    private companion object {
        const val DATE = "2026-10-11"
        const val PUBLICATION_ID = "BOE-A-2026-1"
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"
    }
}
