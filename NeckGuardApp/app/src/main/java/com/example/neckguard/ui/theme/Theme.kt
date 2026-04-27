package com.example.neckguard.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun NeckGuardTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary       = Teal,
        onPrimary     = White,
        secondary     = TealSoft,
        onSecondary   = White,
        tertiary      = Amber,
        onTertiary    = White,
        background    = Bg,
        onBackground  = Slate,
        surface       = White,
        onSurface     = Slate,
        surfaceVariant = TealWash,
        onSurfaceVariant = SlateMuted,
        error         = Alert,
        onError       = White,
        errorContainer = PoorBg,
        onErrorContainer = PoorText,
        primaryContainer = TealWash,
        onPrimaryContainer = Teal,
        secondaryContainer = ModBg,
        onSecondaryContainer = ModText,
        tertiaryContainer = GoodBg,
        onTertiaryContainer = GoodText,
        outline       = CardBorder,
        outlineVariant = Sand
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = HeroBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !com.example.neckguard.ui.theme.isDarkModeState
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}