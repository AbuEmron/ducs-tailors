import sys, cairosvg
sys.path.insert(0,'tools/brand')
from geometry import *
OUT='/tmp/claude-0/-home-user-ducs-tailors/e7e653ec-f9b8-540b-9ccb-8e94f8f26067/scratchpad'

def emblem(fg, accent, hands=True, small=False):
    w = STROKE_SMALL if small else STROKE
    strokes = f'<path d="{ARCH_OUTER}"/>'
    if not small:
        strokes += f'<path d="{ARCH_MIDDLE}"/>'
    if hands:
        strokes += f'<path d="{HAND_L}"/><path d="{HAND_R}"/>'
    inner = SMALL_INNER if small else ARCH_INNER
    return (f'<g fill="none" stroke="{fg}" stroke-width="{w}" stroke-linecap="round" '
            f'stroke-linejoin="round">{strokes}</g><path d="{inner}" fill="{accent}"/>')

specs=[("full/dark","#0B3A30","#F4F1E8","#C6A664",True,False),
       ("full/gold","#0B3A30","#C6A664","#F4F1E8",True,False),
       ("full/ivory","#F4F1E8","#0B3A30","#C6A664",True,False),
       ("icon","#0B3A30","#F4F1E8","#C6A664",False,False),
       ("mono","#FFFFFF","#0C1013","#0C1013",False,False),
       ("small","#0B3A30","#F4F1E8","#C6A664",False,True)]
tiles=[]
for i,(l,bg,fg,ac,hd,sm) in enumerate(specs):
    x=i*80
    tiles.append(f'<rect x="{x}" y="0" width="72" height="72" rx="16" fill="{bg}"/>'
                 f'<g transform="translate({x+4},4)">{emblem(fg,ac,hd,sm)}</g>')
svg=(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {80*len(specs)} 80">'
     f'<rect width="100%" height="100%" fill="#191c1e"/>'+"".join(tiles)+'</svg>')
cairosvg.svg2png(bytestring=svg.encode(), write_to=f'{OUT}/emblem_sheet.png', output_width=1680)

# the sizes that decide it, laid out together at true scale on one strip
strip=[]; x=8
for px in (16,24,32,48,64,96):
    strip.append((px,x)); x+=px+14
W=x
parts=[f'<rect width="{W}" height="112" fill="#191c1e"/>']
for px,xx in strip:
    sm = px<=32
    parts.append(f'<g transform="translate({xx},{8+(96-px)//2}) scale({px/64})">'
                 f'<rect width="64" height="64" rx="14" fill="#0B3A30"/>'
                 + emblem("#F4F1E8","#C6A664",hands=False,small=sm) + '</g>')
cairosvg.svg2png(bytestring=(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} 112">'
    + "".join(parts) + '</svg>').encode(), write_to=f'{OUT}/sizes.png', output_width=W*3)
print("rendered")
