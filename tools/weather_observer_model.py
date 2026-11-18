"""Placeholder model and texture for the Weather Observer (2026-11-18, ModelDesignBounty): a small grey data box with
a dark front panel, a green SEND lamp, a white label strip and a cable gland at the back.

Drawn facing north (the front panel on the north face). Writes the texture, the two block models (lamp off and lit),
the item model and the blockstate (facing x sending). Run from the repo root.
"""
import json
import os

from PIL import Image

ROOT = 'src/main/resources/assets/radiocraft'
NAME = 'weather_observer'


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# Texture regions on the 16 x 16 sheet.
CASE = (0, 0, 8, 8)       # Light grey enclosure.
PANEL = (8, 0, 16, 8)     # The dark front panel.
LABEL = (0, 8, 8, 10)     # White label strip.
LAMP_OFF = (8, 8, 10, 10)
LAMP_ON = (10, 8, 12, 10)
GLAND = (12, 8, 16, 12)   # Black cable gland.


def texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            px[x, y] = (178, 182, 184, 255) if (x + y) % 5 else (166, 170, 172, 255)
            if x in (0, 7) or y in (0, 7):
                px[x, y] = (150, 154, 156, 255)
        for x in range(8, 16):
            px[x, y] = (54, 58, 62, 255) if (x + y) % 4 else (46, 50, 54, 255)
    for y in range(8, 10):
        for x in range(8):
            px[x, y] = (232, 232, 226, 255) if x % 3 else (120, 120, 120, 255)  # A label with a line of print.
        for x in range(8, 10):
            px[x, y] = (30, 70, 34, 255)
        for x in range(10, 12):
            px[x, y] = (110, 255, 120, 255)
    for y in range(8, 12):
        for x in range(12, 16):
            px[x, y] = (24, 24, 26, 255) if (x - 12, y - 8) not in ((0, 0), (3, 0), (0, 3), (3, 3)) else (0, 0, 0, 0)
    os.makedirs(ROOT + '/textures/block', exist_ok=True)
    img.save(ROOT + '/textures/block/' + NAME + '.png')


def face(region, cull=None):
    f = {'uv': list(region), 'texture': '#all'}
    if cull:
        f['cullface'] = cull
    return f


def box(name, frm, to, faces):
    return {'name': name, 'from': frm, 'to': to, 'faces': faces}


def model(lit):
    lamp = LAMP_ON if lit else LAMP_OFF
    case_faces = {f: face(CASE) for f in ('east', 'south', 'west', 'up')}
    case_faces['down'] = face(CASE, 'down')
    case_faces['north'] = face(CASE)
    elements = [
        box('case', [3, 0, 4], [13, 5, 13], case_faces),
        box('panel', [3.5, 0.5, 3.5], [12.5, 4.5, 4], {'north': face(PANEL), 'east': face(PANEL), 'west': face(PANEL),
                                                       'up': face(PANEL), 'down': face(PANEL)}),
        box('label', [5, 2.5, 3.4], [10, 3.5, 3.5], {'north': face(LABEL)}),
        box('lamp', [10.5, 2.5, 3.2], [11.5, 3.5, 3.5], {'north': face(lamp), 'east': face(lamp), 'west': face(lamp),
                                                         'up': face(lamp), 'down': face(lamp)}),
        box('gland', [7, 1.5, 13], [9, 3.5, 14.5], {f: face(GLAND) for f in ('north', 'east', 'south', 'west', 'up', 'down')}),
    ]
    out = {'credit': 'tools/weather_observer_model.py (placeholder, see doc/ModelDesignBounty.md)', 'parent': 'block/block',
           'textures': {'all': 'radiocraft:block/' + NAME, 'particle': 'radiocraft:block/' + NAME}, 'elements': elements}
    if lit:
        out['elements'][3]['light_emission'] = 12
    return out


def blockstate():
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for sending in ('false', 'true'):
            v = {'model': 'radiocraft:block/' + NAME + ('_sending' if sending == 'true' else '')}
            if y:
                v['y'] = y
            variants['facing=%s,sending=%s' % (facing, sending)] = v
    return {'variants': variants}


if __name__ == '__main__':
    texture()
    write(ROOT + '/models/block/' + NAME + '.json', model(False))
    write(ROOT + '/models/block/' + NAME + '_sending.json', model(True))
    write(ROOT + '/models/item/' + NAME + '.json', {'parent': 'radiocraft:block/' + NAME})
    write(ROOT + '/blockstates/' + NAME + '.json', blockstate())
    print('wrote', NAME)
