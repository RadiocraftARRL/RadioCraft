"""Draws the Fire Alarm Key item texture (2026-11-12), from the maintainer's photo of a 102 key: a square stippled
nickel bow with a triangle hole at its top left and a stamped number plate, a tang off its top right and a round tubular
barrel running out to the right with a slot at its tip. Run: python tools/fire_alarm_key_texture.py"""
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / 'src/main/resources/assets/radiocraft/textures/item/fire_alarm_key.png'

OUTLINE = (52, 54, 60, 255)
DARK = (104, 108, 116, 255)
MID = (150, 154, 162, 255)
LIGHT = (192, 196, 204, 255)
SHINE = (236, 238, 242, 255)
HOLE = (0, 0, 0, 0)

img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
rnd = random.Random(102)


def put(x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c


# The bow: x 0..9, y 3..13, outlined, stippled (the photo's hammered face), lit from the top left.
BX0, BX1, BY0, BY1 = 0, 9, 3, 13
for y in range(BY0, BY1 + 1):
    for x in range(BX0, BX1 + 1):
        edge = x in (BX0, BX1) or y in (BY0, BY1)
        if edge:
            c = OUTLINE
        else:
            base = LIGHT if (x + y) < 10 else MID
            r = rnd.random()
            c = DARK if r < 0.12 else SHINE if r < 0.22 else base
        put(x, y, c)
# Rounded corners.
for x, y in ((BX0, BY0), (BX1, BY0), (BX0, BY1), (BX1, BY1)):
    put(x, y, (0, 0, 0, 0))
put(BX0 + 1, BY0 + 1, OUTLINE)
put(BX1 - 1, BY1 - 1, OUTLINE)
put(BX0 + 1, BY1 - 1, OUTLINE)

# The triangle hole at the top left (point down), with a dark rim under it.
for x, y in ((2, 5), (3, 5), (4, 5), (2, 6), (3, 6), (2, 7)):
    put(x, y, HOLE)
for x, y in ((5, 5), (4, 6), (3, 7), (2, 8)):
    put(x, y, DARK)

# The stamped number plate near the bottom: a dark frame with the digits as dots.
for x in range(2, 8):
    put(x, 10, DARK)
    put(x, 12, DARK)
put(2, 11, DARK)
put(7, 11, DARK)
for x in range(3, 7):
    put(x, 11, MID)
for x in (3, 5, 6):  # "1 0 2", as far as 4 pixels go.
    put(x, 11, OUTLINE)


# The barrel: a cylinder x 10..15, y 5..9, shaded top to bottom, rounded tip with a slot.
rows = {5: OUTLINE, 6: SHINE, 7: LIGHT, 8: MID, 9: DARK, 10: OUTLINE}
for x in range(10, 16):
    for y, c in rows.items():
        put(x, y, c)
# The collar where it meets the bow (the photo's ridge), a shade darker.
for y in range(6, 10):
    put(10, y, DARK if y > 7 else MID)
    put(9, y, DARK)  # The bow's edge stays, shadowed where the barrel sits on it.
put(11, 6, SHINE)
# Rounded tip, and the slot cut in it.
put(15, 5, (0, 0, 0, 0))
put(15, 10, (0, 0, 0, 0))
put(15, 6, OUTLINE)
put(15, 9, OUTLINE)
put(15, 7, MID)
put(15, 8, DARK)
put(14, 7, OUTLINE)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print('wrote', OUT)
