# Satellites plan (2026-11-11)

Agreed with the maintainer on 2026-11-11. Supersedes the "Satellites" and "Sputnik-style radio satellite" lines in
`todo.md` where they differ (no launching for now; commanding is from a computer program, not the microwave link).

## Getting one up: contracts, no launch
- Two items: **Amateur Satellite Contract** and **Commercial Satellite Contract**. Sold by a **master cartographer**
  for **64 emeralds and one diamond** (very limited stock). No recipe. No rocket, no pad (maybe later).
- Right click a contract: it picks **three frequencies** (uplink, downlink and, since 2026-11-12, telemetry) that no
  other satellite on the server uses, registers the satellite as launched today with the player as owner, and gives the player:
  - a standard frequency license covering both frequencies (the commercial license item the mod already has), and
  - a **satellite license**: the satellite's name, its three frequencies and its launch day.
- Amateur pairs: both ends on 2m (the mod has no 70cm): downlink 145.800-145.990, uplink 600 kHz below. Commercial: a
  commercial VHF pair. Telemetry: amateur in the 145.800-145.990 sub-band (from the top down), commercial on a
  commercial VHF channel that the satellite's commercial license grants as a third frequency (maintainer, 2026-11-12:
  so no license is ever issued on it and the FCC Monitor calls its beacon LICENSED; licenses from before get it on
  load, and if another license took the channel meanwhile the satellite moves its telemetry to a free one). Downlinks and telemetry share the 20 sub-band channels, so a server has room for 10 amateur
  satellites (it had 20 before telemetry).
- An admin command adds, removes and lists satellites (for testing and moderation).

## On orbit
- A satellite is a server-side record (saved data): owner, allowed players, kind (amateur/commercial), frequencies,
  required CTCSS tone, ID text, on/off, launch time, and its orbit (period, inclination, phase). Its position is
  arithmetic from the world time: no entity, nothing to keep loaded. Overworld only.
- **4 to 6 passes per Minecraft day** over any given place, at different times in different parts of the world. A
  pass rises at one horizon, arcs over to some maximum elevation and sets, over a few minutes.
- **Coverage:** while passing, it is heard anywhere within about **10,000 blocks** of the point under it, so two
  players far apart can use it at the same moment.
- **Decay:** the orbit decays after a long time: **90 real days** from launch by default (server config). The
  tracking program warns as it gets close; then the satellite is gone (its licenses stay as keepsakes).
- No limit on satellites per player.

## In the sky
- While a satellite is passing over a player, all night: a tiny dot, **green for amateur, red for commercial**, that
  **flashes about every 6 seconds at varied brightness**. Near dusk and dawn a **grey flash** now and then (the
  satellite tumbling in sunlight). Nothing while it is below the horizon, nothing by day. Every player sees every
  satellite.

## What it does
- **Repeater:** amateur and commercial alike, an FM repeater: what it hears on its uplink **with the required tone**
  it sends out on its downlink, everywhere in its coverage. Without the tone it ignores the signal.
- **Distress relay (every satellite):** repeats distress beacon alerts (161.975 MHz) and DSC distress calls (channel
  70) it hears, so they are heard anywhere in its coverage with open sky. `distress.DistressAlert` is built for it
  (whole in itself, `encode()` re-sent unchanged, receivers merge copies by id). A relayed alert is not relayed
  again.
- Only while passing, and only with open sky at both ends (the sky check the distress relay needs).

## Controlling it: SATTRACK, a radio computer program
- Lists every satellite and its next passes (rise time, maximum elevation, direction it rises from), and the decay
  date. Anyone can see the list and use any satellite on the air.
- **Only the owner and the players on its allowed list** can change it, and **only while it is passing** over the
  computer: on/off, ID text, required tone, the allowed list.
- No antenna needed at the computer.

