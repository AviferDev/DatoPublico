package es.aviferdev.datopublico.feature.chat

import es.aviferdev.datopublico.feature.chat.data.ChatRepository
import es.aviferdev.datopublico.feature.chat.data.InMemoryChatMode
import es.aviferdev.datopublico.feature.chat.data.InMemoryChatRepository
import es.aviferdev.datopublico.feature.chat.data.sampleAnswer
import es.aviferdev.datopublico.feature.chat.domain.AskChatQuestion
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import es.aviferdev.datopublico.feature.chat.ui.ChatResponseState
import es.aviferdev.datopublico.feature.chat.ui.ChatUiState
import es.aviferdev.datopublico.feature.chat.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests del `ChatViewModel`: el paso `Idle → Loading → Content` con los tres
 * estados del dominio, el estado de error con reintento de la **misma** pregunta,
 * la consulta en blanco ignorada y la limpieza del campo al enviar.
 *
 * `viewModelScope` usa el dispatcher `Main`, así que se sustituye por un
 * `StandardTestDispatcher` (y se restaura al terminar). Corren en
 * `:composeApp:jvmTest` con repositorios dobles; no abren sockets ni tocan la
 * base de datos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdleWithEmptyQuery() {
        val viewModel = viewModel()

        assertEquals(ChatUiState(), viewModel.uiState.value)
    }

    @Test
    fun onQueryChangeUpdatesTheField() {
        val viewModel = viewModel()

        viewModel.onQueryChange("plazo")

        assertEquals("plazo", viewModel.uiState.value.query)
    }

    @Test
    fun blankQueryIsIgnored() = runTest(mainDispatcher) {
        val repository = CountingChatRepository(sampleRepository())
        val viewModel = ChatViewModel(AskChatQuestion(repository))

        viewModel.onQueryChange("   ")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(ChatUiState(query = "   "), viewModel.uiState.value)
        assertEquals(0, repository.calls)
    }

    @Test
    fun answeredQuestionEndsInContent() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onQueryChange("plazo de la beca")
        viewModel.onSubmit()
        advanceUntilIdle()

        val content = assertIs<ChatResponseState.Content>(viewModel.uiState.value.response)
        assertEquals(ChatStatus.ANSWERED, content.answer.status)
    }

    @Test
    fun refutedQuestionEndsInRefutedContent() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onQueryChange("es falso que la beca exija titulación")
        viewModel.onSubmit()
        advanceUntilIdle()

        val content = assertIs<ChatResponseState.Content>(viewModel.uiState.value.response)
        assertEquals(ChatStatus.REFUTED, content.answer.status)
    }

    @Test
    fun unfoundedQuestionEndsInUnfoundedContent() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onQueryChange("clima en Marte")
        viewModel.onSubmit()
        advanceUntilIdle()

        val content = assertIs<ChatResponseState.Content>(viewModel.uiState.value.response)
        assertEquals(ChatStatus.UNFOUNDED, content.answer.status)
    }

    @Test
    fun submitPassesThroughLoadingAndClearsTheField() = runTest(mainDispatcher) {
        var observedState: ChatResponseState? = null
        lateinit var viewModel: ChatViewModel
        val repository = object : ChatRepository {
            override suspend fun ask(query: String): ChatLoadResult {
                observedState = viewModel.uiState.value.response
                return ChatLoadResult.Success(sampleAnswer(query))
            }
        }
        viewModel = ChatViewModel(AskChatQuestion(repository))

        viewModel.onQueryChange("plazo")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(ChatResponseState.Loading("plazo"), observedState)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun failureEndsInErrorAndRetryRecoversWithTheSameQuestion() = runTest(mainDispatcher) {
        val repository = FailOnceChatRepository(delegate = sampleRepository())
        val viewModel = ChatViewModel(AskChatQuestion(repository))

        viewModel.onQueryChange("plazo")
        viewModel.onSubmit()
        advanceUntilIdle()
        val error = assertIs<ChatResponseState.Error>(viewModel.uiState.value.response)
        assertEquals("plazo", error.question)

        viewModel.onRetry()
        advanceUntilIdle()

        val content = assertIs<ChatResponseState.Content>(viewModel.uiState.value.response)
        assertEquals("plazo", content.question)
        assertEquals(ChatStatus.ANSWERED, content.answer.status)
    }

    @Test
    fun retryWithoutQuestionDoesNothing() = runTest(mainDispatcher) {
        val repository = CountingChatRepository(sampleRepository())
        val viewModel = ChatViewModel(AskChatQuestion(repository))

        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(0, repository.calls)
    }

    /** ViewModel sobre el repositorio de ejemplo instantáneo. */
    private fun viewModel(): ChatViewModel = ChatViewModel(AskChatQuestion(sampleRepository()))

    /** Repositorio de ejemplo instantáneo (latencia 0). */
    private fun sampleRepository(): ChatRepository =
        InMemoryChatRepository(mode = InMemoryChatMode.SAMPLE, latencyMillis = 0)
}

/** Repositorio doble: falla la primera vez y delega en el real a partir de ahí. */
private class FailOnceChatRepository(private val delegate: ChatRepository) : ChatRepository {
    private var attempts = 0

    override suspend fun ask(query: String): ChatLoadResult {
        attempts += 1
        val result = if (attempts == 1) ChatLoadResult.Failure else delegate.ask(query)
        return result
    }
}

/** Repositorio doble que cuenta cuántas consultas recibió. */
private class CountingChatRepository(private val delegate: ChatRepository) : ChatRepository {
    var calls = 0

    override suspend fun ask(query: String): ChatLoadResult {
        calls += 1
        return delegate.ask(query)
    }
}
