"""Proposed screen background for the all-band radio (2026-11-09), drawn to match its block model.

The block's front (models/block/all_band_radio.json, textures/block/all_band_radio.png), seen from the player:
  top left     a cream, backlit analog meter          -> the S / PO / SWR meter, top left here too
  top middle   a grey LCD window                       -> the frequency display (left empty and dark; code fills it)
  top right    a small LED and a knob with a bar handle-> POWER and its lamp, the AGC knob below them
  middle left  two amber bars, the short one above     -> amber keys: BAND down/up (short row), modes (long row)
  bottom mid   the big tuning dial with a finger dimple-> the main dial, dimple at its lower left
  bottom left  three small knobs                       -> PTT bar, MIC GAIN, SQL
  bottom right a knob in a light grey ring             -> AF VOL, with the same ring
Colours are the block texture's charcoal greys, its cream meter face and its amber. Lettering and helpers come from
tools/cb_gui_assets.py (the CB radios' art), which this imports and does not change.

Writes:
  src/main/resources/assets/radiocraft/textures/gui/hf_radio_all_band_proposed.png   the viewing copy, every part drawn
  tools/hf_gui_proposals/hf_radio_all_band_proposed_layout.json   every control and label rectangle (x, y, w, h)
  src/main/resources/assets/radiocraft/textures/gui/hf_panels/hf_radio_all_band.png   the runtime panel: no pointers,
      no dial dimple, lamps unlit, mode and meter keys unlit (HfPanel draws those; see RUNTIME_SPEC.md)
  src/main/resources/assets/radiocraft/gui_layouts/hf_hf_radio_all_band.json           the runtime layout
Needs Pillow. Run: python tools/hf_gui_proposals/hf_radio_all_band_proposed.py [preview.png] [--preview DIR]
(preview.png gets a 3x copy of the proposal; --preview DIR draws the runtime panel plus its moving parts, by the
spec's rules, at three settings, 3x, into DIR).
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import cb_gui_assets as cb  # noqa: E402  (the shared pixel font and drawing helpers)

ROOT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "radiocraft")
OUT_PNG = os.path.join(ROOT, "textures", "gui", "hf_radio_all_band_proposed.png")
OUT_JSON = os.path.join(HERE, "hf_radio_all_band_proposed_layout.json")
RUNTIME_PNG = os.path.join(ROOT, "textures", "gui", "hf_panels", "hf_radio_all_band.png")
RUNTIME_JSON = os.path.join(ROOT, "gui_layouts", "hf_hf_radio_all_band.json")
RUNTIME = False  # True while drawing the runtime panel: the moving parts are left for the code.

W, H = 256, 158

# ---- Palette, from textures/block/all_band_radio.png ------------------------------------------------------------
def rnd(v):
    """Round half up (Python's round() goes to even, which makes straight pointer lines wobble)."""
    return int(math.floor(v + 0.5))


def g(v):
    return (v, v, v, 255)


EDGE = g(0x24)          # darkest outline
SHADE = g(0x2A)
DEEP = g(0x36)
CASE = g(0x42)          # the front's main grey
CASE_HI = g(0x47)
CASE_HI2 = g(0x4A)
BEVEL_MID = g(0x51)
KNOB = g(0x54)
BEVEL_HI = g(0x60)
KNOB_HI = g(0x66)
MID = g(0x7E)
LCD_GREY = g(0x98)
RING_DARK = g(0xAE)
RING = g(0xC1)
LABEL = g(0xC1)         # lettering on the case
CREAM_HI = (255, 249, 231, 255)
CREAM = (245, 236, 210, 255)
CREAM_MID = (227, 215, 180, 255)
CREAM_LO = (198, 186, 153, 255)
AMBER = (248, 221, 114, 255)
AMBER_LO = (196, 160, 64, 255)
AMBER_HI = (255, 240, 176, 255)
WARM = (123, 121, 115, 255)
INK = g(0x2A)           # meter lettering
RED = (176, 40, 32, 255)
SCREEN = (20, 22, 21, 255)
KEY_UNLIT = ((150, 124, 56, 255), (110, 90, 40, 255), (176, 150, 80, 255))  # Amber keys, backlight off (runtime).
LIT = "#F8DD72"


def hexc(c):
    return "#%02X%02X%02X" % c[:3]


def spec_deg(screen_deg):
    """Screen degrees (from +x, y down) to the spec's: 0 straight up, positive clockwise."""
    return round(((screen_deg - 270.0 + 180.0) % 360.0) - 180.0, 1)

controls = []   # {"name", "type", "x", "y", "w", "h"} - the things code wires up or draws over
labels = []     # the same for every printed label


extras = {}     # name -> the runtime fields (centres, pointer, lit rectangle ...), see RUNTIME_SPEC.md


def add(kind_list, name, typ, x, y, w, h):
    kind_list.append({"name": name, "type": typ, "x": int(x), "y": int(y), "w": int(w), "h": int(h)})


def label(img, name, x, y, s, colour=LABEL):
    cb.text(img, x, y, s, colour)
    add(labels, name, "label", x, y, cb.text_width(s), 5)


def label_c(img, name, cx, y, s, colour=LABEL):
    label(img, name, cx - cb.text_width(s) // 2, y, s, colour)


# ---- Pieces --------------------------------------------------------------------------------------------------------

def case(img):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], fill=EDGE)
    d.rectangle([1, 1, W - 2, H - 2], fill=CASE)
    # Bevel: light along the top and left, dark along the bottom and right, as on the block's front edge.
    d.line([1, 1, W - 2, 1], fill=BEVEL_HI)
    d.line([1, 2, W - 3, 2], fill=BEVEL_MID)
    d.line([1, 1, 1, H - 2], fill=BEVEL_HI)
    d.line([2, 2, 2, H - 3], fill=BEVEL_MID)
    d.line([1, H - 2, W - 2, H - 2], fill=SHADE)
    d.line([2, H - 3, W - 2, H - 3], fill=DEEP)
    d.line([W - 2, 1, W - 2, H - 2], fill=SHADE)
    d.line([W - 3, 2, W - 3, H - 3], fill=DEEP)
    # The faint horizontal streaks of the block texture's face.
    for y in range(6, H - 6, 5):
        off = (y * 7) % 23
        for x0 in range(8 + off, W - 12, 41):
            d.line([x0, y, x0 + 14, y], fill=CASE_HI)
            d.line([x0 + 18, y + 2, x0 + 24, y + 2], fill=CASE_HI2)
    # A shallow engraved line under the top row (the meter / display band), like the block's upper panel.
    d.line([6, 59, W - 7, 59], fill=SHADE)
    d.line([6, 60, W - 7, 60], fill=CASE_HI2)


