package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.HighlightReason
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_deadline
import es.aviferdev.datopublico.resources.cd_highlight
import es.aviferdev.datopublico.resources.deadline_badge
import es.aviferdev.datopublico.resources.reason_normative_rank
import es.aviferdev.datopublico.resources.reason_normative_section
import es.aviferdev.datopublico.resources.reason_open_deadline
import es.aviferdev.datopublico.resources.reason_relevant_body
import es.aviferdev.datopublico.resources.reason_thematic_keyword
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pastilla con icono y texto: la base compartida de los badges de destacado y de
 * plazo. Recibe ya resueltos el texto, el icono y los colores del tema.
 *
 * @param icon icono Material que aporta significado.
 * @param iconContentDescription descripción del icono para lectores de pantalla.
 * @param text texto visible del badge.
 * @param containerColor color de fondo, leído del tema por el llamante.
 * @param contentColor color del contenido, con contraste AA sobre [containerColor].
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun Badge(
    icon: ImageVector,
    iconContentDescription: String,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(14.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Badge de **destacado**: explica por qué la publicación está destacada con un
 * motivo objetivo. Usa el tono azul pastel (`primaryContainer`).
 *
 * @param reason motivo del destacado.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun HighlightBadge(reason: HighlightReason, modifier: Modifier = Modifier) {
    Badge(
        icon = Icons.Outlined.Star,
        iconContentDescription = stringResource(Res.string.cd_highlight),
        text = stringResource(reason.label()),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    )
}

/**
 * Badge de **plazo**: muestra la fecha límite de una convocatoria en el tono de
 * atención del mockup (ámbar, `tertiaryContainer`).
 *
 * @param deadline plazo de solicitud de la convocatoria.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun DeadlineBadge(deadline: Deadline, modifier: Modifier = Modifier) {
    Badge(
        icon = Icons.Outlined.DateRange,
        iconContentDescription = stringResource(Res.string.cd_deadline),
        text = stringResource(Res.string.deadline_badge, formatIsoDate(deadline.limitDate)),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    )
}

/** Texto localizado del motivo de destacado. */
private fun HighlightReason.label(): StringResource = when (this) {
    HighlightReason.NORMATIVE_SECTION -> Res.string.reason_normative_section
    HighlightReason.NORMATIVE_RANK -> Res.string.reason_normative_rank
    HighlightReason.THEMATIC_KEYWORD -> Res.string.reason_thematic_keyword
    HighlightReason.OPEN_DEADLINE -> Res.string.reason_open_deadline
    HighlightReason.RELEVANT_BODY -> Res.string.reason_relevant_body
}

/** Previsualización del badge de destacado. */
@Preview
@Composable
private fun HighlightBadgePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HighlightBadge(
                reason = HighlightReason.OPEN_DEADLINE,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del badge de plazo. */
@Preview
@Composable
private fun DeadlineBadgePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeadlineBadge(
                deadline = Deadline(limitDate = "2026-11-16"),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de la pastilla base con un contenido de ejemplo. */
@Preview
@Composable
private fun BadgePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Badge(
                icon = Icons.Outlined.Star,
                iconContentDescription = "Badge de ejemplo",
                text = "Ejemplo",
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
