package es.aviferdev.datopublico.backend.rag.review

import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.persistence.ReviewQueueEntity
import es.aviferdev.datopublico.backend.persistence.ReviewQueueRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.persistence.SummaryRepository
import es.aviferdev.datopublico.backend.rag.guardrails.GuardrailDecision
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailResult
import es.aviferdev.datopublico.model.SummaryDto

/**
 * Servicio de **disposición del resumen ciudadano tras los guardrails** (FT00023).
 *
 * Consume la decisión tipada de FT00022 ([GuardrailDecision]) y persiste el
 * resumen con su **estado** y su **confianza** (`groundingScore`) en la tabla
 * `resumen`; si la decisión es `NEEDS_REVIEW`, además lo encola en `cola_revision`
 * **sin publicarlo**. **No** define ningún umbral nuevo: los umbrales y el
 * grounding son de FT00022.
 *
 * Es una **librería sin wiring** (no se construye en `Application.kt`/job):
 * compone repositorios inyectados por constructor. Invariantes:
 *
 * - **no-bloqueo**: un `NEEDS_REVIEW` **nunca** publica y encolar es un efecto
 *   local; el servicio **no** lanza por baja confianza.
 * - **aviso de IA**: [dispose] exige `summary.avisoIA` (fail-fast si es `false`);
 *   el resumen de FT00020 siempre lo trae `true`. La parte **visible** es FT00033.
 *
 * @param summaries repositorio del resumen (tabla `resumen`).
 * @param reviewQueue repositorio de la cola de revisión (tabla `cola_revision`).
 */
class SummaryDispositionService(
    private val summaries: SummaryRepository,
    private val reviewQueue: ReviewQueueRepository,
) {

    /**
     * Dispone [summary] según la [guardrail] evaluada para [publication].
     *
     * @param publication publicación de origen (aporta su `id` persistido).
     * @param summary resumen ciudadano ya generado y evaluado.
     * @param guardrail resultado de los guardrails (decisión y grounding).
     * @return la disposición aplicada con los `id` persistidos.
     * @throws IllegalArgumentException si [summary] no lleva el aviso de IA
     *   exigido (`avisoIA = false`).
     */
    fun dispose(
        publication: Publicacion,
        summary: SummaryDto,
        guardrail: SummaryGuardrailResult,
    ): SummaryReviewOutcome {
        require(summary.aiNotice) {
            "El resumen de ${publication.id} no lleva el aviso de IA obligatorio."
        }
        return when (guardrail.decision) {
            GuardrailDecision.PUBLISHABLE -> publish(publication, summary, guardrail)
            GuardrailDecision.NEEDS_REVIEW -> queue(publication, summary, guardrail)
            GuardrailDecision.DISCARDED -> discard(publication, summary, guardrail)
        }
    }

    /** Publica el resumen: `estado = publicado`, sin crear entrada en la cola. */
    private fun publish(
        publication: Publicacion,
        summary: SummaryDto,
        guardrail: SummaryGuardrailResult,
    ): SummaryReviewOutcome {
        val persisted = persist(publication, summary, guardrail, SummaryState.PUBLISHED)
        return SummaryReviewOutcome.Published(checkNotNull(persisted.id))
    }

    /** Encola el resumen de baja confianza: `en_cola_revision` + entrada pendiente. */
    private fun queue(
        publication: Publicacion,
        summary: SummaryDto,
        guardrail: SummaryGuardrailResult,
    ): SummaryReviewOutcome {
        val persisted = persist(publication, summary, guardrail, SummaryState.IN_REVIEW)
        val summaryId = checkNotNull(persisted.id)
        val entry = reviewQueue.save(
            ReviewQueueEntity(summaryId = summaryId, reason = ReviewReasonFormatter.of(guardrail)),
        )
        return SummaryReviewOutcome.Queued(summaryId, checkNotNull(entry.id))
    }

    /** Descarta el resumen: `estado = descartado`, ni publicación ni cola. */
    private fun discard(
        publication: Publicacion,
        summary: SummaryDto,
        guardrail: SummaryGuardrailResult,
    ): SummaryReviewOutcome {
        val persisted = persist(publication, summary, guardrail, SummaryState.DISCARDED)
        return SummaryReviewOutcome.Discarded(checkNotNull(persisted.id))
    }

    /**
     * Persiste el resumen una sola vez para las tres decisiones (sin duplicar
     * el mapeo) con el [state] y la confianza del guardrail.
     */
    private fun persist(
        publication: Publicacion,
        summary: SummaryDto,
        guardrail: SummaryGuardrailResult,
        state: SummaryState,
    ): SummaryEntity = summaries.save(
        SummaryEntity(
            publicationId = publication.id,
            whatChanges = summary.whatChanges,
            whomItAffects = summary.whoIsAffected,
            keyFigures = summary.keyFigures,
            officialSource = summary.officialSource,
            aiNotice = summary.aiNotice,
            status = state.persistedValue,
            confidence = guardrail.groundingScore,
        ),
    )
}
