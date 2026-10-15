package es.aviferdev.datopublico.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.detail.data.sampleDetails
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_back
import es.aviferdev.datopublico.resources.cd_detail_error
import es.aviferdev.datopublico.resources.cd_not_found
import es.aviferdev.datopublico.resources.detail_error_message
import es.aviferdev.datopublico.resources.detail_error_title
import es.aviferdev.datopublico.resources.detail_loading
import es.aviferdev.datopublico.resources.detail_not_found_message
import es.aviferdev.datopublico.resources.detail_not_found_title
import es.aviferdev.datopublico.resources.detail_retry
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pantalla **stateless** del detalle de una publicación: pinta el estado recibido
 * y delega el reintento y el «volver» en [onRetry]/[onBack]. No hace IO ni conoce
 * el repositorio.
 *
 * El botón de atrás **de la barra superior** lo aporta el chasis global
 * (`AppChrome`), así que aquí no se duplica; sí se conserva la **sub-barra de
 * contenido** del mockup («Volver» + identificador), que comparte [onBack] y es
 * visible en todos los estados para no dejar al usuario atrapado.
 *
 * @param state estado actual de la pantalla.
 * @param onRetry acción a ejecutar al pulsar «Reintentar».
 * @param onBack acción a ejecutar al pulsar «volver».
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ContentSubBar(publicationId = state.publicationIdOrNull(), onBack = onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                DetailUiState.Loading -> LoadingView()
                is DetailUiState.Content -> DetailContent(detail = state.detail)
                DetailUiState.NotFound -> NotFoundView()
                DetailUiState.Error -> ErrorView(onRetry = onRetry)
            }
        }
    }
}

/**
 * Identificador de la publicación si el estado lleva contenido; `null` en el
 * resto de estados (carga, no encontrada y error), donde aún no hay un id.
 *
 * @return el id oficial de la publicación, o `null` si no hay contenido.
 */
private fun DetailUiState.publicationIdOrNull(): String? = when (this) {
    is DetailUiState.Content -> detail.id
    else -> null
}

/**
 * Sub-barra de contenido del mockup: «Volver» a la izquierda (icono + texto) y el
 * identificador de la publicación a la derecha, sobre `surfaceContainerLow`.
 *
 * @param publicationId id oficial de la publicación; `null` si no hay contenido.
 * @param onBack acción a ejecutar al pulsar «Volver».
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ContentSubBar(
    publicationId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.cd_back),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            publicationId?.let { id ->
                Text(
                    text = id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

/** Estado de carga: indicador centrado, sin error ni contenido. */
@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.detail_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** Estado «no encontrada»: icono y mensaje que explican que la publicación no existe. */
@Composable
private fun NotFoundView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(Res.string.cd_not_found),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.detail_not_found_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.detail_not_found_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Estado de error: icono, mensaje y botón «Reintentar». El fallo llega modelado
 * como [DetailUiState.Error], nunca como excepción, así que la app no se cierra.
 *
 * @param onRetry acción de reintento.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun ErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = stringResource(Res.string.cd_detail_error),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.detail_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.detail_error_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(Res.string.detail_retry))
        }
    }
}

/** Previsualización de la pantalla con contenido (con plazo). */
@Preview
@Composable
private fun DetailScreenContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailScreen(
                state = DetailUiState.Content(detail = sampleDetails().first()),
                onRetry = {},
                onBack = {},
            )
        }
    }
}

/** Previsualización de la sub-barra de contenido con su identificador. */
@Preview
@Composable
private fun ContentSubBarPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ContentSubBar(publicationId = "BOE-A-2026-21002", onBack = {})
        }
    }
}

/** Previsualización de la sub-barra de contenido sin identificador (estado de carga). */
@Preview
@Composable
private fun ContentSubBarWithoutIdPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ContentSubBar(publicationId = null, onBack = {})
        }
    }
}

/** Previsualización del estado de carga. */
@Preview
@Composable
private fun LoadingViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LoadingView()
        }
    }
}

/** Previsualización del estado «no encontrada». */
@Preview
@Composable
private fun NotFoundViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            NotFoundView()
        }
    }
}

/** Previsualización del estado de error con reintento. */
@Preview
@Composable
private fun ErrorViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorView(onRetry = {})
        }
    }
}
