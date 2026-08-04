# Brand guidelines

*Fi Sabilillah. The mark, what it means, and the rules that keep it meaning that.*

---

## 1. What the identity says

`fi sabilillah` means **in the path of Allah**. The identity is built from the reference
supplied for this work, redrawn as exact geometry: a gold ogee arch on deep green, holding
a crescent and star above a three-arch arcade, with two leaf sweeps beneath.

| Element | Carries |
| --- | --- |
| The ogee arch | Shelter, and the threshold you cross to enter it. The silhouette the whole identity is recognised by. |
| The inner keyline | Depth. One arch drawn twice reads as a doorway rather than an outline. |
| The crescent and star | The community the platform is for, stated plainly rather than hinted at. |
| The three-arch arcade | A colonnade: a way that continues past where you stand. Three because a colonnade is a repetition, and two does not repeat. |
| The leaf sweeps | Growth from what is sheltered. They also give the mark a base, so it sits rather than floats. |

### The one departure from the reference

The reference carries a block of ornamental script in the position the arcade now occupies.
That block is not readable Arabic, and it is not reproduced here.

This is not a stylistic preference — it is the brief's own rule, stated twice: *"free from
fake or malformed Arabic calligraphy"* and *"do not generate malformed Arabic"*. Script
that approximates Arabic without resolving into words is the single thing a Muslim
audience notices first and forgives last, and it would undo the identity's whole claim to
be careful. The arcade fills the space with something that means something.

Everything else the reference established — the crescent, the star, the arch, the gold on
deep green, the serif wordmark, the rule-and-diamond divider, "For the Sake of Allah" — is
kept.

**What the mark must never be used to claim.** Religious authority, a scholarly ruling,
guaranteed reward, charitable registration, or that any organisation displaying it has been
vetted by anyone. The badges say what has been checked; the logo says only whose
application this is.

---

## 2. Colour

Two palettes, on purpose. The **brand** palette below is what the logo, the app icon, the
splash and the store listing are painted in. The **product** palette in
`androidApp/.../ui/theme/Color.kt` is what the application's screens are painted in, and it
is the Amanah harbour blue. They meet in three places: splash, landing, app icon.

Forcing one to be the other makes both worse. The green is too dark to carry interface text
at the contrast a UI needs; the harbour blue does not read as an institution at 60px on a
shelf of app icons.

### Brand

| Token | HEX | RGB | HSL | CMYK | Usage |
| --- | --- | --- | --- | --- | --- |
| `deep-green` | `#0F3D34` | (15, 61, 52) | 168.3° 60.5% 14.9% | 75/0/15/76 | The ground. Every lockup, every icon plate, the store listing. |
| `forest-deep` | `#0A2A24` | (10, 42, 36) | 168.8° 61.5% 10.2% | 76/0/14/84 | Splash and immersive backgrounds only. Not a surface colour. |
| `mineral` | `#1C574A` | (28, 87, 74) | 166.8° 51.3% 22.5% | 68/0/15/66 | Raised surfaces on a green ground; the second step of depth. |
| `sage` | `#6BAA7D` | (107, 170, 125) | 137.1° 27.0% 54.3% | 37/0/26/33 | Supporting tint. Dividers and quiet states on green. |
| `gold` | `#D4AF37` | (212, 175, 55) | 45.9° 64.6% 52.4% | 0/17/74/17 | The accent, on dark grounds only. 5.75:1 on deep green. |
| `gold-deep` | `#7A5E15` | (122, 94, 21) | 43.4° 70.6% 28.0% | 0/23/83/52 | The accent for light grounds, where `gold` is illegible. |
| `ivory` | `#F5F2E9` | (245, 242, 233) | 45.0° 37.5% 93.7% | 0/1/5/4 | The light ground, and the wordmark on dark grounds. |
| `charcoal` | `#0D1115` | (13, 17, 21) | 210.0° 23.5% 6.7% | 38/19/0/92 | One-colour dark. Print, foil blocking, the monochrome mark. |
| `stone` | `#68757A` | (104, 117, 122) | 196.7° 8.0% 44.3% | 15/4/0/52 | Neutral text and secondary rules. |

