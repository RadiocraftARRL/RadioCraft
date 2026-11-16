"""Writes the handheld Yagi: a three-element 2 m beam held in the offhand for satellite work.

    python tools/handheld_yagi_model.py [--preview DIR]

models/item/handheld_yagi.json and textures/item/handheld_yagi.png. The boom runs along z with the director at the
front (north, away from the player when held) and a black foam grip at the back, like the hand-held satellite Yagis
hams point at a pass. Mono band, so only three elements, flat (horizontal) across the boom:

- reflector   the longest, nearest the grip
- driven      split in the middle at a red feed block, with the coax running back under the boom into the grip
- director    the shortest, at the front

Element tips have small yellow safety caps. Display transforms are included (see DISPLAY).
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from blockmodel import *  # noqa: E402,F403

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/radiocraft"
MODELS = ROOT / "models/item"
TEXTURES = ROOT / "textures/item"
NAME = "handheld_yagi"

# Files the maintainer has since edited by hand: the script no longer writes these.
HAND_EDITED = set()

REGIONS = {
    "alu": [0, 0, 16, 1],          # elements and boom: a long strip so long faces stretch along it
    "boom": [0, 1, 16, 2],
    "grip": [0, 2, 8, 4],
    "feed": [8, 2, 10, 4],
    "clamp": [10, 2, 12, 4],
    "cap": [12, 2, 13, 3],
    "coax": [13, 2, 14, 3],
    "end": [14, 2, 16, 4],
}
ALU = (196, 200, 204)
ALU_LIGHT = (226, 230, 234)
ALU_DARK = (150, 154, 160)
FOAM = (34, 34, 36)
FOAM_LIGHT = (58, 58, 62)
FEED_RED = (200, 46, 40)
FEED_DARK = (150, 30, 28)
CAP_YELLOW = (236, 200, 40)

Y = 8                    # the boom's centre line
T = 0.25                 # half an element's thickness
BOOM = 0.5               # half the boom's width
REFLECTOR_Z, DRIVEN_Z, DIRECTOR_Z = 13, 3, -8
REFLECTOR, DRIVEN, DIRECTOR = 15.5, 14, 12      # half lengths, about 1.04 : 0.95 : 0.9 like a real 2 m beam
BOOM_FRONT, BOOM_BACK = -11, 17
GRIP_BACK = 25.5


def paint():
    p = Painter(REGIONS, 32, seed=21)
    p.fill("alu", ALU, 3, edge(ALU_LIGHT, ALU_DARK, vertical=False))
    p.fill("boom", ALU, 3, edge(ALU_LIGHT, ALU_DARK, vertical=False))
    p.fill("grip", FOAM, 4, lambda x, y, w, h: FOAM_LIGHT if (x * 3 + y * 5) % 7 == 0 else None)
    p.fill("feed", FEED_RED, 5, rim(FEED_DARK))
    p.fill("clamp", IRON, 4, rim(IRON_LIGHT))
    p.fill("cap", CAP_YELLOW, 4)
    p.fill("coax", BLACK, 3)
    p.fill("end", FOAM, 3, rim(BLACK))
    p.write(TEXTURES / f"{NAME}.png")


def element(b, name, z, half, split=False):
    """One element across the boom with yellow caps on its tips; a split one leaves a gap for the feed block."""
    lo, hi = 8 - half, 8 + half
    els = []
    if split:
        els.append(b.box(f"{name}_west", [lo, Y - T, z - T], [8 - 0.75, Y + T, z + T], "alu"))
        els.append(b.box(f"{name}_east", [8 + 0.75, Y - T, z - T], [hi, Y + T, z + T], "alu"))
    else:
        els.append(b.box(name, [lo, Y - T, z - T], [hi, Y + T, z + T], "alu"))
    els.append(b.box(f"{name}_cap_west", [lo - 0.5, Y - 0.375, z - 0.375], [lo, Y + 0.375, z + 0.375], "cap"))
    els.append(b.box(f"{name}_cap_east", [hi, Y - 0.375, z - 0.375], [hi + 0.5, Y + 0.375, z + 0.375], "cap"))
    return els


def elements(b):
    els = [b.box("boom", [8 - BOOM, Y - BOOM, BOOM_FRONT], [8 + BOOM, Y + BOOM, BOOM_BACK], "boom",
                 {"north": "clamp"})]
    els += element(b, "reflector", REFLECTOR_Z, REFLECTOR)
    els += element(b, "driven", DRIVEN_Z, DRIVEN, split=True)
    els += element(b, "director", DIRECTOR_Z, DIRECTOR)
    for name, z in (("reflector", REFLECTOR_Z), ("director", DIRECTOR_Z)):
        els.append(b.box(f"{name}_clamp", [7.25, Y - 0.75, z - 0.75], [8.75, Y + 0.75, z + 0.75], "clamp"))
    els += [
        b.box("feed", [7, Y - 1, DRIVEN_Z - 1], [9, Y + 1.25, DRIVEN_Z + 1], "feed"),
        b.box("coax_drop", [7.75, Y - 2.5, DRIVEN_Z + 0.5], [8.25, Y - 1, DRIVEN_Z + 1], "coax"),
        b.box("coax_run", [7.75, Y - 2.5, DRIVEN_Z + 1], [8.25, Y - 2, BOOM_BACK], "coax"),
        b.box("grip", [7.25, Y - 2.75, BOOM_BACK], [8.75, Y + 0.75, GRIP_BACK], "grip", {"south": "end", "north": "end"}),
    ]
    return els


# With the item in the offhand the holder takes the bow pose (ClientSetupEvents), arm along their look: untransformed,
# the item's north then runs down the arm, so the beam points where they look with its elements level. The grip sits
# 21 units back from the model's middle; at 0.4 that is about 5 towards the shoulder, so it is moved 5 out to the hand.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, -5], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, -5], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0.5, -1.5], "scale": [0.35, 0.35, 0.35]},
    "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0.5, -1.5], "scale": [0.35, 0.35, 0.35]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.32, 0.32, 0.32]},
    "ground": {"translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.42, 0.42, 0.42]},
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="write a Blockbench preview under this directory")
    args = ap.parse_args()
    b = Builder(REGIONS, f"radiocraft:item/{NAME}", 32)
    if f"{NAME}.png" not in HAND_EDITED:
        paint()
    model = b.model(elements(b))
    model.pop("parent")
    model["display"] = DISPLAY
    if f"{NAME}.json" not in HAND_EDITED:
        save(MODELS, NAME, model)
    if args.preview:
        import shutil
        root = Path(args.preview)
        (root / "assets/radiocraft/textures/item").mkdir(parents=True, exist_ok=True)
        shutil.copy(TEXTURES / f"{NAME}.png", root / "assets/radiocraft/textures/item")
        save(root / "assets/radiocraft/models/item", NAME, model)


if __name__ == "__main__":
    main()
