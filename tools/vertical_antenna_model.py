"""Models for the Vertical Antenna Base (2026-11-18) and the tapered antenna poles that stand on it.

The base: a 2 px stone pad (smooth stone, like a slab), eight copper radials laid out over it to the edges, a white
ceramic insulator, and the loading coil (copper turns on a dark former) with its tap clip, up to a short aluminium
stub at the top of the block where the first pole sits. The tap clip sits on a different turn for each band
(blockstate tap=b10 / b20 / b40 / b80: one model each, the clip higher up the coil for the lower bands, as more of
the coil is used).

The poles on a base taper as they go up: antenna_pole_taper_0 (the bottom section, 3 px) to _6 (1.5 px), each with a
slightly wider collar at its foot where it slides into the one below, like a telescoping mast. They use the antenna
pole's own texture.

Writes the texture, the base models, the taper models, the base's item model and blockstate. The pole's blockstate is
generated (RadiocraftBlockstateProvider). Run from the repo root.
"""
import json
import os

from PIL import Image

ROOT = 'src/main/resources/assets/radiocraft'
NAME = 'vertical_antenna_base'
TAPS = ('b10', 'b20', 'b40', 'b80')

# Texture regions (16 x 16).
COPPER = (0, 0, 8, 8)      # Coil turns: copper bands with dark gaps.
FORMER = (8, 0, 12, 4)     # The coil's dark former.
CERAMIC = (12, 0, 16, 4)   # White insulator.
ALU = (8, 4, 12, 8)        # The aluminium stub and clip.
RADIAL = (12, 4, 16, 8)    # Copper radial wire.


def texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            px[x, y] = (196, 112, 64, 255) if y % 2 == 0 else (120, 62, 34, 255)
            if y % 2 == 0 and x in (1, 2):
                px[x, y] = (230, 150, 96, 255)  # A highlight down the turns.
    for y in range(4):
        for x in range(8, 12):
            px[x, y] = (42, 40, 38, 255)
        for x in range(12, 16):
            px[x, y] = (232, 230, 222, 255) if (x + y) % 3 else (214, 212, 204, 255)
    for y in range(4, 8):
        for x in range(8, 12):
            px[x, y] = (196, 200, 204, 255) if x != 8 else (160, 164, 168, 255)
        for x in range(12, 16):
            px[x, y] = (186, 104, 58, 255) if (x + y) % 2 else (158, 86, 46, 255)
    os.makedirs(ROOT + '/textures/block', exist_ok=True)
    img.save(ROOT + '/textures/block/' + NAME + '.png')


def faces(texture, region, skip=()):
    return {f: {'uv': list(region), 'texture': texture} for f in ('north', 'east', 'south', 'west', 'up', 'down') if f not in skip}


