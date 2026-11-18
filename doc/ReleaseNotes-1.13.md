# RadioCraft alpha 1.13

Minecraft 1.21.1, NeoForge. Needs Simple Voice Chat and GeckoLib;
[Another Weather Mod](https://github.com/RadiocraftARRL/AnotherWeatherMod) is optional (1.13.0 or later).
Everything below is explained in the in-game ARRL Radiocraft Handbook and on the [wiki](https://radiocraft.bigtimeradio.us/wiki/).

## 1.13.1

### Fixed
- The FIRE ALARM, SCADA and FCC Monitor program floppies can now be crafted (they were creative-only in 1.13.0), each
  a blank floppy and one more item, shapeless:
  - **FIRE ALARM**: flint and steel.
  - **SCADA**: a redstone repeater.
  - **FCC Monitor**: a spyglass.

## 1.13.0

### New

#### Every HF band has its own job
- Skip now follows real typical ranges, at 2 blocks a kilometre, and each band is good for something different:
  - **80m** is the regional band: line of sight 800 blocks, skip 100-1,000 by day and 100-4,000 at night. Its skip
    starts inside its line of sight, so it has **no silent zone**.
  - **40m** is the daytime workhorse: line of sight 650, skip 100-2,000 by day. At night it **goes long**
    (2,000-7,000) and loses the middle distances to 80m.
  - **20m** is for distance by day: line of sight 500, skip 1,600-8,000 by day and 3,000-8,000 at night, but weak at
    night in average and fair weather and closed in poor.
  - **10m** reaches a little further locally than 40m (line of sight 700). In **average** solar weather it now opens
    lightly by day, reaching only the near part of its skip window (2,500-3,490 blocks); in good and great weather it
    opens fully. At night it stays closed except in great weather.
  - **CB** follows 10m: line of sight 525, a light daytime opening of 1,750-2,440 blocks in average weather, full when
    the sun is lively, and no night skip. **Low band** has a line of sight of 600.
- Which band for which distance: up to 1,000 blocks, 80 or 40m by day and 80m at night; to 2,000, 40m by day and 80m
  at night; to 4,000, 20m by day and 40 or 80m at night; further, 20m by day and 40m at night. The Handbook's Bands &
  Range entry has the whole picture.
- **Radio horizon**: line of sight reaches further from high ground. A band's range grows with the square root of each
  antenna's height above the average terrain around it. Two masts 6 blocks above the land round them get the band's
  usual range; a hilltop or a tall tower gets much more (up to 8 times); two handhelds on flat ground get about half.
- **Takeoff angle per hop**: each skip hop leaves at the angle that reaches the sky half way along it, on a curved
  earth. A short 80m hop goes nearly straight up and the longest hops skim the horizon at about 3 degrees. A station
  deep in a valley loses long, low hops over the valley walls but keeps short, steep ones. Stations now look 256
  blocks out for what blocks a low angle (it was 64).
- **Multi-hop skip** on 10m, 20m and 40m (and CB and low band with 10m): while the band is fair or better and fully
  open, a signal can bounce off the ground and up again, up to 3 hops. Two hops reach twice as far and three hops
  three times; each hop past the first leaves about half the signal.
- **Sunrise and sunset**: bands no longer snap from day to night. While the sun is near the horizon (about 1,900 ticks)
  each band's skip window slides from its night place to its day one, a band that skips only by day fades in and out,
  and the solar weather's day and night conditions blend the same way.
- An antenna's **skip scale** (verticals, the 5-BTV, the CB whip) now moves the whole skip window out, silent zone and
  all, not only its far edge.
- Live radios, the Propagation Predictor (PREDICT) and `/bandcalculate` all use these rules. PREDICT stays hourly.

#### Satellites
- A **satellite** is an FM repeater in orbit. During a pass, anyone with open sky can work it from thousands of blocks
  away: talk on its **uplink** with its tone, and it sends you out on its **downlink** to everyone under it. It passes
  over any one place once or twice a Minecraft day, for up to about two minutes, and is heard within about 5,000 blocks
  of the point under it. Both ends need open sky (wood, glass and leaves overhead are fine, stone is not). It holds
  your voice about 300 ms before repeating it, so you hear yourself echo.
- There is no launching: a satellite goes up on a **contract**. A master cartographer may sell an **Amateur** or
  **Commercial Satellite Contract** (64 emeralds and a diamond). Right click it: the satellite is launched in your name
  and you get its **Satellite License** (its name, three frequencies, tone and launch day) and the SATTRACK floppy.
  An amateur satellite's downlink is on 145.800-145.990 MHz with the uplink 600 kHz below, so a 2m radio with a
  -600 kHz offset and the tone works it. A commercial one uses a commercial VHF pair. Each also has a **telemetry**
  frequency: a steady carrier with a beep a second, its name in Morse every minute, and a data frame (battery, current,
  temperature, sunlight or shadow, mode, days to decay) every ten seconds as a pager-like buzz.
- **Geostationary contracts** (64 emeralds and a block of diamonds, amateur or commercial) park a satellite that stays
  put in the sky near where you use it: always up within 5,000 blocks of that spot and never anywhere else, with half
  the delay, but its orbit decays twice as fast.
- An orbit **decays** after about 90 real days (45 for a geostationary one): the satellite burns up, a tiny streak in
  the night sky, and its license stays as a keepsake. Its owner can also **decommission** it from SATTRACK.
- **Amateur modes**, switched by the owner in SATTRACK while it passes: **Repeater** (FM voice, the default), **APRS**
  (a digipeater across its whole footprint), **Mailbox** (a Packet BBS in orbit: call it by name from the Packet BBS
  terminal page, leave a message with SP, and it is read on a later pass anywhere) and **SSTV** (sends its owner's
  picture every half minute or so). A commercial satellite is a repeater or a **weather imager** (below), and can be
  put in **stealth mode**, where only a tracking antenna locked on it hears it and is heard by it.
- Every satellite **relays distress** beacon alerts and DSC distress calls it hears, in every mode, even when switched
  off, and never holds them back.
- At night a passing satellite is a tiny dot that flashes every six seconds or so: green for amateur, red for
  commercial. Near dusk and dawn it glints grey now and then as it tumbles.
- **SATTRACK** (a floppy: blank floppy + phantom membrane, or from a contract) lists every satellite on the server and
  its next passes over the computer: rise time, highest point, direction, frequencies, tone, mode and decay date. While
  your satellite passes you can switch it on and off, change its Morse ID, tone and mode, allow other players to
  control it, upload an SSTV picture and decommission it. With a radio on the computer's station tuned to the
  telemetry frequency it decodes the beacon. Left open, it keeps tracking on the monitor.
- **Antennas for satellites**: any VHF antenna under open sky works, but a vertical (J-pole, Slim Jim, Ringo, a
  handheld's whip) has a null straight up: fine low in the pass, fading past about 40 degrees, deaf overhead. A
  handheld's own whip hears satellites more weakly below Y 128, rising to full strength by Y 224.
  - The **EB-VHF Eggbeater** is made for satellites: strong all pass long and best overhead.
  - The **Satellite Tracking Antenna** is a dish on a powered mount. Coax it to the radio and run a power wire to it:
    it turns and tilts after the satellite its radio is tuned to (or the highest one up) and hears it at full strength
    all pass long, even under an aurora or in a storm.
  - The **Handheld Yagi**: hold it in your offhand with a VHF, commercial or marine handheld in your main hand and point
    it at the satellite yourself. Within 10 degrees it hears almost as well as the tracking antenna; further off the
    signal falls away and the hiss rises. The handheld shows a cable to it.
- **Moonbounce**: at night, pick MOON in SATTRACK and press T, and the station's tracking antenna turns to the Moon.
  Send CW on 2m (the all-band radio) and about 2.5 seconds later your own echo comes back, faint and fading in and out.
  Every station with a tracker on the Moon hears it, anywhere in the world.
- A handheld's RX lamp and meter now read the FM carrier's strength, so a quiet carrier lights RX.

#### Weather pictures from space
- **WXSAT** (a floppy: blank floppy + lightning rod) receives the pictures a commercial satellite sends in imaging
  mode: radar over the ground in map colours, about 8 blocks a pixel and 2,048 blocks wide, sent down line by line
  with NOAA APT's tick-tock sound. A strong signal gives clean lines; low in the sky they get snowy and some go
  missing. Link a radio tuned to the downlink to the computer. F5 shows the live strip, F6 a mosaic stitched round the
  station (arrows pan, F8 zooms, drag to move, with a scale bar), F7 a loop of passes to watch storms move. F3 saves
  the view as an .IMG, F4 prints it. A geostationary imager sends whole frames of the ground round its spot over and
  over.
- WXSAT records every pass with nobody at the screen (SETUP's new **Services** page can turn that off). Every WXSAT
  computer on a LAN shares the same passes; Options > Block LAN sync keeps one to itself.
- **Hurricanes** show spiral bands: three arms winding out from the core with tails trailing past its edge and clear
  gaps between, curving the way it turns and turning slowly, about a turn a day. The eye and eyewall are as before.
- The **External CRT Monitor**: beside a WXSAT computer (or on its data wire) it shows the mosaic centred where it
  stands, about 1,000 blocks a tube, with a small red + at the station. Right click cycles LIVE MOSAIC, LOOP and OFF.
  Monitors facing the same way make one **video wall** up to 8 wide and 6 high. Touching a premium radio instead, it
  shows a zoomed **waterfall** round the radio's dial (NARROW or WIDE). With no pass yet it says WAITING FOR SATELLITE
  PASS.

#### Fire alarms and SCADA
- A radio fire alarm is three parts: **pull stations** in a building, wired to a **Radio Fire Alarm Box** on the wall,
  which radios each alarm to a **Fire Alarm Controller** at the fire station. Boxes relay each other's messages (up to
  8 hops by default, even in unloaded chunks), so one controller can watch a whole town. A box reaches about 110 blocks
  on its built-in antenna and about twice that with a VHF antenna on its coax.
- Rename a box in an anvil ("342 MAIN ST") and that is its location on the controller. Program it on its panel:
  frequency (any commercial one, not the marine channels), callsign, facility name, and a name and alarm type for each
  station (FIRE, THREAT, DISASTER, MEDICAL, ESCAPE, LOCKDOWN, FLOOD, BREACH or TORNADO). STATUS shows the route and
  signal. A box checks in once a day; one not heard for a day and a half is MISSING, and breaking one sends TAMPER.
- **Pull stations**: the red wall station, the **Outdoor Fire Alarm Pull Box** on its own post, the **Panic Button**
  (wall, floor or under a desk) and yellow and white ALERT versions. The **Fire Alarm Key** resets them and opens a box
  for TEST, RESET, SILENCE and DRILL. The **Fire Bell** rings on the wall behind a box in alarm, and a comparator on
  the box reads 15 until reset.
- The **Fire Alarm Controller** (an RC-704) buzzes, turns red and powers redstone on every side when an alarm comes in.
  Its terminal shows the event, box, location, zone, route and time since, and steps through the log. Load it with
  paper and it prints a **Fire Alarm Report** slip for each event (now and then the head jams). The **FIRE ALARM**
  program on a radio computer lists every box and the log, with a map.
- **SCADA** is redstone over the radio. A **SCADA RTU** reads redstone on some faces and puts it out on others; a
  **SCADA Master** (a two-block console) polls the RTUs one by one and runs your rules, written in a book and quill one
  a line: `IF 3.N >= 10 THEN 7.E = 15`, `COPY 1.N TO 0.U`, `POLL 2`. An RTU that misses three polls is COMM FAIL and
  the outputs it drives drop to 0. RTUs relay like fire alarm boxes. They need a little power. The **SCADA** program
  is the HMI: every unit's faces live, the rules and trends.
- Everything here has a recipe except the FIRE ALARM and SCADA floppies, which are in the creative menu (recipes in
  1.13.1).

#### Commercial licenses
- Commercial VHF (150-174 MHz) and low band (30-50 MHz) belong to licensed businesses. A **Commercial Frequency
  License** grants you one frequency and a WQ callsign (WQAB123) for 10 units. A journeyman cartographer sells blank
  VHF and low band licenses (16 emeralds and paper); right click one to claim a random free frequency.
- The marine and weather channels (156-162.6 MHz) and the five **MURS** channels (151.820, 151.880, 151.940, 154.570,
  154.600) never need a license.
- The license is a certificate for an item frame. Craft it with an empty map for a stamped **(Copy)**. An expert
  cartographer sells the **Commercial License Upgrade** (32 emeralds and a compass): the first adds a repeater input
  frequency and one base, each one after doubles the units and bases, up to 640 units and 32 bases. Bases are
  repeaters, SCADA masters and fire alarm controllers; a license used on a fire alarm or SCADA device sets its
  frequency and callsign.
- When the Radio Programmer writes a codeplug that transmits on a commercial frequency none of your licenses covers, a
  **License Check** asks you to confirm.
- The **FCC Monitor** program tunes a direction finder on the computer and tells you who is on a frequency and whether
  they may be: LICENSED and the holder, UNLICENSED (flashing, with a beep), AMATEUR BAND, CB, MARINE / WEATHER, MURS: NO
  LICENSE NEEDED or OUT OF BAND, with the bearing once the finder locks on. Its floppy is in the creative menu
  (recipe in 1.13.1).

#### Weather network
- The weather station is now the **Local Weather Station**. It has no radio of its own: anything that wants weather
  takes the nearest one within 64 blocks.
- The **Simple Weather Alert Generator** now works from **one station up**: one station gives warnings for what is at
  it, 150 blocks round it; two (150 blocks or more apart) add warnings for storms heading in, 5 minutes ahead; three or
  more add watches 15 minutes ahead and tropical statements, for the area round them all and 200 blocks past. Right
  click it for every station it hears, with distance and age, and why one does not count (too close, stale, or a
  callsign heard from two places: give each APRS station its own SSID).
- The **Weather Observer** sends a Local Weather Station's readings through any radio it touches: every 5 minutes and
  on a redstone pulse, two pips and a slow warble on the radio's frequency. A generator with a radio on the same
  frequency counts it as a station, told apart by place, so one callsign does for all your observers. Station,
  observer, radio and antenna make a weather outpost.
- The **Headless APRS TNC** is a packet modem with no screen, cheaper than an H19: it beacons its position (with a
  Local Weather Station's weather), digipeats with DIGI on, and keeps the beacons it hears for a generator touching it.
  Next to an **H19 only** it stands in for the Radio Interface; a radio computer, fax or anything else still needs a
  real Radio Interface. Its back panel has the callsign, SSID wheel and the DIGI and BEACON switches.
- The **Local Weather Station Display** is a wood-cased desk display whose red LCD steps through the nearest station's
  temperature, humidity, pressure, wind, gusts and rain. It needs no power. Right click for every reading at once.
- **Tropical advisories** from Another Weather Mod on the weather radio and WEFAX: a tropical weather statement with a
  disturbance's chance of becoming a hurricane, a hurricane or tropical storm watch a day or two out, then the
  warning, with seven new voice clips. WX REPT adds them to the forecast sheet and prints a tropical outlook chart.

#### CW keying
- The **Iambic Keyer**: place it beside an HF radio (or the all-band radio) in CW and hold A for dits and D for dahs
  (rebindable), or click its paddles. Iambic A and B, bug and straight key, 6 to 24 WPM, and a sidetone you can set.
  Craft it with a dye, glass, a metal or a gem to change its finger pieces.
- You now hear your own sidetone while you send CW.

#### Linear amplifier and coax loss
- The **Linear Amplifier** goes in the coax between an HF radio and its antenna, on a power wire. Use it to switch
  OPERATE: your signal arrives about half again as strong. It is louder, not further: the band still ends where it
  ends. It draws a lot of power while you transmit, doubles your RF in nearby redstone, blows easily into a bad SWR (a
  gold ingot mends it) and is illegal on CB.
- **Coax loss**: coax loses a little signal both ways, more the higher the band and the further the radio is from its
  antenna. On HF you will hardly notice; on 2m, 50 blocks of coax costs about a fifth of the signal. Keep VHF radios
  near their antennas.

#### LAN programs and the Computer Redstone Block
- Computers joined by data wire or a microwave link form a **LAN**, with three new built-in programs:
  - **RELAY**: a chat room. One computer is set as the relay server (F5) and keeps the room.
  - **EMAIL**: mail to `user@COMPUTER` on the LAN. A computer takes mail while someone is at it or with its background
    services on; otherwise it waits in the sender's outbox, and after three days comes back undelivered.
  - **REDSTONE**: sets **Computer Redstone Blocks** (up to 10 a computer, touching it or on its data wire): on, off or a
    pulse, from that computer or, if allowed, from others on the LAN. Name each block and its levels ("4 = Door Open").
    While off, a block reads the redstone coming in.

#### Airband
- The **airband**, 118-137 MHz, AM voice in 25 kHz channels: 121.5 is guard, 122.75 and 123.45 air-to-air.
- The **Airband Radio** is a panel-mount COM with USE and STBY windows: the knobs tune STBY and the flip-flop swaps
  them. It needs power and an antenna.
- The **Aircraft VHF Antenna** is a small swept whip, placed on top of a block or hung under one like an aircraft's
  belly. The **Airband Ground Plane Antenna** is a ground station's two-block mast that reaches a little further than a
  J-pole. A 2m antenna works on the airband at about 2.2:1.

#### FM broadcasting
- The **FM broadcast band**, 88.1-107.9 MHz, one station every 0.2 MHz. It is VHF, carried a little further than 2m.
- The **FM Broadcast Transmitter** is a two-block cabinet that goes out three times as strong as a radio. Hold ON AIR
  and talk, or use a desk microphone beside it; the program ducks under the mic, and the cabinet gives a redstone
  signal while the mic is keyed (for an ON AIR sign). A tape player on its data wire (or touching it) is its program.
  A weather alert receiver beside it or on its data wire hands it warnings, which it reads out once the mic is free.
  At breaks in the program it speaks a forecast every few minutes (with a Local Weather Station on its data wire, the
  conditions there too) and its station ID every in-game hour. With `fm_internet_streams` on, its owner can point it at
  an internet radio stream (MP3, Ogg, .m3u or .pls); if the stream drops, the tape stands in. It draws a lot of power
  on the air.
- The **FM Broadcast Receiver** is a walnut table radio: look at it and scroll to tune, or open it for POWER, MUTE, the
  dial, VOLUME, BASS and TREBLE. It hears on its own wire antenna or better on one coaxed to it, and runs from a power
  wire or a small battery for about two game days.
- The **FM Broadcast Antenna** is a side-mounted bay for a mast or tower: about 1.1:1 in the middle of the band and
  1.5:1 at its edges. A 2m antenna works, at about 2.8:1.
- The **tape player** has a **QUEUE** mode: tapes fed in from a hopper at the top or sides play one after another and
  go to an out tray a hopper below can empty. A comparator reads how full the queue is.

#### Sirens and public address
- With Another Weather Mod, its outdoor warning sirens can be sounded by anything here that gives redstone: a SCADA
  RTU face set to OUT, or a fire alarm box or controller in alarm.
- Run data wire from a commercial radio to an omnidirectional or horn array siren and the siren becomes the radio's
  **public address**: whatever the radio hears comes out of the siren head, narrow and loud, heard a long way off, with
  an echo off the land. Amateur radios cannot do this.

#### Vertical Antenna Base and wire isolators
- The **Vertical Antenna Base** is a stone pad with radials and a loading coil. Stack antenna poles on it (they taper
  as they go up), run coax into the base and right click it to move the coil's tap: 3 poles for 10m, 5 for 20m, 6 for
  40m, 7 for 80m. Right on, SWR 1.3; one pole off, about 2.5, which a tuner can fix. The coil costs a little on 40m
  and more on 80m. It replaces the old vertical on a 1:1 balun.
- Antenna poles no longer hold wire. The Antenna Connector is now the **Antenna Wire Isolator**: put it (or a balun) on
  top of a pole to hold a wire antenna up. Coax beside a pole runs up against it like a tower beam.

#### VHF memories and tuning step
- The **VHF base station** has memory channels like the all-band radio: hold MEMORY to store (pick the channel with the
  dial, press MEMORY), VFO/MEM to switch, hold VFO/MEM on a memory to copy it to the VFO. They keep the offset, tone and
  step.
- VHF base stations and handhelds have a **tuning step**: 5, 6.25, 10, 12.5, 15, 20 or 25 kHz, or the server's channel
  step.

#### The radio computer
- **Shift+Esc**, or Leave in any program's menu, steps away from the computer with the program still on the screen.
  Every program left on it keeps running on the monitor while you are within 18 blocks (games pause), SATTRACK and
  WXSAT included.
- Every program except SETUP has a clickable **menu bar** (File, Edit, View...) with all of its actions.
- SETUP has a **Services** page: whether DISTRESS and WXSAT keep listening, and the computer on, with nobody at it.
- The monitor goes **dark without power**. Idle on a power wire, a computer draws 1 FE a second as standby, so a flat
  battery shows.
- Plain paper in the scanner now takes a picture print as a sheet with the picture's details in its tooltip (DISTRESS
  maps, Awards certificates, WXSAT prints).

#### Band Plan on the wall
- Use the **Band Plan Sheet** on the side of a block to hang it on the wall, and right click it there to read it.
- Hovering a bar gives that part's range (14.000 - 14.150 MHz), what it is for and its category, and clicking a bar no
  longer opens JEI.

### Changed
- Another Weather Mod is now listed as an **optional dependency**, 1.13.0 or later. That version costs far less frame
  rate in storms and adds a Rain Detail setting. Weather alerts give a hurricane's category at the time, not its peak.
- CW is keyed by hand by default (`cw_keying` is now TRUE_MORSE): the screen's PTT button, the CW key or the iambic
  keyer. The CW key's default is now **Space** (it was Left Alt).
- Every commercial VHF radio, mobile or handheld, hears distress beacons on 161.975 MHz whatever channel it is on, and
  shows DISTRESS SOS RX with the coordinates until dismissed. Low band radios do not.
- Wire antennas from older worlds that were strung to poles drop their wire at the pole: put an Antenna Wire Isolator
  on the pole and string them again. Verticals built on a 1:1 balun no longer work: use the Vertical Antenna Base.
- Less server garbage: a transmission's audio is shared between the radios in range and copied only for those that
  use it (about 30% of the server thread's garbage with the commercial stations on the air). Wire block states share
  their tables, about 287 MB less memory without FerriteCore. CRT pictures repaint in place and audio is not copied for
  antennas out of reach.
- Cables: the wire and bundle outline follows the cable as drawn, so sneak + right click takes the strand you see.
  Bundle strands that must cross bridge over each other, floor cable climbs straight stairs step by step, and coax
  stops at an antenna that draws its own run in.
- QRP radio outlines fit their case.

### Fixed
- The QRP radios' tuning knob and skirt keep turning past the band edges along with the tuning (they stopped at the
  edge).
- The PC68 CB mobile's panel was stretched, so its painted knobs sat below the live ones.
- Jade's box no longer shows through RadioCraft screens.
- Cable up a stair step no longer z-fights with the cable on top, and runs that turn onto a floor no longer leave a
  gap.
- WXSAT: a pass pushed out by a new one folds into the next instead of leaving black ground, monitor loops skip blank
  and repeated passes, unloaded ground no longer shows cave biome colours, and the static on a live pass stays put.
- Security hardening: tapes and streams refuse headers with out-of-range sample rates or channel counts; SCADA rules,
  LAN mailboxes and printed pages have limits; handheld and manpack packets are checked; menu-less controls (keyer,
  fire alarm and SCADA setup) need the player within reach and not a spectator.

### For server operators
- **Existing worlds keep their old band ranges** in `serverconfig/radiocraft-server.toml`. To get the new defaults,
  delete the band sections' keys (or the whole file) and restart.
- New settings:
  - Propagation: `band_range_multiplier` (scales every band's line of sight, silent zone and skip window together: 0.5
    for half the range on a small map, 2 for twice as far), `terrain_radio_horizon`, `terrain_reference_height` (6
    blocks), `terrain_horizon_max_scale` (8), `skip_layer_height_km` (300), and under Skip `skip_max_hops` (3, 1 turns
    multi-hop off), `skip_hop_loss` (0.5) and `skip_multi_hop_bands` (10m, 20m, 40m).
  - Satellites: `satellite_decay_days` (90), `satellite_doppler` (off: heard a few kHz high as it rises and low as it
    sets) and `satellite_eclipses` (off: rarely silent in the Earth's shadow at night).
  - `satellite_tracker_power_use`, `amplifier_power_gain` (1.5), `amplifier_power_use`, `amplifier_standby_power_use`,
    `coax_loss` (1; 0 for lossless coax) and `scada_power_use`.
  - Fire Alarms and SCADA: `mesh_internal_antenna_range` and `mesh_hop_limit` (8).
  - `commercial_licensing`: VILLAGER (cartographers sell blank licenses, the default), ADMIN (only operators issue them
    with `/radiocraft license grant`) or OFF (no checks). Also `/radiocraft license info`, `revoke` and `list`.
  - FM Broadcast Streams: `fm_internet_streams` (off: when on, the server fetches whatever URL a station's owner
    enters), `fm_stream_allow_private_hosts` (off) and `fm_stream_max_streams` (8). A station opens its stream only
    while a radio in range is on and tuned near it.
  - `real_world_self_licence` (off): a player's Real-World Interface licence is now an operator's word, given with
    `/radiocraft realworld licence <player> grant|revoke`. `real_world_bridge_stations` (off): transmissions with no
    player behind them (FM stations, beacons, repeater IDs, tapes) stay in game.
  - `callsign_search_public` (off): `/callsign search` is now for operators only.
- Removed: `terrain_takeoff_10m`, `terrain_takeoff_20m`, `terrain_takeoff_40m` and `terrain_takeoff_80m` (each hop now
  works out its own angle from `skip_layer_height_km`).
- `/radiocraft satellite list|add|remove|decay|overhead|eclipse` manages satellites (operators). `/antenna` is now
  admin-only. An FM station or repeater placed with no owner belongs to the first player to set it up.
- `cw_keying` defaults to TRUE_MORSE in new configs; existing configs keep their value.
