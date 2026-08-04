#!/usr/bin/env python3
"""
Fi Sabilillah — builds every brand asset from the geometry in this directory.

Run:  python3 tools/brand/build.py

Nothing under `brand/` is edited by hand. If a mark is wrong, the fix goes in
`geometry.py`, `letters.py` or `tokens.py` and every one of the ~60 exports
changes together. That is the only way "all exports use consistent geometry"
stays true after the first revision.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cairosvg
from geometry import (VIEWBOX, STROKE, STROKE_SMALL, ARCH_OUTER, ARCH_MIDDLE,
                      ARCH_INNER, SMALL_INNER, HAND_L, HAND_R)
from letters import word_paths, CAP, WEIGHT
import tokens as T

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")
written = []


def write(rel, text):
    path = os.path.join(B, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(text)
    written.append(rel)


def png(rel, svg, w, h=None, bg=None):
    path = os.path.join(B, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=path,
                     output_width=w, output_height=h or w, background_color=bg)
    written.append(rel)


# ── The emblem ───────────────────────────────────────────────────────────────

def emblem_body(arch, inner, small=False, hands=False):
    """The mark itself, on a 0 0 64 64 grid. No background, no wrapper."""
    w = STROKE_SMALL if small else STROKE
    strokes = f'<path d="{ARCH_OUTER}"/>'
    if not small:
        strokes += f'<path d="{ARCH_MIDDLE}"/>'
    if hands:
        strokes += f'<path d="{HAND_L}"/><path d="{HAND_R}"/>'
    opening = SMALL_INNER if small else ARCH_INNER
    return (f'<g fill="none" stroke="{arch}" stroke-width="{w}" stroke-linecap="round" '
            f'stroke-linejoin="round">{strokes}</g>'
            f'<path d="{opening}" fill="{inner}"/>')


def emblem_svg(tone="primary", small=False, rounded=True, pad=0, title=None):
    bg, arch, inner = T.EMBLEM_TONES[tone]
    size = VIEWBOX + pad * 2
    plate = ""
    if bg:
        r = f' rx="{size * 0.22:.2f}"' if rounded else ""
        plate = f'<rect width="{size}" height="{size}"{r} fill="{bg}"/>'
    label = f'<title>{title}</title>' if title else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
            f'width="{size}" height="{size}" role="img" aria-label="Fi Sabilillah">'
            f'{label}{plate}<g transform="translate({pad},{pad})">'
            f'{emblem_body(arch, inner, small)}</g></svg>')


# ── Wordmark and lockups ─────────────────────────────────────────────────────

def wordmark_body(fill, x, y, scale):
    paths, width = word_paths("FI SABILILLAH", x=x, y=y, scale=scale)
    out = []
    for d, rule, is_stroke in paths:
        if is_stroke:
            out.append(f'<path d="{d}" fill="none" stroke="{fill}" '
                       f'stroke-width="{WEIGHT * scale:.2f}" stroke-linecap="butt" '
                       f'stroke-linejoin="round"/>')
        else:
            out.append(f'<path d="{d}" fill="{fill}" fill-rule="{rule}"/>')
    return "".join(out), width


def wordmark_svg(tone="primary"):
    bg, arch, _ = T.EMBLEM_TONES[tone]
    body, w = wordmark_body(arch, x=12, y=12, scale=1.0)
    plate = f'<rect width="{w + 24:.0f}" height="124" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w + 24:.0f} 124" '
            f'role="img" aria-label="Fi Sabilillah">{plate}{body}</svg>')


def horizontal_svg(tone="primary", tagline=False):
    """Emblem left, wordmark right, optically aligned on the emblem's centre."""
    bg, arch, inner = T.EMBLEM_TONES[tone]
    accent = inner
    scale = 0.30                       # cap height 30 against a 64 emblem
    gap = 22
    ex, ey = 14, 6                     # emblem origin
    wx = ex + VIEWBOX + gap
    # Optical centring, not box centring. The emblem's ink runs from y=4 to y=50.5
    # inside its 64 grid -- it does not fill the grid -- so aligning the two boxes
    # leaves the wordmark sitting visibly low.
    emblem_centre = ey + (4 + 50.5) / 2
    wy = emblem_centre - CAP * scale / 2
    body, ww = wordmark_body(arch, x=wx, y=wy, scale=scale)
    tag = ""
    height = 70
    if tagline:
        ty, tw = wy + CAP * scale + 14, ww
        tag = (f'<g transform="translate({wx},{ty})">'
               + _tagline(accent, tw, 0.105) + '</g>')
        height = 92
    total_w = wx + ww + 16
    plate = f'<rect width="{total_w:.0f}" height="{height}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total_w:.0f} {height}" '
            f'role="img" aria-label="Fi Sabilillah">{plate}'
            f'<g transform="translate({ex},{ey})">{emblem_body(arch, inner)}</g>'
            f'{body}{tag}</svg>')


