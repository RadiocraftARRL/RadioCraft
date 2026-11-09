"""Proposed GUI background for the 80m HF radio (2026-11-09), drawn to match its block model.

The block (models/block/hf_radio_80m.json, textures/block/hf_radio_80m.png) is a tall, nearly square grey cabinet.
Seen from the front: a gold meter scale painted on the upper left of the face, a raised pale speckled box at the upper
right (the speaker grille, with the block's DISPLAY quad on its front), two small knobs down the left side, a lever
switch in the middle, a dark maroon box at the lower left carrying the big maroon tuning knob, a small knob at the
bottom middle and two little buttons at the bottom right. Its colours: mid grey face, maroon knobs, gold and tan, and a
yellow pilot lamp. This panel keeps that arrangement and those colours.

Writes (nothing in game uses them yet):
  src/main/resources/assets/radiocraft/textures/gui/hf_radio_80m_proposed.png
  tools/hf_gui_proposals/hf_radio_80m_proposed_layout.json   every control's rectangle, for wiring it up later
The display window and the meter are left without digits and needle: the screen draws those in code.
Needs Pillow. Run: python tools/hf_gui_proposals/hf_radio_80m_proposed.py
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from cb_gui_assets import disc, text, text_centred, text_width  # noqa: E402

NAME = "hf_radio_80m"
OUT_PNG = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "radiocraft", "textures", "gui",
                       NAME + "_proposed.png")
OUT_JSON = os.path.join(HERE, NAME + "_proposed_layout.json")
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "radiocraft")
RUNTIME_PNG = os.path.join(ASSETS, "textures", "gui", "hf_panels", NAME + ".png")
RUNTIME_JSON = os.path.join(ASSETS, "gui_layouts", "hf_" + NAME + ".json")

W, H = 224, 216

# ---- The block texture's palette ---------------------------------------------------------------------------------
EDGE = (46, 46, 46, 255)
FACE_LL = (108, 108, 108, 255)
FACE_LO = (120, 120, 120, 255)
FACE_L2 = (134, 134, 134, 255)
FACE = (147, 147, 147, 255)
FACE_HI = (173, 173, 173, 255)
FACE_HH = (195, 195, 195, 255)
MAROON_DD = (52, 24, 22, 255)
MAROON_D = (70, 34, 32, 255)
MAROON = (77, 42, 40, 255)
MAROON_M = (97, 59, 57, 255)
MAROON_L = (108, 69, 67, 255)
MAROON_HL = (116, 76, 73, 255)
GOLD = (220, 203, 130, 255)
GOLD_D = (192, 175, 103, 255)
OLIVE = (66, 62, 42, 255)
TAN = (221, 205, 167, 255)
TAN_D = (179, 167, 128, 255)
GRILLE = (193, 191, 191, 255)
GRILLE_HI = (211, 210, 210, 255)
GRILLE_LO = (183, 180, 174, 255)
DARK = (34, 34, 34, 255)
DARKER = (28, 28, 28, 255)
LAMP = (248, 221, 114, 255)
LAMP_HI = (255, 248, 153, 255)
LABEL = (34, 34, 34, 255)           # Engraved lettering: near black, with a pale line under it.
LABEL_SHADOW = (173, 173, 173, 255)

layout = []   # (name, type, x, y, w, h), every control and every label outside a control.
runtime = []  # The game's layout (tools/hf_gui_proposals/RUNTIME_SPEC.md): the moving parts the code draws.
RUNTIME = False  # True while drawing the in-game panel: everything that moves is left off it.


def hexc(c):
    return "#%02X%02X%02X" % tuple(c[:3])


def rt(name, kind, x, y, w, h, **extra):
    item = {"name": name, "type": kind, "x": int(x), "y": int(y), "w": int(w), "h": int(h)}
    item.update(extra)
    runtime.append(item)


def add(name, kind, x, y, w, h, **extra):
    item = {"name": name, "type": kind, "x": int(x), "y": int(y), "w": int(w), "h": int(h)}
    item.update(extra)
    layout.append(item)


def engraved(img, x, y, s, colour=LABEL):
    text(img, x + 1, y + 1, s, LABEL_SHADOW)
    text(img, x, y, s, colour)


def label(img, cx, y, s, name, colour=LABEL):
    x = cx - text_width(s) // 2
    engraved(img, x, y, s, colour)
    add(name, "label", x, y, text_width(s) + 1, 6)


def bevel_box(d, x0, y0, x1, y1, fill, hi, lo):
    d.rectangle([x0, y0, x1, y1], fill=fill)
    d.line([x0, y0, x1 - 1, y0], fill=hi)
    d.line([x0, y0, x0, y1 - 1], fill=hi)
    d.line([x0 + 1, y1, x1, y1], fill=lo)
    d.line([x1, y0 + 1, x1, y1], fill=lo)


def maroon_knob(img, cx, cy, size, frac=0.5):
    """The block's maroon knob: dark rim, maroon body with a lighter cap, a tan pointer (frac 0 = 7 o'clock,
    0.5 = 12 o'clock, 1 = 5 o'clock)."""
    d = ImageDraw.Draw(img)
    r = size // 2
    disc(d, cx + 1, cy + 1, r, FACE_LL)                  # Its shadow on the face.
    disc(d, cx, cy, r, MAROON_DD)
    disc(d, cx, cy, r - 1, MAROON)
    for k in range(max(12, size)):                       # The knurled skirt.
        a = 2 * math.pi * k / max(12, size)
        d.point((round(cx + math.cos(a) * (r - 1.5) - 0.5), round(cy + math.sin(a) * (r - 1.5) - 0.5)), fill=MAROON_D)
    disc(d, cx, cy, r - 3, MAROON_M)
    disc(d, cx - 1, cy - 1, max(2, r - 5), MAROON_L)
    disc(d, cx - 2, cy - 2, max(1, r // 4), MAROON_HL)   # Shine.
    if RUNTIME:
        return   # The code draws the pointer.
    a = math.radians(135.0 + 270.0 * frac)
    for i in range(int(r * 0.25), r - 1):
        x, y = cx + math.cos(a) * i, cy + math.sin(a) * i
        d.point((round(x - 0.5), round(y - 0.5)), fill=TAN)
        if size >= 30:
            d.point((round(x - 0.5) + 1, round(y - 0.5)), fill=TAN)


def knob(img, cx, cy, size, name, frac=0.5):
    d = ImageDraw.Draw(img)
    r = size // 2
    for i in range(11):   # Scale dots round it.
        a = math.radians(135.0 + 27.0 * i)
        d.point((round(cx + math.cos(a) * (r + 3) - 0.5), round(cy + math.sin(a) * (r + 3) - 0.5)),
                fill=DARK if i in (0, 10) else FACE_LO)
    maroon_knob(img, cx, cy, size, frac)
    add(name, "knob", cx - r - 4, cy - r - 4, size + 8, size + 8)
    # maroon_knob's pointer: tan, from r / 4 out to r - 2, 7 o'clock (-135) round to 5 o'clock (+135).
    rt(name, "knob", cx - r - 4, cy - r - 4, size + 8, size + 8, cx=cx, cy=cy, pointer_from=int(r * 0.25),
       pointer_r=r - 2, pointer_width=1, pointer_colour=hexc(TAN), min_deg=-135, max_deg=135)


def push_button(img, x, y, w, h, s, name):
    d = ImageDraw.Draw(img)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=EDGE)
    bevel_box(d, x, y, x + w - 1, y + h - 1, MAROON_M, MAROON_HL, MAROON_D)
    d.line([x + 1, y + h - 2, x + w - 2, y + h - 2], fill=MAROON)
    if s:
        text_centred(img, x + w // 2, y + (h - 5) // 2, s, TAN)
    add(name, "button", x - 1, y - 1, w + 2, h + 2)
    rt(name, "button", x, y, w, h)


def slide_switch(img, x, y, positions, name, vertical=True, spacing=None):
    """A small slide switch in a dark slot, handle at the first position, positions labelled beside or under it."""
    d = ImageDraw.Draw(img)
    n = len(positions)
    spacing = spacing or (10 if vertical else 14)
    if vertical:
        sw, sh = 7, spacing * n + 2
        d.rectangle([x, y, x + sw - 1, y + sh - 1], fill=EDGE)
        d.rectangle([x + 1, y + 1, x + sw - 2, y + sh - 2], fill=DARKER)
        if not RUNTIME:
            bevel_box(d, x + 1, y + 2, x + sw - 2, y + 9, TAN, (240, 230, 200, 255), TAN_D)
            d.line([x + 2, y + 5, x + sw - 3, y + 5], fill=TAN_D)
        add(name, "switch", x, y, sw, sh, positions=positions)
        rt(name, "switch", x, y, sw, sh, positions=positions,
           handles=[[x + 1, y + 2 + i * spacing, sw - 2, 8] for i in range(n)],
           handle_colour=hexc(TAN), handle_shadow=hexc(TAN_D))
        for i, s in enumerate(positions):
            py = y + 3 + i * spacing
            engraved(img, x + sw + 3, py, s)
            add(name + "_" + s.lower(), "label", x + sw + 3, py, text_width(s) + 1, 6)
    else:
        sw, sh = spacing * n + 2, 7
        d.rectangle([x, y, x + sw - 1, y + sh - 1], fill=EDGE)
        d.rectangle([x + 1, y + 1, x + sw - 2, y + sh - 2], fill=DARKER)
        if not RUNTIME:
            bevel_box(d, x + 2, y + 1, x + spacing - 1, y + sh - 2, TAN, (240, 230, 200, 255), TAN_D)
            d.line([x + spacing // 2, y + 2, x + spacing // 2, y + sh - 3], fill=TAN_D)
        add(name, "switch", x, y, sw, sh, positions=positions)
        rt(name, "switch", x, y, sw, sh, positions=positions,
           handles=[[x + 2 + i * spacing, y + 1, spacing - 2, sh - 2] for i in range(n)],
           handle_colour=hexc(TAN), handle_shadow=hexc(TAN_D))
        for i, s in enumerate(positions):
            cx = x + 1 + spacing // 2 + i * spacing
            label(img, cx, y + sh + 3, s, name + "_" + s.lower())


METER_START, METER_SWEEP = 220.0, 100.0   # Degrees (screen, y down) where the scale starts, and how far it runs.


def meter(img, x, y, mw, mh, name):
    """The analog meter: the block's gold scale on a tan face in a dark bezel with a gold trim line. S1 to S9 in
    olive, +20 and +40 dB in maroon. No needle: code draws it from the pivot along the same arc (see the JSON)."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 5, y - 5, x + mw + 4, y + mh + 4], fill=FACE_LL)
    d.rectangle([x - 4, y - 4, x + mw + 3, y + mh + 3], fill=EDGE)
    d.rectangle([x - 2, y - 2, x + mw + 1, y + mh + 1], outline=GOLD_D)
    d.rectangle([x - 1, y - 1, x + mw, y + mh], outline=DARKER)
    d.rectangle([x, y, x + mw - 1, y + mh - 1], fill=TAN)
    d.line([x, y, x + mw - 1, y], fill=TAN_D)
    d.line([x, y, x, y + mh - 1], fill=TAN_D)
    cx, cy = x + mw // 2, y + mh - 3
    r = mh - 16
    ink, hot = OLIVE, (150, 40, 34, 255)

    def at(v, rad):
        ang = math.radians(METER_START + METER_SWEEP * v)
        return cx + math.cos(ang) * rad, cy + math.sin(ang) * rad

    for band, colour in ((r - 1, GOLD_D), (r - 2, GOLD)):   # A gold band under the scale, like the block's arc.
        for i in range(301):
            px, py = at(i / 300.0, band)
            d.point((round(px - 0.5), round(py - 0.5)), fill=colour)
    for i in range(301):
        v = i / 300.0
        px, py = at(v, r)
        d.point((round(px - 0.5), round(py - 0.5)), fill=hot if v > 0.6 else ink)
    marks = [(0.6 * k / 9, k % 2 == 1) for k in range(1, 10)] + [(0.7, False), (0.8, True), (0.9, False), (1.0, True)]
    for v, major in marks:
        for k in range(r + 1, r + (5 if major else 3)):
            px, py = at(v, k)
            d.point((round(px - 0.5), round(py - 0.5)), fill=hot if v > 0.6 else ink)
    for v, s in ((0.6 / 9, "1"), (1.8 / 9, "3"), (3.0 / 9, "5"), (4.2 / 9, "7"), (0.6, "9"), (0.8, "+20"), (1.0, "+40")):
        px, py = at(v, r + 10)
        text_centred(img, round(px), round(py) - 2, s, hot if v > 0.6 else ink)
    disc(d, cx, cy + 1, 4, MAROON_D)
    disc(d, cx, cy + 1, 2, MAROON_M)
    text(img, x + 3, y + mh - 7, "S", ink)
    text(img, x + mw - 3 - text_width("DB"), y + mh - 7, "DB", hot)
    add(name, "meter", x - 5, y - 5, mw + 10, mh + 10)
    # Spec angles: 0 straight up, clockwise; the scale's screen angles (0 east) are 90 more.
    rt(name, "meter", x, y, mw, mh, pivot=[cx, cy], radius=r + 2, zero_deg=METER_START + 90 - 360,
       full_deg=METER_START + METER_SWEEP + 90 - 360, s9_fraction=0.6, needle_colour=hexc(MAROON_DD))
    return {"pivot": [cx, cy], "radius": r, "start_deg": METER_START, "sweep_deg": METER_SWEEP,
            "s9_fraction": 0.6, "face": [x, y, mw, mh]}


