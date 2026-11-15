"""Builds the satellite tracking antenna's GeckoLib model (2026-11-12): the microwave dish's own dish, feed and arm
(copied bone for bone from geo/block/satellite_dish.geo.json, so the two read as one family) on a larger az/el
mount: a square plinth, a bearing ring and a turntable that carries the azimuth motor, the elevation drive and a
counterweight, so its turning shows.

Bones the renderer drives (SatelliteTrackerRenderer):
  turntable - turned about Y to the azimuth (the plinth, `base`, stays put)
  lnb_arm   - the dish's arm, tilted to the elevation as the microwave dish's is

Writes geo/block/satellite_tracker.geo.json and textures/block/satellite_tracker.png (the dish's 64x64 texture with
the mount's swatches added in its empty lower half).

    python tools/satellite_tracker_model.py
"""
import copy
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft')
DISH_GEO = os.path.join(ROOT, 'geo/block/satellite_dish.geo.json')
DISH_TEX = os.path.join(ROOT, 'textures/block/satellite_dish.png')

# ---------------------------------------------------------------- texture
# Swatches below the dish's art (it uses rows 0..30): name -> (x, y, w, h, colour)
SW = {
    'plinth':   (0, 40, 8, 8, (88, 90, 92)),      # dark galvanised base
    'plinth_t': (8, 40, 8, 8, (104, 106, 108)),   # its top, with bolts drawn on
    'ring':     (16, 40, 4, 4, (52, 54, 56)),     # the bearing ring
    'turn':     (20, 40, 4, 4, (196, 198, 200)),  # the turntable, painted pale like the dish
    'turn_t':   (24, 40, 8, 8, (188, 190, 192)),  # its top, with a dark rim line
    'motor':    (32, 40, 4, 4, (40, 42, 44)),     # motor housings
    'weight':   (36, 40, 4, 4, (120, 122, 124)),  # the counterweight
    'box':      (40, 40, 4, 4, (70, 84, 96)),     # the power junction box, blue-grey
    'lamp':     (44, 40, 2, 2, (90, 200, 90)),    # its power lamp
    'cable':    (46, 40, 2, 2, (24, 24, 26)),     # cable
    'brass':    (48, 40, 2, 2, (176, 140, 70)),   # the coax connector
}
rnd = random.Random(4417)
img = Image.open(DISH_TEX).convert('RGBA')
assert img.size == (64, 64)
for name, (x0, y0, w, h, colour) in SW.items():
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            j = rnd.randint(-5, 5)
            img.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in colour) + (255,))
# Bolts on the plinth's top corners, a rim line round the turntable's top.
for bx, by in ((9, 41), (14, 41), (9, 46), (14, 46)):
    img.putpixel((bx, by), (60, 62, 64, 255))
for i in range(8):
    for x, y in ((24 + i, 40), (24 + i, 47), (24, 40 + i), (31, 40 + i)):
        img.putpixel((x, y), (130, 132, 134, 255))
img.save(os.path.join(ROOT, 'textures/block/satellite_tracker.png'))


# ---------------------------------------------------------------- geometry
def face(name):
    x, y, w, h, _ = SW[name]
    return {'uv': [x, y], 'uv_size': [w, h]}


def cube(origin, size, side, top=None, rotation=None, pivot=None):
    top = top or side
    c = {'origin': [round(v, 4) for v in origin], 'size': [round(v, 4) for v in size],
         'uv': {'north': face(side), 'east': face(side), 'south': face(side), 'west': face(side),
                'up': face(top), 'down': face(side)}}
    if rotation:
        c['rotation'] = rotation
        c['pivot'] = pivot or [0, origin[1] + size[1] / 2, 0]
    return c


def centred(w, y, h, d, side, top=None, rotation=None):
    return cube([-w / 2, y, -d / 2], [w, h, d], side, top, rotation, [0, y + h / 2, 0] if rotation else None)


