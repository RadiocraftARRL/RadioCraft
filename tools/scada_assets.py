"""Art for the SCADA devices (2026-11-11), after two photos the maintainer picked: a 1970s mainframe operator's console
(a grey CRT terminal with a deep, recessed, rounded green-grey screen, and beside it a dark charcoal control panel:
a meter window, chrome rotary switches, rows of small toggles, a hex keypad and coloured square push buttons) for the
SCADA master, and a data radio (a finned box with a label on top and a front plate with PWR DCD TXD RXD lamps, a DB25
data port, a DIAG jack, a 13.8 V socket and an N antenna connector), made a little older, for the RTU.

Writes, under src/main/resources/assets/radiocraft/:
  textures/block/scada_master.png          128x128 (4 px a model unit, UV = px / 8): the control panel's front, sides
  textures/block/scada_master_screen.png   128x128: the CRT terminal's bezel, glass, recess and case
  textures/block/scada_rtu.png             128x128: the RTU's front plate, top label, sides and fins
  models/block/scada_master.json           the control panel, the master's main block (redstone comes out of it)
  models/block/scada_master_screen.json    the CRT half; it stands on the panel's east (+x) side, which is the
                                           viewer's LEFT as they face the front (front faces north, as all models)
  models/block/scada_rtu.json              the RTU
  models/item/scada_master.json, models/item/scada_rtu.json
  textures/gui/scada/scada_master.png      256 x 144: the master's screen, without its moving parts
  gui_layouts/scada_master.json            where each control is and how to draw what moves (HfPanel's format,
                                           tools/hf_gui_proposals/RUNTIME_SPEC.md)

No blockstates: the parent code's blockstate (part=panel|screen) picks the two halves. To move a control, change it
here and rerun. Needs Pillow. Run: python tools/scada_assets.py [--preview DIR]
(--preview also writes the screen with its moving parts in two states, and front views of the three blocks.)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "radiocraft")

# ---- A 3 x 5 pixel font (as tools/marine_radio_assets.py) -----------------------------------------------------------
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


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


# ---- Palette ---------------------------------------------------------------------------------------------------------
CHARCOAL, CHARCOAL_LIGHT, CHARCOAL_DARK = (34, 40, 42, 255), (60, 68, 70, 255), (16, 20, 21, 255)
LABEL_BAR = (120, 128, 132, 255)            # the grey-blue engraved bars over each group on the console
ENGRAVE = (196, 202, 204, 255)
BEZEL, BEZEL_LIGHT, BEZEL_DARK = (98, 100, 98, 255), (128, 130, 128, 255), (66, 68, 66, 255)
FUNNEL_TOP, FUNNEL_SIDE, FUNNEL_BOTTOM = (78, 80, 78, 255), (90, 92, 90, 255), (118, 120, 118, 255)
GLASS, GLASS_MID, GLASS_EDGE = (40, 52, 48, 255), (52, 66, 60, 255), (26, 34, 31, 255)
CHROME, CHROME_LIGHT, CHROME_DARK = (176, 180, 182, 255), (222, 226, 228, 255), (110, 114, 116, 255)
SKIRT = (22, 24, 26, 255)
METER_FACE, METER_INK = (228, 226, 214, 255), (40, 40, 40, 255)
BAT, BAT_SHADOW = (214, 216, 218, 255), (90, 94, 96, 255)
KEY_LIGHT, KEY_LIGHT_DARK = (222, 220, 210, 255), (150, 148, 140, 255)
PUSH = [(60, 130, 210, 255), (60, 130, 210, 255), (200, 50, 46, 255), (220, 60, 40, 255),
        (60, 130, 210, 255), (90, 150, 210, 255), (60, 130, 210, 255), (170, 210, 90, 255)]
PUTTY, PUTTY_LIGHT, PUTTY_DARK = (186, 182, 168, 255), (206, 202, 188, 255), (140, 136, 124, 255)
ALU, ALU_LIGHT, ALU_DARK = (200, 202, 200, 255), (226, 228, 226, 255), (150, 152, 150, 255)
FIN, FIN_DARK = (70, 72, 74, 255), (44, 46, 48, 255)
LAMP_DARK_RED = (96, 24, 20, 255)
SOCKET = (20, 20, 22, 255)


def uv(x0, y0, x1, y1):
    """Texture pixels (128 wide) to model UV (0-16)."""
    return [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]


def faces(spec):
    """{face: (pixel rect, texture key)} to model faces."""
    return {f: {"uv": uv(*r), "texture": t} for f, (r, t) in spec.items()}


def all_faces(r, t):
    return faces({f: (r, t) for f in ("north", "east", "south", "west", "up", "down")})


# ======================================================================================================================
# The master's control panel block (the main block)
# ======================================================================================================================
P_FRONT = (0, 0, 64, 64)       # 16 x 16 units at 4 px
P_SIDE = (64, 0, 128, 64)
P_TOP = (0, 64, 64, 128)
P_KNOB = (64, 64, 72, 72)
P_KNOB_TOP = (72, 64, 80, 72)
P_KNOBS_PX = [(43, 7), (54, 7)]  # top left (front texture px) of each 4 x 4 rotary knob


def panel_front(sheet):
    d = ImageDraw.Draw(sheet)
    rect(d, 0, 0, 64, 64, CHARCOAL)
    # Plinth and top trim.
    rect(d, 0, 0, 64, 2, CHARCOAL_DARK)
    rect(d, 0, 60, 64, 64, (10, 12, 12, 255))
    # Group dividers: the console is three framed sections.
    for y in (21, 41):
        rect(d, 0, y, 64, y + 1, CHARCOAL_DARK)
        rect(d, 0, y + 1, 64, y + 2, CHARCOAL_LIGHT)
    # Engraved label bars over each group.
    for x0, x1, y in ((3, 22, 3), (26, 60, 3), (3, 60, 23), (3, 30, 43), (34, 60, 43)):
        rect(d, x0, y, x1, y + 1, LABEL_BAR)
    # Top section: the meter window and two rotary switches with a row of toggles.
    rect(d, 4, 6, 21, 15, (12, 14, 14, 255))
    rect(d, 5, 7, 20, 14, METER_FACE)
    for i in range(6):
        a = math.radians(-50 + i * 20)
        x, y = 12.5 + 6 * math.sin(a), 14 - 6 * math.cos(a)
        sheet.putpixel((int(x), int(y)), METER_INK)
    d.line([(12, 13), (8, 9)], fill=(160, 30, 30, 255))
    for x, y in P_KNOBS_PX:  # Skirts round the knob cubes.
        rect(d, x - 1, y - 1, x + 5, y + 5, SKIRT)
    for i in range(6):
        x = 27 + i * 3
        rect(d, x, 14, x + 1, 18, (8, 8, 8, 255))
        rect(d, x, 14, x + 1, 16, BAT)
    # Middle section: two rows of toggles and a small counter.
    for row in range(2):
        for i in range(12):
            x = 4 + i * 4 + (3 if i >= 6 else 0)
            y = 27 + row * 7
            rect(d, x, y, x + 1, y + 4, (8, 8, 8, 255))
            rect(d, x, y + (0 if (i + row) % 3 else 2), x + 1, y + (2 if (i + row) % 3 else 4), BAT)
    rect(d, 55, 27, 61, 31, (10, 10, 10, 255))
    for i in range(3):
        rect(d, 55 + i * 2, 28, 56 + i * 2, 30, (226, 222, 200, 255))
    # Bottom section: the hex keypad and the coloured push buttons.
    for row in range(4):
        for col in range(4):
            x, y = 5 + col * 6, 46 + row * 3
            rect(d, x, y, x + 4, y + 2, KEY_LIGHT)
            rect(d, x, y + 1, x + 4, y + 2, KEY_LIGHT_DARK)
    for i, c in enumerate(PUSH):
        x, y = 35 + (i % 4) * 6, 46 + (i // 4) * 6
        rect(d, x, y, x + 5, y + 4, c)
        rect(d, x, y + 3, x + 5, y + 4, shade(c, 0.6))
        rect(d, x, y, x + 5, y + 1, shade(c, 1.25))
    rect(d, 0, 0, 1, 64, CHARCOAL_LIGHT)  # The seam edge on the viewer's right is dark, the left meets the CRT.


def panel_texture():
    sheet = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    panel_front(sheet)
    d = ImageDraw.Draw(sheet)
    rect(d, *P_SIDE, CHARCOAL)
    bevel(d, *P_SIDE, CHARCOAL_LIGHT, CHARCOAL_DARK)
    rect(d, 64, 60, 128, 64, (10, 12, 12, 255))
    for y in range(10, 56, 3):  # Louvres on the side.
        rect(d, 74, y, 118, y + 1, CHARCOAL_DARK)
    rect(d, *P_TOP, CHARCOAL_LIGHT)
    bevel(d, *P_TOP, (84, 92, 94, 255), CHARCOAL)
    rect(d, *P_KNOB, CHROME)
    for x in range(P_KNOB[0], P_KNOB[2], 2):  # Knurling.
        rect(d, x, P_KNOB[1], x + 1, P_KNOB[3], CHROME_DARK)
    rect(d, *P_KNOB_TOP, CHROME_LIGHT)
    rect(d, 74, 66, 78, 70, CHROME)
    rect(d, 75, 67, 76, 68, (60, 60, 60, 255))
    return sheet


def panel_model():
    elements = [
        {"name": "cabinet", "from": [0, 0, 1], "to": [16, 16, 16], "faces": faces({
            "north": (P_FRONT, "#0"), "east": (P_SIDE, "#0"), "south": (P_SIDE, "#0"), "west": (P_SIDE, "#0"),
            "up": (P_TOP, "#0"), "down": (P_TOP, "#0")})},
    ]
    for i, (x, y) in enumerate(P_KNOBS_PX):
        fx0, fx1 = 16 - (x + 4) / 4.0, 16 - x / 4.0
        fy0, fy1 = 16 - (y + 4) / 4.0, 16 - y / 4.0
        f = faces({"north": (P_KNOB_TOP, "#0"), "east": (P_KNOB, "#0"), "west": (P_KNOB, "#0"), "up": (P_KNOB, "#0"), "down": (P_KNOB, "#0")})
        elements.append({"name": "rotary_%d" % i, "from": [fx0, fy0, 0], "to": [fx1, fy1, 1], "faces": f})
    return {
        "credit": "Made with tools/scada_assets.py (after a 1970s mainframe operator's console)",
        "parent": "block/block",
        "texture_size": [128, 128],
        "textures": {"0": "radiocraft:block/scada_master", "particle": "radiocraft:block/scada_master"},
        "elements": elements,
    }


# ======================================================================================================================
# The master's CRT terminal block (east of the panel, the viewer's left)
# ======================================================================================================================
S_BEZEL = (0, 0, 64, 64)       # the whole front at 4 px a unit; each frame piece takes its own part of it
S_GLASS = (64, 0, 112, 36)     # 12 x 9 units
S_FUN_TOP, S_FUN_SIDE, S_FUN_BOTTOM = (64, 40, 80, 56), (80, 40, 96, 56), (96, 40, 112, 56)
S_SIDE = (0, 64, 64, 128)
S_TOP = (64, 64, 128, 128)
# The screen opening, model units: x 2..14, y 3.5..12.5, recessed from z 1 to z 5.
O_X0, O_X1, O_Y0, O_Y1, DEPTH = 2.0, 14.0, 3.5, 12.5, 5.0


def screen_texture():
    sheet = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    rect(d, *S_BEZEL, BEZEL)
    for y in range(64):  # A faint stipple, painted steel.
        for x in range(64):
            if (x * 5 + y * 11) % 17 == 0:
                sheet.putpixel((x, y), shade(BEZEL, 0.94))
    rect(d, 0, 0, 64, 1, BEZEL_LIGHT)
    rect(d, 0, 0, 1, 64, BEZEL_LIGHT)            # the outer (east, the viewer's left) edge catches the light
    rect(d, 0, 60, 64, 64, (54, 56, 54, 255))      # plinth
    # The lip round the opening (the opening's px: x 8..56, y 14..50).
    rect(d, 7, 13, 57, 14, BEZEL_DARK)
    rect(d, 7, 50, 57, 51, BEZEL_LIGHT)
    rect(d, 7, 13, 8, 51, BEZEL_DARK)
    rect(d, 56, 13, 57, 51, BEZEL_LIGHT)
    text(sheet, 8, 55, "SCADA", (60, 62, 60, 255))
    # The glass: rounded, darker at the edges, a soft lit band.
    gx0, gy0, gx1, gy1 = S_GLASS
    rect(d, gx0, gy0, gx1, gy1, GLASS)
    rect(d, gx0 + 4, gy0 + 3, gx1 - 4, gy1 - 3, GLASS_MID)
    rect(d, gx0 + 6, gy0 + 5, gx0 + 20, gy0 + 9, (66, 82, 76, 255))  # a reflection, upper left
    for x0, y0 in ((gx0, gy0), (gx1 - 3, gy0), (gx0, gy1 - 3), (gx1 - 3, gy1 - 3)):
        rect(d, x0, y0, x0 + 3, y0 + 3, GLASS_EDGE)
    for (cx, cy) in ((gx0, gy0), (gx1 - 1, gy0), (gx0, gy1 - 1), (gx1 - 1, gy1 - 1)):
        sheet.putpixel((cx, cy), FUNNEL_SIDE)
    for i in range(0, 36, 2):  # Faint scan lines.
        rect(d, gx0 + 2, gy0 + i, gx1 - 2, gy0 + i + 1, shade(GLASS_MID if 3 <= i < 33 else GLASS, 0.93))
    rect(d, *S_FUN_TOP, FUNNEL_TOP)
    rect(d, *S_FUN_SIDE, FUNNEL_SIDE)
    rect(d, *S_FUN_BOTTOM, FUNNEL_BOTTOM)
    rect(d, *S_SIDE, BEZEL)
    bevel(d, *S_SIDE, BEZEL_LIGHT, BEZEL_DARK)
    rect(d, 0, 124, 64, 128, (54, 56, 54, 255))
    for y in range(72, 116, 4):
        rect(d, 8, y, 56, y + 1, BEZEL_DARK)
    rect(d, *S_TOP, BEZEL_LIGHT)
    bevel(d, *S_TOP, (150, 152, 150, 255), BEZEL)
    return sheet


def bezel_uv(x0, y0, x1, y1):
    """A part of the front, model units (x0 < x1, y0 < y1), as the bezel texture's pixels. The north face's texture
    left is east (x 16)."""
    return (int(round((16 - x1) * 4)), int(round((16 - y1) * 4)), int(round((16 - x0) * 4)), int(round((16 - y0) * 4)))


def screen_model():
    side = {"east": (S_SIDE, "#0"), "south": (S_SIDE, "#0"), "west": (S_SIDE, "#0"), "up": (S_TOP, "#0"), "down": (S_TOP, "#0")}
    elements = [
        {"name": "case", "from": [0, 0, DEPTH], "to": [16, 16, 16], "faces": faces(dict(side, north=(S_GLASS, "#0")))},
    ]
    # The frame round the deep opening; the case's north face behind it is the glass.
    frame = [
        ("frame_top", [0, O_Y1, 1], [16, 16, DEPTH], {"down": (S_FUN_TOP, "#0")}),
        ("frame_bottom", [0, 0, 1], [16, O_Y0, DEPTH], {"up": (S_FUN_BOTTOM, "#0")}),
        ("frame_east", [O_X1, O_Y0, 1], [16, O_Y1, DEPTH], {"west": (S_FUN_SIDE, "#0")}),
        ("frame_west", [0, O_Y0, 1], [O_X0, O_Y1, DEPTH], {"east": (S_FUN_SIDE, "#0")}),
    ]
    for name, frm, to, inner in frame:
        f = {"north": (bezel_uv(frm[0], frm[1], to[0], to[1]), "#0")}
        for face, (r, t) in (("east", (S_SIDE, "#0")), ("west", (S_SIDE, "#0")), ("up", (S_TOP, "#0")), ("down", (S_TOP, "#0"))):
            f[face] = (r, t)
        f.update(inner)
        if name == "frame_west":
            f.pop("west")  # Against the panel: never seen.
        elements.append({"name": name, "from": frm, "to": to, "faces": faces(f)})
    # The glass shows only through the opening: the case's north face maps the glass over x 2..14, y 3.5..12.5, so
    # give it a face of exactly the opening.
    elements[0] = {"name": "case", "from": [0, 0, DEPTH], "to": [16, 16, 16], "faces": faces(side)}
    elements.append({"name": "glass", "from": [O_X0, O_Y0, DEPTH - 0.01], "to": [O_X1, O_Y1, DEPTH], "faces": faces({"north": (S_GLASS, "#0")})})
    return {
        "credit": "Made with tools/scada_assets.py (after a 1970s mainframe console's CRT terminal)",
        "parent": "block/block",
        "texture_size": [128, 128],
        "textures": {"0": "radiocraft:block/scada_master_screen", "particle": "radiocraft:block/scada_master_screen"},
        "elements": elements,
    }


# ======================================================================================================================
# The RTU: a 1980s data radio
# ======================================================================================================================
R_FRONT = (0, 0, 48, 20)       # 12 x 5 units
R_BACK = (48, 0, 96, 20)
R_TOP = (0, 24, 48, 80)        # 12 x 14
R_SIDE = (48, 24, 104, 44)     # 14 x 5
R_BOTTOM = (48, 48, 96, 104)
R_FIN = (104, 0, 112, 8)
R_FIN_EDGE = (112, 8, 120, 16)
R_N = (112, 0, 120, 8)
R_BOX = (2.0, 0.0, 1.0, 14.0, 5.0, 15.0)   # x0 y0 z0 x1 y1 z1
R_N_PX = (40, 8, 46, 14)                   # the N connector's base on the front plate
R_LAMPS_PX = [(5, 7), (11, 7), (17, 7), (23, 7)]  # PWR DCD TXD RXD


def rtu_texture():
    sheet = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    # Front plate: brushed aluminium in a putty frame.
    rect(d, *R_FRONT, PUTTY_DARK)
    rect(d, 1, 1, 47, 19, ALU)
    for y in range(2, 18, 2):
        rect(d, 2, y, 46, y + 1, shade(ALU, 0.97))
    bevel(d, 1, 1, 47, 19, ALU_LIGHT, ALU_DARK)
    rect(d, 3, 2, 9, 3, (60, 110, 170, 255))   # the blue stripe by DATA RADIO
    rect(d, 10, 2, 30, 3, (110, 112, 116, 255))  # lettering, too small to read
    for x, y in R_LAMPS_PX:
        rect(d, x - 1, y - 1, x + 3, y + 3, (60, 60, 60, 255))
        rect(d, x, y, x + 2, y + 2, LAMP_DARK_RED)
        rect(d, x, y - 3, x + 2, y - 2, (120, 122, 126, 255))  # its legend, too small to read
    rect(d, 31, 4, 46, 6, (150, 40, 34, 255))   # the red WARNING line
    # DB25: silver shell, black trapezoid, two rows of pins.
    rect(d, 3, 11, 22, 17, ALU_DARK)
    rect(d, 4, 12, 21, 16, SOCKET)
    for i in range(7):
        sheet.putpixel((6 + i * 2, 13), (180, 160, 90, 255))
    for i in range(6):
        sheet.putpixel((7 + i * 2, 14), (180, 160, 90, 255))
    sheet.putpixel((4, 15), ALU_DARK)
    sheet.putpixel((20, 15), ALU_DARK)
    # DIAG jack and the 13.8 V socket.
    rect(d, 24, 11, 29, 17, SOCKET)
    rect(d, 25, 12, 28, 14, (60, 60, 64, 255))
    rect(d, 31, 12, 38, 17, SOCKET)
    rect(d, 33, 13, 34, 15, (190, 170, 100, 255))
    rect(d, 35, 13, 36, 15, (190, 170, 100, 255))
    rect(d, 34, 9, 35, 10, SOCKET)
    # The N connector's flange (a cube stands out of it).
    nx0, ny0, nx1, ny1 = R_N_PX
    rect(d, nx0, ny0, nx1, ny1, ALU_DARK)
    rect(d, nx0 + 1, ny0 + 1, nx1 - 1, ny1 - 1, CHROME)
    # Back.
    rect(d, *R_BACK, PUTTY_DARK)
    rect(d, 52, 4, 60, 12, SOCKET)
    # Top: putty with the label plate.
    rect(d, *R_TOP, PUTTY)
    bevel(d, *R_TOP, PUTTY_LIGHT, PUTTY_DARK)
    rect(d, 4, 30, 44, 58, (232, 228, 210, 255))
    bevel(d, 4, 30, 44, 58, (246, 244, 232, 255), (170, 166, 150, 255))
    text_centred(sheet, 24, 34, "SCADA RTU", (40, 48, 70, 255))
    rect(d, 7, 42, 41, 43, (130, 130, 130, 255))
    rect(d, 7, 45, 33, 46, (130, 130, 130, 255))
    rect(d, 5, 53, 43, 56, (60, 110, 170, 255))
    for x, y in ((2, 26), (45, 26), (2, 77), (45, 77)):
        rect(d, x, y, x + 1, y + 1, PUTTY_DARK)
    # Sides and bottom: putty, with the fins' roots as dark lines.
    rect(d, *R_SIDE, PUTTY)
    bevel(d, *R_SIDE, PUTTY_LIGHT, PUTTY_DARK)
    rect(d, *R_BOTTOM, PUTTY_DARK)
    rect(d, *R_FIN, FIN)
    rect(d, R_FIN[0], R_FIN[1], R_FIN[2], R_FIN[1] + 1, shade(FIN, 1.3))
    rect(d, *R_N, CHROME)
    rect(d, 114, 2, 118, 6, CHROME_LIGHT)
    rect(d, 115, 3, 117, 5, (60, 60, 60, 255))
    return sheet


def rtu_model():
    x0, y0, z0, x1, y1, z1 = R_BOX
    elements = [{"name": "case", "from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces({
        "north": (R_FRONT, "#0"), "south": (R_BACK, "#0"), "east": (R_SIDE, "#0"), "west": (R_SIDE, "#0"),
        "up": (R_TOP, "#0"), "down": (R_BOTTOM, "#0")})}]
    fin = all_faces(R_FIN, "#0")
    for i in range(8):
        z = 2.0 + i * 1.6
        elements.append({"name": "fin_east_%d" % i, "from": [x1, 0.5, z], "to": [x1 + 0.75, 4.5, z + 0.5], "faces": fin})
        elements.append({"name": "fin_west_%d" % i, "from": [x0 - 0.75, 0.5, z], "to": [x0, 4.5, z + 0.5], "faces": fin})
    nx0, ny0, nx1, ny1 = R_N_PX
    mx0, mx1 = x1 - (nx1 - 1) / 4.0, x1 - (nx0 + 1) / 4.0
    my0, my1 = y1 - (ny1 - 1) / 4.0, y1 - (ny0 + 1) / 4.0
    elements.append({"name": "n_connector", "from": [mx0, my0, z0 - 1.0], "to": [mx1, my1, z0], "faces": all_faces(R_N, "#0")})
    return {
        "credit": "Made with tools/scada_assets.py (after a data radio, made a decade older)",
        "parent": "block/block",
        "texture_size": [128, 128],
        "textures": {"0": "radiocraft:block/scada_rtu", "particle": "radiocraft:block/scada_rtu"},
        "elements": elements,
    }


# ======================================================================================================================
# The master's screen (256 x 144)
# ======================================================================================================================
W, H = 256, 144
SPLIT = 136                                  # the CRT terminal left of this, the control panel right
OPENING = (4, 9, 132, 137)                   # the bezel's opening, the funnel inside it
CRT = (9, 24, 118, 96)                       # x, y, w, h: the screen the text goes on
CRT_INK = (110, 230, 140)
METER = (143, 7, 34, 20)                     # the face the needle is clipped to
METER_PIVOT, METER_RADIUS = (160, 34), 24
LAMPS = [("power_lamp", "PWR", 184, 9, (60, 220, 90)), ("poll_lamp", "POLL", 218, 9, (60, 220, 90)),
         ("fail_lamp", "FAIL", 184, 21, (240, 50, 40)), ("tx_lamp", "TX", 218, 21, (255, 176, 40))]
KNOB_R = 8
POLL_KNOB = (160, 65, ["1", "2", "3"], [-50, 0, 50])
PAGE_KNOB = (214, 65, ["POINTS", "RULES", "I/O", "SETUP"], [-70, -25, 25, 70])  # The CRT's four pages (2026-11-11).
FACES = ["d", "u", "n", "s", "w", "e"]
TOGGLE_X0, TOGGLE_STEP, TOGGLE_Y = 145, 13, 92   # slot left, spacing, slot top
SCROLL_UP, SCROLL_DOWN = (233, 90, 11, 9), (233, 102, 11, 9)


def toggle_handles(i):
    x = TOGGLE_X0 + i * TOGGLE_STEP
    # OFF down, IN centre, OUT up (as a three-position bat).
    return [[x, TOGGLE_Y + 9, 4, 4], [x, TOGGLE_Y + 5, 4, 4], [x, TOGGLE_Y + 1, 4, 4]]


def draw_knob(img, d, cx, cy):
    disc(d, cx + 1, cy + 1, KNOB_R + 2, (10, 12, 12, 255))
    disc(d, cx, cy, KNOB_R + 2, SKIRT)
    disc(d, cx, cy, KNOB_R, CHROME_DARK)
    disc(d, cx, cy, KNOB_R - 1, CHROME)
    for k in range(16):  # Knurled rim.
        a = math.radians(k * 22.5)
        img.putpixel((int(round(cx - 0.5 + (KNOB_R - 1) * math.sin(a))), int(round(cy - 0.5 - (KNOB_R - 1) * math.cos(a)))), CHROME_DARK)
    disc(d, cx - 1, cy - 1, 3, CHROME_LIGHT)


def gui_panel():
    img = Image.new("RGBA", (W, H), BEZEL)
    d = ImageDraw.Draw(img)
    # ---- The CRT terminal ----
    for y in range(H):
        for x in range(SPLIT):
            if (x * 5 + y * 11) % 17 == 0:
                img.putpixel((x, y), shade(BEZEL, 0.94))
    bevel(d, 0, 0, SPLIT, H, BEZEL_LIGHT, BEZEL_DARK)
    text(img, 6, 2, "RADIOCRAFT", (70, 72, 70, 255))
    text_centred(img, 98, 2, "SCADA MASTER", (70, 72, 70, 255))
    ox0, oy0, ox1, oy1 = OPENING
    cx0, cy0, cw, ch = CRT
    cx1, cy1 = cx0 + cw, cy0 + ch
    rect(d, ox0 - 1, oy0 - 1, ox1 + 1, oy1 + 1, BEZEL_DARK)
    # The deep funnel: four trapezoids, the top in shadow, the bottom catching the light.
    d.polygon([(ox0, oy0), (ox1 - 1, oy0), (cx1, cy0), (cx0 - 1, cy0)], fill=FUNNEL_TOP)
    d.polygon([(ox0, oy1 - 1), (ox1 - 1, oy1 - 1), (cx1, cy1), (cx0 - 1, cy1)], fill=FUNNEL_BOTTOM)
    d.polygon([(ox0, oy0), (cx0 - 1, cy0), (cx0 - 1, cy1), (ox0, oy1 - 1)], fill=FUNNEL_SIDE)
    d.polygon([(ox1 - 1, oy0), (cx1, cy0), (cx1, cy1), (ox1 - 1, oy1 - 1)], fill=shade(FUNNEL_SIDE, 1.08))
    # The glass: dark green-grey, rounded corners.
    d.rounded_rectangle([cx0 - 1, cy0 - 1, cx1, cy1], radius=7, fill=GLASS_EDGE)
    d.rounded_rectangle([cx0, cy0, cx1 - 1, cy1 - 1], radius=6, fill=(22, 30, 27, 255))
    d.rounded_rectangle([cx0 + 3, cy0 + 3, cx1 - 4, cy1 - 4], radius=5, fill=(26, 35, 31, 255))
    rect(d, cx0 + 6, cy0 + 3, cx0 + 40, cy0 + 4, (40, 52, 47, 255))  # a reflection
    # ---- The control panel ----
    rect(d, SPLIT, 0, W, H, CHARCOAL)
    rect(d, SPLIT, 0, SPLIT + 1, H, (12, 14, 14, 255))
    bevel(d, SPLIT + 1, 0, W, H, CHARCOAL_LIGHT, CHARCOAL_DARK)
    for y in (36, 80, 116):  # Section dividers.
        rect(d, SPLIT + 2, y, W - 1, y + 1, CHARCOAL_DARK)
        rect(d, SPLIT + 2, y + 1, W - 1, y + 2, CHARCOAL_LIGHT)
    # Meter window.
    mx, my, mw, mh = METER
    rect(d, mx - 2, my - 2, mx + mw + 2, my + mh + 2, (8, 10, 10, 255))
    rect(d, mx, my, mx + mw, my + mh, METER_FACE)
    px, py = METER_PIVOT
    for k in range(11):
        a = math.radians(-45 + k * 9)
        r0 = METER_RADIUS - (4 if k % 5 == 0 else 2)
        for r in range(r0, METER_RADIUS - 1):
            X, Y = int(round(px + r * math.sin(a))), int(round(py - r * math.cos(a)))
            if mx <= X < mx + mw and my <= Y < my + mh:
                img.putpixel((X, Y), (170, 40, 36, 255) if k >= 9 else METER_INK)
    text_centred(img, px, my + mh + 3, "POLL OK", ENGRAVE)
    # Lamps.
    for name, label, x, y, colour in LAMPS:
        rect(d, x - 1, y - 1, x + 6, y + 6, (8, 8, 8, 255))
        rect(d, x, y, x + 5, y + 5, shade(colour + (255,), 0.28))
        text(img, x + 8, y, label, ENGRAVE)
    # Rotary switches.
    for (cx, cy, positions, angles), label in ((POLL_KNOB, "POLL SEC"), (PAGE_KNOB, "DISPLAY")):
        text_centred(img, cx, 39, label, ENGRAVE)
        draw_knob(img, d, cx, cy)
        for a in angles:  # A dot at each position.
            r = KNOB_R + 5
            img.putpixel((int(round(cx - 0.5 + r * math.sin(math.radians(a)))), int(round(cy - 0.5 - r * math.cos(math.radians(a))))), ENGRAVE)
    # Position legends, outside the dots.
    cx, cy = POLL_KNOB[0], POLL_KNOB[1]
    text(img, cx - 18, cy - 11, "1", ENGRAVE)
    text(img, cx - 2, cy - 19, "2", ENGRAVE)
    text(img, cx + 15, cy - 11, "3", ENGRAVE)
    cx, cy = PAGE_KNOB[0], PAGE_KNOB[1]
    text(img, cx - 14 - text_width("PTS"), cy - 7, "PTS", ENGRAVE)
    text(img, cx - 9 - text_width("RUL"), cy - 17, "RUL", ENGRAVE)
    text(img, cx + 9, cy - 17, "I/O", ENGRAVE)
    text(img, cx + 14, cy - 7, "SET", ENGRAVE)
    # Local I/O toggles.
    text(img, 143, 84, "I/O UP:OUT MID:IN", ENGRAVE)
    for i, face in enumerate(FACES):
        x = TOGGLE_X0 + i * TOGGLE_STEP
        rect(d, x - 2, TOGGLE_Y - 1, x + 6, TOGGLE_Y + 15, (12, 14, 14, 255))
        rect(d, x - 1, TOGGLE_Y, x + 5, TOGGLE_Y + 14, (52, 58, 60, 255))
        rect(d, x + 1, TOGGLE_Y + 2, x + 3, TOGGLE_Y + 12, (6, 6, 6, 255))   # the slot
        text_centred(img, x + 2, TOGGLE_Y + 17, face.upper(), ENGRAVE)
    # Scroll keys.
    text_centred(img, 238, 84, "SCROLL", ENGRAVE)
    for (x, y, w, h), arrow in ((SCROLL_UP, "^"), (SCROLL_DOWN, "v")):
        rect(d, x - 1, y - 1, x + w + 1, y + h + 1, (8, 8, 8, 255))
        rect(d, x, y, x + w, y + h, KEY_LIGHT)
        bevel(d, x, y, x + w, y + h, (250, 248, 240, 255), KEY_LIGHT_DARK)
        text(img, x + 4, y + 2, arrow, (40, 40, 40, 255))
    # Hex keypad (decor).
    keys = "0123456789ABCDEF"
    for i, k in enumerate(keys):
        x, y = 142 + (i % 4) * 9, 119 + (i // 4) * 6
        rect(d, x, y, x + 8, y + 5, KEY_LIGHT)
        rect(d, x, y + 4, x + 8, y + 5, KEY_LIGHT_DARK)
    for i, k in enumerate(keys):  # Legends too small for the 3x5 font: a dark dot each.
        x, y = 142 + (i % 4) * 9, 119 + (i // 4) * 6
        img.putpixel((x + 4, y + 2), (60, 60, 60, 255))
    # Coloured push buttons (decor), two rows of four.
    for i, c in enumerate(PUSH):
        x, y = 184 + (i % 4) * 17, 120 + (i // 4) * 11
        rect(d, x - 1, y - 1, x + 15, y + 9, (8, 8, 8, 255))
        rect(d, x, y, x + 14, y + 8, c)
        rect(d, x, y, x + 14, y + 1, shade(c, 1.3))
        rect(d, x, y + 7, x + 14, y + 8, shade(c, 0.6))
        rect(d, x + 3, y + 3, x + 11, y + 4, shade(c, 1.45))  # legend strokes
    return img


def gui_layout():
    controls = [{"name": "crt", "type": "display", "x": CRT[0], "y": CRT[1], "w": CRT[2], "h": CRT[3], "ink": hexc(CRT_INK)}]
    for (cx, cy, positions, angles), name in ((POLL_KNOB, "poll"), (PAGE_KNOB, "page")):
        controls.append({"name": name, "type": "knob3", "x": cx - KNOB_R - 3, "y": cy - KNOB_R - 3, "w": 2 * KNOB_R + 6, "h": 2 * KNOB_R + 6,
                         "cx": cx - 0.5, "cy": cy - 0.5, "pointer_from": 2, "pointer_r": KNOB_R - 1, "pointer_width": 1,
                         "pointer_colour": "#202020", "positions": positions, "angles_deg": angles})
    for (x, y, w, h), name in ((SCROLL_UP, "scroll_up"), (SCROLL_DOWN, "scroll_down")):
        controls.append({"name": name, "type": "button", "x": x, "y": y, "w": w, "h": h})
    for i, face in enumerate(FACES):
        x = TOGGLE_X0 + i * TOGGLE_STEP
        controls.append({"name": "face_" + face, "type": "switch", "x": x - 2, "y": TOGGLE_Y - 1, "w": 8, "h": 16,
                         "positions": ["OFF", "IN", "OUT"], "handles": toggle_handles(i),
                         "handle_colour": hexc(BAT), "handle_shadow": hexc(BAT_SHADOW)})
    for name, label, x, y, colour in LAMPS:
        controls.append({"name": name, "type": "lamp", "x": x, "y": y, "w": 5, "h": 5, "colour": hexc(colour)})
    mx, my, mw, mh = METER
    controls.append({"name": "meter", "type": "meter", "x": mx, "y": my, "w": mw, "h": mh, "pivot": list(METER_PIVOT),
                     "radius": METER_RADIUS - 2, "zero_deg": -45, "full_deg": 45, "needle_colour": "#202020"})
    return {"texture": "radiocraft:textures/gui/scada/scada_master.png", "width": W, "height": H, "controls": controls}


# ======================================================================================================================
# Previews
# ======================================================================================================================
def preview_gui(base, layout, state):
    img = base.copy()
    d = ImageDraw.Draw(img)
    for c in layout["controls"]:
        t = c["type"]
        if t == "knob3":
            i = c["positions"].index(state[c["name"]])
            a = math.radians(c["angles_deg"][i])
            for k in range(c["pointer_from"] * 3, c["pointer_r"] * 3 + 1):
                r = k / 3.0
                img.putpixel((int(math.floor(c["cx"] + r * math.sin(a))), int(math.floor(c["cy"] - r * math.cos(a)))), (32, 32, 32, 255))
        elif t == "switch":
            h = c["handles"][c["positions"].index(state[c["name"]])]
            rect(d, h[0], h[1], h[0] + h[2], h[1] + h[3], (96, 96, 96, 255))
            rect(d, h[0], h[1], h[0] + h[2] - 1, h[1] + h[3] - 1, (214, 216, 218, 255))
        elif t == "lamp" and state.get(c["name"]):
            col = tuple(int(c["colour"][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
            rect(d, c["x"], c["y"], c["x"] + c["w"], c["y"] + c["h"], col)
            rect(d, c["x"] + 1, c["y"] + 1, c["x"] + c["w"] - 1, c["y"] + c["h"] - 1, tuple((v + 255) // 2 for v in col[:3]) + (255,))
        elif t == "meter":
            a = math.radians(c["zero_deg"] + state["meter"] * (c["full_deg"] - c["zero_deg"]))
            for k in range(0, c["radius"] * 3):
                r = k / 3.0
                X, Y = int(math.floor(c["pivot"][0] + r * math.sin(a))), int(math.floor(c["pivot"][1] - r * math.cos(a)))
                if c["x"] <= X < c["x"] + c["w"] and c["y"] <= Y < c["y"] + c["h"]:
                    img.putpixel((X, Y), (32, 32, 32, 255))
        elif t == "display":
            for row, line in enumerate(state["crt"]):
                text(img, c["x"] + 4, c["y"] + 4 + row * 10, line, (110, 230, 140, 255))
        elif t == "button" and state.get("held") == c["name"]:
            rect(d, c["x"], c["y"], c["x"] + c["w"], c["y"] + c["h"], (0, 0, 0, 85))
    return img.resize((W * 3, H * 3), Image.NEAREST)


def front_view(sheet, rect_px, scale=6):
    return sheet.crop(rect_px).resize(((rect_px[2] - rect_px[0]) * scale, (rect_px[3] - rect_px[1]) * scale), Image.NEAREST)


def preview(out):
    os.makedirs(out, exist_ok=True)
    base, layout = gui_panel(), gui_layout()
    s1 = {"poll": "1", "page": "POINTS", "power_lamp": True, "poll_lamp": True, "fail_lamp": False, "tx_lamp": True, "meter": 0.9,
          "crt": ["UNIT  D  U  N  S  W  E", "0 MS  -  -  15 -  -  0", "3 RT  -  -  12 -  -  -", "7 RT  -  0  -  -  -  15", "9 RT  COMM FAIL"],
          "held": None}
    s1.update({"face_" + f: m for f, m in zip(FACES, ["OFF", "IN", "OUT", "OFF", "IN", "OUT"])})
    s2 = dict(s1, poll="3", page="RULES", fail_lamp=True, poll_lamp=False, meter=0.2, held="scroll_down",
              crt=["1 IF 3.N >= 10 THEN 7.E = 15", "2 COPY 3.N TO 0.U", "3 POLL 3"])
    s2.update({"face_" + f: "OUT" for f in FACES})
    preview_gui(base, layout, s1).save(os.path.join(out, "scada_gui_points.png"))
    preview_gui(base, layout, s2).save(os.path.join(out, "scada_gui_rules.png"))
    # The console front as seen: the CRT (left) beside the panel (right).
    p, s = panel_texture(), screen_texture()
    crt = s.crop(S_BEZEL).copy()
    glass = s.crop(S_GLASS)
    crt.paste(glass.resize((48, 36)), (8, 14))
    panel = p.crop(P_FRONT).copy()
    pd = ImageDraw.Draw(panel)
    for x, y in P_KNOBS_PX:
        rect(pd, x, y, x + 4, y + 4, CHROME_LIGHT)
    both = Image.new("RGBA", (128, 64))
    both.paste(crt, (0, 0))
    both.paste(panel, (64, 0))
    both.resize((768, 384), Image.NEAREST).save(os.path.join(out, "scada_master_front.png"))
    r = rtu_texture()
    front_view(r, R_FRONT, 12).save(os.path.join(out, "scada_rtu_front.png"))
    front_view(r, R_TOP, 8).save(os.path.join(out, "scada_rtu_top.png"))
    s.resize((512, 512), Image.NEAREST).save(os.path.join(out, "scada_master_screen_sheet.png"))


def main():
    save(panel_texture(), "textures", "block", "scada_master.png")
    save(screen_texture(), "textures", "block", "scada_master_screen.png")
    save(rtu_texture(), "textures", "block", "scada_rtu.png")
    write_json(os.path.join(ROOT, "models", "block", "scada_master.json"), panel_model())
    write_json(os.path.join(ROOT, "models", "block", "scada_master_screen.json"), screen_model())
    write_json(os.path.join(ROOT, "models", "block", "scada_rtu.json"), rtu_model())
    write_json(os.path.join(ROOT, "models", "item", "scada_master.json"), {"parent": "radiocraft:block/scada_master"})
    write_json(os.path.join(ROOT, "models", "item", "scada_rtu.json"), {"parent": "radiocraft:block/scada_rtu"})
    save(gui_panel(), "textures", "gui", "scada", "scada_master.png")
    write_json(os.path.join(ROOT, "gui_layouts", "scada_master.json"), gui_layout())
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    print("wrote the SCADA master, its screen and the RTU")


if __name__ == "__main__":
    main()
