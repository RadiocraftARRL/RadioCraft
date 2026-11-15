"""The Premium 40m radio, the TS-520R (2026-11-11), after a 1970s hybrid SSB transceiver: a grey panel in a chrome
frame, its upper half sunk in a darker inset. Top left the amber meter with the METER selector beside it and the row
of rockers under them; top centre the lamps (here TX, ON and RIT) over the sub-dial window, which is the frequency
screen; top right two rows of three silver-capped knobs. Lower half: the MIC connector and PHONES jack, the MODE
knob, the big tuning dial with its skirt ring, the TS-520R plate under it, BAND and FUNCTION, the RIT ON/OFF toggle,
and the POWER rocker. No maker's name.

What each photo control does here:
  METER knob              PO or SWR on the air (S on receive)
  SEND / H.SW / VOX / NB / OFF-FAST-SLOW rockers   PTT (hold), 10X tuning, QUICK, the noise blanker, AGC
  FIX / VFO / RIT lamps   TX, ON (pilot) and RIT
  LOAD / PLATE / DRIVE    SQL, MIC GAIN, RF GAIN
  RIT / PROCESSOR / AF-RF RIT (centre = 0), PROC (decor), AF GAIN
  MODE                    SSB / CW / AM
  RIT ON/OFF toggle       the RIT in or out; CLR zeroes it
  BAND, FUNCTION          decor (a single-band rig)
Writes, under src/main/resources/assets/radiocraft/:
  textures/gui/hf_panels/hf_radio_40m_pure.png, gui_layouts/hf_hf_radio_40m_pure.json   (RUNTIME_SPEC.md)
  textures/block/hf_radio_40m_pure.png, models/block/hf_radio_40m_pure.json             the block, with its DISPLAY
Run: python tools/hf_gui_proposals/hf_radio_40m_pure.py [--preview DIR]
"""
import math
import os
import sys

from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
import premium_rig as rig
from hf_10m_20m_kit import rgb, shade

RADIO = "hf_radio_40m_pure"
W, H = 256, 160

OUTLINE = rgb(12, 12, 13)
CHROME, CHROME_DARK, CHROME_LIGHT = rgb(186, 188, 192), rgb(112, 114, 118), rgb(226, 228, 232)
PANEL, PANEL_LIGHT, PANEL_DARK = rgb(84, 88, 92), rgb(104, 108, 112), rgb(60, 63, 67)
INSET = rgb(62, 66, 70)
LABEL, LABEL_DIM = rgb(226, 226, 224), rgb(160, 162, 164)
AMBER, AMBER_INK = rgb(236, 190, 120), rgb(52, 36, 20)
RED = rgb(180, 36, 26)
GLASS = rgb(30, 22, 14)
SKIRT = rgb(18, 18, 18)
ROCKER = rgb(24, 24, 26)

INSET_BOTTOM = 84
LOWER_TOP = 88


