package es.aviferdev.datopublico.feature.chat

import es.aviferdev.datopublico.feature.chat.data.ChatRepository
import es.aviferdev.datopublico.feature.chat.data.InMemoryChatRepository
import es.aviferdev.datopublico.feature.chat.domain.AskChatQuestion
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests del caso de uso [AskChatQuestion]: la guarda de consulta en blanco o
 * demasiado larga (no llama al repositorio) y el paso por el puerto con una
 * consulta válida.
 *
 * Corren en `:composeApp:jvmTest` sin red ni base de datos.
 */
class AskChatQuestionTest {

    @Test
    fun blankQueryFailsWithoutCallingTheRepository() = runTest {
        val repository = RecordingChatRepository()

        val result = AskChatQuestion(repository)("   ")

        assertEquals(ChatLoadResult.Failure, result)
        assertEquals(0, repository.calls)
    }

    @Test
    fun tooLongQueryFailsWithoutCallingTheRepository() = runTest {
        val repository = RecordingChatRepository()
        val query = "a".repeat(AskChatQuestion.MAX_QUERY_LENGTH + 1)

        val result = AskChatQuestion(repository)(query)

        assertEquals(ChatLoadResult.Failure, result)
        assertEquals(0, repository.calls)
    }

    @Test
    fun validQueryDelegatesToTheRepository() = runTest {
        val repository = RecordingChatRepository()

        val result = AskChatQuestion(repository)("plazo")

        assertIs<ChatLoadResult.Success>(result)
        assertEquals(1, repository.calls)
    }

    @Test
    fun maximumLengthQueryIsAccepted() = runTest {
        val repository = RecordingChatRepository()
        val query = "a".repeat(AskChatQuestion.MAX_QUERY_LENGTH)

        AskChatQuestion(repository)(query)

        assertEquals(1, repository.calls)
    }
}

/** Repositorio doble que cuenta las consultas y delega en el de ejemplo. */
private class RecordingChatRepository : ChatRepository {
    private val delegate = InMemoryChatRepository(latencyMillis = 0)

    /** Número de consultas recibidas. */
    var calls = 0

    override suspend fun ask(query: String): ChatLoadResult {
        calls += 1
        return delegate.ask(query)
    }
}
