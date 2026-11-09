"""Proposed GUI background for the 40m HF radio (2026-11-09), drawn to match its block model.

The block (models/block/hf_radio_40m.json, textures/block/hf_radio_40m.png) is a wide, low box: a light grey case
round a dark green front panel. Seen from the front it has a 2 x 2 group of small dark knobs at the upper left, a dark
purple display window at the top middle with the big tuning dial under it, a small dark meter window at the upper
right, a 2 x 2 group of small knobs at the lower right, and a few tiny push buttons along the bottom. This panel keeps
that arrangement and those colours.

Writes (nothing in game uses them yet):
  src/main/resources/assets/radiocraft/textures/gui/hf_radio_40m_proposed.png
  tools/hf_gui_proposals/hf_radio_40m_proposed_layout.json   every control's rectangle, for wiring it up later
The display window and the meter are left without digits and needle: the screen draws those in code.
Needs Pillow. Run: python tools/hf_gui_proposals/hf_radio_40m_proposed.py
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from cb_gui_assets import disc, draw_knob, meter_radius, text, text_centred, text_width  # noqa: E402

NAME = "hf_radio_40m"
OUT_PNG = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "radiocraft", "textures", "gui",
                       NAME + "_proposed.png")
OUT_JSON = os.path.join(HERE, NAME + "_proposed_layout.json")
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "radiocraft")
RUNTIME_PNG = os.path.join(ASSETS, "textures", "gui", "hf_panels", NAME + ".png")
RUNTIME_JSON = os.path.join(ASSETS, "gui_layouts", "hf_" + NAME + ".json")

W, H = 256, 160

# ---- The block texture's palette ---------------------------------------------------------------------------------
CASE_HI = (217, 217, 217, 255)
CASE = (206, 206, 206, 255)
CASE_MID = (198, 198, 198, 255)
CASE_LO = (164, 164, 164, 255)
CASE_EDGE = (96, 96, 96, 255)
GREEN_DD = (19, 58, 28, 255)
GREEN_D = (34, 83, 45, 255)
GREEN = (43, 96, 55, 255)
GREEN_L = (51, 108, 64, 255)
KNOB_DD = (38, 38, 38, 255)
KNOB_D = (58, 58, 58, 255)
KNOB_M = (79, 79, 79, 255)
KNOB_HI = (178, 178, 178, 255)
PURPLE_DD = (30, 12, 27, 255)
PURPLE_D = (48, 21, 43, 255)
PURPLE = (63, 29, 57, 255)
PURPLE_L = (76, 38, 69, 255)
LABEL = CASE_HI                      # Panel lettering: the case's light grey on the green.
LABEL_DIM = (147, 170, 152, 255)
METER_FACE = (226, 220, 196, 255)
RED = (200, 32, 32, 255)

layout = []   # (name, type, x, y, w, h), every control and every label outside a control.
runtime = []  # The game's layout (tools/hf_gui_proposals/RUNTIME_SPEC.md): the moving parts the code draws.
RUNTIME = False  # True while drawing the in-game panel: everything that moves is left off it.


def hexc(c):
    return "#%02X%02X%02X" % tuple(c[:3])


def rt(name, kind, x, y, w, h, **extra):
    item = {"name": name, "type": kind, "x": int(x), "y": int(y), "w": int(w), "h": int(h)}
    item.update(extra)
    runtime.append(item)


def knob_cap(size):
    """draw_knob's dark knob without its pointer (the same discs), for the in-game panel."""
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(tile)
    c, r = size / 2.0, size // 2
    disc(d, c, c, r, (24, 24, 24, 255))
    disc(d, c, c, r - 2, (44, 44, 44, 255))
    disc(d, c - 1, c - 1, max(2, r - 5), (58, 58, 58, 255))
    return tile


def add(name, kind, x, y, w, h):
    layout.append({"name": name, "type": kind, "x": int(x), "y": int(y), "w": int(w), "h": int(h)})


def label(img, cx, y, s, name, colour=LABEL):
    text_centred(img, cx, y, s, colour)
    add(name, "label", cx - text_width(s) // 2, y, text_width(s), 5)


def bevel_box(d, x0, y0, x1, y1, fill, hi, lo, outline=None):
    """A raised (hi top-left, lo bottom-right) or sunken (swap them) rectangle with an optional outline round it."""
    if outline:
        d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], outline=outline)
    d.rectangle([x0, y0, x1, y1], fill=fill)
    d.line([x0, y0, x1 - 1, y0], fill=hi)
    d.line([x0, y0, x0, y1 - 1], fill=hi)
    d.line([x0 + 1, y1, x1, y1], fill=lo)
    d.line([x1, y0 + 1, x1, y1], fill=lo)


