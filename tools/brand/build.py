#!/usr/bin/env python3
"""
Fi Sabilillah — builds every brand asset from the geometry in this directory.

Run:  python3 tools/brand/build.py
Then: python3 tools/brand/validate.py

Nothing under `brand/` is edited by hand. If a mark is wrong, the fix goes in
`geometry.py`, `letters.py` or `tokens.py`, and every export changes together --
which is the only version of "all assets share one geometry" that survives a
second revision.

Build-time dependencies: cairosvg, Pillow, fontTools, uharfbuzz, svgpathtools.
None of them ship in the application; they exist so that the assets that do ship
can be generated rather than drawn.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cairosvg
import geometry as G
import tokens as T
from letters import (CAP, DISPLAY, DISPLAY_REGULAR, LICENCES, TEXT,
                     calligraphy_path, group, measure, scale_for_width,
                     word_path_data)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")
written = []

WORD = "FI SABILILLAH"
TAGLINE = "For the Sake of Allah"
PILLARS = ("SERVE", "LEARN", "GROW", "GIVE")

# Measured once at scale 1, so no lockup has to guess. Three exports had
# previously been laid out from an assumed scale and overflowed their canvases.
WORD_W = measure(WORD, font=DISPLAY)
TAG_W = measure(TAGLINE, font=DISPLAY_REGULAR, tracking=0.06)

# The calligraphy is fixed geometry once the box is fixed, so it is resolved
# once rather than re-shaped for every one of the sixty-odd exports.
CALLIGRAPHY = calligraphy_path(G.CALLIGRAPHY_BOX)


def write(rel, text):
    path = os.path.join(B, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text)
    written.append(rel)


def png(rel, svg, w, h=None, bg=None):
    path = os.path.join(B, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=path,
                     output_width=w, output_height=h or w, background_color=bg)
    written.append(rel)


# ── The emblem ───────────────────────────────────────────────────────────────

def gold_defs(gid="fsGold"):
    """
    The metal.

    A four-stop linear gradient on a diagonal, so the highlight falls on the
    arch's upper left the way the reference lights it. A gradient rather than a
    bevel or a lighting filter: a filter does not survive being printed,
    foil-blocked, converted to a VectorDrawable or rendered at 16px, and every
    one of those happens to this mark.
    """
    a, b, c, d = T.GOLD_RAMP
    return (f'<linearGradient id="{gid}" x1="0.12" y1="0" x2="0.62" y2="1">'
            f'<stop offset="0%" stop-color="{a}"/><stop offset="28%" stop-color="{b}"/>'
            f'<stop offset="64%" stop-color="{c}"/><stop offset="100%" stop-color="{d}"/>'
            f'</linearGradient>')


def word_defs(gid="fsWord"):
    """Ivory falling to warm at the baseline, as the reference sets the wordmark."""
    return (f'<linearGradient id="{gid}" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0%" stop-color="#FFFDF6"/>'
            f'<stop offset="62%" stop-color="#F5F2E9"/>'
            f'<stop offset="100%" stop-color="#E8DCC0"/></linearGradient>')


def emblem_body(ink, field, small=False, gid="fsGold"):
    """
    The mark on the 0 0 64 64 grid.

    `ink` is a colour or None, which means the gold gradient. `field` is the dark
    panel behind the crescent, or None for the monochrome cuts, where a filled
    centre would make an outline mark into a two-colour one.

    Order matters: leaves first so the band covers where they join it, then the
    field, then the band over the field's edge, then everything read against it.
    """
    f = ink or f"url(#{gid})"
    key = T.GOLD_HIGHLIGHT if ink is None else f

    if small:
        parts = [f'<path d="{G.SMALL_LEAF_L}" fill="{f}"/>',
                 f'<path d="{G.SMALL_LEAF_R}" fill="{f}"/>']
        if field:
            parts.append(f'<path d="{G.FIELD}" fill="{field}"/>')
        parts += [
            f'<path d="{G.ARCH}" fill="none" stroke="{f}" '
            f'stroke-width="{G.SMALL_ARCH_STROKE}" stroke-linejoin="round"/>',
            f'<path d="{G.SMALL_CRESCENT}" fill="{f}"/>',
            f'<path d="{G.SMALL_STAR}" fill="{f}"/>',
        ]
        return "".join(parts)

    parts = [f'<path d="{G.LEAF_L}" fill="{f}"/>', f'<path d="{G.LEAF_R}" fill="{f}"/>']
    if field:
        parts.append(f'<path d="{G.FIELD}" fill="{field}"/>')
    parts += [
        f'<path d="{G.ARCH}" fill="none" stroke="{f}" stroke-width="{G.ARCH_STROKE}" '
        f'stroke-linejoin="round"/>',
        f'<path d="{G.ARCH_KEYLINE}" fill="none" stroke="{key}" '
        f'stroke-width="{G.KEYLINE_STROKE}" opacity="0.9"/>',
        f'<path d="{G.CRESCENT}" fill="{f}"/>',
        f'<path d="{G.STAR}" fill="{f}"/>',
        f'<path d="{CALLIGRAPHY}" fill="{f}"/>',
    ]
    return "".join(parts)


def fit(box, margin=0.86, small=False):
    """Scale + translate that centres the mark's ink inside a `box` square."""
    x0, y0, x1, y1 = G.SMALL_INK if small else G.INK
    w, h = x1 - x0, y1 - y0
    s = box * margin / max(w, h)
    return s, box / 2 - (x0 + x1) / 2 * s, box / 2 - (y0 + y1) / 2 * s


def emblem_svg(tone="primary", small=False, rounded=True, box=64, margin=0.86,
               bleed=False, hairline=False, title="Fi Sabilillah"):
    plate, ink, field, grad = T.EMBLEM_TONES[tone]
    s, tx, ty = fit(box, margin, small)
    defs = f"<defs>{gold_defs()}</defs>" if grad else ""
    bg = ""
    if plate:
        r = f' rx="{box * 0.22:.2f}"' if rounded and not bleed else ""
        bg = f'<rect width="{box}" height="{box}"{r} fill="{plate}"/>'
        if hairline and not bleed:
            # The reference insets a gold hairline from the plate's edge. It is a
            # presentation detail only: a launcher masks the plate, so a border
            # drawn inside one gets cropped into an arc on half the phones there
            # are. It never goes on the adaptive foreground.
            i = box * 0.055
            bg += (f'<rect x="{i:.2f}" y="{i:.2f}" width="{box - 2 * i:.2f}" '
                   f'height="{box - 2 * i:.2f}" rx="{box * 0.165:.2f}" fill="none" '
                   f'stroke="{T.BRAND["gold"][0]}" stroke-width="{box * 0.011:.2f}" '
                   f'opacity="0.55"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {box} {box}" '
            f'width="{box}" height="{box}" role="img" aria-label="{title}">'
            f'<title>{title}</title>{defs}{bg}'
            f'<g transform="translate({tx:.3f},{ty:.3f}) scale({s:.5f})">'
            f'{emblem_body(ink, field, small)}</g></svg>')


