package es.aviferdev.datopublico.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.chat.data.InMemoryChatRepository
import es.aviferdev.datopublico.feature.chat.domain.AskChatQuestion
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Punto de entrada de la **ruta** del chat: recolecta el estado del
 * [ChatViewModel] y delega en [ChatScreen].
 *
 * Es el único composable con dependencia del ViewModel; [ChatScreen] y sus vistas
 * siguen siendo **stateless**. La UI recolecta el estado con
 * `collectAsStateWithLifecycle()` (patrón FT00034).
 *
 * @param onBack acción al pulsar «volver» (atrás de la UI).
 * @param viewModel ViewModel del chat; por defecto se obtiene de la factory
 *   manual (DI por constructor).
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun ChatRoute(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory()),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onRetry = viewModel::onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Previsualización de la ruta con el repositorio de ejemplo. */
@Preview
@Composable
private fun ChatRoutePreview() {
    DatoPublicoTheme {
        ChatRoute(
            onBack = {},
            viewModel = ChatViewModel(AskChatQuestion(InMemoryChatRepository(latencyMillis = 0))),
        )
    }
}
