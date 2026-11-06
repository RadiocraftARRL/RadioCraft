"""The commercial handhelds' 3D item models, with a case that takes dye.

Basic Commercial Handheld: the maintainer's own model (2026-11-06; body, battery, antenna, two knobs, PTT) and its
64x64 texture. The texture's original art stays where the maintainer drew it; this script only adds, in empty parts of
the sheet, a light grey copy of the case (the body's front and its sides) for the case faces to tint, and writes the
model with those faces tinted (tintindex 0) and the speaker panel, the three LEDs and the bottom seam laid over the
front untinted. Undyed, the item colour handler tints the copy back to the maintainer's grey
(BASIC_DEFAULT_COLOUR below = CommercialRadioModel's default for the basic handheld). If the maintainer redraws the
front or the sides, run this again to refresh the copies.

Upgraded Commercial Handheld: a 1990s professional handheld (tall black body, rubber duck antenna on the top left,
channel and volume knobs on top, speaker grille with a "RC-300" plate, a small LCD strip and a 4x4 keypad with A-D in
the right column), drawn here as a 128x128 texture (8 px per model unit). Its case faces are light grey and tinted;
the grille, plate, LCD, keypad, battery, antenna and knobs are separate untinted parts.

Both screens (CommercialHandheldScreen) draw the same texture regions, so keep REGIONS in step with that class.
Run: python tools/commercial_handheld_models.py [--preview DIR]
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "radiocraft")
TEX = os.path.join(ROOT, "textures", "item")
MODELS = os.path.join(ROOT, "models", "item")

# ----------------------------------------------------------------------------------------------------------------------
# Basic (the maintainer's model; 4 px per model unit)
# ----------------------------------------------------------------------------------------------------------------------
BASIC_FRONT = (0, 0, 20, 34)          # the maintainer's body front
BASIC_SIDES = (53, 1, 63, 11)         # the maintainer's body sides
BASIC_FRONT_CASE = (20, 0, 40, 34)    # written here: the front, light grey, for tinting
BASIC_SIDES_CASE = (40, 0, 50, 10)    # written here: the sides, light grey, for tinting
LIGHTEN = 2.6                         # case grey -> tintable light grey; the default colour divides it back out
BASIC_CASE_GREY = (0x55, 0x56, 0x58)  # the maintainer's case front


def grey(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def lighten(img, src, dst):
    x0, y0, x1, y1 = src
    dx, dy = dst[0], dst[1]
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = img.getpixel((x, y))
            v = min(255, round(grey((r, g, b)) * LIGHTEN))
            img.putpixel((dx + x - x0, dy + y - y0), (v, v, v, a))


def basic_default_colour():
    """The tint that turns the light copy of the front back into the maintainer's grey."""
    v = min(255, round(grey(BASIC_CASE_GREY) * LIGHTEN))
    return tuple(min(255, round(c * 255 / v)) for c in BASIC_CASE_GREY)


def uv4(x0, y0, x1, y1):
    return [x0 / 4, y0 / 4, x1 / 4, y1 / 4]


def faces(uv, tint=False, only=None, south=None):
    out = {}
    for f in ("north", "east", "south", "west", "up", "down"):
        if only and f not in only:
            continue
        face = {"uv": south if (f == "south" and south) else uv, "texture": "#0"}
        if tint:
            face["tintindex"] = 0
        out[f] = face
    return out


def overlay(name, x0, y0, x1, y1, uv, z):
    """A flat, untinted part laid just over the front (south) face: model x0..x1, y0..y1."""
    return {"name": name, "from": [x0, y0, z], "to": [x1, y1, z], "faces": {"south": {"uv": uv, "texture": "#0"}}}


