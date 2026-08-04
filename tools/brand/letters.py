"""
Fi Sabilillah — every letterform in the identity, as outlines.

THREE FACES, ALL FROM THE REFERENCE

The reference names its own typography: **Playfair Display** for display and
**Inter** for text. Both are used here, exactly as named. The third face is
**Noto Kufi Arabic**, for the calligraphy inside the emblem.

All three are SIL Open Font License 1.1, all three are vendored into
`tools/brand/fonts/`, and all three licences travel with the repository under
`brand/licences/`. The OFL permits embedding and permits deriving outlines; what
it forbids -- selling the font software, reusing a reserved name -- does not
apply to a logotype.

WHY OUTLINES AND NOT A FONT REFERENCE

A font-referencing SVG depends on that font being installed wherever the file is
opened: an app-store listing, a partner's deck, a printer's RIP. Where it is not,
the wordmark silently falls back to Times New Roman and nobody notices until it
is printed. Outlines cannot fall back.

THE ARABIC

`في سبيل الله` -- *fī sabīlillāh*, "in the path of Allah". Real Unicode Arabic,
shaped by HarfBuzz, in a real Arabic typeface. HarfBuzz picks the contextual
forms and substitutes the ﷲ ligature; nothing here chooses a glyph by hand.

That matters more than it sounds. The brief rules out "fake or malformed Arabic
calligraphy" twice, and it is right to: script that approximates Arabic without
resolving into words is the one thing a Muslim audience notices first and
forgives last. The answer is not to omit the Arabic the reference asks for -- it
is to set it correctly. This does.
"""
import os

import uharfbuzz as hb
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts")

DISPLAY = os.path.join(FONTS, "PlayfairDisplay-Medium.ttf")
DISPLAY_REGULAR = os.path.join(FONTS, "PlayfairDisplay-Regular.ttf")
TEXT = os.path.join(FONTS, "Inter-SemiBold.ttf")
TEXT_REGULAR = os.path.join(FONTS, "Inter-Regular.ttf")
ARABIC = os.path.join(FONTS, "NotoKufiArabic-Bold.ttf")

LICENCES = {
    "PlayfairDisplay-OFL.txt": "Playfair Display — the reference's display face",
    "Inter-OFL.txt": "Inter — the reference's text face",
    "NotoKufiArabic-OFL.txt": "Noto Kufi Arabic — the emblem's calligraphy",
}

CAP = 100.0          # the grid every lockup is laid out on
TRACKING = 0.17      # em. The reference's wordmark spacing, measured off it.

_cache = {}


def _load(path):
    if path not in _cache:
        tt = TTFont(path)
        cap = getattr(tt["OS/2"], "sCapHeight", None) or tt["head"].unitsPerEm * 0.7
        _cache[path] = (tt, tt.getGlyphSet(), tt.getBestCmap(), tt["hmtx"],
                        tt["head"].unitsPerEm, cap)
    return _cache[path]


# ── Latin ────────────────────────────────────────────────────────────────────

def word_path_data(text, x=0.0, y=0.0, scale=1.0, font=DISPLAY, tracking=None):
    """
    The whole run as one already-positioned path, and its width.

    Placement is baked into the coordinates rather than expressed as a wrapping
    transform, because a VectorDrawable has no `transform` attribute and the
    obvious workaround -- a `<group>` with a negative `android:scaleY` -- is the
    kind of thing that renders correctly on the machine it was written on and
    surprises somebody two Android versions later.

    `y` is the top of the cap box, so every lockup's alignment maths is in cap
    heights rather than in whatever the font's ascender happens to be.

    Glyph outlines wind consistently, so one path holding every letter fills
    identically to one path per letter under the nonzero rule.
    """
    _, glyphs, cmap, hmtx, _upem, cap_h = _load(font)
    track = TRACKING if tracking is None else tracking
    s = (CAP / cap_h) * scale
    baseline = y + CAP * scale
    parts, cursor = [], x

    for ch in text:
        name = cmap.get(ord(ch))
        if name is None:
            cursor += 0.34 * CAP * scale
            continue
        if ch != " ":
            pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}")
            # (x, y) -> (cursor + x*s, baseline - y*s): the font's y runs up from
            # the baseline, the drawing surface's runs down.
            glyphs[name].draw(TransformPen(pen, (s, 0, 0, -s, cursor, baseline)))
            d = pen.getCommands()
            if d:
                parts.append(d)
        cursor += hmtx[name][0] * s + track * CAP * scale

    return " ".join(parts), cursor - track * CAP * scale - x


