// Los composables se nombran en PascalCase (convención de Compose), pero el
// chasis vive en `app/` (fuera de las exclusiones `ui/**` de `detekt.yml`).
@file:Suppress("FunctionNaming")

package es.aviferdev.datopublico.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.app_title
import es.aviferdev.datopublico.resources.brand_tagline
import es.aviferdev.datopublico.resources.cd_back
import es.aviferdev.datopublico.resources.cd_search
import es.aviferdev.datopublico.resources.chat_entry
import es.aviferdev.datopublico.resources.nav_chat
import es.aviferdev.datopublico.resources.nav_feed
import es.aviferdev.datopublico.resources.nav_search
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Altura de las barras superior e inferior del chasis. */
private val BAR_HEIGHT = 64.dp

/**
 * Destinos de la **barra inferior**: cada pestaña con su ruta, su etiqueta y su
 * icono (Material Icons del set `core`).
 */
private enum class AppTab(val route: String, val labelRes: StringResource, val icon: ImageVector) {
    /** Feed diario (destino inicial). */
    FEED(AppRoutes.FEED, Res.string.nav_feed, Icons.AutoMirrored.Outlined.List),

    /** Búsqueda con filtros. */
    SEARCH(AppRoutes.SEARCH, Res.string.nav_search, Icons.Outlined.Search),

    /** Chat RAG. */
    CHAT(AppRoutes.CHAT, Res.string.nav_chat, Icons.Outlined.Email),
}

/**
 * Chasis de la app: **barra superior** y **barra inferior** comunes a las cuatro
 * pantallas, más el contenido. El detalle usa una barra superior con atrás y sin
 * barra inferior; el resto son pestañas con barra inferior.
 *
 * @param currentRoute ruta actual del grafo (para resaltar la pestaña activa).
 * @param onNavigate acción al pulsar una pestaña o una acción de la barra superior.
 * @param onBack acción de atrás (solo se usa en el detalle).
 * @param content contenido de la pantalla; recibe el `padding` del chasis.
 */
@Composable
fun DatoPublicoScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isDetail = currentRoute == AppRoutes.DETAIL
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isDetail) {
                DetailTopBar(onBack = onBack, onNavigate = onNavigate)
            } else {
                HomeTopBar(onNavigate = onNavigate)
            }
        },
        bottomBar = {
            if (!isDetail) {
                AppBottomBar(currentRoute = currentRoute, onNavigate = onNavigate)
            }
        },
        content = content,
    )
}

/**
 * Barra superior de las pantallas con pestañas: marca a la izquierda y las
 * acciones de **preguntar** y **buscar** a la derecha.
 *
 * @param onNavigate acción de navegación (chat/búsqueda).
 */
@Composable
private fun HomeTopBar(onNavigate: (String) -> Unit) {
    AppTopBarSurface {
        BrandBlock(modifier = Modifier.weight(1f))
        AskPill(onClick = { onNavigate(AppRoutes.CHAT) })
        SearchAction(onClick = { onNavigate(AppRoutes.SEARCH) })
    }
}

/**
 * Barra superior del **detalle**: atrás, marca y las acciones de preguntar y
 * buscar.
 *
 * @param onBack acción de atrás.
 * @param onNavigate acción de navegación (chat/búsqueda).
 */
@Composable
private fun DetailTopBar(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    AppTopBarSurface {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        BrandBlock(modifier = Modifier.weight(1f))
        SearchAction(onClick = { onNavigate(AppRoutes.SEARCH) })
        AskIconAction(onClick = { onNavigate(AppRoutes.CHAT) })
    }
}

/**
 * Superficie común de las barras superiores: fondo de superficie, altura fija y
 * padding horizontal de la retícula.
 *
 * @param content contenido de la barra (con el alcance de la `Row`).
 */
@Composable
private fun AppTopBarSurface(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() },
        )
    }
}

/**
 * Bloque de marca: nombre del producto y su bajada.
 *
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun BrandBlock(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(Res.string.app_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(Res.string.brand_tagline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Acción principal «Preguntar al BOE»: pastilla con icono y texto.
 *
 * @param onClick acción al pulsarla.
 */
@Composable
private fun AskPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = stringResource(Res.string.chat_entry),
                modifier = Modifier.size(20.dp),
            )
            Text(text = stringResource(Res.string.chat_entry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Acción «buscar» como icono plano.
 *
 * @param onClick acción al pulsarla.
 */
@Composable
private fun SearchAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(Res.string.cd_search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Acción «preguntar» como icono plano (barra del detalle).
 *
 * @param onClick acción al pulsarla.
 */
@Composable
private fun AskIconAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.Email,
            contentDescription = stringResource(Res.string.chat_entry),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Barra inferior de **navegación** con las tres pestañas.
 *
 * @param currentRoute ruta actual (resalta la pestaña activa).
 * @param onNavigate acción de navegación.
 */
@Composable
private fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            AppTab.entries.forEach { tab ->
                AppTabItem(
                    tab = tab,
                    isSelected = currentRoute == tab.route,
                    onClick = { onNavigate(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Pestaña de la barra inferior: icono y etiqueta, resaltados si está activa.
 *
 * @param tab pestaña a pintar.
 * @param isSelected `true` si es la pestaña activa.
 * @param onClick acción al pulsarla.
 * @param modifier modificador de layout del llamante.
 */
@Composable
private fun AppTabItem(
    tab: AppTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.labelRes),
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/** Previsualización de la barra superior de las pestañas. */
@Preview
@Composable
private fun HomeTopBarPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeTopBar(onNavigate = {})
        }
    }
}

/** Previsualización de la barra inferior con la pestaña del feed activa. */
@Preview
@Composable
private fun AppBottomBarPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AppBottomBar(currentRoute = AppRoutes.FEED, onNavigate = {})
        }
    }
}

/** Previsualización de la barra superior del detalle. */
@Preview
@Composable
private fun DetailTopBarPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailTopBar(onBack = {}, onNavigate = {})
        }
    }
}
