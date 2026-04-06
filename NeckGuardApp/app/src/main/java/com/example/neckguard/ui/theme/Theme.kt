package com.example.neckguard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ForestPrimaryCTA,
    secondary = ForestPrimaryCTA,
    tertiary = ForestPrimaryCTA,
    background = ForestDarkBackground,
    surface = ForestSurface,
    surfaceVariant = ForestSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = ForestTextPrimary,
    onSurface = ForestTextPrimary,
    onSurfaceVariant = ForestTextSecondary,
)

// We intentionally discard the Light scheme to force the requested Dark Forest aesthetic.
private val LightColorScheme = DarkColorScheme

@Composable
fun NeckGuardTheme(
    darkTheme: Boolean = true, // Forced True
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Forced False
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}