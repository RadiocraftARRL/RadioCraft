# Needs Testing

What passes the automated tests but still needs a person in `runClient` (rendering, GUIs, audio, feel). One entry per
feature: try it in play and pass it or say what is wrong. The words in brackets are what to look at; no need to test
every case. Add an entry here when a change cannot be checked by `test` / `runGameTestServer`.

Work through it with `python tools/needs_testing.py`: `- [ ]` not tested, `- [x]` passed, `- [!]` needs work, with
`Note (date): ...` comments and `![caption](testing_shots/...)` screenshots indented under the item. Whoever picks up a
`[!]` item fixes it (or moves it to [todo.md](../todo.md)) and sets it back to `[ ]`. Passed items can be deleted (git
keeps the history).

## HF radios

- [ ] Test the new HF panels: 10m, 20m, 40m, 80m, all-band (knobs, dial, mode keys, S/PO/SWR meter, AGC, 10X, QUICK, gain and mic gain)
- [ ] Test the all-band radio (band knob 3-144, FM on 2m, MEMORY and VFO/MEM, lamps, power button)
- [ ] Test the premium HF rigs: the Swan, TS-520R, RC-K2, RTX-100 (models, frequency on the block, panels, RIT, NB, RF gain; the Swan's dial window)
- [ ] Test the QRP radios' home-built panel (tuning knob and skirt, band-edge lamps, OFF/RECEIVE/TRANSMIT, KEY, toggles; the outline now fits the case, 2026-11-15)
- [ ] Test the HF receiver and plain/Cheap radios (old panels still open, squelch knob)
- [ ] Test squelch on every radio (opens on the S-meter reading, tails, hiss at or under band noise)
- [ ] Test CW (sidetone, no static while keying, Space/PTT keying, Left Alt, `cw_keying = CHAT_MORSE`)
- [ ] Test off-frequency signals (SSB 1-10 kHz off pitch/Donald Duck, FM off-frequency, splatter)
- [ ] Test crystal qualities (Premium/Cheap names and panels, raw crystal textures, Cheap drift by ear, sand drops)
- [ ] Test blown radios (bad SWR blows it, Cheap ones sometimes explode, mining gives the parts back)
- [ ] Test out-of-band tuning and the MARS mod (OOB on HF/VHF/manpack, MARS recipe hidden, contraband tooltip, splatter)
- [ ] Test the radio screen readouts on the block (frequency, S-meter, mode, TX within 8 blocks)
- [ ] Test radios on ceilings (hangs right way up, wires join)
- [ ] Test scroll tuning (sneak + wheel while looking at a radio)
- [ ] Test the hand mic on a cord (sneak + use, talk, snaps past 5 blocks, click sounds)
- [ ] Test the desk microphone (hold to talk, sneak to lock, level by distance, data-wired desk mic)
- [ ] Test the HF manpack (wear it chest/Curios, panel, J key, handset, CW)
  - Note (2026-10-27): shift right click doesnt open ui while on curios backpack slot.
  - Note (2026-10-27): by design the key (J, Open Worn Manpack) opens a worn pack and sneak + right click with it in hand wears it. Added the same evening: sneak + right click on nothing with an empty hand while wearing it (chest or Curios) opens the panel too. In hand, a plain right click opens it; if that does nothing in creative, say so, I could not reproduce it from the code.

## VHF radios and handhelds

- [ ] Test the VHF base station (panel, offset, tone, squelch, TX/RX lamps, STEP knob, 2m display)
- [ ] Test VHF base station memory channels (MW, V/M, store with offset/tone/step, survives break and relog)
- [ ] Test the VHF handheld (LCD, memories and names, TXT/RXT tones, OFFSET and STEP pages, no receive while transmitting, held and inventory model, battery pack drawing)
- [ ] Test the handheld APRS module (menu, beacon settings, stations list, messages, mute)
- [ ] Test handheld DTMF (# page, send codes, resend)
- [ ] Test the handheld on the belt (Curios belt slot, plays what it hears, drains its battery)
- [ ] Test the VHF receiver scanner (block, panel, eight channels, scan, SET/TUNE, volume/cell, whip, old saves load)
- [ ] Test the desk charger (handheld in the cradle as its 3D model, charges, power wire joins, sneak-use to dock)
- [ ] Test alkaline cells in the handheld and CB portable (swap either way, flat one comes back)
- [ ] Test the handheld direction finder (set frequency, beeps toward the station, lamp on top)

## CB radios

- [ ] Test the CB mobiles 5875 and PC68 (panels, block models, channel/clock windows, volume knob on/off, FUNCTION/CLAR/RF GAIN, NB/ANL, AM/FM)
- [ ] Test the CB portable (3D item, screen, battery, two players talking, belt slot, held size)
- [ ] Test the CB ground plane antenna (five blocks, breaks as one)
- [ ] Test next-channel bleedover (19 and 20 close together)

## Commercial radios

- [ ] Test the basic and upgraded commercial handhelds (models in hand, first/third person, screens, knobs, zones A-D, 64 channels, dyes, desk charger)
- [ ] Test the Commercial VHF Mobile and Low Band radios (block models, panels, PWR/VOL, CH, LCD, keys, MIC jack, dyes)
- [ ] Test CPS programming (menus, read/write by hand and wired, progress boxes, F9 Program radios, unit IDs, contacts, import from FREQMAN, 40-channel files on 16-channel radios)
- [ ] Test the Radio Cloner (read and write handhelds, mobiles, VHF ham radios and base stations)
- [ ] Test unit IDs on the air (data burst on key-up, caller shown on the LCD, moderation shows "FM ID n")
- [ ] Test commercial frequency licenses (claim a blank, certificate in an item frame, copies, upgrade, cartographer trades, CPS License Check, `commercial_licensing` config)
- [ ] Test MURS (license-free, FCC Monitor says NO LICENSE NEEDED)
- [ ] Test the passive distress receiver on commercial radios (any channel hears a beacon: mobile LCD, handheld alarm and plate, DISMISS, charger, not low band)

## Marine radios and distress

- [ ] Test the Marine Radio and Marine Handheld (models and screens, channels, 16/9, HI/LO, dual watch, duplex 26, INTERNATIONAL plan)
- [ ] Test DSC distress (hold DISTRESS, NAT, alarm on other radios, ACK, range)
- [ ] Test the distress beacon (cover, activate, DF on 161.975 finds it, runs out, satellite relay)

## Airband and FM broadcast

- [ ] Test the Airband Radio (model, screen, USE/STBY flip-flop, AM hiss, squelch/MON)
- [ ] Test the aircraft VHF antenna and airband ground plane (top/underside placement, models)
- [ ] Test the FM Broadcast Transmitter (two blocks, screen, quiet carrier, mic ducking, weather alerts, forecasts and IDs, stream program with `fm_internet_streams`, panel buttons)
- [ ] Test the FM Broadcast Receiver (walnut model, screen, tunes while off, scroll to tune, BASS/TREBLE, battery, pointer on the block, POWER/MUTE toggles)
- [ ] Test the tape player with the FM station (QUEUE mode from a hopper, no longer keys the transmitter)

## Repeaters and controllers

- [ ] Test the VHF repeater (face, PRT RX/TX, CW ID, CTCSS, MIC/SPEAKER, hang time and timeout, squelch tail, sleep and ID)
- [ ] Test the duplexer (behind, on top, under; screws; no duplexer gives "psht"; desense with a TX screw on the input)
- [ ] Test the commercial repeater (four-block rack, PWR lamp needs power, no disconnect on right click)
- [ ] Test the Simplex Repeat Controller "parrot" (model, LEDs, wire stub, delay cycle, echo, courtesy beep, linking)
- [ ] Test the Repeater Autopatch Controller (dial in by DTMF, phone call over the air, hang up with #)
- [ ] Test a repeater loop (two repeaters on each other's frequencies howl and stop)
- [ ] Test the DTMF encoder and decoder blocks (screens, models, redstone, weak-signal dropouts)

## Antennas, tuners and coax gear

- [ ] Test the wire antennas: dipole, inverted V, random wire on 9:1, OCFD on 4:1, delta loop, Moxon (Jade names, SWR, Moxon reflector strung last)
- [ ] Test improvised antennas (bent dipole reads Improvised, still works, tuner fixes it)
- [ ] Test the 5-BTV (seven segments, SWR 1.3, coax runs in, hitbox)
- [ ] Test the J-pole, Slim Jim and Yagi (no flicker with coax, still hear after a restart, yagi aimed in 16 directions)
- [ ] Test the VHF Ringo and folded dipole (models, range, lightning damage)
- [ ] Test the EB-VHF eggbeater (two blocks, model, best for satellites overhead)
- [ ] Test the Ionosonde Antenna (two blocks, SWR on VHF/HF, hitbox)
- [ ] Test the lightning rod as an antenna (poor SWR, strikes)
- [ ] Test the manual and automatic tuners (dials, relay clicks, placed against a radio, readouts, `antenna_tuner_enabled`)
- [ ] Test the antenna analyzer (no crash, resonance and SWR by band, suggestions)
- [ ] Test the linear amplifier (on the coax between radio and antenna)
- [ ] Test coax loss (long runs a little weaker)
- [ ] Test the lightning arrestor (passes signal, protects in a storm)
- [ ] Test the antenna switch (redstone step, knob pointer, on a wall: coax in from below, outputs left/top/right)
- [ ] Test the antenna splitter (both radios hear, neither transmits)
- [ ] Test the dummy load (SWR 1, faint hiss, short range)
- [ ] Test COAX SHARED (one antenna on two radios shows it)
- [ ] Test the baluns (4:1 and 9:1 textures, right-click names the antenna)
- [ ] Test radios with no antenna (faint hiss, bare radios hear each other a few blocks)

## Wires, cables and bundles

- [ ] Test plain, coloured, waterproof and ground wire (look, dye, separate networks, Jade names)
- [ ] Test dyed coax and dyed data wire (16 colours each, stay separate, through one passthrough)
- [ ] Test data wire (look, joins devices only at its ends, teletype/fax/speaker over it, through unloaded chunks)
- [ ] Test bundles (1 px gaps, 7 strands fit, red + blue wire share but plain + a colour may not, round bends, peel-off order, up lattice legs, shift-click takes the strand under the crosshair, power through a bundle)
- [ ] Test strands that must cross: bridge over the other strand (house 15,2,19 = world -40 84 536, grey and plain power wire)
- [ ] Test the cable outline (2026-11-15: it now follows the drawn cable, bridges, steps and stairs included; sneak + right click on the part you see takes that strand out)
- [ ] Test the house's coax up the bunker wall (world -37 91-92 548: the lone coax on top of the bundle sits on the strand's line, no jog) and the wire into the computer at world -38 96 543 (no second riser by the wall run)
- [ ] Test wires hugging walls (floor/wall corners, pillars, corridors, ceilings, passthrough at head height, jogging out to a device)
- [ ] Test wires climbing (steps, trenches, posts, up stairs onto a landing, into the middle of a device's face, over a beam top)
- [ ] Test cables to desk radios (round to the back, drop from a ceiling to the connectors, hop over into a computer)
- [ ] Test the cable fixes in Demo World's house (corner at -36 86 546/547, bundles round -33 86 547, battery top at -41 87 534; known open: -32 86 547)
- [ ] Test the cable test world (`./gradlew runClientTests`, superflat creative, `/test runall CableMatrixGameTests 1 false 0 16`; walk the rows; optional `/test run bundlepastadevicebothstrandsfeed` is a known bug)
- [ ] Test the Wire Passthrough (wall looks unchanged, drops, Jade, power and coax stay separate)
- [ ] Test the Wire Junction (red in, blue out powers a radio; model)
- [ ] Test the floor cables from the Radio Interface to its station gear

## Power

- [ ] Test radios drawing power (no battery = dark, brown-out PWR cycle, draw rates, state after relog)
- [ ] Test batteries (share charge evenly, survive relog, fires, meter)
- [ ] Test the battery meter (analog dial, UVs on every face)
- [ ] Test solar panels (join by touching, Jade output, weaker in rain and AWM severe weather)
- [ ] Test the charge controller (switch, 180 FE a tick)
- [ ] Test the alternator (`./gradlew runClient -PwithCreate`; model, charges a battery, goggles; only one alternator with Crafts & Additions)
- [ ] Test Forge Energy from other mods (alternator, FE cables, handheld idle drain, desk charger speed)
- [ ] Test power switches On/Off and Left/Right (floor and wall, redstone, models)
- [ ] Test the Stable Chemical Battery (inert until redstone, won't charge)
- [ ] Test electrical hazards (2x2 short, wire in water, open end in rain, battery fires, lightning)
- [ ] Test grounding (ground rod quiets noise, ground fault drains a battery)

## Towers and microwave

- [ ] Test tower beams (joins, bends without flicker, braces, reach to ground/walls/antennas, coax hugs the beam)
- [ ] Test tower footholds (on walls and beams, stacked, climb, break chance)
- [ ] Test the Tower Beacon and Beacon Controller (blink, redstone)
- [ ] Test microwave dishes (placed level facing you, aim, ALIGN page, link maths matches the model, TOO WEAK)
- [ ] Test the microwave horn (2x2x4, coax on any base block, turning, draw distance)
- [ ] Test the microwave radio (terminal, SETUP, boot sounds, phones and H19 over a link, three-site relay, rejoin keeps the link)

## Phones

- [ ] Test the telephone (handset up, ring, wall mount, calls, extension change)
- [ ] Test the PABX (panel, linked PABXes over wire/microwave, names, All-Call, timer)
- [ ] Test the Phone PA (call it, voice out of speakers, local-only checkbox)
- [ ] Test the TA-43 field phone (model, handset, ring, party line, dead line)

## Station gear

- [ ] Test the H19 terminal (screen, boot, MAP, INBOX, LIST, MSG, SETUP, acks, whirr, APRS Map item and 2x2 walls)
- [ ] Test the Radio Interface (callsign, TX ON/OFF, chain to H19 and fax)
- [ ] Test APRS through the TNC (beacons, icons, messages, digipeating, `/aprs beacon` and `/aprs broadcast`)
- [ ] Test the Teletype Machine (screen, paper, typing speed, RTTY tones, garble, roll item, scroll back, SOLAR WX)
- [ ] Test the Radio Fax (model, screen, LCD, RECENT, sending books/maps/photos, tones, retries, NO ANSWER, hoppers)
- [ ] Test the WEFAX receiver (model, screen, two sheets, click to take, AUTO, redstone lamp, fax tones)
- [ ] Test the Ionospheric Processor (two blocks, valves glow, terminal, SEND NOW, timed bulletins, blackout alerts with `/rcdebug solar forecast blackout`)
  - Note (2026-10-27): need to make a more obvious screen on the moel. Also, it's not finding a radio with a tuner in between it and the radio.. Also jei blocking it
  - Note (2026-10-27): fixed the same evening: a radio on the far side of a tuner or duplexer touching the rack now counts; the terminal is registered with JEI like the other station screens; the screen face is on the model bounty list.
- [ ] Test the solar weather station / teletype report (needs a radio, printed report, comparator 1-6, Jade)
- [ ] Test the weather station and APRS WX (cups spin, vane follows `/awm here`, H19 symbol)
- [ ] Test the direction finder (modules, DF page, FIX page, map-enabled version, lock sounds, power)
- [ ] Test ADS-B (beacon and receiver screens, live tube, squawk 7700, on Sable ships, ADS-B program)
- [ ] Test the tape player and tapes (`config/radiocraft/tapes`, `/rcdebug tapes`, reels, PLAY)
- [ ] Test the commercial radio module (sealed cabinet, creative setup screen, SSB/AM, dead air hiss)
- [ ] Test speakers (desk, wall, large/premium; passband, static, music from a computer)
- [ ] Test the camera and photo paper (viewfinder, zoom, color/B&W, cooldown, item frame, fax PHOTO slot, `camera_photo_cap`, `camera_enabled`)

## Radio computer

- [ ] Test the radio computer, monitor and scanner (models, boot, text mode, keys, live monitor in the world, power, two players at once, break and replace keeps files)
- [ ] Test the shell and SETUP (launcher views, Program Manager icons, prompt commands, colour schemes, password, name and clock, Services page, ABOUT, launcher search)
- [ ] Test menu bars in every program (click items, greyed items, keyboard still works, no icons through menus)
- [ ] Test data floppies, File Manager and prompt copying (labels, DIR/COPY, A: and C:)
- [ ] Test the All Applications Floppy (installs everything, stays in hand)
- [ ] Test the network (names, data wire between computers, remote sessions, background services on/off)
- [ ] Test EDIT, BOOKEDIT, LOGBOOK and FREQMAN (save with Ctrl+S, forms, grids)
- [ ] Test CPS (see Commercial radios)
- [ ] Test RIGCTL (rig picker, waterfall, scan, remote control over wire and microwave, REMOTE audio, F6 page)
- [ ] Test FT8 (two stations, sound, reach, Scheduler CQ job)
- [ ] Test RTTY and the Packet BBS (type-ahead, SYSOP and TERMINAL pages)
- [ ] Test APRS, FAX, SSTV, WEFAX and DFIND programs (station line, send/receive, confirm before sending)
- [ ] Test PREDICT, REPEATER, STUDY, BANDPLAN, SOLAR and SYSINFO (layouts at 80 columns, exam flow)
- [ ] Test Net Control, Awards, QSL Designer and QSL cards (cards in hand, item frame, album, fax and BBS QSLs)
- [ ] Test the Screensaver and Scheduler (SETUP page, monitor in the world, jobs and reminders with `/time set`)
- [ ] Test MUSIC (48 tunes, volume, shuffle/repeat, visualizer, sound from the computer and speakers, keeps playing in background, no skips under lag)
- [ ] Test the secret programs SNAKE, PILEUP, BIBLE and their loot (`/give` the floppies or find them in chests)
- [ ] Test DISTRESS (background service, alerts bring it forward, F7 SOS, F6 map print, beacon listeners)
- [ ] Test the FCC Monitor program (direction finder, licensed vs not, MURS)
- [ ] Test ADS-B program (scope from the receiver)
- [ ] Test SATTRACK (list, Details/Image/Location views, mouse in the list, modes, stealth, DEL, Close/Exit, F7)
- [ ] Test the WXSAT program (live strip, mosaic, loop, save/print, storms, map zoom/drag, menus, LAN sync and Block LAN sync, geostationary frames; `/radiocraft satellite overhead` for a pass)
- [ ] Test the External CRT Monitor (faces you, wall of monitors, LIVE/MOSAIC/LOOP/OFF, CRT effects, WAITING FOR SATELLITE PASS, radio waterfall on a premium radio; 2026-11-15: a pass fills in smoothly, and breaking one monitor of a wall re-forms the rest within a second or two)
- [ ] Test the Computer Redstone Block and REDSTONE / LAN REDSTONE
- [ ] Test LAN RELAY and LAN EMAIL (usernames, relay server, mail)
- [ ] Test the plain paper prints (DISTRESS map, Awards certificate, pictures)

## Fire alarms and SCADA

- [ ] Test the fire alarm controller (RC-704 model, terminal event screen, SILENCE, ack, log, printing reports, buzzer range)
- [ ] Test the radio fire alarm box (wall cabinet, programming panel MAIN MENU/SETUP/STATUS, LINK meter, alarm types, Fire Alarm Key, comparator, setup without a license)
- [ ] Test pull stations, outdoor pull boxes and panic buttons (models and colours, wiring into wall devices, locations)
- [ ] Test fire bells and box range (bells on the wall behind, ~110 blocks between boxes, works through unloaded chunks)
- [ ] Test the FIRE ALARM program (box list, F4 map)
- [ ] Test the SCADA RTU and Master (setup, faces IN/OUT, rules from a book, COMM FAIL, power, hitbox)
- [ ] Test the SCADA console and program (boot, TUI tabs, F4 trends)

## Satellites and moonbounce

- [ ] Test satellite contracts (cartographer trade, launch, amateur/commercial/geostationary, licenses show three frequencies)
- [ ] Test satellites in the night sky (dot size, flash, grey dot, geostationary stays put, decommissioned glint, decay)
- [ ] Test voice through a satellite (downlink with -600 offset and tone, transit delay echo, Morse ID after the voice; `/radiocraft satellite overhead <name>`)
- [ ] Test satellite modes (APRS, MAILBOX, SSTV, IMAGING, telemetry, Doppler with `satellite_doppler`, eclipse, decay warning)
- [ ] Test the Satellite Tracking Antenna (turns with power, stops without, no flicker, weaker in aurora/storm)
- [ ] Test moonbounce (MOON in SATTRACK, CW echo on 2m with the tracker at night)
- [ ] Test the iambic keyer (model, dye/material variants, screen, keying)

## Sound and propagation

- [ ] Test the radio-sounding voice (band-pass, AGC, crunch, static ducking, HF skip fading)
- [ ] Test two stations at once (SSB both heard, FM capture/garble, desense, SSB S-meter following the voice)
- [ ] Test static (loops cleanly, comes back after the sound engine drops it, FM static recording, dead air hiss)
- [ ] Test solar weather on HF (blackout closes bands, `/solarweather`, `/rcdebug solar set blackout`, S-meter in a blackout)
- [ ] Test the aurora (sky past Z 10000, VHF aurora contacts, crackle, `allow_aurora_at_any_z_coordinate`)
- [ ] Test sporadic E and 10m skip (`/rcdebug solar sporadic_e on`)
- [ ] Test terrain (hills on VHF, valleys on HF, indoors weaker, walls built, RF through roofs, `/rcdebug terrain`)
- [ ] Test redstone interference (receive pops and hum, transmit lights dust, RFI recordings, config switches)
- [ ] Test lightning static (crash by band, `lightning_static_enabled`)
- [ ] Test the new sounds (H19 whirr, bell and typing, power buttons, DSC alarm, buzzer, computer welcome chime)

## World and structures

- [ ] Test the radio sites (`/locate structure radiocraft:repeater_site` and the others; sit on the ground, modules heard from afar, beacon blinks, no lag)
- [ ] Test the ham shack, standalone and in villages (`/locate structure radiocraft:ham_shack`)
- [ ] Test loot (secret floppies in chests)
- [ ] Test the advancements tab (Going Ham, First Contact, Grey Line, QSL ones, Handbook page)
- [ ] Test radios on Sable ships (`./gradlew runClient -PwithSable`; station works aboard, signal follows the ship, screens stay open, yagi/DF/APRS/microwave/ADS-B aboard)
- [ ] Test the guide on first join and the creative tab (released items, no dev tiles, number-word search)
- [ ] Test block drops (pickaxe, by hand, two-block items, passthrough, charger)

## Another Weather Mod

- [ ] Test the outdoor sirens, now in AWM (`anotherweathermod:omni_siren` etc.; models, pole-mounted, switch, redstone)
- [ ] Test siren public address from a commercial mobile on data wire
- [ ] Test the weather alert generator and receiver (models, status, `/awm approach tornado 2`, redstone pulse, silence, battery, antenna)
- [ ] Test storm damage to a station (wire antennas snap in a cat 3 hurricane, mounted ones stand to cat 4; `/awm hazard hurricane`)
- [ ] Test WEFAX weather charts with AWM storms (`/awm spawn supercell`, `/rcdebug wefax radar`, `/rcdebug wx`)

## Moderation and admin

- [ ] Test the Radio Moderation Computer (Win98 window, boot, Live, Hear what they hear, Archive, Map, Links, Tapes, Proximity, Access, sorting, Freeze, Reboot, monitor in the world)
- [ ] Test the moderation log (join notice, export to .opus, repeater/tape/data/commercial lines, `moderation_metadata_only`, REC on screens)
- [ ] Test the FCC field gear (hat, jacket on Steve and Alex, badge on the body/belt Curios slot, banner, `/radiocraft fcc badge <player>`)
- [ ] Test `/contest` (start/stop as op, contacts counted, results book, survives restart)
- [ ] Test callsigns (choose screen for new players, repeater callsigns, `/radiocraft license info <callsign>`)
- [ ] Test `/radio moderateall` (voice and RTTY/APRS readout)
- [ ] Test the Real-World Interface (needs a real radio and sound card: config, PTT never keyed on open, downlink, VOX/COS uplink, time-out, licence button, op only)
- [ ] Test the config screen (readable names for every setting; delete old `radiocraft-common.toml` first)
- [ ] Test the modding API example (`radiocraft_example` loads, example radio and ground plane, website `wiki/modding.html`)

## Handbook, JEI and tooltips

- [ ] Read through the ARRL Radiocraft Handbook (new pages fit, links work, spotlights show items)
- [ ] Check JEI stays clear of every station and computer screen
- [ ] Check tooltips (no "Not Implemented" on working items, explanations on crystals/beams/tuners)
- [ ] Check the Jade readouts (radios, controllers, solar weather station, coax, panels)
