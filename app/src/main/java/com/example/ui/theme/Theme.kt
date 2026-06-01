package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MekanikNeonGreen,
    onPrimary = Color.Black,
    primaryContainer = MekanikDarkGreen,
    onPrimaryContainer = MekanikNeonGreen,
    secondary = MekanikDimGreen,
    onSecondary = Color.Black,
    tertiary = MekanikAccentGreen,
    background = MekanikDarkBg,
    onBackground = MekanikTextPrimary,
    surface = MekanikSurface,
    onSurface = MekanikTextPrimary,
    surfaceVariant = MekanikSurfaceVariant,
    onSurfaceVariant = MekanikTextSecondary,
    error = MekanikErrorRed,
    onError = Color.Black,
    outline = MekanikDimGreen
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Keep it dark by default for the professional cluster look
    dynamicColor: Boolean = false, // Force consistent neon-green brand branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