base = [
    centred(14, 0, 1.5, 14, 'plinth', 'plinth_t'),                       # the plinth
    cube([-7.5, 0, -7.5], [2, 0.75, 2], 'plinth'), cube([5.5, 0, -7.5], [2, 0.75, 2], 'plinth'),   # its feet
    cube([-7.5, 0, 5.5], [2, 0.75, 2], 'plinth'), cube([5.5, 0, 5.5], [2, 0.75, 2], 'plinth'),
    centred(10.5, 1.5, 1, 10.5, 'ring'), centred(10.5, 1.5, 1, 10.5, 'ring', rotation=[0, 45, 0]),  # the bearing ring
    cube([4.25, 1.5, 4.25], [2.5, 2.25, 2.5], 'box'),                    # the power junction box, on a corner
    cube([5.0, 3.0, 4.0], [1, 0.5, 0.25], 'lamp'),                        # its lamp
    cube([-6.75, 1.5, 4.5], [1.25, 1.25, 1.25], 'brass'),                # the coax connector, on another
]
turntable = [
    centred(9.5, 2.5, 2, 9.5, 'turn', 'turn_t', rotation=[0, 22.5, 0]),   # the turntable, an octagon
    centred(9.5, 2.5, 2, 9.5, 'turn', 'turn_t', rotation=[0, 67.5, 0]),
    centred(3.5, 4.5, 1.25, 3.5, 'motor'),                                # the post's sleeve
    centred(2.5, 5.75, 3.0, 2.5, 'turn'),                                 # the post, up to the elevation drive
    cube([-1.5, 7.5, 1.0], [3.0, 2.25, 1.75], 'motor'),                   # the elevation drive, behind the arm's foot
    cube([-5.25, 4.5, -2.0], [2.75, 3.5, 4.0], 'motor'),                  # the azimuth motor, to one side
    cube([-4.75, 8.0, -1.0], [1.75, 0.5, 2.0], 'motor'),                  # its cap
    cube([2.75, 4.5, -1.5], [2.25, 2.5, 3.0], 'weight'),                 # the counterweight, opposite
    cube([-2.5, 4.5, 2.25], [5.0, 0.5, 0.5], 'cable'),                   # a cable loop round the post
    cube([-2.5, 4.5, -2.75], [5.0, 0.5, 0.5], 'cable'),
]

dish = json.load(open(DISH_GEO))
bones = dish['minecraft:geometry'][0]['bones']
by_name = {b['name']: b for b in bones}
# How far the dish's arm stands above where the dish model has it: room to tip down to the horizon over the turntable.
LIFT = 3.0
# The dish's arm and everything on it, shifted so its post stands on the turntable's centre (the dish model's post
# is at x 2.07451, z 1.42404; the mount below turns the copy as the dish model's root does, about that centre).
SHIFT = [-2.07451, LIFT, -1.42404]
keep = set()


def collect(name):
    keep.add(name)
    for b in bones:
        if b.get('parent') == name:
            collect(b['name'])


collect('lnb_arm')
copied = []
for b in bones:
    if b['name'] not in keep:
        continue
    nb = copy.deepcopy(b)
    if 'pivot' in nb:
        nb['pivot'] = [round(p + s, 5) for p, s in zip(nb['pivot'], SHIFT)]
    for c in nb.get('cubes', []):
        c['origin'] = [round(p + s, 5) for p, s in zip(c['origin'], SHIFT)]
        if 'pivot' in c:
            c['pivot'] = [round(p + s, 5) for p, s in zip(c['pivot'], SHIFT)]
    if nb['name'] == 'lnb_arm':
        nb['parent'] = 'mount'
    copied.append(nb)

geo = {
    'format_version': '1.12.0',
    'minecraft:geometry': [{
        'description': {'identifier': 'geometry.satellite_tracker', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
        'bones': [
            {'name': 'satellite_tracker', 'pivot': [0, 0, 0]},
            {'name': 'base', 'parent': 'satellite_tracker', 'pivot': [0, 0, 0], 'cubes': base},
            {'name': 'turntable', 'parent': 'satellite_tracker', 'pivot': [0, 0, 0], 'cubes': turntable},
            {'name': 'mount', 'parent': 'turntable', 'pivot': [0, 0, 0], 'rotation': [0, -90, 0]},
        ] + copied,
    }],
}
# No two cubes of a bone share a top or bottom plane (2026-11-13): the crossed squares of the plinth's ring and the
# turntable's octagon, and the dish hub's segments, overlapped in the same planes and z-fought. Each repeat of a
# cube's height band is grown a hair more than the one before, so their faces sit apart.
for bone in geo['minecraft:geometry'][0]['bones']:
    seen = {}
    for c in bone.get('cubes', []):
        band = (round(c['origin'][1], 2), round(c['size'][1], 2))
        n = seen.get(band, 0)
        seen[band] = n + 1
        if n:
            c['origin'] = [c['origin'][0], round(c['origin'][1] - 0.01 * n, 4), c['origin'][2]]
            c['size'] = [c['size'][0], round(c['size'][1] + 0.02 * n, 4), c['size'][2]]
with open(os.path.join(ROOT, 'geo/block/satellite_tracker.geo.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump(geo, f, indent='\t')
    f.write('\n')
print('wrote satellite tracker geo (%d bones) and texture' % len(geo['minecraft:geometry'][0]['bones']))
