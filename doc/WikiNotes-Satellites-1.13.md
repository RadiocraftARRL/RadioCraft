# Wiki notes: satellites (for the next wiki rebuild, 1.13)

Notes for rebuilding the website wiki (`tools/build_wiki.py` in `C:\Users\ldell\Documents\GitHub\Radiocraft-Website`)
after 1.13. Everything below is **Added in 1.13**. Design: `doc/SatellitePlan.md`.

## Handbook pages (new or changed)
Both entries are new in 1.13 (satellites first appeared on 2026-11-11 and were extended on 2026-11-12). The generator
picks them up from `entries/**.json` on its own; they use only `patchouli:text`, `patchouli:spotlight` and
`patchouli:crafting` pages, which it already renders.
- **Repeaters > Satellites** (`entries/repeaters/satellites.json`). Pages:
  - intro and Contracts (spotlight on `radiocraft:amateur_satellite_contract`);
  - Frequencies (now three: uplink, downlink and telemetry; a commercial satellite's license grants all three);
  - Passes and In the sky;
  - **Modes** (new): repeater, APRS, mailbox and SSTV on amateur satellites, one at a time;
  - **APRS** (new): an ISS-style digipeater across the footprint;
  - **Mailbox** (new): a Packet BBS in orbit, reached from the Packet BBS terminal page by the satellite's name;
  - **SSTV** (new): sends its owner's picture;
  - **Telemetry** (new): the beacon on its telemetry frequency;
  - **Doppler and eclipses** (new): the two server options;
  - Its life.
- **Computer > SATTRACK** (`entries/computer/sattrack.json`). Pages:
  - intro (now lists the three frequencies and the mode);
  - Controlling yours (F7, F3, F4, F5/F6);
  - **Its mode** (new): F8 next mode, F9 APRS frequency, F10 upload an .IMG for SSTV;
  - **Telemetry** (new): decoded frames, POWER SAVING, low battery, decay warning, Doppler shift;
  - the floppy recipe.
- They link to `computer/packet_bbs`, `computer/sstv` and `computer/distress`: check those links resolve.

## Items, frequencies, config options
- Items (new in 1.13): Amateur Satellite Contract, Commercial Satellite Contract, Satellite License, SATTRACK floppy
  (`floppy_sattrack`, which has a recipe). The contracts are sold by master cartographers (64 emeralds and a diamond)
  and have no recipe; the license comes only from a contract. Their added-in badges come from the lang as usual.
- Frequencies: amateur satellites use 2m only:
  - downlink 145.800-145.990 MHz;
  - uplink 600 kHz below the downlink;
  - telemetry also in 145.800-145.990, from the top down, so a server has room for 10 amateur satellites.
- Commercial satellites use a commercial VHF pair plus a telemetry channel, all three on one commercial frequency
  license. The license tooltip and picture show the telemetry line, and the FCC Monitor calls the beacon LICENSED.
- APRS mode defaults to the downlink; 144.390 is allowed but shown "NOT RECOMMENDED (terrestrial APRS)".
- New `TncPacket` kind: TELEMETRY (satellite telemetry frames).
- Server config, section **Satellites** (`serverconfig/radiocraft-server.toml`):
  - `satellite_decay_days` (90): real days before an orbit decays.
  - `satellite_doppler` (false): frequencies shift by up to about 3.4 kHz on 2m over a pass.
  - `satellite_eclipses` (false): a rare night eclipse on a flat battery. The repeater and mode go silent, the beacon
    sends a POWER SAVING frame a minute, and the battery shows low for a while after.

## Commands (operators, permission 2)
- `/radiocraft satellite list`
- `/radiocraft satellite add amateur|commercial [owner]`
- `/radiocraft satellite remove <name>`
- `/radiocraft satellite overhead <name> [minutes]`: plays a pass over you.
- `/radiocraft satellite eclipse <name> [minutes]`: forces an eclipse (default 3 minutes).

## What the generator might need
- Nothing new structurally: no new page types and no new categories.
- If the wiki has hand-written sections for config options or commands (they are written into `build_wiki.py`, e.g. the
  bands section), add the Satellites config section and the satellite commands above there.
- Added-in badges come from release tags. 1.12.0 was only tagged locally; with no 1.12.0 tag where the generator runs,
  satellite items would be badged 1.12 instead of 1.13. Check that the tag exists before building.
- The contracts and license are placeholder art from `tools/satellite_items.py`: re-render the icons if the art
  changes.

## Weather imaging, WXSAT and the External CRT Monitor (added 2026-11-12)
Everything here is **Added in 1.13**. Design: `doc/SatellitePlan.md`, "Weather imaging".
- **Handbook pages**:
  - **Repeaters > Satellites**: new page **Weather imaging** (commercial satellites can image; amateur ones cannot); the
    Modes page now says a commercial satellite is a repeater or a weather imager.
  - **Computer > SATTRACK**: the "Its mode" page says F8 switches a commercial satellite between repeater and imaging.
  - **Computer > WXSAT** (`entries/computer/wxsat.json`, new): spotlight on `radiocraft:floppy_wxsat`, then The
    station, What you see, Live/mosaic/loop, Saving and printing. Links to `computer/external_crt_monitor`,
    `computer/scanner` and `repeaters/satellites`.
  - **Computer > External CRT Monitor** (`entries/computer/external_crt_monitor.json`, new): spotlight on
    `radiocraft:external_crt_monitor`, then Weather pictures, A video wall, A radio's waterfall, How wide, and its
    recipe. (Renamed from the Weather Display on 2026-11-12, before release: id `external_crt_monitor`.)
  - WXSAT ends with its floppy's recipe; Computer > Radio Computer's SETUP page lists the new Services page.
- **New block**: External CRT Monitor (`radiocraft:external_crt_monitor`), a vintage CRT. Recipe: iron ingots round a
  glass pane (III / IGI), wire, copper ingot, wire underneath (WCW). Two uses: a WXSAT computer's mosaic (one
  monitor or a wall of them), or, a single monitor touching a premium radio (Pure crystal: the premium HF rigs and the
  premium VHF base station) with no WXSAT computer, a waterfall round the dial: NARROW 6 kHz / WIDE 24 kHz on HF and
  CB, 50 kHz / 200 kHz on VHF. Right-click: LIVE MOSAIC, LOOP, OFF on a computer; NARROW, WIDE, OFF on a radio.
