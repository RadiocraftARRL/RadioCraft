"""The Premium 10m radio, the RTX-100 (2026-11-11), after a late-1980s 10 metre SSB/CW mobile the maintainer picked: a
low dark blue-grey rig. Top row: three small slide slots in a black box (TX/RX lamps, NB, and a third slide), the model
plate with the RF POWER / SIGNAL LED bar, the green LCD, then the RIT and RF GAIN knobs. Bottom row: the 10 METER
SSB/CW TRANSCEIVER lettering, the microphone socket and phones jack, a block of grey keys (MODE, 500k, MEMORY over
STEP and STORE), UP and DOWN keys, the tuning knob, and the SQUELCH and VOLUME knobs. No brand names.

What each part does here (HfPanelScreen wires them by name):
  slots:  TX and RX lamps; NB slide (noise blanker in or OUT); RIT slide (F.LOCK on the original): the RIT in or OUT
  bar:    the S meter on receive, PO or SWR on the air (a click on it switches which)
  knobs:  RIT (centre is zero), RF GAIN, SQUELCH, VOLUME; MIC gain is a small knob under the microphone socket
  keys:   MODE steps SSB / CW / AM (the LCD shows which); 100K is 10x tuning (500k on the original); MEMORY is QUICK
          (hold to store, press to recall); AGC steps OFF / SLOW / FAST, a green dot under it shows which (STEP on the
          original); RIT 0 zeroes the RIT (STORE); PTT; UP / DOWN step 10 kHz; POWER
Writes, under src/main/resources/assets/radiocraft/:
  textures/gui/hf_panels/hf_radio_10m_pure.png   the panel without its moving parts (RUNTIME_SPEC.md)
  gui_layouts/hf_hf_radio_10m_pure.json          its layout; "meter" is a "bar" (segments lit from the left)
  textures/block/hf_radio_10m_pure.png           64x64: the block's front (4 px a model unit), case and knob tiles
  models/block/hf_radio_10m_pure.json            the block, front north, with its DISPLAY element over the LCD
Run: python tools/hf_gui_proposals/hf_radio_10m_pure.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
from hf_10m_20m_kit import rgb

RADIO = "hf_radio_10m_pure"
W, H = 256, 126

OUTLINE = rgb(18, 20, 24)
CASE, CASE_LIGHT, CASE_DARK = rgb(52, 56, 64), rgb(70, 74, 84), rgb(36, 38, 44)
FACE, FACE_LIGHT, FACE_DARK = rgb(62, 68, 80), rgb(82, 88, 102), rgb(44, 48, 58)
BOX = rgb(22, 24, 28)
KEY = rgb(96, 102, 114)
WHITE, DIM = rgb(222, 224, 228), rgb(160, 166, 178)
LCD = rgb(70, 190, 96)
LCD_DARK = rgb(50, 150, 72)
SLIDE = rgb(30, 30, 30)
LED_OFF = rgb(70, 30, 28)

DIAL = (172, 84, 44)


def key(img, x, y, w, h):
    kit.key(img, x, y, w, h, KEY, OUTLINE)


def slot(img, d, cx, top):
    """A slide slot, 6 x 14, its two stops' handle rects."""
    d.rectangle([cx - 3, top, cx + 2, top + 13], fill=rgb(8, 8, 8))
    d.rectangle([cx - 2, top + 1, cx + 1, top + 12], fill=rgb(40, 40, 44))
    return [[cx - 2, top + 1, 5, 6], [cx - 2, top + 7, 5, 6]]