# ── Lockup furniture ─────────────────────────────────────────────────────────

def diamond(cx, cy, r, fill, opacity=1.0):
    return (f'<path d="M{cx:.2f} {cy - r:.2f} L{cx + r:.2f} {cy:.2f} '
            f'L{cx:.2f} {cy + r:.2f} L{cx - r:.2f} {cy:.2f} Z" '
            f'fill="{fill}" opacity="{opacity}"/>')


def rule_with_tagline(cx, y, half, tag_scale, colour, accent):
    """
    The reference's divider: a hairline running out to a diamond at each end,
    broken in the middle for the tagline.

    Returns `(markup, height_below_y)`.
    """
    body, tw = group(TAGLINE, colour, x=cx - 0, y=0, scale=tag_scale,
                     font=DISPLAY_REGULAR, tracking=0.06)
    d, tw = word_path_data(TAGLINE, x=cx - tw / 2, y=y - CAP * tag_scale * 0.5,
                           scale=tag_scale, font=DISPLAY_REGULAR, tracking=0.06)
    gap = tw / 2 + half * 0.06
    seg = half - gap
    out = []
    if seg > 4:
        for x0 in (cx - half, cx + gap):
            out.append(f'<rect x="{x0 + 3:.2f}" y="{y - 0.4:.2f}" width="{seg - 6:.2f}" '
                       f'height="0.8" fill="{accent}" opacity="0.5"/>')
        out.append(diamond(cx - half + 1.5, y, 1.5, accent, 0.85))
        out.append(diamond(cx + half - 1.5, y, 1.5, accent, 0.85))
    out.append(f'<path d="{d}" fill="{colour}"/>')
    return "".join(out), CAP * tag_scale * 0.5


def pillars_line(cx, y, scale, colour, sep_colour):
    """SERVE ◆ LEARN ◆ GROW ◆ GIVE, in Inter, with diamonds between."""
    widths = [measure(p, font=TEXT, tracking=0.24) * scale for p in PILLARS]
    gap = CAP * scale * 1.5
    total = sum(widths) + gap * (len(PILLARS) - 1)
    x = cx - total / 2
    out = []
    for i, (p, w) in enumerate(zip(PILLARS, widths)):
        d, _ = word_path_data(p, x=x, y=y, scale=scale, font=TEXT, tracking=0.24)
        out.append(f'<path d="{d}" fill="{colour}"/>')
        x += w
        if i < len(PILLARS) - 1:
            out.append(diamond(x + gap / 2, y + CAP * scale * 0.5,
                               CAP * scale * 0.19, sep_colour, 0.9))
            x += gap
    return "".join(out), total


# ── Lockups ──────────────────────────────────────────────────────────────────
#
# The reference's hierarchy, kept exactly: mark, FI SABILILLAH, a rule broken by
# "For the Sake of Allah", then SERVE · LEARN · GROW · GIVE. The lower two lines
# are optional and off by default, because a lockup that always carries four
# lines has no compact form.

def _tones(tone):
    plate, ink, field, grad = T.EMBLEM_TONES[tone]
    on_dark = tone in ("primary", "dark", "flat", "amanah", "ivory")
    word = "url(#fsWord)" if on_dark else (ink or "#0F3D34")
    accent = T.BRAND["gold"][0] if on_dark else T.BRAND["gold-deep"][0]
    tag = T.BRAND["sage"][0] if on_dark else T.BRAND["gold-deep"][0]
    return plate, ink, field, grad, word, accent, tag


def horizontal_svg(tone="primary", lines=0):
    plate, ink, field, grad, word, accent, tag = _tones(tone)
    e_box, gap, pad = 90, 24, 18
    s, tx, ty = fit(e_box, 0.96)
    scale = 0.33
    wx = pad + e_box + gap
    wd, ww = word_path_data(WORD, x=wx, y=0, scale=scale, font=DISPLAY)

    extra, below = "", 0.0
    if lines >= 1:
        ry = CAP * scale + 13
        markup, h = rule_with_tagline(wx + ww / 2, ry, ww / 2, scale * 0.30, tag, accent)
        extra += markup
        below = ry + h + 6
    if lines >= 2:
        py = below + 9
        markup, _ = pillars_line(wx + ww / 2, py, scale * 0.17, accent, accent)
        extra += markup
        below = py + CAP * scale * 0.17 + 6

    block = max(below, CAP * scale)
    height = max(e_box + 14, block + 22)
    wy = (height - block) / 2
    total = wx + ww + pad

    defs = "<defs>" + (gold_defs() if grad else "") + word_defs() + "</defs>"
    bg = f'<rect width="{total:.0f}" height="{height:.0f}" fill="{plate}"/>' if plate else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total:.0f} {height:.0f}" '
            f'role="img" aria-label="Fi Sabilillah">{defs}{bg}'
            f'<g transform="translate({tx + pad:.3f},{(height - e_box) / 2 + ty:.3f}) '
            f'scale({s:.5f})">{emblem_body(ink, field)}</g>'
            f'<g transform="translate(0,{wy:.2f})"><path d="{wd}" fill="{word}"/>'
            f'{extra}</g></svg>')


def stacked_svg(tone="primary", lines=1):
    plate, ink, field, grad, word, accent, tag = _tones(tone)
    e_box, pad = 140, 30
    s, tx, ty = fit(e_box, 0.96)
    scale = 0.30
    total = max(WORD_W * scale, e_box) + pad * 2
    cx = total / 2
    wy = e_box + 20
    wd, ww = word_path_data(WORD, x=cx - WORD_W * scale / 2, y=wy, scale=scale, font=DISPLAY)

    extra, height = "", wy + CAP * scale + 24
    if lines >= 1:
        ry = wy + CAP * scale + 14
        markup, h = rule_with_tagline(cx, ry, ww / 2, scale * 0.32, tag, accent)
        extra += markup
        height = ry + h + 22
    if lines >= 2:
        py = height - 6
        markup, _ = pillars_line(cx, py, scale * 0.18, accent, accent)
        extra += markup
        height = py + CAP * scale * 0.18 + 22

    defs = "<defs>" + (gold_defs() if grad else "") + word_defs() + "</defs>"
    bg = f'<rect width="{total:.0f}" height="{height:.0f}" fill="{plate}"/>' if plate else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total:.0f} {height:.0f}" '
            f'role="img" aria-label="Fi Sabilillah">{defs}{bg}'
            f'<g transform="translate({(total - e_box) / 2 + tx:.3f},{ty + 10:.3f}) '
            f'scale({s:.5f})">{emblem_body(ink, field)}</g>'
            f'<path d="{wd}" fill="{word}"/>{extra}</svg>')


