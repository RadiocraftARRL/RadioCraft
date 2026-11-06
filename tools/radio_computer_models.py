"""Builds the radio computer's placeholder art (1.12 step 5; see doc/ModelDesignBounty.md):

- computer_monitor: the moderation computer's CRT and keyboard (tools/moderation_computer_model.py) without its desktop
  case, in a darker putty colour, the glass showing a blue DOS screen. The glass (model units x 3.75-12.25,
  y 3.25-9.5 on the north face, z 6) is where ComputerMonitorRenderer draws the live screen.
- radio_computer: the PC itself, a full-height putty cabinet (the monitor sits on it) with a 5.25" and a 3.5" drive, power button and lamps.
- scanner: a flatbed scanner and printer with its lid down and a paper tray at the back.
- the floppy disk items: one shared disk texture and a white label layer (tinted per program in ClientSetupEvents),
  item models for the blank disk and every program's disk.

Textures are 128x128 with 4 texture pixels per model unit, so UVs are pixels / 8. Fronts face north; blockstates and
block item models come from the data generator (complexHorizontalBlockWithItem).

    python tools/radio_computer_models.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft') + '/'

PUTTY = (196, 190, 168, 255)
PUTTY_LIGHT = (214, 208, 188, 255)
PUTTY_SHADE = (172, 165, 142, 255)
PUTTY_DARK = (140, 134, 112, 255)
SEAM = (112, 106, 88, 255)
BLACK = (20, 20, 22, 255)
SLOT = (44, 42, 38, 255)
GLASS_EDGE = (30, 32, 36, 255)
DOS_BLUE = (0, 0, 170, 255)
DOS_GREY = (170, 170, 170, 255)
DOS_CYAN = (0, 170, 170, 255)
WHITE = (255, 255, 255, 255)
YELLOW = (255, 255, 85, 255)
GREEN = (60, 220, 60, 255)
AMBER = (230, 160, 40, 255)
KEY = (226, 222, 206, 255)
KEY_SHADE = (140, 134, 116, 255)
GLASS = (60, 80, 96, 255)
GLASS_LIGHT = (110, 140, 160, 255)
PAPER = (240, 238, 228, 255)


def canvas():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    return img, img.load()


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = c


def frame(px, x0, y0, x1, y1, light, dark):
    rect(px, x0, y0, x1, y0 + 1, light)
    rect(px, x0, y0, x0 + 1, y1, light)
    rect(px, x0, y1 - 1, x1, y1, dark)
    rect(px, x1 - 1, y0, x1, y1, dark)


def swatches(px):
    """Plain colours at the top right, stretched over faces."""
    rect(px, 96, 0, 104, 8, PUTTY)
    rect(px, 104, 0, 112, 8, PUTTY_LIGHT)
    rect(px, 112, 0, 120, 8, PUTTY_SHADE)
    rect(px, 120, 0, 128, 8, PUTTY_DARK)
    rect(px, 96, 8, 104, 16, SEAM)
    rect(px, 104, 8, 112, 16, BLACK)


def uv(x0, y0, x1, y1):
    return [x0 / 8, y0 / 8, x1 / 8, y1 / 8]


PLAIN = uv(97, 1, 103, 7)
LIGHT = uv(105, 1, 111, 7)
SHADE = uv(113, 1, 119, 7)
DARK = uv(121, 1, 127, 7)


def box(name, frm, to, north, south, east, west, up, down):
    faces = {}
    for face, area in (('north', north), ('south', south), ('east', east), ('west', west), ('up', up), ('down', down)):
        faces[face] = {'uv': area, 'texture': '#0'}
    return {'name': name, 'from': frm, 'to': to, 'faces': faces}


def save_model(name, elements):
    texture = 'radiocraft:block/' + name
    model = {
        'credit': 'RadioCraft placeholder, made by script (see doc/ModelDesignBounty.md)',
        'parent': 'block/block',
        'texture_size': [128, 128],
        'textures': {'0': texture, 'particle': texture},
        'elements': elements,
    }
    with open(ROOT + 'models/block/' + name + '.json', 'w') as f:
        json.dump(model, f, indent=2)


# -------------------- MONITOR --------------------
img, px = canvas()
# Monitor front, 11 x 9.5 units = 44 x 38 px at (0, 0): the bezel, the glass showing a DOS program (EDIT's look).
rect(px, 0, 0, 44, 38, PUTTY)
frame(px, 0, 0, 44, 38, PUTTY_LIGHT, PUTTY_DARK)
rect(px, 4, 3, 40, 30, GLASS_EDGE)
rect(px, 5, 4, 39, 29, DOS_BLUE)
rect(px, 5, 4, 39, 6, DOS_GREY)         # the title bar
rect(px, 5, 27, 39, 29, DOS_CYAN)       # the key bar
for row, length in ((8, 24), (10, 30), (12, 18), (14, 27), (16, 21), (18, 29), (20, 12)):  # lines of text
    rect(px, 7, row, 7 + length, row + 1, DOS_GREY)
rect(px, 7, 22, 9, 23, YELLOW)          # the cursor
rect(px, 34, 32, 37, 34, GREEN)         # power lamp
rect(px, 6, 32, 16, 33, PUTTY_SHADE)    # a badge
rect(px, 18, 32, 22, 34, PUTTY_DARK)    # brightness knob
rect(px, 24, 32, 28, 34, PUTTY_DARK)    # contrast knob
# Keyboard top, 12 x 4.5 units = 48 x 18 px at (0, 58).
rect(px, 0, 58, 48, 76, PUTTY)
frame(px, 0, 58, 48, 76, PUTTY_LIGHT, PUTTY_DARK)
for y in (60, 63, 66, 69):
    for x in range(2, 36, 3):
        rect(px, x, y, x + 2, y + 2, KEY)
        rect(px, x, y + 2, x + 2, y + 3, KEY_SHADE)
rect(px, 8, 72, 28, 74, KEY)
rect(px, 8, 74, 28, 75, KEY_SHADE)
for y in (60, 63, 66, 69):
    for x in (38, 41, 44):
        rect(px, x, y, x + 2, y + 2, KEY)
        rect(px, x, y + 2, x + 2, y + 3, KEY_SHADE)
# Keyboard front edge, 12 x 1.5 units = 48 x 6 px at (0, 78).
rect(px, 0, 78, 48, 84, PUTTY_SHADE)
rect(px, 0, 83, 48, 84, PUTTY_DARK)
# The tube's side, 6 x 7.5 units = 24 x 30 px at (56, 0), with vents.
rect(px, 56, 0, 80, 30, PUTTY_SHADE)
for y in range(6, 26, 3):
    rect(px, 60, y, 76, y + 1, PUTTY_DARK)
swatches(px)
img.save(ROOT + 'textures/block/computer_monitor.png')

save_model('computer_monitor', [
    box('keyboard', [2, 0, 0.5], [14, 1.5, 5], uv(0, 78, 48, 84), SHADE, SHADE, SHADE, uv(0, 58, 48, 76), DARK),
    box('stand', [5.5, 0, 8], [10.5, 1, 13], SHADE, SHADE, SHADE, SHADE, PLAIN, DARK),
    box('bezel', [2.5, 1, 6], [13.5, 10.5, 9], uv(0, 0, 44, 38), SHADE, SHADE, SHADE, LIGHT, SHADE),
    box('tube', [4, 2, 9], [12, 9.5, 15], SHADE, DARK, uv(56, 0, 80, 30), uv(56, 0, 80, 30), PLAIN, SHADE),
])

# -------------------- COMPUTER --------------------
img, px = canvas()
# Front, 14 x 16 units = 56 x 64 px at (0, 0): two drive bays, the power switch, lamps, a badge and vents.
rect(px, 0, 0, 56, 64, PUTTY)
frame(px, 0, 0, 56, 64, PUTTY_LIGHT, PUTTY_DARK)
rect(px, 6, 6, 50, 16, PUTTY_SHADE)     # 5.25" drive
frame(px, 6, 6, 50, 16, PUTTY_LIGHT, PUTTY_DARK)
rect(px, 9, 10, 47, 12, SLOT)
rect(px, 44, 13, 47, 15, PUTTY_DARK)    # its latch
rect(px, 6, 20, 34, 28, PUTTY_SHADE)    # 3.5" drive
frame(px, 6, 20, 34, 28, PUTTY_LIGHT, PUTTY_DARK)
rect(px, 9, 23, 29, 25, SLOT)
rect(px, 30, 25, 33, 27, PUTTY_DARK)    # eject button
rect(px, 9, 26, 11, 27, GREEN)          # drive lamp
for x in range(6, 50, 3):               # vents
    rect(px, x, 46, x + 1, 58, PUTTY_DARK)
rect(px, 40, 22, 48, 30, PUTTY_LIGHT)   # power switch
frame(px, 40, 22, 48, 30, WHITE, PUTTY_DARK)
rect(px, 40, 34, 42, 35, GREEN)         # power lamp
rect(px, 44, 34, 46, 35, AMBER)         # disk lamp
rect(px, 6, 34, 26, 38, PUTTY_SHADE)    # badge
rect(px, 7, 35, 25, 37, DOS_BLUE)
# Side, 14 x 16 units = 56 x 64 px at (0, 64), with vent slots.
rect(px, 0, 64, 56, 128, PUTTY_SHADE)
frame(px, 0, 64, 56, 128, PUTTY, PUTTY_DARK)
for y in range(70, 122, 4):
    rect(px, 40, y, 52, y + 1, SEAM)
# Back, at (60, 64): the power socket and the ports.
rect(px, 60, 64, 116, 128, PUTTY_DARK)
rect(px, 66, 70, 76, 78, BLACK)
for y in (84, 90, 96, 102):
    rect(px, 70, y, 100, y + 3, SLOT)
swatches(px)
img.save(ROOT + 'textures/block/radio_computer.png')

FRONT = uv(0, 0, 56, 64)
SIDE = uv(0, 64, 56, 128)
BACK = uv(60, 64, 116, 128)
save_model('radio_computer', [
    box('cabinet', [1, 0, 1], [15, 16, 15], FRONT, BACK, SIDE, SIDE, LIGHT, DARK),
])

# -------------------- SCANNER --------------------
img, px = canvas()
# Lid top, 14 x 13 units = 56 x 52 px at (0, 0).
rect(px, 0, 0, 56, 52, PUTTY_LIGHT)
frame(px, 0, 0, 56, 52, WHITE, PUTTY_SHADE)
rect(px, 4, 4, 52, 48, PUTTY)
rect(px, 22, 46, 34, 48, PUTTY_DARK)    # the lid's handle
# Front, 14 x 4 units = 56 x 16 px at (0, 56): the buttons and lamps; the print slot below the lid.
rect(px, 0, 56, 56, 72, PUTTY)
frame(px, 0, 56, 56, 72, PUTTY_LIGHT, PUTTY_DARK)
rect(px, 4, 66, 40, 69, SLOT)           # where prints come out
rect(px, 44, 60, 48, 63, GREEN)         # ready lamp
rect(px, 44, 65, 48, 68, PUTTY_DARK)    # start button
rect(px, 50, 65, 53, 68, PUTTY_DARK)    # copy button
# Side, 13 x 4 units = 52 x 16 px at (0, 76).
rect(px, 0, 76, 52, 92, PUTTY_SHADE)
frame(px, 0, 76, 52, 92, PUTTY, PUTTY_DARK)
rect(px, 0, 79, 52, 80, SEAM)           # the lid's edge
# Paper tray, 12 x 3 units = 48 x 12 px at (0, 96): paper standing in it.
rect(px, 0, 96, 48, 108, PAPER)
for y in range(98, 108, 3):
    rect(px, 0, y, 48, y + 1, (220, 216, 204, 255))
swatches(px)
img.save(ROOT + 'textures/block/scanner.png')

save_model('scanner', [
    box('body', [1, 0, 2], [15, 4, 15], uv(0, 56, 56, 72), SHADE, uv(0, 76, 52, 92), uv(0, 76, 52, 92), uv(0, 0, 56, 52), DARK),
    box('tray', [2, 4, 13], [14, 7, 14], uv(0, 96, 48, 108), uv(0, 96, 48, 108), PLAIN, PLAIN, PLAIN, PLAIN),
])

# -------------------- FLOPPY DISKS --------------------
DISK = (36, 36, 40, 255)
DISK_LIGHT = (70, 70, 78, 255)
SHUTTER = (176, 180, 186, 255)
SHUTTER_DARK = (120, 124, 130, 255)
disk = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
p = disk.load()
rect(p, 2, 2, 14, 14, DISK)
rect(p, 2, 2, 14, 3, DISK_LIGHT)
rect(p, 2, 2, 3, 14, DISK_LIGHT)
rect(p, 13, 2, 14, 3, (0, 0, 0, 0))     # the corner cut
rect(p, 5, 2, 11, 6, SHUTTER)           # the metal shutter
rect(p, 8, 3, 10, 5, SHUTTER_DARK)
rect(p, 3, 12, 4, 13, DISK_LIGHT)       # write-protect tab
disk.save(ROOT + 'textures/item/floppy_disk.png')
label = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
p = label.load()
rect(p, 4, 8, 12, 13, (255, 255, 255, 255))   # the label, tinted per program
rect(p, 5, 9, 11, 10, (210, 210, 210, 255))   # a line written on it
label.save(ROOT + 'textures/item/floppy_disk_label.png')


def floppy_model(name):
    model = {'parent': 'minecraft:item/generated',
             'textures': {'layer0': 'radiocraft:item/floppy_disk', 'layer1': 'radiocraft:item/floppy_disk_label'}}
    with open(ROOT + 'models/item/' + name + '.json', 'w') as f:
        json.dump(model, f, indent=2)


# Every program on a floppy (ComputerApps, kinds CRAFTED and SECRET).
PROGRAMS = ['bookedit', 'logbook', 'freqman', 'programmer', 'solar', 'aprs', 'wefax', 'radiofax', 'sstv', 'df',
            'snake', 'falling_blocks', 'music', 'bible', 'adsb', 'rtty', 'bbs', 'propagation', 'repeaters', 'license']
floppy_model('floppy_disk')
for program in PROGRAMS:
    floppy_model('floppy_' + program)