- **New program**: WXSAT, the "Weather Satellite" floppy (`radiocraft:floppy_wxsat`): a blank floppy and a lightning
  rod, shapeless. A background service: it keeps the computer on (and drawing power) while installed.
- **New SETUP page**: Services, "Background services: On/Off" (default On, saved with the computer). Off: DISTRESS and
  WXSAT do not keep the computer on and listen only while their program is open (a warning says so first; an outgoing
  personal SOS keeps the computer on and repeating until it is ended).
- **New mode**: IMAGING, commercial satellites only (SATTRACK F8 while passing).
- **New `TncPacket` kind**: APT (a weather satellite's scan line).
- Numbers for the wiki (swath widened 2026-11-14): 16 blocks a pixel, 512 pixels, an 8,192-block swath, a line every
  16 blocks of track; the mosaic keeps 8 passes, 256 tiles of 2,048 blocks, within about 7,000 blocks of the station,
  up to 5 Minecraft days; a display shows 2,048 blocks of ground; screens up to 8 x 6 displays; LOOP shows the last 6 passes at 1 s a frame.
- No new config options or commands. `/radiocraft satellite overhead <name>` plays a pass for testing imaging.
- **New block (2026-11-12)**: EB-VHF Eggbeater Antenna (`radiocraft:eggbeater_antenna`), two blocks tall, on coax; no
  recipe yet (to be approved). The satellite antenna: strong all pass, best overhead. Along the ground it carries a little
  less far than a J-pole (range /1.1 against the J-pole's /1.3).
- **Antennas and satellites**: verticals (J-pole, slim jim, ringo, folded dipole, quarter-wave and 5BTV verticals, CB
  antennas, handheld and manpack whips, the lightning rod) match the plain strength curve up to 40 degrees, then fade
  to a fifth of it straight overhead; within about 5 degrees of overhead the satellite cannot hear their uplink (voice,
  APRS, mailbox). Distress beacons are heard from any angle. Other antennas (yagi, dish, wire) keep the plain curve.

## Transit delay, geostationary satellites, decommissioning, burnout (added 2026-11-12)
Everything here is **Added in 1.13**. Design: `doc/SatellitePlan.md`, "Transit delay, geostationary satellites,
decommissioning, burnout".
- **Handbook pages**:
  - **Repeaters > Satellites**: new pages **The delay** (voice about 300 ms, a digipeat about 2 s, a mailbox answer about
    4 s, distress at once), **Geostationary** (spotlight on `radiocraft:amateur_geostationary_contract`) and
    **Decommissioning**; **Its life** now gives 45 days for a geostationary one and mentions the burnout streak.
  - **Computer > SATTRACK**: new page **Geostationary and DEAD** (GEO, always/never up, DEL to decommission).
- **New items**: Amateur Geostationary Satellite Contract (`radiocraft:amateur_geostationary_contract`) and Commercial
  Geostationary Satellite Contract (`radiocraft:commercial_geostationary_contract`). Sold by master cartographers for
  64 emeralds and a block of diamonds; no recipe. Placeholder icons reuse the contract textures (art is a bounty).
- Numbers for the wiki: voice delay 300 ms (150 ms geostationary); digipeat 2 s (1 s); mailbox answer 4 s (2 s);
  distress relay 0. A geostationary satellite is up within 5,000 blocks of where its contract was used, parks 150 to
  750 blocks from there (about 50 to 80 degrees up there), sends weather frames of an 8,192-block square round that spot (512 rows north to south, an end-of-frame, a short pause, again: WXSAT shows each new frame replacing the last row by row and loops them),
  decays in half the configured days (45 by default) and flashes every 18 s (others every 6 s). It shares the frequency
  pool, so a server still has room for 10 amateur satellites in all.
- **SATTRACK**: GEO marker, "always up here" / "never up from here" (NEVER UP in the list), no passes for a
  geostationary satellite; **DEL** decommissions (owner only, while it passes, a Yes/No then a final warning); DEAD in
  the list afterwards.
- **Decommissioned**: silent (no repeater, mode, telemetry, distress relay or imaging), off the repeater directory, its
  frequencies free again (a commercial one's frequency license revoked); still tracked until its orbit decays; in the
  sky only an irregular faint greyish-white glint near dusk and dawn.
- **Burnout**: when an orbit decays, players it is above see a 1-pixel streak for 3 seconds at night.
- **Commands**: `/radiocraft satellite add geostationary amateur|commercial [owner]`; `/radiocraft satellite decay <name>`
  (its orbit decays now, with the burnout); `list` shows GEO and DECOMMISSIONED.
- No new config options.

## Satellite tracking antenna and stealth mode (2026-11-12)

- **New block**: Satellite Tracking Antenna (`radiocraft:satellite_tracker`). Recipe: ` D ` / `OCW` / `III`
  (microwave dish, coax, comparator, wire, iron ingots). Coax to a VHF radio, power wire to its mount; draws
  `satellite_tracker_power_use` (default 8) a tick while it tracks or slews, nothing stowed.
- Follows the satellite its radio is tuned to (within 15 kHz of its downlink or uplink), else the highest one up; slews
  3 degrees a tick and locks within 2.5 degrees. Locked: strength 1.0 all pass long, uplink always heard.
- **Space weather (new for every other antenna)**: under an aurora a satellite's signal is x0.55, in a radio blackout or
  thunderstorm x0.75 (both: x0.41). Low in a pass that drops it below the uplink floor (0.3). A locked tracker ignores it.
- On the ground it is a poor antenna (distance x1.6).
- **Stealth mode** (commercial satellites): SATTRACK `S`, owner or allowed players, while it passes, and to turn it on a
  tracker on the station's coax must be locked on it. In stealth only locked trackers hear it and are heard by it
  (distress relays still reach everyone). Strangers' SATTRACK shows only its name, that it is in stealth, and where it is.

## Moonbounce (EME), keying and the iambic keyer (2026-11-12)

- **Moonbounce**: SATTRACK lists MOON; `T` on it (or any satellite) points the station's tracking antennas at it (`T`
  again: automatic). CW on a VHF band (2m on the all-band radio) through a tracker locked on the Moon comes back after
  2.5 s (50 ticks) to every tracker locked on the Moon, the sender's included. Strength 0.015 to 0.16, fading over
  about 45 s and 11 s cycles that beat together, different each night; aurora x0.55, blackout or thunderstorm x0.75. Needs the
  Moon at least 5 degrees up. Minecraft's Moon is the same for everyone (east at dusk, overhead at midnight, west at
  dawn), so any two Moon stations in the world can work each other all night. CW only.
- **CW keying default**: `cw_keying` is now TRUE_MORSE (the screen's PTT button, or the CW key: now Space). CHAT_MORSE
  adds the typed-Morse box back. While keying, the radio plays its sidetone and mutes its static (others nearby hear the
  sidetone too).
- **Iambic Keyer** (`radiocraft:iambic_keyer`): beside an HF radio; A dit, D dah (rebindable); IAMBIC A, IAMBIC B, BUG,
  STRAIGHT; 6, 8, 12, 24 WPM; sidetone off/low/med/high. Recipe: `B B` / `IRI` / `SSS` (any buttons, iron,
  redstone, smooth stone slabs). Finger-piece finishes, crafted with the keyer: honeycomb (amber, default), glass pane
  (clear), any dye, iron, gold, copper or netherite ingot, diamond, emerald, lapis, amethyst shard, quartz.

## Station, alarms and the LAN (2026-11-13)

- **Linear amplifier** (`radiocraft:linear_amplifier`): coax between an HF radio and its antenna, on power. OPERATE /
  STANDBY by using it. In OPERATE, powered and not blown, HF and CB go out `amplifier_power_gain` (1.5) times stronger,
  capped at full scale (louder, not further); VHF passes unamplified. Draws `amplifier_power_use` (100) a tick while
  transmitting, `amplifier_standby_power_use` (2) idle. Twice a radio's blowout chance into a bad SWR; a gold ingot
  mends it. Doubles the transmit RF reach into redstone. CB through it is illegal: the FCC program, hearing
  that station on a direction finder, shows CB AMPLIFIED: ILLEGAL. Recipe (approved 2026-11-13): `GGG` / `BFB` / `ICI`
  (gold ingots, all-band circuit boards, ferrite core, iron ingots, coax).
- **Coax loss** (`coax_loss`, 1): per 100 blocks between the radio and the antenna, 0.25 dB on 80m, 0.35 on 40m, 0.5 on
  20m, 0.75 on 10m and CB, 1 on low band, 2 on VHF; both ways. No hardline (the loss is light).
- **Hitboxes**: the ionosonde and the 5-BTV are solid to walk into.
- **FIRE ALARM box map** (F4) and **SCADA trends** (F4, the last 120 readings per face). **SCADA power**: RTUs and
  masters draw `scada_power_use` (2) from a power wire; dark, they neither poll nor answer.
- **Computer Redstone Block** (`radiocraft:computer_redstone`) and **LAN REDSTONE** (built in): up to 10 per computer;
  named levels; set on, off, pulse or any level; reads its input while off. Other computers on the LAN may set it when
  their player or computer name is on its lists; the computer's owner (and ops) always.
- **LAN RELAY** (built in): a chat room kept by a computer set as a relay server (needs a SETUP name and background
  services on); the LAN's rooms to pick from, who is in the room, 200 lines of history on the server.
