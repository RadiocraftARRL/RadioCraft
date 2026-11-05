"""Placeholder textures and item models for the FCC field gear (1.12 step 10).

Writes, with only the standard library:
  textures/item/fcc_hat.png, fcc_jacket.png, fcc_banner_pattern.png, fcc_badge.png (16x16 items)
  textures/models/armor/fcc_layer_1.png (64x32: a 1970s brown felt fedora and a mustard corduroy jacket with a wide tie)
  textures/entity/banner/fcc.png and textures/entity/shield/fcc.png (64x64 white masks: the FCC seal and "FCC")
  models/item/<item>.json (item/generated)
They are placeholders: the proper art (and a real fedora brim model) is on doc/ModelDesignBounty.md.
Run: python tools/fcc_assets.py
"""
import json
import os
import struct
import zlib

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")
CLEAR = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)

FELT, FELT_DARK, BAND = (92, 64, 42, 255), (66, 45, 30, 255), (28, 24, 20, 255)
JACKET, JACKET_DARK, SHIRT, TIE = (168, 120, 52, 255), (128, 88, 36, 255), (236, 230, 212, 255), (120, 36, 30, 255)
GOLD, GOLD_DARK, ENAMEL, PAPER, PAPER_EDGE = (226, 186, 60, 255), (150, 112, 28, 255), (38, 66, 150, 255), \
    (222, 208, 170, 255), (170, 150, 110, 255)

# 5x7 and 3x5 capitals for the banner and shield.
BIG = {
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
}
SMALL = {
    "F": ["###", "#..", "##.", "#..", "#.."],
    "C": [".##", "#..", "#..", "#..", ".##"],
}
# A lightning bolt in the seal, 5 wide and 7 high (the old FCC seal had bolts).
BOLT = ["...##", "..##.", ".####", "..##.", ".##..", "##...", "#...."]


def png(path, pixels):
    """Writes an RGBA PNG from rows of (r, g, b, a) tuples."""
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def canvas(width, height):
    return [[CLEAR] * width for _ in range(height)]


