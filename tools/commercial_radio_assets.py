"""Placeholder art for the commercial radios (1.12 step 2).

Writes, with only the standard library:
  textures/item/commercial_handheld_case.png      light grey case, tinted by dye (layer0, tintindex 0)
  textures/item/basic_commercial_handheld.png     antenna, knobs, LEDs and grille over the case (layer1)
  textures/item/upgraded_commercial_handheld.png  the same with a little green display
  textures/block/commercial_mobile_case.png       light grey, tinted by dye
  textures/block/commercial_mobile_front.png      front panel of the VHF mobile
  textures/block/low_band_mobile_front.png        front panel of the low band mobile
  models/item/basic_commercial_handheld.json, models/item/upgraded_commercial_handheld.json
  models/block/commercial_mobile.json, models/block/low_band_mobile.json
The proper models and textures are on doc/ModelDesignBounty.md. Run: python tools/commercial_radio_assets.py
"""
import json
import os
import struct
import zlib

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")
CLEAR = (0, 0, 0, 0)


def png(path, pixels):
    """Writes a 16x16 RGBA PNG from rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def grid(fn):
    return [[fn(x, y) for x in range(16)] for y in range(16)]


def in_case(x, y):
    """The handheld's body: a brick from x 5 to 10, y 4 to 15."""
    return 5 <= x <= 10 and 4 <= y <= 15


def handheld_case(x, y):
    if not in_case(x, y):
        return CLEAR
    edge = x in (5, 10) or y in (4, 15)
    v = 170 if edge else 215
    return (v, v, v, 255)


def handheld_details(display):
    black, knob, red, green, grille, lcd = (20, 20, 20, 255), (70, 70, 70, 255), (200, 40, 40, 255), (40, 200, 70, 255), (90, 90, 90, 255), (150, 185, 120, 255)

    def px(x, y):
        if x == 6 and 0 <= y <= 3:
            return black  # Antenna.
        if (x, y) in ((8, 2), (8, 3), (9, 2), (9, 3)):
            return knob  # Channel knob.
        if (x, y) == (7, 3):
            return knob  # Volume knob.
        if (x, y) == (7, 5):
            return green
        if (x, y) == (8, 5):
            return red
        if display and 6 <= x <= 9 and 7 <= y <= 8:
            return lcd
        if 6 <= x <= 9 and 10 <= y <= 13 and (x + y) % 2 == 0:
            return grille
        return CLEAR
    return grid(px)


def block_case(x, y):
    v = 175 if x in (0, 15) or y in (0, 15) else 215
    return (v, v, v, 255)


def mobile_front(accent):
    panel, lcd, knob, label = (40, 42, 46, 255), (120, 150, 95, 255), (90, 90, 90, 255), accent

    def px(x, y):
        if 5 <= x <= 11 and 4 <= y <= 9:
            return lcd
        if 13 <= x <= 14 and 5 <= y <= 8:
            return knob
        if 1 <= x <= 3 and y == 12:
            return label
        return panel
    return grid(px)


def item_model(details):
    return {"parent": "item/generated", "textures": {"layer0": "radiocraft:item/commercial_handheld_case", "layer1": "radiocraft:item/" + details}}


def faces(texture, tint):
    out = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        f = {"uv": [0, 0, 16, 16], "texture": texture}
        if tint:
            f["tintindex"] = 0
        out[face] = f
    return out


def mobile_model(front):
    """Faces north as drawn (the blockstate turns it). The body is tinted; the front plate, display and knob are not."""
    return {
        "credit": "Placeholder from tools/commercial_radio_assets.py (see doc/ModelDesignBounty.md)",
        "parent": "block/block",
        "textures": {"case": "radiocraft:block/commercial_mobile_case", "front": "radiocraft:block/" + front,
                     "particle": "radiocraft:block/commercial_mobile_case"},
        "elements": [
            {"name": "body", "from": [2, 0, 3], "to": [14, 4, 15], "faces": faces("#case", True)},
            {"name": "front", "from": [2.5, 0.5, 2.75], "to": [13.5, 3.5, 3], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#front"}}},
            {"name": "DISPLAY", "from": [5.5, 1.25, 2.6], "to": [11.5, 3.25, 2.75], "faces": {"north": {"uv": [5, 4, 12, 10], "texture": "#front"}}},
            {"name": "knob", "from": [12.25, 1.25, 2.25], "to": [13.25, 2.25, 2.75], "faces": faces("#front", False)},
            {"name": "mic_hook", "from": [13.5, 0.5, 4], "to": [14.5, 3.5, 6], "faces": faces("#front", False)},
        ],
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    png(os.path.join(ASSETS, "textures", "item", "commercial_handheld_case.png"), grid(handheld_case))
    png(os.path.join(ASSETS, "textures", "item", "basic_commercial_handheld.png"), handheld_details(False))
    png(os.path.join(ASSETS, "textures", "item", "upgraded_commercial_handheld.png"), handheld_details(True))
    png(os.path.join(ASSETS, "textures", "block", "commercial_mobile_case.png"), grid(block_case))
    png(os.path.join(ASSETS, "textures", "block", "commercial_mobile_front.png"), mobile_front((220, 200, 60, 255)))
    png(os.path.join(ASSETS, "textures", "block", "low_band_mobile_front.png"), mobile_front((220, 120, 40, 255)))
    write_json(os.path.join(ASSETS, "models", "item", "basic_commercial_handheld.json"), item_model("basic_commercial_handheld"))
    write_json(os.path.join(ASSETS, "models", "item", "upgraded_commercial_handheld.json"), item_model("upgraded_commercial_handheld"))
    write_json(os.path.join(ASSETS, "models", "block", "commercial_mobile.json"), mobile_model("commercial_mobile_front"))
    write_json(os.path.join(ASSETS, "models", "block", "low_band_mobile.json"), mobile_model("low_band_mobile_front"))


if __name__ == "__main__":
    main()
