# Amanah Design System

The visual and interaction language for Fi Sabilillah.

## Source of truth

`amanah-ui-prototype/index.html` is the approved interactive prototype. It is vendored here
rather than linked so that the design source and the built application cannot drift apart,
and so that a reviewer can open one file in a browser and compare.

Every colour in `androidApp/app/src/main/java/org/fisabilillah/app/ui/theme/Color.kt` is
annotated with the prototype CSS custom property it came from. The radius scale
(10 / 14 / 20 / 28 / 38) and the motion curve (`cubic-bezier(.2, .8, .2, 1)` at 180ms) are
taken from it directly.

### The second revision

The vendored prototype was replaced with the "S+++++" revision. What actually changed in
the tokens, as opposed to what the prototype's own release notes claim:

| | First revision | Now |
| --- | --- | --- |
| Primary | teal `#2D706F` | deep harbour blue `#0E4861` (`--brand`) |
| Secondary | — | teal `#246F6C` (`--brand-2`) |
| Tertiary | violet, shared with learning | warm tan `#8A6C52` (`--brand-3`) |
| Body ink | `#0F2742`, same value as primary | `#0B1F32`, distinct from primary |
| Radii | 6 / 10 / 14 / 20 / 28 | 10 / 14 / 20 / 28 / 38 |
| Depth | one flat card treatment | three shadow levels, mapped to `Elevation` |
| Light surface | warm pearl `#FBFAF7` | white `#FFFFFF` on a cool `#F4F6F4` page |

Two of those are more than taste:

**The teal was doing two jobs.** It was both "this is the application" and "a safeguard is
active", and a colour that means two things means neither. Teal is now reserved for the
second, and the primary moved to the blue.

**Body text and the primary were the same value.** A heading and a button label were
therefore the same colour, and the hierarchy came entirely from weight. They are now
`Ink10` and `Ink20` respectively.

Violet still means learning and nothing else. Organisations previously borrowed it, which
is what the new tan is for — `StatusColors.organizationContainer`.

### What was not adopted

The prototype's **service map with pins** is not built. The application never plots a
person or a request at a point: `ServiceRequest.place.exact` stays private until the
requester releases it to one named helper, and the critical-flow suite asserts that
browsing shows "Eastgate, Ashbourne" and no street address. A pin is a point. If a map is
ever added it has to draw neighbourhood areas, never locations.

The **Next.js rebuild** in the prototype's handoff note is not being done either. The
safety logic lives in `:core:policy` and `:core:domain` behind 329 tests and cannot be
reached from TypeScript, so a second client in another language would mean a second
implementation of every safeguard rule, free to drift from the first.

## Where the tokens live

| Concern | File |
| --- | --- |
| Colour primitives and status colours | `ui/theme/Color.kt` |
| Light, dark and high-contrast schemes | `ui/theme/Theme.kt` |
| Typography, shape, spacing and elevation scales | `ui/theme/Type.kt` |
| Purposeful Motion durations and easings | `ui/theme/Type.kt` (`Motion`) |
| Component vocabulary | `ui/components/Components.kt` |

## The rules the system encodes

**Colour never carries meaning alone.** Every status colour in `StatusColors` is paired in
the components with an icon, a text label and a screen-reader description. The interface
reads identically to somebody who cannot distinguish amber from sage.

**High contrast is a third scheme, not a filter.** A filter over the ordinary palette cannot
guarantee a ratio; `HighContrastScheme` and `HighContrastStatusColors` are separate values
that clear 7:1.

**Dynamic colour is not offered.** Wallpaper-derived palettes would put the verification
mark, the safeguard indicator and the "a moderator is in this conversation" banner at the
mercy of the user's home screen. Those three have to read the same on every device for
people to learn to trust them.

**There is no celebratory motion.** No confetti, no burst, no badge animation on completing
an act of service. `Motion` has no token for it, which is the point: a record of service is
between a person and their Lord, and animating it turns it into a performance.

**Reduced motion collapses to zero, not to "less".** `Motion.scaled()` returns `0` when the
reader has asked for reduced motion, so transitions cut rather than shorten.

**A verification badge cannot be shown without its limits.** `VerificationBadge` takes
`whatItDoesNotMean` as a required parameter. A screen physically cannot render the mark
without also rendering what it does not prove.

## What is deliberately absent

Emerald-and-gold, crescents, mosque silhouettes, calligraphy used as decoration, geometric
pattern fills, glassmorphism, neon, and gradient-heavy surfaces. The palette is a deep
mineral blue with a muted teal and warm neutrals. It is meant to feel like an institution
that happens to be built by and for Muslims, rather than a theme applied over somebody
else's product.

Glassmorphism appears in the prototype's navigation and is **not** carried into the app.
A translucent bar over scrolling content puts arbitrary colour behind the safeguard and
verification indicators, which is the one thing the no-dynamic-colour rule above exists to
prevent.