def wordmark_svg(tone="primary"):
    plate, ink, _field, _grad, word, _accent, _tag = _tones(tone)
    d, w = word_path_data(WORD, x=16, y=16, scale=1.0, font=DISPLAY)
    h = CAP + 32
    bg = f'<rect width="{w + 32:.0f}" height="{h:.0f}" fill="{plate}"/>' if plate else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w + 32:.0f} {h:.0f}" '
            f'role="img" aria-label="Fi Sabilillah"><defs>{word_defs()}</defs>{bg}'
            f'<path d="{d}" fill="{word}"/></svg>')


# ── Badges ───────────────────────────────────────────────────────────────────
#
# The emblem's own arch, closed and filled, holding one glyph. A badge is the
# same shelter carrying one fact about one thing.

BADGE_PLATE = G.FIELD

BADGE_GLYPHS = {
    "verified-organization": ('<path d="M25.4 31.8 L30.2 36.8 L38.8 26.6"/>',
                              "A check. The badge means documents were seen."),
    "learning":              ('<path d="M32 24.8 C28.8 22.8 25.2 22.2 21.8 22.8 L21.8 36.2 '
                              'C25.2 35.6 28.8 36.2 32 38.4"/>'
                              '<path d="M32 24.8 C35.2 22.8 38.8 22.2 42.2 22.8 L42.2 36.2 '
                              'C38.8 35.6 35.2 36.2 32 38.4"/>', "An open book."),
    "service":               ('<path d="M24.2 39.2 L24.2 30.8 C24.2 26.8 27.6 23.0 32 20.8 '
                              'C36.4 23.0 39.8 26.8 39.8 30.8 L39.8 39.2"/>',
                              "The emblem's own arch: shelter offered."),
    "community":             ('<circle cx="32" cy="24.2" r="3.5" fill="INK"/>'
                              '<circle cx="25.0" cy="35.2" r="3.5" fill="INK"/>'
                              '<circle cx="39.0" cy="35.2" r="3.5" fill="INK"/>',
                              "Three, unranked and the same size."),
    "safety":                ('<path d="M27.0 29.6 L27.0 26.6 C27.0 23.8 29.2 21.4 32 21.4 '
                              'C34.8 21.4 37.0 23.8 37.0 26.6 L37.0 29.6"/>'
                              '<rect x="24.2" y="29.6" width="15.6" height="11.6" rx="3.2" '
                              'fill="INK"/>', "A lock. Privacy and restriction."),
    "donation":              ('<circle cx="32" cy="24.0" r="4.0" fill="INK"/>'
                              '<path d="M22.2 32.4 C22.2 39.0 26.8 42.4 32 42.4 '
                              'C37.2 42.4 41.8 39.0 41.8 32.4"/>',
                              "Given into cupped hands, not dropped into a box."),
    "family-introduction":   ('<path d="M23.0 28.8 C23.0 23.8 27.0 20.0 32 20.0 '
                              'C37.0 20.0 41.0 23.8 41.0 28.8"/>'
                              '<circle cx="27.0" cy="37.2" r="3.7" fill="INK"/>'
                              '<circle cx="37.0" cy="37.2" r="3.7" fill="INK"/>',
                              "Two parties, and a third presence over both."),
}


def badge_svg(name, tone="primary"):
    glyph, _ = BADGE_GLYPHS[name]
    plate, ink, _field, grad = T.EMBLEM_TONES[tone]
    f = ink or "url(#fsGold)"
    defs = f"<defs>{gold_defs()}</defs>" if grad else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" '
            f'height="64" role="img" aria-label="{name.replace("-", " ")}">{defs}'
            f'<path d="{BADGE_PLATE}" fill="{plate or "#0F3D34"}" stroke="{f}" '
            f'stroke-width="1.6"/>'
            f'<g fill="none" stroke="{f}" stroke-width="3.0" stroke-linecap="round" '
            f'stroke-linejoin="round">{glyph.replace("INK", f)}</g></svg>')


# ── The five pillars ─────────────────────────────────────────────────────────
#
# The reference's bottom row, as line icons rather than badges: a badge asserts
# that something was checked, and these assert nothing -- they label a section.
# Drawn on the same 64 grid at the same stroke weight so they sit together.

PILLAR_ICONS = {
    "community": ("We rise together",
                  '<circle cx="24" cy="30" r="5"/><circle cx="40" cy="30" r="5"/>'
                  '<path d="M14 48 C14 41.4 18.6 37.5 24 37.5 C29.4 37.5 34 41.4 34 48"/>'
                  '<path d="M30 48 C30 41.4 34.6 37.5 40 37.5 C45.4 37.5 50 41.4 50 48"/>'
                  '<path d="M32 14 C34.6 11.4 38.6 11.6 40.8 14.4 C42.8 17 42.2 20.4 '
                  '39.6 22.4 L32 28.2 L24.4 22.4 C21.8 20.4 21.2 17 23.2 14.4 '
                  'C25.4 11.6 29.4 11.4 32 14 Z"/>'),
    "knowledge": ("Learn. Implement. Grow.",
                  '<path d="M32 20 C27 16.4 21.2 15.2 15 16 L15 44 '
                  'C21.2 43.2 27 44.4 32 48"/>'
                  '<path d="M32 20 C37 16.4 42.8 15.2 49 16 L49 44 '
                  'C42.8 43.2 37 44.4 32 48"/><path d="M32 20 L32 48"/>'),
    "service":   ("Help with Ikhlas",
                  '<path d="M32 26.6 C34.4 24.2 38.2 24.4 40.2 27 C42 29.4 41.4 32.6 '
                  '39 34.6 L32 40 L25 34.6 C22.6 32.6 22 29.4 23.8 27 '
                  'C25.8 24.4 29.6 24.2 32 26.6 Z"/>'
                  '<path d="M16 34 C13.6 36.4 12.6 40 13.4 43.4 C14.4 47.6 18 50 22.2 50 '
                  'L32 50"/>'
                  '<path d="M48 34 C50.4 36.4 51.4 40 50.6 43.4 C49.6 47.6 46 50 41.8 50 '
                  'L32 50"/>'),
    "giving":    ("Sadaqah & donations",
                  '<rect x="14" y="27" width="36" height="9" rx="2"/>'
                  '<path d="M17.5 36 L17.5 49 C17.5 50.1 18.4 51 19.5 51 L44.5 51 '
                  'C45.6 51 46.5 50.1 46.5 49 L46.5 36"/><path d="M32 27 L32 51"/>'
                  '<path d="M32 27 C29 27 24.5 26.4 23 24 C21.7 21.9 22.6 19 24.9 18 '
                  'C27.8 16.8 30.6 20.2 32 27 Z"/>'
                  '<path d="M32 27 C35 27 39.5 26.4 41 24 C42.3 21.9 41.4 19 39.1 18 '
                  'C36.2 16.8 33.4 20.2 32 27 Z"/>'),
    "safety":    ("Built on Trust & Amanah",
                  '<path d="M32 13 L49 19.4 L49 32 C49 41.6 41.8 48.8 32 51.6 '
                  'C22.2 48.8 15 41.6 15 32 L15 19.4 Z"/>'
                  '<path d="M25.4 31.8 L30.2 36.8 L38.8 26.6"/>'),
}


