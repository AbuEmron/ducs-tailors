package org.fisabilillah.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Type.
 *
 * The device's default family is used on purpose. Bundling a display face would add
 * weight, complicate Arabic fallback, and — more to the point — this is an interface
 * people read while arranging a lift to a hospital appointment, not a brand exercise.
 *
 * Line heights are generous throughout. A person deciding whether to hand a stranger their
 * address is reading carefully, and cramped text makes careful reading harder.
 */
private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = lineHeightStyle,
)

internal val FiSabilillahTypography: Typography = Typography(
    displayLarge = style(48, 56, FontWeight.Light, (-0.5)),
    displayMedium = style(38, 46, FontWeight.Light, (-0.25)),
    displaySmall = style(30, 38, FontWeight.Normal),

    headlineLarge = style(28, 36, FontWeight.Normal),
    headlineMedium = style(24, 32, FontWeight.Normal),
    headlineSmall = style(20, 28, FontWeight.Medium),

    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(16, 26, FontWeight.Normal, 0.15),
    bodyMedium = style(14, 22, FontWeight.Normal, 0.2),
    bodySmall = style(12, 18, FontWeight.Normal, 0.3),

    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5),
)

/**
 * Soft geometry. Nothing is a perfect circle except a genuine pill, and nothing is a hard
 * rectangle — the corners are what make dense safety information feel approachable rather
 * than administrative.
 */
internal val FiSabilillahShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Spacing, on a four-point grid.
 *
 * Held as a type rather than as loose dimension constants so that a screen written in six
 * months lands on the same rhythm as the ones written today.
 */
internal data class Spacing(
    val hairline: androidx.compose.ui.unit.Dp = 1.dp,
    val xxs: androidx.compose.ui.unit.Dp = 4.dp,
    val xs: androidx.compose.ui.unit.Dp = 8.dp,
    val sm: androidx.compose.ui.unit.Dp = 12.dp,
    val md: androidx.compose.ui.unit.Dp = 16.dp,
    val lg: androidx.compose.ui.unit.Dp = 24.dp,
    val xl: androidx.compose.ui.unit.Dp = 32.dp,
    val xxl: androidx.compose.ui.unit.Dp = 48.dp,
    val xxxl: androidx.compose.ui.unit.Dp = 64.dp,

    /** The gutter used on every screen edge. */
    val screenHorizontal: androidx.compose.ui.unit.Dp = 20.dp,

    /** Android's accessibility floor for anything tappable. Never go below it. */
    val minimumTouchTarget: androidx.compose.ui.unit.Dp = 48.dp,
)
