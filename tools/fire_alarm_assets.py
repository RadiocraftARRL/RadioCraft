"""Art for the radio fire alarm parts (2026-11-11), after the photos the maintainer picked: a Simplex-style red wall
cabinet (crinkle red, instruction card top left, a round black meter in the middle, DRILL SWITCH toggle top right, key
lock, a clear tag pocket lower right, the red TROUBLE lamp lower left over white label plates with RESET and
NORMAL/SILENCE toggles; inside, the red button is SILENCE), a small red pull station (white PULL DOWN bar across the top, a white FIRE bar between two red
cheeks lettered LOCAL and ALARM), a vintage red panic button on a steel wall plate, and, for the box's screen, the inside
of an old alarm panel after a Kidde C-3 (cream circuit board with relays, fuse clips, capacitors and terminal strips,
a cream control strip with lamps, toggles, spare fuses), squared up like a block. No brand names.

Writes, under src/main/resources/assets/radiocraft/:
  textures/block/fire_alarm_box.png         128x128 (8 px a model unit): the cabinet's faces and its small parts
  models/block/fire_alarm_box.json          on the south wall, front north; models/item/fire_alarm_box.json
  models/block/fire_alarm_box_lamp.json     only the lit TROUBLE lamp (an overlay the blockstate adds while it waits)
  textures/block/pull_station.png           128x128
  models/block/pull_station.json            on the south wall; _pulled with the handle down and out
  models/block/pull_station_pulled.json     models/item/pull_station.json
  textures/block/panic_button.png           128x128
  models/block/panic_button.json            lying on the floor like vanilla's button (the blockstate turns it onto a
  models/block/panic_button_pressed.json    wall or under a desk); models/item/panic_button.json
  textures/gui/fire_alarm/fire_alarm_box.png   176x176: the box's screen, without its moving parts
  gui_layouts/fire_alarm_box.json           HfPanel's format (tools/hf_gui_proposals/RUNTIME_SPEC.md)

The blockstates are written by the parent code's tools (not here). Needs Pillow.
Run: python tools/fire_alarm_assets.py [--preview DIR]
(--preview also writes the screen with its moving parts in two states, and front views of the block models.)
"""
import json
import math
import os
import random
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
    ".": "000000000000010", " ": "000000000000000",
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
                    X, Y = x + i * 4 + gx, y + gy
                    if 0 <= X < img.width and 0 <= Y < img.height:
                        px[X, Y] = colour


def text_centred(img, cx, y, s, colour):
    text(img, int(round(cx - text_width(s) / 2.0)), y, s, colour)


def text_down(img, x, y, s, colour):
    """Letters stacked top to bottom, 6 px apart."""
    for i, ch in enumerate(s):
        text(img, x, y + i * 6, ch, colour)


def rect(d, x0, y0, x1, y1, fill):
    """Fills x0..x1-1, y0..y1-1."""
    if x1 > x0 and y1 > y0:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)


def disc(d, cx, cy, r, fill):
    d.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], fill=fill)


def bevel(d, x0, y0, x1, y1, light, dark):
    rect(d, x0, y0, x1, y0 + 1, light)
    rect(d, x0, y0, x0 + 1, y1, light)
    rect(d, x0, y1 - 1, x1, y1, dark)
    rect(d, x1 - 1, y0, x1, y1, dark)


def crinkle(img, x0, y0, x1, y1, base, spread, seed):
    """A crinkle (wrinkle) paint: the base colour, each pixel a little lighter or darker."""
    rnd = random.Random(seed)
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = rnd.randint(-spread, spread)
            px[x, y] = tuple(max(0, min(255, c + k)) for c in base[:3]) + (255,)


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


# ---- Model helpers: textures are 128 x 128 at 8 px a model unit, so a UV is pixels / 8 ------------------------------
def uv(x0, y0, x1, y1):
    return [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]


def element(name, frm, to, faces, texture="#0"):
    """faces: face name -> pixel rect (x0, y0, x1, y1) on the texture."""
    return {"name": name, "from": list(frm), "to": list(to),
            "faces": {f: {"uv": uv(*r), "texture": texture} for f, r in faces.items()}}


def all_faces(r):
    return {f: r for f in ("north", "east", "south", "west", "up", "down")}


def block_json(credit, texture, elements, **extra):
    data = {"credit": credit, "parent": "block/block", "texture_size": [128, 128],
            "textures": {"0": "radiocraft:block/" + texture, "particle": "radiocraft:block/" + texture},
            "elements": elements}
    data.update(extra)
    return data


# Colours
RED, RED_LIGHT, RED_DARK, RED_DEEP = (176, 30, 28, 255), (206, 58, 52, 255), (124, 18, 18, 255), (86, 12, 12, 255)
WHITE, WHITE_DARK, INK = (236, 232, 222, 255), (186, 182, 172, 255), (30, 28, 28, 255)
BLACK, BLACK_LIGHT = (24, 24, 26, 255), (70, 70, 74, 255)
SILVER, SILVER_LIGHT, SILVER_DARK = (176, 178, 182, 255), (222, 224, 228, 255), (112, 114, 118, 255)
LAMP_DARK, LAMP_RIM = (110, 20, 18, 255), (60, 10, 10, 255)
LAMP_LIT, LAMP_LIT_HOT = (255, 52, 36, 255), (255, 170, 150, 255)
POCKET, POCKET_EDGE = (190, 204, 210, 255), (150, 164, 172, 255)
TAG = (232, 210, 170, 255)
METER_FACE = (238, 234, 220, 255)

# ======================================================================================================================
# The fire alarm box: a red wall cabinet, 14 wide x 11 tall x 5 deep, against the south wall
# ======================================================================================================================
BOX_FROM, BOX_TO = (1.0, 2.5, 11.0), (15.0, 13.5, 16.0)
BOX_FRONT = (0, 0, 112, 88)          # the north face, as the viewer sees it (x 15 at its left)
BOX_SIDE = (0, 88, 40, 128)          # east and west (5 deep x 5 tall shown: stretched, crinkle only)
BOX_TOP = (40, 88, 112, 128)         # up, down, south
T_METER = (112, 0, 128, 16)          # the meter's face, on its bezel
T_BLACK = (112, 16, 120, 24)
T_SILVER = (120, 16, 128, 24)
T_LAMP = (112, 24, 120, 32)          # the TROUBLE lamp, unlit
T_LAMP_LIT = (120, 24, 128, 32)
T_POCKET = (112, 32, 128, 64)        # the tag pocket's front (16 x 32)
T_LOCK = (112, 64, 120, 72)
T_RED = (120, 64, 128, 72)


def box_x(px):
    """Front texture pixel x (from the viewer's left) to model x."""
    return BOX_TO[0] - px / 8.0


def box_y(py):
    return BOX_TO[1] - py / 8.0


# Where the parts are on the front, in front pixels (112 x 88).
BF_CARD = (8, 7, 38, 30)
BF_METER = (44, 10, 64, 30)          # the meter bezel's square (round, painted)
BF_DRILL = (86, 12, 94, 18)          # the drill switch's toggle; its plate below
BF_LOCK = (92, 36, 100, 44)
BF_POCKET = (82, 50, 102, 82)
BF_LAMP = (12, 56, 20, 64)
BF_RESET = (40, 58, 44, 62)          # the RESET toggle
BF_SILENCE = (64, 58, 68, 62)        # the NORMAL / SILENCE toggle


