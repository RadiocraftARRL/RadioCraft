# HF panel runtime format (2026-11-09)

The maintainer approved the `_proposed` panels, so they are going in game. The Java screen (`HfPanel`) draws the
panel PNG, then draws the **moving parts in code** from a layout JSON. Each radio's script must therefore also write:

1. `src/main/resources/assets/radiocraft/textures/gui/hf_panels/<radio>.png`: the same panel **without its moving parts**.
   - Knobs: the cap and skirt, but **no pointer line or dot**.
   - Tuning dial: the knob, but **no finger dimple or marker**.
   - Switches, slides, toggles and levers: the slot, track or bezel, but **no thumb, bat or lever**.
   - Lamps: unlit. Buttons: unpressed. The meter: no needle, as now.
   - Keep `<radio>_proposed.png` unchanged, as the viewing copy.
2. `src/main/resources/assets/radiocraft/gui_layouts/hf_<radio>.json`, in exactly this schema.
   - Coordinates are panel pixels. Floats are fine for centres.
   - **Angles** are in degrees, with 0 straight up (12 o'clock) and positive clockwise.

```json
{
  "texture": "radiocraft:textures/gui/hf_panels/<radio>.png",
  "width": 256, "height": 150,
  "controls": [
    {"name": "af_gain", "type": "knob", "x": 0, "y": 0, "w": 0, "h": 0, "cx": 0, "cy": 0,
     "pointer_from": 2, "pointer_r": 7, "pointer_width": 1, "pointer_colour": "#E0E0E0", "min_deg": -135, "max_deg": 135},
    {"name": "agc", "type": "knob3", "...": "a knob with detents: as knob, plus positions [..] and angles_deg [..]"},
    {"name": "tuning", "type": "dial", "x": 0, "y": 0, "w": 0, "h": 0, "cx": 0, "cy": 0,
     "mark_r": 14, "mark_size": 5, "mark_colour": "#303030", "mark_shadow": "#101010"},
    {"name": "mode_ssb", "type": "button", "x": 0, "y": 0, "w": 0, "h": 0, "lit": [0, 0, 0, 0, "#FFC040"]},
    {"name": "agc", "type": "switch", "x": 0, "y": 0, "w": 0, "h": 0, "positions": ["FAST", "SLOW", "OFF"],
     "handles": [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]], "handle_colour": "#C8C8C8", "handle_shadow": "#606060"},
    {"name": "mode_lever", "type": "lever", "x": 0, "y": 0, "w": 0, "h": 0, "cx": 0, "cy": 0, "positions": ["SSB", "CW", "AM"],
     "angles_deg": [-40, 0, 40], "length": 11, "width": 2, "colour": "#C0C0C0", "tip_colour": "#E0E0E0", "tip_size": 3},
    {"name": "tx_lamp", "type": "lamp", "x": 0, "y": 0, "w": 0, "h": 0, "colour": "#FF3020"},
    {"name": "display", "type": "display", "x": 0, "y": 0, "w": 0, "h": 0, "ink": "#FFB060"},
    {"name": "meter", "type": "meter", "x": 0, "y": 0, "w": 0, "h": 0, "pivot": [0, 0], "radius": 30,
     "zero_deg": -50, "full_deg": 50, "s9_fraction": 0.6, "needle_colour": "#202020"}
  ]
}
```

## Control types

- **knob:** `x y w h` is the click area. The pointer is drawn from `pointer_from` to `pointer_r` px out from
  (`cx`, `cy`), at an angle from `min_deg` (value 0) to `max_deg` (value 1).
- **knob3:** a knob with detents. It adds `positions` and `angles_deg`, one angle per position.
- **dial:** the main tuning dial. The finger dimple is drawn `mark_r` from the centre, as a `mark_size` square, and it
  spins as the dial is turned.
- **switch:** slides, toggles, bats and the meter selector slide. `handles` gives one rectangle per position, the
  thumb's place there, in the same order as `positions`. The code fills it with `handle_colour`, plus a 1 px
  `handle_shadow` on its bottom and right.
- **lever:** a line from (`cx`, `cy`), `length` long, at the angle for its position, with a `tip_size` square at its
  end.
- **button:** pressed, the code darkens it. When a mode or meter button is **selected**, the code fills its `lit`
  rectangle with the given colour. Leave `lit` out if a separate lamp shows the selection.
- **lamp:** filled with `colour` when lit, plus a 1 px lighter centre.
- **display:** the frequency and status text are printed inside it in `ink`.
- **meter:** `x y w h` is the face the needle is clipped to. The needle runs from `pivot`, `radius` long, between
  `zero_deg` and `full_deg`.

## Names

The code wires controls by name, so use these exactly:

- **Mode:** `mode_ssb`, `mode_cw`, `mode_am`, and `mode_fm` on the all-band radio only. The 80m uses `mode_lever`
  instead, with positions SSB/CW/AM.
- **Meter selector:** `meter_s`, `meter_po` and `meter_swr` as buttons, or `meter_select` as a switch with positions
  `["S", "PO", "SWR"]`.
- **Knobs:** `af_gain`, `mic_gain`, `squelch`.
- **AGC:** `agc`, as a switch or knob3 with positions drawn from `OFF`, `SLOW` and `FAST`.
- **Tuning and stepping:** `tuning`, `step_up`, `step_down`, and `band_up` and `band_down` on the all-band radio.
- **Other controls:** `power`, `ptt`, `display`, `meter`.
- **Lamps, by what lights them:**
  - `on_lamp` or `pilot_lamp`: powered.
  - `tx_lamp`: transmitting.
  - `rx_lamp` or `busy_lamp`: a signal is coming in.
  - `ant_lamp`: an antenna is connected.
  - `mode_ssb_lamp`, `mode_cw_lamp`, `mode_am_lamp`: that mode is selected.
- **Other names:** anything else (decor, labels) is ignored. Leave labels out of this JSON.

## Verify

Write a small Python preview that draws `hf_panels/<radio>.png` plus every moving part, by the rules above, at two or
three different settings. Look at the previews. The pointers must sit centred on their knobs, the handles in their
slots, the lever in its bezel, the dimple on the dial and the needle on the meter's pivot.