def basic():
    path = os.path.join(TEX, "basic_commercial_handheld.png")
    img = Image.open(path).convert("RGBA")
    lighten(img, BASIC_FRONT, BASIC_FRONT_CASE)
    lighten(img, BASIC_SIDES, BASIC_SIDES_CASE)
    img.save(path)

    front_case = uv4(*BASIC_FRONT_CASE)
    sides_case = uv4(*BASIC_SIDES_CASE)
    z = 9.26  # the front is at z 9.25
    body_x0, body_top = 5.5, 13.5

    def px(fx, fy):  # front texture pixel -> model x, y
        return body_x0 + fx / 4, body_top - fy / 4

    def plate(name, fx0, fy0, fx1, fy1):
        x0, y1 = px(fx0, fy0)
        x1, y0 = px(fx1, fy1)
        return overlay(name, x0, y0, x1, y1, uv4(fx0, fy0, fx1, fy1), z)

    elements = [
        {"name": "body", "from": [5.5, 5, 5.75], "to": [10.5, 13.5, 9.25], "faces": faces(sides_case, True, south=front_case)},
        {"name": "battery", "from": [5.5, 1.5, 5.75], "to": [10.5, 5, 9.25],
         "faces": faces(uv4(53, 13, 63, 23), south=uv4(0, 36, 20, 50))},
        {"name": "antenna_collar", "from": [5.9, 13.5, 7.3], "to": [7.3, 15, 8.7], "faces": faces(uv4(59, 31, 63, 35))},
        {"name": "antenna", "from": [6.2, 15, 7.6], "to": [7, 21.5, 8.4], "faces": faces(uv4(53, 25, 57, 29))},
        {"name": "channel_knob", "from": [8.1, 13.5, 7.5], "to": [9.1, 14.4, 8.5], "faces": faces(uv4(59, 25, 63, 29))},
        {"name": "volume_knob", "from": [9.4, 13.5, 7.5], "to": [10.4, 14.4, 8.5], "faces": faces(uv4(59, 25, 63, 29))},
        {"name": "ptt", "from": [5.2, 8.5, 7.4], "to": [5.5, 11.5, 8.6], "faces": faces(uv4(53, 31, 57, 35))},
        plate("speaker_panel", 1, 1, 19, 14),
        plate("tx_led", 2, 15, 3, 16),
        plate("rx_led", 4, 15, 5, 16),
        plate("power_led", 6, 15, 7, 16),
        plate("seam", 0, 32, 20, 34),
    ]
    with open(os.path.join(MODELS, "basic_commercial_handheld.json")) as f:
        old = json.load(f)
    model = {
        "format_version": old.get("format_version", "1.21"),
        "credit": "Made by the RadioCraft maintainer (W1BTR); case tinting from tools/commercial_handheld_models.py",
        "texture_size": [64, 64],
        "textures": {"0": "radiocraft:item/basic_commercial_handheld", "particle": "radiocraft:item/basic_commercial_handheld"},
        "elements": elements,
        "gui_light": "front",
        "display": old["display"],
    }
    write_json(os.path.join(MODELS, "basic_commercial_handheld.json"), model)
    return img


# ----------------------------------------------------------------------------------------------------------------------
# Upgraded (8 px per model unit, 128x128)
# ----------------------------------------------------------------------------------------------------------------------
U = 8
CLEAR = (0, 0, 0, 0)
# Light greys for the tinted case (the default colour, 0x2B2D30, turns them radio black).
CASE, CASE_EDGE = (232, 232, 232, 255), (200, 200, 200, 255)
SIDE, SIDE_EDGE = (212, 212, 212, 255), (182, 182, 182, 255)
TOP, TOP_EDGE = (222, 222, 222, 255), (190, 190, 190, 255)
PANEL = (24, 25, 27, 255)
SLOT_DARK, SLOT_LIGHT = (12, 13, 14, 255), (58, 60, 64, 255)
PLATE, PLATE_INK = (40, 42, 46, 255), (205, 209, 214, 255)
LCD_FRAME, LCD_GLASS, LCD_SHINE = (16, 17, 18, 255), (138, 160, 112, 255), (156, 178, 128, 255)
KEYPAD = (20, 21, 23, 255)
KEY, KEY_TOP, KEY_SHADOW, KEY_INK, KEY_INK_FN = (74, 77, 83, 255), (98, 101, 108, 255), (44, 46, 50, 255), (236, 236, 236, 255), (240, 168, 56, 255)
BATTERY, BATTERY_LIGHT, BATTERY_DARK = (36, 37, 41, 255), (56, 58, 63, 255), (22, 23, 26, 255)
RUBBER, RUBBER_LIGHT = (20, 20, 22, 255), (40, 41, 44, 255)
KNOB, KNOB_RIDGE, KNOB_TOP, KNOB_MARK = (34, 35, 38, 255), (18, 18, 20, 255), (46, 47, 51, 255), (220, 220, 220, 255)
METAL = (140, 144, 150, 255)
LED_OFF = (90, 30, 26, 255)

