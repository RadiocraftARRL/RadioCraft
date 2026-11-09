"""Cleans two patterns off the VHF handheld screen texture and its _poor and _pure variants (2026-11-09):
  - the checkerboard on the upper-left panel (behind "VHF FM" and round the small grille) becomes the panel's flat
    colour; the panel's bevel, the lettering and the grille are left alone;
  - the dot grid on the keypad backplate (each dot a light pixel with a dark one down-right of it) becomes the plate's
    flat colour; the keys, their shadows and the corner screws are left alone.
Nothing moves. The first run copies each original to <name>_old.png; later runs start again from that copy, so the
script can be rerun. The _widgets sheets are not touched. Needs Pillow.
Run: python tools/hf_gui_proposals/vhf_handheld_cleanup.py
"""
import os
import shutil

from PIL import Image

GUI = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets",
                   "radiocraft", "textures", "gui")
NAMES = ["vhf_handheld", "vhf_handheld_poor", "vhf_handheld_pure"]

CHECKER = (27, 31, 66, 97)  # Inclusive: inside the upper-left panel's bevel, left of the speaker slots.
CHECKER_FLAT_AT = (27, 31)  # A pixel of the panel's own flat colour (the checkerboard's darker squares).
PLATE = (26, 165, 150, 239)  # Inclusive: the keypad backplate inside its rim.
PLATE_FLAT_AT = (28, 168)


def clean(img):
    px = img.load()
    # The checkerboard: every tile colour in the panel becomes its flat colour; lettering and grille differ in colour.
    flat = px[CHECKER_FLAT_AT]
    tiles = {(81, 93, 97), (84, 96, 100), (87, 99, 103), (92, 104, 108), (95, 107, 111), (101, 112, 116),
             (104, 115, 119)}
    x0, y0, x1, y1 = CHECKER
    n1 = 0
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if px[x, y][:3] in tiles and px[x, y] != flat:
                px[x, y] = flat
                n1 += 1
    # The keypad dots: a light pixel with its dark shadow one down and right, both on the plate.
    plate = px[PLATE_FLAT_AT]
    dot, shadow = (88, 100, 104), (31, 36, 41)
    x0, y0, x1, y1 = PLATE
    n2 = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            if px[x, y][:3] == dot and px[x + 1, y + 1][:3] == shadow:
                px[x, y] = plate
                px[x + 1, y + 1] = plate
                n2 += 2
    # Half dots, the other pixel hidden under a key's outline. The grid's light pixels sit at x = 30 + 6k and
    # their shadows at x = 31 + 6k; a stray one on open plate (2+ plate neighbours) goes. The keys' own corner
    # shading and the screws' shadows are off that grid and stay.
    dots = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = px[x, y][:3]
            on_grid = (c == dot and (x - 30) % 6 == 0) or (c == shadow and (x - 31) % 6 == 0)
            if on_grid and sum(px[x + dx, y + dy] == plate for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) >= 2:
                dots.append((x, y))
    for x, y in dots:
        px[x, y] = plate
    return n1, n2 + len(dots)


def main():
    for name in NAMES:
        path = os.path.join(GUI, name + ".png")
        old = os.path.join(GUI, name + "_old.png")
        if not os.path.exists(old):
            shutil.copyfile(path, old)
        img = Image.open(old).convert("RGBA")
        n1, n2 = clean(img)
        img.save(path)
        print("%s: %d checkerboard pixels flattened, %d keypad dot pixels removed" % (name, n1, n2))


if __name__ == "__main__":
    main()