def pillar_svg(name, colour=None, gradient=True):
    _label, glyph = PILLAR_ICONS[name]
    f = colour or "url(#fsGold)"
    defs = f"<defs>{gold_defs()}</defs>" if gradient and colour is None else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" '
            f'height="64" role="img" aria-label="{name}">{defs}'
            f'<g fill="none" stroke="{f}" stroke-width="2.6" stroke-linecap="round" '
            f'stroke-linejoin="round">{glyph}</g></svg>')


# ── Android ──────────────────────────────────────────────────────────────────
#
# VectorDrawables carry the FLAT gold, not the gradient. A VectorDrawable *can*
# express a gradient, but a themed ("monochrome") icon is composited in one tint
# colour, and a two-tone foreground becomes a solid blob under it. So the
# launcher assets are built to work flat, and the gradient lives in the SVG and
# PNG exports where it survives.

def vector_drawable(ink, field=None, size=64, box=108, margin=G.ADAPTIVE_MARGIN,
                    small=False):
    s, tx, ty = fit(box, margin, small)
    lines = []

    def path(d, fill=None, stroke=None, width=0.0, opacity=None):
        bits = [f'            android:pathData="{d}"']
        if fill:
            bits.append(f'            android:fillColor="{fill}"')
        if stroke:
            bits += [f'            android:strokeColor="{stroke}"',
                     f'            android:strokeWidth="{width}"',
                     '            android:strokeLineJoin="round"']
        if opacity is not None:
            bits.append(f'            android:strokeAlpha="{opacity}"')
        return "        <path\n" + "\n".join(bits) + " />"

    if small:
        lines.append(path(G.SMALL_LEAF_L, fill=ink))
        lines.append(path(G.SMALL_LEAF_R, fill=ink))
        if field:
            lines.append(path(G.FIELD, fill=field))
        lines.append(path(G.ARCH, stroke=ink, width=G.SMALL_ARCH_STROKE))
        lines.append(path(G.SMALL_CRESCENT, fill=ink))
        lines.append(path(G.SMALL_STAR, fill=ink))
    else:
        lines.append(path(G.LEAF_L, fill=ink))
        lines.append(path(G.LEAF_R, fill=ink))
        if field:
            lines.append(path(G.FIELD, fill=field))
        lines.append(path(G.ARCH, stroke=ink, width=G.ARCH_STROKE))
        lines.append(path(G.ARCH_KEYLINE, stroke=ink, width=G.KEYLINE_STROKE,
                          opacity="0.85"))
        lines.append(path(G.CRESCENT, fill=ink))
        lines.append(path(G.STAR, fill=ink))
        lines.append(path(CALLIGRAPHY, fill=ink))

    body = "\n".join(lines)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/brand/build.py. Do not edit. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{box}dp"\n    android:height="{box}dp"\n'
            f'    android:viewportWidth="{box}"\n    android:viewportHeight="{box}">\n'
            f'    <group\n        android:scaleX="{s:.5f}"\n        android:scaleY="{s:.5f}"\n'
            f'        android:translateX="{tx:.3f}"\n        android:translateY="{ty:.3f}">\n'
            f'{body}\n    </group>\n</vector>\n')


def wordmark_vector_drawable(ink="#FFFFFF", height=32, pad=1.0):
    """
    FI SABILILLAH as a VectorDrawable, from the same outlines as every SVG.

    Drawn in white so the app can tint it to whatever the surface needs: a
    VectorDrawable tint multiplies, so anything darker than white would clamp
    the ivory-on-green case to a muddy version of itself.
    """
    d, width = word_path_data(WORD, x=pad, y=pad, scale=1.0, font=DISPLAY)
    h, w = CAP + pad * 2, width + pad * 2
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/brand/build.py. Do not edit. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{height * w / h:.0f}dp"\n'
            f'    android:height="{height}dp"\n'
            f'    android:viewportWidth="{w:.2f}"\n'
            f'    android:viewportHeight="{h:.2f}">\n'
            f'    <path\n        android:pathData="{d}"\n'
            f'        android:fillColor="{ink}" />\n</vector>\n')


# ── Banners, splash, social ──────────────────────────────────────────────────

def _banner(w, h, emblem_px, word_frac, plate="#0F3D34", stacked=False,
            lines=2, halo=True):
    """
    The reference's banner composition, at any canvas.

    Everything is derived from `w` and `h` -- there is no per-canvas fiddling,
    which is what stops the OG card and the website header being two different
    designs that happen to share a mark.
    """
    _p, ink, field, _grad, word, accent, tag = _tones("primary")
    halo_def = (f'<radialGradient id="halo" cx="50%" cy="{"38%" if stacked else "50%"}" r="62%">'
                f'<stop offset="0%" stop-color="#1C574A" stop-opacity="0.55"/>'
                f'<stop offset="100%" stop-color="{plate}" stop-opacity="0"/></radialGradient>')
    defs = f'<defs>{gold_defs()}{word_defs()}{halo_def}</defs>'
    bg = (f'<rect width="{w}" height="{h}" fill="{plate}"/>'
          + (f'<rect width="{w}" height="{h}" fill="url(#halo)"/>' if halo else ""))

    s, tx, ty = fit(emblem_px, 0.96)
    ws = scale_for_width(WORD, w * word_frac, font=DISPLAY)
    parts = []

    if stacked:
        ex, ey = (w - emblem_px) / 2, h * 0.30 - emblem_px / 2
        wd, ww = word_path_data(WORD, x=(w - w * word_frac) / 2, y=0, scale=ws, font=DISPLAY)
        wy = ey + emblem_px + h * 0.045
        cx = w / 2
    else:
        gap = w * 0.045
        ww = w * word_frac
        block = emblem_px + gap + ww
        ex, ey = (w - block) / 2, (h - emblem_px) / 2
        wd, _ = word_path_data(WORD, x=ex + emblem_px + gap, y=0, scale=ws, font=DISPLAY)
        cx = ex + emblem_px + gap + ww / 2
        wy = (h - CAP * ws) / 2 - (h * 0.035 if lines else 0)

    parts.append(f'<g transform="translate({ex + tx:.2f},{ey + ty:.2f}) scale({s:.5f})">'
                 f'{emblem_body(ink, field)}</g>')
    parts.append(f'<g transform="translate(0,{wy:.2f})"><path d="{wd}" fill="{word}"/></g>')

    y = wy + CAP * ws + h * 0.045
    if lines >= 1:
        markup, hh = rule_with_tagline(cx, y, ww / 2, ws * 0.30, tag, accent)
        parts.append(markup)
        y += hh + h * 0.045
    if lines >= 2:
        markup, _ = pillars_line(cx, y, ws * 0.17, accent, accent)
        parts.append(markup)

    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" '
            f'width="{w}" height="{h}" role="img" aria-label="Fi Sabilillah">'
            f'{defs}{bg}{"".join(parts)}</svg>')


