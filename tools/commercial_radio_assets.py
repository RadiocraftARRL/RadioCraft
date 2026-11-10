"""Art for the commercial mobiles (1.12 step 2; redrawn 2026-11-09): the commercial VHF mobile and the low band
mobile, a dash-mount box with a dark control head: a knob on the left, a green LCD in the middle with a row of keys
under it, TX and RX lamps on the right, and a stripe in the radio's colour (yellow on the VHF, orange on the low
band). The low band set is 2 px taller, the extra on top a speaker grille across the head; everything under it sits
where the VHF's does, so the display and lamps line up the same on both.

Writes, under src/main/resources/assets/radiocraft/:
  textures/block/commercial_mobile_case.png      16x16 light grey, tinted by dye (tintindex 0)
  textures/block/commercial_mobile_front.png     64x64 (4 px a model unit): the VHF head (44 x 12) and part tiles
  textures/block/low_band_mobile_front.png       64x64: the low band head (44 x 20) and part tiles
  models/block/commercial_mobile.json, low_band_mobile.json (the DISPLAY element RadioDisplays reads; the TX/RX lamps
      are RadioDisplays.commercialLamps, x 3-4, y 2.25-3 and 1-1.75, which this art keeps them under)
  textures/gui/commercial/commercial_mobile.png, low_band_mobile.png   the screens' panels, without the moving parts
  gui_layouts/commercial_mobile.json, low_band_mobile.json             where each control is (HfPanel's format)

Before 2026-11-09 the front was one 16x16 picture stretched over the 11 x 3 head, and the knob and mic hook cubes
took the whole of it on every face (a tiny squashed copy of the front on each). Each part now has its own tile.

The screens' parts sit where the block has them (2026-11-09), in the block's undyed case colour (the dye is not shown).
The handhelds' 3D item models are tools/commercial_handheld_models.py. The proper models are on
doc/ModelDesignBounty.md. Needs Pillow. Run: python tools/commercial_radio_assets.py [--preview DIR]
(--preview also writes block renders' inputs and screen composites with sample display text, to check for overlaps.)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import marine_radio_assets as mra  # noqa: E402  The 3 x 5 lettering, drawing helpers and the preview font.
from marine_radio_assets import rect, bevel, disc, text, text_centred, text_width, write_json, hexc  # noqa: E402

ROOT = mra.ROOT
# The * and # keys' legends, 5 x 5 (3 px wide they read as an x and an H).
WIDE_GLYPHS = {"*": ["00100", "10101", "01110", "10101", "00100"], "#": ["01010", "11111", "01010", "11111", "01010"]}

# ---- Colours (the block's and the screen's) -------------------------------------------------------------------------
PANEL, PANEL_LIGHT, PANEL_DARK = (40, 42, 46, 255), (64, 66, 72, 255), (18, 18, 20, 255)
LCD, LCD_FRAME = (120, 150, 95, 255), (16, 16, 18, 255)
LABEL = (200, 200, 196, 255)
KNOB_GREY, KNOB_LIGHT, KNOB_DARK = (90, 90, 90, 255), (128, 128, 130, 255), (54, 54, 56, 255)
KEY, KEY_LIGHT, KEY_DARK = (150, 152, 156, 255), (192, 194, 198, 255), (92, 94, 98, 255)
FKEY, FKEY_LIGHT, FKEY_DARK = (70, 72, 76, 255), (104, 106, 110, 255), (36, 36, 40, 255)
INK_DARK, INK_LIGHT = (24, 24, 24, 255), (228, 228, 228, 255)
LAMP_TX_OFF, LAMP_RX_OFF = (90, 16, 12, 255), (16, 70, 24, 255)
HOOK = (34, 34, 36, 255)
CASE = (43, 45, 48, 255)  # CommercialMobileBlockEntity.DEFAULT_CASE_COLOUR

RADIOS = {
    # name: (accent, lettering on the badge, taller by (model units), distress soft key)
    "commercial_mobile": ((220, 200, 60, 255), "VHF", 0, True),
    "low_band_mobile": ((220, 120, 40, 255), "LOW BAND", 2, False),
}


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


# ======================================================================================================================
# The block (64x64 front texture, 4 px a model unit)
# ======================================================================================================================
# The head: model x 2.5-13.5, y 0.5 to 3.5 (+2 on the low band), at z 2.75; 44 px wide. Texture x runs from the
# viewer's left (east, model x 13.5): px = (13.5 - x) * 4; y from the head's top: px = (top - y) * 4.
HEAD_X = (2.5, 13.5)
HEAD_Z = (2.75, 3.0)
KNOB_FACE = (48, 0, 52, 4)
KNOB_SIDE = (52, 0, 56, 4)
HOOK_TILE = (56, 0, 60, 4)
PANEL_TILE = (60, 0, 64, 4)
FRAME_TILE = (48, 4, 52, 8)
GLASS_PX = (8, 1, 32, 9)          # the DISPLAY element's face (below any grille rows)
KNOB_PX = (1, 5, 5, 9)            # the knob cube's place
TX_PX, RX_PX = (38, 2, 42, 5), (38, 7, 42, 10)   # RadioDisplays.commercialLamps


def block_front(accent, grille_units):
    oy = grille_units * 4
    h = 12 + oy
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    rect(d, 0, 0, 44, h, PANEL)
    rect(d, 0, 0, 44, 1, PANEL_LIGHT)
    rect(d, 0, h - 1, 44, h, PANEL_DARK)
    if oy:  # The low band's speaker: slots across the top of the head.
        rect(d, 2, 1, 42, oy - 1, PANEL_DARK)
        for y in range(2, oy - 1, 2):
            rect(d, 3, y, 41, y + 1, (8, 8, 8, 255))
        rect(d, 2, oy - 1, 42, oy, PANEL_LIGHT)
    # The LCD in its frame (the renderer prints the channel on the glass).
    gx0, gy0, gx1, gy1 = GLASS_PX
    rect(d, gx0 - 1, gy0 - 1 + oy, gx1 + 1, gy1 + 1 + oy, LCD_FRAME)
    rect(d, gx0, gy0 + oy, gx1, gy1 + oy, LCD)
    rect(d, gx0, gy0 + oy, gx1, gy0 + 1 + oy, shade(LCD, 1.12))
    # The key row under it: numbers grey, the zone keys in the radio's colour.
    for i in range(8):
        x = 9 + i * 3
        rect(d, x, 10 + oy, x + 2, 11 + oy, accent if i >= 6 else KEY)
    # The knob's well (the knob is a cube over it), and the stripe under it.
    kx0, ky0, kx1, ky1 = KNOB_PX
    rect(d, kx0 - 1, ky0 - 1 + oy, kx1 + 1, ky1 + 1 + oy, PANEL_DARK)
    rect(d, 1, 1 + oy, 6, 3 + oy, accent)
    # The TX and RX lamps, dark (RadioDisplays lights them), in black bezels.
    for (x0, y0, x1, y1), off in ((TX_PX, LAMP_TX_OFF), (RX_PX, LAMP_RX_OFF)):
        rect(d, x0 - 1, y0 - 1 + oy, x1 + 1, y1 + 1 + oy, PANEL_DARK)
        rect(d, x0, y0 + oy, x1, y1 + oy, off)
    # Part tiles: the knob's face (a pointer at twelve) and its knurled sides, the mic hook, the head's edges, the frame.
    rect(d, *KNOB_FACE, KNOB_GREY)
    bevel(d, *KNOB_FACE, KNOB_LIGHT, KNOB_DARK)
    rect(d, KNOB_FACE[0] + 1, KNOB_FACE[1], KNOB_FACE[0] + 3, KNOB_FACE[1] + 2, (230, 230, 230, 255))
    rect(d, *KNOB_SIDE, KNOB_DARK)
    for x in range(KNOB_SIDE[0], KNOB_SIDE[2], 2):
        rect(d, x, KNOB_SIDE[1], x + 1, KNOB_SIDE[3], KNOB_GREY)
    rect(d, *HOOK_TILE, HOOK)
    rect(d, HOOK_TILE[0], HOOK_TILE[1], HOOK_TILE[2], HOOK_TILE[1] + 1, (70, 70, 74, 255))
    rect(d, *PANEL_TILE, PANEL)
    rect(d, *FRAME_TILE, LCD_FRAME)
    return sheet


def block_case():
    img = Image.new("RGBA", (16, 16), (215, 215, 215, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(175, 175, 175, 255))
    return img


def uvpx(box):
    return [box[0] / 4.0, box[1] / 4.0, box[2] / 4.0, box[3] / 4.0]


def faces(texture, uv, tint=False, only=None):
    out = {}
    for face in only or ("north", "east", "south", "west", "up", "down"):
        f = {"uv": uv, "texture": texture}
        if tint:
            f["tintindex"] = 0
        out[face] = f
    return out


def block_model(name, grille_units):
    """Faces north as drawn (the blockstate turns it). The body is tinted; the head, display, knob and hook are not."""
    oy = grille_units * 4
    top = 4 + grille_units
    head_top = top - 0.5

    def glass(box):
        return [box[0], box[1] + oy, box[2], box[3] + oy]

    head = faces("#front", uvpx(PANEL_TILE), only=("east", "west", "up", "down"))
    head["north"] = {"uv": uvpx((0, 0, 44, 12 + oy)), "texture": "#front"}
    display = faces("#front", uvpx(FRAME_TILE), only=("east", "west", "up", "down"))
    display["north"] = {"uv": uvpx(glass(GLASS_PX)), "texture": "#front"}
    knob = faces("#front", uvpx(KNOB_SIDE), only=("east", "west", "up", "down", "south"))
    knob["north"] = {"uv": uvpx(KNOB_FACE), "texture": "#front"}
    return {
        "credit": "Placeholder from tools/commercial_radio_assets.py (see doc/ModelDesignBounty.md)",
        "parent": "block/block",
        "texture_size": [64, 64],
        "textures": {"case": "radiocraft:block/commercial_mobile_case", "front": "radiocraft:block/" + name + "_front",
                     "particle": "radiocraft:block/commercial_mobile_case"},
        "elements": [
            {"name": "body", "from": [2, 0, 3], "to": [14, top, 15], "faces": faces("#case", [0, 0, 16, 16], tint=True)},
            {"name": "front", "from": [HEAD_X[0], 0.5, HEAD_Z[0]], "to": [HEAD_X[1], head_top, HEAD_Z[1]], "faces": head},
            {"name": "DISPLAY", "from": [5.5, 1.25, 2.6], "to": [11.5, 3.25, 2.75], "faces": display},
            {"name": "knob", "from": [12.25, 1.25, 2.25], "to": [13.25, 2.25, 2.75], "faces": knob},
            {"name": "mic_hook", "from": [13.5, 0.5, 4], "to": [14.5, 3.5, 6], "faces": faces("#front", uvpx(HOOK_TILE))},
        ],
    }


# ======================================================================================================================
# The screens: the same head, larger, with the upgraded handheld's controls (CommercialMobileScreen)
# ======================================================================================================================
G_W, G_H = 256, 100
G_HOOK = (0, 34, 5, 60)          # the mic hook seen from the front, on the case's left side
G_CASE = (4, 0, 256, 100)
G_GRILLE_H = 20                  # the low band's extra height: its speaker across the top


def gui_geometry(grille):
    """Every control's place, for a panel with (low band) or without its top grille."""
    oy = G_GRILLE_H if grille else 0
    g = {"oy": oy, "h": G_H + oy}
    g["case"] = (G_CASE[0], G_CASE[1], G_CASE[2], G_CASE[3] + oy)
    g["plate"] = (10, 8, 250, 92 + oy)
    g["grille"] = (16, 12, 244, 12 + G_GRILLE_H - 4) if grille else None
    # As the block's head (2026-11-09, the maintainer: parts where the block has them): the stripe in the radio's
    # colour top left with the MIC jack beside it, the knobs bottom left where the block's knob is, the LCD large in the
    # middle with the keys under it, the TX and RX lamps stacked right of it and DISMISS under them. No speaker on
    # the front (the block has none; the low band's is the grille across the top).
    g["badge"] = (16, 14 + oy, 56, 25 + oy)
    g["jack"] = (70, 20 + oy, 5)
    g["jack_label_y"] = 28 + oy
    g["knobs"] = [("volume", "PWR/VOL", 30, 56 + oy), ("channel", "CH", 66, 56 + oy)]
    g["knob_r"] = 12
    g["knob_label_y"] = 72 + oy
    g["maker_y"] = 82 + oy
    g["glass"] = (92, 14 + oy, 200, 56 + oy)
    g["keys"] = []
    rows = [[("key_1", "1"), ("key_2", "2"), ("key_3", "3"), ("key_4", "4"), ("key_5", "5"), ("key_6", "6"), ("key_7", "7"), ("key_8", "8")],
            [("key_9", "9"), ("key_0", "0"), ("key_star", "*"), ("key_hash", "#"), ("key_a", "A"), ("key_b", "B"), ("key_c", "C"), ("key_d", "D")]]
    for r, row in enumerate(rows):
        for c, (name, legend) in enumerate(row):
            kind = "zone" if legend in "ABCD" else "fn" if legend in "*#" else "num"
            g["keys"].append((name, legend, kind, 95 + c * 13, (63, 77)[r] + oy, 12, 10))
    g["lamps"] = [("tx_lamp", "TX", 214, 18 + oy, (255, 59, 48)), ("rx_lamp", "RX", 214, 32 + oy, (59, 255, 106))]
    g["dismiss"] = (206, 50 + oy, 38, 12)
    return g


