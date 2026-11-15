"""Art for the fire alarm controller (2026-11-11), after the maintainer's photo of a DMP-704 rack receiver: a cream
rack panel with ears and black screws on a dark vented chassis; left, the printer window behind a hinged cream door
with an instruction card taped above it and a dealer sticker below; middle, the hinged strip with its blue line, the
model name (RC-704 here, no maker's name), a key lock and the round KEYBOARD jack; right, the green CRT in a cream
bezel outlined in blue. The glass is its own element, CRT, recessed behind the panel: the game draws the TUI on it.

Writes, under src/main/resources/assets/radiocraft/:
  textures/block/fire_alarm_controller.png          128x128 (front at 8 px a model unit; top, sides and parts)
  models/block/fire_alarm_controller.json           front north; the ALARM lamp under the CRT dark
  models/block/fire_alarm_controller_alarm.json     the same with the ALARM lamp lit (the blockstate's alarm=true)
  models/item/fire_alarm_controller.json
  textures/gui/fire_alarm/fire_alarm_controller.png 256x176: the CRT's close-up, the screen left blank
  gui_layouts/fire_alarm_controller.json            HfPanel's format: one control, "crt" (the display)

Needs Pillow. Run: python tools/fire_alarm_controller_assets.py [--preview DIR]
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fire_alarm_assets import (ROOT, bevel, block_json, disc, element, rect, save, text, text_centred,  # noqa: E402
                               write_json)

CREAM, CREAM_LIGHT, CREAM_DARK, CREAM_SHADOW = (226, 218, 188, 255), (242, 236, 212, 255), (196, 188, 158, 255), (160, 152, 124, 255)
BLUE = (52, 86, 176, 255)
CHASSIS, CHASSIS_LIGHT, CHASSIS_DARK = (40, 40, 42, 255), (66, 66, 70, 255), (18, 18, 20, 255)
SCREW, SCREW_LIGHT = (22, 22, 24, 255), (90, 90, 96, 255)
GLASS, GLASS_EDGE = (10, 26, 16, 255), (24, 44, 30, 255)
CARD, CARD_INK = (246, 246, 240, 255), (70, 70, 90, 255)
INK = (40, 40, 44, 255)
LAMP_DARK, LAMP_LIT, LAMP_HOT = (100, 20, 18, 255), (255, 52, 36, 255), (255, 170, 150, 255)
STICKER = (214, 196, 120, 255)

# ---- The block: 16 wide, 9 tall, 14 deep (z 1 to 15); the panel's front at z 1, the glass back at z 1.4 ----
FRONT = (0, 0, 128, 72)          # the north face, as the viewer sees it (x 16 at its left), 8 px a unit
TOP = (0, 72, 64, 128)           # vented top, 4 px a unit (16 x 14)
SIDE = (64, 72, 92, 90)          # sides, back and bottom: plain chassis
INNER = (92, 72, 100, 80)        # the bezel's inner walls round the glass
T_GLASS = (100, 72, 108, 80)
T_LAMP = (108, 72, 112, 76)
T_LAMP_LIT = (112, 72, 116, 76)
T_SCREW = (116, 72, 120, 76)
WINDOW = (76, 13, 118, 51)       # the glass, front pixels: 5.25 x 4.75 units
LAMP = (106, 61, 111, 66)        # the ALARM lamp under the CRT, front pixels
PANEL_Z, GLASS_Z, BACK_Z = 1.0, 1.4, 15.0


def fx(px):
    """Front pixel x (from the viewer's left) to model x."""
    return 16.0 - px / 8.0


def fy(py):
    return 9.0 - py / 8.0


def screw(d, cx, cy):
    disc(d, cx, cy, 3, SCREW)
    rect(d, cx - 1, cy - 1, cx, cy, SCREW_LIGHT)


def block_texture():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Front panel.
    rect(d, 0, 0, 128, 72, CREAM)
    bevel(d, 0, 0, 128, 72, CREAM_LIGHT, CREAM_SHADOW)
    for x in (5, 123):  # rack ears: a seam and two screws each
        rect(d, x, 1, x + 1, 71, CREAM_DARK)
    for cx in (2, 125):
        screw(d, cx + 1, 8)
        screw(d, cx + 1, 63)
    # Instruction card, taped.
    rect(d, 8, 4, 42, 17, CARD)
    for i, y in enumerate((6, 9, 12, 15)):
        rect(d, 10 + (i % 2) * 2, y, 38 - i * 3, y + 1, CARD_INK)
    rect(d, 20, 3, 26, 5, (230, 230, 200, 255))  # tape
    # Printer door with its window and the paper roll's edge behind.
    rect(d, 7, 20, 49, 66, CREAM_LIGHT)
    bevel(d, 7, 20, 49, 66, CREAM_LIGHT, CREAM_SHADOW)
    rect(d, 13, 30, 39, 50, CHASSIS_DARK)
    rect(d, 13, 46, 39, 50, (230, 228, 220, 255))
    rect(d, 13, 45, 39, 46, (190, 188, 180, 255))
    bevel(d, 13, 30, 39, 50, CHASSIS_DARK, CREAM_SHADOW)
    screw(d, 44, 26)
    screw(d, 44, 58)
    rect(d, 16, 60, 34, 65, STICKER)  # dealer sticker
    rect(d, 17, 62, 33, 63, (180, 60, 50, 255))
    # The hinged strip: hinge, the blue line, the model name, the lock, the KEYBOARD jack.
    rect(d, 49, 18, 51, 66, CREAM_SHADOW)
    for y in range(20, 66, 6):
        rect(d, 49, y, 51, y + 3, (150, 150, 150, 255))
    rect(d, 52, 16, 68, 64, CREAM_LIGHT)
    bevel(d, 52, 16, 68, 64, CREAM_LIGHT, CREAM_SHADOW)
    rect(d, 55, 19, 56, 61, BLUE)                 # the blue line down the strip
    for i, ch in enumerate("RC704"):               # the model name, stacked
        text(img, 60, 20 + i * 6, ch, BLUE)
    disc(d, 61, 54, 3, (150, 150, 156, 255))      # key lock
    rect(d, 60, 53, 62, 55, INK)
    disc(d, 61, 61, 2, SCREW)                     # KEYBOARD jack
    # CRT bezel: blue outline, cream bezel, a shadow into the window.
    rect(d, 71, 7, 123, 58, BLUE)
    rect(d, 72, 8, 122, 57, CREAM)
    rect(d, 74, 10, 120, 54, CREAM_DARK)
    rect(d, 75, 11, 119, 53, CREAM_SHADOW)
    rect(d, WINDOW[0], WINDOW[1], WINDOW[2], WINDOW[3], GLASS)
    text(img, 76, 62, "ALARM", INK)
    rect(d, LAMP[0] - 1, LAMP[1] - 1, LAMP[2] + 1, LAMP[3] + 1, SCREW)
    rect(d, LAMP[0], LAMP[1], LAMP[2], LAMP[3], LAMP_DARK)
    # Top: dark chassis with rows of vent slots.
    rect(d, 0, 72, 64, 128, CHASSIS)
    for row, y in enumerate(range(76, 124, 6)):
        for x in range(4 + (row % 2) * 4, 60, 9):
            rect(d, x, y, x + 6, y + 2, CHASSIS_DARK)
    rect(d, 64, 72, 92, 90, CHASSIS)
    bevel(d, 64, 72, 92, 90, CHASSIS_LIGHT, CHASSIS_DARK)
    rect(d, 92, 72, 100, 80, CREAM_SHADOW)
    rect(d, 100, 72, 108, 80, GLASS)
    rect(d, 108, 72, 112, 76, LAMP_DARK)
    rect(d, 112, 72, 116, 76, LAMP_LIT)
    rect(d, 113, 73, 115, 75, LAMP_HOT)
    rect(d, 116, 72, 120, 76, SCREW)
    return img


def sub(px0, py0, px1, py1):
    """A piece of the front face, as a texture rect."""
    return (px0, py0, px1, py1)


def block_models():
    side = {"east": SIDE, "west": SIDE, "south": SIDE, "down": SIDE}
    chassis = element("chassis", (0, 0, 1.5), (16, 9, BACK_Z), dict(side, north=FRONT, up=TOP))
    wx0, wy0, wx1, wy1 = WINDOW
    plates = []

    def plate(name, px0, py0, px1, py1):
        faces = {f: INNER for f in ("east", "west", "up", "down")}
        faces["north"] = sub(px0, py0, px1, py1)
        plates.append(element(name, (fx(px1), fy(py1), PANEL_Z), (fx(px0), fy(py0), 1.5), faces))

    plate("panel_left", 0, 0, wx0, 72)          # all of the panel left of the glass (as seen)
    plate("panel_right", wx1, 0, 128, 72)
    plate("panel_above", wx0, 0, wx1, wy0)
    plate("panel_below", wx0, wy1, wx1, 72)
    crt = element("CRT", (fx(wx1), fy(wy1), GLASS_Z), (fx(wx0), fy(wy0), 1.5), {"north": T_GLASS})

    def lamp(lit):
        x0, y0, x1, y1 = LAMP
        tex = T_LAMP_LIT if lit else T_LAMP
        e = element("alarm_lamp", (fx(x1), fy(y1), PANEL_Z - 0.4), (fx(x0), fy(y0), PANEL_Z), {f: tex for f in ("north", "east", "west", "up", "down")})
        if lit:  # Glows in the dark: NeoForge's per-element light, full bright and unshaded.
            e["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        return e

    def knob(name, cx, cy, r, depth):
        return element(name, (fx(cx + r), fy(cy + r), PANEL_Z - depth), (fx(cx - r), fy(cy - r), PANEL_Z),
                       {f: T_SCREW for f in ("north", "east", "west", "up", "down")})

    parts = [knob("keyboard_jack", 61, 61, 1.5, 0.25), knob("key_lock", 61, 54, 2, 0.3)]
    credit = "Made with tools/fire_alarm_controller_assets.py (after a 1990s rack fire alarm receiver)"
    display = {"thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
               "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]}}
    plain = block_json(credit, "fire_alarm_controller", [chassis] + plates + [crt, lamp(False)] + parts)
    alarm = block_json(credit, "fire_alarm_controller", [chassis] + plates + [crt, lamp(True)] + parts)
    return plain, alarm, crt


# ---- The screen: a close-up of the CRT ----
GW, GH = 256, 176
CRT_RECT = (50, 18, 196, 140)   # x, y, w, h


def gui_texture():
    img = Image.new("RGBA", (GW, GH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, GW, GH, CREAM)
    bevel(d, 0, 0, GW, GH, CREAM_LIGHT, CREAM_SHADOW)
    # The hinged strip at the left.
    rect(d, 4, 4, 36, GH - 4, CREAM_LIGHT)
    bevel(d, 4, 4, 36, GH - 4, CREAM_LIGHT, CREAM_SHADOW)
    rect(d, 19, 12, 21, 62, BLUE)
    rect(d, 19, 92, 21, 140, BLUE)
    text_centred(img, 20, 74, "RC-704", BLUE)
    disc(d, 20, 150, 6, SCREW)
    disc(d, 20, 150, 3, (60, 60, 64, 255))
    text_centred(img, 20, 160, "KEYBD", BLUE)
    rect(d, 36, 4, 39, GH - 4, CREAM_SHADOW)  # the hinge line
    for y in range(8, GH - 8, 12):
        rect(d, 36, y, 39, y + 6, (150, 150, 150, 255))
    # The bezel: a blue outline, cream, a shadowed slope into the glass.
    x, y, w, h = CRT_RECT
    rect(d, x - 8, y - 10, x + w + 8, y + h + 12, BLUE)
    rect(d, x - 7, y - 9, x + w + 7, y + h + 11, CREAM)
    rect(d, x - 4, y - 5, x + w + 4, y + h + 5, CREAM_DARK)
    rect(d, x - 2, y - 3, x + w + 2, y + h + 3, CREAM_SHADOW)
    rect(d, x - 1, y - 1, x + w + 1, y + h + 1, GLASS_EDGE)
    rect(d, x, y, x + w, y + h, GLASS)
    return img


def gui_layout():
    x, y, w, h = CRT_RECT
    return {"texture": "radiocraft:textures/gui/fire_alarm/fire_alarm_controller.png", "width": GW, "height": GH,
            "controls": [{"name": "crt", "type": "display", "x": x, "y": y, "w": w, "h": h, "ink": "#5CFF7A"}]}


def preview(out, tex, gui):
    os.makedirs(out, exist_ok=True)
    for lit in (False, True):
        front = tex.crop(FRONT).copy()
        d = ImageDraw.Draw(front)
        if lit:
            rect(d, LAMP[0], LAMP[1], LAMP[2], LAMP[3], LAMP_LIT)
        rect(d, WINDOW[0], WINDOW[1], WINDOW[2], WINDOW[3], GLASS)
        text(front, WINDOW[0] + 2, WINDOW[1] + 2, "FIRE ALARM", (92, 255, 122, 255))
        front.resize((front.width * 5, front.height * 5), Image.NEAREST).save(
            os.path.join(out, "fire_controller_front%s.png" % ("_alarm" if lit else "")))
    g = gui.copy()
    x, y, w, h = CRT_RECT
    for row in range(15):
        text(g, x + 3, y + 3 + row * 9, ("ROW %02d " % row) + "X" * 30, (92, 255, 122, 255))
    g.resize((GW * 3, GH * 3), Image.NEAREST).save(os.path.join(out, "fire_controller_gui.png"))
    tex.resize((512, 512), Image.NEAREST).save(os.path.join(out, "fire_controller_sheet.png"))


def main():
    tex = block_texture()
    save(tex, "textures", "block", "fire_alarm_controller.png")
    plain, alarm, crt = block_models()
    write_json(os.path.join(ROOT, "models", "block", "fire_alarm_controller.json"), plain)
    write_json(os.path.join(ROOT, "models", "block", "fire_alarm_controller_alarm.json"), alarm)
    write_json(os.path.join(ROOT, "models", "item", "fire_alarm_controller.json"), {"parent": "radiocraft:block/fire_alarm_controller"})
    gui = gui_texture()
    save(gui, "textures", "gui", "fire_alarm", "fire_alarm_controller.png")
    write_json(os.path.join(ROOT, "gui_layouts", "fire_alarm_controller.json"), gui_layout())
    print("CRT element: from", crt["from"], "to", crt["to"])
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1], tex, gui)


if __name__ == "__main__":
    main()