def measure(text, font=DISPLAY, tracking=None):
    """Width of `text` at scale 1. Measured once, so nothing has to guess it."""
    return word_path_data(text, scale=1.0, font=font, tracking=tracking)[1]


def scale_for_width(text, width, font=DISPLAY, tracking=None):
    """The scale that makes `text` exactly `width` wide."""
    return width / measure(text, font=font, tracking=tracking)


def group(text, fill, x, y, scale, font=DISPLAY, tracking=None, extra=""):
    """The run as one fill-coloured SVG path, and its width."""
    d, w = word_path_data(text, x=x, y=y, scale=scale, font=font, tracking=tracking)
    return f'<path d="{d}" fill="{fill}"{extra}/>', w


# ── Arabic ───────────────────────────────────────────────────────────────────

CALLIGRAPHY = "في سبيل الله"


def _shape(text, font):
    """`[(glyph_name, pen_x, pen_y)]` in visual order, from HarfBuzz."""
    blob = hb.Blob.from_file_path(font)
    hbfont = hb.Font(hb.Face(blob))

    buf = hb.Buffer()
    buf.add_str(text)
    buf.direction = "rtl"
    buf.script = "Arab"
    buf.language = "ar"
    hb.shape(hbfont, buf)

    tt, _glyphs, _cmap, _hmtx, _upem, _cap = _load(font)
    order = tt.getGlyphOrder()

    out, cursor = [], 0.0
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        out.append((order[info.codepoint], cursor + pos.x_offset, pos.y_offset))
        cursor += pos.x_advance
    return out


def _draw(run, font, transform):
    """Emit `run` through `transform` as one path, and its inked bounds."""
    _tt, glyphs, _cmap, _hmtx, _upem, _cap = _load(font)
    a, b, c, d_, e, f = transform
    parts, bounds = [], BoundsPen(glyphs)
    for name, gx, gy in run:
        # The glyph's own placement, composed with the caller's transform. y is
        # negated because the font's axis runs up from the baseline and the
        # drawing surface's runs down.
        t = (a, b, c, d_, e + gx * a, f - gy * d_)
        pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}")
        glyphs[name].draw(TransformPen(pen, t))
        cmds = pen.getCommands()
        if cmds:
            parts.append(cmds)
        glyphs[name].draw(TransformPen(bounds, t))
    return " ".join(parts), bounds.bounds


def arabic_line(text, box, font=ARABIC):
    """
    One shaped Arabic run, scaled and centred inside `box`.

    Measured first at unit scale, then drawn once at the scale that fits -- so
    the placement is baked into the coordinates rather than wrapped in a
    transform. Same reason as the Latin runs: this path has to survive into
    `res/drawable`, where there is no transform attribute to wrap it in.

    Fitted to whichever of width or height binds first, so the phrase never
    distorts.
    """
    run = _shape(text, font)
    _d, (x0, y0, x1, y1) = _draw(run, font, (1, 0, 0, -1, 0, 0))

    bx0, by0, bx1, by1 = box
    s = min((bx1 - bx0) / (x1 - x0), (by1 - by0) / (y1 - y0))
    tx = bx0 + ((bx1 - bx0) - (x1 - x0) * s) / 2 - x0 * s
    ty = by0 + ((by1 - by0) - (y1 - y0) * s) / 2 - y0 * s

    return _draw(run, font, (s, 0, 0, -s, tx, ty))[0]


# The phrase, stacked. Set on one line it is a 6:1 run, and inside a roughly
# square field that means a scale where the strokes are a hairline -- which is
# the opposite of the reference, where the calligraphy is a solid block of
# comparable weight to the arch around it.
#
# Two lines is not a compromise to make it fit: `في سبيل` over `الله` is an
# ordinary way to set this phrase compactly, it keeps every letter in its correct
# contextual form, and it reads in the right order.
CALLIGRAPHY_LINES = ("في سبيل", "الله")


def calligraphy_path(box, font=ARABIC, lines=CALLIGRAPHY_LINES, gap=0.14, split=0.62):
    """
    The calligraphy block: `lines` stacked and centred inside `box`.

    `split` is the share of the block's height the first line gets. It is not
    0.5 because `في سبيل` is four letters wide against `الله`'s one ligature, so
    equal heights would leave the second line looking swollen.
    """
    bx0, by0, bx1, by1 = box
    h = by1 - by0
    gap_h = h * gap
    h1 = (h - gap_h) * split
    h2 = (h - gap_h) - h1
    return " ".join([
        arabic_line(lines[0], (bx0, by0, bx1, by0 + h1), font),
        arabic_line(lines[1], (bx0, by1 - h2, bx1, by1), font),
    ])