def _tagline(fill, width, scale):
    body, w = wordmark_body(fill, x=0, y=0, scale=scale)
    # centred under the wordmark
    dx = (width - w) / 2
    return f'<g transform="translate({dx:.2f},0)">{body}</g>'


def stacked_svg(tone="primary"):
    bg, arch, inner = T.EMBLEM_TONES[tone]
    scale = 0.22
    emblem_scale = 1.7        # the emblem carries a stacked lockup; the words support it
    body, ww = wordmark_body(arch, x=0, y=0, scale=scale)
    ew = VIEWBOX * emblem_scale
    total_w = max(ww, ew) + 48
    ex = (total_w - ew) / 2
    wy = 8 + 52 * emblem_scale + 22
    plate = f'<rect width="{total_w:.0f}" height="{wy + CAP * scale + 18:.0f}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" '
            f'viewBox="0 0 {total_w:.0f} {wy + CAP * scale + 18:.0f}" '
            f'role="img" aria-label="Fi Sabilillah">{plate}'
            f'<g transform="translate({ex:.2f},{8 - 4 * emblem_scale:.2f}) '
            f'scale({emblem_scale})">{emblem_body(arch, inner)}</g>'
            f'<g transform="translate({(total_w - ww) / 2:.2f},{wy:.2f})">{body}</g></svg>')


# ── Badges ───────────────────────────────────────────────────────────────────
#
# One container for all seven, so they read as a family rather than as seven
# logos. The container is the emblem's own arch closed at the foot: a badge is
# the same shelter, holding one fact about one thing.

# The apex is rounded rather than pointed. A sharp point turns the badge into a
# teardrop, and seven teardrops in a row read as weather icons.
BADGE_CONTAINER = ("M32 6 C33.4 6 34.6 6.6 36.2 7.7 C46.4 14.6 55 23.8 55 34.5 "
                   "L55 47 C55 54.2 49.2 60 42 60 L22 60 C14.8 60 9 54.2 9 47 "
                   "L9 34.5 C9 23.8 17.6 14.6 27.8 7.7 C29.4 6.6 30.6 6 32 6 Z")

