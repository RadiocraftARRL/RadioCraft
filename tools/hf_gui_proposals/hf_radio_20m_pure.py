"""The Premium 20m radio, "the Swan" (2026-11-11), after a Swan-350 SSB transceiver: a dark slate panel with a chrome
trim line, the cream meter top left over a red and silver SWAN nameplate, the slide-rule dial window top centre with
the big silver-capped tuning knob under it, silver-capped knobs on the right, and the knob strip along the bottom
(AF GAIN, the mode knob where REC TUNE-CW was, RF GAIN, the MIC jack, BAND, MIC GAIN). Added, as the maintainer asked:
a frequency screen top right with a row of modern keys under it (UP, DN, QUICK, 10X, RIT, NB).

What each photo control does here:
  meter (P.A. CATHODE)    S on receive; click it for PO or SWR on the air (the screen's "meter" control)
  P.A. PLATE              AGC, OFF / SLOW / FAST
  P.A. LOAD               SQL, the squelch
  FINE                    the RIT (receiver clarifier), centre = 0; the RIT key puts it in, CLR (DIAL SET) zeroes it
  AF GAIN, RF GAIN        af_gain and rf_gain
  REC TUNE-CW             MODE, SSB / CW / AM
  MIC GAIN                mic_gain
  CAR. BAL.               the POWER toggle
  PTT / VOX slide         a big PTT key
  BAND                    decor: a single-band rig, it stays on 14.0
Writes, under src/main/resources/assets/radiocraft/:
  textures/gui/hf_panels/hf_radio_20m_pure.png, gui_layouts/hf_hf_radio_20m_pure.json   (RUNTIME_SPEC.md)
  textures/block/hf_radio_20m_pure.png, models/block/hf_radio_20m_pure.json             the block, with its DISPLAY
Run: python tools/hf_gui_proposals/hf_radio_20m_pure.py [--preview DIR]
"""
import math
import os
import sys

from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
import premium_rig as rig
from hf_10m_20m_kit import rgb, shade

RADIO = "hf_radio_20m_pure"
W, H = 256, 160

OUTLINE = rgb(14, 14, 15)
CASE = rgb(34, 36, 38)
PANEL, PANEL_LIGHT, PANEL_DARK = rgb(56, 60, 64), rgb(72, 76, 80), rgb(40, 43, 46)
STRIP = rgb(36, 38, 41)
CHROME, CHROME_DARK = rgb(184, 186, 190), rgb(110, 112, 116)
LABEL, LABEL_DIM = rgb(222, 222, 220), rgb(150, 152, 154)
CREAM, CREAM_INK = rgb(232, 226, 204), rgb(40, 36, 30)
NEEDLE_RED = rgb(170, 30, 24)
DIAL_GREEN = rgb(40, 150, 120)
KEY = rgb(64, 66, 70)
GLASS = rgb(16, 30, 22)
NAME_RED = rgb(196, 30, 28)

STRIP_TOP = 102
KNOB_SKIRT = rgb(20, 20, 20)


def dial_window(img, x, y, w, h):
    """The slide-rule window: cream face, a green band, tick marks and the 14 MHz numbers, a red hairline."""
    d = ImageDraw.Draw(img)
    d.rectangle([x - 3, y - 3, x + w + 2, y + h + 2], fill=CHROME_DARK)
    d.rectangle([x - 2, y - 2, x + w + 1, y + h + 1], fill=CHROME)
    d.rectangle([x - 1, y - 1, x + w, y + h], fill=OUTLINE)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=CREAM)
    cx, cy, r = x + w / 2.0, y + h + 46, 58

    def put(a, k, colour):
        px, py = round(cx - 0.5 + math.cos(a) * k), round(cy - 0.5 + math.sin(a) * k)
        if x <= px < x + w and y <= py < y + h:
            d.point((px, py), fill=colour)

    for i in range(0, 241):
        a = math.radians(237 + 66 * i / 240)
        for k in range(r - 14, r - 9):
            put(a, k, DIAL_GREEN)
        put(a, r, CREAM_INK)
    # The ticks, the numbers and the red hairline are the game's (the "slide_rule" control, 2026-11-11): the scale
    # turns with the dial frequency, so the numbers read true.
    d.line([x + 1, y + 1, x + w - 2, y + 1], fill=shade(CREAM, 14))
    return {"x": x, "y": y, "w": w, "h": h, "cx": cx, "cy": cy, "r": r, "deg_per_khz": round(66 * 0.22 / 100, 4),
            "ink": "#%02X%02X%02X" % CREAM_INK[:3], "hairline": "#%02X%02X%02X" % NEEDLE_RED[:3]}


