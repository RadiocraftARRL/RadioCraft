"""GUI art for the CB radios (2026-11-08): the screens' backgrounds and their shared widget sheet.

Reads the layouts in src/main/resources/assets/radiocraft/gui_layouts/cb_*.json (where every knob, switch, lamp,
meter and window sits) and writes, under textures/gui/cb/:
  cb_mobile_5875.png   the 5875's front: woodgrain lid, display strip, meter faces with their scales, speaker grille,
                       control panel, and every label
  cb_mobile_pc68.png   the PC68's chrome face, with the hand mic plugged in and its coiled cord going round the back
  cb_portable.png      the TRC-217 style portable, front on, with the channel knob standing on top
  cb_widgets.png       the moving parts the screens draw over them (see SHEET below; CbPanel has the same numbers)
The screens (CbPanel) draw only the needles, the numbers and the pulled-out whip in code. To change a panel, edit its
JSON (both this script and the game read it) and rerun, or paint over the PNGs. Needs Pillow.
Run: python tools/cb_gui_assets.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")
LAYOUTS = os.path.join(ROOT, "gui_layouts")
OUT = os.path.join(ROOT, "textures", "gui", "cb")

# ---- The widget sheet: kind -> (y, frame size); frames run along x, 11 knob positions (0, 10%, ... 100%) ---------
KNOBS = {"small": (0, 20), "large": (20, 30), "chrome_small": (50, 22), "chrome_large": (72, 32), "chrome_xl": (104, 40)}
FRAMES = 11
TOGGLE = (0, 144, 8, 16)      # up frame at x 0, down frame at x 8
LAMP_Y, LAMP = 160, 6         # off, red, green, amber at x 0, 6, 12, 18
LIGHT_BUTTON = (24, 160, 10)  # off at x 24, lit at x 34
PTT_BAR = (448, 0, 6, 50)     # normal at x 448, held at x 456
JACK_HELD = (464, 0, 26)      # a red glow round a mic jack held down
SHEET_SIZE = (512, 192)

BLACK = (12, 12, 12, 255)
SILVER = (201, 204, 207, 255)
SILVER_DARK = (142, 146, 150, 255)
SILVER_LIGHT = (230, 232, 234, 255)
LABEL = (32, 32, 32, 255)
LABEL_LIGHT = (200, 200, 200, 255)
METER_FACE = (237, 230, 200, 255)

# ---- A 3 x 5 pixel font for the panel lettering ------------------------------------------------------------------
GLYPHS = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
    "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101111011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Y": "101101010010010",
    "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100111101111", "7": "111001010010010", "8": "111101111101111",
    "9": "111101111001110", "/": "001001010100100", "-": "000000111000000", "+": "000010111010000", ":": "000010000010000",
    ".": "000000000000010", " ": "000000000000000",
}


def text_width(s):
    return max(0, len(s) * 4 - 1)


def text(img, x, y, s, colour=LABEL):
    px = img.load()
    for i, ch in enumerate(s.upper()):
        glyph = GLYPHS.get(ch, GLYPHS[" "])
        for gy in range(5):
            for gx in range(3):
                if glyph[gy * 3 + gx] == "1":
                    X, Y = x + i * 4 + gx, y + gy
                    if 0 <= X < img.width and 0 <= Y < img.height:
                        px[X, Y] = colour


def text_centred(img, cx, y, s, colour=LABEL):
    text(img, cx - text_width(s) // 2, y, s, colour)


def disc(d, cx, cy, r, fill, outline=None):
    d.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], fill=fill, outline=outline)


def knob_angle(frac):
    """7 o'clock (0) round to 5 o'clock (1), in screen degrees (y down)."""
    return math.radians(135.0 + 270.0 * frac)


# ---- The widget sheet --------------------------------------------------------------------------------------------

def draw_knob(img, ox, oy, size, frac, chrome):
    d = ImageDraw.Draw(img)
    c = size / 2.0
    r = size // 2
    if chrome:
        disc(d, c, c, r, (96, 100, 104, 255))
        disc(d, c, c, r - 1, (176, 180, 184, 255))
        for k in range(24):  # Knurling round the edge.
            a = 2 * math.pi * k / 24
            x, y = c + math.cos(a) * (r - 2), c + math.sin(a) * (r - 2)
            d.point((ox + x, oy + y), fill=(120, 124, 128, 255))
        disc(d, c, c, r - 4, (214, 217, 220, 255))
        disc(d, c - 1, c - 1, max(2, r // 3), (236, 238, 240, 255))  # Shine.
        pointer = (30, 30, 30, 255)
    else:
        disc(d, c, c, r, (24, 24, 24, 255))
        disc(d, c, c, r - 2, (44, 44, 44, 255))
        disc(d, c - 1, c - 1, max(2, r - 5), (58, 58, 58, 255))
        pointer = (238, 238, 238, 255)
    a = knob_angle(frac)
    for i in range(int(r * 0.3), r - 1):
        x, y = c + math.cos(a) * i, c + math.sin(a) * i
        d.point((x, y), fill=pointer)
        if size >= 30:
            d.point((x + 0.6, y + 0.6), fill=pointer)
    return img


def sheet():
    out = Image.new("RGBA", SHEET_SIZE, (0, 0, 0, 0))
    for kind, (y, size) in KNOBS.items():
        for f in range(FRAMES):
            tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            draw_knob(tile, 0, 0, size, f / (FRAMES - 1), kind.startswith("chrome"))
            out.alpha_composite(tile, (f * size, y))
    d = ImageDraw.Draw(out)
    tx, ty, tw, th = TOGGLE
    for frame, up in ((0, True), (1, False)):
        x = tx + frame * tw
        d.rectangle([x, ty, x + tw - 1, ty + th - 1], fill=(70, 70, 70, 255))
        d.rectangle([x + 1, ty + 1, x + tw - 2, ty + th - 2], fill=(26, 26, 26, 255))
        hy = ty + 2 if up else ty + th - 8
        d.rectangle([x + 2, hy, x + tw - 3, hy + 5], fill=(224, 112, 32, 255))  # The orange bat handle.
        d.point((x + 2, hy), fill=(255, 170, 90, 255))
    for i, colour in enumerate([(42, 42, 42, 255), (255, 48, 32, 255), (59, 255, 106, 255), (255, 176, 32, 255)]):
        x = i * LAMP
        d.rectangle([x, LAMP_Y, x + LAMP - 1, LAMP_Y + LAMP - 1], fill=(20, 20, 20, 255))
        d.rectangle([x + 1, LAMP_Y + 1, x + LAMP - 2, LAMP_Y + LAMP - 2], fill=colour)
        if i:
            d.point((x + 2, LAMP_Y + 2), fill=(255, 255, 255, 255))
    lx, ly, ls = LIGHT_BUTTON
    for i, colour in enumerate([(176, 30, 30, 255), (255, 80, 70, 255)]):
        x = lx + i * ls
        d.rectangle([x, ly, x + ls - 1, ly + ls - 1], fill=(60, 10, 10, 255))
        d.rectangle([x + 1, ly + 1, x + ls - 2, ly + ls - 2], fill=colour)
    px, py, pw, ph = PTT_BAR
    for i, colour in enumerate([(48, 48, 48, 255), (110, 40, 40, 255)]):
        x = px + i * 8
        d.rectangle([x, py, x + pw - 1, py + ph - 1], fill=(20, 20, 20, 255))
        d.rectangle([x + 1, py + 1, x + pw - 2, py + ph - 2], fill=colour)
        for yy in range(py + 4, py + ph - 3, 3):
            d.line([x + 1, yy, x + pw - 2, yy], fill=(30, 30, 30, 255))
    jx, jy, js = JACK_HELD
    for rr in range(js // 2, js // 2 - 4, -1):
        d.ellipse([jx + js / 2 - rr, jy + js / 2 - rr, jx + js / 2 + rr - 1, jy + js / 2 + rr - 1], outline=(255, 60, 40, 120 + (js // 2 - rr) * 40))
    return out


# ---- Pieces the backgrounds share --------------------------------------------------------------------------------

def meter_radius(mw, mh):
    """How far the scale (and CbPanel's needle) reaches from the pivot: inside the face, under its top label."""
    return int(min(mh - 14, (mw / 2.0 - 4) / 0.866))


def meter_face(img, w):
    d = ImageDraw.Draw(img)
    x, y, mw, mh = w["x"], w["y"], w["w"], w["h"]
    d.rectangle([x - 2, y - 2, x + mw + 1, y + mh + 1], fill=(40, 40, 40, 255))
    face = (190, 228, 196, 255) if w.get("backlit") else METER_FACE
    d.rectangle([x, y, x + mw - 1, y + mh - 1], fill=face)
    # The scale: the same arc the needle swings on (CbPanel: pivot at the bottom middle, 3 px up; 120 degrees).
    cx, cy, r = x + mw / 2.0, y + mh - 3, meter_radius(mw, mh)
    for i in range(0, 41):
        v = i / 40.0
        a = math.radians(210.0 + 120.0 * v)
        colour = (200, 32, 32, 255) if v > 0.7 else (32, 128, 64, 255)
        rr = r + (2 if i % 5 == 0 else 0)
        for k in range(r, rr + 2):
            d.point((cx + math.cos(a) * k, cy + math.sin(a) * k), fill=colour)
    disc(d, cx, cy + 1, 3, (60, 60, 60, 255))
    text_centred(img, x + mw // 2, y + 3, w.get("label", ""), (64, 64, 64, 255))
    if w.get("label2"):
        text(img, x + 3, y + mh - 8, w["label2"], (64, 64, 64, 255))


def lamp_socket(img, w, label_colour):
    d = ImageDraw.Draw(img)
    x, y = w["x"], w["y"]
    d.rectangle([x - 4, y - 4, x + 3, y + 3], fill=(36, 36, 36, 255))
    pos = w.get("label_pos", "right")
    s = w.get("label", "")
    if pos == "right":
        text(img, x + 6, y - 2, s, label_colour)
    elif pos == "above":
        text_centred(img, x, y - 11, s, label_colour)
    elif pos == "below":
        text_centred(img, x, y + 6, s, label_colour)
    else:
        text(img, x - 6 - text_width(s), y - 2, s, label_colour)


def toggle_plate(img, w, label_colour):
    d = ImageDraw.Draw(img)
    x, y = w["x"], w["y"]
    d.rectangle([x - 6, y - 10, x + 5, y + 9], fill=(150, 154, 158, 255), outline=(110, 114, 118, 255))
    if w.get("style") == "updown":  # The PC68's way: what up means above the switch, what down means below it.
        text_centred(img, x, y - 17, w.get("up", ""), label_colour)
        text_centred(img, x, y + 12, w.get("down", ""), label_colour)
        return
    text_centred(img, x, y - 18, w.get("label", ""), label_colour)
    if w.get("up"):
        text(img, x + 7, y - 9, w["up"], label_colour)
    if w.get("down"):
        text(img, x + 7, y + 4, w["down"], label_colour)


def knob_label(img, w, label_colour, below=False):
    size = KNOBS.get(w.get("size", "small"), (0, w.get("h", 16)))[1]
    if below:
        text_centred(img, w["x"], w["y"] + size // 2 + 3, w.get("label", ""), label_colour)
    else:
        text_centred(img, w["x"], w["y"] - size // 2 - 8, w.get("label", ""), label_colour)
    if w.get("min"):
        text(img, w["x"] - size // 2 - text_width(w["min"]) + 1, w["y"] + size // 2 + 1, w["min"], label_colour)
    if w.get("max"):
        text(img, w["x"] + size // 2, w["y"] + size // 2 + 1, w["max"], label_colour)


def mic_jack(img, w, label_colour):
    d = ImageDraw.Draw(img)
    x, y, r = w["x"], w["y"], w.get("r", 9)
    disc(d, x, y, r, (110, 114, 118, 255))
    disc(d, x, y, r - 2, (160, 164, 168, 255))
    disc(d, x, y, r - 4, (30, 30, 30, 255))
    for dx, dy in ((-2, -2), (1, -2), (-2, 1), (1, 1)):
        d.point((x + dx, y + dy), fill=(208, 176, 96, 255))
    text_centred(img, x, y - r - 8, w.get("label", ""), label_colour)


def brushed(img, box, base, step=2):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=base)
    for yy in range(y0, y1 + 1, step):
        shade = tuple(max(0, c - 10) for c in base[:3]) + (255,)
        d.line([x0, yy, x1, yy], fill=shade)


# ---- The 5875 ----------------------------------------------------------------------------------------------------

def mobile_5875(layout):
    W, H = layout["width"], layout["height"]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, 9], fill=(90, 58, 34, 255))  # The woodgrain lid's edge.
    for x in range(2, W - 2, 9):
        d.line([x, 3, x + 4, 3], fill=(110, 74, 46, 255))
        d.line([x + 3, 6, x + 7, 6], fill=(74, 46, 26, 255))
    d.rectangle([0, 10, W - 1, H - 1], fill=SILVER_DARK)
    d.rectangle([3, 12, W - 4, H - 4], fill=SILVER)
    d.rectangle([10, 14, W - 10, 70], fill=BLACK, outline=(70, 70, 70, 255))  # The display strip.
    d.rectangle([10, 76, 114, H - 8], fill=BLACK)  # Speaker grille.
    for y in range(80, H - 10, 4):
        for x in range(14, 112, 4):
            d.rectangle([x, y, x + 1, y + 1], fill=(44, 44, 44, 255))
    d.rectangle([120, 74, W - 10, H - 8], fill=SILVER_LIGHT, outline=SILVER_DARK)  # Control panel.
    d.line([120, 124, W - 10, 124], fill=SILVER_DARK)
    for w in layout["widgets"]:
        t = w["type"]
        in_strip = w["y"] < 72
        colour = LABEL_LIGHT if in_strip else LABEL
        if t == "meter":
            meter_face(img, w)
        elif t == "lamp":
            lamp_socket(img, w, (150, 150, 150, 255))
        elif t == "digits" and w.get("back"):
            d.rectangle([w["x"], w["y"], w["x"] + w["w"] - 1, w["y"] + w["h"] - 1], fill=tuple(int(w["back"][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
        elif t == "toggle":
            toggle_plate(img, w, colour)
        elif t == "knob":
            knob_label(img, w, colour)
        elif t == "ptt":
            mic_jack(img, w, colour)
    return img


# ---- The PC68 ----------------------------------------------------------------------------------------------------

def coil(d, points, colour=(18, 18, 18, 255)):
    """A coiled mic cord along the path: a thick line with a loop every few pixels."""
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        d.line([x0, y0, x1, y1], fill=colour, width=3)
        length = math.hypot(x1 - x0, y1 - y0)
        for k in range(0, int(length), 4):
            x, y = x0 + (x1 - x0) * k / length, y0 + (y1 - y0) * k / length
            d.ellipse([x - 3, y - 3, x + 2, y + 2], outline=colour)
            d.point((x - 1, y - 2), fill=(70, 70, 70, 255))


def hanging_coil(img, x, top, bottom):
    """A coiled mic cord from a plug at (x, top), turn under turn down past the bottom of the panel, after the 10m
    radio's: a helix seen from the side, each turn a pale band across the front (its lower half) over a dark band
    going round behind (its upper half), the stack leaning a little to the left."""
    d = ImageDraw.Draw(img)
    black, front, light, back = (14, 14, 16, 255), (128, 136, 142, 255), (178, 186, 192, 255), (66, 70, 84, 255)
    d.rectangle([x - 3, top, x + 2, top + 6], fill=black)  # The strain relief.
    d.rectangle([x - 2, top, x + 1, top + 5], fill=back)
    turns = []
    y, k = top + 4, 0
    while y < bottom - 12:
        turns.append([x - 15 - 2 * k, y, x + 13 - 2 * k, y + 12])
        y += 8
        k += 1
    for box in turns:  # Behind first: the far side of every turn.
        d.arc(box, 180, 360, fill=black, width=5)
        d.arc([box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], 185, 355, fill=back, width=3)
    for box in turns:  # Then the near side over them.
        d.arc(box, 0, 180, fill=black, width=5)
        inner = [box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1]
        d.arc(inner, 5, 175, fill=front, width=3)
        d.arc([inner[0] + 2, inner[1], inner[2] - 2, inner[3] - 1], 70, 150, fill=light, width=1)  # Its shine.
    last = turns[-1]
    tx, ty = last[0] + 2, (last[1] + last[3]) // 2
    d.line([tx, ty, tx - 2, bottom - 1], fill=black, width=5)  # The tail, off the bottom.
    d.line([tx, ty + 1, tx - 2, bottom - 2], fill=front, width=2)


def mobile_pc68(layout):
    W, H = layout["width"], layout["height"]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fx0, fy0, fx1, fy1 = layout["faceplate"]
    mic = next(w for w in layout["widgets"] if w["type"] == "ptt")
    d.rectangle([fx0 - 3, fy0 - 3, fx1 + 3, fy1 + 3], fill=(28, 28, 28, 255))  # The black case behind the face.
    brushed(img, (fx0, fy0, fx1, fy1), (226, 228, 230, 255))
    d.rectangle([fx0, fy0, fx1, fy0], fill=(255, 255, 255, 255))
    d.rectangle([fx0, fy1, fx1, fy1], fill=(150, 152, 156, 255))
    for w in layout["widgets"]:
        t = w["type"]
        if t == "meter":
            meter_face(img, w)
        elif t == "lamp":
            lamp_socket(img, w, LABEL)
        elif t == "digits" and w.get("back"):
            d.rectangle([w["x"] - 2, w["y"] - 2, w["x"] + w["w"] + 1, w["y"] + w["h"] + 1], fill=(40, 40, 40, 255))
            d.rectangle([w["x"], w["y"], w["x"] + w["w"] - 1, w["y"] + w["h"] - 1], fill=tuple(int(w["back"][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
        elif t == "toggle":
            toggle_plate(img, w, LABEL)
        elif t == "knob":
            size = KNOBS[w["size"]][1]
            if w.get("bezel"):  # The channel selector's chrome bezel, the knob sits in it.
                disc(d, w["x"], w["y"], size // 2 + 6, (120, 124, 128, 255))
                disc(d, w["x"], w["y"], size // 2 + 4, (196, 200, 204, 255))
                disc(d, w["x"], w["y"], size // 2 + 2, (60, 60, 64, 255))
            if w.get("ring"):  # The outer squelch ring round the volume knob.
                disc(d, w["x"], w["y"], size // 2 + 5, (150, 154, 158, 255))
                disc(d, w["x"], w["y"], size // 2 + 3, (190, 194, 198, 255))
            knob_label(img, w, LABEL)
        elif t == "ptt":
            mic_jack(img, w, LABEL)
            if w.get("plugged"):  # The mic's plug in the jack, its coiled cord hanging below the radio.
                disc(d, w["x"], w["y"], w["r"] - 1, (170, 174, 178, 255))
                disc(d, w["x"], w["y"], w["r"] - 4, (214, 217, 220, 255))
                hanging_coil(img, w["x"], w["y"] + w["r"] - 2, H)
    text(img, fx1 - 120, fy1 - 9, "PC68", (40, 40, 40, 255))
    return img


# ---- The portable ------------------------------------------------------------------------------------------------

def portable(layout):
    W, H = layout["width"], layout["height"]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    l, t, r, b = layout["body"]
    wx, wy, ww = layout["whip"]
    cap = 22
    # The whip's stub, collar and base above the case (the code draws the rest while it is pulled out).
    d.rectangle([wx, wy, wx + ww - 1, t - 14], fill=(216, 220, 224, 255))
    d.rectangle([wx - 3, t - 30, wx + ww + 2, t - 15], fill=(32, 32, 32, 255))
    for y in range(t - 29, t - 14, 3):
        d.line([wx - 3, y, wx + ww + 2, y], fill=(58, 58, 58, 255))
    d.rectangle([wx - 2, t - 14, wx + ww + 1, t - 1], fill=(184, 188, 192, 255))
    # The channel knob standing on the cap, side on.
    ck = next(w for w in layout["widgets"] if w["type"] == "knob" and w["control"] == "CHANNEL")
    d.rectangle([ck["x"] - 8, ck["y"] - 8, ck["x"] + 7, ck["y"] + 6], fill=SILVER_DARK)
    d.rectangle([ck["x"] - 7, ck["y"] - 7, ck["x"] + 6, ck["y"] + 5], fill=SILVER)
    d.rectangle([ck["x"] - 2, ck["y"] - 9, ck["x"] + 1, ck["y"] - 8], fill=SILVER)
    # The case: silver cap, black body.
    d.rectangle([l - 2, t - 2, r + 1, b + 1], fill=(5, 5, 5, 255))
    d.rectangle([l, t, r - 1, t + cap - 1], fill=SILVER)
    d.rectangle([l, t + cap - 2, r - 1, t + cap - 1], fill=SILVER_DARK)
    d.rectangle([l, t + cap, r - 1, b - 1], fill=(20, 20, 20, 255))
    d.rectangle([l, t + cap, l + 1, b - 1], fill=(42, 42, 42, 255))
    d.rectangle([r - 2, t + cap, r - 1, b - 1], fill=(42, 42, 42, 255))
    # Channel window frame, grille, control panel, mic grille.
    win = next(w for w in layout["widgets"] if w["type"] == "digits")
    d.rectangle([win["x"] - 2, win["y"] - 1, win["x"] + win["w"] + 1, win["y"] + win["h"]], fill=(10, 10, 10, 255))
    d.rectangle([l + 8, t + cap + 24, r - 9, t + cap + 87], fill=(28, 28, 28, 255))
    for y in range(t + cap + 27, t + cap + 86, 4):
        d.rectangle([l + 10, y, r - 11, y + 1], fill=(8, 8, 8, 255))
    d.rectangle([l + 6, t + 120, r - 7, t + 175], fill=(14, 14, 14, 255))
    d.rectangle([l + 7, t + 121, r - 8, t + 174], fill=(20, 20, 20, 255))
    d.rectangle([l + 8, t + 180, r - 9, b - 5], fill=(10, 10, 10, 255))
    for y in range(t + 183, b - 5, 3):
        d.line([l + 12, y, r - 13, y], fill=(38, 38, 38, 255))
    text_centred(img, (l + r) // 2, t + 162, "40 CHANNEL CB", (224, 224, 224, 255))
    text_centred(img, (l + r) // 2, t + 168, "TRC-217", (224, 224, 224, 255))
    for w in layout["widgets"]:
        if w["type"] == "knob" and w.get("size") != "none":
            knob_label(img, w, (224, 224, 224, 255))
        elif w["type"] == "light":
            text(img, w["x"] - 2 - text_width(w["label"]), w["y"] + 3, w["label"], (154, 154, 154, 255))
        elif w["type"] == "ptt" and w.get("side"):  # The PTT bar on the left side: a recess for the sprite.
            d.rectangle([w["x"] - 1, w["y"] - 1, w["x"] + w["w"], w["y"] + w["h"]], fill=(10, 10, 10, 255))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet().save(os.path.join(OUT, "cb_widgets.png"))
    for name, draw in (("cb_mobile_5875", mobile_5875), ("cb_mobile_pc68", mobile_pc68), ("cb_portable", portable)):
        with open(os.path.join(LAYOUTS, name + ".json"), encoding="utf-8") as f:
            layout = json.load(f)
        draw(layout).save(os.path.join(OUT, name + ".png"))
    print("ok")


if __name__ == "__main__":
    main()
