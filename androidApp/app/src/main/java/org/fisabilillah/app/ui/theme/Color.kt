package org.fisabilillah.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * # Amanah Design System — colour primitives
 *
 * The palette is taken directly from the approved interactive prototype
 * (`amanah-ui-prototype/index.html`), so the built application and the design source
 * cannot drift apart. Hex values below are the prototype's CSS custom properties.
 *
 * The direction is deep mineral blue and ink navy, a muted teal, warm sand and pearl
 * neutrals, and a desaturated violet reserved for learning. What is deliberately absent:
 * the emerald-and-gold that Islamic products default to, crescents, and ornament. A
 * palette that announces its identity ends up feeling like a theme applied over somebody
 * else's product; this one is meant to feel like an institution that happens to be built
 * by and for Muslims.
 *
 * Nothing here is used directly by a screen. Semantic roles are assigned in `Theme.kt`,
 * and screens read `MaterialTheme.colorScheme` or `AmanahTheme.status`.
 */

// ── Ink: the primary. Deep mineral blue through to near-black navy. ───────────
internal val Ink00 = Color(0xFF061422)
internal val Ink05 = Color(0xFF0A1B2D)
internal val Ink10 = Color(0xFF0C1D28) // prototype dark canvas
internal val Ink20 = Color(0xFF0F2742) // prototype --ink
internal val Ink30 = Color(0xFF173956) // prototype --ink-2
internal val Ink40 = Color(0xFF204A6B)
internal val Ink50 = Color(0xFF2F6285)
internal val Ink60 = Color(0xFF4A809F)
internal val Ink70 = Color(0xFF7BA6BE)
internal val Ink80 = Color(0xFFA9C6D6)
internal val Ink90 = Color(0xFFD5E6E3) // prototype dark --ink-2
internal val Ink95 = Color(0xFFEFF7F6) // prototype dark --ink

// ── Teal: service, discovery, safeguard-affirmative. ─────────────────────────
internal val Teal20 = Color(0xFF11403F)
internal val Teal30 = Color(0xFF173B3E) // prototype dark --teal-soft
internal val Teal40 = Color(0xFF215857)
internal val Teal50 = Color(0xFF2D706F) // prototype --teal
internal val Teal60 = Color(0xFF3F8C8A)
internal val Teal80 = Color(0xFFA8D2D1)
internal val Teal90 = Color(0xFFDCECEB) // prototype --teal-soft
internal val Teal95 = Color(0xFFEDF6F5)

// ── Violet: learning. Muted, never decorative. ───────────────────────────────
internal val Violet20 = Color(0xFF2A2C48)
internal val Violet30 = Color(0xFF292B48) // prototype dark --violet-soft
internal val Violet40 = Color(0xFF453F63)
internal val Violet50 = Color(0xFF635B8D) // prototype --violet
internal val Violet80 = Color(0xFFC5BFDE)
internal val Violet90 = Color(0xFFECE9F5) // prototype --violet-soft

// ── Neutrals: sand, pearl, stone. ────────────────────────────────────────────
internal val Sand20 = Color(0xFF162D38) // prototype dark --sand
internal val Sand30 = Color(0xFF28414C) // prototype dark --line
internal val Sand40 = Color(0xFF4A5A63)
internal val Sand50 = Color(0xFF68737D) // prototype --stone
internal val Sand60 = Color(0xFF9FB0B6) // prototype dark --stone
internal val Sand80 = Color(0xFFDCE2E4) // prototype --line
internal val Sand90 = Color(0xFFF4EFE6) // prototype --sand
internal val Sand95 = Color(0xFFF7F9F7)
internal val Sand99 = Color(0xFFFBFAF7) // prototype --pearl
internal val White = Color(0xFFFFFFFF)
internal val Parchment = Color(0xFFE8E2D8)
internal val CrimsonInk = Color(0xFF5A0A14)

internal val Slate10 = Color(0xFF10222F) // prototype dark --pearl
internal val Slate15 = Color(0xFF132936) // prototype dark --white (raised surface)

// ── Status. Sage, amber, rust, crimson — never neon. ─────────────────────────
internal val Sage30 = Color(0xFF1E4436)
internal val Sage40 = Color(0xFF3B7358) // prototype --success
internal val Sage80 = Color(0xFFA7D4BF)
internal val Sage90 = Color(0xFFD9EDE3)

internal val Amber30 = Color(0xFF4A2F0A)
internal val Amber40 = Color(0xFFA66A20) // prototype --warning
internal val Amber80 = Color(0xFFE9C48C)
internal val Amber90 = Color(0xFFF8E9D2)

