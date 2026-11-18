"""Model and texture for the Headless APRS TNC (2026-11-18), after the maintainer's photo of a PacComm TINY-2: a grey
ribbed case, a blue front with a white swoosh, a round striped logo and PACKET TNC, RC-TNC on the right, the DCD /
PTT / STA / CON / PWR lamps along the bottom and an ON/OFF rocker. Its screen (HeadlessTncScreen) draws the same face.

The box is 12 x 4 x 11 pixels, front to the north. The texture is 256 x 256 at 8 texels a model pixel: three fronts
(off; on with PWR lit; on with PWR and PTT lit) and the ribbed case. Writes the texture, the three block models, the
item model and the blockstate (facing x on x ptt). Run from the repo root.
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = 'src/main/resources/assets/radiocraft'
NAME = 'headless_tnc'
S = 8  # Texels a model pixel.
X0, X1, Y0, Y1, Z0, Z1 = 2, 14, 0, 4, 3, 14
FW, FH = (X1 - X0) * S, (Y1 - Y0) * S  # The front: 96 x 32.
FRONTS = {'off': 0, 'on': FH, 'ptt': 2 * FH}  # Their y on the sheet.
CASE_Y = 3 * FH  # The ribbed case below them, 96 x 96.

FONT = {  # 3 x 5.
    'A': '010101111101101', 'C': '011100100100011', 'D': '110101101101110', 'E': '111100110100111',
    'F': '111100110100100', 'K': '101110100110101', 'N': '110101101101101', 'O': '010101101101010',
    'P': '110101110100100', 'R': '110101110101101', 'S': '011100010001110', 'T': '111010010010010',
    'W': '101101101111101', '-': '000000111000000', '/': '001001010100100', ' ': '000000000000000',
}

BLUE, BLUE_EDGE, WHITE = (46, 125, 196), (28, 90, 148), (244, 246, 248)
CASE, RIB_DARK, RIB_LIGHT = (154, 156, 158), (126, 128, 130), (178, 180, 182)


def text(d, x, y, s, colour, scale=1):
    for ch in s:
        glyph = FONT[ch]
        for i, bit in enumerate(glyph):
            if bit == '1':
                gx, gy = x + (i % 3) * scale, y + (i // 3) * scale
                d.rectangle([gx, gy, gx + scale - 1, gy + scale - 1], fill=colour)
        x += 4 * scale


def width(s, scale=1):
    return len(s) * 4 * scale - scale


def front(img, oy, pwr, ptt):
    d = ImageDraw.Draw(img)
    d.rectangle([0, oy, FW - 1, oy + FH - 1], fill=BLUE_EDGE)
    d.rectangle([1, oy + 1, FW - 2, oy + FH - 2], fill=BLUE)
    for x in range(1, FW - 1):  # The swoosh.
        t = x / (FW - 1)
        y = round(oy + FH - 4 - 6 * t * t)  # Under the lamp labels, rising to the rocker.
        d.rectangle([x, y, x, y + 1], fill=WHITE)
    for sx in (3, FW - 5):  # Screws.
        d.rectangle([sx, oy + 3, sx + 1, oy + 4], fill=(150, 152, 154))
    for i, w in enumerate((3, 5, 7, 7, 5, 3)):  # The logo's stripes.
        d.rectangle([9 + (7 - w) // 2, oy + 3 + i, 9 + (7 - w) // 2 + w - 1, oy + 3 + i], fill=WHITE)
    text(d, 18, oy + 4, 'PACKET TNC', WHITE)
    text(d, FW - 7 - width('RC-TNC'), oy + 4, 'RC-TNC', WHITE)
    lamps = (('DCD', (60, 224, 76), (28, 74, 34), False), ('PTT', (255, 58, 46), (90, 24, 20), ptt),
             ('STA', (255, 216, 46), (90, 78, 20), False), ('CON', (60, 224, 76), (28, 74, 34), False),
             ('PWR', (255, 58, 46), (90, 24, 20), pwr))
    for i, (label, lit, dark, on) in enumerate(lamps):
        cx = 12 + i * 14
        d.rectangle([cx - 1, oy + 15, cx + 1, oy + 17], fill=lit if on else dark)
        if on:
            d.point((cx - 1, oy + 15), fill=(255, 255, 255))
        text(d, cx - width(label) // 2, oy + 20, label, WHITE)
    rx = FW - 18  # The rocker, the pushed side darker.
    d.rectangle([rx - 1, oy + 13, rx + 10, oy + 19], fill=(74, 76, 78))
    d.rectangle([rx, oy + 14, rx + 9, oy + 18], fill=(232, 228, 216))
    if pwr:
        d.rectangle([rx + 5, oy + 14, rx + 9, oy + 18], fill=(200, 194, 180))
    else:
        d.rectangle([rx, oy + 14, rx + 4, oy + 18], fill=(200, 194, 180))


def case(img):
    d = ImageDraw.Draw(img)
    for y in range(CASE_Y, CASE_Y + 96):
        d.line([0, y, 95, y], fill=RIB_DARK if (y - CASE_Y) % 4 == 0 else RIB_LIGHT if (y - CASE_Y) % 4 == 2 else CASE)


def texture():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    front(img, FRONTS['off'], False, False)
    front(img, FRONTS['on'], True, False)
    front(img, FRONTS['ptt'], True, True)
    case(img)
    os.makedirs(ROOT + '/textures/block', exist_ok=True)
    img.save(ROOT + '/textures/block/' + NAME + '.png')


def uv(x, y, w, h):
    """A texel rectangle as model uv (16 per 256 texels)."""
    return [x / 16, y / 16, (x + w) / 16, (y + h) / 16]


def model(kind):
    fy = FRONTS[kind]
    side_w, side_h = (Z1 - Z0) * S, FH
    faces = {
        'north': {'uv': uv(0, fy, FW, FH), 'texture': '#all'},
        'south': {'uv': uv(0, CASE_Y, FW, FH), 'texture': '#all'},
        'east': {'uv': uv(0, CASE_Y, side_w, side_h), 'texture': '#all'},
        'west': {'uv': uv(0, CASE_Y, side_w, side_h), 'texture': '#all'},
        'up': {'uv': uv(0, CASE_Y, FW, side_w), 'texture': '#all'},
        'down': {'uv': uv(0, CASE_Y, FW, side_w), 'texture': '#all', 'cullface': 'down'},
    }
    return {'credit': 'tools/headless_tnc_model.py', 'parent': 'block/block',
            'textures': {'all': 'radiocraft:block/' + NAME, 'particle': 'radiocraft:block/' + NAME},
            'elements': [{'name': 'case', 'from': [X0, Y0, Z0], 'to': [X1, Y1, Z1], 'faces': faces}],
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.9, 0.9, 0.9]}}}


def blockstate():
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for on in ('false', 'true'):
            for ptt in ('false', 'true'):
                kind = 'off' if on == 'false' else 'ptt' if ptt == 'true' else 'on'
                v = {'model': 'radiocraft:block/' + NAME + ('' if kind == 'off' else '_' + kind)}
                if y:
                    v['y'] = y
                variants['facing=%s,on=%s,ptt=%s' % (facing, on, ptt)] = v
    return {'variants': variants}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    texture()
    write(ROOT + '/models/block/' + NAME + '.json', model('off'))
    write(ROOT + '/models/block/' + NAME + '_on.json', model('on'))
    write(ROOT + '/models/block/' + NAME + '_ptt.json', model('ptt'))
    write(ROOT + '/models/item/' + NAME + '.json', {'parent': 'radiocraft:block/' + NAME + '_on'})
    write(ROOT + '/blockstates/' + NAME + '.json', blockstate())
    print('wrote', NAME)