# Front layout, in front pixels (40 wide, 84 high: the body is 5 x 10.5 units).
FRONT_W, FRONT_H = 40, 84
GRILLE = (3, 3, 37, 30)
PLATE_BOX = (7, 4, 33, 11)
LCD_BOX = (3, 32, 37, 46)
KEYPAD_BOX = (4, 48, 37, 82)
KEY_W, KEY_H, KEY_PITCH_X, KEY_PITCH_Y, KEY_X0, KEY_Y0 = 7, 7, 8, 8, 5, 49
KEYS = ["123A", "456B", "789C", "*0#D"]

# Sheet regions (x, y, w, h).
REGIONS = {
    "front_details": (0, 0, FRONT_W, FRONT_H),
    "front_case": (40, 0, FRONT_W, FRONT_H),
    "side_case": (80, 0, 26, FRONT_H),
    "top_case": (40, 84, 40, 26),
    "battery_front": (0, 84, 40, 32),
    "battery_side": (80, 84, 26, 32),
    "battery_plain": (40, 110, 40, 18),
    "knob_side": (106, 0, 8, 8),
    "knob_top": (106, 8, 8, 8),
    "antenna": (114, 0, 4, 8),
    "collar": (114, 8, 8, 8),
    "ptt": (106, 16, 8, 8),
    "clip": (114, 16, 8, 8),
    "led": (122, 0, 2, 2),
}

GLYPHS = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["##.", "..#", ".#.", "#..", "###"], "3": ["##.", "..#", ".#.", "..#", "##."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "##.", "..#", "##."],
    "6": [".##", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "##."],
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "R": ["##.", "#.#", "##.", "#.#", "#.#"], "-": ["...", "...", "###", "...", "..."],
    "*": ["...", "#.#", ".#.", "#.#", "..."], "#": ["#.#", "###", "#.#", "###", "#.#"],
}


class Sheet:
    def __init__(self, size):
        self.img = Image.new("RGBA", (size, size), CLEAR)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.img.putpixel((x, y), c)

    def bordered(self, x, y, w, h, fill, edge):
        self.rect(x, y, x + w, y + h, edge)
        self.rect(x + 1, y + 1, x + w - 1, y + h - 1, fill)

    def text(self, x, y, s, c):
        for ch in s:
            rows = GLYPHS[ch]
            for gy, row in enumerate(rows):
                for gx, bit in enumerate(row):
                    if bit == "#":
                        self.img.putpixel((x + gx, y + gy), c)
            x += 4


