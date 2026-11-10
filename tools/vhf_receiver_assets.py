"""Art for the VHF receiver (2026-11-09), after a 1970s eight-channel crystal scanner the maintainer picked: a low, wide
box in a black wraparound case with a carry bail over the top and a chrome and brushed-aluminium front. Top left, a
key box with a blue MAN/AUTO key, the red SET key and the SET lamp; top right, a smoked channel window with the red
numerals 1 to 8; under the key box the VOLUME (with OFF), SQUELCH and TUNE knobs (the real set has no tuning knob: TUNE
sits as a third knob in the row, where its lamp column was); under the window eight slide switches, up on and down
off, over a line marked ON and OFF. No brand names: the lettering says SCAN FM MONITOR and RADIOCRAFT.

Writes, under src/main/resources/assets/radiocraft/:
  textures/gui/vhf_receiver.png      the screen's panel (256 x 104), without its moving parts
  gui_layouts/vhf_receiver.json      where each control is and how to draw what moves (HfPanel's format, plus "key"
                                     for the latching keys and lamps with a "digit" for the channel numerals)
  textures/block/vhf_receiver.png    128x128 (8 px a model unit): the 96 x 32 front, then tiles for the case, knobs, bail
  models/block/vhf_receiver_hanging.json  the ceiling variant (raised, the whip pointing down; RadiocraftBlockstateProvider)
  models/block/vhf_receiver.json     the case, three knob cubes, the bail and the whip (a thin steel rod up to the top of
                                     the block from the back; not in the hitbox); no DISPLAY element (the block has no
                                     screen). VHFReceiverRenderer draws the switch handles, the lit numeral and the SET
                                     lamp over the front: its constants are FRONT_* below.

VHFReceiverScreen draws the panel PNG, then from the layout: knob pointers, the key caps out or in, the switch handles
up or down, the numerals (dim ones are painted; the lit one is drawn over, the same 3 x 5 glyph doubled) and the SET
lamp. The frequency shows above the panel as a tooltip-like label, never on the panel. To move a control, change it
here and rerun. Needs Pillow. Run: python tools/vhf_receiver_assets.py [--preview DIR]
(--preview writes the panel in a few states with the label above it, a front render of the block with its moving parts,
and the two side by side.)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import marine_radio_assets as mra  # noqa: E402  The 3 x 5 lettering, drawing helpers and the preview font.
from marine_radio_assets import rect, bevel, disc, text, text_centred, text_width, write_json, hexc, GLYPHS  # noqa: E402

ROOT = mra.ROOT

# ---- Colours ---------------------------------------------------------------------------------------------------------
CASE, CASE_LIGHT, CASE_DARK = (24, 24, 26, 255), (52, 52, 56, 255), (8, 8, 9, 255)
CHROME = [(236, 236, 238, 255), (206, 208, 212, 255), (160, 162, 168, 255), (122, 124, 130, 255), (178, 180, 186, 255), (222, 224, 228, 255)]
CHROME_EDGE = (84, 86, 92, 255)
ALU, ALU_LIGHT, ALU_DARK = (196, 197, 196, 255), (214, 215, 214, 255), (176, 177, 176, 255)
INK = (34, 34, 36, 255)
GLASS, GLASS_GLINT = (16, 8, 8, 255), (52, 26, 24, 255)
NUM_DIM, NUM_LIT, NUM_GLOW = (78, 20, 14, 255), (255, 74, 48, 255), (110, 22, 12, 255)
BLUE = ((48, 118, 196, 255), (110, 168, 228, 255), (24, 64, 120, 255))
RED = ((228, 74, 40, 255), (250, 140, 110, 255), (140, 36, 18, 255))
SLOT, SLOT_RIM = (14, 14, 16, 255), (110, 112, 118, 255)
HANDLE, HANDLE_SHADOW, HANDLE_CAP = (214, 216, 220, 255), (70, 72, 76, 255), (40, 40, 44, 255)
LAMP_OFF, LAMP_ON = (70, 18, 14, 255), (255, 48, 32, 255)
KNOB_DARK, KNOB_MID, KNOB_LIGHT, KNOB_SHINE = (92, 94, 100, 255), (168, 170, 176, 255), (214, 216, 222, 255), (246, 246, 250, 255)
POINTER = "#2A2A2E"


def brushed(d, x0, y0, x1, y1, base, light, dark, seed=0):
    """Brushed aluminium: horizontal streaks, the same each run."""
    rect(d, x0, y0, x1, y1, base)
    for y in range(y0, y1):
        for x in range(x0, x1):
            h = (x * 3 + y * 131 + seed * 17 + (x // 7) * (y % 5)) % 29
            if h == 0:
                d.point((x, y), fill=light)
            elif h == 1:
                d.point((x, y), fill=dark)
        if (y * 7 + seed) % 5 == 0:
            rect(d, x0, y, x1, y + 1, tuple((a + b) // 2 for a, b in zip(base[:3], light[:3])) + (255,))


def chrome(d, x0, y0, x1, y1):
    """Chrome trim: bright bands top and bottom, a dark reflection across the middle."""
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        t = (y - y0) / max(1, h - 1)
        k = min(len(CHROME) - 1, int(t * len(CHROME)))
        rect(d, x0, y, x1, y + 1, CHROME[k])


def glyph(img, x, y, ch, colour, scale=1):
    g = GLYPHS[ch]
    px = img.load()
    for gy in range(5):
        for gx in range(3):
            if g[gy * 3 + gx] == "1":
                for sy in range(scale):
                    for sx in range(scale):
                        X, Y = x + gx * scale + sx, y + gy * scale + sy
                        if 0 <= X < img.width and 0 <= Y < img.height:
                            px[X, Y] = colour


def scalloped_knob(d, cx, cy, r, scallops=12):
    """A chrome knob with a scalloped skirt, lit from the top left."""
    pts = []
    for k in range(scallops * 6):
        a = 2 * math.pi * k / (scallops * 6)
        bump = 0.9 * abs(math.cos(a * scallops / 2))
        rr = r - 1 + bump
        pts.append((cx - 0.5 + math.cos(a) * rr, cy - 0.5 + math.sin(a) * rr))
    d.polygon([(x, y + 1) for x, y in pts], fill=CASE_DARK)
    d.polygon(pts, fill=KNOB_DARK)
    disc(d, cx, cy, r - 2, KNOB_MID)
    disc(d, cx - 1, cy - 1, r - 4, KNOB_LIGHT)
    disc(d, cx, cy, r - 6, KNOB_MID)
    disc(d, cx - 2, cy - 3, max(2, r // 4), KNOB_SHINE)


# ======================================================================================================================
# The screen (256 x 104)
# ======================================================================================================================
G_W, G_H = 256, 104
G_KEYBOX = (8, 8, 102, 41)
G_WINDOW = (106, 8, 250, 41)
G_GLASS = (109, 11, 247, 38)
G_PLATE = (8, 44, 250, 97)
G_KEYS = [("key_man", "MAN", "AUTO", 30, BLUE), ("key_set", "SET", "", 62, RED)]   # name, label over, under, well x, colour
G_KEY_WELL_W, G_KEY_WELL_Y, G_KEY_WELL_H = 24, 17, 16
G_KEY_CAP_W, G_KEY_CAP_H, G_KEY_TRAVEL = 22, 9, 5
G_SET_LAMP = (90, 22, 6, 6)
G_KNOBS = [("volume", "VOLUME", 25), ("squelch", "SQUELCH", 57), ("tune", "TUNE", 89)]
G_KNOB_CY, G_KNOB_R = 64, 11
G_KNOB_LABEL_Y = 84
G_DIVIDERS = (41, 73, 105)
G_CH_X0, G_CH_PITCH = 126, 16           # channel 1's centre, and the pitch of the numerals and switches
G_NUM_Y = 19                            # numeral top (3 x 5 glyph at 2x: 6 x 10)
G_SLOT_Y = (50, 78)
G_HANDLE_ON_Y, G_HANDLE_OFF_Y, G_HANDLE_H = 52, 68, 8
G_LINE_Y = 63
G_LETTERING_Y = 83


def ch_x(i):
    return G_CH_X0 + G_CH_PITCH * i


def gui_panel():
    img = Image.new("RGBA", (G_W, G_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # The black wraparound case, a light lip along the top, then the chrome front inside it.
    d.rounded_rectangle([0, 0, G_W - 1, G_H - 1], radius=5, fill=CASE, outline=CASE_DARK)
    rect(d, 4, 1, G_W - 4, 3, CASE_LIGHT)
    chrome(d, 4, 4, G_W - 4, G_H - 4)
    bevel(d, 4, 4, G_W - 4, G_H - 4, CHROME[0], CHROME_EDGE)
    # The key box: a chrome frame round a brushed plate.
    x0, y0, x1, y1 = G_KEYBOX
    rect(d, x0, y0, x1, y1, CHROME_EDGE)
    brushed(d, x0 + 2, y0 + 2, x1 - 2, y1 - 2, ALU, ALU_LIGHT, ALU_DARK, 1)
    bevel(d, x0 + 1, y0 + 1, x1 - 1, y1 - 1, CHROME_EDGE, CHROME[0])
    for name, over, under, wx, colour in G_KEYS:
        rect(d, wx, G_KEY_WELL_Y, wx + G_KEY_WELL_W, G_KEY_WELL_Y + G_KEY_WELL_H, (30, 30, 34, 255))
        bevel(d, wx, G_KEY_WELL_Y, wx + G_KEY_WELL_W, G_KEY_WELL_Y + G_KEY_WELL_H, (16, 16, 18, 255), ALU_LIGHT)
        text_centred(img, wx + G_KEY_WELL_W / 2.0, 11, over, INK)
        if under:
            text_centred(img, wx + G_KEY_WELL_W / 2.0, 34, under, INK)
    # The SET lamp, dark (the screen lights it), in a chrome ring.
    lx, ly, lw, lh = G_SET_LAMP
    disc(d, lx + lw / 2.0, ly + lh / 2.0, lw / 2.0 + 2, CHROME_EDGE)
    disc(d, lx + lw / 2.0, ly + lh / 2.0, lw / 2.0 + 1, CHROME[1])
    disc(d, lx + lw / 2.0, ly + lh / 2.0, lw / 2.0, LAMP_OFF)
    # The channel window: a chrome frame, smoked glass, the numerals dim behind it (the screen lights the one in use).
    x0, y0, x1, y1 = G_WINDOW
    rect(d, x0, y0, x1, y1, CHROME_EDGE)
    chrome(d, x0 + 1, y0 + 1, x1 - 1, y1 - 1)
    gx0, gy0, gx1, gy1 = G_GLASS
    rect(d, gx0 - 1, gy0 - 1, gx1 + 1, gy1 + 1, CASE_DARK)
    rect(d, gx0, gy0, gx1, gy1, GLASS)
    rect(d, gx0, gy0, gx1, gy0 + 1, GLASS_GLINT)
    for i in range(8):
        glyph(img, ch_x(i) - 3, G_NUM_Y, str(i + 1), NUM_DIM, 2)
    # The lower plate: brushed, framed, with dividers between the knobs.
    x0, y0, x1, y1 = G_PLATE
    rect(d, x0, y0, x1, y1, CHROME_EDGE)
    brushed(d, x0 + 2, y0 + 2, x1 - 2, y1 - 2, ALU, ALU_LIGHT, ALU_DARK, 2)
    bevel(d, x0 + 1, y0 + 1, x1 - 1, y1 - 1, CHROME_EDGE, CHROME[0])
    for x in G_DIVIDERS:
        rect(d, x, y0 + 4, x + 1, y1 - 4, INK)
    # The knobs (the screen draws their pointers), a ring of scale dots, and the labels under them.
    for kname, label, kx in G_KNOBS:
        for k in range(11):
            a = math.radians(-135 + 27 * k)
            px, py = kx - 0.5 + math.sin(a) * (G_KNOB_R + 3), G_KNOB_CY - 0.5 - math.cos(a) * (G_KNOB_R + 3)
            d.point((round(px), round(py)), fill=INK)
        scalloped_knob(d, kx, G_KNOB_CY, G_KNOB_R)
        text_centred(img, kx, G_KNOB_LABEL_Y, label, INK)
    text(img, 11, 76, "OFF", INK)  # The volume knob's OFF click, at its far left.
    # The ON / OFF line across the switches, the slots over it, and the lettering under them.
    rect(d, 107, G_LINE_Y, 247, G_LINE_Y + 1, INK)
    text(img, 109, G_HANDLE_ON_Y + 2, "ON", INK)
    text(img, 107, G_HANDLE_OFF_Y + 2, "OFF", INK)
    for i in range(8):
        cx = ch_x(i)
        d.rounded_rectangle([cx - 5, G_SLOT_Y[0] - 1, cx + 5, G_SLOT_Y[1]], radius=4, fill=SLOT_RIM)
        d.rounded_rectangle([cx - 4, G_SLOT_Y[0], cx + 4, G_SLOT_Y[1] - 1], radius=3, fill=SLOT)
    text(img, ch_x(0) - 4, G_LETTERING_Y, "SCAN FM MONITOR", INK)
    text(img, 246 - text_width("RADIOCRAFT"), G_LETTERING_Y, "RADIOCRAFT", INK)
    return img


def gui_layout():
    controls = []
    for name, over, under, wx, colour in G_KEYS:
        cap_x = wx + (G_KEY_WELL_W - G_KEY_CAP_W) // 2
        out_y = G_KEY_WELL_Y + 1
        controls.append({"name": name, "type": "key", "x": wx, "y": G_KEY_WELL_Y, "w": G_KEY_WELL_W, "h": G_KEY_WELL_H,
                         "positions": ["OUT", "IN"],
                         "handles": [[cap_x, out_y, G_KEY_CAP_W, G_KEY_CAP_H], [cap_x, out_y + G_KEY_TRAVEL, G_KEY_CAP_W, G_KEY_CAP_H]],
                         "cap_colour": hexc(colour[0]), "cap_light": hexc(colour[1]), "cap_dark": hexc(colour[2])})
    lx, ly, lw, lh = G_SET_LAMP
    controls.append({"name": "set_lamp", "type": "lamp", "x": lx, "y": ly, "w": lw, "h": lh, "colour": hexc(LAMP_ON)})
    for i in range(8):
        cx = ch_x(i)
        controls.append({"name": "numeral_%d" % (i + 1), "type": "lamp", "x": cx - 4, "y": G_NUM_Y - 1, "w": 8, "h": 12,
                         "digit": i + 1, "glyph_x": cx - 3, "glyph_y": G_NUM_Y, "glyph_scale": 2,
                         "colour": hexc(NUM_LIT), "glow": hexc(NUM_GLOW)})
    for i in range(8):
        cx = ch_x(i)
        controls.append({"name": "switch_%d" % (i + 1), "type": "switch", "x": cx - 5, "y": G_SLOT_Y[0] - 1, "w": 11,
                         "h": G_SLOT_Y[1] - G_SLOT_Y[0] + 1, "positions": ["OFF", "ON"],
                         "handles": [[cx - 3, G_HANDLE_OFF_Y, 7, G_HANDLE_H], [cx - 3, G_HANDLE_ON_Y, 7, G_HANDLE_H]],
                         "handle_colour": hexc(HANDLE), "handle_shadow": hexc(HANDLE_SHADOW), "cap_colour": hexc(HANDLE_CAP)})
    r = G_KNOB_R
    for kname, label, kx in G_KNOBS:
        c = {"name": kname, "type": "knob", "x": kx - r, "y": G_KNOB_CY - r, "w": 2 * r, "h": 2 * r, "cx": kx - 0.5,
             "cy": G_KNOB_CY - 0.5, "pointer_from": 3, "pointer_r": r - 2, "pointer_width": 1, "pointer_colour": POINTER,
             "min_deg": -135, "max_deg": 135}
        if kname == "volume":
            c["on_min_deg"] = -108  # Switched on, the knob turns from here up; at -135 it sits on OFF.
        controls.append(c)
    return {"texture": "radiocraft:textures/gui/vhf_receiver.png", "width": G_W, "height": G_H, "controls": controls}


# ======================================================================================================================
# The block (128x128 texture, 8 px a model unit; front 96 x 32 = 12 x 4 units)
# ======================================================================================================================
# The case: model x 2-14, y 0-4, z 4-14; the front is its north face. Front texture x runs from the viewer's left (east,
# model x 14): px = (14 - x) * 8; y from the top: py = (4 - y) * 8. VHFReceiverRenderer has these as FRONT_*.
CASE_BOX = ((2.0, 0.0, 4.0), (14.0, 4.0, 14.0))
FRONT_PX = (0, 0, 96, 32)
CASE_TILE = (96, 0, 128, 32)
KNOB_FACE = (96, 32, 104, 40)
KNOB_SIDE = (104, 32, 112, 40)
BAIL_TILE = (112, 32, 120, 40)
WHIP_TILE = (120, 32, 128, 40)
# The whip: a 0.5 unit square steel rod from a 1 x 0.5 x 1 mount on the case's top, back right as seen from the front
# (model x 3-4, z 12-13), up to the top of the block (y 16). Hanging from a ceiling it points down instead (block_model_hanging).
WHIP_MOUNT = ([3.0, 4.0, 12.0], [4.0, 4.5, 13.0])
WHIP = ([3.25, 4.5, 12.25], [3.75, 16.0, 12.75])
#: How high the radio itself reaches (the bail's bar), without the whip: what hanging from a ceiling lines up.
BODY_TOP = 5.0
B_KEYBOX = (3, 3, 37, 14)
B_WINDOW = (39, 3, 93, 14)
B_GLASS = (40, 4, 92, 13)
B_PLATE = (3, 16, 93, 30)
B_KEYS = [(9, BLUE), (21, RED)]        # well x (10 wide)
B_SET_LAMP = (32, 8, 2, 2)              # VHFReceiverRenderer.SET_LAMP
B_NUM_X0, B_NUM_PITCH, B_NUM_Y = 45, 6, 6  # numeral i's glyph left is 45 + 6i: VHFReceiverRenderer.NUMERAL_*
B_KNOBS = [9, 19, 29]                   # knob centres; 6 x 6 cubes
B_KNOB_Y = 19                           # knob top (6 px)
B_SLOT_Y = (18, 29)
B_HANDLE_ON_Y, B_HANDLE_OFF_Y, B_HANDLE_H = 19, 25, 3  # VHFReceiverRenderer.HANDLE_*
B_LINE_Y = 23


def block_texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # The front: a black rim (the case wraps round it), the chrome face inside.
    rect(d, 0, 0, 96, 32, CASE)
    chrome(d, 1, 1, 95, 31)
    x0, y0, x1, y1 = B_KEYBOX
    rect(d, x0, y0, x1, y1, CHROME_EDGE)
    brushed(d, x0 + 1, y0 + 1, x1 - 1, y1 - 1, ALU, ALU_LIGHT, ALU_DARK, 3)
    for wx, colour in B_KEYS:
        rect(d, wx, 5, wx + 10, 12, (30, 30, 34, 255))
        rect(d, wx + 1, 6, wx + 9, 9, colour[0])        # The caps standing out (the in-world keys do not move).
        rect(d, wx + 1, 6, wx + 9, 7, colour[1])
        for x in range(wx + 2, wx + 9, 2):
            rect(d, x, 7, x + 1, 9, colour[2])
    lx, ly, lw, lh = B_SET_LAMP
    rect(d, lx - 1, ly - 1, lx + lw + 1, ly + lh + 1, CHROME_EDGE)
    rect(d, lx, ly, lx + lw, ly + lh, LAMP_OFF)
    x0, y0, x1, y1 = B_WINDOW
    rect(d, x0, y0, x1, y1, CHROME_EDGE)
    gx0, gy0, gx1, gy1 = B_GLASS
    rect(d, gx0, gy0, gx1, gy1, GLASS)
    rect(d, gx0, gy0, gx1, gy0 + 1, GLASS_GLINT)
    for i in range(8):
        glyph(img, B_NUM_X0 + B_NUM_PITCH * i, B_NUM_Y, str(i + 1), NUM_DIM)
    x0, y0, x1, y1 = B_PLATE
    rect(d, x0, y0, x1, y1, CHROME_EDGE)
    brushed(d, x0 + 1, y0 + 1, x1 - 1, y1 - 1, ALU, ALU_LIGHT, ALU_DARK, 4)
    for x in (14, 24, 34):
        rect(d, x, y0 + 2, x + 1, y1 - 2, INK)
    for kx in B_KNOBS:  # The knobs' shadows (the knobs are cubes in front).
        rect(d, kx - 3, B_KNOB_Y + 1, kx + 4, B_KNOB_Y + 7, (110, 110, 114, 255))
    rect(d, 36, B_LINE_Y, 92, B_LINE_Y + 1, INK)
    for i in range(8):
        x = B_NUM_X0 + B_NUM_PITCH * i
        rect(d, x - 1, B_SLOT_Y[0] - 1, x + 4, B_SLOT_Y[1] + 1, SLOT_RIM)
        rect(d, x, B_SLOT_Y[0], x + 3, B_SLOT_Y[1], SLOT)
    # Tiles: the case's black vinyl, the knobs' face and skirt, the bail's chrome.
    rect(d, *CASE_TILE, CASE)
    for y in range(CASE_TILE[1], CASE_TILE[3]):
        for x in range(CASE_TILE[0], CASE_TILE[2]):
            if (x * 5 + y * 11) % 13 == 0:
                d.point((x, y), fill=(34, 34, 37, 255))
    rect(d, CASE_TILE[0], CASE_TILE[1], CASE_TILE[2], CASE_TILE[1] + 1, CASE_LIGHT)
    rect(d, *KNOB_FACE, KNOB_DARK)
    disc(d, KNOB_FACE[0] + 4, KNOB_FACE[1] + 4, 4, KNOB_MID)
    disc(d, KNOB_FACE[0] + 4, KNOB_FACE[1] + 4, 3, KNOB_LIGHT)
    rect(d, KNOB_FACE[0] + 2, KNOB_FACE[1] + 2, KNOB_FACE[0] + 4, KNOB_FACE[1] + 4, KNOB_SHINE)
    rect(d, KNOB_FACE[0] + 4, KNOB_FACE[1] + 1, KNOB_FACE[0] + 5, KNOB_FACE[1] + 4, (40, 40, 44, 255))  # The pointer.
    rect(d, *KNOB_SIDE, KNOB_DARK)
    for x in range(KNOB_SIDE[0], KNOB_SIDE[2], 2):
        rect(d, x, KNOB_SIDE[1], x + 1, KNOB_SIDE[3], KNOB_LIGHT)
    chrome(d, *BAIL_TILE)
    rect(d, *WHIP_TILE, (150, 154, 160, 255))  # Steel: a light edge and a dark one, so the thin rod reads round.
    rect(d, WHIP_TILE[0], WHIP_TILE[1], WHIP_TILE[0] + 3, WHIP_TILE[3], (206, 210, 216, 255))
    rect(d, WHIP_TILE[2] - 2, WHIP_TILE[1], WHIP_TILE[2], WHIP_TILE[3], (96, 98, 104, 255))
    return img


def uv(box):
    return [box[0] / 8.0, box[1] / 8.0, box[2] / 8.0, box[3] / 8.0]


def faces(tile, only=None):
    return {f: {"uv": uv(tile), "texture": "#0"} for f in (only or ("north", "east", "south", "west", "up", "down"))}


def block_model():
    (x0, y0, z0), (x1, y1, z1) = CASE_BOX
    body = faces(CASE_TILE)
    body["north"] = {"uv": uv(FRONT_PX), "texture": "#0"}
    elements = [{"name": "case", "from": [x0, y0, z0], "to": [x1, y1, z1], "faces": body}]
    for name, kx in zip(("knob_volume", "knob_squelch", "knob_tune"), B_KNOBS):
        knob = faces(KNOB_SIDE, ("east", "west", "up", "down", "south"))
        knob["north"] = {"uv": uv(KNOB_FACE), "texture": "#0"}
        mx1, mx0 = 14 - (kx - 3) / 8.0, 14 - (kx + 3) / 8.0
        my1, my0 = 4 - B_KNOB_Y / 8.0, 4 - (B_KNOB_Y + 6) / 8.0
        elements.append({"name": name, "from": [mx0, my0, 3.4], "to": [mx1, my1, 4.0], "faces": knob})
    # The bail: an arm up each side from a pivot halfway back, and the bar over the top running between the arms (not
    # over them: their tops would share a plane with it and flicker, 2026-11-09).
    elements.append({"name": "bail_left", "from": [1.5, 1.5, 8.5], "to": [2.0, 5.0, 9.5], "faces": faces(BAIL_TILE)})
    elements.append({"name": "bail_right", "from": [14.0, 1.5, 8.5], "to": [14.5, 5.0, 9.5], "faces": faces(BAIL_TILE)})
    elements.append({"name": "bail_bar", "from": [2.0, 4.5, 8.5], "to": [14.0, 5.0, 9.5], "faces": faces(BAIL_TILE)})
    # The whip (2026-11-09): a thin steel rod from a small mount on the case's back right corner, straight up to the top
    # of the block. ReceiverWhipAntenna hears through it; it is not in the hitbox.
    elements.append({"name": "whip_mount", "from": WHIP_MOUNT[0], "to": WHIP_MOUNT[1], "faces": faces(CASE_TILE)})
    elements.append({"name": "whip", "from": WHIP[0], "to": WHIP[1], "faces": faces(WHIP_TILE)})
    return {
        "credit": "Made with tools/vhf_receiver_assets.py (after a 1970s eight-channel crystal scanner)",
        "parent": "block/block",
        "texture_size": [128, 128],
        "textures": {"0": "radiocraft:block/vhf_receiver", "particle": "radiocraft:block/vhf_receiver"},
        "elements": elements,
    }


def block_model_hanging():
    """The ceiling variant: everything raised so the bail's bar meets the block above (as the datagen's other ceiling
    models are, by 16 - BODY_TOP), and the whip turned to hang down from a mount under the case instead of poking up
    into the ceiling. RadiocraftBlockstateProvider uses it for the receiver's ceiling states."""
    model = block_model()
    rise = 16.0 - BODY_TOP
    elements = []
    for e in model["elements"]:
        e = json.loads(json.dumps(e))
        if e["name"] == "whip_mount":
            e["from"], e["to"] = [WHIP_MOUNT[0][0], -0.5, WHIP_MOUNT[0][2]], [WHIP_MOUNT[1][0], 0.0, WHIP_MOUNT[1][2]]
        elif e["name"] == "whip":
            e["from"], e["to"] = [WHIP[0][0], -rise, WHIP[0][2]], [WHIP[1][0], -0.5, WHIP[1][2]]
        e["from"][1] += rise
        e["to"][1] += rise
        elements.append(e)
    model["elements"] = elements
    model["credit"] = model["credit"] + "; hanging from a ceiling, the whip down"
    return model


