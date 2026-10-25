package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AuraAccent,
    onPrimary = Color.White,
    primaryContainer = AuraPastelLavender,
    onPrimaryContainer = AuraTextPrimaryLight,
    secondary = AuraIceBlue,
    onSecondary = AuraTextPrimaryLight,
    tertiary = AuraSoftCyan,
    onTertiary = AuraTextPrimaryLight,
    background = AuraBgLight,
    onBackground = AuraTextPrimaryLight,
    surface = AuraPearlWhite,
    onSurface = AuraTextPrimaryLight,
    surfaceVariant = Color(0xFFECEFFC),
    onSurfaceVariant = AuraTextSecondaryLight,
    outline = Color(0xFFD6DBEC),
    outlineVariant = Color(0xFFE2E7F6)
)

private val DarkColorScheme = darkColorScheme(
    primary = AuraAccentDark,
    onPrimary = Color(0xFF161C30),
    primaryContainer = Color(0xFF38365F),
    onPrimaryContainer = Color(0xFFE2DDFF),
    secondary = Color(0xFF354563),
    onSecondary = AuraTextPrimaryDark,
    tertiary = Color(0xFF254C59),
    onTertiary = Color(0xFFD5FAF5),
    background = AuraBgDark,
    onBackground = AuraTextPrimaryDark,
    surface = AuraSurfaceDark,
    onSurface = AuraTextPrimaryDark,
    surfaceVariant = Color(0xFF35425C),
    onSurfaceVariant = AuraTextSecondaryDark,
    outline = Color(0xFF65718D),
    outlineVariant = Color(0xFF42516B),
    surfaceDim = Color(0xFF1E2940),
    surfaceBright = Color(0xFF42516E),
    surfaceContainerLowest = Color(0xFF202B42),
    surfaceContainerLow = Color(0xFF293650),
    surfaceContainer = Color(0xFF30405C),
    surfaceContainerHigh = Color(0xFF394A68),
    surfaceContainerHighest = Color(0xFF435574),
    inverseSurface = Color(0xFFE2E9FF),
    inverseOnSurface = Color(0xFF20283F)
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "light", // "light", "dark", "system"
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "system" -> isSystemInDarkTheme()
        else -> false // light is default per requirements
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
