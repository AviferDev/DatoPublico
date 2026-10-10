package es.aviferdev.datopublico.backend.rag.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests de gate (sin red) de [SummaryOutputParser]: JSON plano, *fences* de
 * Markdown, salida vacía y JSON inválido o incompleto (escenarios 2 y 6 del spec).
 */
class SummaryOutputParserTest {

    private val parser = SummaryOutputParser()

    @Test
    fun `parses a plain json output`() {
        val parsed = parser.parse(PLAIN_JSON)

        assertEquals("Cambia el plazo", parsed.queCambia)
        assertEquals("A la ciudadanía", parsed.aQuienAfecta)
        assertEquals(listOf("30 días"), parsed.cifrasClave)
        assertNull(parsed.plazo)
    }

    @Test
    fun `parses a json wrapped in markdown fences`() {
        val parsed = parser.parse("```json\n$PLAIN_JSON\n```")

        assertEquals("Cambia el plazo", parsed.queCambia)
        assertEquals(listOf("30 días"), parsed.cifrasClave)
    }

    @Test
    fun `parses a json wrapped in bare fences`() {
        val parsed = parser.parse("```\n$PLAIN_JSON\n```")

        assertEquals("A la ciudadanía", parsed.aQuienAfecta)
    }

    @Test
    fun `defaults the key figures and the deadline when absent`() {
        val parsed = parser.parse("""{"queCambia":"a","aQuienAfecta":"b"}""")

        assertEquals(emptyList(), parsed.cifrasClave)
        assertNull(parsed.plazo)
    }

    @Test
    fun `an empty output throws SummaryGenerationException`() {
        assertFailsWith<SummaryGenerationException> { parser.parse("   ") }
    }

    @Test
    fun `an invalid json throws SummaryGenerationException`() {
        assertFailsWith<SummaryGenerationException> { parser.parse("no es json") }
    }

    @Test
    fun `a json missing a required field throws SummaryGenerationException`() {
        assertFailsWith<SummaryGenerationException> { parser.parse("""{"queCambia":"solo esto"}""") }
    }

    private companion object {
        const val PLAIN_JSON =
            """{"queCambia":"Cambia el plazo","aQuienAfecta":"A la ciudadanía","cifrasClave":["30 días"]}"""
    }
}
