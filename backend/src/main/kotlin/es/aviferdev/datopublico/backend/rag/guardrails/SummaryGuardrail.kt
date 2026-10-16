package es.aviferdev.datopublico.backend.rag.guardrails

import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.SummaryDto
import es.aviferdev.datopublico.validation.CitizenSummaryValidator

/**
 * Servicio de **guardrails anti-alucinación y trazabilidad** del resumen ciudadano
 * (FT00022).
 *
 * Se ejecuta **después** de generar (FT00020) y **antes** de publicar: combina la
 * validación de **contrato** (`CitizenSummaryValidator`, FT00019) con la
 * verificación de **fuente** ([OfficialSourceValidator] + el puerto opcional
 * [SourceReachability]) y de **grounding** ([SummaryGroundingChecker]) contra el
 * contexto recuperado, y devuelve una **decisión tipada**
 * ([GuardrailDecision]) con una **puntuación de grounding**.
 *
 * Es una **librería sin wiring** (no se construye en `Application.kt` ni en el
 * job) y **no** persiste nada: no escribe en `resumen` ni en `cola_revision` (eso
 * es FT00023/FT00026). **No** conoce Ktor salvo a través del puerto de
 * alcanzabilidad, que se inyecta de forma opcional (sin él solo se valida la
 * estructura de la fuente).
 *
 * @param reachability puerto opcional de alcanzabilidad; `null` valida solo la
 *   estructura de la fuente (el gate usa un doble determinista).
 */
class SummaryGuardrail(private val reachability: SourceReachability? = null) {

    /**
     * Evalúa los guardrails de [summary] contra [context] para [publication].
     *
     * @param publication publicación de origen; debe tener `categoria` y su
     *   `urlOficial` es la fuente esperada.
     * @param summary resumen ciudadano generado (con la fuente ya inyectada).
     * @param context fragmentos recuperados que sustentan la generación.
     * @return la decisión, las violaciones de contrato y de contenido y la
     *   puntuación de grounding.
     * @throws IllegalArgumentException si [publication] no tiene categoría
     *   (fail-fast, igual que el generador).
     */
    suspend fun evaluate(
        publication: Publication,
        summary: SummaryDto,
        context: List<FragmentEntity>,
    ): SummaryGuardrailResult {
        val category = requireCategory(publication)
        val sourceValid = OfficialSourceValidator.isOfficial(summary.officialSource, publication.officialUrl)
        val contract = CitizenSummaryValidator.validate(summary, category)
        val grounding = SummaryGroundingChecker.check(summary, context)
        val reachable = reachable(sourceValid, summary.officialSource)
        val violations = violationsOf(sourceValid, context, reachable, grounding)
        val decision = decide(contract.isValid, sourceValid, reachable, grounding)
        return SummaryGuardrailResult(decision, contract.violations, violations, grounding.score)
    }

    /** Categoría obligatoria de la publicación (fail-fast si falta). */
    private fun requireCategory(publication: Publication): CategoryDto =
        publication.category
            ?: throw IllegalArgumentException(
                "La publicación ${publication.id} no tiene categoría; no se puede evaluar el guardrail."
            )

    /** Comprueba la alcanzabilidad solo si hay puerto y la fuente es estructuralmente válida. */
    private suspend fun reachable(sourceValid: Boolean, source: String): Boolean {
        val port = reachability
        return if (port != null && sourceValid) port.isReachable(source) else true
    }

    /** Acumula las violaciones de fuente y grounding en el orden documentado. */
    private fun violationsOf(
        sourceValid: Boolean,
        context: List<FragmentEntity>,
        reachable: Boolean,
        grounding: GroundingAssessment,
    ): List<SummaryGuardrailViolation> = buildList {
        if (!sourceValid) add(SummaryGuardrailViolation.SOURCE_NOT_OFFICIAL)
        if (context.isEmpty()) add(SummaryGuardrailViolation.NO_CONTEXT)
        if (!reachable) add(SummaryGuardrailViolation.SOURCE_NOT_REACHABLE)
        addAll(grounding.violations)
    }

    /**
     * Decide el desenlace: contrato roto, fuente inválida o no alcanzable descartan
     * directamente; si no, el grounding decide entre descartar, revisar y publicar.
     *
     * Invariante de trazabilidad: una afirmación o cifra no sustentada por el
     * contexto **nunca** es publicable. Como el `score` es una fracción, un caso
     * límite (p. ej. 4 de 5 afirmaciones sustentadas = 0.8) podría alcanzar
     * [REVIEW_THRESHOLD] con violaciones presentes; por eso, además del umbral, se
     * exige que [GroundingAssessment.violations] esté vacía para publicar. Si hay
     * alguna violación de grounding, el resumen va a revisión
     * ([GuardrailDecision.NEEDS_REVIEW]).
     */
    private fun decide(
        contractValid: Boolean,
        sourceValid: Boolean,
        reachable: Boolean,
        grounding: GroundingAssessment,
    ): GuardrailDecision = when {
        !contractValid || !sourceValid || !reachable -> GuardrailDecision.DISCARDED
        grounding.score < DISCARD_THRESHOLD -> GuardrailDecision.DISCARDED
        grounding.score < REVIEW_THRESHOLD || grounding.violations.isNotEmpty() ->
            GuardrailDecision.NEEDS_REVIEW
        else -> GuardrailDecision.PUBLISHABLE
    }

    companion object {
        /** Por debajo de este grounding el resumen se **descarta**. */
        const val DISCARD_THRESHOLD: Double = 0.5

        /** Entre [DISCARD_THRESHOLD] y este umbral el resumen va a **revisión**. */
        const val REVIEW_THRESHOLD: Double = 0.8
    }
}