def recess(d, x0, y0, x1, y1):
    """A window cut into the case: dark outline, shadow on the top and left, light lip on the bottom and right."""
    d.rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], fill=EDGE)
    d.line([x0 - 2, y1 + 2, x1 + 2, y1 + 2], fill=BEVEL_HI)
    d.line([x1 + 2, y0 - 2, x1 + 2, y1 + 2], fill=BEVEL_HI)


def meter(img, x, y, w, h, r=26):
    d = ImageDraw.Draw(img)
    recess(d, x, y, x + w - 1, y + h - 1)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=SHADE)
    # The backlit face (the block's 4 x 3 cream patch, enlarged): warm edge, a brighter band across the top.
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=CREAM_LO)
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=CREAM_MID)
    d.rectangle([x + 2, y + 1, x + w - 3, y + h - 10], fill=CREAM)
    d.line([x + 8, y + 1, x + w - 9, y + 1], fill=CREAM_HI)
    cx, cy = x + w / 2.0, y + h - 5
    span, start = 110.0, 215.0  # Degrees of swing, from the left end (screen angles, y down).

    def at(frac, rad):
        a = math.radians(start + span * frac)
        return rnd(cx + math.cos(a) * rad), rnd(cy + math.sin(a) * rad)

    red_from = 0.62  # S9 sits here; the dB-over-S9 part of the scale is red.
    seen = set()
    for i in range(0, 241):  # A 1 px baseline, then ticks: S1, S3 ... S9, +20, +40, +60 long, the rest short.
        v = i / 240.0
        p = at(v, r)
        if p not in seen:
            seen.add(p)
            d.point(p, fill=RED if v > red_from else INK)
    for k in range(10):
        v = red_from * k / 9.0
        length = 3 if k % 2 == 1 else 2
        for rr in range(r + 1, r + 1 + length):
            d.point(at(v, rr), fill=INK)
    for k in range(1, 7):
        v = red_from + (1 - red_from) * k / 6.0
        for rr in range(r + 1, r + 1 + (3 if k % 2 == 0 else 2)):
            d.point(at(v, rr), fill=RED)
    # A red band under the over-S9 part, as on real S meters.
    for i in range(0, 81):
        v = red_from + (1 - red_from) * i / 80.0
        d.point(at(v, r - 1), fill=RED)
    for k, s in ((1, "1"), (3, "3"), (5, "5"), (7, "7"), (9, "9")):
        px, py = at(red_from * k / 9.0, r + 8)
        cb.text(img, px - 1, py - 2, s, INK)
    for k, s in ((2, "20"), (6, "60")):
        px, py = at(red_from + (1 - red_from) * k / 6.0, r + 8)
        cb.text(img, px - cb.text_width(s) // 2, py - 2, s, RED)
    # The PO / SWR scale: a thinner inner arc in warm grey, ticks only.
    for i in range(0, 121):
        d.point(at(i / 120.0, r - 8), fill=WARM)
    for k in range(0, 6):
        d.point(at(k / 5.0, r - 9), fill=WARM)
        d.point(at(k / 5.0, r - 10), fill=WARM)
    cb.text(img, x + 3, y + h - 7, "S", INK)
    cb.text(img, x + w - 3 - cb.text_width("PO SWR"), y + h - 7, "PO SWR", WARM)
    cb.disc(d, cx, cy + 1, 3, g(0x3C))  # The needle's pivot cap (the needle is drawn in code).
    d.point((rnd(cx) - 1, rnd(cy)), fill=MID)
    add(controls, "meter", "meter", x, y, w, h)
    extras["meter"] = {"pivot": [cx, cy], "radius": r - 1, "zero_deg": spec_deg(start),
                       "full_deg": spec_deg(start + span), "s9_fraction": red_from, "needle_colour": "#202020"}


def display(img, x, y, w, h):
    """The frequency window: a grey glass frame like the block's grey LCD, the glass itself dark and empty."""
    d = ImageDraw.Draw(img)
    recess(d, x - 2, y - 2, x + w + 1, y + h + 1)
    d.rectangle([x - 2, y - 2, x + w + 1, y + h + 1], fill=LCD_GREY)
    d.line([x - 2, y - 2, x + w + 1, y - 2], fill=g(0xAB))
    d.line([x - 2, y + h + 1, x + w + 1, y + h + 1], fill=MID)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=EDGE)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=SCREEN)
    d.line([x, y, x + w - 1, y], fill=g(0x0E))  # Shadow along the glass's top edge.
    add(controls, "display", "display", x, y, w, h)
    extras["display"] = {"ink": "#FFB060"}


