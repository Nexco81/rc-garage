package fr.nexco.rcgarage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C), secondary = Color(0xFF4D6357),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DD5A5), secondary = Color(0xFFB4CCBC),
)

@Composable
fun RCGarageTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode.usesDarkColors(isSystemInDarkTheme())) DarkColors else LightColors,
        content = content,
    )
}
