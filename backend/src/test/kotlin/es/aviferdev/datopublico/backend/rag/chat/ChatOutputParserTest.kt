package es.aviferdev.datopublico.backend.rag.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests de gate (sin red) de [ChatOutputParser]: JSON plano, *fences* de Markdown,
 * salida vacía, JSON inválido, campo ausente y mapeo del estado con fail-fast
 * (escenario 7 del spec).
 */
class ChatOutputParserTest {

    private val parser = ChatOutputParser()

    @Test
    fun `parses a plain json output`() {
        val parsed = parser.parse(PLAIN_JSON)

        assertEquals("respondida", parsed.status)
        assertEquals("El plazo es de 30 días", parsed.answer)
        assertEquals(listOf(0, 1), parsed.citations)
    }

    @Test
    fun `parses a json wrapped in markdown fences`() {
        val parsed = parser.parse("```json\n$PLAIN_JSON\n```")

        assertEquals(listOf(0, 1), parsed.citations)
    }

    @Test
    fun `defaults the citations when the key is absent`() {
        val parsed = parser.parse("""{"estado":"sin_fundamento","respuesta":"No consta"}""")

        assertEquals(emptyList(), parsed.citations)
    }

    @Test
    fun `an empty output throws ChatGenerationException`() {
        assertFailsWith<ChatGenerationException> { parser.parse("   ") }
    }

    @Test
    fun `an invalid json throws ChatGenerationException`() {
        assertFailsWith<ChatGenerationException> { parser.parse("no es json") }
    }

    @Test
    fun `a json missing a required field throws ChatGenerationException`() {
        assertFailsWith<ChatGenerationException> { parser.parse("""{"estado":"respondida"}""") }
    }

    @Test
    fun `maps the three known status tokens`() {
        assertEquals(ChatStatus.ANSWERED, parser.statusOf("respondida"))
        assertEquals(ChatStatus.REFUTED, parser.statusOf("refutada"))
        assertEquals(ChatStatus.UNFOUNDED, parser.statusOf("sin_fundamento"))
    }

    @Test
    fun `an unknown status token throws ChatGenerationException`() {
        assertFailsWith<ChatGenerationException> { parser.statusOf("inventado") }
    }

    private companion object {
        const val PLAIN_JSON =
            """{"estado":"respondida","respuesta":"El plazo es de 30 días","citas":[0,1]}"""
    }
}
