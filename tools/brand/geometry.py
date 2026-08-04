"""
Fi Sabilillah — brand geometry.

One source of truth for every exported asset. Every SVG, VectorDrawable and PNG in
`brand/` is generated from the constants below, which is the only way the brief's
requirement that "all exports use consistent geometry" can actually be true rather than
merely intended.

THE EMBLEM, IN WORDS

A pointed arch, open at the foot. Inside it a tapered way rises from the threshold and
narrows as it goes, the way a road narrows towards the horizon. Above the way, a single
point of light. Beneath the arch, two curves lift towards each other without touching.

`fi sabilillah` means *in the path of Allah*. The reference image reached for the generic
signifiers of Islamic design -- crescent, star, dome, calligraphy -- and never drew the
one thing the name actually says. The path is the mark.

WHAT EACH PART CARRIES
  arch    shelter, threshold, a place that holds people
  way     the sabil itself; service as a road walked rather than a status held
  light   what the road is walked towards. A point, not a star: a star is a symbol
          somebody else already owns, and this is meant to read as light.
  hands   two curves that can be read as cupped hands, as leaves, or as a cradle. They
          do not touch, because the gap is where the person being helped stands.

WHAT IS DELIBERATELY ABSENT
  No crescent. No star. No dome. No minaret. No calligraphy, real or otherwise -- the
  reference's interior lettering does not resolve into readable Arabic at any size, and
  an unreadable approximation of sacred script is worse than none.
"""

# ── The emblem, on a 64x64 grid ──────────────────────────────────────────────
#
# 64 rather than 24 or 100 because every icon size that matters (16, 32, 64, 128,
# 512, 1024) is an integer multiple or divisor of it, so nothing lands on a half
# pixel at the sizes people actually see.

VIEWBOX = 64
STROKE = 5.0
STROKE_SMALL = 6.0

# THE WAY IS A PASSAGE
#
# Three arches on one baseline, each inside the last, the innermost filled. It
# reads as looking down a colonnade towards a lit opening: a way through a
# sheltering structure, which is what `fi sabilillah` says and what two earlier
# drafts failed to draw.
#
# Draft one put a tapered stem and a dot inside the arch and produced a lowercase
# "i". Draft two replaced them with three receding road markings and produced a
# hamburger menu. Both were legible; both meant something else. This one has no
# competing reading because a nested arch is not a glyph in any alphabet.

ARCH_OUTER = "M16 48 L16 26 C16 18 23 11 32 6.5 C41 11 48 18 48 26 L48 48"
ARCH_MIDDLE = "M23.5 48 L23.5 31 C23.5 26 27 21.5 32 19 C37 21.5 40.5 26 40.5 31 L40.5 48"

# Filled rather than stroked: the far opening is light, not another wall.
ARCH_INNER = ("M28.7 48 L28.7 34.2 C28.7 31.2 30.1 28.7 32 27.4 "
              "C33.9 28.7 35.3 31.2 35.3 34.2 L35.3 48 Z")

# Two curves that lift and do not meet. Cupped hands, leaves, or a cradle,
# depending on who is looking. The gap between them is where the person being
# helped stands, and it is 7 units wide so that it survives being drawn at 24px.
HAND_L = "M14.5 52.5 C18 58 23 60.5 28.5 61"
HAND_R = "M49.5 52.5 C46 58 41 60.5 35.5 61"

# Below 32px the middle arch closes up against the other two. The small mark
# drops it and keeps the outer arch and the lit opening, which is the whole
# sentence in two words.
SMALL_INNER = ("M27.9 48 L27.9 33.4 C27.9 30 29.6 27.1 32 25.6 "
               "C34.4 27.1 36.1 30 36.1 33.4 L36.1 48 Z")
