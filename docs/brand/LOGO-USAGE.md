# Logo usage

*Which mark, where, and at what size. The guidelines say what the identity means; this says
what to reach for.*

## The decision, in one table

| Context | Use | Why |
| --- | --- | --- |
| App launcher | Adaptive icon (`ic_launcher.xml`) | The system masks it. Never ship a pre-rounded foreground. |
| Splash | Stacked lockup on `forest-deep` | The one moment the brand speaks before the product does. |
| Landing and sign-in | Horizontal lockup, in-product tone | Somebody arriving needs to know where they are. |
| Every other screen | Nothing | The application does not need to keep saying its own name. |
| Notification | Monochrome emblem, small cut | Android tints it flat; two colours become one blob. |
| Favicon | `fi-sabilillah-favicon.svg` (small cut) | 16px. |
| Collapsed sidebar | Emblem, 24–32px, small cut | |
| Expanded sidebar | Horizontal lockup, emblem 32–40px | |
| Store listing | `play-store-512.png`, `AppIcon-1024.png` | Square, no transparency, no baked corners. |
| Social card | `fi-sabilillah-og-image.png` | 1200 × 630. |
| Print, one colour | `…-icon-mono-black.svg` | Also correct for engraving, embossing and stamps. |
| Silkscreen, embroidery, single plate | `…-icon-flat.svg` | The gradient will not survive these; the flat cut is the same mark without it. |
| Cover, signage, a deck's title slide | `…-logo-horizontal-tagline.svg` | The full five lines. Only where there is room. |

## Sizes

| Size | What is drawn |
| --- | --- |
| **≥ 128px** | Full emblem: arch, keyline, crescent, star, three-arch arcade, leaf sweeps. |
| **48–127px** | The same. |
| **33–47px** | The same; check it against its background. |
| **≤ 32px** | Small cut: arch at the heavier 4.6 stroke, crescent, two-arch arcade. No keyline, no star, no leaves. Automatic. |
| **< 16px** | Do not. Use a text label. |

The switch at 32px is made by the code, not by the person placing the mark — `BrandEmblem`
in Compose and `FiSabilillahLogo` in React both decide it from the size they are given. A
caller asking for a horizontal lockup at 16px gets the icon instead, because a wordmark
nobody can read beside a mark nobody can see is worse than either.

## Lockup proportions

The wordmark is **12.4 cap-heights wide**. That is the number every layout decision below
comes from, and it is measured off the generated outlines rather than guessed — three
lockups had previously been laid out from an assumed scale and overflowed their own
canvases.

| Lockup | Wordmark width | Minimum overall width |
| --- | --- | --- |
| Horizontal | 6 × the emblem's height | 180px |
| Stacked | 2.9 × the emblem's height | 150px |

`BrandLockup` and `BrandLockupStacked` scale the wordmark to fit rather than clipping it,
so a caller who asks for an emblem too large for the screen gets a small lockup instead of
a cropped one.

## Clear space

One arch-stroke width on every side — 3.4 units on the 64-grid, 5.7% of the emblem's
height. Nothing enters it, including the wordmark and including the edge of a card.

## Backgrounds

| Background | Mark |
| --- | --- |
| `deep-green`, `forest-deep` | primary tone, gradient gold |
| `charcoal` | dark tone |
| `ivory`, white, light neutrals | light tone, drawn in `gold-deep` |
| A green plate where gold would compete with adjacent gold | ivory tone |
| Amanah product surfaces | in-product tone |
| Photography | a plate behind the mark, or a monochrome variant. Never the mark alone. |
| Anything with a pattern | as above. |

**`gold` on a light background is 1.88:1.** It is the single easiest way to make this
identity look cheap and be unreadable at the same time. The light-tone assets already use
`gold-deep`; the risk is somebody picking the gold swatch by eye in a new document.

## Gold is an accent

Gold is the mark, the tagline, and the divider rule. It is never a ground, a fill behind
text, or the largest area of colour in an asset. If a layout has more gold in it than deep
green, it is wrong regardless of how it looks.

## RTL

The horizontal lockup mirrors as a unit: emblem to the right of the wordmark. The emblem
itself is not mirrored — it is very nearly symmetrical, so mirroring changes nothing except
the chance of it being done inconsistently across surfaces.

## Regenerating

```console
$ python3 tools/brand/build.py     # writes every asset
$ python3 tools/brand/validate.py  # 191 checks over what it wrote
```

The six drawables under `androidApp/app/src/main/res/drawable/` are copies of generated
files. `validate.py` asserts they match byte for byte, so forgetting to re-copy them fails
the check rather than shipping a stale mark.
