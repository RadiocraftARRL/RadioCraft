"""Models and textures for the airband radio, the aircraft antenna and the FM broadcast pair (2026-11-13).

Drawn from the maintainer's reference photos (no maker's names or logos on them):
- airband_radio: a vintage panel-mount NAV/COM: a black faceplate with two amber seven-segment windows (USE and STBY,
  named DISPLAY so the renderer writes the frequencies on them), a flip-flop button between, a push-button row, a
  volume knob on the left and a big concentric tuning knob on the right.
- airband_antenna: an aircraft VHF whip: a small white swept fin with a raked steel whip, one block tall. It stands
  on top of a block or hangs under one (the blockstate turns it over for the ceiling).
- fm_broadcast_transmitter: a two-block blue transmitter cabinet: a silver strip of meters and knobs down the left,
  a darker control panel with lamps at the top right, a silver exciter with a bar-graph meter, a power-control box
  and a tall door below. Its lamps light and the bar graph climbs while it is on the air (the _on textures).
- fm_broadcast_receiver: a walnut table radio (redrawn 2026-11-13 after the maintainer's photo): a woven grille in
  chrome on the left, a chrome-framed panel on the right with a tall orange dial window and TUNING, VOLUME, BASS and
  TREBLE knobs. The transmitter's panels, meters and handles stand out of its cabinet (2026-11-13).
Run with part names (airband, transmitter, receiver) to make only those; with none, all of them.

Writes the textures, the block and item models and the blockstates (the airband radio's and the receiver's
blockstates and item models come from datagen, RadiocraftBlockstateProvider.radio). Run from the repo root.
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = 'src/main/resources/assets/radiocraft'
TEX = ROOT + '/textures/block/'


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def save(img, name):
    img.save(TEX + name + '.png')


def rgba(c):
    return c + (255,) if len(c) == 3 else c


def canvas(w, h, colour):
    return Image.new('RGBA', (w, h), rgba(colour))


def rect(img, x0, y0, x1, y1, colour):
    """Fills the pixels x0..x1-1, y0..y1-1."""
    ImageDraw.Draw(img).rectangle([x0, y0, x1 - 1, y1 - 1], fill=rgba(colour))


def px(img, x, y, colour):
    img.putpixel((x, y), rgba(colour))


def faces(texture, uv=(0, 0, 16, 16), skip=(), tint=False):
    out = {}
    for f in ('north', 'east', 'south', 'west', 'up', 'down'):
        if f in skip:
            continue
        out[f] = {'uv': list(uv), 'texture': texture}
    return out


def box(name, frm, to, faces_, rotation=None):
    e = {'name': name, 'from': frm, 'to': to, 'faces': faces_}
    if rotation:
        e['rotation'] = rotation
    return e


def horizontal_variants(model, extra=''):
    out = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        v = {'model': model}
        if y:
            v['y'] = y
        out['facing=' + facing + extra] = v
    return out


# ============================== AIRBAND RADIO ==============================
# Front face: 15 x 5 model units at 4 px a unit = 60 x 20 px of a 64 x 32 texture. Drawn as seen from the front.

PLATE = (34, 34, 37)
BEVEL = (58, 58, 62)
PRINT = (196, 196, 190)
WINDOW = (26, 14, 8)
AMBER_DIM = (70, 34, 10)


def airband_front():
    img = canvas(64, 32, PLATE)
    for x in range(60):  # Bevel round the faceplate.
        px(img, x, 0, BEVEL)
        px(img, x, 19, (22, 22, 24))
    for y in range(20):
        px(img, 0, y, BEVEL)
        px(img, 59, y, (22, 22, 24))
    # The two seven-segment windows (USE, STBY), u 13..29 and 33..49, v 3..10: the renderer writes on them.
    for x0 in (13, 33):
        rect(img, x0 - 1, 2, x0 + 17, 11, (16, 16, 18))
        rect(img, x0, 3, x0 + 16, 10, WINDOW)
        for i in range(5):  # Dark segments of "88888", as an unlit display shows.
            sx = x0 + 1 + i * 3
            rect(img, sx, 4, sx + 2, 5, AMBER_DIM)
            rect(img, sx, 6, sx + 2, 7, AMBER_DIM)
            rect(img, sx, 8, sx + 2, 9, AMBER_DIM)
    for x in (14, 15, 16, 34, 35, 36):  # "USE" and "STBY" legends under the windows.
        px(img, x, 12, PRINT)
    for x in (34, 35, 36, 37):
        px(img, x, 12, PRINT)
    # The flip-flop button between the windows: a light key with arrows.
    rect(img, 30, 4, 32, 9, (150, 150, 146))
    px(img, 30, 5, (40, 40, 40))
    px(img, 31, 7, (40, 40, 40))
    # The button row.
    for x0 in (13, 19, 25, 37, 43):
        rect(img, x0, 14, x0 + 4, 17, (70, 70, 74))
        rect(img, x0, 14, x0 + 4, 15, (98, 98, 102))
        px(img, x0 + 1, 16, PRINT)
        px(img, x0 + 2, 16, PRINT)
    # Legends round the knobs (the knobs are elements): PWR VOL and PUSH SQ on the left, TUNE on the right.
    for x in range(2, 6):
        px(img, x, 2, PRINT)
    for x in range(2, 7):
        px(img, x, 17, PRINT)
    for x in range(51, 56):
        px(img, x, 3, PRINT)
    # A small photocell and the COM/NAV legend.
    rect(img, 9, 3, 11, 5, (60, 50, 44))
    for x in (8, 9, 10):
        px(img, x, 12, PRINT)
    return img


def airband_case():
    img = canvas(16, 16, (46, 46, 50))
    for y in range(2, 14, 3):
        for x in range(2, 14):
            px(img, x, y, (36, 36, 40))
    return img


def knob(name, x0, y0, size, depth, texture='#knob'):
    """A knob on the front (north) face, its face at z = 2.5 - depth."""
    return box(name, [x0, y0, 2.5 - depth], [x0 + size, y0 + size, 2.5],
               {f: {'uv': [0, 0, size * 2, size * 2], 'texture': texture} for f in ('north', 'east', 'west', 'up', 'down')})


def airband_model():
    faceplate_uv = [0, 0, 15, 10]  # 64 x 32 texture: v runs 2 px a unit
    elements = [
        box('body', [1.5, 0, 3], [14.5, 4.5, 15], faces('#case')),
        box('faceplate', [0.5, 0, 2.5], [15.5, 5, 3], {
            'north': {'uv': faceplate_uv, 'texture': '#front'},
            'east': {'uv': [0, 0, 0.5, 5], 'texture': '#case'},
            'west': {'uv': [0, 0, 0.5, 5], 'texture': '#case'},
            'up': {'uv': [0, 0, 15, 0.5], 'texture': '#case'},
            'down': {'uv': [0, 0, 15, 0.5], 'texture': '#case'}}),
        # The windows the renderer writes on (just proud of the plate so it finds them). Viewer's left is east (+x).
        box('DISPLAY', [8.25, 2.5, 2.45], [12.25, 4.25, 2.5], {'north': {'uv': [3.25, 1.5, 7.25, 5], 'texture': '#front'}}),
        box('DISPLAY', [3.25, 2.5, 2.45], [7.25, 4.25, 2.5], {'north': {'uv': [8.25, 1.5, 12.25, 5], 'texture': '#front'}}),
        # PWR/VOL (top) and NAV VOL (bottom) on the viewer's left; the concentric tuner on the right.
        knob('volume', 13.25, 2.75, 1.5, 0.75),
        knob('nav_volume', 13.25, 0.75, 1.5, 0.75),
        knob('tune_outer', 1.0, 0.75, 2.5, 0.75),
        knob('tune_inner', 1.5, 1.25, 1.5, 1.5),
    ]
    return {'credit': 'tools/airband_fm_models.py', 'parent': 'block/block', 'texture_size': [64, 32],
            'textures': {'front': 'radiocraft:block/airband_radio_front', 'case': 'radiocraft:block/airband_radio_case',
                         'knob': 'radiocraft:block/airband_radio_knob', 'particle': 'radiocraft:block/airband_radio_case'},
            'elements': elements}


def knob_texture(base, ring):
    img = canvas(16, 16, base)
    for i in range(16):
        px(img, i, 0, ring)
        px(img, i, 15, ring)
        if i % 2 == 0:
            px(img, i, 1, ring)  # Knurling.
    px(img, 7, 3, (220, 220, 220))  # The pointer dot.
    px(img, 8, 3, (220, 220, 220))
    return img


# ============================== AIRCRAFT ANTENNA ==============================

def white_texture():
    img = canvas(16, 16, (232, 232, 228))
    for y in range(16):
        px(img, 0, y, (210, 210, 206))
    rect(img, 6, 6, 10, 10, (40, 92, 170))  # A blue decal, no name on it.
    rect(img, 7, 7, 9, 9, (232, 232, 228))
    return img


def steel_texture():
    img = canvas(16, 16, (168, 170, 174))
    for y in range(0, 16, 2):
        px(img, 0, y, (130, 132, 136))
    return img


def antenna_model():
    w = '#fin'
    elements = [
        box('mount', [5.5, 0, 4], [10.5, 0.5, 12.5], faces(w)),  # The doubler plate against the skin.
        box('fin_root', [7, 0.5, 4.5], [9, 2, 12], {**faces(w), 'east': {'uv': [4.5, 14, 12, 15.5], 'texture': w},
                                                    'west': {'uv': [4.5, 14, 12, 15.5], 'texture': w}}),
        box('fin_mid', [7.2, 2, 6.5], [8.8, 3.5, 12], faces(w)),
        box('fin_tip', [7.4, 3.5, 8.5], [8.6, 5, 12], faces(w)),
        box('fin_top', [7.5, 5, 10], [8.5, 6, 12], faces(w)),
        # The whip, raked back 22.5 degrees from the top of the fin.
        box('whip', [7.75, 5.5, 10.75], [8.25, 16.5, 11.25], faces('#whip', (7, 0, 8, 11)),
            {'angle': 22.5, 'axis': 'x', 'origin': [8, 5.5, 11]}),
    ]
    return {'credit': 'tools/airband_fm_models.py', 'parent': 'block/block',
            'textures': {'fin': 'radiocraft:block/airband_antenna_fin', 'whip': 'radiocraft:block/airband_antenna_whip',
                         'particle': 'radiocraft:block/airband_antenna_fin'},
            'elements': elements,
            'display': {'gui': {'rotation': [20, 135, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]}}}


# ============================== FM BROADCAST TRANSMITTER ==============================
# Two blocks: each half's front is 32 x 32 (2 px a unit); the cabinet is the whole block wide.

BLUE = (30, 74, 120)
BLUE_DARK = (22, 54, 92)
BLUE_EDGE = (16, 40, 70)
SILVER = (178, 182, 186)
SILVER_DARK = (126, 130, 136)
METER = (226, 224, 210)


def meter(img, x0, y0, w, h, needle):
    rect(img, x0, y0, x0 + w, y0 + h, (40, 40, 42))
    rect(img, x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, METER)
    for i in range(1, w - 1, 2):
        px(img, x0 + i, y0 + 1, (60, 60, 60))  # Scale.
    nx = x0 + 1 + int(round(needle * (w - 3)))
    px(img, nx, y0 + h - 2, (20, 20, 20))
    px(img, x0 + w // 2, y0 + h - 2, (20, 20, 20))


def round_knob(img, cx, cy):
    rect(img, cx - 1, cy - 1, cx + 2, cy + 2, (210, 212, 214))
    px(img, cx, cy, (90, 92, 96))
    px(img, cx - 1, cy - 1, SILVER)


def blue_panel(img, x0, y0, x1, y1, colour=BLUE):
    rect(img, x0, y0, x1, y1, colour)
    for x in range(x0, x1):
        px(img, x, y0, BLUE_EDGE)
    for y in range(y0, y1):
        px(img, x0, y, BLUE_EDGE)


def strip(img):
    rect(img, 1, 0, 9, 32, SILVER)
    for y in range(32):
        px(img, 1, y, SILVER_DARK)
        px(img, 8, y, SILVER_DARK)


def transmitter_upper(on):
    img = canvas(32, 32, BLUE)
    rect(img, 0, 0, 32, 3, (24, 24, 28))  # The header strip.
    rect(img, 2, 1, 6, 2, (230, 230, 230))  # A blank badge.
    rect(img, 25, 1, 30, 2, (200, 200, 200))
    strip(img)
    round_knob(img, 5, 6)
    round_knob(img, 5, 11)
    meter(img, 2, 14, 6, 4, 0.7 if on else 0.0)
    meter(img, 2, 20, 6, 4, 0.6 if on else 0.0)
    meter(img, 2, 26, 6, 4, 0.5 if on else 0.0)
    blue_panel(img, 10, 3, 19, 18)  # Plain blue cover.
    blue_panel(img, 19, 3, 31, 18, BLUE_DARK)  # The control panel.
    lamps = [(21, 6, (90, 220, 120) if on else (40, 70, 50)), (24, 6, (240, 70, 50) if not on else (80, 30, 26)),
             (27, 6, (250, 170, 60) if on else (80, 60, 30))]
    for x, y, c in lamps:
        rect(img, x, y, x + 2, y + 2, c)
    rect(img, 23, 10, 25, 12, (240, 200, 80))  # The amber push buttons.
    for x in (21, 24, 27):
        rect(img, x, 14, x + 2, 16, (150, 30, 30) if x == 24 else (40, 140, 90))
    blue_panel(img, 10, 18, 31, 21, BLUE_DARK)  # A switch strip.
    for x in (13, 16, 19, 22):
        rect(img, x, 19, x + 1, 20, (230, 230, 230))
    # The exciter: a silver chassis with a bar graph and a meter.
    rect(img, 10, 22, 31, 30, (196, 198, 200))
    for x in range(10, 31):
        px(img, x, 22, (150, 152, 156))
        px(img, x, 29, (140, 142, 146))
    rect(img, 12, 24, 20, 27, (24, 24, 26))
    lit = 7 if on else 0
    for i in range(7):
        px(img, 13 + i, 25, (90, 230, 90) if i < lit - 1 else (230, 210, 60) if i < lit else (40, 60, 40))
    meter(img, 23, 23, 6, 5, 0.65 if on else 0.0)
    blue_panel(img, 10, 30, 31, 32, BLUE_DARK)
    return img


def transmitter_lower(on):
    img = canvas(32, 32, BLUE)
    strip(img)
    meter(img, 2, 1, 6, 4, 0.55 if on else 0.0)
    meter(img, 2, 7, 6, 4, 0.4 if on else 0.0)
    for y in (14, 17):  # Toggle switches.
        rect(img, 2, y, 4, y + 2, (30, 30, 30))
        rect(img, 5, y, 7, y + 2, (30, 30, 30))
        px(img, 3, y, (200, 200, 200))
        px(img, 6, y, (200, 200, 200))
    rect(img, 3, 24, 7, 30, (60, 60, 64))  # The breaker.
    rect(img, 4, 25, 6, 27, (230, 230, 230))
    blue_panel(img, 10, 0, 31, 5, BLUE_DARK)  # The power-control box with its three lamps.
    for i, x in enumerate((13, 16, 19)):
        rect(img, x, 2, x + 1, 3, (90, 230, 120) if on or i == 0 else (40, 70, 50))
    blue_panel(img, 10, 5, 31, 31)  # The tall door.
    rect(img, 28, 15, 29, 20, (150, 154, 160))  # Its handle.
    rect(img, 10, 31, 32, 32, (16, 30, 54))
    return img


def transmitter_side():
    img = canvas(16, 16, BLUE)
    for y in range(16):
        px(img, 0, y, BLUE_EDGE)
        px(img, 15, y, BLUE_EDGE)
    for y in range(3, 13, 2):
        for x in range(4, 12):
            px(img, x, y, BLUE_DARK)  # Louvres.
    return img


def transmitter_top():
    img = canvas(16, 16, (40, 44, 50))
    for x in range(16):
        px(img, x, 0, BLUE_EDGE)
        px(img, x, 15, BLUE_EDGE)
    return img


def front_part(name, tx0, ty0, tx1, ty1, depth, z=1.0, texture='#front', px_per_unit=2.0, size=16.0, edge='#side'):
    """A part standing proud of a front face at z (north), covering texture pixels tx0..tx1, ty0..ty1 of a texture
    drawn as seen from the front (its u 0 at the viewer's left, the model's +x). Its front shows that patch; its sides
    show the patch's own edge pixels, so a panel looks like a thick plate of itself."""
    u0, v0, u1, v1 = tx0 / px_per_unit, ty0 / px_per_unit, tx1 / px_per_unit, ty1 / px_per_unit
    x0, x1 = size - u1, size - u0
    y0, y1 = size - v1, size - v0
    e = 0.5 / px_per_unit * 2  # Half a texture pixel's strip, in uv.
    return box(name, [x0, y0, z - depth], [x1, y1, z], {
        'north': {'uv': [u0, v0, u1, v1], 'texture': texture},
        'east': {'uv': [u0, v0, u0 + e, v1], 'texture': texture},
        'west': {'uv': [u1 - e, v0, u1, v1], 'texture': texture},
        'up': {'uv': [u0, v0, u1, v0 + e], 'texture': texture},
        'down': {'uv': [u0, v1 - e, u1, v1], 'texture': texture}})


