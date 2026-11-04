package io.github.phonemousekeyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A deliberately plain, monochrome dark theme. The app is a tool; the UI should stay out of
 * the way and be comfortable to use in a dim room in front of a screen.
 */
private val UtilitarianColors = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF121212),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF121212),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFEF9A9A),
)

/** Colour used to indicate a live connection. */
val ConnectedGreen = Color(0xFF81C784)

@Composable
fun PhoneMouseKeyboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = UtilitarianColors, content = content)
}
