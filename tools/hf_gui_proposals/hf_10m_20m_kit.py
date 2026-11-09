"""Shared pieces for the proposed 10m and 20m HF radio GUI backgrounds (hf_radio_10m_proposed.py and
hf_radio_20m_proposed.py). Builds on tools/cb_gui_assets.py (pixel font, knobs, discs) and adds a layout recorder
that checks no two controls or labels overlap, plus the HF parts: push keys, toggles, a big tuning dial, an S-meter
face, a display window and LEDs. Proposal art only; nothing in game reads these files yet. Needs Pillow.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import cb_gui_assets as cb  # noqa: E402  (the CB GUI script: pixel font, knobs, discs)

ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "radiocraft")
GUI_OUT = os.path.join(ASSETS, "textures", "gui")
PANELS_OUT = os.path.join(GUI_OUT, "hf_panels")  # The in-game panels, moving parts left out (RUNTIME_SPEC.md).
LAYOUTS_OUT = os.path.join(ASSETS, "gui_layouts")

text, text_centred, text_width, disc, draw_knob = cb.text, cb.text_centred, cb.text_width, cb.disc, cb.draw_knob


def hexc(c):
    return "#%02X%02X%02X" % tuple(c[:3])


def rgb(*c):
    return tuple(c) + (255,) if len(c) == 3 else tuple(c)


def shade(c, k):
    return tuple(max(0, min(255, v + k)) for v in c[:3]) + (255,)


class Layout:
    """Records every control and label rectangle; check() asserts none of them come within 2 px of another."""

    def __init__(self, name, w, h):
        self.name, self.w, self.h = name, w, h
        self.controls, self.labels, self.runtime = [], [], []

    def control(self, name, kind, x, y, w, h, rt=None, rt_type=None, **extra):
        """Records a control. With `rt` (the runtime fields, see RUNTIME_SPEC.md) it also goes in the game's layout
        JSON, as type `rt_type` (default `kind`) with the same click rectangle."""
        self.controls.append(dict(name=name, type=kind, x=int(x), y=int(y), w=int(w), h=int(h), **extra))
        if rt is not None:
            entry = dict(name=name, type=rt_type or kind, x=int(x), y=int(y), w=int(w), h=int(h))
            entry.update(rt)  # rt may also move the rectangle (a lamp's lens inside its bezel).
            self.runtime.append(entry)
        return self.controls[-1]

    def label(self, img, s, x, y, colour, centred=False):
        if centred:
            x = x - text_width(s) // 2
        text(img, x, y, s, colour)
        self.labels.append(dict(name="label:" + s, type="label", x=int(x), y=int(y), w=text_width(s), h=5))
        return self.labels[-1]

    def check(self, gap=2):
        rects = self.controls + self.labels
        bad = []
        for i, a in enumerate(rects):
            assert a["x"] >= 0 and a["y"] >= 0 and a["x"] + a["w"] <= self.w and a["y"] + a["h"] <= self.h, a
            for b in rects[i + 1:]:
                if (a["x"] < b["x"] + b["w"] + gap and b["x"] < a["x"] + a["w"] + gap
                        and a["y"] < b["y"] + b["h"] + gap and b["y"] < a["y"] + a["h"] + gap):
                    bad.append((a["name"], b["name"]))
        assert not bad, "overlapping or too close (< %d px): %s" % (gap, bad)

    def save_json(self, path):
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"panel": self.name, "width": self.w, "height": self.h,
                       "controls": self.controls, "labels": self.labels}, f, indent=1)
            f.write("\n")


# ---- Parts ----------------------------------------------------------------------------------------------------------

def bevel_box(d, box, fill, light, dark, outline=None):
    x0, y0, x1, y1 = box
    if outline:
        d.rectangle(box, fill=outline)
        x0, y0, x1, y1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
    d.rectangle([x0, y0, x1, y1], fill=fill)
    d.line([x0, y0, x1, y0], fill=light)
    d.line([x0, y0, x0, y1], fill=light)
    d.line([x0, y1, x1, y1], fill=dark)
    d.line([x1, y0, x1, y1], fill=dark)


def recess(d, box, fill, rim_dark, rim_light):
    """A window or inset sunk into the panel: dark top-left rim, light bottom-right lip."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=fill)
    d.line([x0, y0, x1, y0], fill=rim_dark)
    d.line([x0, y0, x0, y1], fill=rim_dark)
    d.line([x0, y1, x1, y1], fill=rim_light)
    d.line([x1, y0, x1, y1], fill=rim_light)


