# Development Checklist

Everything still to do, from this file, `1.12-ideas.txt` and the plan docs (combined 2026-11-15). Finished items are in
[doc/Done.md](doc/Done.md); the 2026-10 bug audit is in [doc/Testing.md](doc/Testing.md#bug-audit-2026-10); art for
someone else to draw is in [doc/ModelDesignBounty.md](doc/ModelDesignBounty.md).

## Open

### Ongoing

- [ ] Docs in step with the mod: after each batch bring the Handbook (Patchouli, `assets/radiocraft/patchouli_books/guide`)
  and the website wiki (`Radiocraft-Website`) up to date with each other and with the mod (maintainer, 2026-10-27)
- [ ] Watch for new flaky GameTests. `parrotEchoesVoiceAfterDelay` failures come from interference: ignore them
  (maintainer, 2026-11-15)
- [ ] Port to Minecraft 26.1 and its NeoForge, keeping a 1.21.1 branch alongside, once most of this list is done
  (maintainer, 2026-10-23). Expect churn in GUI/render APIs, registries, mixins, SVC, Jade, TOP, GeckoLib
- [ ] Recipes awaiting the maintainer's review: Airband Radio, Aircraft VHF Antenna, FM Broadcast Transmitter, FM
  Broadcast Receiver (none yet, 2026-11-13); the 1.13 batch recipes (fire alarm, SCADA, keyer, amp, tracker, Computer
  Redstone) are in the provider (check: maybe approved in the 2026-11-13 review, see RadiocraftRecipesProvider.java)

- [ ] 1.14 is the performance release: plan in doc/Plan-1.14.md (draft, for review). Performance leftovers from the 2026-11-15 review (build/agy-notes/performance.md; done: CRT textures reused, bundle
  layouts worked out once a block, wire caches capped, audio not copied for out-of-reach antennas, CRT wall poll):
  wire blockstates (52 wire blocks x 3,840 states, about 200,000; measured 2026-11-15: 82% of all blockstates, roughly 170-340 MB of an 850 MB title-screen heap without FerriteCore; merging hug/climb/ceiling only saves 13%, since
  ceiling wires hug and climb too; the big levers are the 48 dyed copies and a stored climb: maintainer to decide), the bundle solver re-reading every node per trial flip, and
  radios working out their SWR every tick

### Radios & bands

- [ ] Server config for the proposal's HF static squelch, modern/vintage toggles and "talk everywhere" overrides
  (check: squelch knobs exist since 2026-11-09)
- [ ] Redstone interference polish: a real buzz loop (asset bounty), chokes that cut feedline pickup, the interference
  term joining static masking, handheld radios (no block antenna) on transmit
- [ ] DTMF follow-ups: repeater control by DTMF (enable/disable TX, read the S-meter back; the autopatch is built), a
  sidetone for the handheld's own keys, DTMF keys on the base station's panel
- [ ] Repeater: a switch for a second antenna, after the UI rework (volume is the mouse wheel over SPEAKER, 2026-10-26)
- [ ] Aircraft traffic: recorded or generated ATC chatter on the airband (needs audio)
- [ ] ISM band
- [ ] AM broadcast band (MF, 530-1700 kHz) on the FM broadcast receiver, whose dial has an AM scale
- [ ] EAS on broadcast FM (doc/Plan-Computer3.md: "a separate later section")
- [ ] Internet radio streams: a block that plays an IP radio stream onto the air or a speaker (maintainer, 2026-10-27)
- [ ] A global internet radio stream, heard anywhere above ground (maintainer, 2026-10-27)
- [ ] CB leftovers: the 5875's AWI lamp has no job; the maintainer's models use `#missing` on some knobs and the PC68
  DISPLAY; SSB on the 5875's FUNCTION knob?
- [ ] Commercial licenses leftovers (doc/Plan-FireAlarmScadaLicensing.md): the license callsign as the codeplug's
  station ID and a commercial repeater's CW ID (check: maybe done, see MeshDeviceBlockEntity.java), OVER LIMIT in the
  FCC program, the FCC floppy's recipe (none in RadiocraftRecipesProvider.java)
- [ ] Battery self-discharge over time (proposal), if wanted
- [ ] Once the microphone element exists the maintainer switches the VHF handheld recipe to it (it still takes a hand
  microphone; check: maybe settled in the 2026-11-09 recipe review)
- [ ] More radio models and brands (maintainer, 2026-10-23)

### Antennas

- [ ] HF yagis (maintainer, 2026-10-21): options and open questions in [doc/Plan-HFYagiAndWireSag.md](doc/Plan-HFYagiAndWireSag.md)
  (plan only, 2026-11-13; waits for the maintainer)
- [ ] Sag-varying visuals for long unsupported antenna wire: same plan, Part 2 (waits for the maintainer)
- [ ] Loop antennas: play the horizontal/vertical quads (2:1 balun) and the delta loop, then settle the loop gains
  (maintainer, 2026-10-21)
- [ ] VHF Moxon block: needs a model (two tall like the Yagi, SWR 1 on 2m; doc/DeltaLoopAndMoxonPlan.md)
- [ ] Antenna analyzer screen (a swept SWR graph) if chat turns out not to be enough
- [ ] Index each network's antennas by area, if servers grow past a few hundred antennas per band

### Computer programs and data gear

- [ ] Ionospheric Processor: a Jade provider; the teletype's comparator and Jade still read the local conditions
- [ ] Redesigned UI for the H19 (maintainer); for now the old APRS/MSG tabs
- [ ] Radio Fax: real inventory panel art (bounty); for now the vanilla chest's bottom half under the fascia
- [ ] WEFAX weather charts (radar, forecast, surface) at twice the resolution: 256 x 256 needs our own map data and
  renderer, or four tiled maps; the solar report stays (check: maybe done, see WefaxReceiverScreen.java)
- [ ] Microwave: keep linked radios' chunks loaded? (a far end in an unloaded chunk drops the link)
- [ ] Fire alarm leftovers (doc/Plan-FireAlarmScadaLicensing.md): data-wire output for bells (redstone only today), a
  box keeping its name when broken, floppy recipes for FIRE and SCADA (none in the provider; check: maybe intended)
- [ ] Moderation Computer leftovers: scrubbing within a playback (play/stop only) and narrowband re-encoding (Simple
  Voice Chat's server API has no bitrate setting)
- [ ] Solar weather station, rest of the proposal: printed paper report (paper, hopper fed, redstone activated) and a
  best-band calculator between two coordinates (`/bandcalculate` is the admin version)
- [ ] Ender-quantum chest link set: a computer and a chest interface (with an ender chest) pair over the station; the
  terminal lists linked remote chests to send items to and pull from (maintainer, 2026-10-27)
- [ ] APRS Discord bridge (the rest of the old "random structures, auto-beacon radios, external API hooks" item is
  built: sites, commercial radio modules, the 1.12 API)

- [ ] LAN EMAIL attachments (maintainer, 2026-11-15): attach a file from the computer's drive (or floppy) to a mail
  for another user or computer; mail with a file is slow to send, taking longer the bigger the file
- [ ] Security leftover from the 2026-11-15 review (build/agy-notes/security.md): pin the checked address for an FM
  stream's connection (DNS rebinding; the JVM's 30 s DNS cache covers most of it). The rest was fixed 2026-11-15

### Wires & bundles

- [ ] Known: `CableHouseGameTests.houseCablesLookRight` is optional until the maintainer's house is clean (15
  problems on 2026-11-15: one three-strand crossing at house 17,14,25 with no room to bridge, and right-angle
  detours to centred devices, which are by design)
