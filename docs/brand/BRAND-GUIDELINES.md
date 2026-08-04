# Brand guidelines

*Fi Sabilillah. The mark, what it means, and the rules that keep it meaning that.*

---

## 1. What the identity says

`fi sabilillah` means **in the path of Allah**. The mark draws that and nothing else.

Three arches on one baseline, each inside the last, the innermost filled. It reads as
looking down a colonnade towards a lit opening: a way through a sheltering structure.

| Element | Carries |
| --- | --- |
| The outer arch | Shelter. A threshold, a place that holds people. |
| The nested arches | Passage. Depth, and a way that continues past where you stand. |
| The lit opening | What the way is walked towards. |

**What it deliberately does not contain.** No crescent, no star, no dome, no minaret, and
no calligraphy. The reference this identity was developed from used all five. Three of
them are somebody else's flag, one is a building this platform does not own, and the
fifth — an approximation of Arabic script that does not resolve into readable words at
any size — is the one a Muslim audience will notice first and forgive last.

**What it must never be used to claim.** Religious authority, a scholarly ruling,
guaranteed reward, charitable registration, or that any organisation displaying it has
been vetted by anyone. The badges say what has been checked; the logo says only whose
application this is.

---

## 2. Colour

Two palettes, on purpose. The **brand** palette below is what the logo, the app icon, the
splash and the store listing are painted in. The **product** palette in
`androidApp/.../ui/theme/Color.kt` is what the application's screens are painted in, and
it is the Amanah harbour blue. They meet in three places: splash, sidebar mark, app icon.

Forcing one to be the other makes both worse. The green is too dark to carry interface
text at the contrast ratios a UI needs; the harbour blue does not read as an institution
at 60px on a shelf of app icons.

### Brand

| Token | HEX | RGB | HSL | CMYK | Usage |
| --- | --- | --- | --- | --- | --- |
| `sabil-green` | `#0B3A30` | (11, 58, 48) | 167.2° 68.1% 13.5% | 81/0/17/77 | The primary. Deep enough to sit under brass without the brass glaring. |
| `sabil-green-deep` | `#072A22` | (7, 42, 34) | 166.3° 71.4% 9.6% | 83/0/19/84 | Splash and immersive backgrounds only. Not a surface colour. |
| `mineral-green` | `#1F5A4C` | (31, 90, 76) | 165.8° 48.8% 23.7% | 66/0/16/65 | Raised surfaces on a green ground; the second step of depth. |
| `sage` | `#7FA98C` | (127, 169, 140) | 138.6° 19.6% 58.0% | 25/0/17/34 | Supporting tint. Dividers and quiet states on green. |
| `brass` | `#C6A664` | (198, 166, 100) | 40.4° 46.2% 58.4% | 0/16/49/22 | The accent, on dark grounds only. 5.44:1 on sabil-green. |
| `brass-deep` | `#836427` | (131, 100, 39) | 39.8° 54.1% 33.3% | 0/24/70/49 | The accent for light grounds. Brass on ivory is 2.06:1 and illegible; |
| `ivory` | `#F4F1E8` | (244, 241, 232) | 45.0° 35.3% 93.3% | 0/1/5/4 | The light ground and the mark on dark grounds. |
| `charcoal` | `#0C1013` | (12, 16, 19) | 205.7° 22.6% 6.1% | 37/16/0/93 | One-colour dark. Print, foil blocking, and the monochrome mark. |
| `stone` | `#64727C` | (100, 114, 124) | 205.0° 10.7% 43.9% | 19/8/0/51 | Neutral text and secondary rules. |

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
| `neutral` | `#64727C` | (100, 114, 124) | Everything unweighted. |

### Contrast, measured

| Pair | Ratio | Verdict |
| --- | --- | --- |
| ivory on sabil green | **11.19:1** | passes AA for text |
| brass on sabil green | **5.44:1** | passes AA for text |
| brass on ivory | **2.06:1** | FAILS — never use this pair |
| brass deep on ivory | **4.87:1** | passes AA for text |
| sabil green on ivory | **11.19:1** | passes AA for text |
| ivory on charcoal | **16.92:1** | passes AA for text |

**The rule that matters: brass never touches a light background.** `brass` on `ivory` is
2.06:1, which is illegible. Light grounds use `brass-deep` at 4.87:1. Every generated
light-tone asset already does this; the failure mode is a designer picking the gold swatch
by eye in a new document.

---

## 3. Construction and clear space

The emblem is drawn on a 64-unit grid. Its ink occupies 37 × 46.5 of that grid; it does
not fill the square, which is why lockups align to its **optical centre** (y = 27.25) and
not to its bounding box.

- **Clear space** on all four sides: the width of one outer arch leg — 5 units on the
  64-grid, or 8% of the emblem's height. Nothing enters it, including the wordmark.
- **Minimum size, emblem:** 16px. Below that it stops resolving.
- **Minimum size, horizontal lockup:** 120px wide. Below that use the emblem.
- **Simplification is automatic, not optional.** Below 32px the middle arch closes against
  the other two. Every implementation — Compose, React, the generated SVGs — switches to
  the small cut at that threshold on its own.

