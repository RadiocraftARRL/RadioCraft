"""Placeholder art for the marine radios (1.12 step 3).

Writes, with only the standard library:
  textures/item/marine_handheld.png       dark grey handheld with a yellow float collar and a blue-grey display
  textures/block/marine_radio_case.png    white case of the fixed-mount radio
  textures/block/marine_radio_front.png   its dark front panel: display, channel knob, keys, TX/RX lamps
  models/item/marine_handheld.json
  models/block/marine_radio.json          with a DISPLAY element (RadioDisplays reads it) and a mic hook
The proper models and textures are on doc/ModelDesignBounty.md. Run: python tools/marine_radio_assets.py
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


def handheld(x, y):
    body, edge, collar, black, lcd, key, red = (45, 50, 58, 255), (30, 34, 40, 255), (232, 176, 32, 255), (15, 15, 15, 255), (184, 201, 216, 255), (90, 96, 104, 255), (200, 40, 40, 255)
    if x == 7 and 0 <= y <= 3:
        return black  # Antenna.
    if not (5 <= x <= 10 and 4 <= y <= 15):
        return CLEAR
    if y >= 13:
        return collar  # The yellow float collar at the bottom.
    if 6 <= x <= 9 and 5 <= y <= 7:
        return lcd
    if (x, y) == (9, 4):
        return red  # The 16/9 key.
    if 6 <= x <= 9 and 9 <= y <= 11 and (x + y) % 2 == 0:
        return key
    return edge if x in (5, 10) or y == 4 else body


def block_case(x, y):
    v = 205 if x in (0, 15) or y in (0, 15) else 236
    return (v, v, v - 4, 255)


def marine_front(x, y):
    panel, lcd, knob, key, lamp, red = (32, 36, 42, 255), (150, 170, 190, 255), (90, 90, 90, 255), (70, 76, 84, 255), (24, 24, 24, 255), (190, 40, 40, 255)
    if 5 <= x <= 11 and 3 <= y <= 9:
        return lcd
    if 12 <= x <= 14 and 4 <= y <= 7:
        return knob
    if 1 <= x <= 3 and y in (4, 8):
        return lamp  # TX and RX, lit by the renderer.
    if y == 12 and 2 <= x <= 13 and x % 3 != 1:
        return red if x in (2, 3) else key  # The 16/9 key (red) and the others.
    return panel


def faces(texture, uv=(0, 0, 16, 16)):
    return {face: {"uv": list(uv), "texture": texture} for face in ("north", "east", "south", "west", "up", "down")}


def marine_model():
    """Faces north as drawn (the blockstate turns it)."""
    return {
        "credit": "Placeholder from tools/marine_radio_assets.py (see doc/ModelDesignBounty.md)",
        "parent": "block/block",
        "textures": {"case": "radiocraft:block/marine_radio_case", "front": "radiocraft:block/marine_radio_front",
                     "particle": "radiocraft:block/marine_radio_case"},
        "elements": [
            {"name": "body", "from": [2, 0, 4], "to": [14, 5, 15], "faces": faces("#case")},
            {"name": "front", "from": [2.5, 0.5, 3.75], "to": [13.5, 4.5, 4], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#front"}}},
            {"name": "DISPLAY", "from": [5.5, 1.75, 3.6], "to": [11.5, 4.0, 3.75], "faces": {"north": {"uv": [5, 3, 12, 10], "texture": "#front"}}},
            {"name": "knob", "from": [12, 2, 3.25], "to": [13, 3, 3.75], "faces": faces("#front", (12, 4, 15, 7))},
            {"name": "mic_hook", "from": [14, 1, 6], "to": [15, 4, 8], "faces": faces("#front", (12, 4, 15, 7))},
        ],
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    png(os.path.join(ASSETS, "textures", "item", "marine_handheld.png"), grid(handheld))
    png(os.path.join(ASSETS, "textures", "block", "marine_radio_case.png"), grid(block_case))
    png(os.path.join(ASSETS, "textures", "block", "marine_radio_front.png"), grid(marine_front))
    write_json(os.path.join(ASSETS, "models", "item", "marine_handheld.json"),
               {"parent": "item/generated", "textures": {"layer0": "radiocraft:item/marine_handheld"}})
    write_json(os.path.join(ASSETS, "models", "block", "marine_radio.json"), marine_model())


if __name__ == "__main__":
    main()