### The gold ramp

The emblem's gold is a four-stop gradient, not a flat fill:

| Stop | HEX | At |
| --- | --- | --- |
| Highlight | `#F0DFA8` | 0% |
| Light | `#E0C264` | 30% |
| Core | `#D4AF37` | 66% |
| Shadow | `#A8811F` | 100% |

Run on a diagonal (`x1 .15 y1 0 → x2 .6 y2 1`) so the highlight falls on the arch's upper
left, as metal would. **The gradient is for the SVG and PNG exports only.** Every
VectorDrawable is drawn in flat `gold`, because Android's themed-icon layer composites the
whole foreground in one tint and a two-tone foreground becomes a solid blob under it.

### Gold is an accent

The brief's rule, and it is enforced by construction: gold is never the ground. It appears
as the mark, the tagline, the divider rule and nothing else. Deep green carries every
surface; ivory carries every word. An asset where gold is the largest area of colour is
wrong, whatever it looks like.

### Functional

Restated from the product palette so nobody reading only this sheet invents a second
success green.

| Token | HEX | RGB | Usage |
| --- | --- | --- | --- |
| `success` | `#3E7A5E` | (62, 122, 94) | Confirmed, settled, attended. |
| `warning` | `#AF7A2A` | (175, 122, 42) | Needs attention. Never used for danger. |
| `danger` | `#A04B55` | (160, 75, 85) | Restriction, dispute, refusal. |
| `learning` | `#6C638E` | (108, 99, 142) | Learning surfaces, and nothing else. |
| `service` | `#246F6C` | (36, 111, 108) | Service and safeguard-affirmative. |
| `neutral` | `#68757A` | (104, 117, 122) | Everything unweighted. |

### Contrast, measured

| Pair | Ratio | Verdict |
| --- | --- | --- |
| ivory on deep green | **10.80:1** | passes AA for text |
| gold on deep green | **5.75:1** | passes AA for text |
| gold on ivory | **1.88:1** | FAILS — never use this pair |
| gold-deep on ivory | **5.45:1** | passes AA for text |
| deep green on ivory | **10.80:1** | passes AA for text |
| ivory on charcoal | **16.93:1** | passes AA for text |
| sage on deep green | **4.42:1** | large text and non-text only |

**The rule that matters: gold never touches a light background.** `gold` on `ivory` is
1.88:1, which is illegible. Light grounds use `gold-deep` at 5.45:1. Every generated
light-tone asset already does this; the failure mode is a designer picking the gold swatch
by eye in a new document. `tools/brand/validate.py` asserts both the floor and the
failure — if `gold` on `ivory` ever *passed*, that would mean the palette moved and this
rule went stale, so the check fails in that direction too.

---

## 3. Construction and clear space

The emblem is drawn on a 64-unit grid. Its ink occupies 42.8 × 59.3 of that grid — the arch
and leaves together run wider and lower than the square suggests — so every lockup and icon
aligns to the **measured ink box**, not the viewBox. An icon laid out on the viewBox pushes
the leaf tips into the launcher's corner radius.

- **Clear space** on all four sides: the width of the arch stroke at display size — 3.4
  units on the 64-grid, or 5.7% of the emblem's height. Nothing enters it, including the
  wordmark.
- **Minimum size, emblem:** 16px. Below that it stops resolving.
- **Minimum size, horizontal lockup:** 180px wide. The wordmark is 12.4 cap-heights long;
  below that the letterforms close up. Use the emblem alone instead.
- **Simplification is automatic, not optional.** Below 32px the mark switches to its small
  cut: the inner keyline, the star and the two leaf sweeps are dropped, the arch stroke
  thickens from 3.4 to 4.6, and the arcade goes from three arches to two. Every
  implementation — Compose, React, the generated SVGs — makes the switch on its own, because
  leaving it to the caller means it gets got wrong exactly once, in a notification, at 24dp.

