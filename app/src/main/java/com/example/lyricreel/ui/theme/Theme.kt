package com.example.lyricreel.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    background = PureBlack,
    surface = DarkGray,
    onPrimary = PureBlack,
    onBackground = TextWhite,
    onSurface = TextWhite
)
@Composable
fun LyricReelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
