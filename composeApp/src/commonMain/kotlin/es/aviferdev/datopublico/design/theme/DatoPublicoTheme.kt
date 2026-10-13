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
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview

// Tokens de DESIGN.md (fuente de verdad del diseño). Es el ÚNICO lugar donde el
// cliente fija colores literales: el resto de la UI lee siempre de
// `MaterialTheme.colorScheme.*` (regla MUST de CONSTRAINTS.md §«Cliente»).
private val Primary = Color(0xFF1A4D8F)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFDCE6F5)
private val OnPrimaryContainer = Color(0xFF102C52)
private val Secondary = Color(0xFF0F766E)
private val OnSecondary = Color(0xFFFFFFFF)
private val Tertiary = Color(0xFFF59E0B)
private val OnTertiary = Color(0xFF1F2937)
private val Background = Color(0xFFF7F9FC)
private val Surface = Color(0xFFFFFFFF)
private val Outline = Color(0xFFCBD5E1)
private val OnSurfaceVariant = Color(0xFF4B5563)

/**
 * Esquema de color del cliente: mapea los tokens de `DESIGN.md` (`primary`
 * `#1A4D8F`, `secondary` `#0F766E`, `tertiary` = `accent` `#F59E0B`,
 * `background` `#F7F9FC`, `surface` `#FFFFFF`, `text` `#1F2937`) a los roles de
 * Material 3, con los pares `on*` elegidos para cumplir contraste **AA**.
 *
 * Sólo se define el esquema **claro**: el modo oscuro no está en el alcance de
 * FT00032.
 */
private val DatoPublicoColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    background = Background,
    onBackground = OnSurfaceVariant,
    surface = Surface,
    onSurface = OnSurfaceVariant,
    surfaceVariant = Color(0xFFEFF3F8),
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
)

// Tipografía de DESIGN.md: títulos con peso 700 y cuerpo con peso 400, con el
// cuerpo por encima de 16sp para lectura móvil.
private val DatoPublicoTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

// Formas de DESIGN.md: radios suaves `sm` 6dp y `md` 12dp, sin esquinas marcadas.
private val DatoPublicoShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
)

/**
 * Tema del cliente: **única** entrada que envuelve la UI de `:composeApp` con
 * los tokens de `DESIGN.md` (color, tipografía y formas).
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
