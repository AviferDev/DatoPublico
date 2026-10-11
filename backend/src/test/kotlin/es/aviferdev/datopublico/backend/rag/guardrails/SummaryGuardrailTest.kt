package es.aviferdev.datopublico.backend.rag.guardrails

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.ResumenDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.validation.CitizenSummaryViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate (sin red ni BD) de [SummaryGuardrail] con **dobles deterministas**
 * (escenarios 1–8 del spec): publicable, afirmación fuera del contexto, cifra no
 * sustentada, fuente no oficial/inexistente, fuente no alcanzable, contrato roto,
 * baja confianza y contexto vacío. Incluye el caso límite del umbral de revisión
 * (4 de 5 afirmaciones sustentadas = 0.8) con una violación presente: no debe
 * publicarse.
 */
class SummaryGuardrailTest {

    @Test
    fun `a valid supported summary is publishable`() = runTest {
        val result = guardrailCon(reachable = true).evaluate(publication(), supportedSummary(), context())

        assertEquals(GuardrailDecision.PUBLISHABLE, result.decision)
        assertTrue(result.isPublishable)
        assertTrue(result.groundingScore >= SummaryGuardrail.REVIEW_THRESHOLD)
        assertTrue(result.contractViolations.isEmpty())
        assertTrue(result.guardrailViolations.isEmpty(), result.guardrailViolations.toString())
    }

