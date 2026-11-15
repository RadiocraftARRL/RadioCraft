# Done

Finished items moved out of [todo.md](../todo.md) on 2026-11-15, one line each. Details are in the release notes,
the Handbook and the code; the dates are when they were built.

## Ongoing and plan steps

- [x] Flaky GameTests fixed (2026-10-30): `GameTestDefaults.calmSky`, three-line weak RTTY test, own batch for the weak
  fax test, H19 boot test waits for both flags
- [x] Plan step 1: two HF radios talk, voice and CW, `TransmissionGameTests` (2026-10-19)
- [x] Plan step 2: solar weather shapes propagation, blackouts, aurorae, teletype report (2026-10-21); 10m skip (2026-10-23)
- [x] Plan step 3: static masking and squelch on the client, radio-shaped voice (2026-10-21)
- [x] Plan step 4: radios draw power and brown out, `radios_need_power`, repeater and handheld drain, charge controller switch (2026-10-21..24)
- [x] Plan step 5: VHF FM hiss model, `ReceiverMix.fmHiss` (2026-10-24)

## Radios & bands

- [x] Core HF radios (10/20/40/80m) and recipes
- [x] SWR and propagation hooks: SWR per antenna type, skip, solar weather (2026-10)
- [x] HF receiver, QRP radios, all-band radio shapeless combinations
- [x] VHF handhelds work
- [x] Hand mic on a cord, sneak-right-click a radio (2026-10-24)
- [x] Comparator S-meter and Jade reading (2026-10-20)
- [x] Callsign capability, `/callsign` tools and admin commands (`set`, `reset`, `generate`)
- [x] Self-chosen ham callsigns, repeater X#XX callsigns, `repeater_callsign_mode` (2026-10-24)
- [x] Operator moderation `/radio moderateall`, `/radio moderate <player>` (2026-10-24)
- [x] Optional voice recording, `moderation_record_radio` (2026-11-04)
- [x] Redstone interference, receive side (2026-10-20) and transmit side (2026-10-20), doc/RedstoneInterference.md
- [x] Lightning static spikes on powered radios (2026-10-20)
- [x] SWR consequences: blown radios, poor-crystal explosions, bare connector works short range (2026-10-21..23)
- [x] Solar event noise and day/night skip via `SolarEventManager` (2026-10-21)
- [x] Propagation scaling: `BandUtils.maxReach` skip, terrain cached per pair (2026-10-24)
- [x] Terrain-aware propagation, saved terrain atlas, `/bandcalculate` counts terrain (2026-10-24)
- [x] Terrain phase 3: worldgen fill for unread chunks (2026-11-07)
- [x] `/bandcalculate <from> <to>` (2026-10-23)
- [x] 10m skip, one long hop (2026-10-23)
- [x] Sporadic E (2026-10-24)
- [x] Blackout keeps local contacts at half range (2026-10-31); blackout flutter on LOS HF voice (2026-10-31)
- [x] `/solarweather` works again (2026-10-31)
- [x] Gain and mic gain dials, CW key, Chat Morse (2026-10-24)
- [x] Squelch: CB SQUELCH knobs, 20m/40m/HF receiver spare knobs (2026-11-09)
- [x] SSB with several stations: weighted mix, shared AGC, nearby-antenna desense (2026-11-02)
- [x] Modes meet: `Modulation`, `ModeMix`, 10m freeband to 26.9 MHz, all-band AM (2026-11-08)
- [x] VHF repeater: offsets, CTCSS, CW ID, hang time, timeout, duplexer (2026-10-21)
- [x] Repeater sleep/wake (2026-10-24)
- [x] Multi-channel scan on the VHF receiver: memories, scan, lamps (2026-11-09)
- [x] DTMF tones, handheld DTMF page, DTMF Encoder and Decoder (2026-10-24, 2026-11-04)
- [x] Simplex repeat controller ("parrot"), its polish, sneak-link (2026-10-20..24)
- [x] Radios and wires hang under a block (2026-10-21)
- [x] Duplexer sits on the repeater with its coax down the back; J-pole and duplexer z-fight fixed (2026-10-21)
- [x] CB radio (11m): TRC-217 portable, 5875 mobile, PC68 (2026-11-08); recipes approved 2026-11-09
- [x] CB second pass: JSON-layout screens, in-world displays, CB Ground Plane Antenna (2026-11-08)
- [x] Airband: `airband` band, Airband Radio, Aircraft VHF Antenna (2026-11-13)
- [x] FM broadcast band, FM Broadcast Transmitter and receiver (2026-11-13)
- [x] Small NiCd / Small Alkaline batteries, All Applications Floppy (2026-11-08)
- [x] Crystals from sand in three qualities, crystal radio variants (2026-10-22)
- [x] Microphone Element and speaker crafting components (2026-10-22..23)
- [x] Desk, premium, wall and loud speakers, with recipes (2026-10-26..27)
- [x] HF manpack (2026-10-26); manpack radio worn on the back (2026-10-23 request)
- [x] Handheld battery drain spread over ticks; Curios belt slot and worn-radio talk key (2026-10-23..24)
- [x] Industrial radio block, built as the commercial radio module (2026-10-26)
- [x] Commercial gear and tapes: commercial repeater, radio module, tape reels, tape player (2026-10-26..27)
- [x] Commercial radio modules on the air with chunks unloaded, `CommercialStations` (2026-10-28)
- [x] Review all recipes together (finished 2026-11-09)
- [x] Station plans: `/contest` (2026-11-09), iambic keyer (2026-11-12), linear amplifier and coax loss (2026-11-13), doc/Plan-Station.md
- [x] Creative search by number words, `SearchAliases` (2026-10-31)
- [x] Release creative tab, nothing left dev-only (2026-10-21, 2026-10-27)

