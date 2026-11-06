# Model design bounty

Blocks and items that need a proper model and texture from someone who draws. Code can ship with a placeholder
(a plain box with flat colours), but these are what players will look at, so they deserve the same hand-drawn style
as the existing radios and antennas.

## How the existing assets are made

- Block models are Blockbench exports (`"credit": "Made with Blockbench"`, `"parent": "block/block"`) in
  `src/main/resources/assets/radiocraft/models/block/`, with one texture atlas per block in
  `textures/block/` (32x32 for small things like the baluns, 64x64 for radios and the charge controller) and
  `texture_size` set to match. `charge_controller.json` is a good small example, `hf_radio_20m.json` a large one.
- Lit parts use a separate emissive texture named `<block>_e.png` (see `all_band_radio_e.png`) or a glow mask
  (`desk_charger_glowmask.png`), so lights stay bright in the dark.
- Blocks with an on/off look have two models (`charge_controller.json`, `charge_controller_on.json`) switched by
  blockstate.
- Deliverables for a bounty: the `.bbmodel` source, the exported block model JSON, the texture PNG(s) including any
  emissive layer, and if the block has states, one model per state. Item models can usually reuse the block model.

## How a model gets into the game

**Rotations:** Minecraft 1.21.1 only loads element rotations on one axis of -45, -22.5, 0, 22.5 or 45 degrees. Keep
the Blockbench project on Minecraft 1.21.1 (an export stamped `"format_version": "1.21.11"` is for a later version
and allows free angles, which then fail to load). `ModelFilesTest` checks every model the game uses.

**Exports stay as they are:** Blockbench writes over its export each time it saves, so models that need splitting
(two-tall blocks, lamps, glowing valves) or turning are built from the export by `python tools/build_models.py`
under other names. Add a new model to its `BUILDS` list and run it after every re-export.

What was done for the parrot controller, as the pattern for the next one:

- Export the block model JSON from Blockbench, then split it by state: one model with the body, and one tiny model
  per light holding only the lit face (`parrot_controller_rx.json` / `_tx.json`). The light models get
  `"render_type": "minecraft:cutout"` (transparent pixels would render black otherwise) and
  `"neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": false}` on the element so the LED is
  full bright. No `_e` texture or glow mask is needed for a plain JSON block; those are for the GeckoLib models.