def key(img, x, y, w, h, cap, outline=rgb(26, 26, 26)):
    """A push key at rest: dark outline, cap with a lit top edge and a shadowed bottom."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=outline)
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=cap)
    d.line([x + 1, y + 1, x + w - 2, y + 1], fill=shade(cap, 28))
    d.line([x + 1, y + h - 2, x + w - 2, y + h - 2], fill=shade(cap, -40))
    d.point((x + 1, y + 1), fill=shade(cap, 40))


KNOB_POINTER = (238, 238, 238)


def small_knob(img, cx, cy, size, frac, base_shadow, moving=True):
    """One of the CB sheet's black knobs, with a contact shadow ring on the panel under it. moving=False leaves the
    pointer off (the game draws it). Returns the runtime knob fields."""
    d = ImageDraw.Draw(img)
    disc(d, cx + 1, cy + 1, size // 2 + 1, base_shadow)
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if moving:
        draw_knob(tile, 0, 0, size, frac, False)
    else:  # draw_knob's black knob, without its pointer.
        td, c, r = ImageDraw.Draw(tile), size / 2.0, size // 2
        disc(td, c, c, r, (24, 24, 24, 255))
        disc(td, c, c, r - 2, (44, 44, 44, 255))
        disc(td, c - 1, c - 1, max(2, r - 5), (58, 58, 58, 255))
    img.alpha_composite(tile, (cx - size // 2, cy - size // 2))
    r = size // 2
    return {"cx": cx, "cy": cy, "pointer_from": int(r * 0.3), "pointer_r": r - 2, "pointer_width": 1,
            "pointer_colour": hexc(KNOB_POINTER), "min_deg": -135, "max_deg": 135}


def knob_ticks(img, cx, cy, r, colour, n=11):
    """The printed scale round a knob: n dots from 7 o'clock to 5 o'clock."""
    px = img.load()
    for i in range(n):
        a = cb.knob_angle(i / (n - 1))
        x, y = round(cx - 0.5 + math.cos(a) * r), round(cy - 0.5 + math.sin(a) * r)
        px[x, y] = colour


def big_dial(img, cx, cy, size, frac, body, rim_dark, rim_light, face, face_light, mark, moving=True):
    """The main tuning knob, seen face on: knurled skirt, dished face, a finger dimple, a white index line."""
    d = ImageDraw.Draw(img)
    r = size // 2
    disc(d, cx + 2, cy + 2, r + 1, rgb(18, 18, 18))  # Its shadow on the panel.
    disc(d, cx, cy, r + 1, rgb(14, 14, 14))
    disc(d, cx, cy, r, body)
    teeth = 60
    for k in range(teeth):  # The knurling round the skirt.
        a = 2 * math.pi * k / teeth
        c = rim_light if k % 2 else rim_dark
        for rr in (r - 1, r - 2):
            d.point((round(cx - 0.5 + math.cos(a) * rr), round(cy - 0.5 + math.sin(a) * rr)), fill=c)
    disc(d, cx, cy, r - 4, rgb(16, 16, 16))
    disc(d, cx, cy, r - 5, face)
    d.arc([cx - r + 6, cy - r + 6, cx + r - 7, cy + r - 7], 190, 260, fill=face_light, width=2)  # Its sheen.
    disc(d, cx, cy, r // 3, shade(face, -10))
    disc(d, cx - 1, cy - 1, r // 3 - 1, shade(face, 6))
    rt = {"cx": cx, "cy": cy, "mark_r": round(r * 0.58), "mark_size": 6, "mark_colour": hexc(shade(face, -14)),
          "mark_shadow": hexc((16, 16, 16))}
    if not moving:  # The game spins the finger dimple; the index line goes with it.
        return rt
    a = cb.knob_angle(frac)
    for i in range(r // 3 + 1, r - 5):  # The index line.
        d.point((round(cx - 0.5 + math.cos(a) * i), round(cy - 0.5 + math.sin(a) * i)), fill=mark)
    b = a + math.radians(120)  # The finger dimple, a third of a turn on from the index.
    fx, fy = cx + math.cos(b) * (r * 0.58), cy + math.sin(b) * (r * 0.58)
    disc(d, round(fx), round(fy), 4, rgb(16, 16, 16))
    disc(d, round(fx), round(fy), 3, shade(face, -14))
    d.point((round(fx) - 2, round(fy) - 2), fill=shade(face, 30))
    return rt


def dial_ring(img, cx, cy, r, colour, every=12):
    px = img.load()
    for k in range(0, 360, every):
        a = math.radians(k)
        long = k % (every * 3) == 0
        for rr in range(r, r + (3 if long else 2)):
            px[round(cx - 0.5 + math.cos(a) * rr), round(cy - 0.5 + math.sin(a) * rr)] = colour


def meter(img, x, y, w, h, face, ink, red, bezel, lip, drop=10, half=50):
    """An analog meter, no needle (the screen draws it): S-units 1-9 and +20/+40 dB along the top arc, a PO/SWR arc
    inside it. The pivot sits `drop` px below the face, hidden by the bezel as on a real meter, so the arcs are wide
    and flat; the needle swings `half` degrees either side of straight up and should be clipped to the face.
    Returns the pivot, reach and swing for the layout JSON."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 3, y - 3, x + w + 2, y + h + 2], fill=bezel)
    d.line([x - 3, y + h + 2, x + w + 2, y + h + 2], fill=lip)
    d.line([x + w + 2, y - 3, x + w + 2, y + h + 2], fill=lip)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=rgb(10, 10, 10))
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=face)
    d.line([x, y, x + w - 1, y], fill=shade(face, -26))  # The bezel's shadow across the top of the face.
    cx, cy = x + w / 2.0, y + h - 1 + drop
    r = h + drop - 12
    a0 = 270.0 - half
    span = 2.0 * half

    def ang(v):
        return math.radians(a0 + span * v)

    def put(a, k, colour):
        px, py = round(cx - 0.5 + math.cos(a) * k), round(cy - 0.5 + math.sin(a) * k)
        if x <= px < x + w and y <= py < y + h:
            d.point((px, py), fill=colour)

    s_end = 0.62  # S9 sits here; past it the arc is red.
    for i in range(0, 41):
        v = i / 40.0
        colour = red if v > s_end + 0.001 else ink
        major = i % 5 == 0
        for k in range(r, r + (4 if major else 2)):
            put(ang(v), k, colour)
    steps = 240
    for i in range(steps + 1):  # The arc line itself, and a thicker red band past S9.
        v = i / steps
        put(ang(v), r, red if v > s_end else ink)
        if v > s_end:
            put(ang(v), r + 1, red)
    numbers = [("1", 0.0), ("3", 0.155), ("5", 0.31), ("7", 0.465), ("9", s_end), ("+20", 0.81), ("+40", 1.0)]
    for s, v in numbers:
        a = ang(v)
        tx, ty = cx + math.cos(a) * (r + 8), cy + math.sin(a) * (r + 8)
        tx = min(max(round(tx - text_width(s) / 2.0), x + 2), x + w - 2 - text_width(s))  # Kept on the face.
        text(img, tx, round(ty - 2.5), s, red if s.startswith("+") else ink)
    r2 = r - 8  # The PO / SWR arc.
    for i in range(steps + 1):
        v = i / steps
        put(ang(v), r2, red if v > 0.75 else ink)
    for i in range(0, 11):
        v = i / 10.0
        for k in range(r2 - (3 if i % 5 == 0 else 2), r2):
            put(ang(v), k, red if v > 0.75 else ink)
    text(img, x + 3, y + 3, "S", ink)
    text(img, x + 3, y + h - 7, "PO", ink)
    text(img, x + w - 3 - text_width("SWR"), y + h - 7, "SWR", red)
    d.pieslice([cx - 7, y + h - 5, cx + 6, y + h + 8], 180, 360, fill=rgb(36, 36, 36))  # The pivot's cover.
    d.arc([cx - 7, y + h - 5, cx + 6, y + h + 8], 200, 340, fill=rgb(70, 70, 70))
    return {"pivot_x": round(cx), "pivot_y": round(cy), "needle_r": r + 2, "swing_deg": [round(a0), round(a0 + span)],
            "clip_to_face": True,
            "rt": {"pivot": [cx, cy], "radius": r + 2, "zero_deg": -half, "full_deg": half, "s9_fraction": s_end,
                   "needle_colour": hexc((32, 32, 32))}}


