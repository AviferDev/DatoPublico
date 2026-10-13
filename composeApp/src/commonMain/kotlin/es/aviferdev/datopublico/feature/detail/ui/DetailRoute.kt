package es.aviferdev.datopublico.feature.detail.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.detail.data.InMemoryDetailRepository
import es.aviferdev.datopublico.feature.detail.domain.GetPublicationDetail
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Punto de entrada de la **ruta** del detalle: mantiene el [DetailStateHolder],
 * lanza la carga inicial y conecta el reintento con la corrutina de composición.
 *
 * Es el **contrato de invocación** que conectará la navegación (FT00034): se
 * llamará con el `id` seleccionado en el feed y un [onBack] para volver. Es el
 * único composable con efectos de la feature; [DetailScreen] y sus vistas siguen
 * siendo stateless.
 *
 * @param stateHolder holder local del estado del detalle.
 * @param onBack acción a ejecutar al pulsar «volver».
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun DetailRoute(
    stateHolder: DetailStateHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(stateHolder) { stateHolder.load() }
    DetailScreen(
        state = stateHolder.state,
        onRetry = { scope.launch { stateHolder.load() } },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Previsualización de la ruta con el repositorio de ejemplo. */
@Preview
@Composable
private fun DetailRoutePreview() {
    val stateHolder = DetailStateHolder(
        id = "BOE-A-2026-21002",
        getPublicationDetail = GetPublicationDetail(InMemoryDetailRepository(latencyMillis = 0)),
    )
    DatoPublicoTheme {
        DetailRoute(stateHolder = stateHolder, onBack = {})
    }
}