- A multipart blockstate stacks them: body rotated by `facing`, each light when its property is true, and any
  connection stubs (the parrot's wire towards its radio) by their own property.
- Textures are lower-case files under `textures/block/`; the `.bbmodel` source goes in `doc/models/`.

## Open bounties

### Models and sounds: weather alert generator and receiver (2026-11-06)

Placeholders from `tools/weather_alert_models.py` (32x32 textures; blockstates hand-made in `src/main/resources`
with lit parts):

- `radiocraft:weather_alert_generator` (`models/block/weather_alert_generator.json`, `_ready.json`, `_tx.json`,
  `textures/block/weather_alert_generator.png`): the box that turns a weather station network into alerts. Placeholder:
  a grey full-block cabinet with a dark status window ("WX"), a green READY lamp, a red TX lamp and vents. Wanted: a
  rack-mount or wall-mount equipment cabinet (a 1980s-90s broadcast encoder: a small LCD or LED readout, a row of
  status lamps, a keypad or toggle switches). Keep the two lamps as their own full-bright parts (`ready=true` while it
  is on the air, `transmitting=true` while it sends).
- `radiocraft:weather_alert_receiver` (`weather_alert_receiver.json`, `weather_alert_receiver_lamp.json`,
  `textures/block/weather_alert_receiver.png`): a table-top weather radio. Placeholder: a 12 x 6 x 8 pixel white box with
  a speaker grille, a small green display, a red ALERT lamp on top and a short whip at the back. Wanted: the classic
  home weather alert radio (a white or grey wedge, a big speaker grille, a large red WEATHER/ALERT button-lamp on top,
  a pull-up whip, a little LCD), keeping the lamp lit model (`lit=true`, full bright) and a size that sits on a desk.
- Sounds (optional): the voice is generated with Piper and the public-domain LJ Speech voice (see
  `doc/Credits-WeatherVoice.md`); a recorded human voice (CC0/CC BY, every word in `tools/weather_voice.py`'s list)
  would sound better. The 1050 Hz tone is fine as it is.

### Item: Radio Cloner (2026-11-05)

`radiocraft:radio_cloner` (`models/item/radio_cloner.json`, `textures/item/radio_cloner.png`, from
`tools/radio_cloner_assets.py`): a radio shop's cloning box, read from one radio and write into others. Placeholder: a
grey box with a little LCD, a green and a red lamp and a coiled cable. Wanted: a flat item sprite of a small handheld
cloning box (a two-digit display, READ/WRITE buttons) with its cable and radio plug, perhaps a second texture (or an
item property) for the loaded state; the item glints while loaded. No block model.

### Models: ADS-B beacon, receiver and antenna (2026-11-05)

Placeholders from `tools/adsb_models.py` (32x32 textures; blockstates hand-made in `src/main/resources`, each with a
lit part added by `lit=true`):

- `radiocraft:adsb_beacon` (`models/block/adsb_beacon.json`, `adsb_beacon_lamp.json`, `textures/block/adsb_beacon.png`):
  the 1090 MHz transponder a craft carries. Placeholder: a 10 x 5 x 12 pixel grey box with an amber code window, two
  knobs, an IDENT button and a white blade antenna on top. Wanted: a panel-mount avionics transponder (a Garmin/King
  style face: four-digit squawk readout, IDENT, mode knob) on a small tray, or a remote box with a shark-fin blade.
  The front is where the player looked when placing it (the craft's nose), north in the model. Keep the reply lamp
  (`adsb_beacon_lamp`, full bright, cutout) as its own model, lit while transmitting.
- `radiocraft:adsb_receiver` (`adsb_receiver.json`, `adsb_receiver_screen.json`, `textures/block/adsb_receiver.png`):
  a full-block display unit whose **whole front is a square screen, edge to edge** (maintainer, 1.12 playtest fixes
  2), like a modern ATC/ADS-B display. Placeholder: a dark cabinet, the front a dark green square with one ring, knobs
  on the side. Wanted: the cabinet sides, top and back (a monitor housing, vents, knobs on the side or top), keeping the
  front face a flat screen to its edges; the glowing face (`adsb_receiver_screen`, full bright, the whole north face at
  z -0.02) when on. `client/render/AdsbScopeRenderer` draws the live picture over that face (`SCREEN_X0..Z`), so keep
  them in step if the screen moves. Its screen GUI is drawn in code and needs no art.
- `radiocraft:adsb_antenna` (`adsb_antenna.json`, `textures/block/adsb_antenna.png`): a short 1090 MHz vertical (a
  quarter wave is 7 cm) with a ground plane, or a white fibreglass collinear. Placeholder: a black stub with a white
  tip on a metal plate.

### Real-World Interface (2026-11-05)

Placeholder from `tools/real_world_interface_assets.py` (re-run it to rebuild; it overwrites the files below):
`radiocraft:real_world_interface`, a small dark box (`models/block/real_world_interface.json`, 10 x 4 x 9 px,
`textures/block/real_world_interface_case.png` and `_front.png`) with a red and white licence stripe, PTT and COS lamps,
two level knobs and two jacks at the back. Wanted: a proper rig interface (think SignaLink or a repeater controller):
a front with PTT/TX and COS/RX lamps and IN/OUT level knobs, a cable or two to the back, and a sticker saying it goes to
a real transmitter. The block is 4 px high; keep the hanging (ceiling) variant in mind. The GUI is plain fills too
(`client/screens/RealWorldInterfaceScreen`): a panel texture with the warning area, lamps and two level meters would
help.

### FCC field gear: hat, jacket, badge, banner seal (2026-11-05)

Placeholders from `tools/fcc_assets.py` (re-run it to rebuild them; it overwrites the files below):

- `radiocraft:fcc_hat` (`textures/item/fcc_hat.png`) and its worn look in `textures/models/armor/fcc_layer_1.png`
  (the 64x32 vanilla armour layout, head and hat boxes): a 1970s FCC field agent's felt fedora. The placeholder only
  paints the top half of the head box brown with a black band, so it reads as a beanie; a real brim needs a model.
  Wanted: a sprite, and either a better layer texture or a small custom armour model (a NeoForge
  `IClientItemExtensions#getHumanoidArmorModel` with a brim, which is code we can add once the art exists).
- `radiocraft:fcc_jacket` (`textures/item/fcc_jacket.png`, body and arm boxes of `fcc_layer_1.png`): a 1970s mustard
  corduroy sport coat with wide lapels, a white shirt and a wide maroon tie. Wanted: a proper sprite and layer texture
  (corduroy ribs, lapels, pockets, maybe the badge on a breast-pocket wallet).
- `radiocraft:fcc_badge` (`textures/item/fcc_badge.png`): the FCC field agent's badge, worn in the Curios body (chest) slot.
  Placeholder: a gold shield with a blue enamel seal. Wanted: a sprite in the style of a US federal badge (eagle on
  top, "Federal Communications Commission" ring, "Field Agent"). Nothing draws it on the player when worn; a Curios
  renderer (like the handheld on the belt) could hang it on the chest if someone draws a small model.
- `radiocraft:fcc_banner_pattern` (`textures/item/fcc_banner_pattern.png`): a pattern item like the vanilla ones.
- The seal pattern itself, `textures/entity/banner/fcc.png` and `textures/entity/shield/fcc.png` (64x64 white masks in
  the vanilla banner and shield layouts; the dye colours them): placeholder is a ring with a bolt over "FCC" and an
  underline. Wanted: a seal-like emblem (the 1970s FCC seal had an eagle over lightning bolts) that reads at 20x40
  pixels, and its 12x22 shield version.

### Item: Band Plan Sheet (2026-11-04)

`radiocraft:band_plan` (`models/item/band_plan.json`, `textures/item/band_plan.png`, from `tools/band_plan_assets.py`):
the ARRL-style band plan chart a ham pins over the desk. The placeholder is a 16x16 sheet of paper with four rows of
coloured stripes. Wanted: a proper flat item sprite in the mod's style (a sheet, perhaps slightly curled, with a few
recognisable coloured band bars and a title line). Its screen is drawn in code and needs no art.

### Item: MARS Mod (2026-11-04)

`radiocraft:mars_mod` (`models/item/mars_mod.json`, `textures/item/mars_mod.png`, from `tools/band_plan_assets.py`):
the little modification board that opens a ham rig's transmitter out of band. Placeholder: a green board with a red
diode and two jumper wires. Wanted: a small PCB sprite (a diode or a cut jumper, a couple of SMD parts) that reads as
"a part you solder into a radio", a little shady/contraband in feel.

### Model: Radio Moderation Computer (2026-11-04)

`radiocraft:moderation_computer` (`models/block/moderation_computer.json`, `textures/block/moderation_computer.png`, from
`tools/moderation_computer_model.py`): the server admins' moderation screen, so it should look like a late-90s office PC
in yellowed beige plastic. The placeholder is five boxes: a desktop case (CD and floppy drives, power button, green and
amber lamps) with a CRT monitor on top (its glass showing a teal desktop with a small grey window) and a keyboard in
front. Wanted: a proper chunky CRT with a curved tub and vents, a keyboard with readable key rows, maybe a mouse and pad,
and a screen glow (an emissive layer for the glass) so it reads as switched on. It faces north (keyboard to the front),
one block, nothing above 15 pixels. Its screen GUI is drawn in code (Windows 98 style) and needs no art.

### Model: repeater autopatch controller (2026-11-04)

`radiocraft:repeater_autopatch`: the box that patches a phone PABX into a VHF repeater. For now it is the phone PABX's
model with its texture recoloured dark green (`textures/block/repeater_autopatch.png`, from
`tools/repeater_autopatch_assets.py`). Same size as the PABX is fine; it wants a front of its own (a small DTMF keypad
or status lamps, a phone jack and a coax/audio port to the repeater) and a lamp that lights while armed or on a call
(`lit=true`).

### Texture: APRS module (2026-11-04)

`radiocraft:aprs_module` (`textures/item/aprs_module.png`): a small TNC/GPS board that goes inside the VHF handheld.
The current 16x16 is a programmer placeholder (dark board, orange APRS label, gold edge pins).

### Model: hand microphone in the hand (2026-11-04)

`models/item/hand_microphone_3d.json` with `textures/item/hand_microphone_3d.png`, made by
`tools/hand_microphone_model.py`: a placeholder speaker mic (grille, PTT bar, clip, cord stub) shown in the hand for
the hand, corded and manpack mics. The inventory keeps the flat `hand_microphone.png`.

### Model: VHF handheld in the hand (2026-11-04)

`models/item/vhf_handheld_3d.json` with `textures/item/vhf_handheld_3d.png` is a first try at the handheld held in the
hand (body, LCD, keypad, rubber duck antenna, battery pack on the bottom), made by hand rather than in Blockbench. The
inventory icon stays the flat sprite. A proper Blockbench model and texture in the same spirit is welcome.

### Model: antenna splitter (2026-10-30)

`radiocraft:antenna_splitter`: a receive-only splitter, one antenna to several radios. A small metal box with coax
connectors (SO-239 style) on its sides, the full block width and about 6 px high so coax meets its sides (the shape is
0-16 x 0-6 x 0-16). For now `models/block/antenna_splitter.json` is a placeholder slab of iron and anvil textures.


### Sound: redstone interference buzz

Done 2026-10-27: the maintainer's `redstone_rfi.ogg` (19 s of switching hash) plays through `RadioInterferenceHumSoundInstance`,
its loudness following the interference level, so a lever flip is a short burst and a clock keeps it going.


### Textures: crystals and crystal-quality radios

Requested by the maintainer 2026-10-22 (they plan to draw these). All are placeholders copied from existing art:

- ~~Raw crystals~~ Done (2026-10-23, maintainer): `textures/item/crystal_pure.png`, `crystal_good.png` and
  `crystal_poor.png`, shared by every band's raw crystal of that quality.
- Encased crystals: `textures/item/encased_crystal_<band>_<quality>.png`, copies of the old radio crystal texture.
- ~~Insulation~~ Done (2026-10-23, maintainer): `textures/item/insulation.png`.
- Premium (`_pure`) and Cheap (`_poor`) radios: `textures/block/<radio>_<pure|poor>.png`,
  `textures/gui/<radio>_<pure|poor>.png` and `..._widgets.png` for hf_radio_10m/20m/40m/80m and vhf_base_station,
  and `textures/item/vhf_handheld_<pure|poor>.png` plus its GUI pair. All copies of the plain radio's art.

### Radio Interface and Radio Fax

From the TNC rework (2026-10-23, see [TncRework.md](TncRework.md)). The H19 APRS Terminal and the Ionosonde Antenna
and the Ionospheric Processor are done (maintainer).

- **Radio Interface** (`radio_interface`): wanted a small modem box with PWR, DCD, TX, RX and RPT lamps. Its screen
  is a callsign box and a TX switch on a plain panel. Draft 2026-10-25 from `tools/interface_models.py`: hammertone
  modem with a bakelite panel, five brass-bezelled lamps and a TX toggle; `radio_interface_pwr/dcd/tx/rx/rpt` are the
  lit lamp faces, waiting on lamp properties in the block. The old `digital_interface.png` is no longer used.
- **Radio Fax** (`radio_fax_<wood>`, 2026-10-27, replacing the Digital Transfer Interface): a desk fax machine
  clad in each vanilla log (the log textures, so one detail texture `radio_fax.png` and a model per wood) from
  `tools/radio_fax_models.py`: a cream deck with a keypad and a small LCD, a sheet in the front tray and one standing
  in the feeder behind. Its screen is a painted fascia (`textures/gui/radio_fax.png`) over the vanilla chest's
  inventory half: wanted, a proper inventory panel in the same style. The old `interface_models.py` transfer
  interface draft (`transfer_interface.json`, `transfer_interface_busy.json`) is no longer used.
- **DTMF Decoder** (`dtmf_decoder`, 2026-11-04): a small box against a radio with a red lamp (a code is coming in)
  and a green lamp (it matched); `dtmf_decoder_red` and `dtmf_decoder_green` are the lit faces the blockstate adds.
  Placeholder grey box from `tools/dtmf_models.py` (texture `dtmf.png`); wanted: a 1970s tone decoder or repeater
  controller card in a box, with the two lamps and maybe a code thumbwheel. Its screen is a plain panel.
- **DTMF Encoder** (`dtmf_encoder`, 2026-11-04): a touch-tone keypad (1-9, *, 0, #) in a small box with a display,
  against a radio. Same placeholder script and texture; wanted: a desk "autopatch" pad or a Bell keypad in a case.
  Its screen is a plain panel with buttons: wanted a keypad panel in the same style.
- Lamps go in as separate named elements, like the H19's MSG / CONTACT / READY: `python tools/split_lamps.py <model>
  <LAMP>...` splits a Blockbench export into the body and one full-bright model per lamp.

### Models: station furniture, commercial gear, the horn and the manpack

Asked for by the maintainer 2026-10-25, drafted the same day from `tools/station_models.py` (none registered yet):

- **Tape player** (`tape_player`): a full block reel-to-reel in a walnut cabinet with a brushed deck, VU meters and
  piano keys. `tape_player_base` plus `tape_player_reels_0..3`: the reel sprites turned in 22.5 degree steps, so
  cycling the four frames while playing spins them (the four spokes repeat every 90 degrees).
- **Commercial repeater** (`commercial_repeater_0..3`): four blocks of open silver rack: batteries and charger, the
  repeater radio and amplifier, the duplexer cavities and harness, the controller under the roof. Lit lamps:
  `commercial_repeater_1_pwr/_rx/_tx` and `commercial_repeater_3_beacon`.
- **Tape player screen** (wanted 2026-10-26): a panel for the tape player's GUI in the wood-and-brushed-deck style
  of its model, with a window for the tape's name and time, PLAY / STOP / REC / EJECT keys, a mode switch (manual,
  loop, interval, redstone) and an interval knob. It is drawn plainly in code for now (`TapePlayerScreen`).
- **Commercial radio module** (`commercial_radio_module_0..3`): a GE MASTR II style closed cabinet, four blocks:
  louvred door, badge and handle, the control panel with meter and lamps (`commercial_radio_module_2_pwr/_rx/_tx`),
  top vent and cap.
- **Microwave horn** (`microwave_horn_<x>_<y>_<z>`, x and z 0..1, y 0..3): the AT&T Long Lines horn-reflector, two by
  two blocks and four tall, mouth facing north. Edit the whole horn in `doc/models/microwave_horn.bbmodel` (it links
  `textures/block/microwave_horn.png`), save, and run `python tools/build_models.py` to cut it into the 16 block
  models. No JSON export: Minecraft cannot load a model that size, so the build reads the `.bbmodel` itself. The
  project is in Blockbench's Generic Model format on purpose: Java Block snaps every cube back inside 3x3x3 blocks.
  Rotate on one axis only, by 22.5 or 45 degrees; a rotated cube cannot be cut at block edges, so keep each one
  within a block of the block its middle is in (the reflector panels are).
- **Microwave radio** (`microwave_radio`): a full block with the open boards on top and a screen, jacks, knob and
  DB9 on the front; `microwave_radio_screen` is the lit screen overlay.
- **Telephone** (`telephone`): a cream desk phone, half a block wide and a quarter tall, with a sloped keypad deck.
- **WEFAX receiver screen** (`textures/gui/wefax_receiver.png`, `wefax_receiver_paper.png`): drawn 2026-10-25 by
  `tools/wefax_gui.py` in the teletype screen's manner; the script's constants give every box the screen needs
  (paper slot, buttons up and down, paper-supply window steps, lamp). No screen class yet.
- **Manpack radio** (`manpack_radio`): a PRC-25 style backpack sized to a player's back (8 wide, 11 tall, 4 deep,
  straps on the south side) for a wearable renderer to draw.

### Models: tower beams and the antenna switch

Asked for by the maintainer 2026-10-26, drafted the same day (the beams were registered the same day, with a
dynamic model that reads the 26 neighbours, `client/model/TowerBeamModel`; the antenna switch was registered 2026-10-30):

- **Tower beams** (`tower_beam_thick` 8x8, `tower_beam` 6x6, `tower_beam_thin` 4x4, all 16 tall straight up), from
  `tools/tower_beam_models.py`: steel lattice beams in the iron bars palette that connect to any touching beam,
  including the diagonal ones. Each thickness is a part set: `<beam>_core` (a cube at the centre) plus 26
  `<beam>_arm_<direction>` arms (up/down, north/south, east/west, joined in that order: `up_north_east`), each from
  the core to the block boundary where it meets the neighbour's matching arm. Face arms are boxes; edge arms (two
  components) are boxes turned 45 degrees about the third axis and poke past the boundary to the shared block edge;
  corner arms (three components) are stair-stepped cubes, since no single-axis rotation reaches a body diagonal.
  `<beam>.json` is the straight column for the inventory. Showing the arms needs a dynamic baked model (or a block
  entity's ModelData) that reads the 26 neighbours: 26 boolean properties would be 2^26 blockstates. A 2x2 column of
  beams then cross-braces itself. The textures are opaque; see-through bars would show the hidden faces where parts
  overlap unless the model drops them.
- **Antenna switch** (`antenna_switch`, `antenna_switch_pointer`), from `tools/antenna_switch_model.py`: a Workman
  CX-3 style coaxial switch, a 10x6x10 aluminium box with a cream label plate on top and a 2x2x2 SO-239 on each
  side at ground level, one pixel inside the block face (x 7..9, y 0..2, where the coax wire model runs). The
  north connector is the input (brass); outputs 1, 2, 3 are east, south, west, numbered on the plate. The pointer
  model is the knob's lever pointing north: turn it with the blockstate `y` rotation toward the output in use.

### GUI textures drawn 2026-10-26 by `tools/link_guis.py`

- `microwave_radio.png` (beige rack front), `telephone.png` (desk phone from above) and `hf_manpack.png` (olive drab
  PRC panel), each with its `_widgets.png`: procedural first drafts; hand-drawn versions are welcome as long as the
  boxes in the script stay where they are (the screens' hit boxes).

### Item: world map module

- **World map module** (`textures/item/world_map_module.png`, 16x16): a 3.5 inch floppy with a scrap of map on its
  label, crafted with a direction finder into the map enabled one. The current texture is a quick placeholder drawn
  2026-10-26 (`scratchpad` script, no source kept); a proper one is welcome.

### Models: speakers

Asked for by the maintainer 2026-10-26, drafted the same day from `tools/speaker_models.py`; registered the same day:

- **Desk speaker** (`desk_speaker_<wood>` for oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo,
  crimson, warped): a 6x5x4 wooden cabinet with a cloth grille, knob and terminal strip. The cabinet faces use the
  vanilla plank textures, so one grille texture (`desk_speaker.png`) serves every wood.
- **Large speaker** (`large_speaker`): a full block after the National NC speaker: grey speckled cabinet, round
  diamond-weave grille, three chrome bars, red diamond badge, chrome feet.
- **Wall speaker** (`wall_speaker`): 16 wide and tall, 3 deep, back against the south face as vanilla wall blocks
  are drawn (facing=north shows it as is); silver frame, marbled grey cloth, round badge, a terminal strip on the
  back for a wire in the block behind.
- **Premium desk speaker** (`premium_speaker_marble`, `_iron`, `_gold`; marble was black until 2026-10-26): an 8x6x6 metal cabinet with a slotted
  grille, bezel strips, knob and feet; one geometry, a texture per colourway.
- **Loudspeaker** (`loudspeaker`): a PA reflex horn, blue-grey stepped bell with a cream rim pointing north, an
  octagonal driver, on a U-bracket and wooden base.

### Models: direction finder, module, screen and handheld

Asked for by the maintainer 2026-10-26, drafted the same day from `tools/direction_finder_models.py`:

- **Direction finder** (`direction_finder`): a cream CRT cabinet in the H19's manner, taller (14) with a 12 x 9.5
  screen, a power button and a pilot lamp on the lower panel, an SO-239 low on the back where the coax wire runs.
  Overlays (cutout, full bright, for a `display` property as the H19 has): `direction_finder_on` (lamp amber and an
  amber prompt on the screen) and `direction_finder_compass` (lamp amber and an amber compass rose).
- **Direction finder module** (`direction_finder_module`): the same cabinet with a floppy slot and eject button and
  one lamp; `direction_finder_module_red` and `_green` light it.
- **Direction finder screen** (`textures/gui/direction_finder.png`, `direction_finder_widgets.png`, 300 x 200 like
  the H19's): a black CRT for the amber TUI to draw on, a side panel with the DF-1 plate, PWR lamp and POWER button;
  the widgets texture has the button pressed and the lamp lit at the same places. The script's `CRT`, `POWER_BUTTON`
  and `GUI_LAMP` constants are the boxes. Used by `DirectionFinderScreen` (2026-10-26).
- **Handheld direction finder** (`handheld_direction_finder`, with `display` transforms, for an item model to use as
  its parent): a much simplified loop DF set, olive with a meter and dial, loop and whip on top, and a lamp on the
  top edge; `handheld_direction_finder_lit` is the lamp flashing. Both live in `models/block` so `ModelFilesTest`
  checks them.

### Models: weather instruments (Another Weather Mod and RadioCraft)

Requested by the maintainer 2026-10-25. None of these are built yet; they wait on models. The first five belong to
Another Weather Mod (MIT, `E:\AnotherWeatherMod`, assets under `assets/anotherweathermod/`), the weather station to
RadioCraft. Each is a plain JSON block model like the parrot controller, unless noted.

- **Tornado siren**: a pole-mounted siren horn (the classic round or rotating horn), about one block, sits on a
  fence or pole. Sounds when a tornado comes near or on a redstone signal. A rotating horn would need two models or
  an animated part.
- **Wind vane**: a weathercock or arrow on a short post with N/E/S/W letters; the arrow turns to the wind (separate
  arrow model, turned in code, or 8 or 16 rotation states).
- **Barometer**: a wall-mounted round brass dial with a needle (needle as its own small model, or states for a few
  positions: stormy, rain, change, fair, very dry).
- **Rain gauge**: a small clear cylinder with marks on a short post; shows a water level (a few fill states).
- **Thermometer**: a wall-mounted tall thermometer with a red column (a few fill states, or the column as its own
  model). Draft 2026-10-25: `thermometer_base` plus `thermometer_column_0..4`.
- **Weather station** (RadioCraft, for APRS weather reports): a mast-top station combining a small anemometer cup
  wheel, a wind vane and a radiation shield (the stacked white plates), as a block that sits on an antenna pole.
  The cups could spin (GeckoLib) or be still.
  Draft model drawn 2026-10-25 (1960s look), from `tools/weather_station_model.py`: `weather_station` (whole),
  `weather_station_base` plus `weather_station_cups_0..3` (four spin frames at 22.5 degree steps). Registered
  2026-10-26 with these models; the cups spin by blockstate.
  Matching drafts for the single instruments (wind vane, barometer, rain gauge, solar detector, tornado siren) come
  from `tools/weather_instruments_model.py` and land in Another Weather Mod's `models/block` and `textures/block`;
  the tornado siren is two blocks (`tornado_siren_lower`, `tornado_siren_upper_0..3`). All registered 2026-10-26.
- **Tornado acoustic sensor** (Another Weather Mod, built 2026-10-26): a half-height equipment cabinet with a white
  radome on top, like the small domes storm chasers park in tornado alley. Draft from
  `tools/weather_instruments_model.py` (`tornado_sensor`); the dome is stacked layers and could be rounder.
- **Storm Protector** (Another Weather Mod, creative only, built): wanted its own look in place of the command
  block's textures. Draft 2026-10-25: riveted slate with a brass frame, a shield and lightning bolt on the sides and
  a storm spiral on the ends (`storm_protector_side.png`, `storm_protector_top.png`, model now `cube_column`).
- **Ionospheric Processor screen** (maintainer, 2026-10-27): the rack's terminal screen is not obvious enough on the
  model; wants a clearly readable amber screen face (lit while on, as the block state already provides) so players
  see there is something to right click.
- **Commercial radios** (1.12 step 2, placeholders from `tools/commercial_radio_assets.py`): the case must stay a
  light grey layer (item `layer0` / block faces with `tintindex: 0`) so dye can tint it, with the details untinted on
  top.
  - ~~**Basic Commercial Handheld**~~ Done (2026-11-06, maintainer): his 3D model `models/item/basic_commercial_handheld.json`
    and 64x64 `textures/item/basic_commercial_handheld.png` (body, battery, antenna, channel and volume knobs, PTT,
    speaker panel, TX/RX/power LEDs). `tools/commercial_handheld_models.py` only adds a light grey copy of his case
    (front and sides) in empty parts of the sheet for the dye tint (`tintindex` 0) and lays the speaker panel, LEDs
    and seam over the front untinted; undyed it is tinted back to his grey (`BASIC_DEFAULT_CASE_COLOUR` 0x616265).
    If you redraw the front or sides, run the script again to refresh the copies. Its screen draws the same texture.
  - **Upgraded Commercial Handheld**: placeholder 3D model from `tools/commercial_handheld_models.py` (2026-11-06), a
    1990s professional handheld (GP300 feel, generic "RC-300" plate, no real brand): tall black body, rubber duck on
    the top left, channel and volume knobs and a lamp on top, speaker grille, a small LCD strip, a 4x4 keypad (A-D in
    the right column; B and C switch zones), side PTT, belt clip, battery. 128x128 `upgraded_commercial_handheld.png`
    (8 px per unit): case regions light grey and tinted, everything else untinted; region map in the script, which the
    screen (`CommercialHandheldScreen`) also reads. A proper model and texture in the same layout are welcome.
  - **Commercial Mobile Radio** and **Commercial Low Band Radio**: dash-mount mobiles (blocks), a box with a front panel,
    display (the element named `DISPLAY` is where the channel text is drawn), channel knob, TX/RX lamps (positions in
    `RadioDisplays`), a mic hook on the side. The low band one older and plainer. Models `commercial_mobile`,
    `low_band_mobile`; textures `commercial_mobile_case`, `commercial_mobile_front`, `low_band_mobile_front`.
  - The handhelds' screens now draw their item textures (2026-11-06); the mobile's front panel screen is still
    plain fills: wants a texture.
- **Marine radios** (1.12 step 3, placeholders from `tools/marine_radio_assets.py`):
  - **Marine Handheld** (`marine_handheld`, item): a floating VHF marine handheld (Standard Horizon HX / Icom M-series
    feel): dark grey, a yellow or orange float collar, rubber antenna, a big display for the channel number, keys for
    CH up/down, 16/9 (red), H/L and DW. Ideally a 3D item model.
  - **Marine Radio** (`marine_radio`, block): a fixed-mount VHF marine radio (Standard Horizon Eclipse / Icom M330
    feel): white case, dark front panel, big channel display (the element named `DISPLAY` is where the channel text
    is drawn), a channel knob, the red 16/9 key, TX/RX lamps (positions in `RadioDisplays`), a mic hook on the side.
    Textures `marine_radio_case`, `marine_radio_front`. It works on Sable ships, so it may be seen on boat consoles.
  - Both screens (handheld front, fixed-mount front panel) are drawn with plain fills: want textures.
- **Camera and photo paper** (1.12 step 4, placeholders from `tools/camera_assets.py`):
  - **Camera** (`camera`, item): a mid-century press or box camera (Graflex / Kodak Brownie feel): black leather body,
    chrome top plate and lens ring, viewfinder, a red or chrome shutter button. Item texture `camera`; ideally a 3D item
    model held up to the eye in first person.
  - **Photo Paper** (`photo_paper`) and **Color Photo Paper** (`color_photo_paper`): a small packet or stack of glossy
    sheets (a Kodak/Polaroid-style box would be nice), the colour one with a red-green-blue mark. Item textures.
  - **Sound: camera shutter**: the camera uses the stone button click (pitched up) for now; a real mechanical shutter
    and film-advance sound (CC0) would be better.
  - **Viewfinder overlay**: drawn with plain fills (darkened border, white corner marks, centre cross); a texture of a
    real camera viewfinder (rounded mask, brightline frame) would look better.
  - **Radio fax PHOTO slot**: the fax panel art has three supply slots; the fourth (photo paper, right of MAP) has its
    frame drawn by code. Redraw the panel with four (`tools/radio_fax_models.py`).
- **Radio computer** (1.12 step 5, placeholders from `tools/radio_computer_models.py`):
  - **Computer Monitor** (`computer_monitor`, block): the moderation computer's CRT and keyboard without its desktop
    case, as the brief asked ("the moderation computer's model, slightly changed"): an early-90s VGA monitor on a
    swivel stand with a putty case, and a clicky keyboard in front. The glass must stay where
    `ComputerMonitorRenderer` draws the live screen (north face, x 3.75-12.25, y 3.25-9.5 model units, z 6), or move
    both together. The idle glass shows a blue DOS screen.
  - **Radio Computer** (`radio_computer`, block): the PC under the monitor, a full-height putty tower or a desktop on
    a small desk: a 5.25" and a 3.5" drive bay (the floppy goes in the 3.5"), power switch, green power and amber disk
    lamps. A lit disk lamp while a program reads or writes would be a nice extra (needs a blockstate).
  - **Scanner** (`scanner`, block): a 1990s flatbed scanner and printer (an HP OfficeJet or a Canon all-in-one):
    a lid, a paper tray standing at the back, a print slot at the front, a ready lamp.
  - **Floppy disks** (items): one disk texture (`textures/item/floppy_disk.png`, a black 3.5" disk with its metal
    shutter) and a white label layer (`floppy_disk_label.png`) tinted per program in `ComputerAppScreens`. Better art:
    a label with a little picture per program (logbook, APRS, SSTV...), and the secret disks (Snake, the
    falling-blocks game, the music player, the Bible) in other colours or hand-written labels.
  - **Scanner screen**: the lid-up panel is plain fills (`ScannerScreen`); wants a texture.
  - **Program Manager icons** (1.12 launcher work, placeholders from `tools/computer_icons.py`): one 32 x 32 PNG per
    program in `textures/gui/computer/icons/<program id>.png` (26 now: the built-in ones, every floppy program and the
    secret ones, SETUP and ABOUT). Wanted: Windows 3.1 / DOS Shell style pictograms, readable at 32 x 32. **Keep to
    the sixteen EGA colours exactly** (`DosColours.PALETTE`; no anti-aliasing, transparency allowed): the colour
    schemes recolour icons by matching those colours, and `ComputerIconsTest` fails on any other colour.
