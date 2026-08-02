# Amanah Design System

The visual and interaction language for Fi Sabilillah.

## Source of truth

`amanah-ui-prototype/index.html` is the approved interactive prototype. It is vendored here
rather than linked so that the design source and the built application cannot drift apart,
and so that a reviewer can open one file in a browser and compare.

Every colour in `androidApp/app/src/main/java/org/fisabilillah/app/ui/theme/Color.kt` is
annotated with the prototype CSS custom property it came from. The radius scale
(10 / 14 / 20 / 28) and the motion curve (`cubic-bezier(.2, .8, .2, 1)` at 180ms) are taken
from it directly.

## Where the tokens live

| Concern | File |
| --- | --- |
| Colour primitives and status colours | `ui/theme/Color.kt` |
| Light, dark and high-contrast schemes | `ui/theme/Theme.kt` |
| Typography, shape and spacing scales | `ui/theme/Type.kt` |
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
