package es.aviferdev.datopublico.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

// Textos literales a propósito: los recursos/idiomas llegan con la UI real
// (FT00032+); aquí solo se demuestra que la pantalla es una única fuente en
// `commonMain`.
private const val APP_TITLE = "DatoPublico"
private const val APP_SUBTITLE = "Esqueleto Compose Multiplatform"

// Tokens mínimos de DESIGN.md (`primary`, `background`); el tema completo es de
// una feature posterior de UI.
private val DatoPublicoColorScheme = lightColorScheme(
    primary = Color(0xFF1A4D8F),
    background = Color(0xFFF7F9FC),
)

/**
 * Pantalla mínima compartida por las tres plataformas: Android, iOS y Web la
 * montan desde su entrypoint sin duplicar UI.
 */
@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = DatoPublicoColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = APP_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = APP_SUBTITLE,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
