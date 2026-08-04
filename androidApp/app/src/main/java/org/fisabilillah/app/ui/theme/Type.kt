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
    // The prototype's --r-xs through --r-xl. Softer than the previous scale at every
    // step, and the top of it is now 38dp rather than 28: the large cards are meant to
    // read as objects sitting on the surface rather than as regions divided out of it.
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

/**
 * # Depth
 *
 * The prototype gained a three-step shadow scale in this revision, where before it had one
 * flat card treatment. Translated to Compose's dp elevation rather than to literal shadow
 * offsets, because Android draws elevation itself and a hand-rolled shadow would not match
 * the platform's own surfaces sitting next to it.
 *
 * Restraint is the rule. Level three exists for exactly one thing at a time — the sheet or
 * dialogue the reader is currently answering — and a screen with two of them has lost the
 * plot about what the reader is being asked.
 */
internal data class Elevation(
    /** Flat. Most cards. */
    val flat: androidx.compose.ui.unit.Dp = 0.dp,
    /** `--shadow-1`. A card that can be tapped. */
    val raised: androidx.compose.ui.unit.Dp = 2.dp,
    /** `--shadow-2`. A menu, a popover, the mobile dock. */
    val floating: androidx.compose.ui.unit.Dp = 8.dp,
    /** `--shadow-3`. A modal sheet. One at a time. */
    val modal: androidx.compose.ui.unit.Dp = 16.dp,
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

/**
 * # Purposeful Motion
 *
 * Durations and easings, held as tokens so that a screen cannot invent its own timing.
 *
 * Motion here has one job: to explain a state change. It is never entertainment. There is
 * no celebratory animation anywhere in this product — a confetti burst when someone
 * completes an act of service would turn a private matter between a person and their Lord
 * into a performance, which is the exact instinct this platform is built to avoid.
 *
 * Every value is multiplied by zero when the reader has asked the system to reduce motion.
 * See [Motion.scaled].
 */
internal data class Motion(
    /** A control acknowledging a press. Barely perceptible, and that is the point. */
    val instant: Int = 90,
    /** A chip, switch, or badge changing state. */
    val quick: Int = 140,
    /** The prototype's own transition: 180ms on a decelerating curve. */
    val standard: Int = 180,
    /** A sheet, drawer, or expanding card. */
    val emphasised: Int = 260,
    /** A whole-screen transition, or a safeguard indicator settling. */
    val deliberate: Int = 340,

    /** Whether the reader has asked for reduced motion. Set by the shell from settings. */
    val reduced: Boolean = false,
) {
    /** The duration to actually use. Zero when motion is reduced, so transitions cut. */
    fun scaled(durationMillis: Int): Int = if (reduced) 0 else durationMillis

    /**
     * The prototype's easing: `cubic-bezier(.2, .8, .2, 1)`. Decelerating, so a thing
     * arrives quickly and settles gently rather than sliding to a stop.
     */
    val easing: androidx.compose.animation.core.CubicBezierEasing
        get() = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** For something leaving. Slightly faster than arriving; exits should not linger. */
    val exitEasing: androidx.compose.animation.core.CubicBezierEasing
        get() = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 1f, 1f)
}
