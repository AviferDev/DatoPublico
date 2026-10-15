package es.aviferdev.datopublico.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview

// Tokens de `docs/desing/datopublico/DESIGN.md` (frontmatter Material 3, la
// fuente de verdad del rediseño). Es el ÚNICO lugar donde el cliente fija colores
// literales: el resto de la UI lee siempre de `MaterialTheme.colorScheme.*`
// (regla MUST de CONSTRAINTS.md §«Cliente»).
private val Primary = Color(0xFF00366F)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFD6E3FF)
private val OnPrimaryContainer = Color(0xFF0E4688)
private val InversePrimary = Color(0xFFA9C7FF)
private val Secondary = Color(0xFF006A63)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFF9CF2E8)
private val OnSecondaryContainer = Color(0xFF00504A)
private val Tertiary = Color(0xFF4F3000)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFFFFDDB8)
private val OnTertiaryContainer = Color(0xFF653E00)
private val Background = Color(0xFFF9F9FF)
private val OnBackground = Color(0xFF141B2B)
private val Surface = Color(0xFFF9F9FF)
private val OnSurface = Color(0xFF141B2B)
private val SurfaceContainerLowest = Color(0xFFFFFFFF)
private val SurfaceContainerLow = Color(0xFFF1F3FF)
private val SurfaceContainer = Color(0xFFE9EDFF)
private val SurfaceContainerHigh = Color(0xFFE1E8FD)
private val SurfaceContainerHighest = Color(0xFFDCE2F7)
private val SurfaceVariant = Color(0xFFDCE2F7)
private val OnSurfaceVariant = Color(0xFF434750)
private val Outline = Color(0xFF737782)
private val OutlineVariant = Color(0xFFC3C6D2)
private val Error = Color(0xFFBA1A1A)
private val OnError = Color(0xFFFFFFFF)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF93000A)
private val InverseSurface = Color(0xFF293040)
private val InverseOnSurface = Color(0xFFEDF0FF)
private val SurfaceTint = Color(0xFF305EA1)

/**
 * Esquema de color del cliente: mapea los tokens Material 3 del mockup
 * (`docs/desing/datopublico/DESIGN.md`) a los roles de Material 3. Los pares
 * `on*` ya vienen fijados por el diseño con contraste **AA**.
 *
 * Sólo se define el esquema **claro** (el mockup opera solo en modo claro). Los
 * tonos pastel de las etiquetas usan los roles `*Container` (no existe
 * `primaryFixed` en `ColorScheme`).
 */
private val DatoPublicoColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = SurfaceTint,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

// Tipografía del mockup (familia Inter): títulos en negrita y cuerpo sobre 16sp
// para lectura móvil. Se conserva la familia por defecto de la plataforma (no se
// empaqueta Inter) y se respetan tamaños, pesos y espaciado del diseño.
private val DatoPublicoTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.005).em,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.03.em,
    ),
)

// Formas del mockup: radios discretos `sm`/`md`/`lg`, sin esquinas marcadas. Las
// pastillas y botones redondeados usan `CircleShape` en el componente.
private val DatoPublicoShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
)

/**
 * Tema del cliente: **única** entrada que envuelve la UI de `:composeApp` con
 * los tokens de `docs/desing/datopublico/DESIGN.md` (color, tipografía y
 * formas).
 *
 * @param content contenido que se renderiza dentro del tema.
 */
@Composable
fun DatoPublicoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DatoPublicoColorScheme,
        typography = DatoPublicoTypography,
        shapes = DatoPublicoShapes,
        content = content,
    )
}

/** Previsualización del tema: fondo, tipografía y color primario del token. */
@Preview
@Composable
private fun DatoPublicoThemePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Text(
                text = "DatoPublico",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
