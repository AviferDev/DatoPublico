package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate del **contrato de wire del chat RAG** (FT00031) en `:shared`: los
 * **tokens `@SerialName`** estables del estado, el *round-trip* de la petición y de
 * la respuesta con citas y la **compatibilidad hacia atrás** (un wire sin `citas`
 * decodifica con su default). No usan red ni base de datos.
 */
class ChatContractTest {

    @Test
    fun `chat status tokens are stable snake case`() {
        val tokens = ChatStatusDto.entries.associateWith { status ->
            DatoPublicoJson.encodeToString(ChatStatusDto.serializer(), status)
        }

        assertEquals("\"respondida\"", tokens[ChatStatusDto.ANSWERED])
        assertEquals("\"refutada\"", tokens[ChatStatusDto.REFUTED])
        assertEquals("\"sin_fundamento\"", tokens[ChatStatusDto.UNFOUNDED])
    }

    @Test
    fun `chat request round trips with the consulta key`() {
        val request = ChatRequestDto(query = "¿Cuál es el plazo de solicitud?")

        val json = DatoPublicoJson.encodeToString(ChatRequestDto.serializer(), request)
        val decoded = DatoPublicoJson.decodeFromString(ChatRequestDto.serializer(), json)

        assertEquals(request, decoded)
        assertEquals("""{"consulta":"¿Cuál es el plazo de solicitud?"}""", json)
    }

    @Test
    fun `chat response round trips with citations and no usage`() {
        val response = ChatResponseDto(
            query = "¿Cuál es el plazo de solicitud?",
            status = ChatStatusDto.ANSWERED,
            answer = "El plazo de solicitud es de 30 días hábiles.",
            citations = listOf(
                ChatCitationDto(
                    publicationId = "BOE-A-2026-1",
                    title = "Real Decreto de prueba",
                    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
                )
            ),
        )

        val json = DatoPublicoJson.encodeToString(ChatResponseDto.serializer(), response)
        val decoded = DatoPublicoJson.decodeFromString(ChatResponseDto.serializer(), json)

        assertEquals(response, decoded)
        assertTrue(json.contains("\"estado\":\"respondida\""))
        assertTrue(json.contains("\"citas\""))
        assertTrue(json.contains("\"publicacionId\""))
        assertTrue(json.contains("\"urlOficial\""))
        assertTrue(!json.contains("usage"))
    }

    @Test
    fun `chat response defaults citations to empty`() {
        val decoded = DatoPublicoJson.decodeFromString(
            ChatResponseDto.serializer(),
            """{"consulta":"¿Llueve?","estado":"sin_fundamento","respuesta":"No consta en el BOE"}""",
        )

        assertEquals(ChatStatusDto.UNFOUNDED, decoded.status)
        assertEquals(emptyList(), decoded.citations)
    }
}
