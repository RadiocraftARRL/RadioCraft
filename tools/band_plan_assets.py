"""Placeholder item textures for the Band Plan sheet and the MARS mod (1.12 step 1).

Writes textures/item/band_plan.png and textures/item/mars_mod.png (16x16) with only the standard library, so it runs
anywhere. They are placeholders: the proper art is on doc/ModelDesignBounty.md. Run: python tools/band_plan_assets.py
"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft", "textures", "item")


def png(path, pixels):
    """Writes a 16x16 RGBA PNG from rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


CLEAR = (0, 0, 0, 0)


def band_plan():
    """A sheet of paper with four coloured band bars and a header line."""
    paper, edge, ink = (244, 239, 223, 255), (185, 174, 144, 255), (43, 33, 24, 255)
    bars = [[(59, 111, 216), (142, 68, 201), (46, 158, 79), (224, 138, 30)],
            [(59, 111, 216), (46, 158, 79), (46, 158, 79), (224, 138, 30)],
            [(30, 154, 168), (184, 85, 155), (216, 185, 42), (139, 195, 74)],
            [(119, 119, 74), (210, 60, 60), (109, 139, 166), (42, 85, 160)]]
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if x < 2 or x > 13 or y < 1 or y > 14:
                row.append(CLEAR)
            elif x in (2, 13) or y in (1, 14):
                row.append(edge)
            elif y == 3 and 4 <= x <= 11:
                row.append(ink)
            elif y in (5, 7, 9, 11):
                bar = bars[(y - 5) // 2]
                row.append(bar[min(3, (x - 3) // 3)] + (255,) if 3 <= x <= 12 else paper)
            else:
                row.append(paper)
        rows.append(row)
    return rows


def mars_mod():
    """A small green circuit board with a red diode, copper pads and two jumper wires."""
    board, dark, copper, red, wire = (46, 110, 58, 255), (30, 74, 38, 255), (196, 120, 60, 255), (200, 40, 40, 255), (220, 210, 190, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if 3 <= x <= 12 and 5 <= y <= 12:
                if x in (3, 12) or y in (5, 12):
                    row.append(dark)
                elif y == 8 and 6 <= x <= 9:
                    row.append(red)
                elif (x, y) in ((5, 8), (10, 8), (5, 10), (10, 10)):
                    row.append(copper)
                else:
                    row.append(board)
            elif x in (5, 10) and 1 <= y <= 4:
                row.append(wire)
            else:
                row.append(CLEAR)
        rows.append(row)
    return rows


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    png(os.path.join(OUT, "band_plan.png"), band_plan())
    png(os.path.join(OUT, "mars_mod.png"), mars_mod())
    print("wrote band_plan.png and mars_mod.png")
