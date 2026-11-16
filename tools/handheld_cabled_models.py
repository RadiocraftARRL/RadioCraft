"""Writes each handheld's "cabled" item model: its antenna off and a coax cable from its antenna jack to the handheld
Yagi, shown while the holder has the Yagi in their offhand (2026-11-15).

    python tools/handheld_cabled_models.py

Reads models/item/<handheld>.json (the VHF handheld's 3D model and the commercial and marine handhelds), drops the
elements named antenna / antenna_tip, and adds a BNC plug on the antenna collar and a cable that rises, bends towards
the other hand and droops, in the handheld Yagi's coax and clamp colours (textures/item/handheld_yagi.png). Writes
models/item/<handheld>_cabled.json. Run it again after changing a handheld's model.

The commercial and marine item models switch to these with an override on "radiocraft:yagi" (written by
commercial_handheld_models.py and marine_radio_assets.py); the VHF handheld, drawn by HandheldItemRenderer, swaps its
model in code.
"""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/radiocraft"
ITEMS = ROOT / "models/item"
HANDHELDS = ["vhf_handheld_3d", "basic_commercial_handheld", "upgraded_commercial_handheld", "marine_handheld"]
DROPPED = {"antenna", "antenna_tip"}
CABLE_TEXTURE = "radiocraft:item/handheld_yagi"
# Regions on the Yagi's 32 px texture, in model UV units (0-16): its coax and its clamp (the plug's metal).
COAX = [13, 2, 14, 3]
PLUG = [10, 2, 12, 4]


def faces(region, key):
    return {f: {"uv": list(region), "texture": key} for f in ("north", "east", "south", "west", "up", "down")}


def cable(collar, key):
    """A plug on the collar's top and a cable from it: up, across to the west (towards the offhand), then down."""
    x0, y, z0, x1, z1 = collar["from"][0], collar["to"][1], collar["from"][2], collar["to"][0], collar["to"][2]
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    r = 0.3  # Half the cable's width.
    top = y + 3.0
    west = cx - 5.0
    boxes = [
        ("cable_plug", [cx - 0.55, y, cz - 0.55], [cx + 0.55, y + 1.25, cz + 0.55], PLUG),
        ("cable_up", [cx - r, y + 1.25, cz - r], [cx + r, top, cz + r], COAX),
        ("cable_across", [west, top - 2 * r, cz - r], [cx + r, top, cz + r], COAX),
        ("cable_down", [west, top - 2 * r - 3.5, cz - r], [west + 2 * r, top - 2 * r, cz + r], COAX),
    ]
    return [{"name": n, "from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": faces(uv, key)}
            for n, f, t, uv in boxes]


def main():
    for name in HANDHELDS:
        model = json.loads((ITEMS / f"{name}.json").read_text(encoding="utf-8"))
        out = copy.deepcopy(model)
        out.pop("overrides", None)
        textures = out.setdefault("textures", {})
        key = str(max(int(k) for k in textures if k.isdigit()) + 1)
        textures[key] = CABLE_TEXTURE
        collar = next(e for e in model["elements"] if e.get("name") == "antenna_collar")
        out["elements"] = [e for e in model["elements"] if e.get("name") not in DROPPED] + cable(collar, f"#{key}")
        out["credit"] = f"{model.get('credit', 'RadioCraft')}; cabled to the handheld Yagi by tools/handheld_cabled_models.py"
        path = ITEMS / f"{name}_cabled.json"
        path.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8", newline="\n")
        print("wrote", path)


if __name__ == "__main__":
    main()