def box(name, frm, to, f, rotation=None):
    e = {'name': name, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': f}
    if rotation:
        e['rotation'] = rotation
    return e


def base(tap):
    stone = {'north': {'uv': [0, 14, 16, 16], 'texture': '#stone'}, 'south': {'uv': [0, 14, 16, 16], 'texture': '#stone'},
             'east': {'uv': [0, 14, 16, 16], 'texture': '#stone'}, 'west': {'uv': [0, 14, 16, 16], 'texture': '#stone'},
             'up': {'uv': [0, 0, 16, 16], 'texture': '#stone_top'}, 'down': {'uv': [0, 0, 16, 16], 'texture': '#stone_top', 'cullface': 'down'}}
    for f in ('north', 'south', 'east', 'west'):
        stone[f]['cullface'] = f
    e = [box('pad', [0, 0, 0], [16, 2, 16], stone)]
    # Eight radials over the pad: four along the axes, four on the diagonals (turned 45 degrees about the middle).
    r = faces('#all', RADIAL)
    e.append(box('radial_ns', [7.75, 2, 0], [8.25, 2.4, 16], r))
    e.append(box('radial_ew', [0, 2, 7.75], [16, 2.4, 8.25], r))
    e.append(box('radial_d1', [7.75, 2, -3], [8.25, 2.4, 19], r, {'angle': 45, 'axis': 'y', 'origin': [8, 2, 8]}))
    e.append(box('radial_d2', [-3, 2, 7.75], [19, 2.4, 8.25], r, {'angle': 45, 'axis': 'y', 'origin': [8, 2, 8]}))
    # The hub where the radials meet, the insulator, the coil and its former, the stub.
    e.append(box('hub', [6.5, 2, 6.5], [9.5, 3, 9.5], faces('#all', ALU)))
    e.append(box('insulator', [7, 3, 7], [9, 5, 9], faces('#all', CERAMIC)))
    e.append(box('former', [7.25, 5, 7.25], [8.75, 13, 8.75], faces('#all', FORMER)))
    e.append(box('coil', [6.25, 5.5, 6.25], [9.75, 12.5, 9.75], {f: {'uv': list(COPPER), 'texture': '#all'} for f in ('north', 'east', 'south', 'west')}))
    e.append(box('coil_caps', [6.25, 5.5, 6.25], [9.75, 12.5, 9.75], {f: {'uv': list(FORMER), 'texture': '#all'} for f in ('up', 'down')}))
    e.append(box('stub', [6.75, 12.5, 6.75], [9.25, 16, 9.25], faces('#all', ALU)))
    # The tap clip: a small band on the coil, higher for the lower bands, with a lead down to the hub on the north side.
    clip_y = {'b10': 6.0, 'b20': 7.5, 'b40': 9.5, 'b80': 11.5}[tap]
    e.append(box('tap_clip', [6, clip_y, 6], [10, clip_y + 0.75, 10], faces('#all', ALU)))
    e.append(box('tap_lead', [7.75, 3, 5.75], [8.25, clip_y, 6.25], faces('#all', ALU)))
    return {'credit': 'tools/vertical_antenna_model.py', 'parent': 'block/block', 'ambientocclusion': False,
            'textures': {'all': 'radiocraft:block/' + NAME, 'stone': 'minecraft:block/smooth_stone_slab_side',
                         'stone_top': 'minecraft:block/smooth_stone', 'particle': 'minecraft:block/smooth_stone'},
            'elements': e}


def taper(section):
    w = 3.0 - section * 0.25  # 3 px at the bottom to 1.5 px at the top.
    lo, hi = 8 - w / 2, 8 + w / 2
    pole_faces = {'north': {'uv': [0, 0, 2, 16], 'texture': '#0'}, 'east': {'uv': [2, 0, 4, 16], 'texture': '#0'},
                  'south': {'uv': [4, 0, 6, 16], 'texture': '#0'}, 'west': {'uv': [6, 0, 8, 16], 'texture': '#0'},
                  'up': {'uv': [8, 0, 10, 2], 'texture': '#0'}, 'down': {'uv': [8, 2, 10, 4], 'texture': '#0'}}
    e = [box('pole', [lo, 0, lo], [hi, 16, hi], pole_faces)]
    if section > 0:  # The collar where it slides into the section below (that one's width).
        cw = w + 0.25
        e.append(box('collar', [8 - cw / 2, 0, 8 - cw / 2], [8 + cw / 2, 1, 8 + cw / 2],
                     {f: {'uv': [8, 4, 10, 5], 'texture': '#0'} for f in ('north', 'east', 'south', 'west', 'up', 'down')}))
    return {'credit': 'tools/vertical_antenna_model.py', 'parent': 'block/block',
            'textures': {'0': 'radiocraft:block/antenna_pole', 'particle': 'radiocraft:block/antenna_pole'}, 'elements': e}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    texture()
    for tap in TAPS:
        write(ROOT + '/models/block/' + NAME + '_' + tap + '.json', base(tap))
    for section in range(7):
        write(ROOT + '/models/block/antenna_pole_taper_%d.json' % section, taper(section))
    write(ROOT + '/models/item/' + NAME + '.json', {'parent': 'radiocraft:block/' + NAME + '_b20'})
    write(ROOT + '/blockstates/' + NAME + '.json',
          {'variants': {'tap=' + tap: {'model': 'radiocraft:block/' + NAME + '_' + tap} for tap in TAPS}})
    print('wrote', NAME)
