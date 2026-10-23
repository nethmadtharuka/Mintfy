package com.mintfy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mintfy.app.R

// res/font/inter.ttf is the Inter variable font (SIL OFL, see assets/licenses/inter-OFL.txt).
// The weight axis has to be set explicitly, otherwise every weight renders as the default 400.
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

// Figma uses Inter's auto line height (about 1.2x) and no letter spacing.
private fun style(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.2f).sp,
    letterSpacing = 0.sp,
)

// Figma "Typography (Inter)" scale, plus the sizes the screens use (top bar 22, subtitle 15, input 17, error 13).
val Typography = Typography(
    displaySmall = style(40, FontWeight.Bold),      // Display
    headlineMedium = style(28, FontWeight.Bold),    // Headline
    headlineSmall = style(22, FontWeight.Bold),     // Top bar title
    titleLarge = style(20, FontWeight.SemiBold),    // Title
    titleMedium = style(16, FontWeight.SemiBold),   // Full-width buttons
    titleSmall = style(17, FontWeight.Medium),      // Input value
    bodyLarge = style(16, FontWeight.Normal),      // Body
    bodyMedium = style(15, FontWeight.Normal),     // Screen subtitle
    bodySmall = style(12, FontWeight.Normal),      // Caption, input label
    labelLarge = style(14, FontWeight.SemiBold),    // Compact buttons
    labelMedium = style(13, FontWeight.Medium),     // Error, chips
    labelSmall = style(11, FontWeight.Normal),
)
