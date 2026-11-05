"""Placeholder item texture and model for the Radio Cloner (1.12 playtest fixes 2).

Writes textures/item/radio_cloner.png (16x16) and models/item/radio_cloner.json with only the standard library. A
placeholder: the proper art is on doc/ModelDesignBounty.md. Run: python tools/radio_cloner_assets.py
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")
CLEAR = (0, 0, 0, 0)


def png(path, pixels):
    """Writes a 16x16 RGBA PNG from rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def cloner():
    """A small grey box with a two-digit LED display, a green READ and a red WRITE lamp, and a coiled cable to a plug."""
    case, edge, lcd, ink, green, red, cable, plug = (92, 96, 102, 255), (48, 50, 54, 255), (150, 170, 120, 255), \
        (40, 60, 30, 255), (60, 200, 70, 255), (210, 50, 40, 255), (24, 24, 26, 255), (190, 160, 70, 255)
    rows = [[CLEAR] * 16 for _ in range(16)]
    for y in range(2, 11):
        for x in range(2, 12):
            rows[y][x] = edge if x in (2, 11) or y in (2, 10) else case
    for y in range(4, 6):
        for x in range(4, 10):
            rows[y][x] = lcd
    rows[4][5] = rows[4][7] = rows[5][6] = rows[5][8] = ink  # Digits.
    rows[8][4] = green
    rows[8][9] = red
    for x, y in ((12, 9), (13, 10), (13, 11), (12, 12), (11, 13), (12, 14), (13, 14)):
        rows[y][x] = cable  # The cloning cable, coiled.
    rows[14][14] = rows[13][14] = plug
    return rows


def main():
    png(os.path.join(ROOT, "textures", "item", "radio_cloner.png"), cloner())
    with open(os.path.join(ROOT, "models", "item", "radio_cloner.json"), "w", newline="\n") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "radiocraft:item/radio_cloner"}}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
