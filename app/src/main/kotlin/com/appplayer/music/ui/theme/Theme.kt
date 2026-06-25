package com.appplayer.music.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = OnDarkPrimary,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,

    secondary = Violet40,
    onSecondary = OnDarkPrimary,
    secondaryContainer = Violet20,
    onSecondaryContainer = Violet90,

    tertiary = GoldAccent,
    onTertiary = DarkBg,

    background = DarkBg,
    onBackground = OnDarkPrimary,

    surface = DarkSurface,
    onSurface = OnDarkPrimary,

    surfaceVariant = DarkSurface2,
    onSurfaceVariant = OnDarkSecondary,

    surfaceTint = NeonViolet,

    error = ErrorRed,
    onError = OnDarkPrimary,

    outline = OnDarkTertiary,
    outlineVariant = DarkSurface3
)

@Composable
fun AppPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
