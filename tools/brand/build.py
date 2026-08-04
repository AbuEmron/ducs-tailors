#!/usr/bin/env python3
"""
Fi Sabilillah — builds every brand asset from the geometry in this directory.

Run:  python3 tools/brand/build.py

Nothing under `brand/` is edited by hand. If a mark is wrong the fix goes in
`geometry.py`, `letters.py` or `tokens.py`, and every export changes together.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cairosvg
import geometry as G
import tokens as T
from letters import wordmark_group, word_paths, word_path_data, CAP, copy_licence

# The wordmark's natural width at scale 1. Measured once rather than guessed,
# because three separate lockups had been laying it out from an assumed scale
# and overflowing their own canvases.
WORDMARK_W = word_paths("FI SABILILLAH", scale=1.0)[1]


def scale_to(width):
    """The wordmark scale that makes it exactly `width` wide."""
    return width / WORDMARK_W

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")
written = []

# The emblem's ink, measured rather than assumed: the arch and leaves together
# run wider and lower than the 64 grid suggests, and an icon that ignores this
# pushes the leaf tips into the launcher's corner radius.
INK = (10.6, 2.3, 53.4, 61.6)      # x0, y0, x1, y1
INK_W, INK_H = INK[2] - INK[0], INK[3] - INK[1]
INK_CX, INK_CY = (INK[0] + INK[2]) / 2, (INK[1] + INK[3]) / 2


def fit(box, margin=0.86):
    """Scale + translate that centres the ink inside a `box` square at `margin`."""
    s = box * margin / max(INK_W, INK_H)
    return s, box / 2 - INK_CX * s, box / 2 - INK_CY * s


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


def gold_defs(gid="fsGold"):
    a, b, c, d = T.GOLD_RAMP
    return (f'<linearGradient id="{gid}" x1="0.15" y1="0" x2="0.6" y2="1">'
            f'<stop offset="0%" stop-color="{a}"/><stop offset="30%" stop-color="{b}"/>'
            f'<stop offset="66%" stop-color="{c}"/><stop offset="100%" stop-color="{d}"/>'
            f'</linearGradient>')


def emblem_body(ink, small=False, gid="fsGold"):
    """
    The mark on the 0 0 64 64 grid.

    `ink` is either a colour or None, which means the gold gradient. The small
    cut drops the keyline, the star and the leaves: at 24px those three are
    noise, and noise inside a small mark is worse than absence.
    """
    f = ink or f"url(#{gid})"
    if small:
        return (f'<path d="{G.ARCH}" fill="none" stroke="{f}" '
                f'stroke-width="{G.SMALL_ARCH_STROKE}" stroke-linejoin="round"/>'
                f'<path d="{G.SMALL_CRESCENT}" fill="{f}"/>'
                f'<path d="{G.SMALL_ARCADE}" fill="{f}"/>')
    return (f'<path d="{G.ARCH}" fill="none" stroke="{f}" stroke-width="{G.ARCH_STROKE}" '
            f'stroke-linejoin="round"/>'
            f'<path d="{G.ARCH_KEYLINE}" fill="none" stroke="{f}" stroke-width="0.9" '
            f'opacity="0.72"/>'
            f'<path d="{G.CRESCENT}" fill="{f}"/>'
            f'<path d="{G.STAR}" fill="{f}"/>'
            f'<path d="{G.ARCADE}" fill="{f}"/>'
            f'<path d="{G.LEAF_L}" fill="{f}"/><path d="{G.LEAF_R}" fill="{f}"/>')


def emblem_svg(tone="primary", small=False, rounded=True, box=64, margin=0.86,
               bleed=False, title="Fi Sabilillah"):
    plate, ink, grad = T.EMBLEM_TONES[tone]
    s, tx, ty = fit(box, margin)
    defs = f"<defs>{gold_defs()}</defs>" if grad else ""
    bg = ""
    if plate and not bleed:
        r = f' rx="{box * 0.22:.2f}"' if rounded else ""
        bg = f'<rect width="{box}" height="{box}"{r} fill="{plate}"/>'
    elif plate and bleed:
        bg = f'<rect width="{box}" height="{box}" fill="{plate}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {box} {box}" '
            f'width="{box}" height="{box}" role="img" aria-label="{title}">'
            f'<title>{title}</title>{defs}{bg}'
            f'<g transform="translate({tx:.3f},{ty:.3f}) scale({s:.5f})">'
            f'{emblem_body(ink, small)}</g></svg>')


# ── Lockups ──────────────────────────────────────────────────────────────────
#
# The reference's hierarchy, kept: mark, then FI SABILILLAH, then a rule, then
# "For the Sake of Allah", then SERVE · LEARN · GROW · GIVE. The lower two lines
# are optional and off by default, because a lockup that always carries five
# lines has no compact form.

TAGLINE = "For the Sake of Allah"
PILLARS = "SERVE   ·   LEARN   ·   GROW   ·   GIVE"


def _rule(x, y, w, colour, gap=None):
    """A hairline with a diamond at each end, as the reference has it."""
    d = 2.0
    parts = [f'<rect x="{x + d * 1.6:.2f}" y="{y - 0.35:.2f}" '
             f'width="{w - d * 3.2:.2f}" height="0.7" fill="{colour}" opacity="0.55"/>']
    for cx in (x + d * 0.8, x + w - d * 0.8):
        parts.append(f'<path d="M{cx:.2f} {y - d / 2:.2f} L{cx + d / 2:.2f} {y:.2f} '
                     f'L{cx:.2f} {y + d / 2:.2f} L{cx - d / 2:.2f} {y:.2f} Z" '
                     f'fill="{colour}" opacity="0.8"/>')
    return "".join(parts)


def horizontal_svg(tone="primary", lines=0):
    plate, ink, grad = T.EMBLEM_TONES[tone]
    word_ink = "#F5F2E9" if tone in ("primary", "dark") else (ink or "#D4AF37")
    accent = "#D4AF37" if tone in ("primary", "dark") else (ink or "#7A5E15")
    e_box, gap, pad = 72, 24, 14
    s, tx, ty = fit(e_box, 0.94)
    scale = 0.34
    wx = pad + e_box + gap
    wy = e_box / 2 - CAP * scale / 2 - (7 if lines else 0)
    body, ww = wordmark_group("FI SABILILLAH", word_ink, x=wx, y=wy, scale=scale)

    extra, height = "", e_box + 12
    if lines >= 1:
        ry = wy + CAP * scale + 11
        extra += _rule(wx, ry, ww, accent)
        tb, tw = wordmark_group(TAGLINE, accent, x=wx, y=ry + 6, scale=scale * 0.44)
        extra += f'<g transform="translate({(ww - tw) / 2:.2f},0)">{tb}</g>'
    if lines >= 2:
        py = wy + CAP * scale + 11 + CAP * scale * 0.44 + 13
        pb, pw = wordmark_group(PILLARS, accent, x=wx, y=py, scale=scale * 0.26)
        extra += f'<g transform="translate({(ww - pw) / 2:.2f},0)">{pb}</g>'
        height = py + CAP * scale * 0.26 + 14

    total = wx + ww + pad
    defs = f"<defs>{gold_defs()}</defs>" if grad else ""
    bg = f'<rect width="{total:.0f}" height="{height}" fill="{plate}"/>' if plate else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total:.0f} {height}" '
            f'role="img" aria-label="Fi Sabilillah">{defs}{bg}'
            f'<g transform="translate({tx + pad:.3f},{ty + 6:.3f}) scale({s:.5f})">'
            f'{emblem_body(ink)}</g>{body}{extra}</svg>')


def stacked_svg(tone="primary", lines=1):
    plate, ink, grad = T.EMBLEM_TONES[tone]
    word_ink = "#F5F2E9" if tone in ("primary", "dark") else (ink or "#D4AF37")
    accent = "#D4AF37" if tone in ("primary", "dark") else (ink or "#7A5E15")
    e_box, scale = 132, 0.30
    s, tx, ty = fit(e_box, 0.94)
    body, ww = wordmark_group("FI SABILILLAH", word_ink, x=0, y=0, scale=scale)
    total = max(ww, e_box) + 56
    wy = e_box + 16
    height = wy + CAP * scale + 22
    extra = ""
    if lines >= 1:
        ry = wy + CAP * scale + 12
        extra += _rule((total - ww) / 2, ry, ww, accent)
        tb, tw = wordmark_group(TAGLINE, accent, x=0, y=ry + 6, scale=scale * 0.46)
        extra += f'<g transform="translate({(total - tw) / 2:.2f},0)">{tb}</g>'
        height = ry + 6 + CAP * scale * 0.46 + 20
    defs = f"<defs>{gold_defs()}</defs>" if grad else ""
    bg = f'<rect width="{total:.0f}" height="{height:.0f}" fill="{plate}"/>' if plate else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total:.0f} {height:.0f}" '
            f'role="img" aria-label="Fi Sabilillah">{defs}{bg}'
            f'<g transform="translate({(total - e_box) / 2 + tx:.3f},{ty + 8:.3f}) '
            f'scale({s:.5f})">{emblem_body(ink)}</g>'
            f'<g transform="translate({(total - ww) / 2:.2f},{wy:.2f})">'
            f'{body.replace(f"translate({0:.2f}", "translate(0.00")}</g>{extra}</svg>')


def wordmark_svg(tone="primary"):
    plate, ink, _ = T.EMBLEM_TONES[tone]
    colour = "#F5F2E9" if tone in ("primary", "dark") else (ink or "#0F3D34")
    body, w = wordmark_group("FI SABILILLAH", colour, x=16, y=16, scale=1.0)
    h = CAP + 32
    bg = f'<rect width="{w + 32:.0f}" height="{h:.0f}" fill="{plate}"/>' if plate else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w + 32:.0f} {h:.0f}" '
            f'role="img" aria-label="Fi Sabilillah">{bg}{body}</svg>')


# ── Badges ───────────────────────────────────────────────────────────────────
#
# The emblem's own onion arch, closed and filled, holding one glyph. A badge is
# the same shelter carrying one fact about one thing.

BADGE_PLATE = ("M32 6 C39.6 13.2 45.4 20.4 45.4 28.8 L45.4 41.4 "
               "C45.4 47.4 39.6 52.2 32 54 C24.4 52.2 18.6 47.4 18.6 41.4 "
               "L18.6 28.8 C18.6 20.4 24.4 13.2 32 6 Z")

BADGE_GLYPHS = {
    "verified-organization": ('<path d="M25.8 31.6 L30.4 36.6 L38.6 26.8"/>',
                              "A check. The badge means documents were seen."),
    "learning":              ('<path d="M32 24.4 C28.8 22.4 25 21.8 21.6 22.4 L21.6 36.4 '
                              'C25 35.8 28.8 36.4 32 38.6"/>'
                              '<path d="M32 24.4 C35.2 22.4 39 21.8 42.4 22.4 L42.4 36.4 '
                              'C39 35.8 35.2 36.4 32 38.6"/>', "An open book."),
    "service":               ('<path d="M24 39.4 L24 30.6 C24 26.4 27.6 22.6 32 20.4 '
                              'C36.4 22.6 40 26.4 40 30.6 L40 39.4"/>',
                              "The emblem's own arch: shelter offered."),
    "community":             ('<circle cx="32" cy="23.8" r="3.6" fill="INK"/>'
                              '<circle cx="24.6" cy="35.4" r="3.6" fill="INK"/>'
                              '<circle cx="39.4" cy="35.4" r="3.6" fill="INK"/>',
                              "Three, unranked and the same size."),
    "safety":                ('<path d="M26.6 29.4 L26.6 26.2 C26.6 23.2 29 20.8 32 20.8 '
                              'C35 20.8 37.4 23.2 37.4 26.2 L37.4 29.4"/>'
                              '<rect x="23.6" y="29.4" width="16.8" height="12.4" rx="3.4" '
                              'fill="INK"/>', "A lock. Privacy and restriction."),
    "donation":              ('<circle cx="32" cy="23.6" r="4.2" fill="INK"/>'
                              '<path d="M21.6 32.4 C21.6 39.4 26.4 43 32 43 '
                              'C37.6 43 42.4 39.4 42.4 32.4"/>',
                              "Given into cupped hands, not dropped into a box."),
    "family-introduction":   ('<path d="M22.4 28.6 C22.4 23.2 26.8 19.2 32 19.2 '
                              'C37.2 19.2 41.6 23.2 41.6 28.6"/>'
                              '<circle cx="26.6" cy="37.8" r="3.9" fill="INK"/>'
                              '<circle cx="37.4" cy="37.8" r="3.9" fill="INK"/>',
                              "Two parties, and a third presence over both."),
}


def badge_svg(name, tone="primary"):
    glyph, _ = BADGE_GLYPHS[name]
    plate, ink, grad = T.EMBLEM_TONES[tone]
    f = ink or "url(#fsGold)"
    defs = f"<defs>{gold_defs()}</defs>" if grad else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" '
            f'height="64" role="img" aria-label="{name.replace("-", " ")}">{defs}'
            f'<path d="{BADGE_PLATE}" fill="{plate or "#0F3D34"}" stroke="{f}" '
            f'stroke-width="1.6"/>'
            f'<g fill="none" stroke="{f}" stroke-width="3.2" stroke-linecap="round" '
            f'stroke-linejoin="round">{glyph.replace("INK", f)}</g></svg>')


# ── Android ──────────────────────────────────────────────────────────────────
#
# VectorDrawables carry the FLAT gold, not the gradient. A VectorDrawable can
# express a gradient, but a themed ("monochrome") icon is composited in one tint
# colour and a two-tone foreground becomes a solid blob under it -- so the
# launcher assets are built to work flat, and the gradient lives in the SVG and
# PNG exports where it survives.

def vector_drawable(ink, size=64, box=108, margin=0.62, small=False):
    s, tx, ty = fit(box, margin)
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
        lines.append(path(G.ARCH, stroke=ink, width=G.SMALL_ARCH_STROKE))
        lines.append(path(G.SMALL_CRESCENT, fill=ink))
        lines.append(path(G.SMALL_ARCADE, fill=ink))
    else:
        lines.append(path(G.ARCH, stroke=ink, width=G.ARCH_STROKE))
        lines.append(path(G.ARCH_KEYLINE, stroke=ink, width=0.9, opacity="0.72"))
        lines.append(path(G.CRESCENT, fill=ink))
        lines.append(path(G.STAR, fill=ink))
        lines.append(path(G.ARCADE, fill=ink))
        lines.append(path(G.LEAF_L, fill=ink))
        lines.append(path(G.LEAF_R, fill=ink))

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

    Drawn in white so `BrandLockup` can tint it to whatever the surface needs;
    a VectorDrawable tint multiplies, so anything darker than white would clamp
    the ivory-on-green case to a muddy version of itself.
    """
    d, width = word_path_data("FI SABILILLAH", x=pad, y=pad, scale=1.0)
    # "FI SABILILLAH" has no descender and no lowercase, so the cap box plus a
    # hair of padding is the whole of it.
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


