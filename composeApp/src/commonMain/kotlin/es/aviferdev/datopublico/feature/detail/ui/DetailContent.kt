package es.aviferdev.datopublico.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import es.aviferdev.datopublico.design.components.SectionBlock
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.detail.data.sampleDetails
import es.aviferdev.datopublico.feature.detail.domain.PublicationDetail
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.formatIsoDate
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_deadline
import es.aviferdev.datopublico.resources.detail_deadline_title
import es.aviferdev.datopublico.resources.detail_key_figures
import es.aviferdev.datopublico.resources.detail_what_changes
import es.aviferdev.datopublico.resources.detail_who_is_affected
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Contenido del detalle: cabecera, los tres bloques del resumen, el plazo (si es
 * convocatoria), el enlace a la fuente oficial y el aviso de IA.
 *
 * La fuente oficial y el aviso de IA son **siempre** visibles cuando hay contenido
 * (regla MUST de `CONSTRAINTS.md`); el aviso solo se omite si el resumen no es
 * generado por IA ([PublicationDetail.showsAiNotice]).
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
        DetailHeader(detail = detail)
        SectionBlock(label = Res.string.detail_what_changes) {
            Text(
                text = detail.summary.whatChanges,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        SectionBlock(label = Res.string.detail_who_is_affected) {
            Text(
                text = detail.summary.whoIsAffected,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (detail.summary.keyFigures.isNotEmpty()) {
            SectionBlock(label = Res.string.detail_key_figures) {
                KeyFigures(figures = detail.summary.keyFigures)
            }
        }
        detail.deadline?.let { deadline -> DeadlineNotice(deadline = deadline) }
        OfficialSourceLink(url = detail.officialSourceUrl)
        if (detail.showsAiNotice) {
            AiNotice()
        }
    }
}

/**
 * Cabecera del detalle: título, etiqueta de categoría y fecha de publicación.
 *
 * @param detail detalle que se muestra.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DetailHeader(detail: PublicationDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = detail.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryLabel(category = detail.category)
            Text(
                text = formatIsoDate(detail.publishedDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Plazo **resaltado** de una convocatoria, en el tono de atención de `DESIGN.md`
 * (`tertiary`), con la fecha límite formateada `dd/mm/aaaa`.
 *
 * @param deadline plazo de solicitud de la convocatoria.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DeadlineNotice(deadline: Deadline, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = stringResource(Res.string.cd_deadline),
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = stringResource(Res.string.detail_deadline_title),
                    style = MaterialTheme.typography.labelLarge,
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

/** Previsualización de la cabecera del detalle. */
@Preview
@Composable
private fun DetailHeaderPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailHeader(detail = sampleDetails().first(), modifier = Modifier.padding(8.dp))
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

/** Previsualización del plazo resaltado. */
@Preview
@Composable
private fun DeadlineNoticePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeadlineNotice(
                deadline = Deadline(limitDate = "2026-10-30"),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