def rocker_key(img, lay, name, label, x, y, w, h, lit):
    lay.label(img, label, x + w // 2, y - 8, LABEL, centred=True)
    rig.rocker(img, x, y, w, h, ROCKER)
    d = ImageDraw.Draw(img)
    d.rectangle([x + 1, y + 1, x + w - 2, y + h // 2 - 1], fill=rgb(40, 40, 42))  # The rocker's top half, raised.
    d.line([x + 1, y + 1, x + w - 2, y + 1], fill=rgb(80, 80, 82))
    rt = {"lit": [x + 3, y + 3, w - 6, 2, "#FFB050"]} if lit else {}
    lay.control(name, "button", x, y, w, h, rt=rt)


def rocker_switch(img, lay, name, label, x, y, w, h, positions):
    """A rocker the game draws as a switch: the lit block shows the position (top, middle or bottom)."""
    lay.label(img, label, x + w // 2, y - 8, LABEL, centred=True)
    rig.rocker(img, x, y, w, h, ROCKER)
    n = len(positions)
    step = (h - 2) // n
    handles = [[x + 2, y + 1 + i * step, w - 4, step] for i in range(n)]
    lay.control(name, "switch", x, y, w, h, rt={"positions": positions, "handles": handles,
                                               "handle_colour": "#C8C8C8", "handle_shadow": "#505050"})


def draw(moving):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lay = kit.Layout(RADIO, W, H)

    # ---- Chrome frame, panel, the darker upper inset ----
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, H - 2], fill=CHROME_DARK)
    d.rectangle([1, 1, W - 3, H - 3], fill=CHROME)
    d.line([1, 1, W - 3, 1], fill=CHROME_LIGHT)
    d.rectangle([3, 3, W - 4, H - 4], fill=PANEL)
    kit.recess(d, [5, 5, W - 6, INSET_BOTTOM], INSET, PANEL_DARK, PANEL_LIGHT)

    # ---- Upper left: meter, METER knob, the rocker row ----
    needle = kit.meter(img, 10, 12, 58, 32, AMBER, AMBER_INK, RED, PANEL_DARK, PANEL_LIGHT)
    lay.control("meter", "meter", 10, 12, 58, 32, rt=needle["rt"])
    lay.label(img, "METER", 86, 8, LABEL, centred=True)
    angles = [-120, 120]
    rig.detent_dots(img, 86, 26, 12, angles, LABEL_DIM)
    rt = rig.silver_knob(img, 86, 26, 18, SKIRT)
    lay.control("meter_select", "knob3", 75, 15, 22, 22, rt=rig.knob3_rt(rt, ["PO", "SWR"], angles))
    lay.label(img, "PO", 74, 40, LABEL_DIM)
    lay.label(img, "SWR", 89, 40, LABEL_DIM)

    kit.recess(d, [8, 50, 102, 81], rgb(46, 49, 52), PANEL_DARK, PANEL_LIGHT)
    rocker_key(img, lay, "ptt", "SEND", 12, 62, 14, 17, False)
    rocker_key(img, lay, "coarse", "10X", 30, 62, 14, 17, True)
    rocker_key(img, lay, "quick", "QCK", 48, 62, 14, 17, True)
    rocker_switch(img, lay, "nb", "NB", 66, 62, 14, 17, ["ON", "OFF"])
    rocker_switch(img, lay, "agc", "AGC", 84, 62, 14, 17, ["FAST", "SLOW", "OFF"])

    # ---- Upper centre: the lamps and the screen ----
    for s, name, cx, colour in (("TX", "tx_lamp", 122, "#FF3020"), ("ON", "pilot_lamp", 138, "#FFA030"),
                                ("RIT", "rit_lamp", 154, "#FFA030")):
        lay.label(img, s, cx, 8, LABEL, centred=True)
        kit.led(img, cx - 2, 15, rgb(70, 30, 20))
        lay.control(name, "lamp", cx - 2, 15, 4, 4, rt={"colour": colour})
    kit.display_window(img, 111, 25, 54, 22, GLASS, OUTLINE, PANEL_LIGHT)  # Clear of the METER knob and the rockers.
    lay.control("display", "display", 111, 25, 54, 22, rt={"ink": "#FFB050"})

    # ---- Upper right: two rows of three knobs ----
    for row, (cy, names) in enumerate(((25, (("SQL", "squelch"), ("MIC", "mic_gain"), ("RF", "rf_gain"))),
                                       (63, (("RIT", "rit"), ("PROC", None), ("AF", "af_gain"))))):
        for i, (s, name) in enumerate(names):
            cx = 183 + i * 25  # The right column's dots end at 244, inside the panel.
            lay.label(img, s, cx, cy - 18, LABEL, centred=True)
            kit.knob_ticks(img, cx, cy, 11, LABEL_DIM)
            if name is None:  # PROCESSOR: decor, its pointer painted at 3 o'clock.
                rig.silver_knob(img, cx, cy, 18, SKIRT, pointer=0.3)
                lay.control("proc", "decor", cx - 11, cy - 11, 22, 22)
                continue
            if name == "rit":
                ImageDraw.Draw(img).point((cx, cy - 12), fill=RED)
            rt = rig.silver_knob(img, cx, cy, 18, SKIRT)
            lay.control(name, "knob", cx - 11, cy - 11, 22, 22, rt=rt)

    # ---- Lower half: MIC, PHONES, MODE, the dial and plate, BAND, FUNCTION, RIT toggle, CLR, POWER ----
    lay.label(img, "MIC", 21, 92, LABEL, centred=True)
    kit.disc(d, 21, 108, 8, CHROME_DARK)
    kit.disc(d, 21, 108, 7, CHROME)
    kit.disc(d, 21, 108, 4, rgb(30, 30, 30))
    for a in range(0, 360, 90):
        d.point((round(21 + math.cos(math.radians(a + 45)) * 2), round(108 + math.sin(math.radians(a + 45)) * 2)), fill=CHROME)
    lay.control("mic_connector", "decor", 13, 100, 17, 17)
    lay.label(img, "PHONES", 18, 143, LABEL_DIM, centred=True)
    kit.disc(d, 18, 134, 4, CHROME)
    kit.disc(d, 18, 134, 2, rgb(10, 10, 10))
    lay.control("phones", "decor", 13, 129, 11, 11)

    mode_angles = [-50, 0, 50]
    rig.detent_dots(img, 56, 112, 13, mode_angles, LABEL_DIM)
    rt = rig.silver_knob(img, 56, 112, 20, SKIRT)
    lay.control("mode_lever", "knob3", 45, 101, 22, 22, rt=rig.knob3_rt(rt, ["SSB", "CW", "AM"], mode_angles))
    lay.label(img, "SSB", 32, 102, LABEL_DIM)
    lay.label(img, "CW", 56, 94, LABEL_DIM, centred=True)
    lay.label(img, "AM", 69, 102, LABEL_DIM)
    lay.label(img, "MODE", 56, 126, LABEL, centred=True)

    kit.dial_ring(img, 128, 114, 29, LABEL_DIM, every=9)
    rt = kit.big_dial(img, 128, 114, 50, 0.5, rgb(150, 152, 156), rgb(90, 92, 96), rgb(214, 216, 220),
                      rgb(26, 26, 28), rgb(60, 60, 62), rgb(214, 214, 214), moving)
    rt["mark_colour"], rt["mark_shadow"] = "#9A9CA0", "#0E0E0E"
    lay.control("tuning", "dial", 103, 89, 50, 50, rt=rt)
    d.rectangle([106, 146, 150, 154], fill=OUTLINE)
    d.rectangle([107, 147, 149, 153], fill=rgb(40, 42, 44))
    kit.text_centred(img, 128, 148, "TS-520R", CHROME_LIGHT)
    lay.control("plate", "decor", 106, 146, 45, 9)

    lay.label(img, "BAND", 174, 116, LABEL, centred=True)
    rig.silver_knob(img, 174, 103, 18, SKIRT, pointer=0.4)
    kit.text(img, 162, 93, "7", LABEL_DIM)
    lay.control("band", "decor", 163, 92, 22, 22)
    lay.label(img, "FUNC", 204, 116, LABEL, centred=True)
    rig.silver_knob(img, 204, 103, 18, SKIRT, pointer=0.5)
    lay.control("function", "decor", 193, 92, 22, 22)

    lay.label(img, "RIT", 172, 124, LABEL, centred=True)
    th = kit.toggle(img, 168, 131, 2, 0, CHROME, CHROME_DARK, OUTLINE, CHROME_LIGHT, pitch=8, moving=moving)
    lay.label(img, "ON", 178, 133, LABEL_DIM)
    lay.label(img, "OFF", 178, 141, LABEL_DIM)
    lay.control("rit_switch", "switch", 168, 131, 8, th, rt={"positions": ["ON", "OFF"], "handles": kit.toggle_handles(168, 131, 2, pitch=8),
                                                           "handle_colour": "#E0E0E4", "handle_shadow": "#4A4A4A"})
    lay.label(img, "CLR", 204, 126, LABEL, centred=True)
    kit.key(img, 196, 134, 16, 9, rgb(40, 40, 42))
    lay.control("rit_clear", "button", 196, 134, 16, 9, rt={})

    lay.label(img, "PWR", 237, 92, LABEL, centred=True)
    rig.rocker(img, 230, 100, 14, 22, ROCKER)
    lay.control("power", "switch", 230, 100, 14, 22, rt={"positions": ["ON", "OFF"],
                                                       "handles": [[232, 102, 10, 9], [232, 111, 10, 9]],
                                                       "handle_colour": "#E8E8E8", "handle_shadow": "#606060"})
    lay.label(img, "OFF", 237, 125, LABEL_DIM, centred=True)
    return img, lay


# ---- The block: a simplified TS-520R, 16 x 7 x 13 -------------------------------------------------------------------

BH = 7


def block():
    f = Image.new("RGBA", (64, BH * 4), PANEL)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 63, 27], outline=CHROME)
    d.rectangle([1, 1, 62, 13], fill=INSET)
    # Meter, top left, and the rocker row under it.
    d.rectangle([2, 2, 14, 7], fill=PANEL_DARK)
    d.rectangle([3, 3, 13, 6], fill=AMBER)
    d.line([5, 6, 10, 3], fill=AMBER_INK)
    for i in range(5):
        d.rectangle([3 + i * 4, 10, 5 + i * 4, 12], fill=ROCKER)
    # Lamps over the screen: TX, ON, RIT (dark; the renderer lights TX and RX).
    for x, c in ((27, rgb(90, 16, 12)), (31, rgb(16, 70, 24)), (35, rgb(90, 60, 16))):
        d.rectangle([x, 2, x + 1, 3], fill=c)
    d.rectangle([25, 5, 40, 10], fill=OUTLINE)
    d.rectangle([26, 6, 39, 9], fill=GLASS)
    # The dial's ring, the plate.
    d.rectangle([28, 25, 36, 26], fill=rgb(40, 42, 44))
    d.point((29, 25), fill=CHROME_LIGHT)
    d.point((33, 25), fill=CHROME_LIGHT)
    return f


