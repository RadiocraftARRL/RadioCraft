"""Proposed GUI background for the 20m HF radio (2026-11-09), drawn after its block model: a near-black case with an
upper deck carrying the pale grey toggles at the left, the tan analog meter, and the dark maroon display with the
red lamp; on the lower deck a row of small black knobs at the left and the big tuning knob right of centre, and a
small pale control at the bottom right (here the PTT).

Writes textures/gui/hf_radio_20m_proposed.png and hf_radio_20m_proposed_layout.json (every control's rectangle).
Knobs and switches are drawn at rest; the screen would draw the readout, the meter needle and pressed or lit states.
Also writes the game's copy (RUNTIME_SPEC.md): textures/gui/hf_panels/hf_radio_20m.png, the panel with the knob
pointers, the dial's dimple and the toggle bats left out, and gui_layouts/hf_hf_radio_20m.json.
Run: python tools/hf_gui_proposals/hf_radio_20m_proposed.py
"""
from PIL import Image, ImageDraw

import hf_10m_20m_kit as kit
from hf_10m_20m_kit import rgb

W, H = 256, 150

# The block texture's colours (textures/block/hf_radio_20m.png).
OUTLINE = rgb(22, 22, 22)
CASE, CASE_LIGHT, CASE_HIGH = rgb(51, 51, 51), rgb(64, 64, 64), rgb(87, 87, 87)
DECK, DECK_DARK, DEEP = rgb(45, 45, 45), rgb(36, 36, 36), rgb(28, 28, 28)
PALE, PALE_DARK = rgb(181, 181, 181), rgb(115, 115, 115)
KEY = rgb(73, 73, 73)
TAN, TAN_DARK, TAN_INK = rgb(202, 184, 142), rgb(161, 145, 106), rgb(56, 44, 30)
NEEDLE_RED = rgb(139, 19, 19)
GLASS = rgb(43, 18, 38)
RED_OFF = rgb(139, 19, 19)
LABEL, LABEL_DIM = rgb(214, 214, 214), rgb(150, 150, 150)
LABEL_TAN = TAN