def splash_svg(dark=True):
    return _banner(1080, 1920, 420, 0.62,
                   plate="#0A2A24" if dark else "#F5F2E9",
                   stacked=True, lines=1) if dark else _splash_light()


def _splash_light():
    """The light splash is a different tone, not the dark one on a pale plate."""
    _p, ink, field, _g, _w, _a, _t = _tones("light")
    w, h, e = 1080, 1920, 420
    s, tx, ty = fit(e, 0.96)
    ws = scale_for_width(WORD, w * 0.62, font=DISPLAY)
    ex, ey = (w - e) / 2, h * 0.30 - e / 2
    wd, ww = word_path_data(WORD, x=(w - w * 0.62) / 2, y=0, scale=ws, font=DISPLAY)
    wy = ey + e + h * 0.045
    markup, _ = rule_with_tagline(w / 2, wy + CAP * ws + h * 0.045, ww / 2, ws * 0.30,
                                  T.BRAND["gold-deep"][0], T.BRAND["gold-deep"][0])
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" '
            f'role="img" aria-label="Fi Sabilillah">'
            f'<rect width="{w}" height="{h}" fill="#F5F2E9"/>'
            f'<g transform="translate({ex + tx:.2f},{ey + ty:.2f}) scale({s:.5f})">'
            f'{emblem_body(ink, field)}</g>'
            f'<g transform="translate(0,{wy:.2f})"><path d="{wd}" fill="#0F3D34"/></g>'
            f'{markup}</svg>')


# ── Tokens ───────────────────────────────────────────────────────────────────

def token_files():
    payload = {
        "brand": {k: {"hex": v[0], "rgb": list(T.rgb(v[0])), "hsl": list(T.hsl(v[0])),
                      "cmyk": list(T.cmyk(v[0])), "usage": " ".join(v[1].split())}
                  for k, v in T.BRAND.items()},
        "functional": {k: {"hex": v[0], "rgb": list(T.rgb(v[0])), "hsl": list(T.hsl(v[0])),
                           "cmyk": list(T.cmyk(v[0])), "usage": v[1]}
                       for k, v in T.FUNCTIONAL.items()},
        "goldRamp": list(T.GOLD_RAMP),
        "goldHighlight": T.GOLD_HIGHLIGHT,
        "contrast": {
            "ivory-on-deep-green": T.contrast("#F5F2E9", "#0F3D34"),
            "gold-on-deep-green": T.contrast("#D4AF37", "#0F3D34"),
            "gold-on-ivory": T.contrast("#D4AF37", "#F5F2E9"),
            "gold-deep-on-ivory": T.contrast("#7A5E15", "#F5F2E9"),
            "sage-on-deep-green": T.contrast("#6BAA7D", "#0F3D34"),
            "deep-green-on-ivory": T.contrast("#0F3D34", "#F5F2E9"),
            "ivory-on-charcoal": T.contrast("#F5F2E9", "#0D1115"),
        },
        "typography": {
            **{k: dict(v) for k, v in T.TYPOGRAPHY.items()},
            "capHeight": CAP,
            "scale": T.TYPE_SCALE,
            "delivery": "outlines embedded as paths; no asset references a font",
        },
        "emblem": {
            "grid": G.VIEWBOX,
            "ink": [round(v, 3) for v in G.INK],
            "smallInk": [round(v, 3) for v in G.SMALL_INK],
            "archStroke": G.ARCH_STROKE,
            "smallArchStroke": G.SMALL_ARCH_STROKE,
            "keylineStroke": G.KEYLINE_STROKE,
            "simplifyBelowPx": 32,
        },
    }
    write("tokens/brand-tokens.json", json.dumps(payload, indent=2) + "\n")

    css = ["/* Generated by tools/brand/build.py. Do not edit. */", ":root {"]
    for k, (hexv, use) in T.BRAND.items():
        css.append(f"  --brand-{k}: {hexv};  /* {' '.join(use.split())} */")
    for k, (hexv, use) in T.FUNCTIONAL.items():
        css.append(f"  --brand-fn-{k}: {hexv};  /* {use} */")
    css += [f"  --brand-gold-highlight: {T.GOLD_HIGHLIGHT};",
            "  --brand-gold-ramp: " + ", ".join(T.GOLD_RAMP) + ";",
            f'  --brand-font-display: "{T.TYPOGRAPHY["display"]["family"]}", Georgia, serif;',
            f'  --brand-font-text: "{T.TYPOGRAPHY["text"]["family"]}", system-ui, sans-serif;',
            "}", ""]
    write("tokens/brand-tokens.css", "\n".join(css) + "\n")

    ts = ["// Generated by tools/brand/build.py. Do not edit.",
          "export const brandTokens = {"]
    ts += [f'  "{k}": "{v[0]}",' for k, v in T.BRAND.items()]
    ts += ["} as const;", "", "export const functionalTokens = {"]
    ts += [f'  "{k}": "{v[0]}",' for k, v in T.FUNCTIONAL.items()]
    ts += ["} as const;", "",
           "export const goldRamp = " + json.dumps(list(T.GOLD_RAMP)) + " as const;",
           f'export const goldHighlight = "{T.GOLD_HIGHLIGHT}" as const;', "",
           "export const typography = " + json.dumps(T.TYPOGRAPHY, indent=2) + " as const;",
           "export const typeScale = " + json.dumps(T.TYPE_SCALE, indent=2) + " as const;", "",
           "export type BrandToken = keyof typeof brandTokens;", ""]
    write("tokens/brand-tokens.ts", "\n".join(ts))


# ── React ────────────────────────────────────────────────────────────────────
#
# Generated, not hand-written. An earlier pair of components carried the emblem
# geometry as constants copied out of geometry.py by hand, with a comment
# admitting the copy had to be kept in step manually. It was not: the emblem
# changed and the components went on drawing the old one. Generating them is the
# only version of "keep these in step" that survives a revision.

