"""
Fi Sabilillah — brand geometry, second direction.

THIS FOLLOWS THE SUPPLIED REFERENCE.

An earlier pass stripped the reference down to a bare nested arch on the grounds
that the crescent is a cliché and the ornament would not scale. That call was
overruled, and correctly: the reference's character -- the onion arch, the
crescent and star, the leaf sweeps, gold on deep green -- is the brief, and a
mark that is merely defensible is not the same as a mark somebody wants.

What "or better" means here, concretely:

  * Real vector geometry, not a traced raster. Every curve below is authored.
  * A dimensional gold that is a gradient, not a bevel filter -- so it survives
    being printed, foil-blocked, and rendered at 16px.
  * A flat cut and a monochrome cut of the same shapes, so the identity does not
    collapse when it meets a themed launcher icon or a fax machine.
  * A simplified small cut, because the full ornament genuinely does not resolve
    below about 32px and pretending otherwise is how identities end up as mud in
    a notification tray.

WHAT IS STILL NOT HERE, AND WHY

The reference's interior calligraphy. It does not resolve into readable Arabic
at any size, and the brief itself rules it out twice -- "free from fake or
malformed Arabic calligraphy", and "do not place tiny words or detailed Arabic
calligraphy inside the app icon". In its place the interior carries a mihrab
niche: a lit doorway under the crescent, which is what the calligraphy block
occupied and reads at every size. Real Arabic can be set beneath the wordmark
in a lockup, where it is large enough to be read; it cannot go inside a 16px
icon in any typeface.
"""

VIEWBOX = 64

import math

# ── The onion arch ───────────────────────────────────────────────────────────
#
# A pointed ogee: straight-ish flanks, a shoulder that bulges very slightly, and
# a sharp apex. Closed at the foot, because the leaves emerge from underneath it
# rather than continuing it.

ARCH_STROKE = 3.4

ARCH = ("M32 4 "
        "C39.4 11.4 45.6 18.8 45.6 27.4 "
        "L45.6 40.4 "
        "C45.6 46.6 39.8 51.4 32 53.2 "
        "C24.2 51.4 18.4 46.6 18.4 40.4 "
        "L18.4 27.4 "
        "C18.4 18.8 24.6 11.4 32 4 Z")

# The inner keyline, a hair inside the arch. This is the single detail that
# carries most of the reference's "premium" reading, and it is also the first
# thing to disappear below 32px.
ARCH_KEYLINE = ("M32 9.2 "
                "C38.2 15.6 42.4 21.4 42.4 27.8 "
                "L42.4 39.8 "
                "C42.4 44.6 37.8 48.4 32 49.9 "
                "C26.2 48.4 21.6 44.6 21.6 39.8 "
                "L21.6 27.8 "
                "C21.6 21.4 25.8 15.6 32 9.2 Z")

# ── Crescent and star ────────────────────────────────────────────────────────
#
# Drawn as two circles resolved with evenodd rather than as a hand-authored
# crescent outline: the subtraction is exact, and the horns come to a real point
# instead of the blunt stub a hand-drawn crescent usually gets.

def _crescent(cx, cy, R, d, r):
    """
    An exact crescent: the outer circle minus a circle offset by `d` in +x,
    expressed as two circular arcs meeting at the horns.

    Not two overlapping circles resolved with `evenodd`. That trick renders as a
    near-solid disc in some rasterisers, and `evenodd` does not exist at all in
    an Android VectorDrawable -- so the horns have to be real geometry if the
    same path is going to survive into the launcher icon.
    """
    a = (d * d - r * r + R * R) / (2 * d)
    h = math.sqrt(R * R - a * a)
    top = (cx + a, cy - h)
    bottom = (cx + a, cy + h)
    return (f"M{top[0]:.3f} {top[1]:.3f} "
            f"A{R} {R} 0 1 0 {bottom[0]:.3f} {bottom[1]:.3f} "
            f"A{r} {r} 0 0 1 {top[0]:.3f} {top[1]:.3f} Z")


# Opening to the upper right, with the star sitting in it, as the reference has it.
CRESCENT = _crescent(cx=30.4, cy=18.2, R=6.4, d=3.7, r=5.4)

STAR = ("M37.9 12.4 L38.75 14.75 L41.25 14.83 L39.3 16.4 "
        "L39.98 18.8 L37.9 17.36 L35.82 18.8 L36.5 16.4 "
        "L34.55 14.83 L37.05 14.75 Z")

# ── The mihrab niche ─────────────────────────────────────────────────────────
#
# A lit doorway where the reference put its calligraphy block. Same visual mass,
# same position, and it still reads at 16px.

def _arcade(cx, y_base, half_w, height, n=3, gap=1.1):
    """n small arches side by side on one baseline."""
    span = (2 * half_w - gap * (n - 1)) / n
    out, x = [], cx - half_w
    for _ in range(n):
        r = span / 2
        out.append(f"M{x:.2f} {y_base:.2f} L{x:.2f} {y_base - height + r:.2f} "
                   f"C{x:.2f} {y_base - height + r * 0.45:.2f} "
                   f"{x + r * 0.45:.2f} {y_base - height:.2f} "
                   f"{x + r:.2f} {y_base - height:.2f} "
                   f"C{x + r * 1.55:.2f} {y_base - height:.2f} "
                   f"{x + span:.2f} {y_base - height + r * 0.45:.2f} "
                   f"{x + span:.2f} {y_base - height + r:.2f} "
                   f"L{x + span:.2f} {y_base:.2f} Z")
        x += span + gap
    return " ".join(out)


ARCADE = _arcade(cx=32, y_base=46.6, half_w=9.4, height=13.4)

# ── The leaves ───────────────────────────────────────────────────────────────
#
# Two sweeps beneath the arch, pointed at the outer tip. Service, growth, and
# two open hands, depending on who is looking.

# The two leaves meet at one point rather than crossing. Crossed, they read as
# a ribbon tied under the mark; met, they read as two hands or two leaves
# springing from the same root, which is the reading the reference intends.
LEAF_L = ("M32 54.4 C25.6 58.6 18.4 60.6 10.6 60.8 "
          "C17.2 63.6 26.4 62.0 32 58.0 Z")
LEAF_R = ("M32 54.4 C38.4 58.6 45.6 60.6 53.4 60.8 "
          "C46.8 63.6 37.6 62.0 32 58.0 Z")

# ── The small cut ────────────────────────────────────────────────────────────
#
# Below 32px: arch, crescent, niche. The keyline, the star and the leaves all
# turn to noise at that size, and noise inside a 16px mark is worse than nothing.

SMALL_ARCH_STROKE = 4.6
# No star below 32px: at that size it is three pixels of noise beside the moon.
SMALL_CRESCENT = _crescent(cx=30.6, cy=18.6, R=6.9, d=4.0, r=5.8)
# Two arches rather than three below 32px: at that size the third closes the
# gaps and the arcade becomes a solid bar.
SMALL_ARCADE = _arcade(cx=32, y_base=46.8, half_w=8.8, height=13.0, n=2, gap=1.8)
