"""Screen background for the VHF base station, the 2m FM rig (2026-11-09), in the all-band radio's style.

Laid out like the block's front (models/block/vhf_base_station.json, textures/block/vhf_base_station.png), seen from
the player:
  top, the long faceplate strip, near-black plum: the plum LCD at its left, and a green and a red LED at its right
      -> here: the display (left), the S / PO / SWR meter, then the BUSY (green) and TX (red) lamps
  either side of the strip, a small light grey knob   -> POWER and its lamp on the left, AF VOL on the right
  bottom left, a knob with a light bar handle          -> the repeater OFFSET switch (- / S / +)
  next to it, the big tuning knob                      -> the main dial, with its 10x coarse key at the lower right
  two light grey dots beside the knob                  -> the STEP down / up keys
  three small dark knobs along the bottom right        -> SQL, MIC gain, and the mic socket (PTT)
CTCSS tone: TONE down / up keys under the display, as on the old screen's soft keys.
Colours are the block texture's: charcoal 0x20-0x52, light grey 0x97-0xBF, the plum LCD 0x2E1130.

The drawing pieces (knobs, dial, keys, meter, lamps, the preview) are the all-band script's, imported.
Writes:
  src/main/resources/assets/radiocraft/textures/gui/vhf_base_station_proposed.png   the viewing copy, every part drawn
  src/main/resources/assets/radiocraft/textures/gui/hf_panels/vhf_base_station.png  the runtime panel (no moving parts)
  src/main/resources/assets/radiocraft/gui_layouts/hf_vhf_base_station.json         the runtime layout (RUNTIME_SPEC.md)
  tools/hf_gui_proposals/vhf_base_station_proposed_layout.json                       controls and labels, for checking
Needs Pillow. Run: python tools/hf_gui_proposals/vhf_base_station_proposed.py [preview.png] [--preview DIR]
"""
import contextlib
import json
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hf_radio_all_band_proposed as ab  # noqa: E402  (its pieces; importing it draws nothing)
from hf_radio_all_band_proposed import cb, g  # noqa: E402

ROOT = ab.ROOT
OUT_PNG = os.path.join(ROOT, "textures", "gui", "vhf_base_station_proposed.png")
OUT_JSON = os.path.join(HERE, "vhf_base_station_proposed_layout.json")
RUNTIME_PNG = os.path.join(ROOT, "textures", "gui", "hf_panels", "vhf_base_station.png")
RUNTIME_JSON = os.path.join(ROOT, "gui_layouts", "hf_vhf_base_station.json")

W, H = 256, 128

# ---- Palette, from textures/block/vhf_base_station.png ----------------------------------------------------------
PALETTE = {
    "EDGE": g(0x20), "SHADE": g(0x26), "DEEP": g(0x2C), "CASE": g(0x33), "CASE_HI": g(0x38), "CASE_HI2": g(0x3E),
    "BEVEL_MID": g(0x3E), "BEVEL_HI": g(0x47), "KNOB": g(0x49), "KNOB_HI": g(0x52), "MID": g(0x6F),
    "RING": g(0xBF), "RING_DARK": g(0x97), "LABEL": g(0xBF),
    "LCD_GREY": (0x34, 0x22, 0x3A, 255),   # the display's bezel: the strip's plum, a shade lighter
    "SCREEN": (0x26, 0x0C, 0x2A, 255),     # the plum LCD, dark
}
STRIP = (0x19, 0x11, 0x1F, 255)
STRIP_EDGE = (0x22, 0x17, 0x29, 255)
LIGHT_KEY = (g(0xAB), g(0x97), g(0xBF))       # the light grey buttons: face, low edge, high edge
LIGHT_KNOB = {"KNOB": g(0x97), "KNOB_HI": g(0xAB), "MID": g(0xBF), "RING": g(0x26)}
ab.LAMP_COLOURS.update({"busy_lamp": "#3BFF6A", "tx_lamp": "#FF3020", "on_lamp": "#FFB020"})
INK = "#FF8098"  # the display's lettering, pink on plum


@contextlib.contextmanager
def palette(**colours):
    """Draw with some of the all-band module's colours swapped, then put them back."""
    old = {k: getattr(ab, k) for k in colours}
    for k, v in colours.items():
        setattr(ab, k, v)
    try:
        yield
    finally:
        for k, v in old.items():
            setattr(ab, k, v)


