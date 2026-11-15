"""Builds the EB-VHF eggbeater antenna's art (2026-11-12), after the maintainer's photo of an M2 eggbeater with its
reflector kit: a two block tall mast, eight reflector radials round its foot, a black balun at the top of the lower
block and two crossed loops (one copper, one aluminium) on the upper block.

The loops are 16-sided rings of thin boxes: block models only turn an element by 22.5 degree steps about one axis,
so the ring's vertices sit at 11.25 + 22.5k degrees, which puts every segment's run at a multiple of 22.5 degrees.

Writes models/block/eggbeater_antenna_lower.json, _upper.json, models/item/eggbeater_antenna.json,
blockstates/eggbeater_antenna.json and textures/block/eggbeater_antenna.png (16x16, UVs are pixels).

    python tools/eggbeater_model.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft')
TEX = 'radiocraft:block/eggbeater_antenna'

# Texture swatches, 4x4 pixels each: name -> (x0, y0)
SW = {'alu': (0, 0), 'copper': (4, 0), 'black': (8, 0), 'grey': (12, 0), 'alu_dark': (0, 4), 'hub': (4, 4)}
COLOURS = {'alu': (196, 200, 206), 'copper': (184, 112, 52), 'black': (28, 28, 30), 'grey': (160, 166, 172),
           'alu_dark': (140, 146, 152), 'hub': (110, 114, 120)}

rnd = random.Random(144)
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for name, (x0, y0) in SW.items():
    for y in range(y0, y0 + 4):
        for x in range(x0, x0 + 4):
            j = rnd.randint(-6, 6)
            img.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in COLOURS[name]) + (255,))
os.makedirs(os.path.join(ROOT, 'textures/block'), exist_ok=True)
img.save(os.path.join(ROOT, 'textures/block/eggbeater_antenna.png'))


def uv(name):
    x0, y0 = SW[name]
    return [x0 + 0.5, y0 + 0.5, x0 + 3.5, y0 + 3.5]


def box(frm, to, swatch, rotation=None, faces=('north', 'east', 'south', 'west', 'up', 'down')):
    el = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to],
          'faces': {f: {'uv': uv(swatch), 'texture': '#0'} for f in faces}}
    if rotation:
        el['rotation'] = rotation
    return el


def rod(cx, cy, cz, length, t, swatch, along, axis=None, angle=0.0):
    half = {'x': (length / 2, t / 2, t / 2), 'y': (t / 2, length / 2, t / 2), 'z': (t / 2, t / 2, length / 2)}[along]
    el = box([cx - half[0], cy - half[1], cz - half[2]], [cx + half[0], cy + half[1], cz + half[2]], swatch)
    if angle:
        el['rotation'] = {'angle': angle, 'axis': axis, 'origin': [cx, cy, cz]}
    return el


def loop(plane, centre_y, radius, t, swatch):
    """A 16-sided ring in the x-y plane (z = 8) or the z-y plane (x = 8)."""
    out = []
    seg = 2 * radius * math.sin(math.radians(11.25)) + t * 0.6  # A little long, so the corners close.
    mid_r = radius * math.cos(math.radians(11.25))
    for k in range(16):
        phi = 22.5 * k                      # The segment's middle, round the ring.
        a = (phi + 90.0) % 180.0            # Its run, 0..180 in the ring's own (across, up) coordinates.
        if a > 90.0:
            a -= 180.0                      # -90..90
        c = mid_r * math.cos(math.radians(phi))
        cy = centre_y + mid_r * math.sin(math.radians(phi))
        if plane == 'xy':
            cx, cz = 8 + c, 8
            # About +z a turn takes +x towards +y: a rod along x turned by a runs at a; along y, at 90 + angle.
            if -45.0 <= a <= 45.0:
                out.append(rod(cx, cy, cz, seg, t, swatch, 'x', 'z', a))
            else:
                out.append(rod(cx, cy, cz, seg, t, swatch, 'y', 'z', a - 90.0 if a > 0 else a + 90.0))
        else:
            cx, cz = 8, 8 + c
            # About +x a turn takes +y towards +z: in (z, y) a rod along z turned by b runs at -b; along y, at 90 - b.
            if -45.0 <= a <= 45.0:
                out.append(rod(cx, cy, cz, seg, t, swatch, 'z', 'x', -a))
            else:
                out.append(rod(cx, cy, cz, seg, t, swatch, 'y', 'x', 90.0 - a if a > 0 else -90.0 - a))
    for el in out:
        if el.get('rotation', {}).get('angle') == 0:
            del el['rotation']
    return out


def lower_elements(dy=0.0):
    els = [
        box([7.5, 0 + dy, 7.5], [8.5, 16 + dy, 8.5], 'alu'),                  # the mast
        box([6.5, 1.25 + dy, 6.5], [9.5, 3.25 + dy, 9.5], 'hub'),             # the radials' hub
        box([6.75, 10.5 + dy, 6.75], [9.25, 16 + dy, 9.25], 'black'),         # the balun
        box([7.25, 9.75 + dy, 7.25], [8.75, 10.5 + dy, 8.75], 'alu_dark'),    # its clamp
    ]
    # Eight radials, two per rod through the hub: along x, along z, and both turned 45 degrees.
    els.append(rod(8, 2.25 + dy, 8, 30, 0.5, 'alu', 'x'))
    els.append(rod(8, 2.25 + dy, 8, 30, 0.5, 'alu', 'z'))
    els.append(rod(8, 2.25 + dy, 8, 30, 0.5, 'alu', 'x', 'y', 45.0))
    els.append(rod(8, 2.25 + dy, 8, 30, 0.5, 'alu', 'z', 'y', 45.0))
    return els


def upper_elements(dy=0.0):
    els = [box([7.5, 0 + dy, 7.5], [8.5, 15.25 + dy, 8.5], 'alu'),             # the mast
           box([7.25, 15.25 + dy, 7.25], [8.75, 16 + dy, 8.75], 'alu_dark')]  # its cap, where the loops meet
    els += loop('xy', 8.0 + dy, 7.6, 0.45, 'copper')
    els += loop('zy', 8.0 + dy, 7.6, 0.45, 'grey')
    return els


def model(elements, extra=None):
    m = {'credit': 'Made with tools/eggbeater_model.py (an M2-style eggbeater with reflector radials)',
         'render_type': 'minecraft:cutout', 'texture_size': [16, 16],
         'textures': {'0': TEX, 'particle': TEX}, 'elements': elements}
    if extra:
        m.update(extra)
    return m


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent='\t')
        f.write('\n')


write('models/block/eggbeater_antenna_lower.json', model(lower_elements()))
write('models/block/eggbeater_antenna_upper.json', model(upper_elements()))
# The item: both halves stacked (lower at y 0..16, upper at 16..32), shrunk to fit.
write('models/item/eggbeater_antenna.json', model(lower_elements() + upper_elements(16.0), {'display': {
    'gui': {'rotation': [25, -45, 0], 'translation': [0, -3.5, 0], 'scale': [0.42, 0.42, 0.42]},
    'ground': {'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'translation': [0, -3, 0], 'scale': [0.45, 0.45, 0.45]},
    'thirdperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1.75, 0], 'scale': [0.25, 0.25, 0.25]},
    'thirdperson_lefthand': {'rotation': [0, 45, 0], 'translation': [0, 1.75, 0], 'scale': [0.25, 0.25, 0.25]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]},
    'firstperson_lefthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]}}}))

# Blockstates: as the J-pole's (the loops are symmetric, so facing only turns the radials' clamp), with coax stubs.
parts = []
for half in ('lower', 'upper'):
    for facing, y in (('north', 90), ('south', 270), ('east', 180), ('west', 0)):
        parts.append({'when': {'half': half, 'facing': facing}, 'apply': {'model': f'radiocraft:block/eggbeater_antenna_{half}', 'y': y}})
for side, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    apply = {'model': 'radiocraft:block/antenna_coax'}
    if y:
        apply['y'] = y
    parts.append({'when': {'half': 'lower', side: 'true'}, 'apply': apply})
write('blockstates/eggbeater_antenna.json', {'multipart': parts})
print('wrote eggbeater antenna model, item, blockstate and texture')
