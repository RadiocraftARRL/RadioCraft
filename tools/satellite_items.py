"""Item textures for satellites (2026-11-11, doc/SatellitePlan.md): the amateur and commercial satellite contracts and
the satellite license. Simple 16 x 16 placeholders, listed on doc/ModelDesignBounty.md for an artist to redo.

- amateur_satellite_contract.png / commercial_satellite_contract.png: a rolled paper contract tied with a ribbon, a
  small satellite (body and two solar panels) drawn on the open part, green for amateur and red for commercial.
- satellite_license.png: a flat cream certificate with a navy border line, a small grey satellite and a gold seal.

Run from the repository root:  python tools/satellite_items.py
"""
import os
from PIL import Image

OUT = os.path.join('src', 'main', 'resources', 'assets', 'radiocraft', 'textures', 'item')

PAPER = (232, 223, 192, 255)
PAPER_SHADE = (196, 184, 150, 255)
PAPER_DARK = (150, 136, 100, 255)
OUTLINE = (92, 78, 52, 255)
INK = (70, 70, 80, 255)
NAVY = (31, 58, 104, 255)
GOLD = (212, 160, 23, 255)
GOLD_DARK = (150, 108, 10, 255)
GREY = (150, 150, 158, 255)
PANEL = (60, 80, 140, 255)


def new():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def rect(img, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour)


def satellite(img, cx, cy, body, panel=PANEL):
    """A satellite 7 wide: a 3 x 3 body with a solar panel either side on a strut."""
    rect(img, cx - 1, cy - 1, cx + 1, cy + 1, body)
    img.putpixel((cx, cy), tuple(min(255, c + 60) for c in body[:3]) + (255,))  # A glint on the body.
    for side in (-1, 1):
        img.putpixel((cx + 2 * side, cy), INK)  # Strut.
        rect(img, cx + 3 * side if side > 0 else cx - 4, cy - 1, cx + 4 if side > 0 else cx - 3, cy + 1, panel)
    img.putpixel((cx, cy - 2), INK)  # Antenna.


def contract(colour, name):
    img = new()
    # The open sheet, slanted a little by its curl, on rolls at top and bottom.
    rect(img, 3, 3, 12, 12, PAPER)
    for x in range(3, 13):
        img.putpixel((x, 2), PAPER_SHADE)
        img.putpixel((x, 13), PAPER_SHADE)
    for y in range(2, 14):
        img.putpixel((2, y), OUTLINE)
        img.putpixel((13, y), OUTLINE)
    # The rolls: a darker tube at each end.
    for x in range(1, 15):
        img.putpixel((x, 1), PAPER_DARK)
        img.putpixel((x, 14), PAPER_DARK)
        img.putpixel((x, 0), OUTLINE)
        img.putpixel((x, 15), OUTLINE)
    for y in (0, 1, 14, 15):
        img.putpixel((0, y), OUTLINE)
        img.putpixel((15, y), OUTLINE)
    # Lines of text under the satellite.
    for y in (9, 11):
        for x in range(4, 12):
            if (x + y) % 3:
                img.putpixel((x, y), INK)
    satellite(img, 8, 5, colour)
    # The ribbon round the lower roll, in the satellite's colour.
    dark = tuple(max(0, c - 70) for c in colour[:3]) + (255,)
    rect(img, 7, 13, 8, 15, colour)
    img.putpixel((6, 15), dark)
    img.putpixel((9, 15), dark)
    img.save(os.path.join(OUT, name + '.png'))


def license_item():
    img = new()
    rect(img, 1, 2, 14, 13, PAPER)
    for x in range(1, 15):
        img.putpixel((x, 1), OUTLINE)
        img.putpixel((x, 14), OUTLINE)
    for y in range(1, 15):
        img.putpixel((0, y), OUTLINE)
        img.putpixel((15, y), OUTLINE)
    for x in range(2, 14):  # The navy border line.
        img.putpixel((x, 3), NAVY)
        img.putpixel((x, 12), NAVY)
    for y in range(3, 13):
        img.putpixel((2, y), NAVY)
        img.putpixel((13, y), NAVY)
    satellite(img, 7, 6, GREY)
    for x in range(4, 9):
        img.putpixel((x, 10), INK)
    rect(img, 10, 9, 12, 11, GOLD)  # The seal.
    img.putpixel((11, 10), GOLD_DARK)
    img.save(os.path.join(OUT, 'satellite_license.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    contract((70, 200, 70, 255), 'amateur_satellite_contract')
    contract((210, 50, 40, 255), 'commercial_satellite_contract')
    license_item()
    print('wrote 3 satellite item textures to', OUT)