def key(img, name, x, y, w, h, face=AMBER, face_lo=AMBER_LO, face_hi=AMBER_HI, lit=False):
    """A push key. lit=True: a selectable key whose face the code lights. On the runtime panel amber keys are unlit."""
    d = ImageDraw.Draw(img)
    if RUNTIME and face == AMBER:  # Every amber key unlit, so the band keys match the mode keys.
        face, face_lo, face_hi = KEY_UNLIT
    if lit:
        extras[name] = {"lit": [x + 1, y + 1, w - 2, h - 2, LIT]}
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=EDGE)
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=face)
    d.line([x + 1, y + 1, x + w - 2, y + 1], fill=face_hi)
    d.line([x + 1, y + h - 2, x + w - 2, y + h - 2], fill=face_lo)
    d.line([x + w - 2, y + 1, x + w - 2, y + h - 2], fill=face_lo)
    d.line([x + 1, y + h, x + w - 1, y + h], fill=SHADE)  # Its shadow on the case.
    add(controls, name, "button", x, y, w, h + 1)


def arrow(img, cx, cy, up, colour):
    d = ImageDraw.Draw(img)
    for i in range(3):
        yy = cy - 1 + i if up else cy + 1 - i
        d.line([cx - i, yy, cx + i, yy], fill=colour)


