package org.fisabilillah.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * # Amanah Design System — colour primitives
 *
 * The palette is taken directly from the approved interactive prototype
 * (`amanah-ui-prototype/index.html`), so the built application and the design source
 * cannot drift apart. Hex values marked `--x` below are that file's CSS custom properties
 * verbatim; the unmarked steps between them are interpolations, which exist because a
 * screen needs a pressed state and a disabled state and CSS did not have to.
 *
 * The direction is deep harbour blue, a muted teal, warm tan, and a desaturated violet
 * reserved for learning, over cool near-white neutrals. What is deliberately absent: the
 * emerald-and-gold that Islamic products default to, crescents, and ornament. A palette
 * that announces its identity ends up feeling like a theme applied over somebody else's
 * product; this one is meant to feel like an institution that happens to be built by and
 * for Muslims.
 *
 * **The second revision.** The first drew its primary from the teal. This one moves the
 * primary to the deep blue `--brand` and demotes the teal to secondary, which matters for
 * more than taste: the teal was doing double duty as both "this is the app" and "a
 * safeguard is active", and a colour that means two things means neither. Teal now means
 * only the second.
 *
 * Nothing here is used directly by a screen. Semantic roles are assigned in `Theme.kt`,
 * and screens read `MaterialTheme.colorScheme` or `FiSabilillahTheme.status`.
 */

// ── Ink: the primary. Deep harbour blue through to pale mist. ────────────────
internal val Ink00 = Color(0xFF040E18) // scrim
internal val Ink05 = Color(0xFF071724) // prototype dark --bg
internal val Ink10 = Color(0xFF0B1F32) // prototype --ink (body text on light)
internal val Ink20 = Color(0xFF0E4861) // prototype --brand
internal val Ink30 = Color(0xFF17607E)
internal val Ink40 = Color(0xFF24798F)
internal val Ink50 = Color(0xFF3F93A8)
internal val Ink60 = Color(0xFF64AEC0)
internal val Ink70 = Color(0xFF8FC7D4)
internal val Ink80 = Color(0xFFB8DCE4)
internal val Ink90 = Color(0xFFD7EAEE)
internal val Ink95 = Color(0xFFEDF5F5) // prototype dark --ink

// ── Teal: safeguard-affirmative, and only that. ──────────────────────────────
internal val Teal20 = Color(0xFF0E3A38)
internal val Teal30 = Color(0xFF163847) // prototype dark --soft-brand
internal val Teal40 = Color(0xFF1B5654)
internal val Teal50 = Color(0xFF246F6C) // prototype --brand-2
internal val Teal60 = Color(0xFF38908C)
internal val Teal80 = Color(0xFFA5D0CD)
internal val Teal90 = Color(0xFFE7F0F1) // prototype --soft-brand
internal val Teal95 = Color(0xFFF1F7F7)

// ── Violet: learning. Muted, never decorative. ───────────────────────────────
internal val Violet20 = Color(0xFF2A2542)
internal val Violet30 = Color(0xFF292A48) // prototype dark --soft-violet
internal val Violet40 = Color(0xFF494267)
internal val Violet50 = Color(0xFF6C638E) // prototype --violet
internal val Violet80 = Color(0xFFC8C2DB)
internal val Violet90 = Color(0xFFEFEDF6) // prototype --soft-violet

// ── Tan: the warm third. Organisations, and the few places the interface
//    should feel like paper rather than glass. New in this revision. ─────────
internal val Tan20 = Color(0xFF2E241B)
internal val Tan30 = Color(0xFF322A25) // prototype dark --soft-warm
internal val Tan40 = Color(0xFF5E4938)
internal val Tan50 = Color(0xFF8A6C52) // prototype --brand-3
internal val Tan80 = Color(0xFFD9C6B4)
internal val Tan90 = Color(0xFFF4EEE8) // prototype --soft-warm

// ── Neutrals: cool near-whites, not warm sand. ───────────────────────────────
internal val Sand20 = Color(0xFF14303F)
internal val Sand30 = Color(0xFF27404E)
internal val Sand40 = Color(0xFF48575F)
internal val Sand50 = Color(0xFF64727C) // prototype --muted
internal val Sand60 = Color(0xFF9DB0B7) // prototype dark --muted
internal val Sand80 = Color(0xFFE2E6E9) // prototype --line, flattened to opaque
internal val Sand90 = Color(0xFFEDF1F0)
internal val Sand95 = Color(0xFFF4F6F4) // prototype --bg
internal val Sand99 = Color(0xFFF8FAF9) // prototype --surface-2
internal val White = Color(0xFFFFFFFF) // prototype --surface
internal val Parchment = Color(0xFFE8ECEA)
internal val CrimsonInk = Color(0xFF5A0A14)

internal val Slate10 = Color(0xFF0D2434) // prototype dark --surface
internal val Slate15 = Color(0xFF102B3D) // prototype dark --surface-2

// ── Status. Sage, amber, rust, crimson — never neon. ─────────────────────────
internal val Sage30 = Color(0xFF1B4636)
internal val Sage40 = Color(0xFF3E7A5E) // prototype --safe
internal val Sage80 = Color(0xFFA9D6C1)
internal val Sage90 = Color(0xFFDCEEE5)

internal val Amber30 = Color(0xFF4E340C)
internal val Amber40 = Color(0xFFAF7A2A) // prototype --warn
internal val Amber80 = Color(0xFFEDC992)
internal val Amber90 = Color(0xFFF9ECD6)

internal val Rust40 = Color(0xFF8A4A2E)
internal val Rust90 = Color(0xFFF6DFD2)

internal val Crimson20 = Color(0xFF43151C)
internal val Crimson30 = Color(0xFF722C36)
internal val Crimson40 = Color(0xFFA04B55) // prototype --danger
internal val Crimson80 = Color(0xFFEDB6BB)
internal val Crimson90 = Color(0xFFF9E0E2)

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
    // Organisations: a masjid, charity or trust rather than an individual
    val organizationContainer: Color,
    val organizationOnContainer: Color,
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
    organizationContainer = Tan90,
    organizationOnContainer = Tan20,
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
    organizationContainer = Tan30,
    organizationOnContainer = Tan90,
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
    organizationContainer = White,
    organizationOnContainer = Color(0xFF2E241B),
    liveIndicator = Color(0xFF5A0A14),
    liveContainer = White,
    liveOnContainer = Color(0xFF5A0A14),
    privateIndicator = Ink00,
)