- [ ] Bundles: a strand that joins from the far side still crosses the others (check: maybe done by the 2026-11-15
  bridges, see CableRoutes#bridges)
- [ ] Cable trays (maintainer, 2026-10-21): one block, colour coded, a coloured ring where a cable leaves the tray, at
  ends and at blocks (bundles carry up to 7 cables since 2026-10-27; the tray and rings are not built)
- [ ] Combined coax-and-wire cable into a radio, split out later (check: maybe done by wire bundles, see WireBundleBlock.java)
- [ ] Improve wire spool storage UX (bulk crafting/collapse)
- [ ] Solar panels: removing a device from the middle of a touching row does not split the row
- [ ] Wire Passthrough: should Jade name the mimicked block; a 2-thick wall needs two

### World & progression

- [ ] Ponder-style guide (maintainer, 2026-10-21): PonderLib scenes shipped as a datapack (maintainer, 2026-10-23)
- [ ] Guide book: pictures (GUI screenshots), the wide-band receive antenna, the handheld APRS module (check: maybe done)
- [ ] Screenshots on the website wiki
- [ ] Prepper bunker structure (maintainer, 2026-10-23)
- [ ] Structures that spawn tape players with found tapes; the tape player's screen art
- [ ] Satellites: structure locating from a pass (on hold, 2026-11-13)
- [ ] Flooding of some sort: water rising onto stations, shorts, in storms?

### Integrations

- [ ] Map mods (JourneyMap, Xaero's; [doc/ModIntegrationPlans.md](doc/ModIntegrationPlans.md)): APRS map terrain
  beyond render distance, satellite passes marking areas explored, waypoints for APRS stations, distress positions
  and fire alarm boxes
- [ ] Manpack versions for [Traveler's Backpack](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack) and
  [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks)
- [ ] Seasons mods (Serene Seasons etc.): seasonal propagation, solar output, weather
- [ ] Crystals from pressing sand with Create's mechanical press (maintainer, 2026-10-22)
- [ ] Sable: an antenna wire with only one end on a new ship is left behind and drops (no Sable hook)
- [ ] Sable lightning: rods aboard do nothing, a deck does not shelter an antenna, no fire, a turned ship's bolt can miss
  (left for the alpha testers, maintainer, 2026-10-28)
- [ ] Look for other mods that would be easy to integrate with (maintainer, 2026-10-23)

### AWM companion mod

- [ ] Ball lightning: a rare event in swamps for players who have not slept (AWM's own list is `E:\AnotherWeatherMod\todo.md`)

### Maybe / someday

- [ ] A redstone strand in cable bundles (skipped for now, maintainer, 2026-11-15)
- [ ] Grounding: a little efficiency for verticals on a good ground
- [ ] BASIC interpreter with redstone I/O on the radio computer (skipped, doc/Plan-Computer3.md)
- [ ] Beacon-only (Sputnik) satellites (doc/SatellitePlan.md "Later"; check: session notes of 2026-11-13 say not wanted)
- [ ] 6m, only as an all-band radio setting with sporadic-E (doc/Plan-Station.md: more bands not planned)
- [ ] The smaller "left undone" notes in doc/Plan-1.12.md's step handoffs (3D item models for handhelds, marine
  tri-watch, a fax/FaxModem merge, ADS-B terrain...), if any become wanted

## Decided against

- [-] Terrain losses in the antenna analyzer: it only sweeps SWR; `/bandcalculate` covers paths (maintainer, 2026-11-08)
- [-] FRS radios: too new for the mod's era (maintainer, 2026-11-08)
- [-] Combining or splitting licenses (maintainer, 2026-11-15)
- [-] Satellite launching or building, a 70cm band; Doppler stays an off-by-default option (maintainer, 2026-11-13)
- [-] Advancements "Up the Tower" and "Off the Grid" (maintainer, 2026-11-06)
- [-] Hardline coax: the loss is light enough not to need it (2026-11-13)
- [-] The fire alarm controller's own CRT screen (maintainer, 2026-11-13)
