package com.smartaccountant.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F7),
    onPrimaryContainer = Color(0xFF12294A),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F0F2),
    onSecondaryContainer = Color(0xFF073A3F),
    tertiary = ActionOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8CC),
    onTertiaryContainer = Color(0xFF4A2A00),
    background = SurfaceLight,
    onBackground = Color(0xFF1A2332),
    surface = Color.White,
    onSurface = Color(0xFF1A2332),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF5A6B80),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5F9),
    surfaceContainer = Color(0xFFECEFF4),
    surfaceContainerHigh = Color(0xFFE4E9F0),
    surfaceContainerHighest = Color(0xFFDCE2EB),
    outline = Color(0xFFB6C2D2),
    outlineVariant = Color(0xFFD8E0EA),
    error = DebitRed
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB3E8),
    onPrimary = Color(0xFF0E1F3D),
    primaryContainer = Color(0xFF27467A),
    onPrimaryContainer = Color(0xFFDCE6F7),
    secondary = Color(0xFF7FD4DC),
    onSecondary = Color(0xFF06333A),
    secondaryContainer = Color(0xFF0E4A52),
    onSecondaryContainer = Color(0xFFD2F0F2),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3A2400),
    tertiaryContainer = Color(0xFF5C3D00),
    onTertiaryContainer = Color(0xFFFFE8CC),
    background = SurfaceDark,
    onBackground = Color(0xFFE6EBF4),
    surface = Color(0xFF141C2E),
    onSurface = Color(0xFFE6EBF4),
    surfaceVariant = Color(0xFF1E2A40),
    onSurfaceVariant = Color(0xFF9AA9BE),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF141C2E),
    surfaceContainer = Color(0xFF1A2438),
    surfaceContainerHigh = Color(0xFF22304A),
    surfaceContainerHighest = Color(0xFF2A3A58),
    outline = Color(0xFF3D4E6B),
    outlineVariant = Color(0xFF2A3A58),
    error = Color(0xFFFF8A80)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SmartAccountantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