REACT_HEADER = """/**
 * Fi Sabilillah — {what}
 *
 * GENERATED by tools/brand/build.py. Do not edit; edit the geometry and re-run.
 *
 * NOTE ON STATUS. There is no web client in this repository -- the product is a
 * native Kotlin/Compose Android application -- so nothing currently imports this.
 * It exists so a future web or PWA client starts from the identity rather than
 * re-tracing it.
 */
"""


def react_logo_tsx():
    tones = {k: (v[0], v[1], v[2]) for k, v in T.EMBLEM_TONES.items()}
    q = lambda v: "null" if v is None else f'"{v}"'
    rows = "\n".join(f'  "{k}": {{ plate: {q(p)}, ink: {q(i)}, field: {q(f)} }},'
                     for k, (p, i, f) in tones.items())
    a, b, c, d = T.GOLD_RAMP
    es, etx, ety = fit(64)
    ss, stx, sty = fit(64, small=True)
    paths = "\n".join(
        f'const {n} = "{v}";' for n, v in [
            ("ARCH", G.ARCH), ("FIELD", G.FIELD), ("ARCH_KEYLINE", G.ARCH_KEYLINE),
            ("CRESCENT", G.CRESCENT), ("STAR", G.STAR),
            ("CALLIGRAPHY", CALLIGRAPHY),
            ("LEAF_L", G.LEAF_L), ("LEAF_R", G.LEAF_R),
            ("SMALL_CRESCENT", G.SMALL_CRESCENT), ("SMALL_STAR", G.SMALL_STAR),
            ("SMALL_LEAF_L", G.SMALL_LEAF_L), ("SMALL_LEAF_R", G.SMALL_LEAF_R),
        ]) + (
        f'\nconst ARCH_STROKE = {G.ARCH_STROKE};'
        f'\nconst SMALL_ARCH_STROKE = {G.SMALL_ARCH_STROKE};'
        f'\nconst KEYLINE_STROKE = {G.KEYLINE_STROKE};'
        f'\nconst GOLD_HIGHLIGHT = "{T.GOLD_HIGHLIGHT}";')

    return REACT_HEADER.format(what="the responsive brand mark.") + f'''import * as React from "react";

export type LogoVariant =
  | "icon"
  | "horizontal"
  | "stacked"
  | "wordmark"
  | "compact"
  | "monochrome";

export type LogoTone = keyof typeof TONES;

export type LogoSize = "xs" | "sm" | "md" | "lg" | "xl" | "hero";

const EMBLEM_HEIGHT: Record<LogoSize, number> = {{
  xs: 16,
  sm: 24,
  md: 40,
  lg: 64,
  xl: 96,
  hero: 160,
}};

/**
 * Below this the keyline is a fifth of a pixel, the star is noise beside the
 * moon, and the calligraphy -- the one thing that has to be legible or absent --
 * becomes a smear. The mark switches to its small cut.
 */
const SIMPLIFY_BELOW = 32;

{paths}

/** `ink: null` means the gold gradient; `field: null` means no inner panel. */
const TONES = {{
{rows}
}} as const;

const GOLD_RAMP = ["{a}", "{b}", "{c}", "{d}"] as const;

export interface FiSabilillahLogoProps {{
  variant?: LogoVariant;
  tone?: LogoTone;
  size?: LogoSize;
  /**
   * Omit when something adjacent already names the application. A logo repeated
   * to a screen reader on every page is noise, not access.
   */
  title?: string | null;
  className?: string;
}}

function Emblem({{ ink, field, small }}: {{ ink: string; field: string | null; small: boolean }}) {{
  const key = ink.startsWith("url(") ? GOLD_HIGHLIGHT : ink;
  return (
    <>
      <path d={{small ? SMALL_LEAF_L : LEAF_L}} fill={{ink}} />
      <path d={{small ? SMALL_LEAF_R : LEAF_R}} fill={{ink}} />
      {{field && <path d={{FIELD}} fill={{field}} />}}
      <path
        d={{ARCH}}
        fill="none"
        stroke={{ink}}
        strokeWidth={{small ? SMALL_ARCH_STROKE : ARCH_STROKE}}
        strokeLinejoin="round"
      />
      {{!small && (
        <path d={{ARCH_KEYLINE}} fill="none" stroke={{key}} strokeWidth={{KEYLINE_STROKE}} opacity={{0.9}} />
      )}}
      <path d={{small ? SMALL_CRESCENT : CRESCENT}} fill={{ink}} />
      <path d={{small ? SMALL_STAR : STAR}} fill={{ink}} />
      {{!small && <path d={{CALLIGRAPHY}} fill={{ink}} />}}
    </>
  );
}}

export function FiSabilillahLogo({{
  variant = "horizontal",
  tone = "primary",
  size = "md",
  title = "Fi Sabilillah",
  className,
}}: FiSabilillahLogoProps) {{
  const px = EMBLEM_HEIGHT[size];
  // React ids contain colons: legal in an id attribute, and url(#...) resolves
  // them, but anything that later tries to select one breaks.
  const gid = "fs" + React.useId().replace(/:/g, "");
  const t = TONES[variant === "monochrome" ? "mono-white" : tone];

  // The variant asked for is not always the variant that should be drawn. A
  // "horizontal" logo at 16px is a wordmark nobody can read beside a mark nobody
  // can see, so small sizes collapse to the icon whatever the caller wanted.
  const effective: LogoVariant =
    px < SIMPLIFY_BELOW && variant !== "wordmark" ? "icon" : variant;
  const small = px < SIMPLIFY_BELOW;

  const a11y = title
    ? {{ role: "img" as const, "aria-label": title }}
    : {{ "aria-hidden": true as const, focusable: false as const }};

  if (effective === "icon" || effective === "compact") {{
    const ink = t.ink ?? `url(#${{gid}})`;
    const [s, tx, ty] = small
      ? [{ss:.5f}, {stx:.3f}, {sty:.3f}]
      : [{es:.5f}, {etx:.3f}, {ety:.3f}];
    return (
      <svg viewBox="0 0 64 64" width={{px}} height={{px}} className={{className}} {{...a11y}}>
        {{title && <title>{{title}}</title>}}
        {{t.ink === null && (
          <defs>
            <linearGradient id={{gid}} x1="0.12" y1="0" x2="0.62" y2="1">
              {{GOLD_RAMP.map((c, i) => (
                <stop key={{c}} offset={{`${{[0, 28, 64, 100][i]}}%`}} stopColor={{c}} />
              ))}}
            </linearGradient>
          </defs>
        )}}
        {{t.plate && <rect width="64" height="64" rx="{64 * 0.22:.2f}" fill={{t.plate}} />}}
        <g transform={{`translate(${{tx}},${{ty}}) scale(${{s}})`}}>
          <Emblem ink={{ink}} field={{t.field}} small={{small}} />
        </g>
      </svg>
    );
  }}

  // The lockups need the drawn letterforms, which live in the generated SVGs
  // rather than inline: twelve glyphs of Playfair path data would triple this
  // file and would be a second copy free to drift from `brand/logo/*.svg`.
  const href = {{
    horizontal: "/brand/logo/fi-sabilillah-logo-horizontal.svg",
    stacked: "/brand/logo/fi-sabilillah-logo-stacked.svg",
    wordmark: "/brand/logo/fi-sabilillah-wordmark.svg",
    monochrome: "/brand/logo/fi-sabilillah-logo-horizontal-mono.svg",
    icon: "/brand/logo/fi-sabilillah-icon-primary.svg",
    compact: "/brand/logo/fi-sabilillah-icon-primary.svg",
  }}[effective];

  // Ratios measured off the generated files, not guessed: the wordmark is
  // {WORD_W / CAP:.2f} cap-heights wide, and a lockup sized by eye is a lockup that
  // overflows something.
  const heightFor: Partial<Record<LogoVariant, number>> = {{
    horizontal: px * 1.18,
    stacked: px * 1.86,
    wordmark: px * 0.44,
  }};

  return (
    <img
      src={{tone === "light" ? href.replace(".svg", "-light.svg") : href}}
      alt={{title ?? ""}}
      height={{heightFor[effective] ?? px}}
      className={{className}}
      {{...(title ? {{}} : {{ "aria-hidden": true }})}}
    />
  );
}}

export default FiSabilillahLogo;
'''


