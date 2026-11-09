"""Item models for the CB portable (2026-11-08).

Writes models/item/cb_portable.json (the radio with its whip down; it switches to cb_portable_extended.json on the
radiocraft:extended property, while the radio is on). Plain vanilla models, so Blockbench opens them; the inventory
shows the 3D radio too. The extended whip is 40 model pixels long, which the
third person transform shows at about 2.5 blocks over a half-block radio: about 3 blocks all told, as on the real set.
Body texture regions (textures/item/cb_portable_body.png): front x 0-5, side x 6-9, back x 10-15, all 16 tall.
Run: python tools/cb_portable_models.py
"""
import json
import os

MODELS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft", "models", "item")
BODY = "radiocraft:item/cb_portable_body"


def face(uv, tex="#body"):
    return {"uv": uv, "texture": tex}


def box(name, frm, to, faces):
    return {"name": name, "from": frm, "to": to, "faces": faces}


def body_elements(extended=False):
    # The case: 3 wide, 8 tall, 2 deep, standing from y -16 so the whip has room above it.
    return [
        box("body", [6.5, -16, 7], [9.5, -8, 9], {
            "north": face([0, 0, 6, 16]),
            "south": face([10, 0, 16, 16]),
            "east": face([6, 0, 10, 16]),
            "west": face([6, 0, 10, 16]),
            "up": face([0, 0, 6, 2]),
            "down": face([10, 14, 16, 16]),
        }),
        box("channel_knob", [7.0, -8, 7.5], [8.0, -7.25, 8.5], {d: face([0, 0, 2, 2]) for d in ("north", "south", "east", "west", "up")}),
        # Pulled out, the thick lower section runs 40% of the antenna's 40 px (y -8 to 8), then the black collar, then
        # the thin whip the rest of the way (2026-11-09). Pushed down, all three are stubs.
        box("whip_base", [8.5, -8, 7.75], [9.0, BASE_TOP if extended else -6, 8.25], {d: face([0, 0, 16, 16], "#metal") for d in ("north", "south", "east", "west", "up")}),
        box("collar", [8.4, BASE_TOP if extended else -6, 7.65], [9.1, (BASE_TOP if extended else -6) + 2, 8.35], {d: face([0, 0, 16, 16], "#rubber") for d in ("north", "south", "east", "west", "up", "down")}),
    ]


BASE_TOP = 8


def whip(top, bottom=-4):
    return box("whip", [8.65, bottom, 7.9], [8.85, top, 8.1], {d: face([7, 0, 9, 16], "#metal") for d in ("north", "south", "east", "west", "up")})


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 20.9, 1.5], "scale": [0.96, 0.96, 0.96]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 20.9, 1.5], "scale": [0.96, 0.96, 0.96]},
    # The body sits 20 px below the model's middle (room for the whip above it): at scale 0.6 that is 12 px, so these
    # lift it 12 px over where a handheld sits, to bring it into view.
    "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [0, 20.1, -1], "scale": [1.03, 1.03, 1.03]},
    "firstperson_lefthand": {"rotation": [0, 20, 0], "translation": [0, 20.1, -1], "scale": [1.03, 1.03, 1.03]},
    "ground": {"translation": [0, 21.6, 0], "scale": [1.03, 1.03, 1.03]},
    "fixed": {"translation": [0, 19.9, 0], "scale": [1.17, 1.17, 1.17]},
    "head": {"translation": [0, 20, 0]},
}


def model3d(extended):
    display = dict(DISPLAY)
    # In the inventory: the whole radio, scaled to fit the slot (the extended one is 48 px tall).
    display["gui"] = ({"rotation": [15, -30, 0], "translation": [0, 0, 0], "scale": [0.33, 0.33, 0.33]} if extended
                      else {"rotation": [15, -30, 0], "translation": [0, 27, 0], "scale": [1.6, 1.6, 1.6]})
    model = {
        "credit": "Placeholder from tools/cb_portable_models.py (see doc/ModelDesignBounty.md)",
        "textures": {"body": BODY, "metal": "minecraft:block/iron_block", "rubber": "minecraft:block/black_concrete", "particle": BODY},
        "elements": body_elements(extended) + [whip(32, BASE_TOP + 2) if extended else whip(-2)],
        "display": display,
    }
    if not extended:
        model["overrides"] = [{"predicate": {"radiocraft:extended": 1}, "model": "radiocraft:item/cb_portable_extended"}]
    return model


def write(name, data):
    with open(os.path.join(MODELS, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# Plain vanilla models (no NeoForge loader), so Blockbench opens them (maintainer, 2026-11-08).
write("cb_portable", model3d(False))
write("cb_portable_extended", model3d(True))
for old in ("cb_portable_retracted", "cb_portable_extended_item", "cb_portable_icon"):
    path = os.path.join(MODELS, old + ".json")
    if os.path.exists(path):
        os.remove(path)
print("ok")