BADGE_GLYPHS = {
    "verified-organization": ('<path d="M22 34 L29 41.5 L43 26"/>', "A check, and nothing else. The badge means documents were seen."),
    "learning":              ('<path d="M32 25 C27.5 22 22 21 17 22 L17 41 C22 40 27.5 41 32 44"/>'
                              '<path d="M32 25 C36.5 22 42 21 47 22 L47 41 C42 40 36.5 41 32 44"/>', "An open book."),
    "service":               ('<path d="M20 44 L20 33 C20 27 25.5 22 32 19 C38.5 22 44 27 44 33 L44 44"/>', "The emblem's own arch: shelter offered."),
    "community":             ('<circle cx="32" cy="24.5" r="4.4" fill="CURRENT"/>'
                              '<circle cx="22" cy="41" r="4.4" fill="CURRENT"/>'
                              '<circle cx="42" cy="41" r="4.4" fill="CURRENT"/>', "Three, unranked and the same size."),
    # A lock, not a shield: the container is already a shield, and an earlier
    # draft (a dot under an arc) was indistinguishable from the introduction
    # badge at a glance -- two badges that look the same are one badge.
    "safety":                ('<path d="M24.5 31 L24.5 26.5 C24.5 22.4 27.9 19 32 19 '
                              'C36.1 19 39.5 22.4 39.5 26.5 L39.5 31"/>'
                              '<rect x="20.5" y="31" width="23" height="16.5" rx="4.4" fill="CURRENT"/>',
                              "A lock. Privacy and restriction, which is what this badge governs."),
    # Cupped, with the ends turning up. The first draft used a shallow downward
    # arc under a large circle and read unmistakably as a head and shoulders.
    "donation":              ('<circle cx="32" cy="24.5" r="5.2" fill="CURRENT"/>'
                              '<path d="M17.5 36.5 C17.5 45.5 24 50 32 50 C40 50 46.5 45.5 46.5 36.5"/>',
                              "Given into cupped hands, not dropped into a box."),
    # Two parties with a third presence arched over both. An earlier draft used two
    # facing brackets, which with the bar above read as a face rather than as a
    # guardian -- and a face is the last thing this particular badge should suggest.
    "family-introduction":   ('<path d="M17 30 C17 22.5 23.5 17 32 17 C40.5 17 47 22.5 47 30"/>'
                              '<circle cx="24" cy="42" r="5" fill="CURRENT"/>'
                              '<circle cx="40" cy="42" r="5" fill="CURRENT"/>', "Two parties, and a third presence over both."),
}


def badge_svg(name, tone="primary"):
    glyph, _ = BADGE_GLYPHS[name]
    bg, arch, inner = T.EMBLEM_TONES[tone]
    # The glyphs sit in a fill="none" group so their strokes inherit; any solid
    # element has to name its own fill or it renders as nothing at all.
    glyph = glyph.replace("CURRENT", arch)
    plate = f'<path d="{BADGE_CONTAINER}" fill="{bg or "#0B3A30"}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" '
            f'height="64" role="img" aria-label="{name.replace("-", " ")}">{plate}'
            f'<g fill="none" stroke="{arch}" stroke-width="4.6" stroke-linecap="round" '
            f'stroke-linejoin="round">{glyph}</g></svg>')


# ── Android ──────────────────────────────────────────────────────────────────
#
# The adaptive icon foreground is 108x108dp with a 66dp safe circle. The emblem's
# own bounding box is 37 x 46.5 within its 64 grid, so it is scaled 1.1 and
# centred, which lands it comfortably inside the safe zone at every mask shape
# Android ships.
#
# No rounded corners are baked into the foreground: the launcher applies the
# mask, and a foreground that has already been rounded gets rounded twice.

ADAPTIVE_SCALE = 1.1
ADAPTIVE_TX = 54 - 32 * ADAPTIVE_SCALE
ADAPTIVE_TY = 54 - 27.25 * ADAPTIVE_SCALE


def vector_drawable(arch, inner, size=64, group=None, small=False):
    def path(d, fill="#00000000", stroke=None, width=0.0):
        bits = [f'        android:pathData="{d}"']
        if fill != "#00000000":
            bits.append(f'        android:fillColor="{fill}"')
        if stroke:
            bits += [f'        android:strokeColor="{stroke}"',
                     f'        android:strokeWidth="{width}"',
                     '        android:strokeLineCap="round"',
                     '        android:strokeLineJoin="round"']
        return "    <path\n" + "\n".join(bits) + " />"

    w = STROKE_SMALL if small else STROKE
    inner_d = SMALL_INNER if small else ARCH_INNER
    body = [path(ARCH_OUTER, stroke=arch, width=w)]
    if not small:
        body.append(path(ARCH_MIDDLE, stroke=arch, width=w))
    body.append(path(inner_d, fill=inner))
    inner_xml = "\n".join(body)
    if group:
        inner_xml = (f'    <group\n        android:scaleX="{group[0]}"\n'
                     f'        android:scaleY="{group[0]}"\n'
                     f'        android:translateX="{group[1]:.3f}"\n'
                     f'        android:translateY="{group[2]:.3f}">\n'
                     + "\n".join("    " + line for line in inner_xml.splitlines())
                     + "\n    </group>")
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/brand/build.py. Do not edit. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{size}"\n    android:viewportHeight="{size}">\n'
            f'{inner_xml}\n</vector>\n')