def transmitter_model(half, on):
    front = '#front'
    f = {'north': {'uv': [0, 0, 16, 16], 'texture': front},
         'south': {'uv': [0, 0, 16, 16], 'texture': '#side'},
         'east': {'uv': [0, 0, 16, 16], 'texture': '#side'},
         'west': {'uv': [0, 0, 16, 16], 'texture': '#side'},
         'up': {'uv': [0, 0, 16, 16], 'texture': '#top' if half == 'upper' else '#side'},
         'down': {'uv': [0, 0, 16, 16], 'texture': '#top' if half == 'lower' else '#side'}}
    if half == 'upper':
        del f['down']
    else:
        del f['up']
    # The cabinet stands back 1.5 from the front of the block; its panels, meters and handles come forward of it.
    elements = [box('cabinet', [0, 0, 1.5], [16, 16, 16], f)]
    if half == 'upper':
        elements += [
            front_part('header', 0, 0, 32, 3, 0.5, 1.5),
            front_part('strip', 1, 3, 9, 32, 0.75, 1.5),
            front_part('knob_1', 4, 5, 7, 8, 0.5, 0.75),
            front_part('knob_2', 4, 10, 7, 13, 0.5, 0.75),
            front_part('control_panel', 19, 3, 31, 18, 0.5, 1.5),
            front_part('switch_strip', 10, 18, 31, 21, 0.25, 1.5),
            front_part('exciter', 10, 22, 31, 30, 1.0, 1.5),
            front_part('exciter_handle_l', 10, 23, 11, 29, 0.5, 0.5),
            front_part('exciter_handle_r', 30, 23, 31, 29, 0.5, 0.5),
        ]
    else:
        elements += [
            front_part('strip', 1, 0, 9, 32, 0.75, 1.5),
            front_part('breaker', 3, 24, 7, 30, 0.5, 0.75),
            front_part('power_box', 10, 0, 31, 5, 0.75, 1.5),
            front_part('door', 10, 5, 31, 31, 0.4, 1.5),
            front_part('door_handle', 28, 15, 29, 20, 0.75, 1.1),
            front_part('kick', 0, 31, 32, 32, 0.25, 1.5),
        ]
    suffix = '_on' if on else ''
    return {'credit': 'tools/airband_fm_models.py', 'parent': 'block/block', 'texture_size': [32, 32],
            'textures': {'front': 'radiocraft:block/fm_broadcast_transmitter_' + half + suffix,
                         'side': 'radiocraft:block/fm_broadcast_transmitter_side',
                         'top': 'radiocraft:block/fm_broadcast_transmitter_top',
                         'particle': 'radiocraft:block/fm_broadcast_transmitter_side'},
            'elements': elements}


