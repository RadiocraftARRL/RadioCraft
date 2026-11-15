"""Placeholder art for the Computer Redstone block (2026-11-13): a beige interface box with a red lamp on each side.

Writes the blockstate (dark at power 0, lit above), two block models, the item model and three textures. On the
model bounty list for real art. Run from the repo root.
"""
import json
import os

from PIL import Image

ROOT = 'src/main/resources/assets/radiocraft'
TEX = ROOT + '/textures/block/computer_redstone'

BEIGE, EDGE, DARK = (196, 186, 160), (150, 140, 116), (60, 56, 50)


def base():
    img = Image.new('RGBA', (16, 16), BEIGE + (255,))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, EDGE + (255,))
    return img


def side(lit):
    img = base()
    for x in range(3, 13):  # A port strip.
        img.putpixel((x, 11), DARK + (255,))
        img.putpixel((x, 12), DARK + (255,))
    lamp = (255, 40, 30) if lit else (90, 20, 18)
    for x in range(6, 10):
        for y in range(4, 8):
            img.putpixel((x, y), lamp + (255,))
    if lit:
        img.putpixel((6, 4), (255, 160, 140, 255))
    return img


def top():
    img = base()
    for x in range(4, 12):
        img.putpixel((x, 7), EDGE + (255,))
        img.putpixel((x, 8), EDGE + (255,))
    return img


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


os.makedirs(os.path.dirname(TEX), exist_ok=True)
side(False).save(TEX + '_side.png')
side(True).save(TEX + '_side_on.png')
top().save(TEX + '_top.png')
for name, s in (('computer_redstone', 'computer_redstone_side'), ('computer_redstone_on', 'computer_redstone_side_on')):
    write(ROOT + '/models/block/%s.json' % name, {'parent': 'minecraft:block/cube_column', 'textures': {
        'end': 'radiocraft:block/computer_redstone_top', 'side': 'radiocraft:block/' + s}})
write(ROOT + '/models/item/computer_redstone.json', {'parent': 'radiocraft:block/computer_redstone'})
write(ROOT + '/blockstates/computer_redstone.json', {'variants': {
    'power=%d' % p: {'model': 'radiocraft:block/computer_redstone' + ('_on' if p > 0 else '')} for p in range(16)}})
print('ok')
