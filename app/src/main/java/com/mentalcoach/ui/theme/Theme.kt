package com.mentalcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light =
    lightColorScheme(
        primary = DeepGreen,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD5E6DF),
        onPrimaryContainer = Color(0xFF0E2A22),
        secondary = WarmAccent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF7DCCB),
        onSecondaryContainer = Color(0xFF4A2410),
        background = Paper,
        onBackground = Ink,
        surface = PaperCard,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE9E4D8),
        onSurfaceVariant = Muted,
        surfaceContainer = Color(0xFFF1EDE2),
        outline = Color(0xFFB9B2A3),
    )

private val Dark =
    darkColorScheme(
        primary = DeepGreenLight,
        onPrimary = Color(0xFF0E2A22),
        primaryContainer = Color(0xFF244A40),
        onPrimaryContainer = Color(0xFFD5E6DF),
        secondary = WarmAccentLight,
        onSecondary = Color(0xFF4A2410),
        secondaryContainer = Color(0xFF6B3A1E),
        onSecondaryContainer = Color(0xFFF7DCCB),
        background = NightBackground,
        onBackground = NightInk,
        surface = NightSurface,
        onSurface = NightInk,
        surfaceVariant = Color(0xFF2B3430),
        onSurfaceVariant = Color(0xFFB4BFBA),
        surfaceContainer = Color(0xFF232C29),
    )

/** Calm palette from the spec. No dynamic colour, so the lab look stays consistent. */
@Composable
fun MentalCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = Typography,
        content = content,
    )
}
