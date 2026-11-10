"""Art for the marine radios (2026-11-09), after two real sets the maintainer picked: a 1980s portable (tall off-white
box, slotted speaker grille in a raised panel, small red LED, a silver plate, black rubber duck top left, a rotary knob
top right and a small red button on top) and a 1980s fixed-mount set (a low cream box with a recessed black front: a
red LED display and three grey knobs on the left with small switches between, a keypad of grey number keys with
yellow, green, blue and red keys in the middle, the red one the channel 16 key, and a speaker grille on the right).
No brand names: the plate says MARINE VHF and the lettering RADIOCRAFT.

Writes, under src/main/resources/assets/radiocraft/:
  textures/gui/marine/marine_radio.png      the fixed-mount radio's front panel screen, without its moving parts
  textures/gui/marine/marine_handheld.png   the handheld's front screen, without its moving parts
  gui_layouts/marine_radio.json             where each control is and how to draw what moves (HfPanel's format,
  gui_layouts/marine_handheld.json          tools/hf_gui_proposals/RUNTIME_SPEC.md, plus "jack" and "side_knob")
  textures/block/marine_radio_front.png     64x64: the block's front panel (4 px a model unit) and a knob tile
  textures/block/marine_radio_case.png      64x64: the cream case
  models/block/marine_radio.json            with the DISPLAY element RadioDisplays reads (TX/RX lamps: RadioDisplays)
  textures/item/marine_handheld.png         128x128 (8 px a model unit): front, back, sides, top, bottom, parts
  models/item/marine_handheld.json          a 3D item model, the VHF handheld's hand transforms

The screens (MarineRadioScreen, MarineHandheldScreen) draw the panel PNG, then from the layout: knob pointers, the
switch handles, lamps, pressed keys and the display text. To move a control, change it here and rerun.
Needs Pillow. Run: python tools/marine_radio_assets.py [--preview DIR]
(--preview also writes composites with the moving parts and sample display text, to check nothing overlaps.)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "radiocraft")

# ---- A 3 x 5 pixel font for the lettering (as tools/cb_gui_assets.py, plus arrows) ----------------------------------
GLYPHS = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
    "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101111011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Y": "101101010010010",
    "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100111101111", "7": "111001010010010", "8": "111101111101111",
    "9": "111101111001110", "/": "001001010100100", "-": "000000111000000", "+": "000010111010000", ":": "000010000010000",
    ".": "000000000000010", " ": "000000000000000", "^": "000010111000000", "v": "000111010000000",
}


def text_width(s):
    return max(0, len(s) * 4 - 1)


def text(img, x, y, s, colour):
    px = img.load()
    for i, ch in enumerate(s):
        glyph = GLYPHS.get(ch if ch in ("^", "v") else ch.upper(), GLYPHS[" "])
        for gy in range(5):
            for gx in range(3):
                if glyph[gy * 3 + gx] == "1":
                    X, Y = x + i * 4 + gx, y + gy
                    if 0 <= X < img.width and 0 <= Y < img.height:
                        px[X, Y] = colour


def text_centred(img, cx, y, s, colour):
    text(img, int(round(cx - text_width(s) / 2.0)), y, s, colour)


def rect(d, x0, y0, x1, y1, fill):
    """Fills x0..x1-1, y0..y1-1."""
    if x1 > x0 and y1 > y0:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)


def disc(d, cx, cy, r, fill):
    d.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], fill=fill)


def bevel(d, x0, y0, x1, y1, light, dark):
    """A 1 px edge: light on top and left, dark on the bottom and right (raised; swap them for sunk)."""
    rect(d, x0, y0, x1, y0 + 1, light)
    rect(d, x0, y0, x0 + 1, y1, light)
    rect(d, x0, y1 - 1, x1, y1, dark)
    rect(d, x1 - 1, y0, x1, y1, dark)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def save(img, *parts):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def hexc(c):
    return "#%02X%02X%02X" % c[:3]


# ======================================================================================================================
# The fixed-mount radio's screen (256 x 124)
# ======================================================================================================================
CREAM, CREAM_LIGHT, CREAM_DARK, CREAM_EDGE = (228, 206, 150, 255), (242, 226, 182, 255), (198, 174, 118, 255), (150, 128, 80, 255)
PANEL, PANEL_LIGHT, PANEL_DARK = (26, 26, 28, 255), (58, 58, 62, 255), (8, 8, 8, 255)
PANEL_LABEL = (206, 206, 200, 255)
KNOB_SKIRT, KNOB, KNOB_TOP, KNOB_SHINE, KNOB_RIDGE = (60, 60, 64, 255), (118, 118, 122, 255), (146, 146, 150, 255), (170, 170, 174, 255), (92, 92, 96, 255)
KEY_GREY, KEY_GREY_LIGHT, KEY_GREY_DARK = (176, 178, 182, 255), (214, 216, 220, 255), (110, 112, 116, 255)
KEY_DARK, KEY_DARK_LIGHT, KEY_DARK_DARK = (70, 72, 76, 255), (104, 106, 110, 255), (36, 36, 40, 255)
KEY_COLOURS = {
    "yellow": ((232, 200, 40, 255), (250, 230, 120, 255), (160, 132, 20, 255)),
    "green": ((56, 168, 82, 255), (120, 210, 140, 255), (28, 104, 46, 255)),
    "blue": ((52, 118, 200, 255), (120, 170, 236, 255), (26, 66, 130, 255)),
    "red": ((204, 40, 36, 255), (240, 110, 100, 255), (120, 18, 16, 255)),
}
LED_GLASS, LED_FRAME = (34, 8, 8, 255), (4, 4, 4, 255)
SLOT_BACK = (10, 10, 10, 255)

M_W, M_H = 256, 124
M_PANEL = (8, 9, 248, 116)
M_DISPLAY = (16, 15, 96, 36)                      # x, y, w, h
M_SWITCHES = [("power", "PWR", 24, ["OFF", "ON"]), ("hi_lo", "HI/LO", 46, ["LO", "HI"]), ("dual_watch", "DW", 68, ["OFF", "ON"])]
M_SWITCH_Y = (56, 68)                             # slot top and bottom
M_LAMPS = [("tx_lamp", "TX", 90, (255, 48, 32)), ("rx_lamp", "RX", 106, (60, 230, 90))]
M_LAMP_CY = 61
M_ROW1_LABEL_Y = 71
M_KNOBS = [("volume", "VOL", 26), ("squelch", "SQL", 56), ("channel", "CH", 86)]
M_KNOB_CY, M_KNOB_R = 94, 10
M_JACK = (112, 94, 5)                             # the mic jack: centre and radius
M_ROW2_LABEL_Y = 107
# The keypad: 4 x 4, the coloured keys down the left as on the real set. (name, legend, colour)
M_KEYS = [
    [("key_16_9", "16/9", "yellow"), ("key_1", "1", "grey"), ("key_2", "2", "grey"), ("key_3", "3", "grey")],
    [("key_up", "CH^", "green"), ("key_4", "4", "grey"), ("key_5", "5", "grey"), ("key_6", "6", "grey")],
    [("key_down", "CHv", "blue"), ("key_7", "7", "grey"), ("key_8", "8", "grey"), ("key_9", "9", "grey")],
    [("key_16", "16", "red"), ("key_clr", "CLR", "dark"), ("key_0", "0", "grey"), ("key_ent", "ENT", "dark")],
]
M_KEY_X0, M_KEY_Y0, M_KEY_W, M_KEY_H, M_KEY_GAP = 126, 26, 17, 13, 3
# DSC (2026-11-09): a row under the keypad. NAT cycles the nature of distress, ACK acknowledges the alert on the display,
# and the red DISTRESS button sits under a hinged red cover (the code draws the cover: shut over the button, or lifted
# into the strip above it). The keypad's "VHF FM MARINE" lettering made way for the row: the top line now reads
# RADIOCRAFT MARINE.
M_DSC_Y = 97
M_DSC_KEYS = [("key_nature", "NAT", "dark", 126), ("key_ack", "ACK", "green", 146)]
M_DISTRESS = (166, M_DSC_Y, 37, 13)               # x, y, w, h of the button
M_COVER_OPEN = (165, 89, 39, 5)                   # the lifted cover, seen edge on above the button
M_SPEAKER = (210, 16, 240, 110)


def key_box(col, row):
    x = M_KEY_X0 + col * (M_KEY_W + M_KEY_GAP)
    y = M_KEY_Y0 + row * (M_KEY_H + M_KEY_GAP)
    return x, y, M_KEY_W, M_KEY_H


def draw_key(img, d, x, y, w, h, colour, legend):
    if colour == "grey":
        face, light, dark, ink = KEY_GREY, KEY_GREY_LIGHT, KEY_GREY_DARK, (24, 24, 24, 255)
    elif colour == "dark":
        face, light, dark, ink = KEY_DARK, KEY_DARK_LIGHT, KEY_DARK_DARK, (226, 226, 226, 255)
    else:
        face, light, dark = KEY_COLOURS[colour]
        ink = (250, 250, 250, 255) if colour in ("red", "blue") else (24, 24, 24, 255)
    rect(d, x - 1, y - 1, x + w + 1, y + h + 1, PANEL_DARK)  # The hole the key sits in.
    rect(d, x, y, x + w, y + h, face)
    bevel(d, x, y, x + w, y + h, light, dark)
    text_centred(img, x + w / 2.0, y + (h - 5) // 2, legend, ink)


def draw_panel_knob(d, cx, cy, r):
    disc(d, cx, cy + 1, r, PANEL_DARK)          # Shadow under it.
    disc(d, cx, cy, r, KNOB_SKIRT)
    for k in range(28):                           # Ridges round the skirt.
        a = 2 * math.pi * k / 28
        d.point((cx - 0.5 + math.cos(a) * (r - 1.2), cy - 0.5 + math.sin(a) * (r - 1.2)), fill=KNOB_RIDGE)
    disc(d, cx, cy, r - 2, KNOB)
    disc(d, cx, cy, r - 4, KNOB_TOP)
    disc(d, cx - 1, cy - 1, max(2, r // 3), KNOB_SHINE)


def mobile_panel():
    img = Image.new("RGBA", (M_W, M_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # The cream case: rounded corners, a light top lip, darker underneath.
    d.rounded_rectangle([0, 0, M_W - 1, M_H - 1], radius=4, fill=CREAM, outline=CREAM_EDGE)
    rect(d, 2, 1, M_W - 2, 3, CREAM_LIGHT)
    rect(d, 2, M_H - 4, M_W - 2, M_H - 1, CREAM_DARK)
    # The recessed black front: a dark lip on top and left (the shadow), lighter at the bottom and right.
    x0, y0, x1, y1 = M_PANEL
    rect(d, x0 - 1, y0 - 1, x1 + 1, y1 + 1, CREAM_EDGE)
    rect(d, x0, y0, x1, y1, PANEL)
    bevel(d, x0, y0, x1, y1, PANEL_DARK, PANEL_LIGHT)

    # The LED display: black glass with a red tint, in a frame.
    dx, dy, dw, dh = M_DISPLAY
    rect(d, dx - 2, dy - 2, dx + dw + 2, dy + dh + 2, LED_FRAME)
    rect(d, dx, dy, dx + dw, dy + dh, LED_GLASS)
    rect(d, dx, dy, dx + dw, dy + 1, (60, 18, 18, 255))  # A glint on the glass's top edge.

    # The small switches (slots; the code draws the handle) and the lamps, labelled underneath.
    for name, label, cx, _ in M_SWITCHES:
        rect(d, cx - 4, M_SWITCH_Y[0] - 1, cx + 4, M_SWITCH_Y[1] + 1, PANEL_LIGHT)
        rect(d, cx - 3, M_SWITCH_Y[0], cx + 3, M_SWITCH_Y[1], SLOT_BACK)
        text_centred(img, cx, M_ROW1_LABEL_Y, label, PANEL_LABEL)
    for name, label, cx, colour in M_LAMPS:
        rect(d, cx - 4, M_LAMP_CY - 4, cx + 4, M_LAMP_CY + 4, PANEL_DARK)
        dim = tuple(c // 4 for c in colour) + (255,)
        rect(d, cx - 3, M_LAMP_CY - 3, cx + 2, M_LAMP_CY + 2, dim)
        text_centred(img, cx, M_ROW1_LABEL_Y, label, PANEL_LABEL)

    # The knobs (the code draws the pointer) and the mic jack, labelled underneath.
    for name, label, cx in M_KNOBS:
        draw_panel_knob(d, cx, M_KNOB_CY, M_KNOB_R)
        text_centred(img, cx, M_ROW2_LABEL_Y, label, PANEL_LABEL)
    jx, jy, jr = M_JACK
    disc(d, jx, jy, jr + 1, (150, 150, 154, 255))
    disc(d, jx, jy, jr, (96, 96, 100, 255))
    disc(d, jx, jy, jr - 2, (6, 6, 6, 255))
    text_centred(img, jx, M_ROW2_LABEL_Y, "MIC", PANEL_LABEL)

    # The keypad, lettering above and below it.
    text_centred(img, M_KEY_X0 + (4 * M_KEY_W + 3 * M_KEY_GAP) / 2.0, 16, "RADIOCRAFT MARINE", PANEL_LABEL)
    for row, keys in enumerate(M_KEYS):
        for col, (name, legend, colour) in enumerate(keys):
            x, y, w, h = key_box(col, row)
            draw_key(img, d, x, y, w, h, colour, legend)
    # The DSC row: NAT and ACK keys, the DISTRESS button in a red-rimmed well with the cover's hinge above it.
    for name, legend, colour, x in M_DSC_KEYS:
        draw_key(img, d, x, M_DSC_Y, M_KEY_W, M_KEY_H, colour, legend)
    bx, by, bw, bh = M_DISTRESS
    rect(d, bx - 2, by - 2, bx + bw + 2, by + bh + 2, (120, 18, 16, 255))  # The red rim of the well.
    draw_key(img, d, bx, by, bw, bh, "red", "DISTRESS")
    cx, cy, cw, ch = M_COVER_OPEN
    rect(d, cx + 2, by - 4, cx + cw - 2, by - 2, (70, 72, 76, 255))  # The hinge bar along the well's top.

    # The speaker: horizontal slots in a dark grille.
    sx0, sy0, sx1, sy1 = M_SPEAKER
    rect(d, sx0 - 1, sy0 - 1, sx1 + 1, sy1 + 1, PANEL_DARK)
    rect(d, sx0, sy0, sx1, sy1, (44, 44, 46, 255))
    for y in range(sy0 + 3, sy1 - 2, 3):
        rect(d, sx0 + 3, y, sx1 - 3, y + 1, (6, 6, 6, 255))
        rect(d, sx0 + 3, y + 1, sx1 - 3, y + 2, (64, 64, 66, 255))
    return img


def mobile_layout():
    controls = []
    dx, dy, dw, dh = M_DISPLAY
    controls.append({"name": "display", "type": "display", "x": dx, "y": dy, "w": dw, "h": dh, "ink": "#FF4030", "ink_dim": "#5A1410"})
    for name, label, cx, positions in M_SWITCHES:
        top, bottom = M_SWITCH_Y
        controls.append({"name": name, "type": "switch", "x": cx - 5, "y": top - 2, "w": 10, "h": bottom - top + 4,
                         "positions": positions,
                         # Down is the first position (OFF, LO), up the second (ON, HI).
                         "handles": [[cx - 3, bottom - 5, 6, 5], [cx - 3, top, 6, 5]],
                         "handle_colour": "#C8C8C4", "handle_shadow": "#606060"})
    for name, label, cx, colour in M_LAMPS:
        controls.append({"name": name, "type": "lamp", "x": cx - 3, "y": M_LAMP_CY - 3, "w": 5, "h": 5, "colour": hexc(colour)})
    for name, label, cx in M_KNOBS:
        r = M_KNOB_R
        controls.append({"name": name, "type": "knob", "x": cx - r, "y": M_KNOB_CY - r, "w": 2 * r, "h": 2 * r, "cx": cx - 0.5, "cy": M_KNOB_CY - 0.5,
                         "pointer_from": 2, "pointer_r": r - 2, "pointer_width": 1, "pointer_colour": "#F0F0F0", "min_deg": -135, "max_deg": 135})
    jx, jy, jr = M_JACK
    controls.append({"name": "ptt", "type": "jack", "x": jx - jr - 1, "y": jy - jr - 1, "w": 2 * jr + 2, "h": 2 * jr + 2, "glow": "#FF3020"})
    for row, keys in enumerate(M_KEYS):
        for col, (name, legend, colour) in enumerate(keys):
            x, y, w, h = key_box(col, row)
            controls.append({"name": name, "type": "button", "x": x, "y": y, "w": w, "h": h})
    for name, legend, colour, x in M_DSC_KEYS:
        controls.append({"name": name, "type": "button", "x": x, "y": M_DSC_Y, "w": M_KEY_W, "h": M_KEY_H})
    bx, by, bw, bh = M_DISTRESS
    cx, cy, cw, ch = M_COVER_OPEN
    # The button under its cover: "cover" is where the shut cover is drawn (over the button and its rim), "cover_open"
    # where the lifted one is; "distress_cover" is the lifted cover's strip, clicked to shut it.
    controls.append({"name": "distress", "type": "distress", "x": bx, "y": by, "w": bw, "h": bh,
                     "cover": [[bx - 2, by - 2, bw + 4, bh + 4]], "cover_open": [[cx, cy, cw, ch]],
                     "cover_colour": "#C82828", "cover_edge": "#701010", "cover_shine": "#F07070"})
    controls.append({"name": "distress_cover", "type": "cover", "x": cx, "y": cy, "w": cw, "h": ch})
    return {"texture": "radiocraft:textures/gui/marine/marine_radio.png", "width": M_W, "height": M_H, "controls": controls}


# ======================================================================================================================
# The handheld's screen (104 x 222)
# ======================================================================================================================
WHITE, WHITE_LIGHT, WHITE_DARK, WHITE_EDGE = (222, 222, 208, 255), (238, 238, 228, 255), (196, 196, 180, 255), (140, 140, 126, 255)
GRILLE, SLOT_DARK, SLOT_LIGHT = (230, 230, 218, 255), (150, 150, 138, 255), (246, 246, 238, 255)
SILVER, SILVER_LIGHT, SILVER_DARK = (196, 200, 204, 255), (228, 230, 232, 255), (132, 136, 140, 255)
RUBBER, RUBBER_LIGHT, RUBBER_DARK = (30, 30, 32, 255), (62, 62, 66, 255), (10, 10, 10, 255)
LCD, LCD_EDGE = (150, 166, 140, 255), (52, 56, 50, 255)
LETTERING = (40, 44, 80, 255)

H_W, H_H = 104, 222
H_BODY = (8, 46, 96, 220)
H_ANTENNA = (13, 2, 21, 38)                       # the rubber duck; its connector below to the body
H_BUTTON = (30, 41, 38, 46)                       # the red channel 16 button on top
H_KNOB = (62, 30, 82, 46)                         # the volume knob on top, seen side on
H_GRILLE = (14, 52, 90, 136)
H_LED = (78, 56, 84, 61)
H_PLATE = (36, 120, 86, 132)
H_LCD = (30, 144, 74, 164)
H_KEYS = [("key_down", "CHv", 16, 172), ("key_up", "CH^", 42, 172), ("key_16_9", "16/9", 68, 172),
          ("hi_lo", "H/L", 29, 186), ("dual_watch", "DW", 55, 186)]
H_KEY_W, H_KEY_H = 20, 10
H_PTT = (3, 66, 9, 120)
H_SEAM = 203


def handheld_panel():
    img = Image.new("RGBA", (H_W, H_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # The rubber duck: ribbed black, a rounded cap, a connector at its foot.
    ax0, ay0, ax1, ay1 = H_ANTENNA
    d.rounded_rectangle([ax0, ay0, ax1 - 1, ay1 + 2], radius=3, fill=RUBBER)
    rect(d, ax0 + 1, ay0 + 2, ax0 + 2, ay1, RUBBER_LIGHT)
    for y in range(ay0 + 6, ay1, 4):
        rect(d, ax0 + 1, y, ax1 - 1, y + 1, RUBBER_DARK)
    rect(d, ax0 - 2, ay1, ax1 + 2, H_BODY[1], (20, 20, 22, 255))
    rect(d, ax0 - 1, ay1 + 2, ax1 + 1, ay1 + 3, (90, 90, 94, 255))
    # The red button on top.
    bx0, by0, bx1, by1 = H_BUTTON
    rect(d, bx0 - 1, by0 + 1, bx1 + 1, by1, (60, 60, 60, 255))
    rect(d, bx0, by0, bx1, by1, (204, 40, 36, 255))
    rect(d, bx0, by0, bx1, by0 + 1, (240, 120, 110, 255))
    # The knob: a knurled black cylinder with a lighter top, on a grey collar (the code draws its white index mark).
    kx0, ky0, kx1, ky1 = H_KNOB
    rect(d, kx0 - 2, ky1 - 3, kx1 + 2, ky1, (110, 110, 112, 255))
    rect(d, kx0, ky0, kx1, ky1 - 3, RUBBER)
    for x in range(kx0 + 1, kx1, 2):
        rect(d, x, ky0 + 2, x + 1, ky1 - 3, RUBBER_LIGHT)
    rect(d, kx0, ky0, kx1, ky0 + 2, (80, 80, 84, 255))

    # The body.
    x0, y0, x1, y1 = H_BODY
    d.rounded_rectangle([x0, y0, x1 - 1, y1 - 1], radius=4, fill=WHITE, outline=WHITE_EDGE)
    rect(d, x0 + 1, y0 + 2, x0 + 3, y1 - 2, WHITE_LIGHT)
    rect(d, x1 - 4, y0 + 2, x1 - 1, y1 - 2, WHITE_DARK)
    # The push-to-talk bar on the left side.
    px0, py0, px1, py1 = H_PTT
    rect(d, px0, py0, px1, py1, RUBBER)
    for y in range(py0 + 3, py1 - 2, 4):
        rect(d, px0 + 1, y, px1 - 1, y + 1, RUBBER_LIGHT)

    # The raised grille panel: slots in three columns, the LED top right, a mic vent and the plate at its foot.
    gx0, gy0, gx1, gy1 = H_GRILLE
    rect(d, gx0, gy0, gx1, gy1, GRILLE)
    bevel(d, gx0, gy0, gx1, gy1, WHITE_LIGHT, WHITE_EDGE)
    rect(d, gx0 + 1, gy1, gx1, gy1 + 1, WHITE_DARK)  # Its shadow on the case.
    lx0, ly0, lx1, ly1 = H_LED
    for y in range(58, 115, 4):
        for cx0, cx1 in ((18, 38), (42, 62), (66, 86)):
            if cx0 == 66 and y < ly1 + 2:
                cx1 = lx0 - 3  # Make room for the LED.
            rect(d, cx0, y, cx1, y + 1, SLOT_DARK)
            rect(d, cx0, y + 1, cx1, y + 2, SLOT_LIGHT)
    rect(d, lx0, ly0, lx1, ly1, (70, 60, 56, 255))
    rect(d, lx0 + 1, ly0 + 1, lx1 - 1, ly1 - 1, (110, 30, 24, 255))
    for x in range(20, 30, 3):  # The mic vent beside the plate.
        rect(d, x, 121, x + 1, 131, SLOT_DARK)
    plx0, ply0, plx1, ply1 = H_PLATE
    rect(d, plx0, ply0, plx1, ply1, SILVER)
    bevel(d, plx0, ply0, plx1, ply1, SILVER_LIGHT, SILVER_DARK)
    text_centred(img, (plx0 + plx1) / 2.0, ply0 + 4, "MARINE VHF", (60, 62, 66, 255))

    # The little LCD in the plain lower half (the code prints the channel).
    cx0, cy0, cx1, cy1 = H_LCD
    rect(d, cx0 - 2, cy0 - 2, cx1 + 2, cy1 + 2, WHITE_EDGE)
    rect(d, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, LCD_EDGE)
    rect(d, cx0, cy0, cx1, cy1, LCD)

    # The keys, legends on them.
    for name, legend, kx, ky in H_KEYS:
        rect(d, kx - 1, ky - 1, kx + H_KEY_W + 1, ky + H_KEY_H + 1, WHITE_EDGE)
        rect(d, kx, ky, kx + H_KEY_W, ky + H_KEY_H, (92, 94, 98, 255))
        bevel(d, kx, ky, kx + H_KEY_W, ky + H_KEY_H, (130, 132, 136, 255), (50, 52, 56, 255))
        text_centred(img, kx + H_KEY_W / 2.0, ky + 3, legend, (236, 236, 236, 255))

    # The battery seam, and the lettering with its stripes.
    rect(d, x0 + 1, H_SEAM, x1 - 1, H_SEAM + 1, WHITE_DARK)
    rect(d, x0 + 1, H_SEAM + 1, x1 - 1, H_SEAM + 2, WHITE_LIGHT)
    word = "RADIOCRAFT"
    wx = int(round((x0 + x1) / 2.0 - text_width(word) / 2.0))
    text(img, wx, H_SEAM + 6, word, LETTERING)
    for y in (H_SEAM + 6, H_SEAM + 8, H_SEAM + 10):
        rect(d, x0 + 8, y, wx - 3, y + 1, LETTERING)
        rect(d, wx + text_width(word) + 3, y, x1 - 8, y + 1, LETTERING)
    return img


def handheld_layout():
    controls = []
    cx0, cy0, cx1, cy1 = H_LCD
    controls.append({"name": "display", "type": "display", "x": cx0, "y": cy0, "w": cx1 - cx0, "h": cy1 - cy0, "ink": "#141C14"})
    kx0, ky0, kx1, ky1 = H_KNOB
    controls.append({"name": "volume", "type": "side_knob", "x": kx0 - 2, "y": ky0 - 2, "w": kx1 - kx0 + 4, "h": ky1 - ky0 + 2,
                     "cx": (kx0 + kx1) / 2.0, "r": (kx1 - kx0) / 2.0, "y0": ky0 + 2, "y1": ky1 - 3, "mark_colour": "#F0F0F0",
                     "min_deg": -135, "max_deg": 135})
    bx0, by0, bx1, by1 = H_BUTTON
    controls.append({"name": "key_16", "type": "button", "x": bx0 - 2, "y": by0 - 3, "w": bx1 - bx0 + 4, "h": by1 - by0 + 3})
    lx0, ly0, lx1, ly1 = H_LED
    controls.append({"name": "tx_lamp", "type": "lamp", "x": lx0 + 1, "y": ly0 + 1, "w": lx1 - lx0 - 2, "h": ly1 - ly0 - 2, "colour": "#FF3020",
                     "rx_colour": "#40E060"})
    for name, legend, kx, ky in H_KEYS:
        c = {"name": name, "type": "button", "x": kx, "y": ky, "w": H_KEY_W, "h": H_KEY_H}
        if name == "hi_lo":
            c["lit"] = [kx + 2, ky + 1, H_KEY_W - 4, 1, "#FFB020"]  # Lit while on LO.
        elif name == "dual_watch":
            c["lit"] = [kx + 2, ky + 1, H_KEY_W - 4, 1, "#40E060"]
        controls.append(c)
    px0, py0, px1, py1 = H_PTT
    controls.append({"name": "ptt", "type": "side_ptt", "x": px0 - 1, "y": py0, "w": px1 - px0 + 2, "h": py1 - py0, "glow": "#FF3020"})
    return {"texture": "radiocraft:textures/gui/marine/marine_handheld.png", "width": H_W, "height": H_H, "controls": controls}


# ======================================================================================================================
# The block: a low, wide cream box with a recessed black front (64x64 textures, 4 px a model unit)
# ======================================================================================================================
# The front panel art is 44 x 16 px, as seen from the front (left = east, the model's x 13.5), at B_FACE_OFFSET in the face.
B_PANEL_X = (2.5, 13.5)
B_PANEL_Y = (0.5, 4.5)
B_RECESS_Z = 4.5          # the panel face; the bezel stands out to z 4
B_FACE_OFFSET = 2         # the panel art's place in the 48 x 20 px north face texture
B_DISPLAY_PX = (2, 2, 17, 7)
B_KNOBS_PX = [(3, 11), (7, 11), (11, 11)]   # top left of each 3 x 3 knob
B_LAMPS_PX = [(13, 8, True), (15, 8, False)]
KNOB_TILE = (48, 0, 52, 4)                    # a knob's faces
BEZEL_TILE = (48, 8, 52, 12)                  # cream, for the bezel strips
DARK_TILE = (56, 0, 60, 4)                    # the mic hook


def block_front():
    """The front: the 44 x 16 px panel at (2, 2) inside a 48 x 20 px face (the body's whole north face, 12 x 5 units;
    the cream rim is under the bezel), then tiles for the knobs, bezel and mic hook."""
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    ImageDraw.Draw(sheet).rectangle([0, 0, 47, 19], fill=CREAM)
    img = Image.new("RGBA", (44, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 44, 16, PANEL)
    rect(d, 0, 0, 44, 1, PANEL_DARK)
    rect(d, 0, 0, 1, 16, PANEL_DARK)
    # The LED display (the renderer prints the channel over it).
    x0, y0, x1, y1 = B_DISPLAY_PX
    rect(d, x0 - 1, y0 - 1, x1 + 1, y1 + 1, LED_FRAME)
    rect(d, x0, y0, x1, y1, LED_GLASS)
    # The switches (grey bats) and the TX and RX lamps, dark (the renderer lights them).
    for x in (3, 6, 9):
        rect(d, x, 8, x + 1, 10, (176, 176, 172, 255))
    for x, y, tx in B_LAMPS_PX:
        rect(d, x, y, x + 1, y + 1, (90, 16, 12, 255) if tx else (16, 70, 24, 255))
    # Knob shadows (the knobs themselves are cubes) and the mic jack.
    for x, y in B_KNOBS_PX:
        rect(d, x, y, x + 3, y + 3, (60, 60, 64, 255))
    rect(d, 15, 11, 17, 13, (110, 110, 114, 255))
    rect(d, 15, 12, 16, 13, (6, 6, 6, 255))
    # The keypad: 4 x 4 keys of 2 x 2, the coloured ones down the left.
    left = [KEY_COLOURS["yellow"][0], KEY_COLOURS["green"][0], KEY_COLOURS["blue"][0], KEY_COLOURS["red"][0]]
    for row in range(4):
        for col in range(4):
            x, y = 19 + col * 3, 2 + row * 3
            if col == 0:
                c = left[row]
            elif row == 3 and col in (1, 3):
                c = KEY_DARK
            else:
                c = KEY_GREY
            rect(d, x, y, x + 2, y + 2, c)
            rect(d, x + 1, y + 1, x + 2, y + 2, tuple(max(0, v - 40) for v in c[:3]) + (255,))
    # The speaker: horizontal slots.
    rect(d, 32, 2, 43, 15, (44, 44, 46, 255))
    for y in range(3, 15, 2):
        rect(d, 33, y, 42, y + 1, (6, 6, 6, 255))
    sheet.alpha_composite(img, (B_FACE_OFFSET, B_FACE_OFFSET))
    img, d = sheet, ImageDraw.Draw(sheet)
    # Tiles for the other parts.
    rect(d, *KNOB_TILE, KNOB)
    rect(d, KNOB_TILE[0], KNOB_TILE[1], KNOB_TILE[2], KNOB_TILE[1] + 1, KNOB_TOP)
    rect(d, KNOB_TILE[0] + 1, KNOB_TILE[1] + 1, KNOB_TILE[0] + 2, KNOB_TILE[1] + 2, KNOB_SHINE)
    rect(d, *BEZEL_TILE, CREAM)
    rect(d, *DARK_TILE, (40, 40, 42, 255))
    return img


def block_case():
    img = Image.new("RGBA", (64, 64), CREAM)
    d = ImageDraw.Draw(img)
    for y in range(64):  # A faint grain, so the big faces are not flat.
        for x in range(64):
            if (x * 7 + y * 13) % 23 == 0:
                img.putpixel((x, y), (222, 200, 144, 255))
    rect(d, 0, 0, 64, 2, CREAM_LIGHT)
    rect(d, 0, 0, 2, 64, CREAM_LIGHT)
    rect(d, 0, 62, 64, 64, CREAM_DARK)
    rect(d, 62, 0, 64, 64, CREAM_DARK)
    return img


def uvpx(x0, y0, x1, y1):
    """Texture pixels (64 wide) to model UV (0-16)."""
    return [x0 / 4.0, y0 / 4.0, x1 / 4.0, y1 / 4.0]


def faces_all(texture, uv):
    return {f: {"uv": uv, "texture": texture} for f in ("north", "east", "south", "west", "up", "down")}


def block_model():
    px0, px1 = B_PANEL_X
    py0, py1 = B_PANEL_Y

    def model_x(px):  # Panel texture x (from the viewer's left, east) to model x.
        return px1 - px / 4.0

    def model_y(py):
        return py1 - py / 4.0

    bezel = uvpx(*BEZEL_TILE)
    elements = [
        {"name": "body", "from": [2, 0, B_RECESS_Z], "to": [14, 5, 15], "faces": {
            "north": {"uv": uvpx(0, 0, 48, 20), "texture": "#front"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#case"}, "south": {"uv": [0, 0, 16, 16], "texture": "#case"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#case"}, "up": {"uv": [0, 0, 16, 16], "texture": "#case"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#case"}}},
        # The cream bezel round the recessed front.
        {"name": "bezel_top", "from": [2, py1, 4], "to": [14, 5, B_RECESS_Z], "faces": faces_all("#case", [0, 0, 16, 2])},
        {"name": "bezel_bottom", "from": [2, 0, 4], "to": [14, py0, B_RECESS_Z], "faces": faces_all("#case", [0, 14, 16, 16])},
        {"name": "bezel_left", "from": [px1, py0, 4], "to": [14, py1, B_RECESS_Z], "faces": faces_all("#case", bezel)},
        {"name": "bezel_right", "from": [2, py0, 4], "to": [px0, py1, B_RECESS_Z], "faces": faces_all("#case", bezel)},
    ]
    dx0, dy0, dx1, dy1 = B_DISPLAY_PX
    elements.append({"name": "DISPLAY", "from": [model_x(dx1), model_y(dy1), B_RECESS_Z - 0.1], "to": [model_x(dx0), model_y(dy0), B_RECESS_Z],
                     "faces": {"north": {"uv": uvpx(dx0 + B_FACE_OFFSET, dy0 + B_FACE_OFFSET, dx1 + B_FACE_OFFSET, dy1 + B_FACE_OFFSET), "texture": "#front"}}})
    for i, (kx, ky) in enumerate(B_KNOBS_PX):
        elements.append({"name": "knob_" + ("volume", "squelch", "channel")[i], "from": [model_x(kx + 3), model_y(ky + 3), 3.85],
                         "to": [model_x(kx), model_y(ky), B_RECESS_Z], "faces": faces_all("#front", uvpx(*KNOB_TILE))})
    elements.append({"name": "mic_hook", "from": [14, 1, 6], "to": [14.75, 4, 8], "faces": faces_all("#front", uvpx(*DARK_TILE))})
    return {
        "credit": "Made with tools/marine_radio_assets.py (after a 1980s fixed-mount marine set)",
        "parent": "block/block",
        "texture_size": [64, 64],
        "textures": {"case": "radiocraft:block/marine_radio_case", "front": "radiocraft:block/marine_radio_front",
                     "particle": "radiocraft:block/marine_radio_case"},
        "elements": elements,
    }


def block_lamps():
    """The TX and RX lamps for RadioDisplays: (x1 west, x2 east, y1, y2, front z)."""
    out = []
    for x, y, tx in B_LAMPS_PX:
        out.append((B_PANEL_X[1] - (x + 1) / 4.0, B_PANEL_X[1] - x / 4.0, B_PANEL_Y[1] - (y + 1) / 4.0, B_PANEL_Y[1] - y / 4.0, B_RECESS_Z, tx))
    return out


# ======================================================================================================================
# The handheld item: a 3D model, 128x128 texture at 8 px a model unit
# ======================================================================================================================
# Body x 5.5-10.5, y 1.5-14, z 6.75-9.25; the front is south (as the other handhelds), the antenna top left.
I_BODY = ((5.5, 1.5, 6.75), (10.5, 14.0, 9.25))
I_FRONT = (0, 0, 40, 100)      # south
I_BACK = (40, 0, 80, 100)      # north
I_SIDE = (80, 0, 100, 100)     # east and west
I_TOP = (0, 100, 40, 120)
I_BOTTOM = (40, 100, 80, 120)
I_EDGE = (100, 0, 108, 8)      # the grille panel's edges
I_BLACK = (100, 8, 108, 16)    # antenna, knob sides, PTT
I_KNOB_TOP = (100, 16, 108, 24)
I_RED = (100, 24, 108, 32)
I_GRILLE_PX = (3, 3, 37, 57)   # the raised panel on the front, in front texture pixels


def item_texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Front (40 x 100): case, grille panel, LED, vent and plate, the LCD, keys, seam and lettering.
    rect(d, 0, 0, 40, 100, WHITE)
    rect(d, 0, 0, 1, 100, WHITE_LIGHT)
    rect(d, 39, 0, 40, 100, WHITE_DARK)
    gx0, gy0, gx1, gy1 = I_GRILLE_PX
    rect(d, gx0, gy0, gx1, gy1, GRILLE)
    bevel(d, gx0, gy0, gx1, gy1, WHITE_LIGHT, WHITE_EDGE)
    for y in range(7, 45, 3):
        for cx0, cx1 in ((5, 14), (16, 25), (27, 35)):
            if cx0 == 27 and y < 10:
                cx1 = 29
            rect(d, cx0, y, cx1, y + 1, SLOT_DARK)
    rect(d, 31, 5, 34, 8, (70, 60, 56, 255))
    rect(d, 32, 6, 33, 7, (200, 40, 30, 255))  # The red LED.
    for x in (6, 8, 10):
        rect(d, x, 47, x + 1, 54, SLOT_DARK)
    rect(d, 13, 47, 35, 54, SILVER)
    bevel(d, 13, 47, 35, 54, SILVER_LIGHT, SILVER_DARK)
    rect(d, 17, 50, 31, 51, (110, 112, 116, 255))  # The plate's lettering, too small to read.
    # The little screen in the lower half, showing 16 (the channel a radio comes on).
    rect(d, 10, 62, 30, 73, LCD_EDGE)
    rect(d, 11, 63, 29, 72, LCD)
    text(img, 16, 65, "16", (20, 28, 20, 255))
    # The keys under it.
    for x, y in ((8, 77), (17, 77), (26, 77), (12, 81), (21, 81)):
        rect(d, x, y, x + 6, y + 3, (92, 94, 98, 255))
        rect(d, x, y, x + 6, y + 1, (130, 132, 136, 255))
    # The seam and the lettering with its stripes.
    rect(d, 1, 88, 39, 89, WHITE_DARK)
    for y in (92, 94, 96):
        rect(d, 4, y, 9, y + 1, LETTERING)
        rect(d, 31, y, 36, y + 1, LETTERING)
    for x in range(11, 30, 2):  # RADIOCRAFT, too small to read: letter strokes.
        rect(d, x, 92 if x % 4 == 3 else 93, x + 1, 97 if x % 4 == 3 else 96, LETTERING)
    # Back: plain case with the battery door.
    rect(d, *I_BACK, WHITE)
    rect(d, 43, 60, 77, 61, WHITE_DARK)
    rect(d, 43, 60, 44, 97, WHITE_DARK)
    rect(d, 76, 60, 77, 97, WHITE_DARK)
    rect(d, 43, 96, 77, 97, WHITE_DARK)
    rect(d, 57, 10, 63, 40, WHITE_DARK)  # The belt clip's shadow.
    # Sides, top, bottom.
    rect(d, *I_SIDE, WHITE_DARK)
    rect(d, 80, 0, 100, 1, WHITE_LIGHT)
    rect(d, 80, 88, 100, 89, WHITE_EDGE)
    rect(d, *I_TOP, WHITE_LIGHT)
    rect(d, *I_BOTTOM, WHITE_DARK)
    # Part tiles.
    rect(d, *I_EDGE, WHITE_LIGHT)
    rect(d, *I_BLACK, RUBBER)
    for y in range(I_BLACK[1], I_BLACK[3], 2):
        rect(d, I_BLACK[0], y, I_BLACK[2], y + 1, RUBBER_LIGHT)
    rect(d, *I_KNOB_TOP, (60, 60, 64, 255))
    rect(d, I_KNOB_TOP[0] + 2, I_KNOB_TOP[1] + 2, I_KNOB_TOP[2] - 2, I_KNOB_TOP[3] - 2, (110, 110, 114, 255))
    rect(d, *I_RED, (204, 40, 36, 255))
    return img


def uv8(box):
    x0, y0, x1, y1 = box
    return [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]


def item_model():
    (x0, y0, z0), (x1, y1, z1) = I_BODY
    gx0, gy0, gx1, gy1 = I_GRILLE_PX
    tex = "#0"

    def f(box):
        return {"uv": uv8(box), "texture": tex}

    def all6(box):
        return {d: f(box) for d in ("north", "east", "south", "west", "up", "down")}

    elements = [
        {"name": "body", "from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {
            "south": f(I_FRONT), "north": f(I_BACK), "east": f(I_SIDE), "west": f(I_SIDE), "up": f(I_TOP), "down": f(I_BOTTOM)}},
        # The raised grille panel: its face is the same part of the front texture.
        {"name": "grille_panel", "from": [x0 + gx0 / 8.0, y1 - gy1 / 8.0, z1], "to": [x0 + gx1 / 8.0, y1 - gy0 / 8.0, z1 + 0.2], "faces": {
            "south": f(I_GRILLE_PX), "east": f(I_EDGE), "west": f(I_EDGE), "up": f(I_EDGE), "down": f(I_EDGE)}},
        {"name": "antenna_collar", "from": [5.85, 14, 7.35], "to": [7.15, 15, 8.65], "faces": all6(I_BLACK)},
        {"name": "antenna", "from": [6.1, 15, 7.6], "to": [6.9, 21.5, 8.4], "faces": all6(I_BLACK)},
        {"name": "volume_knob", "from": [8.6, 14, 7.35], "to": [9.9, 15.25, 8.65], "faces": {
            "north": f(I_BLACK), "east": f(I_BLACK), "south": f(I_BLACK), "west": f(I_BLACK), "up": f(I_KNOB_TOP), "down": f(I_BLACK)}},
        {"name": "button_16", "from": [7.45, 14, 7.7], "to": [8.15, 14.4, 8.3], "faces": all6(I_RED)},
        {"name": "ptt", "from": [5.25, 8.5, 7.4], "to": [5.5, 11.5, 8.6], "faces": all6(I_BLACK)},
    ]
    display = {  # As the VHF handheld's, so it is the same size in the hand.
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": [0.5625, 0.5625, 0.5625]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": [0.5625, 0.5625, 0.5625]},
        "firstperson_righthand": {"rotation": [-8, 12, 6], "translation": [-1.5, 0.5, -1], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [-8, -12, -6], "translation": [-1.5, 0.5, -1], "scale": [0.6, 0.6, 0.6]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "gui": {"rotation": [10, -25, 0], "translation": [0, -2.4, 0], "scale": [0.68, 0.68, 0.68]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -2.4, 0], "scale": [0.68, 0.68, 0.68]},
    }
    return {
        "credit": "Made with tools/marine_radio_assets.py (after a 1980s marine handheld)",
        "texture_size": [128, 128],
        "textures": {"0": "radiocraft:item/marine_handheld", "particle": "radiocraft:item/marine_handheld"},
        "elements": elements,
        "display": display,
    }


# ======================================================================================================================
# Previews: the panels with their moving parts drawn as the screens draw them, and sample display text
# ======================================================================================================================
MC_NARROW = {"i": 2, "l": 3, "I": 4, "t": 4, " ": 4, ".": 2, ":": 2, "!": 2, ",": 2, "'": 2, "k": 5, "f": 5, "%": 6}


def mc_width(s):
    """Minecraft's font: most glyphs 5 px and a 1 px gap."""
    return sum(MC_NARROW.get(c, 6) for c in s) - (1 if s else 0)


