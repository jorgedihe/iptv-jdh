package com.m3u.smartphone.ui.material.model

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * IPTV dark palette inspired by DiiXtream: pure black background, blue accent.
 * See M3UAndroid-design-refs/BRIEF.md for the design tokens this implements.
 */
val IptvDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF387AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1F4FAF),
    onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFF8FB6FF),

    secondary = Color(0xFF5FFF87),               // rating pill green (Rotten-style)
    onSecondary = Color(0xFF003B14),
    secondaryContainer = Color(0xFF1F4FAF),       // bottom-nav indicator: blue, not green
    onSecondaryContainer = Color(0xFFD6E4FF),

    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1A0D33),
    tertiaryContainer = Color(0xFF2E1F5B),
    onTertiaryContainer = Color(0xFFD6C8FF),

    background = Color(0xFF000000),
    onBackground = Color.White,

    surface = Color(0xFF1A1A1F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFFA0A0A8),
    surfaceTint = Color(0xFF387AFF),

    surfaceContainerLowest = Color(0xFF050507),
    surfaceContainerLow = Color(0xFF101012),
    surfaceContainer = Color(0xFF1A1A1F),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF333339),
    surfaceBright = Color(0xFF2A2A30),
    surfaceDim = Color(0xFF0A0A0C),

    outline = Color(0xFF45454C),
    outlineVariant = Color(0xFF2E2E33),

    inverseSurface = Color(0xFFEEEEF2),
    inverseOnSurface = Color(0xFF1A1A1F),

    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF5B1A1A),
    onErrorContainer = Color(0xFFFFD6D6),

    scrim = Color(0xFF000000)
)

@Composable
fun Theme(
    @Suppress("UNUSED_PARAMETER") argb: Int,
    @Suppress("UNUSED_PARAMETER") useDynamicColors: Boolean,
    @Suppress("UNUSED_PARAMETER") useDarkTheme: Boolean = isSystemInDarkTheme(),
    typography: Typography,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IptvDarkScheme,
        typography = typography
    ) {
        content()
    }
}
