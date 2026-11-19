package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ClassicMonochromeScheme =
  lightColorScheme(
    primary = InkBlack,
    onPrimary = PureWhite,
    primaryContainer = SoftAlabaster,
    onPrimaryContainer = InkBlack,
    secondary = InkCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = SubtleSurface,
    onSecondaryContainer = InkBlack,
    tertiary = SuccessInk,
    onTertiary = PureWhite,
    tertiaryContainer = SuccessSurface,
    onTertiaryContainer = SuccessInk,
    background = PaperBackground,
    onBackground = InkBlack,
    surface = PureWhite,
    onSurface = InkBlack,
    surfaceVariant = SoftAlabaster,
    onSurfaceVariant = InkGraphite,
    surfaceContainerLow = PaperBackground,
    surfaceContainer = PureWhite,
    surfaceContainerHigh = SoftAlabaster,
    outline = StrongBorder,
    outlineVariant = HairlineBorder,
    error = DangerInk,
    onError = PureWhite,
    errorContainer = DangerSurface,
    onErrorContainer = DangerInk,
  )

val LeadXShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun LeadXTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ClassicMonochromeScheme,
    typography = LeadXTypography,
    shapes = LeadXShapes,
    content = content,
  )
}
