"""Art for the distress beacon (2026-11-09), after a 1980s-90s EPIRB: a squat high-visibility orange case, octagonal
(two crossed boxes), on a wider black rubber bumper; a white label band round its middle lettered EPIRB on the front
and DISTRESS on the back, red and white stripes on the sides and a band of silver reflective tape on the shoulder; a
clear strobe dome and a stubby black whip on top; a red arming tab between two guards on the front. Real ones float
upright, so it stands on its bumper. No brand names.

Writes, under src/main/resources/assets/radiocraft/:
  textures/item/distress_beacon.png          128x128 (8 px a model unit), strobe dark
  textures/item/distress_beacon_active.png   the same, strobe lit
  textures/item/spent_distress_beacon.png    faded, scuffed, label yellowed and torn, strobe dark
  models/item/distress_beacon.json           strobe dark; switches to _active on radiocraft:active
  models/item/distress_beacon_active.json    strobe lit (its dome glows: neoforge_data light 15)
  models/item/spent_distress_beacon.json     the used-up beacon: the arming tab is pulled out, a hole where it was

Plain vanilla models (no loader), so Blockbench opens them. The hand transforms are the marine handheld's, lifted
for a body that stands on y 0 rather than 1.5, so it is the same size in the hand: a little shorter and fatter.
Needs Pillow. Run: python tools/distress_beacon_assets.py
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "radiocraft")

# ---- A 3 x 5 pixel font (as tools/marine_radio_assets.py) ------------------------------------------------------------
GLYPHS = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "I": "111010010010111", "M": "101111111101101", "P": "110101110100100", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "1": "010110010010111", "2": "110001010100111", "5": "111100110001110", "4": "101101111001001", "6": "011100111101111",
    "7": "111001010010010", "9": "111101111001110", "O": "111101101101111", "0": "111101101101111", ".": "000000000000010", " ": "000000000000000",
}


def text_width(s):
    return max(0, len(s) * 4 - 1)


def text(img, x, y, s, colour):
    px = img.load()
    for i, ch in enumerate(s):
        glyph = GLYPHS.get(ch.upper(), GLYPHS[" "])
        for gy in range(5):
            for gx in range(3):
                if glyph[gy * 3 + gx] == "1":
                    px[x + i * 4 + gx, y + gy] = colour


def text_centred(img, cx, y, s, colour):
    text(img, int(round(cx - text_width(s) / 2.0)), y, s, colour)


def rect(d, x0, y0, x1, y1, fill):
    """Fills x0..x1-1, y0..y1-1."""
    if x1 > x0 and y1 > y0:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ======================================================================================================================
# Palettes: new, and spent (sun-faded, scuffed)
# ======================================================================================================================
NEW = {
    "orange": (238, 104, 22, 255), "orange_light": (255, 146, 58, 255), "orange_dark": (186, 70, 12, 255),
    "orange_edge": (140, 50, 8, 255),
    "white": (240, 238, 228, 255), "white_dark": (204, 200, 188, 255), "ink": (22, 22, 24, 255), "red": (206, 32, 28, 255),
    "red_light": (244, 92, 80, 255), "red_dark": (130, 16, 14, 255), "grey_text": (150, 148, 140, 255),
    "tape": (214, 220, 226, 255), "tape_light": (246, 248, 250, 255), "tape_dark": (156, 164, 172, 255),
    "rubber": (34, 34, 36, 255), "rubber_light": (62, 62, 66, 255), "rubber_dark": (14, 14, 16, 255),
    "grey": (96, 98, 102, 255), "grey_light": (138, 140, 144, 255), "hole": (8, 8, 8, 255),
}
SPENT = {
    "orange": (198, 128, 86, 255), "orange_light": (216, 156, 116, 255), "orange_dark": (160, 100, 66, 255),
    "orange_edge": (120, 76, 50, 255),
    "white": (214, 204, 168, 255), "white_dark": (180, 168, 132, 255), "ink": (84, 78, 70, 255), "red": (164, 84, 74, 255),
    "red_light": (186, 116, 104, 255), "red_dark": (112, 58, 50, 255), "grey_text": (164, 154, 128, 255),
    "tape": (170, 170, 164, 255), "tape_light": (192, 192, 184, 255), "tape_dark": (130, 130, 124, 255),
    "rubber": (52, 50, 48, 255), "rubber_light": (78, 76, 72, 255), "rubber_dark": (30, 28, 28, 255),
    "grey": (104, 102, 100, 255), "grey_light": (132, 130, 126, 255), "hole": (12, 10, 10, 255),
}
# The strobe dome: frosted clear with the dark lamp inside, or lit (white-hot core, amber-white glass).
DOME_DARK = {"glass": (176, 196, 204, 255), "glass_light": (226, 238, 242, 255), "glass_dark": (118, 136, 146, 255),
             "core": (92, 104, 112, 255), "core_hot": (130, 142, 150, 255)}
DOME_LIT = {"glass": (255, 236, 176, 255), "glass_light": (255, 255, 250, 255), "glass_dark": (250, 200, 110, 255),
            "core": (255, 252, 230, 255), "core_hot": (255, 255, 255, 255)}
DOME_SPENT = {"glass": (170, 170, 152, 255), "glass_light": (198, 196, 178, 255), "glass_dark": (124, 122, 108, 255),
              "core": (96, 94, 86, 255), "core_hot": (118, 116, 106, 255)}

# ======================================================================================================================
# Geometry (model units; the front is south, as the handhelds)
# ======================================================================================================================
BODY_Y = (2.0, 10.5)          # 8.5 tall: 68 texture px
BODY_W = (5.5, 10.5)          # square (maintainer, 2026-11-09): 5 across, 40 texture px a face
BASE_Y = (0.0, 2.0)
BASE_W = (5.0, 11.0)          # the bumper, half a unit wider all round
SHOULDER_Y = (10.5, 11.5)
SHOULDER_W = (6.0, 10.0)

# Texture regions (pixels in the 128x128 sheet).
R_FRONT = (0, 0, 40, 68)        # body south: label band lettered EPIRB
R_BACK = (40, 0, 80, 68)        # body north: DISTRESS, the bracket clip
R_SIDE = (80, 0, 120, 68)       # body east and west: stripes
R_TOP = (120, 0, 128, 16)       # plain orange tops
R_BASE = (0, 68, 48, 84)        # the bumper's sides
R_RUBBER = (56, 68, 72, 84)     # bumper top, ring, guards
R_SHOULDER = (72, 68, 104, 76)  # the shoulder's sides: reflective tape
R_WHIP = (112, 68, 120, 84)     # the whip: ribbed black
R_RED = (120, 68, 128, 76)      # the arming tab
R_GREY = (120, 76, 128, 84)     # the switch housing's sides
R_DOME_SIDE = (0, 84, 24, 100)
R_DOME_TOP = (24, 84, 40, 100)
R_HOUSING = (40, 84, 60, 100)   # the switch housing's front: 2.5 x 2 units
R_TAB_FACE = (60, 84, 68, 92)   # the tab's front: a pull ring printed on it

BAND = (30, 46)                 # the label band's rows on the body faces (68 tall)


# ======================================================================================================================
# Texture
# ======================================================================================================================
def body_face(img, d, box, p, kind, rng):
    x0, y0, x1, y1 = box
    w = x1 - x0
    rect(d, x0, y0, x1, y1, p["orange"])
    rect(d, x0, y0, x1, y0 + 1, p["orange_light"])            # top lip catches the light
    rect(d, x0, y1 - 2, x1, y1, p["orange_dark"])             # darker toward the bumper
    if w > 8:
        rect(d, x0, y0, x0 + 1, y1, p["orange_light"])
        rect(d, x1 - 1, y0, x1, y1, p["orange_dark"])
    # The moulded seam where the two shell halves meet, low on the case.
    rect(d, x0, y0 + 56, x1, y0 + 57, p["orange_edge"])
    # The label band, wrapping all the way round.
    b0, b1 = y0 + BAND[0], y0 + BAND[1]
    rect(d, x0, b0, x1, b1, p["white"])
    rect(d, x0, b0, x1, b0 + 1, p["white_dark"])
    rect(d, x0, b1 - 1, x1, b1, p["white_dark"])
    cx = x0 + w / 2.0
    if kind == "front":
        rect(d, x0 + 2, b0 + 2, x1 - 2, b0 + 9, p["red"])     # a red panel with the white word on it
        text_centred(img, cx, b0 + 3, "SOS", p["white"])
        for i, y in enumerate((b0 + 11, b0 + 13)):           # instructions, too small to read
            rect(d, x0 + 4, y, x1 - (4 if i == 0 else 10), y + 1, p["grey_text"])
        text_centred(img, cx, y0 + 50, "161.975", p["orange_edge"])  # its frequency (MHz), moulded into the case
    elif kind == "back":
        text_centred(img, cx, b0 + 3, "DISTRESS", p["ink"])
        rect(d, x0 + 4, b0 + 10, x1 - 4, b0 + 11, p["red"])
        rect(d, x0 + 4, b0 + 12, x1 - 8, b0 + 13, p["grey_text"])
        # The bracket clip: a dark recess the mounting bracket's latch hooks into.
        rect(d, x0 + 13, y0 + 8, x1 - 13, y0 + 20, p["orange_edge"])
        rect(d, x0 + 14, y0 + 9, x1 - 14, y0 + 19, p["rubber"])
        rect(d, x0 + 14, y0 + 9, x1 - 14, y0 + 10, p["rubber_dark"])
    elif kind == "side":
        for i in range(0, w, 6):                             # red and white hazard stripes
            rect(d, x0 + i, b0 + 3, min(x1, x0 + i + 3), b1 - 3, p["red"])
        rect(d, x0, b0 + 2, x1, b0 + 3, p["white_dark"])
        rect(d, x0, b1 - 3, x1, b1 - 2, p["white_dark"])
    # A strip of reflective tape high on the case.
    if w > 8:
        rect(d, x0, y0 + 4, x1, y0 + 7, p["tape"])
        rect(d, x0, y0 + 4, x1, y0 + 5, p["tape_light"])
        rect(d, x0, y0 + 6, x1, y0 + 7, p["tape_dark"])
    else:
        rect(d, x0, y0 + 4, x1, y0 + 7, p["tape"])


def scuff(img, box, p, rng, n, torn=False):
    """Spent only: scratches, chips through to a darker shell, and a torn corner off the label."""
    px = img.load()
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    for _ in range(n):
        x, y = x0 + rng.randrange(w), y0 + rng.randrange(h)
        length = rng.randrange(1, 4)
        colour = p["orange_edge"] if rng.random() < 0.5 else p["orange_light"]
        for k in range(length):
            if x + k < x1:
                px[x + k, y] = colour
    if torn:
        b0 = y0 + BAND[0]
        for yy in range(b0 + 9, y0 + BAND[1]):               # the band's lower right corner, peeled away
            for xx in range(x1 - 3 - (yy - b0 - 9), x1):
                if xx >= x0:
                    px[xx, yy] = p["orange_dark"]


def paint(variant):
    p = SPENT if variant == "spent" else NEW
    dome = {"dark": DOME_DARK, "active": DOME_LIT, "spent": DOME_SPENT}[variant]
    rng = random.Random(406)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    body_face(img, d, R_FRONT, p, "front", rng)
    body_face(img, d, R_BACK, p, "back", rng)
    body_face(img, d, R_SIDE, p, "side", rng)
    rect(d, *R_TOP, p["orange_light"])
    rect(d, R_TOP[0] + 1, R_TOP[1] + 1, R_TOP[2] - 1, R_TOP[3] - 1, p["orange"])

    # The bumper: black rubber, a light ridge round its top and a dark foot.
    x0, y0, x1, y1 = R_BASE
    rect(d, x0, y0, x1, y1, p["rubber"])
    rect(d, x0, y0, x1, y0 + 2, p["rubber_light"])
    rect(d, x0, y1 - 3, x1, y1, p["rubber_dark"])
    for x in range(x0 + 2, x1, 5):                          # moulded grip ribs
        rect(d, x, y0 + 4, x + 1, y1 - 4, p["rubber_dark"])
    rect(d, *R_RUBBER, p["rubber"])
    rect(d, R_RUBBER[0], R_RUBBER[1], R_RUBBER[2], R_RUBBER[1] + 1, p["rubber_light"])

    # The shoulder: silver reflective tape between orange edges.
    x0, y0, x1, y1 = R_SHOULDER
    rect(d, x0, y0, x1, y1, p["tape"])
    rect(d, x0, y0, x1, y0 + 1, p["orange_light"])
    rect(d, x0, y1 - 1, x1, y1, p["orange_dark"])
    rect(d, x0, y0 + 1, x1, y0 + 2, p["tape_light"])
    for x in range(x0 + 3, x1, 6):                          # the tape's diamond grid glints
        rect(d, x, y0 + 4, x + 1, y0 + 5, p["tape_light"])

    # The whip: ribbed black; the tab red; the housing grey.
    x0, y0, x1, y1 = R_WHIP
    rect(d, x0, y0, x1, y1, p["rubber"])
    for y in range(y0, y1, 2):
        rect(d, x0, y, x1, y + 1, p["rubber_light"])
    rect(d, *R_RED, p["red"])
    rect(d, R_RED[0], R_RED[1], R_RED[2], R_RED[1] + 1, p["red_light"])
    rect(d, R_RED[0], R_RED[3] - 1, R_RED[2], R_RED[3], p["red_dark"])
    rect(d, *R_GREY, p["grey"])
    rect(d, R_GREY[0], R_GREY[1], R_GREY[2], R_GREY[1] + 1, p["grey_light"])

    # The switch housing's front: grey, a slot the tab sits in (a dark hole once it is pulled), ARM lettered under it.
    x0, y0, x1, y1 = R_HOUSING
    rect(d, x0, y0, x1, y1, p["grey"])
    rect(d, x0, y0, x1, y0 + 1, p["grey_light"])
    rect(d, x0, y0, x0 + 1, y1, p["grey_light"])
    rect(d, x0, y1 - 1, x1, y1, p["rubber_dark"])
    rect(d, x0 + 6, y0 + 3, x1 - 6, y0 + 9, p["hole"] if variant == "spent" else p["rubber_dark"])
    text_centred(img, x0 + 10, y0 + 10, "ARM", p["white"] if variant != "spent" else p["white_dark"])
    # The tab's face: a white pull ring printed on the red.
    x0, y0, x1, y1 = R_TAB_FACE
    rect(d, x0, y0, x1, y1, p["red"])
    rect(d, x0 + 2, y0 + 2, x1 - 2, y1 - 2, p["white"])
    rect(d, x0 + 3, y0 + 3, x1 - 3, y1 - 3, p["red"])

    # The strobe dome: frosted glass, the lamp a darker (or white-hot) column inside, highlights up its left.
    x0, y0, x1, y1 = R_DOME_SIDE
    rect(d, x0, y0, x1, y1, dome["glass"])
    rect(d, x0 + 7, y0 + 2, x1 - 7, y1, dome["core"])
    rect(d, x0 + 9, y0 + 4, x1 - 9, y1, dome["core_hot"])
    rect(d, x0 + 2, y0 + 1, x0 + 4, y1 - 2, dome["glass_light"])
    rect(d, x1 - 2, y0, x1, y1, dome["glass_dark"])
    rect(d, x0, y1 - 1, x1, y1, dome["glass_dark"])
    x0, y0, x1, y1 = R_DOME_TOP
    rect(d, x0, y0, x1, y1, dome["glass"])
    rect(d, x0 + 4, y0 + 4, x1 - 4, y1 - 4, dome["core"])
    rect(d, x0 + 6, y0 + 6, x1 - 6, y1 - 6, dome["core_hot"])
    rect(d, x0 + 2, y0 + 2, x0 + 5, y0 + 4, dome["glass_light"])

    if variant == "spent":
        for box, n in ((R_FRONT, 18), (R_BACK, 18), (R_SIDE, 18), (R_TOP, 4), (R_SHOULDER, 6)):
            scuff(img, box, p, rng, n, torn=box is R_FRONT)
        px = img.load()
        for _ in range(30):                                  # scuffs on the bumper, worn grey
            x, y = R_BASE[0] + rng.randrange(R_BASE[2] - R_BASE[0]), R_BASE[1] + rng.randrange(16)
            px[x, y] = p["rubber_light"]
    return img


# ======================================================================================================================
# Model
# ======================================================================================================================
def uv8(box):
    x0, y0, x1, y1 = box
    return [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]


def f(box):
    return {"uv": uv8(box), "texture": "#0"}


def all6(box, skip=()):
    return {d: f(box) for d in ("north", "east", "south", "west", "up", "down") if d not in skip}


def el(name, frm, to, faces, glow=False):
    e = {"name": name, "from": list(frm), "to": list(to), "faces": faces}
    if glow:
        e["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return e


def elements(variant):
    by0, by1 = BODY_Y
    lit = variant == "active"
    out = [
        # The bumper, the case and the shoulder: square boxes (maintainer, 2026-11-09; they had cut corners).
        el("base", (BASE_W[0], BASE_Y[0], BASE_W[0]), (BASE_W[1], BASE_Y[1], BASE_W[1]), {
            "north": f(R_BASE), "south": f(R_BASE), "east": f(R_BASE), "west": f(R_BASE), "up": f(R_RUBBER), "down": f(R_RUBBER)}),
        el("case", (BODY_W[0], by0, BODY_W[0]), (BODY_W[1], by1, BODY_W[1]), {
            "south": f(R_FRONT), "north": f(R_BACK), "east": f(R_SIDE), "west": f(R_SIDE), "up": f(R_TOP), "down": f(R_TOP)}),
        el("shoulder", (SHOULDER_W[0], SHOULDER_Y[0], SHOULDER_W[0]), (SHOULDER_W[1], SHOULDER_Y[1], SHOULDER_W[1]), {
            "south": f(R_SHOULDER), "north": f(R_SHOULDER), "east": f(R_SHOULDER), "west": f(R_SHOULDER), "up": f(R_TOP)}),
        # The strobe on the left, on a black ring: a wide lower dome and a smaller cap.
        el("strobe_ring", (6.1, 11.5, 6.65), (8.8, 12.0, 9.35), all6(R_RUBBER, skip=("down",))),
        el("strobe_dome", (6.3, 12.0, 6.85), (8.6, 13.5, 9.15), {
            d: f(R_DOME_SIDE) for d in ("north", "east", "south", "west")} | {"up": f(R_DOME_TOP)}, glow=lit),
        el("strobe_cap", (6.75, 13.5, 7.3), (8.15, 14.1, 8.7), {
            d: f(R_DOME_SIDE) for d in ("north", "east", "south", "west")} | {"up": f(R_DOME_TOP)}, glow=lit),
        # The stubby whip on the right, in a rubber boot, with a ball tip.
        el("whip_boot", (8.8, 11.5, 7.4), (10.0, 12.75, 8.6), all6(R_RUBBER, skip=("down",))),
        el("whip", (9.1, 12.75, 7.7), (9.7, 18.0, 8.3), all6(R_WHIP, skip=("down",))),
        el("whip_tip", (9.0, 18.0, 7.6), (9.8, 18.6, 8.4), all6(R_RUBBER)),
        # The arming switch on the front, under the shoulder: a grey housing between two black guards.
        el("switch_housing", (6.75, 7.75, 10.5), (9.25, 9.75, 10.9), {
            "south": f(R_HOUSING), "east": f(R_GREY), "west": f(R_GREY), "up": f(R_GREY), "down": f(R_GREY)}),
        el("guard_left", (6.25, 7.5, 10.5), (6.75, 10.0, 11.4), all6(R_RUBBER, skip=("north",))),
        el("guard_right", (9.25, 7.5, 10.5), (9.75, 10.0, 11.4), all6(R_RUBBER, skip=("north",))),
    ]
    if variant != "spent":  # The tab: a red lever standing out of its slot. Pulled on the spent one, leaving the hole.
        out.append(el("arming_tab", (7.55, 8.5, 10.9), (8.45, 9.5, 11.7), {
            "south": f(R_TAB_FACE), "east": f(R_RED), "west": f(R_RED), "up": f(R_RED), "down": f(R_RED)}))
    return out


# As the marine handheld's (so it is the same size in the hand), lifted 1.5 model px because this stands on y 0.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3.25, 1.5], "scale": [0.5625, 0.5625, 0.5625]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3.25, 1.5], "scale": [0.5625, 0.5625, 0.5625]},
    "firstperson_righthand": {"rotation": [-8, 12, 6], "translation": [-1.5, 1.4, -1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [-8, -12, -6], "translation": [-1.5, 1.4, -1], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    # The model runs y 0-18.6 (centre 9.3): these centre it in the slot, a touch from above to show the dome.
    "gui": {"rotation": [20, -30, 0], "translation": [0, -1.1, 0], "scale": [0.82, 0.82, 0.82]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -1.1, 0], "scale": [0.82, 0.82, 0.82]},
}

TEXTURES = {"dark": "distress_beacon", "active": "distress_beacon_active", "spent": "spent_distress_beacon"}


def model(variant):
    tex = "radiocraft:item/" + TEXTURES[variant]
    m = {
        "credit": "Made with tools/distress_beacon_assets.py (after a 1980s-90s EPIRB)",
        "texture_size": [128, 128],
        "textures": {"0": tex, "particle": tex},
        "elements": elements(variant),
        "display": DISPLAY,
    }
    if variant == "dark":
        m["overrides"] = [{"predicate": {"radiocraft:active": 1}, "model": "radiocraft:item/distress_beacon_active"}]
    return m


def main():
    for variant, name in (("dark", "distress_beacon"), ("active", "distress_beacon_active"), ("spent", "spent_distress_beacon")):
        path = os.path.join(ROOT, "textures", "item", TEXTURES[variant] + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        paint(variant).save(path)
        write_json(os.path.join(ROOT, "models", "item", name + ".json"), model(variant))
    print("ok")


if __name__ == "__main__":
    main()
