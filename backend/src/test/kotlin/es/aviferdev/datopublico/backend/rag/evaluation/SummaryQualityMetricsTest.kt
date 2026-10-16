package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.rag.summary.SummaryGenerationDto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests de gate (puros, sin red ni BD) de [SummaryQualityMetrics] (escenario 1 del
 * spec): F1 léxico por campo y *recall* de cifras con sus casos límite (sin
 * solape, solape total, multiconjunto, referencia sin términos, tokens vacíos y
 * referencia sin cifras).
 */
class SummaryQualityMetricsTest {

    @Test
    fun `an identical summary scores the maximum F1`() {
        val text = "Se convocan 2.000 ayudas para practicas formativas en empresas."

        assertEquals(1.0, SummaryQualityMetrics.tokenF1(text, text), TOLERANCE)
    }

    @Test
    fun `summaries without shared terms score zero`() {
        val hypothesis = "Convocatoria de becas para estudiantes"
        val reference = "Regimen sancionador de trafico y seguridad vial"

        assertEquals(0.0, SummaryQualityMetrics.tokenF1(hypothesis, reference), TOLERANCE)
    }

    @Test
    fun `partial overlap uses the harmonic mean of precision and recall`() {
        val f1 = SummaryQualityMetrics.tokenF1(
            hypothesis = "becas ayudas estudiantes",
            reference = "becas ayudas empresas",
        )

        assertEquals(2.0 / 3.0, f1, TOLERANCE)
    }

    @Test
    fun `repeated terms are compared as a multiset`() {
        val f1 = SummaryQualityMetrics.tokenF1(
            hypothesis = "becas becas ayudas",
            reference = "becas ayudas",
        )

        assertEquals(0.8, f1, TOLERANCE)
    }

    @Test
    fun `empty token lists score zero`() {
        assertEquals(0.0, SummaryQualityMetrics.tokenF1("", ""), TOLERANCE)
        assertEquals(0.0, SummaryQualityMetrics.tokenF1("de la en", "a el"), TOLERANCE)
    }

    @Test
    fun `a reference without content terms scores zero`() {
        assertEquals(0.0, SummaryQualityMetrics.tokenF1("convocatoria", "de la en"), TOLERANCE)
    }

    @Test
    fun `figures of a summary come from all comparable fields`() {
        val summary = summary(
            whatChanges = "Se convocan 2.000 ayudas",
            whoIsAffected = "Estudiantes de master",
            keyFigures = listOf("6 meses"),
        )

        assertEquals(listOf("2", "000", "6"), SummaryQualityMetrics.figuresOf(summary))
    }

    @Test
    fun `figures recall is the fraction of required figures present`() {
        val summary = summary(
            whatChanges = "Se convocan 2.000 ayudas",
            whoIsAffected = "Estudiantes de master",
            keyFigures = listOf("6 meses"),
        )

        assertEquals(1.0, SummaryQualityMetrics.figuresRecall(summary, listOf("2", "6")), TOLERANCE)
        assertEquals(2.0 / 3.0, SummaryQualityMetrics.figuresRecall(summary, listOf("2", "000", "12")), TOLERANCE)
        assertEquals(0.0, SummaryQualityMetrics.figuresRecall(summary, listOf("9")), TOLERANCE)
    }

    @Test
    fun `figures recall ignores duplicated reference figures`() {
        val summary = summary(whatChanges = "Se convocan 2.000 ayudas")

        assertEquals(1.0, SummaryQualityMetrics.figuresRecall(summary, listOf("2", "2", "000")), TOLERANCE)
    }

    @Test
    fun `a reference without figures is vacuously satisfied`() {
        val summary = summary(whatChanges = "Convocatoria sin cifras")

        assertEquals(1.0, SummaryQualityMetrics.figuresRecall(summary, emptyList()), TOLERANCE)
    }

    /** Resumen de prueba con la forma del DTO de generación. */
    private fun summary(
        whatChanges: String = "Que cambia",
        whoIsAffected: String = "A quien afecta",
        keyFigures: List<String> = emptyList(),
    ): SummaryGenerationDto = SummaryGenerationDto(
        whatChanges = whatChanges,
        whoIsAffected = whoIsAffected,
        keyFigures = keyFigures,
    )

    private companion object {
        /** Tolerancia de comparación de `Double`. */
        const val TOLERANCE = 1e-9
    }
}