def draw():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lay = kit.Layout(RADIO, W, H)

    # ---- The case ----
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, 5], fill=CASE)
    d.line([1, 1, W - 2, 1], fill=CASE_LIGHT)
    d.line([1, 6, W - 2, 6], fill=CASE_DARK)
    d.rectangle([1, 7, W - 2, H - 2], fill=FACE)
    d.line([1, 7, W - 2, 7], fill=FACE_LIGHT)
    d.line([1, 7, 1, H - 2], fill=FACE_LIGHT)
    d.line([W - 2, 7, W - 2, H - 2], fill=FACE_DARK)
    d.line([1, H - 2, W - 2, H - 2], fill=FACE_DARK)
    d.line([2, 46, 130, 46], fill=FACE_DARK)  # the step between the upper and lower panel
    d.line([2, 47, 130, 47], fill=FACE_LIGHT)

    # ---- Top left: the black box of slots ----
    d.rectangle([4, 9, 58, 42], fill=BOX)
    # TX / RX lamps in the first slot's place.
    lay.label(img, "TX", 14, 11, WHITE, centred=True)
    kit.led(img, 12, 18, LED_OFF)
    lay.control("tx_lamp", "lamp", 12, 18, 4, 4, rt={"colour": "#FF3020"})
    kit.led(img, 12, 26, rgb(30, 60, 30))
    lay.control("rx_lamp", "lamp", 12, 26, 4, 4, rt={"colour": "#40FF50"})
    lay.label(img, "RX", 14, 34, WHITE, centred=True)
    for cx, top_label, name in ((31, "NB", "nb"), (48, "RIT", "rit_switch")):
        lay.label(img, top_label, cx, 11, WHITE, centred=True)
        handles = slot(img, d, cx, 18)
        lay.control(name, "switch", cx - 3, 18, 6, 14, rt={"positions": ["ON", "OFF"], "handles": handles,
                    "handle_colour": kit.hexc(rgb(200, 202, 206)), "handle_shadow": kit.hexc(rgb(90, 92, 98))})
        lay.label(img, "OUT", cx, 34, WHITE, centred=True)

    # ---- The model plate and the LED bar ----
    lay.label(img, "MODEL RTX-100", 66, 10, WHITE)
    lay.label(img, "RF POWER", 66, 18, DIM)
    segs, colours = [], []
    for k in range(5):
        sx = 66 + k * 12
        d.rectangle([sx - 1, 25, sx + 10, 29], fill=rgb(10, 10, 12))
        d.rectangle([sx, 26, sx + 9, 28], fill=LED_OFF if k == 4 else rgb(70, 36, 30))
        segs.append([sx, 26, 10, 3])
        colours.append("#FF5040" if k < 4 else "#FFB030")
    lay.control("meter", "bar", 65, 25, 59, 5, rt={"segments": segs, "colours": colours})
    for s, k in (("3", 0), ("5", 1), ("7", 2), ("9", 3), ("OVER", 4)):
        lay.label(img, s, 66 + k * 12 + 5, 32, WHITE, centred=True)
    lay.label(img, "SIGNAL", 66, 39, DIM)

    # ---- The LCD ----
    lx, ly, lw, lh = 133, 12, 57, 26  # Its frame ends at 192, clear of the RIT knob's dots (from 197).
    d.rectangle([lx - 3, ly - 3, lx + lw + 2, ly + lh + 2], fill=BOX)
    d.rectangle([lx, ly, lx + lw - 1, ly + lh - 1], fill=LCD)
    d.line([lx, ly + lh - 1, lx + lw - 1, ly + lh - 1], fill=LCD_DARK)
    lay.control("display", "display", lx, ly, lw, lh, rt={"ink": "#0C2410"})

    # ---- Knobs on the right: RIT, RF GAIN over SQUELCH, VOLUME ----
    for label, name, cx, cy, frac, ly_ in (("RIT", "rit", 210, 30, 0.5, 11), ("RF GAIN", "rf_gain", 238, 30, 1.0, 11),
                                           ("SQUELCH", "squelch", 210, 80, 0.0, 61), ("VOLUME", "af_gain", 238, 80, 0.5, 61)):
        lay.label(img, label, cx, ly_, WHITE, centred=True)
        kit.knob_ticks(img, cx, cy, 13, DIM)
        rt = kit.small_knob(img, cx, cy, 20, frac, rgb(30, 32, 38), moving=False)
        lay.control(name, "knob", cx - 11, cy - 11, 22, 22, rt=rt)

    # ---- Bottom left: lettering, microphone, phones, mic gain, power ----
    lay.label(img, "10 METER SSB/CW TRANSCEIVER", 6, 50, WHITE)
    lay.label(img, "MICROPHONE", 6, 58, DIM)
    kit.disc(d, 17, 76, 9, rgb(16, 16, 18))
    kit.disc(d, 17, 76, 8, rgb(170, 172, 176))
    kit.disc(d, 17, 76, 6, rgb(50, 50, 54))
    for a in range(0, 360, 45):
        d.point((round(17 + 3 * math.cos(math.radians(a))), round(76 + 3 * math.sin(math.radians(a)))), fill=rgb(210, 210, 210))
    lay.control("decor_mic", "jack", 8, 67, 19, 19)
    kit.disc(d, 38, 76, 4, rgb(16, 16, 18))
    kit.disc(d, 38, 76, 2, rgb(4, 4, 4))
    lay.control("decor_phones", "jack", 34, 72, 9, 9)
    lay.label(img, "MIC", 17, 93, WHITE, centred=True)
    kit.knob_ticks(img, 17, 109, 10, DIM)
    rt = kit.small_knob(img, 17, 109, 16, 0.5, rgb(30, 32, 38), moving=False)
    lay.control("mic_gain", "knob", 8, 100, 18, 18, rt=rt)
    lay.label(img, "POWER", 44, 93, WHITE, centred=True)
    key(img, 36, 101, 16, 10)
    lay.control("power", "button", 36, 101, 16, 10, rt={})

    # ---- The key block ----
    cols = (70, 91, 113)
    rows = (64, 88)
    kw, kh = 18, 10
    keys = [(0, 0, "MODE", "mode_lever", "mode"), (0, 1, "100K", "coarse", "lit"), (0, 2, "MEMORY", "quick", "lit"),
            (1, 0, "AGC", "agc", "agc"), (1, 1, "RIT 0", "rit_clear", "plain"), (1, 2, "PTT", "ptt", "plain")]
    for row, colm, label, name, kind in keys:
        cx, y = cols[colm], rows[row]
        x = cx - kw // 2
        lay.label(img, label, cx, y - 7, WHITE, centred=True)
        key(img, x, y, kw, kh)
        if kind == "lit":
            lay.control(name, "button", x, y, kw, kh, rt={"lit": kit.key_lit(x, y, kw, kh, "#60F070")})
        elif kind == "plain":
            lay.control(name, "button", x, y, kw, kh, rt={})
        elif kind == "mode":  # A knob3 with no pointer to see: a click steps SSB, CW, AM (the LCD shows which).
            px, py = x + 3, y + 4
            lay.control(name, "button", x, y, kw, kh, rt={"cx": px + 0.5, "cy": py + 0.5, "pointer_from": 0, "pointer_r": 0,
                        "pointer_width": 1, "pointer_colour": kit.hexc(KEY), "positions": ["SSB", "CW", "AM"],
                        "angles_deg": [0, 0, 0]}, rt_type="knob3")
        elif kind == "agc":  # Three dots under the key; the game lights the one AGC is on.
            acx, acy, r = cx, y + kh + 7, 5
            angles = [-60, 0, 60]
            for a in angles:
                ax = math.floor(acx + r * math.sin(math.radians(a)))
                ay = math.floor(acy - r * math.cos(math.radians(a)))
                d.rectangle([ax, ay, ax + 1, ay + 1], fill=rgb(24, 30, 26))
            lay.control(name, "button", x, y, kw, kh + 7, rt={"cx": acx, "cy": acy, "pointer_from": r, "pointer_r": r,
                        "pointer_width": 2, "pointer_colour": "#60F070", "positions": ["OFF", "SLOW", "FAST"],
                        "angles_deg": angles}, rt_type="knob3")
    # UP / DOWN.
    for label, name, y in (("UP", "step_up", 64), ("DOWN", "step_down", 88)):
        lay.label(img, label, 137, y - 7, WHITE, centred=True)
        key(img, 128, y, 18, 10)
        d.polygon([(134, y + 6 if name == "step_up" else y + 3), (140, y + 6 if name == "step_up" else y + 3),
                   (137, y + 3 if name == "step_up" else y + 6)], fill=rgb(200, 202, 206))
        lay.control(name, "button", 128, y, 18, 10, rt={})

    # ---- The tuning knob ----
    dcx, dcy, ds = DIAL
    rt = kit.big_dial(img, dcx, dcy, ds, 0.5, rgb(26, 28, 32), rgb(18, 18, 20), rgb(64, 66, 72),
                      rgb(38, 40, 44), rgb(64, 66, 72), WHITE, moving=False)
    lay.control("tuning", "dial", dcx - ds // 2, dcy - ds // 2, ds, ds, rt=rt)
    return img, lay


# ======================================================================================================================
# The block: a low mobile rig, the front art 52 x 18 px (13 x 4.5 units) at the top left of a 64x64 texture
# ======================================================================================================================
BX0, BX1, BY1, BZ = 1.5, 14.5, 4.5, 3.0
FRONT = (0, 0, 52, 18)
CASE_TILE = (0, 32, 32, 64)
KNOB_TILE = (56, 0, 60, 4)
DIAL_TILE = (56, 4, 60, 8)
LCD_PX = (30, 2, 41, 6)            # inclusive
KNOBS_PX = [(44, 2), (48, 2), (44, 10), (48, 10)]
DIAL_PX = (34, 9, 41, 16)          # x0, y0, x1, y1 (exclusive end)
TX_LED, RX_LED = (3, 3), (3, 5)


def block_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 51, 17], fill=FACE)
    d.line([0, 0, 51, 0], fill=FACE_LIGHT)
    d.line([0, 17, 51, 17], fill=FACE_DARK)
    d.line([0, 8, 42, 8], fill=FACE_DARK)
    d.rectangle([1, 1, 12, 7], fill=BOX)                              # the slot box
    d.point(TX_LED, fill=LED_OFF)
    d.point(RX_LED, fill=rgb(30, 60, 30))
    for x in (7, 10):
        d.line([x, 2, x, 6], fill=rgb(60, 60, 64))
        d.point((x, 3), fill=rgb(200, 202, 206))
    d.line([14, 2, 26, 2], fill=DIM)                                  # the plate's lettering
    for k in range(5):
        d.line([14 + k * 3, 5, 15 + k * 3, 5], fill=rgb(70, 36, 30))
    d.rectangle([29, 1, 42, 7], fill=BOX)
    d.rectangle([LCD_PX[0], LCD_PX[1], LCD_PX[2], LCD_PX[3]], fill=LCD)
    d.line([1, 10, 11, 10], fill=WHITE)                               # 10 METER lettering
    d.rectangle([2, 12, 5, 15], fill=rgb(170, 172, 176))              # microphone socket
    d.rectangle([3, 13, 4, 14], fill=rgb(50, 50, 54))
    d.point((8, 13), fill=rgb(4, 4, 4))                               # phones
    for row in range(2):                                              # the key block
        for c in range(3):
            x, y = 14 + c * 4, 10 + row * 4
            d.rectangle([x, y, x + 2, y + 1], fill=KEY)
    d.rectangle([27, 10, 29, 11], fill=KEY)                           # UP / DOWN
    d.rectangle([27, 14, 29, 15], fill=KEY)
    for x, y in KNOBS_PX:
        d.rectangle([x, y, x + 2, y + 2], fill=FACE_DARK)
    d.rectangle([CASE_TILE[0], CASE_TILE[1], CASE_TILE[2] - 1, CASE_TILE[3] - 1], fill=CASE)
    d.line([CASE_TILE[0], CASE_TILE[1], CASE_TILE[2] - 1, CASE_TILE[1]], fill=CASE_LIGHT)
    for y in range(CASE_TILE[1] + 4, CASE_TILE[3] - 4, 3):            # heatsink ribs on top and sides
        d.line([CASE_TILE[0] + 2, y, CASE_TILE[2] - 3, y], fill=CASE_DARK)
    d.rectangle([KNOB_TILE[0], KNOB_TILE[1], KNOB_TILE[2] - 1, KNOB_TILE[3] - 1], fill=rgb(26, 26, 30))
    d.point((KNOB_TILE[0] + 1, KNOB_TILE[1] + 1), fill=rgb(90, 92, 98))
    d.rectangle([DIAL_TILE[0], DIAL_TILE[1], DIAL_TILE[2] - 1, DIAL_TILE[3] - 1], fill=rgb(20, 20, 22))
    d.point((DIAL_TILE[0] + 2, DIAL_TILE[1] + 1), fill=rgb(70, 70, 74))
    return img


