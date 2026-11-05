"""Placeholder item textures for the camera and photo paper (1.12 step 4).

Writes textures/item/camera.png, photo_paper.png and color_photo_paper.png (16x16) with only the standard library.
They are placeholders: the proper art is on doc/ModelDesignBounty.md. Run: python tools/camera_assets.py
"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft", "textures", "item")
CLEAR = (0, 0, 0, 0)


def png(path, pixels):
    """Writes a 16x16 RGBA PNG from rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def camera():
    """A 1950s press camera from the front: black leather body, chrome top plate, a big lens, a red shutter button."""
    body, edge, chrome, lens, glass, glint, red = (40, 38, 36, 255), (20, 19, 18, 255), (190, 192, 196, 255), \
        (70, 72, 78, 255), (30, 50, 90, 255), (170, 200, 240, 255), (200, 40, 40, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 9.0
            r2 = dx * dx + dy * dy
            if y < 3 or y > 14 or x < 1 or x > 14:
                row.append(CLEAR)
            elif y == 3 and (x in (3, 4) or 10 <= x <= 12):
                row.append(red if x in (3, 4) else chrome)  # Shutter button and viewfinder.
            elif y == 3:
                row.append(CLEAR)
            elif y in (4, 5):
                row.append(chrome)
            elif r2 <= 4.0:
                row.append(glint if (x, y) == (6, 8) else glass)
            elif r2 <= 11.0:
                row.append(lens)
            elif x in (1, 14) or y == 14:
                row.append(edge)
            else:
                row.append(body)
        rows.append(row)
    return rows


def paper(colours):
    """A small stack of glossy sheets with a picture corner showing; {colours} tints the picture."""
    sheet, edge, shadow = (246, 246, 242, 255), (176, 176, 170, 255), (130, 130, 124, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if x < 2 or x > 14 or y < 2 or y > 14:
                row.append(CLEAR)
            elif y == 14 or x == 14:
                row.append(shadow)  # The sheets below.
            elif x in (2, 13) or y in (2, 13):
                row.append(edge)
            elif 4 <= x <= 11 and 4 <= y <= 11:
                band = min(len(colours) - 1, (y - 4) * len(colours) // 8)
                row.append(colours[band] + (255,))
            else:
                row.append(sheet)
        rows.append(row)
    return rows


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    png(os.path.join(OUT, "camera.png"), camera())
    png(os.path.join(OUT, "photo_paper.png"), paper([(60, 60, 60), (110, 110, 110), (160, 160, 160), (210, 210, 210)]))
    png(os.path.join(OUT, "color_photo_paper.png"), paper([(200, 50, 50), (60, 160, 70), (60, 90, 200), (220, 190, 60)]))
    print("Wrote camera.png, photo_paper.png and color_photo_paper.png to", os.path.abspath(OUT))