# ============================== FM BROADCAST RECEIVER ==============================
# After the maintainer's photo of a 1970s walnut table radio (no maker's name on it): a walnut case with a lip round
# the front, the speaker grille (woven tan cloth in a chrome frame) on the left, and on the right a chrome-framed
# brushed panel with a tall dial window (an orange scale and pointer) and four chrome knobs: TUNING (big, top),
# VOLUME, then BASS and TREBLE side by side, two small switches under them.
# The front is 14 x 8 units at 4 px a unit: 56 x 32 px of a 64 x 64 texture, drawn as seen from the front.
# FmBroadcastReceiverScreen draws the same layout; keep the two together.

WALNUT = (92, 54, 32)
WALNUT_DARK = (66, 37, 21)
WALNUT_LIGHT = (112, 68, 40)
CHROME = (196, 198, 202)
CHROME_DARK = (128, 130, 136)
BRUSHED = (176, 176, 172)
CLOTH = (172, 124, 70)
DIAL_GLASS = (34, 22, 14)
DIAL_ORANGE = (240, 136, 40)

# Where things are on the front, texture pixels x0, y0, x1, y1.
RX_GRILLE = (5, 5, 32, 27)
RX_PANEL = (34, 4, 52, 28)
RX_DIAL = (36, 6, 42, 26)
RX_TUNING = (43, 6, 50, 13)
RX_VOLUME = (44, 15, 49, 20)
RX_BASS = (43, 21, 46, 24)
RX_TREBLE = (47, 21, 50, 24)
RX_SWITCHES = ((43, 25, 45, 27), (48, 25, 50, 27))