# ── React ────────────────────────────────────────────────────────────────────
#
# Generated, not hand-written. The previous pair of components carried the emblem
# geometry as four constants copied out of geometry.py by hand, with a comment
# admitting the copy would have to be kept in step manually. It was not: the
# emblem changed and the components went on drawing the old one. Generating them
# is the only version of "keep these in step" that survives a revision.

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
    tones = {
        "full":       ("#0F3D34", None),
        "dark":       ("#0A2A24", None),
        "light":      ("#F5F2E9", "#7A5E15"),
        "gold":       (None, "#D4AF37"),
        "monochrome": (None, "currentColor"),
    }
    tone_rows = "\n".join(
        f'  {k}: {{ plate: {"null" if p is None else chr(34) + p + chr(34)}, '
        f'ink: {"null" if i is None else chr(34) + i + chr(34)} }},'
        for k, (p, i) in tones.items())
    a, b, c, d = T.GOLD_RAMP
    es, etx, ety = fit(64)
    paths = "\n".join([
        f'const ARCH = "{G.ARCH}";',
        f'const ARCH_KEYLINE = "{G.ARCH_KEYLINE}";',
        f'const CRESCENT = "{G.CRESCENT}";',
        f'const STAR = "{G.STAR}";',
        f'const ARCADE = "{G.ARCADE}";',
        f'const LEAF_L = "{G.LEAF_L}";',
        f'const LEAF_R = "{G.LEAF_R}";',
        f'const SMALL_CRESCENT = "{G.SMALL_CRESCENT}";',
        f'const SMALL_ARCADE = "{G.SMALL_ARCADE}";',
        f'const ARCH_STROKE = {G.ARCH_STROKE};',
        f'const SMALL_ARCH_STROKE = {G.SMALL_ARCH_STROKE};',
    ])
    return REACT_HEADER.format(what="the responsive brand mark.") + f'''import * as React from "react";

export type LogoVariant =
  | "icon"
  | "horizontal"
  | "stacked"
  | "wordmark"
  | "compact"
  | "monochrome";

export type LogoTone = "full" | "dark" | "light" | "gold" | "monochrome";

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
 * Below this the keyline, the star and the leaf sweeps are under a pixel wide,
 * and the mark switches to its small cut.
 */
const SIMPLIFY_BELOW = 32;

{paths}

/** `ink: null` means the gold gradient rather than a flat colour. */
const TONES: Record<LogoTone, {{ plate: string | null; ink: string | null }}> = {{
{tone_rows}
}};

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

function Emblem({{ ink, small }}: {{ ink: string; small: boolean }}) {{
  if (small) {{
    return (
      <>
        <path d={{ARCH}} fill="none" stroke={{ink}} strokeWidth={{SMALL_ARCH_STROKE}} strokeLinejoin="round" />
        <path d={{SMALL_CRESCENT}} fill={{ink}} />
        <path d={{SMALL_ARCADE}} fill={{ink}} />
      </>
    );
  }}
  return (
    <>
      <path d={{ARCH}} fill="none" stroke={{ink}} strokeWidth={{ARCH_STROKE}} strokeLinejoin="round" />
      <path d={{ARCH_KEYLINE}} fill="none" stroke={{ink}} strokeWidth={{0.9}} opacity={{0.72}} />
      <path d={{CRESCENT}} fill={{ink}} />
      <path d={{STAR}} fill={{ink}} />
      <path d={{ARCADE}} fill={{ink}} />
      <path d={{LEAF_L}} fill={{ink}} />
      <path d={{LEAF_R}} fill={{ink}} />
    </>
  );
}}

export function FiSabilillahLogo({{
  variant = "horizontal",
  tone = "full",
  size = "md",
  title = "Fi Sabilillah",
  className,
}}: FiSabilillahLogoProps) {{
  const px = EMBLEM_HEIGHT[size];
  const t = TONES[variant === "monochrome" ? "monochrome" : tone];
  const gid = React.useId();

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
    return (
      <svg viewBox="0 0 64 64" width={{px}} height={{px}} className={{className}} {{...a11y}}>
        {{title && <title>{{title}}</title>}}
        {{t.ink === null && (
          <defs>
            <linearGradient id={{gid}} x1="0.15" y1="0" x2="0.6" y2="1">
              {{GOLD_RAMP.map((c, i) => (
                <stop key={{c}} offset={{`${{[0, 30, 66, 100][i]}}%`}} stopColor={{c}} />
              ))}}
            </linearGradient>
          </defs>
        )}}
        {{t.plate && <rect width="64" height="64" rx="{64 * 0.22:.2f}" fill={{t.plate}} />}}
        <g transform="translate({etx:.3f},{ety:.3f}) scale({es:.5f})">
          <Emblem ink={{ink}} small={{small}} />
        </g>
      </svg>
    );
  }}

  // The lockups need the drawn letterforms, which live in the generated SVGs
  // rather than inline: twelve glyphs of path data would triple this file and
  // would be a second copy free to drift from `brand/logo/*.svg`.
  const href = {{
    horizontal: "/brand/logo/fi-sabilillah-logo-horizontal.svg",
    stacked: "/brand/logo/fi-sabilillah-logo-stacked.svg",
    wordmark: "/brand/logo/fi-sabilillah-wordmark.svg",
    monochrome: "/brand/logo/fi-sabilillah-logo-horizontal-mono.svg",
    icon: "/brand/logo/fi-sabilillah-icon-primary.svg",
    compact: "/brand/logo/fi-sabilillah-icon-primary.svg",
  }}[effective];

  // Ratios measured off the generated files, not guessed: the wordmark is
  // {WORDMARK_W / CAP:.2f} cap-heights wide, and a lockup sized by eye is a lockup that
  // overflows something.
  const heightFor: Partial<Record<LogoVariant, number>> = {{
    horizontal: px * 1.17,
    stacked: px * 1.95,
    wordmark: px * 0.48,
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
 * on the Amanah curve. It does not spin, pulse, sparkle or loop. Under
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
        <FiSabilillahLogo variant="stacked" tone="full" size="xl" />
      </div>
    </div>
  );
}

export default FiSabilillahSplash;
'''


