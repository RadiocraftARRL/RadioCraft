"""Proposed GUI background for the 10m HF radio (2026-11-09), drawn after its block model: a mid-grey case with a
lighter lid, a row of white push keys at the top left with small black knobs under them, the big tuning knob on a
dark inset in the middle, a dark purple display at the right with the green lamp above it, and the pale grey key
at the bottom right (here the PTT).

Writes textures/gui/hf_radio_10m_proposed.png and hf_radio_10m_proposed_layout.json (every control's rectangle).
Knobs are drawn at rest; the screen would draw the readout, the meter needle and pressed or lit states in code.
Also writes the game's copy (RUNTIME_SPEC.md): textures/gui/hf_panels/hf_radio_10m.png, the panel with the knob
pointers, the dial's dimple and the AGC slider left out, and gui_layouts/hf_hf_radio_10m.json.
Run: python tools/hf_gui_proposals/hf_radio_10m_proposed.py
"""
from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
from hf_10m_20m_kit import rgb

W, H = 256, 150

# The block texture's colours (textures/block/hf_radio_10m.png).
OUTLINE = rgb(26, 26, 26)
LID, LID_LIGHT = rgb(87, 87, 87), rgb(98, 98, 98)
FACE, FACE_LIGHT, FACE_DARK = rgb(57, 57, 57), rgb(66, 66, 66), rgb(47, 47, 47)
INSET, INSET_DARK = rgb(41, 41, 41), rgb(35, 35, 35)
KEY_WHITE, KEY_GREY = rgb(197, 197, 197), rgb(172, 172, 172)
GLASS = rgb(38, 22, 54)
GREEN_OFF = rgb(23, 80, 21)
METER_FACE = rgb(240, 222, 150)  # The texture's lamp yellow, toned down to a lit meter face.
INK, RED = rgb(48, 40, 24), rgb(176, 36, 28)
LABEL, LABEL_DIM = rgb(225, 225, 225), rgb(172, 172, 172)


