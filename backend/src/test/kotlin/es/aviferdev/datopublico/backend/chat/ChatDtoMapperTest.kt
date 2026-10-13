package es.aviferdev.datopublico.backend.chat

import es.aviferdev.datopublico.backend.rag.chat.ChatCitation
import es.aviferdev.datopublico.backend.rag.chat.ChatResult
import es.aviferdev.datopublico.model.ChatStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate (puros, sin red ni BD) de [ChatDtoMapper]: traducción de los tres
 * resultados del dominio a su estado de wire, de las citas internas a su DTO y
 * composición de la respuesta con el eco de la consulta.
 */
class ChatDtoMapperTest {

    @Test
    fun `an answered result maps to the answered status with its citations`() {
        val result = ChatResult.Answered(answer = "El plazo es de 30 días.", citations = listOf(citation()))

        val response = ChatDtoMapper.toResponseDto("¿Cuál es el plazo?", result)

        assertEquals("¿Cuál es el plazo?", response.query)
        assertEquals(ChatStatusDto.ANSWERED, response.status)
        assertEquals("El plazo es de 30 días.", response.answer)
        assertEquals(listOf(ChatDtoMapper.toCitationDto(citation())), response.citations)
    }

    @Test
    fun `a refuted result maps to the refuted status`() {
        val result = ChatResult.Refuted(answer = "No es correcto.", citations = listOf(citation()))

        assertEquals(ChatStatusDto.REFUTED, ChatDtoMapper.statusOf(result))
    }

    @Test
    fun `an unfounded result maps to the unfounded status without citations`() {
        val result = ChatResult.Unfounded(answer = "No consta en el BOE")

        val response = ChatDtoMapper.toResponseDto("¿Llueve?", result)

        assertEquals(ChatStatusDto.UNFOUNDED, response.status)
        assertTrue(response.citations.isEmpty())
    }

    @Test
    fun `a citation maps every field to the wire dto`() {
        val dto = ChatDtoMapper.toCitationDto(citation())

        assertEquals("BOE-A-2026-1", dto.publicationId)
        assertEquals("Real Decreto de prueba", dto.title)
        assertEquals(URL, dto.officialUrl)
    }

    private fun citation(): ChatCitation = ChatCitation(
        publicationId = "BOE-A-2026-1",
        title = "Real Decreto de prueba",
        officialUrl = URL,
    )

    private companion object {
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"
    }
}