def nameplate(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=CHROME_DARK)
    d.rectangle([x, y, x + w - 2, y + h - 2], fill=CHROME)
    d.line([x + 1, y + 1, x + w - 3, y + 1], fill=shade(CHROME, 30))
    tw = 4 * 4 * 2 - 2  # SWAN at 2x
    tx = x + (w - tw) // 2
    rig.big_text(img, tx + 1, y + (h - 10) // 2 + 1, "SWAN", shade(NAME_RED, -90))
    rig.big_text(img, tx, y + (h - 10) // 2, "SWAN", NAME_RED)


def draw(moving):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lay = kit.Layout(RADIO, W, H)

    # ---- The cabinet, the panel, the chrome line and the darker strip ----
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, H - 2], fill=CASE)
    d.rectangle([4, 4, W - 5, H - 5], fill=PANEL)
    d.line([4, 4, W - 5, 4], fill=PANEL_LIGHT)
    d.line([4, 4, 4, H - 5], fill=PANEL_LIGHT)
    d.rectangle([5, STRIP_TOP, W - 6, H - 6], fill=STRIP)
    d.line([5, STRIP_TOP - 2, W - 6, STRIP_TOP - 2], fill=CHROME)
    d.line([5, STRIP_TOP - 1, W - 6, STRIP_TOP - 1], fill=CHROME_DARK)
    for sx, sy in ((81, 66), (W - 8, 6), (8, 6)):
        kit.screw(img, sx, sy, rgb(120, 122, 126), rgb(50, 50, 52))

    # ---- Top left: the meter (click it: PO or SWR on the air), the nameplate, PTT and CLR ----
    needle = kit.meter(img, 10, 11, 70, 38, CREAM, CREAM_INK, NEEDLE_RED, PANEL_DARK, PANEL_LIGHT)
    lay.control("meter", "meter", 10, 11, 70, 38, rt=needle["rt"])
    nameplate(img, 10, 56, 66, 13)
    lay.control("nameplate", "decor", 10, 56, 66, 13)
    kit.key(img, 10, 78, 44, 16, rgb(196, 196, 190))
    kit.text_centred(img, 32, 83, "PTT", rgb(40, 40, 40))
    lay.control("ptt", "button", 10, 78, 44, 16, rt={})
    lay.label(img, "CLR", 67, 75, LABEL, centred=True)
    kit.key(img, 59, 82, 16, 9, KEY)
    lay.control("rit_clear", "button", 59, 82, 16, 9, rt={})

    # ---- Top centre: the dial window and the big tuning knob ----
    rule = dial_window(img, 88, 12, 66, 26)
    lay.control("dial_window", "decor", 85, 9, 72, 32, rt=rule, rt_type="slide_rule")
    rt = kit.big_dial(img, 121, 72, 44, 0.5, KNOB_SKIRT, rgb(10, 10, 10), rgb(60, 60, 62),
                      rgb(176, 178, 182), rgb(214, 216, 220), rgb(40, 40, 40), moving)
    rt["mark_colour"], rt["mark_shadow"] = "#8E9094", "#3A3C40"
    lay.control("tuning", "dial", 99, 50, 44, 44, rt=rt)

    # ---- Top right: the screen, the key row under it, AGC, SQL and FINE (RIT) ----
    kit.display_window(img, 166, 12, 80, 28, GLASS, OUTLINE, PANEL_LIGHT)
    lay.control("display", "display", 166, 12, 80, 28, rt={"ink": "#7CFFA0"})
    keys = [("UP", "step_up", False), ("DN", "step_down", False), ("QCK", "quick", True), ("10X", "coarse", True),
            ("RIT", "rit_on", True), ("NB", "nb_on", True)]
    for i, (s, name, lit) in enumerate(keys):
        kx = 166 + i * 14
        lay.label(img, s, kx + 6, 46, LABEL, centred=True)
        kit.key(img, kx, 53, 12, 8, KEY)
        lay.control(name, "button", kx, 53, 12, 8, rt={"lit": kit.key_lit(kx, 53, 12, 8, "#7CFFA0")} if lit else {})

    for s, cx in (("AGC", 180), ("SQL", 210), ("FINE", 240)):
        lay.label(img, s, cx, 66, LABEL, centred=True)
    rt = rig.silver_knob(img, 180, 84, 18, KNOB_SKIRT)
    agc_angles = [-60, 0, 60]
    rig.detent_dots(img, 180, 84, 11, agc_angles, LABEL_DIM)
    lay.control("agc", "knob3", 169, 73, 22, 22, rt=rig.knob3_rt(rt, ["OFF", "SLOW", "FAST"], agc_angles))
    kit.knob_ticks(img, 210, 84, 11, LABEL_DIM)
    rt = rig.silver_knob(img, 210, 84, 18, KNOB_SKIRT)
    lay.control("squelch", "knob", 199, 73, 22, 22, rt=rt)
    kit.knob_ticks(img, 240, 84, 11, LABEL_DIM)
    ImageDraw.Draw(img).point((240, 72), fill=NEEDLE_RED)  # The RIT's centre mark.
    rt = rig.silver_knob(img, 240, 84, 18, KNOB_SKIRT)
    lay.control("rit", "knob", 229, 73, 22, 22, rt=rt)

    # ---- The bottom strip ----
    ky = 132
    lay.label(img, "AF GAIN", 22, 106, LABEL, centred=True)
    kit.knob_ticks(img, 22, ky, 13, LABEL_DIM)
    rt = rig.silver_knob(img, 22, ky, 22, KNOB_SKIRT)
    lay.control("af_gain", "knob", 10, ky - 12, 24, 24, rt=rt)

    lay.label(img, "MODE", 62, 106, LABEL, centred=True)
    mode_angles = [-50, 0, 50]
    rig.detent_dots(img, 62, ky, 14, mode_angles, LABEL_DIM)
    rt = rig.silver_knob(img, 62, ky, 22, KNOB_SKIRT)
    lay.control("mode_lever", "knob3", 50, ky - 12, 24, 24, rt=rig.knob3_rt(rt, ["SSB", "CW", "AM"], mode_angles))
    lay.label(img, "SSB", 37, ky - 10, LABEL_DIM)
    lay.label(img, "CW", 62, ky - 19, LABEL_DIM, centred=True)
    lay.label(img, "AM", 76, ky - 10, LABEL_DIM)

    lay.label(img, "RF GAIN", 98, 106, LABEL, centred=True)
    kit.knob_ticks(img, 98, ky, 13, LABEL_DIM)
    rt = rig.silver_knob(img, 98, ky, 22, KNOB_SKIRT)
    lay.control("rf_gain", "knob", 86, ky - 12, 24, 24, rt=rt)

    lay.label(img, "MIC", 134, 106, LABEL, centred=True)  # The mic jack.
    kit.disc(d, 134, ky, 6, rgb(150, 152, 156))
    kit.disc(d, 134, ky, 4, rgb(96, 98, 100))
    kit.disc(d, 134, ky, 2, rgb(10, 10, 10))
    lay.control("mic_jack", "decor", 127, ky - 7, 15, 15)

    lay.label(img, "BAND", 168, 106, LABEL, centred=True)  # Decor: a single-band rig, on 14.0.
    lay.label(img, "14", 183, 117, LABEL_DIM)
    rig.silver_knob(img, 168, ky, 22, KNOB_SKIRT, pointer=0.62)
    lay.control("band", "decor", 156, ky - 12, 24, 24)

    lay.label(img, "MIC GAIN", 204, 106, LABEL, centred=True)
    kit.knob_ticks(img, 204, ky, 13, LABEL_DIM)
    rt = rig.silver_knob(img, 204, ky, 22, KNOB_SKIRT)
    lay.control("mic_gain", "knob", 192, ky - 12, 24, 24, rt=rt)

    lay.label(img, "PWR", 241, 106, LABEL, centred=True)
    th = kit.toggle(img, 238, 116, 2, 1, CHROME, CHROME_DARK, OUTLINE, rgb(214, 214, 214), pitch=9, moving=moving)
    lay.label(img, "ON", 228, 118, LABEL_DIM)
    lay.label(img, "OFF", 224, 127, LABEL_DIM)
    lay.control("power", "switch", 238, 116, 8, th, rt={"positions": ["ON", "OFF"], "handles": kit.toggle_handles(238, 116, 2, pitch=9),
                                                       "handle_colour": "#D6D6D6", "handle_shadow": "#4A4A4A"})
    lay.label(img, "SSB TRANSCEIVER", 134, 149, LABEL_DIM, centred=True)
    return img, lay