def fill(img, x0, y0, x1, y1, colour):
    """Fills x0..x1-1, y0..y1-1."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y][x] = colour


def glyphs(img, x, y, text, font, colour):
    for ch in text:
        rows = font[ch]
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c == "#":
                    img[y + dy][x + dx] = colour
        x += len(rows[0]) + 1


def ring(img, cx, cy, radius, width, colour, x0, y0, x1, y1):
    for y in range(y0, y1):
        for x in range(x0, x1):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if radius - width <= d <= radius:
                img[y][x] = colour


def seal(img, x0, y0, w, h, big):
    """The FCC seal in white on face x0..x0+w, y0..y0+h: a ring with a bolt in it over the letters."""
    font = BIG if big else SMALL
    cx, cy = x0 + w / 2, y0 + (h * 0.32)
    radius = w * 0.4
    ring(img, cx, cy, radius, 1.4 if big else 1.0, WHITE, x0, y0, x0 + w, y0 + h)
    if big:
        for dy, row in enumerate(BOLT):
            for dx, c in enumerate(row):
                if c == "#":
                    img[int(cy) - 3 + dy][int(cx) - 2 + dx] = WHITE
    else:
        img[int(cy)][int(cx)] = WHITE
        img[int(cy) - 1][int(cx)] = WHITE
    text_w = 3 * len(font["F"][0]) + 2
    ty = int(y0 + h * 0.62)
    glyphs(img, x0 + (w - text_w) // 2, ty, "FCC", font, WHITE)
    if big:
        fill(img, x0 + 2, ty + len(font["F"]) + 2, x0 + w - 2, ty + len(font["F"]) + 3, WHITE)


def banner_texture(big):
    img = canvas(64, 64)
    if big:  # The flag: front face x 1..20, y 1..40; the back face (x 22..41) is its mirror image, as cloth.
        x0, y0, w, h = 1, 1, 20, 40
    else:  # The shield's front face: x 1..12, y 1..22.
        x0, y0, w, h = 1, 1, 12, 22
    seal(img, x0, y0, w, h, big)
    if big:
        for y in range(y0, y0 + h):
            for x in range(w):
                img[y][22 + x] = img[y][x0 + w - 1 - x]
    return img


def armour_layer():
    """64x32 humanoid armour layer 1: the head (0,0) and hat (32,0) boxes, the body (16,16) and arm (40,16) boxes."""
    img = canvas(64, 32)
    # Head box (8x8x8): top (8,0), sides y 8..15 at x 0, 8, 16, 24. The crown covers the top half of the head.
    fill(img, 8, 0, 16, 8, FELT)  # Crown top.
    for x0 in (0, 8, 16, 24):
        fill(img, x0, 8, x0 + 8, 11, FELT)
        fill(img, x0, 11, x0 + 8, 12, BAND)  # The hat band.
    fill(img, 9, 1, 15, 2, FELT_DARK)  # The pinch in the crown.
    # Hat overlay (a size bigger): only its band row shows, as the brim.
    for x0 in (32, 40, 48, 56):
        fill(img, x0, 12, x0 + 8, 13, FELT_DARK)
    # Body box (8x12x4): top (20,16), right (16,20), front (20,20), left (28,20), back (32,20).
    fill(img, 20, 16, 36, 20, JACKET)
    fill(img, 16, 20, 40, 32, JACKET)
    fill(img, 16, 31, 40, 32, JACKET_DARK)  # Hem.
    # The front: a V of shirt, a wide 1970s tie and the lapels.
    for y in range(20, 26):
        half = max(0, 3 - (y - 20) // 2)
        fill(img, 24 - half, y, 24 + half, y + 1, SHIRT)
    fill(img, 23, 21, 25, 30, TIE)
    for y in range(20, 26):
        img[y][23 - max(0, 3 - (y - 20) // 2)] = JACKET_DARK
        img[y][24 + max(0, 3 - (y - 20) // 2)] = JACKET_DARK
    img[23][21] = GOLD  # A lapel pin.
    fill(img, 32, 20, 40, 21, JACKET_DARK)  # Collar at the back.
    # Arm box (4x12x4): top (44,16), sides y 20..31 at x 40..55. Sleeves with shirt cuffs.
    fill(img, 44, 16, 52, 20, JACKET)
    fill(img, 40, 20, 56, 31, JACKET)
    fill(img, 40, 31, 56, 32, SHIRT)
    return img


def item_hat():
    img = canvas(16, 16)
    fill(img, 4, 4, 12, 9, FELT)
    fill(img, 5, 3, 11, 4, FELT)
    fill(img, 7, 4, 9, 5, FELT_DARK)  # The pinch.
    fill(img, 4, 8, 12, 9, BAND)
    fill(img, 1, 9, 15, 11, FELT_DARK)  # Brim.
    fill(img, 2, 9, 14, 10, FELT)
    return img


def item_jacket():
    img = canvas(16, 16)
    fill(img, 1, 2, 15, 5, JACKET)  # Shoulders.
    fill(img, 1, 5, 4, 11, JACKET)  # Sleeves.
    fill(img, 12, 5, 15, 11, JACKET)
    fill(img, 1, 10, 4, 11, SHIRT)
    fill(img, 12, 10, 15, 11, SHIRT)
    fill(img, 4, 2, 12, 15, JACKET)
    fill(img, 4, 14, 12, 15, JACKET_DARK)
    for y in range(2, 7):
        half = max(0, 3 - (y - 2) // 2)
        fill(img, 8 - half, y, 8 + half, y + 1, SHIRT)
    fill(img, 7, 3, 9, 12, TIE)
    img[5][5] = GOLD
    return img


def item_pattern():
    img = canvas(16, 16)
    fill(img, 3, 1, 13, 15, PAPER)
    for x in range(3, 13):
        img[1][x] = img[14][x] = PAPER_EDGE
    for y in range(1, 15):
        img[y][3] = img[y][12] = PAPER_EDGE
    ring(img, 8, 5.5, 3.2, 1.0, ENAMEL, 3, 1, 13, 15)
    img[5][8] = ENAMEL
    glyphs(img, 4, 9, "F", SMALL, ENAMEL)
    glyphs(img, 8, 9, "C", SMALL, ENAMEL)
    return img


def item_badge():
    """A gold shield badge with a blue enamel seal, on a chain loop."""
    img = canvas(16, 16)
    img[1][7] = img[1][8] = GOLD_DARK
    img[2][6] = img[2][9] = GOLD_DARK
    for y in range(3, 15):
        if y < 10:
            x0, x1 = 3, 13
        else:
            x0, x1 = 3 + (y - 9), 13 - (y - 9)
        fill(img, x0, y, x1, y + 1, GOLD)
        img[y][x0] = img[y][x1 - 1] = GOLD_DARK
    fill(img, 3, 3, 13, 4, GOLD_DARK)
    ring(img, 8, 8, 3.0, 3.0, ENAMEL, 3, 3, 13, 14)
    img[7][8] = img[8][7] = WHITE
    return img


def item_model(name):
    path = os.path.join(ASSETS, "models", "item", name + ".json")
    with open(path, "w", newline="\n") as f:
        f.write(json.dumps({"parent": "item/generated", "textures": {"layer0": "radiocraft:item/" + name}}, indent=2) + "\n")


if __name__ == "__main__":
    tex = os.path.join(ASSETS, "textures")
    png(os.path.join(tex, "item", "fcc_hat.png"), item_hat())
    png(os.path.join(tex, "item", "fcc_jacket.png"), item_jacket())
    png(os.path.join(tex, "item", "fcc_banner_pattern.png"), item_pattern())
    png(os.path.join(tex, "item", "fcc_badge.png"), item_badge())
    png(os.path.join(tex, "models", "armor", "fcc_layer_1.png"), armour_layer())
    png(os.path.join(tex, "entity", "banner", "fcc.png"), banner_texture(True))
    png(os.path.join(tex, "entity", "shield", "fcc.png"), banner_texture(False))
    for item in ("fcc_hat", "fcc_jacket", "fcc_banner_pattern", "fcc_badge"):
        item_model(item)
    print("FCC textures and item models written")
