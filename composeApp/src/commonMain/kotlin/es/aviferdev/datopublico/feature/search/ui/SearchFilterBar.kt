package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.components.labelResource
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.domain.isIsoDate
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.cd_search
import es.aviferdev.datopublico.resources.cd_search_clear
import es.aviferdev.datopublico.resources.search_category_all
import es.aviferdev.datopublico.resources.search_categories_label
import es.aviferdev.datopublico.resources.search_clear_filters
import es.aviferdev.datopublico.resources.search_date_error
import es.aviferdev.datopublico.resources.search_date_format_hint
import es.aviferdev.datopublico.resources.search_date_placeholder
import es.aviferdev.datopublico.resources.search_date_range_label
import es.aviferdev.datopublico.resources.search_from_date_label
import es.aviferdev.datopublico.resources.search_query_helper
import es.aviferdev.datopublico.resources.search_query_label
import es.aviferdev.datopublico.resources.search_query_placeholder
import es.aviferdev.datopublico.resources.search_submit
import es.aviferdev.datopublico.resources.search_to_date_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Panel de filtros de la búsqueda, delimitado por una tarjeta del mockup
 * ([Surface] sobre `surfaceContainerLowest`, radio `medium` y borde
 * `outlineVariant`): campo de **consulta**, tira de **categorías**, rango de
 * **fechas** ISO y las acciones «Buscar» / «Limpiar filtros».
 *
 * Es **stateless**: recibe la entrada del usuario y delega los eventos. Es
 * específica de la búsqueda, así que vive en `feature/search/ui/`. Todos los
 * colores salen del tema.
 *
 * @param query consulta escrita.
 * @param category categoría seleccionada; `null` es «Todas».
 * @param fromDate fecha `desde` en crudo.
 * @param toDate fecha `hasta` en crudo.
 * @param onQueryChange evento de cambio de consulta.
 * @param onCategorySelected evento de selección de categoría.
 * @param onFromDateChange evento de cambio de `desde`.
 * @param onToDateChange evento de cambio de `hasta`.
 * @param onSubmit evento de envío de la búsqueda.
 * @param onClearFilters evento de limpieza de filtros.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun SearchFilterBar(
    query: String,
    category: PublicationCategory?,
    fromDate: String,
    toDate: String,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (PublicationCategory?) -> Unit,
    onFromDateChange: (String) -> Unit,
    onToDateChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SearchQueryField(query = query, onQueryChange = onQueryChange, onSubmit = onSubmit)
            CategorySection(category = category, onCategorySelected = onCategorySelected)
            DateRangeSection(
                fromDate = fromDate,
                toDate = toDate,
                onFromDateChange = onFromDateChange,
                onToDateChange = onToDateChange,
            )
            SearchActions(onSubmit = onSubmit, onClearFilters = onClearFilters)
        }
    }
}

