package com.khereba1.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC8442B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3B0A02),
    secondary = Color(0xFF6B5A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E1D9),
    onSecondaryContainer = Color(0xFF241812),
    background = Color(0xFFFAF6F0),
    onBackground = Color(0xFF1C1A17),
    surface = Color(0xFFFAF6F0),
    onSurface = Color(0xFF1C1A17),
    surfaceVariant = Color(0xFFEDE5DA),
    onSurfaceVariant = Color(0xFF4F4740),
    outline = Color(0xFF8A8076),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8F72),
    onPrimary = Color(0xFF4A1204),
    primaryContainer = Color(0xFF6B2412),
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = Color(0xFFD7C2B8),
    onSecondary = Color(0xFF3A2D26),
    secondaryContainer = Color(0xFF52433B),
    onSecondaryContainer = Color(0xFFF2E1D9),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFECE6DE),
    surface = Color(0xFF14130F),
    onSurface = Color(0xFFECE6DE),
    surfaceVariant = Color(0xFF2B2823),
    onSurfaceVariant = Color(0xFFCFC6BB),
    outline = Color(0xFF978E83),
)

@Composable
fun HarfTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
