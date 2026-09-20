package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SleekDarkColorScheme = darkColorScheme(
    primary = SleekIceBlue,
    onPrimary = SleekBackground,
    primaryContainer = SleekContainerSlate,
    onPrimaryContainer = SleekIceBlue,
    secondary = SleekMint,
    onSecondary = SleekBackground,
    secondaryContainer = SleekSurfaceCard,
    onSecondaryContainer = SleekMint,
    tertiary = SleekLavender,
    onTertiary = SleekBackground,
    background = SleekBackground,
    onBackground = SleekTextPrimary,
    surface = SleekSurface,
    onSurface = SleekTextPrimary,
    surfaceVariant = SleekSurfaceCard,
    onSurfaceVariant = SleekTextSecondary,
    outline = SleekBorder,
    error = SleekCoral,
    onError = SleekCoralContainer
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SleekDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SleekBackground.toArgb()
            window.navigationBarColor = SleekBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
