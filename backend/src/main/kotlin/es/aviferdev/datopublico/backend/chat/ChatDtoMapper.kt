package es.aviferdev.datopublico.backend.chat

import es.aviferdev.datopublico.backend.rag.chat.ChatCitation
import es.aviferdev.datopublico.backend.rag.chat.ChatResult
import es.aviferdev.datopublico.model.ChatCitationDto
import es.aviferdev.datopublico.model.ChatResponseDto
import es.aviferdev.datopublico.model.ChatStatusDto

/**
 * Mapeo **puro** del resultado interno del chat ([ChatResult], FT00025) al
 * contrato de transporte de `:shared` ([ChatResponseDto], FT00031).
 *
 * Es un `object` sin red, BD, Ktor, reloj ni estado: la misma entrada produce
 * siempre la misma salida. Traduce los tres desenlaces del dominio a
 * [ChatStatusDto] y las citas internas a [ChatCitationDto]; el `usage` del
 * resultado **no** viaja al cliente (el control de coste es interno, FT00030).
 */
object ChatDtoMapper {

    /**
     * Compone la respuesta de transporte con el **eco** de [query] y el resultado
     * [result].
     *
     * @param query consulta original (se devuelve tal cual para el eco).
     * @param result resultado tipado del servicio de chat.
     * @return la respuesta de wire con su estado y sus citas.
     */
    fun toResponseDto(query: String, result: ChatResult): ChatResponseDto = ChatResponseDto(
        query = query,
        status = statusOf(result),
        answer = result.answer,
        citations = result.citations.map { citation -> toCitationDto(citation) },
    )

    /**
     * Traduce una cita interna a su DTO de wire.
     *
     * @param citation cita con la fuente oficial ya inyectada.
     * @return la cita de transporte.
     */
    fun toCitationDto(citation: ChatCitation): ChatCitationDto = ChatCitationDto(
        publicationId = citation.publicationId,
        title = citation.title,
        officialUrl = citation.officialUrl,
    )

    /**
     * Traduce el resultado tipado a su estado de wire (`when` exhaustivo, sin
     * `else if`).
     *
     * @param result resultado del servicio de chat.
     * @return el estado de wire correspondiente.
     */
    fun statusOf(result: ChatResult): ChatStatusDto = when (result) {
        is ChatResult.Answered -> ChatStatusDto.ANSWERED
        is ChatResult.Refuted -> ChatStatusDto.REFUTED
        is ChatResult.Unfounded -> ChatStatusDto.UNFOUNDED
    }
}
