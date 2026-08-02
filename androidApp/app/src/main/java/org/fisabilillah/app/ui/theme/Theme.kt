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
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Sabr40,
    onPrimary = White,
    primaryContainer = Sabr95,
    onPrimaryContainer = Sabr20,
    inversePrimary = Sabr80,

    secondary = Sand40,
    onSecondary = White,
    secondaryContainer = Sand90,
    onSecondaryContainer = Sand20,

    tertiary = Dusk40,
    onTertiary = White,
    tertiaryContainer = Dusk90,
    onTertiaryContainer = Dusk20,

    error = Error40,
    onError = White,
    errorContainer = Error90,
    onErrorContainer = Error20,

    background = Sand99,
    onBackground = Ink15,
    surface = Sand99,
    onSurface = Ink15,
    surfaceVariant = Sand95,
    onSurfaceVariant = Sand30,
    surfaceContainerLowest = White,
    surfaceContainerLow = Sand99,
    surfaceContainer = Sand95,
    surfaceContainerHigh = Sand90,
    surfaceContainerHighest = Color(0xFFE4DCD1),

    outline = Sand60,
    outlineVariant = Sand80,
    scrim = Ink05,
    inverseSurface = Ink20,
    inverseOnSurface = Sand95,
)

private val DarkScheme = darkColorScheme(
    primary = Sabr80,
    onPrimary = Sabr20,
    primaryContainer = Sabr30,
    onPrimaryContainer = Sabr95,
    inversePrimary = Sabr40,

    secondary = Sand80,
    onSecondary = Sand20,
    secondaryContainer = Sand30,
    onSecondaryContainer = Sand90,

    tertiary = Dusk80,
    onTertiary = Dusk20,
    tertiaryContainer = Dusk30,
    onTertiaryContainer = Dusk90,

    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,

    background = Ink05,
    onBackground = Ink90,
    surface = Ink05,
    onSurface = Ink90,
    surfaceVariant = Ink20,
    onSurfaceVariant = Ink80,
    surfaceContainerLowest = Color(0xFF070908),
    surfaceContainerLow = Ink10,
    surfaceContainer = Ink15,
    surfaceContainerHigh = Ink20,
    surfaceContainerHighest = Ink25,

    outline = Sand40,
    outlineVariant = Ink25,
    scrim = Color(0xFF000000),
    inverseSurface = Ink90,
    inverseOnSurface = Ink15,
)

internal val LocalSafeguardColors: ProvidableCompositionLocal<SafeguardColors> =
    staticCompositionLocalOf { LightSafeguardColors }

internal val LocalSpacing: ProvidableCompositionLocal<Spacing> =
    staticCompositionLocalOf { Spacing() }

/**
 * The application theme.
 *
 * Dynamic colour is deliberately not offered. A wallpaper-derived palette would put the
 * verification mark, the safeguard indicator and the "moderator present" banner at the
 * mercy of whatever the user's home screen happens to look like — and those three things
 * have to read the same way on every device for people to learn to trust them.
 */
@Composable
internal fun FiSabilillahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val safeguardColors = if (darkTheme) DarkSafeguardColors else LightSafeguardColors

    CompositionLocalProvider(
        LocalSafeguardColors provides safeguardColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FiSabilillahTypography,
            shapes = FiSabilillahShapes,
            content = content,
        )
    }
}

/** Convenience accessors, so screens read `theme.safeguard.verified` rather than a local. */
internal object FiSabilillahTheme {
    val safeguard: SafeguardColors
        @Composable @ReadOnlyComposable get() = LocalSafeguardColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
}