def receiver_front():
    img = canvas(64, 64, WALNUT_DARK)
    rect(img, 0, 0, 56, 32, (24, 18, 14))  # Shadow behind the lip.
    # The grille: a chrome frame round woven tan cloth.
    x0, y0, x1, y1 = RX_GRILLE
    rect(img, x0, y0, x1, y1, CHROME)
    for x in range(x0, x1):
        px(img, x, y1 - 1, CHROME_DARK)
    for y in range(y0, y1):
        px(img, x1 - 1, y, CHROME_DARK)
    for y in range(y0 + 1, y1 - 1):
        for x in range(x0 + 1, x1 - 1):
            weave = (x + y) % 2
            c = (CLOTH[0] + 14, CLOTH[1] + 12, CLOTH[2] + 8) if weave else (CLOTH[0] - 18, CLOTH[1] - 16, CLOTH[2] - 12)
            if (x * 3 + y * 5) % 13 == 0:
                c = (CLOTH[0] - 30, CLOTH[1] - 26, CLOTH[2] - 18)
            px(img, x, y, c)
    rect(img, x0 + 2, y0 + 2, x0 + 7, y0 + 4, (214, 214, 210))  # A blank nameplate.
    for x in range(x0 + 2, x0 + 7):
        px(img, x, y0 + 3, (160, 160, 160))
    # The panel: a chrome frame round brushed aluminium.
    x0, y0, x1, y1 = RX_PANEL
    rect(img, x0, y0, x1, y1, CHROME)
    for x in range(x0, x1):
        px(img, x, y0, (226, 228, 232))
        px(img, x, y1 - 1, CHROME_DARK)
    for y in range(y0 + 1, y1 - 1):
        for x in range(x0 + 1, x1 - 1):
            px(img, x, y, BRUSHED if (y % 3) else (166, 166, 162))
    # The dial window: dark glass, a lit orange scale up its middle, white ticks, the pointer.
    x0, y0, x1, y1 = RX_DIAL
    rect(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, CHROME_DARK)
    rect(img, x0, y0, x1, y1, DIAL_GLASS)
    for y in range(y0 + 1, y1 - 1):
        px(img, x0 + 3, y, (130, 70, 24))
        if (y - y0) % 2 == 1:
            px(img, x0 + 1, y, (220, 210, 190))
            px(img, x0 + 2, y, (220, 210, 190) if (y - y0) % 4 == 1 else DIAL_GLASS)
    # No pointer here: FmBroadcastReceiverRenderer draws it on the DIAL element at the tuned frequency.
    px(img, x0 + 4, y0 + 2, DIAL_ORANGE)  # The FM lamp.
    # Under the knobs: dark sockets; the knobs themselves are elements.
    for r in (RX_TUNING, RX_VOLUME, RX_BASS, RX_TREBLE) + RX_SWITCHES:
        rect(img, r[0], r[1], r[2], r[3], (60, 60, 62))
    return img


