"""Shared pieces for the premium HF rigs' art (2026-11-11): hf_radio_20m_pure.py (the Swan) and hf_radio_40m_pure.py
(the TS-520R). Builds on hf_10m_20m_kit.py (layout recorder, pixel font, keys, meter, big dial) and adds:

  - silver-capped knobs and knobs with detents (knob3), with their runtime fields;
  - decor knobs whose pointer is painted on (they do nothing, so the game never draws one);
  - a 2x pixel font for nameplates;
  - a preview that draws a runtime panel's moving parts from its layout JSON, by RUNTIME_SPEC.md;
  - a block model builder: a front texture of 4 px a model unit on a 64 x 64 sheet, knob cubes on it, a DISPLAY
    element where the screen is (RadioDisplays reads it), and the radios' usual item transforms.
Needs Pillow.
"""
import json
import math
import os

from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
from hf_10m_20m_kit import rgb, shade, disc, text, text_width

ASSETS = kit.ASSETS


# ---- Knobs ----------------------------------------------------------------------------------------------------------

def silver_knob(img, cx, cy, size, skirt=rgb(20, 20, 20), cap=rgb(178, 180, 184), pointer=None):
    """A black knob with a spun-aluminium cap, seen face on. `pointer` (0-1) paints the white index line on the skirt
    (decor knobs only). Returns the runtime knob fields: the game's pointer runs over the skirt, outside the cap."""
    d = ImageDraw.Draw(img)
    r = size // 2
    disc(d, cx + 1, cy + 1, r + 1, rgb(12, 12, 12))
    disc(d, cx, cy, r, skirt)
    for k in range(36):  # The fluted skirt.
        a = 2 * math.pi * k / 36
        if k % 2:
            d.point((round(cx - 0.5 + math.cos(a) * (r - 1)), round(cy - 0.5 + math.sin(a) * (r - 1))), fill=shade(skirt, 22))
    rc = max(2, round(r * 0.58))
    disc(d, cx, cy, rc + 1, rgb(10, 10, 10))
    disc(d, cx, cy, rc, cap)
    d.arc([cx - rc + 1, cy - rc + 1, cx + rc - 1, cy + rc - 1], 200, 250, fill=shade(cap, 40))
    d.arc([cx - rc + 1, cy - rc + 1, cx + rc - 1, cy + rc - 1], 20, 70, fill=shade(cap, -40))
    disc(d, cx, cy, max(1, rc // 3), shade(cap, 18))
    if pointer is not None:
        a = math.radians(135.0 + 270.0 * pointer)
        for t in range(rc + 1, r):
            d.point((round(cx - 0.5 + math.cos(a) * t), round(cy - 0.5 + math.sin(a) * t)), fill=rgb(236, 236, 236))
    return {"cx": cx, "cy": cy, "pointer_from": rc + 1, "pointer_r": r - 1, "pointer_width": 1,
            "pointer_colour": "#EEEEEE", "min_deg": -135, "max_deg": 135}


def knob3_rt(rt, positions, angles):
    out = dict(rt)
    out["positions"] = list(positions)
    out["angles_deg"] = list(angles)
    return out


def detent_dots(img, cx, cy, r, angles, colour):
    """A dot at each detent of a knob3, `r` out from its centre (angles: 0 up, clockwise)."""
    px = img.load()
    for deg in angles:
        a = math.radians(deg)
        px[round(cx - 0.5 + math.sin(a) * r), round(cy - 0.5 - math.cos(a) * r)] = colour


def big_text(img, x, y, s, colour, scale=2):
    """The 3 x 5 font at `scale` times: a nameplate's lettering."""
    w = max(1, text_width(s))
    tile = Image.new("RGBA", (w, 5), (0, 0, 0, 0))
    text(tile, 0, 0, s, colour)
    tile = tile.resize((w * scale, 5 * scale), Image.NEAREST)
    img.alpha_composite(tile, (x, y))
    return w * scale


def rocker(img, x, y, w, h, body=rgb(26, 26, 26), face=rgb(70, 70, 70)):
    """A rocker or slide well (its moving part, the lit half, is drawn by the game as a switch handle)."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=rgb(10, 10, 10))
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=body)
    d.line([x + 1, y + h - 2, x + w - 2, y + h - 2], fill=shade(body, 20))


# ---- Preview --------------------------------------------------------------------------------------------------------

def _hex(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def preview(radio, setting, out_path, display_text="14.200 MHz"):
    """The runtime panel with its moving parts drawn as HfPanelScreen draws them. `setting`: 0, 1 or 2 (all knobs and
    switches at their first, middle or last position, every lamp and lit key on at 2)."""
    with open(os.path.join(ASSETS, "gui_layouts", "hf_" + radio + ".json"), encoding="utf-8") as f:
        lay = json.load(f)
    img = Image.open(os.path.join(ASSETS, "textures", "gui", "hf_panels", radio + ".png")).convert("RGBA")
    d = ImageDraw.Draw(img)
    frac = (0.0, 0.5, 1.0)[setting]

    def ray(cx, cy, deg, a, b, width, colour, clip=None):
        s, c = math.sin(math.radians(deg)), math.cos(math.radians(deg))
        t = a
        while t <= b:
            x, y = math.floor(cx + t * s), math.floor(cy - t * c)
            if clip is None or (clip[0] <= x < clip[0] + clip[2] and clip[1] <= y < clip[1] + clip[3]):
                d.rectangle([x, y, x + width - 1, y + width - 1], fill=colour)
            t += 0.35

    for c in lay["controls"]:
        t = c["type"]
        if t == "knob":
            deg = c["min_deg"] + frac * (c["max_deg"] - c["min_deg"])
            ray(c["cx"], c["cy"], deg, c["pointer_from"], c["pointer_r"], c["pointer_width"], _hex(c["pointer_colour"]))
        elif t in ("knob3",):
            angles = c["angles_deg"]
            deg = angles[min(len(angles) - 1, round(frac * (len(angles) - 1)))]
            ray(c["cx"], c["cy"], deg, c["pointer_from"], c["pointer_r"], c["pointer_width"], _hex(c["pointer_colour"]))
        elif t == "dial":
            deg = -135 + 120 * setting
            mx = c["cx"] + c["mark_r"] * math.sin(math.radians(deg))
            my = c["cy"] - c["mark_r"] * math.cos(math.radians(deg))
            s = c["mark_size"]
            x0, y0 = math.floor(mx - s / 2), math.floor(my - s / 2)
            d.rectangle([x0 + 1, y0 + 1, x0 + s, y0 + s], fill=_hex(c["mark_shadow"]))
            d.rectangle([x0, y0, x0 + s - 1, y0 + s - 1], fill=_hex(c["mark_colour"]))
        elif t == "switch":
            hs = c["handles"]
            h = hs[min(len(hs) - 1, round(frac * (len(hs) - 1)))]
            d.rectangle([h[0], h[1], h[0] + h[2] - 1, h[1] + h[3] - 1], fill=_hex(c["handle_shadow"]))
            d.rectangle([h[0], h[1], h[0] + h[2] - 2, h[1] + h[3] - 2], fill=_hex(c["handle_colour"]))
        elif t == "button" and setting == 2 and "lit" in c:
            x, y, w, h, col = c["lit"]
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=_hex(col))
        elif t == "lamp" and setting == 2:
            col = _hex(c["colour"])
            d.rectangle([c["x"], c["y"], c["x"] + c["w"] - 1, c["y"] + c["h"] - 1], fill=col)
        elif t == "meter":
            deg = c["zero_deg"] + (0.15, 0.6, 0.95)[setting] * (c["full_deg"] - c["zero_deg"])
            ray(c["pivot"][0], c["pivot"][1], deg, 0, c["radius"], 1, _hex(c["needle_colour"]), [c["x"], c["y"], c["w"], c["h"]])
        elif t == "display":
            ink = _hex(c["ink"])
            text(img, c["x"] + 2, c["y"] + 2, "USB", ink)
            s = display_text.replace(" MHZ", "")
            text(img, c["x"] + c["w"] // 2 - text_width(display_text) // 2, c["y"] + c["h"] // 2, display_text, ink)
    img = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
    img.save(out_path)


# ---- Block model ----------------------------------------------------------------------------------------------------

ITEM_DISPLAY = {
    "thirdperson_righthand": {"rotation": [71, 0, 0], "translation": [0, 1, 1.75], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"rotation": [71, 0, 0], "translation": [0, 1, 1.75], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [0, 130, 0], "translation": [-1, 6, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 130, 0], "translation": [-1, 6, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"scale": [0.49, 0.49, 0.49]},
    "gui": {"rotation": [-150.25, -45.25, -180], "translation": [0, 1.5, 0], "scale": [0.58, 0.58, 0.58]},
}


def uv(x0, y0, x1, y1):
    """Sheet pixels (64 wide) to model UV (0-16)."""
    return [x0 / 4.0, y0 / 4.0, x1 / 4.0, y1 / 4.0]


class Rig:
    """A desk rig: a body 16 wide, `height` tall, from z FRONT to z 15, its front face a 64 x height*4 px picture at the
    top of a 64 x 64 sheet; below it, tiles for the case and the knobs."""

    FRONT = 2.0

    def __init__(self, name, height, front_img, case, case_light, case_dark):
        self.name, self.h = name, height
        self.sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.sheet.alpha_composite(front_img, (0, 0))
        self.tiles = {}
        self.next_tile = (0, 48)
        d = ImageDraw.Draw(self.sheet)
        # The case: top, sides, back, bottom, a 32 x 16 tile with a lit and a shadowed edge.
        d.rectangle([0, 32, 31, 47], fill=case)
        d.line([0, 32, 31, 32], fill=case_light)
        d.line([0, 47, 31, 47], fill=case_dark)
        for x in range(4, 32, 6):  # Vent slots on the lid.
            d.line([x, 36, x, 43], fill=shade(case, -14))
        self.elements = []

    def tile(self, key, draw):
        """An 8 x 8 tile drawn by draw(d, x, y) the first time, for knob faces and the like."""
        if key not in self.tiles:
            x, y = self.next_tile
            draw(ImageDraw.Draw(self.sheet), x, y)
            self.tiles[key] = (x, y, x + 8, y + 8)
            self.next_tile = (x + 8, y) if x + 8 < 64 else (0, y + 8)
        return self.tiles[key]

    def mx(self, px):  # Front picture x (from the viewer's left) to model x: the front faces north, east is on the left.
        return 16.0 - px / 4.0

    def my(self, py):
        return self.h - py / 4.0

    def body(self):
        case = uv(0, 32, 32, 48)
        self.elements.append({"name": "body", "from": [0, 0, self.FRONT], "to": [16, self.h, 15], "faces": {
            "north": {"uv": uv(0, 0, 64, self.h * 4), "texture": "#0"},
            "east": {"uv": case, "texture": "#0"}, "south": {"uv": case, "texture": "#0"},
            "west": {"uv": case, "texture": "#0"}, "up": {"uv": case, "texture": "#0"}, "down": {"uv": case, "texture": "#0"}}})
        # Feet.
        for x0 in (1.5, 13):
            self.elements.append({"name": "foot", "from": [x0, -0.0, 3], "to": [x0 + 1.5, 0.5, 14],
                                  "faces": {f: {"uv": uv(0, 46, 8, 48), "texture": "#0"} for f in ("north", "east", "south", "west", "down")}})

    def part(self, name, box_px, depth, tile_uv, front_uv=None):
        """A cube standing `depth` out of the front over the front picture's pixels box_px = (x0, y0, x1, y1)."""
        x0, y0, x1, y1 = box_px
        faces = {f: {"uv": uv(*tile_uv), "texture": "#0"} for f in ("east", "west", "up", "down")}
        faces["north"] = {"uv": uv(*(front_uv or tile_uv)), "texture": "#0"}
        self.elements.append({"name": name, "from": [self.mx(x1), self.my(y1), self.FRONT - depth],
                              "to": [self.mx(x0), self.my(y0), self.FRONT], "faces": faces})

    def display(self, box_px):
        """The DISPLAY element over the screen, a hair in front of the face, showing the picture's screen."""
        x0, y0, x1, y1 = box_px
        self.elements.append({"name": "DISPLAY", "from": [self.mx(x1), self.my(y1), self.FRONT - 0.05],
                              "to": [self.mx(x0), self.my(y0), self.FRONT],
                              "faces": {"north": {"uv": uv(x0, y0, x1, y1), "texture": "#0"}}})

    def lamp(self, box_px, tx):
        """A front lamp for RadioDisplays.Lamp: (x1 west, x2 east, y1, y2, front, tx)."""
        x0, y0, x1, y1 = box_px
        return (self.mx(x1), self.mx(x0), self.my(y1), self.my(y0), self.FRONT, tx)

    def write(self, credit):
        tex = os.path.join(ASSETS, "textures", "block", self.name + ".png")
        self.sheet.save(tex)
        model = {"credit": credit, "texture_size": [64, 64],
                 "textures": {"0": "radiocraft:block/" + self.name, "particle": "radiocraft:block/" + self.name},
                 "elements": self.elements, "display": ITEM_DISPLAY}
        with open(os.path.join(ASSETS, "models", "block", self.name + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, indent=1)
            f.write("\n")
        print("wrote", self.name, "block model and texture")

    def preview(self, out_path):
        """The front as seen: the front picture with each part's front face pasted where it stands, 8x."""
        img = self.sheet.crop((0, 0, 64, self.h * 4)).copy()
        for e in self.elements:
            if e["name"] in ("body", "foot", "DISPLAY"):
                continue
            u = e["faces"]["north"]["uv"]
            src = self.sheet.crop((round(u[0] * 4), round(u[1] * 4), round(u[2] * 4), round(u[3] * 4)))
            x0 = round((16 - e["to"][0]) * 4)
            y0 = round((self.h - e["to"][1]) * 4)
            w = max(1, round((e["to"][0] - e["from"][0]) * 4))
            h = max(1, round((e["to"][1] - e["from"][1]) * 4))
            img.alpha_composite(src.resize((w, h), Image.NEAREST), (x0, y0))
        img.resize((img.width * 8, img.height * 8), Image.NEAREST).save(out_path)
