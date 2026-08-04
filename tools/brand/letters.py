"""
Fi Sabilillah — the wordmark, drawn rather than typeset.

"FI SABILILLAH" needs seven unique capitals: F I S A B L H. Drawing them is
cheaper than it sounds and buys three things a licensed font cannot:

  * No font dependency in any SVG. The brief requires it, and it is also the
    difference between a logo that renders on a stranger's machine and one that
    falls back to Times New Roman on an app-store listing.
  * No licensing question about embedding a commercial face in a trademark.
  * Terminals, weight and counters tuned to sit beside the emblem specifically.

The reference image set its wordmark in Playfair Display. A serif at wordmark
size is a defensible choice, but it made the identity depend on a Google font
being present, and Playfair's hairlines disappear entirely below about 18px --
which is most of the places a wordmark actually appears on a phone.

These are geometric capitals on a 100-unit cap height with a 14-unit stem, wide
letterspacing, and flat terminals. Institutional rather than fashionable.
"""

CAP = 100.0
WEIGHT = 14.0
TRACKING = 22.0   # generous: letterspaced capitals read as an institution

# Each glyph: (advance width, [(path, fillrule)])
GLYPHS = {
    "F": (60.0, [("M0 0 L60 0 L60 14 L14 14 L14 43 L50 43 L50 57 L14 57 L14 100 L0 100 Z", "nonzero")]),
    "I": (14.0, [("M0 0 L14 0 L14 100 L0 100 Z", "nonzero")]),
    "L": (58.0, [("M0 0 L14 0 L14 86 L58 86 L58 100 L0 100 Z", "nonzero")]),
    "H": (68.0, [("M0 0 L14 0 L14 43 L54 43 L54 0 L68 0 L68 100 L54 100 L54 57 L14 57 L14 100 L0 100 Z", "nonzero")]),
    # Flat-topped A. The counter is a separate subpath lifted out with evenodd
    # rather than drawn as a reversed contour, because a reversed contour is the
    # commonest way a hand-built glyph renders solid in one browser and correct
    # in another.
    "A": (72.0, [("M0 100 L26 0 L46 0 L72 100 L57.5 100 L53.2 82 L18.8 82 L14.5 100 Z"
                  " M36 22 L22.2 68 L49.8 68 Z", "evenodd")]),
    "B": (62.0, [("M0 0 L38 0 C50 0 57 7 57 22 C57 33 52.5 41 45 44.5"
                  " C54 47.5 62 55.5 62 71 C62 88 54 100 39 100 L0 100 Z"
                  " M14 13 L38 13 C44 13 47.5 18.5 47.5 28 C47.5 37.5 44 43 38 43 L14 43 Z"
                  " M14 57 L39 57 C46 57 51 63 51 72 C51 81 46 87 39 87 L14 87 Z", "evenodd")]),
    # S is the one letter with no flat sides to build from, so it is a stroked
    # centreline at the same 14 units with butt caps. The terminals then match
    # the flat terminals of every other glyph exactly.
    "S": (62.0, [("STROKE:M52 22 C52 12 43 7 31 7 C19 7 11 15 11 25"
                  " C11 35 19 41 31 46 C43 51 52 58 52 70"
                  " C52 82 43 93 31 93 C19 93 11 86 11 77", "nonzero")]),
}

SPACE = 44.0


def word_paths(text, x=0.0, y=0.0, scale=1.0):
    """Emit (d, fillrule, is_stroke) for each glyph, positioned along the baseline."""
    out, cursor = [], x
    for ch in text:
        if ch == " ":
            cursor += SPACE * scale
            continue
        adv, paths = GLYPHS[ch]
        for d, rule in paths:
            stroke = d.startswith("STROKE:")
            if stroke:
                d = d[len("STROKE:"):]
            out.append((_translate(d, cursor, y, scale), rule, stroke))
        cursor += (adv + TRACKING) * scale
    return out, cursor - TRACKING * scale - x


def _translate(d, dx, dy, s):
    """Scale then translate every coordinate pair in an absolute-only path."""
    out, i, n = [], 0, len(d)
    while i < n:
        c = d[i]
        if c.isalpha():
            out.append(c); i += 1; continue
        if c in " ,":
            out.append(c); i += 1; continue
        j = i
        while j < n and (d[j].isdigit() or d[j] in ".-"):
            j += 1
        out.append(d[i:j]); i = j
    # rebuild, transforming coordinate pairs in order
    nums, rebuilt, pending = [], [], []
    for tok in out:
        if tok and (tok[0].isdigit() or tok[0] in ".-"):
            nums.append(float(tok))
    it = iter(nums)
    vals = []
    try:
        while True:
            px = next(it); py = next(it)
            vals.append(px * s + dx); vals.append(py * s + dy)
    except StopIteration:
        pass
    k = 0
    for tok in out:
        if tok and (tok[0].isdigit() or tok[0] in ".-"):
            rebuilt.append(f"{vals[k]:.2f}"); k += 1
        else:
            rebuilt.append(tok)
    return "".join(rebuilt)