def wordmark_vector_drawable():
    """The wordmark as a VectorDrawable, so Compose draws the same outlines the
    SVGs do rather than a font approximation of them."""
    paths, w = word_paths("FI SABILILLAH", x=0, y=0, scale=1.0)
    body = []
    for d, rule, is_stroke in paths:
        if is_stroke:
            body.append(f'    <path\n        android:pathData="{d}"\n'
                        f'        android:strokeColor="#FFFFFFFF"\n'
                        f'        android:strokeWidth="{WEIGHT}"\n'
                        f'        android:strokeLineCap="butt"\n'
                        f'        android:strokeLineJoin="round" />')
        else:
            body.append(f'    <path\n        android:pathData="{d}"\n'
                        f'        android:fillColor="#FFFFFFFF"\n'
                        f'        android:fillType="{"evenOdd" if rule == "evenodd" else "nonZero"}" />')
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/brand/build.py. Do not edit. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w / 4:.0f}dp"\n    android:height="25dp"\n'
            f'    android:viewportWidth="{w:.2f}"\n    android:viewportHeight="100"\n'
            f'    android:tint="#FFFFFFFF">\n' + "\n".join(body) + "\n</vector>\n")


# ── Token files ──────────────────────────────────────────────────────────────

def token_files():
    payload = {
        "brand": {k: {"hex": v[0], "rgb": list(T.rgb(v[0])), "hsl": list(T.hsl(v[0])),
                      "cmyk": list(T.cmyk(v[0])), "usage": v[1]}
                  for k, v in T.BRAND.items()},
        "functional": {k: {"hex": v[0], "rgb": list(T.rgb(v[0])), "hsl": list(T.hsl(v[0])),
                           "cmyk": list(T.cmyk(v[0])), "usage": v[1]}
                       for k, v in T.FUNCTIONAL.items()},
        "contrast": {
            "ivory-on-sabil-green": T.contrast("#F4F1E8", "#0B3A30"),
            "brass-on-sabil-green": T.contrast("#C6A664", "#0B3A30"),
            "brass-on-ivory": T.contrast("#C6A664", "#F4F1E8"),
            "brass-deep-on-ivory": T.contrast("#836427", "#F4F1E8"),
            "sabil-green-on-ivory": T.contrast("#0B3A30", "#F4F1E8"),
            "ivory-on-charcoal": T.contrast("#F4F1E8", "#0C1013"),
        },
        "geometry": {"viewBox": 64, "stroke": STROKE, "strokeSmall": STROKE_SMALL,
                     "capHeight": CAP, "wordmarkWeight": WEIGHT},
    }
    write("tokens/brand-tokens.json", json.dumps(payload, indent=2) + "\n")

    css = ["/* Generated by tools/brand/build.py. Do not edit. */", ":root {"]
    for k, (hexv, use) in {**T.BRAND}.items():
        css.append(f"  --brand-{k}: {hexv};            /* {use.splitlines()[0]} */")
    for k, (hexv, use) in T.FUNCTIONAL.items():
        css.append(f"  --brand-fn-{k}: {hexv};         /* {use} */")
    css += ["}", ""]
    write("tokens/brand-tokens.css", "\n".join(css) + "\n")

    ts = ["// Generated by tools/brand/build.py. Do not edit.",
          "export const brandTokens = {"]
    for k, (hexv, _) in T.BRAND.items():
        ts.append(f'  "{k}": "{hexv}",')
    ts.append("} as const;")
    ts.append("")
    ts.append("export const functionalTokens = {")
    for k, (hexv, _) in T.FUNCTIONAL.items():
        ts.append(f'  "{k}": "{hexv}",')
    ts += ["} as const;", "",
           "export type BrandToken = keyof typeof brandTokens;", ""]
    write("tokens/brand-tokens.ts", "\n".join(ts))


# ── Splash, social and store artwork ─────────────────────────────────────────