def wood_texture():
    """Walnut: long dark grain lines with a lighter figure between."""
    img = canvas(16, 16, WALNUT)
    for y in range(16):
        for x in range(16):
            wave = (y * 2 + (x // 5)) % 7
            if wave == 0:
                px(img, x, y, WALNUT_DARK)
            elif wave == 3 and (x + y) % 3 == 0:
                px(img, x, y, WALNUT_LIGHT)
    return img


def receiver_case():
    """The back: dark hardboard with vent slots."""
    img = canvas(16, 16, (40, 32, 26))
    for y in range(3, 13, 2):
        for x in range(3, 13):
            px(img, x, y, (16, 12, 10))
    return img


def receiver_model():
    wood = '#wood'
    chrome = {f: {'uv': [0, 0, 16, 16], 'texture': '#chrome'} for f in ('north', 'east', 'west', 'up', 'down')}

    def knob(name, r, depth, z=5.0):
        # Texture pixels to the model: x = 15 - px / 4 (the front's u 0 is at x 15), y = 8 - py / 4.
        return box(name, [15 - r[2] / 4, 8 - r[3] / 4, z - depth], [15 - r[0] / 4, 8 - r[1] / 4, z], chrome)

    t = RX_TUNING
    elements = [
        box('body', [1, 0, 5], [15, 8, 11.5], {
            'north': {'uv': [0, 0, 14, 8], 'texture': '#front'},
            'south': {'uv': [1, 4, 15, 12], 'texture': '#back'},
            'east': {'uv': [0, 4, 6.5, 12], 'texture': wood},
            'west': {'uv': [0, 4, 6.5, 12], 'texture': wood},
            'up': {'uv': [1, 0, 15, 6.5], 'texture': wood},
            'down': {'uv': [1, 0, 15, 6.5], 'texture': '#back'}}),
        # The walnut lip round the front, standing half a unit proud.
        box('lip_top', [1, 7.25, 4.5], [15, 8, 5], faces(wood, (1, 0, 15, 0.75), skip=('south',))),
        box('lip_bottom', [1, 0, 4.5], [15, 0.75, 5], faces(wood, (1, 0, 15, 0.75), skip=('south',))),
        box('lip_left', [14.25, 0.75, 4.5], [15, 7.25, 5], faces(wood, (0, 1, 0.75, 7.5), skip=('south',))),
        box('lip_right', [1, 0.75, 4.5], [1.75, 7.25, 5], faces(wood, (0, 1, 0.75, 7.5), skip=('south',))),
        # The chrome frames stand a little proud of the front.
        box('panel_frame', [15 - RX_PANEL[2] / 4, 8 - RX_PANEL[3] / 4, 4.85], [15 - RX_PANEL[0] / 4, 8 - RX_PANEL[1] / 4, 5],
            {'north': {'uv': [RX_PANEL[0] / 4, RX_PANEL[1] / 4, RX_PANEL[2] / 4, RX_PANEL[3] / 4], 'texture': '#front'},
             'east': {'uv': [0, 0, 1, 16], 'texture': '#chrome'}, 'west': {'uv': [0, 0, 1, 16], 'texture': '#chrome'},
             'up': {'uv': [0, 0, 16, 1], 'texture': '#chrome'}, 'down': {'uv': [0, 0, 16, 1], 'texture': '#chrome'}}),
        # The dial glass, just proud of the panel, for FmBroadcastReceiverRenderer to find and draw the pointer on.
        box('DIAL', [15 - RX_DIAL[2] / 4, 8 - RX_DIAL[3] / 4, 4.8], [15 - RX_DIAL[0] / 4, 8 - RX_DIAL[1] / 4, 4.85],
            {'north': {'uv': [RX_DIAL[0] / 4, RX_DIAL[1] / 4, RX_DIAL[2] / 4, RX_DIAL[3] / 4], 'texture': '#front'}}),
        # Knobs: the big tuning knob with a cap, volume, bass and treble, and two small switches.
        knob('tuning_knob', t, 1.0, 4.85),
        box('tuning_cap', [15 - (t[2] - 1) / 4, 8 - (t[3] - 1) / 4, 3.6], [15 - (t[0] + 1) / 4, 8 - (t[1] + 1) / 4, 3.85], chrome),
        knob('volume_knob', RX_VOLUME, 0.8, 4.85),
        knob('bass_knob', RX_BASS, 0.6, 4.85),
        knob('treble_knob', RX_TREBLE, 0.6, 4.85),
        knob('switch_1', RX_SWITCHES[0], 0.3, 4.85),
        knob('switch_2', RX_SWITCHES[1], 0.3, 4.85),
    ]
    return {'credit': 'tools/airband_fm_models.py', 'parent': 'block/block', 'texture_size': [64, 64],
            'textures': {'front': 'radiocraft:block/fm_broadcast_receiver_front', 'wood': 'radiocraft:block/fm_broadcast_receiver_wood',
                         'back': 'radiocraft:block/fm_broadcast_receiver_case', 'chrome': 'radiocraft:block/airband_radio_knob_chrome',
                         'particle': 'radiocraft:block/fm_broadcast_receiver_wood'},
            'elements': elements}


# ============================== AIRBAND GROUND PLANE ==============================
# Two blocks: a galvanised mast, four radials drooping from a clamp near the bottom of the upper block, a white whip.

def zinc_texture():
    """Galvanised steel: pale gold to grey along its length, with a little shimmer."""
    img = canvas(16, 16, (176, 168, 128))
    for y in range(16):
        for x in range(16):
            t = (x + y * 0.5) / 24.0
            c = (int(196 - 40 * t), int(182 - 24 * t), int(120 + 20 * t))
            if (x * 7 + y * 3) % 11 == 0:
                c = (min(255, c[0] + 22), min(255, c[1] + 22), min(255, c[2] + 18))
            px(img, x, y, c)
    for y in range(16):  # The black end caps of the radials (their u 15).
        px(img, 15, y, (24, 24, 26))
    return img


def radome_texture():
    img = canvas(16, 16, (236, 236, 230))
    for y in range(16):
        px(img, 0, y, (214, 214, 208))
        px(img, 15, y, (220, 220, 214))
    return img


def ground_plane_model(half):
    z = '#zinc'
    if half == 'lower':
        elements = [
            box('mast', [7.25, 0, 7.25], [8.75, 16, 8.75], faces(z, (2, 0, 4, 16))),
            box('coax', [7.5, 0, 8.75], [8.5, 1, 10.5], faces('#coax')),  # The pigtail out of its foot.
        ]
    else:
        def radial(name, frm, to, axis, angle):
            # Along its length the texture's last column, black, is the end cap.
            return box(name, frm, to, {f: {'uv': [0, 0, 16, 1], 'texture': z} for f in ('north', 'east', 'south', 'west', 'up', 'down')},
                       {'angle': angle, 'axis': axis, 'origin': [8, 3.75, 8]})
        elements = [
            box('mast', [7.25, 0, 7.25], [8.75, 4.5, 8.75], faces(z, (2, 0, 4, 5))),
            box('clamp', [6.5, 2.75, 6.5], [9.5, 4.75, 9.5], faces('#clamp')),
            # Radials 16 long, drooping 22.5 degrees: east and west turn about z, north and south about x.
            radial('radial_east', [8, 3.5, 7.75], [24, 4, 8.25], 'z', -22.5),
            radial('radial_west', [-8, 3.5, 7.75], [8, 4, 8.25], 'z', 22.5),
            radial('radial_south', [7.75, 3.5, 8], [8.25, 4, 24], 'x', 22.5),
            radial('radial_north', [7.75, 3.5, -8], [8.25, 4, 8], 'x', -22.5),
            box('whip', [7.0, 4.75, 7.0], [9.0, 15.5, 9.0], faces('#radome', (4, 0, 8, 11))),
            box('whip_cap', [7.25, 15.5, 7.25], [8.75, 16, 8.75], faces('#radome', (4, 0, 6, 1))),
        ]
    return {'credit': 'tools/airband_fm_models.py', 'parent': 'block/block',
            'textures': {'zinc': 'radiocraft:block/airband_ground_plane_zinc', 'radome': 'radiocraft:block/airband_ground_plane_radome',
                         'clamp': 'radiocraft:block/airband_antenna_whip', 'coax': 'radiocraft:block/airband_radio_case',
                         'particle': 'radiocraft:block/airband_ground_plane_zinc'},
            'elements': elements}


def write_ground_plane():
    save(zinc_texture(), 'airband_ground_plane_zinc')
    save(radome_texture(), 'airband_ground_plane_radome')
    for half in ('lower', 'upper'):
        write(ROOT + '/models/block/airband_ground_plane_%s.json' % half, ground_plane_model(half))
    write(ROOT + '/blockstates/airband_ground_plane.json', {'variants': {
        'half=lower': {'model': 'radiocraft:block/airband_ground_plane_lower'},
        'half=upper': {'model': 'radiocraft:block/airband_ground_plane_upper'}}})
    # The item shows the whole antenna, both halves, scaled down.
    lower, upper = ground_plane_model('lower'), ground_plane_model('upper')
    for e in upper['elements']:
        e['from'][1] += 16
        e['to'][1] += 16
        if 'rotation' in e:
            e['rotation']['origin'][1] += 16
    write(ROOT + '/models/item/airband_ground_plane.json', {
        'parent': 'block/block', 'textures': lower['textures'], 'elements': lower['elements'] + upper['elements'],
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, -4, 0], 'scale': [0.4, 0.4, 0.4]},
                    'ground': {'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]},
                    'fixed': {'translation': [0, -4, 0], 'scale': [0.4, 0.4, 0.4]},
                    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
                    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -2, 0], 'scale': [0.3, 0.3, 0.3]}}})


