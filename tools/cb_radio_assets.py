"""Placeholder art for the CB radios (5875 base/mobile, PC68 compact mobile, TRC-217 portable).

Writes, with only the standard library:
  textures/block/cb_mobile_5875_front.png   front panel of the 5875 (clock, meters, channel window, ON AIR lamp, grille, knobs)
  textures/block/cb_mobile_5875_case.png    brushed silver sides and back of the 5875
  textures/block/cb_mobile_5875_top.png     brown woodgrain top cover of the 5875
  models/block/cb_mobile_5875.json          with a DISPLAY element (3.5..5.0 high, 5.5..8.0 x, z 2.5..2.75) and 3D knobs
  textures/block/cb_mobile_pc68_front.png   chrome front panel of the PC68 (meter, mic jack, 4 toggles, 3 knobs, channel window, LED, channel knob)
  textures/block/cb_mobile_pc68_case.png    black metal case of the PC68
  models/block/cb_mobile_pc68.json          with a DISPLAY element (2.0..3.0 high, 3.25..5.25 x, z 3.5..3.75) and channel knob
  textures/item/cb_portable.png             16x16 inventory icon of the TRC-217 portable (front view, antenna retracted)
  models/item/cb_portable_icon.json         item/generated model for the inventory icon
  textures/item/cb_portable_body.png        16x16 texture sheet for the 3D portable model:
                                              FRONT: x 0..5, y 0..15 (6 wide, 16 tall: silver top 2 rows, channel window,
                                                     grille, volume + squelch knob dots, mic grille at bottom)
                                              SIDE:  x 6..9, y 0..15 (4 wide, 16 tall: black with a silver strip and the
                                                     dark push-to-talk bar)
                                              BACK:  x 10..15, y 0..15 (6 wide, 16 tall: black, plain)
The proper models and textures are on doc/ModelDesignBounty.md. Run: python tools/cb_radio_assets.py
"""
import json
import os
import struct
import zlib

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "radiocraft")
CLEAR = (0, 0, 0, 0)
DISPLAY_DARK_RED = (42, 6, 6, 255)  # #2A0606


