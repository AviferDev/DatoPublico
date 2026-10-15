package es.aviferdev.datopublico.feature.detail.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.AiNotice
import es.aviferdev.datopublico.design.components.CategoryLabel
import es.aviferdev.datopublico.design.components.OfficialSourceLink
import es.aviferdev.datopublico.design.components.OfficialSourceUrl
import es.aviferdev.datopublico.design.components.SectionBlock
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.detail.data.sampleDetails
import es.aviferdev.datopublico.feature.detail.domain.PublicationDetail
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_deadline
import es.aviferdev.datopublico.resources.detail_ai_notice_title
import es.aviferdev.datopublico.resources.detail_deadline_title
import es.aviferdev.datopublico.resources.detail_key_figures
import es.aviferdev.datopublico.resources.detail_what_changes
import es.aviferdev.datopublico.resources.detail_who_is_affected
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Contenido del detalle, en el orden del mockup: tarjeta de cabecera, plazo (solo
 * en convocatorias), los tres bloques del resumen, el aviso de IA y la fuente
 * oficial.
 *
 * La fuente oficial es **siempre** visible cuando hay contenido (regla MUST de
 * `CONSTRAINTS.md` §«Producto y datos») y el **aviso de IA** se muestra cuando el
 * resumen es generado por IA ([PublicationDetail.showsAiNotice]).
 *
 * @param detail detalle que se muestra.
 * @param modifier modificador de layout del llamante.
 */
@Composable
internal fun DetailContent(detail: PublicationDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DetailHeaderCard(detail = detail)
        detail.deadline?.let { deadline -> DeadlineBlock(deadline = deadline) }
        SectionBlock(label = Res.string.detail_what_changes) {
            Text(
                text = detail.summary.whatChanges,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        SectionBlock(label = Res.string.detail_who_is_affected) {
            Text(
                text = detail.summary.whoIsAffected,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (detail.summary.keyFigures.isNotEmpty()) {
            SectionBlock(label = Res.string.detail_key_figures) {
                KeyFigures(figures = detail.summary.keyFigures)
            }
        }
        if (detail.showsAiNotice) {
            AiNotice(title = stringResource(Res.string.detail_ai_notice_title))
        }
        OfficialSourceLink(url = detail.officialSourceUrl)
        OfficialSourceUrl(url = detail.officialSourceUrl)
    }
}

/**
 * Tarjeta de cabecera del mockup: superficie blanca (`surfaceContainerLowest`),
 * radio `medium`, borde `outlineVariant` y una **barra de acento** de 4 dp en
 * `primary` arriba.
 *
 * El organismo emisor **no** se pinta: no existe en el modelo de dominio del
 * cliente y no se inventa.
 *
 * @param detail detalle que se muestra.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DetailHeaderCard(detail: PublicationDetail, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(color = MaterialTheme.colorScheme.primary),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderMetaRow(detail = detail)
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Fila de metadatos de la cabecera: etiqueta de categoría y pastilla con la fecha
 * de publicación formateada `dd/mm/aaaa`.
 *
 * @param detail detalle que se muestra.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun HeaderMetaRow(detail: PublicationDetail, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryLabel(category = detail.category)
        DatePill(date = formatIsoDate(detail.publishedDate))
    }
}

/**
 * Pastilla discreta con la fecha de publicación, sobre `surfaceContainer`.
 *
 * @param date fecha ya formateada para mostrar.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DatePill(date: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Lista de **cifras clave** del resumen, una por línea con un punto tipográfico.
 *
 * @param figures cifras clave; el llamante omite el bloque si la lista está vacía.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun KeyFigures(figures: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        figures.forEach { figure ->
            Text(
                text = "• " + figure,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Bloque de **plazo** destacado de una convocatoria: superficie en
 * `tertiaryContainer`, con un círculo blanco (`surfaceContainerLowest`) y el icono
 * `DateRange`, seguido del título del plazo y la fecha límite formateada.
 *
 * @param deadline plazo de solicitud de la convocatoria.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DeadlineBlock(deadline: Deadline, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = stringResource(Res.string.cd_deadline),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.detail_deadline_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = formatIsoDate(deadline.limitDate),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Previsualización del contenido completo con plazo. */
@Preview
@Composable
private fun DetailContentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailContent(detail = sampleDetails().first())
        }
    }
}

/** Previsualización del contenido sin plazo (no es convocatoria). */
@Preview
@Composable
private fun DetailContentWithoutDeadlinePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailContent(detail = sampleDetails().last())
        }
    }
}

/** Previsualización de la tarjeta de cabecera. */
@Preview
@Composable
private fun DetailHeaderCardPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailHeaderCard(detail = sampleDetails().first(), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la fila de metadatos de la cabecera. */
@Preview
@Composable
private fun HeaderMetaRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HeaderMetaRow(detail = sampleDetails().first(), modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la pastilla de fecha. */
@Preview
@Composable
private fun DatePillPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DatePill(date = "13/10/2026", modifier = Modifier.padding(8.dp))
        }
    }
}

/** Previsualización de la lista de cifras clave. */
@Preview
@Composable
private fun KeyFiguresPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            KeyFigures(
                figures = sampleDetails().first().summary.keyFigures,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del plazo destacado. */
@Preview
@Composable
private fun DeadlineBlockPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeadlineBlock(
                deadline = Deadline(limitDate = "2026-10-30"),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