def upgraded():
    s = Sheet(128)
    # Front details (untinted; the case around them is see-through here and comes from front_case).
    s.rect(*GRILLE, PANEL)
    for y in range(13, GRILLE[3] - 1):
        s.rect(GRILLE[0] + 2, y, GRILLE[2] - 2, y + 1, SLOT_DARK if y % 2 else SLOT_LIGHT)
    s.rect(*PLATE_BOX, PLATE)
    label = "RC-300"
    width = len(label) * 4 - 1
    s.text(PLATE_BOX[0] + (PLATE_BOX[2] - PLATE_BOX[0] - width) // 2, PLATE_BOX[1] + 1, label, PLATE_INK)
    s.rect(*LCD_BOX, LCD_FRAME)
    s.rect(LCD_BOX[0] + 1, LCD_BOX[1] + 1, LCD_BOX[2] - 1, LCD_BOX[3] - 1, LCD_GLASS)
    s.rect(LCD_BOX[0] + 1, LCD_BOX[1] + 1, LCD_BOX[2] - 1, LCD_BOX[1] + 2, LCD_SHINE)
    s.rect(*KEYPAD_BOX, KEYPAD)
    for r, row in enumerate(KEYS):
        for c, ch in enumerate(row):
            kx, ky = KEY_X0 + c * KEY_PITCH_X, KEY_Y0 + r * KEY_PITCH_Y
            s.rect(kx, ky, kx + KEY_W, ky + KEY_H, KEY)
            s.rect(kx, ky, kx + KEY_W, ky + 1, KEY_TOP)
            s.rect(kx, ky + KEY_H - 1, kx + KEY_W, ky + KEY_H, KEY_SHADOW)
            s.text(kx + 2, ky + 1, ch, KEY_INK_FN if c == 3 else KEY_INK)
    # The tinted case.
    x, y, w, h = REGIONS["front_case"]
    s.bordered(x, y, w, h, CASE, CASE_EDGE)
    x, y, w, h = REGIONS["side_case"]
    s.bordered(x, y, w, h, SIDE, SIDE_EDGE)
    x, y, w, h = REGIONS["top_case"]
    s.bordered(x, y, w, h, TOP, TOP_EDGE)
    # Battery: ridged pack with a latch.
    x, y, w, h = REGIONS["battery_front"]
    s.rect(x, y, x + w, y + h, BATTERY)
    s.rect(x, y, x + w, y + 1, BATTERY_DARK)
    for ry in range(y + 4, y + h - 3, 4):
        s.rect(x + 2, ry, x + w - 2, ry + 1, BATTERY_LIGHT)
        s.rect(x + 2, ry + 1, x + w - 2, ry + 2, BATTERY_DARK)
    s.rect(x + 14, y + h - 3, x + 26, y + h - 1, BATTERY_LIGHT)
    x, y, w, h = REGIONS["battery_side"]
    s.rect(x, y, x + w, y + h, BATTERY)
    s.rect(x, y, x + w, y + 1, BATTERY_DARK)
    x, y, w, h = REGIONS["battery_plain"]
    s.rect(x, y, x + w, y + h, BATTERY)
    # Small parts.
    x, y, w, h = REGIONS["knob_side"]
    s.rect(x, y, x + w, y + h, KNOB)
    for kx in range(x, x + w, 2):
        s.rect(kx, y, kx + 1, y + h, KNOB_RIDGE)
    x, y, w, h = REGIONS["knob_top"]
    s.rect(x, y, x + w, y + h, KNOB_TOP)
    s.rect(x + 3, y, x + 5, y + 4, KNOB_MARK)
    x, y, w, h = REGIONS["antenna"]
    s.rect(x, y, x + w, y + h, RUBBER)
    s.rect(x + 1, y, x + 2, y + h, RUBBER_LIGHT)
    x, y, w, h = REGIONS["collar"]
    s.rect(x, y, x + w, y + h, METAL)
    x, y, w, h = REGIONS["ptt"]
    s.rect(x, y, x + w, y + h, KNOB)
    for py in range(y + 1, y + h, 2):
        s.rect(x, py, x + w, py + 1, KNOB_RIDGE)
    x, y, w, h = REGIONS["clip"]
    s.rect(x, y, x + w, y + h, BATTERY_DARK)
    x, y, w, h = REGIONS["led"]
    s.rect(x, y, x + w, y + h, LED_OFF)
    s.img.save(os.path.join(TEX, "upgraded_commercial_handheld.png"))

    def uv(name):
        x, y, w, h = REGIONS[name]
        return [x * 16 / 128, y * 16 / 128, (x + w) * 16 / 128, (y + h) * 16 / 128]

    def front_uv(fx0, fy0, fx1, fy1):
        return [fx0 * 16 / 128, fy0 * 16 / 128, fx1 * 16 / 128, fy1 * 16 / 128]

    body = {"x0": 5.5, "x1": 10.5, "y0": 5.5, "y1": 16.0, "z0": 6.0, "z1": 9.25}
    z = body["z1"] + 0.01

    def plate(name, box):
        fx0, fy0, fx1, fy1 = box
        return overlay(name, body["x0"] + fx0 / U, body["y1"] - fy1 / U, body["x0"] + fx1 / U, body["y1"] - fy0 / U, front_uv(*box), z)

    case_faces = {
        "north": {"uv": uv("front_case"), "texture": "#0", "tintindex": 0},
        "south": {"uv": uv("front_case"), "texture": "#0", "tintindex": 0},
        "east": {"uv": uv("side_case"), "texture": "#0", "tintindex": 0},
        "west": {"uv": uv("side_case"), "texture": "#0", "tintindex": 0},
        "up": {"uv": uv("top_case"), "texture": "#0", "tintindex": 0},
        "down": {"uv": uv("top_case"), "texture": "#0", "tintindex": 0},
    }
    battery_faces = faces(uv("battery_plain"))
    battery_faces["south"]["uv"] = uv("battery_front")
    battery_faces["east"]["uv"] = uv("battery_side")
    battery_faces["west"]["uv"] = uv("battery_side")
    knob_faces = faces(uv("knob_side"))
    knob_faces["up"]["uv"] = uv("knob_top")
    elements = [
        {"name": "body", "from": [body["x0"], body["y0"], body["z0"]], "to": [body["x1"], body["y1"], body["z1"]], "faces": case_faces},
        {"name": "battery", "from": [5.5, 1.5, 6.0], "to": [10.5, 5.5, 9.25], "faces": battery_faces},
        {"name": "belt_clip", "from": [6.5, 7.0, 5.6], "to": [9.5, 13.0, 6.0], "faces": faces(uv("clip"))},
        {"name": "antenna_collar", "from": [5.8, 16.0, 6.9], "to": [7.3, 17.25, 8.4], "faces": faces(uv("collar"))},
        {"name": "antenna", "from": [6.15, 17.25, 7.25], "to": [6.95, 23.75, 8.05], "faces": faces(uv("antenna"))},
        {"name": "antenna_tip", "from": [6.05, 23.75, 7.15], "to": [7.05, 24.25, 8.15], "faces": faces(uv("antenna"))},
        {"name": "led", "from": [7.35, 16.0, 7.45], "to": [7.6, 16.2, 7.7], "faces": faces(uv("led"))},
        {"name": "channel_knob", "from": [7.75, 16.0, 6.95], "to": [9.05, 17.4, 8.25], "faces": knob_faces},
        {"name": "volume_knob", "from": [9.25, 16.0, 7.1], "to": [10.25, 17.1, 8.1], "faces": knob_faces},
        {"name": "ptt", "from": [5.2, 10.5, 6.9], "to": [5.5, 13.5, 8.35], "faces": faces(uv("ptt"))},
        plate("grille", GRILLE),
        plate("lcd", LCD_BOX),
        plate("keypad", KEYPAD_BOX),
    ]
    # The radio is 22.75 units tall (1.5 to 24.25), centred 4.9 above the slot's middle: scaled and moved down to fit.
    display = {
        "thirdperson_righthand": {"translation": [0, 2.5, 1.5], "scale": [0.5625, 0.5625, 0.5625]},
        "thirdperson_lefthand": {"translation": [0, 2.5, 1.5], "scale": [0.5625, 0.5625, 0.5625]},
        "firstperson_righthand": {"rotation": [-8, 12, 6], "translation": [-1.5, 0.5, -1], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [-8, -12, -6], "translation": [-1.5, 0.5, -1], "scale": [0.6, 0.6, 0.6]},
        "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [10, -25, 0], "translation": [0, -3.2, 0], "scale": [0.64, 0.64, 0.64]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -3.2, 0], "scale": [0.64, 0.64, 0.64]},
    }
    model = {
        "credit": "RadioCraft placeholder from tools/commercial_handheld_models.py (see doc/ModelDesignBounty.md)",
        "texture_size": [128, 128],
        "textures": {"0": "radiocraft:item/upgraded_commercial_handheld", "particle": "radiocraft:item/upgraded_commercial_handheld"},
        "elements": elements,
        "gui_light": "front",
        "display": display,
    }
    write_json(os.path.join(MODELS, "upgraded_commercial_handheld.json"), model)
    return s.img


def write_json(path, data):
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ----------------------------------------------------------------------------------------------------------------------
# Preview (for people, not the game): each front at every dye colour, as the game multiplies the tint in.
# ----------------------------------------------------------------------------------------------------------------------
DYES = [("undyed", None), ("white", 0xF9FFFE), ("orange", 0xF9801D), ("magenta", 0xC74EBD), ("light_blue", 0x3AB3DA),
        ("yellow", 0xFED83D), ("lime", 0x80C71F), ("pink", 0xF38BAA), ("gray", 0x474F52), ("light_gray", 0x9D9D97),
        ("cyan", 0x169C9C), ("purple", 0x8932B8), ("blue", 0x3C44AA), ("brown", 0x835432), ("green", 0x5E7C16),
        ("red", 0xB02E26), ("black", 0x1D1D21)]


def tint(img, colour):
    r0, g0, b0 = (colour >> 16) & 255, (colour >> 8) & 255, colour & 255
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r * r0 // 255, g * g0 // 255, b * b0 // 255, a)
    return out


def crop(img, x, y, w, h):
    return img.crop((x, y, x + w, y + h))


def preview(directory, basic_img, upgraded_img):
    from PIL import ImageDraw
    bd = basic_default_colour()
    basic_default = (bd[0] << 16) | (bd[1] << 8) | bd[2]
    upgraded_default = 0x2B2D30
    tiles = []
    for name, colour in DYES:
        # Basic: tinted front copy, the untinted panel, LEDs and seam over it, the battery below.
        b = Image.new("RGBA", (20, 48), (0, 0, 0, 0))
        b.alpha_composite(tint(crop(basic_img, *BASIC_FRONT_CASE[:2], 20, 34), colour or basic_default), (0, 0))
        for box in ((1, 1, 19, 14), (2, 15, 3, 16), (4, 15, 5, 16), (6, 15, 7, 16), (0, 32, 20, 34)):
            b.alpha_composite(crop(basic_img, box[0], box[1], box[2] - box[0], box[3] - box[1]), (box[0], box[1]))
        b.alpha_composite(crop(basic_img, 0, 36, 20, 14), (0, 34))
        b = b.resize((20 * 8, 48 * 8), Image.NEAREST)
        u = Image.new("RGBA", (40, 116), (0, 0, 0, 0))
        u.alpha_composite(tint(crop(upgraded_img, *REGIONS["front_case"]), colour or upgraded_default), (0, 0))
        u.alpha_composite(crop(upgraded_img, *REGIONS["front_details"]), (0, 0))
        u.alpha_composite(crop(upgraded_img, *REGIONS["battery_front"]), (0, 84))
        u = u.resize((40 * 4, 116 * 4), Image.NEAREST)
        tiles.append((name, b, u))
    cell_w, cell_h = 360, 500
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (200, 205, 210, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, b, u) in enumerate(tiles):
        cx, cy = (i % cols) * cell_w, (i // cols) * cell_h
        sheet.alpha_composite(b, (cx + 10, cy + 30))
        sheet.alpha_composite(u, (cx + 185, cy + 20))
        draw.text((cx + 10, cy + 6), name, fill=(0, 0, 0, 255))
    os.makedirs(directory, exist_ok=True)
    sheet.save(os.path.join(directory, "commercial_handhelds_dyes.png"))
    basic_img.resize((64 * 8, 64 * 8), Image.NEAREST).save(os.path.join(directory, "basic_commercial_handheld_texture.png"))
    upgraded_img.resize((128 * 4, 128 * 4), Image.NEAREST).save(os.path.join(directory, "upgraded_commercial_handheld_texture.png"))


def main():
    b = basic()
    u = upgraded()
    print("basic default colour 0x%02X%02X%02X" % basic_default_colour())
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1], b, u)


if __name__ == "__main__":
    main()
