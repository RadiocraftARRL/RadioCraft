"""The battery meter as an analog panel voltmeter (2026-11-11, the maintainer asked for one): a black Bakelite case
standing on its base, a cream dial behind the glass with a DC VOLTS scale from 8 to 16 V (a tick every half volt,
numbers every 2 V), a red band for a flat 12 V bank (below 11.8), a green one for charged at rest (12.4 to 12.8) and
an amber one for charging (13.8 to 14.6), the zero-adjust screw under the dial, and a red and a black binding post
on top. The needle is not painted: BatteryMeterRenderer draws it from the pivot (PIVOT below) and swings it with the
voltage.

Writes, under src/main/resources/assets/radiocraft/:
  textures/block/battery_meter.png      128 x 128: the front (8 px a model unit) and the case and post tiles
  models/block/battery_meter.json       facing north as drawn; the blockstate (datagen) turns it
Run: python tools/battery_meter_model.py [--preview DIR]   (the preview draws the needle at 8, 12.6 and 14.4 V)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from marine_radio_assets import GLYPHS  # noqa: E402  The shared 3 x 5 lettering.

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "radiocraft")

# The case, in model pixels, facing north: its front face at z CASE[2].
CASE = (1.0, 0.0, 4.0, 15.0, 13.0, 15.0)
PX = 8                               # texture pixels a model unit
FRONT_PX = (112, 104)                # the front's texture area: 14 x 13 units
DIAL = (8, 8, 104, 86)                # the cream dial, x0 y0 x1 y1 (exclusive end), texture px
PIVOT = (56, 78)                     # the needle's pivot, texture px (BatteryMeterRenderer has it in model units)
RADIUS = 60                          # the scale's outer edge from the pivot, px
LOW, HIGH = 8.0, 16.0
SWEEP = 45.0                         # degrees either side of straight up

BAKELITE = (30, 28, 27)
BAKELITE_LIGHT = (58, 54, 50)
BAKELITE_DARK = (14, 13, 12)
CREAM = (232, 224, 198)
CREAM_SHADE = (214, 205, 178)
INK = (34, 32, 30)
RED = (196, 46, 36)
GREEN = (52, 150, 70)
AMBER = (222, 150, 40)
POST_RED = (170, 30, 26)
POST_BLACK = (24, 24, 24)


def angle(volts):
    """Degrees from straight up, clockwise, for {@code volts} on the scale."""
    return -SWEEP + (min(HIGH, max(LOW, volts)) - LOW) / (HIGH - LOW) * 2.0 * SWEEP


def point(deg, r):
    return PIVOT[0] + r * math.sin(math.radians(deg)), PIVOT[1] - r * math.cos(math.radians(deg))


def text(img, x, y, s, colour):
    px = img.load()
    for i, ch in enumerate(s):
        glyph = GLYPHS.get(ch.upper(), GLYPHS[" "])
        for gy in range(5):
            for gx in range(3):
                if glyph[gy * 3 + gx] == "1":
                    px[x + i * 4 + gx, y + gy] = colour


def text_centred(img, cx, y, s, colour):
    text(img, round(cx - (len(s) * 4 - 1) / 2.0), y, s, colour)


def band(d, v0, v1, r0, r1, colour):
    """A coloured band on the scale from {@code v0} to {@code v1} volts, between radii r0 and r1."""
    steps = max(2, int((angle(v1) - angle(v0)) * 2))
    for i in range(steps + 1):
        deg = angle(v0) + (angle(v1) - angle(v0)) * i / steps
        for r in range(r0, r1 + 1):
            x, y = point(deg, r)
            d.point((round(x), round(y)), fill=colour)


def front(img, d):
    w, h = FRONT_PX
    d.rectangle([0, 0, w - 1, h - 1], fill=BAKELITE)
    d.line([0, 0, w - 1, 0], fill=BAKELITE_LIGHT)
    d.line([0, 0, 0, h - 1], fill=BAKELITE_LIGHT)
    d.line([w - 1, 0, w - 1, h - 1], fill=BAKELITE_DARK)
    d.line([0, h - 1, w - 1, h - 1], fill=BAKELITE_DARK)
    # The dial behind its glass: a dark bezel line round it, the face shaded lower down.
    x0, y0, x1, y1 = DIAL
    d.rectangle([x0 - 1, y0 - 1, x1, y1], fill=BAKELITE_DARK)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=CREAM)
    d.rectangle([x0, y1 - 6, x1 - 1, y1 - 1], fill=CREAM_SHADE)
    d.line([x0, y0, x1 - 1, y0], fill=(250, 246, 230))  # The glass catching the light along the top.
    # The scale: the arc, the bands inside it, a tick every half volt (longer every volt), numbers every two.
    band(d, LOW, 11.8, RADIUS - 9, RADIUS - 6, RED)
    band(d, 12.4, 12.8, RADIUS - 9, RADIUS - 6, GREEN)
    band(d, 13.8, 14.6, RADIUS - 9, RADIUS - 6, AMBER)
    band(d, LOW, HIGH, RADIUS, RADIUS, INK)
    v = LOW
    while v <= HIGH + 1e-6:
        major = abs(v - round(v)) < 1e-6
        r_in = RADIUS - (5 if major else 3)
        deg = angle(v)
        for r in range(r_in, RADIUS + 1):
            x, y = point(deg, r)
            d.point((round(x), round(y)), fill=INK)
        if major and int(round(v)) % 2 == 0:
            x, y = point(deg, RADIUS - 17)
            text_centred(img, x, round(y) - 2, str(int(round(v))), INK)
        v += 0.5
    text_centred(img, PIVOT[0], 62, "DC VOLTS", INK)
    # The pivot boss over the needle's root (the renderer's needle starts under it).
    d.ellipse([PIVOT[0] - 5, PIVOT[1] - 5, PIVOT[0] + 5, PIVOT[1] + 5], fill=BAKELITE)
    d.point((PIVOT[0] - 2, PIVOT[1] - 2), fill=BAKELITE_LIGHT)
    # Under the dial: the zero-adjust screw and the maker-less legend.
    d.ellipse([PIVOT[0] - 4, 90, PIVOT[0] + 4, 98], fill=(120, 116, 108))
    d.line([PIVOT[0] - 2, 94, PIVOT[0] + 2, 94], fill=BAKELITE_DARK)
    text(img, 12, 92, "12V BANK", (150, 144, 132))
    text(img, 80, 92, "- +", (150, 144, 132))


def tiles(img, d):
    d.rectangle([112, 0, 127, 15], fill=BAKELITE)       # case sides, top, back, bottom
    d.line([112, 0, 127, 0], fill=BAKELITE_LIGHT)
    d.rectangle([112, 16, 119, 23], fill=POST_RED)      # binding posts
    d.point((113, 17), fill=(220, 80, 70))
    d.rectangle([120, 16, 127, 23], fill=POST_BLACK)
    d.point((121, 17), fill=(80, 80, 80))
    d.rectangle([112, 24, 127, 31], fill=(176, 150, 80))  # brass nuts at their feet


def uv(px):
    return [round(v * 16.0 / 128.0, 4) for v in px]  # UV runs 0 to 16 over the 128 px texture


def build():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    front(img, d)
    tiles(img, d)
    tex = "radiocraft:block/battery_meter"
    case_tile, red, black, brass = uv((112, 2, 128, 16)), uv((112, 16, 120, 24)), uv((120, 16, 128, 24)), uv((112, 24, 128, 32))

    def box(name, frm, to, north, other):
        faces = {f: {"uv": other, "texture": "#t"} for f in ("east", "south", "west", "up", "down")}
        faces["north"] = {"uv": north, "texture": "#t"}
        return {"name": name, "from": frm, "to": to, "faces": faces}

    x0, y0, z0, x1, y1, z1 = CASE
    elements = [
        box("case", [x0, y0, z0], [x1, y1, z1], uv((0, 0) + FRONT_PX), case_tile),
        box("post_red", [10.0, y1, 11.0], [11.5, y1 + 1.5, 12.5], red, red),
        box("post_black", [4.5, y1, 11.0], [6.0, y1 + 1.5, 12.5], black, black),
        box("nut_red", [9.75, y1, 10.75], [11.75, y1 + 0.5, 12.75], brass, brass),
        box("nut_black", [4.25, y1, 10.75], [6.25, y1 + 0.5, 12.75], brass, brass),
    ]
    model = {
        "credit": "Made with tools/battery_meter_model.py (an analog panel voltmeter; the needle is BatteryMeterRenderer's)",
        "parent": "block/block",
        "texture_size": [128, 128],
        "textures": {"t": tex, "particle": tex},
        "elements": elements,
    }
    return img, model


def preview(img, out):
    for volts in (8.0, 12.6, 14.4):
        p = img.crop((0, 0) + FRONT_PX).convert("RGB")
        d = ImageDraw.Draw(p)
        x, y = point(angle(volts), RADIUS - 2)
        d.line([PIVOT, (x, y)], fill=(20, 20, 20), width=2)
        p.resize((FRONT_PX[0] * 4, FRONT_PX[1] * 4), Image.NEAREST).save(os.path.join(out, "battery_meter_%.1f.png" % volts))


if __name__ == "__main__":
    img, model = build()
    img.save(os.path.join(ROOT, "textures", "block", "battery_meter.png"))
    with open(os.path.join(ROOT, "models", "block", "battery_meter.json"), "w", encoding="utf-8") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    print("wrote the battery meter; pivot in model units: x %.3f y %.3f, front z %.1f, needle %.2f long"
          % (CASE[3] - PIVOT[0] / PX, CASE[4] - PIVOT[1] / PX, CASE[2], (RADIUS - 2) / PX))
    if "--preview" in sys.argv:
        preview(img, sys.argv[sys.argv.index("--preview") + 1])