def png(path, pixels):
    """Writes a 16x16 RGBA PNG from rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def grid(fn):
    return [[fn(x, y) for x in range(16)] for y in range(16)]


def cb_5875_case(x, y):
    if x in (0, 15) or y in (0, 15):
        return (164, 168, 174, 255)
    if y == 1:
        return (218, 222, 228, 255)
    v = 204 if y % 2 == 0 else 196
    return (v, v + 4, v + 10, 255)


def cb_5875_top(x, y):
    if y == 0:
        return (204, 208, 214, 255)  # Front brushed silver trim strip.
    if x in (0, 15) or y == 15:
        return (76, 48, 30, 255)
    bands = (
        (106, 68, 44, 255),
        (94, 60, 38, 255),
        (116, 76, 48, 255),
        (88, 56, 36, 255),
    )
    return bands[(y + (1 if x > 8 and y % 3 == 0 else 0)) % len(bands)]


def cb_5875_front(x, y):
    bezel = (196, 200, 206, 255)
    bezel_edge = (156, 160, 166, 255)
    glass = (20, 22, 26, 255)
    clock_red = (232, 42, 42, 255)
    clock_dim = (150, 26, 26, 255)
    meter_bg = (230, 234, 228, 255)
    meter_green = (84, 188, 104, 255)
    meter_red = (214, 64, 56, 255)
    meter_needle = (60, 64, 70, 255)
    on_air_unlit = (120, 22, 22, 255)
    grille_frame = (26, 28, 32, 255)
    grille_slot = (12, 13, 16, 255)
    grille_bar = (46, 50, 56, 255)
    panel = (208, 212, 218, 255)
    knob_dark = (42, 45, 50, 255)
    knob_ring = (150, 154, 160, 255)
    toggle_tip = (238, 242, 246, 255)

    if x in (0, 15) or y in (0, 15):
        return bezel_edge if (x == 15 or y == 15) else bezel
    if y == 6:
        return bezel

    # Top black display strip (y 1..5).
    if 1 <= y <= 5:
        if 8 <= x <= 10:
            return DISPLAY_DARK_RED  # Channel LED window (covered by DISPLAY).
        if x == 11 and y in (2, 3):
            return on_air_unlit  # ON AIR lamp dot.
        if 1 <= x <= 4 and 2 <= y <= 4:
            if (x, y) in ((1, 2), (1, 3), (1, 4), (2, 2), (2, 4), (4, 2), (4, 3), (4, 4)):
                return clock_red
            if (x, y) == (3, 3):
                return clock_dim
            return glass
        if (5 <= x <= 7 or 12 <= x <= 14) and 2 <= y <= 4:
            if y == 2:
                return meter_red if x in (7, 14) else meter_green
            if y == 4 and x in (6, 13):
                return meter_needle
            return meter_bg
        return glass

    # Bottom-left black speaker grille (x 1..6, y 7..14).
    if 1 <= x <= 6:
        if x in (1, 6) or y in (7, 14):
            return grille_frame
        return grille_slot if y % 2 == 0 else grille_bar

    # Bottom-right silver control panel (x 7..14, y 7..14).
    if y in (8, 9):
        if x in (8, 11, 12, 14):
            return knob_dark
        if x in (9, 10, 13):
            return toggle_tip if y == 9 else knob_dark
    if y == 11 and x in (8, 10, 12, 14):
        return knob_ring
    if y in (12, 13):
        if x in (8, 10, 12):
            return knob_dark
        if x == 14:
            return knob_dark if y == 12 else knob_ring
    return panel


def cb_pc68_case(x, y):
    if x in (0, 15) or y in (0, 15):
        return (22, 24, 28, 255)
    v = 36 if y % 2 == 0 else 32
    return (v, v + 2, v + 6, 255)


def cb_pc68_front(x, y):
    chrome_edge = (140, 145, 152, 255)
    chrome_shine = (232, 236, 242, 255)
    chrome = (212, 216, 222, 255)
    chrome_alt = (204, 208, 214, 255)
    meter_frame = (46, 52, 50, 255)
    meter_lit = (68, 212, 162, 255)
    meter_red = (210, 62, 58, 255)
    dark = (32, 34, 38, 255)
    ring = (152, 157, 164, 255)
    pin = (200, 204, 210, 255)
    rx_tx_led = (28, 130, 52, 255)
    fm_led = (120, 26, 26, 255)

    if x in (0, 15) or y in (0, 15):
        return chrome_edge
    if y == 1:
        return chrome_shine

    # Small greenish-lit meter at top-left (x 1..3, y 2..5).
    if 1 <= x <= 3 and 2 <= y <= 5:
        if y in (2, 5):
            return meter_frame
        return meter_red if x == 3 else meter_lit

    # Mic jack circle under the meter (x 1..3, y 8..12).
    if 1 <= x <= 3 and 8 <= y <= 12:
        if (x in (1, 3) and y in (8, 12)):
            return chrome
        if x in (1, 3) or y in (8, 12):
            return ring
        return pin if (x, y) == (2, 10) else dark

    # Row of 4 tiny toggles in the middle (x = 4, 6, 8, 10, y 3..5).
    if x in (4, 6, 8, 10) and 3 <= y <= 5:
        return chrome_shine if y == 4 else dark

    # 3 knob dots under the toggles (x = 5, 7, 9, y 10..12).
    if x in (5, 7, 9) and 10 <= y <= 12:
        return ring if y == 12 else dark

    # Green RX/TX dot LED (and red FM dot under it) at x = 11.
    if x == 11 and y in (3, 4):
        return rx_tx_led
    if x == 11 and y == 6:
        return fm_led

    # Red 2-digit channel window at top-right (x 12..14, y 2..6).
    if 12 <= x <= 14 and 2 <= y <= 6:
        return DISPLAY_DARK_RED

    # Bigger channel knob at bottom-right (x 12..14, y 8..13).
    if 12 <= x <= 14 and 8 <= y <= 13:
        if (x in (12, 14) and y in (8, 13)):
            return chrome
        if x in (12, 14) or y in (8, 13):
            return ring
        return chrome_shine if (x, y) == (13, 10) else dark

    return chrome if y % 2 == 0 else chrome_alt


def cb_portable_icon(x, y):
    black_edge = (22, 24, 28, 255)
    body = (38, 41, 46, 255)
    grille_slot = (16, 18, 21, 255)
    grille_bar = (52, 56, 62, 255)
    silver_hi = (220, 224, 230, 255)
    silver = (192, 197, 204, 255)
    silver_lo = (162, 167, 175, 255)
    red = (220, 44, 44, 255)
    chan_bg = (208, 212, 216, 255)

    # Short chrome antenna stub on top-right (x 9..10, y 0..2).
    if x in (9, 10) and 0 <= y <= 2:
        if y == 0:
            return silver_hi
        if y == 1:
            return black_edge if x == 9 else silver
        return black_edge

    # Top channel knob (x 6..7, y 2).
    if x in (6, 7) and y == 2:
        return silver if x == 6 else silver_hi

    # Side PTT bar on upper left (x 3, y 5..8).
    if x == 3 and 5 <= y <= 8:
        return black_edge

    if not (4 <= x <= 11 and 3 <= y <= 15):
        return CLEAR

    # Silver top band (y 3..4).
    if y in (3, 4):
        if x in (4, 11):
            return silver_lo
        return silver_hi if y == 3 else silver

    if x in (4, 11) or y == 15:
        return black_edge

    # Channel window and red light button row (y 5).
    if y == 5:
        if x == 7:
            return chan_bg
        if x in (9, 10):
            return red
        return body

    # Speaker grille (y 6..9).
    if 6 <= y <= 9:
        return grille_slot if y % 2 == 0 else grille_bar

    # Two silver knobs low on the front (y 11..12).
    if y in (11, 12):
        if x in (5, 6, 9, 10):
            return silver_hi if y == 11 else silver
        return body

    # Bottom microphone grille line (y 14).
    if y == 14:
        return grille_slot

    return body


def cb_portable_body(x, y):
    black_edge = (22, 24, 28, 255)
    body = (36, 39, 44, 255)
    grille_slot = (16, 18, 20, 255)
    grille_bar = (50, 54, 60, 255)
    silver_hi = (218, 222, 228, 255)
    silver = (192, 196, 204, 255)
    silver_lo = (168, 172, 180, 255)
    red = (220, 44, 44, 255)
    chan_bg = (210, 214, 218, 255)
    ptt_dark = (16, 18, 20, 255)
    ptt_mid = (30, 32, 36, 255)

    # FRONT: x 0..5, y 0..15 (6 wide, 16 tall).
    if 0 <= x <= 5:
        if y in (0, 1):
            if x in (0, 5):
                return silver_lo
            return silver_hi if y == 0 else silver
        if x in (0, 5) or y == 15:
            return black_edge
        if y in (2, 3):
            if x == 2:
                return chan_bg if y == 2 else silver_lo
            if x == 4 and y == 2:
                return red
            return body
        if 4 <= y <= 9:
            return grille_slot if y % 2 == 0 else grille_bar
        if y in (11, 12) and x in (1, 4):
            return silver_hi if y == 11 else silver
        if y == 14:
            return grille_slot
        return body

    # SIDE: x 6..9, y 0..15 (4 wide, 16 tall).
    if 6 <= x <= 9:
        if y in (0, 1):
            return silver if y == 0 else silver_lo
        if x in (6, 9) or y == 15:
            return black_edge
        if 3 <= y <= 7:
            return ptt_dark if y in (3, 7) else ptt_mid
        return silver if x == 7 else silver_lo

    # BACK: x 10..15, y 0..15 (6 wide, 16 tall, plain black).
    if x in (10, 15) or y in (0, 15):
        return black_edge
    return body


def all_faces(texture, uv=(0, 0, 16, 16)):
    return {face: {"uv": list(uv), "texture": texture} for face in ("north", "east", "south", "west", "up", "down")}


def cb_5875_model():
    """Faces north as drawn (front at low z)."""
    body_faces = all_faces("#case")
    body_faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#top"}
    front_faces = all_faces("#case")
    front_faces["north"] = {"uv": [0, 0, 16, 16], "texture": "#front"}
    return {
        "credit": "Placeholder from tools/cb_radio_assets.py (see doc/ModelDesignBounty.md)",
        "parent": "block/block",
        "textures": {
            "front": "radiocraft:block/cb_mobile_5875_front",
            "case": "radiocraft:block/cb_mobile_5875_case",
            "top": "radiocraft:block/cb_mobile_5875_top",
            "particle": "radiocraft:block/cb_mobile_5875_case",
        },
        "elements": [
            {"name": "body", "from": [1, 0, 3], "to": [15, 5.5, 13], "faces": body_faces},
            {"name": "front", "from": [1.5, 0.25, 2.75], "to": [14.5, 5.25, 3], "faces": front_faces},
            {"name": "DISPLAY", "from": [5.5, 3.5, 2.5], "to": [8.0, 5.0, 2.75],
             "faces": all_faces("#front", (8, 1, 11, 6))},
            {"name": "channel_knob", "from": [4.0, 2.0, 2.25], "to": [5.0, 2.75, 2.75],
             "faces": all_faces("#front", (11, 8, 13, 10))},
            {"name": "function_knob", "from": [7.25, 2.0, 2.35], "to": [8.0, 2.75, 2.75],
             "faces": all_faces("#front", (11, 8, 13, 10))},
            {"name": "clarifier_knob", "from": [2.25, 2.0, 2.35], "to": [3.0, 2.75, 2.75],
             "faces": all_faces("#front", (11, 8, 13, 10))},
            {"name": "knob_1", "from": [7.25, 0.75, 2.35], "to": [8.0, 1.5, 2.75],
             "faces": all_faces("#front", (11, 8, 13, 10))},
            {"name": "knob_2", "from": [5.5, 0.75, 2.35], "to": [6.25, 1.5, 2.75],
             "faces": all_faces("#front", (11, 8, 13, 10))},
            {"name": "knob_3", "from": [3.75, 0.75, 2.35], "to": [4.5, 1.5, 2.75],
             "faces": all_faces("#front", (11, 8, 13, 10))},
        ],
    }


def cb_pc68_model():
    """Faces north as drawn (front at low z)."""
    front_faces = all_faces("#front", (0, 0, 1, 1))
    front_faces["north"] = {"uv": [0, 0, 16, 16], "texture": "#front"}
    return {
        "credit": "Placeholder from tools/cb_radio_assets.py (see doc/ModelDesignBounty.md)",
        "parent": "block/block",
        "textures": {
            "front": "radiocraft:block/cb_mobile_pc68_front",
            "case": "radiocraft:block/cb_mobile_pc68_case",
            "particle": "radiocraft:block/cb_mobile_pc68_case",
        },
        "elements": [
            {"name": "body", "from": [2.5, 0, 4], "to": [13.5, 3.5, 13], "faces": all_faces("#case")},
            {"name": "front", "from": [2.5, 0, 3.75], "to": [13.5, 3.5, 4], "faces": front_faces},
            {"name": "DISPLAY", "from": [3.25, 2.0, 3.5], "to": [5.25, 3.0, 3.75],
             "faces": all_faces("#front", (12, 2, 15, 7))},
            {"name": "channel_knob", "from": [3.25, 0.5, 3.25], "to": [4.75, 1.75, 3.75],
             "faces": all_faces("#front", (12, 8, 15, 14))},
        ],
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    png(os.path.join(ASSETS, "textures", "block", "cb_mobile_5875_front.png"), grid(cb_5875_front))
    png(os.path.join(ASSETS, "textures", "block", "cb_mobile_5875_case.png"), grid(cb_5875_case))
    png(os.path.join(ASSETS, "textures", "block", "cb_mobile_5875_top.png"), grid(cb_5875_top))
    write_json(os.path.join(ASSETS, "models", "block", "cb_mobile_5875.json"), cb_5875_model())

    png(os.path.join(ASSETS, "textures", "block", "cb_mobile_pc68_front.png"), grid(cb_pc68_front))
    png(os.path.join(ASSETS, "textures", "block", "cb_mobile_pc68_case.png"), grid(cb_pc68_case))
    write_json(os.path.join(ASSETS, "models", "block", "cb_mobile_pc68.json"), cb_pc68_model())

    png(os.path.join(ASSETS, "textures", "item", "cb_portable.png"), grid(cb_portable_icon))
    write_json(os.path.join(ASSETS, "models", "item", "cb_portable_icon.json"),
               {"parent": "item/generated", "textures": {"layer0": "radiocraft:item/cb_portable"}})
    png(os.path.join(ASSETS, "textures", "item", "cb_portable_body.png"), grid(cb_portable_body))


if __name__ == "__main__":
    main()