def draw():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # The cabinet: a dark outline, a bevelled grey rim and the face inside it, with a faint grain.
    d.rectangle([0, 0, W - 1, H - 1], fill=EDGE)
    bevel_box(d, 1, 1, W - 2, H - 2, FACE_HI, FACE_HH, FACE_LL)
    fx0, fy0, fx1, fy1 = 6, 6, W - 7, H - 7
    d.rectangle([fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1], fill=FACE_LO)
    d.rectangle([fx0, fy0, fx1, fy1], fill=FACE)
    streaks = Image.new("RGBA", (fx1 - fx0 + 1, fy1 - fy0 + 1), (0, 0, 0, 0))
    sd = ImageDraw.Draw(streaks)
    for k in range(-streaks.height, streaks.width, 7):   # The block face's faint diagonal streaks.
        sd.line([k, streaks.height - 1, k + streaks.height - 1, 0], fill=(142, 142, 142, 255))
    img.alpha_composite(streaks, (fx0, fy0))
    d.rectangle([fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1], outline=FACE_LO)
    d.line([fx0, fy1 + 1, fx1 + 1, fy1 + 1], fill=FACE_HH)
    d.line([fx1 + 1, fy0, fx1 + 1, fy1 + 1], fill=FACE_HH)
    for sx, sy in ((fx0 + 3, fy0 + 3), (fx1 - 3, fy0 + 3), (fx0 + 3, fy1 - 3), (fx1 - 3, fy1 - 3)):  # Screws.
        disc(d, sx + 1, sy + 1, 2, FACE_LL)
        disc(d, sx + 1, sy + 1, 1, FACE_HH)

    # Upper left: the meter, its function switch under it.
    meter_info = meter(img, 16, 16, 84, 48, "meter")
    engraved(img, 14, 76, "METER")
    add("meter_select_title", "label", 14, 76, text_width("METER") + 1, 6)
    slide_switch(img, 40, 75, ["PO", "SWR"], "meter_select", vertical=False, spacing=18)   # S is always on receive.

    # Upper right: the block's raised speckled box, the display window in its top, the grille below.
    bx0, by0, bx1, by1 = 112, 12, 211, 94
    d.rectangle([bx0 - 1, by0 - 1, bx1 + 2, by1 + 2], fill=FACE_LL)
    bevel_box(d, bx0, by0, bx1, by1, GRILLE, GRILLE_HI, GRILLE_LO)
    dx, dy, dw, dh = 121, 20, 82, 26
    d.rectangle([dx - 3, dy - 3, dx + dw + 2, dy + dh + 2], fill=FACE_LL)
    d.rectangle([dx - 2, dy - 2, dx + dw + 1, dy + dh + 1], fill=DARK)
    d.rectangle([dx, dy, dx + dw - 1, dy + dh - 1], fill=(20, 18, 18, 255))
    d.line([dx, dy, dx + dw - 1, dy], fill=(12, 10, 10, 255))
    add("display", "display", dx - 3, dy - 3, dw + 6, dh + 6)
    rt("display", "display", dx, dy, dw, dh, ink="#FFC860")
    for yy in range(56, by1 - 3, 3):        # The grille: rows of holes, staggered.
        for xx in range(bx0 + 7 + (yy // 3 % 2), bx1 - 5, 3):
            d.point((xx, yy), fill=FACE_LO)
            d.point((xx + 1, yy + 1), fill=GRILLE_HI)
    add("speaker_grille", "decor", bx0 + 5, 54, bx1 - bx0 - 9, by1 - 56)

    # The middle band: AF, squelch and mic gain knobs, the block's lever switch for the mode, the AGC switch.
    for cx, s, nm in ((24, "AF", "af_gain"), (58, "SQL", "squelch"), (92, "MIC", "mic_gain")):
        label(img, cx, 99, s, nm + "_label")
        knob(img, cx, 121, 20, nm)
    lx, ly = 148, 134   # The mode lever, pointing up and left at SSB (the other detents: straight up CW, up and right AM).
    label(img, lx, 99, "MODE", "mode_label")
    disc(d, lx, ly, 10, FACE_LO)
    disc(d, lx, ly, 9, FACE_HH)
    disc(d, lx, ly, 8, FACE_HI)
    for a_deg in (225, 270, 315):   # Detent marks.
        a = math.radians(a_deg)
        d.line([round(lx + math.cos(a) * 11), round(ly + math.sin(a) * 11),
                round(lx + math.cos(a) * 13), round(ly + math.sin(a) * 13)], fill=DARK)
    a = math.radians(225)
    hx, hy = lx + math.cos(a) * 11, ly + math.sin(a) * 11
    if not RUNTIME:   # The bat; in game the code draws it, over the hub, at the mode's angle.
        d.line([lx + 1, ly + 1, round(hx) + 1, round(hy) + 1], fill=MAROON_DD, width=5)
        d.line([lx, ly, round(hx), round(hy)], fill=MAROON_D, width=5)
        d.line([lx, ly, round(hx), round(hy)], fill=MAROON_M, width=3)
        d.line([lx - 1, ly - 1, round(hx) - 1, round(hy) - 1], fill=MAROON_HL)
    rt("mode_lever", "lever", lx - 13, ly - 14, 27, 27, cx=lx, cy=ly, positions=["SSB", "CW", "AM"],
       angles_deg=[-45, 0, 45], length=11, width=3, colour=hexc(MAROON_M), tip_colour=hexc(MAROON_HL), tip_size=4)
    disc(d, lx, ly, 5, MAROON_DD)
    disc(d, lx, ly, 4, MAROON_M)
    disc(d, lx - 1, ly - 1, 2, MAROON_HL)
    add("mode_lever", "mode_switch", lx - 13, ly - 14, 27, 27, positions=["SSB", "CW", "AM"])
    engraved(img, lx - 13 - 3 - text_width("SSB") - 1, ly - 15, "SSB")
    add("mode_ssb", "label", lx - 13 - 3 - text_width("SSB") - 1, ly - 15, text_width("SSB") + 1, 6)
    engraved(img, lx - text_width("CW") // 2, ly - 23, "CW")
    add("mode_cw", "label", lx - text_width("CW") // 2, ly - 23, text_width("CW") + 1, 6)
    engraved(img, lx + 13 + 3, ly - 15, "AM")
    add("mode_am", "label", lx + 13 + 3, ly - 15, text_width("AM") + 1, 6)

    label(img, 188, 99, "AGC", "agc_label")
    slide_switch(img, 180, 108, ["FAST", "SLOW", "OFF"], "agc")

    # Lower left: the maroon box with the big tuning knob on it, a ring of tan marks round the knob.
    bx0, by0, bx1, by1 = 14, 138, 104, 203
    d.rectangle([bx0 - 1, by0 - 1, bx1 + 2, by1 + 2], fill=EDGE)
    bevel_box(d, bx0, by0, bx1, by1, MAROON_D, MAROON_L, MAROON_DD)
    d.rectangle([bx0 + 2, by0 + 2, bx1 - 2, by1 - 2], outline=MAROON)
    text_centred(img, (bx0 + bx1) // 2, by0 + 5, "TUNING", TAN)
    add("tuning_label", "label", (bx0 + bx1) // 2 - text_width("TUNING") // 2, by0 + 5, text_width("TUNING"), 5)
    tx, ty, ts = (bx0 + bx1) // 2 + 1, 176, 40
    for i in range(36):
        a = math.radians(i * 10)
        r1 = 25 if i % 3 == 0 else 23
        for k in range(22, r1 + 1):
            d.point((round(tx + math.cos(a) * k - 0.5), round(ty + math.sin(a) * k - 0.5)), fill=TAN if i % 3 == 0 else TAN_D)
    maroon_knob(img, tx, ty, ts, 0.5)
    if not RUNTIME:
        disc(d, tx + 8, ty + 7, 4, MAROON_DD)   # The finger dimple.
        disc(d, tx + 8, ty + 7, 3, MAROON)
    add("tuning_dial", "dial", tx - 26, ty - 26, 52, 52)
    rt("tuning", "dial", tx - 26, ty - 26, 52, 52, cx=tx, cy=ty, mark_r=11, mark_size=5, mark_colour=hexc(MAROON),
       mark_shadow=hexc(MAROON_DD))

    # Bottom middle: power with the yellow pilot lamp; bottom right: the maker's plate and PTT.
    label(img, 128, 162, "POWER", "power_label")
    push_button(img, 118, 172, 20, 16, "", "power")
    d.arc([124, 176, 132, 184], 300, 240, fill=LAMP)   # The power symbol.
    d.line([128, 175, 128, 180], fill=LAMP)
    d.rectangle([118, 193, 125, 200], fill=EDGE)
    d.rectangle([119, 194, 124, 199], fill=OLIVE)
    d.rectangle([120, 195, 123, 198], fill=(120, 106, 52, 255))   # The pilot lamp, unlit (code can light it).
    add("pilot_lamp", "lamp", 118, 193, 8, 8)
    rt("pilot_lamp", "lamp", 120, 195, 4, 4, colour=hexc(LAMP))
    engraved(img, 129, 195, "ON")
    add("pilot_lamp_label", "label", 129, 195, text_width("ON") + 1, 6)

    engraved(img, 150, 166, "HF-80")
    add("brand", "label", 150, 166, text_width("HF-80") + 1, 6)
    engraved(img, 150, 174, "80M SSB/CW/AM")
    add("brand2", "label", 150, 174, text_width("80M SSB/CW/AM") + 1, 6)
    push_button(img, 150, 184, 60, 18, "PTT", "ptt")

    # The 10x coarse-tuning key, latching, just off the tuning box's top right corner; its little lamp shows it on.
    cx0, cy0 = 110, 142
    push_button(img, cx0, cy0, 21, 11, "", "coarse")
    d.rectangle([cx0 + 2, cy0 + 4, cx0 + 4, cy0 + 6], fill=(120, 106, 52, 255))
    text(img, cx0 + 7, cy0 + 3, "10X", TAN)
    runtime[-1]["lit"] = [cx0 + 2, cy0 + 4, 3, 3, hexc(LAMP)]

    # The QUICK key (quick-recall frequency), under the mode lever, beside the 10x key; lit while on it.
    qx, qy = 142, 150
    push_button(img, qx, qy, 29, 11, "", "quick")
    d.rectangle([qx + 2, qy + 4, qx + 4, qy + 6], fill=(120, 106, 52, 255))
    text(img, qx + 7, qy + 3, "QUICK", TAN)
    runtime[-1]["lit"] = [qx + 2, qy + 4, 3, 3, hexc(LAMP)]

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