def knob(img, cx, cy, size, name, frac=0.5, ticks=True):
    """A dark knob like the block's (dark grey, pale pointer) sitting in a thin silver skirt, with scale dots."""
    d = ImageDraw.Draw(img)
    r = size // 2
    if ticks:
        for i in range(11):
            a = math.radians(135.0 + 27.0 * i)
            x, y = cx + math.cos(a) * (r + 4), cy + math.sin(a) * (r + 4)
            d.point((round(x - 0.5), round(y - 0.5)), fill=LABEL_DIM if 0 < i < 10 else LABEL)
    disc(d, cx + 1, cy + 1, r + 2, GREEN_DD)          # Its shadow on the panel.
    disc(d, cx, cy, r + 2, KNOB_HI)                     # The skirt.
    disc(d, cx, cy, r + 1, KNOB_M)
    if RUNTIME:
        tile = knob_cap(size)
    else:
        tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_knob(tile, 0, 0, size, frac, False)
    img.alpha_composite(tile, (cx - r, cy - r))
    add(name, "knob", cx - r - 5, cy - r - 5, size + 10, size + 10)
    # draw_knob's pointer: from 0.3 r out to r - 2, pale, from 7 o'clock (-135) round to 5 o'clock (+135).
    rt(name, "knob", cx - r - 5, cy - r - 5, size + 10, size + 10, cx=cx, cy=cy, pointer_from=int(r * 0.3),
       pointer_r=r - 2, pointer_width=1, pointer_colour="#EEEEEE", min_deg=-135, max_deg=135)


