"""Builds the ADS-B blocks' placeholder art (1.12 step 9; see doc/ModelDesignBounty.md):

- adsb_beacon: a small grey avionics box (a panel-mount transponder) with a code window, two knobs, an IDENT button,
  a reply lamp, and a blade antenna on top. Its front faces the way it was placed (the craft's nose), north in the
  model. adsb_beacon_lamp: the lamp alone, lit (full bright), added by the blockstate while it transmits.
- adsb_receiver: a dark cabinet, a full block, whose whole front (north) is a square green display, edge to edge
  (1.12 playtest fixes 2); knobs on the side. adsb_receiver_screen: the display's glowing face alone (full bright,
  cutout), added while it is on.
- adsb_antenna: a short 1090 MHz vertical (a stub with a ball tip) on a round-ish ground plane plate.

Textures are 32x32, so UVs are pixels / 2. Writes the textures, block and item models and blockstates (hand-made
blockstates, not the data generator's, since each has an extra lit part).

    python tools/adsb_models.py
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft') + '/'

GREY = (88, 92, 98, 255)
GREY_LIGHT = (120, 124, 130, 255)
GREY_DARK = (54, 57, 62, 255)
BLACK = (18, 20, 22, 255)
CODE = (40, 30, 14, 255)
AMBER = (240, 168, 48, 255)
WHITE = (226, 228, 230, 255)
KNOB = (30, 31, 34, 255)
CAB = (46, 52, 50, 255)
CAB_LIGHT = (70, 78, 74, 255)
CAB_DARK = (30, 34, 33, 255)
BEZEL = (24, 26, 26, 255)
SCOPE = (6, 28, 10, 255)
RING = (30, 110, 40, 255)
GLOW = (110, 255, 120, 255)
GLOW_DIM = (40, 160, 60, 255)
LAMP_OFF = (60, 40, 30, 255)
LAMP_ON = (255, 210, 80, 255)
METAL = (176, 180, 184, 255)
METAL_DARK = (120, 124, 128, 255)


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


def face(uv, rotation=0):
    out = {'uv': uv, 'texture': '#0'}
    if rotation:
        out['rotation'] = rotation
    return out


def box(name, frm, to, faces):
    return {'name': name, 'from': frm, 'to': to, 'faces': faces}


def model(texture, elements, extra=None):
    out = {'credit': 'RadioCraft placeholder, made by tools/adsb_models.py (see doc/ModelDesignBounty.md)',
           'parent': 'block/block', 'texture_size': [32, 32],
           'textures': {'0': 'radiocraft:block/' + texture, 'particle': 'radiocraft:block/' + texture},
           'elements': elements}
    if extra:
        out.update(extra)
    return out


FULL_BRIGHT = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


def horizontal_lit(name, part):
    parts = []
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        apply = {'model': 'radiocraft:block/' + name}
        if y:
            apply['y'] = y
        parts.append({'when': {'facing': facing}, 'apply': apply})
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        apply = {'model': 'radiocraft:block/' + part}
        if y:
            apply['y'] = y
        parts.append({'when': {'facing': facing, 'lit': 'true'}, 'apply': apply})
    return {'multipart': parts}


# -------------------- BEACON --------------------
# Regions (pixels): front 0-16 x 0-8, sides 16-32 x 0-8 (5 high used), top 0-16 x 8-20, lamp 16-18 x 8-10 (off),
# lamp lit 18-20 x 8-10, blade 20-24 x 8-16.
def beacon():
    img, px = canvas()
    # Front panel (10 wide, 5 high in the model: 0-10 x 0-5 pixels used of 0-16 x 0-8, scaled 1:1).
    rect(px, 0, 0, 16, 8, GREY)
    frame(px, 0, 0, 10, 5, GREY_LIGHT, GREY_DARK)
    rect(px, 1, 1, 5, 3, CODE)  # The code window.
    px[2, 2] = AMBER
    px[3, 2] = AMBER
    px[4, 1] = AMBER
    rect(px, 5, 1, 6, 2, KNOB)  # Knobs (the reply lamp is at 8, 1).
    rect(px, 6, 3, 7, 4, KNOB)
    rect(px, 7, 3, 9, 4, WHITE)  # IDENT.
    # Sides and back.
    rect(px, 16, 0, 32, 8, GREY_DARK)
    frame(px, 16, 0, 28, 5, GREY, BLACK)
    # Top.
    rect(px, 0, 8, 16, 20, GREY)
    frame(px, 0, 8, 10, 20, GREY_LIGHT, GREY_DARK)
    # Lamps and blade.
    rect(px, 16, 8, 18, 10, LAMP_OFF)
    rect(px, 18, 8, 20, 10, LAMP_ON)
    rect(px, 20, 8, 24, 16, WHITE)
    rect(px, 23, 8, 24, 16, METAL_DARK)
    save(img, 'adsb_beacon')

    body = box('box', [3, 0, 2], [13, 5, 14], {
        'north': face([0, 0, 5, 2.5]),
        'south': face([8, 0, 13, 2.5]),
        'east': face([8, 0, 14, 2.5]),
        'west': face([8, 0, 14, 2.5]),
        'up': face([0, 4, 5, 10]),
        'down': face([0, 4, 5, 10]),
    })
    # The front faces north: texture u runs from model x 13 down, v from model y 5 down. The lamp sits at u 8, v 1.
    lamp = box('lamp', [4, 3, 1.9], [5, 4, 2], {'north': face([8, 4, 9, 5])})
    blade = box('blade', [7.5, 5, 6], [8.5, 9, 10], {
        'east': face([10, 4, 12, 8]), 'west': face([10, 4, 12, 8]),
        'north': face([11.5, 4, 12, 8]), 'south': face([11.5, 4, 12, 8]), 'up': face([10, 4, 12, 4.5]),
    })
    write('models/block/adsb_beacon.json', model('adsb_beacon', [body, lamp, blade]))
    lit = box('lamp_lit', [4, 3, 1.85], [5, 4, 1.95], {'north': face([9, 4, 10, 5])})
    lit['neoforge_data'] = FULL_BRIGHT
    write('models/block/adsb_beacon_lamp.json', model('adsb_beacon', [lit], {'render_type': 'minecraft:cutout'}))
    write('blockstates/adsb_beacon.json', horizontal_lit('adsb_beacon', 'adsb_beacon_lamp'))
    write('models/item/adsb_beacon.json', {'parent': 'radiocraft:block/adsb_beacon'})


# -------------------- RECEIVER --------------------
# The tube is a square display filling the whole front, edge to edge (1.12 playtest fixes 2), as a modern ATC/ADS-B
# display: a square screen with range rings clipped at its edges, not a round radar tube in a bezel.
# Regions (pixels): front (the dark screen) 0-16 x 0-16, side 16-32 x 0-16 (vents, knobs), top 0-16 x 16-32,
# lit screen 16-32 x 16-32. UVs are pixels / 2.
def scope(px, ox, oy, size, lit):
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            edge = x in (0, size - 1) or y in (0, size - 1)
            if lit:
                colour = (10, 48, 16, 255)
                if any(abs(d - c * k / 4.0) < 0.5 for k in (1, 2, 3, 4, 5)):
                    colour = GLOW_DIM
                if x == round(c) or y == round(c):
                    colour = (24, 90, 34, 255)
                if edge:
                    colour = (16, 60, 24, 255)
            else:
                colour = SCOPE
                if abs(d - c * 0.5) < 0.5:
                    colour = RING
                if edge:
                    colour = BEZEL
            px[ox + x, oy + y] = colour
    if lit:
        px[ox + 3, oy + 4] = GLOW
        px[ox + 11, oy + 9] = GLOW
        px[ox + 9, oy + 3] = GLOW


def receiver():
    img, px = canvas()
    scope(px, 0, 0, 16, False)
    rect(px, 16, 0, 32, 16, CAB_DARK)
    frame(px, 16, 0, 32, 16, CAB, BLACK)
    for y in (3, 5, 7, 9):  # Vents.
        rect(px, 19, y, 29, y + 1, BLACK)
    rect(px, 19, 12, 21, 14, KNOB)  # Range and power knobs on the side.
    rect(px, 24, 12, 26, 14, KNOB)
    px[28, 13] = GLOW_DIM  # Power lamp.
    rect(px, 0, 16, 16, 32, CAB)
    frame(px, 0, 16, 16, 32, CAB_LIGHT, CAB_DARK)
    scope(px, 16, 16, 16, True)
    save(img, 'adsb_receiver')

    body = box('cabinet', [0, 0, 0], [16, 16, 16], {
        'north': face([0, 0, 8, 8]),
        'south': face([8, 0, 16, 8]),
        'east': face([8, 0, 16, 8]),
        'west': face([8, 0, 16, 8]),
        'up': face([0, 8, 8, 16]),
        'down': face([0, 8, 8, 16]),
    })
    write('models/block/adsb_receiver.json', model('adsb_receiver', [body]))
    # The screen is the whole north face, just in front of it (z -0.02 to 0).
    # client/render/AdsbScopeRenderer draws the live scope over this face (SCREEN_X0..Z): keep them in step if it moves.
    glow = box('scope', [0, 0, -0.02], [16, 16, 0], {'north': face([8, 8, 16, 16])})
    glow['neoforge_data'] = FULL_BRIGHT
    write('models/block/adsb_receiver_screen.json', model('adsb_receiver', [glow], {'render_type': 'minecraft:cutout'}))
    write('blockstates/adsb_receiver.json', horizontal_lit('adsb_receiver', 'adsb_receiver_screen'))
    write('models/item/adsb_receiver.json', {'parent': 'radiocraft:block/adsb_receiver'})


# -------------------- ANTENNA --------------------
# Regions: plate top 0-8 x 0-8, plate side 0-8 x 8-10, mast 8-10 x 0-12, tip 10-12 x 0-2.
def antenna():
    img, px = canvas()
    rect(px, 0, 0, 8, 8, METAL)
    frame(px, 0, 0, 8, 8, WHITE, METAL_DARK)
    rect(px, 3, 3, 5, 5, BLACK)  # The connector under the mast.
    rect(px, 0, 8, 8, 10, METAL_DARK)
    rect(px, 8, 0, 10, 12, BLACK)
    rect(px, 8, 0, 9, 12, GREY_DARK)
    rect(px, 10, 0, 12, 2, WHITE)
    save(img, 'adsb_antenna')

    plate = box('ground_plane', [4, 0, 4], [12, 2, 12], {
        'up': face([0, 0, 4, 4]), 'down': face([0, 0, 4, 4]),
        'north': face([0, 4, 4, 5]), 'south': face([0, 4, 4, 5]), 'east': face([0, 4, 4, 5]), 'west': face([0, 4, 4, 5]),
    })
    mast = box('radiator', [7.5, 2, 7.5], [8.5, 13, 8.5], {
        'north': face([4, 0, 5, 5.5]), 'south': face([4, 0, 5, 5.5]), 'east': face([4, 0, 5, 5.5]), 'west': face([4, 0, 5, 5.5]),
    })
    tip = box('tip', [7, 13, 7], [9, 14, 9], {
        'up': face([5, 0, 6, 1]), 'north': face([5, 0, 6, 0.5]), 'south': face([5, 0, 6, 0.5]),
        'east': face([5, 0, 6, 0.5]), 'west': face([5, 0, 6, 0.5]), 'down': face([5, 0, 6, 1]),
    })
    write('models/block/adsb_antenna.json', model('adsb_antenna', [plate, mast, tip]))
    write('blockstates/adsb_antenna.json', {'variants': {'': {'model': 'radiocraft:block/adsb_antenna'}}})
    write('models/item/adsb_antenna.json', {'parent': 'radiocraft:block/adsb_antenna'})


if __name__ == '__main__':
    beacon()
    receiver()
    antenna()
