"""Builds the weather alert blocks' placeholder art (1.12 weather alerts; see doc/ModelDesignBounty.md):

- weather_alert_generator: a full-block grey equipment cabinet, front (north) with a dark status window, a green
  READY lamp and a red TX lamp. weather_alert_generator_ready / _tx: each lamp alone, lit (full bright), added by the
  blockstate while it is on the air / sending.
- weather_alert_receiver: a small white table-top weather radio (12 x 6 x 8 pixels) with a speaker grille, a little
  green display, a red ALERT lamp on top and a short whip at the back. weather_alert_receiver_lamp: the lamp lit,
  added while it flashes.

Textures are 32x32, so UVs are pixels / 2. Writes the textures, block and item models and blockstates (hand-made, as
each has lit parts).

    python tools/weather_alert_models.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft') + '/'

GREY = (96, 100, 106, 255)
GREY_LIGHT = (132, 136, 142, 255)
GREY_DARK = (58, 61, 66, 255)
WINDOW = (20, 28, 30, 255)
TEXT = (60, 150, 90, 255)
GREEN_OFF = (30, 60, 34, 255)
GREEN_ON = (90, 255, 110, 255)
RED_OFF = (70, 24, 24, 255)
RED_ON = (255, 40, 40, 255)
WHITE = (222, 224, 220, 255)
WHITE_DARK = (176, 178, 174, 255)
GRILLE = (110, 112, 110, 255)
LCD = (120, 160, 110, 255)
BLACK = (24, 24, 26, 255)
METAL = (180, 184, 188, 255)


def canvas():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    return img, img.load()


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = c


def frame(px, x0, y0, x1, y1, light, dark):
    rect(px, x0, y0, x1, y0 + 1, light)
    rect(px, x0, y0, x0 + 1, y1, light)
    rect(px, x0, y1 - 1, x1, y1, dark)
    rect(px, x1 - 1, y0, x1, y1, dark)


def save(img, name):
    img.save(ROOT + 'textures/block/' + name + '.png')


def write(path, data):
    with open(ROOT + path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def face(uv):
    return {'uv': uv, 'texture': '#0'}


def box(name, frm, to, faces):
    return {'name': name, 'from': frm, 'to': to, 'faces': faces}


def model(texture, elements, extra=None):
    out = {'credit': 'RadioCraft placeholder, made by tools/weather_alert_models.py (see doc/ModelDesignBounty.md)',
           'parent': 'block/block', 'texture_size': [32, 32],
           'textures': {'0': 'radiocraft:block/' + texture, 'particle': 'radiocraft:block/' + texture},
           'elements': elements}
    if extra:
        out.update(extra)
    return out


FULL_BRIGHT = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
TURNS = (('north', 0), ('east', 90), ('south', 180), ('west', 270))


def multipart(base, parts):
    """The base model for each facing, and each (model, condition) part added when its condition holds."""
    out = []
    for name, when in [(base, {})] + parts:
        for facing, y in TURNS:
            apply = {'model': 'radiocraft:block/' + name}
            if y:
                apply['y'] = y
            out.append({'when': dict(when, facing=facing), 'apply': apply})
    return {'multipart': out}


def lit_part(name, frm, to, uv):
    part = box(name, frm, to, {'north': face(uv)})
    part['neoforge_data'] = FULL_BRIGHT
    return part


# -------------------- GENERATOR --------------------
# Regions (pixels): front 0-16 x 0-16, side 16-32 x 0-16, top 0-16 x 16-32; lamps lit at 16-18 x 16-18 (green) and
# 18-20 x 16-18 (red).
def generator():
    img, px = canvas()
    rect(px, 0, 0, 16, 16, GREY)
    frame(px, 0, 0, 16, 16, GREY_LIGHT, GREY_DARK)
    rect(px, 2, 2, 14, 7, WINDOW)  # Status window with "WX" in it.
    for x, y in ((3, 3), (3, 4), (3, 5), (4, 6), (5, 5), (6, 6), (7, 3), (7, 4), (7, 5), (9, 3), (11, 3), (10, 4), (9, 5), (11, 5)):
        px[x, y] = TEXT
    rect(px, 3, 9, 5, 11, GREEN_OFF)  # READY.
    rect(px, 7, 9, 9, 11, RED_OFF)  # TX.
    for y in (12, 14):  # Vents.
        rect(px, 2, y, 14, y + 1, GREY_DARK)
    rect(px, 16, 0, 32, 16, GREY)
    frame(px, 16, 0, 32, 16, GREY_LIGHT, GREY_DARK)
    for y in (4, 6, 8, 10):
        rect(px, 19, y, 29, y + 1, GREY_DARK)
    rect(px, 0, 16, 16, 32, GREY_LIGHT)
    frame(px, 0, 16, 16, 32, GREY_LIGHT, GREY_DARK)
    rect(px, 16, 16, 18, 18, GREEN_ON)
    rect(px, 18, 16, 20, 18, RED_ON)
    save(img, 'weather_alert_generator')

    body = box('cabinet', [0, 0, 0], [16, 16, 16], {
        'north': face([0, 0, 8, 8]), 'south': face([8, 0, 16, 8]), 'east': face([8, 0, 16, 8]),
        'west': face([8, 0, 16, 8]), 'up': face([0, 8, 8, 16]), 'down': face([0, 8, 8, 16]),
    })
    write('models/block/weather_alert_generator.json', model('weather_alert_generator', [body]))
    # The lamps: texture pixels (3-5, 9-11) and (7-9, 9-11) on the north face are model x 13-11 and 9-7, y 7-5.
    ready = lit_part('ready', [11, 5, -0.02], [13, 7, 0], [8, 8, 9, 9])
    write('models/block/weather_alert_generator_ready.json', model('weather_alert_generator', [ready], {'render_type': 'minecraft:cutout'}))
    tx = lit_part('tx', [7, 5, -0.02], [9, 7, 0], [9, 8, 10, 9])
    write('models/block/weather_alert_generator_tx.json', model('weather_alert_generator', [tx], {'render_type': 'minecraft:cutout'}))
    write('blockstates/weather_alert_generator.json', multipart('weather_alert_generator', [
        ('weather_alert_generator_ready', {'ready': 'true'}), ('weather_alert_generator_tx', {'transmitting': 'true'})]))
    write('models/item/weather_alert_generator.json', {'parent': 'radiocraft:block/weather_alert_generator'})


# -------------------- RECEIVER --------------------
# Regions (pixels): front 0-12 x 0-6, back 12-24 x 0-6, sides 24-32 x 0-6, top 0-12 x 8-16, lamp off 12-14 x 8-10,
# lamp lit 14-16 x 8-10, whip 16-17 x 8-18.
def receiver():
    img, px = canvas()
    rect(px, 0, 0, 12, 6, WHITE)
    frame(px, 0, 0, 12, 6, WHITE, WHITE_DARK)
    for y in (1, 3):  # Speaker grille, left.
        for x in (1, 3, 5):
            rect(px, x, y, x + 1, y + 1, GRILLE)
    rect(px, 7, 1, 11, 3, LCD)  # Display.
    rect(px, 7, 4, 9, 5, GREY_DARK)  # Buttons.
    rect(px, 10, 4, 11, 5, GREY_DARK)
    rect(px, 12, 0, 24, 6, WHITE_DARK)
    rect(px, 24, 0, 32, 6, WHITE)
    frame(px, 24, 0, 32, 6, WHITE, WHITE_DARK)
    rect(px, 0, 8, 12, 16, WHITE)
    frame(px, 0, 8, 12, 16, WHITE, WHITE_DARK)
    rect(px, 12, 8, 14, 10, RED_OFF)
    rect(px, 14, 8, 16, 10, RED_ON)
    rect(px, 16, 8, 17, 18, METAL)
    save(img, 'weather_alert_receiver')

    body = box('case', [2, 0, 4], [14, 6, 12], {
        'north': face([0, 0, 6, 3]), 'south': face([6, 0, 12, 3]), 'east': face([12, 0, 16, 3]),
        'west': face([12, 0, 16, 3]), 'up': face([0, 4, 6, 8]), 'down': face([0, 4, 6, 8]),
    })
    lamp = box('alert_lamp', [6, 6, 5], [8, 7, 7], {
        'north': face([6, 4, 7, 4.5]), 'south': face([6, 4, 7, 4.5]), 'east': face([6, 4, 7, 4.5]),
        'west': face([6, 4, 7, 4.5]), 'up': face([6, 4, 7, 5]),
    })
    whip = box('whip', [12, 6, 10.5], [12.5, 13, 11], {
        'north': face([8, 4, 8.5, 9]), 'south': face([8, 4, 8.5, 9]), 'east': face([8, 4, 8.5, 9]),
        'west': face([8, 4, 8.5, 9]), 'up': face([8, 4, 8.5, 4.5]),
    })
    write('models/block/weather_alert_receiver.json', model('weather_alert_receiver', [body, lamp, whip]))
    lit = box('alert_lamp_lit', [5.95, 5.95, 4.95], [8.05, 7.05, 7.05], {
        'north': face([7, 4, 8, 4.5]), 'south': face([7, 4, 8, 4.5]), 'east': face([7, 4, 8, 4.5]),
        'west': face([7, 4, 8, 4.5]), 'up': face([7, 4, 8, 5]),
    })
    lit['neoforge_data'] = FULL_BRIGHT
    write('models/block/weather_alert_receiver_lamp.json', model('weather_alert_receiver', [lit], {'render_type': 'minecraft:cutout'}))
    write('blockstates/weather_alert_receiver.json', multipart('weather_alert_receiver', [('weather_alert_receiver_lamp', {'lit': 'true'})]))
    write('models/item/weather_alert_receiver.json', {'parent': 'radiocraft:block/weather_alert_receiver'})


if __name__ == '__main__':
    generator()
    receiver()