def display_window(img, x, y, w, h, glass, bezel, lip):
    """The blank readout: a dark glass window in a bezel; the screen writes the frequency into it."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 3, y - 3, x + w + 2, y + h + 2], fill=bezel)
    d.line([x - 3, y + h + 2, x + w + 2, y + h + 2], fill=lip)
    d.line([x + w + 2, y - 3, x + w + 2, y + h + 2], fill=lip)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=rgb(8, 6, 10))
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=glass)
    # The top 9 px stay plain glass: the game prints the mode marker and TX there. A glint low on the left.
    d.line([x + 2, y + h - 3, x + 9, y + h - 3], fill=shade(glass, 12))


def led(img, x, y, colour):
    """A 4 x 4 lamp lens (unlit) in a black bezel at (x, y)."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 1, y - 1, x + 4, y + 4], fill=rgb(14, 14, 14))
    d.rectangle([x, y, x + 3, y + 3], fill=colour)
    d.point((x, y), fill=shade(colour, 40))


def toggle(img, x, y, positions, at, plate, plate_dark, slot, bat, pitch=7, moving=True):
    """A bat toggle on its plate, x, y its top left, 8 wide. Stop i's bat top is at y + 2 + i * pitch (5 px tall),
    so a label beside each stop can sit at that y. Returns the plate's height."""
    d = ImageDraw.Draw(img)
    h = 4 + pitch * (positions - 1) + 5
    d.rectangle([x, y, x + 7, y + h - 1], fill=plate_dark)
    d.rectangle([x, y, x + 6, y + h - 2], fill=plate)
    d.rectangle([x + 2, y + 2, x + 5, y + h - 3], fill=slot)
    if not moving:
        return h
    by = y + 2 + at * pitch
    d.rectangle([x + 1, by, x + 6, by + 5], fill=rgb(14, 14, 14))
    d.rectangle([x + 2, by, x + 5, by + 4], fill=bat)
    d.line([x + 2, by, x + 5, by], fill=shade(bat, 36))
    return h


