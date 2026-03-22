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
import com.andrewwin.sumup.data.local.entities.AppThemeMode

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
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
}