/**
 * Campo de **consulta**: etiqueta y pista de coincidencia arriba, campo con el
 * icono de búsqueda a la izquierda y un botón de borrado a la derecha que limpia
 * la consulta (`onQueryChange("")`). La acción «Buscar» del teclado envía.
 *
 * @param query consulta escrita.
 * @param onQueryChange evento de cambio de consulta.
 * @param onSubmit evento de envío (acción «Buscar» del teclado).
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.search_query_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.search_query_helper),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = stringResource(Res.string.search_query_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(Res.string.cd_search),
                )
            },
            trailingIcon = {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.cd_search_clear),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        )
    }
}

/**
 * Sección de **categorías**: etiqueta y una tira con scroll horizontal con
 * «Todas» y las 11 categorías curadas.
 *
 * @param category categoría seleccionada; `null` es «Todas».
 * @param onCategorySelected evento de selección.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun CategorySection(
    category: PublicationCategory?,
    onCategorySelected: (PublicationCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.search_categories_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryPill(
                labelRes = Res.string.search_category_all,
                isSelected = category == null,
                onClick = { onCategorySelected(null) },
            )
            PublicationCategory.entries.forEach { candidate ->
                CategoryPill(
                    labelRes = candidate.labelResource(),
                    isSelected = category == candidate,
                    onClick = { onCategorySelected(candidate) },
                )
            }
        }
    }
}

/**
 * Pastilla de categoría: fondo pastel (`primaryContainer`) con marca de
 * selección cuando está activa y `surfaceContainer` cuando no. Reutiliza los
 * roles del tema, sin literales.
 *
 * @param labelRes etiqueta localizada de la pastilla.
 * @param isSelected `true` si es la opción activa.
 * @param onClick acción al pulsarla.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun CategoryPill(
    labelRes: StringResource,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Sección del **rango de publicación**: etiqueta, campos «Desde»/«Hasta» (ISO) y
 * la nota del formato requerido.
 *
 * @param fromDate fecha `desde` en crudo.
 * @param toDate fecha `hasta` en crudo.
 * @param onFromDateChange evento de cambio de `desde`.
 * @param onToDateChange evento de cambio de `hasta`.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DateRangeSection(
    fromDate: String,
    toDate: String,
    onFromDateChange: (String) -> Unit,
    onToDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.search_date_range_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateField(
                value = fromDate,
                labelRes = Res.string.search_from_date_label,
                onValueChange = onFromDateChange,
                modifier = Modifier.weight(1f),
            )
            DateField(
                value = toDate,
                labelRes = Res.string.search_to_date_label,
                onValueChange = onToDateChange,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(Res.string.search_date_format_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Campo de fecha ISO con validación **pura** ([isIsoDate]): una fecha inválida se
 * marca en rojo sin provocar un error; se omitirá al buscar.
 *
 * @param value fecha escrita.
 * @param labelRes etiqueta localizada del campo.
 * @param onValueChange evento de cambio.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun DateField(
    value: String,
    labelRes: StringResource,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInvalid = value.isNotEmpty() && !isIsoDate(value)
    val supportingText: (@Composable () -> Unit)? = if (isInvalid) {
        { Text(text = stringResource(Res.string.search_date_error)) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        isError = isInvalid,
        label = { Text(text = stringResource(labelRes)) },
        placeholder = { Text(text = stringResource(Res.string.search_date_placeholder)) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = null,
            )
        },
        supportingText = supportingText,
    )
}

/**
 * Acciones del panel: «Buscar» (relleno, principal, con icono) y «Limpiar
 * filtros» (contorno, con icono).
 *
 * @param onSubmit evento de envío.
 * @param onClearFilters evento de limpieza de filtros.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun SearchActions(
    onSubmit: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onSubmit, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(Res.string.search_submit))
        }
        OutlinedButton(onClick = onClearFilters) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(Res.string.search_clear_filters))
        }
    }
}

/** Previsualización del panel de filtros completo. */
@Preview
@Composable
private fun SearchFilterBarPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchFilterBar(
                query = "becas para jóvenes",
                category = PublicationCategory.GRANTS_AND_SUBSIDIES,
                fromDate = "2026-10-01",
                toDate = "2026-10-13",
                onQueryChange = {},
                onCategorySelected = {},
                onFromDateChange = {},
                onToDateChange = {},
                onSubmit = {},
                onClearFilters = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del campo de consulta con texto y botón de borrado. */
@Preview
@Composable
private fun SearchQueryFieldPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchQueryField(
                query = "becas para jóvenes",
                onQueryChange = {},
                onSubmit = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de la tira de categorías con una categoría seleccionada. */
@Preview
@Composable
private fun CategorySectionPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategorySection(
                category = PublicationCategory.GRANTS_AND_SUBSIDIES,
                onCategorySelected = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de una pastilla de categoría seleccionada. */
@Preview
@Composable
private fun CategoryPillPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoryPill(
                labelRes = Res.string.search_category_all,
                isSelected = true,
                onClick = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del rango de fechas con una fecha inválida. */
@Preview
@Composable
private fun DateRangeSectionPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DateRangeSection(
                fromDate = "2026-10-01",
                toDate = "13/10/2026",
                onFromDateChange = {},
                onToDateChange = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de un campo de fecha con formato válido. */
@Preview
@Composable
private fun DateFieldPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DateField(
                value = "2026-10-01",
                labelRes = Res.string.search_from_date_label,
                onValueChange = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización de las acciones del panel. */
@Preview
@Composable
private fun SearchActionsPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchActions(
                onSubmit = {},
                onClearFilters = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