def box_texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = BOX_FRONT
    crinkle(img, x0, y0, x1, y1, RED, 10, 7)
    bevel(d, x0, y0, x1, y1, RED_LIGHT, RED_DARK)
    # The instruction card, its lines too small to read.
    cx0, cy0, cx1, cy1 = BF_CARD
    rect(d, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, INK)
    rect(d, cx0, cy0, cx1, cy1, WHITE)
    rect(d, cx0 + 3, cy0 + 2, cx1 - 3, cy0 + 3, INK)
    for y in range(cy0 + 5, cy1 - 1, 2):
        rect(d, cx0 + 2, y, cx1 - 2 - (y * 7 % 6), y + 1, (120, 116, 110, 255))
    # The meter's place (the bezel is an element; under it, a shadow).
    mx0, my0, mx1, my1 = BF_METER
    disc(d, (mx0 + mx1) // 2 + 1, (my0 + my1) // 2 + 1, 10, RED_DARK)
    # The drill switch: its toggle bushing and the white plate under it.
    rect(d, 80, 21, 100, 26, WHITE)
    bevel(d, 80, 21, 100, 26, WHITE, WHITE_DARK)
    rect(d, 83, 23, 97, 24, INK)                 # DRILL SWITCH, as a stroke
    disc(d, 90, 15, 4, SILVER_DARK)
    # The key lock's escutcheon.
    disc(d, 97, 41, 6, SILVER_DARK)
    # The tag pocket's shadow.
    rect(d, BF_POCKET[0] + 1, BF_POCKET[1] + 1, BF_POCKET[2] + 1, BF_POCKET[3] + 1, RED_DARK)
    # The TROUBLE lamp's bezel and plate.
    disc(d, 16, 60, 6, BLACK)
    rect(d, 6, 67, 26, 72, WHITE)
    rect(d, 9, 69, 23, 70, INK)                  # TROUBLE
    # RESET: a toggle and its plate.
    disc(d, 42, 60, 3, SILVER_DARK)
    rect(d, 32, 67, 52, 72, WHITE)
    rect(d, 36, 69, 48, 70, INK)
    # NORMAL above and SILENCE below a toggle.
    rect(d, 56, 48, 76, 53, WHITE)
    rect(d, 59, 50, 73, 51, INK)
    disc(d, 66, 60, 3, SILVER_DARK)
    rect(d, 56, 67, 76, 72, WHITE)
    rect(d, 59, 69, 73, 70, INK)
    # The maker's plate, bottom middle.
    rect(d, 36, 77, 70, 84, (220, 220, 214, 255))
    bevel(d, 36, 77, 70, 84, WHITE, (140, 140, 136, 255))
    rect(d, 39, 79, 67, 80, INK)
    rect(d, 42, 81, 64, 82, (90, 90, 90, 255))
    # A screw head, left of the meter, as on the photo.
    rect(d, 30, 40, 32, 42, SILVER_DARK)
    # Sides, top: crinkle red.
    crinkle(img, *BOX_SIDE, RED, 10, 8)
    crinkle(img, *BOX_TOP, RED, 10, 9)
    # Small parts.
    sx0, sy0, sx1, sy1 = T_METER
    rect(d, sx0, sy0, sx1, sy1, BLACK)
    disc(d, sx0 + 8, sy0 + 8, 7, BLACK_LIGHT)
    disc(d, sx0 + 8, sy0 + 8, 6, METER_FACE)
    rect(d, sx0 + 2, sy0 + 10, sx1 - 2, sy1 - 2, BLACK)   # the black lower half under the scale
    for a in range(-50, 51, 20):
        r = math.radians(a)
        X, Y = sx0 + 8 + 5 * math.sin(r), sy0 + 9 - 5 * math.cos(r)
        img.putpixel((int(X), int(Y)), INK)
    d.line([sx0 + 8, sy0 + 9, sx0 + 5, sy0 + 5], fill=INK)  # the needle at rest
    rect(d, *T_BLACK, BLACK)
    rect(d, *T_SILVER, SILVER)
    rect(d, T_SILVER[0], T_SILVER[1], T_SILVER[2], T_SILVER[1] + 1, SILVER_LIGHT)
    lx0, ly0, lx1, ly1 = T_LAMP
    rect(d, lx0, ly0, lx1, ly1, LAMP_RIM)
    rect(d, lx0 + 1, ly0 + 1, lx1 - 1, ly1 - 1, LAMP_DARK)
    img.putpixel((lx0 + 2, ly0 + 2), (160, 60, 56, 255))
    lx0, ly0, lx1, ly1 = T_LAMP_LIT
    rect(d, lx0, ly0, lx1, ly1, LAMP_LIT)
    rect(d, lx0 + 2, ly0 + 2, lx1 - 2, ly1 - 2, LAMP_LIT_HOT)
    px0, py0, px1, py1 = T_POCKET
    rect(d, px0, py0, px1, py1, POCKET)
    bevel(d, px0, py0, px1, py1, (220, 230, 234, 255), POCKET_EDGE)
    rect(d, px0 + 3, py0 + 4, px1 - 3, py1 - 3, TAG)           # the inspection tag inside
    disc(d, (px0 + px1) // 2, py0 + 7, 2, (200, 60, 40, 255))  # its red eyelet
    for y in range(py0 + 11, py1 - 5, 3):
        rect(d, px0 + 4, y, px1 - 4, y + 1, (150, 130, 110, 255))
    rect(d, *T_LOCK, SILVER)
    rect(d, T_LOCK[0] + 3, T_LOCK[1] + 2, T_LOCK[0] + 5, T_LOCK[1] + 6, BLACK)  # the keyway
    crinkle(img, *T_RED, RED, 8, 3)
    return img


def box_parts(lit=False):
    """The proud parts (front at z 11): meter bezel, drill toggle, lock, pocket, lamp, two toggles."""
    z = BOX_FROM[2]
    out = []

    def part(name, r, depth, front_tex, side_tex):
        x0, y0, x1, y1 = r
        faces = {"north": front_tex, "east": side_tex, "west": side_tex, "up": side_tex, "down": side_tex}
        out.append(element(name, (box_x(x1), box_y(y1), z - depth), (box_x(x0), box_y(y0), z), faces))

    part("meter", BF_METER, 0.5, T_METER, T_BLACK)
    part("drill_switch", BF_DRILL, 1.0, T_SILVER, T_SILVER)
    part("key_lock", BF_LOCK, 0.5, T_LOCK, T_SILVER)
    part("tag_pocket", BF_POCKET, 0.25, T_POCKET, T_SILVER)
    part("reset_toggle", BF_RESET, 0.75, T_SILVER, T_SILVER)
    part("silence_toggle", BF_SILENCE, 0.75, T_SILVER, T_SILVER)
    part("trouble_lamp", BF_LAMP, 0.5, T_LAMP, T_LAMP)
    return out


def box_models():
    body = element("cabinet", BOX_FROM, BOX_TO, {"north": BOX_FRONT, "east": BOX_SIDE, "west": BOX_SIDE,
                                                  "up": BOX_TOP, "down": BOX_TOP, "south": BOX_TOP})
    model = block_json("Made with tools/fire_alarm_assets.py (after a Simplex-style alarm cabinet)", "fire_alarm_box",
                       [mount_plate((0.5, 1.0, 15.0), (15.5, 15.0, 16.0)), body] + box_parts())
    model["textures"]["m"] = MOUNT
    # The overlay: the TROUBLE lamp lit, a hair bigger and prouder so it covers the unlit one.
    x0, y0, x1, y1 = BF_LAMP
    z = BOX_FROM[2]
    lamp = element("trouble_lamp_lit", (box_x(x1) - 0.05, box_y(y1) - 0.05, z - 0.55), (box_x(x0) + 0.05, box_y(y0) + 0.05, z),
                   {f: T_LAMP_LIT for f in ("north", "east", "west", "up", "down")})
    lamp["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}  # Glows in the dark (NeoForge's per-element light).
    overlay = {"credit": "Made with tools/fire_alarm_assets.py", "texture_size": [128, 128],
               "textures": {"0": "radiocraft:block/fire_alarm_box", "particle": "radiocraft:block/fire_alarm_box"},
               "elements": [lamp]}
    return model, overlay


# ======================================================================================================================
# The pull station: 5 wide x 8 tall x 2.5 deep, against the south wall
# ======================================================================================================================
PS_FROM, PS_TO = (5.5, 4.0, 13.5), (10.5, 12.0, 16.0)
PS_FRONT = (0, 0, 40, 64)
PS_SIDE = (40, 0, 60, 64)
PS_TOP = (60, 0, 100, 20)
PS_HANDLE_FRONT = (0, 64, 36, 76)     # 4.5 x 1.5 units
PS_HANDLE_SIDE = (36, 64, 40, 76)
PS_HANDLE_TOP = (0, 76, 36, 80)
PS_HANDLE = ((5.75, 9.25, 13.0), (10.25, 10.75, 13.5))
PS_HANDLE_PULLED = ((5.75, 7.0, 12.0), (10.25, 8.5, 12.5))


# The pull stations' colours (2026-11-11): red (fire), yellow and white, each with its body's four shades, the cheeks'
# lettering, the bar and its word, and the handle and its lettering, so every one reads on its own colour.
PULL_COLOURS = {
    "red": {"body": (RED, RED_LIGHT, RED_DARK, RED_DEEP), "cheek_ink": WHITE, "bar": WHITE, "bar_ink": INK, "word": "FIRE",
            "handle": (WHITE, (250, 248, 240, 255), WHITE_DARK), "handle_ink": INK},
    "yellow": {"body": ((222, 176, 28, 255), (244, 204, 70, 255), (172, 132, 14, 255), (116, 86, 8, 255)), "cheek_ink": INK,
               "bar": WHITE, "bar_ink": INK, "word": "ALERT", "handle": (WHITE, (250, 248, 240, 255), WHITE_DARK), "handle_ink": INK},
    "white": {"body": ((224, 222, 214, 255), (246, 244, 238, 255), (178, 176, 168, 255), (122, 120, 114, 255)), "cheek_ink": RED,
              "bar": RED, "bar_ink": WHITE, "word": "ALERT", "handle": (RED, RED_LIGHT, RED_DARK), "handle_ink": WHITE},
}


def pull_texture(colour="red"):
    pal = PULL_COLOURS[colour]
    body, light, dark, deep = pal["body"]
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = PS_FRONT
    rect(d, x0, y0, x1, y1, body)
    bevel(d, x0, y0, x1, y1, light, dark)
    # The cup at the top, with its screw.
    rect(d, 3, 2, 37, 18, dark)
    rect(d, 3, 2, 37, 3, deep)
    rect(d, 19, 6, 21, 8, SILVER)
    # The bar's place under the handle (seen when it is pulled): a dark slot.
    rect(d, 2, 18, 38, 30, deep)
    # The two cheeks and the bar between them, with its word.
    rect(d, 3, 30, 15, 62, body)
    bevel(d, 3, 30, 15, 62, light, dark)
    rect(d, 25, 30, 37, 62, body)
    bevel(d, 25, 30, 37, 62, light, dark)
    rect(d, 15, 30, 25, 62, deep)
    rect(d, 16, 31, 24, 62, pal["bar"])
    text_down(img, 18, 34 if len(pal["word"]) <= 4 else 32, pal["word"], pal["bar_ink"])
    text_down(img, 8, 32, "LOCAL", pal["cheek_ink"])
    text_down(img, 30, 32, "ALARM", pal["cheek_ink"])
    rect(d, *PS_SIDE, body)
    rect(d, PS_SIDE[0], PS_SIDE[1], PS_SIDE[0] + 1, PS_SIDE[3], light)
    rect(d, *PS_TOP, dark)
    # The handle, PULL DOWN on it.
    handle, handle_light, handle_dark = pal["handle"]
    hx0, hy0, hx1, hy1 = PS_HANDLE_FRONT
    rect(d, hx0, hy0, hx1, hy1, handle)
    bevel(d, hx0, hy0, hx1, hy1, handle_light, handle_dark)
    text(img, hx0 + 1, hy0 + 2, "PULL DOWN", pal["handle_ink"])
    rect(d, *PS_HANDLE_SIDE, handle_dark)
    rect(d, *PS_HANDLE_TOP, handle)
    return img


def pull_models(texture="pull_station"):
    body = element("body", PS_FROM, PS_TO, {"north": PS_FRONT, "east": PS_SIDE, "west": PS_SIDE,
                                            "up": PS_TOP, "down": PS_TOP, "south": PS_TOP})
    handle_faces = {"north": PS_HANDLE_FRONT, "east": PS_HANDLE_SIDE, "west": PS_HANDLE_SIDE,
                    "up": PS_HANDLE_TOP, "down": PS_HANDLE_TOP, "south": PS_HANDLE_FRONT}
    up = element("handle", PS_HANDLE[0], PS_HANDLE[1], handle_faces)
    # Pulled: the handle comes down and out of its slot, on two short arms back to the body.
    down = element("handle", PS_HANDLE_PULLED[0], PS_HANDLE_PULLED[1], handle_faces)
    arm_l = element("arm_left", (5.75, 7.5, 12.5), (6.25, 8.0, 13.5), all_faces(PS_HANDLE_SIDE))
    arm_r = element("arm_right", (9.75, 7.5, 12.5), (10.25, 8.0, 13.5), all_faces(PS_HANDLE_SIDE))
    credit = "Made with tools/fire_alarm_assets.py (after a cast pull station)"
    plate = mount_plate((0.0, 0.0, 15.0), (16.0, 16.0, 16.0))  # The whole wall face, so it never looks stuck on (2026-11-11).
    up_json = block_json(credit, texture, [plate, body, up])
    down_json = block_json(credit, texture, [plate, body, down, arm_l, arm_r])
    for j in (up_json, down_json):
        j["textures"]["m"] = MOUNT
    return up_json, down_json


# ======================================================================================================================
# The outdoor pull box (2026-11-11): the indoor station's red face and white handle on a red head 5 deep, on a fluted
# post with a base plate, and a red locator lamp on top (always lit, glows in the dark). Faces north, turned by FACING.
# ======================================================================================================================
OB_HEAD_SIDE = (60, 20, 100, 84)      # 5 deep x 8 tall
OB_HEAD_TOP = (0, 84, 40, 124)        # 5 x 5
OB_POST = (100, 0, 124, 64)           # 3 wide x 8 tall (the post is 7.5 tall)
OB_BASE_TOP = (40, 84, 88, 124)       # 6 x 5 used of it (the plate is 6 x 6)
OB_BASE_SIDE = (0, 124, 48, 128)      # 6 wide x 0.5 tall
OB_LAMP = (88, 84, 104, 100)          # 2 x 2
DY, DZ = 4.0, -8.0                    # the indoor station's parts moved onto the head


def outdoor_texture(colour="red"):
    body, light, dark, deep = PULL_COLOURS[colour]["body"]
    img = pull_texture(colour)
    d = ImageDraw.Draw(img)
    crinkle(img, *OB_HEAD_SIDE, body, 10, 31)
    bevel(d, *OB_HEAD_SIDE, light, dark)
    crinkle(img, *OB_HEAD_TOP, body, 10, 32)
    bevel(d, *OB_HEAD_TOP, light, dark)
    # The post: a darker shade with flutes down it.
    px0, py0, px1, py1 = OB_POST
    rect(d, px0, py0, px1, py1, dark)
    for x in range(px0 + 2, px1, 6):
        rect(d, x, py0, x + 2, py1, deep)
        rect(d, x + 2, py0, x + 3, py1, body)
    rect(d, px0, py0, px1, py0 + 3, body)           # a collar under the head
    rect(d, px0, py1 - 4, px1, py1, deep)           # and where it meets the base
    # The base plate: cast iron, four bolts.
    bx0, by0, bx1, by1 = OB_BASE_TOP
    rect(d, bx0, by0, bx1, by1, (54, 54, 58, 255))
    bevel(d, bx0, by0, bx1, by1, (90, 90, 96, 255), (30, 30, 32, 255))
    for x, y in ((bx0 + 4, by0 + 4), (bx1 - 7, by0 + 4), (bx0 + 4, by1 - 7), (bx1 - 7, by1 - 7)):
        rect(d, x, y, x + 3, y + 3, SILVER_DARK)
    rect(d, *OB_BASE_SIDE, (40, 40, 44, 255))
    # The locator lamp: red glass.
    lx0, ly0, lx1, ly1 = OB_LAMP
    rect(d, lx0, ly0, lx1, ly1, (210, 40, 30, 255))
    rect(d, lx0 + 3, ly0 + 3, lx0 + 7, ly0 + 7, (255, 140, 120, 255))
    return img


def outdoor_models(texture="outdoor_pull_box"):
    def moved(frm, to):
        return (frm[0], frm[1] + DY, frm[2] + DZ), (to[0], to[1] + DY, to[2] + DZ)

    head = element("head", (5.5, 8.0, 5.5), (10.5, 16.0, 10.5), {"north": PS_FRONT, "east": OB_HEAD_SIDE, "west": OB_HEAD_SIDE,
                                                                 "south": OB_HEAD_SIDE, "up": OB_HEAD_TOP, "down": OB_HEAD_TOP})
    post = element("post", (6.5, 0.5, 6.5), (9.5, 8.0, 9.5), {f: OB_POST for f in ("north", "east", "south", "west")})
    base = element("base", (5.0, 0.0, 5.0), (11.0, 0.5, 11.0), {"up": OB_BASE_TOP, "down": OB_BASE_TOP, "north": OB_BASE_SIDE,
                                                                "south": OB_BASE_SIDE, "east": OB_BASE_SIDE, "west": OB_BASE_SIDE})
    lamp = element("lamp", (7.0, 16.0, 7.0), (9.0, 18.0, 9.0), all_faces(OB_LAMP))
    lamp["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    handle_faces = {"north": PS_HANDLE_FRONT, "east": PS_HANDLE_SIDE, "west": PS_HANDLE_SIDE,
                    "up": PS_HANDLE_TOP, "down": PS_HANDLE_TOP, "south": PS_HANDLE_FRONT}
    up = element("handle", *moved(*PS_HANDLE), handle_faces)
    down = element("handle", *moved(*PS_HANDLE_PULLED), handle_faces)
    arm_l = element("arm_left", *moved((5.75, 7.5, 12.5), (6.25, 8.0, 13.5)), all_faces(PS_HANDLE_SIDE))
    arm_r = element("arm_right", *moved((9.75, 7.5, 12.5), (10.25, 8.0, 13.5)), all_faces(PS_HANDLE_SIDE))
    credit = "Made with tools/fire_alarm_assets.py (after a street fire alarm box)"
    parts = [base, post, head, lamp]
    return (block_json(credit, texture, parts + [up]),
            block_json(credit, texture, parts + [down, arm_l, arm_r]))


# ======================================================================================================================
# The fire bell (2026-11-11): a red domed gong 10 across on a dark square plate, a steel stud in the middle. The dome is
# stacked layers, each a chamfered square (a cross of two bars and a square), their fronts cut from one painted dome
# so the shading runs across the steps. Faces north on a south wall.
# ======================================================================================================================
FB_DOME = (0, 0, 80, 80)        # the dome's face, 10 x 10 units: front faces sample their own part of it
FB_SIDE = (80, 0, 96, 16)       # the gong's edge: dark red
FB_PLATE = (0, 80, 48, 128)     # the mounting plate, 6 x 6
FB_STUD = (96, 0, 108, 12)


def bell_texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = FB_DOME
    cx, cy, hx, hy = 40.0, 40.0, 28.0, 26.0  # centre; the highlight up and left
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / 40.0
            h = math.hypot(x + 0.5 - hx, y + 0.5 - hy) / 40.0
            k = max(0.0, 1.0 - r * 0.55) * 0.9 + max(0.0, 0.35 - h) * 1.6
            c = tuple(min(255, int(v)) for v in (90 + 150 * k, 14 + 40 * k * k, 12 + 34 * k * k))
            px[x, y] = c + (255,)
    for ring in (38, 39):  # the rolled rim
        for t in range(0, 720):
            a = math.radians(t / 2.0)
            X, Y = int(cx + ring * math.cos(a)), int(cy + ring * math.sin(a))
            if x0 <= X < x1 and y0 <= Y < y1:
                px[X, Y] = RED_DEEP
    rect(d, *FB_SIDE, RED_DARK)
    rect(d, FB_SIDE[0], FB_SIDE[1], FB_SIDE[2], FB_SIDE[1] + 2, RED)
    rect(d, *FB_PLATE, (44, 44, 48, 255))
    bevel(d, *FB_PLATE, (80, 80, 86, 255), (24, 24, 26, 255))
    for x, y in ((FB_PLATE[0] + 3, FB_PLATE[1] + 3), (FB_PLATE[2] - 6, FB_PLATE[1] + 3), (FB_PLATE[0] + 3, FB_PLATE[3] - 6), (FB_PLATE[2] - 6, FB_PLATE[3] - 6)):
        rect(d, x, y, x + 3, y + 3, SILVER_DARK)
    rect(d, *FB_STUD, SILVER)
    rect(d, FB_STUD[0] + 2, FB_STUD[1] + 2, FB_STUD[0] + 6, FB_STUD[1] + 6, SILVER_LIGHT)
    return img


def bell_model():
    elements = [element("plate", (5.0, 5.0, 15.0), (11.0, 11.0, 16.0), {"north": FB_PLATE, "east": FB_SIDE, "west": FB_SIDE,
                                                                       "up": FB_SIDE, "down": FB_SIDE})]

    def dome_face(xa, ya, xb, yb):
        """The dome's face under model rect x xa..xb, y ya..yb (north face: x runs right to left as seen)."""
        u0, u1 = (13.0 - xb) * 8, (13.0 - xa) * 8
        v0, v1 = (13.0 - yb) * 8, (13.0 - ya) * 8
        return (int(u0), int(v0), int(u1), int(v1))

    layers = [  # z front, z back, and the layer's bars (x1, y1, x2, y2)
        (13.0, 15.0, [(3.0, 4.5, 13.0, 11.5), (4.5, 3.0, 11.5, 13.0), (3.5, 3.5, 12.5, 12.5)]),
        (12.0, 13.0, [(4.0, 5.5, 12.0, 10.5), (5.5, 4.0, 10.5, 12.0), (4.5, 4.5, 11.5, 11.5)]),
        (11.0, 12.0, [(5.5, 6.5, 10.5, 9.5), (6.5, 5.5, 9.5, 10.5), (6.0, 6.0, 10.0, 10.0)]),
    ]
    for i, (zf, zb, bars) in enumerate(layers):
        for j, (xa, ya, xb, yb) in enumerate(bars):
            elements.append(element("gong_%d_%d" % (i, j), (xa, ya, zf), (xb, yb, zb),
                                    {"north": dome_face(xa, ya, xb, yb), "east": FB_SIDE, "west": FB_SIDE, "up": FB_SIDE, "down": FB_SIDE}))
    elements.append(element("stud", (7.25, 7.25, 10.25), (8.75, 8.75, 11.0), all_faces(FB_STUD)))
    elements.insert(0, mount_plate((0.0, 0.0, 15.5), (16.0, 16.0, 16.0)))  # Mounted on the whole wall face (2026-11-11).
    out = block_json("Made with tools/fire_alarm_assets.py (a vintage fire alarm bell)", "fire_bell", elements)
    out["textures"]["m"] = MOUNT
    return out


# ======================================================================================================================
# Wiring (2026-11-11): a grey mounting plate behind the box and the pull station, and the last bit of data wire each
# device draws itself, from where the wire stops to its plate (see WallWired). Data wire is 1 px, running up a wall at
# the block's middle line and straddling the wall's face (z 15.5 to 16.5 here, the wall at z 16).
#   <name>_wire_up / _wire_down       up the wall from the plate to the block above / below
#   <name>_wire_cw_wall / _ccw_wall   along the wall to the middle of the block beside (+x / -x), and down it to its floor
#   <name>_wire_cw_floor / _ccw_floor along the floor by that side and the wall to the run down (shown with _wire_down)
# The panic button on a floor (or a ceiling, turned over) has _floor_wire_front / back / cw / ccw instead: along the
# floor from each side's middle to its plate.
# ======================================================================================================================
CABLE, MOUNT = "radiocraft:block/fire_alarm_cable", "radiocraft:block/fire_alarm_mount"


def wiring_textures():
    cable_img = Image.new("RGBA", (16, 16), (26, 26, 28, 255))
    ImageDraw.Draw(cable_img).rectangle([0, 0, 15, 3], fill=(52, 52, 56, 255))
    mount = Image.new("RGBA", (16, 16), (120, 122, 126, 255))
    rnd = random.Random(5)
    px = mount.load()
    for y in range(16):
        for x in range(16):
            v = 120 + rnd.randint(-5, 5)
            px[x, y] = (v, v + 2, v + 6, 255)
    d = ImageDraw.Draw(mount)
    d.rectangle([0, 0, 15, 0], fill=(150, 152, 156, 255))
    d.rectangle([0, 15, 15, 15], fill=(86, 88, 92, 255))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):  # Its four screws.
        mount.putpixel((x, y), (70, 72, 76, 255))
    return cable_img, mount


def cable_json(credit, elements):
    return {"credit": credit, "parent": "block/block", "textures": {"c": CABLE, "particle": CABLE}, "elements": elements}


def cable(name, frm, to):
    return {"name": name, "from": list(frm), "to": list(to),
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#c"} for f in ("north", "east", "south", "west", "up", "down")}}


def mount_plate(frm, to):
    return {"name": "mounting_plate", "from": list(frm), "to": list(to),
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#m"} for f in ("north", "east", "west", "up", "down")}}


def wall_wiring(px0, py0, px1, py1):
    """The wire pieces for a device on a wall whose plate is x px0..px1, y py0..py1 (model, facing north)."""
    ym = (py0 + py1) / 2.0
    z0, z1 = 15.5, 16.5
    return {
        "wire_up": [cable("up", (7.5, py1, z0), (8.5, 16.0, z1))],
        "wire_down": [cable("down", (7.5, 0.0, z0), (8.5, py0, z1))],
        "wire_cw_wall": [cable("along", (px1, ym - 0.5, z0), (24.5, ym + 0.5, z1)), cable("drop", (23.55, 0.0, 15.55), (24.45, ym - 0.5, 16.45))],
        "wire_ccw_wall": [cable("along", (-8.5, ym - 0.5, z0), (px0, ym + 0.5, z1)), cable("drop", (-8.45, 0.0, 15.55), (-7.55, ym - 0.5, 16.45))],
        "wire_cw_floor": [cable("by_side", (15.0, 0.0, 7.5), (16.0, 1.0, z0)), cable("by_wall", (8.5, 0.0, z0), (16.0, 1.0, z1))],
        "wire_ccw_floor": [cable("by_side", (0.0, 0.0, 7.5), (1.0, 1.0, z0)), cable("by_wall", (0.0, 0.0, z0), (7.5, 1.0, z1))],
    }


def write_wiring(name, pieces, credit):
    for piece, elements in pieces.items():
        elements = [e for e in elements if all(t > f for f, t in zip(e["from"], e["to"]))]  # A plate to the block's edge: none
        write_json(os.path.join(ROOT, "models", "block", "%s_%s.json" % (name, piece)), cable_json(credit, elements))


# ======================================================================================================================
# The panic button: a red button on a 6 x 5 steel plate, lying on the floor (as vanilla's button model)
# ======================================================================================================================
PB_PLATE = ((5.0, 0.0, 5.5), (11.0, 1.0, 10.5))
PB_BEZEL = ((6.25, 1.0, 6.25), (9.75, 1.5, 9.75))
PB_BUTTON = ((6.75, 1.0, 6.75), (9.25, 2.5, 9.25))
PB_BUTTON_PRESSED = ((6.75, 1.0, 6.75), (9.25, 1.75, 9.25))
PB_PLATE_TOP = (0, 0, 48, 40)
PB_PLATE_EDGE = (0, 40, 48, 48)
PB_BEZEL_TOP = (48, 0, 76, 28)
PB_BEZEL_SIDE = (48, 28, 76, 32)
PB_BUTTON_TOP = (80, 0, 100, 20)
PB_BUTTON_SIDE = (80, 20, 100, 32)


def panic_texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = PB_PLATE_TOP
    rnd = random.Random(11)
    for y in range(y0, y1):  # Brushed steel: streaks along the plate.
        shade = 168 + rnd.randint(-8, 8)
        rect(d, x0, y, x1, y + 1, (shade, shade + 2, shade + 6, 255))
    bevel(d, x0, y0, x1, y1, SILVER_LIGHT, SILVER_DARK)
    for sx in (4, 42):  # The two screws.
        disc(d, sx + 1, 20, 2, SILVER_DARK)
        rect(d, sx, 20, sx + 2, 21, (70, 72, 76, 255))
    rect(d, *PB_PLATE_EDGE, SILVER_DARK)
    bx0, by0, bx1, by1 = PB_BEZEL_TOP
    rect(d, bx0, by0, bx1, by1, SILVER)  # Opaque all over: the model is solid, a clear texel draws as black.
    disc(d, bx0 + 14, by0 + 14, 14, SILVER_DARK)
    disc(d, bx0 + 14, by0 + 14, 13, SILVER_LIGHT)
    disc(d, bx0 + 14, by0 + 14, 11, (60, 60, 64, 255))
    rect(d, bx0, by0, bx0 + 4, by0 + 4, SILVER)       # corners filled (the element is square)
    rect(d, bx1 - 4, by0, bx1, by0 + 4, SILVER)
    rect(d, bx0, by1 - 4, bx0 + 4, by1, SILVER)
    rect(d, bx1 - 4, by1 - 4, bx1, by1, SILVER)
    d.rectangle([bx0, by0, bx1 - 1, by1 - 1], outline=SILVER_DARK)
    rect(d, *PB_BEZEL_SIDE, SILVER)
    ux0, uy0, ux1, uy1 = PB_BUTTON_TOP
    rect(d, ux0, uy0, ux1, uy1, (60, 60, 64, 255))
    disc(d, ux0 + 10, uy0 + 10, 10, RED)
    disc(d, ux0 + 8, uy0 + 8, 5, RED_LIGHT)
    disc(d, ux0 + 7, uy0 + 7, 2, (240, 120, 110, 255))
    rect(d, *PB_BUTTON_SIDE, RED_DARK)
    rect(d, PB_BUTTON_SIDE[0], PB_BUTTON_SIDE[1], PB_BUTTON_SIDE[2], PB_BUTTON_SIDE[1] + 1, RED)
    return img


def panic_models():
    plate = element("plate", PB_PLATE[0], PB_PLATE[1], {"up": PB_PLATE_TOP, "down": PB_PLATE_TOP, "north": PB_PLATE_EDGE,
                                                        "south": PB_PLATE_EDGE, "east": PB_PLATE_EDGE, "west": PB_PLATE_EDGE})
    bezel = element("bezel", PB_BEZEL[0], PB_BEZEL[1], {"up": PB_BEZEL_TOP, "north": PB_BEZEL_SIDE, "south": PB_BEZEL_SIDE,
                                                        "east": PB_BEZEL_SIDE, "west": PB_BEZEL_SIDE})
    faces = {"up": PB_BUTTON_TOP, "north": PB_BUTTON_SIDE, "south": PB_BUTTON_SIDE, "east": PB_BUTTON_SIDE, "west": PB_BUTTON_SIDE}
    credit = "Made with tools/fire_alarm_assets.py (a vintage panic button)"
    # No "block/block" parent's cull: the plate is thin. Like vanilla's button, so ambient occlusion is off.
    up = block_json(credit, "panic_button", [plate, bezel, element("button", PB_BUTTON[0], PB_BUTTON[1], faces)], ambientocclusion=False)
    down = block_json(credit, "panic_button", [plate, bezel, element("button", PB_BUTTON_PRESSED[0], PB_BUTTON_PRESSED[1], faces)], ambientocclusion=False)
    item = {"parent": "radiocraft:block/panic_button", "display": {
        # 2026-11-12: it was tipped back (-60), showing its underside. Now its face is turned up to the viewer (the plate
        # lies 7 px below the model's centre, so it is lifted back into the slot), and the hands hold it as vanilla
        # holds a pressure plate.
        "gui": {"rotation": [60, 0, 0], "translation": [0, 5, 0], "scale": [1.4, 1.4, 1.4]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -6], "scale": [1.2, 1.2, 1.2]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]}}}
    return up, down, item


def to_wall(model):
    """A floor-lying model stood up on a south wall facing north: floor (x, y, z) -> wall (x, 16 - z, 16 - y)."""
    face_map = {"up": "north", "down": "south", "north": "up", "south": "down", "east": "east", "west": "west"}
    out = json.loads(json.dumps(model))
    for e in out["elements"]:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        e["from"], e["to"] = [x0, 16.0 - z1, 16.0 - y1], [x1, 16.0 - z0, 16.0 - y0]
        e["faces"] = {face_map[f]: v for f, v in e["faces"].items()}
    return out


def mounted(model):
    """A wall model on a grey mounting plate the size of the wall face, behind its own plate (2026-11-11)."""
    model["elements"].insert(0, mount_plate((0.0, 0.0, 15.5), (16.0, 16.0, 16.0)))
    model["textures"]["m"] = MOUNT
    return model


def panic_floor_wiring():
    """Along the floor from the middle of each side to the plate (x 5..11, z 5.5..10.5)."""
    return {
        "floor_wire_front": [cable("front", (7.5, 0.0, 0.0), (8.5, 1.0, 5.5))],
        "floor_wire_back": [cable("back", (7.5, 0.0, 10.5), (8.5, 1.0, 16.0))],
        "floor_wire_cw": [cable("cw", (11.0, 0.0, 7.5), (16.0, 1.0, 8.5))],
        "floor_wire_ccw": [cable("ccw", (0.0, 0.0, 7.5), (5.0, 1.0, 8.5))],
    }


# ======================================================================================================================
# The box's screen: inside the cabinet (176 x 176)
# ======================================================================================================================
G_W = G_H = 176
CAB, CAB_LIGHT, CAB_DARK, CAB_DEEP = (122, 30, 30, 255), (150, 46, 44, 255), (90, 20, 20, 255), (58, 12, 12, 255)
BOARD, BOARD_LIGHT, BOARD_DARK, BOARD_EDGE = (226, 212, 160, 255), (240, 230, 188, 255), (196, 180, 128, 255), (150, 132, 84, 255)
LABEL = (60, 48, 30, 255)
G_BOARD = (10, 10, 166, 88)
G_STRIP = (10, 92, 166, 166)
G_METER_FACE = (126, 22, 30, 18)     # x, y, w, h: the face the needle is clipped to
G_METER_PIVOT = (141, 40)
G_LAMPS = [("power_lamp", "PWR", 46, (60, 220, 90)), ("trouble_lamp", "TRBL", 64, (255, 176, 32)), ("alarm_lamp", "ALM", 82, (255, 40, 32))]
G_LAMP_Y = 99
G_ZONES_X = [104, 114, 124, 134, 144, 154]
G_SWITCHES = [("drill", 44, ["OFF", "DRILL"], ("OFF", "DRILL"))]
G_SW_Y = (124, 138)                   # the slot's top and bottom
G_BUTTONS = [("silence", "SILENCE", 60, (190, 36, 30, 255), (226, 80, 70, 255), (120, 20, 16, 255)),
             ("reset", "RESET", 92, (40, 40, 44, 255), (90, 90, 96, 255), (16, 16, 18, 255)),
             ("test", "TEST", 122, (40, 40, 44, 255), (90, 90, 96, 255), (16, 16, 18, 255))]
G_BUTTON_Y, G_BUTTON_W, G_BUTTON_H = 123, 24, 12
G_PLATE = (40, 147, 72, 14)           # x, y, w, h


def gui_panel():
    img = Image.new("RGBA", (G_W, G_H), CAB)
    d = ImageDraw.Draw(img)
    crinkle(img, 0, 0, G_W, G_H, CAB, 6, 21)
    # The cabinet's walls seen from inside: a lit rim, then the shadowed sides.
    bevel(d, 0, 0, G_W, G_H, CAB_LIGHT, CAB_DEEP)
    bevel(d, 3, 3, G_W - 3, G_H - 3, CAB_DEEP, CAB_LIGHT)
    rect(d, 4, 4, G_W - 4, 8, CAB_DARK)
    rect(d, 4, 4, 8, G_H - 4, CAB_DARK)
    # ---- The circuit board ----
    bx0, by0, bx1, by1 = G_BOARD
    rect(d, bx0 + 2, by0 + 2, bx1 + 2, by1 + 2, CAB_DEEP)       # its shadow
    rect(d, bx0, by0, bx1, by1, BOARD)
    bevel(d, bx0, by0, bx1, by1, BOARD_LIGHT, BOARD_EDGE)
    for sx, sy in ((bx0 + 3, by0 + 3), (bx1 - 5, by0 + 3), (bx0 + 3, by1 - 5), (bx1 - 5, by1 - 5)):
        rect(d, sx, sy, sx + 2, sy + 2, SILVER_DARK)              # its mounting screws
    # The terminal strip across the top, wires coming in from above.
    rect(d, 16, 14, 108, 22, (40, 40, 42, 255))
    for i, x in enumerate(range(19, 106, 8)):
        disc(d, x + 2, 18, 3, SILVER)
        rect(d, x + 1, 18, x + 4, 19, SILVER_DARK)
        colour = [(240, 240, 236, 255), (200, 40, 34, 255), (60, 60, 64, 255), (210, 180, 60, 255)][i % 4]
        d.line([x + 2, 14, x + 2 - (i % 3), 4], fill=colour, width=1)  # the wires up out of the box
    d.line([14, 4, 18, 12, 30, 12], fill=(240, 240, 236, 255))
    d.line([12, 4, 15, 13, 44, 13], fill=(200, 40, 34, 255))
    # Relays: black boxes with clear covers.
    for x, y in ((16, 28), (38, 28)):
        rect(d, x, y, x + 18, y + 14, (30, 30, 32, 255))
        rect(d, x + 2, y + 2, x + 16, y + 12, (90, 96, 100, 255))
        rect(d, x + 4, y + 4, x + 14, y + 6, (180, 120, 60, 255))   # the coil
        rect(d, x + 4, y + 8, x + 14, y + 10, SILVER_DARK)
    # A round relay can with a white top.
    disc(d, 70, 36, 9, SILVER_DARK)
    disc(d, 70, 36, 7, (240, 238, 230, 255))
    disc(d, 70, 36, 2, SILVER_DARK)
    # Capacitors: black cans with white bands.
    for x, y in ((18, 50), (32, 50), (46, 50)):
        rect(d, x, y, x + 11, y + 22, (26, 26, 28, 255))
        rect(d, x + 1, y + 1, x + 3, y + 21, (70, 70, 74, 255))
        rect(d, x, y + 4, x + 11, y + 6, (230, 230, 226, 255))
        rect(d, x, y + 16, x + 11, y + 18, (230, 230, 226, 255))
    # Resistors and a diode.
    for i, y in enumerate((52, 58, 64, 70)):
        rect(d, 62, y + 1, 92, y + 2, SILVER_DARK)
        rect(d, 70, y, 84, y + 3, (204, 168, 120, 255))
        for bx, band in zip((72, 75, 78), [(150, 70, 40, 255), (30, 30, 30, 255), (200, 40, 30, 255)][i % 3:] + [(30, 30, 30, 255)] * 3):
            rect(d, bx, y, bx + 1, y + 3, band)
    # The terminal strip down the right side of the board.
    rect(d, 100, 28, 110, 84, (40, 40, 42, 255))
    for y in range(31, 82, 7):
        disc(d, 105, y + 2, 3, SILVER)
        rect(d, 103, y + 2, 108, y + 3, SILVER_DARK)
    # Printed traces.
    for y in (78, 82):
        rect(d, 16, y, 96, y + 1, BOARD_DARK)
    # The LINK meter (redrawn 2026-11-11): a black case, a cream face, an arc scale with ticks, red (weak) to green
    # (strong) under it, the needle's pivot at the foot of the face. The game draws the needle (the "meter" control).
    rect(d, 120, 16, 162, 54, (20, 20, 22, 255))
    bevel(d, 120, 16, 162, 54, (70, 70, 74, 255), (8, 8, 8, 255))
    rect(d, 123, 19, 159, 42, (60, 60, 64, 255))                 # the face's rim
    rect(d, 124, 20, 158, 41, METER_FACE)
    rect(d, 124, 20, 158, 21, (206, 200, 178, 255))               # a shadow under the rim
    px_, py_ = G_METER_PIVOT
    ink = (34, 32, 28, 255)

    def arc_point(deg, r):
        a = math.radians(deg)
        return px_ + 0.5 + r * math.sin(a), py_ + 0.5 - r * math.cos(a)

    for t in range(-500, 501):                                     # the scale line
        X, Y = arc_point(t / 10.0, 12.0)
        img.putpixel((int(X), int(Y)), ink)
    for t in range(-500, 501):                                     # the colour band inside it
        deg = t / 10.0
        colour = (196, 44, 34, 255) if deg < -18 else (60, 150, 70, 255) if deg > 18 else None
        if colour:
            for r in (10.0, 10.6):
                X, Y = arc_point(deg, r)
                img.putpixel((int(X), int(Y)), colour)
    for k in range(0, 6):                                          # six ticks, the ends and middle pair longer
        deg = -50 + k * 20
        x0, y0 = arc_point(deg, 12.0)
        x1, y1 = arc_point(deg, 14.6 if k in (0, 5) else 13.6)
        d.line([int(x0), int(y0), int(x1), int(y1)], fill=ink)
    text(img, 126, 36, "0", ink)
    text_centred(img, 155, 36, "1", ink)
    rect(d, 124, 41, 159, 52, (20, 20, 22, 255))                   # under the face, the case
    disc(d, px_ + 1, py_, 3, (40, 40, 44, 255))                    # the pivot's boss
    disc(d, px_ + 1, py_, 1, (90, 90, 96, 255))
    text_centred(img, 141, 45, "LINK", (220, 220, 214, 255))
    # A wire bundle from the board down to the strip, left side.
    d.line([12, 86, 13, 94], fill=(200, 40, 34, 255))
    d.line([14, 86, 15, 94], fill=(30, 30, 30, 255))
    # ---- The control strip ----
    sx0, sy0, sx1, sy1 = G_STRIP
    rect(d, sx0 + 2, sy0 + 2, sx1 + 2, sy1 + 2, CAB_DEEP)
    rect(d, sx0, sy0, sx1, sy1, BOARD)
    bevel(d, sx0, sy0, sx1, sy1, BOARD_LIGHT, BOARD_EDGE)
    # Two spare fuses in their clips, at the left.
    for fx in (15, 25):
        rect(d, fx, 100, fx + 6, 103, SILVER)
        rect(d, fx, 135, fx + 6, 138, SILVER)
        rect(d, fx + 1, 103, fx + 5, 135, (200, 214, 220, 255))   # the glass
        rect(d, fx + 1, 103, fx + 2, 135, (236, 244, 248, 255))
        rect(d, fx + 3, 104, fx + 4, 134, (120, 110, 100, 255))   # the element
        rect(d, fx - 1, 99, fx + 7, 100, SILVER_DARK)
        rect(d, fx - 1, 138, fx + 7, 139, SILVER_DARK)
    text_centred(img, 23, 142, "SPARE", LABEL)
    text_centred(img, 23, 148, "FUSES", LABEL)
    # Lamps: jewel bezels, unlit.
    for name, label, x, colour in G_LAMPS:
        disc(d, x + 3, G_LAMP_Y + 3, 5, SILVER_DARK)
        disc(d, x + 3, G_LAMP_Y + 3, 4, (30, 30, 30, 255))
        rect(d, x, G_LAMP_Y, x + 6, G_LAMP_Y + 6, tuple(c // 3 for c in colour) + (255,))
        text_centred(img, x + 3, G_LAMP_Y + 9, label, LABEL)
    for i, x in enumerate(G_ZONES_X):
        disc(d, x + 2, G_LAMP_Y + 3, 4, SILVER_DARK)
        rect(d, x, G_LAMP_Y + 1, x + 5, G_LAMP_Y + 6, (90, 18, 16, 255))
        text_centred(img, x + 2, G_LAMP_Y + 9, "Z%d" % (i + 1), LABEL)
    text_centred(img, 129, G_LAMP_Y + 15, "ZONES", LABEL)
    rect(d, 102, G_LAMP_Y + 17, 117, G_LAMP_Y + 18, LABEL)
    rect(d, 141, G_LAMP_Y + 17, 158, G_LAMP_Y + 18, LABEL)
    # Toggle switches: the slot and bushing, no bat; position words above and below.
    for name, x, positions, words in G_SWITCHES:
        t, b = G_SW_Y
        text_centred(img, x + 2, t - 7, words[0], LABEL)
        rect(d, x - 2, t - 1, x + 6, b + 1, SILVER_DARK)
        rect(d, x - 1, t, x + 5, b, SILVER)
        rect(d, x + 1, t + 1, x + 3, b - 1, (20, 20, 20, 255))
        text_centred(img, x + 2, b + 2, words[1], LABEL)
    # Push buttons, unpressed.
    for name, label, x, cap, light, dark in G_BUTTONS:
        y = G_BUTTON_Y
        rect(d, x - 1, y - 1, x + G_BUTTON_W + 1, y + G_BUTTON_H + 1, (40, 40, 42, 255))
        rect(d, x, y, x + G_BUTTON_W, y + G_BUTTON_H, cap)
        bevel(d, x, y, x + G_BUTTON_W, y + G_BUTTON_H, light, dark)
        text_centred(img, x + G_BUTTON_W / 2.0, y + G_BUTTON_H + 3, label, LABEL)
    # The engraved plate, blank (the screen prints the box number and frequency on it).
    px0, py0, pw, ph = G_PLATE
    rect(d, px0 - 1, py0 - 1, px0 + pw + 1, py0 + ph + 1, BOARD_EDGE)
    rect(d, px0, py0, px0 + pw, py0 + ph, (36, 36, 38, 255))
    bevel(d, px0, py0, px0 + pw, py0 + ph, (16, 16, 18, 255), (80, 80, 84, 255))
    # The maker's lettering on the strip.
    text(img, 116, 149, "RADIO BOX", LABEL)
    text(img, 116, 155, "MODEL RC-3", LABEL)
    # Screws at the strip's ends.
    for x, y in ((sx0 + 3, sy0 + 3), (sx1 - 5, sy0 + 3), (sx0 + 3, sy1 - 5), (sx1 - 5, sy1 - 5)):
        rect(d, x, y, x + 2, y + 2, SILVER_DARK)
    return img


# The box's programming panel (2026-11-11): the same inside, with the control strip's lamps and keys swapped for an
# unlit 16 x 2 LCD and five keys (left, up, down, right, ENTER). The screen writes the LCD's text.
P_LCD = (38, 98, 104, 28)           # x, y, w, h of the bezel; the glass is 3 px in
P_KEY_Y, P_KEY_H = 133, 12
P_KEYS = [("left", 38, 14), ("up", 56, 14), ("down", 74, 14), ("right", 92, 14), ("enter", 112, 30)]
LCD_GLASS, LCD_GLASS_DARK, LCD_INK = (150, 160, 128, 255), (124, 134, 104, 255), (30, 40, 26, 255)


def arrow(d, cx, cy, way, colour):
    """A small solid triangle pointing {way}, 5 px across."""
    for i in range(3):
        if way == "up":
            rect(d, cx - i, cy - 1 + i, cx + i + 1, cy + i, colour)
        elif way == "down":
            rect(d, cx - i, cy + 1 - i, cx + i + 1, cy + 2 - i, colour)
        elif way == "left":
            rect(d, cx - 1 + i, cy - i, cx + i, cy + i + 1, colour)
        else:
            rect(d, cx + 1 - i, cy - i, cx + 2 - i, cy + i + 1, colour)


def gui_program_panel():
    img = gui_panel()
    d = ImageDraw.Draw(img)
    sx0, sy0, sx1, sy1 = G_STRIP
    rect(d, sx0, sy0, sx1, sy1, BOARD)  # A clean strip: no lamps, toggles, keys or plate.
    bevel(d, sx0, sy0, sx1, sy1, BOARD_LIGHT, BOARD_EDGE)
    for fx in (15, 25):  # The spare fuses stay.
        rect(d, fx, 100, fx + 6, 103, SILVER)
        rect(d, fx, 135, fx + 6, 138, SILVER)
        rect(d, fx + 1, 103, fx + 5, 135, (200, 214, 220, 255))
        rect(d, fx + 1, 103, fx + 2, 135, (236, 244, 248, 255))
        rect(d, fx + 3, 104, fx + 4, 134, (120, 110, 100, 255))
        rect(d, fx - 1, 99, fx + 7, 100, SILVER_DARK)
        rect(d, fx - 1, 138, fx + 7, 139, SILVER_DARK)
    text_centred(img, 23, 142, "SPARE", LABEL)
    text_centred(img, 23, 148, "FUSES", LABEL)
    # The LCD: a black bezel, the unlit grey-green glass, a faint shadow along its top and left.
    x, y, w, h = P_LCD
    rect(d, x - 1, y - 1, x + w + 1, y + h + 1, BOARD_EDGE)
    rect(d, x, y, x + w, y + h, (28, 28, 30, 255))
    rect(d, x + 3, y + 3, x + w - 3, y + h - 3, LCD_GLASS)
    rect(d, x + 3, y + 3, x + w - 3, y + 4, LCD_GLASS_DARK)
    rect(d, x + 3, y + 3, x + 4, y + h - 3, LCD_GLASS_DARK)
    # The keys: black caps with the arrows, ENTER wider; ENTER's word under it.
    for name, kx, kw in P_KEYS:
        rect(d, kx - 1, P_KEY_Y - 1, kx + kw + 1, P_KEY_Y + P_KEY_H + 1, (40, 40, 42, 255))
        rect(d, kx, P_KEY_Y, kx + kw, P_KEY_Y + P_KEY_H, (40, 40, 44, 255))
        bevel(d, kx, P_KEY_Y, kx + kw, P_KEY_Y + P_KEY_H, (90, 90, 96, 255), (16, 16, 18, 255))
        if name != "enter":
            arrow(d, kx + kw // 2, P_KEY_Y + P_KEY_H // 2, name, (220, 220, 214, 255))
        else:
            rect(d, kx + 10, P_KEY_Y + 5, kx + 20, P_KEY_Y + 7, (220, 220, 214, 255))
    text_centred(img, 127, P_KEY_Y + P_KEY_H + 3, "ENTER", LABEL)
    text_centred(img, 74, P_KEY_Y + P_KEY_H + 3, "SELECT", LABEL)
    text_down(img, 151, 99, "PROGRAM", LABEL)
    text(img, 38, 156, "RADIO BOX MODEL RC-3", LABEL)
    for px, py in ((sx0 + 3, sy0 + 3), (sx1 - 5, sy0 + 3), (sx0 + 3, sy1 - 5), (sx1 - 5, sy1 - 5)):
        rect(d, px, py, px + 2, py + 2, SILVER_DARK)
    return img


def gui_program_layout():
    x, y, w, h = P_LCD
    controls = [{"name": "lcd", "type": "display", "x": x + 3, "y": y + 3, "w": w - 6, "h": h - 6, "ink": hexc(LCD_INK)}]
    mx, my, mw, mh = G_METER_FACE
    controls.append({"name": "meter", "type": "meter", "x": mx, "y": my, "w": mw, "h": mh, "pivot": list(G_METER_PIVOT), "radius": 14,
                     "zero_deg": -50, "full_deg": 50, "s9_fraction": 0.6, "needle_colour": "#1A1A1A"})
    for name, kx, kw in P_KEYS:
        controls.append({"name": name, "type": "button", "x": kx, "y": P_KEY_Y, "w": kw, "h": P_KEY_H})
    return {"texture": "radiocraft:textures/gui/fire_alarm/fire_alarm_box_program.png", "width": G_W, "height": G_H, "controls": controls}


def gui_layout():
    controls = []
    for name, label, x, colour in G_LAMPS:
        controls.append({"name": name, "type": "lamp", "x": x, "y": G_LAMP_Y, "w": 6, "h": 6, "colour": hexc(colour)})
    for i, x in enumerate(G_ZONES_X):
        controls.append({"name": "zone_lamp_%d" % (i + 1), "type": "lamp", "x": x, "y": G_LAMP_Y + 1, "w": 5, "h": 5, "colour": "#FF2A20"})
    for name, x, positions, words in G_SWITCHES:
        t, b = G_SW_Y
        controls.append({"name": name, "type": "switch", "x": x - 4, "y": t - 2, "w": 12, "h": b - t + 4, "positions": positions,
                         "handles": [[x, t - 1, 5, 7], [x, b - 6, 5, 7]], "handle_colour": "#D8D8DC", "handle_shadow": "#5A5A5E"})
    for name, label, x, cap, light, dark in G_BUTTONS:
        controls.append({"name": name, "type": "button", "x": x, "y": G_BUTTON_Y, "w": G_BUTTON_W, "h": G_BUTTON_H})
    mx, my, mw, mh = G_METER_FACE
    controls.append({"name": "meter", "type": "meter", "x": mx, "y": my, "w": mw, "h": mh, "pivot": list(G_METER_PIVOT), "radius": 14,
                     "zero_deg": -50, "full_deg": 50, "s9_fraction": 0.6, "needle_colour": "#1A1A1A"})
    px0, py0, pw, ph = G_PLATE
    controls.append({"name": "plate", "type": "display", "x": px0, "y": py0, "w": pw, "h": ph, "ink": "#E8E4D8"})
    return {"texture": "radiocraft:textures/gui/fire_alarm/fire_alarm_box.png", "width": G_W, "height": G_H, "controls": controls}


# ======================================================================================================================
# Previews
# ======================================================================================================================
def preview_gui(panel, layout, state, path):
    img = panel.copy()
    d = ImageDraw.Draw(img)
    for c in layout["controls"]:
        t = c["type"]
        if t == "lamp" and state["lamps"].get(c["name"]):
            col = tuple(int(c["colour"][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
            rect(d, c["x"], c["y"], c["x"] + c["w"], c["y"] + c["h"], col)
            rect(d, c["x"] + 1, c["y"] + 1, c["x"] + c["w"] - 1, c["y"] + c["h"] - 1, tuple((v + 255) // 2 for v in col[:3]) + (255,))
        elif t == "switch":
            h = c["handles"][c["positions"].index(state["switches"][c["name"]])]
            rect(d, h[0], h[1], h[0] + h[2], h[1] + h[3], (90, 90, 94, 255))
            rect(d, h[0], h[1], h[0] + h[2] - 1, h[1] + h[3] - 1, (216, 216, 220, 255))
        elif t == "button" and state.get("pressed") == c["name"]:
            ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(ov).rectangle([c["x"], c["y"], c["x"] + c["w"] - 1, c["y"] + c["h"] - 1], fill=(0, 0, 0, 85))
            img.alpha_composite(ov)
            d = ImageDraw.Draw(img)
        elif t == "meter":
            deg = c["zero_deg"] + state["meter"] * (c["full_deg"] - c["zero_deg"])
            cx, cy = c["pivot"]
            for k in range(0, int(c["radius"] / 0.35)):
                tt = k * 0.35
                X, Y = int(math.floor(cx + tt * math.sin(math.radians(deg)))), int(math.floor(cy - tt * math.cos(math.radians(deg))))
                if c["x"] <= X < c["x"] + c["w"] and c["y"] <= Y < c["y"] + c["h"]:
                    img.putpixel((X, Y), (26, 26, 26, 255))
        elif t == "display":
            text_centred(img, c["x"] + c["w"] / 2.0, c["y"] + (c["h"] - 5) // 2, state["plate"], (232, 228, 216, 255))
    img = img.resize((img.width * 4, img.height * 4), Image.NEAREST)
    img.save(path)


def preview_front(model, texture, path, view="north", scale=24):
    """An orthographic front view (north faces, looking south) or top view (up faces) of a model's elements."""
    canvas = Image.new("RGBA", (16 * scale, 16 * scale), (70, 90, 110, 255))
    elements = model["elements"]
    if view == "north":
        elements = sorted(elements, key=lambda e: -e["from"][2])
    else:
        elements = sorted(elements, key=lambda e: e["to"][1])
    for e in elements:
        face = e["faces"].get(view)
        if face is None:
            continue
        u0, v0, u1, v1 = [v * 8 for v in face["uv"]]
        tile = texture.crop((int(u0), int(v0), int(u1), int(v1)))
        if view == "north":
            x0, x1 = (16 - e["to"][0]) * scale, (16 - e["from"][0]) * scale
            y0, y1 = (16 - e["to"][1]) * scale, (16 - e["from"][1]) * scale
        else:
            x0, x1 = e["from"][0] * scale, e["to"][0] * scale
            y0, y1 = e["from"][2] * scale, e["to"][2] * scale
        w, h = int(round(x1 - x0)), int(round(y1 - y0))
        if w > 0 and h > 0:
            canvas.alpha_composite(tile.resize((w, h), Image.NEAREST), (int(round(x0)), int(round(y0))))
    canvas.save(path)


def main():
    box = box_texture()
    save(box, "textures", "block", "fire_alarm_box.png")
    box_model, box_overlay = box_models()
    write_json(os.path.join(ROOT, "models", "block", "fire_alarm_box.json"), box_model)
    write_json(os.path.join(ROOT, "models", "block", "fire_alarm_box_lamp.json"), box_overlay)
    write_json(os.path.join(ROOT, "models", "item", "fire_alarm_box.json"), {"parent": "radiocraft:block/fire_alarm_box"})

    for colour in PULL_COLOURS:
        name = "pull_station" if colour == "red" else colour + "_pull_station"
        save(pull_texture(colour), "textures", "block", name + ".png")
        c_up, c_down = pull_models(name)
        write_json(os.path.join(ROOT, "models", "block", name + ".json"), c_up)
        write_json(os.path.join(ROOT, "models", "block", name + "_pulled.json"), c_down)
        write_json(os.path.join(ROOT, "models", "item", name + ".json"), {"parent": "radiocraft:block/" + name})
        outdoor_name = "outdoor_pull_box" if colour == "red" else colour + "_outdoor_pull_box"
        save(outdoor_texture(colour), "textures", "block", outdoor_name + ".png")
        o_up, o_down = outdoor_models(outdoor_name)
        write_json(os.path.join(ROOT, "models", "block", outdoor_name + ".json"), o_up)
        write_json(os.path.join(ROOT, "models", "block", outdoor_name + "_pulled.json"), o_down)
        write_json(os.path.join(ROOT, "models", "item", outdoor_name + ".json"), {"parent": "radiocraft:block/" + outdoor_name})
    pull = pull_texture()
    pull_up, pull_down = pull_models()

    outdoor = outdoor_texture()
    outdoor_up, outdoor_down = outdoor_models()

    bell = bell_texture()
    save(bell, "textures", "block", "fire_bell.png")
    bell_json = bell_model()
    write_json(os.path.join(ROOT, "models", "block", "fire_bell.json"), bell_json)
    write_json(os.path.join(ROOT, "models", "item", "fire_bell.json"), {"parent": "radiocraft:block/fire_bell"})

    cable_png, mount_png = wiring_textures()
    save(cable_png, "textures", "block", "fire_alarm_cable.png")
    save(mount_png, "textures", "block", "fire_alarm_mount.png")
    wired = "Made with tools/fire_alarm_assets.py (data wire into a wall device)"
    write_wiring("fire_alarm_box", wall_wiring(0.5, 1.0, 15.5, 15.0), wired)
    write_wiring("pull_station", wall_wiring(0.0, 0.0, 16.0, 16.0), wired)
    write_wiring("panic_button", wall_wiring(0.0, 0.0, 16.0, 16.0), wired)
    write_wiring("panic_button", panic_floor_wiring(), wired)

    panic = panic_texture()
    save(panic, "textures", "block", "panic_button.png")
    panic_up, panic_down, panic_item = panic_models()
    write_json(os.path.join(ROOT, "models", "block", "panic_button.json"), panic_up)
    write_json(os.path.join(ROOT, "models", "block", "panic_button_pressed.json"), panic_down)
    write_json(os.path.join(ROOT, "models", "block", "panic_button_wall.json"), mounted(to_wall(panic_up)))
    write_json(os.path.join(ROOT, "models", "block", "panic_button_wall_pressed.json"), mounted(to_wall(panic_down)))
    write_json(os.path.join(ROOT, "models", "item", "panic_button.json"), panic_item)

    panel = gui_panel()
    save(panel, "textures", "gui", "fire_alarm", "fire_alarm_box.png")
    layout = gui_layout()
    write_json(os.path.join(ROOT, "gui_layouts", "fire_alarm_box.json"), layout)
    program = gui_program_panel()
    save(program, "textures", "gui", "fire_alarm", "fire_alarm_box_program.png")
    write_json(os.path.join(ROOT, "gui_layouts", "fire_alarm_box_program.json"), gui_program_layout())
    print("wrote the fire alarm box, pull station, panic button and the box's screen")

    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        preview_gui(panel, layout, {"lamps": {"power_lamp": True}, "switches": {"drill": "OFF"},
                                    "meter": 0.7, "plate": "BOX 0012  151.880"}, os.path.join(out, "fire_box_gui_normal.png"))
        preview_gui(panel, layout, {"lamps": {"power_lamp": True, "trouble_lamp": True, "alarm_lamp": True, "zone_lamp_1": True, "zone_lamp_4": True,
                                              "zone_lamp_6": True},
                                    "switches": {"drill": "DRILL"}, "meter": 0.15, "plate": "BOX 0012  151.880",
                                    "pressed": "reset"}, os.path.join(out, "fire_box_gui_alarm.png"))
        program.resize((G_W * 4, G_H * 4), Image.NEAREST).save(os.path.join(out, "fire_box_program.png"))
        preview_front(box_model, box, os.path.join(out, "fire_box_front.png"))
        lit = dict(box_model)
        lit["elements"] = box_model["elements"] + box_overlay["elements"]
        preview_front(lit, box, os.path.join(out, "fire_box_front_lit.png"))
        preview_front(pull_up, pull, os.path.join(out, "pull_station_front.png"), scale=32)
        preview_front(pull_down, pull, os.path.join(out, "pull_station_pulled_front.png"), scale=32)
        preview_front(panic_up, panic, os.path.join(out, "panic_button_top.png"), view="up", scale=32)
        preview_front(outdoor_up, outdoor, os.path.join(out, "outdoor_front.png"), scale=32)
        preview_front(bell_json, bell, os.path.join(out, "fire_bell_front.png"), scale=32)
        preview_front(outdoor_down, outdoor, os.path.join(out, "outdoor_front_pulled.png"), scale=32)
        print("Previews in", out)


if __name__ == "__main__":
    main()