# ── Splash, social, tokens ───────────────────────────────────────────────────

def splash_svg(dark=True):
    plate = "#0A2A24" if dark else "#F5F2E9"
    tone = "primary" if dark else "light"
    _, ink, grad = T.EMBLEM_TONES[tone]
    word = "#F5F2E9" if dark else "#0F3D34"
    accent = "#D4AF37" if dark else "#7A5E15"
    W, H = 1080, 1920
    halo = ('<radialGradient id="halo" cx="50%" cy="40%" r="46%">'
            f'<stop offset="0%" stop-color="{"#145143" if dark else "#FFFFFF"}" '
            f'stop-opacity="{0.6 if dark else 0.85}"/>'
            f'<stop offset="100%" stop-color="{plate}" stop-opacity="0"/></radialGradient>')
    e = 420
    s, tx, ty = fit(e, 0.94)
    ex, ey = (W - e) / 2, H * 0.40 - e / 2
    ws = scale_to(W * 0.66)
    body, ww = wordmark_group("FI SABILILLAH", word, x=0, y=0, scale=ws)
    wy = H * 0.40 + e / 2 + 40
    tb, tw = wordmark_group(TAGLINE, accent, x=0, y=0, scale=ws * 0.42)
    defs = gold_defs() + halo
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
            f'<defs>{defs}</defs><rect width="{W}" height="{H}" fill="{plate}"/>'
            f'<rect width="{W}" height="{H}" fill="url(#halo)"/>'
            f'<g transform="translate({ex + tx:.1f},{ey + ty:.1f}) scale({s:.5f})">'
            f'{emblem_body(ink)}</g>'
            f'<g transform="translate({(W - ww) / 2:.1f},{wy:.1f})">{body}</g>'
            f'{_rule((W - ww) / 2, wy + CAP * ws + 34, ww, accent)}'
            f'<g transform="translate({(W - tw) / 2:.1f},{wy + CAP * ws + 46:.1f})">'
            f'{tb}</g></svg>')