def draw(moving):
    """The panel. moving=True is the viewing copy (_proposed.png); False leaves out what the game draws."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lay = kit.Layout("hf_radio_20m_proposed", W, H)

    # ---- The case: lid edge, upper deck, lower deck, foot ----
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, 6], fill=CASE_LIGHT)
    d.line([1, 1, W - 2, 1], fill=CASE_HIGH)
    d.rectangle([1, 7, W - 2, H - 5], fill=CASE)
    kit.recess(d, [3, 8, W - 4, 56], DECK, DEEP, CASE_LIGHT)  # The upper deck, set back as on the model.
    d.line([1, 7, 1, H - 5], fill=CASE_LIGHT)
    d.rectangle([1, H - 4, W - 2, H - 2], fill=DEEP)
    for fx in (12, W - 28):
        d.rectangle([fx, H - 3, fx + 15, H - 2], fill=rgb(12, 12, 12))

    # ---- Upper deck: pale toggles, the tan meter, the maroon readout and its red lamp ----
    lay.label(img, "PWR", 14, 12, LABEL, centred=True)
    h = kit.toggle(img, 10, 20, 2, 1, PALE, PALE_DARK, DEEP, PALE, pitch=8, moving=moving)
    rt = {"positions": ["ON", "OFF"], "handles": kit.toggle_handles(10, 20, 2, pitch=8),
          "handle_colour": kit.hexc(PALE), "handle_shadow": kit.hexc(PALE_DARK)}
    lay.control("power", "switch2", 10, 20, 8, h, rt=rt, rt_type="switch", positions=["ON", "OFF"], rest=1)
    lay.label(img, "AGC", 30, 12, LABEL, centred=True)
    h = kit.toggle(img, 26, 20, 3, 1, PALE, PALE_DARK, DEEP, PALE, pitch=7, moving=moving)
    rt = {"positions": ["FAST", "SLOW", "OFF"], "handles": kit.toggle_handles(26, 20, 3, pitch=7),
          "handle_colour": kit.hexc(PALE), "handle_shadow": kit.hexc(PALE_DARK)}
    lay.control("agc", "switch3", 26, 20, 8, h, rt=rt, rt_type="switch", positions=["FAST", "SLOW", "OFF"], rest=1)
    for i, s in enumerate(("FAST", "SLOW", "OFF")):
        lay.label(img, s, 36, 22 + i * 7, LABEL_DIM)

    needle = kit.meter(img, 60, 14, 76, 36, TAN, TAN_INK, NEEDLE_RED, CASE_LIGHT, CASE_HIGH)
    lay.control("meter", "meter", 60, 14, 76, 36, scales=["S", "PO", "SWR"], **needle)

    kit.display_window(img, 147, 15, 94, 28, GLASS, rgb(18, 18, 18), CASE_LIGHT)
    lay.control("display", "display", 147, 15, 94, 28, rt={"ink": "#FFB060"})
    lay.label(img, "RC-20", 146, 49, LABEL_TAN)
    lay.label(img, "SSB CW AM", 170, 49, LABEL_DIM)
    d.line([207, 48, 207, 54], fill=CASE_LIGHT)  # A divider between the mode line and the lamp.
    lay.label(img, "ON AIR", 210, 49, LABEL_DIM)
    kit.led(img, 236, 49, RED_OFF)
    lay.control("tx_lamp", "lamp", 235, 48, 6, 6, colour="red",
                rt={"x": 236, "y": 49, "w": 4, "h": 4, "colour": "#FF3020"})

    # ---- Lower deck, left: meter selector, mode keys, the small knobs ----
    lay.label(img, "METER", 75, 67, LABEL_DIM)
    for s, cx in (("PO", 102), ("SWR", 118)):  # Receive always reads S; the keys pick what transmit shows.
        lay.label(img, s, cx, 59, LABEL, centred=True)
        kit.key(img, cx - 6, 66, 12, 7, KEY)
        lay.control("meter_" + s.lower(), "button", cx - 6, 66, 12, 7, rt={"lit": kit.key_lit(cx - 6, 66, 12, 7)})

    lay.label(img, "MODE", 8, 66, LABEL_DIM)
    for s, name, cx in (("SSB", "mode_ssb", 18), ("CW", "mode_cw", 40), ("AM", "mode_am", 62)):
        lay.label(img, s, cx, 76, LABEL, centred=True)
        kit.key(img, cx - 8, 83, 16, 9, KEY)
        lay.control(name, "button", cx - 8, 83, 16, 9, rt={"lit": kit.key_lit(cx - 8, 83, 16, 9)})

    for s, name, cx, frac in (("AF", "af_gain", 20, 0.5), ("MIC", "mic_gain", 50, 0.5), ("SQL", "squelch", 80, 0.0)):
        lay.label(img, s, cx, 102, LABEL, centred=True)
        kit.knob_ticks(img, cx, 123, 13, LABEL_DIM)
        rt = kit.small_knob(img, cx, 123, 20, frac, rgb(30, 30, 30), moving)
        lay.control(name, "knob", cx - 14, 109, 28, 28, rt=rt, size=20, centre=[cx, 123], rest=frac)

    # ---- Lower deck, middle and right: step keys, the tuning knob, speaker, PTT ----
    for s, name, ky in (("UP", "step_up", 87), ("DN", "step_down", 112)):
        lay.label(img, s, 125, ky - 7, LABEL, centred=True)
        kit.key(img, 116, ky, 18, 9, KEY)
        lay.control(name, "button", 116, ky, 18, 9, rt={})
    lay.label(img, "QUICK", 125, 124, LABEL, centred=True)  # Lit while on the quick-recall frequency.
    kit.key(img, 116, 131, 18, 9, KEY)
    lay.control("quick", "button", 116, 131, 18, 9, rt={"lit": kit.key_lit(116, 131, 18, 9)})

    lay.label(img, "TUNING", 176, 62, LABEL, centred=True)
    kit.dial_ring(img, 176, 103, 31, LABEL_DIM)
    rt = kit.big_dial(img, 176, 103, 56, 0.5, rgb(26, 26, 26), rgb(18, 18, 18), rgb(64, 64, 64),
                      rgb(45, 45, 45), rgb(73, 73, 73), rgb(214, 214, 214), moving)
    lay.control("tuning", "dial", 148, 75, 56, 56, rt=rt, size=56, centre=[176, 103])  # The knob is the click area.
    lay.label(img, "10X", 140, 59, LABEL)  # The coarse-tuning key, latching, at the dial's top left.
    kit.key(img, 140, 66, 14, 7, KEY)
    lay.control("coarse", "button", 140, 66, 14, 7, rt={"lit": kit.key_lit(140, 66, 14, 7)})

    kit.recess(d, [214, 62, 251, 98], DEEP, OUTLINE, CASE_LIGHT)
    kit.grille(img, [215, 63, 250, 97], DEEP, rgb(14, 14, 14))
    lay.label(img, "PTT", 232, 106, LABEL, centred=True)
    kit.key(img, 214, 113, 37, 20, PALE)
    d.rectangle([218, 117, 246, 128], outline=PALE_DARK)
    lay.control("ptt", "button", 214, 113, 37, 20, rt={})
    return img, lay


def main():
    img, lay = draw(True)
    kit.write(img, lay, "hf_radio_20m_proposed")
    img, lay = draw(False)
    kit.write_runtime(img, lay, "hf_radio_20m")


if __name__ == "__main__":
    main()
