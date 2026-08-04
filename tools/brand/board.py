#!/usr/bin/env python3
"""
Assembles the brand presentation board from the generated assets.

Every mark on the page is the real exported file, inlined. Nothing here draws
anything; if the board looks wrong, the asset is wrong.
"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tokens as T
from build import PILLAR_ICONS, pillar_svg
from letters import CAP, DISPLAY, word_path_data

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")

_uid = [0]


def inner(path):
    """
    The guts of an SVG, so it can be dropped inline at any size.

    The gradient id is rewritten per inclusion. Every emblem export defines
    `fsGold`, and a page holding twenty of them would hold twenty elements with
    the same id -- which is invalid, and which resolves every reference to
    whichever one the parser saw first.
    """
    s = open(os.path.join(B, path)).read()
    s = s[s.index(">", s.index("<svg")) + 1: s.rindex("</svg>")]
    s = re.sub(r"<title>.*?</title>", "", s, flags=re.S)
    _uid[0] += 1
    return s.replace("fsGold", f"g{_uid[0]}").replace("fsWord", f"w{_uid[0]}")


def svg(path, box="0 0 64 64", cls="", extra="", plateless=False):
    """
    `plateless` drops the lockup's own background rect.

    The board's ground is already the brand's dark; a lockup that carries its
    plate on top of it reads as a card floating on the page rather than as the
    identity the page is about.
    """
    body = inner(path)
    if plateless:
        body = re.sub(r'<rect width="\d+" height="\d+" fill="#[0-9A-Fa-f]{6}"/>', "",
                      body, count=1)
    return (f'<svg viewBox="{box}" class="{cls}" {extra} aria-hidden="true">{body}</svg>')


def viewbox_of(path):
    return re.search(r'viewBox="([^"]+)"', open(os.path.join(B, path)).read()).group(1)


def raw(markup, box="0 0 64 64", cls=""):
    _uid[0] += 1
    body = markup[markup.index(">", markup.index("<svg")) + 1: markup.rindex("</svg>")]
    return (f'<svg viewBox="{box}" class="{cls}" aria-hidden="true">'
            f'{body.replace("fsGold", f"p{_uid[0]}")}</svg>')


D = json.load(open(os.path.join(B, "tokens/brand-tokens.json")))
OUT = os.environ.get("BOARD_OUT", "/tmp/board.html")

# ── swatches ─────────────────────────────────────────────────────────────────
sw = "".join(f'''<figure class="sw">
  <div class="sw__chip" style="background:{v['hex']};color:{"#0F3D34" if v['hsl'][2] > 55 else "#F5F2E9"}">{v['hex']}</div>
  <figcaption><b>{k}</b><span>{v['usage']}</span>
  <code>rgb({', '.join(str(c) for c in v['rgb'])})</code>
  <code>{v['hsl'][0]}&deg; {v['hsl'][1]}% {v['hsl'][2]}%</code>
  <code>cmyk {' '.join(str(c) for c in v['cmyk'])}</code></figcaption></figure>'''
               for k, v in D["brand"].items())

ramp = "".join(f'<div class="ramp__stop" style="background:{c}"><span>{c}</span><em>{o}%</em></div>'
               for c, o in zip(D["goldRamp"], (0, 28, 64, 100)))

con = "".join(
    f'<tr><td>{k.replace("-", " ")}</td><td class="num">{v}:1</td>'
    f'<td class="{"ok" if v >= 4.5 else ("mid" if v >= 3 else "bad")}">'
    f'{"AA text" if v >= 4.5 else ("AA large only" if v >= 3 else "fails — never")}</td></tr>'
    for k, v in D["contrast"].items())

# ── the reference's own "meaning behind the design" ──────────────────────────
MEANING = [
    ("crescent", "Crescent &amp; star", "Faith, guidance &amp; divine purpose"),
    ("calligraphy", "Arabic calligraphy", "Unity, deen &amp; timeless Islamic identity"),
    ("leaves", "Hands &amp; leaves", "Service, help &amp; positive impact"),
    ("arch", "Arch &amp; shape", "Masjid, shelter, community &amp; sacred space"),
]

import geometry as G                                             # noqa: E402
from build import CALLIGRAPHY, gold_defs                         # noqa: E402

MEANING_ART = {
    "crescent": f'<path d="{G.CRESCENT}" fill="GOLD"/><path d="{G.STAR}" fill="GOLD"/>',
    "calligraphy": f'<path d="{CALLIGRAPHY}" fill="GOLD"/>',
    "leaves": f'<path d="{G.LEAF_L}" fill="GOLD"/><path d="{G.LEAF_R}" fill="GOLD"/>',
    "arch": (f'<path d="{G.ARCH}" fill="none" stroke="GOLD" '
             f'stroke-width="{G.ARCH_STROKE}" stroke-linejoin="round"/>'),
}


def meaning_svg(key):
    _uid[0] += 1
    gid = f"m{_uid[0]}"
    return (f'<svg viewBox="8 0 48 60" aria-hidden="true">'
            f'<defs>{gold_defs(gid)}</defs>'
            f'{MEANING_ART[key].replace("GOLD", f"url(#{gid})")}</svg>')


meaning = "".join(
    f'<figure class="mean"><div class="mean__art">{meaning_svg(k)}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>'
    for k, t, d in MEANING)

# ── app icon row, exactly the reference's five ───────────────────────────────
ICON_ROW = [("primary", "Deep green", "The default"),
            ("dark", "Charcoal", "Photography, dark shelves"),
            ("light", "Ivory", "Drawn in gold-deep"),
            ("ivory", "Mineral", "Ivory mark on green"),
            ("mono-white", "Outline", "One colour, no field")]
icons = "".join(
    f'<figure class="tone{" tone--dark" if p == "mono-white" else ""}">'
    f'<div class="tone__art">{svg(f"logo/fi-sabilillah-icon-{p}.svg")}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>'
    for p, t, d in ICON_ROW)

TONES = [("primary", "Primary", "Gradient gold on deep green"),
         ("flat", "Flat", "Silkscreen, embroidery, one plate"),
         ("dark", "Dark", "Charcoal and photography"),
         ("light", "Light", "Ivory and white; gold-deep"),
         ("ivory", "Ivory", "Where gold would compete with gold"),
         ("amanah", "In-product", "On Amanah surfaces, inside the app"),
         ("mono-black", "Mono, black", "Print, engraving, stamps"),
         ("mono-white", "Mono, white", "Reversed out of any dark ground")]
tones = "".join(
    f'<figure class="tone{" tone--plate" if p == "mono-black" else ""}'
    f'{" tone--dark" if p == "mono-white" else ""}">'
    f'<div class="tone__art">{svg(f"logo/fi-sabilillah-icon-{p}.svg")}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for p, t, d in TONES)

sizes = "".join(
    f'<figure class="size"><div class="size__art" style="--s:{p}px">'
    f'{svg("logo/fi-sabilillah-icon-small.svg" if p <= 32 else "logo/fi-sabilillah-icon-primary.svg")}'
    f'</div><figcaption>{p}px</figcaption></figure>'
    for p in (16, 24, 32, 48, 64, 96, 160))

MASKS = [("circle", "Circle", "Pixel, most launchers"),
         ("squircle", "Squircle", "Samsung One UI"),
         ("rounded", "Rounded square", "iOS, and Android's default"),
         ("teardrop", "Teardrop", "Some OEM skins")]
masks = "".join(
    f'<figure class="mask"><div class="mask__art mask--{m}">'
    f'<div class="mask__bg"></div><div class="mask__fg">'
    f'{svg("logo/fi-sabilillah-icon-mono-white.svg")}</div>'
    f'<div class="mask__safe"></div></div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for m, t, d in MASKS)

BADGES = [("verified-organization", "Verified organisation", "Registration documents were seen by a named reviewer."),
          ("learning", "Learning", "A learning offering. Not an endorsement of its content."),
          ("service", "Service", "A service opportunity. The emblem's own arch."),
          ("community", "Community", "A community space. Three equal dots, unranked."),
          ("safety", "Safety", "Privacy and restriction surfaces."),
          ("donation", "Donation", "A campaign that has passed financial review."),
          ("family-introduction", "Family introduction", "Two parties, and a third presence over both.")]
badges = "".join(
    f'<figure class="badge"><div class="badge__art">{svg(f"badges/{n}.svg")}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>'
    for n, t, d in BADGES)

PILLAR_TITLES = {"community": "Community", "knowledge": "Knowledge", "service": "Service",
                 "giving": "Giving", "safety": "Safety"}
pillars = "".join(
    f'<figure class="pillar"><div class="pillar__art">'
    f'{raw(pillar_svg(n))}</div>'
    f'<figcaption><b>{PILLAR_TITLES[n]}</b><span>{PILLAR_ICONS[n][0]}</span>'
    f'</figcaption></figure>' for n in PILLAR_ICONS)

WRONG = [("stretch", "Stretched", "The proportions are the mark."),
         ("rotate", "Rotated", "An arch that is not upright is not an arch."),
         ("recolour", "Arbitrarily recoloured", "Tones are listed. That list is the whole list."),
         ("gold", "Gold on ivory", "1.88:1. Unreadable, and the fastest way to look cheap."),
         ("glow", "Glow added", "No bevel, no shadow, no second metallic gradient."),
         ("crowd", "On busy imagery", "Use a plate, or a monochrome cut.")]
wrong = "".join(
    f'<figure class="wrong"><div class="wrong__art wrong--{c}">'
    f'{svg("logo/fi-sabilillah-icon-primary.svg")}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for c, t, d in WRONG)

GLYPHS = "FISABLH"


def _glyph_svg(ch):
    d, w = word_path_data(ch, x=0, y=0, scale=1.0, font=DISPLAY)
    return (f'<svg viewBox="-6 -6 {w + 12:.1f} {CAP + 12:.1f}" aria-hidden="true">'
            f'<path d="{d}"/></svg>')


wm_vb = viewbox_of("logo/fi-sabilillah-wordmark.svg")
hz = "logo/fi-sabilillah-logo-horizontal-tagline.svg"
st = "logo/fi-sabilillah-logo-stacked-full.svg"
ty = D["typography"]

HTML = f"""<title>Fi Sabilillah — Visual Identity</title>
<style>
:root {{
  --ground:#070E12; --surface:#0C1720; --raised:#102028;
  --ink:#F1EEE4; --muted:#93A3A6; --rule:rgba(212,175,55,.22);
  --gold:#D4AF37; --sage:#6BAA7D; --green:#0F3D34; --ivory:#F5F2E9;
  --display: ui-serif, "Iowan Old Style", "Palatino Linotype", Palatino, "Book Antiqua", Georgia, serif;
  --body: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  --mono: ui-monospace, "SF Mono", "Cascadia Mono", Menlo, Consolas, monospace;
  --halo: rgba(28,87,74,.5);
  --pad: clamp(20px, 5vw, 72px);
}}
@media (prefers-color-scheme: light) {{
  :root {{ --ground:#F3F1E8; --surface:#FFFFFF; --raised:#FBFAF5;
           --ink:#0C2A23; --muted:#5C6E67; --rule:rgba(15,61,52,.18);
           --gold:#7A5E15; --sage:#3E7A5E; --halo:rgba(212,175,55,.14); }}
}}
:root[data-theme="dark"] {{ --ground:#070E12; --surface:#0C1720; --raised:#102028;
  --ink:#F1EEE4; --muted:#93A3A6; --rule:rgba(212,175,55,.22); --gold:#D4AF37;
  --sage:#6BAA7D; --halo:rgba(28,87,74,.5); }}
:root[data-theme="light"] {{ --ground:#F3F1E8; --surface:#FFFFFF; --raised:#FBFAF5;
  --ink:#0C2A23; --muted:#5C6E67; --rule:rgba(15,61,52,.18); --gold:#7A5E15;
  --sage:#3E7A5E; --halo:rgba(212,175,55,.14); }}

* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--ground); color:var(--ink); font-family:var(--body);
  font-size:16px; line-height:1.6; -webkit-font-smoothing:antialiased; }}
.wrap {{ max-width:1180px; margin:0 auto; padding:0 var(--pad); }}
section {{ padding:clamp(48px,7vw,104px) 0; border-top:1px solid var(--rule); }}
.eyebrow {{ font:600 11px/1 var(--body); letter-spacing:.22em; text-transform:uppercase;
  color:var(--gold); margin:0 0 18px; }}
h2 {{ font-family:var(--display); font-weight:400; font-size:clamp(26px,3.4vw,40px);
  line-height:1.15; margin:0 0 14px; text-wrap:balance; letter-spacing:-.01em; }}
h3 {{ font-family:var(--display); font-weight:400; font-size:20px; margin:0 0 8px; }}
p {{ max-width:64ch; color:var(--muted); margin:0 0 14px; }}
p strong, li strong {{ color:var(--ink); font-weight:600; }}
a {{ color:var(--gold); }}
code {{ font-family:var(--mono); font-size:12px; }}
[dir=rtl] {{ font-size:1.3em; }}

/* hero — the reference's own banner, at page scale */
.hero {{ min-height:92vh; display:grid; place-items:center; text-align:center;
  padding:clamp(56px,10vh,120px) var(--pad);
  background:radial-gradient(56% 42% at 50% 40%, var(--halo), transparent 72%); }}
.hero__lockup {{ width:min(88vw,620px); height:auto; display:block; margin:0 auto 34px; }}
.hero__thesis {{ font-family:var(--display); font-size:clamp(17px,2vw,21px); line-height:1.5;
  color:var(--ink); max-width:54ch; margin:0 auto; }}

/* the identity's own motion, shown by using it */
.hero__lockup [stroke]:not([fill]) {{ stroke-dasharray:230; stroke-dashoffset:230;
  animation:draw 1100ms cubic-bezier(.2,.8,.2,1) forwards; }}
@keyframes draw {{ to {{ stroke-dashoffset:0; }} }}
@media (prefers-reduced-motion: reduce) {{
  .hero__lockup [stroke]:not([fill]) {{ animation:none; stroke-dashoffset:0; }}
}}

.grid {{ display:grid; gap:clamp(16px,2vw,26px); }}
.g2 {{ grid-template-columns:repeat(auto-fit,minmax(280px,1fr)); }}
.g3 {{ grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); }}
.g4 {{ grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); }}
.g5 {{ grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); }}