    @Test
    fun `a claim outside the context is not publishable`() = runTest {
        val summary = supportedSummary()
            .copy(queCambia = "Se aumenta el plazo de solicitud. Se reduce el impuesto especial")

        val result = guardrailCon(reachable = true).evaluate(publication(), summary, context())

        assertEquals(GuardrailDecision.NEEDS_REVIEW, result.decision)
        assertFalse(result.isPublishable)
        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.UNSUPPORTED_CLAIM))
    }

    @Test
    fun `an unsupported figure is not publishable`() = runTest {
        val summary = supportedSummary().copy(cifrasClave = listOf("5000 euros"))

        val result = guardrailCon(reachable = true).evaluate(publication(), summary, context())

        assertFalse(result.isPublishable)
        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.UNSUPPORTED_FIGURE))
    }

    @Test
    fun `an unsupported claim at the review threshold is not publishable`() = runTest {
        val summary = supportedSummary().copy(
            queCambia = "Se aumenta el plazo de solicitud. Se reduce el impuesto especial. A las personas beneficiarias",
        )

        val result = guardrailCon(reachable = true).evaluate(publication(), summary, context())

        assertEquals(SummaryGuardrail.REVIEW_THRESHOLD, result.groundingScore, 1e-9)
        assertEquals(GuardrailDecision.NEEDS_REVIEW, result.decision)
        assertFalse(result.isPublishable)
        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.UNSUPPORTED_CLAIM))
    }

    @Test
    fun `an unsupported figure at the review threshold is not publishable`() = runTest {
        val summary = supportedSummary().copy(
            queCambia = "Se aumenta el plazo de solicitud. Se aumenta el plazo con 5000 euros. A las personas beneficiarias",
        )

        val result = guardrailCon(reachable = true).evaluate(publication(), summary, context())

        assertEquals(SummaryGuardrail.REVIEW_THRESHOLD, result.groundingScore, 1e-9)
        assertEquals(GuardrailDecision.NEEDS_REVIEW, result.decision)
        assertFalse(result.isPublishable)
        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.UNSUPPORTED_FIGURE))
    }

    @Test
    fun `an inexistent or non official source is discarded`() = runTest {
        val invalidSources = listOf(
            " ",
            "http://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
            "https://example.com/diario_boe/txt.php?id=BOE-A-2026-1",
            "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-2",
        )

        for (source in invalidSources) {
            val result = guardrailCon(reachable = true)
                .evaluate(publication(), supportedSummary(source = source), context())

            assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.SOURCE_NOT_OFFICIAL), source)
            assertEquals(GuardrailDecision.DISCARDED, result.decision, source)
        }
    }

    @Test
    fun `a structurally valid but unreachable source is discarded`() = runTest {
        val result = guardrailCon(reachable = false).evaluate(publication(), supportedSummary(), context())

        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.SOURCE_NOT_REACHABLE))
        assertEquals(GuardrailDecision.DISCARDED, result.decision)
    }

    @Test
    fun `a contract violation is discarded`() = runTest {
        val summary = supportedSummary().copy(aQuienAfecta = "")

        val result = guardrailCon(reachable = true).evaluate(publication(), summary, context())

        assertTrue(result.contractViolations.contains(CitizenSummaryViolation.MISSING_WHO_IS_AFFECTED))
        assertEquals(GuardrailDecision.DISCARDED, result.decision)
    }

    @Test
    fun `a low confidence summary goes to review without publishing`() = runTest {
        val summary = supportedSummary()
            .copy(
                queCambia = "Se aumenta el plazo de solicitud. Se reduce el impuesto especial",
                cifrasClave = emptyList(),
            )

        val result = guardrailCon(reachable = true).evaluate(publication(), summary, context())

        assertEquals(GuardrailDecision.NEEDS_REVIEW, result.decision)
        assertFalse(result.isPublishable)
        assertTrue(result.groundingScore >= SummaryGuardrail.DISCARD_THRESHOLD)
        assertTrue(result.groundingScore < SummaryGuardrail.REVIEW_THRESHOLD)
    }

    @Test
    fun `an empty context is discarded with zero grounding`() = runTest {
        val result = guardrailCon(reachable = true).evaluate(publication(), supportedSummary(), emptyList())

        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.NO_CONTEXT))
        assertEquals(0.0, result.groundingScore)
        assertEquals(GuardrailDecision.DISCARDED, result.decision)
    }

    @Test
    fun `the reachability port is not invoked when the source is invalid`() = runTest {
        val port = FakeSourceReachability(reachable = true)

        val result = SummaryGuardrail(port).evaluate(publication(), supportedSummary(source = " "), context())

        assertEquals(0, port.calls)
        assertTrue(result.guardrailViolations.contains(SummaryGuardrailViolation.SOURCE_NOT_OFFICIAL))
    }

    @Test
    fun `a publication without category fails fast`() = runTest {
        val publication = publication().copy(categoria = null)

        assertFailsWith<IllegalArgumentException> {
            guardrailCon(reachable = true).evaluate(publication, supportedSummary(), context())
        }
    }

    private fun guardrailCon(reachable: Boolean): SummaryGuardrail =
        SummaryGuardrail(FakeSourceReachability(reachable))

    private fun publication(): Publicacion = Publicacion(
        id = "BOE-A-2026-1",
        titulo = "Real Decreto de prueba",
        fechaPublicacion = "2026-10-10",
        organismo = "MINISTERIO DE PRUEBA",
        seccion = SeccionBoeDto.I,
        epigrafe = "Disposiciones generales",
        texto = CONTEXT_TEXT,
        urlOficial = URL,
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        categoria = CategoriaDto.NORMAS_Y_LEGISLACION,
    )

    private fun supportedSummary(source: String = URL): ResumenDto = ResumenDto(
        queCambia = "Se aumenta el plazo de solicitud",
        aQuienAfecta = "A las personas beneficiarias",
        cifrasClave = listOf("1000 euros"),
        fuenteOficial = source,
    )

    private fun context(): List<FragmentEntity> = listOf(
        FragmentEntity(
            publicationId = "BOE-A-2026-1",
            order = 0,
            reference = "Artículo 1",
            content = CONTEXT_TEXT,
        )
    )

    /** Doble de [SourceReachability]: devuelve [reachable] y cuenta las llamadas. */
    private class FakeSourceReachability(private val reachable: Boolean) : SourceReachability {
        var calls: Int = 0

        override suspend fun isReachable(url: String): Boolean {
            calls += 1
            return reachable
        }
    }

    private companion object {
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

        const val CONTEXT_TEXT =
            "Se aumenta el plazo de solicitud para las personas beneficiarias con una cuantía de 1000 euros."
    }
}
