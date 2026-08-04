package org.fisabilillah.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * # Amanah Design System — themes
 *
 * Three schemes, not two: light, dark, and a genuine high-contrast mode that is a distinct
 * set of values rather than a filter.
 *
 * Dynamic colour is deliberately not offered. Wallpaper-derived palettes would put the
 * verification mark, the safeguard indicator and the "a moderator is in this conversation"
 * banner at the mercy of whatever the user's home screen happens to look like, and those
 * three have to read identically on every device for people to learn to trust them.
 */

private val LightScheme = lightColorScheme(
    // Primary is the deep harbour blue rather than the teal. Body text is Ink10, which is
    // a different colour from the primary for the first time — previously the two were
    // the same value, so a heading and a button label were indistinguishable in a
    // screenshot and the hierarchy came entirely from weight.
    primary = Ink20,
    onPrimary = White,
    primaryContainer = Teal90,
    onPrimaryContainer = Ink20,
    inversePrimary = Ink80,

    secondary = Teal50,
    onSecondary = White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal20,

    // Tan, not violet. Violet is spoken for: it means learning, and a tertiary that also
    // meant learning left nothing for organisations.
    tertiary = Tan50,
    onTertiary = White,
    tertiaryContainer = Tan90,
    onTertiaryContainer = Tan20,

    error = Crimson40,
    onError = White,
    errorContainer = Crimson90,
    onErrorContainer = Crimson20,

    background = Sand95,
    onBackground = Ink10,
    surface = White,
    onSurface = Ink10,
    surfaceVariant = Sand90,
    onSurfaceVariant = Sand50,
    surfaceContainerLowest = White,
    surfaceContainerLow = Sand99,
    surfaceContainer = Sand95,
    surfaceContainerHigh = Sand90,
    surfaceContainerHighest = Parchment,

    outline = Sand50,
    outlineVariant = Sand80,
    scrim = Ink00,
    inverseSurface = Ink20,
    inverseOnSurface = Sand95,
)

private val DarkScheme = darkColorScheme(
    primary = Ink90,
    onPrimary = Ink20,
    primaryContainer = Ink30,
    onPrimaryContainer = Ink95,
    inversePrimary = Ink30,

    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal95,

    tertiary = Tan80,
    onTertiary = Tan20,
    tertiaryContainer = Tan30,
    onTertiaryContainer = Tan90,

    error = Crimson80,
    onError = Crimson20,
    errorContainer = Crimson30,
    onErrorContainer = Crimson90,

    background = Ink05,
    onBackground = Ink95,
    surface = Slate10,
    onSurface = Ink95,
    surfaceVariant = Sand20,
    onSurfaceVariant = Sand60,
    surfaceContainerLowest = Ink00,
    surfaceContainerLow = Slate10,
    surfaceContainer = Slate15,
    surfaceContainerHigh = Sand20,
    surfaceContainerHighest = Sand30,

    outline = Sand60,
    outlineVariant = Sand30,
    scrim = Ink00,
    inverseSurface = Ink95,
    inverseOnSurface = Ink10,
)

/** Every pair here clears 7:1. Used when the reader has asked the system for more contrast. */
private val HighContrastScheme = lightColorScheme(
    primary = Ink00,
    onPrimary = White,
    primaryContainer = White,
    onPrimaryContainer = Ink00,
    secondary = Teal20,
    onSecondary = White,
    secondaryContainer = White,
    onSecondaryContainer = Ink00,
    tertiary = Violet20,
    onTertiary = White,
    tertiaryContainer = White,
    onTertiaryContainer = Violet20,
    error = CrimsonInk,
    onError = White,
    errorContainer = White,
    onErrorContainer = CrimsonInk,
    background = White,
    onBackground = Ink00,
    surface = White,
    onSurface = Ink00,
    surfaceVariant = White,
    onSurfaceVariant = Ink00,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = White,
    outline = Ink00,
    outlineVariant = Ink00,
    scrim = Ink00,
    inverseSurface = Ink00,
    inverseOnSurface = White,
)

internal val LocalStatusColors: ProvidableCompositionLocal<StatusColors> =
    staticCompositionLocalOf { LightStatusColors }

internal val LocalSpacing: ProvidableCompositionLocal<Spacing> =
    staticCompositionLocalOf { Spacing() }

internal val LocalMotion: ProvidableCompositionLocal<Motion> =
    staticCompositionLocalOf { Motion() }

internal val LocalElevation: ProvidableCompositionLocal<Elevation> =
    staticCompositionLocalOf { Elevation() }

/**
 * The application theme.
 *
 * @param highContrast a separate scheme rather than a modifier over the others.
 */
@Composable
internal fun FiSabilillahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        highContrast -> HighContrastScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val statusColors = when {
        highContrast -> HighContrastStatusColors
        darkTheme -> DarkStatusColors
        else -> LightStatusColors
    }

    CompositionLocalProvider(
        LocalStatusColors provides statusColors,
        LocalSpacing provides Spacing(),
        LocalMotion provides Motion(),
        LocalElevation provides Elevation(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FiSabilillahTypography,
            shapes = FiSabilillahShapes,
            content = content,
        )
    }
}

/** The Amanah Design System's accessors. */
internal object FiSabilillahTheme {
    /**
     * Status colours. Named `safeguard` for continuity with the screens that already read
     * it; the type covers verification, risk, completion, oversight, learning and live
     * sessions as well.
     */
    val safeguard: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val elevation: Elevation
        @Composable @ReadOnlyComposable get() = LocalElevation.current
}
