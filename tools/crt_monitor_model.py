"""Builds the External CRT Monitor's art (2026-11-12): a vintage studio CRT monitor after the maintainer's photo (a charcoal
broadcast monitor, logo left off). One full block facing north:

- a charcoal case with a shallow carrying well sunk in its top;
- a front bezel standing 2 units proud of the case, thin at the sides (1.25) and top (1.5), a little taller at the
  chin (1.75), so a wall of displays reads as one picture cut by bezels yet still looks vintage. Its inside faces
  are the bezel's depth;
- the tube's glass recessed 2 units behind the bezel face: SCREEN_* below is where CrtMonitorRenderer draws the
  picture (in front of the glass by a hair) with its CRT artifacts;
- a small green power lamp on the chin (full bright).

Texture: 64x64, 4 texture pixels per model unit, so UVs are pixels / 4.

    python tools/crt_monitor_model.py
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft')
TEX = 'radiocraft:block/external_crt_monitor'

# The picture area on the north face, in model units (the renderer's constants must match).
SCREEN_X0, SCREEN_X1, SCREEN_Y0, SCREEN_Y1 = 1.25, 14.75, 1.75, 14.5
SCREEN_Z = 2.0          # The glass face.
BEZEL_DEPTH = 2.0       # The bezel stands from z 0 to 2; the case runs 2 to 16.

CASE = (50, 50, 53)
CASE_LIGHT = (66, 66, 70)
CASE_DARK = (36, 36, 39)
WELL = (30, 30, 33)
BEZEL = (44, 44, 47)
BEZEL_EDGE = (72, 72, 76)
INNER = (24, 24, 26)
GLASS = (78, 92, 90)
LED = (90, 230, 120)

# name: (x0, y0, x1, y1) in texture pixels
R = {
    'case': (0, 0, 32, 32),
    'case_side': (32, 0, 64, 32),
    'bezel_h': (0, 32, 64, 40),   # top and chin fronts (wide)
    'bezel_v': (0, 40, 8, 64),    # side fronts (tall)
    'inner': (8, 40, 16, 64),     # the bezel's inside faces (its depth)
    'well': (16, 40, 48, 56),     # the carrying well's floor
    'glass': (48, 40, 64, 56),
    'led': (16, 56, 20, 60),
    'vent': (20, 56, 64, 64),     # the back's vent slots
}

rnd = random.Random(1980)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
p = img.load()


def fill(region, colour, jitter=3, fn=None):
    x0, y0, x1, y1 = R[region]
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = (fn(x - x0, y - y0, x1 - x0, y1 - y0) if fn else None) or colour
            j = rnd.randint(-jitter, jitter)
            p[x, y] = tuple(max(0, min(255, v + j)) for v in c) + (255,)


# The case: fine plastic texture, lit along its top edge.
fill('case', CASE, fn=lambda x, y, w, h: CASE_LIGHT if y == 0 else CASE_DARK if y == h - 1 else None)
fill('case_side', CASE, fn=lambda x, y, w, h: CASE_LIGHT if y == 0 else CASE_DARK if x in (0, w - 1) else None)
# Bezel fronts: a highlight on the outer top edge, a shadow where it turns inward.
fill('bezel_h', BEZEL, 2, lambda x, y, w, h: BEZEL_EDGE if y == 0 else None)
fill('bezel_v', BEZEL, 2, lambda x, y, w, h: BEZEL_EDGE if x == 0 else None)
fill('inner', INNER, 2, lambda x, y, w, h: (34, 34, 37) if x < 2 else None)
fill('well', WELL, 2, lambda x, y, w, h: CASE_DARK if min(x, y, w - 1 - x, h - 1 - y) == 0 else None)


def glass(x, y, w, h):
    # Dark grey-green tube, a darker vignette to the corners and a soft window reflection at the top left.
    dx, dy = (x + 0.5) / w - 0.5, (y + 0.5) / h - 0.5
    d = (dx * dx + dy * dy) ** 0.5
    k = 1.0 - 0.55 * d
    c = tuple(int(v * k) for v in GLASS)
    if x < w * 0.4 and y < h * 0.25 and (x + 2 * y) % 7 == 0:
        c = tuple(min(255, v + 7) for v in c)
    return c


fill('glass', GLASS, 1, glass)
fill('led', LED, 4)
fill('vent', CASE, 2, lambda x, y, w, h: WELL if y % 3 == 1 and 2 <= x < w - 2 else None)
os.makedirs(os.path.join(ROOT, 'textures/block'), exist_ok=True)
img.save(os.path.join(ROOT, 'textures/block/external_crt_monitor.png'))


def uv(region, w=None, h=None):
    """A region's UVs; with w/h (in model units) the face takes just that much of the region from its top left."""
    x0, y0, x1, y1 = R[region]
    if w is not None:
        x1 = min(x1, x0 + w * 4)
    if h is not None:
        y1 = min(y1, y0 + h * 4)
    return [x0 / 4, y0 / 4, x1 / 4, y1 / 4]


