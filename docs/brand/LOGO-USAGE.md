# Logo usage

*Which mark, where, and at what size. The guidelines say what the identity means; this says
what to reach for.*

## The decision, in one table

| Context | Use | Why |
| --- | --- | --- |
| App launcher | `ic_launcher.xml` (adaptive) | The system masks it. Never ship a pre-rounded foreground. |
| Play Store listing | `app-icons/android/play-store-512.png` | 512², square, no transparency. |
| App Store listing | `app-icons/ios/AppIcon-1024.png` | 1024², RGB with no alpha, or the upload is rejected. |
| Splash | Stacked lockup on `forest-deep` | The one moment the brand speaks before the product does. |
| Landing and sign-in | Horizontal lockup, in-product tone | Somebody arriving needs to know where they are. |
| Every other screen | Nothing | The application does not need to keep saying its own name. |
| Notification | Monochrome emblem, small cut | Android tints it flat; two colours become one blob. |
| Favicon | `logo/fi-sabilillah-favicon.svg`, plus the 16/32/48 PNGs | |
| Collapsed sidebar | Emblem, 24–32px, small cut | |
| Expanded sidebar | Horizontal lockup, emblem 32–40px | |
| Website header band | `banners/fi-sabilillah-website-header.svg` | 1600 × 320, scales to any width. |
| Open Graph / LinkedIn / Facebook | `fi-sabilillah-og-image.png` | 1200 × 630. |
| X / Twitter header | `banners/fi-sabilillah-twitter-header.png` | 1500 × 500. |
| Instagram | `banners/fi-sabilillah-banner-square.png` | 1080². |
| Signage, print, a deck's title slide | `banners/fi-sabilillah-banner-wide.svg` | 2400 × 800, vector. |
| Print, one colour | `…-icon-mono-black.svg` | Also engraving, embossing, stamps. |
| Silkscreen, embroidery, one plate | `…-icon-flat.svg` | The gradient will not survive these. |
| Foil, letterpress, a printed cover | `…-icon-presentation.svg` | The only asset with the inset gold hairline. |

## Sizes

| Size | What is drawn |
| --- | --- |
| **≥ 128px** | Everything: band, keyline, crescent, star, calligraphy, curled leaf sweeps. |
| **48–127px** | The same. The calligraphy reads as a block rather than as words, which is what the reference does at this size too. |
| **33–47px** | The same; check it against its background. |
| **≤ 32px** | Small cut: heavier band, no keyline, **no calligraphy**, enlarged crescent and star, uncurled sweeps. |
| **< 16px** | Do not. Use a text label. |

The switch at 32px is made by the code, not by the person placing the mark — `BrandEmblem` in
Compose and `FiSabilillahLogo` in React both decide it from the size they are given. A caller
asking for a horizontal lockup at 16px gets the icon instead, because a wordmark nobody can
read beside a mark nobody can see is worse than either.

**The calligraphy is the reason the threshold is not negotiable.** At 24px its strokes are a
fifth of a pixel. Illegible Arabic is the specific failure this identity is most careful
about, so below 32px there is none.

## Lockup proportions

The wordmark is **11.6 cap-heights wide** in Playfair Display at 0.17em. That number is
measured off the generated outlines, not guessed, and every layout below comes from it.

| Lockup | Wordmark width | Minimum overall width |
| --- | --- | --- |
| Horizontal | 6 × the emblem's height | 180px |
| Stacked | 2.9 × the emblem's height | 150px |

`BrandLockup` and `BrandLockupStacked` scale the wordmark to fit rather than clipping it, so
a caller who asks for an emblem too large for the screen gets a small lockup instead of a
cropped one.

## Clear space

One arch-stroke width on every side — 4 units on the 64-grid, 7.2% of the emblem's height.
Nothing enters it, including the wordmark and including the edge of a card.

## Backgrounds

| Background | Mark |
| --- | --- |
| `deep-green`, `forest-deep` | primary tone, gradient gold |
| `charcoal` | dark tone |
| `ivory`, white, light neutrals | light tone, drawn in `gold-deep` |
| `mineral` green, or anywhere gold would compete with adjacent gold | ivory tone |
| Amanah product surfaces | in-product tone |
| Photography | a plate behind the mark, or a monochrome cut. Never the mark alone. |
| Anything with a pattern | as above. |

**`gold` on a light background is 1.88:1.** It is the single easiest way to make this identity
look cheap and be unreadable at the same time. The light-tone assets already use `gold-deep`;
the risk is somebody picking the gold swatch by eye in a new document.

## Gold is an accent

Gold is the mark, the divider rule and the pillars line. It is never a ground, a fill behind
text, or the largest area of colour in an asset. If a layout has more gold in it than deep
green, it is wrong regardless of how it looks.

## The tagline's colour

"For the Sake of Allah" is `sage` on dark grounds and `gold-deep` on light ones. Sage is
4.42:1 on deep green, which clears AA for **large text only**. It is correct for the tagline,
which is always set at display size, and correct for nothing else in the system.

## RTL

The horizontal lockup mirrors as a unit: emblem to the right of the wordmark. The emblem
itself is **not** mirrored — it is symmetrical, and the Arabic inside it must never be
reversed.

## Regenerating

```console
$ python3 tools/brand/build.py     # writes every asset
$ python3 tools/brand/validate.py  # 369 checks over what it wrote
```

The six drawables under `androidApp/app/src/main/res/drawable/` are copies of generated
files. `validate.py` asserts they match byte for byte, so forgetting to re-copy them fails
the check rather than shipping a stale mark.
