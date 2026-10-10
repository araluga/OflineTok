package com.oflinetok.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TokPink = Color(0xFFFE2C55)
val TokCyan = Color(0xFF25F4EE)
val TokGold = Color(0xFFFACE15)

private val colors = darkColorScheme(
    primary = TokPink,
    onPrimary = Color.White,
    secondary = TokCyan,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFBBBBBB),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
)

@Composable
fun OflineTokTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
