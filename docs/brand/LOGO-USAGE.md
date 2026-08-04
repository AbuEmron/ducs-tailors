# Logo usage

*Which mark, where, and at what size. The guidelines say what the identity means; this
says what to reach for.*

## The decision, in one table

| Context | Use | Why |
| --- | --- | --- |
| App launcher | Adaptive icon | The system masks it. Never ship a pre-rounded foreground. |
| Splash | Stacked lockup on `sabil-green-deep` | The one moment the brand speaks before the product does. |
| Landing and sign-in | Horizontal lockup, in-product tone | Somebody arriving needs to know where they are. |
| Every other screen | Nothing | The application does not need to keep saying its own name. |
| Notification | Monochrome emblem, small cut | Android tints it flat; two colours become one blob. |
| Favicon | `fi-sabilillah-favicon.svg` (small cut) | 16px. |
| Collapsed sidebar | Emblem, 24–32px, small cut | |
| Expanded sidebar | Horizontal lockup, emblem 32px | |
| Store listing | `play-store-512.png`, `AppIcon-1024.png` | Square, no transparency, no baked corners. |
| Social card | `fi-sabilillah-og-image.png` | 1200 × 630. |
| Print, one colour | `…-monochrome.svg` | Also correct for engraving, embossing and stamps. |
| Foil or embossed presentation | `…-icon-gold.svg` | Presentation only. Never in an interface. |

## Sizes

| Size | What is drawn |
| --- | --- |
| **≥ 128px** | Full emblem: three arches, lit opening. |
| **48–127px** | The same. |
| **33–47px** | The same; check it against its background. |
| **≤ 32px** | Small cut: outer arch and lit opening, heavier stroke. Automatic. |
| **< 16px** | Do not. Use a text label. |

The switch at 32px is made by the code, not by the person placing the mark —
`BrandEmblem` in Compose and `FiSabilillahLogo` in React both decide it from the size
they are given. A caller asking for a horizontal lockup at 16px gets the icon instead,
because a wordmark nobody can read beside a mark nobody can see is worse than either.

## Clear space

One outer-arch leg width on every side — 5 units on the 64-grid, 8% of the emblem's
height. Nothing enters it, including the wordmark and including the edge of a card.

## Backgrounds

| Background | Mark |
| --- | --- |
| `sabil-green`, `sabil-green-deep`, `charcoal` | primary or dark tone, brass opening |
| `ivory`, white, light neutrals | light tone, `brass-deep` opening |
| Amanah product surfaces | in-product tone |
| Photography | a plate behind the mark, or the monochrome variant. Never the mark alone. |
| Anything with a pattern | as above. |

**Brass on a light background is 2.06:1.** It is the single easiest way to make this
identity look cheap and be unreadable at the same time. The light-tone assets already use
`brass-deep`; the risk is somebody picking the gold swatch by eye in a new document.

## RTL

The horizontal lockup mirrors as a unit: emblem to the right of the wordmark. The emblem
itself is not mirrored — it is a symmetrical arch, so mirroring changes nothing except
the chance of it being done inconsistently across surfaces.

## Regenerating

```console
$ python3 tools/brand/build.py     # writes every asset
$ python3 tools/brand/validate.py  # 149 checks over what it wrote
```