FACE_AXIS = {"east": (0, 1), "west": (0, 0), "up": (1, 1), "down": (1, 0), "south": (2, 1), "north": (2, 0)}


def coplanar_overlaps(model):
    """Pairs of faces that point the same way in the same plane and overlap: they flicker (z-fight) in game."""
    out = []
    els = model["elements"]
    for i, a in enumerate(els):
        for b in els[i + 1:]:
            for face, (axis, high) in FACE_AXIS.items():
                if face not in a["faces"] or face not in b["faces"]:
                    continue
                pa = a["to"][axis] if high else a["from"][axis]
                pb = b["to"][axis] if high else b["from"][axis]
                if abs(pa - pb) > 1e-6:
                    continue
                others = [k for k in range(3) if k != axis]
                if all(min(a["to"][k], b["to"][k]) - max(a["from"][k], b["from"][k]) > 1e-6 for k in others):
                    out.append((a["name"], b["name"], face, pa))
    return out


# ======================================================================================================================
# Previews
# ======================================================================================================================
BG = (198, 204, 212, 255)
HINT_BG, HINT_EDGE, HINT_INK = (16, 0, 16, 240), (80, 0, 255, 255), (255, 255, 255, 255)


def lighter(c):
    return tuple((v + 255) // 2 for v in c[:3]) + (255,)


def preview_gui(state, path):
    """The panel as VHFReceiverScreen draws it, with the frequency label above it (state["hint"])."""
    layout = gui_layout()
    top = 22
    img = Image.new("RGBA", (G_W, G_H + top), BG)
    img.alpha_composite(gui_panel(), (0, top))
    d = ImageDraw.Draw(img)
    on = state.get("on", True)
    enabled = state.get("enabled", [])
    for c in layout["controls"]:
        t = c["type"]
        x, y = c["x"], c["y"] + top
        if t == "knob":
            v = state["knobs"][c["name"]]
            lo = c["min_deg"] if (c["name"] != "volume" or not on) else c["on_min_deg"]
            deg = c["min_deg"] if (c["name"] == "volume" and not on) else lo + v * (c["max_deg"] - lo)
            mra.ray(img, c["cx"], c["cy"] + top, deg, c["pointer_from"], c["pointer_r"], mra.col(c["pointer_colour"]))
        elif t == "key":
            pressed = state.get("keys", {}).get(c["name"], False)
            hx, hy, hw, hh = c["handles"][1 if pressed else 0]
            hy += top
            face, light, dark = mra.col(c["cap_colour"]), mra.col(c["cap_light"]), mra.col(c["cap_dark"])
            if pressed:
                face, light = tuple(int(v * 0.8) for v in face[:3]) + (255,), face
            rect(d, hx, hy, hx + hw, hy + hh, face)
            rect(d, hx, hy, hx + hw, hy + 1, light)
            rect(d, hx, hy + hh - 1, hx + hw, hy + hh, dark)
            for rx in range(hx + 2, hx + hw - 1, 3):
                rect(d, rx, hy + 2, rx + 1, hy + hh - 2, dark)
        elif t == "switch":
            i = int(c["name"].split("_")[1]) - 1
            hx, hy, hw, hh = c["handles"][1 if i in enabled else 0]
            hy += top
            rect(d, hx, hy, hx + hw, hy + hh, mra.col(c["handle_shadow"]))
            rect(d, hx, hy, hx + hw - 1, hy + hh - 1, mra.col(c["handle_colour"]))
            rect(d, hx + 2, hy + 2, hx + hw - 3, hy + hh - 3, mra.col(c["cap_colour"]))
        elif t == "lamp" and c["name"] == "set_lamp" and on and state.get("set_lit"):
            col = mra.col(c["colour"])
            disc(d, x + c["w"] / 2.0, y + c["h"] / 2.0, c["w"] / 2.0, col)
            disc(d, x + c["w"] / 2.0 - 1, y + c["h"] / 2.0 - 1, 1.5, lighter(col))
        elif t == "lamp" and "digit" in c and on and state.get("lit") == c["digit"] - 1:
            s, gx, gy = c["glyph_scale"], c["glyph_x"], c["glyph_y"] + top
            g = GLYPHS[str(c["digit"])]
            glow = mra.col(c["glow"])
            for py in range(5):
                for px in range(3):
                    if g[py * 3 + px] == "1":
                        rect(d, gx + px * s - 1, gy + py * s - 1, gx + px * s + s + 1, gy + py * s + s + 1, glow)
            glyph(img, gx, gy, str(c["digit"]), mra.col(c["colour"]), s)
    hint = state.get("hint")
    if hint:  # The tooltip-like label, centred over the panel (VHFReceiverScreen.renderHint).
        w = mra.mc_width(hint)
        hx = (G_W - w) // 2
        rect(d, hx - 4, 4, hx + w + 4, 17, HINT_BG)
        d.rectangle([hx - 4, 4, hx + w + 3, 16], outline=HINT_EDGE)
        mra.mc_text(img, hx, 6, hint, HINT_INK)
    img = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
    img.save(path)


def render_front(model, texture, state, scale=24):
    """The block seen straight from the front (north), with what VHFReceiverRenderer draws over it."""
    w = h = 16 * scale
    out = Image.new("RGBA", (w, h), BG)
    els = sorted(model["elements"], key=lambda e: -min(e["from"][2], e["to"][2]))
    for e in els:
        f = e["faces"].get("north")
        if not f:
            continue
        u0, v0, u1, v1 = [c * 8 for c in f["uv"]]
        tile = texture.crop((int(u0), int(v0), int(u1), int(v1)))
        x0, x1 = 16 - e["to"][0], 16 - e["from"][0]
        y0, y1 = 16 - e["to"][1], 16 - e["from"][1]
        tile = tile.resize((max(1, round((x1 - x0) * scale)), max(1, round((y1 - y0) * scale))), Image.NEAREST)
        out.alpha_composite(tile, (round(x0 * scale), round(y0 * scale)))
    (cx0, cy0, cz0), (cx1, cy1, cz1) = CASE_BOX
    ox, oy = (16 - cx1) * scale, (16 - cy1) * scale  # The front's top left on screen.
    k = scale / 8.0
    d = ImageDraw.Draw(out)

    def px(x, y, ww, hh, colour):
        rect(d, round(ox + x * k), round(oy + y * k), round(ox + (x + ww) * k), round(oy + (y + hh) * k), colour)

    for i in range(8):
        x = B_NUM_X0 + B_NUM_PITCH * i
        hy = B_HANDLE_ON_Y if i in state.get("enabled", []) else B_HANDLE_OFF_Y
        px(x, hy, 3, B_HANDLE_H, HANDLE)
        px(x, hy + B_HANDLE_H - 1, 3, 1, HANDLE_SHADOW)
        if state.get("on") and state.get("lit") == i:
            g = GLYPHS[str(i + 1)]
            for gy in range(5):
                for gx in range(3):
                    if g[gy * 3 + gx] == "1":
                        px(x + gx, B_NUM_Y + gy, 1, 1, NUM_LIT)
    if state.get("on") and state.get("set_lit"):
        px(*B_SET_LAMP, LAMP_ON)
    return out.crop((round(1 * scale), 0, round(15 * scale), 16 * scale))


def main():
    for old, new in ((("textures", "block", "vhf_receiver.png"), ("textures", "block", "vhf_receiver_old.png")),
                     (("textures", "gui", "vhf_receiver.png"), ("textures", "gui", "vhf_receiver_old.png"))):
        src, dst = os.path.join(ROOT, *old), os.path.join(ROOT, *new)
        if os.path.exists(src) and not os.path.exists(dst):  # The pre-scanner art, kept once.
            Image.open(src).save(dst)
    tex = block_texture()
    mra.save(tex, "textures", "block", "vhf_receiver.png")
    model = block_model()
    write_json(os.path.join(ROOT, "models", "block", "vhf_receiver.json"), model)
    hanging = block_model_hanging()
    write_json(os.path.join(ROOT, "models", "block", "vhf_receiver_hanging.json"), hanging)
    for name, m in (("vhf_receiver", model), ("vhf_receiver_hanging", hanging)):
        for a, b, face, plane in coplanar_overlaps(m):
            print("Z-FIGHT in %s: %s and %s, %s faces at %.3f" % (name, a, b, face, plane))
    mra.save(gui_panel(), "textures", "gui", "vhf_receiver.png")
    write_json(os.path.join(ROOT, "gui_layouts", "vhf_receiver.json"), gui_layout())

    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        knobs = {"volume": 0.5, "squelch": 0.3, "tune": 0.42}
        scanning = {"on": True, "enabled": [0, 2, 3, 6], "lit": 3, "knobs": knobs, "keys": {"key_man": True}, "hint": "SCAN"}
        stopped = {"on": True, "enabled": [0, 2, 3, 6], "lit": 2, "knobs": knobs, "keys": {"key_man": True},
                   "hint": "CH 3 · 156.800 MHz"}
        setting = {"on": True, "enabled": [2], "lit": 2, "set_lit": True, "knobs": dict(knobs, tune=(156.8 - 144) / 30.0),
                   "keys": {"key_man": True, "key_set": True}, "hint": "SET CH 3 · 156.800 MHz"}
        set_blocked = {"on": True, "enabled": [0, 2], "lit": 0, "knobs": knobs, "keys": {"key_man": True, "key_set": True},
                       "hint": "SET: switch on just one channel"}
        off = {"on": False, "enabled": [0, 1], "knobs": {"volume": 0.0, "squelch": 0.0, "tune": 0.0}, "keys": {"key_man": True}}
        preview_gui(scanning, os.path.join(out, "vhf_receiver_gui_scanning.png"))
        preview_gui(stopped, os.path.join(out, "vhf_receiver_gui_stopped.png"))
        preview_gui(setting, os.path.join(out, "vhf_receiver_gui_set.png"))
        preview_gui(set_blocked, os.path.join(out, "vhf_receiver_gui_set_blocked.png"))
        preview_gui(off, os.path.join(out, "vhf_receiver_gui_off.png"))
        front = render_front(model, tex, stopped)
        front.save(os.path.join(out, "vhf_receiver_block_front.png"))
        render_front(model, tex, setting).save(os.path.join(out, "vhf_receiver_block_front_set.png"))
        panel = Image.open(os.path.join(out, "vhf_receiver_gui_stopped.png")).convert("RGBA")
        f = front.resize((panel.width, int(front.height * panel.width / front.width)), Image.NEAREST)
        both = Image.new("RGBA", (panel.width, f.height + panel.height + 12), BG)
        both.alpha_composite(f, (0, 0))
        both.alpha_composite(panel, (0, f.height + 12))
        both.save(os.path.join(out, "vhf_receiver_compare.png"))
        print("Previews in", out)


if __name__ == "__main__":
    main()
