#!/usr/bin/env python3
"""Checks every generated brand asset. Run after tools/brand/build.py."""
import glob, json, os, re, sys, xml.etree.ElementTree as ET
import cairosvg
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")
fails, checks = [], 0


def check(ok, label, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(f"{label} — {detail}")


svgs = sorted(glob.glob(os.path.join(B, "**", "*.svg"), recursive=True))
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
    check("font-family" not in text and "@font-face" not in text, rel, "depends on a font")

# Android drawables parse, and the adaptive foreground has no baked corner
for p in sorted(glob.glob(os.path.join(B, "app-icons", "**", "*.xml"), recursive=True)):
    rel = os.path.relpath(p, ROOT)
    try:
        ET.parse(p)
        check(True, rel)
    except Exception as exc:                                  # noqa: BLE001
        check(False, rel, f"malformed XML: {exc}")
fg = open(os.path.join(B, "app-icons/android/ic_launcher_foreground.xml")).read()
check("clip" not in fg.lower() and "rx" not in fg,
      "adaptive foreground", "has a baked corner or clip")

# iOS store icon: square, exact, and no alpha
ios = Image.open(os.path.join(B, "app-icons/ios/AppIcon-1024.png"))
check(ios.size == (1024, 1024), "AppIcon-1024", f"is {ios.size}")
check(ios.mode == "RGB", "AppIcon-1024", f"has an alpha channel ({ios.mode})")

for name, want in (("pwa/icon-192.png", 192), ("pwa/icon-512.png", 512),
                   ("pwa/icon-maskable-512.png", 512), ("pwa/favicon-32.png", 32),
                   ("pwa/favicon-16.png", 16), ("android/play-store-512.png", 512)):
    im = Image.open(os.path.join(B, "app-icons", name))
    check(im.size == (want, want), name, f"is {im.size}, expected {want}")

og = Image.open(os.path.join(B, "fi-sabilillah-og-image.png"))
check(og.size == (1200, 630), "og-image", f"is {og.size}")
for s in ("splash/splash-dark.png", "splash/splash-light.png"):
    im = Image.open(os.path.join(B, s))
    check(im.size == (1080, 1920), s, f"is {im.size}")

# The manifest may only name files that exist
man = json.load(open(os.path.join(B, "app-icons/pwa/manifest.webmanifest")))
for icon in man["icons"]:
    target = os.path.join(ROOT, icon["src"].lstrip("/"))
    check(os.path.exists(target), "manifest", f"references missing {icon['src']}")

# The emblem must still be distinguishable at 16px: more than one ink colour present
tiny = Image.open(os.path.join(B, "app-icons/pwa/favicon-16.png")).convert("RGB")
check(len(set(tiny.convert("RGB").getcolors(maxcolors=4096) or [])) > 3,
      "favicon-16", "collapsed to a flat block")

# The Android app's copies must be the generated ones.
#
# These are the only assets in the repository that exist twice: `build.py` writes
# them under `brand/`, and they are copied into the app's resources because that
# is where aapt looks. A copy is a thing that goes stale, so this asserts byte
# equality rather than trusting whoever last ran the build to have remembered.
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

# The launcher background colour is a resource, not a generated file, so it can
# drift from the plate every other asset is drawn on.
colours = open(os.path.join(ROOT, "androidApp/app/src/main/res/values/colors.xml")).read()
check("#0F3D34" in colours, "launcher_background", "is not the brand's deep green")

# No mirrored group in a VectorDrawable. The wordmark's letterforms come out of a
# font whose y axis runs the other way, and the tempting fix is a negative
# android:scaleY; the flip is baked into the path data instead, and this is what
# stops it quietly coming back.
for p in sorted(glob.glob(os.path.join(B, "app-icons", "**", "*.xml"), recursive=True)):
    body = open(p).read()
    check('android:scaleY="-' not in body and 'android:scaleX="-' not in body,
          os.path.relpath(p, ROOT), "contains a mirrored group")

# The React components are generated. Hand-editing them is how the previous pair
# ended up drawing an emblem the rest of the system had stopped using.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geometry as G                                             # noqa: E402
for name in ("FiSabilillahLogo.tsx", "FiSabilillahSplash.tsx"):
    body = open(os.path.join(B, "react", name)).read()
    check("GENERATED by tools/brand/build.py" in body, name, "lost its generated header")
tsx = open(os.path.join(B, "react/FiSabilillahLogo.tsx")).read()
for label, d in (("ARCH", G.ARCH), ("CRESCENT", G.CRESCENT), ("ARCADE", G.ARCADE),
                 ("STAR", G.STAR), ("LEAF_L", G.LEAF_L)):
    check(d in tsx, "FiSabilillahLogo.tsx", f"{label} does not match geometry.py")

# Contrast floors. Every pairing the guidelines permit has to clear 4.5:1 for
# body-sized text; the point of writing them down is that somebody checks.
tokens = json.load(open(os.path.join(B, "tokens/brand-tokens.json")))
for pair, floor in (("ivory-on-deep-green", 4.5), ("gold-on-deep-green", 4.5),
                    ("gold-deep-on-ivory", 4.5), ("deep-green-on-ivory", 4.5),
                    ("ivory-on-charcoal", 4.5)):
    got = tokens["contrast"][pair]
    check(got >= floor, f"contrast {pair}", f"{got}:1, below {floor}:1")
# And the one pairing that must NOT be permitted, asserted so the guidelines
# cannot quietly start claiming it is fine.
check(tokens["contrast"]["gold-on-ivory"] < 4.5, "contrast gold-on-ivory",
      "now passes, so the rule sending light grounds to gold-deep is stale")

print(f"{checks} checks, {len(fails)} failed")
for f in fails:
    print("  FAIL:", f)

# On success, write the count into the inventory. build.py leaves the word
# PENDING there, so a page that claims a number of checks is claiming a number
# that actually ran — a documented figure nobody re-derives is a figure that goes
# quietly wrong.
if not fails:
    inv = os.path.join(ROOT, "docs/brand/ASSET-INVENTORY.md")
    if os.path.exists(inv):
        body = open(inv).read()
        patched = re.sub(r"— \S+ checks,", f"— {checks} checks,", body, count=1)
        if patched != body:
            open(inv, "w").write(patched)

sys.exit(1 if fails else 0)