def push_button(img, x, y, w, h, s, name, lamp=None):
    """A grey push button with its legend on it, like the block's little square buttons (lamp: an LED above)."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=GREEN_DD)
    bevel_box(d, x, y, x + w - 1, y + h - 1, KNOB_D, KNOB_HI, KNOB_DD)
    d.rectangle([x + 1, y + 1, x + w - 2, y + 1], fill=KNOB_M)
    text_centred(img, x + w // 2, y + (h - 5) // 2, s, CASE_HI)
    add(name, "button", x - 1, y - 1, w + 2, h + 2)
    rt(name, "button", x, y, w, h)   # Its LED shows the selection, so no "lit".
    if lamp:
        lx, ly = lamp
        d.rectangle([lx - 2, ly - 2, lx + 1, ly + 1], fill=GREEN_DD)
        d.rectangle([lx - 1, ly - 1, lx, ly], fill=(70, 24, 20, 255))
        add(name + "_lamp", "lamp", lx - 2, ly - 2, 4, 4)
        rt(name + "_lamp", "lamp", lx - 1, ly - 1, 2, 2, colour="#FF4030")


def slide_switch(img, x, y, positions, name, title, vertical=True):
    """A small slide switch in a dark slot, its handle at the first position; positions are labelled beside it."""
    d = ImageDraw.Draw(img)
    n = len(positions)
    if vertical:
        sw, sh = 7, 10 * n + 2
        d.rectangle([x, y, x + sw - 1, y + sh - 1], fill=GREEN_DD)
        d.rectangle([x + 1, y + 1, x + sw - 2, y + sh - 2], fill=KNOB_DD)
        hy = y + 2
        if not RUNTIME:
            bevel_box(d, x + 1, hy, x + sw - 2, hy + 7, KNOB_HI, CASE_HI, KNOB_M)
            d.line([x + 2, hy + 3, x + sw - 3, hy + 3], fill=KNOB_M)
        add(name, "switch", x, y, sw, sh)
        rt(name, "switch", x, y, sw, sh, positions=positions,
           handles=[[x + 1, y + 2 + i * 10, sw - 2, 8] for i in range(n)],
           handle_colour=hexc(KNOB_HI), handle_shadow=hexc(KNOB_M))
        for i, s in enumerate(positions):
            py = y + 3 + i * 10
            text(img, x + sw + 3, py, s, LABEL)
            add(name + "_" + s.lower(), "label", x + sw + 3, py, text_width(s), 5)
        label(img, x + 3 + (text_width(max(positions, key=len)) + 3) // 2, y - 9, title, name + "_title")
    else:
        sw, sh = 14 * n + 2, 7
        d.rectangle([x, y, x + sw - 1, y + sh - 1], fill=GREEN_DD)
        d.rectangle([x + 1, y + 1, x + sw - 2, y + sh - 2], fill=KNOB_DD)
        if not RUNTIME:
            bevel_box(d, x + 2, y + 1, x + 13, y + sh - 2, KNOB_HI, CASE_HI, KNOB_M)
            d.line([x + 7, y + 2, x + 7, y + sh - 3], fill=KNOB_M)
        add(name, "switch", x, y, sw, sh)
        rt(name, "switch", x, y, sw, sh, positions=positions,
           handles=[[x + 2 + i * 14, y + 1, 12, sh - 2] for i in range(n)],
           handle_colour=hexc(KNOB_HI), handle_shadow=hexc(KNOB_M))
        for i, s in enumerate(positions):
            cx = x + 8 + i * 14
            text_centred(img, cx, y + sh + 3, s, LABEL)
            add(name + "_" + s.lower(), "label", cx - text_width(s) // 2, y + sh + 3, text_width(s), 5)


METER_START, METER_SWEEP = 220.0, 100.0   # Degrees (screen, y down) where the scale starts, and how far it runs.


def meter(img, x, y, mw, mh, name):
    """The analog meter: a cream face in the block's dark purple bezel and an S-meter scale (S1 to S9 in black, then
    +20 and +40 dB in red), with no needle: code draws that from the pivot (bottom middle, 3 px up) along the same
    arc, so the JSON records the pivot, radius and sweep."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 4, y - 4, x + mw + 3, y + mh + 3], fill=GREEN_DD)
    bevel_box(d, x - 3, y - 3, x + mw + 2, y + mh + 2, PURPLE, PURPLE_L, PURPLE_DD)
    d.rectangle([x - 1, y - 1, x + mw, y + mh], fill=PURPLE_DD)
    d.rectangle([x, y, x + mw - 1, y + mh - 1], fill=METER_FACE)
    d.line([x, y, x + mw - 1, y], fill=(196, 188, 160, 255))  # The bezel's shadow on the face.
    cx, cy = x + mw // 2, y + mh - 3
    r = mh - 16
    ink = (40, 40, 40, 255)

    def at(v, rad):
        ang = math.radians(METER_START + METER_SWEEP * v)
        return cx + math.cos(ang) * rad, cy + math.sin(ang) * rad

    for i in range(201):   # The arc itself, red past S9.
        v = i / 200.0
        px, py = at(v, r)
        d.point((round(px - 0.5), round(py - 0.5)), fill=RED if v > 0.6 else ink)
    marks = [(0.6 * k / 9, k % 2 == 1) for k in range(1, 10)] + [(0.7, False), (0.8, True), (0.9, False), (1.0, True)]
    for v, major in marks:
        for k in range(r + 1, r + (4 if major else 3)):
            px, py = at(v, k)
            d.point((round(px - 0.5), round(py - 0.5)), fill=RED if v > 0.6 else ink)
    for v, s in ((0.6 / 9, "1"), (1.8 / 9, "3"), (3.0 / 9, "5"), (4.2 / 9, "7"), (0.6, "9"), (0.8, "20"), (1.0, "40")):
        px, py = at(v, r + 9)
        text_centred(img, round(px), round(py) - 2, s, RED if v > 0.6 else ink)
    disc(d, cx, cy + 1, 3, KNOB_D)
    text(img, x + 3, y + mh - 7, "S", (90, 90, 90, 255))
    text(img, x + mw - 3 - text_width("+DB"), y + mh - 7, "+DB", RED)
    add(name, "meter", x - 4, y - 4, mw + 8, mh + 8)
    # Spec angles: 0 straight up, clockwise; the scale's screen angles (0 east) are 90 more.
    rt(name, "meter", x, y, mw, mh, pivot=[cx, cy], radius=r + 2, zero_deg=METER_START + 90 - 360,
       full_deg=METER_START + METER_SWEEP + 90 - 360, s9_fraction=0.6, needle_colour="#202020")
    return {"pivot": [cx, cy], "radius": r, "start_deg": METER_START, "sweep_deg": METER_SWEEP,
            "s9_fraction": 0.6, "face": [x, y, mw, mh]}


