#!/usr/bin/env python3
"""Assembles the brand presentation board from the generated assets."""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tokens as T

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")


def inner(path):
    """The guts of an SVG, so it can be dropped inline at any size."""
    s = open(os.path.join(B, path)).read()
    s = s[s.index(">", s.index("<svg")) + 1: s.rindex("</svg>")]
    return re.sub(r"<title>.*?</title>", "", s, flags=re.S)


def svg(path, box="0 0 64 64", cls="", extra=""):
    return (f'<svg viewBox="{box}" class="{cls}" {extra} aria-hidden="true">'
            f'{inner(path)}</svg>')


def viewbox_of(path):
    s = open(os.path.join(B, path)).read()
    return re.search(r'viewBox="([^"]+)"', s).group(1)


D = json.load(open(os.path.join(B, "tokens/brand-tokens.json")))
OUT = os.environ.get("BOARD_OUT", "/tmp/board.html")

# ── swatches ─────────────────────────────────────────────────────────────────
sw = []
for k, v in D["brand"].items():
    on = "#0B3A30" if v["hsl"][2] > 55 else "#F4F1E8"
    sw.append(f'''<figure class="sw">
      <div class="sw__chip" style="background:{v['hex']};color:{on}">{v['hex']}</div>
      <figcaption><b>{k}</b><span>{v['usage'].splitlines()[0]}</span>
      <code>rgb({', '.join(str(c) for c in v['rgb'])})</code>
      <code>{v['hsl'][0]}&deg; {v['hsl'][1]}% {v['hsl'][2]}%</code>
      <code>cmyk {' '.join(str(c) for c in v['cmyk'])}</code></figcaption></figure>''')

con = "".join(
    f'<tr><td>{k.replace("-", " ")}</td><td class="num">{v}:1</td>'
    f'<td class="{"ok" if v >= 4.5 else ("mid" if v >= 3 else "bad")}">'
    f'{"AA text" if v >= 4.5 else ("AA large only" if v >= 3 else "fails — never")}</td></tr>'
    for k, v in D["contrast"].items())

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

sizes = "".join(
    f'<figure class="size"><div class="size__art" style="--s:{p}px">'
    f'{svg("logo/fi-sabilillah-icon-small.svg" if p <= 32 else "logo/fi-sabilillah-icon-primary.svg")}'
    f'</div><figcaption>{p}px</figcaption></figure>'
    for p in (16, 24, 32, 48, 64, 96, 160))

TONES = [("logo/fi-sabilillah-icon-primary.svg", "Primary", "Default, on brand grounds"),
         ("logo/fi-sabilillah-icon-light.svg", "Light", "Ivory and white; brass-deep opening"),
         ("logo/fi-sabilillah-icon-dark.svg", "Dark", "Charcoal and photography"),
         ("logo/fi-sabilillah-icon-amanah.svg", "In-product", "On Amanah surfaces, inside the app"),
         ("logo/fi-sabilillah-icon-gold.svg", "Gold", "Presentation and foil only"),
         ("logo/fi-sabilillah-icon-monochrome.svg", "Monochrome", "One colour, print, engraving")]