def case(img):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], fill=ab.EDGE)
    d.rectangle([1, 1, W - 2, H - 2], fill=ab.CASE)
    d.line([1, 1, W - 2, 1], fill=ab.BEVEL_HI)
    d.line([1, 2, W - 3, 2], fill=ab.BEVEL_MID)
    d.line([1, 1, 1, H - 2], fill=ab.BEVEL_HI)
    d.line([2, 2, 2, H - 3], fill=ab.BEVEL_MID)
    d.line([1, H - 2, W - 2, H - 2], fill=ab.SHADE)
    d.line([2, H - 3, W - 2, H - 3], fill=ab.DEEP)
    d.line([W - 2, 1, W - 2, H - 2], fill=ab.SHADE)
    d.line([W - 3, 2, W - 3, H - 3], fill=ab.DEEP)
    for y in range(6, H - 6, 4):  # The block's darker 0x2C streaks.
        off = (y * 11) % 29
        for x0 in range(6 + off, W - 14, 37):
            d.line([x0, y, x0 + 10, y], fill=ab.DEEP)
    d.line([4, 62, W - 5, 62], fill=ab.SHADE)  # Where the block's upper and lower halves meet.
    d.line([4, 63, W - 5, 63], fill=ab.CASE_HI2)


def strip(img, x0, y0, x1, y1):
    """The faceplate strip across the top: near-black plum, standing a little proud of the case."""
    d = ImageDraw.Draw(img)
    d.rectangle([x0 - 1, y0 - 1, x1 + 2, y1 + 2], fill=ab.SHADE)
    d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], fill=ab.EDGE)
    d.rectangle([x0, y0, x1, y1], fill=STRIP_EDGE)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=STRIP)
    d.line([x0, y0, x1, y0], fill=(0x2E, 0x22, 0x36, 255))


def mic_jack(img, cx, cy, r):
    """The mic socket the PTT lives on: a chrome ring, four pins (the old screen's socket)."""
    d = ImageDraw.Draw(img)
    cb.disc(d, cx + 1, cy + 1, r, ab.EDGE)
    cb.disc(d, cx, cy, r, ab.EDGE)
    cb.disc(d, cx, cy, r - 1, g(0x97))
    cb.disc(d, cx - 0.5, cy - 0.5, r - 2, g(0xBF))
    cb.disc(d, cx, cy, r - 4, g(0x6F))
    cb.disc(d, cx, cy, r - 5, g(0x1A))
    for dx, dy in ((-3, -3), (2, -3), (-3, 2), (2, 2)):
        d.rectangle([cx + dx, cy + dy, cx + dx + 1, cy + dy + 1], fill=(208, 176, 96, 255))
    d.rectangle([cx - 1, cy - 6, cx, cy - 5], fill=g(0x6F))  # The key notch.
    ab.add(ab.controls, "ptt", "button", cx - r, cy - r, 2 * r + 1, 2 * r + 1)


def light_key(img, name, x, y, w, h, lit=False):
    face, lo, hi = LIGHT_KEY
    ab.key(img, name, x, y, w, h, face=face, face_lo=lo, face_hi=hi, lit=lit)