def knob_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=SKIRT)
    d.rectangle([x + 2, y + 2, x + 5, y + 5], fill=rgb(180, 182, 186))
    d.point((x + 2, y + 2), fill=CHROME_LIGHT)


def side_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=rgb(24, 24, 24))
    d.line([x, y, x + 7, y], fill=rgb(64, 64, 64))


def dial_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=CHROME)
    d.rectangle([x + 1, y + 1, x + 6, y + 6], fill=rgb(30, 30, 32))
    d.rectangle([x + 2, y + 2, x + 3, y + 3], fill=rgb(70, 70, 72))


def chrome_tile(d, x, y):
    d.rectangle([x, y, x + 7, y + 7], fill=CHROME)
    d.line([x, y, x + 7, y], fill=CHROME_LIGHT)


def build_block():
    r = rig.Rig(RADIO, BH, block(), rgb(70, 74, 78), rgb(96, 100, 104), rgb(40, 42, 46))
    r.body()
    knob, side, dial, chrome = r.tile("knob", knob_tile), r.tile("side", side_tile), r.tile("dial", dial_tile), r.tile("chrome", chrome_tile)
    r.display((26, 6, 40, 10))

    def knob_part(name, x0, y0, size, depth, front=knob, sides=side):
        r.part(name, (x0, y0, x0 + size, y0 + size), depth, sides, front)

    knob_part("tuning", 27, 14, 10, 1.5, dial, chrome)  # The big dial, its chrome skirt.
    knob_part("meter_knob", 17, 3, 3, 0.75)
    for row, y in enumerate((2, 8)):                    # The two rows of three, top right.
        for i, x in enumerate((44, 51, 58)):
            knob_part("knob_%d_%d" % (row, i), x - 1, y, 4, 0.75)
    knob_part("mode", 11, 17, 4, 0.75)
    knob_part("band", 42, 16, 4, 0.75)
    knob_part("function", 49, 16, 4, 0.75)
    r.part("mic", (3, 16, 7, 20), 0.5, side, chrome)
    r.part("power", (57, 17, 60, 22), 0.5, side, side)
    r.write("Made with tools/hf_gui_proposals/hf_radio_40m_pure.py (after a TS-520S)")
    return r


def main():
    img, lay = draw(False)
    kit.write_runtime(img, lay, RADIO)
    r = build_block()
    print("TX lamp:", r.lamp((27, 2, 29, 4), True))
    print("RX lamp:", r.lamp((31, 2, 33, 4), False))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        for s in range(3):
            rig.preview(RADIO, s, os.path.join(out, RADIO + "_%d.png" % s), "7.150 MHz")
        r.preview(os.path.join(out, RADIO + "_block.png"))


if __name__ == "__main__":
    main()