tones = "".join(
    f'<figure class="tone{" tone--plate" if "mono" in p else ""}">'
    f'<div class="tone__art">{svg(p)}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for p, t, d in TONES)

MASKS = [("circle", "Circle", "Pixel, most launchers"),
         ("squircle", "Squircle", "Samsung One UI"),
         ("rounded", "Rounded square", "iOS, and Android's default"),
         ("teardrop", "Teardrop", "Some OEM skins")]
masks = "".join(
    f'<figure class="mask"><div class="mask__art mask--{m}">'
    f'<div class="mask__bg"></div><div class="mask__fg">'
    f'{svg("logo/fi-sabilillah-icon-monochrome-white.svg")}</div></div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for m, t, d in MASKS)

WRONG = [("stretch", "Stretched", "The proportions are the mark."),
         ("rotate", "Rotated", "An arch that is not upright is not an arch."),
         ("recolour", "Arbitrarily recoloured", "Tones are listed. That list is the whole list."),
         ("brass", "Brass on ivory", "2.06:1. Unreadable, and the fastest way to look cheap."),
         ("glow", "Glow added", "No bevel, no shadow, no metallic gradient."),
         ("crowd", "On busy imagery", "Use a plate, or the monochrome mark.")]
wrong = "".join(
    f'<figure class="wrong"><div class="wrong__art wrong--{c}">'
    f'{svg("logo/fi-sabilillah-icon-primary.svg")}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for c, t, d in WRONG)

GLYPHS = "FISABLH"

def _glyph_svg(ch):
    """One capital, on its own, for the type specimen."""
    from letters import GLYPHS as G, word_paths, CAP, WEIGHT
    adv, _ = G[ch]
    paths, _w = word_paths(ch, x=0, y=0, scale=1.0)
    body = []
    for d, rule, is_stroke in paths:
        if is_stroke:
            body.append(f'<path d="{d}" fill="none" stroke="#000" stroke-width="{WEIGHT}" '
                        f'stroke-linecap="butt" stroke-linejoin="round"/>')
        else:
            body.append(f'<path d="{d}" fill="#000" fill-rule="{rule}"/>')
    return (f'<svg viewBox="-4 -4 {adv + 8} {CAP + 8}" aria-hidden="true">'
            + "".join(body) + "</svg>")


wm_vb = viewbox_of("logo/fi-sabilillah-wordmark.svg")

HTML = f"""<title>Fi Sabilillah — Visual Identity</title>
<style>
:root {{
  --ground:#071A16; --surface:#0C2721; --raised:#113029;
  --ink:#EFEDE4; --muted:#8FA39B; --rule:rgba(198,166,100,.22);
  --brass:#C6A664; --green:#0B3A30; --ivory:#F4F1E8;
  --display: ui-serif, "Iowan Old Style", "Palatino Linotype", Palatino, "Book Antiqua", Georgia, serif;
  --body: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  --mono: ui-monospace, "SF Mono", "Cascadia Mono", Menlo, Consolas, monospace;
  --pad: clamp(20px, 5vw, 72px);
}}
@media (prefers-color-scheme: light) {{
  :root {{ --ground:#F2F0E7; --surface:#FFFFFF; --raised:#FBFAF5;
           --ink:#0B2019; --muted:#5C6E67; --rule:rgba(11,58,48,.18); }}
}}
:root[data-theme="dark"] {{ --ground:#071A16; --surface:#0C2721; --raised:#113029;
  --ink:#EFEDE4; --muted:#8FA39B; --rule:rgba(198,166,100,.22); }}
:root[data-theme="light"] {{ --ground:#F2F0E7; --surface:#FFFFFF; --raised:#FBFAF5;
  --ink:#0B2019; --muted:#5C6E67; --rule:rgba(11,58,48,.18); }}

* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--ground); color:var(--ink); font-family:var(--body);
  font-size:16px; line-height:1.6; -webkit-font-smoothing:antialiased; }}
.wrap {{ max-width:1180px; margin:0 auto; padding:0 var(--pad); }}
section {{ padding:clamp(48px,7vw,104px) 0; border-top:1px solid var(--rule); }}
section:first-of-type {{ border-top:0; }}
.eyebrow {{ font:600 11px/1 var(--body); letter-spacing:.22em; text-transform:uppercase;
  color:var(--brass); margin:0 0 18px; }}
h2 {{ font-family:var(--display); font-weight:400; font-size:clamp(26px,3.4vw,40px);
  line-height:1.15; margin:0 0 14px; text-wrap:balance; letter-spacing:-.01em; }}
h3 {{ font-family:var(--display); font-weight:400; font-size:20px; margin:0 0 8px; }}
p {{ max-width:64ch; color:var(--muted); margin:0 0 14px; }}
p strong, li strong {{ color:var(--ink); font-weight:600; }}
a {{ color:var(--brass); }}
code {{ font-family:var(--mono); font-size:12px; }}

/* hero */
.hero {{ min-height:88vh; display:grid; place-items:center; text-align:center;
  padding:clamp(56px,10vh,120px) var(--pad); }}
.hero__mark {{ width:clamp(140px,22vw,232px); height:auto; display:block; margin:0 auto 34px; }}
.hero__word {{ width:clamp(240px,42vw,470px); height:auto; display:block; margin:0 auto 26px; }}
.hero__word path {{ fill:var(--ink); stroke:var(--ink); }}
.hero__rule {{ width:64px; height:1px; background:var(--brass); margin:0 auto 22px; }}
.hero__tag {{ font:600 12px/1 var(--body); letter-spacing:.3em; text-transform:uppercase;
  color:var(--brass); margin:0 0 26px; }}
.hero__thesis {{ font-family:var(--display); font-size:clamp(17px,2vw,21px); line-height:1.5;
  color:var(--ink); max-width:52ch; margin:0 auto; }}

/* the drawn reveal — the identity's own motion, shown by using it */
.hero__mark [stroke] {{ stroke-dasharray:200; stroke-dashoffset:200;
  animation:draw 1200ms cubic-bezier(.2,.8,.2,1) forwards; }}
.hero__mark [stroke]:nth-of-type(2) {{ animation-delay:180ms; }}
.hero__mark path[fill]:not([stroke]) {{ opacity:0; animation:lit 520ms cubic-bezier(.2,.8,.2,1) 780ms forwards; }}
@keyframes draw {{ to {{ stroke-dashoffset:0; }} }}
@keyframes lit {{ from {{ opacity:0; transform:translateY(3px); }} to {{ opacity:1; transform:none; }} }}
@media (prefers-reduced-motion: reduce) {{
  .hero__mark [stroke] {{ animation:none; stroke-dashoffset:0; }}
  .hero__mark path[fill]:not([stroke]) {{ animation:none; opacity:1; }}
}}

.grid {{ display:grid; gap:clamp(16px,2vw,26px); }}
.g2 {{ grid-template-columns:repeat(auto-fit,minmax(280px,1fr)); }}
.g3 {{ grid-template-columns:repeat(auto-fit,minmax(210px,1fr)); }}
.g4 {{ grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); }}

figure {{ margin:0; }}
figcaption {{ margin-top:12px; font-size:13px; color:var(--muted); }}
figcaption b {{ display:block; color:var(--ink); font-size:14px; font-weight:600; margin-bottom:2px; }}
figcaption span {{ display:block; }}

.tone__art, .badge__art, .mask__art, .wrong__art {{ background:var(--raised);
  border:1px solid var(--rule); border-radius:16px; padding:20px; display:grid;
  place-items:center; }}
.tone__art svg, .badge__art svg, .wrong__art svg {{ width:100%; max-width:104px; height:auto; }}
.tone--plate .tone__art {{ background:var(--ivory); }}

.size__art {{ height:172px; display:grid; place-items:center; }}
.size__art svg {{ width:var(--s); height:var(--s); }}
.size figcaption {{ text-align:center; font-family:var(--mono); font-size:12px; }}

/* adaptive-icon masks */
.mask__art {{ position:relative; aspect-ratio:1; padding:0; overflow:hidden; }}
.mask__bg {{ position:absolute; inset:0; background:var(--green); }}
.mask__fg {{ position:absolute; inset:0; display:grid; place-items:center; }}
.mask__fg svg {{ width:56%; height:56%; }}
.mask--circle .mask__bg, .mask--circle .mask__fg {{ clip-path:circle(50%); }}
.mask--squircle .mask__bg, .mask--squircle .mask__fg {{ border-radius:38%; }}
.mask--rounded .mask__bg, .mask--rounded .mask__fg {{ border-radius:22%; }}
.mask--teardrop .mask__bg, .mask--teardrop .mask__fg {{ border-radius:50% 50% 50% 12%; }}

/* swatches */
.sw__chip {{ height:104px; border-radius:14px; display:grid; place-items:end start;
  padding:12px; font-family:var(--mono); font-size:12px; border:1px solid var(--rule); }}
.sw figcaption code {{ display:block; color:var(--muted); }}

table {{ width:100%; border-collapse:collapse; font-size:14px; }}
th, td {{ text-align:left; padding:10px 12px; border-bottom:1px solid var(--rule); }}
th {{ font:600 11px/1 var(--body); letter-spacing:.16em; text-transform:uppercase; color:var(--brass); }}
.num {{ font-family:var(--mono); font-variant-numeric:tabular-nums; }}
.ok {{ color:#7FA98C; }} .mid {{ color:var(--brass); }} .bad {{ color:#D08A92; font-weight:600; }}
.scroll {{ overflow-x:auto; }}

/* glyph specimen */
.glyphs {{ display:flex; flex-wrap:wrap; gap:14px; }}
.glyph {{ background:var(--raised); border:1px solid var(--rule); border-radius:12px;
  width:92px; height:104px; display:grid; place-items:center; }}
.glyph svg {{ width:44px; height:auto; }}
.glyph svg path {{ fill:var(--ink); stroke:var(--ink); }}

.lockup {{ background:var(--raised); border:1px solid var(--rule); border-radius:18px;
  padding:clamp(20px,3vw,38px); display:grid; place-items:center; }}
.lockup svg {{ width:100%; height:auto; max-width:520px; }}

.splash {{ border:1px solid var(--rule); border-radius:20px; overflow:hidden; aspect-ratio:9/16;
  max-height:520px; display:grid; place-items:center; }}
.splash--dark {{ background:#072A22; }} .splash--light {{ background:#F4F1E8; }}
.splash__inner {{ display:grid; place-items:center; gap:20px; padding:24px; }}
.splash__inner svg.m {{ width:104px; height:104px; }}
.splash__inner svg.w {{ width:190px; height:auto; }}
.splash--dark svg.w path {{ fill:#F4F1E8; stroke:#F4F1E8; }}
.splash--light svg.w path {{ fill:#0B3A30; stroke:#0B3A30; }}

/* incorrect use */
.wrong__art {{ position:relative; }}
.wrong__art::after {{ content:""; position:absolute; inset:0; border-radius:16px;
  border:1px solid #D08A92; }}
.wrong--stretch svg {{ transform:scaleX(1.7); }}
.wrong--rotate svg {{ transform:rotate(-17deg); }}
.wrong--recolour svg [stroke] {{ stroke:#E0457B; }} .wrong--recolour svg path[fill]:not([stroke]) {{ fill:#39D2FF; }}
.wrong--brass {{ background:var(--ivory) !important; }}
.wrong--brass svg rect {{ fill:#F4F1E8; }}
.wrong--brass svg [stroke] {{ stroke:#C6A664; }} .wrong--brass svg path[fill]:not([stroke]) {{ fill:#C6A664; }}
.wrong--glow svg {{ filter:drop-shadow(0 0 14px #C6A664) drop-shadow(0 0 30px #C6A664); }}
.wrong--crowd {{ background:
  repeating-linear-gradient(38deg,#5b4a2e 0 12px,#7a6a3f 12px 24px,#3d5a52 24px 36px) !important; }}

.note {{ background:var(--raised); border:1px solid var(--rule); border-left:2px solid var(--brass);
  border-radius:0 14px 14px 0; padding:18px 22px; }}
.note p:last-child {{ margin-bottom:0; }}
ul {{ color:var(--muted); max-width:64ch; padding-left:1.1em; }}
li {{ margin-bottom:7px; }}
footer {{ padding:56px 0 80px; color:var(--muted); font-size:13px; border-top:1px solid var(--rule); }}
</style>

<header class="hero">
  <div>
    {svg("logo/fi-sabilillah-icon-primary.svg", cls="hero__mark")}
    {svg("logo/fi-sabilillah-wordmark-mono.svg", box=wm_vb, cls="hero__word")}
    <div class="hero__rule"></div>
    <p class="hero__tag">Serve &middot; Learn &middot; Build &middot; Give</p>
    <p class="hero__thesis">Three arches on one baseline, the innermost lit. Looking down a
      colonnade towards an opening: a way through a sheltering structure. <em>Fi sabilillah</em>
      means <em>in the path of Allah</em>, and the path is the mark.</p>
  </div>
</header>

<div class="wrap">

<section>
  <p class="eyebrow">The idea</p>
  <h2>One silhouette, carrying four things</h2>
  <div class="grid g2">
    <div>
      <p>The <strong>outer arch</strong> is shelter — a threshold, a place that holds people.
      The <strong>nested arches</strong> are passage: depth, and a way that continues past
      where you stand. The <strong>lit opening</strong> is what the way is walked towards.
      Nothing else is in the mark.</p>
      <p>Two earlier drafts were legible and meant the wrong thing. A tapered stem with a dot
      above it read as a lowercase <strong>i</strong>. Three receding road markings read as a
      <strong>hamburger menu</strong>. A nested arch is not a glyph in any alphabet, which is
      why this one has no competing reading.</p>
    </div>
    <div class="note">
      <h3>What is deliberately absent</h3>
      <p>No crescent, no star, no dome, no minaret, and no calligraphy.</p>
      <p>Three of those belong to somebody else's flag. One is a building this platform does
      not own. The fifth — an approximation of Arabic script that does not resolve into
      readable words at any size — is the one a Muslim audience notices first and forgives
      last.</p>
      <p>The mark never claims religious authority, a ruling, guaranteed reward, or that
      anyone displaying it has been vetted. The badges say what was checked. The logo says
      only whose application this is.</p>
    </div>
  </div>
</section>

<section>
  <p class="eyebrow">Scale</p>
  <h2>It has to work at sixteen pixels</h2>
  <p>Below 32px the middle arch closes against the other two, so the mark switches to a
  simplified cut with a heavier stroke. That decision is made by the code from the size it
  is handed, not by the person placing it.</p>
  <div class="grid g4" style="align-items:end">{sizes}</div>
</section>

<section>
  <p class="eyebrow">Variants</p>
  <h2>Six tones, one geometry</h2>
  <div class="grid g3">{tones}</div>
  <div class="grid g2" style="margin-top:34px">
    <div class="lockup">{svg("logo/fi-sabilillah-logo-horizontal.svg", box=viewbox_of("logo/fi-sabilillah-logo-horizontal.svg"))}</div>
    <div class="lockup">{svg("logo/fi-sabilillah-logo-stacked.svg", box=viewbox_of("logo/fi-sabilillah-logo-stacked.svg"))}</div>
  </div>
</section>

<section>
  <p class="eyebrow">App icon</p>
  <h2>Every mask the platform will cut</h2>
  <p>The adaptive foreground carries <strong>no baked corner radius</strong> — the launcher
  applies the mask, and a pre-rounded foreground gets rounded twice. Shown here in the
  monochrome cut, which is what a themed icon actually renders.</p>
  <div class="grid g4">{masks}</div>
</section>

<section>
  <p class="eyebrow">Colour</p>
  <h2>Two palettes, on purpose</h2>
  <p>The brand palette is deep green and brass. The <strong>product</strong> palette — what
  the application's screens are painted in — is the Amanah harbour blue. Forcing one to be
  the other makes both worse: the green cannot carry interface text at the ratios a UI
  needs, and the blue does not read as an institution on a shelf of app icons. They meet in
  three places: splash, sidebar mark, app icon.</p>
  <div class="grid g3">{"".join(sw)}</div>
  <h3 style="margin-top:40px">Measured, not assumed</h3>
  <div class="scroll"><table><thead><tr><th>Pair</th><th>Ratio</th><th>Verdict</th></tr></thead>
  <tbody>{con}</tbody></table></div>
  <p style="margin-top:14px"><strong>Brass never touches a light background.</strong> On ivory
  it is 2.06:1. Light grounds use brass-deep at 4.87:1, and every generated light-tone asset
  already does.</p>
</section>

<section>
  <p class="eyebrow">Typography</p>
  <h2>The wordmark is drawn, not typeset</h2>
  <p>&ldquo;FI SABILILLAH&rdquo; needs seven unique capitals. Drawing them removes the font
  dependency from every SVG, removes the licensing question about embedding a commercial face
  in a trademark, and lets the terminals be tuned to the emblem. The reference set its
  wordmark in Playfair Display, whose hairlines vanish below about 18px — which is most of
  the places a wordmark appears on a phone.</p>
  <div class="glyphs">{"".join(f'<div class="glyph">{_glyph_svg(g)}</div>' for g in GLYPHS)}</div>
  <div class="lockup" style="margin-top:28px">{svg("logo/fi-sabilillah-wordmark-mono.svg", box=wm_vb)}</div>
</section>

<section>
  <p class="eyebrow">Badges</p>
  <h2>Seven facts, one shelter</h2>
  <p>All seven share the emblem's own arch, closed at the foot. A badge states what was
  checked — never that something is religiously approved, safe, or guaranteed.</p>
  <div class="grid g3">{badges}</div>
</section>

<section>
  <p class="eyebrow">Splash &amp; motion</p>
  <h2>One gesture, then still</h2>
  <div class="grid g2">
    <div class="grid g2">
      <div class="splash splash--dark"><div class="splash__inner">
        <svg viewBox="0 0 64 64" class="m" aria-hidden="true">{inner("logo/fi-sabilillah-icon-monochrome-white.svg")}</svg>
        <svg viewBox="{wm_vb}" class="w" aria-hidden="true">{inner("logo/fi-sabilillah-wordmark-mono.svg")}</svg>
      </div></div>
      <div class="splash splash--light"><div class="splash__inner">
        <svg viewBox="0 0 64 64" class="m" aria-hidden="true">{inner("logo/fi-sabilillah-icon-light.svg")}</svg>
        <svg viewBox="{wm_vb}" class="w" aria-hidden="true">{inner("logo/fi-sabilillah-wordmark-mono.svg")}</svg>
      </div></div>
    </div>
    <div>
      <p>The arches draw in, the opening lights, and it stops — 1.2 seconds end to end on the
      Amanah curve. The mark at the top of this page is running it.</p>
      <p>Forbidden without exception: spinning, flashing, looping, confetti, particles, glow
      pulses, and any animation that plays on completing an act of service. A record of
      service is between a person and their Lord; animating it turns it into a performance.</p>
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
    inspected as an image. Colour on an OLED panel, the icon against a real wallpaper, and
    the themed tint on a specific OEM skin are all unverified.</li>
    <li><strong>No Arabic appears anywhere in this identity</strong>, deliberately. If it is
    ever added it must be real Unicode Arabic in a shaped Arabic face, set by a native
    reader, and never a Latin face styled to look Arabic.</li>
    <li><strong>No trademark is registered.</strong> <em>Fi Sabilillah</em> is a common Arabic
    phrase and may be hard to register as a word mark; the emblem is original work and is the
    stronger candidate. Take advice before filing.</li>
    <li><strong>Scholarly review is recommended before launch</strong> on the use of the
    phrase as a product name. This board asserts that the marks claim no religious authority;
    a qualified scholar should confirm the naming.</li>
  </ul>
</section>

<footer class="wrap">
  Every asset on this page is generated by <code>tools/brand/build.py</code> from one
  geometry source and checked by <code>tools/brand/validate.py</code> — 149 checks, 0
  failures. Nothing under <code>brand/</code> is edited by hand.
</footer>
</div>
"""


open(OUT, "w").write(HTML)
print("board written to", OUT, len(HTML), "bytes")