def mc_text(img, x, y, s, colour, scale=1.0):
    """Approximates Minecraft's font (6 px advance, 7 px caps), scaled; for the previews only."""
    w = max(1, mc_width(s))
    tmp = Image.new("RGBA", (w + 2, 9), (0, 0, 0, 0))
    font = ImageFont.load_default()
    td = ImageDraw.Draw(tmp)
    cx = 0
    for c in s:
        td.text((cx, -2), c, font=font, fill=colour)
        cx += MC_NARROW.get(c, 6)
    if scale != 1.0:
        tmp = tmp.resize((max(1, int(round(tmp.width * scale))), max(1, int(round(tmp.height * scale)))), Image.NEAREST)
    img.alpha_composite(tmp, (int(round(x)), int(round(y))))


def ray(img, cx, cy, deg, r0, r1, colour):
    px = img.load()
    t = r0
    while t <= r1:
        x, y = int(math.floor(cx + t * math.sin(math.radians(deg)))), int(math.floor(cy - t * math.cos(math.radians(deg))))
        if 0 <= x < img.width and 0 <= y < img.height:
            px[x, y] = colour
        t += 0.35


def col(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def preview_mobile(layout, state, path):
    img = mobile_panel()
    d = ImageDraw.Draw(img)
    for c in layout["controls"]:
        t = c["type"]
        if t == "knob":
            v = state["knobs"][c["name"]]
            ray(img, c["cx"], c["cy"], c["min_deg"] + v * (c["max_deg"] - c["min_deg"]), c["pointer_from"], c["pointer_r"], col(c["pointer_colour"]))
        elif t == "switch":
            h = c["handles"][c["positions"].index(state["switches"][c["name"]])]
            rect(d, h[0], h[1], h[0] + h[2], h[1] + h[3], col(c["handle_shadow"]))
            rect(d, h[0], h[1], h[0] + h[2] - 1, h[1] + h[3] - 1, col(c["handle_colour"]))
        elif t == "lamp" and state["lamps"].get(c["name"]):
            rect(d, c["x"], c["y"], c["x"] + c["w"], c["y"] + c["h"], col(c["colour"]))
        elif t == "button" and c["name"] == state.get("pressed"):
            img.alpha_composite(Image.new("RGBA", (c["w"], c["h"]), (0, 0, 0, 85)), (c["x"], c["y"]))
        elif t == "distress":
            draw_cover(img, d, c, state.get("cover_open", False))
        elif t == "display" and state["on"] and "lines" in state:
            # A DSC alert on the display (MarineRadioScreen.renderDsc): three lines, each shrunk to fit.
            ink = col(c["ink"])
            x, y, w, h = c["x"], c["y"], c["w"], c["h"]
            for i, line in enumerate(state["lines"]):
                scale = min(1.0, (w - 8) / max(1, mc_width(line)))
                mc_text(img, x + 4, y + 3 + i * 11 + (1.0 - scale) * 4.0, line, ink, scale)
        elif t == "display" and state["on"]:
            ink = col(c["ink"])
            x, y, w, h = c["x"], c["y"], c["w"], c["h"]
            # As MarineRadioScreen: the channel at twice size top left, the plan and power/DW right, the name under.
            mc_text(img, x + 4, y + 3, state["channel"], ink, 2.0)
            mc_text(img, x + w - 3 - mc_width(state["plan"]), y + 3, state["plan"], ink)
            mc_text(img, x + w - 3 - mc_width(state["flags"]), y + 12, state["flags"], ink)
            mc_text(img, x + 4, y + h - 11, state["name"], ink)
    img = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
    img.save(path)


def draw_cover(img, d, c, open_):
    """As MarinePanels.cover: shut, a red translucent lid over the button and its rim; open, its edge above it."""
    fill, edge, shine = col(c["cover_colour"]), col(c["cover_edge"]), col(c["cover_shine"])
    if open_:
        x, y, w, h = c["cover_open"][0]
        rect(d, x, y, x + w, y + h, fill)
        rect(d, x, y, x + w, y + 1, shine)
        rect(d, x, y + h - 1, x + w, y + h, edge)
    else:
        x, y, w, h = c["cover"][0]
        img.alpha_composite(Image.new("RGBA", (w, h), fill[:3] + (150,)), (x, y))
        rect(d, x, y, x + w, y + 1, shine)
        rect(d, x, y + h - 1, x + w, y + h, edge)
        rect(d, x, y, x + 1, y + h, edge)
        rect(d, x + w - 1, y, x + w, y + h, edge)


def preview_handheld(layout, state, path):
    img = handheld_panel()
    d = ImageDraw.Draw(img)
    for c in layout["controls"]:
        t = c["type"]
        if t == "side_knob" and state["on"]:
            deg = c["min_deg"] + state["volume"] * (c["max_deg"] - c["min_deg"])
            if abs(deg) < 85:
                x = int(math.floor(c["cx"] + c["r"] * math.sin(math.radians(deg))))
                rect(d, x, int(c["y0"]), x + 1, int(c["y1"]), col(c["mark_colour"]))
        elif t == "lamp" and state.get("tx"):
            rect(d, c["x"], c["y"], c["x"] + c["w"], c["y"] + c["h"], col(c["colour"]))
        elif t == "button":
            if "lit" in c and state["lit"].get(c["name"]):
                lx, ly, lw, lh, lc = c["lit"]
                rect(d, lx, ly, lx + lw, ly + lh, col(lc))
            if c["name"] == state.get("pressed"):
                img.alpha_composite(Image.new("RGBA", (c["w"], c["h"]), (0, 0, 0, 85)), (c["x"], c["y"]))
        elif t == "display" and state["on"]:
            s = state["channel"]
            scale = min(c["w"] * 0.8 / mc_width(s), c["h"] * 0.8 / 7.0)
            mc_text(img, c["x"] + (c["w"] - mc_width(s) * scale) / 2.0, c["y"] + (c["h"] - 7 * scale) / 2.0 - scale, s, col(c["ink"]), scale)
    img = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
    img.save(path)


def main():
    mobile = mobile_layout()
    handheld = handheld_layout()
    save(mobile_panel(), "textures", "gui", "marine", "marine_radio.png")
    save(handheld_panel(), "textures", "gui", "marine", "marine_handheld.png")
    write_json(os.path.join(ROOT, "gui_layouts", "marine_radio.json"), mobile)
    write_json(os.path.join(ROOT, "gui_layouts", "marine_handheld.json"), handheld)
    save(block_front(), "textures", "block", "marine_radio_front.png")
    save(block_case(), "textures", "block", "marine_radio_case.png")
    write_json(os.path.join(ROOT, "models", "block", "marine_radio.json"), block_model())
    save(item_texture(), "textures", "item", "marine_handheld.png")
    write_json(os.path.join(ROOT, "models", "item", "marine_handheld.json"), item_model())
    print("Block lamps for RadioDisplays (x1, x2, y1, y2, front, tx):")
    for lamp in block_lamps():
        print("   ", lamp)

    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        preview_mobile(mobile, {"on": True, "channel": "22A", "plan": "USA", "flags": "LO DW", "name": "PORT OPS VTS",
                                "knobs": {"volume": 0.5, "squelch": 0.0, "channel": 0.2},
                                "switches": {"power": "ON", "hi_lo": "LO", "dual_watch": "ON"},
                                "lamps": {"rx_lamp": True}, "pressed": "key_16"}, os.path.join(out, "marine_radio_preview_a.png"))
        preview_mobile(mobile, {"on": True, "channel": "16", "plan": "INT", "flags": "HI", "name": "DISTRESS",
                                "knobs": {"volume": 1.0, "squelch": 0.7, "channel": 0.9},
                                "switches": {"power": "ON", "hi_lo": "HI", "dual_watch": "OFF"},
                                "lamps": {"tx_lamp": True}}, os.path.join(out, "marine_radio_preview_b.png"))
        # DSC (2026-11-09): an alert heard, cover lifted; and the radio's own call going out, cover shut.
        preview_mobile(mobile, {"on": True, "lines": ["DSC DISTRESS", "366123456 FIRE", "X -12345 Z 23456"],
                                "knobs": {"volume": 0.5, "squelch": 0.2, "channel": 0.5},
                                "switches": {"power": "ON", "hi_lo": "HI", "dual_watch": "ON"},
                                "lamps": {"rx_lamp": True}, "cover_open": True, "pressed": "key_ack"},
                       os.path.join(out, "marine_radio_preview_dsc_alert.png"))
        preview_mobile(mobile, {"on": True, "lines": ["DISTRESS SENT", "MAN OVERBOARD", "NEXT RPT 3:58"],
                                "knobs": {"volume": 0.5, "squelch": 0.2, "channel": 0.5},
                                "switches": {"power": "ON", "hi_lo": "HI", "dual_watch": "OFF"},
                                "lamps": {"tx_lamp": True}, "cover_open": False},
                       os.path.join(out, "marine_radio_preview_dsc_call.png"))
        preview_handheld(handheld, {"on": True, "channel": "22A", "volume": 0.4, "tx": True,
                                    "lit": {"hi_lo": True, "dual_watch": True}, "pressed": "key_up"},
                         os.path.join(out, "marine_handheld_preview_a.png"))
        preview_handheld(handheld, {"on": True, "channel": "WX1", "volume": 0.75, "lit": {}}, os.path.join(out, "marine_handheld_preview_b.png"))
        print("Previews in", out)


if __name__ == "__main__":
    main()