def main(parts=('airband', 'transmitter', 'receiver')):
    if 'airband' in parts:
        write_airband()
    if 'transmitter' in parts:
        write_transmitter()
    if 'receiver' in parts:
        write_receiver()
    print('ok')


def write_airband():
    save(airband_front(), 'airband_radio_front')
    save(airband_case(), 'airband_radio_case')
    save(knob_texture((26, 26, 28), (60, 60, 64)), 'airband_radio_knob')
    save(knob_texture(CHROME, (150, 152, 156)), 'airband_radio_knob_chrome')
    write(ROOT + '/models/block/airband_radio.json', airband_model())

    save(white_texture(), 'airband_antenna_fin')
    save(steel_texture(), 'airband_antenna_whip')
    write_ground_plane()
    write(ROOT + '/models/block/airband_antenna.json', antenna_model())
    write(ROOT + '/models/item/airband_antenna.json', {'parent': 'radiocraft:block/airband_antenna'})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        variants['face=floor,facing=' + facing] = {'model': 'radiocraft:block/airband_antenna', **({'y': y} if y else {})}
        # Hanging under a block: turned over, so its whip still rakes back the way it faces.
        variants['face=ceiling,facing=' + facing] = {'model': 'radiocraft:block/airband_antenna', 'x': 180, 'y': (y + 180) % 360}
        # Never placed on a wall (placement gives floor or ceiling), but the state exists and needs a model.
        variants['face=wall,facing=' + facing] = {'model': 'radiocraft:block/airband_antenna', **({'y': y} if y else {})}
    write(ROOT + '/blockstates/airband_antenna.json', {'variants': variants})