def toggle_handles(x, y, positions, pitch=7):
    """The bat's rectangle at each stop of a toggle(): [x, y, w, h], cap plus its 1 px shadow."""
    return [[x + 2, y + 2 + i * pitch, 5, 6] for i in range(positions)]


def key_lit(x, y, w, h, colour="#FFC040"):
    """The lamp strip a selected key lights: its cap inside the outline and bevels."""
    return [x + 3, y + 2, w - 6, h - 5, colour]


def grille(img, box, back, hole, step=3):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=back)
    for yy in range(y0 + 2, y1 - 1, step):
        for xx in range(x0 + 2 + ((yy - y0) // step % 2), x1 - 1, step):
            d.point((xx, yy), fill=hole)


def screw(img, cx, cy, head, slot):
    d = ImageDraw.Draw(img)
    disc(d, cx, cy, 2, head)
    d.point((cx - 1, cy - 1), fill=slot)
    d.point((cx, cy - 1), fill=slot)


def write(img, layout, name):
    layout.check()
    img.save(os.path.join(GUI_OUT, name + ".png"))
    layout.save_json(os.path.join(HERE, name + "_layout.json"))
    print("wrote", name)


def write_runtime(img, layout, radio):
    """The game's copy: the panel with its moving parts left out, and the layout JSON HfPanel reads."""
    layout.check()
    os.makedirs(PANELS_OUT, exist_ok=True)
    img.save(os.path.join(PANELS_OUT, radio + ".png"))
    with open(os.path.join(LAYOUTS_OUT, "hf_" + radio + ".json"), "w", encoding="utf-8") as f:
        json.dump({"texture": "radiocraft:textures/gui/hf_panels/%s.png" % radio, "width": layout.w,
                   "height": layout.h, "controls": layout.runtime}, f, indent=1)
        f.write("\n")
    print("wrote", radio, "runtime panel and layout")
