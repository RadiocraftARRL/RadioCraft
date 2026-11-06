"""Placeholder item textures for the QSL card and the QSL album (computer batch 3C).

Writes textures/item/qsl_card.png and qsl_album.png (16x16) with only the standard library. They are placeholders:
the proper art is on doc/ModelDesignBounty.md. Run: python tools/qsl_assets.py
"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft", "textures", "item")
CLEAR = (0, 0, 0, 0)


def png(path, rows):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def card():
    """A cream postcard, navy border, a red callsign bar, a small blue picture and two grey lines."""
    paper, edge, red, pic, line = (246, 240, 224, 255), (40, 52, 110, 255), (190, 40, 40, 255), (90, 140, 210, 255), (130, 130, 130, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y < 3 or y > 12:
                row.append(CLEAR)
            elif y in (3, 12) or x in (0, 15):
                row.append(edge)
            elif y == 5 and 3 <= x <= 12:
                row.append(red)
            elif 7 <= y <= 10 and 2 <= x <= 5:
                row.append(pic)
            elif y in (8, 10) and 7 <= x <= 13:
                row.append(line)
            else:
                row.append(paper)
        rows.append(row)
    return rows


def album():
    """A brown leather album with a card peeking out of the top and a gold label."""
    leather, dark, gold, paper = (110, 66, 36, 255), (70, 40, 20, 255), (220, 180, 60, 255), (246, 240, 224, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y < 2 or y > 14 or x < 2 or x > 13:
                row.append(CLEAR)
            elif y < 4:
                row.append(paper if 5 <= x <= 11 else CLEAR)
            elif x in (2, 13) or y in (4, 14):
                row.append(dark)
            elif x == 3:
                row.append(dark)
            elif 7 <= y <= 9 and 6 <= x <= 11:
                row.append(gold)
            else:
                row.append(leather)
        rows.append(row)
    return rows


if __name__ == "__main__":
    png(os.path.join(OUT, "qsl_card.png"), card())
    png(os.path.join(OUT, "qsl_album.png"), album())
    print("wrote qsl_card.png and qsl_album.png")