def write_transmitter():
    for on in (False, True):
        s = '_on' if on else ''
        save(transmitter_upper(on), 'fm_broadcast_transmitter_upper' + s)
        save(transmitter_lower(on), 'fm_broadcast_transmitter_lower' + s)
        for half in ('lower', 'upper'):
            write(ROOT + '/models/block/fm_broadcast_transmitter_%s%s.json' % (half, s), transmitter_model(half, on))
    save(transmitter_side(), 'fm_broadcast_transmitter_side')
    save(transmitter_top(), 'fm_broadcast_transmitter_top')
    write(ROOT + '/models/item/fm_broadcast_transmitter.json', {'parent': 'radiocraft:item/fm_broadcast_transmitter_full'})
    # The item shows both halves: a model with the lower half and the upper one a block up.
    lower = transmitter_model('lower', False)
    upper = transmitter_model('upper', False)
    whole = {'parent': 'block/block', 'texture_size': [32, 32],
             'textures': {'lower': 'radiocraft:block/fm_broadcast_transmitter_lower', 'upper': 'radiocraft:block/fm_broadcast_transmitter_upper',
                          'side': 'radiocraft:block/fm_broadcast_transmitter_side', 'top': 'radiocraft:block/fm_broadcast_transmitter_top',
                          'particle': 'radiocraft:block/fm_broadcast_transmitter_side'},
             'elements': [], 'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, -3, 0], 'scale': [0.42, 0.42, 0.42]},
                                         'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
                                         'fixed': {'rotation': [0, 0, 0], 'translation': [0, -4, 0], 'scale': [0.4, 0.4, 0.4]},
                                         'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.3, 0.3, 0.3]},
                                         'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]}}}
    for half, model, dy in (('lower', lower, 0), ('upper', upper, 16)):
        for e in model['elements']:
            e = json.loads(json.dumps(e))
            e['from'][1] += dy
            e['to'][1] += dy
            for face in e['faces'].values():
                if face['texture'] == '#front':
                    face['texture'] = '#' + half
            whole['elements'].append(e)
    write(ROOT + '/models/item/fm_broadcast_transmitter_full.json', whole)
    # The bottom is the radio (fm_broadcast_transmitter), the top its own block (fm_broadcast_transmitter_top).
    for block, half in (('fm_broadcast_transmitter', 'lower'), ('fm_broadcast_transmitter_top', 'upper')):
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for on in ('false', 'true'):
                v = {'model': 'radiocraft:block/fm_broadcast_transmitter_%s%s' % (half, '_on' if on == 'true' else '')}
                if y:
                    v['y'] = y
                variants['facing=%s,on_air=%s' % (facing, on)] = v
        write(ROOT + '/blockstates/%s.json' % block, {'variants': variants})


def write_receiver():
    save(receiver_front(), 'fm_broadcast_receiver_front')
    save(wood_texture(), 'fm_broadcast_receiver_wood')
    save(receiver_case(), 'fm_broadcast_receiver_case')
    write(ROOT + '/models/block/fm_broadcast_receiver.json', receiver_model())


if __name__ == '__main__':
    import sys
    main(tuple(sys.argv[1:]) or ('airband', 'transmitter', 'receiver'))