def draw():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # The light grey case, a bevelled frame round the green front panel, with the block's feet under it.
    d.rectangle([0, 0, W - 1, H - 5], fill=CASE_EDGE)
    bevel_box(d, 1, 1, W - 2, H - 6, CASE, CASE_HI, CASE_LO)
    d.rectangle([3, 3, W - 4, 4], fill=CASE_HI)
    for fx in (14, W - 30):
        d.rectangle([fx, H - 5, fx + 15, H - 1], fill=CASE_EDGE)
        d.rectangle([fx + 1, H - 5, fx + 14, H - 2], fill=KNOB_D)
    fx0, fy0, fx1, fy1 = 7, 7, W - 8, H - 12
    d.rectangle([fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1], fill=CASE_LO)
    d.rectangle([fx0, fy0, fx1, fy1], fill=GREEN_DD)
    d.rectangle([fx0 + 1, fy0 + 1, fx1 - 1, fy1 - 1], fill=GREEN)
    for yy in range(fy0 + 3, fy1, 3):   # A faint brushed grain, like the block's two greens.
        d.line([fx0 + 1, yy, fx1 - 1, yy], fill=GREEN_D if yy % 2 else (40, 91, 52, 255))
    d.line([fx0 + 1, fy0 + 1, fx1 - 1, fy0 + 1], fill=GREEN_L)
    d.line([fx0 + 1, fy0 + 1, fx0 + 1, fy1 - 1], fill=GREEN_L)
    for sx, sy in ((fx0 + 3, fy0 + 3), (fx1 - 4, fy0 + 3), (fx0 + 3, fy1 - 4), (fx1 - 4, fy1 - 4)):  # Screws.
        d.rectangle([sx - 1, sy - 1, sx + 1, sy + 1], fill=KNOB_HI)
        d.point((sx, sy), fill=KNOB_M)

    # Upper left, the block's 2 x 2 knob group: AF and squelch over the power button and mic gain.
    label(img, 30, 14, "AF", "af_label")
    knob(img, 30, 36, 20, "af_gain")
    label(img, 66, 14, "SQL", "squelch_label")
    knob(img, 66, 36, 20, "squelch")
    label(img, 30, 66, "POWER", "power_label")
    push_button(img, 20, 78, 21, 14, "", "power", lamp=None)
    d.arc([26, 81, 34, 89], 300, 240, fill=(230, 80, 64, 255))   # The power symbol on the button.
    d.line([30, 80, 30, 85], fill=(230, 80, 64, 255))
    label(img, 66, 66, "MIC", "mic_gain_label")
    knob(img, 66, 88, 20, "mic_gain")

    # Top middle, the purple display window (the frequency is drawn in code).
    dx, dy, dw, dh = 88, 14, 82, 26
    d.rectangle([dx - 4, dy - 4, dx + dw + 3, dy + dh + 3], fill=GREEN_DD)
    bevel_box(d, dx - 3, dy - 3, dx + dw + 2, dy + dh + 2, PURPLE, PURPLE_L, PURPLE_DD)
    d.rectangle([dx - 1, dy - 1, dx + dw, dy + dh], fill=PURPLE_DD)
    d.rectangle([dx, dy, dx + dw - 1, dy + dh - 1], fill=(18, 8, 16, 255))
    d.line([dx, dy, dx + dw - 1, dy], fill=(10, 4, 9, 255))
    add("display", "display", dx - 4, dy - 4, dw + 8, dh + 8)
    rt("display", "display", dx, dy, dw, dh, ink="#7CFF9A")

    # Under it, the big tuning dial in a ring of scale marks.
    tx, ty, ts = 129, 98, 40
    for i in range(36):
        a = math.radians(i * 10)
        r0, r1 = (25, 28) if i % 3 == 0 else (25, 26)
        for k in range(r0, r1 + 1):
            d.point((round(tx + math.cos(a) * k - 0.5), round(ty + math.sin(a) * k - 0.5)), fill=LABEL_DIM if i % 3 else LABEL)
    disc(d, tx + 1, ty + 2, ts // 2 + 3, GREEN_DD)
    disc(d, tx, ty, ts // 2 + 3, KNOB_HI)
    disc(d, tx, ty, ts // 2 + 2, KNOB_D)
    if RUNTIME:
        img.alpha_composite(knob_cap(ts), (tx - ts // 2, ty - ts // 2))
    else:
        tile = Image.new("RGBA", (ts, ts), (0, 0, 0, 0))
        draw_knob(tile, 0, 0, ts, 0.5, False)
        img.alpha_composite(tile, (tx - ts // 2, ty - ts // 2))
        disc(d, tx + 8, ty + 7, 4, KNOB_DD)             # The finger dimple.
        disc(d, tx + 8, ty + 7, 3, KNOB_HI)
    add("tuning_dial", "dial", tx - 29, ty - 29, 58, 58)
    rt("tuning", "dial", tx - 29, ty - 29, 58, 58, cx=tx, cy=ty, mark_r=11, mark_size=5, mark_colour=hexc(KNOB_HI),
       mark_shadow=hexc(KNOB_DD))
    label(img, tx, ty + 32, "TUNING", "tuning_label")

    # Top right, the meter window with its function switch under it.
    mx, my, mw, mh = 184, 12, 58, 40
    meter_info = meter(img, mx, my, mw, mh, "meter")
    slide_switch(img, 198, 60, ["PO", "SWR"], "meter_select", "", vertical=False)   # S is always on receive.

    # Lower right, the block's second knob group: mode buttons, the AGC switch, PTT.
    label(img, 179, 79, "MODE", "mode_label")
    push_button(img, 164, 87, 30, 11, "SSB", "mode_ssb", lamp=(200, 92))
    push_button(img, 164, 102, 30, 11, "CW", "mode_cw", lamp=(200, 107))
    push_button(img, 164, 117, 30, 11, "AM", "mode_am", lamp=(200, 122))
    slide_switch(img, 210, 90, ["FAST", "SLOW", "OFF"], "agc", "AGC")
    push_button(img, 164, 132, 77, 14, "PTT", "ptt")

    # The 10x coarse-tuning key, latching, in the space up and left of the tuning dial; its little lamp shows it on.
    cx0, cy0 = 86, 52
    push_button(img, cx0, cy0, 21, 11, "", "coarse")
    d.rectangle([cx0 + 2, cy0 + 4, cx0 + 4, cy0 + 6], fill=(70, 24, 20, 255))
    text(img, cx0 + 7, cy0 + 3, "10X", CASE_HI)
    runtime[-1]["lit"] = [cx0 + 2, cy0 + 4, 3, 3, "#FFC040"]

    # The QUICK key (quick-recall frequency), up and right of the dial, matching the 10x key; lit while on it.
    qx, qy = 145, 52
    push_button(img, qx, qy, 29, 11, "", "quick")
    d.rectangle([qx + 2, qy + 4, qx + 4, qy + 6], fill=(70, 24, 20, 255))
    text(img, qx + 7, qy + 3, "QUICK", CASE_HI)
    runtime[-1]["lit"] = [qx + 2, qy + 4, 3, 3, "#FFC040"]

    # The maker's plate, lower left.
    text(img, 14, 118, "HF-40", CASE_HI)
    add("brand", "label", 14, 118, text_width("HF-40"), 5)
    text(img, 14, 126, "40M SSB/CW/AM", LABEL_DIM)
    add("brand2", "label", 14, 126, text_width("40M SSB/CW/AM"), 5)
    text(img, 14, 134, "TRANSCEIVER", LABEL_DIM)
    add("brand3", "label", 14, 134, text_width("TRANSCEIVER"), 5)

    return img, meter_info


def main():
    global RUNTIME
    img, meter_info = draw()
    img.save(OUT_PNG)
    check(layout)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump({"texture": "radiocraft:textures/gui/" + NAME + "_proposed.png", "width": W, "height": H,
                   "meter": meter_info, "controls": layout}, f, indent=2)
    print("ok", OUT_PNG)
    # The in-game panel: the same drawing with the moving parts left off, and the layout the screen reads.
    layout.clear()
    runtime.clear()
    RUNTIME = True
    img, _ = draw()
    check(layout)
    os.makedirs(os.path.dirname(RUNTIME_PNG), exist_ok=True)
    os.makedirs(os.path.dirname(RUNTIME_JSON), exist_ok=True)
    img.save(RUNTIME_PNG)
    with open(RUNTIME_JSON, "w", encoding="utf-8") as f:
        json.dump({"texture": "radiocraft:textures/gui/hf_panels/" + NAME + ".png", "width": W, "height": H,
                   "controls": runtime}, f, indent=2)
    print("ok", RUNTIME_PNG, RUNTIME_JSON)


def check(items, gap=2):
    """No two rectangles may touch or come within gap pixels of each other, and all must sit on the panel."""
    for a in items:
        assert a["x"] >= 0 and a["y"] >= 0 and a["x"] + a["w"] <= W and a["y"] + a["h"] <= H, a
    for i, a in enumerate(items):
        for b in items[i + 1:]:
            apart = (a["x"] + a["w"] + gap <= b["x"] or b["x"] + b["w"] + gap <= a["x"]
                     or a["y"] + a["h"] + gap <= b["y"] or b["y"] + b["h"] + gap <= a["y"])
            assert apart, "overlap: %s and %s" % (a, b)


if __name__ == "__main__":
    main()
