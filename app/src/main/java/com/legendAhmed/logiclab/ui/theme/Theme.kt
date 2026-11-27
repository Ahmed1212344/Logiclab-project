package com.legendAhmed.logiclab.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Violet,
    secondary = Blue,
    error = Danger,
    onPrimary = Color.White,
    onSecondary = Color.White
)

private val LightColors = lightColorScheme(
    primary = Violet,
    secondary = Blue,
    error = Danger,
    onPrimary = Color.White,
    onSecondary = Color.White
)

/**
 * Wraps the whole app.
 * Defaults to [isSystemInDarkTheme] unless overridden by the in-app toggle.
 */
@Composable
fun LogicLabTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = remember(dark) { if (dark) DarkColors else LightColors }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Keep status bar icons light (white) because the top Brand header is dark in both themes
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}