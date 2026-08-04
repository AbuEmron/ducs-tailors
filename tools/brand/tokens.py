"""
Fi Sabilillah — brand colour tokens.

TWO PALETTES, ON PURPOSE

The brand palette below is deep green and gold. The product palette -- what the
application's screens are actually painted in -- is the Amanah harbour blue in
`androidApp/.../ui/theme/Color.kt`. They are different, and that is a decision
rather than an oversight.

A brand colour has to survive an app-store grid, a printed leaflet and a masjid
noticeboard. A product colour has to carry a hundred states of meaning across
hundreds of screens. Forcing one to be the other makes both worse: the green is
too dark to be a UI primary at the contrast ratios interface text needs, and the
harbour blue does not read as an institution at 60px on a shelf of icons.

They meet in exactly three places: the splash screen, the sidebar mark and the
app icon. Everywhere inside the app the emblem is drawn in the product palette,
which is why `EMBLEM_TONES` includes an `amanah` tone.

Gold is an accent and never the load-bearing element. Every mark works with the
gold removed entirely -- see the monochrome variants -- because gold on a light
background is the single commonest way a premium identity becomes illegible.
"""

BRAND = {
    "deep-green":  ("#0F3D34", "The primary ground. The reference's own value, kept."),
    "forest-deep": ("#0A2A24", "Splash and immersive grounds; a step under the primary."),
    "mineral":     ("#1C574A", "Raised surfaces on a green ground."),
    "sage":        ("#6BAA7D", "Supporting tint. 4.42:1 on deep green — graphics, not body text."),
    "gold":        ("#D4AF37", "The accent, on dark grounds. 5.75:1 on deep green."),
    "gold-deep":   ("#7A5E15", "The accent for light grounds. Gold on ivory is 1.88:1 and\n"
                               "                                unusable; this is 5.45:1."),
    "ivory":       ("#F5F2E9", "The light ground, and the wordmark on dark grounds."),
    "charcoal":    ("#0D1115", "One-colour dark, print, foil blocking, monochrome."),
    "stone":       ("#68757A", "Neutral text and secondary rules."),
}

# Restated from the Amanah product palette so a designer reading only the brand
# sheet cannot invent a second success green.
FUNCTIONAL = {
    "success":  ("#3E7A5E", "Confirmed, settled, attended."),
    "warning":  ("#AF7A2A", "Needs attention. Never used for danger."),
    "danger":   ("#A04B55", "Restriction, dispute, refusal."),
    "learning": ("#6C638E", "Learning surfaces, and nothing else."),
    "service":  ("#246F6C", "Service and safeguard-affirmative."),
    "neutral":  ("#68757A", "Everything unweighted."),
}

# The gold ramp the emblem's gradient is built from. A gradient rather than a
# bevel filter: a filter does not survive being printed, foil-blocked, converted
# to a VectorDrawable, or rendered at 16px, and every one of those happens.
GOLD_RAMP = ("#F0DFA8", "#E0C264", "#D4AF37", "#A8811F")

# How the emblem is coloured in each context.
#   (background, arch stroke, inner opening)
#   name -> (plate, ink, gradient?)
EMBLEM_TONES = {
    "primary":    ("#0F3D34", None,      True),   # gold gradient on deep green
    "flat":       ("#0F3D34", "#D4AF37", False),  # one flat gold; print, and Android
    "dark":       ("#0D1115", None,      True),
    "light":      ("#F5F2E9", "#7A5E15", False),  # gold-deep: gold on ivory is 1.88:1
    "ivory":      ("#0F3D34", "#F5F2E9", False),  # ivory mark on green
    "mono-black": (None,      "#0D1115", False),
    "mono-white": (None,      "#FFFFFF", False),
    # Inside the application, where the surrounding surfaces are Amanah harbour blue.
    "amanah":     ("#0E4861", "#D4AF37", False),
}


def rgb(hex_):
    h = hex_.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hsl(hex_):
    r, g, b = [v / 255 for v in rgb(hex_)]
    mx, mn = max(r, g, b), min(r, g, b)
    l = (mx + mn) / 2
    if mx == mn:
        return (0.0, 0.0, round(l * 100, 1))
    d = mx - mn
    s = d / (2 - mx - mn) if l > 0.5 else d / (mx + mn)
    if mx == r:
        h = ((g - b) / d + (6 if g < b else 0))
    elif mx == g:
        h = (b - r) / d + 2
    else:
        h = (r - g) / d + 4
    return (round(h * 60, 1), round(s * 100, 1), round(l * 100, 1))


def cmyk(hex_):
    """Naive conversion. Adequate for a brand sheet; a printer will re-separate."""
    r, g, b = [v / 255 for v in rgb(hex_)]
    k = 1 - max(r, g, b)
    if k >= 1:
        return (0, 0, 0, 100)
    f = lambda v: round((1 - v - k) / (1 - k) * 100)
    return (f(r), f(g), f(b), round(k * 100))


def _lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_):
    r, g, b = rgb(hex_)
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return round((hi + 0.05) / (lo + 0.05), 2)
