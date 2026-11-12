package com.khereba1.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HarfBg = Color(0xFF0E0F12)
val HarfSurface = Color(0xFF16181D)
val HarfSurface2 = Color(0xFF20232A)
val HarfOutline = Color(0xFF3A3F49)
val HarfOutlineSoft = Color(0xFF2A2E36)
val HarfText = Color(0xFFECEDEF)
val HarfTextDim = Color(0xFF9AA0AB)
val HarfAccent = Color(0xFFFF6A3D)
val HarfCanvasBg = Color(0xFF08090B)

private val Colors = darkColorScheme(
    primary = HarfAccent,
    onPrimary = Color(0xFF2A0D03),
    primaryContainer = Color(0xFF3D1E12),
    onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = HarfTextDim,
    onSecondary = HarfBg,
    secondaryContainer = HarfOutlineSoft,
    onSecondaryContainer = HarfText,
    background = HarfBg,
    onBackground = HarfText,
    surface = HarfSurface,
    onSurface = HarfText,
    surfaceVariant = HarfSurface2,
    onSurfaceVariant = HarfTextDim,
    surfaceTint = HarfSurface,
    outline = HarfOutline,
    outlineVariant = HarfOutlineSoft,
)

@Composable
fun HarfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
