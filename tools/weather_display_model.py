"""Model and texture for the Local Weather Station Display (2026-11-18): a desk weather display in a wood case, like a
clock radio, with a black bezel round a dark red LCD window. The renderer (WeatherDisplayRenderer) draws the red
seven-segment readings on the element named DISPLAY.

Drawn facing north (the LCD on the north face). Writes the texture, the block and item models and the blockstate. Run
from the repo root.
"""
import json
import os

from PIL import Image

ROOT = 'src/main/resources/assets/radiocraft'
NAME = 'weather_display'

WOOD = (0, 0, 8, 8)
BEZEL = (8, 0, 12, 4)
LCD = (12, 0, 16, 4)
FOOT = (8, 4, 12, 8)


def texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):  # Walnut, with grain along x.
            base = (112, 72, 44) if (y * 3 + (x // 3)) % 5 else (94, 60, 36)
            if (x + y * 2) % 7 == 0:
                base = (126, 84, 52)
            px[x, y] = base + (255,)
    for y in range(4):
        for x in range(8, 12):
            px[x, y] = (22, 22, 24, 255)
        for x in range(12, 16):
            px[x, y] = (34, 8, 8, 255) if (x + y) % 3 else (40, 10, 10, 255)
    for y in range(4, 8):
        for x in range(8, 12):
            px[x, y] = (40, 40, 42, 255)
    os.makedirs(ROOT + '/textures/block', exist_ok=True)
    img.save(ROOT + '/textures/block/' + NAME + '.png')


def faces(region, skip=()):
    return {f: {'uv': list(region), 'texture': '#all'} for f in ('north', 'east', 'south', 'west', 'up', 'down') if f not in skip}


def model():
    elements = [
        {'name': 'case', 'from': [2, 1, 6], 'to': [14, 9, 11], 'faces': faces(WOOD)},
        {'name': 'bezel', 'from': [2.5, 2, 5.6], 'to': [13.5, 8, 6], 'faces': faces(BEZEL, ('south',))},
        {'name': 'DISPLAY', 'from': [3, 2.5, 5.5], 'to': [13, 7.5, 5.6], 'faces': {'north': {'uv': list(LCD), 'texture': '#all'}}},
        {'name': 'foot_left', 'from': [3, 0, 7], 'to': [5, 1, 10], 'faces': faces(FOOT)},
        {'name': 'foot_right', 'from': [11, 0, 7], 'to': [13, 1, 10], 'faces': faces(FOOT)},
    ]
    return {'credit': 'tools/weather_display_model.py (placeholder, see doc/ModelDesignBounty.md)', 'parent': 'block/block',
            'textures': {'all': 'radiocraft:block/' + NAME, 'particle': 'radiocraft:block/' + NAME}, 'elements': elements}


def blockstate():
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        v = {'model': 'radiocraft:block/' + NAME}
        if y:
            v['y'] = y
        variants['facing=' + facing] = v
    return {'variants': variants}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    texture()
    write(ROOT + '/models/block/' + NAME + '.json', model())
    write(ROOT + '/models/item/' + NAME + '.json', {'parent': 'radiocraft:block/' + NAME})
    write(ROOT + '/blockstates/' + NAME + '.json', blockstate())
    print('wrote', NAME)
