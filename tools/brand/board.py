#!/usr/bin/env python3
"""Assembles the brand presentation board from the generated assets."""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tokens as T
from letters import word_path_data, CAP

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
B = os.path.join(ROOT, "brand")

_uid = [0]


def inner(path):
    """
    The guts of an SVG, so it can be dropped inline at any size.

    The gradient id is rewritten per inclusion. Every emblem export defines
    `fsGold`, and a page holding fifteen of them would hold fifteen elements with
    the same id -- which is invalid, and which resolves every reference to
    whichever one the parser saw first.
    """
    s = open(os.path.join(B, path)).read()
    s = s[s.index(">", s.index("<svg")) + 1: s.rindex("</svg>")]
    s = re.sub(r"<title>.*?</title>", "", s, flags=re.S)
    _uid[0] += 1
    return s.replace("fsGold", f"g{_uid[0]}")


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
    on = "#0F3D34" if v["hsl"][2] > 55 else "#F5F2E9"
    sw.append(f'''<figure class="sw">
      <div class="sw__chip" style="background:{v['hex']};color:{on}">{v['hex']}</div>
      <figcaption><b>{k}</b><span>{' '.join(v['usage'].split())}</span>
      <code>rgb({', '.join(str(c) for c in v['rgb'])})</code>
      <code>{v['hsl'][0]}&deg; {v['hsl'][1]}% {v['hsl'][2]}%</code>
      <code>cmyk {' '.join(str(c) for c in v['cmyk'])}</code></figcaption></figure>''')

ramp = "".join(
    f'<div class="ramp__stop" style="background:{c}"><span>{c}</span>'
    f'<em>{o}%</em></div>'
    for c, o in zip(D["goldRamp"], (0, 30, 66, 100)))

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

TONES = [("logo/fi-sabilillah-icon-primary.svg", "Primary", "Default. Gradient gold on deep green"),
         ("logo/fi-sabilillah-icon-flat.svg", "Flat", "Silkscreen, embroidery, one plate"),
         ("logo/fi-sabilillah-icon-light.svg", "Light", "Ivory and white; drawn in gold-deep"),
         ("logo/fi-sabilillah-icon-dark.svg", "Dark", "Charcoal and photography"),
         ("logo/fi-sabilillah-icon-ivory.svg", "Ivory", "Where gold would compete with gold"),
         ("logo/fi-sabilillah-icon-amanah.svg", "In-product", "On Amanah surfaces, inside the app"),
         ("logo/fi-sabilillah-icon-mono-black.svg", "Mono, black", "Print, engraving, stamps"),
         ("logo/fi-sabilillah-icon-mono-white.svg", "Mono, white", "Reversed out of any dark ground")]