## Modes, telemetry, Doppler and eclipses (maintainer, 2026-11-12)
- **One mode at a time** on an amateur satellite (`SatelliteMode`), switched in SATTRACK (F8) by the owner or allowed
  players while it passes, saved with the satellite: **REPEATER** (the default, as above), **APRS**, **MAILBOX**,
  **SSTV**. A commercial satellite is a repeater or (2026-11-12, see "Weather imaging") a weather imager; SATTRACK
  refuses the amateur modes on it (`sat_no_modes`). The distress relay works in
  every mode, on or off, even eclipsed (a safety-of-life relay; the maintainer's list for eclipses named the repeater,
  the mode and the beacon).
- **APRS**: a digipeater like the ISS, simplex on its APRS frequency: the downlink unless the owner sets another 2m
  frequency (F9; 144.390 is allowed and shown "NOT RECOMMENDED (terrestrial APRS)"). An APRS beacon, message or ack
  heard there from under it goes out once (by packet id), one hop on, to every station in its footprint, a second
  later (the sender's radio is deaf while it finishes sending). It respects the TNC digipeat hop limit.
- **MAILBOX** (PACSAT style): a Packet BBS on board (`PacketBbs`, the same command set as the computer's BBS, kept to
  40 messages, saved in `radiocraft_satellites`). Stations reach it from the Packet BBS program's terminal page by
  calling the satellite's name (RO-3) on its uplink or downlink; answers come down on the downlink. Upload with SP on one
  pass, list and read anywhere on a later pass. QSL card downloads (D n) are not offered on orbit.
- **SSTV**: the owner uploads an .IMG from the radio computer (F10) while it passes. Every 30 s or so (after the last
  picture ends) while any station is under it, it sends the picture on its downlink as the SSTV program does: a file
  header, the SSTV tones (with its tone), then the file, so the SSTV program receives it, keeps it and prints it.
- **Telemetry beacon** (every satellite, always on, any mode, on or off): a data frame (`TncPacket.Kind.TELEMETRY`,
  `SatelliteTelemetry`) every 10 s on its telemetry frequency with an AFSK burst, and its name in Morse (MCW, as the
  repeater IDs) every minute. Values: battery volts and current, temperature, sunlight or shadow, mode (OFF when
  switched off, SAFE with a low battery), real days to decay. They follow the world's time of day (charging and warm by
  day, draining and cold at night, a tumble ripple). SATTRACK decodes the frames a radio on the computer's station bus
  hears while the computer is on (someone at it or a background program; approved 2026-11-12), shows the latest with its age, flags a low
  battery and warns the owner in the last week before decay. Satellites saved before this get a telemetry channel on
  load: free if one is, the same one each load (seeded by the name).
- **Doppler** (server option `satellite_doppler`, off): each station hears a satellite's downlink, APRS, mailbox,
  SSTV and telemetry signals shifted by `SatelliteDoppler` (range rate over the round Earth, scaled to 7.5 km/s): about
  +3.4 kHz at rise to -3.4 kHz at set on 2m, through zero at closest approach. FM receivers hear 2 kHz off, so a
  station retunes during a pass. The satellite hears uplinks shifted the same way (its uplink match is 6.25 kHz wide).
  Distress relays are not shifted. SATTRACK shows the downlink's shift while it passes.
- **Eclipses** (server option `satellite_eclipses`, off): time is cut into slots of about one revolution (4,000 ticks);
  a slot at night has a 1 in 20 chance, fixed by the satellite and the slot (`SatelliteEclipse`), of finding it in the
  Earth's shadow on a flat battery: repeater and mode silent until the slot ends or the sun rises; the beacon only sends
  a power-saving frame (battery flat, SAFE, `PWR=SAVING`) every minute and no Morse name (maintainer, 2026-11-12). For
  three slots after, telemetry shows the battery low and the mode SAFE. `/radiocraft satellite eclipse <name> [minutes]`
  forces one for testing. SATTRACK shows ECLIPSE while it is up and silent.