---

## 4. Typography

| Role | Face | Why |
| --- | --- | --- |
| Wordmark | **Custom geometric capitals**, drawn as vector paths | Seven unique glyphs (F I S A B L H). No font dependency in any SVG, no licensing question about embedding a commercial face in a trademark, and terminals tuned to the emblem. |
| Brand headings | An elegant serif, if one is licensed | Optional. The wordmark does not depend on it. |
| Interface | The Amanah type scale in `ui/theme/Type.kt` | Already in place; the brand does not override it. |

The reference set its wordmark in Playfair Display. That made the identity depend on a
Google font being present at render time, and Playfair's hairlines vanish below roughly
18px — which is most of the places a wordmark appears on a phone.

**Arabic.** No Arabic appears in this identity. If it is ever added it must be real
Unicode Arabic in a shaped Arabic typeface, set by a native reader, and never a Latin face
styled to look Arabic. No Quranic text is to be used in any mark, at any size, for any
variant.

---

## 5. Variants

| Variant | File | Use |
| --- | --- | --- |
| Icon, primary | `brand/logo/fi-sabilillah-icon-primary.svg` | Default. |
| Icon, light ground | `…-icon-light.svg` | Ivory and white backgrounds. |
| Icon, dark ground | `…-icon-dark.svg` | Charcoal and photographic backgrounds. |
| Icon, gold | `…-icon-gold.svg` | Presentation and foil only. Not for UI. |
| Icon, in-product | `…-icon-amanah.svg` | Inside the application, on Amanah surfaces. |
| Icon, monochrome | `…-icon-monochrome.svg`, `…-monochrome-white.svg` | One colour, print, engraving, stamps. |
| Icon, small | `…-icon-small.svg` | Below 32px. Also the favicon. |
| Horizontal | `…-logo-horizontal.svg` (+ `-light`, `-mono`) | Headers, letterheads, the landing screen. |
| Stacked | `…-logo-stacked.svg` (+ `-light`) | Splash, square placements, print covers. |
| Wordmark | `…-wordmark.svg` (+ `-light`, `-mono`) | Where the emblem already appears nearby. |

---

## 6. Badges

Seven, sharing one container: the emblem's own arch, closed at the foot. A badge is the
same shelter holding one fact about one thing.

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

Forbidden without exception: spinning, flashing, looping, confetti, particles, glow
pulses, and any animation that plays on completing an act of service. A record of service
is between a person and their Lord; animating it turns it into a performance.

Under reduced motion the animation is **removed, not shortened**. `Motion.scaled()`
returns zero and the mark is simply present.

---

## 8. Incorrect use

Do not:

- stretch, squash or rotate the mark;
- recolour it outside the tones in §5;
- put brass on a light background — it is 2.06:1;
- add a mosque, dome, minaret, crescent or star to it;
- add Quranic text, or any Arabic, to any variant;
- place it over busy photography without a plate behind it;
- alter the spacing between the arches, or the arch weights, individually;
- add a glow, bevel, drop shadow or metallic gradient;
- use the full lockup as a repeating element in the interface;
- imply with it that anything is religiously approved, or that the platform vouches for a
  person or an organisation.

---

## 9. Accessibility

- Every generated SVG carries `role="img"` and an `aria-label`; inline instances that are
  decorative pass `title={null}` / `contentDescription = null` and are hidden from
  assistive technology instead of announced twice.
- The mark never relies on colour to be understood: the monochrome variant is a first-class
  export, not a fallback.
- The wordmark is drawn as paths and does not scale with the system font setting. That is
  correct for a logo and correct nowhere else — every sentence in the application still
  scales.
- Contrast ratios are measured and recorded in §2 rather than assumed.
- RTL: the horizontal lockup mirrors as a whole (emblem to the right of the wordmark). The
  emblem itself is **not** mirrored — it is a symmetrical arch, and mirroring it changes
  nothing except the risk of it being done inconsistently.

---

## 10. Legal

- Trademark status: **none registered.** `Fi Sabilillah` is a common Arabic phrase and may
  be difficult to register as a word mark in some jurisdictions; the emblem is original
  work and is the stronger candidate. Take advice before filing.
- The emblem, wordmark and badges are original vector geometry created for this project.
  No stock artwork, no licensed typeface outlines and no third-party material is embedded
  in any asset.
- The reference image supplied at the start of this work was used as directional input
  only. No part of it is traced, embedded or reproduced in any exported file.
- **Scholarly review is recommended before public launch**, on two points specifically:
  the use of the phrase `fi sabilillah` as a product name, and the absence of any claim of
  religious authority in the marks and badges. This document asserts the second; a
  qualified scholar should confirm the first.

---

## 11. Regenerating

Nothing under `brand/` is edited by hand.

```console
$ python3 tools/brand/build.py
```

Geometry lives in `tools/brand/geometry.py`, letterforms in `letters.py`, colour in
`tokens.py`. Change one of those and every export moves together — which is the only way
"all exports use consistent geometry" survives a second revision.