internal val Rust40 = Color(0xFF8A4A2E)
internal val Rust90 = Color(0xFFF6DFD2)

internal val Crimson20 = Color(0xFF3E1219)
internal val Crimson30 = Color(0xFF6B2A33)
internal val Crimson40 = Color(0xFF943D46) // prototype --danger
internal val Crimson80 = Color(0xFFEBB2B8)
internal val Crimson90 = Color(0xFFF8DCDF)

/**
 * Semantic colours the Material scheme has no slot for.
 *
 * Held as a type rather than as loose constants so a screen cannot invent its own shade of
 * "verified gold" or "safeguard green", and so light, dark and high-contrast themes stay in
 * step with each other.
 *
 * A rule that applies to every field here: **colour never carries meaning on its own.**
 * Each of these is paired in the components with an icon, a text label, and a screen-reader
 * description, so the interface reads identically to somebody who cannot distinguish
 * amber from sage.
 */
internal data class StatusColors(
    // Safeguards
    val safeguardActive: Color,
    val safeguardActiveContainer: Color,
    val safeguardOnContainer: Color,
    // Verification
    val verified: Color,
    val verifiedContainer: Color,
    val verifiedOnContainer: Color,
    // Attention and risk
    val cautionContainer: Color,
    val cautionOnContainer: Color,
    val riskContainer: Color,
    val riskOnContainer: Color,
    // Completion
    val completeContainer: Color,
    val completeOnContainer: Color,
    // Oversight: a guardian, moderator, or third party is present
    val oversightContainer: Color,
    val oversightOnContainer: Color,
    // Learning surfaces
    val learningContainer: Color,
    val learningOnContainer: Color,
    // Live sessions: an audio or video room that is open right now
    val liveIndicator: Color,
    val liveContainer: Color,
    val liveOnContainer: Color,
    // Privacy affordances
    val privateIndicator: Color,
)

internal val LightStatusColors = StatusColors(
    safeguardActive = Teal50,
    safeguardActiveContainer = Teal90,
    safeguardOnContainer = Teal20,
    verified = Ink30,
    verifiedContainer = Ink80.copy(alpha = 0.30f),
    verifiedOnContainer = Ink20,
    cautionContainer = Amber90,
    cautionOnContainer = Amber30,
    riskContainer = Crimson90,
    riskOnContainer = Crimson20,
    completeContainer = Sage90,
    completeOnContainer = Sage30,
    oversightContainer = Violet90,
    oversightOnContainer = Violet20,
    learningContainer = Violet90,
    learningOnContainer = Violet20,
    liveIndicator = Crimson40,
    liveContainer = Crimson90,
    liveOnContainer = Crimson20,
    privateIndicator = Sand50,
)

internal val DarkStatusColors = StatusColors(
    safeguardActive = Teal80,
    safeguardActiveContainer = Teal30,
    safeguardOnContainer = Teal95,
    verified = Ink80,
    verifiedContainer = Ink40,
    verifiedOnContainer = Ink95,
    cautionContainer = Amber30,
    cautionOnContainer = Amber90,
    riskContainer = Crimson30,
    riskOnContainer = Crimson90,
    completeContainer = Sage30,
    completeOnContainer = Sage90,
    oversightContainer = Violet30,
    oversightOnContainer = Violet90,
    learningContainer = Violet30,
    learningOnContainer = Violet90,
    liveIndicator = Crimson80,
    liveContainer = Crimson30,
    liveOnContainer = Crimson90,
    privateIndicator = Sand60,
)

/**
 * High contrast.
 *
 * Not a filter over the ordinary theme — a separate set of values that pushes every
 * container/content pair past 7:1. Offered because a service platform is used by elders
 * arranging hospital transport in bright daylight, which is not an edge case.
 */
internal val HighContrastStatusColors = StatusColors(
    safeguardActive = Teal20,
    safeguardActiveContainer = White,
    safeguardOnContainer = Ink00,
    verified = Ink00,
    verifiedContainer = White,
    verifiedOnContainer = Ink00,
    cautionContainer = White,
    cautionOnContainer = Color(0xFF3A2400),
    riskContainer = White,
    riskOnContainer = Color(0xFF5A0A14),
    completeContainer = White,
    completeOnContainer = Color(0xFF0B2E1F),
    oversightContainer = White,
    oversightOnContainer = Color(0xFF1E1B36),
    learningContainer = White,
    learningOnContainer = Color(0xFF1E1B36),
    liveIndicator = Color(0xFF5A0A14),
    liveContainer = White,
    liveOnContainer = Color(0xFF5A0A14),
    privateIndicator = Ink00,
)
