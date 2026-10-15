package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_search
import es.aviferdev.datopublico.resources.cd_search_empty
import es.aviferdev.datopublico.resources.cd_search_error
import es.aviferdev.datopublico.resources.search_empty_message
import es.aviferdev.datopublico.resources.search_empty_title
import es.aviferdev.datopublico.resources.search_error_message
import es.aviferdev.datopublico.resources.search_error_title
import es.aviferdev.datopublico.resources.search_idle_message
import es.aviferdev.datopublico.resources.search_idle_title
import es.aviferdev.datopublico.resources.search_loading
import es.aviferdev.datopublico.resources.search_results_for
import es.aviferdev.datopublico.resources.search_results_summary
import es.aviferdev.datopublico.resources.search_retry
import es.aviferdev.datopublico.resources.search_sort_recent
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Vistas de los estados **no** de contenido de la búsqueda (inicial, carga, «sin
 * resultados» y error) y las piezas de encabezado del **contenido** (tira de
 * resumen e indicador de orden). Son **stateless** y comparten la composición de
 * mensaje ([SearchStateMessage]); cada una tiene su `@Preview`.
 */

/**
 * Tira de resumen editorial: punto `secondary`, recuento sobre `onSurface`, eco
 * de la consulta en `primary` y, a la derecha, el indicador de orden.
 *
 * @param count número de publicaciones encontradas.
 * @param query consulta enviada (eco).
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun SearchResultsSummary(count: Int, query: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
            )
            Text(
                text = stringResource(Res.string.search_results_summary, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.search_results_for, query),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SearchSortIndicator()
    }
}

/**
 * Indicador de orden de los resultados (icono y «Recientes»).
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchSortIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.search_sort_recent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Vista del **estado inicial**: invita a buscar antes de haber enviado nada.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun SearchIdleView(modifier: Modifier = Modifier) {
    SearchStateMessage(
        icon = Icons.Outlined.Search,
        iconContentDescription = stringResource(Res.string.cd_search),
        title = stringResource(Res.string.search_idle_title),
        message = stringResource(Res.string.search_idle_message),
        modifier = modifier,
    )
}

/**
 * Vista del **estado de carga**: indicador centrado hasta que llegan resultados.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun SearchLoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.search_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Vista del **estado «sin resultados»**: explica que la combinación no casó.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun SearchEmptyView(modifier: Modifier = Modifier) {
    SearchStateMessage(
        icon = Icons.Outlined.Info,
        iconContentDescription = stringResource(Res.string.cd_search_empty),
        title = stringResource(Res.string.search_empty_title),
        message = stringResource(Res.string.search_empty_message),
        modifier = modifier,
    )
}

/**
 * Vista del **estado de error**: icono, mensaje y botón «Reintentar». El fallo
 * llega modelado como [SearchResultState.Error], nunca como excepción, así que la
 * app no se cierra.
 *
 * @param onRetry acción de reintento.
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun SearchErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    SearchStateMessage(
        icon = Icons.Outlined.Warning,
        iconContentDescription = stringResource(Res.string.cd_search_error),
        title = stringResource(Res.string.search_error_title),
        message = stringResource(Res.string.search_error_message),
        modifier = modifier,
        action = {
            Button(onClick = onRetry) {
                Text(text = stringResource(Res.string.search_retry))
            }
        },
    )
}

/**
 * Composición compartida de un mensaje de estado: icono con significado, título y
 * descripción, más una acción opcional.
 *
 * @param icon icono Material que aporta significado.
 * @param iconContentDescription descripción del icono para lectores de pantalla.
 * @param title título del estado.
 * @param message descripción del estado.
 * @param modifier modificador de layout del llamante.
 * @param action acción opcional (p. ej. «Reintentar»); `null` si no hay.
 */
@Composable
private fun SearchStateMessage(
    icon: ImageVector,
    iconContentDescription: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            action()
        }
    }
}

/** Previsualización de la tira de resumen de resultados. */
@Preview
@Composable
private fun SearchResultsSummaryPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchResultsSummary(
                count = 3,
                query = "becas para jóvenes",
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del indicador de orden. */
@Preview
@Composable
private fun SearchSortIndicatorPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchSortIndicator(modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización del estado inicial. */
@Preview
@Composable
private fun SearchIdleViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchIdleView()
        }
    }
}

/** Previsualización del estado de carga. */
@Preview
@Composable
private fun SearchLoadingViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchLoadingView()
        }
    }
}

/** Previsualización del estado «sin resultados». */
@Preview
@Composable
private fun SearchEmptyViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchEmptyView()
        }
    }
}

/** Previsualización del estado de error con reintento. */
@Preview
@Composable
private fun SearchErrorViewPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchErrorView(onRetry = {})
        }
    }
}

/** Previsualización de la composición de mensaje sin acción. */
@Preview
@Composable
private fun SearchStateMessagePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchStateMessage(
                icon = Icons.Outlined.Info,
                iconContentDescription = "Estado de ejemplo",
                title = "Título de ejemplo",
                message = "Descripción de ejemplo.",
            )
        }
    }
}
