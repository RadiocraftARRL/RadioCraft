"""Placeholder models for the CB ground plane antenna (2026-11-08; see doc/ModelDesignBounty.md).

Writes blockstates/cb_ground_plane_antenna.json (a multipart: one model per segment, coax stubs at the base as on the
5-BTV), models/block/cb_ground_plane_antenna_segment_0..4.json and models/item/cb_ground_plane_antenna.json. Segment 0
is the base: a yellow insulator, the mounting plate and four radials drooping out about half a block past the block;
the tubing tapers up the other four. Vanilla textures only. Run: python tools/cb_ground_plane_models.py
"""
import json
import os

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")
HEIGHT = 5
TEX = {"tube": "minecraft:block/iron_block", "insulator": "minecraft:block/yellow_concrete", "plate": "minecraft:block/light_gray_concrete",
       "particle": "minecraft:block/iron_block"}
FACES = ("north", "east", "south", "west", "up", "down")


def box(name, frm, to, tex, rotation=None):
    e = {"name": name, "from": frm, "to": to, "faces": {f: {"uv": [0, 0, 2, 2], "texture": "#" + tex} for f in FACES}}
    if rotation:
        e["rotation"] = rotation
    return e


def tube(half, y0=0, y1=16):
    return box("tube", [8 - half, y0, 8 - half], [8 + half, y1, 8 + half], "tube")


def segment(i):
    if i == 0:
        y = 9.0
        r = 0.25
        elements = [
            tube(1.0, 0, 16),
            box("insulator", [6.5, 3, 6.5], [9.5, 8, 9.5], "insulator"),
            box("plate", [5, 8, 5], [11, 9, 11], "plate"),
            # Radials: 16 px long (half a block past the block's side), drooping 22.5 degrees.
            box("radial_east", [8, y, 8 - r], [24, y + 2 * r, 8 + r], "tube", {"angle": -22.5, "axis": "z", "origin": [8, y, 8]}),
            box("radial_west", [-8, y, 8 - r], [8, y + 2 * r, 8 + r], "tube", {"angle": 22.5, "axis": "z", "origin": [8, y, 8]}),
            box("radial_south", [8 - r, y, 8], [8 + r, y + 2 * r, 24], "tube", {"angle": 22.5, "axis": "x", "origin": [8, y, 8]}),
            box("radial_north", [8 - r, y, -8], [8 + r, y + 2 * r, 8], "tube", {"angle": -22.5, "axis": "x", "origin": [8, y, 8]}),
        ]
    else:
        half = [1.0, 0.75, 0.5, 0.35, 0.25][i]
        elements = [tube(half, 0, 16 if i < HEIGHT - 1 else 14), box("joint", [8 - half - 0.25, 0, 8 - half - 0.25], [8 + half + 0.25, 1, 8 + half + 0.25], "tube")]
    return {"credit": "Placeholder from tools/cb_ground_plane_models.py (see doc/ModelDesignBounty.md)", "parent": "block/block",
            "textures": TEX, "elements": elements}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    parts = []
    for i in range(HEIGHT):
        write(os.path.join(ASSETS, "models", "block", "cb_ground_plane_antenna_segment_%d.json" % i), segment(i))
        parts.append({"when": {"segment": str(i)}, "apply": {"model": "radiocraft:block/cb_ground_plane_antenna_segment_%d" % i}})
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        apply = {"model": "radiocraft:block/antenna_coax"}
        if y:
            apply["y"] = y
        parts.append({"when": {"segment": "0", side: "true"}, "apply": apply})
    write(os.path.join(ASSETS, "blockstates", "cb_ground_plane_antenna.json"), {"multipart": parts})
    write(os.path.join(ASSETS, "models", "item", "cb_ground_plane_antenna.json"), {"parent": "radiocraft:block/cb_ground_plane_antenna_segment_0"})
    print("ok")


if __name__ == "__main__":
    main()