def slide3(img, lay, x, y, w, at, labels, name, moving=True):
    """A three-way slide switch: slot, white slider at position `at`, a label under each stop."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + 7], fill=OUTLINE)
    d.rectangle([x + 1, y + 1, x + w - 2, y + 6], fill=INSET_DARK)
    d.line([x + 3, y + 4, x + w - 4, y + 4], fill=rgb(20, 20, 20))
    stops = [x + 5 + i * (w - 10) // 2 for i in range(3)]
    if moving:
        kit.key(img, stops[at] - 5, y - 1, 10, 10, KEY_WHITE)
        for i in range(1, 4):  # Grip lines on the slider.
            d.line([stops[at] - 5 + 2 * i + 1, y + 2, stops[at] - 5 + 2 * i + 1, y + 6], fill=KEY_GREY)
    rt = {"positions": labels, "handles": [[sx - 4, y, 8, 8] for sx in stops],
          "handle_colour": kit.hexc(KEY_WHITE), "handle_shadow": kit.hexc(rgb(120, 120, 120))}
    lay.control(name, "switch3", x, y - 1, w, 10, rt=rt, rt_type="switch", positions=labels, rest=at, stops_x=stops)
    for s, sx in zip(labels, stops):
        lay.label(img, s, sx, y + 12, LABEL_DIM, centred=True)


def draw(moving):
    """The panel. moving=True is the viewing copy (_proposed.png); False leaves out what the game draws."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lay = kit.Layout("hf_radio_10m_proposed", W, H)

    # ---- The case: lid band, face, foot ----
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, 6], fill=LID)
    d.line([1, 1, W - 2, 1], fill=LID_LIGHT)
    for x in range(6, W - 6, 16):  # Vent slots in the lid.
        d.line([x, 4, x + 9, 4], fill=FACE_DARK)
    d.line([1, 7, W - 2, 7], fill=INSET)
    d.rectangle([1, 8, W - 2, H - 5], fill=FACE)
    d.line([1, 8, W - 2, 8], fill=FACE_LIGHT)
    d.line([1, 8, 1, H - 5], fill=FACE_LIGHT)
    d.line([W - 2, 8, W - 2, H - 5], fill=FACE_DARK)
    d.rectangle([1, H - 4, W - 2, H - 2], fill=INSET_DARK)
    for fx in (12, W - 28):  # Feet.
        d.rectangle([fx, H - 3, fx + 15, H - 2], fill=rgb(20, 20, 20))

    # ---- Left: the white keys, the meter, its selector keys, the small knobs ----
    for s, cx in (("PWR", 13), ("SSB", 33), ("CW", 49), ("AM", 65)):
        lay.label(img, s, cx, 12, LABEL, centred=True)
        kit.key(img, cx - 7, 19, 14, 9, KEY_WHITE)
        rt = {} if s == "PWR" else {"lit": kit.key_lit(cx - 7, 19, 14, 9)}
        lay.control({"PWR": "power", "SSB": "mode_ssb", "CW": "mode_cw", "AM": "mode_am"}[s], "button",
                    cx - 7, 19, 14, 9, rt=rt)
    d.line([25, 30, 73, 30], fill=FACE_LIGHT)  # A bracket under the mode keys.
    lay.label(img, "MODE", 77, 21, LABEL_DIM)

    needle = kit.meter(img, 9, 37, 82, 38, METER_FACE, INK, RED, rgb(30, 30, 30), FACE_LIGHT)
    lay.control("meter", "meter", 9, 37, 82, 38, scales=["S", "PO", "SWR"], **needle)  # needle["rt"]: game fields.

    lay.label(img, "METER", 8, 93, LABEL_DIM)
    for s, cx in (("PO", 39), ("SWR", 57)):  # Receive always reads S; the keys pick what transmit shows.
        lay.label(img, s, cx, 84, LABEL, centred=True)
        kit.key(img, cx - 6, 91, 12, 8, KEY_GREY)
        lay.control("meter_" + s.lower(), "button", cx - 6, 91, 12, 8, rt={"lit": kit.key_lit(cx - 6, 91, 12, 8)})

    for s, name, cx, frac in (("AF", "af_gain", 20, 0.5), ("MIC", "mic_gain", 50, 0.5), ("SQL", "squelch", 80, 0.0)):
        lay.label(img, s, cx, 103, LABEL, centred=True)
        kit.knob_ticks(img, cx, 124, 13, LABEL_DIM)
        rt = kit.small_knob(img, cx, 124, 20, frac, rgb(36, 36, 36), moving)
        lay.control(name, "knob", cx - 14, 110, 28, 28, rt=rt, size=20, centre=[cx, 124], rest=frac)

    # ---- Middle: the dark inset with the tuning knob, the step keys and the AGC switch ----
    kit.recess(d, [99, 11, 163, 143], INSET, INSET_DARK, FACE_LIGHT)
    lay.label(img, "TUNING", 131, 15, LABEL, centred=True)
    kit.dial_ring(img, 131, 54, 28, LABEL_DIM)
    rt = kit.big_dial(img, 131, 54, 50, 0.5, rgb(30, 30, 30), rgb(22, 22, 22), rgb(76, 76, 76),
                      rgb(52, 52, 52), rgb(76, 76, 76), rgb(225, 225, 225), moving)
    lay.control("tuning", "dial", 106, 29, 50, 50, rt=rt, size=50, centre=[131, 54])  # The knob is the click area.
    lay.label(img, "10X", 101, 13, LABEL)  # The coarse-tuning key, latching, in the inset's corner by the dial.
    kit.key(img, 101, 20, 14, 7, KEY_GREY)
    lay.control("coarse", "button", 101, 20, 14, 7, rt={"lit": kit.key_lit(101, 20, 14, 7)})
    for s, name, kx, kw in (("DN", "step_down", 102, 14), ("QUICK", "quick", 122, 19), ("UP", "step_up", 147, 14)):
        lay.label(img, s, kx + kw // 2, 88, LABEL, centred=True)
        kit.key(img, kx, 95, kw, 9, KEY_GREY)
        # QUICK lights while on the quick-recall frequency; the step keys only press.
        lay.control(name, "button", kx, 95, kw, 9, rt={"lit": kit.key_lit(kx, 95, kw, 9)} if name == "quick" else {})
    lay.label(img, "AGC", 131, 111, LABEL, centred=True)
    slide3(img, lay, 108, 119, 46, 1, ["OFF", "SLOW", "FAST"], "agc", moving)

    # ---- Right: busy lamp, the purple readout, speaker, PTT ----
    lay.label(img, "BUSY", 222, 12, LABEL_DIM)
    kit.led(img, 240, 12, GREEN_OFF)
    lay.control("busy_lamp", "lamp", 239, 11, 6, 6, colour="green",
                rt={"x": 240, "y": 12, "w": 4, "h": 4, "colour": "#40FF50"})
    kit.display_window(img, 170, 23, 80, 26, GLASS, rgb(30, 30, 30), FACE_LIGHT)
    lay.control("display", "display", 170, 23, 80, 26, rt={"ink": "#D8B8FF"})
    kit.recess(d, [168, 58, 251, 106], INSET_DARK, rgb(26, 26, 26), FACE_LIGHT)
    kit.grille(img, [169, 59, 250, 105], INSET_DARK, rgb(16, 16, 16))
    lay.label(img, "RC-10", 170, 119, LABEL)
    lay.label(img, "SSB CW AM", 170, 127, LABEL_DIM)
    lay.label(img, "PTT", 231, 112, LABEL, centred=True)
    kit.key(img, 214, 119, 36, 20, KEY_GREY)
    d.rectangle([218, 123, 245, 134], outline=rgb(150, 150, 150))  # The PTT's dished centre.
    lay.control("ptt", "button", 214, 119, 36, 20, rt={})
    return img, lay


def main():
    img, lay = draw(True)
    kit.write(img, lay, "hf_radio_10m_proposed")
    img, lay = draw(False)
    kit.write_runtime(img, lay, "hf_radio_10m")


if __name__ == "__main__":
    main()