## Weather imaging (maintainer, 2026-11-12)
- **IMAGING mode, commercial satellites only** (`SatelliteMode.IMAGING`): a commercial satellite is a REPEATER (the
  default) or an IMAGING weather satellite, one at a time, switched with SATTRACK's F8 by the owner or allowed players
  while it passes. Amateur satellites refuse it (`sat_no_imaging`). The distress relay and the telemetry beacon work in
  both modes; an eclipse silences imaging like any mode.
- **Capture, like NOAA APT** (`imaging.AptSwath`, `AptPainter`, `SatelliteRadio.imaging`): while any station is under
  it, it scans a line across its ground track every 16 blocks the point under it moves (one every 4 ticks):
  512 pixels of 16 blocks, a swath 8,192 blocks wide (widened 2026-11-14 from 256 pixels of 8 blocks, with the fewer,
  shorter passes: it sees most of the ground it is up over), so a full pass makes a strip of about 600 lines. Each line goes
  down its downlink (Doppler shifted per station when Doppler is on) as a `TncPacket.Kind.APT` packet whose text is the
  line's geometry (sequence, centre, right vector); the pixels stay on the server (`AptLines`, by packet id, a few
  seconds). Each copy of the satellite (they are 24,000 blocks apart) scans its own ground. The downlink also sounds
  like APT (`AptAudio`): a 2,400 Hz tone following the picture's brightness with the sync A/B "tick-tock" twice a
  second.
- **The picture** (`AptPainter`): full-colour radar over terrain in map colours. Loaded chunks: the top block's map
  colour with map slope shading; unloaded: the biome's colour from the biome source (nothing generated), as WEFAX finds
  coastlines. Terrain is always a step darker than a map (never `Brightness.HIGH`) so weather can be read back
  (`RadarScale.levelOf`). Weather: `WeatherSource.rainAt` (AWM storms, or vanilla rain broken into drifting cells so it
  reads as radar), none over deserts/badlands/savanna, snow in cold biomes (blues), magenta in a severe thunderstorm's
  or supercell's core and purple for hail (from the source's storm list). Radar scale (`RadarScale`): light green,
  green, dark green, yellow, orange, red, dark red, magenta, purple; snow: ice, light blue, blue.
- **Reception**: a radio linked to a radio computer with WXSAT (touching it, on its data wire, or beside its scanner)
  decodes a line when tuned to it (`RadioBlockEntity.receiveData`): down to 0.4 of its copy threshold, adding how far
  above it was. WXSAT degrades the line by that (`AptLine.degrade`): clean from 1.5, snowier below, lines lost now and
  then under 1 and always under 0.4; lines that never arrive show as gaps (by sequence number). No radio, or the wrong
  frequency: nothing.
- **WXSAT** (`WxSatHandler`, `WxSatApp`): a **background service** like DISTRESS (it keeps the computer on and draws
  power while installed), because passes come several times a Minecraft day at unpredictable times, last up to three
  minutes, and the External CRT Monitor has to update with nobody at the screen. Live view (F5, the strip filling in with
  the signal and next imaging passes), mosaic (F6, pan with arrows, F8 zoom 8/16/32/64 blocks a pixel, Home, a cross on
  the station), loop (F7, pass by pass a second each, Space, PgUp/PgDn), F3 save the view as a 128 x 128 .IMG, F4 print
  it through the scanner (map or photo paper: the picture; plain paper: area, pass and time, and a precipitation
  summary). Menus on F10.
- **Mosaic** (`WxMosaic`, saved per computer in `data/radiocraft_wxsat_<computer id>.dat`): world-anchored tiles of
  128 x 128 px (1,024 blocks); each pass is a layer, composited oldest first so the newest is on top and older passes
  show through gaps. Kept within 3 tiles of the station's tile (about 3,500 blocks), at most 8 passes, 128 tiles in all
  (2 MB raw), none older than 5 Minecraft days. A new pass begins after 400 ticks without lines or from another
  satellite.