---

## 4. Typography

| Role | Face | Why |
| --- | --- | --- |
| Wordmark | **Libre Baskerville**, outlines extracted as paths | The reference's character — a high-contrast transitional serif — without the reference's dependency. |
| Tagline and pillars | The same face, smaller, in gold | One face throughout; the hierarchy comes from size and colour. |
| Interface | The Amanah type scale in `ui/theme/Type.kt` | Already in place; the brand does not override it. |

The reference set its wordmark in Playfair Display. Two changes, both technical:

**The outlines are embedded, not referenced.** A font-referencing SVG depends on that font
being installed wherever it is opened — an app-store listing, a partner's deck, a printer's
RIP — and where it is not, the wordmark silently falls back to Times New Roman. Outlines
cannot fall back.

**Libre Baskerville rather than Playfair.** Same transitional skeleton and stroke contrast,
slightly sturdier serifs, which is an improvement at wordmark sizes: Playfair's hairlines
start disappearing below about 18px, which is most of the places a wordmark appears on a
phone. Libre Baskerville is under the SIL Open Font License 1.1; the licence travels with
the repository at `brand/licences/LibreBaskerville-OFL.txt`. The OFL permits embedding and
permits deriving outlines. What it forbids — selling the font software, reusing the reserved
name — does not apply to a logotype.

Set in capitals at 0.16em tracking, matching the reference's spacing.

**Arabic.** No Arabic appears in this identity, by the brief's own rule and for the reason
in §1. If it is ever added it must be real Unicode Arabic in a shaped Arabic typeface, set
by a native reader, and never a Latin face styled to look Arabic. No Quranic text is to be
used in any mark, at any size, for any variant.

---

## 5. Variants

Eighteen logo files, plus the icon exports.

| Variant | File | Use |
| --- | --- | --- |
| Icon, primary | `brand/logo/fi-sabilillah-icon-primary.svg` | Default. Gradient gold on deep green. |
| Icon, flat | `…-icon-flat.svg` | Where a gradient will not survive: silkscreen, embroidery, a single-plate print. |
| Icon, dark | `…-icon-dark.svg` | Charcoal and photographic grounds. |
| Icon, light | `…-icon-light.svg` | Ivory and white grounds. Draws in `gold-deep`. |
| Icon, ivory | `…-icon-ivory.svg` | Ivory mark on the green plate, where gold would compete. |
| Icon, in-product | `…-icon-amanah.svg` | Inside the application, on Amanah surfaces. |
| Icon, monochrome | `…-icon-mono-black.svg`, `…-icon-mono-white.svg` | One colour. Print, engraving, stamps, fax-grade reproduction. |
| Icon, small | `…-icon-small.svg` | Below 32px. |
| Favicon | `…-favicon.svg` | The small cut again, named for where it goes. |
| Horizontal | `…-logo-horizontal.svg` (+ `-light`, `-mono`) | Headers, letterheads, the landing screen. |
| Horizontal + tagline | `…-logo-horizontal-tagline.svg` | Where there is room for the full five lines: covers, signage, the presentation board. |
| Stacked | `…-logo-stacked.svg` (+ `-light`) | Splash, square placements, print covers. |
| Wordmark | `…-wordmark.svg` (+ `-light`, `-mono`) | Where the emblem already appears nearby. |

---

## 6. Badges

Seven, sharing one container: the emblem's own arch, closed at the foot and filled. A badge
is the same shelter carrying one fact about one thing.

| Badge | Glyph | States |
| --- | --- | --- |
| `verified-organization` | A check | Registration documents were seen by a named reviewer. |
| `learning` | An open book | A learning offering. Not an endorsement of its content. |
| `service` | The emblem's arch | A service opportunity. |
| `community` | Three equal dots | A community space. Unranked, deliberately. |
| `safety` | A lock | Privacy and restriction surfaces. |
| `donation` | Given into cupped hands | A campaign that has passed financial review. |
| `family-introduction` | Two parties under an arch | A formal introduction, with a guardian present. |