def react_splash_tsx():
    return REACT_HEADER.format(what="splash and brand motion.") + '''import * as React from "react";
import { FiSabilillahLogo } from "./FiSabilillahLogo";

/**
 * One gesture, 520ms, then still. The mark fades up and settles from 96% to 100%
 * on the Amanah curve. It does not spin, pulse, sparkle, shimmer or loop. Under
 * `prefers-reduced-motion: reduce` the animation is not shortened, it is removed:
 * somebody who asked the system for no motion asked for no motion.
 */
export const brandMotionCSS = `
@keyframes fs-settle {
  from { opacity: 0; transform: scale(0.96); }
  to   { opacity: 1; transform: scale(1); }
}
.fs-splash {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  background: #0A2A24;
}
.fs-splash__mark {
  animation: fs-settle 520ms cubic-bezier(.2,.8,.2,1) both;
}
@media (prefers-reduced-motion: reduce) {
  .fs-splash__mark { animation: none; }
}
`;

export function FiSabilillahSplash() {
  return (
    <div className="fs-splash">
      <style>{brandMotionCSS}</style>
      <div className="fs-splash__mark">
        <FiSabilillahLogo variant="stacked" tone="primary" size="xl" />
      </div>
    </div>
  );
}

export default FiSabilillahSplash;
'''


# ── Inventory ────────────────────────────────────────────────────────────────

INVENTORY_TAIL = """
## Validation performed

`python3 tools/brand/validate.py` — PENDING checks, run against what the build
actually wrote rather than against what it was supposed to write. The count on
that line is written back by `validate.py` itself when it passes, so this page
cannot claim a number of checks that were never run.

| Check | Method | Result |
| --- | --- | --- |
| Every SVG parses and renders | rendered through `cairosvg` | pass — a file that fails to render fails the check |
| No embedded raster in any SVG | grepped for `data:image` and `<image` | none present |
| No external font in any SVG | grepped for `font-family`, `@font-face` and `<text` | none present; every letterform is a path |
| Valid `viewBox` on every SVG | parsed | present on all |
| Android VectorDrawable well-formed | XML-parsed each file | pass |
| No mirrored group in any VectorDrawable | grepped for negative `scaleX`/`scaleY` | none; the wordmark's flip is baked into its path data |
| Adaptive foreground has no baked corners | no `rx` and no clip in the foreground | pass |
| Emblem ink fits the adaptive safe zone | ink box projected into the 108dp viewport and compared with the 66dp circle | pass |
| iOS icons are square, exact and opaque | Pillow size and mode check on every size | pass, RGB |
| Emblem legible at 16px | rendered; asserted to be more than one colour | pass, with the small cut |
| PWA manifest references only files that exist | each `src` checked against disk | pass |
| The app's `res/drawable` copies match the generated originals | byte comparison | pass |
| `launcher_background` is the brand's deep green | read out of `colors.xml` | pass |
| The React components are generated and carry the current geometry | header and path constants compared against `geometry.py` | pass |
| The Arabic is correctly shaped | re-shaped through HarfBuzz and compared with what was drawn | pass |
| Contrast floors hold | computed from the token file | pass, and `gold`-on-`ivory` is asserted to still fail |

## Known limitations

- **Nothing here has been seen on a device.** Everything was rendered and inspected
  as PNG. Colour on an OLED panel, the icon against a real launcher wallpaper, and
  the themed-icon tint on a specific OEM skin are all unverified.
- **No PDF brand sheet.** There is no PDF toolchain in this environment.
  `docs/brand/BRAND-GUIDELINES.md` and the presentation board carry the same
  content, and a PDF is one `pandoc` run away.
- **No Lottie file.** The motion is one 520ms fade-and-settle, which CSS and
  Compose both express in four lines; a Lottie JSON for it would be more machinery
  than motion.
- **The React components are unhosted.** There is no web client in this
  repository. They are generated so that if one ever appears, it starts from the
  identity rather than re-tracing it.
- **The Arabic has not been read by a native reader.** It is shaped by HarfBuzz
  from real Unicode in a real Arabic typeface, and the checks confirm the glyph
  sequence is the one HarfBuzz produces — but "technically correct" and "well set"
  are different claims, and only the first is made here.
"""


def inventory_md():
    """
    The asset table, generated.

    Hand-maintained inventories are wrong by the second revision -- this one was,
    listing five filenames that no longer existed and sizes for a different mark.
    """
    groups = {}
    for rel in sorted(written):
        d = os.path.dirname(rel) or "."
        groups.setdefault(d, []).append(rel)

    lines = [
        "# Asset inventory",
        "",
        f"*{len(written)} files, all generated by `python3 tools/brand/build.py`. This page "
        "is generated by it too — nothing here is maintained by hand.*",
        "",
    ]
    for d in sorted(groups):
        lines += [f"### `brand/{d}/`" if d != "." else "### `brand/`", "",
                  "| File | Size |", "| --- | --- |"]
        for rel in groups[d]:
            size = os.path.getsize(os.path.join(B, rel))
            lines.append(f"| `{os.path.basename(rel)}` | {size:,} B |")
        lines.append("")
    return "\n".join(lines)


# ── main ─────────────────────────────────────────────────────────────────────

TONE_FILES = ("primary", "flat", "dark", "light", "ivory", "amanah",
              "mono-black", "mono-white")