def splash_svg(dark=True):
    bg = "#072A22" if dark else "#F4F1E8"
    arch = "#F4F1E8" if dark else "#0B3A30"
    inner = "#C6A664" if dark else "#836427"
    W, H = 1080, 1920
    # A single very soft radial behind the mark. Not a glow on the mark itself --
    # a glow on a logo is the thing that makes an identity look cheap on an OLED.
    halo = ('<radialGradient id="h" cx="50%" cy="42%" r="42%">'
            f'<stop offset="0%" stop-color="{"#0F5142" if dark else "#FFFFFF"}" stop-opacity="{0.55 if dark else 0.9}"/>'
            f'<stop offset="100%" stop-color="{bg}" stop-opacity="0"/></radialGradient>')
    s = 5.2
    ex, ey = (W - VIEWBOX * s) / 2, H * 0.42 - VIEWBOX * s / 2
    body, ww = wordmark_body(arch, x=0, y=0, scale=0.62)
    wx, wy = (W - ww) / 2, H * 0.42 + VIEWBOX * s / 2 + 96
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
            f'<defs>{halo}</defs><rect width="{W}" height="{H}" fill="{bg}"/>'
            f'<rect width="{W}" height="{H}" fill="url(#h)"/>'
            f'<g transform="translate({ex:.1f},{ey:.1f}) scale({s})">'
            f'{emblem_body(arch, inner)}</g>'
            f'<g transform="translate({wx:.1f},{wy:.1f})">{body}</g></svg>')


def og_svg():
    W, H = 1200, 630
    body, ww = wordmark_body("#F4F1E8", x=0, y=0, scale=0.86)
    s = 3.1
    total = VIEWBOX * s + 44 + ww
    ex = (W - total) / 2
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
            f'<rect width="{W}" height="{H}" fill="#0B3A30"/>'
            f'<g transform="translate({ex:.1f},{(H - VIEWBOX * s) / 2:.1f}) scale({s})">'
            f'{emblem_body("#F4F1E8", "#C6A664")}</g>'
            f'<g transform="translate({ex + VIEWBOX * s + 44:.1f},{H / 2 - CAP * 0.86 / 2:.1f})">'
            f'{body}</g></svg>')


def maskable_svg():
    """PWA maskable: the mark inside the 80% safe circle, plate bled to the edge."""
    pad = 12   # 64 + 2*12 = 88; mark occupies ~73% of the width
    return emblem_svg("primary", rounded=False, pad=pad)


# ── Everything ───────────────────────────────────────────────────────────────

