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

print(f"{checks} checks, {len(fails)} failed")
for f in fails:
    print("  FAIL:", f)
sys.exit(1 if fails else 0)