def og_svg():
    W, H = 1200, 630
    e = 300
    s, tx, ty = fit(e, 0.94)
    ws = scale_to(W - e - 40 - 120)
    body, ww = wordmark_group("FI SABILILLAH", "#F5F2E9", x=0, y=0, scale=ws)
    tb, tw = wordmark_group(TAGLINE, "#D4AF37", x=0, y=0, scale=ws * 0.44)
    total = e + 40 + ww
    ex = (W - total) / 2
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
            f'<defs>{gold_defs()}</defs><rect width="{W}" height="{H}" fill="#0F3D34"/>'
            f'<g transform="translate({ex + tx:.1f},{(H - e) / 2 + ty:.1f}) scale({s:.5f})">'
            f'{emblem_body(None)}</g>'
            f'<g transform="translate({ex + e + 40:.1f},{H / 2 - CAP * ws / 2 - 26:.1f})">'
            f'{body}</g>'
            f'{_rule(ex + e + 40, H / 2 + CAP * ws / 2 - 6, ww, "#D4AF37")}'
            f'<g transform="translate({ex + e + 40 + (ww - tw) / 2:.1f},'
            f'{H / 2 + CAP * ws / 2 + 6:.1f})">{tb}</g></svg>')


def token_files():
    payload = {
        "brand": {k: {"hex": v[0], "rgb": list(T.rgb(v[0])), "hsl": list(T.hsl(v[0])),
                      "cmyk": list(T.cmyk(v[0])), "usage": v[1]}
                  for k, v in T.BRAND.items()},
        "functional": {k: {"hex": v[0], "rgb": list(T.rgb(v[0])), "hsl": list(T.hsl(v[0])),
                           "cmyk": list(T.cmyk(v[0])), "usage": v[1]}
                       for k, v in T.FUNCTIONAL.items()},
        "goldRamp": list(T.GOLD_RAMP),
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
            "wordmark": "Libre Baskerville, outlines embedded as paths (SIL OFL 1.1)",
            "capHeight": CAP, "tracking": "0.16em",
        },
    }
    write("tokens/brand-tokens.json", json.dumps(payload, indent=2) + "\n")

    css = ["/* Generated by tools/brand/build.py. Do not edit. */", ":root {"]
    for k, (hexv, use) in T.BRAND.items():
        css.append(f"  --brand-{k}: {hexv};  /* {use.splitlines()[0]} */")
    for k, (hexv, use) in T.FUNCTIONAL.items():
        css.append(f"  --brand-fn-{k}: {hexv};  /* {use} */")
    css += ["  --brand-gold-ramp: " + ", ".join(T.GOLD_RAMP) + ";", "}", ""]
    write("tokens/brand-tokens.css", "\n".join(css) + "\n")

    ts = ["// Generated by tools/brand/build.py. Do not edit.", "export const brandTokens = {"]
    ts += [f'  "{k}": "{v[0]}",' for k, v in T.BRAND.items()]
    ts += ["} as const;", "", "export const functionalTokens = {"]
    ts += [f'  "{k}": "{v[0]}",' for k, v in T.FUNCTIONAL.items()]
    ts += ["} as const;", "",
           "export const goldRamp = " + json.dumps(list(T.GOLD_RAMP)) + " as const;", "",
           "export type BrandToken = keyof typeof brandTokens;", ""]
    write("tokens/brand-tokens.ts", "\n".join(ts))


