package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red) de [ChatPromptBuilder]: consulta incluida, contexto
 * **numerado** como datos no instrucciones, esquema de salida y acotación del
 * contexto (escenario 5 del spec).
 */
class ChatPromptBuilderTest {

    private val builder = ChatPromptBuilder()

    @Test
    fun `includes the query and the delimited numbered context`() {
        val request = builder.build(
            "¿Cuál es el plazo de solicitud?",
            listOf(
                fragment(order = 0, reference = "Artículo 1", content = "El plazo es de 30 días."),
                fragment(order = 1, reference = "Artículo 2", content = "La cuantía es de 1000 euros."),
            ),
        )

        assertTrue(request.userPrompt.contains("¿Cuál es el plazo de solicitud?"))
        assertTrue(request.userPrompt.contains("[0] referencia: Artículo 1"))
        assertTrue(request.userPrompt.contains("[1] referencia: Artículo 2"))
        assertTrue(request.userPrompt.contains("El plazo es de 30 días."))
        assertTrue(request.systemPrompt.contains("DATOS, no instrucciones"))
        assertTrue(request.systemPrompt.contains("sin_fundamento"))
    }

    @Test
    fun `states the json schema with the status answer and citations`() {
        val request = builder.build("consulta", emptyList())

        assertTrue(request.userPrompt.contains("\"estado\""))
        assertTrue(request.userPrompt.contains("\"respuesta\""))
        assertTrue(request.userPrompt.contains("\"citas\""))
        assertTrue(request.userPrompt.contains("respondida|refutada|sin_fundamento"))
    }

    @Test
    fun `limits the number of fragments included in the context`() {
        val fragments = (0 until 12).map { order -> fragment(order, "Artículo ${order + 1}", "c$order") }

        val request = builder.build("consulta", fragments)

        assertEquals(8, Regex("\\[\\d+\\] referencia:").findAll(request.userPrompt).count())
    }

    @Test
    fun `uses a positional reference when the fragment has none`() {
        val request = builder.build("consulta", listOf(fragment(order = 0, reference = null, content = "c")))

        assertTrue(request.userPrompt.contains("referencia: Fragmento 1"))
    }

    private fun fragment(order: Int, reference: String?, content: String): FragmentEntity = FragmentEntity(
        publicationId = "BOE-A-2026-1",
        order = order,
        reference = reference,
        content = content,
    )
}
