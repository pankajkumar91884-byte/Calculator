package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Black,
    secondary = AccentGreenLight,
    onSecondary = Black,
    background = DarkGreyBg,
    onBackground = TextWhite,
    surface = KeypadBg,
    onSurface = TextWhite,
    surfaceVariant = OperatorBg,
    onSurfaceVariant = AccentGreenLight,
    tertiary = CharcoalSurface,
    onTertiary = AccentGreenLight
)

private val LightColorScheme = lightColorScheme(
    primary = AccentGreenDark,
    onPrimary = TextWhite,
    secondary = AccentGreenDark,
    onSecondary = TextWhite,
    background = LightBg,
    onBackground = LightButtonText,
    surface = LightSurface,
    onSurface = LightButtonText,
    surfaceVariant = LightButton,
    onSurfaceVariant = AccentGreenDark,
    tertiary = LightButton,
    onTertiary = AccentGreenDark
)

@Composable
fun CalculatorVaultTheme(
    darkTheme: Boolean = true, // Force dark theme by default, but allow user setting override
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
