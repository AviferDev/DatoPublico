package es.aviferdev.datopublico.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_back
import es.aviferdev.datopublico.resources.detail_placeholder_message
import es.aviferdev.datopublico.resources.detail_placeholder_publication_id
import es.aviferdev.datopublico.resources.detail_placeholder_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * **Placeholder** de navegación del detalle de una publicación.
 *
 * Es temporal: solo demuestra que el grafo lleva el `id` de la publicación al
 * destino `detail/{publicationId}` y que el atrás vuelve al feed. La pantalla de
 * detalle **real** (resumen «¿Qué cambia?» / «¿A quién afecta?», cifras clave,
 * plazo, enlace a la fuente oficial y aviso de IA) es **FT00033**, que
 * sustituirá este composable reutilizando la misma ruta. Aquí no hay resumen,
 * ni fuente, ni aviso de IA, ni red.
 *
 * @param publicationId identificador oficial de la publicación recibido por
 *   argumento de ruta; puede llegar vacío y no se usa como control de flujo.
 * @param onBack acción de atrás: vuelve al feed.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun DetailPlaceholderScreen(
    publicationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(Res.string.detail_placeholder_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(Res.string.detail_placeholder_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.detail_placeholder_publication_id, publicationId),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Previsualización del placeholder de detalle con un `id` de ejemplo. */
@Preview
@Composable
private fun DetailPlaceholderScreenPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailPlaceholderScreen(
                publicationId = "BOE-A-2026-21001",
                onBack = {},
            )
        }
    }
}
