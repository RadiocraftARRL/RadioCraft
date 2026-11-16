"""Writes each handheld's "cabled" item model: its antenna off and a coax cable from its antenna jack to the handheld
Yagi, shown while the holder has the Yagi in their offhand (2026-11-15).

    python tools/handheld_cabled_models.py

Reads models/item/<handheld>.json (the commercial and marine handhelds), drops the elements named antenna /
antenna_tip, and adds the cable of the VHF handheld's cabled model (the maintainer's, drawn by hand; TEMPLATE) fitted
to this radio: a plug on the collar, up, back over the top to behind the radio and down its back. Writes
models/item/<handheld>_cabled.json. Run it again after changing a handheld's model or the VHF's cable.

The commercial and marine item models switch to these with an override on "radiocraft:yagi" (written by
commercial_handheld_models.py and marine_radio_assets.py); the VHF handheld, drawn by HandheldItemRenderer, swaps its
model in code, to models/item/<variant>_cabled.json (vhf_handheld_poor, vhf_handheld, vhf_handheld_pure). Those are
one-line files with vhf_handheld_3d_cabled as their parent; a variant given its own model gets its own cabled one
there instead.
"""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/radiocraft"
ITEMS = ROOT / "models/item"
HANDHELDS = ["vhf_handheld_3d", "basic_commercial_handheld", "upgraded_commercial_handheld", "marine_handheld"]
DROPPED = {"antenna", "antenna_tip"}
# Cabled models the maintainer has since edited by hand: the script no longer writes these.
HAND_EDITED = {"vhf_handheld_3d"}
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


TEMPLATE = "vhf_handheld_3d"  # The maintainer's cable (2026-11-15), drawn by hand: the others copy its shape.
BODY = ("body", "battery", "belt_clip")


def matched(template, model, key):
    """The template's cable fitted to {@code model}: its plug on this collar, its run back over the top to just behind
    this radio's back (or belt clip) and down to just under its bottom, as the template's is on the VHF handheld."""
    tmpl_collar = next(e for e in template["elements"] if e.get("name") == "antenna_collar")
    tmpl_body = [e for e in template["elements"] if e.get("name") in BODY]
    collar = next(e for e in model["elements"] if e.get("name") == "antenna_collar")
    body = [e for e in model["elements"] if e.get("name") in BODY]

    def centre(c):
        return (c["from"][0] + c["to"][0]) / 2, c["to"][1], (c["from"][2] + c["to"][2]) / 2

    tx, ty, tz = centre(tmpl_collar)
    cx, cy, cz = centre(collar)
    t_back, back = min(e["from"][2] for e in tmpl_body), min(e["from"][2] for e in body)
    t_bottom, bottom = min(e["from"][1] for e in tmpl_body), min(e["from"][1] for e in body)
    out = []
    for e in template["elements"]:
        if not e.get("name", "").startswith("cable"):
            continue
        e = copy.deepcopy(e)
        e.pop("rotation", None)
        for end in ("from", "to"):
            e[end][0] = round(e[end][0] + cx - tx, 3)
        down = e["from"][1] < ty - 2  # The run down the back.
        for end in ("from", "to"):
            v = e[end]
            if down:  # Behind the back by as much, from under the bottom by as much, up to the run over the top.
                v[2] = round(v[2] + back - t_back, 3)
                v[1] = round(v[1] + (bottom - t_bottom) if end == "from" else v[1] + cy - ty, 3)
            elif v[2] < t_back:  # The run over the top's back end.
                v[1] = round(v[1] + cy - ty, 3)
                v[2] = round(v[2] + back - t_back, 3)
            else:
                v[1] = round(v[1] + cy - ty, 3)
                v[2] = round(v[2] + cz - tz, 3)
        for face in e["faces"].values():
            face["texture"] = key
        out.append(e)
    return out


def main():
    for name in HANDHELDS:
        if name in HAND_EDITED:
            print(f"kept {name}_cabled.json (edited by hand)")
            continue
        model = json.loads((ITEMS / f"{name}.json").read_text(encoding="utf-8"))
        out = copy.deepcopy(model)
        out.pop("overrides", None)
        textures = out.setdefault("textures", {})
        key = str(max(int(k) for k in textures if k.isdigit()) + 1)
        textures[key] = CABLE_TEXTURE
        collar = next(e for e in model["elements"] if e.get("name") == "antenna_collar")
        template = json.loads((ITEMS / f"{TEMPLATE}_cabled.json").read_text(encoding="utf-8"))
        out["elements"] = [e for e in model["elements"] if e.get("name") not in DROPPED] + matched(template, model, f"#{key}")
        out["credit"] = f"{model.get('credit', 'RadioCraft')}; cabled to the handheld Yagi by tools/handheld_cabled_models.py"
        path = ITEMS / f"{name}_cabled.json"
        path.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8", newline="\n")
        print("wrote", path)


if __name__ == "__main__":
    main()
