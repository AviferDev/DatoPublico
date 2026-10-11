package es.aviferdev.datopublico.backend.rag.guardrails

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests de gate (sin red ni BD) de [SummaryTextNormalizer]: minúsculas y acentos,
 * palabras vacías, cifras y oraciones.
 */
class SummaryTextNormalizerTest {

    @Test
    fun `tokens lowercases removes accents and splits non alphanumeric`() {
        val result = SummaryTextNormalizer.tokens("Artículo 1: ¡Añádase el «plazo»!")

        assertEquals(listOf("articulo", "1", "anadase", "el", "plazo"), result)
    }

    @Test
    fun `tokens collapse whitespace and return empty for blank text`() {
        val result = SummaryTextNormalizer.tokens("   \u00a0  ")

        assertEquals(emptyList(), result)
    }

    @Test
    fun `contentTokens drops stopwords and short tokens`() {
        val result = SummaryTextNormalizer.contentTokens("El plazo de la solicitud es de 30 días")

        assertEquals(listOf("plazo", "solicitud", "30", "dias"), result)
    }

    @Test
    fun `contentTokens keeps short numeric tokens`() {
        val result = SummaryTextNormalizer.contentTokens("de 30")

        assertEquals(listOf("30"), result)
    }

    @Test
    fun `figures returns only tokens with a digit`() {
        val result = SummaryTextNormalizer.figures("cuantía de 1000 euros y 30 días")

        assertEquals(listOf("1000", "30"), result)
    }

    @Test
    fun `sentences splits by period semicolon and newline`() {
        val result = SummaryTextNormalizer.sentences("Primera. Segunda; Tercera\nCuarta.  ")

        assertEquals(listOf("Primera", "Segunda", "Tercera", "Cuarta"), result)
    }
}
