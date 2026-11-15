"""Placeholder model and textures for the linear amplifier (2026-11-13): a desk box with a plate meter and a lamp.

Writes the blockstate (facing x lit x keyed), three block models (off, on, keyed: the lamp lights, the needle kicks),
the item model and the textures. On the model bounty list for real art. Run from the repo root.
"""
import json
import os

from PIL import Image

ROOT = 'src/main/resources/assets/radiocraft'
TEX = ROOT + '/textures/block/linear_amplifier'


def save(img, name):
    os.makedirs(os.path.dirname(TEX + '_' + name + '.png'), exist_ok=True)
    img.save(TEX + '_' + name + '.png')


def panel(colour):
    return Image.new('RGBA', (16, 16), colour + (255,))


def side():
    img = panel((52, 54, 58))
    for y in range(9, 15, 2):
        for x in range(4, 12):
            img.putpixel((x, y), (30, 31, 34, 255))  # Vent slots.
    return img


def top():
    img = panel((60, 62, 66))
    for y in range(4, 12, 2):
        for x in range(3, 13):
            img.putpixel((x, y), (34, 35, 38, 255))
    return img


def front(lamp, needle):
    img = panel((40, 42, 46))
    for x in range(1, 15):
        img.putpixel((x, 15), (70, 72, 76, 255))  # Bottom trim.
        img.putpixel((x, 8), (70, 72, 76, 255))   # Top trim.
    for x in range(3, 8):  # Meter window.
        for y in range(9, 13):
            img.putpixel((x, y), (228, 218, 178, 255))
    tip = (4, 11) if needle == 'low' else (7, 10)
    img.putpixel((5, 12), (30, 30, 30, 255))
    img.putpixel(tip, (30, 30, 30, 255))
    img.putpixel(((5 + tip[0]) // 2, (12 + tip[1]) // 2), (30, 30, 30, 255))
    colour = (255, 156, 40, 255) if lamp else (90, 24, 20, 255)
    img.putpixel((9, 10), colour)
    img.putpixel((10, 10), colour)
    for x in range(9, 14):
        img.putpixel((x, 13), (150, 150, 150, 255))  # Label.
    return img


def model(front_tex):
    box = {'from': [1, 0, 2], 'to': [15, 8, 14], 'faces': {
        'north': {'uv': [1, 8, 15, 16], 'texture': '#front'},
        'south': {'uv': [1, 8, 15, 16], 'texture': '#side'},
        'east': {'uv': [2, 8, 14, 16], 'texture': '#side'},
        'west': {'uv': [2, 8, 14, 16], 'texture': '#side'},
        'up': {'uv': [1, 2, 15, 14], 'texture': '#top'},
        'down': {'uv': [1, 2, 15, 14], 'texture': '#side'}}}
    knob = {'from': [11.5, 2, 1], 'to': [13.5, 4, 2], 'faces': {
        f: {'uv': [0, 0, 2, 2], 'texture': '#side'} for f in ('north', 'east', 'west', 'up', 'down')}}
    return {'parent': 'block/block', 'textures': {'front': 'radiocraft:block/linear_amplifier_' + front_tex,
                                                   'side': 'radiocraft:block/linear_amplifier_side',
                                                   'top': 'radiocraft:block/linear_amplifier_top',
                                                   'particle': 'radiocraft:block/linear_amplifier_side'},
            'elements': [box, knob]}


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


save(side(), 'side')
save(top(), 'top')
save(front(False, 'low'), 'front')
save(front(True, 'low'), 'front_on')
save(front(True, 'high'), 'front_keyed')
names = {'off': 'linear_amplifier', 'on': 'linear_amplifier_on', 'keyed': 'linear_amplifier_keyed'}
write(ROOT + '/models/block/linear_amplifier.json', model('front'))
write(ROOT + '/models/block/linear_amplifier_on.json', model('front_on'))
write(ROOT + '/models/block/linear_amplifier_keyed.json', model('front_keyed'))
write(ROOT + '/models/item/linear_amplifier.json', {'parent': 'radiocraft:block/linear_amplifier'})
variants = {}
for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    for lit in ('false', 'true'):
        for keyed in ('false', 'true'):
            name = names['keyed'] if keyed == 'true' else names['on'] if lit == 'true' else names['off']
            v = {'model': 'radiocraft:block/' + name}
            if y:
                v['y'] = y
            variants['facing=%s,keyed=%s,lit=%s' % (facing, keyed, lit)] = v
write(ROOT + '/blockstates/linear_amplifier.json', {'variants': variants})
print('ok')