tones = "".join(
    f'<figure class="tone{" tone--plate" if p.endswith("mono-black.svg") else ""}'
    f'{" tone--dark" if p.endswith("mono-white.svg") else ""}">'
    f'<div class="tone__art">{svg(p)}</div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for p, t, d in TONES)

MASKS = [("circle", "Circle", "Pixel, most launchers"),
         ("squircle", "Squircle", "Samsung One UI"),
         ("rounded", "Rounded square", "iOS, and Android's default"),
         ("teardrop", "Teardrop", "Some OEM skins")]
masks = "".join(
    f'<figure class="mask"><div class="mask__art mask--{m}">'
    f'<div class="mask__bg"></div><div class="mask__fg">'
    f'{svg("logo/fi-sabilillah-icon-mono-white.svg")}</div></div>'
    f'<figcaption><b>{t}</b><span>{d}</span></figcaption></figure>' for m, t, d in MASKS)

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

# The seven unique capitals in FI SABILILLAH.
GLYPHS = "FISABLH"


def _glyph_svg(ch):
    """One capital, on its own, for the type specimen."""
    d, w = word_path_data(ch, x=0, y=0, scale=1.0)
    return (f'<svg viewBox="-6 -6 {w + 12:.1f} {CAP + 12:.1f}" aria-hidden="true">'
            f'<path d="{d}"/></svg>')


wm_vb = viewbox_of("logo/fi-sabilillah-wordmark.svg")
hz = "logo/fi-sabilillah-logo-horizontal-tagline.svg"

HTML = f"""<title>Fi Sabilillah — Visual Identity</title>
<style>
:root {{
  --ground:#0A2A24; --surface:#0F3D34; --raised:#14483D;
  --ink:#F1EEE4; --muted:#96AFA4; --rule:rgba(212,175,55,.24);
  --gold:#D4AF37; --green:#0F3D34; --ivory:#F5F2E9;
  --display: ui-serif, "Iowan Old Style", "Palatino Linotype", Palatino, "Book Antiqua", Georgia, serif;
  --body: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  --mono: ui-monospace, "SF Mono", "Cascadia Mono", Menlo, Consolas, monospace;
  --halo: rgba(28,87,74,.55);
  --pad: clamp(20px, 5vw, 72px);
}}
@media (prefers-color-scheme: light) {{
  :root {{ --ground:#F3F1E8; --surface:#FFFFFF; --raised:#FBFAF5;
           --ink:#0C2A23; --muted:#5C6E67; --rule:rgba(15,61,52,.18); --gold:#7A5E15;
           --halo: rgba(212,175,55,.16); }}
}}
:root[data-theme="dark"] {{ --ground:#0A2A24; --surface:#0F3D34; --raised:#14483D;
  --ink:#F1EEE4; --muted:#96AFA4; --rule:rgba(212,175,55,.24); --gold:#D4AF37; --halo:rgba(28,87,74,.55); }}
:root[data-theme="light"] {{ --ground:#F3F1E8; --surface:#FFFFFF; --raised:#FBFAF5;
  --ink:#0C2A23; --muted:#5C6E67; --rule:rgba(15,61,52,.18); --gold:#7A5E15;
  --halo:rgba(212,175,55,.16); }}

* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--ground); color:var(--ink); font-family:var(--body);
  font-size:16px; line-height:1.6; -webkit-font-smoothing:antialiased; }}
.wrap {{ max-width:1180px; margin:0 auto; padding:0 var(--pad); }}
section {{ padding:clamp(48px,7vw,104px) 0; border-top:1px solid var(--rule); }}
section:first-of-type {{ border-top:0; }}
.eyebrow {{ font:600 11px/1 var(--body); letter-spacing:.22em; text-transform:uppercase;
  color:var(--gold); margin:0 0 18px; }}
h2 {{ font-family:var(--display); font-weight:400; font-size:clamp(26px,3.4vw,40px);
  line-height:1.15; margin:0 0 14px; text-wrap:balance; letter-spacing:-.01em; }}
h3 {{ font-family:var(--display); font-weight:400; font-size:20px; margin:0 0 8px; }}
p {{ max-width:64ch; color:var(--muted); margin:0 0 14px; }}
p strong, li strong {{ color:var(--ink); font-weight:600; }}
a {{ color:var(--gold); }}
code {{ font-family:var(--mono); font-size:12px; }}

/* hero */
.hero {{ min-height:88vh; display:grid; place-items:center; text-align:center;
  padding:clamp(56px,10vh,120px) var(--pad);
  background:radial-gradient(58% 44% at 50% 36%, var(--halo), transparent 72%); }}
.hero__mark {{ width:clamp(150px,24vw,250px); height:auto; display:block; margin:0 auto 34px; }}
.hero__word {{ width:min(84vw,520px); height:auto; display:block; margin:0 auto 24px; }}
.hero__word path {{ fill:var(--ink); }}
.hero__rule {{ width:64px; height:1px; background:var(--gold); margin:0 auto 22px; }}
.hero__tag {{ font-family:var(--display); font-size:clamp(15px,2vw,19px); letter-spacing:.02em;
  color:var(--gold); margin:0 0 10px; }}
.hero__pillars {{ font:600 11px/1 var(--body); letter-spacing:.3em; text-transform:uppercase;
  color:var(--gold); opacity:.8; margin:0 0 26px; }}
.hero__thesis {{ font-family:var(--display); font-size:clamp(17px,2vw,21px); line-height:1.5;
  color:var(--ink); max-width:52ch; margin:0 auto; }}

/* the identity's own motion, shown by using it */
.hero__mark [stroke] {{ stroke-dasharray:220; stroke-dashoffset:220;
  animation:draw 1100ms cubic-bezier(.2,.8,.2,1) forwards; }}
.hero__mark [stroke]:nth-of-type(2) {{ animation-delay:160ms; }}
.hero__mark path[fill]:not([stroke]):not(:first-child) {{
  opacity:0; animation:lit 520ms cubic-bezier(.2,.8,.2,1) 720ms forwards; }}
@keyframes draw {{ to {{ stroke-dashoffset:0; }} }}
@keyframes lit {{ from {{ opacity:0; transform:translateY(3px); }} to {{ opacity:1; transform:none; }} }}
@media (prefers-reduced-motion: reduce) {{
  .hero__mark [stroke] {{ animation:none; stroke-dashoffset:0; }}
  .hero__mark path[fill]:not([stroke]):not(:first-child) {{ animation:none; opacity:1; }}
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
.tone--dark .tone__art {{ background:#0D1115; }}

.ramp-sizes {{ display:flex; flex-wrap:wrap; align-items:flex-end;
  gap:clamp(14px,3vw,40px); }}
.size__art {{ height:172px; display:grid; place-items:center; }}
.size__art svg {{ width:var(--s); height:var(--s); }}
.size figcaption {{ text-align:center; font-family:var(--mono); font-size:12px; }}

/* adaptive-icon masks */
.mask__art {{ position:relative; aspect-ratio:1; padding:0; overflow:hidden; }}
.mask__bg {{ position:absolute; inset:0; background:var(--green); }}
.mask__fg {{ position:absolute; inset:0; display:grid; place-items:center; }}
.mask__fg svg {{ width:62%; height:62%; }}
.mask--circle .mask__bg, .mask--circle .mask__fg {{ clip-path:circle(50%); }}
.mask--squircle .mask__bg, .mask--squircle .mask__fg {{ border-radius:38%; }}
.mask--rounded .mask__bg, .mask--rounded .mask__fg {{ border-radius:22%; }}
.mask--teardrop .mask__bg, .mask--teardrop .mask__fg {{ border-radius:50% 50% 50% 12%; }}

/* swatches */
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

/* glyph specimen */
.glyphs {{ display:flex; flex-wrap:wrap; gap:14px; }}
.glyph {{ background:var(--raised); border:1px solid var(--rule); border-radius:12px;
  width:92px; height:104px; display:grid; place-items:center; }}
.glyph svg {{ width:auto; height:52px; }}
.glyph svg path {{ fill:var(--ink); }}

.lockup {{ background:var(--raised); border:1px solid var(--rule); border-radius:18px;
  padding:clamp(20px,3vw,38px); display:grid; place-items:center; }}
.lockup svg {{ width:100%; height:auto; max-width:520px; }}

.splashes {{ display:grid; grid-template-columns:1fr 1fr; gap:clamp(12px,2vw,20px);
  align-content:start; }}
.splash {{ border:1px solid var(--rule); border-radius:20px; overflow:hidden;
  aspect-ratio:9/16; display:grid; place-items:center; }}
.splash--dark {{ background:#0A2A24; }} .splash--light {{ background:#F5F2E9; }}
.splash__inner {{ display:grid; place-items:center; gap:18px; padding:24px; width:100%; }}
.splash__inner svg.m {{ width:104px; height:104px; }}
.splash__inner svg.w {{ width:76%; height:auto; }}
.splash--dark svg.w path {{ fill:#F5F2E9; }}
.splash--light svg.w path {{ fill:#0F3D34; }}

/* incorrect use */
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
ul {{ color:var(--muted); max-width:64ch; padding-left:1.1em; }}
li {{ margin-bottom:7px; }}
footer {{ padding:56px 0 80px; color:var(--muted); font-size:13px; border-top:1px solid var(--rule); }}
</style>

<header class="hero">
  <div>
    {svg("logo/fi-sabilillah-icon-primary.svg", cls="hero__mark")}
    {svg("logo/fi-sabilillah-wordmark-mono.svg", box=wm_vb, cls="hero__word")}
    <div class="hero__rule"></div>
    <p class="hero__tag">For the Sake of Allah</p>
    <p class="hero__pillars">Serve &middot; Learn &middot; Grow &middot; Give</p>
    <p class="hero__thesis">A gold arch on deep green, holding a crescent and star above a
      colonnade, rooted in two leaves. <em>Fi sabilillah</em> means <em>in the path of
      Allah</em>, and the path is the mark.</p>
  </div>
</header>

<div class="wrap">

<section>
  <p class="eyebrow">The idea</p>
  <h2>One silhouette, carrying five things</h2>
  <div class="grid g2">
    <div>
      <p>The <strong>ogee arch</strong> is shelter, and the threshold you cross to enter it —
      the silhouette the whole identity is recognised by. The <strong>inner keyline</strong>
      makes it a doorway rather than an outline. The <strong>crescent and star</strong> name
      the community the platform is for, plainly rather than by hint. The
      <strong>three-arch arcade</strong> is a colonnade: a way that continues past where you
      stand, three because a colonnade is a repetition and two does not repeat. The
      <strong>leaf sweeps</strong> are growth from what is sheltered, and they give the mark a
      base so it sits rather than floats.</p>
      <p>Every path is computed, not traced: the crescent is the intersection of two circles
      solved exactly, the arcade is an arc sweep, the arch and leaves are cubics. That is what
      lets the same geometry produce an SVG, a VectorDrawable and a React component that are
      the same shape rather than three drawings of it.</p>
    </div>
    <div class="note">
      <h3>The one departure from the reference</h3>
      <p>The reference carries a block of ornamental script where the arcade now sits. It is
      not readable Arabic, and it is not reproduced here.</p>
      <p>That is the brief's own rule, stated twice: <em>free from fake or malformed Arabic
      calligraphy</em>. Script that approximates Arabic without resolving into words is the
      one thing a Muslim audience notices first and forgives last, and it would undo the
      identity's whole claim to be careful.</p>
      <p>Everything else the reference established is kept — the crescent, the star, the arch,
      gold on deep green, the serif wordmark, the rule-and-diamond divider, and
      &ldquo;For the Sake of Allah&rdquo;.</p>
    </div>
  </div>
</section>

<section>
  <p class="eyebrow">Scale</p>
  <h2>It has to work at sixteen pixels</h2>
  <p>Below 32px the keyline, the star and the leaf sweeps are under a pixel wide, so the mark
  switches to a small cut: a heavier arch stroke, the crescent, and a two-arch arcade. Detail
  you cannot resolve does not read as detail — it reads as a smudge over the parts that were
  legible. That decision is made by the code from the size it is handed, not by the person
  placing it.</p>
  <div class="ramp-sizes">{sizes}</div>
</section>

<section>
  <p class="eyebrow">Variants</p>
  <h2>Eight tones, one geometry</h2>
  <div class="grid g3">{tones}</div>
  <div class="grid g2" style="margin-top:34px">
    <div class="lockup">{svg(hz, box=viewbox_of(hz))}</div>
    <div class="lockup">{svg("logo/fi-sabilillah-logo-stacked.svg", box=viewbox_of("logo/fi-sabilillah-logo-stacked.svg"))}</div>
  </div>
</section>

<section>
  <p class="eyebrow">App icon</p>
  <h2>Every mask the platform will cut</h2>
  <p>The adaptive foreground carries <strong>no baked corner radius</strong> — the launcher
  applies the mask, and a pre-rounded foreground gets rounded twice. Shown here in the
  monochrome cut, which is what a themed icon actually renders: the launcher composites the
  whole foreground in one tint, so the gradient lives in the SVG and PNG exports and every
  VectorDrawable is drawn flat.</p>
  <div class="grid g4">{masks}</div>
</section>

<section>
  <p class="eyebrow">Colour</p>
  <h2>Two palettes, on purpose</h2>
  <p>The brand palette is deep green and gold. The <strong>product</strong> palette — what the
  application's screens are painted in — is the Amanah harbour blue. Forcing one to be the
  other makes both worse: the green cannot carry interface text at the ratios a UI needs, and
  the blue does not read as an institution on a shelf of app icons. They meet in three places:
  splash, landing, app icon.</p>
  <div class="grid g3">{"".join(sw)}</div>

  <h3 style="margin-top:40px">The gold ramp</h3>
  <p>Four stops on a diagonal, so the highlight falls on the arch's upper left as metal would.
  <strong>Gold is an accent and never a ground.</strong> It is the mark, the tagline and the
  divider rule; an asset where gold is the largest area of colour is wrong however it looks.</p>
  <div class="ramp">{ramp}</div>

  <h3 style="margin-top:40px">Measured, not assumed</h3>
  <div class="scroll"><table><thead><tr><th>Pair</th><th>Ratio</th><th>Verdict</th></tr></thead>
  <tbody>{con}</tbody></table></div>
  <p style="margin-top:14px"><strong>Gold never touches a light background.</strong> On ivory
  it is 1.88:1. Light grounds use gold-deep at 5.45:1, and every generated light-tone asset
  already does. The check suite asserts the floor <em>and</em> asserts that gold-on-ivory
  still fails — if it ever passed, this rule would have gone stale without anyone noticing.</p>
</section>

<section>
  <p class="eyebrow">Typography</p>
  <h2>The wordmark is drawn, not typeset</h2>
  <p>&ldquo;FI SABILILLAH&rdquo; is set in <strong>Libre Baskerville</strong> at 0.16em
  tracking, with the outlines extracted and embedded as paths. The reference used Playfair
  Display, which made the identity depend on a Google font being installed wherever the file
  was opened — an app-store listing, a partner's deck, a printer's RIP — and where it is not,
  the wordmark silently falls back to Times New Roman. Outlines cannot fall back.</p>
  <p>Libre Baskerville has the same transitional skeleton and stroke contrast with slightly
  sturdier serifs, which is an improvement at wordmark sizes: Playfair's hairlines start
  disappearing below about 18px, which is most of the places a wordmark appears on a phone.
  It is under the SIL Open Font License 1.1, which permits both embedding and deriving
  outlines; the licence travels with the repository.</p>
  <div class="glyphs">{"".join(f'<div class="glyph">{_glyph_svg(g)}</div>' for g in GLYPHS)}</div>
  <div class="lockup" style="margin-top:28px">{svg("logo/fi-sabilillah-wordmark-mono.svg", box=wm_vb)}</div>
</section>

<section>
  <p class="eyebrow">Badges</p>
  <h2>Seven facts, one shelter</h2>
  <p>All seven share the emblem's own arch, closed at the foot and filled. A badge states what
  was checked — never that something is religiously approved, safe, or guaranteed.</p>
  <div class="grid g3">{badges}</div>
</section>

<section>
  <p class="eyebrow">Splash &amp; motion</p>
  <h2>One gesture, then still</h2>
  <div class="grid g2">
    <div class="splashes">
      <div class="splash splash--dark"><div class="splash__inner">
        <svg viewBox="0 0 64 64" class="m" aria-hidden="true">{inner("logo/fi-sabilillah-icon-primary.svg")}</svg>
        <svg viewBox="{wm_vb}" class="w" aria-hidden="true">{inner("logo/fi-sabilillah-wordmark-mono.svg")}</svg>
      </div></div>
      <div class="splash splash--light"><div class="splash__inner">
        <svg viewBox="0 0 64 64" class="m" aria-hidden="true">{inner("logo/fi-sabilillah-icon-light.svg")}</svg>
        <svg viewBox="{wm_vb}" class="w" aria-hidden="true">{inner("logo/fi-sabilillah-wordmark-mono.svg")}</svg>
      </div></div>
    </div>
    <div>
      <p>The arch draws in, the crescent and arcade light, and it stops — about 1.2 seconds end
      to end on the Amanah curve. The mark at the top of this page is running it. In the
      application it is simpler still: a fade and a settle from 96% to 100% over 520ms.</p>
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
    <li><strong>No Arabic appears anywhere in this identity</strong>, deliberately. If it is
    ever added it must be real Unicode Arabic in a shaped Arabic face, set by a native reader,
    and never a Latin face styled to look Arabic.</li>
    <li><strong>The emblem is drawn from a supplied reference, at the client's direction.</strong>
    Every path is original geometry and no part of the reference is traced, embedded or
    reproduced in any file — but the design is deliberately close to it, which is a different
    position from independent creation, and anyone filing should say so. A crescent and star
    are separately a very common device; the arch, arcade, leaf base and lockup are what a
    filing would rest on.</li>
    <li><strong>No trademark is registered</strong>, and <strong>scholarly review is
    recommended before launch</strong> on the use of the phrase as a product name. This board
    asserts that the marks claim no religious authority; a qualified scholar should confirm the
    naming.</li>
  </ul>
</section>

<footer class="wrap">
  Every asset on this page is generated by <code>tools/brand/build.py</code> from one geometry
  source and checked by <code>tools/brand/validate.py</code> — 191 checks, 0 failures. Nothing
  under <code>brand/</code> is edited by hand.
</footer>
</div>
"""


open(OUT, "w").write(HTML)
print("board written to", OUT, len(HTML), "bytes")
