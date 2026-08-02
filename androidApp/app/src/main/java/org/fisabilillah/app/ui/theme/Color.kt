package org.fisabilillah.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * Built around a muted olive-teal and a warm sand, with a single restrained gold reserved
 * for verification marks. Deliberately not the emerald-and-gold that Islamic apps default
 * to: a colour scheme that shouts its identity ends up feeling like a theme applied to
 * someone else's product rather than a considered surface of its own.
 *
 * Every pairing below was chosen against WCAG AA for body text (4.5:1) and large text
 * (3:1). `ContrastCheckTest` in the unit tests asserts it rather than trusting the eye.
 */

// ── Primary: a deep, desaturated teal-green. Calm, not clinical. ───────────────
internal val Sabr10 = Color(0xFF001410)
internal val Sabr20 = Color(0xFF00291F)
internal val Sabr30 = Color(0xFF0A3F32)
internal val Sabr40 = Color(0xFF1F5747)
internal val Sabr50 = Color(0xFF35705E)
internal val Sabr60 = Color(0xFF528B78)
internal val Sabr70 = Color(0xFF71A793)
internal val Sabr80 = Color(0xFF91C3AE)
internal val Sabr90 = Color(0xFFACDFC9)
internal val Sabr95 = Color(0xFFC8F1DE)
internal val Sabr99 = Color(0xFFF3FFF8)

// ── Secondary: warm sand. Used for surfaces and quiet emphasis. ───────────────
internal val Sand10 = Color(0xFF1E1B16)
internal val Sand20 = Color(0xFF34302A)
internal val Sand30 = Color(0xFF4B4640)
internal val Sand40 = Color(0xFF635D56)
internal val Sand60 = Color(0xFF95908A)
internal val Sand80 = Color(0xFFCFC8BF)
internal val Sand90 = Color(0xFFEBE4DA)
internal val Sand95 = Color(0xFFF7F1E7)
internal val Sand99 = Color(0xFFFFFBF3)

// ── Tertiary: a dusk blue, for informational surfaces. ────────────────────────
internal val Dusk20 = Color(0xFF102030)
internal val Dusk30 = Color(0xFF1B3448)
internal val Dusk40 = Color(0xFF2C4A62)
internal val Dusk80 = Color(0xFFB0CBE2)
internal val Dusk90 = Color(0xFFCFE3F4)

// ── Verification gold. One accent, used sparingly and never decoratively. ─────
internal val Amanah40 = Color(0xFF7A5A12)
internal val Amanah60 = Color(0xFFA97F26)
internal val Amanah80 = Color(0xFFE8C67A)
internal val Amanah90 = Color(0xFFF6E4BA)

// ── Error: a warm brick rather than a siren red. ─────────────────────────────
internal val Error20 = Color(0xFF521B15)
internal val Error30 = Color(0xFF6E2A22)
internal val Error40 = Color(0xFF8C3E33)
internal val Error80 = Color(0xFFF2B7AC)
internal val Error90 = Color(0xFFFFDAD3)

// ── Neutrals ─────────────────────────────────────────────────────────────────
internal val Ink05 = Color(0xFF0C0F0E)
internal val Ink10 = Color(0xFF131816)
internal val Ink15 = Color(0xFF1B211F)
internal val Ink20 = Color(0xFF262D2B)
internal val Ink25 = Color(0xFF313937)
internal val Ink80 = Color(0xFFC4CCC9)
internal val Ink90 = Color(0xFFE0E8E5)
internal val Ink95 = Color(0xFFEFF4F2)
internal val White = Color(0xFFFFFFFF)

/**
 * Colours the design system needs that Material's scheme has no slot for.
 *
 * Kept as a typed object rather than scattered literals so a screen cannot invent its own
 * shade of "safeguard green" — and so both themes stay in step.
 */
internal data class SafeguardColors(
    val safeguardActive: Color,
    val safeguardActiveContainer: Color,
    val safeguardOnContainer: Color,
    val verified: Color,
    val verifiedContainer: Color,
    val verifiedOnContainer: Color,
    val cautionContainer: Color,
    val cautionOnContainer: Color,
    val privateIndicator: Color,
    val oversightContainer: Color,
    val oversightOnContainer: Color,
)

internal val LightSafeguardColors = SafeguardColors(
    safeguardActive = Sabr40,
    safeguardActiveContainer = Sabr95,
    safeguardOnContainer = Sabr20,
    verified = Amanah40,
    verifiedContainer = Amanah90,
    verifiedOnContainer = Color(0xFF3D2C00),
    cautionContainer = Color(0xFFFDECC8),
    cautionOnContainer = Color(0xFF4A3200),
    privateIndicator = Sand40,
    oversightContainer = Dusk90,
    oversightOnContainer = Dusk20,
)

internal val DarkSafeguardColors = SafeguardColors(
    safeguardActive = Sabr80,
    safeguardActiveContainer = Sabr30,
    safeguardOnContainer = Sabr95,
    verified = Amanah80,
    verifiedContainer = Color(0xFF473300),
    verifiedOnContainer = Amanah90,
    cautionContainer = Color(0xFF3F2E00),
    cautionOnContainer = Color(0xFFFDECC8),
    privateIndicator = Sand80,
    oversightContainer = Dusk30,
    oversightOnContainer = Dusk90,
)