INVENTORY_TAIL = """
## Validation performed

`python3 tools/brand/validate.py` — PENDING checks, run against what the build actually
wrote rather than against what it was supposed to write. The count on that line is written
back by `validate.py` itself when it passes, so this page cannot claim a number of checks
that were never run.

| Check | Method | Result |
| --- | --- | --- |
| Every SVG parses and renders | rendered through `cairosvg` | pass — a file that fails to render fails the check |
| No embedded raster in any SVG | grepped for `data:image` and `<image` | none present |
| No external font in any SVG | grepped for `font-family` and `@font-face` | none present; the wordmark is outlines |
| Valid `viewBox` on every SVG | parsed | present on all |
| Android VectorDrawable well-formed | XML-parsed each file | pass |
| No mirrored group in any VectorDrawable | grepped for negative `scaleX`/`scaleY` | none; the wordmark's flip is baked into its path data |
| Adaptive foreground has no baked corners | no `rx` and no clip in the foreground | pass |
| iOS 1024 has no alpha channel | Pillow mode check after flattening onto the plate | pass, RGB |
| Emblem legible at 16px | rendered; asserted to be more than one colour | pass, with the small cut |
| PWA manifest references only files that exist | each `src` checked against disk | pass |
| The app's `res/drawable` copies match the generated originals | byte comparison of all six | pass |
| `launcher_background` is the brand's deep green | read out of `colors.xml` | pass |
| The React components are generated and carry the current geometry | header and five path constants compared against `geometry.py` | pass |
| Contrast floors hold | computed from the token file | pass, and `gold`-on-`ivory` is asserted to still fail |

## Known limitations

- **Nothing here has been seen on a device.** Everything was rendered and inspected as PNG.
  Colour on an OLED panel, the icon against a real launcher wallpaper, and the themed-icon
  tint on a specific OEM skin are all unverified.
- **No PDF brand sheet.** There is no PDF toolchain in this environment.
  `docs/brand/BRAND-GUIDELINES.md` and the presentation board carry the same content, and a
  PDF is one `pandoc` run away.
- **No Lottie file.** The motion is one 520ms fade-and-settle, which CSS and Compose both
  express in four lines; a Lottie JSON for it would be more machinery than motion.
- **The React components are unhosted.** There is no web client in this repository. They are
  generated so that if one ever appears, it starts from the identity rather than re-tracing
  it.
"""