A badge states what was checked. It never states that something is religiously approved,
safe, or guaranteed.

---

## 7. Motion

One gesture, 520ms, then still: the mark fades up and settles from 96% to 100% on
`cubic-bezier(.2, .8, .2, 1)` — the same curve the rest of the Amanah system uses.

Forbidden without exception: spinning, flashing, looping, confetti, particles, glow pulses,
shimmer passes across the gold, and any animation that plays on completing an act of
service. A record of service is between a person and their Lord; animating it turns it into
a performance.

Under reduced motion the animation is **removed, not shortened**. `Motion.scaled()` returns
zero and the mark is simply present.

---

## 8. Incorrect use

Do not:

- stretch, squash or rotate the mark;
- recolour it outside the tones in §5;
- put `gold` on a light background — it is 1.88:1;
- make gold the ground, or the largest area of colour in any asset;
- apply the gold gradient to a VectorDrawable, a monochrome cut, or anything that will be
  tinted;
- add a mosque, dome or minaret to it — the arch is the building this identity owns;
- add Arabic, ornamental script, or anything resembling either, to any variant;
- place it over busy photography without a plate behind it;
- alter the arcade's arch count, the leaf sweeps, or the stroke weights individually;
- add a glow, bevel, drop shadow or a second metallic gradient;
- use the full lockup as a repeating element in the interface;
- imply with it that anything is religiously approved, or that the platform vouches for a
  person or an organisation.

---

## 9. Accessibility

- Every generated SVG carries `role="img"` and an `aria-label`; decorative instances pass
  `title={null}` / `contentDescription = null` and are hidden from assistive technology
  rather than announced twice.
- The mark never relies on colour to be understood: the monochrome variants are first-class
  exports, not fallbacks.
- The wordmark is drawn as paths and does not scale with the system font setting. That is
  correct for a logo and correct nowhere else — every sentence in the application still
  scales.
- Contrast ratios are measured and recorded in §2, and asserted in `validate.py`, rather
  than assumed.
- RTL: the horizontal lockup mirrors as a whole (emblem to the right of the wordmark). The
  emblem itself is **not** mirrored — it is very nearly symmetrical, and mirroring it changes
  nothing except the risk of it being done inconsistently.

---

## 10. Legal

- Trademark status: **none registered.** `Fi Sabilillah` is a common Arabic phrase and may
  be difficult to register as a word mark in some jurisdictions; the emblem is the stronger
  candidate. Take advice before filing.
- **The emblem is drawn from a reference image supplied for this work, at that supplier's
  direction.** Every path is original geometry — computed circle intersections for the
  crescent, an arc sweep for the arcade, cubic curves for the arch and leaves — and no part
  of the reference is traced, embedded or reproduced in any exported file. But the design
  is deliberately close to it, which is a different position from independent creation, and
  anyone filing should say so.
- A crescent and star are, separately, a very common device. That helps nothing on
  distinctiveness: the arch, the arcade, the leaf base and the lockup are what a filing
  would rest on.
- The wordmark uses Libre Baskerville outlines under the SIL Open Font License 1.1. No
  stock artwork and no commercially licensed typeface is embedded in any asset.
- **Scholarly review is recommended before public launch**, on two points specifically: the
  use of the phrase `fi sabilillah` as a product name, and the absence of any claim of
  religious authority in the marks and badges. This document asserts the second; a qualified
  scholar should confirm the first.

---

## 11. Regenerating

Nothing under `brand/` is edited by hand.

```console
$ python3 tools/brand/build.py      # 67 files
$ python3 tools/brand/validate.py   # 191 checks
```

Geometry lives in `tools/brand/geometry.py`, letterforms in `letters.py`, colour in
`tokens.py`. Change one of those and every export moves together — which is the only way
"all exports use consistent geometry" survives a second revision.

The Android app's `res/drawable` holds copies of six generated files, because that is where
`aapt` looks. `validate.py` asserts they are byte-identical to the generated originals, so a
build that forgets to re-copy them fails rather than shipping a stale mark.
