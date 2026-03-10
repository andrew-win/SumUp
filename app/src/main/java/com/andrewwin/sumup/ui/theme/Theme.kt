package com.andrewwin.sumup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LightBluePrimary,
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = ExpressivePrimary,

    background = DarkBackground,
    onBackground = OnSurfaceWhite,

    surface = DarkSurface,
    onSurface = OnSurfaceWhite,

    surfaceContainerLow = DarkBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF2B344A),

    surfaceVariant = Color(0xFF21293A),
    onSurfaceVariant = SecondaryText,

    outline = Color(0xFF444D66),
    outlineVariant = OutlineColor,

    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD9E2F1)
)

private val LightColorScheme = lightColorScheme(
)

@Composable
fun SumUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
}