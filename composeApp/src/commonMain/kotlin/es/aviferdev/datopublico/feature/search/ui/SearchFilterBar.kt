package es.aviferdev.datopublico.feature.search.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import es.aviferdev.datopublico.resources.search_category_all
import es.aviferdev.datopublico.resources.search_clear_filters
import es.aviferdev.datopublico.resources.search_date_error
import es.aviferdev.datopublico.resources.search_date_placeholder
import es.aviferdev.datopublico.resources.search_from_date_label
import es.aviferdev.datopublico.resources.search_query_label
import es.aviferdev.datopublico.resources.search_submit
import es.aviferdev.datopublico.resources.search_to_date_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Barra de filtros de la búsqueda: campo de **consulta**, chips de **categoría**
 * (las 11 curadas + «Todas») y campos de **fecha** `desde`/`hasta` (ISO), más las
 * acciones «Buscar» y «Limpiar filtros».
 *
 * Es **stateless**: recibe la entrada del usuario y delega los eventos. Es
 * específica de la búsqueda, así que vive en `feature/search/ui/` (regla de
 * `CONSTRAINTS.md`). Todos los colores salen del tema.
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchQueryField(query = query, onQueryChange = onQueryChange, onSubmit = onSubmit)
        CategoryChipRow(category = category, onCategorySelected = onCategorySelected)
        DateRangeFields(
            fromDate = fromDate,
            toDate = toDate,
            onFromDateChange = onFromDateChange,
            onToDateChange = onToDateChange,
        )
        SearchActions(onSubmit = onSubmit, onClearFilters = onClearFilters)
    }
}

/**
 * Campo de texto de la consulta, con icono de búsqueda y acción de teclado.
 *
 * @param query consulta escrita.
 * @param onQueryChange evento de cambio de consulta.
 * @param onSubmit evento de envío (acción «Buscar» del teclado).
 */
@Composable
private fun SearchQueryField(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(text = stringResource(Res.string.search_query_label)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(Res.string.cd_search),
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

/**
 * Fila de chips de categoría: «Todas» (sin filtro) y las 11 categorías curadas,
 * en una fila con scroll horizontal para no robar altura a los resultados.
 *
 * @param category categoría seleccionada; `null` es «Todas».
 * @param onCategorySelected evento de selección.
 */
@Composable
private fun CategoryChipRow(
    category: PublicationCategory?,
    onCategorySelected: (PublicationCategory?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = category == null,
            onClick = { onCategorySelected(null) },
            label = { Text(text = stringResource(Res.string.search_category_all)) },
        )
        PublicationCategory.entries.forEach { candidate ->
            FilterChip(
                selected = category == candidate,
                onClick = { onCategorySelected(candidate) },
                label = { Text(text = stringResource(candidate.labelResource())) },
            )
        }
    }
}

/**
 * Campos de fecha `desde`/`hasta` (ISO), cada uno con validación pura.
 *
 * @param fromDate fecha `desde` en crudo.
 * @param toDate fecha `hasta` en crudo.
 * @param onFromDateChange evento de cambio de `desde`.
 * @param onToDateChange evento de cambio de `hasta`.
 */
@Composable
private fun DateRangeFields(
    fromDate: String,
    toDate: String,
    onFromDateChange: (String) -> Unit,
    onToDateChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        supportingText = supportingText,
    )
}

/**
 * Acciones de la barra: botón «Buscar» (principal) y «Limpiar filtros».
 *
 * @param onSubmit evento de envío.
 * @param onClearFilters evento de limpieza de filtros.
 */
@Composable
private fun SearchActions(onSubmit: () -> Unit, onClearFilters: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onSubmit, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(Res.string.search_submit))
        }
        TextButton(onClick = onClearFilters) {
            Text(text = stringResource(Res.string.search_clear_filters))
        }
    }
}

/** Previsualización de la barra de filtros completa. */
@Preview
@Composable
private fun SearchFilterBarPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchFilterBar(
                query = "becas para jóvenes",
                category = PublicationCategory.GRANTS_AND_SUBSIDIES,
                fromDate = "2026-01-01",
                toDate = "",
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

/** Previsualización del campo de consulta aislado. */
@Preview
@Composable
private fun SearchQueryFieldPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchQueryField(query = "", onQueryChange = {}, onSubmit = {})
        }
    }
}

/** Previsualización de los chips de categoría con «Todas» seleccionada. */
@Preview
@Composable
private fun CategoryChipRowPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoryChipRow(category = null, onCategorySelected = {})
        }
    }
}

/** Previsualización de los campos de fecha, uno de ellos inválido. */
@Preview
@Composable
private fun DateRangeFieldsPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DateRangeFields(
                fromDate = "2026-01-01",
                toDate = "01/2026",
                onFromDateChange = {},
                onToDateChange = {},
            )
        }
    }
}

/** Previsualización de las acciones de la barra. */
@Preview
@Composable
private fun SearchActionsPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchActions(onSubmit = {}, onClearFilters = {})
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
                value = "2026-01-01",
                labelRes = Res.string.search_from_date_label,
                onValueChange = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