def build(runtime=False):
    ab.RUNTIME = runtime
    ab.W, ab.H = W, H
    ab.controls.clear()
    ab.labels.clear()
    ab.extras.clear()
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    with palette(**PALETTE):
        case(img)
        label, label_c = ab.label, ab.label_c

        # Left of the strip: POWER (the block's light knob there) and the ON lamp.
        light_key(img, "power", 8, 10, 18, 10)
        label_c(img, "power_label", 17, 24, "PWR")
        ab.lamp(img, "on_lamp", 14, 34)
        label_c(img, "on_lamp_label", 17, 44, "ON")

        # The faceplate strip: display, meter, the green and red lamps.
        strip(img, 34, 5, 224, 59)
        ab.display(img, 40, 12, 92, 28)
        ab.extras["display"]["ink"] = INK
        label(img, "tone_label", 40, 50, "TONE")
        light_key(img, "tone_down", 60, 49, 13, 7)
        light_key(img, "tone_up", 77, 49, 13, 7)
        ab.arrow(img, 66, 52, False, ab.EDGE)
        ab.arrow(img, 83, 52, True, ab.EDGE)

        ab.meter(img, 140, 9, 64, 36, r=20)
        label(img, "meter_po_label", 141, 50, "PO")
        light_key(img, "meter_po", 150, 49, 13, 7, lit=True)
        label(img, "meter_swr_label", 168, 50, "SWR")
        light_key(img, "meter_swr", 182, 49, 13, 7, lit=True)

        ab.lamp(img, "busy_lamp", 210, 12)
        label_c(img, "busy_lamp_label", 213, 21, "BUSY")
        ab.lamp(img, "tx_lamp", 210, 32)
        label_c(img, "tx_lamp_label", 213, 41, "TX")

        # Right of the strip: AF VOL, the block's other light knob.
        label_c(img, "vol_label", 239, 18, "VOL")
        with palette(**LIGHT_KNOB):
            ab.knob(img, "af_gain", 239, 34, 9, rest=0.3)

        # Bottom left: the repeater offset on the bar-handle knob, resting on S (simplex).
        ab.knob(img, "offset", 24, 96, 10, bar=True, rest=0.5, detents=(("-", -90), ("S", 0), ("+", 90)))
        label_c(img, "offset_s", 24, 78, "S")
        label(img, "offset_minus", 24 - 13 - cb.text_width("-"), 94, "-")
        label(img, "offset_plus", 24 + 13, 94, "+")
        label_c(img, "offset_label", 24, 111, "OFFSET")

        # The main dial, its 10x key in the corner at its lower right, and the STEP keys beside it.
        ab.dial(img, 80, 95, 23)
        label_c(img, "step_label", 127, 69, "STEP")
        light_key(img, "step_down", 112, 77, 14, 8)
        light_key(img, "step_up", 129, 77, 14, 8)
        ab.arrow(img, 118, 81, False, ab.EDGE)
        ab.arrow(img, 135, 81, True, ab.EDGE)
        label_c(img, "coarse_label", 119, 104, "10X")
        light_key(img, "coarse", 112, 111, 15, 7, lit=True)
        label_c(img, "quick_label", 141, 104, "QUICK")  # Lit on the quick-recall frequency.
        light_key(img, "quick", 134, 111, 15, 7, lit=True)

        # Bottom right: the block's three small knobs, the last one the mic socket.
        label_c(img, "sql_label", 163, 82, "SQL")
        ab.knob(img, "squelch", 163, 98, 9, rest=0.0)
        label_c(img, "mic_label", 193, 82, "MIC")
        ab.knob(img, "mic_gain", 193, 98, 9, rest=0.5)
        label_c(img, "ptt_label", 231, 79, "PTT")
        mic_jack(img, 231, 98, 11)
        label_c(img, "brand", 193, 116, "2M FM TRANSCEIVER")
    return img


def runtime_layout():
    out = []
    for c in ab.controls:
        entry = dict(c)
        entry.update(ab.extras.get(c["name"], {}))
        out.append(entry)
    return {"texture": "radiocraft:textures/gui/hf_panels/vhf_base_station.png", "width": W, "height": H,
            "controls": out}


PREVIEW_STATES = [
    {"af_gain": 0.0, "mic_gain": 0.0, "squelch": 0.0, "offset": "S", "tuning": 225, "meter": 0.0},
    {"af_gain": 0.5, "mic_gain": 0.5, "squelch": 0.3, "offset": "-", "tuning": 0, "meter_po": True, "coarse": True,
     "on_lamp": True, "busy_lamp": True, "display": ("FM", "", "146.940"), "meter": 0.62},
    {"af_gain": 1.0, "mic_gain": 1.0, "squelch": 1.0, "offset": "+", "tuning": 90, "meter_swr": True,
     "on_lamp": True, "tx_lamp": True, "display": ("T 100.0", "TX", "147.120 +"), "quick": True, "meter": 1.0},
]


def main():
    args = sys.argv[1:]
    preview_dir = None
    if "--preview" in args:
        i = args.index("--preview")
        preview_dir = args[i + 1]
        del args[i:i + 2]
    img = build()
    ab.check()
    img.save(OUT_PNG)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump({"texture": "radiocraft:textures/gui/vhf_base_station_proposed.png", "width": W, "height": H,
                   "controls": ab.controls, "labels": ab.labels}, f, indent=2)
        f.write("\n")
    if args:
        img.resize((W * 3, H * 3), Image.NEAREST).save(args[0])
    print("ok: %d controls, %d labels" % (len(ab.controls), len(ab.labels)))

    panel = build(runtime=True)
    ab.check()
    layout = runtime_layout()
    os.makedirs(os.path.dirname(RUNTIME_PNG), exist_ok=True)
    os.makedirs(os.path.dirname(RUNTIME_JSON), exist_ok=True)
    panel.save(RUNTIME_PNG)
    with open(RUNTIME_JSON, "w", encoding="utf-8") as f:
        json.dump(layout, f, indent=2)
        f.write("\n")
    print("runtime panel and layout written")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        for k, state in enumerate(PREVIEW_STATES):
            ab.preview(panel, layout, state).resize((W * 3, H * 3), Image.NEAREST).save(
                os.path.join(preview_dir, "vhf_base_station_runtime_%d.png" % k))


if __name__ == "__main__":
    main()