## Antennas

- [x] Dipole, end-fed, quarter-wave vertical, loop, wide-band receive, VHF Yagi/J-pole/Slim Jim
- [x] Shared antenna behaviour hooks (types set SWR, gain, reach)
- [x] Antenna tuner runtime effect (2026-10-20), made manual with three dials (2026-10-23)
- [x] Automatic antenna tuner (2026-10-23)
- [x] Duplexer runtime effect and tuning with desense (2026-10-23)
- [x] Dummy load block (2026-10-21)
- [x] Antenna analyzer readout in chat, crash fixed (2026-10-31)
- [x] Inverted V dipole (2026-10-20)
- [x] Random wire on a 9:1 balun (2026-10-20)
- [x] 4:1 balun and off-centre-fed dipole (2026-10-21)
- [x] Moxon (HF wire) and delta loop (2026-10-24), doc/DeltaLoopAndMoxonPlan.md
- [x] Ringo (2026-10-27) and industrial 2-bay folded dipole (2026-10-27)
- [x] Hustler 5-BTV multiband vertical (2026-10-23)
- [x] Satellite dish is the small microwave dish (2026-10-26)
- [x] Ionosonde Antenna (2026-10-23)
- [x] Ionosonde and 5-BTV hitboxes (2026-11-13)
- [x] Lightning rods as basic VHF antennas; antennas draw lightning
- [x] Lightning strike on an antenna burns coax and blows radios (2026-10-24)
- [x] Lightning arrestor, grounded since 2026-11-08 (2026-11-07)
- [x] HF grounding: ground rods, ground wire, `GroundQuality` (2026-11-08)
- [x] Wind antenna damage with Another Weather Mod (2026-10-25)
- [x] Receive-only antenna splitter (2026-10-30)
- [x] Antenna switch, one radio three antennas (2026-10-30)
- [x] RF passes through wood, glass, wool and leaves, tag `rf_transparent` (2026-11-02)

## Computer programs and data gear

- [x] Radio Moderation Computer: logging, archive, Live/Archive/Map/Links/Tapes/Proximity tabs, lock, privacy, config (2026-11-04)
- [x] Radio computer and all 1.12 programs, see 1.12 ideas below and doc/Plan-1.12.md (2026-11-04..06)
- [x] TNC rework steps 1-2: Radio Interface, H19 APRS Terminal, station bus (2026-10-23), doc/TncRework.md
- [x] TNC rework step 3: Teletype Machine with RTTY and SOLAR WX (2026-10-23)
- [x] Ionospheric Processor block (2026-10-23) and working bulletins (2026-10-27)
- [x] Solar bulletins over the air, QUERY, blackout alerts, `getUpcoming` (2026-10-27)
- [x] APRS maps on H19 with empty maps (2026-10-23)
- [x] Digital interface (TNC) first slice: packets, APRS beacons, messages, digipeating (2026-10-21)
- [x] TNC APRS map terrain from loaded chunks (2026-10-21)
- [x] TNC screen scaling (2026-10-21), RTTY (2026-10-21), FILES (2026-10-21), digipeating lamp (2026-10-21)
- [x] Radio Fax replaces the Digital Transfer Interface (2026-10-27)
- [x] Teletype roll: 400 lines, tear off and feed back (2026-10-27)
- [x] H19 needs power (2026-10-27)
- [x] WX reports move to the WEFAX machine; teletype SOLAR WX removed (2026-11-07)
- [x] Another Weather Mod written and in `libs/` (2026-10-24)
- [x] Weather API `WeatherSource`, APRS `_` packet (2026-10-25)
- [x] WEFAX charts on locked maps (2026-10-25)
- [x] WEFAX receiver block, model fixed, screen (2026-10-23..26)
- [x] WEFAX from the air via the computer and Scheduler (verified 2026-11-07)
- [x] WEFAX tray and RFI sound (2026-10-27); readability (2026-10-27)
- [x] WEFAX tornado watch (2026-10-30) and redstone output (2026-10-30)
- [x] Weather fax on the air as real WEFAX audio (2026-10-31)
- [x] Weather station block beacons weather via H19 (2026-10-26); alert tone covered by the Weather Alert Generator (2026-11-07)
- [x] Microwave links, dishes, horn, RSSI, ALIGN page (2026-10-26..27)
- [x] Microwave sites relay, `MicrowaveRoutes` (2026-10-28); horn fed at any base block (2026-10-28)
- [x] Microwave radio, large dish (= horn), recipes (2026-10-26)
- [x] Telephones: handset, wall phone, models (2026-10-27); power via PABX (2026-11-02)
- [x] PABX numbering, linked PABXes, All-Call, Phone PA (2026-10-31..11-02)
- [x] TA-43 field phone (2026-11-04)
- [x] Data wire through unloaded chunks, `DataWireMemory` (2026-11-04)
- [x] Direction finder DF-1 with modules, map, power, recipes; handheld direction finder (2026-10-26..27)
- [x] APRS handheld: the VHF handheld's APRS module (1.11), covers "TNC: handheld/Curios side" too
- [x] Solar weather station, minimal; needs a radio (2026-10-20, 2026-10-23)
- [x] Satellites as FM repeaters, SATTRACK, distress relay, WXSAT over the LAN (2026-11-09..15), doc/SatellitePlan.md