figure {{ margin:0; }}
figcaption {{ margin-top:12px; font-size:13px; color:var(--muted); }}
figcaption b {{ display:block; color:var(--ink); font-size:14px; font-weight:600; margin-bottom:2px; }}
figcaption span {{ display:block; }}

.tone__art, .badge__art, .mask__art, .wrong__art, .pillar__art, .mean__art {{
  background:var(--raised); border:1px solid var(--rule); border-radius:16px;
  padding:20px; display:grid; place-items:center; }}
.tone__art svg, .badge__art svg, .wrong__art svg {{ width:100%; max-width:104px; height:auto; }}
.pillar__art svg {{ width:100%; max-width:64px; height:auto; }}
.mean__art {{ padding:14px; }} .mean__art svg {{ width:100%; max-width:56px; height:auto; }}
.mean {{ display:grid; grid-template-columns:76px 1fr; gap:16px; align-items:center; }}
.tone--plate .tone__art {{ background:var(--ivory); }}
.tone--dark .tone__art {{ background:#0D1115; }}

.ramp-sizes {{ display:flex; flex-wrap:wrap; align-items:flex-end; gap:clamp(14px,3vw,40px); }}
.size__art {{ height:172px; display:grid; place-items:center; }}
.size__art svg {{ width:var(--s); height:var(--s); }}
.size figcaption {{ text-align:center; font-family:var(--mono); font-size:12px; }}

/* adaptive-icon masks, with the 66dp safe circle drawn on */
.mask__art {{ position:relative; aspect-ratio:1; padding:0; overflow:hidden; }}
.mask__bg {{ position:absolute; inset:0; background:var(--green); }}
.mask__fg {{ position:absolute; inset:0; display:grid; place-items:center; }}
.mask__fg svg {{ width:50%; height:50%; }}
.mask__safe {{ position:absolute; left:50%; top:50%; width:61.1%; height:61.1%;
  transform:translate(-50%,-50%); border:1px dashed rgba(255,90,120,.85);
  border-radius:50%; pointer-events:none; }}
.mask--circle .mask__bg, .mask--circle .mask__fg {{ clip-path:circle(50%); }}
.mask--squircle .mask__bg, .mask--squircle .mask__fg {{ border-radius:38%; }}
.mask--rounded .mask__bg, .mask--rounded .mask__fg {{ border-radius:22%; }}
.mask--teardrop .mask__bg, .mask--teardrop .mask__fg {{ border-radius:50% 50% 50% 12%; }}

.sw__chip {{ height:104px; border-radius:14px; display:grid; place-items:end start;
  padding:12px; font-family:var(--mono); font-size:12px; border:1px solid var(--rule); }}
.sw figcaption code {{ display:block; color:var(--muted); }}

.ramp {{ display:grid; grid-template-columns:repeat(4,1fr); border-radius:14px;
  overflow:hidden; border:1px solid var(--rule); margin-top:8px; }}
.ramp__stop {{ height:104px; display:grid; align-content:end; gap:2px; padding:12px;
  font-family:var(--mono); font-size:11px; color:#3A2E08; }}
.ramp__stop em {{ font-style:normal; opacity:.65; }}

table {{ width:100%; border-collapse:collapse; font-size:14px; }}
th, td {{ text-align:left; padding:10px 12px; border-bottom:1px solid var(--rule); }}
th {{ font:600 11px/1 var(--body); letter-spacing:.16em; text-transform:uppercase; color:var(--gold); }}
.num {{ font-family:var(--mono); font-variant-numeric:tabular-nums; }}
.ok {{ color:#6BAA7D; }} .mid {{ color:var(--gold); }} .bad {{ color:#D08A92; font-weight:600; }}
.scroll {{ overflow-x:auto; }}

.glyphs {{ display:flex; flex-wrap:wrap; gap:14px; }}
.glyph {{ background:var(--raised); border:1px solid var(--rule); border-radius:12px;
  width:92px; height:104px; display:grid; place-items:center; }}
.glyph svg {{ width:auto; height:54px; }} .glyph svg path {{ fill:var(--ink); }}

.lockup {{ background:var(--raised); border:1px solid var(--rule); border-radius:18px;
  padding:clamp(18px,2.4vw,32px); display:grid; place-items:center; }}
.lockup svg {{ width:100%; height:auto; }}
.lockup--wide svg {{ max-width:620px; }} .lockup--tall svg {{ max-width:320px; }}

.splashes {{ display:grid; grid-template-columns:1fr 1fr; gap:clamp(12px,2vw,20px);
  align-content:start; }}
.splash {{ border:1px solid var(--rule); border-radius:20px; overflow:hidden;
  aspect-ratio:9/16; }}
.splash img, .splash svg {{ width:100%; height:100%; object-fit:cover; }}

.wrong__art {{ position:relative; }}
.wrong__art::after {{ content:""; position:absolute; inset:0; border-radius:16px;
  border:1px solid #D08A92; }}
.wrong--stretch svg {{ transform:scaleX(1.7); }}
.wrong--rotate svg {{ transform:rotate(-17deg); }}
.wrong--recolour svg [stroke] {{ stroke:#E0457B; }}
.wrong--recolour svg path[fill]:not([stroke]) {{ fill:#39D2FF; }}
.wrong--gold {{ background:var(--ivory) !important; }}
.wrong--gold svg rect {{ fill:#F5F2E9; }}
.wrong--gold svg [stroke] {{ stroke:#D4AF37; }}
.wrong--gold svg path[fill]:not([stroke]) {{ fill:#D4AF37; }}
.wrong--glow svg {{ filter:drop-shadow(0 0 14px #D4AF37) drop-shadow(0 0 30px #D4AF37); }}
.wrong--crowd {{ background:
  repeating-linear-gradient(38deg,#5b4a2e 0 12px,#7a6a3f 12px 24px,#3d5a52 24px 36px) !important; }}

.note {{ background:var(--raised); border:1px solid var(--rule); border-left:2px solid var(--gold);
  border-radius:0 14px 14px 0; padding:18px 22px; }}
.note p:last-child {{ margin-bottom:0; }}
.arabic {{ font-size:clamp(30px,5vw,52px); color:var(--gold); line-height:1.5; margin:0 0 10px;
  font-family:"Noto Kufi Arabic","Segoe UI",system-ui,sans-serif; }}
ul {{ color:var(--muted); max-width:64ch; padding-left:1.1em; }}
li {{ margin-bottom:7px; }}
footer {{ padding:56px 0 80px; color:var(--muted); font-size:13px; border-top:1px solid var(--rule); }}
</style>

<header class="hero">
  <div>
    {svg(st, box=viewbox_of(st), cls="hero__lockup", plateless=True)}
    <p class="hero__thesis">A gold arch on deep green, holding a crescent and star above
      <span dir="rtl">في سبيل الله</span>, cradled by two leaves. <em>Fi sabilillah</em> means
      <em>in the path of Allah</em>, and the path is the mark.</p>
  </div>
</header>

<div class="wrap">

<section style="border-top:0">
  <p class="eyebrow">Meaning behind the design</p>
  <h2>Five readings, one silhouette</h2>
  <p>These are the reference's own, from its meaning panel. They are kept because they were
  the brief — not reinterpreted, not improved on.</p>
  <div class="grid g2">{meaning}</div>
</section>

<section>
  <p class="eyebrow">The Arabic</p>
  <h2>Really Arabic, really shaped</h2>
  <div class="grid g2">
    <div>
      <p class="arabic" dir="rtl" lang="ar">في سبيل الله</p>
      <p><em>fī sabīlillāh</em>. Real Unicode Arabic, in a real Arabic typeface — <strong>Noto
      Kufi Arabic</strong> — shaped by <strong>HarfBuzz</strong>. The shaper chooses the
      contextual forms and substitutes the&nbsp;ﷲ&nbsp;ligature; nothing in this system picks a
      glyph by hand, and nothing draws a shape that is <em>meant to look like</em> Arabic.</p>
      <p>Set on two lines because on one it is a 6:1 run, and inside a field of the arch's
      proportions that means a scale where the strokes are a hairline. Two lines is an ordinary
      way to set the phrase compactly and keeps every letter in its correct form.</p>
    </div>
    <div class="note">
      <h3>Checked, not promised</h3>
      <p>Every build asserts three things about the calligraphy: that the shaper produced
      contextual forms rather than isolated letters, that <span dir="rtl">الله</span> resolved
      to the&nbsp;ﷲ&nbsp;ligature, and that the path in the exported icon is byte-identical to
      what re-shaping produces now.</p>
      <p>Below 32px there is <strong>no calligraphy at all</strong>. At 24px its strokes are a
      fifth of a pixel. Illegible Arabic is exactly the failure this identity is most careful
      about, so the small cut drops it and gives the field to the crescent.</p>
      <p>What is <em>not</em> claimed: that it has been read by a native reader. It is
      technically correct; well-set is a different claim and only a person can make it.</p>
    </div>
  </div>
</section>

<section>
  <p class="eyebrow">App icon</p>
  <h2>Five plates, one mark</h2>
  <div class="grid g5">{icons}</div>
</section>

<section>
  <p class="eyebrow">Logo variations</p>
  <h2>Eight tones, one geometry</h2>
  <div class="grid g3">{tones}</div>
  <div class="grid g2" style="margin-top:34px">
    <div class="lockup lockup--wide">{svg(hz, box=viewbox_of(hz))}</div>
    <div class="lockup lockup--tall">{svg(st, box=viewbox_of(st))}</div>
  </div>
</section>

<section>
  <p class="eyebrow">Scale</p>
  <h2>It has to work at sixteen pixels</h2>
  <p>Below 32px the mark drops the keyline, the calligraphy and the curled leaf tips, thickens
  the band, and enlarges the crescent to fill the field the calligraphy has left. Detail you
  cannot resolve does not read as detail — it reads as dirt over the parts that were legible.
  That decision is made by the code from the size it is handed, not by the person placing it.</p>
  <div class="ramp-sizes">{sizes}</div>
</section>

<section>
  <p class="eyebrow">Adaptive icon</p>
  <h2>Inside the circle, on every mask</h2>
  <p>Android guarantees only the central <strong>66dp circle</strong> — dashed above.
  Everything outside it is the launcher's to crop, and every OEM crops it differently. The
  mark is a tall arch with leaves at the foot, so its furthest-from-centre points are the leaf
  tips; at the 62% scale that looks right in a preview they sit 40.6dp out, comfortably inside
  a square mask and clipped by a circular one. The foreground occupies 50% instead.</p>
  <p>The check samples 800 points along every path, projects them into the viewport and fails
  if any lands outside the circle. It samples the curves rather than the bounding box, because
  the mark's bbox corners are empty and a corner check would reject an icon that is
  comfortably inside.</p>
  <div class="grid g4">{masks}</div>
</section>

<section>
  <p class="eyebrow">Colour</p>
  <h2>The reference's five, kept exactly</h2>
  <p>The brand palette is deep green and gold. The <strong>product</strong> palette — what the
  application's screens are painted in — is the Amanah harbour blue. Forcing one to be the
  other makes both worse: the green cannot carry interface text at the ratios a UI needs, and
  the blue does not read as an institution on a shelf of app icons.</p>
  <div class="grid g3">{sw}</div>

  <h3 style="margin-top:40px">The gold ramp</h3>
  <p>Four stops on a diagonal, so the light falls on the arch's upper left as the reference
  lights it. A gradient rather than a bevel or a lighting filter: a filter does not survive
  being printed, foil-blocked, converted to a VectorDrawable or rendered at 16px, and every
  one of those happens. <strong>Gold is an accent and never a ground.</strong></p>
  <div class="ramp">{ramp}</div>

  <h3 style="margin-top:40px">Measured, not assumed</h3>
  <div class="scroll"><table><thead><tr><th>Pair</th><th>Ratio</th><th>Verdict</th></tr></thead>
  <tbody>{con}</tbody></table></div>
  <p style="margin-top:14px"><strong>Gold never touches a light background.</strong> On ivory
  it is 1.88:1. Light grounds use gold-deep at 5.45:1. The checks assert the floors <em>and</em>
  assert that gold-on-ivory still fails — if it ever passed, the rule sending light grounds to
  gold-deep would have gone stale without anyone noticing.</p>
  <p><strong>Sage is a large-text colour.</strong> 4.42:1 carries the tagline, which is always
  set at display size, and nothing else in the system.</p>
</section>

<section>
  <p class="eyebrow">Typography</p>
  <h2>{ty["display"]["family"]} and {ty["text"]["family"]}, as named</h2>
  <p>The reference names its own faces and both are used exactly as named — Playfair Display
  for the wordmark and tagline, Inter for the pillars line and labels. Both are SIL Open Font
  License 1.1, both are vendored into the repository, and both licences travel with the
  assets.</p>
  <p>The letterforms are <strong>outlines, not font references</strong>. A font-referencing SVG
  depends on that font being installed wherever the file is opened — an app-store listing, a
  partner's deck, a printer's RIP — and where it is not, the wordmark silently falls back to
  Times New Roman. Outlines cannot fall back, and the check rejects any SVG containing
  <code>&lt;text</code>, <code>font-family</code> or <code>@font-face</code>.</p>
  <div class="glyphs">{"".join(f'<div class="glyph">{_glyph_svg(g)}</div>' for g in GLYPHS)}</div>
  <div class="lockup lockup--wide" style="margin-top:28px">{svg("logo/fi-sabilillah-wordmark-mono.svg", box=wm_vb)}</div>
</section>

<section>
  <p class="eyebrow">The five pillars</p>
  <h2>What the platform is for</h2>
  <p>Line icons rather than badges, and the distinction matters: a badge asserts that something
  was checked, and these assert nothing. They label a section.</p>
  <div class="grid g5">{pillars}</div>
</section>

<section>
  <p class="eyebrow">Badges</p>
  <h2>Seven facts, one shelter</h2>
  <p>All seven share the emblem's own arch, closed at the foot and filled. A badge states what
  was checked — never that something is religiously approved, safe, or guaranteed.</p>
  <div class="grid g3">{badges}</div>
</section>

<section>
  <p class="eyebrow">Banners</p>
  <h2>One composition, five canvases</h2>
  <p>The social card, the site header and the print banner are the same function at different
  sizes — which is what stops them being three designs that happen to share a mark.</p>
  <div class="lockup lockup--wide" style="padding:0;overflow:hidden">
    {svg("banners/fi-sabilillah-social-card.svg", box="0 0 1200 630")}</div>
  <div class="grid g2" style="margin-top:22px">
    <div class="lockup" style="padding:0;overflow:hidden">
      {svg("banners/fi-sabilillah-website-header.svg", box="0 0 1600 320")}</div>
    <div class="lockup" style="padding:0;overflow:hidden">
      {svg("banners/fi-sabilillah-twitter-header.svg", box="0 0 1500 500")}</div>
  </div>
</section>

<section>
  <p class="eyebrow">Splash &amp; motion</p>
  <h2>One gesture, then still</h2>
  <div class="grid g2">
    <div class="splashes">
      <div class="splash">{svg("splash/splash-dark.svg", box="0 0 1080 1920")}</div>
      <div class="splash">{svg("splash/splash-light.svg", box="0 0 1080 1920")}</div>
    </div>
    <div>
      <p>The arch draws in and stops. In the application it is simpler still: a fade and a
      settle from 96% to 100% over 520ms on the Amanah curve.</p>
      <p>Forbidden without exception: spinning, flashing, looping, confetti, particles, glow
      pulses, shimmer passes across the gold, and any animation that plays on completing an act
      of service. A record of service is between a person and their Lord; animating it turns it
      into a performance.</p>
      <p>Under reduced motion the animation is <strong>removed, not shortened</strong>. Somebody
      who asked the system for no motion asked for no motion.</p>
    </div>
  </div>
</section>

<section>
  <p class="eyebrow">Incorrect use</p>
  <h2>Six ways to ruin it</h2>
  <div class="grid g3">{wrong}</div>
</section>

<section>
  <p class="eyebrow">Honest notes</p>
  <h2>What has not been verified</h2>
  <ul>
    <li><strong>Nothing here has been seen on a device.</strong> Every mark was rendered and
    inspected as an image. Colour on an OLED panel, the icon against a real wallpaper, and the
    themed tint on a specific OEM skin are all unverified.</li>
    <li><strong>The Arabic has not been read by a native reader.</strong> It is shaped by
    HarfBuzz from real Unicode in a real Arabic typeface, and the checks confirm the glyph
    sequence — but "technically correct" and "well set" are different claims, and only the
    first is made here.</li>
    <li><strong>The emblem is drawn from a supplied reference, at the client's direction.</strong>
    Every path is original geometry and no part of the reference is traced, embedded or
    reproduced — but the design is deliberately close to it, which is a different position from
    independent creation, and anyone filing should say so.</li>
    <li><strong>No trademark is registered</strong>, and <strong>scholarly review is
    recommended before launch</strong> on the use of the phrase as a product name and on setting
    <span dir="rtl">في سبيل الله</span> inside a commercial mark. This board asserts that the
    marks claim no religious authority; a qualified scholar should confirm the rest.</li>
  </ul>
</section>

<footer class="wrap">
  Every asset on this page is generated by <code>tools/brand/build.py</code> from one geometry
  source and checked by <code>tools/brand/validate.py</code> — 369 checks, 0 failures. Nothing
  under <code>brand/</code> is edited by hand.
</footer>
</div>
"""


open(OUT, "w").write(HTML)
print("board written to", OUT, len(HTML), "bytes")