def draw_key(img, d, x, y, w, h, kind, legend, accent):
    if kind == "num":
        face, light, dark, ink = KEY, KEY_LIGHT, KEY_DARK, INK_DARK
    elif kind == "fn":
        face, light, dark, ink = FKEY, FKEY_LIGHT, FKEY_DARK, INK_LIGHT
    elif kind == "zone":
        face, light, dark, ink = accent, shade(accent, 1.2), shade(accent, 0.6), INK_DARK
    else:  # The distress soft key.
        face, light, dark, ink = (150, 34, 30, 255), (200, 90, 80, 255), (80, 14, 12, 255), INK_LIGHT
    rect(d, x - 1, y - 1, x + w + 1, y + h + 1, PANEL_DARK)
    rect(d, x, y, x + w, y + h, face)
    bevel(d, x, y, x + w, y + h, light, dark)
    if legend in WIDE_GLYPHS:
        gx, gy = x + (w - 5) // 2, y + (h - 5) // 2
        for row, bits in enumerate(WIDE_GLYPHS[legend]):
            for col, bit in enumerate(bits):
                if bit == "1":
                    img.putpixel((gx + col, gy + row), ink)
    else:
        text_centred(img, x + w / 2.0, y + (h - 5) // 2, legend, ink)


def wrap(lines, fits, most=3):
    """As CommercialMobileScreen.wrapLines: while there is a spare line (of `most`), a line too wide is broken at its last space
    that fits; what is still too wide is shrunk when drawn."""
    out = list(lines)
    i = 0
    while i < len(out) and len(out) < most:
        line = out[i]
        if not fits(line) and " " in line:
            cut = -1
            for j, ch in enumerate(line):
                if ch == " " and fits(line[:j]):
                    cut = j
            if cut < 0:
                cut = line.index(" ")
            out[i:i + 1] = [line[:cut], line[cut + 1:]]
            continue
        i += 1
    return out[:most]


def gui_panel(name):
    accent, badge, grille_units, dismiss = RADIOS[name]
    g = gui_geometry(grille_units > 0)
    img = Image.new("RGBA", (G_W, g["h"]), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # The case in the block's undyed colour (not tinted with the dye: 2026-11-09, the maintainer), a light lip and a
    # dark foot.
    cx0, cy0, cx1, cy1 = g["case"]
    d.rounded_rectangle([cx0, cy0, cx1 - 1, cy1 - 1], radius=3, fill=CASE)
    shadeimg = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadeimg)
    sd.rounded_rectangle([cx0, cy0, cx1 - 1, cy1 - 1], radius=3, outline=(0, 0, 0, 150))
    rect(sd, cx0 + 2, cy0 + 1, cx1 - 2, cy0 + 3, (255, 255, 255, 60))
    rect(sd, cx0 + 1, cy0 + 2, cx0 + 2, cy1 - 2, (255, 255, 255, 40))
    rect(sd, cx0 + 2, cy1 - 4, cx1 - 2, cy1 - 1, (0, 0, 0, 80))
    rect(sd, cx1 - 2, cy0 + 2, cx1 - 1, cy1 - 2, (0, 0, 0, 60))
    img.alpha_composite(shadeimg)
    # The mic hook on the left side.
    hx0, hy0, hx1, hy1 = G_HOOK
    rect(d, hx0, hy0 + g["oy"] // 2, hx1, hy1 + g["oy"] // 2, HOOK)
    rect(d, hx0, hy0 + g["oy"] // 2, hx1, hy0 + 1 + g["oy"] // 2, (70, 70, 74, 255))
    # The head.
    x0, y0, x1, y1 = g["plate"]
    rect(d, x0 - 1, y0 - 1, x1 + 1, y1 + 1, PANEL_DARK)
    rect(d, x0, y0, x1, y1, PANEL)
    bevel(d, x0, y0, x1, y1, PANEL_LIGHT, PANEL_DARK)
    if g["grille"]:
        gx0, gy0, gx1, gy1 = g["grille"]
        rect(d, gx0 - 1, gy0 - 1, gx1 + 1, gy1 + 1, PANEL_DARK)
        rect(d, gx0, gy0, gx1, gy1, (30, 30, 32, 255))
        for y in range(gy0 + 2, gy1 - 1, 3):
            rect(d, gx0 + 2, y, gx1 - 2, y + 1, (6, 6, 6, 255))
            rect(d, gx0 + 2, y + 1, gx1 - 2, y + 2, (52, 52, 56, 255))
    # The knobs (the screen draws their pointers), labelled under them.
    for kname, label, kx, ky in g["knobs"]:
        disc(d, kx, ky, g["knob_r"] + 2, PANEL_DARK)
        mra.draw_panel_knob(d, kx, ky, g["knob_r"])
        text_centred(img, kx, g["knob_label_y"], label, LABEL)
    jx, jy, jr = g["jack"]
    disc(d, jx, jy, jr + 1, (150, 150, 154, 255))
    disc(d, jx, jy, jr, (96, 96, 100, 255))
    disc(d, jx, jy, jr - 2, (6, 6, 6, 255))
    text_centred(img, jx, g["jack_label_y"], "MIC", LABEL)
    # The badge in the radio's colour, the maker's lettering under it.
    bx0, by0, bx1, by1 = g["badge"]
    rect(d, bx0, by0, bx1, by1, accent)
    bevel(d, bx0, by0, bx1, by1, shade(accent, 1.2), shade(accent, 0.6))
    text_centred(img, (bx0 + bx1) / 2.0, by0 + (by1 - by0 - 5) // 2, badge, INK_DARK)
    text_centred(img, (bx0 + bx1) / 2.0, g["maker_y"], "RADIOCRAFT", LABEL)
    # The LCD (the screen prints on it; it goes dark when the radio is off).
    lx0, ly0, lx1, ly1 = g["glass"]
    rect(d, lx0 - 2, ly0 - 2, lx1 + 2, ly1 + 2, LCD_FRAME)
    rect(d, lx0, ly0, lx1, ly1, LCD)
    rect(d, lx0, ly0, lx1, ly0 + 1, shade(LCD, 1.12))
    for kname, legend, kind, kx, ky, kw, kh in g["keys"]:
        draw_key(img, d, kx, ky, kw, kh, kind, legend, accent)
    # The lamps, dark, labelled to their right.
    for lname, label, lx, ly, colour in g["lamps"]:
        rect(d, lx - 1, ly - 1, lx + 7, ly + 7, PANEL_DARK)
        rect(d, lx, ly, lx + 6, ly + 6, tuple(c // 4 for c in colour) + (255,))
        text(img, lx + 10, ly + 1, label, LABEL)
    # The distress soft key (VHF), or the band's lettering (low band).
    dx, dy, dw, dh = g["dismiss"]
    if dismiss:
        draw_key(img, d, dx, dy, dw, dh, "distress", "DISMISS", accent)
    else:
        text_centred(img, dx + dw / 2.0, dy + 1, "30-50 MHZ", LABEL)
        text_centred(img, dx + dw / 2.0, dy + 8, "FM", LABEL)
    return img


def gui_layout(name):
    accent, badge, grille_units, dismiss = RADIOS[name]
    g = gui_geometry(grille_units > 0)
    controls = []
    lx0, ly0, lx1, ly1 = g["glass"]
    controls.append({"name": "display", "type": "display", "x": lx0, "y": ly0, "w": lx1 - lx0, "h": ly1 - ly0,
                     "ink": "#1F2A14", "off": "#283020"})
    r = g["knob_r"]
    for kname, label, kx, ky in g["knobs"]:
        controls.append({"name": kname, "type": "knob", "x": kx - r, "y": ky - r, "w": 2 * r, "h": 2 * r, "cx": kx - 0.5, "cy": ky - 0.5,
                         "pointer_from": 2, "pointer_r": r - 2, "pointer_width": 1, "pointer_colour": "#F0F0F0", "min_deg": -135, "max_deg": 135})
    jx, jy, jr = g["jack"]
    controls.append({"name": "ptt", "type": "jack", "x": jx - jr - 1, "y": jy - jr - 1, "w": 2 * jr + 2, "h": 2 * jr + 2, "glow": "#FF3020"})
    for lname, label, lx, ly, colour in g["lamps"]:
        controls.append({"name": lname, "type": "lamp", "x": lx, "y": ly, "w": 6, "h": 6, "colour": hexc(colour)})
    for kname, legend, kind, kx, ky, kw, kh in g["keys"]:
        controls.append({"name": kname, "type": "button", "x": kx, "y": ky, "w": kw, "h": kh})
    if dismiss:
        dx, dy, dw, dh = g["dismiss"]
        controls.append({"name": "dismiss", "type": "button", "x": dx, "y": dy, "w": dw, "h": dh})
    return {"texture": "radiocraft:textures/gui/commercial/" + name + ".png", "width": G_W, "height": g["h"], "controls": controls}


# ======================================================================================================================
# Previews: the screen as CommercialMobileScreen draws it, with sample display text
# ======================================================================================================================
def preview_gui(name, state, path):
    layout = gui_layout(name)
    panel = gui_panel(name)
    img = Image.new("RGBA", panel.size, (198, 204, 212, 255))
    d = ImageDraw.Draw(img)
    img.alpha_composite(panel)
    d = ImageDraw.Draw(img)
    for c in layout["controls"]:
        t = c["type"]
        if t == "knob":
            v = state["knobs"][c["name"]]
            mra.ray(img, c["cx"], c["cy"], c["min_deg"] + v * (c["max_deg"] - c["min_deg"]), c["pointer_from"], c["pointer_r"], mra.col(c["pointer_colour"]))
        elif t == "lamp" and state.get("lamps", {}).get(c["name"]):
            col = mra.col(c["colour"])
            rect(d, c["x"], c["y"], c["x"] + c["w"], c["y"] + c["h"], col)
            rect(d, c["x"] + 1, c["y"] + 1, c["x"] + c["w"] - 1, c["y"] + c["h"] - 1, tuple((v + 255) // 2 for v in col[:3]) + (255,))
        elif t == "button" and c["name"] == state.get("pressed"):
            img.alpha_composite(Image.new("RGBA", (c["w"], c["h"]), (0, 0, 0, 85)), (c["x"], c["y"]))
        elif t == "jack" and state.get("ptt"):
            d.ellipse([c["x"] - 2, c["y"] - 2, c["x"] + c["w"] + 1, c["y"] + c["h"] + 1], outline=mra.col(c["glow"]))
        elif t == "display":
            if not state["on"]:
                img.alpha_composite(Image.new("RGBA", (c["w"], c["h"]), (40, 48, 32, 176)), (c["x"], c["y"]))
                continue
            ink = mra.col(c["ink"])
            # As CommercialMobileScreen.renderDisplay: three lines 12 apart (a distress alert up to four, 9 apart), each
            # shrunk to fit, the second split left and right.
            lines = state["lines"]
            step = 12
            if all(isinstance(line, str) for line in lines):
                lines = wrap(lines, lambda s: mra.mc_width(s) <= c["w"] - 8, 4)
                step = 9 if len(lines) > 3 else 12
            for i, line in enumerate(lines):
                if isinstance(line, tuple):
                    left, right = line
                    mra.mc_text(img, c["x"] + 4, c["y"] + 4 + i * step, left, ink)
                    mra.mc_text(img, c["x"] + c["w"] - 4 - mra.mc_width(right), c["y"] + 4 + i * step, right, ink)
                    continue
                scale = min(1.0, (c["w"] - 8) / max(1, mra.mc_width(line)))
                mra.mc_text(img, c["x"] + 4, c["y"] + 4 + i * step + (1.0 - scale) * 4.0, line, ink, scale)
    img = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
    img.save(path)


def trim(img):
    """The box round what is not the preview renderer's background colour."""
    bg = img.getpixel((0, 0))
    mask = Image.new("L", img.size, 0)
    px, mp = img.load(), mask.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y] != bg:
                mp[x, y] = 255
    return mask.getbbox()


def main():
    blocks = os.path.join(ROOT, "textures", "block")
    os.makedirs(blocks, exist_ok=True)
    block_case().save(os.path.join(blocks, "commercial_mobile_case.png"))
    for name, (accent, badge, grille_units, dismiss) in RADIOS.items():
        block_front(accent, grille_units).save(os.path.join(blocks, name + "_front.png"))
        write_json(os.path.join(ROOT, "models", "block", name + ".json"), block_model(name, grille_units))
        mra.save(gui_panel(name), "textures", "gui", "commercial", name + ".png")
        write_json(os.path.join(ROOT, "gui_layouts", name + ".json"), gui_layout(name))

    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        preview_gui("commercial_mobile", {"on": True, "lines": ["FIRE DISP 1", ("CH 3", "DIG"), "154.2800 MHZ"],
                                          "knobs": {"volume": 0.5, "channel": 2 / 15.0}, "lamps": {"rx_lamp": True}, "pressed": "key_hash"},
                    os.path.join(out, "commercial_mobile_gui_normal.png"))
        preview_gui("commercial_mobile", {"on": True, "lines": ["DISTRESS DETECTED 161.975", "X 120 Y 64 Z -340", "STEVE FIRE"],
                                          "knobs": {"volume": 0.8, "channel": 0.0}, "lamps": {"rx_lamp": True}, "pressed": "dismiss"},
                    os.path.join(out, "commercial_mobile_gui_distress.png"))
        preview_gui("commercial_mobile", {"on": False, "lines": [],
                                          "knobs": {"volume": 0.0, "channel": 1.0}}, os.path.join(out, "commercial_mobile_gui_off.png"))
        preview_gui("low_band_mobile", {"on": True, "lines": ["CH 5", ("LOW BAND", "TX"), "42.1000 MHZ"],
                                        "knobs": {"volume": 0.3, "channel": 4 / 15.0}, "lamps": {"tx_lamp": True}, "ptt": True},
                    os.path.join(out, "low_band_mobile_gui_tx.png"))
        preview_gui("low_band_mobile", {"on": True, "lines": ["ZONE A ONLY", ("LOW BAND", "FM"), "42.1000 MHZ"],
                                        "knobs": {"volume": 1.0, "channel": 1.0}, "pressed": "key_b"},
                    os.path.join(out, "low_band_mobile_gui_info.png"))
        for name in RADIOS:  # The block's front (render it to <name>_block_front.png first) over the panel, to compare.
            block = os.path.join(out, name + "_block_front.png")
            if os.path.exists(block):
                front = Image.open(block).convert("RGBA")
                front = front.crop(front.getbbox() if front.getchannel("A").getextrema()[0] < 255 else trim(front))
                panel = Image.open(os.path.join(out, name + ("_gui_normal.png" if name == "commercial_mobile" else "_gui_tx.png"))).convert("RGBA")
                front = front.resize((panel.width, int(front.height * panel.width / front.width)), Image.NEAREST)
                both = Image.new("RGBA", (panel.width, front.height + panel.height + 12), (198, 204, 212, 255))
                both.alpha_composite(front, (0, 0))
                both.alpha_composite(panel, (0, front.height + 12))
                both.save(os.path.join(out, name + "_compare.png"))
        print("Previews in", out)


if __name__ == "__main__":
    main()
