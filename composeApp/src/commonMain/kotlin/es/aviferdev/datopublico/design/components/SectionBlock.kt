package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.detail_what_changes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Bloque de sección del detalle (`DESIGN.md`, «Bloque de sección: etiqueta +
 * contenido»): una **etiqueta** de sección y su **contenido**.
 *
 * Es un componente reutilizable: lo usan los tres bloques del resumen ciudadano
 * («¿Qué cambia?», «¿A quién afecta?» y «Cifras clave») y lo podrán reutilizar las
 * features siguientes (búsqueda, chat). La etiqueta llega como recurso i18n y el
 * contenido como *slot*, para no acoplar el componente a ningún modelo.
 *
 * @param label etiqueta de la sección (recurso i18n, resuelto aquí).
 * @param modifier modificador de layout del llamante.
 * @param content contenido de la sección.
 */
@Composable
fun SectionBlock(
    label: StringResource,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/** Previsualización del bloque de sección con un contenido de ejemplo. */
@Preview
@Composable
private fun SectionBlockPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SectionBlock(
                label = Res.string.detail_what_changes,
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = "Contenido de ejemplo del bloque de sección.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