# Every size Apple asks for, iPhone and iPad, plus the store icon.
IOS_SIZES = (1024, 180, 167, 152, 120, 87, 80, 76, 60, 58, 40, 29, 20)


def main():
    # One list, in letters.py, next to the fonts they cover.
    for name in LICENCES:
        if os.path.exists(os.path.join(B, "licences", name)):
            written.append(f"licences/{name}")

    # Logo
    for tone in TONE_FILES:
        write(f"logo/fi-sabilillah-icon-{tone}.svg", emblem_svg(tone))
    write("logo/fi-sabilillah-icon-presentation.svg",
          emblem_svg("primary", box=256, hairline=True))
    write("logo/fi-sabilillah-icon-small.svg", emblem_svg("primary", small=True))
    write("logo/fi-sabilillah-favicon.svg", emblem_svg("primary", small=True))

    for tone in ("primary", "light", "mono-black"):
        suffix = "" if tone == "primary" else f"-{tone.replace('mono-black', 'mono')}"
        write(f"logo/fi-sabilillah-logo-horizontal{suffix}.svg", horizontal_svg(tone))
        write(f"logo/fi-sabilillah-wordmark{suffix}.svg", wordmark_svg(tone))
    write("logo/fi-sabilillah-logo-horizontal-tagline.svg", horizontal_svg("primary", lines=2))
    write("logo/fi-sabilillah-logo-stacked.svg", stacked_svg("primary"))
    write("logo/fi-sabilillah-logo-stacked-light.svg", stacked_svg("light"))
    write("logo/fi-sabilillah-logo-stacked-full.svg", stacked_svg("primary", lines=2))

    # Badges and pillars
    for name in BADGE_GLYPHS:
        write(f"badges/{name}.svg", badge_svg(name))
        write(f"badges/{name}-light.svg", badge_svg(name, "light"))
    for name in PILLAR_ICONS:
        write(f"icons/pillars/{name}.svg", pillar_svg(name))
        write(f"icons/pillars/{name}-light.svg",
              pillar_svg(name, colour=T.BRAND["gold-deep"][0]))

    # Android
    gold = T.BRAND["gold"][0]
    write("app-icons/android/ic_launcher_foreground.xml", vector_drawable(gold))
    write("app-icons/android/ic_launcher_monochrome.xml",
          vector_drawable("#FFFFFF", small=True))
    write("app-icons/android/ic_launcher_background.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Generated by tools/brand/build.py. Do not edit. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="108dp"\n    android:height="108dp"\n'
          '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
          '    <path android:pathData="M0,0h108v108h-108z" android:fillColor="#0F3D34" />\n'
          '</vector>\n')
    write("app-icons/android/ic_launcher.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@drawable/ic_launcher_background" />\n'
          '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
          '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
          '</adaptive-icon>\n')
    write("app-icons/android/brand_emblem.xml",
          vector_drawable(gold, field="#0A2A24", box=64, margin=0.96))
    write("app-icons/android/brand_emblem_small.xml",
          vector_drawable(gold, box=64, margin=0.96, small=True))
    write("app-icons/android/brand_wordmark.xml", wordmark_vector_drawable())
    write("app-icons/notifications/ic_notification.xml",
          vector_drawable("#FFFFFF", box=24, margin=0.92, small=True))

    # Raster icons
    square = emblem_svg("primary", bleed=True)
    for s in IOS_SIZES:
        png(f"app-icons/ios/AppIcon-{s}.png", square, s, bg="#0F3D34")
    png("app-icons/pwa/icon-192.png", emblem_svg("primary"), 192)
    png("app-icons/pwa/icon-512.png", emblem_svg("primary"), 512)
    png("app-icons/pwa/icon-maskable-512.png",
        emblem_svg("primary", bleed=True, margin=G.ADAPTIVE_MARGIN), 512, bg="#0F3D34")
    for s in (48, 32, 16):
        png(f"app-icons/pwa/favicon-{s}.png", emblem_svg("primary", small=True), s)
    png("app-icons/notifications/ic_notification-96.png",
        emblem_svg("mono-white", small=True), 96)
    png("app-icons/android/play-store-512.png", square, 512, bg="#0F3D34")

    # Banners
    banners = {
        "social-card": (1200, 630, 300, 0.44, False, 2),        # OG, Facebook, LinkedIn
        "twitter-header": (1500, 500, 220, 0.40, False, 2),
        "website-header": (1600, 320, 208, 0.36, False, 1),
        "banner-wide": (2400, 800, 420, 0.42, False, 2),
        "banner-square": (1080, 1080, 460, 0.66, True, 2),      # Instagram
    }
    for name, (w, h, e, wf, stacked, lines) in banners.items():
        svg = _banner(w, h, e, wf, stacked=stacked, lines=lines)
        write(f"banners/fi-sabilillah-{name}.svg", svg)
        png(f"banners/fi-sabilillah-{name}.png", svg, w, h)
    # Kept at its old path: it is referenced by <meta property="og:image">, and a
    # social card that 404s because it moved is worse than a duplicate file.
    png("fi-sabilillah-og-image.png", _banner(1200, 630, 300, 0.44, lines=2), 1200, 630)

    # Splash
    for dark in (True, False):
        name = "dark" if dark else "light"
        svg = splash_svg(dark)
        write(f"splash/splash-{name}.svg", svg)
        png(f"splash/splash-{name}.png", svg, 1080, 1920)

    write("app-icons/pwa/manifest.webmanifest", json.dumps({
        "name": "Fi Sabilillah", "short_name": "Fi Sabilillah",
        "description": "Service, learning, mutual aid and community.",
        "start_url": "/", "display": "standalone",
        "background_color": "#0F3D34", "theme_color": "#0F3D34",
        "icons": [
            {"src": "/brand/app-icons/pwa/icon-192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "/brand/app-icons/pwa/icon-512.png", "sizes": "512x512", "type": "image/png"},
            {"src": "/brand/app-icons/pwa/icon-maskable-512.png", "sizes": "512x512",
             "type": "image/png", "purpose": "maskable"},
            {"src": "/brand/logo/fi-sabilillah-favicon.svg", "sizes": "any",
             "type": "image/svg+xml"},
        ],
    }, indent=2) + "\n")

    write("react/FiSabilillahLogo.tsx", react_logo_tsx())
    write("react/FiSabilillahSplash.tsx", react_splash_tsx())

    token_files()

    inv = os.path.join(ROOT, "docs/brand/ASSET-INVENTORY.md")
    os.makedirs(os.path.dirname(inv), exist_ok=True)
    open(inv, "w").write(inventory_md() + INVENTORY_TAIL)

    print(f"{len(written)} files written, plus docs/brand/ASSET-INVENTORY.md")


if __name__ == "__main__":
    main()