def knob(img, name, cx, cy, r, ringed=False, bar=False, rest=0.0, detents=None):
    """A grey knob like the block's: dark skirt, mid grey cap, lit from the top left, white pointer at rest."""
    d = ImageDraw.Draw(img)
    outer = r + (3 if ringed else 0)
    if ringed:  # The light grey ring the block's lower right knob sits in.
        cb.disc(d, cx, cy, r + 3, EDGE)
        cb.disc(d, cx, cy, r + 2, RING_DARK)
        cb.disc(d, cx - 0.5, cy - 0.5, r + 1, RING)
    cb.disc(d, cx + 1, cy + 1, r, EDGE)  # Drop shadow.
    cb.disc(d, cx, cy, r, EDGE)
    cb.disc(d, cx, cy, r - 1, DEEP)
    for k in range(20):  # Knurled skirt.
        a = 2 * math.pi * k / 20
        d.point((rnd(cx - 0.5 + math.cos(a) * (r - 1.5)), rnd(cy - 0.5 + math.sin(a) * (r - 1.5))), fill=SHADE)
    cb.disc(d, cx, cy, r - 3, KNOB)
    cb.disc(d, cx - 1, cy - 1, r - 4, KNOB_HI)
    cb.disc(d, cx - 2, cy - 2, max(2, r // 3), MID)
    a = cb.knob_angle(rest)
    add(controls, name, "knob3" if detents else "knob", cx - outer, cy - outer, outer * 2 + 1, outer * 2 + 1)
    extras[name] = {"cx": cx - 0.5, "cy": cy - 0.5, "pointer_from": int(r * 0.3), "pointer_r": r - 2,
                    "pointer_width": 2 if bar else 1, "pointer_colour": hexc(RING),
                    "min_deg": spec_deg(math.degrees(cb.knob_angle(0.0))),
                    "max_deg": spec_deg(math.degrees(cb.knob_angle(1.0)))}
    if detents:
        extras[name]["positions"] = [p for p, _ in detents]
        extras[name]["angles_deg"] = [ang for _, ang in detents]
    if RUNTIME:
        return
    if bar:  # The block's upper right knob is a bar handle across the cap.
        bx, by = math.cos(a), math.sin(a)
        for t in range(-(r - 2), r - 1):
            for o in (-1, 0, 1):
                px, py = cx - 0.5 + bx * t - by * o, cy - 0.5 + by * t + bx * o
                d.point((rnd(px), rnd(py)), fill=MID if o < 0 else (EDGE if o > 0 else BEVEL_HI))
    for i in range(int(r * 0.3), r - 1):
        d.point((rnd(cx - 0.5 + math.cos(a) * i), rnd(cy - 0.5 + math.sin(a) * i)), fill=RING)


def dial(img, cx, cy, r):
    """The big tuning dial: a printed tick ring round it, a heavy knurled skirt and a finger dimple at the lower left."""
    d = ImageDraw.Draw(img)
    for i in range(48):
        a = 2 * math.pi * i / 48
        length = 3 if i % 4 == 0 else 1
        for k in range(r + 2, r + 2 + length):
            d.point((rnd(cx - 0.5 + math.cos(a) * k), rnd(cy - 0.5 + math.sin(a) * k)), fill=MID if i % 4 else RING_DARK)
    cb.disc(d, cx + 2, cy + 2, r, SHADE)  # Shadow on the case.
    cb.disc(d, cx, cy, r, EDGE)
    cb.disc(d, cx, cy, r - 1, DEEP)
    for k in range(72):  # The knurled rubber grip.
        a = 2 * math.pi * k / 72
        for rr in (r - 2, r - 3):
            d.point((rnd(cx - 0.5 + math.cos(a) * rr), rnd(cy - 0.5 + math.sin(a) * rr)), fill=EDGE if k % 2 else g(0x3C))
    cb.disc(d, cx, cy, r - 5, SHADE)
    cb.disc(d, cx, cy, r - 6, CASE_HI)
    cb.disc(d, cx - 1, cy - 1, r - 8, KNOB)
    cb.disc(d, cx - 3, cy - 3, r - 16, BEVEL_HI)  # The dish catching the light.
    cb.disc(d, cx - 5, cy - 5, r - 22, KNOB_HI)
    # The finger dimple (the block's little spinner at the dial's lower left).
    fx, fy = cx - r * 0.45, cy + r * 0.45
    add(controls, "tuning", "dial", cx - r - 5, cy - r - 5, 2 * r + 10, 2 * r + 10)
    extras["tuning"] = {"cx": cx - 0.5, "cy": cy - 0.5, "mark_r": round(math.hypot(fx - cx, fy - cy), 1),
                        "mark_size": 7, "mark_colour": hexc(SHADE), "mark_shadow": hexc(EDGE)}
    if RUNTIME:
        return
    cb.disc(d, fx, fy, 6, EDGE)
    cb.disc(d, fx, fy, 5, SHADE)
    cb.disc(d, fx + 1, fy + 1, 3, KNOB)


LAMP_COLOURS = {"on_lamp": "#FFB020", "rx_lamp": "#3BFF6A", "ant_lamp": "#3BFF6A", "tx_lamp": "#FF3020"}


def lamp(img, name, x, y):
    d = ImageDraw.Draw(img)
    extras[name] = {"colour": LAMP_COLOURS[name]}
    d.rectangle([x, y, x + 6, y + 6], fill=EDGE)
    d.rectangle([x + 1, y + 1, x + 5, y + 5], fill=g(0x30))
    d.point((x + 2, y + 2), fill=CASE_HI)
    add(controls, name, "lamp", x, y, 7, 7)


def ptt(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=EDGE)
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=g(0x3A))
    d.line([x + 1, y + 1, x + w - 2, y + 1], fill=KNOB)
    for xx in range(x + 4, x + w - 3, 3):  # Rubber ridges.
        d.line([xx, y + 3, xx, y + h - 4], fill=SHADE)
    d.line([x + 1, y + h - 2, x + w - 2, y + h - 2], fill=SHADE)
    d.line([x + 1, y + h, x + w - 1, y + h], fill=SHADE)
    add(controls, "ptt", "button", x, y, w, h + 1)


# ---- The panel -----------------------------------------------------------------------------------------------------

def build(runtime=False):
    global RUNTIME
    RUNTIME = runtime
    controls.clear()
    labels.clear()
    extras.clear()
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    case(img)

    # Top row: meter, display, power.
    meter(img, 11, 9, 72, 46)
    display(img, 95, 13, 100, 26)
    label_c(img, "brand", 145, 46, "RC-1 ALL BAND TRANSCEIVER")

    key(img, "power", 206, 11, 18, 11, face=KNOB, face_lo=SHADE, face_hi=KNOB_HI)
    label_c(img, "power_label", 215, 26, "POWER")
    lamp(img, "on_lamp", 236, 13)
    label_c(img, "on_lamp_label", 240, 26, "ON")

    # The AGC knob with the bar handle, at rest on SLOW (straight up).
    agc_x, agc_y = 225, 70
    knob(img, "agc", agc_x, agc_y, 10, bar=True, rest=0.5, detents=(("OFF", -100), ("SLOW", 0), ("FAST", 100)))
    label_c(img, "agc_slow", agc_x, agc_y - 18, "SLOW")
    label(img, "agc_off", agc_x - 13 - cb.text_width("OFF"), agc_y + 3, "OFF")
    label(img, "agc_fast", agc_x + 13, agc_y + 3, "FAST")
    label_c(img, "agc_label", agc_x, agc_y + 13, "AGC")

    # Middle left: the two amber rows, the short one above as on the block.
    label(img, "meter_sel_label", 12, 66, "METER")
    # The meter reads S on receive; on the air these pick PO or SWR.
    for i, (s, x) in enumerate((("PO", 36), ("SWR", 56))):
        label_c(img, "meter_" + s.lower() + "_label", x + 6, 64, s)
        key(img, "meter_" + s.lower(), x, 71, 13, 7, face=KNOB, face_lo=SHADE, face_hi=KNOB_HI, lit=True)
    label_c(img, "band_label", 104, 64, "BAND")
    key(img, "band_down", 89, 71, 13, 7)
    key(img, "band_up", 106, 71, 13, 7)
    arrow(img, 95, 74, False, EDGE)
    arrow(img, 112, 74, True, EDGE)
    for i, s in enumerate(("SSB", "CW", "FM", "AM")):
        x = 12 + i * 27
        label_c(img, "mode_" + s.lower() + "_label", x + 11, 85, s)
        key(img, "mode_" + s.lower(), x, 92, 23, 7, lit=True)
    lamp(img, "rx_lamp", 13, 104)
    label(img, "rx_lamp_label", 22, 105, "RX")
    lamp(img, "ant_lamp", 40, 104)
    label(img, "ant_lamp_label", 49, 105, "ANT")
    lamp(img, "tx_lamp", 71, 104)
    label(img, "tx_lamp_label", 80, 105, "TX")

    # Bottom left: PTT bar, MIC GAIN, SQL (the block's three small knobs).
    label_c(img, "ptt_label", 27, 121, "PTT")
    ptt(img, 12, 128, 31, 18)
    label_c(img, "mic_label", 64, 121, "MIC")
    knob(img, "mic_gain", 64, 138, 9, rest=0.5)
    label_c(img, "sql_label", 94, 121, "SQL")
    knob(img, "squelch", 94, 138, 9, rest=0.0)

    # The main dial, bottom middle, with the 10x coarse-tuning key in the corner at its lower right.
    dial(img, 157, 114, 29)
    # Above the dial: MEMORY and VFO/MEM (lit in MEM mode). QUICK (lit on the quick-recall frequency) over the 10X key.
    label_c(img, "quick_label", 202, 113, "QUICK")
    key(img, "quick", 193, 120, 15, 7, face=KNOB, face_lo=SHADE, face_hi=KNOB_HI, lit=True)
    for name, text, cx, w in (("memory", "MEMORY", 141, 17), ("vfo_mem", "VFO/MEM", 172, 19)):
        label_c(img, name + "_label", cx, 63, text)
        key(img, name, cx - w // 2, 70, w, 7, face=KNOB, face_lo=SHADE, face_hi=KNOB_HI, lit=name != "memory")
    label_c(img, "coarse_label", 200, 131, "10X")
    key(img, "coarse", 193, 138, 15, 7, lit=True)

    # Bottom right: AF VOL in its light ring.
    label_c(img, "vol_label", 226, 112, "VOL")  # Not "AF VOL": QUICK sits just left of it.
    knob(img, "af_gain", 225, 134, 11, ringed=True, rest=0.3)
    return img


def check(min_gap=2):
    rects = controls + labels
    for i, a in enumerate(rects):
        assert a["x"] >= 3 and a["y"] >= 3 and a["x"] + a["w"] <= W - 3 and a["y"] + a["h"] <= H - 3, ("off the panel", a)
        for b in rects[i + 1:]:
            apart = (a["x"] + a["w"] + min_gap <= b["x"] or b["x"] + b["w"] + min_gap <= a["x"]
                     or a["y"] + a["h"] + min_gap <= b["y"] or b["y"] + b["h"] + min_gap <= a["y"])
            assert apart, "overlap (or under %d px apart): %s / %s" % (min_gap, a, b)


def runtime_layout():
    out = []
    for c in controls:
        entry = dict(c)
        entry.update(extras.get(c["name"], {}))
        out.append(entry)
    return {"texture": "radiocraft:textures/gui/hf_panels/hf_radio_all_band.png", "width": W, "height": H,
            "controls": out}


# ---- Preview: the runtime panel plus its moving parts, drawn by RUNTIME_SPEC.md's rules ---------------------------

def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def spec_point(cx, cy, deg, dist):
    a = math.radians(deg)
    return cx + math.sin(a) * dist, cy - math.cos(a) * dist


def preview(panel, layout, state):
    img = panel.copy()
    d = ImageDraw.Draw(img)
    for c in layout["controls"]:
        n, t = c["name"], c["type"]
        if t in ("knob", "knob3"):
            if t == "knob3":
                deg = c["angles_deg"][c["positions"].index(state[n])]
            else:
                deg = c["min_deg"] + (c["max_deg"] - c["min_deg"]) * state[n]
            for k in range(c["pointer_from"], c["pointer_r"] + 1):
                px, py = spec_point(c["cx"], c["cy"], deg, k)
                d.rectangle([rnd(px), rnd(py), rnd(px) + c["pointer_width"] - 1, rnd(py) + c["pointer_width"] - 1],
                            fill=rgb(c["pointer_colour"]))
        elif t == "dial":
            # The dimple spins with the dial; state is its angle in spec degrees (the painted one sat at 225).
            mx, my = spec_point(c["cx"], c["cy"], state[n], c["mark_r"])
            s = c["mark_size"]
            x0, y0 = rnd(mx - s / 2.0), rnd(my - s / 2.0)
            d.rectangle([x0 + 1, y0 + 1, x0 + s, y0 + s], fill=rgb(c["mark_shadow"]))
            d.rectangle([x0, y0, x0 + s - 1, y0 + s - 1], fill=rgb(c["mark_colour"]))
        elif t == "button" and c.get("lit") and state.get(n):
            x, y, w, h, col = c["lit"]
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=rgb(col))
        elif t == "lamp" and state.get(n):
            d.rectangle([c["x"] + 1, c["y"] + 1, c["x"] + c["w"] - 2, c["y"] + c["h"] - 2], fill=rgb(c["colour"]))
            d.point((c["x"] + 3, c["y"] + 3), fill=tuple(min(255, v + 90) for v in rgb(c["colour"])[:3]) + (255,))
        elif t == "display" and state.get(n):
            # As the code prints it: mode marker top left, TX top right (in the top 9 px), the frequency lower.
            marker, tx, freq = state[n]
            ink = rgb(c["ink"])
            cb.text(img, c["x"] + 2, c["y"] + 2, marker, ink)
            cb.text(img, c["x"] + c["w"] - 2 - cb.text_width(tx), c["y"] + 2, tx, ink)
            cb.text(img, c["x"] + (c["w"] - cb.text_width(freq)) // 2, c["y"] + 13, freq, ink)
        elif t == "meter":
            px, py = c["pivot"]
            deg = c["zero_deg"] + (c["full_deg"] - c["zero_deg"]) * state[n]
            d.line([(px, py), spec_point(px, py, deg, c["radius"])], fill=rgb(c["needle_colour"]))
    return img


PREVIEW_STATES = [
    {"af_gain": 0.0, "mic_gain": 0.0, "squelch": 0.0, "agc": "OFF", "tuning": 225, "mode_ssb": True,
     "meter": 0.0},
    {"af_gain": 0.5, "mic_gain": 0.5, "squelch": 0.3, "agc": "SLOW", "tuning": 0, "mode_cw": True, "meter_po": True,
     "on_lamp": True, "rx_lamp": True, "ant_lamp": True, "display": ("CW", "", "14.074.00"), "coarse": True, "vfo_mem": True, "meter": 0.62},
    {"af_gain": 1.0, "mic_gain": 1.0, "squelch": 1.0, "agc": "FAST", "tuning": 90, "mode_am": True, "meter_swr": True,
     "on_lamp": True, "tx_lamp": True, "ant_lamp": True, "display": ("AM", "TX", "28.400.00"), "quick": True, "meter": 1.0},
]


def main():
    args = sys.argv[1:]
    preview_dir = None
    if "--preview" in args:
        i = args.index("--preview")
        preview_dir = args[i + 1]
        del args[i:i + 2]
    img = build()
    check()
    img.save(OUT_PNG)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump({"texture": "radiocraft:textures/gui/hf_radio_all_band_proposed.png", "width": W, "height": H,
                   "controls": controls, "labels": labels}, f, indent=2)
        f.write("\n")
    if args:
        img.resize((W * 3, H * 3), Image.NEAREST).save(args[0])
    print("ok: %d controls, %d labels" % (len(controls), len(labels)))

    panel = build(runtime=True)
    check()
    layout = runtime_layout()
    os.makedirs(os.path.dirname(RUNTIME_PNG), exist_ok=True)
    os.makedirs(os.path.dirname(RUNTIME_JSON), exist_ok=True)
    panel.save(RUNTIME_PNG)
    with open(RUNTIME_JSON, "w", encoding="utf-8") as f:
        json.dump(layout, f, indent=2)
        f.write("\n")
    print("runtime panel and layout written")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        for k, state in enumerate(PREVIEW_STATES):
            preview(panel, layout, state).resize((W * 3, H * 3), Image.NEAREST).save(
                os.path.join(preview_dir, "hf_radio_all_band_runtime_%d.png" % k))


if __name__ == "__main__":
    main()
