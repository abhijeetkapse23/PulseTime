package com.pulsetime.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val Accent = Color(0xFF7FD3FF)

/**
 * Pure-black OLED theme: black pixels are fully off on OLED, which is the single biggest
 * display-power saving available to a watch app.
 */
private val PulseColors = Colors(
    primary = Accent,
    primaryVariant = Color(0xFF3A9FD0),
    secondary = Color(0xFFFFC27F),
    background = Color.Black,
    surface = Color(0xFF161616),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = PulseColors, content = content)
}
