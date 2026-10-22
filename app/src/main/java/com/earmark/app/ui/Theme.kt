package com.earmark.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0E0F12)
    val Surface = Color(0xFF17191E)
    val SurfaceHi = Color(0xFF22252C)
    val Accent = Color(0xFFFF9F1C)
    val OnAccent = Color(0xFF1B1200)
    val Text = Color(0xFFF3F3F4)
    val Muted = Color(0xFFA2A6AF)
    val Faint = Color(0xFF6C717C)
    val Line = Color(0xFF2A2D35)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    secondary = Palette.Accent,
    onSecondary = Palette.OnAccent,
    secondaryContainer = Color(0xFF3A2A12),
    onSecondaryContainer = Palette.Accent,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHi,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHi,
    surfaceContainerHighest = Palette.SurfaceHi,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
)

private val base = Typography()
private val type = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun EarmarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
