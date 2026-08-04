"""
Fi Sabilillah — the wordmark.

The reference sets its wordmark in Playfair Display: a high-contrast transitional
serif, letterspaced, in ivory over deep green. That is the right character, and
this reproduces it -- with one change that matters technically.

Rather than referencing a font at render time, the glyph outlines are extracted
once and written into the artwork as paths. The reference's approach means every
SVG, every app-store listing and every partner's presentation deck depends on a
Google font being installed; where it is not, the wordmark silently falls back to
Times New Roman. Outlines cannot fall back.

THE FACE

Libre Baskerville (SIL Open Font License 1.1), the closest available relative of
Playfair Display: same transitional skeleton, same high stroke contrast, slightly
sturdier serifs -- which is an improvement at wordmark sizes, where Playfair's
hairlines start disappearing below about 18px.

LICENSING

The OFL permits embedding and permits deriving outlines. What it forbids is
selling the font software itself and reusing the reserved name. Neither applies
to a logotype. The licence file travels with the repository at
`brand/licences/LibreBaskerville-OFL.txt`.
"""
import os
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONT_PATH = "/mnt/skills/examples/canvas-design/canvas-fonts/LibreBaskerville-Regular.ttf"
LICENCE_PATH = "/mnt/skills/examples/canvas-design/canvas-fonts/LibreBaskerville-OFL.txt"

CAP = 100.0          # the grid every lockup is laid out on
TRACKING = 0.16      # em. Wide, institutional, and matching the reference's spacing.

_font = None
_upem = None
_cap_scale = None


def _load():
    global _font, _upem, _cap_scale
    if _font is None:
        _font = TTFont(FONT_PATH)
        _upem = _font["head"].unitsPerEm
        cap = _font["OS/2"].sCapHeight if hasattr(_font["OS/2"], "sCapHeight") else 1400
        _cap_scale = CAP / cap
    return _font, _upem, _cap_scale


def word_paths(text, x=0.0, y=0.0, scale=1.0):
    """
    (d, fill-rule, is_stroke) for each glyph, positioned along the baseline.

    `y` is the top of the cap box, matching the geometric letterforms this
    replaced, so every lockup's alignment maths is unchanged.
    """
    font, upem, cap_scale = _load()
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    s = cap_scale * scale
    baseline = y + CAP * scale
    out, cursor = [], x

    for ch in text:
        name = cmap.get(ord(ch))
        if name is None:
            cursor += 0.34 * CAP * scale
            continue
        advance = hmtx[name][0]
        if ch != " ":
            pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}")
            glyphs[name].draw(pen)
            d = pen.getCommands()
            if d:
                # The font's y axis runs up from the baseline; SVG's runs down.
                out.append((
                    f'<g transform="translate({cursor:.2f},{baseline:.2f}) '
                    f'scale({s:.5f},{-s:.5f})"><path d="{d}"/></g>',
                    "nonzero", False))
        cursor += advance * s + TRACKING * CAP * scale

    return out, cursor - TRACKING * CAP * scale - x


def word_path_data(text, x=0.0, y=0.0, scale=1.0):
    """
    The whole wordmark as one already-positioned path, and its width.

    Same outlines as [word_paths], but with the placement baked into the
    coordinates instead of expressed as a wrapping transform. That is what a
    VectorDrawable needs: it has no SVG `transform` attribute, and while a
    `<group>` can carry a negative `android:scaleY` to undo the font's upward y
    axis, a mirrored group is the kind of thing that renders correctly on the
    machine it was written on and surprises somebody two Android versions later.
    Baking it in leaves nothing to interpret.

    Glyph outlines wind consistently, so one path holding every letter fills
    identically to twelve separate ones under the nonzero rule.
    """
    font, upem, cap_scale = _load()
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    s = cap_scale * scale
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
        cursor += hmtx[name][0] * s + TRACKING * CAP * scale

    return " ".join(parts), cursor - TRACKING * CAP * scale - x


def wordmark_group(text, fill, x, y, scale):
    """The whole wordmark as one fill-coloured group, and its width."""
    parts, width = word_paths(text, x=x, y=y, scale=scale)
    body = "".join(p for p, _, _ in parts)
    return f'<g fill="{fill}">{body}</g>', width


def copy_licence(dest_dir):
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, "LibreBaskerville-OFL.txt")
    with open(LICENCE_PATH) as src, open(dest, "w") as out:
        out.write(src.read())
    return dest
