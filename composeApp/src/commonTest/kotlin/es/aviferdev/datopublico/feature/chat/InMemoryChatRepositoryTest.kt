package es.aviferdev.datopublico.feature.chat

import es.aviferdev.datopublico.feature.chat.data.InMemoryChatMode
import es.aviferdev.datopublico.feature.chat.data.InMemoryChatRepository
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests del repositorio **en memoria** del chat: la clasificación determinista
 * (answered/refuted/unfounded) por el texto de la consulta y el modo
 * `UNAVAILABLE`.
 *
 * Corren en `:composeApp:jvmTest` sin red ni base de datos.
 */
class InMemoryChatRepositoryTest {

    @Test
    fun corpusQuestionIsAnsweredWithCitations() = runTest {
        val result = repository().ask("¿Cuál es el plazo de la beca?")

        val success = assertIs<ChatLoadResult.Success>(result)
        assertEquals(ChatStatus.ANSWERED, success.answer.status)
        assertTrue(success.answer.citations.isNotEmpty())
    }

    @Test
    fun refutationMarkerIsRefutedWithCitations() = runTest {
        val result = repository().ask("Es falso que la beca exija titulación")

        val success = assertIs<ChatLoadResult.Success>(result)
        assertEquals(ChatStatus.REFUTED, success.answer.status)
        assertTrue(success.answer.citations.isNotEmpty())
    }

    @Test
    fun unknownQuestionIsUnfoundedWithoutCitations() = runTest {
        val result = repository().ask("¿Cuánto cuesta el metro cuadrado en Marte?")

        val success = assertIs<ChatLoadResult.Success>(result)
        assertEquals(ChatStatus.UNFOUNDED, success.answer.status)
        assertTrue(success.answer.citations.isEmpty())
    }

    @Test
    fun classificationIgnoresCaseAndPrefersRefutation() = runTest {
        val result = repository().ask("REFUTADA: la norma no dice eso")

        val success = assertIs<ChatLoadResult.Success>(result)
        assertEquals(ChatStatus.REFUTED, success.answer.status)
    }

    @Test
    fun unavailableModeReturnsFailure() = runTest {
        val result = repository(mode = InMemoryChatMode.UNAVAILABLE).ask("plazo")

        assertEquals(ChatLoadResult.Failure, result)
    }

    /** Repositorio en memoria en el modo indicado, sin latencia. */
    private fun repository(mode: InMemoryChatMode = InMemoryChatMode.SAMPLE): InMemoryChatRepository =
        InMemoryChatRepository(mode = mode, latencyMillis = 0)
}
