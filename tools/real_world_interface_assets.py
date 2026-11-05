"""Placeholder art for the Real-World Interface (1.12 step 11).

Writes, with only the standard library:
  textures/block/real_world_interface_case.png    dark grey rig interface box
  textures/block/real_world_interface_front.png   its front: a red licence stripe, PTT and COS lamps, two level knobs
  models/block/real_world_interface.json          a small box facing north (the blockstate turns it)
The proper model and textures are on doc/ModelDesignBounty.md. Run: python tools/real_world_interface_assets.py
"""
import json
import os
import struct
import zlib

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")


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


def case(x, y):
    v = 52 if x in (0, 15) or y in (0, 15) else 66
    return (v, v + 2, v + 6, 255)


def front(x, y):
    panel, edge = (34, 36, 40, 255), (20, 21, 24, 255)
    red, white = (200, 36, 30, 255), (235, 235, 235, 255)
    if x in (0, 15) or y in (0, 15):
        return edge
    if 1 <= y <= 3:
        return white if (x + y) % 4 == 0 else red  # The licence stripe.
    if y in (6, 7) and x in (2, 3):
        return (220, 40, 40, 255)  # PTT lamp.
    if y in (6, 7) and x in (5, 6):
        return (60, 210, 90, 255)  # COS/VOX lamp.
    if 9 <= y <= 12 and (3 <= x <= 5 or 10 <= x <= 12):
        return (150, 150, 155, 255) if (x in (4, 11) or y in (10, 11)) else (90, 90, 95, 255)  # Level knobs.
    if y in (6, 7) and 9 <= x <= 13:
        return (24, 60, 30, 255)  # Level meter.
    return panel


def face(texture, uv=(0, 0, 16, 16)):
    return {"uv": list(uv), "texture": texture}


def model():
    all_faces = {d: face("#case") for d in ("north", "east", "south", "west", "up", "down")}
    return {
        "credit": "Placeholder from tools/real_world_interface_assets.py (see doc/ModelDesignBounty.md)",
        "parent": "block/block",
        "textures": {
            "case": "radiocraft:block/real_world_interface_case",
            "front": "radiocraft:block/real_world_interface_front",
            "particle": "radiocraft:block/real_world_interface_case",
        },
        "elements": [
            {"name": "body", "from": [3, 0, 4], "to": [13, 4, 13], "faces": all_faces},
            {"name": "front", "from": [3.5, 0.5, 3.75], "to": [12.5, 3.5, 4], "faces": {"north": face("#front")}},
            {"name": "jack_left", "from": [4, 1, 13], "to": [5, 2, 13.5],
             "faces": {d: face("#front", (2, 6, 4, 8)) for d in ("north", "east", "south", "west", "up", "down")}},
            {"name": "jack_right", "from": [11, 1, 13], "to": [12, 2, 13.5],
             "faces": {d: face("#front", (5, 6, 7, 8)) for d in ("north", "east", "south", "west", "up", "down")}},
        ],
    }


def main():
    png(os.path.join(ASSETS, "textures", "block", "real_world_interface_case.png"), grid(case))
    png(os.path.join(ASSETS, "textures", "block", "real_world_interface_front.png"), grid(front))
    path = os.path.join(ASSETS, "models", "block", "real_world_interface.json")
    with open(path, "w", newline="\n") as f:
        json.dump(model(), f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
