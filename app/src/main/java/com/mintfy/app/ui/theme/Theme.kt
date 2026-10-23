package com.mintfy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dark theme only (Figma: "Dark theme only · Material Design 3"). No light scheme, no dynamic colour.
private val MintfyColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = TextOnGreen,
    primaryContainer = ChartGreen35,
    onPrimaryContainer = BrandPrimary,
    inversePrimary = ChartGreen50,
    secondary = BrandPrimary,
    onSecondary = TextOnGreen,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusInfo,
    onTertiary = BgBlack,
    background = BgBlack,
    onBackground = TextPrimary,
    surface = BgBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = BgBlack,
    surfaceContainerLow = BgDeep,
    surfaceContainer = SurfacePrimary,
    surfaceContainerHigh = SurfaceCard,
    surfaceContainerHighest = SurfaceElevated,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgBlack,
    error = StatusError,
    onError = TextPrimary,
    errorContainer = SurfaceCard,
    onErrorContainer = StatusError,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    scrim = BgBlack,
)

// Figma radii: chips 8, inputs 12, cards 16, pill buttons 28.
private val MintfyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MintfyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MintfyColorScheme,
        typography = Typography,
        shapes = MintfyShapes,
        content = content
    )
}
