"""
Fi Sabilillah — brand colour tokens.

TWO PALETTES, ON PURPOSE

The brand palette below is deep green and brass. The product palette -- what the
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
brass removed entirely -- see the monochrome variants -- because gold on a light
background is the single commonest way a premium identity becomes illegible.
"""

BRAND = {
    "sabil-green":      ("#0B3A30", "The primary. Deep enough to sit under brass without the brass glaring."),
    "sabil-green-deep": ("#072A22", "Splash and immersive backgrounds only. Not a surface colour."),
    "mineral-green":    ("#1F5A4C", "Raised surfaces on a green ground; the second step of depth."),
    "sage":             ("#7FA98C", "Supporting tint. Dividers and quiet states on green."),
    "brass":            ("#C6A664", "The accent, on dark grounds only. 5.44:1 on sabil-green."),
    "brass-deep":       ("#836427", "The accent for light grounds. Brass on ivory is 2.06:1 and illegible;\n                                 this is 4.87:1 and clears AA for text as well as for graphics."),
    "ivory":            ("#F4F1E8", "The light ground and the mark on dark grounds."),
    "charcoal":         ("#0C1013", "One-colour dark. Print, foil blocking, and the monochrome mark."),
    "stone":            ("#64727C", "Neutral text and secondary rules."),
}

# Restated from the Amanah product palette so a designer reading only the brand
# sheet cannot invent a second success green.
FUNCTIONAL = {
    "success":  ("#3E7A5E", "Confirmed, settled, attended."),
    "warning":  ("#AF7A2A", "Needs attention. Never used for danger."),
    "danger":   ("#A04B55", "Restriction, dispute, refusal."),
    "learning": ("#6C638E", "Learning surfaces, and nothing else."),
    "service":  ("#246F6C", "Service and safeguard-affirmative."),
    "neutral":  ("#64727C", "Everything unweighted."),
}

# How the emblem is coloured in each context.
#   (background, arch stroke, inner opening)
EMBLEM_TONES = {
    "primary":    ("#0B3A30", "#F4F1E8", "#C6A664"),
    "dark":       ("#0C1013", "#F4F1E8", "#C6A664"),
    "light":      ("#F4F1E8", "#0B3A30", "#836427"),
    "gold":       ("#0B3A30", "#C6A664", "#F4F1E8"),
    "mono-black": (None,      "#0C1013", "#0C1013"),
    "mono-white": (None,      "#FFFFFF", "#FFFFFF"),
    # In-app: the emblem drawn in the product palette so it belongs to the screen
    # it sits on rather than looking pasted over it.
    "amanah":     ("#0E4861", "#F4F1E8", "#C6A664"),
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
