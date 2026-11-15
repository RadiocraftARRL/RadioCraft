"""The Premium 80m radio, the RC-K2 (2026-11-11), after a 1990s kit-built QRP transceiver the maintainer picked: a
compact grey case with a black window across the top middle (a bar-graph S meter and a green LCD), two columns of small
grey keys on the left and a 3 x 3 block on the right (white legends above, yellow second legends below), four small
knobs left of the big tuning knob, a RATE/LOCK key, the RIT offset knob and the ON/OFF key bottom right, and the mic
connector and phones jack bottom left. No brand names: the window says RC-K2 TRANSCEIVER.

What each key does here (the panel keeps the photo's places; HfPanelScreen wires them by name):
  left:   UP / DOWN step 10 kHz (BAND+/BAND- on the original), QUICK (hold to store, press to recall: RCL/STORE),
          DISPLAY picks what the bar reads on the air (PO or SWR), ANT 1/2 (decor), NB the noise blanker
  window: the bar meter, the LCD (frequency), and NB / ANT / TX / RIT marks lit under the LCD
  knobs:  MIC and SQL (KEYER and POWER on the original), AF GAIN, RF GAIN; the tuning knob; RATE = 10x tuning
  right:  MODE steps SSB / CW / AM; A/B and A=B (decor); PRE/ATT and XFIL (decor); AGC steps OFF / SLOW / FAST, its
          green dot under the key shows which; RIT puts the RIT in; CLR zeroes it (XIT on the original); PTT (MSG)
  bottom: the RIT knob (-1 .. +1 kHz) and ON/OFF

Writes, under src/main/resources/assets/radiocraft/:
  textures/gui/hf_panels/hf_radio_80m_pure.png   the panel without its moving parts (RUNTIME_SPEC.md)
  gui_layouts/hf_hf_radio_80m_pure.json          its layout; "meter" is a "bar" (segments lit from the left)
  textures/block/hf_radio_80m_pure.png           64x64: the block's front (4 px a model unit), case and knob tiles
  models/block/hf_radio_80m_pure.json            the block, front north, with its DISPLAY element over the LCD
Run: python tools/hf_gui_proposals/hf_radio_80m_pure.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
from hf_10m_20m_kit import rgb

kit.cb.GLYPHS.setdefault("=", "000111000111000")  # for A=B

RADIO = "hf_radio_80m_pure"
W, H = 256, 140

OUTLINE = rgb(30, 30, 30)
CASE, CASE_LIGHT, CASE_DARK = rgb(124, 126, 124), rgb(146, 148, 146), rgb(96, 98, 96)
FACE, FACE_LIGHT, FACE_DARK = rgb(112, 114, 112), rgb(132, 134, 132), rgb(88, 90, 88)
KEY = rgb(154, 156, 152)
WINDOW = rgb(14, 14, 14)
LCD = rgb(150, 178, 84)
LCD_DARK = rgb(120, 146, 64)
WHITE, YELLOW = rgb(236, 236, 232), rgb(228, 198, 70)
WIN_INK = rgb(220, 200, 90)       # the window's printing
SEG_OFF = rgb(30, 44, 26)
LAMP_OFF = rgb(36, 40, 30)

# ---- Where things go (panel pixels) ----------------------------------------------------------------------------------
LEFT_COLS = (16, 44)               # key centres
KEY_ROWS = (20, 45, 70)            # key tops
KW, KH = 18, 9
RIGHT_COLS = (200, 221, 242)
RKW = 16
WIN = (62, 16, 190, 58)            # the black window
LCD_BOX = (108, 21, 78, 24)        # x, y, w, h: the display
BAR_X, BAR_Y, SEG_W, SEG_H, SEGS = 66, 30, 3, 6, 10
DIAL = (148, 96, 50)               # centre and size
KNOBS = [("MIC", "mic_gain", 76, 80, 0.5), ("SQL", "squelch", 106, 80, 0.0),
         ("AF GAIN", "af_gain", 76, 115, 0.5), ("RF GAIN", "rf_gain", 106, 115, 1.0)]


def key(img, x, y, w=KW, h=KH):
    kit.key(img, x, y, w, h, KEY, OUTLINE)


def draw():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lay = kit.Layout(RADIO, W, H)

    # ---- The case: grey lid lip, face, screws ----
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, 5], fill=CASE)
    d.line([1, 1, W - 2, 1], fill=CASE_LIGHT)
    d.line([1, 6, W - 2, 6], fill=CASE_DARK)
    d.rectangle([1, 7, W - 2, H - 2], fill=FACE)
    d.line([1, 7, W - 2, 7], fill=FACE_LIGHT)
    d.line([1, 7, 1, H - 2], fill=FACE_LIGHT)
    d.line([W - 2, 7, W - 2, H - 2], fill=FACE_DARK)
    d.line([1, H - 2, W - 2, H - 2], fill=FACE_DARK)
    for sx in (4, W - 5):
        kit.screw(img, sx, 10, CASE_LIGHT, FACE_DARK)
        kit.screw(img, sx, H - 6, CASE_LIGHT, FACE_DARK)

    # ---- Left: two columns of keys, white legend above, yellow below ----
    left = [  # (row, col, white, yellow, name or None, lit?)
        (0, 0, "UP", "RCL", "step_up", False), (1, 0, "DOWN", "STORE", "step_down", False), (2, 0, "QUICK", "HOLD", "quick", True),
        (0, 1, "DISPLAY", "PO/SWR", "meter", False), (1, 1, "ANT 1/2", "TUNE", None, False), (2, 1, "NB", "LEVEL", "nb_on", True)]
    for row, colm, white, yellow, name, lit in left:
        cx, y = LEFT_COLS[colm], KEY_ROWS[row]
        x = cx - KW // 2
        lay.label(img, white, cx, y - 7, WHITE, centred=True)
        key(img, x, y)
        lay.label(img, yellow, cx, y + KH + 2, YELLOW, centred=True)
        rt = None if name is None else ({"lit": kit.key_lit(x, y, KW, KH)} if lit else {})
        lay.control(name or "decor_" + white, "button", x, y, KW, KH, rt=rt)
    # Mic connector and phones jack, bottom left.
    kit.disc(d, 17, 112, 9, rgb(60, 60, 60))
    kit.disc(d, 17, 112, 8, rgb(178, 180, 178))
    kit.disc(d, 17, 112, 6, rgb(70, 70, 70))
    for a in range(0, 360, 60):
        d.point((round(17 + 3 * math.cos(math.radians(a))), round(112 + 3 * math.sin(math.radians(a)))), fill=rgb(200, 200, 200))
    lay.control("decor_mic", "jack", 8, 103, 19, 19)
    kit.disc(d, 40, 112, 4, rgb(40, 40, 40))
    kit.disc(d, 40, 112, 2, rgb(8, 8, 8))
    lay.control("decor_phones", "jack", 36, 108, 9, 9)
    lay.label(img, "MIC", 17, 125, WHITE, centred=True)
    lay.label(img, "PHONES", 40, 125, WHITE, centred=True)  # x 29..51: clear of MIC's 12..22

    # ---- The window: bar meter, LCD, the marks under it ----
    x0, y0, x1, y1 = WIN
    d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], fill=FACE_DARK)
    d.rectangle([x0, y0, x1, y1], fill=WINDOW)
    d.line([x0, y1 + 1, x1 + 1, y1 + 1], fill=FACE_LIGHT)
    lay.label(img, "RC-K2 TRANSCEIVER", (x0 + x1) // 2, 10, WHITE, centred=True)
    # The bar meter: ten segments; S1 5 9 +20 40 over it, RF 3 5 7 10 under it.
    segs = []
    colours = []
    for k in range(SEGS):
        sx = BAR_X + k * (SEG_W + 1)
        d.rectangle([sx, BAR_Y, sx + SEG_W - 1, BAR_Y + SEG_H - 1], fill=SEG_OFF)
        segs.append([sx, BAR_Y, SEG_W, SEG_H])
        colours.append("#60F048" if k < 6 else "#F0D040")
    for s, k in (("S1", 0), ("5", 3), ("9", 5), ("+20", 7)):
        lay.label(img, s, BAR_X + k * (SEG_W + 1), BAR_Y - 7, WIN_INK)
    lay.label(img, "PO", BAR_X, BAR_Y + SEG_H + 2, YELLOW)
    lay.label(img, "SWR", BAR_X + 4 * (SEG_W + 1), BAR_Y + SEG_H + 2, YELLOW)
    lay.control("meter", "bar", BAR_X, BAR_Y, SEGS * (SEG_W + 1) - 1, SEG_H,
                rt={"segments": segs, "colours": colours})
    # The LCD.
    lx, ly, lw, lh = LCD_BOX
    d.rectangle([lx - 2, ly - 2, lx + lw + 1, ly + lh + 1], fill=rgb(40, 40, 40))
    d.rectangle([lx, ly, lx + lw - 1, ly + lh - 1], fill=LCD)
    d.line([lx, ly + lh - 1, lx + lw - 1, ly + lh - 1], fill=LCD_DARK)
    lay.control("display", "display", lx, ly, lw, lh, rt={"ink": "#18260C"})
    # Marks under the LCD: a dark lens over each word, lit by the game.
    marks = [("NB", "nb_lamp", "#60F048"), ("ANT", "ant_lamp", "#60F048"), ("TX", "tx_lamp", "#FF4030"), ("RIT", "rit_lamp", "#60F048")]
    mx = lx + 2
    for word, name, colour in marks:
        wdt = kit.text_width(word)
        lay.label(img, word, mx, 52, WIN_INK)
        d.rectangle([mx, 48, mx + wdt - 1, 49], fill=LAMP_OFF)
        lay.control(name, "lamp", mx, 48, wdt, 2, rt={"colour": colour})
        mx += wdt + 6

    # ---- Knobs, the tuning knob, RATE ----
    for label, name, cx, cy, frac in KNOBS:
        lay.label(img, label, cx, cy - 19, WHITE, centred=True)
        kit.knob_ticks(img, cx, cy, 13, WHITE)
        rt = kit.small_knob(img, cx, cy, 20, frac, rgb(70, 72, 70), moving=False)
        lay.control(name, "knob", cx - 12, cy - 12, 24, 24, rt=rt)
    dcx, dcy, ds = DIAL
    rt = kit.big_dial(img, dcx, dcy, ds, 0.5, rgb(26, 26, 26), rgb(18, 18, 18), rgb(70, 70, 70),
                      rgb(40, 40, 40), rgb(70, 70, 70), WHITE, moving=False)
    lay.control("tuning", "dial", dcx - ds // 2, dcy - ds // 2, ds, ds, rt=rt)
    lay.label(img, "RATE", 184, 93, WHITE, centred=True)
    key(img, 178, 100, 12, 9)
    lay.control("coarse", "button", 178, 100, 12, 9, rt={"lit": kit.key_lit(178, 100, 12, 9)})
    lay.label(img, "LOCK", 184, 111, YELLOW, centred=True)

    # ---- Right: the 3 x 3 keys ----
    right = [  # (row, col, white, yellow, name, kind)
        (0, 0, "MODE", "VOX", "mode_lever", "mode"), (0, 1, "A/B", "REV", None, None), (0, 2, "A=B", "SPLT", None, None),
        (1, 0, "PRE", "SPOT", None, None), (1, 1, "AGC", None, "agc", "agc"), (1, 2, "XFIL", "AFIL", None, None),
        (2, 0, "RIT", "PF1", "rit_on", "lit"), (2, 1, "CLR", "PF2", "rit_clear", "plain"), (2, 2, "PTT", "MIC", "ptt", "plain")]
    for row, colm, white, yellow, name, kind in right:
        cx, y = RIGHT_COLS[colm], KEY_ROWS[row]
        x = cx - RKW // 2
        lay.label(img, white, cx, y - 7, WHITE, centred=True)
        key(img, x, y, RKW, KH)
        if yellow:
            lay.label(img, yellow, cx, y + KH + 2, YELLOW, centred=True)
        if kind is None:
            lay.control("decor_" + white, "button", x, y, RKW, KH)
        elif kind == "lit":
            lay.control(name, "button", x, y, RKW, KH, rt={"lit": kit.key_lit(x, y, RKW, KH)})
        elif kind == "plain":
            lay.control(name, "button", x, y, RKW, KH, rt={})
        elif kind == "mode":  # A knob3 with no pointer to see: a click steps SSB, CW, AM (the LCD shows which).
            px, py = x + 3, y + 4  # a pixel of the cap, in the cap's colour
            lay.control(name, "button", x, y, RKW, KH, rt={"cx": px + 0.5, "cy": py + 0.5, "pointer_from": 0, "pointer_r": 0,
                        "pointer_width": 1, "pointer_colour": kit.hexc(KEY), "positions": ["SSB", "CW", "AM"],
                        "angles_deg": [0, 0, 0]}, rt_type="knob3")
        elif kind == "agc":  # Three dots under the key on a small arc; the game lights the one AGC is on.
            acx, acy, r = cx, y + KH + 7, 5
            angles = [-60, 0, 60]
            for a in angles:
                px = math.floor(acx + r * math.sin(math.radians(a)))
                py = math.floor(acy - r * math.cos(math.radians(a)))
                d.rectangle([px, py, px + 1, py + 1], fill=rgb(40, 50, 36))
            lay.control(name, "button", x, y, RKW, KH + 7, rt={"cx": acx, "cy": acy, "pointer_from": r, "pointer_r": r,
                        "pointer_width": 2, "pointer_colour": "#60F048", "positions": ["OFF", "SLOW", "FAST"],
                        "angles_deg": angles}, rt_type="knob3")

    # ---- Bottom right: RIT knob, ON/OFF ----
    lay.label(img, "RIT", 214, 90, WHITE, centred=True)
    kit.knob_ticks(img, 214, 109, 12, WHITE, n=3)
    rt = kit.small_knob(img, 214, 109, 18, 0.5, rgb(70, 72, 70), moving=False)
    lay.control("rit", "knob", 203, 98, 22, 22, rt=rt)
    lay.label(img, "-1", 203, 122, WHITE)
    lay.label(img, "+1", 219, 122, WHITE)
    lay.label(img, "ON", 242, 95, WHITE, centred=True)
    key(img, 235, 102, 14, 11)
    lay.control("power", "button", 235, 102, 14, 11, rt={})
    lay.label(img, "OFF", 242, 115, WHITE, centred=True)
    return img, lay


# ======================================================================================================================
# The block: a compact grey box, the front art 56 x 28 px (14 x 7 units) at the top left of a 64x64 texture
# ======================================================================================================================
BX0, BX1, BY1, BZ = 1.0, 15.0, 7.0, 2.0      # body x span, top, front face z (the body runs back to z 15)
FRONT = (0, 0, 56, 28)
CASE_TILE = (0, 32, 32, 64)
KNOB_TILE = (56, 0, 60, 4)
DIAL_TILE = (56, 4, 60, 8)
LCD_PX = (24, 5, 40, 11)                       # x0, y0, x1, y1 in the front art
BAR_PX = (16, 6, 23, 9)
SMALL_KNOBS_PX = [(16, 15), (20, 15), (16, 20), (20, 20), (46, 21)]
DIAL_PX = (25, 14, 32, 21)


def block_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Front.
    d.rectangle([0, 0, 55, 27], fill=FACE)
    d.line([0, 0, 55, 0], fill=CASE_LIGHT)
    d.line([0, 27, 55, 27], fill=FACE_DARK)
    d.rectangle([14, 3, 41, 13], fill=WINDOW)                       # the window
    d.rectangle([BAR_PX[0], BAR_PX[1], BAR_PX[2], BAR_PX[3]], fill=SEG_OFF)
    for x in range(BAR_PX[0], BAR_PX[0] + 4):
        d.point((x, BAR_PX[1] + 1), fill=rgb(96, 230, 72))            # a few lit segments
    d.rectangle(LCD_PX, fill=LCD)
    for x, y in ((2, 4), (8, 4), (2, 9), (8, 9), (2, 14), (8, 14)):   # left keys
        d.rectangle([x, y, x + 3, y + 2], fill=KEY)
        d.line([x, y + 2, x + 3, y + 2], fill=FACE_DARK)
        d.point((x + 1, y + 3), fill=YELLOW)
    for row in range(3):                                              # right keys
        for c in range(3):
            x, y = 43 + c * 4, 4 + row * 5
            d.rectangle([x, y, x + 2, y + 2], fill=KEY)
            d.line([x, y + 2, x + 2, y + 2], fill=FACE_DARK)
    d.rectangle([35, 18, 37, 20], fill=KEY)                           # RATE
    d.rectangle([51, 21, 53, 23], fill=KEY)                           # ON/OFF
    for x, y in SMALL_KNOBS_PX + [DIAL_PX[:2]]:                       # shadows under the knob cubes
        d.rectangle([x, y, x + 1, y + 1], fill=FACE_DARK)
    d.rectangle([2, 20, 5, 23], fill=rgb(178, 180, 178))              # mic connector
    d.rectangle([3, 21, 4, 22], fill=rgb(60, 60, 60))
    d.point((8, 22), fill=rgb(10, 10, 10))                            # phones
    # Case: plain grey with a lighter top edge and a vent block.
    d.rectangle([CASE_TILE[0], CASE_TILE[1], CASE_TILE[2] - 1, CASE_TILE[3] - 1], fill=CASE)
    d.line([CASE_TILE[0], CASE_TILE[1], CASE_TILE[2] - 1, CASE_TILE[1]], fill=CASE_LIGHT)
    for y in range(CASE_TILE[1] + 12, CASE_TILE[1] + 20, 2):
        d.line([CASE_TILE[0] + 12, y, CASE_TILE[0] + 19, y], fill=CASE_DARK)
    # Tiles.
    d.rectangle([KNOB_TILE[0], KNOB_TILE[1], KNOB_TILE[2] - 1, KNOB_TILE[3] - 1], fill=rgb(30, 30, 30))
    d.point((KNOB_TILE[0] + 1, KNOB_TILE[1] + 1), fill=rgb(90, 90, 90))
    d.rectangle([DIAL_TILE[0], DIAL_TILE[1], DIAL_TILE[2] - 1, DIAL_TILE[3] - 1], fill=rgb(22, 22, 22))
    d.point((DIAL_TILE[0] + 2, DIAL_TILE[1] + 1), fill=rgb(70, 70, 70))
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
        {"name": "body", "from": [BX0, 0, BZ], "to": [BX1, BY1, 15], "faces": {
            "north": {"uv": uv(FRONT), "texture": "#tex"}, "east": {"uv": case, "texture": "#tex"},
            "south": {"uv": case, "texture": "#tex"}, "west": {"uv": case, "texture": "#tex"},
            "up": {"uv": case, "texture": "#tex"}, "down": {"uv": case, "texture": "#tex"}}},
        {"name": "DISPLAY", "from": [mx(LCD_PX[2] + 1), my(LCD_PX[3] + 1), BZ - 0.01], "to": [mx(LCD_PX[0]), my(LCD_PX[1]), BZ - 0.01],
         "faces": {"north": {"uv": uv((LCD_PX[0], LCD_PX[1], LCD_PX[2] + 1, LCD_PX[3] + 1)), "texture": "#tex"}}},
        cube("tuning_knob", DIAL_PX[0], DIAL_PX[1], DIAL_PX[2], DIAL_PX[3], BZ - 1.0, BZ, DIAL_TILE),
    ]
    for i, (x, y) in enumerate(SMALL_KNOBS_PX):
        elements.append(cube("knob_%d" % i, x, y, x + 2, y + 2, BZ - 0.5, BZ, KNOB_TILE))
    return {"credit": "Made with tools/hf_gui_proposals/hf_radio_80m_pure.py (the RC-K2, after a kit-built QRP rig)",
            "parent": "block/block", "texture_size": [64, 64],
            "textures": {"tex": "radiocraft:block/" + RADIO, "particle": "radiocraft:block/" + RADIO},
            "elements": elements}


def main():
    img, lay = draw()
    kit.write_runtime(img, lay, RADIO)
    block = block_texture()
    block.save(os.path.join(kit.ASSETS, "textures", "block", RADIO + ".png"))
    with open(os.path.join(kit.ASSETS, "models", "block", RADIO + ".json"), "w", encoding="utf-8") as f:
        json.dump(block_model(), f, indent="\t")
        f.write("\n")
    print("wrote the", RADIO, "block model and texture")


if __name__ == "__main__":
    main()