# ---- The block: a simplified Swan, 16 x 7 x 13 ----------------------------------------------------------------------

BH = 7  # model units tall; the front picture is 64 x 28


def block():
    f = Image.new("RGBA", (64, BH * 4), PANEL)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 63, 27], outline=CASE)
    d.rectangle([1, 17, 62, 26], fill=STRIP)
    d.line([1, 16, 62, 16], fill=CHROME)
    # Meter and nameplate, top left.
    d.rectangle([3, 2, 16, 9], fill=PANEL_DARK)
    d.rectangle([4, 3, 15, 8], fill=CREAM)
    d.line([6, 7, 13, 4], fill=NEEDLE_RED)
    d.rectangle([3, 11, 16, 14], fill=CHROME)
    d.line([6, 12, 13, 12], fill=NAME_RED)
    d.line([6, 13, 13, 13], fill=NAME_RED)
    # Dial window, top centre.
    d.rectangle([22, 2, 38, 8], fill=CHROME)
    d.rectangle([23, 3, 37, 7], fill=CREAM)
    d.line([24, 6, 36, 6], fill=DIAL_GREEN)
    d.line([30, 3, 30, 7], fill=NEEDLE_RED)
    # The screen, top right, and the key row under it (TX lamp at the row's right end).
    d.rectangle([42, 2, 61, 8], fill=OUTLINE)
    d.rectangle([43, 3, 60, 7], fill=GLASS)
    for i in range(5):
        d.rectangle([43 + i * 3, 10, 44 + i * 3, 11], fill=KEY)
    d.rectangle([58, 10, 59, 11], fill=rgb(90, 16, 12))  # TX lamp, dark (the renderer lights it).
    return f