## Wires, bundles & power

- [x] Wire, waterproof wire, insulation texture, recipes
- [x] Wires hug walls (2026-10-21)
- [x] Dyed coax (2026-10-26), thin and coloured wire (2026-10-27), data wire (2026-10-30), dyed data wire (2026-10-31)
- [x] Wire bundles, up to 7 cables in one block (2026-10-27)
- [x] Bundle drawing: order round bends, `/rcdebug wires` (2026-10-28)
- [x] Per-side power and coax network slots, `BENetworkObject.Slot` (2026-10-28)
- [x] Wire Junction (2026-10-28)
- [x] Wire Passthrough (2026-10-21)
- [x] Battery meter (2026-11-07)
- [x] Solar panel and batteries use FE; power from other mods, rebalance (2026-10-23)
- [x] Solar panels join by touching (2026-10-21); recipe with glass and daylight detector
- [x] Small alkaline battery charge handling
- [x] Large batteries combine; radios brown out on empty
- [x] Battery fires (2026-10-24)
- [x] Wire hazards: shorts, electrocution, sparks (2026-10-24)
- [x] Radio power draw tied into `RadioNetworkObject` (2026-10-21)
- [x] Create alternator when CC&A is missing (2026-10-26)

## World & progression

- [x] Patchouli guide book (2026-10-23); on first join, `/radiocraft guide` (2026-10-24)
- [x] Structures: six radio sites, `radiocraft:site` (2026-10-28)
- [x] Something radio-related in villages: the ham shack village variant (1.12 step 8, `VillageHouses`)
- [x] Tower footholds and old footholds (2026-10-24); footholds stack (2026-10-28)
- [x] Tower beams (2026-10-26) on a tower (2026-10-28); Tower Beacon Controller (2026-10-28)
- [x] Lightning breaks carried handhelds and manpacks (2026-10-30)
- [x] Sable support for ships (2026-10-28)

## 1.12 ideas (1.12-ideas.txt, built in 1.12, 2026-11-04..06)

- [x] Camera and B&W / Color Photo Paper
- [x] ADS-B beacon, receiver with CRT radar, antenna, computer app
- [x] Radio computer, monitor and scanner; DOS apps: APRS, ADS-B, WEFAX, fax, DF, solar, forecasts, book editor,
  CPS programmer, band plan, frequency manager, logbook, SSTV; INSTALL from floppies
- [x] Secret floppies: Snake, Bible (WEB gospels), Pileup, Music
- [x] Commercial radios: basic and upgraded handhelds, mobile, low-band mobile, digital, encryption, dyeable
- [x] Marine radios
- [x] MARS mod with OUT OF BAND and splatter
- [x] Real-World Interface
- [x] API rework and website developer docs
- [x] Guide renamed "ARRL Radiocraft Handbook"
- [x] FCC hat, jacket, badge (Curios body slot), banner
- [x] Band Plan sheet, editable in config
- [x] AWM: siren controller and data wire, AWM's own data wire, warning sirens with art and recorded sounds (2026-11-13)

## Later plans built

- [x] Computer batch 3 (doc/Plan-Computer3.md): rig control, waterfall, FT8, Scheduler, Net Control, Awards, QSL
  cards, data floppies, screensaver, Weather Alert Generator and Receiver (2026-11-06)
- [x] LAN (doc/Plan-Computer4.md): LAN RELAY, LAN EMAIL, LAN REDSTONE, Computer Redstone Block (2026-11-13)
- [x] Advancements (doc/Plan-Advancements.md): all but the two skipped (2026-11-06)
- [x] Fire alarm, SCADA and commercial licenses (doc/Plan-FireAlarmScadaLicensing.md), most of it (2026-11-10..13)