def box(name, frm, to, faces, extra=None):
    el = {'name': name, 'from': frm, 'to': to,
          'faces': {f: {'uv': u, 'texture': '#0', **({'cullface': c} if c else {})} for f, (u, c) in faces.items()}}
    if extra:
        el.update(extra)
    return el


FULL = {'neoforge_data': {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}}
elements = [
    # The case behind the bezel, with the well sunk 1 unit into its top (rims all round it).
    box('case', [0, 0, 2], [16, 15, 16], {
        'south': (uv('vent'), 'south'), 'east': (uv('case_side'), 'east'), 'west': (uv('case_side'), 'west'),
        'down': (uv('case'), 'down'), 'up': (uv('well'), None)}),
    box('rim_front', [0, 15, 2], [16, 16, 4], {'up': (uv('case', 16, 2), 'up'), 'north': (uv('bezel_h', 16, 1), None),
                                               'south': (uv('inner', 2, 1), None), 'east': (uv('case_side', 2, 1), 'east'),
                                               'west': (uv('case_side', 2, 1), 'west')}),
    box('rim_back', [0, 15, 14], [16, 16, 16], {'up': (uv('case', 16, 2), 'up'), 'south': (uv('case', 16, 1), 'south'),
                                                'north': (uv('inner', 2, 1), None), 'east': (uv('case_side', 2, 1), 'east'),
                                                'west': (uv('case_side', 2, 1), 'west')}),
    box('rim_left', [0, 15, 4], [1.5, 16, 14], {'up': (uv('case', 1.5, 10), 'up'), 'west': (uv('case_side', 10, 1), 'west'),
                                                'east': (uv('inner', 2, 1), None)}),
    box('rim_right', [14.5, 15, 4], [16, 16, 14], {'up': (uv('case', 1.5, 10), 'up'), 'east': (uv('case_side', 10, 1), 'east'),
                                                   'west': (uv('inner', 2, 1), None)}),
    # The bezel, 2 units deep: its fronts face north, its inside faces show its depth round the glass.
    box('bezel_left', [0, 0, 0], [SCREEN_X0, 16, BEZEL_DEPTH], {
        'north': (uv('bezel_v', SCREEN_X0, 16), 'north'), 'east': (uv('inner', BEZEL_DEPTH, 16), None),
        'west': (uv('case_side', 2, 16), 'west'), 'up': (uv('case', SCREEN_X0, 2), 'up'), 'down': (uv('case', SCREEN_X0, 2), 'down')}),
    box('bezel_right', [SCREEN_X1, 0, 0], [16, 16, BEZEL_DEPTH], {
        'north': (uv('bezel_v', 16 - SCREEN_X1, 16), 'north'), 'west': (uv('inner', BEZEL_DEPTH, 16), None),
        'east': (uv('case_side', 2, 16), 'east'), 'up': (uv('case', 16 - SCREEN_X1, 2), 'up'), 'down': (uv('case', 16 - SCREEN_X1, 2), 'down')}),
    box('bezel_top', [SCREEN_X0, SCREEN_Y1, 0], [SCREEN_X1, 16, BEZEL_DEPTH], {
        'north': (uv('bezel_h', SCREEN_X1 - SCREEN_X0, 16 - SCREEN_Y1), 'north'), 'down': (uv('inner', BEZEL_DEPTH, 6), None),
        'up': (uv('case', 13.5, 2), 'up')}),
    box('bezel_chin', [SCREEN_X0, 0, 0], [SCREEN_X1, SCREEN_Y0, BEZEL_DEPTH], {
        'north': (uv('bezel_h', SCREEN_X1 - SCREEN_X0, SCREEN_Y0), 'north'), 'up': (uv('inner', BEZEL_DEPTH, 6), None),
        'down': (uv('case', 13.5, 2), 'down')}),
    # The tube's glass, recessed behind the bezel.
    box('glass', [SCREEN_X0, SCREEN_Y0, SCREEN_Z - 0.01], [SCREEN_X1, SCREEN_Y1, SCREEN_Z], {'north': (uv('glass'), None)}),
    # The power lamp on the chin, at the right as you face it (low x is the viewer's right on a north face).
    box('power_lamp', [2.15, 0.6, -0.1], [2.75, 1.1, 0], {'north': (uv('led'), None)}, FULL),
]

model = {'credit': 'Made with tools/crt_monitor_model.py (a vintage studio CRT monitor)', 'parent': 'block/block',
         'texture_size': [64, 64], 'textures': {'0': TEX, 'particle': TEX}, 'elements': elements}
os.makedirs(os.path.join(ROOT, 'models/block'), exist_ok=True)
with open(os.path.join(ROOT, 'models/block/external_crt_monitor.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump(model, f, indent='\t')
    f.write('\n')
print('wrote external_crt_monitor model and texture')