- **External CRT Monitor** (`external_crt_monitor`, the maintainer's model): horizontal facing; links to a computer with WXSAT
  beside it (or on its data wire) or through any chain of displays; same-facing displays edge to edge form screens
  (`CrtMonitorGroups`: bottom-left first, the largest rectangle up to 8 x 6) and the picture spans each screen's
  whole front including the bezels, each tube showing the part behind its glass. 1,024 blocks of ground per display,
  centred on the screen. Right-click cycles LIVE MOSAIC, LOOP (last 6 passes, 1 s a frame) and OFF. No power of its own:
  it shows while the computer is on. The screen's master sends the picture to nearby players (deflated, at most every
  2 s while lines come in); the renderer draws it with linear filtering and cheap CRT faults (scanlines, vignette,
  glow, convergence, flicker, hum bar, noise, power on/off).
  **Renamed and extended 2026-11-12** (was the Weather Display): a single monitor (not in a bigger screen) with no
  WXSAT computer, touching a premium radio (Pure crystal), shows a waterfall round the dial from the radio's second
  band scope (`RadioBlockEntity#monitorScope`, so rig control's own scope is untouched): `Waterfall.monitorSpan`,
  NARROW 6 kHz / WIDE 24 kHz below 30 MHz, 50 / 200 kHz above. A row every 10 ticks (`CCrtWaterfallPacket`, 76 level
  bytes), sent only when the row or span changed and only to players tracking the chunk; the client repeats the last
  row meanwhile (`CrtMonitorWaterfalls`). A computer wins over a radio. Right-click: NARROW, WIDE, OFF.
- **Background services switch** (2026-11-12): SETUP > Services, per computer (`ComputerSettings.backgroundServices`,
  default on). Off, DISTRESS and WXSAT (`ComputerApps.isBackgroundService`) do not keep the computer in use, and tick
  and hear packets only while their program asked for something in the last 3 s (`RadioComputerBlockEntity#serviceRunning`);
  `ComputerAppHandler#serviceStopped` lets DISTRESS stop an outgoing personal SOS. A monitor on such a computer goes
  dark with nobody at the computer.

## Transit delay, geostationary satellites, decommissioning, burnout (maintainer, 2026-11-12)
- **Transit delay** (`SatelliteDelay`): what a satellite hears goes back out later. Voice **300 ms** (an echo you can
  still talk through): `SatelliteRadio` holds each frame in order with its due time and sends it from the voice thread
  as later frames arrive and from the server tick for the tail of an over, so the stream is neither dropped nor
  reordered; switching it off or to another mode, or an eclipse, drops what it held. An APRS digipeat **2 s** (40
  ticks), a mailbox answer **4 s** (80 ticks, then 8 ticks between lines as before). A **distress relay is never
  held**. Self-generated signals (ID, telemetry, SSTV, imaging) are not delayed. The Morse ID waits until the held
  voice is out.
- **Geostationary contracts**: `amateur_geostationary_contract` and `commercial_geostationary_contract`, sold by the
  same master cartographers for **64 emeralds and a block of diamonds** (the orbiting ones: 64 emeralds and one
  diamond), one per restock. Both kinds: real amateur (QO-100) and commercial (weather, TV) birds are geostationary.
  Item art is a bounty (they reuse the contract textures). `/radiocraft satellite add geostationary amateur|commercial
  [owner]` for testing.
  - It **stays put** (`SatelliteGeo`). Its coverage (10,000 blocks, as the others) is centred on **where its owner
    used the contract**; that is also where it images. It hangs over a point 150 to 750 blocks (halved 2026-11-14 with the coverage) from there in a random
    direction, so two bought in one place sit apart in the sky (about 50 to 80 degrees up where it points); the
    elevation is scaled so it is above the horizon everywhere in its coverage, and it is never up anywhere else.
  - **Same features** as an orbiting one of its kind (modes, telemetry, distress relay, Doppler is always zero). A
    geostationary imager sends **frames** (2026-11-14, as GOES full-disc pictures): a square grid one swath
    (8,192 blocks) across centred where it points, row by row north to south at the normal line rate (512 rows, about
    2,000 ticks), then an end-of-frame signal (a line with no pixels, row = rows; `AptLine.Geometry` carries
    `g<frame>,<row>,<rows>`), a 100-tick pause, and the next frame from the top, the weather read afresh each row.
    WXSAT lays each frame as a new layer over the last, so while one comes in the new rows replace the old frame top to
    bottom; the end of the frame marks it complete ("frame N, row r of R" in the status). The loop and the CRT monitor's
    LOOP step through frames as they do passes.
  - Its orbit **decays twice as fast** (45 days with the default 90; `SatelliteRules.GEO_DECAY_RATE`); **half the
    transit delay** (voice 150 ms, digipeat 1 s, mailbox 2 s; no data reply sooner than 1 s, the sender's radio is
    deaf until then) - the maintainer's call, "since they're lower".
  - It takes frequencies from the **same pool** and a name from the same count (RO-n / CS-n): there is no other cap
    on satellites, so it counts toward the same limit (10 amateur satellites per server).
  - In the sky it hangs still and **flashes a third as often** (every 18 s instead of 6).
  - SATTRACK marks it GEO; it is "always up here, el X to the Y" or "never up from here" (NEVER UP in the list), and
    has no passes list.
- **Decommissioning**: in SATTRACK, **DEL** on a satellite of yours while it passes (the owner only, not the allowed
  list). A Yes/No question, then a FINAL WARNING whose buttons are No / Yes (No first, so Enter alone cancels). Then
  it sends nothing (no repeating, beacon, telemetry, distress relay, imaging, mailbox), leaves the repeater directory,
  and its frequencies go back to the pool (a commercial one's frequency license is revoked). It stays on orbit, saved,
  and listed in SATTRACK as DEAD with its days to decay and where it is; it takes no more commands
  (`sat_decommissioned`). In the sky its flashing stops: only a faint irregular grey glint now and then near dusk and dawn as it tumbles.
- **Burnout** (`SatelliteBurnout`): when any satellite's orbit decays (working or decommissioned), each player in the
  overworld it is above the horizon of at that moment gets `CSatelliteBurnoutPacket` (where it is, its heading, the
  start time: 20 bytes); the sky shows a one-pixel orange-white streak with a short fainter trail, running 3,000 blocks
  along its heading and sinking to 40% of its elevation, flaring and fading over **3 seconds**. Like the satellites
  themselves, only at night and not in rain. An admin's `remove` shows no burnout; `/radiocraft satellite decay <name>`
  brings one down now the way a decay does, burnout and all, for testing.

## Later (not now)
- Decided 2026-11-13 (maintainer): satellites come by contract only. No launching or building them, no 70cm band,
  and Doppler stays as it is (a server option, off).
- Beacon-only (Sputnik) satellites.
- Structure locating from a satellite (on todo.md). Mapping explored areas into JourneyMap / Xaero's is in
  `doc/ModIntegrationPlans.md`.

## Tracking antenna, space weather and stealth (2026-11-12)

The Satellite Tracking Antenna (`SatelliteTrackerBlock`, GeckoLib `satellite_tracker.geo.json` from
`tools/satellite_tracker_model.py`: the microwave dish's arm and dish on a plinth and turntable) is an antenna on the coax
and, through the new `PowerMember` interface, a member of a power wire's network. `SatelliteTrackerNetworkObject` picks a
satellite every 10 ticks, slews the mount (`SatelliteTracking`) and records the lock in a thread-safe map that
`SatelliteRadio.pattern(antenna, satellite)` reads on the voice thread: locked is `SatellitePattern.TRACKER`. Every other
pattern now fades under an aurora over the station or in a blackout or thunderstorm (`SatellitePattern.weather`).
Stealth (`Satellite.stealth`, commercial only, saved) limits `reaches` and `uplinks` to locked trackers, except distress
relays; SATTRACK's status sends a stranger only the name, `hidden`, `geo`, `dead` and the look and passes.