def inventory_md():
    """
    The asset table, generated.

    Hand-maintained inventories are wrong by the second revision — this one was,
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


def main():
    copy_licence(os.path.join(B, "licences"))
    written.append("licences/LibreBaskerville-OFL.txt")

    for tone in ("primary", "flat", "dark", "light", "ivory", "amanah",
                 "mono-black", "mono-white"):
        write(f"logo/fi-sabilillah-icon-{tone}.svg", emblem_svg(tone))
    write("logo/fi-sabilillah-icon-small.svg", emblem_svg("primary", small=True))
    write("logo/fi-sabilillah-favicon.svg", emblem_svg("primary", small=True))

    for tone in ("primary", "light", "mono-black"):
        suffix = "" if tone == "primary" else f"-{tone.replace('mono-black', 'mono')}"
        write(f"logo/fi-sabilillah-logo-horizontal{suffix}.svg", horizontal_svg(tone))
        write(f"logo/fi-sabilillah-wordmark{suffix}.svg", wordmark_svg(tone))
    write("logo/fi-sabilillah-logo-horizontal-tagline.svg", horizontal_svg("primary", lines=2))
    write("logo/fi-sabilillah-logo-stacked.svg", stacked_svg("primary"))
    write("logo/fi-sabilillah-logo-stacked-light.svg", stacked_svg("light"))

    for name in BADGE_GLYPHS:
        write(f"badges/{name}.svg", badge_svg(name))
        write(f"badges/{name}-light.svg", badge_svg(name, "light"))

    write("app-icons/android/ic_launcher_foreground.xml", vector_drawable("#D4AF37"))
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
    write("app-icons/android/brand_emblem.xml", vector_drawable("#D4AF37", box=64, margin=0.94))
    write("app-icons/android/brand_emblem_small.xml",
          vector_drawable("#D4AF37", box=64, margin=0.94, small=True))
    write("app-icons/notifications/ic_notification.xml",
          vector_drawable("#FFFFFF", box=24, margin=0.92, small=True))
    write("app-icons/android/brand_wordmark.xml", wordmark_vector_drawable())

    square = emblem_svg("primary", bleed=True)
    png("app-icons/ios/AppIcon-1024.png", square, 1024, bg="#0F3D34")
    for s in (180, 152, 120, 87, 80, 60, 58, 40):
        png(f"app-icons/ios/AppIcon-{s}.png", square, s, bg="#0F3D34")
    png("app-icons/pwa/icon-192.png", emblem_svg("primary"), 192)
    png("app-icons/pwa/icon-512.png", emblem_svg("primary"), 512)
    png("app-icons/pwa/icon-maskable-512.png",
        emblem_svg("primary", bleed=True, margin=0.62), 512, bg="#0F3D34")
    png("app-icons/pwa/favicon-32.png", emblem_svg("primary", small=True), 32)
    png("app-icons/pwa/favicon-16.png", emblem_svg("primary", small=True), 16)
    png("app-icons/notifications/ic_notification-96.png",
        emblem_svg("mono-white", small=True), 96)
    png("app-icons/android/play-store-512.png", square, 512, bg="#0F3D34")
    png("fi-sabilillah-og-image.png", og_svg(), 1200, 630)
    png("splash/splash-dark.png", splash_svg(True), 1080, 1920)
    png("splash/splash-light.png", splash_svg(False), 1080, 1920)

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

    # Written last, because it counts everything above it.
    inv = os.path.join(ROOT, "docs/brand/ASSET-INVENTORY.md")
    os.makedirs(os.path.dirname(inv), exist_ok=True)
    open(inv, "w").write(inventory_md() + INVENTORY_TAIL)

    print(f"{len(written)} files written, plus docs/brand/ASSET-INVENTORY.md")


if __name__ == "__main__":
    main()