def main():
    # Logo family
    write("logo/fi-sabilillah-icon-primary.svg", emblem_svg("primary"))
    write("logo/fi-sabilillah-icon-light.svg", emblem_svg("light"))
    write("logo/fi-sabilillah-icon-dark.svg", emblem_svg("dark"))
    write("logo/fi-sabilillah-icon-gold.svg", emblem_svg("gold"))
    write("logo/fi-sabilillah-icon-amanah.svg", emblem_svg("amanah"))
    write("logo/fi-sabilillah-icon-monochrome.svg", emblem_svg("mono-black"))
    write("logo/fi-sabilillah-icon-monochrome-white.svg", emblem_svg("mono-white"))
    write("logo/fi-sabilillah-icon-small.svg", emblem_svg("primary", small=True))
    write("logo/fi-sabilillah-logo-horizontal.svg", horizontal_svg("primary"))
    write("logo/fi-sabilillah-logo-horizontal-light.svg", horizontal_svg("light"))
    write("logo/fi-sabilillah-logo-horizontal-mono.svg", horizontal_svg("mono-black"))
    write("logo/fi-sabilillah-logo-stacked.svg", stacked_svg("primary"))
    write("logo/fi-sabilillah-logo-stacked-light.svg", stacked_svg("light"))
    write("logo/fi-sabilillah-wordmark.svg", wordmark_svg("primary"))
    write("logo/fi-sabilillah-wordmark-light.svg", wordmark_svg("light"))
    write("logo/fi-sabilillah-wordmark-mono.svg", wordmark_svg("mono-black"))
    write("logo/fi-sabilillah-favicon.svg", emblem_svg("primary", small=True))

    # Badges
    for name in BADGE_GLYPHS:
        write(f"badges/{name}.svg", badge_svg(name))
        write(f"badges/{name}-light.svg", badge_svg(name, "light"))

    # Android
    fg = vector_drawable("#F4F1E8", "#C6A664", size=108,
                         group=(ADAPTIVE_SCALE, ADAPTIVE_TX, ADAPTIVE_TY))
    write("app-icons/android/ic_launcher_foreground.xml", fg)
    write("app-icons/android/ic_launcher_background.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Generated by tools/brand/build.py. Do not edit. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="108dp"\n    android:height="108dp"\n'
          '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
          '    <path\n        android:pathData="M0,0h108v108h-108z"\n'
          '        android:fillColor="#0B3A30" />\n</vector>\n')
    write("app-icons/android/ic_launcher_monochrome.xml",
          vector_drawable("#FFFFFF", "#FFFFFF", size=108,
                          group=(ADAPTIVE_SCALE, ADAPTIVE_TX, ADAPTIVE_TY)))
    write("app-icons/android/ic_launcher.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@drawable/ic_launcher_background" />\n'
          '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
          '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
          '</adaptive-icon>\n')
    write("app-icons/android/brand_emblem.xml", vector_drawable("#F4F1E8", "#C6A664"))
    write("app-icons/android/brand_wordmark.xml", wordmark_vector_drawable())
    write("app-icons/android/brand_emblem_small.xml",
          vector_drawable("#F4F1E8", "#C6A664", small=True))
    write("app-icons/notifications/ic_notification.xml",
          vector_drawable("#FFFFFF", "#FFFFFF", size=24, small=True,
                          group=(24 / 64 * 0.92, 24 * 0.5 - 32 * (24 / 64 * 0.92),
                                 24 * 0.5 - 27.25 * (24 / 64 * 0.92))))

    # Raster: iOS, PWA, favicons, store, social, splash
    square = emblem_svg("primary", rounded=False)
    png("app-icons/ios/AppIcon-1024.png", square, 1024, bg="#0B3A30")
    for s in (180, 152, 120, 87, 80, 60, 58, 40):
        png(f"app-icons/ios/AppIcon-{s}.png", square, s, bg="#0B3A30")
    png("app-icons/pwa/icon-192.png", emblem_svg("primary"), 192)
    png("app-icons/pwa/icon-512.png", emblem_svg("primary"), 512)
    png("app-icons/pwa/icon-maskable-512.png", maskable_svg(), 512, bg="#0B3A30")
    png("app-icons/pwa/favicon-32.png", emblem_svg("primary", small=True), 32)
    png("app-icons/pwa/favicon-16.png", emblem_svg("primary", small=True), 16)
    png("app-icons/notifications/ic_notification-96.png",
        emblem_svg("mono-white", small=True), 96)
    png("app-icons/android/play-store-512.png", square, 512, bg="#0B3A30")
    png("fi-sabilillah-og-image.png", og_svg(), 1200, 630)
    png("splash/splash-dark.png", splash_svg(True), 1080, 1920)
    png("splash/splash-light.png", splash_svg(False), 1080, 1920)

    # PWA manifest, referencing only files this script actually wrote
    write("app-icons/pwa/manifest.webmanifest", json.dumps({
        "name": "Fi Sabilillah",
        "short_name": "Fi Sabilillah",
        "description": "Service, learning, mutual aid and community.",
        "start_url": "/",
        "display": "standalone",
        "background_color": "#0B3A30",
        "theme_color": "#0B3A30",
        "icons": [
            {"src": "/brand/app-icons/pwa/icon-192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "/brand/app-icons/pwa/icon-512.png", "sizes": "512x512", "type": "image/png"},
            {"src": "/brand/app-icons/pwa/icon-maskable-512.png", "sizes": "512x512",
             "type": "image/png", "purpose": "maskable"},
            {"src": "/brand/logo/fi-sabilillah-favicon.svg", "sizes": "any", "type": "image/svg+xml"},
        ],
    }, indent=2) + "\n")

    token_files()
    print(f"{len(written)} files written")
    return written


if __name__ == "__main__":
    main()