def uv(box):
    return [box[0] / 4.0, box[1] / 4.0, box[2] / 4.0, box[3] / 4.0]


def mx(px):
    return BX1 - px / 4.0


def my(py):
    return BY1 - py / 4.0


def cube(name, px0, py0, px1, py1, z0, z1, tile):
    return {"name": name, "from": [mx(px1), my(py1), z0], "to": [mx(px0), my(py0), z1],
            "faces": {f: {"uv": uv(tile), "texture": "#tex"} for f in ("north", "east", "south", "west", "up", "down")}}


def block_model():
    case = uv(CASE_TILE)
    elements = [
        {"name": "body", "from": [BX0, 0, BZ], "to": [BX1, BY1, 16], "faces": {
            "north": {"uv": uv(FRONT), "texture": "#tex"}, "east": {"uv": case, "texture": "#tex"},
            "south": {"uv": case, "texture": "#tex"}, "west": {"uv": case, "texture": "#tex"},
            "up": {"uv": case, "texture": "#tex"}, "down": {"uv": case, "texture": "#tex"}}},
        {"name": "DISPLAY", "from": [mx(LCD_PX[2] + 1), my(LCD_PX[3] + 1), BZ - 0.01], "to": [mx(LCD_PX[0]), my(LCD_PX[1]), BZ - 0.01],
         "faces": {"north": {"uv": uv((LCD_PX[0], LCD_PX[1], LCD_PX[2] + 1, LCD_PX[3] + 1)), "texture": "#tex"}}},
        cube("tuning_knob", DIAL_PX[0], DIAL_PX[1], DIAL_PX[2], DIAL_PX[3], BZ - 1.0, BZ, DIAL_TILE),
    ]
    for i, (x, y) in enumerate(KNOBS_PX):
        elements.append(cube("knob_%d" % i, x, y, x + 3, y + 3, BZ - 0.75, BZ, KNOB_TILE))
    return {"credit": "Made with tools/hf_gui_proposals/hf_radio_10m_pure.py (the RTX-100, after a late-1980s 10m mobile)",
            "parent": "block/block", "texture_size": [64, 64],
            "textures": {"tex": "radiocraft:block/" + RADIO, "particle": "radiocraft:block/" + RADIO},
            "elements": elements}


def block_lamps():
    """TX and RX lamps for RadioDisplays.Lamp: (x1 west, x2 east, y1, y2, front z)."""
    return [(mx(x + 1), mx(x), my(y + 1), my(y), BZ) for x, y in (TX_LED, RX_LED)]


def main():
    img, lay = draw()
    kit.write_runtime(img, lay, RADIO)
    block_texture().save(os.path.join(kit.ASSETS, "textures", "block", RADIO + ".png"))
    with open(os.path.join(kit.ASSETS, "models", "block", RADIO + ".json"), "w", encoding="utf-8") as f:
        json.dump(block_model(), f, indent="\t")
        f.write("\n")
    print("wrote the", RADIO, "block model and texture; lamps (TX, RX):", block_lamps())


if __name__ == "__main__":
    main()