def knob_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=KNOB_SKIRT)
    d.rectangle([x + 2, y + 2, x + 5, y + 5], fill=rgb(176, 178, 182))
    d.point((x + 2, y + 2), fill=rgb(220, 222, 226))


def side_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=rgb(26, 26, 26))
    d.line([x, y, x + 7, y], fill=rgb(60, 60, 60))


def big_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=KNOB_SKIRT)
    d.rectangle([x + 1, y + 1, x + 6, y + 6], fill=rgb(176, 178, 182))
    d.rectangle([x + 2, y + 2, x + 3, y + 3], fill=rgb(214, 216, 220))
    d.point((x + 5, y + 5), fill=rgb(120, 122, 126))


def build_block():
    r = rig.Rig(RADIO, BH, block(), CASE, rgb(60, 62, 66), rgb(20, 20, 22))
    r.body()
    knob, side, big = r.tile("knob", knob_tile), r.tile("side", side_tile), r.tile("big", big_tile)
    r.display((43, 3, 61, 8))

    def knob_part(name, x0, y0, size, depth, front=knob):
        r.part(name, (x0, y0, x0 + size, y0 + size), depth, side, front)

    knob_part("tuning", 25, 10, 10, 1.5, big)            # The big tuning knob, under the dial window.
    for i, x in enumerate((44, 51, 58)):                    # AGC, SQL, FINE.
        knob_part("knob_top_%d" % i, x - 1, 13, 4, 0.75)
    for i, x in enumerate((3, 11, 19, 40, 49)):             # AF, MODE, RF, BAND, MIC GAIN along the strip.
        knob_part("knob_strip_%d" % i, x, 19, 5, 1.0)
    r.part("mic_jack", (32, 20, 35, 23), 0.25, side, side)
    r.part("power", (58, 20, 60, 24), 0.75, r.tile("chrome", lambda d, x, y: d.rectangle([x, y, x + 7, y + 7], fill=CHROME)))
    r.write("Made with tools/hf_gui_proposals/hf_radio_20m_pure.py (after a Swan-350)")
    return r


def main():
    img, lay = draw(False)
    kit.write_runtime(img, lay, RADIO)
    r = build_block()
    lamp = r.lamp((58, 10, 60, 12), True)
    print("TX lamp (x1, x2, y1, y2, front):", lamp)
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        for s in range(3):
            rig.preview(RADIO, s, os.path.join(out, RADIO + "_%d.png" % s))
        r.preview(os.path.join(out, RADIO + "_block.png"))


if __name__ == "__main__":
    main()
