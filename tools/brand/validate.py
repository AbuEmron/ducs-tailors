#!/usr/bin/env python3
"""
Checks every generated brand asset. Run after tools/brand/build.py.

The point of this file is that the claims made in `docs/brand/` are checkable.
Anything asserted there — that no SVG depends on a font, that the icon clears
Android's safe zone, that the Arabic is correctly shaped, that gold never lands
on a light ground — is asserted here against the bytes that were actually
written, rather than against what the build was supposed to write.
"""
import glob, json, math, os, re, sys, xml.etree.ElementTree as ET

import cairosvg
from PIL import Image
from svgpathtools import parse_path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geometry as G                                             # noqa: E402
import letters as L                                              # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")
fails, checks = [], 0


def check(ok, label, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(f"{label} — {detail}")


# ── Every SVG is a real, self-contained vector ───────────────────────────────

svgs = sorted(glob.glob(os.path.join(B, "**", "*.svg"), recursive=True))
check(len(svgs) > 30, "svg count", f"only {len(svgs)} found")
for p in svgs:
    rel = os.path.relpath(p, ROOT)
    text = open(p).read()
    try:
        cairosvg.svg2png(bytestring=text.encode(), write_to=None, output_width=64)
        check(True, rel)
    except Exception as exc:                                  # noqa: BLE001
        check(False, rel, f"does not render: {exc}")
    check("viewBox" in text, rel, "no viewBox")
    check("data:image" not in text and "<image" not in text, rel, "embeds a raster")
    # Not just a font *reference*: a <text> element at all means a letterform
    # that is not a path, which is the thing the whole outline pipeline exists
    # to prevent.
    check("font-family" not in text and "@font-face" not in text
          and "<text" not in text, rel, "depends on a font rather than outlines")
    check("<path" in text or "<rect" in text, rel, "contains no vector geometry")


# ── Android ──────────────────────────────────────────────────────────────────

for p in sorted(glob.glob(os.path.join(B, "app-icons", "**", "*.xml"), recursive=True)):
    rel = os.path.relpath(p, ROOT)
    body = open(p).read()
    try:
        ET.parse(p)
        check(True, rel)
    except Exception as exc:                                  # noqa: BLE001
        check(False, rel, f"malformed XML: {exc}")
    # No mirrored group. The wordmark's letterforms come out of a font whose y
    # axis runs the other way, and the tempting fix is a negative
    # android:scaleY; the flip is baked into the path data instead, and this is
    # what stops it quietly coming back.
    check('android:scaleY="-' not in body and 'android:scaleX="-' not in body,
          rel, "contains a mirrored group")

fg_path = os.path.join(B, "app-icons/android/ic_launcher_foreground.xml")
fg = open(fg_path).read()
check("clip" not in fg.lower() and "rx" not in fg,
      "adaptive foreground", "has a baked corner or clip")


def _safe_zone(paths, stroke_pad, box=108, margin=G.ADAPTIVE_MARGIN, small=False):
    """
    Furthest any point of the mark sits from the centre of the 108dp viewport,
    once the build's own fit() has placed it.

    Sampled off the real curves rather than off a bounding box: the mark is a
    tall narrow arch with leaves at the foot, so its bbox corners are empty and
    a corner check would reject a mark that is comfortably inside the circle.
    """
    x0, y0, x1, y1 = G.SMALL_INK if small else G.INK
    s = box * margin / max(x1 - x0, y1 - y0)
    tx = box / 2 - (x0 + x1) / 2 * s
    ty = box / 2 - (y0 + y1) / 2 * s
    worst = 0.0
    for d, pad in paths:
        path = parse_path(d)
        for i in range(801):
            pt = path.point(i / 800)
            px, py = pt.real * s + tx, pt.imag * s + ty
            worst = max(worst, math.hypot(px - box / 2, py - box / 2) + pad * s / 2)
    return worst


FULL = [(G.ARCH, G.ARCH_STROKE), (G.LEAF_L, 0), (G.LEAF_R, 0),
        (G.CRESCENT, 0), (G.STAR, 0)]
SMALL = [(G.ARCH, G.SMALL_ARCH_STROKE), (G.SMALL_LEAF_L, 0), (G.SMALL_LEAF_R, 0),
         (G.SMALL_CRESCENT, 0), (G.SMALL_STAR, 0)]

# Android's adaptive icon: a 108dp viewport of which only the central 66dp
# circle is guaranteed visible. Everything outside it is the launcher's to crop,
# and every OEM crops it differently.
R = G.SAFE_RADIUS_DP
worst = _safe_zone(FULL, 0)
check(worst <= R, "adaptive safe zone",
      f"the mark reaches {worst:.1f}dp from centre; the 66dp circle allows {R}")
worst_mono = _safe_zone(SMALL, 0, small=True)
check(worst_mono <= R, "adaptive safe zone (monochrome)",
      f"the mark reaches {worst_mono:.1f}dp from centre; allows {R}")


# ── Raster icons ─────────────────────────────────────────────────────────────

ios = sorted(glob.glob(os.path.join(B, "app-icons/ios/AppIcon-*.png")))
check(len(ios) >= 12, "ios icon set", f"only {len(ios)} sizes")
for p in ios:
    want = int(re.search(r"AppIcon-(\d+)", p).group(1))
    im = Image.open(p)
    name = os.path.basename(p)
    check(im.size == (want, want), name, f"is {im.size}, expected {want}")
    # The App Store rejects an icon with an alpha channel, and it is the last
    # thing anybody checks before a submission.
    check(im.mode == "RGB", name, f"has an alpha channel ({im.mode})")

for name, want in (("pwa/icon-192.png", 192), ("pwa/icon-512.png", 512),
                   ("pwa/icon-maskable-512.png", 512), ("pwa/favicon-48.png", 48),
                   ("pwa/favicon-32.png", 32), ("pwa/favicon-16.png", 16),
                   ("android/play-store-512.png", 512)):
    im = Image.open(os.path.join(B, "app-icons", name))
    check(im.size == (want, want), name, f"is {im.size}, expected {want}")

BANNERS = {"social-card": (1200, 630), "twitter-header": (1500, 500),
           "website-header": (1600, 320), "banner-wide": (2400, 800),
           "banner-square": (1080, 1080)}
for name, size in BANNERS.items():
    im = Image.open(os.path.join(B, f"banners/fi-sabilillah-{name}.png"))
    check(im.size == size, name, f"is {im.size}, expected {size}")

og = Image.open(os.path.join(B, "fi-sabilillah-og-image.png"))
check(og.size == (1200, 630), "og-image", f"is {og.size}")
for s in ("splash/splash-dark.png", "splash/splash-light.png"):
    im = Image.open(os.path.join(B, s))
    check(im.size == (1080, 1920), s, f"is {im.size}")

# The emblem must still be distinguishable at 16px: more than one ink colour.
tiny = Image.open(os.path.join(B, "app-icons/pwa/favicon-16.png")).convert("RGB")
check(len(set(tiny.getcolors(maxcolors=4096) or [])) > 3,
      "favicon-16", "collapsed to a flat block")

man = json.load(open(os.path.join(B, "app-icons/pwa/manifest.webmanifest")))
for icon in man["icons"]:
    target = os.path.join(ROOT, icon["src"].lstrip("/"))
    check(os.path.exists(target), "manifest", f"references missing {icon['src']}")


# ── The Arabic ───────────────────────────────────────────────────────────────
#
# The brief rules out malformed Arabic, and the only honest way to hold that
# line is to check it rather than to promise it. This re-runs the shaping and
# asserts three things: that HarfBuzz produced the contextual forms rather than
# isolated letters, that the ﷲ ligature was substituted, and that what the build
# drew is byte-identical to what re-shaping produces now.

run = L._shape(L.CALLIGRAPHY_LINES[0], L.ARABIC)
names = [n for n, _x, _y in run]
check(any(n.startswith("uniFE") or n.startswith("uniFB") for n in names),
      "arabic shaping", f"no contextual forms in {names} — the text is unshaped")
allah = L._shape(L.CALLIGRAPHY_LINES[1], L.ARABIC)
check(any(n == "uniFDF2" for n, _x, _y in allah),
      "arabic ligature", f"الله did not resolve to the ﷲ ligature: {[n for n,_,_ in allah]}")
drawn = L.calligraphy_path(G.CALLIGRAPHY_BOX)
primary = open(os.path.join(B, "logo/fi-sabilillah-icon-primary.svg")).read()
check(drawn in primary, "arabic in the emblem",
      "the calligraphy in the exported icon is not what re-shaping produces")
check(len(drawn) > 400, "arabic path", f"suspiciously short ({len(drawn)} chars)")


# ── The app's copies ─────────────────────────────────────────────────────────
#
# These are the only assets in the repository that exist twice: `build.py`
# writes them under `brand/`, and they are copied into the app's resources
# because that is where aapt looks. A copy is a thing that goes stale, so this
# asserts byte equality rather than trusting whoever last ran the build.

APP_RES = os.path.join(ROOT, "androidApp/app/src/main/res/drawable")
for src, name in (("app-icons/android/brand_emblem.xml", "brand_emblem.xml"),
                  ("app-icons/android/brand_emblem_small.xml", "brand_emblem_small.xml"),
                  ("app-icons/android/brand_wordmark.xml", "brand_wordmark.xml"),
                  ("app-icons/android/ic_launcher_foreground.xml",
                   "ic_launcher_foreground.xml"),
                  ("app-icons/android/ic_launcher_monochrome.xml",
                   "ic_launcher_monochrome.xml"),
                  ("app-icons/notifications/ic_notification.xml", "ic_notification.xml")):
    dest = os.path.join(APP_RES, name)
    check(os.path.exists(dest), name, "missing from the app's res/drawable")
    if os.path.exists(dest):
        check(open(os.path.join(B, src)).read() == open(dest).read(), name,
              "the app's copy differs from the generated one — re-copy it")

colours = open(os.path.join(ROOT, "androidApp/app/src/main/res/values/colors.xml")).read()
check("#0F3D34" in colours, "launcher_background", "is not the brand's deep green")


# ── The React components ─────────────────────────────────────────────────────
#
# Generated. Hand-editing them is how the previous pair ended up drawing an
# emblem the rest of the system had stopped using.

for name in ("FiSabilillahLogo.tsx", "FiSabilillahSplash.tsx"):
    body = open(os.path.join(B, "react", name)).read()
    check("GENERATED by tools/brand/build.py" in body, name, "lost its generated header")
tsx = open(os.path.join(B, "react/FiSabilillahLogo.tsx")).read()
for label, d in (("ARCH", G.ARCH), ("FIELD", G.FIELD), ("ARCH_KEYLINE", G.ARCH_KEYLINE),
                 ("CRESCENT", G.CRESCENT), ("STAR", G.STAR), ("LEAF_L", G.LEAF_L),
                 ("LEAF_R", G.LEAF_R), ("SMALL_CRESCENT", G.SMALL_CRESCENT)):
    check(d in tsx, "FiSabilillahLogo.tsx", f"{label} does not match geometry.py")
check(drawn in tsx, "FiSabilillahLogo.tsx", "the calligraphy does not match")
check("useId().replace" in tsx, "FiSabilillahLogo.tsx",
      "the gradient id is not colon-stripped")


# ── Geometry ─────────────────────────────────────────────────────────────────
#
# The arch is authored as one half and mirrored. If that ever stops holding, the
# mark is lopsided in a way nobody can see and everybody can feel.

for label, d in (("ARCH", G.ARCH), ("FIELD", G.FIELD), ("ARCH_KEYLINE", G.ARCH_KEYLINE)):
    x0, x1, _y0, _y1 = parse_path(d).bbox()
    check(abs((x0 + x1) / 2 - 32.0) < 1e-6, f"{label} symmetry",
          f"centred on {(x0 + x1) / 2:.4f}, not 32")

# The calligraphy has to stay inside the field, or it prints over the band.
cx0, cx1, cy0, cy1 = parse_path(drawn).bbox()
fx0, fx1, fy0, fy1 = parse_path(G.FIELD).bbox()
check(cx0 > fx0 and cx1 < fx1 and cy0 > fy0 and cy1 < fy1, "calligraphy fit",
      f"({cx0:.1f},{cy0:.1f})-({cx1:.1f},{cy1:.1f}) is not inside the field")


# ── Contrast ─────────────────────────────────────────────────────────────────

tokens = json.load(open(os.path.join(B, "tokens/brand-tokens.json")))
for pair, floor in (("ivory-on-deep-green", 4.5), ("gold-on-deep-green", 4.5),
                    ("gold-deep-on-ivory", 4.5), ("deep-green-on-ivory", 4.5),
                    ("ivory-on-charcoal", 4.5)):
    got = tokens["contrast"][pair]
    check(got >= floor, f"contrast {pair}", f"{got}:1, below {floor}:1")
# Sage carries the tagline, which is display-sized. AA large is 3:1.
check(tokens["contrast"]["sage-on-deep-green"] >= 3.0, "contrast sage-on-deep-green",
      f'{tokens["contrast"]["sage-on-deep-green"]}:1, below the 3:1 large-text floor')
# And the one pairing that must NOT be permitted, asserted so the guidelines
# cannot quietly start claiming it is fine.
check(tokens["contrast"]["gold-on-ivory"] < 4.5, "contrast gold-on-ivory",
      "now passes, so the rule sending light grounds to gold-deep is stale")

check(tokens["typography"]["display"]["family"] == "Playfair Display",
      "typography", "the display face is not the reference's")
check(tokens["typography"]["text"]["family"] == "Inter",
      "typography", "the text face is not the reference's")


print(f"{checks} checks, {len(fails)} failed")
for f in fails:
    print("  FAIL:", f)

# On success, write the count into the inventory. build.py leaves the word
# PENDING there, so a page that claims a number of checks is claiming a number
# that actually ran — a documented figure nobody re-derives is a figure that
# goes quietly wrong.
if not fails:
    inv = os.path.join(ROOT, "docs/brand/ASSET-INVENTORY.md")
    if os.path.exists(inv):
        body = open(inv).read()
        patched = re.sub(r"— \S+ checks", f"— {checks} checks", body, count=1)
        if patched != body:
            open(inv, "w").write(patched)

sys.exit(1 if fails else 0)
