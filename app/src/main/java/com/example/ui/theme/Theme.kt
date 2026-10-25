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
    onPrimary = Color(0xFF0F121E),
    primaryContainer = Color(0xFF292454),
    onPrimaryContainer = Color(0xFFE2DDFF),
    secondary = Color(0xFF1E283E),
    onSecondary = AuraTextPrimaryDark,
    tertiary = Color(0xFF193238),
    onTertiary = Color(0xFFD5FAF5),
    background = AuraBgDark,
    onBackground = AuraTextPrimaryDark,
    surface = AuraSurfaceDark,
    onSurface = AuraTextPrimaryDark,
    surfaceVariant = Color(0xFF22283D),
    onSurfaceVariant = AuraTextSecondaryDark,
    outline = Color(0xFF333B54),
    outlineVariant = Color(0xFF262D42)
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
