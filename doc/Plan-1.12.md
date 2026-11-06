# RadioCraft 1.12 plan

Source: `1.12-ideas.txt` (maintainer, 2026-11-04). This file is the shared brief for the agents building 1.12, one
step at a time. **Read the whole file before starting a step, including every earlier step's handoff notes.** When you
finish, fill in your step's "Handoff" with what a later agent needs: the classes and data components you added, the
APIs other steps should call, what you left undone and why, and any gotchas. Keep it short and factual.

## Rules for every step

**OVERRIDE (maintainer, 2026-11-04, while they play a game): do NOT run any `gradlew` command at all (no build,
compileJava, test, runData, runGameTestServer) and do NOT commit.** Code the whole step in one go and check it by
careful reading: imports, method signatures against the real classes, registrations, lang keys in the language
provider, JSON validity. Still write the unit tests and GameTests, but don't run them. Mark your handoff
"UNBUILT / UNTESTED" and list what to verify first. Where the rules below say build, test, runData or commit, skip
that part until this override is lifted. A plain `javac -Xlint:all` type-check of the files you changed against the
cached NeoForm/NeoForge jars (output to the scratchpad, no Gradle; see the Step 6 handoff) is allowed and recommended.

- Follow `AGENTS.md` and `doc/Testing.md`. Java 21, tabs as already used in the file you edit, `-Xlint:all` clean.
- Match real radio behaviour, simplified (the maintainer is a ham, W1BTR). Real ham/commercial terminology in names.
- Busy-server performance matters: no per-tick world scans, no per-tick allocation in hot paths.
- New blocks/items that need art: ship a placeholder model and add them to `doc/ModelDesignBounty.md`.
- Recipes: pick something sensible and cheap-to-mid; list them in your handoff so the maintainer can review them.
- Lang strings go through the data generator (`RadiocraftLanguageProvider` or equivalent). Never hand-edit
  `src/generated/`; run `./gradlew runData` yourself and commit its output.
- Tests: unit tests for pure logic (`./gradlew test`), GameTests for server-side behaviour
  (`./gradlew runGameTestServer`). Both must pass before you commit. Anything needing eyes/ears goes in
  `doc/NeedsTesting.md`.
- Release notes: add your player-facing changes to `doc/ReleaseNotes-1.12.md` (create it on the first step, same style
  as `doc/ReleaseNotes-1.11.md`, under a `## 1.12.0` heading).
- Guide: add or update the Patchouli page(s) for what you built (`assets/radiocraft/patchouli_books/guide`).
- Git: commit your step on `main` when tests pass, **stage by folder** (`git add src doc tools ...`; a missing path
  aborts the whole add). Present-tense message. End the message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. **Do not push, tag or release.**
- The maintainer's game client is not running; builds and GameTests are fine. Do not start `runClient`.
- If a design question would really change the result and this file does not answer it, pick the most realistic
  option, record it under "Open questions for the maintainer" below, and carry on.

## Decisions already made (maintainer, 2026-11-04)

- Bible app: **World English Bible** (public domain), the four gospels.
- The falling-blocks game must have an original name and look (not "Tetris"). Snake is fine as "Snake".
- Music app: public-domain or CC0 MIDI only; record each source and licence in `doc/Credits-Music.md`.
- Secret floppies (snake, blocks game, music, Bible) are rare loot in vanilla structure chests **and** in a new small
  ham shack structure (a little house with a J-pole or Yagi on the roof; spawning inside villages if that is not too
  hard, otherwise standalone).
- MARS mod: each HF band grows by half its width, a quarter on each side (20m 14.000-14.350 becomes
  13.9125-14.4375 MHz); VHF radios also get 150-174 MHz. Without it, radios still receive out of band but show
  "OUT OF BAND" and refuse to transmit. Out-of-band transmissions splatter: their interference reaches twice as far
  into adjacent frequencies.
- Marine radio: a fixed-mount base block (with hand mic, works on Sable boats) and a marine handheld. Channel-only,
  with a 16/9 quick button.
- Real-World Interface: build it fully (javax.sound + jSerialComm for RTS/DTR PTT), **off by default**; sound cards
  only configurable in the server config file. Untestable here: list it in NeedsTesting.
- AWM sirens: new siren types and the switch to recorded audio files **wait for the maintainer's photos and
  recordings**. Only the siren controller and data-wire control are built now.
- Commit each step, no push.
- Camera (step 4 answers): a viewfinder and zoom to 3x; the fax prints received photos on photo paper when it has
  some (a colour photo on B&W paper prints grey), else an empty map; "Color Photo Paper" is the name; a per-player
  photo cap in the server config, **no cap by default** (0), and a player at the cap is offered their old photos to
  delete (always asked first, never automatic).
- Camera (maintainer, 2026-11-04): viewfinder and up to 3x zoom; the fax prints photos on a map or on photo paper;
  "Color Photo Paper" is the name; per-player photo cap, server config, default no cap (busy servers can set e.g. 60); at the cap the player is offered to delete
  old photos (never deleted automatically).

## Steps

Order matters: later steps build on earlier ones.

### Step 1: Bands, band plan, MARS mod, Handbook rename
- New bands: commercial VHF 150-174 MHz, commercial low band 30-50 MHz ("just above 10m"), marine VHF channels
  (156-162.025 MHz, the international/US channel table, simplex and duplex pairs). Wire them into `Band`,
  `BandUtils`, the server config and propagation (low band behaves between 10m and 6m-ish VHF; VHF commercial like
  2m).
- Band plan model: a data structure of segments (range, use, colour) and markers (frequency, label), loaded from the
  server config so it is editable, synced to clients. Defaults, simplified but true to the ARRL band plan:
  80/40/20/10m (CW/digital, SSTV/fax, phone), 2m (CW/weak signal, data incl. 144.390 APRS (use the frequency the mod
  already uses for APRS), simplex, repeater in/out), low band and VHF commercial (business, licence required;
  public safety; military/federal. Realistic hints: 162-174 is mostly federal/military; 150.8-162 public safety
  and business pockets; MURS 151.820/151.880/151.940/154.570/154.600; low band has military pockets around 30-30.56,
  32-33, 34-35, 36-37, 38-39, 40-42, 46.6-47, 49.6-50). Markers such as "144.390 APRS" and a "7.060 Server-wide net"
  example.
- **Band Plan sheet** item: cheap recipe, right click opens a scrollable UI showing each band as a bar with coloured
  segments and markers, plus a legend. Not a map item.
- MARS mod component item ("MARS Mod"), tooltip says contraband / not legal for amateur use. Crafted with a radio
  (shapeless, keeps the radio's components) to set a data component/BE flag. Applies to all HF transceivers and VHF
  radios. Transmit-range check everywhere a radio transmits (block radios, handhelds, CW, data modes) with
  "OUT OF BAND" on the display. Splatter as in Decisions.
- Rename the Patchouli book to **"ARRL Radiocraft Handbook"** (name, item name, landing text, website mentions in
  the repo).

### Step 2: Commercial radios
- Channel model: a programmed channel list (name, rx/tx freq, analog FM or digital, CTCSS optional, encryption key id
  or none, "key readable" flag) stored as a data component on commercial radios. Programmed only by the radio
  computer's programmer app (step 6); until then expose it through `/rcdebug` so it can be tested. Write the
  programming API so step 6 just calls it.
- **Basic commercial handheld** (1990s brick): cheap, no screen, RX/TX LEDs and a channel knob (1-16). 150-174 MHz.
- **Upgraded commercial handheld** (1990s fire-service style): small screen showing channel name, digital modes,
  encryption, also covers the 2m ham band; channels still pre-programmed; option "encryption key not readable".
- **Commercial mobile radio** (block, hand mic, like the base stations; vehicle/Sable friendly) for VHF 150-174, and
  a **commercial low-band mobile** for 30-50 MHz.
- Digital channels: no static; audio is clean until the signal would give 70% static on FM, then it drops out (with
  the usual short digital warble at the edge if cheap to do).
- Encryption is virtual: listeners without the key hear garbled digital noise instead of voice. The moderation
  computer records and hears the raw audio.
- Dyeable cases (handhelds and mobiles), like leather armour: tint layer in the model.

### Step 3: Marine radios
- Fixed-mount marine radio block and marine handheld per Decisions. Marine channel table from step 1. Ch 16 distress
  and calling, ch 9 hailing, a dual-watch/16 key, 25 W / 1 W high/low on the base, handheld 5/1 W.

### Step 4: Camera and photo paper
- Camera item: right click takes a photo of what the player sees (client renders/reads the framebuffer, scales to
  128x128 and quantises to map colours; send to the server in chunks, size-limited, rate-limited) and prints it onto a
  map item (a locked filled map with the picture, like a picture map) using one Photo Paper.
- Photo Paper: 1 empty map + 8 paper -> 16 B&W photo paper. Colour photo paper: same but the bottom three paper are
  red, green and blue dye. B&W paper gives a greyscale photo.
- The photo map must work with the existing radio fax (send it like any map).

### Step 5: Radio computer platform
- Three blocks: **monitor** (the moderation computer's model, slightly changed), **computer** (tower/desk unit, placed
  under the monitor; it holds the storage, floppy drive and the apps), **scanner** (beside it; scans maps/photos/books
  in, prints faxes/photos/documents out using paper/photo paper).
- DOS-style colour text-mode UI (80x25 feel, CGA/VGA colours), distinct from the moderation computer's Windows 98
  look; reuse its infrastructure where it fits (look at `client/screens/moderation`, `common/moderation`).
- Files stored on the computer (its BE or a SavedData keyed by computer id): documents, images (map colour arrays),
  logbook entries, frequency lists. Sensible size caps.
- Ships with: text editor, system info, band plan viewer (step 1 data), and "Install", which asks for a floppy.
  Floppy disk items, one per app, crafted (cheap-to-mid). Installing copies the app onto the computer.
- Without the scanner, everything works except bringing in images from players and printing.

### Step 6: Office apps
- Book editor (unlimited pages, saved on the computer, print to a written book through the scanner/printer),
  logbook (ADIF-like fields: date/time, call, freq, mode, RST, notes; can auto-fill from recent contacts if the radio
  is linked), frequency manager (saved channels; export to a commercial radio), **commercial radio programmer**
  (reads/writes the step 2 channel list of a radio in the scanner slot or a linked one; respects "key not readable"),
  solar weather and forecasts (from the existing solar weather and the weather sources API).

### Step 7: Radio apps
- APRS (needs a VHF radio and a radio interface linked by data wire), wefax send/receive, radio fax send/receive,
  SSTV (like the fax but for map images, digital unless printed; save, recall, resend, add text captions),
  direction finding (if a direction finder is connected: better map, 3D accuracy animation, saved past searches).
  Reuse the existing APRS, fax, wefax and direction-finding code paths.

### Step 8: Secret apps, loot and the ham shack
- Snake, the falling-blocks game (original name), music player (public-domain MIDI, played client side via
  `javax.sound.midi` or a simple synth into the radio audio), Bible (WEB gospels, book/chapter navigation).
- Loot modifiers for vanilla chests; the ham shack structure with a J-pole or Yagi on the roof, with the existing
  `worldgen` site system if it fits; a village variant if not too hard.

### Step 9: ADS-B
- ADS-B beacon block (UHF 1090 MHz): on Sable sub-levels / Create Aeronautics craft (optional deps; see
  `-PwithSable`/`-PwithCreate` and existing compat). Broadcasts position, callsign, heading, altitude and speed about
  once a second. Not affected by lightning/solar weather. Line-of-sight range grows with altitude (radio horizon).
- ADS-B antenna block and ADS-B receiver block with a green CRT-style radar screen (range rings, targets with tags,
  trails). Computer ADS-B app (step 5 platform) using the same receiver.

### Step 10: FCC gear
- FCC field-agent hat and chestplate (1970s style; armour items with placeholder models), FCC banner (pattern or
  pre-made banner item), FCC badge: given only by ops (`/radiocraft fcc badge <player>` or similar, not craftable),
  goes in the Curios necklace/chest slot when Curios is installed.

### Step 11: Real-World Interface
- Block linked to an antenna, or "everywhere" mode. Server config lists sound devices (in/out) and serial port +
  RTS/DTR PTT. The block's UI selects one of the configured devices only. Bridges in-game receive -> sound card out +
  PTT, and sound card in (VOX/COS on a configured line) -> in-game transmit. Big licensing warning in UI and config:
  every player must be licensed in real life and use their real callsign. Off by default. Add jSerialComm from Maven
  Central to `build.gradle.kts` (shaded/jar-in-jar like the other runtime libs; check the `modJar` notes in
  AGENTS.md).

### Step 12: API rework and docs
- Check the radio/antenna API (`com.arrl.radiocraft.api`) lets a companion mod add a radio and an antenna on any
  band, including the 1.12 bands. Fix gaps, add Javadoc, add an example in the gametest source set. Write the API
  guide for the **website** wiki (repo path in memory / `radiocraft-website-wiki`), not the in-game book.

### Step 13: Handbook and wiki pass
- Bring the Handbook and the website wiki up to date with everything above; check the release notes read well.

### AWM step (separate repo `E:\AnotherWeatherMod`, can run alongside)
- Siren controller block: redstone-activated; powers sirens connected to it by data wire (sirens still also work from
  plain redstone). Wire detection by a block tag `anotherweathermod:siren_wire` containing AWM's own wire and
  `radiocraft:data_wire` (optional entry). AWM registers its own data wire block only when RadioCraft is not loaded
  (or always register it and hide it when RadioCraft is present; record the choice). Tests in AWM, commit in AWM, no
  push, no release, do not copy the jar into RadioCraft's `libs/` (the coordinator does that at the end).

## Open questions for the maintainer

- Answers (maintainer, 2026-11-05): receiving out of band does NOT need the MARS mod (as built). ADS-B range: twice
  the microwave horn's maximum (dish range x horn factor) for an aircraft at Y=200 or higher, falling off steeply
  below that to a minimum of 200 blocks. Radio computers use power, about an HF radio's receive draw. The FCC badge
  is decorative (for role-play FCC servers); no powers. Fax/SSTV/WEFAX/APRS on the computer keep needing a radio
  interface between radio and computer (as built). Recipes: to be reviewed together next.


- **Answers (maintainer, 2026-11-05, round 2)**: these answer every open question below. **Keep as built:**
  - marine plan US by default;
  - digital channels stay mixed mode on receive;
  - WEFAX prints when AUTO is on;
  - everything received is saved;
  - the game name Pileup;
  - loot chances;
  - gospels only;
  - no ADS-B terrain, the beacon needs power, distances in m/km;
  - own ship hidden, and the ship name is used as the flight ID;
  - the badge stays in JEI and drops on death;
  - Everywhere keeps fading;
  - Modrinth Maven only, and the API version stays a separate 1.0;
  - scanner printing uses paper only;
  - the Radio Cloner recipe is approved.

  **Change** (Open-question fixes, below):
  1. Commercial radios ship **blank**, with no factory channels.
  2. **Remove the 7.060 "Server-wide net" marker**: it was a placeholder.
  3. The radio computer gets an **optional password**, set by its owner from the DOS shell.
  4. Solar/Forecast still need a receiver, but **try any connected radio** (wired, touching or through a radio
     interface), **or use an Ionospheric Processor connected straight to the computer**, with no radio needed.
  5. **Remove callsign addressing** from SSTV/WEFAX pictures.
  6. Found ham shacks are **dark**, with a dead or flat battery.
  7. The FCC badge stays decorative, but badge holders are **marked in the moderation computer**.
  8. Real-World Interface:
     - the **power switch is ops-only** too;
     - each player **clicks "I hold a licence" once** before their voice goes out through it;
     - add a **monitor speaker**.
  9. Website: the Modding API page goes under a new **"FOR DEVELOPERS"** sidebar heading.

(Agents: add questions here instead of stopping.)

- (Step 1) **Out-of-band receive without the mod.** Read "without it, radios still receive out of band" as: every ham
  transceiver and receiver tunes the extended range all the time (general coverage receive), and the MARS mod only
  unlocks transmit there. If you meant "the mod widens the dial", `RadioBlockEntity.tunesOutOfBand()` is the switch.
- (Step 1) **VHF tuning range is continuous 143-174 MHz** (2m minus a quarter of its width, up to the top of commercial
  VHF), so 148-150 MHz is tunable too (real MARS/CAP territory), not just 144-148 plus 150-174.
- (Step 1) **7.060 "Server-wide net"** sits in the ARRL CW/digital segment of 40m (US phone starts at 7.125). Kept as
  you wrote it; move it (to 7.260, say) if the net is meant to be voice.
- (Step 1) **The marine table defaults to the US plan** (22A, WX1-7, duplex 20/24-28/84-87); the international ITU
  table is there too (`MarineChannels.Region`). Which should the marine radios use by default?
- (Step 1) Recipes for review: MARS Mod shaped `WTW / _B_` (wire, redstone torch, HF circuit board), installed by a
  shapeless special recipe with the radio; Band Plan Sheet shapeless paper + ink sac.
- (Step 2) **Digital channels are mixed mode on receive**: a digital channel also hears analog FM on its frequency
  (most P25 radios ship like that). If you want digital-only receive, return early in `RadioBlockEntity.receiveVoice` /
  `PlayerRadio.receive` when the decoder is set and the packet is analog.
- (Step 2) **Encrypted without the key** plays garbled digital noise (as you asked); a real P25 radio would stay muted.
  Analog and ham radios hear any digital signal as a harsh buzz (real P25 on FM sounds like that).
- (Step 2) **One CTCSS tone per channel** (sent and needed to open), analog only; digital channels ignore it. Separate
  TX/RX tones and P25 NAC codes were left out.
- (Step 2) **Factory channels**: so a radio does something before step 6, VHF radios ship with the five MURS channels
  (the upgraded handheld adds 146.520), low band with 33.12/33.90/37.98/39.46 MHz simplex. Say if they should ship blank.
- (Step 2) **Knob stops at 1 and 16** (a rotary switch), no wrap. Mobiles reuse the VHF base station's power draw
  config (no new keys) and transmit power; handhelds are like the ham handheld.
- (Step 2) **Recipes for review**: Basic Commercial Handheld shaped `_L_ / SCM / _I_` (L lightning rod, S speaker,
  C any encased VHF crystal, M hand mic, I iron ingot); Upgraded `_G_ / RHR / _B_` (G glass pane, R redstone, H basic
  commercial handheld, B HF circuit board; its programming is not carried over); Commercial Mobile Radio
  `III / SCM / WBW` (I iron, S speaker, C any encased VHF crystal, M hand mic, W wire, B HF circuit board); Low Band
  Mobile Radio the same with an encased 10m crystal. Dyeing is vanilla's armour dye recipe via `#minecraft:dyeable`.
- (Step 3) **Marine plan default**: the radios follow the new server config `marine_channel_plan`, US by default
  (the Step 1 question). Say if INTERNATIONAL should be the default.
- (Step 3) **Dual watch priority**: DW hears the working channel and 16; a signal on 16 mutes the working channel
  until 16 has been quiet for 1.5 s (a real radio samples 16 every second or two). Tri-watch (16 + 9 + working) is not
  built; a handheld in a desk charger listens on its channel only.
- (Step 3) **Power numbers**: 1 W is 0.2 of the 25 W base's strength and 0.45 of the handheld's 5 W
  (`MarineRadioState.BASE_LOW_POWER` / `HANDHELD_LOW_POWER`); 25 W is the strength of a ham VHF base station, 5 W of
  a ham handheld. Channels 13 and 67 are always 1 W (no momentary high-power override as on real radios).
- (Step 3) **Left out on purpose**: marine radios are not dyeable; channel 70 refuses voice but there is no DSC
  distress button or MMSI; the WX channels receive whatever is on them (nothing broadcasts NOAA weather yet).
- (Step 3) **Recipes for review**: Marine Handheld `_L_ / SCM / _W_` (lightning rod, speaker, any encased VHF crystal,
  hand mic, waterproof wire); Marine Radio `III / SCM / WBW` (the commercial mobile's recipe with waterproof wire).
- (Step 4) **Recipes for review**: Camera shaped `_B_ / IGI / IRI` (stone button, iron ingot, glass pane, redstone);
  Photo Paper `PPP / PMP / PPP` (paper, empty map) makes 16; Color Photo Paper `PPP / PMP / RGB` (red, green, blue
  dye) makes 16.

- (Step 5) **No lock.** Anyone may use any computer and read its files. Say if it should lock to its placer. (Power:
  answered 2026-11-05, it now draws `computer_power_use` 8 FE/t while in use; see "First build" below.)
- (Step 5) **All floppies are registered now** (one item per planned program, steps 6-9 included); a program whose
  screen is not written opens a "not ready in this version" box. The falling-blocks game's disk is `floppy_falling_blocks`
  named "Falling Blocks" until step 8 picks its original name (rename only the lang, keep the id).
- (Step 5) **Scanner paper**: pictures print on color photo paper (a color "Photo"), else an empty map (a plain color
  "Printout"), else B&W photo paper; documents print as a written book at one sheet of plain paper a page. Scanner
  prints are not counted by `camera_photo_cap`. Say if a book should also need a book (leather) or ink.
- (Step 5) **Files follow the computer**: its id rides on the item, so breaking and placing it keeps its files; a creative
  copy of the item shares the same disk. Disk 1 MB, 256 files by default.
- (Step 5) **Recipes for review**: Radio Computer `IBI / RCR / III` (iron, HF circuit board, redstone, comparator);
  Computer Monitor `III / IGI / BRB` (iron, glass pane, stone button, redstone); Scanner `GGG / IRI / IKI` (glass pane,
  iron, redstone, ink sac); Floppy Disk x2 (iron nugget, redstone, black dye, paper); program disks = blank disk + book
  and quill / book / band plan sheet / comparator / daylight detector / compass / empty map / paper / painting /
  spyglass / feather (book editor, logbook, frequency manager, programmer, solar, APRS, WEFAX, radio fax, SSTV, DF,
  ADS-B).

- (Step 6) **How the computer reaches a commercial radio**: the scanner's glass takes commercial handhelds and mobile
  items (the "programming cradle"), and placed mobiles are reached through the data wire / touching (the "programming
  cable"). No separate programming-cable item. Say if you want one (a cable item from the computer to a handheld in
  the hand, as real CPS cables are).
- (Step 6) **Hidden keys can never be made readable** without typing the key in again (fixed in
  `CommercialProgramming.resolve`: the readable flag used to come from the request). A hidden key cannot be copied to
  another position or radio; a codeplug file keeps it as `****` and is refused by a radio with no key at that place.
- (Step 6) **Solar and forecast need a receiver** like the Teletype: a powered radio with a real antenna wired to the
  computer ("WWV geophysical alert / NOAA broadcasts"), or an Ionospheric Processor wired or beside one of its radios;
  `solar_weather_station_needs_radio = false` drops it. Say if the computer should get them without a radio.
- (Step 6) **Logbook "heard"** lists players heard talking (voice only, not CW or data) by the radios touching or wired
  to the computer in the last 10 minutes, with the player's callsign (or name). The report is estimated from the best
  strength against the radio's noise now. Times are real UTC (as hams log), plus the in-game day stamp in the notes
  column of the ADIF.
- (Step 6) **Books print in volumes of 64 pages** (one stack of paper in the tray), not the game's 100. EDIT and
  the shell now refuse a print longer than 64 pages with a message instead of "no paper".

- (Step 7) Radio programs: interface required, who answers a fax, WEFAX AUTO printing charts, addressed pictures,
  saving everything received: see the Step 7 handoff's open questions.
- (Step 8) Secret programs and the ham shack: the name Pileup, loot chances, shacks found powered, music only at the
  screen, gospels only: see the Step 8 handoff's open questions.
- (Step 9) ADS-B: range numbers and terrain, power draw, NM or km, own ship on the scope, ship name as flight ID: see
  the Step 9 handoff's open questions.
- (Step 10) FCC gear: necklace vs chest slot, badge in JEI, recipes, what a badge should do: see the
  Step 10 handoff's open questions.
- (Step 11) Real-World Interface: op-only by default, config synced to clients, Everywhere and fading, what it
  should hear (commercial modules, CW), local monitor speaker, ID: see the Step 11 handoff's open questions.
- (Step 12) Modding API: a real Maven and a `group`, the API version number, the wiki page's place in the sidebar: see
  the Step 12 handoff's open questions.

## Handoff notes

### Step 1
**UNBUILT / UNTESTED** (2026-11-04): the maintainer needed the CPU, so no Gradle command was run (no compile, no
`test`, no `runGameTestServer`, no `runData`) and nothing is committed. Everything was checked by reading only.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava` (or `build -x test`), watching `-Xlint:all`. Riskiest
   bits: the `RadioBlockEntity.applyImplicitComponents/collectImplicitComponents` overrides,
   `RadiocraftDataComponent.MARS_MOD` (`StreamCodec.unit` type inference), the loot `CopyComponentsFunction` in
   `RadiocraftBlockLootProvider`, the pattern variable in `ManpackRadio.transmitMorse`, the new `defineListAllowEmpty`
   lists in `RadiocraftServerConfig`.
2. `./gradlew runData` and commit `src/generated` (new lang keys, the two recipes, radio loot tables now copying the
   `radiocraft:mars_mod` component, config names for the new keys).
3. `./gradlew test` (new: `BandEdgesTest`, `bandplan/BandPlanTest`, `MarineChannelsTest`; extended: `BandTest`,
   `DetuningTest`, `ConfigNamesTest`; `ConfigRoundTripTest` and `PatchouliBookTest` cover the config and the book).
4. `./gradlew runGameTestServer` (new `BandEdgeGameTests`, batches `band_edges` and `band_plan`; changed
   `RadioGameTests.frequencyStepsAndClampsToBand` and `AllBandRadioGameTests.tuningWorksOn2m`, whose radios now tune
   past the band). Watch for other tests that step a radio below its band bottom and expect a clamp.
5. Then commit (stage by folder: `git add src doc tools README.md`).

**Bands** (`common/radio/Band.java`): new `Band.LOW_BAND` ("low_band", 30-50 MHz, LOS 650, day skip 3000-7000 only)
and `Band.VHF_COMMERCIAL` ("vhf_commercial", 150-174 MHz, like 2m). `Band.getDefaults()` is still the five **ham**
bands (everything that iterates bands for hams keeps working: analyzer, DF, Jade, `/bandcalculate`); use
`Band.getCommercialDefaults()` / `Band.getAllDefaults()` for the rest. New on `Band`: `isAmateur()`, `contains(hz)`
(50 Hz edge slack), `width()`, `wavelengthMeters()`, `displayName()` ("Low Band", "VHF Comm"), `conditionName()` (low
band uses 10m's solar weather unless a solar event JSON names `low_band`). `isVhf()` is now `minFrequency >= 100 MHz`
(VHF antenna network, aurora, sporadic E): true for 2m and commercial VHF, false for low band (HF network; wire
antennas compute their SWR from frequency, so a dipole with 2-block arms is a low-band antenna).
`Band.getBand(float)` is now deterministic (narrowest band containing it) and falls back to the ham band whose MARS
range holds the frequency (13.95 MHz -> 20m), so antennas and tuners see out-of-band signals on their band; it no
longer logs an error on a miss. Marine is **not** a separate Band: it is a channel table inside commercial VHF.

**Config** (`RadiocraftServerConfig`): band sections "Commercial Low Band (30-50 MHz)" and "Commercial VHF (150-174 MHz
and marine)" with keys `low_band_los`, `vhf_commercial_minFrequency` ... (`configKeyPrefix()`; the ham bands keep their
old `20mm_los` keys). Section "Band Plan": `band_plan_segments`, `band_plan_markers` (string lists). Section "MARS
Mod": `mars_mod_splatter` (default 2.0). Server configs are synced to clients, so the client reads the band plan
straight from the config (no packet).

**Tuning/transmit API** (`common/radio/BandEdges.java`, pure): `tuneMin/tuneMax(band)` (HF +/- a quarter of the band
width; 2m from 143 MHz to the top of commercial VHF; commercial bands exactly their band), `canTune`,
`canTransmit(band, hz, marsMod)`, `isOutOfBand`, `splatterReach` (config), `snapToTuningRange(band, hz, step, steps)`
(grid anchored on the band bottom). `RadioBlockEntity`: `tunesOutOfBand()` (false for repeaters),
`limitsTransmitToBand()` (false for repeaters and receivers), `hasMarsMod()/setMarsMod()`, `isOutOfBand()`,
`isTransmitInhibited()` (client-safe; drives OUT OF BAND), `getDetuning(txHz, splatterReach)`. Voice, data
(TNC/RTTY/DTMF) and CW (`HFRadioBlockEntity.canTransmitCW`) all refuse while inhibited. Handheld:
`PlayerRadio.transmitFrequency(freq, offset)`, `handheldMayTransmit(stack, hz)`, `handheldInhibited(stack, cap)`; the
capability clamps to the tuning range. Manpack: `ManpackRadio.mayTransmit(state, mars)`. **Commercial radios (step 2)
should pass their own commercial `Band`** to the antenna, so they are in band (no splatter), and override
`limitsTransmitToBand()` / `tunesOutOfBand()` if they extend `RadioBlockEntity`.

**Splatter**: `IAntennaPacket.getSplatterReach()` (default method: the config value when the packet's frequency is
outside the packet's band, else 1). `Detuning.ssb(..., reach)` / `Detuning.fm(..., reach)` stretch the splatter zone
(SSB: qrm only, out to reach x 10 kHz, and splatter starts nearer the centre inside the window; FM: splatter zone and
channel-step splatter x reach). Wired into block radios, handhelds, docked handhelds and the manpack. Out of band means
outside the *band passed with the packet*: a MARS 2m handheld on 155 MHz splatters (it passes "2m"), a commercial radio
passing `vhf_commercial` will not.

**MARS mod**: item `radiocraft:mars_mod` (`MarsModItem`: `fits(stack)`, `isFitted(stack)`), data component
`radiocraft:mars_mod` (`RadiocraftDataComponent.MARS_MOD`, `Unit`), special recipe `MarsModInstallRecipe` (serializer
`radiocraft:mars_mod_install`, recipe id `radiocraft:mars_mod_install`) that copies the radio stack and adds the
component. Fits HF radios (all crystals), all-band, QRP, VHF base station, VHF handheld, HF manpack; not receivers or
repeaters. Block radios read it in `applyImplicitComponents` and write it in `collectImplicitComponents`; every
`RadioBlock`'s loot table copies it (`copy_components` from the block entity). BE tag key `marsMod` (synced). A
"MARS mod fitted" tooltip line for any modded item (`ItemTooltipEvents`). `/rcdebug mars <pos> on|off`; `/rcdebug
radio` prints mars/outOfBand/txInhibited and the tuning range.

**Band plan**: `common/radio/bandplan/BandPlan` (pure record): `Segment(fromHz, toHz, use, colour)`,
`Marker(hz, label)`, `Row(band, segments, markers)`; `parse/parseSegment/parseMarker`, `defaults()`, `current()`
(config, re-parsed when the lists change), `rows(bands)`, `sheetBands()` (all 7 bands), `useAt(hz)`, `segmentAt`,
`markersNear(hz, tol)`, `legendColours()`, `rgb(colour)`, `legend(colour)`, `COLOURS` (category names; `#RRGGBB` is
also allowed). The radio computer's band plan app (step 5) should draw `BandPlan.current().rows(BandPlan.sheetBands())`.
`/rcdebug bandplan [mhz]`.

**Marine channels**: `common/radio/MarineChannels` (pure): `table(Region.US | INTERNATIONAL)`, `Channel(name,
transmitHz, receiveHz, use, lowPowerOnly, voice)` with `isDuplex()`, `isReceiveOnly()` (WX), `coastTransmitHz()`;
`byName`, `indexOf`, `step(region, name, n)` (knob order, wraps), `channelAt(region, hz, tol)`, `shipFrequencyHz(n)`;
constants `DISTRESS` ("16") and `HAILING` ("9"). Step 3 builds the marine radios on this.

**Band Plan Sheet**: item `radiocraft:band_plan` (`BandPlanItem`, stacks to 16), client `BandPlanScreen` (scrollable
bars, marker ticks and labels, hover tooltip, legend). Placeholder textures from `tools/band_plan_assets.py`; both new
items are on `doc/ModelDesignBounty.md`.

**Other**: `RedstoneInterference.bandFactor` uses the band's wavelength (low band and commercial VHF hear 0.1).
`TerrainPropagation.takeoffDegrees` and `FaxRates.slower` treat low band like 10m; `WideBandReceiveAntennaType` knows
both new bands; `FiveBtvAntennaType` matches on ham bands only. Handbook renamed "ARRL Radiocraft Handbook" (book.json
name and landing text, `/radio guide` messages, config comment, README). Patchouli: new `propagation/band_plan` and
`radios/mars_mod`, and a "Commercial bands" page in `propagation/bands`. NeedsTesting has a "1.12 step 1" section;
`doc/ReleaseNotes-1.12.md` is created.

**Recipes added** (for review): MARS Mod shaped `WTW / _B_` (W wire, T redstone torch, B HF circuit board); the MARS
install special recipe (radio + mod, shapeless); Band Plan Sheet shapeless paper + ink sac.

**Left undone / gotchas**: the 3D handheld item renderer (`HandheldItemRenderer`) still shows TX rather than OOB when
keyed out of band (the screen's LCD does show OOB). The solar event JSONs were not given `low_band` entries (it follows
10m). The website wiki was not touched (step 13). Ham VHF radios still step 20 kHz, so 12.5/25 kHz commercial and
marine channels (154.570, 156.825) are only exact on the channelized radios of steps 2 and 3.

### Step 2
**UNBUILT / UNTESTED** (2026-11-04): no Gradle command run, nothing committed; checked by reading only. Builds on the
uncommitted Step 1.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava`. Riskiest: `PlayerRadio.SynchronousRadioState`'s
   constructor (final fields assigned in two branches), `CommercialProgram.STREAM_CODEC` (`ByteBufCodecs.list`
   inference), `DebugCommands.commercialActions` (brigadier `ArgumentBuilder<S, ?>` generics),
   `RadiocraftCapabilities.handhelds()` (stream concat), `CommercialRadioModel.band` (multi-catch), the colour lambdas
   in `ClientSetupEvents`.
2. `./gradlew runData` and commit `src/generated`: lang (`addCommercialRadios` in the language provider), blockstates
   and block item models for `commercial_mobile` / `low_band_mobile`, the four recipes, POWER/COAX tags, radio loot
   tables (now also copying `radiocraft:commercial_program` and `minecraft:dyed_color`).
3. `./gradlew test`: new `voice/DigitalVoiceTest`, `commercial/CommercialChannelTest` (channels, programs, models, the
   programming API on a stand-in radio; `CommercialRadioModel` falls back to `Band.getAllDefaults()` without config).
   `ModelFilesTest` and `PatchouliBookTest` cover the new models and `radios/commercial_radios` (its mobile spotlight
   needs runData's item model).
4. `./gradlew runGameTestServer`: new `CommercialRadioGameTests` (batch `commercial_radios`, 155.0125/155.0375/155.0625
   MHz over bare connectors). Re-run the handheld, repeater, desk charger and APRS tests: `PlayerRadio`,
   `DockedHandheld`, `RadioBlockEntity.receiveVoice/transmitVoice` and `VHFRepeaterBlockEntity` changed.

**Channel model** (`common/radio/commercial`, pure): `CommercialChannel(name, rxHz, txHz, digital, ctcssTone, keyId,
keyReadable)` (txHz 0 = receive only, rxHz 0 = `BLANK`, `KEY_HIDDEN` -1, `NAME_MAX` 8, `CHANNEL_STEP_HZ` 12500;
`mode()`, `decoder()`, `analogTone()`, `withKeyHidden()`, `sanitized()`, text form `format()`/`parse()`:
`NAME|rxMHz|txMHz or = or RX|FM or DIG|tone|key|R or H`). `CommercialProgram(channels <= 16, selected)` (`active()`,
`channel(i)`, `select`, `turn` (clamps 1-16), `with`, `withChannels`). Data component `radiocraft:commercial_program`
(`RadiocraftDataComponent.COMMERCIAL_PROGRAM`) on the handhelds and the mobiles' items; the mobile BE saves it as tag
`program` and syncs it. `CommercialRadioModel`: `BASIC_HANDHELD` (vhf_commercial, analog), `UPGRADED_HANDHELD`
(vhf_commercial + 2m, digital, encryption, display), `VHF_MOBILE` (vhf_commercial, digital, encryption, display),
`LOW_BAND_MOBILE` (low_band, analog); `check(channel)` gives a problem key or null; `factoryProgram()`; `bandFor(hz)`.

**Programming API for step 6** (`CommercialProgramming`): `of(ItemStack)` (handheld, or mobile item) / `of(Level,
BlockPos)` (placed mobile) give a `Target` (`model()`, `program()`, `storeProgram()`); `read(target)` (16 positions,
unreadable keys as `KEY_HIDDEN`); `write(target, channels)` (all or nothing, `Result` of `Problem(position, key)`, keys
under `message.radiocraft.commercial.*`, lang done); `writeChannel`, `clear`, `select`, `factoryReset`. Writing back a
`KEY_HIDDEN` keeps the radio's key at that position, so a programmer can edit a radio without learning its keys.
`/rcdebug commercial hand|<pos> [set <ch> <spec> | clear <ch> | select <ch> | factory]` drives it now.

**Digital voice** (`common/radio/voice/DigitalVoice`, pure): `Mode(digital, keyId)` on every `AntennaVoicePacket`
(`getVoiceMode()`, captured at construction from a thread-local the transmitter sets with `DigitalVoice.push/pop`).
Set in `RadioBlockEntity.transmitVoice` (hook `transmitMode()`, analog by default), per handheld in
`PlayerRadio.acceptVoicePacket`, and passed through by `VHFRepeaterBlockEntity.transmitMode()` (`current()`: a relay
from inside `receiveVoice` keeps the original's mode; its own beeps are analog). Receivers call
`DigitalVoice.receive(decoder, mode, strength)` -> `Reception` (ANALOG, CLEAR, WARBLE, SCRAMBLED, BUZZ, DROPPED) and
`render(...)`: wired into `RadioBlockEntity.receiveVoice` (hook `digitalDecoder()`, null = analog; `getLastReception()`
for tests and debug), `PlayerRadio.receive` and `DockedHandheld`. `CLIFF` is computed where FM would be 70% static
(dropout plus hiss), about 0.1 strength; `EDGE` = 1.3 x cliff. Decoded digital goes into the mix as non-FM (fmSignal
-1: no hiss, no squelch tail). New `BEVoiceReceiver.receive` overload takes the pcm to play. Moderation records at
the source, so it has the clear voice.

**Radios**: `CommercialHandheldItem extends VHFHandheldItem` (items `basic_commercial_handheld`,
`upgraded_commercial_handheld`; statics `program`, `activeChannel`, `setProgram`, `selectChannel`, `syncState` (copies
the channel into `HandheldRadioState`), `mayTransmit`, `transmitBand`, `decoder`). `PlayerRadio.SynchronousRadioState`
reads the channel for commercial items (rx/tx, tone, `txBand` = the model's commercial band, 12.5 kHz step, mode,
decoder); `handheldMayTransmit/handheldInhibited` go through `mayTransmit`. **Commercial radios pass their own band**
(vhf_commercial, low_band, or 2m for the upgraded handheld on 2m), so BandEdges never sees them out of band. Blocks
`commercial_mobile`, `low_band_mobile` (`CommercialMobileBlock extends RadioBlock`; one BE type `COMMERCIAL_MOBILE` ->
`CommercialMobileBlockEntity extends RadioBlockEntity implements CommercialProgramming.Target`): channelized
(`updateFrequency(steps)` turns the knob, `snapToTuningRange` no-op, `tunesOutOfBand` false), FM, band = model's home
band, blank/receive-only channels inhibit TX, display text `displayLine/displayStatus` (used by
`RadioDisplayRenderer`), lamps in `RadioDisplays`. `CommercialMobileMenu`, screens `CommercialMobileScreen` and
`CommercialHandheldScreen` (plain fills), packet `SCommercialRadioPacket` (knob only). MARS mod, APRS module, callsign
prompt, memory packet and DTMF refuse commercial radios; `SHandheldRadioUpdatePacket` takes only power/PTT/gains.

**Dye**: `src/main/resources/data/minecraft/tags/item/dyeable.json` (hand-written) adds the four items, so vanilla's
armour dye recipe colours them. Items tint layer 0 (`DyedItemColor`); the placed mobile tints from its BE
(`RegisterColorHandlersEvent.Block`) and redraws on sync. The mobile takes and gives back `dyed_color` and
`commercial_program` via `apply/collectImplicitComponents`.

**Art**: placeholders from `tools/commercial_radio_assets.py` (textures, item models, block models with a `DISPLAY`
element), all on `doc/ModelDesignBounty.md`. NeedsTesting has a "1.12 step 2" section; release notes and the Handbook
page `radios/commercial_radios` written. No config keys added.

**Left undone / gotchas**: a commercial handheld in a desk charger shows the charger's own geo radio. No 3D item
models; Jade shows nothing new. A repeater that queues audio (not relayed from inside `receiveVoice`) would send it
analog. A data burst from a radio on a digital channel would go out "digital" (no commercial radio sends data yet).
Open questions are under "Open questions for the maintainer" (Step 2).

### Step 3
**UNBUILT / UNTESTED** (2026-11-04): no Gradle command run, nothing committed; checked by reading only. Builds on the
uncommitted Steps 1 and 2.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava`. Riskiest: `PlayerRadio.SynchronousRadioState` (now
   three branches assigning every final field: commercial, marine, ham), the new private
   `PlayerRadio.transmitAudioPacket(..., power)` overload next to the interface ones, `RadiocraftCapabilities.handhelds()`
   (nested `Stream.concat` with `Stream.<Item>of`), `DebugCommands.marineActions` (brigadier generics, same shape as
   `commercialActions`), the exhaustive enum switch in `MarineRadioState.press`, `MarineRadioState.STREAM_CODEC`
   (4-field `StreamCodec.composite`).
2. `./gradlew runData` and commit `src/generated`: lang (`addMarineRadios`), blockstate and block item model for
   `marine_radio`, the two recipes, POWER/COAX tags, the radio loot tables (now also copying
   `radiocraft:marine_radio`), the config name for `marine_channel_plan`.
3. `./gradlew test`: new `marine/MarineRadioStateTest`; `ConfigNamesTest` gained a line; `ModelFilesTest` and
   `PatchouliBookTest` cover the new models and `radios/marine_radios` (its `marine_radio` spotlight needs runData's
   item model).
4. `./gradlew runGameTestServer`: new `MarineRadioGameTests` (batch `marine_radios`; channels 68, 26 (157.300), 72 and
   16, bare connectors). Re-run the handheld, desk charger and commercial tests: `PlayerRadio`, `DockedHandheld`,
   `HandheldRadiation`, `CommercialMobileBlockEntity` (now `implements ChannelDisplay`) and `RadioDisplayRenderer`
   changed.

**State** (`common/radio/marine/MarineRadioState`, pure record): `(channel, previous, highPower, dualWatch)`, `DEFAULT`
(16, HI, no DW), `CODEC`/`STREAM_CODEC`; `channel(region)` (falls back to 16 if the plan has no such channel), `turn`
(wraps, as `MarineChannels.step`), `select(region, name)` (unknown names return `this`), `sixteenNine` (work -> 16 ->
9 -> work; `previous` holds the working channel), `togglePower`, `toggleDualWatch`, `press(region, keyOrdinal, value)`
with `enum Key { TURN, SIXTEEN_NINE, HIGH_LOW, DUAL_WATCH }`, `receiveHz/transmitHz` (duplex: coast/ship),
`mayTransmit` (not WX, not 70), `transmitsHigh` (13/15/17/67 forced low), `basePower/handheldPower`, `watchHz` (156.8
MHz or 0), `displayLine/displayStatus`. Constants `SIXTEEN_HZ`, `CHANNEL_STEP_HZ` (25 kHz), `PRIORITY_HOLD_NANOS`
(1.5 s), `BASE_LOW_POWER` 0.2, `HANDHELD_LOW_POWER` 0.45. Nested `PriorityWatch` (volatile nanos; `heardPriority`,
`prioritySpeaking`, `reset`). `MarineRadios`: `region()` (config `MARINE_CHANNEL_PLAN`, US before the config loads),
`band()` (commercial VHF). Data component `radiocraft:marine_radio` (`RadiocraftDataComponent.MARINE_RADIO`).

**Radios**: block `marine_radio` (`MarineRadioBlock extends RadioBlock`; BE type `MARINE_RADIO` ->
`MarineRadioBlockEntity extends RadioBlockEntity implements ChannelDisplay`): band = commercial VHF (in band, no
splatter, VHF antenna network), `getRadioState/setRadioState`, `pressSixteenNine/togglePower/toggleDualWatch`,
`updateFrequency(steps)` turns the channel, `transmitPower()` 1 or 0.2, `isTransmitInhibited` on WX/70, `getDetuning`
also hears 16 on DW, `receiveVoice` gives 16 priority, `isPriorityBusy()`; saves tag `marine`; takes and gives the
component via `apply/collectImplicitComponents`; hand mic cord, Sable and power as every radio block.
`MarineRadioMenu`, `MarineRadioScreen`. Item `marine_handheld` (`MarineHandheldItem extends VHFHandheldItem`: `is`,
`state`, `setState` (+`syncState` into `HandheldRadioState`: receive frequency, duplex offset), `channel`,
`mayTransmit`, `transmitPower`), screen `MarineHandheldScreen`. `PlayerRadio.SynchronousRadioState` has `marine`,
`watchHz`, `txPower`; dual watch and priority in `PlayerRadio.receive` (field `priorityWatch`); handheld voice goes
out through `HandheldRadiation.voice(..., power)` (new overload, strength x power capped at 1). Packet
`SMarineRadioPacket(slot, pos, key, value)` (handheld slot, or -1 and the open menu's pos).

New shared helpers: `ChannelDisplay` (commercial and marine BEs; `RadioDisplayRenderer` draws any of them) and
`VHFHandheldItem.isChannelized(stack)` (commercial or marine: no keypad/DTMF, memories, APRS module, MARS mod or ham
callsign prompt; used in PlayerRadio, MarsModItem, CallsignPrompt, SHandheldMemoryPacket, AprsModuleInstallRecipe,
DeskChargerBlockRenderer). `SHandheldRadioUpdatePacket` re-syncs a marine handheld from its channel and ignores client
frequencies. `DockedHandheld` listens on the channel. `/rcdebug marine hand|<pos> [channel <name> | sixteen | hilo |
dw]`. Config: section "Marine Radios", `marine_channel_plan` (US | INTERNATIONAL).

**Art**: placeholders from `tools/marine_radio_assets.py` (item texture/model, block case/front textures, block model
with `DISPLAY`), lamps in `RadioDisplays` ("marine_radio"); on `doc/ModelDesignBounty.md`. NeedsTesting "1.12 step 3"
section, release notes and the Handbook page `radios/marine_radios` written.

**Recipes added** (for review): Marine Handheld shaped `_L_ / SCM / _W_` (L lightning rod, S speaker, C any encased VHF
crystal, M hand mic, W waterproof wire); Marine Radio `III / SCM / WBW` (iron, speaker, VHF crystal, hand mic,
waterproof wire, HF circuit board: the commercial mobile's recipe with waterproof wire).

**Left undone / gotchas**: no dual watch for a handheld in a desk charger. Changing `marine_channel_plan` on a live
server leaves a placed radio's `frequency` field stale until its channel changes or it reloads (transmit always reads
the plan). A marine handheld's `HandheldRadioState` frequency is only re-synced when its state is set (a new item
starts on 156.800). No DSC button/MMSI, no NOAA weather voice on the WX channels, not dyeable, no 3D item model.
Website wiki not touched (step 13). Open questions are under "Open questions for the maintainer" (Step 3).

### Step 4
**UNBUILT / UNTESTED** (2026-11-04): no Gradle command run, nothing committed; checked by reading only. Builds on the
uncommitted Steps 1-3. Includes the maintainer's follow-ups (viewfinder and zoom, fax photo paper, photo cap with
deleting).

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava`. Riskiest: `client/photo/PhotoCapture.grab` (NativeImage
   read of the main target's colour texture), `client/photo/CameraClient` (NeoForge client events: `ViewportEvent.ComputeFov`,
   `InputEvent.MouseScrollingEvent`, `RenderHandEvent`, `RenderGuiEvent.Post`, `ItemTooltipEvent`),
   `SPhotoChunkPacket.STREAM_CODEC`, `PhotoInfo`'s codec, `PhotoCommands` (brigadier; it merges into the existing
   `/radiocraft` root), the radio fax's supplies `ItemStackHandler.deserializeNBT` override.
2. `./gradlew runData` and commit `src/generated`: lang (`addCamera`), the three recipes, config names (Camera section).
3. `./gradlew test`: new `common/photo/PhotoPaletteTest`, `PhotoImageTest`, `PhotoUploadTest`, `PhotoAlbumsTest`,
   `MapPalettesTest` (loads `MapColor` without bootstrapping; if that fails, move it to a GameTest). `ConfigNamesTest`
   gained two lines.
4. `./gradlew runGameTestServer`: new `CameraGameTests` (fake players; `photoCapAndDeleting` sets
   `camera_photo_cap` to 2 inside try/finally and assumes fake players are not ops), and in `RadioFaxGameTests`
   `photoGoesLikeAMap` (144.830) and `photoPrintsOnPhotoPaper` (144.850), batch `fax`. Re-run all fax tests: the fax
   has a fourth supply slot now.
5. runClient (NeedsTesting "1.12 step 4"): frame orientation and colour order, the viewfinder matching the photo at
   each zoom, the fax's PHOTO slot frame against the panel art.

**Taking a photo**: hold right click to aim (`CameraItem.use` checks `camera_enabled`, the cooldown, paper and on the
server the cap, then `startUsingItem`; `UseAnim.SPYGLASS`). Letting go (`releaseUsing`) fires the shutter on the
server (`CameraItem.fireShutter(player, hand)`: checks again, `PhotoUploads.open` -> nonce, `CPhotoShutterPacket`,
click, cooldown `camera_cooldown_seconds`) and on the client holds the zoom (`CameraClient.shutterReleased`, up to
2 s). The client grabs the next frame at `RenderLevelStageEvent.Stage.AFTER_LEVEL` (`PhotoCapture`), quantises it and
sends 4 `SPhotoChunkPacket`s of 4096 bytes; `PhotoUploads.accept` takes each chunk once for the open nonce within 200
ticks; `develop` checks the cap again, takes the paper, prints with `PhotoPrints.photo` (sanitised; B&W greyscaled on
the server), records it in `PhotoLedger` and gives it to the player.

**Viewfinder and zoom** (`client/photo/CameraClient`): while aiming, the hand is hidden and a GUI overlay darkens all
but the middle square (exactly the square `PhotoCapture` takes, in GUI coordinates), with corner marks, a centre cross,
the zoom ("1.5x"), the paper (COLOR / B&W / NO PAPER) and a hint. The scroll wheel (cancelled, so not the hotbar) steps
the zoom 1x-3x by 0.5; the zoom is done in `ViewportEvent.ComputeFov` (only `usedConfiguredFov`, so not the hand) as
`2 atan(tan(fov/2) / zoom)`, so it is a true magnification and works with FOV effects off. The zoom is remembered
between photos. `CameraClient.zoomedFov` is static and pure.

**Fax** (`RadioFaxBlockEntity`): supply slot `SLOT_PHOTO` (3, `SUPPLY_SLOTS` 4) takes either photo paper. A received
photo prints on it when it has some (`supplySlotFor`, now an instance method), else on an empty map. Colour paper (or a
B&W photo): the same photo (shared map). B&W paper and a colour photo: `photoCopy` makes a new greyscale photo (new map
id, `PhotoInfo.colour` false). Old saves load with three supply slots and are grown to four in the handler's
`deserializeNBT`. `RadioFaxMenu.SUPPLY_X` has a fourth position (86); `RadioFaxScreen` draws that slot's frame with fills
and labels it PHOTO. `FileTransfer.describe` says PHOTO for photos, so both LCDs and logs do.

**Photo cap and deleting**: config `camera_photo_cap` (default **0 = no cap**, 0-100000). `PhotoAlbums` (pure: per
player list of `Photo(mapId, day, takenAt)`, `atCap(count, cap)`) saved by `PhotoLedger` (SavedData
`radiocraft_photos` on the overworld). Every camera photo is recorded, cap or not, so turning a cap on later counts
photos already taken; it counts photos kept (deleting frees a place), not photos ever taken. **Creative players and
operators (permission level 2) are exempt** (their photos are still recorded). Fax prints and B&W fax copies are not
counted. At the cap (`PhotoLedger.refuseOverCap`, checked on aim, on shutter and on develop) the player gets a chat line
with a [Choose photos to delete] link to `/radiocraft photos [page]` (`PhotoCommands`: own photos only, oldest first,
8 a page, "#map, day N (date time)", [Delete]). Delete asks first (`/radiocraft photos delete <map>`), and only
`... confirm` deletes: `PhotoLedger.delete` takes it out of the album and blanks the map's picture through
`updateColor` (holders see it go). Every copy (cartography copies, faxed copies sharing the map) goes blank; a photo
item whose map is blank shows a red "Photo deleted" tooltip (`CameraClient.onTooltip`, `PhotoLedger.isBlank`). The map
file stays (a blank map, small). No thumbnails in the list (chat cannot show them cheaply) and one photo per
confirmation; a screen with thumbnails and multi-select could come with the step 5 computer.

**API for steps 5 and 7** (package `common/photo`; pure classes have no game imports):
- `PhotoImage` (pure): `SIZE` 128, `PIXELS`, `PixelSource`, `downscale`, `greyscale`, `quantise` (Floyd-Steinberg),
  `photo`, `toRgb`. `PhotoPalette` (pure): `nearest`, `contains`, `rgbOf`, `greys()`, `sanitised`, `greyscaled`,
  `luminance`. `MapPalettes.vanilla()/greys()`.
- `PhotoUpload` (pure chunking), `PhotoUploads.open/accept/develop/openNonce/close`.
- `PhotoPrints.photo/print/pixels/isPhoto/info`; data component `radiocraft:photo` (`PhotoInfo`: photographer,
  colour, source).
- `PhotoLedger.get(server).albums()`, `refuseOverCap`, `record`, `delete`, `isBlank` (the computer's photo app could
  list and delete with these).
- Client: `PhotoCapture.requestFrame(Consumer<int[]>)`, `CameraClient.zoom()/aiming()`.

**Items**: `camera` (`CameraItem`: `fireShutter`, `isPaper`, `paperFor`, `takePaper`, `cooldownTicks`), `photo_paper`,
`color_photo_paper`. Creative tab after the Band Plan sheet. Placeholder textures from `tools/camera_assets.py`; on
`doc/ModelDesignBounty.md` with the shutter sound, the viewfinder art and the fax panel's fourth slot. Handbook entry
`digital/camera`, release notes and NeedsTesting updated.

**Recipes added** (for review): Camera `_B_ / IGI / IRI` (stone button, iron, glass pane, redstone); Photo Paper
`PPP / PMP / PPP` x16; Color Photo Paper `PPP / PMP / RGB` x16.

**Left undone / gotchas**: no 3D item model or flash; the shutter is the stone button click. Aiming slows the player
like drawing a bow. The capture reads the full frame from the GPU once per photo; shader mods may change what the main
target holds. If the server refuses on release (cap reached meanwhile) the client's held zoom just times out. Deleting
a photo also blanks fax copies that share its map (only B&W fax prints have their own). Fake players in GameTests must
not be ops for the cap test. Website wiki not touched (step 13). The recipe review is under "Open questions" (Step 4).

### Step 5
**UNBUILT / UNTESTED** (2026-11-04): no Gradle command run, nothing committed; checked by reading only. Builds on the
uncommitted Steps 1-4 (uses Step 1's `BandPlan` and Step 4's `PhotoPrints`/`MapPalettes`; touches nothing of theirs).

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava`. Riskiest: `client/screens/computer/DosRenderer`
   (writes quads straight into `g.bufferSource()` with `RenderType.gui()` and `font.drawInBatch`, then `g.flush()`, to
   avoid the deprecated `drawManaged`), `ComputerSession.capture` (copied from `ModerationDesk.capture`),
   `DosImage` (`DynamicTexture`/`NativeImage`), `ComputerData.STREAM_CODEC` (`stringUtf8(32).apply(list(64))`),
   `RadioComputerMenu`'s buffer constructor (reads two `BlockPos`), the `switch` on `char` constants in
   `DosRenderer.lines`, the item-colour registration in `ClientSetupEvents` (stream of `DeferredHolder::get` to
   `ItemLike[]`). Source files hold literal box-drawing characters (UTF-8; the build sets UTF-8).
2. `./gradlew runData` and commit `src/generated`: blockstates and block item models for `radio_computer`,
   `computer_monitor`, `scanner` (`complexHorizontalBlockWithItem`), lang (`addRadioComputer`), 15 recipes, loot
   tables (the computer's copies `radiocraft:computer`), `radiocraft:data_blocks` and pickaxe tags, config names
   (Radio Computer section).
3. `./gradlew test`: new `common/computer/ComputerStorageTest`, `ComputerUploadsTest`, `ComputerAppsTest`,
   `BookPagesTest`, `client/screens/computer/TextGridTest`; `ConfigNamesTest` gained a line. `ModelFilesTest` and
   `PatchouliBookTest` cover the new models and the `computer` category (its crafting pages and icons need runData).
4. `./gradlew runGameTestServer`: new `RadioComputerGameTests` (no batch, no radio traffic).
5. runClient (NeedsTesting "1.12 step 5"): the text mode (glyph centring, box lines, scaling), keyboard capture (E,
   Escape), the monitor mirror, the scanner panel.

**Blocks** (no power, no ticking): `radio_computer` (`RadioComputerBlock`/`RadioComputerBlockEntity`, a
`StationTerminal`), `computer_monitor` (`ComputerMonitorBlock`, empty `ComputerMonitorBlockEntity` for its renderer;
goes on top of the computer), `scanner` (`ScannerBlock`/`ScannerBlockEntity`, menu `ScannerMenu`, screen
`ScannerScreen`). Right click the monitor (or the computer, if it has a monitor) to open; a floppy used on either goes
in the drive; sneak + empty hand on the computer ejects; breaking it drops the floppy. The computer's item keeps
`ComputerData(id, installed)` (component `radiocraft:computer`); its files are a `ComputerFileStore` SavedData named
`radiocraft_computer_<uuid>` in the overworld's data folder, so they follow the id. Config "Radio Computer":
`computer_storage_kb` (1024), `computer_max_files` (256); one file at most 256 KB (`ComputerStorage.MAX_FILE_BYTES`).

**Floppies** (decided: **all registered now**, so ids, lang, models and recipes are fixed and later steps only write
code): `floppy_disk` (blank) and one `FloppyDiskItem` per non-built-in app, `floppy_<id>`, from
`ComputerApps.onFloppies()` (`RadiocraftItems.FLOPPIES`, `RadiocraftItems.floppy(app)`). Secret ones are
`Rarity.RARE` and have no recipe (step 8 adds the loot). An installed app with no screen yet opens `PendingApp` ("not
ready in this version"). Label colours per app in `ComputerAppScreens.label` (tint layer 1).

#### Writing a program (guide for steps 6-9)
1. **Registry**: your app is already in `common/computer/ComputerApps` (ids: `bookedit logbook freqman programmer
   solar aprs wefax radiofax sstv df snake falling_blocks music bible adsb`). Its name and one-line description are
   lang keys `screen.radiocraft.computer.app.<id>` and `.description` (in `addRadioComputer`; reword them there). A new
   app: `ComputerApps.add(id, COMMAND, Kind)` in the static list (before items register), a lang name and floppy name
   (`FLOPPY_NAMES`), a model line in `tools/radio_computer_models.py` (`PROGRAMS`) and a recipe in
   `RadiocraftRecipesProvider.radioComputer`.
2. **Screen** (client): `class XApp extends DosApp` with a `(ComputerSession, ComputerApp)` constructor, in
   `client/screens/computer/apps`; register in `ComputerAppScreens`' static block: `register(ComputerApps.X, XApp::new)`
   (and `registerOpener("LOG", ComputerApps.LOGBOOK)` to open `.LOG` files from the shell; you get `openFile(name)`).
   Override `render(TextGrid g, long now)` (draw the whole 80x25 screen every frame; `Dos.frame(g, title, keys)` gives
   the standard title bar, blue body and key bar; `g.put/putField/box/bar/fill/set/cursor`; colours in `DosColours`,
   any 0xRRGGBB works), `onKey` (GLFW codes), `onChar`, `onClick(col,row)`, `onScroll`, `opened`, `resumed`, `tick`,
   `event`, `closed`, `onEscape` (default exits). Widgets: `DosList` (scrolling list), `DosInput` (one-line field),
   `DosTextArea` (multi-line editor), `DosDialog.message/confirm/choice/ask` and `DosFilePicker` via `showDialog`.
   Pictures: `g.image(x, y, w, h, key, pixels, 128, 128)` (pixel rectangle on the 480x250 screen; keep the same byte[]
   and key while the picture is unchanged, a texture is made per key) or `imageInCells`. Words: `Dos.tr(key, args)`
   reads `screen.radiocraft.computer.<key>`; add them to `addRadioComputer`. Programs stay alive while the screen is
   closed (10 min), so keep state in fields.
3. **Files**: `session.files()`: `list(ext, cb)` (+ `ComputerFiles.infos(response)`), `read(name, cb)` (bytes in
   `response.data().getByteArray("data")`), `readText`, `write(name, bytes, cb)` (sent in 16 KB parts automatically),
   `writeText`, `delete`, `rename`. Names are DOS 8.3 (`ComputerStorage.normalise/suggest`). Formats
   (`ComputerFileFormats`): `.TXT` UTF-8, `.IMG` exactly 16384 map colour ids (checked on write), `.LOG/.FRQ/.BOK/.DAT`
   compressed NBT (`ComputerFileFormats.nbt(tag)` / `nbt(bytes)`, both sides). Server side: `ComputerFileStore.of(server,
   id)` or `context.files()`: `write/writeText/writeNbt/read/readText/readNbt/delete/rename/list`.
4. **Server**: most programs need none. If yours does, `ComputerAppHandlers.register(ComputerApps.X, handler)` from
   common setup (or a static init the server loads); implement `ComputerAppHandler.handle(context, action, data,
   reply)` and answer once with `reply.ok(tag)` / `reply.error("key")` (key under `screen.radiocraft.computer.err.`).
   The screen calls it with `session.app(app, action, data, response -> ...)`; the platform checks the app is installed.
   Push unasked news with `context.push(app, kind, data)` / `computer.push(app, kind, data)` (everyone at it); it
   arrives in `DosApp.event(kind, data)`. `receive(computer, TncPacket)` gets every packet the radio interfaces on the
   computer's station bus decode (APRS, step 7); `closed(context)` when a player leaves.
5. **Linked blocks**: in a handler, `context.linked(RadioBlockEntity.class)` (anything touching the computer or on its
   data wire, nearest first; `DataWire.linked`), `context.scanner()`, `context.bus()` (`StationBus`: callsign, transmit
   through the radio interfaces), `context.level()/pos()/player()`. Commercial programming (step 6):
   `CommercialProgramming.of(level, pos)` on a linked mobile, or `of(scanner.bed())`-style on an item you put on the
   scanner's glass (the glass takes maps and books only today: widen `ScannerBlockEntity.isScannable` for radios).
6. **Scanner** from the screen: `session.scanner()`: `read` (kind image/book, image bytes or pages), `save(name)`,
   `printImage(file)`, `printTextFile(file)`, `printText(title, text)` (keep under ~20K chars: one packet),
   `printBook(title, pages)`. Server side: `ScannerBlockEntity.scanImage/scanPages/bedTitle/printImage/printBook`
   (`Result` enum; OUTPUT_FULL when the last print was not taken).
7. **Platform requests** (any program, `session.request(action, data, cb)`): `open`, `sys.info`, `sys.floppies`,
   `sys.insert`, `sys.eject`, `sys.install`, `sys.uninstall`, `fs.*`, `scan.*`, `print.*` (`ComputerServer` Javadoc).
   Any answer carrying `apps`/`floppy` updates `session.apps()`/`session.floppy()`.

**Built in**: the shell (`ShellApp`: programs and files, open/delete/rename/print/scan, `.IMG` viewer), `EditorApp`
(EDIT, 64K, print/scan books), `SysInfoApp`, `BandPlanApp` (draws `BandPlan.current().rows(BandPlan.sheetBands())`),
`InstallApp` (insert from inventory, install with a copying bar, eject, remove). Boot: `DosBoot`. Screen:
`RadioComputerScreen` (scales in half steps up to 2x; swallows every key so E types; Escape in the shell closes).
Monitor mirror: `ComputerSession.capture` / `monitorPicture`, drawn by `ComputerMonitorRenderer` over the glass.

**Recipes added** (for review): Radio Computer `IBI / RCR / III` (iron, HF circuit board, redstone, comparator);
Computer Monitor `III / IGI / BRB` (iron, glass pane, stone buttons, redstone); Scanner `GGG / IRI / IKI` (glass pane,
iron, redstone, ink sac); Floppy Disk x2 shapeless (iron nugget, redstone, black dye, paper); each program's disk
shapeless blank floppy + book and quill (book editor), book (logbook), band plan sheet (frequency manager), comparator
(programmer), daylight detector (solar), compass (APRS), empty map (WEFAX), paper (radio fax), painting (SSTV),
spyglass (DF), feather (ADS-B). Secret disks: no recipe.

**Left undone / gotchas**: power draw was added at the first build (see "First build" at the end). No lock: anyone can use any
computer and read its files. Two players at one computer each run their own programs; files are shared, lists refresh
on demand. The shell rebuilds its lists every frame (small allocations while the screen is open). Box drawing,
arrows and shading are drawn as fills, other non-ASCII glyphs come from the vanilla font (unifont), so stick to ASCII
plus `TextGrid`'s constants. A file write is 16 KB per packet, sequential; a 256 KB file is 16 round trips. Orphaned
disks (a computer item lost in lava) stay in the save. Scanner prints make a new map each time and are not counted by
`camera_photo_cap`. The website wiki was not touched (step 13).

### Step 6
**UNBUILT / UNTESTED** (2026-11-04): no Gradle command run, nothing committed, no test run. Checked by reading
(signatures against the codebase and the MC 1.21.1 sources, lang keys cross-checked by script) and by a plain `javac
-Xlint:all` type-check of this step's files, the language provider, `Radiocraft`, `RadioBlockEntity`, the new unit tests
and GameTests against the cached NeoForm/NeoForge jars (output to the scratchpad, nothing in `build/`): clean apart from
the Create compat classes, whose optional jars were not on that classpath. Builds on the uncommitted Steps 1-5.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava`. Riskiest: `common/computer/Logbook` (java.time
   multi-catch, `DateTimeFormatter` with `withZone`), the lambdas with `Predicate<Character>` in the apps' `DosInput`s
   (`Character.isLetterOrDigit(c)` on a boxed `Character`, as Step 5's `DosInput.fileName` does), `SolarWeatherHandler`
   (`IBENetworks.getObject` + `DummyLoadNetworkObject` instanceof, copied from the Teletype), `LogbookHandler`
   (`server.getProfileCache().get(UUID)`), `ProgrammerApp.edit` (definite assignment of `made` after try/catch),
   `-Xlint:all` this-escape (the new dialogs and apps are `final` for that).
2. `./gradlew runData` and commit `src/generated`: lang only (`addOfficeApps` in the language provider, plus the
   changed `edit.keys`, `shell.keys_files`, `edit.save_to_print`, `err.bed_empty`, new `err.too_many_pages`). No new
   recipes, items, models or tags.
3. `./gradlew test`: new `common/computer/BookDocumentTest`, `LogbookTest`, `FrequencyListTest`, `CodeplugTest`,
   `common/radio/StationsHeardTest`. `PatchouliBookTest` covers the four new entries (their crafting pages name
   `radiocraft:floppy_*` recipes, which need runData). `CommercialChannelTest` should still pass with the
   `resolve` fix.
4. `./gradlew runGameTestServer`: new `OfficeAppsGameTests` (6 tests; `solarNeedsAReceiver` in batch
   `computer_solar` because it flips `solar_weather_station_needs_radio` inside try/finally; the logbook test injects a
   voice packet straight into a VHF base station on 147.180 MHz). Re-run `RadioComputerGameTests` (ComputerServer's
   print change) and any test that reads `ScannerBlockEntity.isScannable`.
5. runClient: NeedsTesting "1.12 step 6".

**Programs** (screens in `client/screens/computer/apps`, server sides in `common/computer/apps`, registered by
`OfficeApps.register()` from a `FMLCommonSetupEvent` listener in `Radiocraft`'s constructor; screens and file openers in
`ComputerAppScreens`):
- `BookEditorApp` (BOOKEDIT) + `BookEditorHandler` (`print` {name, volume}: prints a saved `.BOK`, since a long book is
  more than one packet). Data: `common/computer/BookDocument` (title, author, pages up to 1000; `volumes()`,
  `volumePages`, `volumeTitle` "Title Vol. 2", `bookLines(page)`/`overflows`, `toTag/fromTag`). `PAGES_PER_VOLUME` 64.
- `LogbookApp` (LOGBOOK) + `LogbookHandler` (`heard`: stations heard by linked radios in the last 10 min, with call,
  player, mhz, mode, rst, ago, radio). Data: `Logbook` (entries in time order, max 2000; `Entry(utcMillis, gameTime,
  call, frequencyMhz, mode, rstSent, rstReceived, notes)`; `adif(version)`, `adifMode`, `adifBand`, `rst(strength,
  noise, cw)`, `parseUtc`, `utcDate/utcTime`). Opens `LOGBOOK.LOG`; F7 writes `<name>.ADI`.
- `FrequencyManagerApp` (FREQMAN) + `FrequencyManagerHandler` (`radios`, `read`, `export` {radio, start 0-based,
  channels}: writes only those positions, everything else and every key kept). Data: `FrequencyList` (`Entry(name,
  rxMhz, txMhz (0 = RX only), Mode FM/DIG/USB/LSB/CW/AM, tone, notes)`; `toChannel()` (no key; null for SSB/CW/AM),
  `Entry.of(channel)`, `move`, `sortByFrequency`, `parseMhz`, `mhz`). Opens `CHANNELS.FRQ`.
- `ProgrammerApp` (CPS) + `ProgrammerHandler` (`radios`, `read`, `write`, `factory`). Data: `Codeplug(model, 16
  channels)` with `channelTag/channel/channelsTag/channels` (the request and `.CPG` file layout). `RadioPicker`
  (client) asks `radios` and picks one. Both programs reach radios through `CommercialTargets` (server): the scanner's
  glass (`GLASS` id "glass") and `context.linked(CommercialMobileBlockEntity.class)` (id `pos:<asLong>`), looked up
  afresh per request so a client can only name a radio the computer reaches. Answers carry `problems` (pos, key under
  `message.radiocraft.commercial.`) instead of an error, so the screen can mark the rows.
- `SolarWeatherApp` (SOLAR, pages SOLAR and FORECAST) + `SolarWeatherHandler` (`solar`, `forecast`, both gated by
  `receiver(context)`: config off, else an online Ionospheric Processor wired or beside a radio, else a powered radio
  with a real antenna; else error `solar_no_receiver`). Forecast lines are `WeatherReports.lines` + `outlook` days +
  `stormList` (local sources).

**Framework changes later steps must know** (Step 5's guide still applies):
- **F2 and F11 never reach a screen**: Minecraft's `KeyboardHandler` takes the screenshot / full screen keys first
  (checked in the 1.21.1 source). Step 5's EDIT saved and the shell renamed with F2, which did nothing. Now
  `Dos.isSaveKey(key)` (Ctrl+S, or F2 for players who moved the screenshot key) saves in EDIT, BOOKEDIT and CPS; the
  shell renames with F7 (or F2). Show "^S=Save" in key bars; do not use F1 (fine with a screen open, but avoid), F2 or
  F11. `Dos.ctrl()` is Screen.hasControlDown().
- New widgets: `DosFormDialog` (labelled `DosInput` fields, OK/Cancel; the callback gets the values and returns null
  to close or a message to show; `focus(i)`) and `DosPickDialog` (pick a line from a list). Both `final`.
- `ComputerFileFormats.CODEPLUG` ("CPG") and `ADIF` ("ADI"); openers: BOK -> BOOKEDIT, LOG -> LOGBOOK, FRQ -> FREQMAN,
  CPG -> CPS, ADI -> EDIT.
- `ScannerBlockEntity.isScannable` takes `CommercialProgramming.isProgrammable` stacks; `bedChanged()` saves after a
  stack on the glass was changed in place.
- `ComputerServer.printBook` (print.text/print.book) refuses more than 64 pages with `too_many_pages` (the tray holds
  one stack; before, a 65-100 page print just said "no paper").
- `RadioBlockEntity.getStationsHeard()` (`common/radio/StationsHeard`: 8 fixed slots, written from the voice thread in
  `receiveVoice` after the digital drop-out check, no allocation per frame; `recent(now, within)`). Voice only.
- Step 2 fix: `CommercialProgramming.resolve` keeps a hidden key **unreadable** (it took the readable flag from the
  request, so a crafted write of `KEY_HIDDEN` + readable made the key readable on the next read).

**Left undone / gotchas**: CW and data contacts are not offered by the logbook's F6 (only voice has a talking player).
The book editor edits a page in a 44-column box with no soft wrap; the 19-character book line is only counted, not
shown. The forecast's text is the teletype report's English caps, not translated. No dupe check on log or list names
beyond "exists". Programs keep their state while the screen is closed (Step 5), so a CPS with unwritten edits stays
"modified" for 10 minutes. Website wiki not touched (step 13). No new recipes, art or config keys.

### Step 7
**UNBUILT / UNTESTED** (2026-11-04): no Gradle command run, nothing committed, no test run. Checked by reading and by a
plain `javac -Xlint:all` type-check of the **whole tree** (main, test and gametest sources together) against cached jars,
output to the scratchpad: **0 errors**, and no warning that was not there before this step (32 old ones: deprecations,
unchecked in `ClientSetupEvents`, this-escape in geckolib classes). Builds on the uncommitted Steps 1-6.

How the type-check was run (for later steps; no Gradle): the classpath is an earlier session's runtime classpath file
(`d6e82987-.../scratchpad/cp.txt`, copied to this step's scratchpad as `cp.txt`) without `build/classes`, plus
`org.jetbrains:annotations:24.1.0`, the optional Create 6.0.10 / Flywheel / Ponder / Registrate / Sable / Veil jars from
`~/.gradle/caches/modules-2/files-2.1` (so the compat classes compile too) and, for tests, the JUnit 5.10.2 /
platform 1.10.2 / opentest4j / apiguardian jars. Then
`javac -Xlint:all -encoding UTF-8 -proc:none -d <scratch>/out -cp <cp> @<list of every .java under src/main, src/test,
src/gametest>`. Scripts: `7f2e3421-.../scratchpad/tc.sh` and `new.sh` (new problems only, against `baseline_warnings.log`).

**Framework bugs found by that check and fixed** (they would have failed the first build):
- `DebugCommands.commercialResult` (Step 2): a literal newline inside `String.join("...")` (unclosed string literal).
- `PlayerRadio.transmitAudioPacket(..., ctcss, power)` (Step 3) was `private` with the same signature as
  `IAntenna`'s public default method: "attempting to assign weaker access privileges". Now `public @Override` (same body).
- `DigitalVoice` (Step 2): a redundant `(int)` cast (`-Xlint:all`).

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava` (should be clean; javac said so).
2. `./gradlew runData` and commit `src/generated`: lang only (`addRadioApps` in the language provider, plus
   `item.radiocraft.wefax_chart.station`). No new items, recipes, models, tags or config keys.
3. `./gradlew test`: new `common/computer/ImageCaptionTest`, `PictureReceptionTest`, `DfSearchesTest`,
   `common/radio/data/PixelFontTest`, `client/screens/computer/apps/DfDrawingTest` (map plot and the 3D scene);
   `ComputerStorageTest` gained `nextNameIsTheFirstFreeNumber`. `PatchouliBookTest` covers the five new entries (their
   crafting pages name the `radiocraft:floppy_*` recipes from Step 5's runData).
4. `./gradlew runGameTestServer`: new `RadioAppsGameTests` (batch `radio_apps`, 144.930/144.950/144.970/144.990/145.030
   MHz over bare connectors; plus three quick tests with no radio). The riskiest is `sstvPictureArrives`: it needs the
   receiving radio's `isCopying` to be set by the SSTV tones (voice frames) during the GameTest; if every line comes in as
   noise the check "mostly the picture" fails, and the cause is the copy flag, not the transport. Re-run
   `RadioFaxGameTests` (the fax prints a computer's picture on a real map now, constants made public),
   `WefaxGameTests`/`WefaxAlertGameTests` (`WefaxReceiverBlockEntity.receive` changed), anything drawing APRS maps
   (`AprsMapPainter` now uses `PixelFont`: a space draws blank and lower case as capitals, where both drew a dash), and
   `RadioComputerGameTests`/`OfficeAppsGameTests` (the computer now ticks and saves program states).
5. runClient: NeedsTesting "1.12 step 7".

**Programs** (screens in `client/screens/computer/apps`, server sides in `common/computer/apps`, registered by
`RadioApps.register()` from a second `FMLCommonSetupEvent` listener in `Radiocraft`; screens in `ComputerAppScreens`).
All but DFIND go on the air through **radio interfaces on the computer's station bus** (`RadioStation`: `bus`, `radios`,
`status`, `cannotSend` (keys `station_no_interface`/`_no_callsign`/`_no_radio`), `transmit` (notes the ids, a FILE's
header too), `copying`, `hasAntenna`), under the interface's callsign. A radio wired straight to the computer is not
enough (it never hands packets to the computer; see open questions).
- `AprsApp` (APRS) + `AprsHandler`: the H19's logic for the computer (reuses `AprsTerminalBlockEntity.Heard/Logged/
  Delivery`, `TncPacket.beacon/weatherBeacon/message/ack`, the config's beacon interval and retries, `AprsMapPainter`).
  Needs a VHF radio on (`aprs_no_vhf`). State `AprsHandler.State` (stations, messages, comment, icon, auto, saved).
  Actions `status`, `map`, `beacon`, `send`, `settings`, `callsign`, `read`, `clear`; pushes `update`.
- `FaxApp` (FAX) + `FaxHandler` + `FaxModem`: the radio fax conversation (call, answer, 30 acked packets) as the machine
  holds it, with the machine's timing constants (`RadioFaxBlockEntity.CALL_TRIES`, `ANSWER_WAIT_TICKS`,
  `ACK_WAIT_TICKS`, `RECEIVE_PATIENCE_TICKS`, now public), so computers and machines fax each other. No microwave link.
  Sends `.IMG` (a picture document), `.TXT`/`.BOK` (a written book); keeps what comes in as `FAX00001.IMG/.TXT`
  (inbox in the state). Answers only with auto answer on and no `RadioFaxBlockEntity` on the same bus. Refuses an
  enchanted book ("CANNOT STORE") and a full disk ("DISK FULL").
- `SstvApp`/`WefaxApp` on `PictureApp` + `SstvHandler`/`WefaxHandler` on `PictureHandler`: a picture goes out as a
  `TncPacket.file` (to "" = everyone, or a callsign) carrying a **picture document** (below). Receivers start on the
  header (`PictureReception`: lines by the clock, noise while no bus radio `isCopying`), finish on the FILE packet or
  60 ticks after the tones, and keep `SSTV0001.IMG` / `WFX00001.IMG` (list in the state). Pushes `rx` (the picture so
  far, every 2 s) and `received`. SSTV captions are made on the client (`common/computer/ImageCaption`) and written as
  a new file. WEFAX also takes SOLAR bulletins (started `START_SHARE` early, as the receiver block does) and ALERTs,
  `wxrept` (the coast station's radar + forecast, tones played through the bus radios, needs `hasAntenna` or
  `wefax_weather_always_works`) and `chart` (writes `WXRADAR.IMG`/`WXFCST.IMG`).
- `DfApp` (DFIND) + `DfHandler`: the first `DirectionFinderBlockEntity` in `context.linked`. `status` (set, bearings with
  source OWN/TYPED/LINKED, fix), `map` (terrain by `AprsMapPainter` fitted round the bearings and fix; the client draws
  the overlay with `DfDrawing`), `power`/`tune`/`band`/`frequency`/`add`/`remove` (the set's own methods), `save`/
  `searches`/`delete` (`common/computer/DfSearches` in `DFSEARCH.DAT`, 50 newest). The 3D page is `DfScene` (pure
  wireframe into map colours, two buffers swapped so the texture repaints).

**Framework changes later steps must know**:
- `ComputerAppHandler.tick(computer, gameTime)` + `ticks()` (default false): the computer block now has a **server
  ticker**; it calls `tick` every tick on the installed programs whose handler says `ticks()` (list cached, rebuilt on
  install/uninstall/load). Throttle inside (`(gameTime + pos.asLong()) % N`).
- `ComputerAppState` + `RadioComputerBlockEntity.state(app, factory)` / `existingState(app)`: per-computer server state
  for a program, saved in the computer's BE tag under `appStates.<id>` (not on the item: breaking the computer drops
  them, files stay); dropped on uninstall.
- `RadioComputerBlockEntity.receive` now **dedupes packet ids** (`SeenPackets`), as `StationTerminal` asks (Step 5's
  computer passed a packet from two interfaces twice). `computer.sent(packet)` notes our own.
- **Picture documents** (`FileTransfer.image(pixels, name, mode)`, modes `MODE_SSTV`/`MODE_WEFAX`/`MODE_PICTURE`): a
  `FILLED_MAP` with no map id carrying its pixels in `CUSTOM_DATA` (`radiocraft_pixels`, `radiocraft_mode`), so sending
  makes no map file. `isVirtualImage`, `mode`, `imagePixels(stack, level)` (also reads real maps), `materialise`
  (what a fax machine prints). `FileTransfer.audio/frameCount` play `MODE_WEFAX` as weather fax tones for
  `wefaxTicks()` and other pictures as SSTV (`sstvTicks()`); `describe` says SSTV/WEFAX. `RadioFaxBlockEntity.print`
  materialises them on its blank; `WefaxReceiverBlockEntity` with AUTO on prints a WEFAX picture's FILE_HEADER
  (`WefaxPrints.printPicture`). No new `TncPacket.Kind` (several exhaustive switches on it).
- `common/radio/data/PixelFont` (pure; the APRS map font plus . , ! ? : ' + = ( ) # % & * " _ and space);
  `AprsMapPainter.glyph` delegates. `ComputerStorage.nextName(prefix, ext, taken)`. `DosList.top()`.
- Screens: pictures are drawn **over** the text cells, dialogs included: skip `g.image` while `hasDialog()`.
  `DosImage` repaints a key only when the `byte[]` reference changes: keep one key per picture slot and hand it a new
  (or the other of two) array when it changes. Avoid `%` in lang strings read by `Dos.tr` (String.format).

**Left undone / gotchas**: `RadioFaxBlockEntity` was not moved onto `FaxModem` (two copies of the protocol; the timing
constants are shared, keep any protocol change in both). No `/rcdebug` for the new programs. The computer fax has no
microwave link; the coast station broadcast is on request only (no schedule); SSTV has one mode (the existing ~27 s map
SSTV tones), no Martin/Scottie choice. A computer and an H19 on one bus both log and ack the same APRS message. The 3D
page and map are drawn on the client from what `status`/`map` return; nothing of them is synced to the monitor mirror
other than the screen picture. Received pictures and faxes are written to the disk automatically; with the disk full
they are lost (said on screen). No new art, recipes or config keys; website wiki not touched (step 13).

**Open questions for the maintainer (Step 7)**:
- **Interface required**: every radio program but DFIND needs a radio interface on the computer's station bus (the
  TNC / sound card), as the APRS brief said; a radio wired straight to the computer does nothing for them. Say if a
  directly wired radio should count as the computer's sound card for fax, SSTV and WEFAX.
- **Who answers a fax**: with a radio fax machine on the same station, the machine answers and the computer only sends.
  Auto answer (F7) is on by default.
- **WEFAX receivers with AUTO on now print charts a station sends** (using paper) as a real recorder starts on the
  start tone; with AUTO off they ignore them. Say if they should always print, or never.
- **Addressed pictures**: SSTV and WEFAX pictures can go to one callsign (only that station keeps it) or to everyone;
  real SSTV has no addressing. Keep the option?
- **Saved automatically**: everything received is kept on the disk at once (digital until printed), named
  `SSTV0001.IMG`, `WFX00001.IMG`, `FAX00001.TXT`. Say if the programs should ask, or keep only the last N.

### Step 8
**UNBUILT / UNTESTED** (2026-11-05): no Gradle command run, nothing committed, no test or GameTest run. Checked by reading
and by the plain `javac -Xlint:all` type-check of the **whole tree** (main, test, gametest) with Step 7's scripts
(`7f2e3421-.../scratchpad/tc.sh`, `new.sh`): **0 errors, no new warning**. A scratch `java` run of the pure classes
against the real Bible file (references, search, wrap, a Pileup and a Snake game to their ends) behaved; that is all
that ran. Builds on the uncommitted Steps 1-7.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava` (javac said clean).
2. `./gradlew runData` and commit `src/generated`: lang only (`addSecretApps` in the language provider; the falling-
   blocks game's name and floppy now "Pileup"; `tooltip.radiocraft.floppy_disk.secret`). No new items, recipes,
   models, tags or config keys.
3. `./gradlew test`: new `common/computer/games/SnakeGameTest`, `PileupGameTest`, `HighScoresTest`,
   `common/computer/BibleTextTest` (also reads the shipped gzip), `MusicTracksTest` (every `.mid` listed, a MIDI file,
   and named in `doc/Credits-Music.md`), `common/worldgen/HamShackTemplatesTest` (reads the six `.nbt` with `NbtIo`).
4. `./gradlew runGameTestServer`: new `SecretAppsGameTests` (install the four disks; roll `chests/secret_floppies`,
   `chests/ham_shack`, `minecraft:chests/ancient_city` and `nether_bridge` hundreds of times, wide statistical bounds)
   and in `SiteGameTests`: `hamShackWorks` (batch `radio_sites`: powered, J-pole SWR), `villageHamShackWorksTurned`
   (the plains village template placed four ways with known shapes + `JigsawReplacementProcessor` +
   `RotateBlocksProcessor`, then `SiteCheck`), `villagesCanHaveAHamShack` (pools hold it once; a second `add` adds
   nothing), `radioCraftBlocksTurnWithThePiece`. Riskiest: the turned village shack (does the radio/battery/coax network
   join with the processor-turned wire states?) and the loot tests (the GLM's `neoforge:loot_table_id` must see the
   table id when a table is rolled from `reloadableRegistries().getLootTable`).
5. runClient: NeedsTesting "1.12 step 8" (games, MUSIC through `javax.sound.midi`, BIBLE, loot, both kinds of shack).

**Programs** (client only; no server handlers): `SnakeApp` + pure `common/computer/games/SnakeGame`; `PileupApp` + pure
`PileupGame` (the falling-blocks game: id still `falling_blocks`, command now `PILEUP`, name "Pileup": a ham pileup,
rows are QSOs, levels are bands 160m-70cm; 12 x 20 board, ten pieces of 3-5 cells from a shuffled bag, wall kicks, `[]`
cells and a `::` ghost on a dark spectrum display). High scores in `SNAKE.DAT`/`PILEUP.DAT` (`games/HighScores`, top 10
with the player's name, through `apps/HighScoreFile`). `MusicApp` + `client/screens/computer/ComputerMidi` (one Gervill
synth and sequencer for the client, opened on a daemon thread; volume = master x RECORDS, applied as note-on velocity
scaled by sqrt(gain); 16 channel meters; stops on screen close, closes on Esc) + pure `common/computer/MusicTracks`
(`assets/radiocraft/computer/music/tracks.txt`, `file|title|composer|seconds`). `BibleApp` + pure
`common/computer/BibleText` (`assets/radiocraft/computer/bible/web_gospels.txt.gz`, read once per game: parse, typed
references "John 3:16"/"Mk 4", all-words search capped at 500, chapter wrap with the verse numbers in the margin). All
four registered in `ComputerAppScreens`. Secret floppies get a gold "rare disk" tooltip line (`FloppyDiskItem`).

**Data**: 12 Mutopia MIDI files, all marked Public Domain (sources and licences in `doc/Credits-Music.md`, which also
credits the WEB). The Bible resource is made by `tools/bible_gospels.py` from ebible.org's `eng-web_vpl.zip`
(typography folded to ASCII for the text-mode font, noted in the credits). Loot: `loot_table/chests/secret_floppies`
(one of the four, equal weights) and three `neoforge:add_table` GLMs listed in
`data/neoforge/loot_modifiers/global_loot_modifiers.json`: `secret_floppies_rare` 12% (ancient city, stronghold library,
mansion, trial chamber vault `reward`, end city, igloo), `_uncommon` 5% (shipwreck supply/map, dungeon, outpost, desert
and jungle temple, mineshaft, buried treasure), `_villages` 3% (the five village house chests, cartographer, temple).
`chests/ham_shack`: 3-6 radio parts plus a 40% chance of one secret disk. Advancement `radiocraft:found_secret_floppy`
(no display) unlocks the hidden Handbook entry `computer/secret_programs` ("Disks Never Sold"); the Programs page's
rumour line now hints at chests and the shack.

**Ham shack**: templates written by `tools/ham_shack_templates.py` (its own gzip NBT writer; full block states copied
from the in-game-saved site templates): `structure/sites/ham_shack.nbt` (11 x 7 x 9, one-block margin like the other
sites) and `structure/village/ham_shack_{plains,taiga,snowy,savanna,desert}.nbt` (9 x 7 x 8, that village's materials,
doorstep jigsaw `minecraft:building_entrance` on the south edge, villager jigsaw). A timber house: VHF base station on
146.520 MHz (on), coax up the north wall to a J-pole on the flat roof, large battery on wire, radio computer + monitor
(Logbook and Frequency Manager installed), bookshelf, chest, chair, bed, plant, lightning rod. Standalone:
`worldgen/structure/ham_shack.json` (`radiocraft:site`, max slope 4), biome tag `has_structure/ham_shack`, weight 2 in
the existing `radio_sites` set (so it never overlaps another site; `SitePrediction` copes, the shack has no module).
Villages: `common/worldgen/VillageHouses` adds a `legacy` RIGID element (processor list `radiocraft:ham_shack_village`)
with weight 1 to `minecraft:village/<type>/houses` on `ServerAboutToStartEvent` (listener in `Radiocraft`'s
constructor), by reflection on `StructureTemplatePool.templates` (Mojang names at runtime; logs and skips if missing).
`common/worldgen/RotateBlocksProcessor` (`radiocraft:rotate_blocks`, registered in `RadiocraftStructures`) turns
radiocraft blocks' horizontal `DirectionProperty`s, `north/east/south/west` booleans and `WireBlock.HUG`, because **no
RadioCraft block overrides `Block.rotate`** and village pieces are placed with known shapes (nothing fixes them up
after). If anyone adds `rotate()` to those blocks, drop the processor or they turn twice.

**Left undone / gotchas**:
- The shack has **no `Sites.java` builder** (gametest `sites/Sites`): it exists only as the generated templates. To
  rebuild it in game, add a builder; or edit the Python and rerun it (a shack saved with `/rcsite save` would differ).
- MUSIC is heard only by the player at the screen (client side, not positional, not through Minecraft's sound engine)
  and stops when the screen closes; a held note keeps the volume it started at. Java's synth opens its own audio line:
  untested on Linux/macOS. The first play opens Java's default soundbank (a short pause).
- The village shack has one layout for all five types (only materials differ); a desert one has a jungle door.
- No server config for the loot chances or the village weight (data: the GLM JSONs; code: `VillageHouses.WEIGHT`).
- Lang strings use `%s` only; no literal `%` (Dos.tr formats).
- Website wiki not touched (step 13). No new art, recipes or config keys.

**Open questions for the maintainer (Step 8)**:
- **Name**: the falling-blocks game is **Pileup** (a pileup is a crowd calling one DX station; rows worked are QSOs,
  levels are bands), `PILEUP.EXE`. Happy with it?
- **Loot chances**: 12% / 5% / 3% per chest (above) and 40% in a ham shack chest. Too generous or too rare?
- **Shack found powered**: the radio sits on 146.520 MHz, switched on, its battery at 50-100% (the site processor's
  wear; in villages as built). Should found shacks be dark like the buried site?
- **Music only at the screen**: should MUSIC keep playing after the screen closes (positional, heard nearby), or play
  into the radio audio so it can be transmitted? Neither is built: it stops on close.
- **Bible scope**: the four gospels, as decided. The converter can take the whole WEB (Psalms, say) if wanted.

### Step 9
**UNBUILT / UNTESTED** (2026-11-05): no Gradle command run, nothing committed, no GameTest run. Checked by reading and by
the plain `javac -Xlint:all` type-check of the **whole tree** (main, test, gametest) with Step 7's scripts
(`7f2e3421-.../scratchpad/tc.sh`, `new.sh`): **0 errors, no new warning**. A scratch `java` run (reflection, JUnit's
assertions, no Gradle) of the new pure tests passed: `AdsbMathTest`, `AdsbMotionTest`, `AdsbTracksTest`,
`AdsbScopeTest` and `ConfigNamesTest` (29 tests); `ModelFilesTest` passes; `PatchouliBookTest` fails only on recipes
and items that need runData (the earlier steps' too). Builds on the uncommitted Steps 1-8.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava` (javac said clean).
2. `./gradlew runData` and commit `src/generated`: lang (`addAdsb`), three recipes, loot tables (dropSelf), block tags
   (`POWER_BLOCKS`: beacon, receiver; `COAX_BLOCKS`: receiver, antenna; `DATA_BLOCKS`: receiver; pickaxe), config names
   (ADS-B section). **Blockstates and models are hand-made** in `src/main/resources` by `tools/adsb_models.py` (each
   block has a lit part), not by the data generator. Without the tags, wires do not attach (the antenna-on-top path
   still works).
3. `./gradlew test`: new `common/radio/adsb/AdsbMathTest` (horizon, range, units, squawks), `AdsbMotionTest` (speed,
   track, climb from reports, nose heading when parked, teleports), `AdsbTracksTest` (trails, coasting, the picture
   tag), `AdsbScopeTest` (the computer's scope picture); `ConfigNamesTest` gained two lines.
4. `./gradlew runGameTestServer`: new `AdsbGameTests` (6): `receiverHearsABeacon`, `noAntennaHearsNothing`,
   `antennaOnCoax` (needs the coax tags), `silentBeaconsLeaveTheAir`, `computerReadsTheReceiver`, and
   `outOfRangeIsNotHeard` in batch `adsb_range` (sets `adsb_min_range` and `adsb_max_range` to 2 in @BeforeBatch, back
   in @AfterBatch). Riskiest: whether the network objects exist when `togglePower` runs three ticks after `setBlock`
   (created on demand, as the microwave radio's), and the LIT asserts.
5. runClient (`-PwithSable`): NeedsTesting "1.12 step 9".

**How it works** (package `common/radio/adsb`, pure unless noted):
- `AdsbMath`: `range(y, seaLevel, fullY, min, max)` (the aircraft's absolute Y only: max at or above fullY, else
  min + (max - min) x t^2 with t the height above sea level over fullY's, min at or below sea level; replaced the
  horizon-factor model on 2026-11-05), `maxRange(setting, dish, hornFactor)` (0 = 2 x dish x horn); `bearing` (north -Z), feet/knots/fpm (ADS-B's 64 ft/min steps), `flightLevel`, `hex`, flight ID
  cleaning (A-Z0-9, 8), squawk parsing/validation (octal), emergency codes 7500/7600/7700 and their tags.
- `AdsbMotion`: velocity from successive reports (smoothed 0.5), track while moving (>= 0.5 block/s), else the nose
  heading it is given; a jump over 400 blocks/s or a gap over 10 s starts afresh.
- `AdsbTarget` (record: icao, callsign, squawk, x/y/z world, height above sea level, heading, groundSpeed,
  verticalRate (blocks/s), time, beacon pos), `AdsbTracks` (per ICAO: latest report + 30-point trail; coast 200 ticks;
  64 tracks max), `AdsbPicture` (NBT for screens: state, range, horizon, targets relative to the receiver with trails;
  `contacts(tag)` reads it back as `Contact`s), `AdsbScope` (paints the picture into map colour ids for the computer).
- `AdsbAirspace` (server, game classes): `Map<dimension, Map<beacon pos, latest AdsbTarget>>`. Beacons `publish` once a
  second and `withdraw` when unpowered, broken, removed or unloaded; `isFresh` = sent within 60 ticks. Cleared on
  `ServerStoppedEvent` (listener in `Radiocraft`'s constructor). Nothing scans the world.

**Blocks** (`common/blocks/adsb`, `common/blockentities/adsb`, all `AbstractNetworkBlock`, `PROPERTIES_RADIO`):
- `adsb_beacon` (`AdsbBeaconBlock`: `FACING` = the way the player looked, the nose; `LIT`): `AdsbBeaconBlockEntity`.
  Power network object `AdsbNetworkObjects.Beacon` (`adsb_beacon_power_use`, 2 FE/t; free when `radios_need_power` is
  off). Every 20 ticks (offset by position) while powered: `Sublevels.worldCenter` -> `AdsbMotion.update` with the nose
  heading (`FACING` turned by `Sublevels.frame(...).applyDirection`) -> `AdsbAirspace.publish`. Saves icao (random
  24-bit on first load), callsign, squawk (1200), switchedOn. With no flight ID set, it sends the Sable ship's name
  (`Sublevels.name`, new), cleaned. Menu `AdsbBeaconMenu` (actions `power`, `callsign`, `squawk`); screen
  `client/screens/adsb/AdsbBeaconScreen`.
- `adsb_receiver` (`AdsbReceiverBlock`, `FACING`, `LIT`): `AdsbReceiverBlockEntity`, network object
  `AdsbNetworkObjects.Receiver` (power + coax; `antennas()`; `adsb_receiver_power_use`, 4 FE/t). Every 20 ticks while
  powered: the highest ADS-B antenna on its coax or straight on top (`findAntenna`), then every fresh report in range
  of that antenna (`AdsbMath.range` from both heights, horizontal distance) goes into its `AdsbTracks`; its own ship's
  beacon is skipped (`Sublevels.id`, new, compared). Off, it computes nothing and clears its tracks. Display range
  (`RANGES` 250-20000, saved) is stepped by its menu (`AdsbReceiverMenu`: `power`, `range`). `picture()` is shared by
  the screen and the computer program.
- `adsb_antenna` (`AdsbAntennaBlock`): `AdsbAntennaBlockEntity`, coax object `AdsbNetworkObjects.Antenna`.
- All three are in `RadiocraftCapabilities`' BE_NETWORKS list and `BENetworkTypes`; menus check `stillValid` through
  `ContainerLevelAccess` (so a block in a Sable plot works if Sable patches `canInteractWithBlock`, as for chests).
- Screens get their data in `CAdsbPacket(pos, tag)`, sent once a second only to players whose open menu is that block
  (loop over `level.players()`), and right after `openMenu`; client side `AdsbScreens.update` hands it to the open
  screen. `AdsbReceiverScreen` draws the CRT with `GuiGraphics.fill` (disc, rings, sweep + wake, trails, targets with a
  phosphor afterglow from the sweep, half-size tags), the list and the details; click to pick, wheel or -/+ for range.

**Computer**: `AdsbApp` (client, `ComputerAppScreens`) + `AdsbHandler` (registered in `RadioApps.register`): `status`
(the first `AdsbReceiverBlockEntity` in `context.linked`, its `picture()` plus `receiver`), `power` (error
`adsb_no_receiver`). The app polls once a second, paints `AdsbScope` into two alternating 128 x 128 buffers (a new
array so `DosImage` repaints), keeps its own range and tags. `/rcdebug adsb` lists the beacons on the air.

**Config** (server, section "ADS-B"; range per the maintainer, 2026-11-05): `adsb_full_range_y` 200 (absolute Y),
`adsb_min_range` 200, `adsb_max_range` 0 (= twice the horn: `microwave_dish_range` x `microwave_horn_range_factor` x 2,
18,000 blocks by default; `AdsbReceiverBlockEntity.fullRange()`). The picture's `horizon` became `reach`/`reachLow`.
Display ranges (receiver and program) 250 m - 20 km (added 12 and 20 km). `adsb_beacon_power_use` 2, `adsb_receiver_power_use` 4. `ConfigNames`
writes "adsb" as "ADS-B".

**Recipes added** (for review): ADS-B Beacon `_L_ / IBI / IRI` (lightning rod, iron, HF circuit board, redstone);
ADS-B Receiver `IGI / RBR / III` (iron, lime stained glass pane, redstone, HF circuit board); ADS-B Antenna vertical
`C / I / X` (copper ingot, iron ingot, coax wire). The ADS-B floppy was Step 5's (blank floppy + feather).

**Docs**: Handbook category `aviation` ("Aviation & ADS-B": `adsb_beacon`, `adsb_receiver`) and `computer/adsb`;
NeedsTesting "1.12 step 9"; ModelDesignBounty "Models: ADS-B beacon, receiver and antenna"; release notes. Website
wiki not touched (step 13).

**Left undone / gotchas**:
- **Range is the aircraft's Y only**: terrain and buildings between a craft and the antenna do not block 1090 MHz
  (no ray casts, for cost). Heights are above the level's sea level, not above the ground.
- Beacons in unloaded chunks are not on the air (a craft must be loaded to fly anyway). A beacon moved by Sable
  assembly is a new block entity at the plot position: its address and settings come with it, its velocity starts
  from zero.
- No in-world live scope on the receiver's tube (a block entity renderer like the computer monitor's would do it).
- The receiver's radar sweep is cosmetic; reports come in once a second whatever the sweep.
- No Jade/TOP providers, no JEI info pages, no sounds.
- Not tried with Create Aeronautics itself (it is built on Sable; the code only uses `Sublevels`).

**Open questions for the maintainer (Step 9)**:
- **Range numbers**: ANSWERED 2026-11-05 (twice the horn from Y 200 up, steep fall-off to 200 blocks; built). The
  antenna's height no longer counts. Terrain blocking is not modelled; want it (a few ray samples per pair a second)?
- **Power**: the beacon draws 2 FE/t and the receiver 4 FE/t from the power wire (like the other gear), so a craft
  needs a battery. Should the beacon run without power?
- **Units**: altitude in feet and flight levels, speed in knots (as aviation), but distances and ranges in m/km rather
  than nautical miles (game blocks are metres). NM instead?
- **Own ship**: a receiver on a ship hides the beacon on the same ship (as real ADS-B In does). Show it as an "own
  ship" symbol in the middle instead?
- **Ship name as flight ID**: with no flight ID set, the beacon sends its Sable ship's name (capitals and digits, 8
  max). Keep, or always show the ICAO address until one is set?

### Step 10
**UNBUILT / UNTESTED** (2026-11-05): no Gradle command run, nothing committed, no GameTest run. Checked by reading and by
the plain `javac -Xlint:all` type-check of the whole tree (main, test, gametest) with `scratchpad/new.sh all`: **0
errors, no new warning**. Scratch reflection run (no Gradle): `FccBadgesTest` (4) and `ModelFilesTest` pass. All JSON
files parse. Builds on the uncommitted Steps 1-9; touches nothing of theirs except one line each in `Radiocraft`,
`RadiocraftCommands`, `RadiocraftTabs`, the language and recipe providers.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava` (javac said clean).
2. `./gradlew runData` and commit `src/generated`: lang (`addFccGear`: four items, the pattern's `.desc`, the 16
   `block.radiocraft.banner.fcc.<colour>` names, `item.radiocraft.fcc_banner`, tooltips, messages, two command lines)
   and three recipes (`fcc_hat`, `fcc_jacket`, `fcc_banner_pattern`). No blockstates, loot or block tags. Item models,
   textures and every other data file are hand-made in `src/main/resources` (by `tools/fcc_assets.py`).
3. `./gradlew test`: new `common/fcc/FccBadgesTest`. `PatchouliBookTest` needs runData for the new entry's recipes.
4. `./gradlew runGameTestServer`: new `FccGameTests` (6): `hatAndJacketProtectLikeLeather`, `recipesMakeTheGear`,
   `sealPatternIsLoaded` (the data-driven banner pattern and its `pattern_item` tag), `onlyOperatorsIssueBadges` (runs
   the command as a fake player at permission 0 and 2 with `@s`), `showingABadgeSaysWhoseItIs`,
   `badgeIsWornOnTheNecklaceSlot` (Curios in the dev runtime). Riskiest: the command test (selector permission and
   `performPrefixedCommand` with a FakePlayer source) and whether Curios has the necklace slot for a FakePlayer.
5. runClient: NeedsTesting "1.12 step 10" (worn look, loom, shield, creative banner, badge show, Curios slot).

**What was added**:
- Armour material `radiocraft:fcc` (`common/init/RadiocraftArmorMaterials`, a new `DeferredRegister<ArmorMaterial>`
  registered in `Radiocraft`'s constructor; NeoForge registers armour materials before items): leather's numbers
  (defence 1/2/3/1, body 3, enchantability 15, leather equip sound, durability multiplier 5), repaired with brown wool,
  one non-dyeable layer `textures/models/armor/fcc_layer_1.png`.
- Items (`RadiocraftItems`, after the floppies): `fcc_hat` (`ArmorItem` HELMET), `fcc_jacket` (`ArmorItem`
  CHESTPLATE), `fcc_banner_pattern` (vanilla `BannerPatternItem` with the tag `radiocraft:pattern_item/fcc`),
  `fcc_badge` (`FccBadgeItem`, stacks to 1, EPIC, fire resistant). Hat and jacket are in `#minecraft:head_armor` /
  `#minecraft:chest_armor` (hand-written `data/minecraft/tags/item/`), which gives them the vanilla armour enchantments.
- Banner pattern: `data/radiocraft/banner_pattern/fcc.json` (asset `radiocraft:fcc`, key `block.radiocraft.banner.fcc`),
  tag `data/radiocraft/tags/banner_pattern/pattern_item/fcc.json`, textures `textures/entity/banner/fcc.png` and
  `textures/entity/shield/fcc.png` (vanilla's atlases pick up every namespace's `entity/banner` and `entity/shield`).
  `FccGear.PATTERN` / `PATTERN_ITEM` are the keys. `FccGear.banner(HolderLookup.Provider)` builds the ready-made blue
  banner (yellow border, white seal, item name "FCC Banner"); it is in the creative tab, not craftable (use the loom).
- Badge: data component `radiocraft:fcc_badge` (`FccBadge(agent, agentId, number)`), pure rules in `FccBadges`
  (number = a hash of the UUID into 1000-9999, so a reissued badge keeps its number; names cut to 32). Command
  `FccCommands`: `/radiocraft fcc badge <players>`, `requires(hasPermission(2))` on the `fcc` node (the `radiocraft`
  root stays open for `/radiocraft photos`), gives via `FccGear.issueBadge` (inventory, else dropped). Right click
  (`FccBadgeItem.use`) sends `shownMessage` to players within 8 blocks (gold for your own badge, red "issued to X" for
  someone else's, grey for a blank), a chain clink, 40-tick cooldown. Not in the creative tab.
- Curios: `necklace` added to `data/radiocraft/curios/entities/radiocraft.json`; `data/curios/tags/item/necklace.json`
  lists the badge. No Curios code (nothing to tick); without Curios it is an ordinary item.

**Recipes added** (for review): FCC Field Agent Hat shaped `WWW / WBW` (brown wool, black dye); FCC Field Agent Jacket
`W_W / WTW / WWW` (brown wool, red dye for the tie); FCC Banner Pattern shapeless paper + lightning rod. Badge: none.

**Docs**: Handbook `admin/fcc_gear` ("FCC Field Gear", in Server Admins; the category description now mentions it);
NeedsTesting "1.12 step 10"; ModelDesignBounty "FCC field gear"; release notes. Website wiki not touched (step 13).

**Left undone / gotchas**:
- The hat is only an armour-layer texture, so it looks like a brown cap with a band, not a fedora with a brim. A real
  brim needs a small model (`IClientItemExtensions#getHumanoidArmorModel`); left for the bounty art.
- Nothing draws the badge on the player when it is worn in the Curios slot (no `ICurioRenderer`).
- The badge is not hidden from JEI (it shows there with no recipe); a creative player can still pick it from JEI.
- The badge does nothing beyond showing itself: no powers (e.g. reading a radio's MARS mod, or the moderation
  computer) are tied to it.

**Open questions for the maintainer (Step 10)**:
- ~~Necklace or chest slot?~~ Resolved by the coordinator from the ideas file ("chest curios slot"): the badge is in
  Curios' `body` slot (`data/curios/tags/item/body.json`, `body` in the entities file); the GameTest is now
  `badgeIsWornOnTheBodySlot`. Older notes above that say necklace are out of date.
- **Should the badge do something?** Today it is a mark of office you can flash at people. Ideas: show the holder's
  badge in the moderation computer, let a badge holder read a radio's MARS mod / out-of-band transmissions, or tag
  their chat. Also: should the badge be hidden from JEI and dropped on death like any item (it is now)?
- **Recipes and look**: brown wool for a 1970s felt hat and corduroy jacket, paper + lightning rod for the seal
  (the old FCC seal had lightning bolts). The ready-made banner is blue with a yellow border and a white seal.

### Step 11
**UNBUILT / UNTESTED** (2026-11-05): no Gradle command run, nothing committed, no GameTest run, and no real sound card
or serial port touched. Checked by reading and by the plain `javac -Xlint:all` type-check of the whole tree (main,
test, gametest) with `scratchpad/new.sh all`, with jSerialComm 2.11.4 (downloaded from Maven Central, sha1 checked, into
`scratchpad/dl_jserialcomm/` and appended to `scratchpad/cp_main3.txt`; the old list is `cp_main3.before_step11.txt`):
**0 errors, no new warning**. Scratch reflection run (no Gradle): `InterfaceSpecTest`, `ResamplerTest`,
`PcmCodecTest`, `KeyingTimerTest`, `ConfigNamesTest`, `ModelFilesTest`, `ConfigRoundTripTest` pass (33 tests). All
new JSON parses. Builds on the uncommitted Steps 1-10.

Verify first, in this order:
1. **The jar packaging.** `./gradlew build` (javac said clean). Then open `build/libs/radiocraft-<v>.jar`:
   `META-INF/jarjar/jSerialComm-2.11.4.jar` must be there and listed in `META-INF/jarjar/metadata.json`, and there must
   be no `com/fazecast/` entries loose in the jar (verifyModJar now checks both). Then `./gradlew prodLaunchTest`: the
   server and client must start with the feature off, with no jSerialComm line in the log. If NeoForge refuses a
   non-mod nested jar, or the mod cannot see `com.fazecast.jSerialComm` at runtime (it is only ever reached by
   `Class.forName`, so this shows up only when a serial port opens), the fallback is to add `FMLModType: GAMELIBRARY`
   to the nested jar's manifest via a small repackaging task; do **not** shade/relocate it (see below).
2. `./gradlew runData` and commit `src/generated`: lang (`addRealWorldInterface`, the moderation kind "Real radio"),
   the recipe, blockstate and block item model (`radio(...)` in the blockstate provider), POWER and COAX tags, loot
   (the RadioBlock branch, copies the MARS component like every radio), config names.
3. `./gradlew test`: new `common/realworld/InterfaceSpecTest`, `ResamplerTest`, `PcmCodecTest` (also covers
   `FrameChunker` and `AudioLevels`), `KeyingTimerTest`; `ConfigNamesTest` gained a line; `PatchouliBookTest` needs
   runData for `radiocraft:real_world_interface` (recipe and item model).
4. `./gradlew runGameTestServer`: new `RealWorldInterfaceGameTests` (6, batch `real_world_interface`; @BeforeBatch
   turns the feature on, lists six `GT ...` interfaces and installs the `FakeBackend`; @AfterBatch puts them back):
   `bridgesBothWaysWithVox` (147.420), `quietAudioStaysOffTheAir` (147.440), `cosKeysInAndTimeoutDropsPtt` (147.460,
   8 kHz card, inverted DTR, 2 s time-out), `oneBlockPerInterface`, `devicesCloseWhenTheBlockGoes` (147.500),
   `everywhereIgnoresDistance` (147.480, a J-pole station 15 blocks off). The bridges run in real time on their own
   threads, so the tests wait on conditions; the riskiest timing is the time-out test (60 ticks of talking must last
   more than 2 s of wall time, and a lagging server must not leave gaps longer than the 300 ms PTT hang). Re-run the
   moderation, repeater, handheld and DTMF tests: `ModerationHooks.voice`, `BEVoiceReceiver.receive` and
   `RadioBlockEntity.tick` changed (see below).
5. Real hardware: NeedsTesting "1.12 step 11" (downlink/uplink audio, pitch at 8/44.1/48 kHz, PTT and COS lines,
   time-out, hot-unplug, server stop releases the devices).

**How it works.**
- Pure (package `common/realworld`, no game classes, unit tested): `InterfaceSpec` (one config line, `key=value;...`,
  `parse/parseAll/find/format`, `triggerText/pttText`, defaults in `DEFAULTS`); `Resampler` (streaming windowed-sinc,
  32 taps x 256 phases, Blackman, cutoff 0.9 x the lower Nyquist; the read position is an exact fraction so any block
  split gives the same output; equal rates pass through); `PcmCodec` (16-bit LE/BE, channels averaged in, copied out);
  `FrameChunker` (960-sample frames); `AudioLevels` (RMS dBFS, clipped gain, a falling meter); `KeyingTimer` (key on
  activity, hang, time-out that stays off until 1 s of quiet: used both ways).
- Devices: `AudioBackend` (interfaces `Input`, `Output`, `Port`, enum `Line` RTS/DTR/CTS/DSR, `NotFoundException`).
  `JavaSoundBackend` (`javax.sound.sampled`: 16-bit LE, mono else stereo, 100 ms line buffers; a device is matched by
  exact name, else the first mixer whose name or description holds the text, "default" = the system's) reaches
  `SerialControlPort` (the only class naming jSerialComm) **by reflection only**. `Bridges` holds the config (parsed
  once per list change, bad lines logged), the claims (one block per interface, `claim/owner/release`), the bridges
  (`open` closes the interface's previous bridge, the new one waits up to 3 s for it to let go), `closeAll` (on
  `ServerStoppingEvent`, in `Radiocraft`'s constructor) and `setBackendForTesting`.
- `Bridge`: three daemon threads per running interface, never the server thread. *link* opens the port (setting the
  PTT line unkeyed at once, since opening a port raises RTS/DTR on many systems), the output and the input, retries a
  missing one every 10 s, hands each frame from the real radio to the block, and closes everything when stopped;
  *capture* reads 20 ms blocks, applies `input_gain`, resamples to 48 kHz, cuts 960-sample frames and gates them
  (VOX: RMS >= `vox_threshold`, hang `vox_hang`; COS: the line, 150 ms debounce) with the time-out, never while PTT is
  keyed (simplex); *playback* keys PTT on the first in-game frame, writes `ptt_delay` of silence, then the frames
  (gain, resampled to the card's rate), silence through `ptt_hang`, and drops PTT after it or at the time-out. Queues
  of 25 frames each way drop their oldest when full (the voice thread only ever offers). Status (`Bridge.Status`) is
  volatile fields.
- Block `real_world_interface` (`RealWorldInterfaceBlock extends RadioBlock`; BE `RealWorldInterfaceBlockEntity extends
  RadioBlockEntity`): a radio on power and coax (or the bare connector), any band (`setTunedFrequency` picks the band
  holding the frequency, FM on VHF/commercial, SSB on HF ham; 500 Hz / 2.5 kHz grid), FM or SSB, no mic, no PTT, no
  hand mic, never blows, no client sounds. Its BEVoiceReceiver has a **tap** (new `BEVoiceReceiver.setTap`): what it
  would play goes, mixed and processed as a speaker would hear it, to `Bridge.offerDownlink`, with no voice chat
  server needed. Frames from the real radio go out through `transmitVoice` (antenna/bare, `RadioModeration`, the
  moderation log under the new `ModerationHooks.Tag.REAL_WORLD` / `TransmissionKind.REAL_WORLD`, added last in the
  enum so query masks keep their bits). `txActive` (polled from the bridge each tick) is `canTransmitVoice`, so the
  receiver is deaf while it transmits. The link starts/stops on the 10-tick sync (or at once after a change): enabled,
  picked, claimed, switched on and powered; a config edit restarts it.
- **Everywhere** mode: `usesBareConnector()` (new hook in `RadioBlockEntity.tick`) is false; an `EverywhereAntenna`
  (`common/radio/antenna`) on the band's network sends every antenna in the dimension the frame at strength 1 (each
  applies its own gain and pattern) and hears nothing through the network. Hearing is at the source:
  `ModerationHooks.voice` (every voice transmitter's one call, already at every source) now ends with
  `Everywhere.onAir`, which hands the frame to each listening interface (registered only while its link is live) as a
  packet whose destination is its EverywhereAntenna; `receiveVoice` accepts only those in Everywhere mode (and only
  antenna packets otherwise), `isSkipPath` false and `desense` 1.
- Screen `client/screens/RealWorldInterfaceScreen` (plain fills): the licence warning, `<`/`>` over "(none)" plus the
  configured names, MHz box + Tune, FM/LSB/USB, Antenna/Everywhere, Power, then state, each device FOUND / NOT FOUND /
  WON'T OPEN, PTT line, trigger and time-out, lamps (PTT, TX, TOT, ON) and In/Out meters. Packets
  `SRealWorldInterfacePacket` (SELECT, FREQUENCY, MODE, EVERYWHERE; refusals over the hotbar) and
  `CRealWorldInterfacePacket` (the status tag, every 5 ticks to players with the menu open, and once from
  `createMenu`; the screen keeps the last one in a static because that first one arrives before the screen opens).
- Config (server, section "Real-World Interface", the licence warning in its comment): `real_world_interface_enabled`
  (false), `real_world_interface_op_only` (true: operators only may pick, tune, change mode or Everywhere; power stays
  open), `real_world_interfaces` (list of strings; the element check accepts any string so a typo is logged, not
  silently dropped by NeoForge's correction). The mod list's Config screen hides every `real_world_interface*` key
  (a `ConfigurationScreen` filter in `ClientSetupEvents`), as the maintainer asked: file only.
- Commands (`RealWorldCommands`, op level 3, merged into `/radiocraft`): `realworld devices` (sound inputs, outputs and
  serial ports, gathered on a short-lived thread; only when enabled) and `realworld status`.
- **Packaging choice** (build.gradle.kts): `implementation` + `jarJar("com.fazecast:jSerialComm:[2.11.4,3.0)")`, not
  Shadow. jSerialComm's native code binds to its Java class names through JNI, so relocating the package would make
  every native call fail; shading it unrelocated would put a second `com.fazecast.jSerialComm` package next to any
  other mod's copy (the JOrbis module clash of issue #101). Nested whole, it keeps its module name, its native
  libraries (`Windows/`, `Linux/`, `OSX/`... at its root, unpacked to the temp folder on first use) and its signature,
  and NeoForge dedupes it across mods by version range. Nothing reaches it unless the feature is on (reflection).
- Recipe (for review): Real-World Interface shaped `ICI / SRM / IBI` (iron, comparator, radio speaker, radio interface,
  hand microphone, HF circuit board).
- Docs: Handbook `admin/real_world_interface` (spotlight with the licence warning, three text pages, the recipe; the
  Server Admins description mentions it); NeedsTesting "1.12 step 11"; ModelDesignBounty "Real-World Interface"
  (placeholder from `tools/real_world_interface_assets.py`); release notes.

**Left undone / gotchas**:
- Untested against any real sound card or serial port. Device matching by name substring may pick the wrong one of two
  similar cards: give the full name. Some cards will not open mono or at the rate asked; the screen then reads WON'T
  OPEN with the javax message (use `rate=48000`, the usual native rate).
- Everywhere mode hears only what goes through `ModerationHooks.voice`: players, handhelds, manpacks, block radios,
  repeaters passing a signal on, tapes through a radio. Commercial radio modules' broadcasts and the WEFAX coast station
  are not voice transmissions there and are not heard; CW and data are not carried at all (voice only, as asked).
  Receivers far from an Everywhere interface still apply their own skip-path fading (QSB) to its signal.
- No local monitor speaker: the block itself plays nothing in game. The status meters are the only sign of the audio.
- The server config (with the device names) is synced to every client, as all RadioCraft server settings are.
- No station ID or CW ID is sent by the interface; IDing is the operators' job (the warning says so).
- The bridge copies a 48 kHz frame per 20 ms on its threads (small allocations, off the server thread). The voice
  thread's tap renders one extra mix per frame for the block, as a speaker would.

**Open questions for the maintainer (Step 11)**:
- **Op only by default?** `real_world_interface_op_only = true`: only operators pick the interface, tune it or change
  the mode/Everywhere; anyone can switch it on or off (power is the radio packet every radio uses). Lock power too?
- **Licence acknowledgement**: the warning is always on the screen, in the tooltip, config and Handbook. Should players
  have to click "I hold a licence" (or have a callsign set with `/callsign`) before their voice goes out through it?
  The bridge cannot tell who is talking on the in-game side today (it hears the mixed receiver audio).
- **Everywhere and fading**: Everywhere ignores the path on its own side, but a receiver far away still applies its
  usual skip fading to it. Should Everywhere signals arrive perfectly steady (a flag on the packet the receivers check)?
- **What Everywhere hears**: should it also hear commercial modules' broadcasts and the WEFAX station?
- **A monitor speaker** on the block (what goes out to the real radio, quietly, for people standing by it)?
- **Defaults**: VOX -35 dBFS / 800 ms hang, PTT hang 300 ms, TX delay 150 ms, time-out 180 s. Real repeaters often run
  a shorter tail; say if you want different numbers.

### Step 12
**UNBUILT / UNTESTED** (2026-11-05): no Gradle command run, nothing committed (either repo), no GameTest run. Checked
by reading and by the plain `javac -Xlint:all` type-check of the whole tree (main, test, gametest) with
`scratchpad/new.sh all`: **0 errors, no new warning**. `javac -Xdoclint:missing/protected` over `api/` leaves only
missing `@param`/`@return` tags on the older weather records and `WeatherSource` (every type and method has a
comment). Scratch reflection run (no Gradle): new `api/CompanionApiTest` (6) plus `BandTest`, `BandEdgesTest` pass
(17). Builds on the uncommitted Steps 1-11.

Verify first, in this order:
1. `./gradlew compileJava compileTestJava compileGametestJava` (javac said clean).
2. `./gradlew test`: new `src/test/java/com/arrl/radiocraft/api/CompanionApiTest` (band registry, `RadioSpec`,
   `SimpleAntennaType` coverage). No runData needed for this step (no lang, recipes, tags or models).
3. `./gradlew runGameTestServer`: new `gametest/example/CompanionApiGameTests` (batch `companion_api`, 3 tests):
   `exampleRadioTalksToStockLowBandMobile` (33.120 MHz, bare connectors, both ways), `exampleAntennaCarriesTheSignal`
   (33.140 MHz, example radio -> coax -> example ground plane, SWR 1.2, both ways with a stock low band mobile on its
   bare connector 2 blocks from the antenna), `bandRulesThroughTheApi`. **Riskiest**: that NeoForge accepts
   registering blocks under the foreign namespace `radiocraft_example` from the radiocraft dev mod (`RegisterEvent`;
   if it refuses, set `ExampleCompanionMod.NAMESPACE` to `radiocraft` with `example_*` paths, then check that
   `BlockDropGameTests` and datagen do not iterate them), and that `@EventBusSubscriber(modid = "radiocraft")` without
   `bus` routes `RegisterEvent`, `RegisterCapabilitiesEvent` and `FMLCommonSetupEvent` to the mod bus (it does for
   `RadiocraftCapabilities`; `bus = MOD` is deprecated for removal in 21.1.248 and warns under -Xlint). Re-run the
   antenna, CW, data/APRS, handheld and solar tests: `AntennaNetworkObject`, `BareConnectorAntenna`, `PlayerRadio`,
   `SolarEventManager.getCondition` and the `WireBlock`/`WireUtils` connection checks changed (see below).
4. runClient: NeedsTesting "1.12 step 12" (the example blocks via /setblock, wires join them, stock radios unchanged,
   the website page).

**API added** (all in `com.arrl.radiocraft.api`, Javadoc on every type and method, `@since 1.12.0`):
- `RadiocraftApi`: `API_VERSION` "1.0" (+ `_MAJOR`/`_MINOR`), `BE_NETWORKS`/`HANDHELDS` capability handles,
  `registerNetworkBlocks(event, blocks...)` (the BE_NETWORKS block capability: the gap that kept any other mod's
  radio from ever powering on), `registerHandhelds(event, items...)` (handheld state + `RADIO_ENERGY` energy),
  `radioNetworkObject(level, pos, tx, rx)`, `networkObject(level, pos)`, `Tags.POWER_BLOCKS/COAX_BLOCKS/DATA_BLOCKS`.
- `radio.RadioBands`: band name constants (`M80`..`M2`, `LOW_BAND`, `VHF_COMMERCIAL`), `get`, `at`, `all`,
  `register(band, amateur, solarLike, displayName)`, `registered`, `isAmateur`, `antennaNetwork`, the `BandEdges`
  rules (`tuneMin/Max`, `canTune`, `canTransmit`, `isOutOfBand`, `splatterReach`), `marineChannel(name)` ->
  `MarineChannel` record. `radio.RadioSpec` (record: band, fm, stepHz, tx/rx FE, tunesOutOfBand; `fm()`/`ssb()`,
  withers). `radio.CompanionRadioBlockEntity extends RadioBlockEntity` (spec-driven, `createMenu` returns null),
  `radio.CompanionRadioBlock extends RadioBlock implements IWireConnectable` (power/coax/data without tags).
- `antenna.SimpleAntennaType` (final, extends the internal `NonDirectionalAntennaType<EmptyAntennaData>`; builder:
  `block`/`matcher`, `band(name, swr, dBi)`, `range(min, max, swr, dBi)`, `outsideSwr` (10), `lineOfSight`, `skip`,
  `lightningDamageFactor`; per-band gain on top of the stock propagation). `antenna.CompanionAntennaBlock` (feed
  point: coax without a tag, ticks `AntennaBlockEntity::tick`, `neighborChanged` -> `markAntennaChanged`, leaves the
  networks on removal through `AbstractNetworkBlock`), `antenna.CompanionAntennaBlockEntity`, `antenna.IAntennaBlock`
  (`getAntennaNetwork(state)`), `antenna.AntennaNetworks` (`HF`, `VHF`, `forBand`, `get`). `AntennaTypes` gained
  `registerPriorityType` (asked before the ordinary types) and `getTypes()`.
- `blockentities.radio.IRadio` (implemented by `RadioBlockEntity`: band, frequencies, power/toggle, PTT,
  canTransmitVoice, inhibited, `mayTransmitOn(hz)`, out of band, MARS, FM, last signal, SWR),
  `blockentities.radio.IAntennaReceiver` (`receiveVoice`, default `receiveData`; implemented by `RadioBlockEntity`),
  `ICWReceiver.acceptsCW(packet)` (default `canReceiveCW()`).
- `items.IHandheldRadio` (`transmitBand(stack)`, `mayTransmit(stack, txHz, mars)`), `benetworks.IWireConnectable`
  (`acceptsPower/Coax/Data`, static `accepts`, `acceptsDataWire`), `weather.SolarSource` + `weather.SolarSources`
  (`register`, `unregister`, `condition`).

**Internal hooks (small)**: `Band` has a registry of other mods' bands (`Band.Registered`, `register`,
`getRegistered`, `isRegistered`; `getBand(name)` checks it first, `getBand(float)` includes it, and `isAmateur`,
`displayName`, `conditionName` read it; stock names are refused). `RadioBlockEntity` implements `IRadio` and
`IAntennaReceiver`; new overridable `mayTransmitOn(double hz)`, which `isTransmitInhibited` uses (commercial and
marine override `isTransmitInhibited` themselves, unchanged). `AntennaNetworkObject` hands voice/data to any
`IAntennaReceiver` (was `RadioBlockEntity`) and CW to non-HF `ICWReceiver`s that `acceptsCW`; `BareConnectorAntenna`
the same for CW. `AntennaBlockEntity` has a constructor taking any `BlockEntityType`, and `networkFor` asks
`IAntennaBlock`. `WireBlock.isValidConnection/isDataConnection` and `WireUtils.connectToWires` also accept
`IWireConnectable`. `PlayerRadio` asks `IHandheldRadio` for the TX band and permission (ham branch only).
`SolarEventManager.getCondition(level, band)` asks `SolarSources` first (empty list: one `isEmpty` check). Javadoc
filled in on the older API types (IAntenna, IAntennaPacket, IAntennaType (its TODO removed), BENetworkObject,
PowerBENetwork, PowerSharing, IVoiceTransmitter, the IVHFHandheldCapability setters, WeatherReport, DayOutlook).

**Example companion mod** (`src/gametest/java/com/arrl/radiocraft/gametest/example/ExampleCompanionMod`): imports
only the api package (plus the `Band` record), vanilla and NeoForge. Registers band `6m` (50-54 MHz, amateur, follows
10m's solar weather), `radiocraft_example:low_band_radio` (`CompanionRadioBlock`, `RadioSpec.fm(LOW_BAND, 12_500)`)
and `radiocraft_example:ground_plane` (`CompanionAntennaBlock` on HF, `SimpleAntennaType`: low band SWR 1.2/0 dBi,
6m 1.5/+1 dBi). No items, models or lang on purpose (never shipped). Because the GameTest source set is in every dev
run, the dev client and dev server have these blocks and the 6m band too (nothing stock tunes to 6m;
`Band.getBand(50.0 MHz)` gives 6m, the narrower band, instead of low band, in dev runs only).

**Website** (`C:\Users\ldell\Documents\GitHub\Radiocraft-Website`, not committed): new SITE_PAGES entry `"modding"`
("Modding API", 12 sections: getting started with Modrinth Maven coordinates `maven.modrinth:radiocraft:<version>`
and the honest note that RadioCraft has no Maven of its own yet, API overview, bands and band edges, adding a radio,
an antenna, handhelds, networks, hearing signals, weather and solar sources, events and threads, versioning, the
example) and a `_code()` helper in `tools/build_wiki.py`; `.wiki-main pre.wk-code` rules at the end of `styles.css`;
`wiki/modding.html` generated by running the builder in a scratch copy of the site. Only that page was copied back,
so the other pages' sidebars and `search.json` list it once step 13 re-runs `python tools/build_wiki.py E:\Radiocraft`.

**Left undone / gotchas**:
- Handhelds stay on the VHF network with the 143-174 MHz dial (`VHFHandheldCapability` clamps to 2m's tuning
  range): an HF or low band handheld is not possible through the API. `CompanionRadioBlockEntity` has no CW (extend
  the internal `HFRadioBlockEntity`) and no screen; another mod cannot reuse the stock radio screens.
- Registered bands are not in the band plan sheet, `/bandcalculate`, the solar station's list or the analyzer, and
  have no config entry. An amateur VHF band registered above 174 MHz gets no upper tuning extension (`BandEdges` uses
  the commercial VHF top for every amateur VHF band).
- No custom NeoForge events were added (the guide says so): posting per-frame events from the voice thread is the
  reason not to.
- `SimpleAntennaType` looks its bands up by name on every SWR call (the radio asks each tick), like the stock J-pole.

**Open questions for the maintainer (Step 12)**:
- **Publishing**: the build has `maven-publish` but no repository and no `group`, so nothing is on a Maven; the guide
  points developers at Modrinth's Maven. Want a real Maven (GitHub Packages, say, with sources and Javadoc jars) and a
  `group` such as `com.arrl`?
- **API version**: started at "1.0" (`RadiocraftApi.API_VERSION`), separate from the mod version. OK, or tie it to the
  mod version?
- **Wiki placement**: the Modding API page is listed under "BEFORE YOU PLAY" with the other hand-written pages (that is
  how `build_wiki.py` groups SITE_PAGES). A separate "FOR DEVELOPERS" heading needs a small change to `sidebar()`.

### Step 13
**Docs only, NOT COMMITTED (either repo), no Gradle run** (2026-11-05). No Java touched. Every book JSON parses; a
script copy of `PatchouliBookTest`'s checks (plus link anchors) finds only recipes and item models that runData makes.

**Handbook** (`assets/radiocraft/patchouli_books/guide/en_us`), checked entry by entry against the recipe provider,
lang provider, config defaults and the apps' key handlers (keys bars and `onKey` agree; saving is Ctrl+S everywhere):
- Category order: Computer and Telephones were both sortnum 11. Now Telephones 11, Radio Computer 12, Aviation 13,
  Server Admins 14. Radios and Digital Modes descriptions mention commercial/marine/MARS and the camera.
- `propagation/bands`: low band said "carries a little further than 10 m"; `Band` has LOS 650 vs 10m's 800, so it now
  says further than 2 m, not quite as far as 10 m, day skip only.
- Crafting pages added (recipe ids = item ids, need runData): `band_plan` (band plan entry), `mars_mod` (+ which radios
  take no mod), basic/upgraded commercial handheld and commercial/low band mobile (commercial entry; the upgrade starts
  on factory channels), marine handheld + marine radio. Links added band plan <-> commercial/marine radios, MARS ->
  band plan, commercial -> frequency manager. Welcome page mentions commercial and marine radios. Scanner names the
  Printout item.
- New entry `getting_started/ham_shacks` (sortnum 4): where shacks spawn (biome tag, five village types) and what is
  inside (base station on 146.520, battery, J-pole, computer with Logbook and FREQMAN, a chest of parts; the secret
  disk is only hinted at).
- Book name is "ARRL Radiocraft Handbook" (book.json, landing text); no "F2" or "necklace" left in the book, notes or wiki.

**Release notes** (`doc/ReleaseNotes-1.12.md`): rewritten as one 1.12.0 section grouped by feature (bands/MARS,
commercial, marine, camera, computer, secret disks + shacks, ADS-B, FCC, Real-World Interface), then Changes, a new
**Not finished yet** list (placeholder art, RWI untested on hardware and voice only, ADS-B ignores terrain and is
untried on Create Aeronautics, no DSC/NOAA voice, music only at the screen, AWM sirens to come) and For mod developers
(with the API's limits). Fixed: low band range, "five MURS channels" for every radio (low band has its own four),
"a handheld" for the MARS mod (VHF handheld only).

**Website** (`C:\Users\ldell\Documents\GitHub\Radiocraft-Website`, nothing committed or pushed):
- `tools/build_wiki.py`: "ARRL Radiocraft Handbook" in the docstring, `NAME_FIXES` and the wiki home title; Installing
  lists Curios' slots (belt, back, FCC badge chest slot) and Sable/Create Aeronautics for ships and ADS-B; two FAQ
  entries (`no_license`: commercial/marine need no call sign, MARS is contraband; `real_radio`: the Real-World
  Interface); the Blocks & Items table counts spotlight items as covered and shows a program floppy's description as
  its tooltip.
- Rebuilt every page: new `wiki/computer.html`, `wiki/aviation.html`, `wiki/admin.html` (already untracked), all
  sidebars and `wiki/search.json` now list Modding API, the new categories and entries. The 3D renders for the ADS-B
  blocks and the moderation computer are not made (needs `--render`).
- **runData has not run**, so the builder was pointed at a scratch mod root
  (`7f2e3421-.../scratchpad/s13/modroot`: `src/main` is a junction to the real one; `src/generated` is a copy plus the
  1.12 lang strings parsed from `RadiocraftLanguageProvider` and the 1.12 recipes written out by hand from
  `RadiocraftRecipesProvider`, by `scratchpad/s13/overlay.py`). **After runData, re-run
  `python tools/build_wiki.py E:\Radiocraft`** so the wiki comes from the real generated files (block item icons for
  the new blocks appear only then).
- The secret "Disks Never Sold" entry is on the wiki like any other (the builder ignores `secret`); the release notes
  name the disks anyway. Say if the wiki should hide it.

**For the code agent** (found, not fixed; no Java edited):
- `Band.java` Javadoc on `getCommercialDefaults` (~line 198) says low band "carries a little further than 10m by line
  of sight", but its LOS is 650 against 10m's 800. Docs now follow the numbers; fix the comment (or the number, if
  further than 10m was meant).
- `RadiocraftLanguageProvider.addFccGear`'s Javadoc is a copy of the moderation computer's ("The Radio Moderation
  Computer: its block, lock messages...").
- Builder warnings worth a look: no tooltip and no handbook mention for `field_phone_handset`, and the premium speakers'
  item ids read as `radiocraft:gold` / `iron` / `marble` in the wiki's item list (older, not 1.12).

### AWM step
**UNBUILT / UNTESTED, NOT COMMITTED** (maintainer asked for no Gradle runs while playing). All changes sit uncommitted
in `E:\AnotherWeatherMod`. Verify first: `./gradlew build` (compile, `-Xlint:all`), `./gradlew test`
(`block/SirenWiringTest`), `./gradlew runGameTestServer` (`SirenControllerGameTests`, 3 tests; the existing
`sirenWindsUpOnRedstone` covers plain redstone), then commit in AWM. Check the mixin injects into
`ServerLevel.onBlockStateChange` and that the 1-px wire models and climb pieces render (NeedsTesting has the list).
- Blocks: `siren_controller` (`SirenControllerBlock`/`SirenControllerBlockEntity`, `POWERED` lamp) and `data_wire`
  (`DataWireBlock`, redstone-like: floor run, straight up/down, climbs walls and steps down ledges). Sirens implement
  `WiredSiren`; `TornadoSirenBlockEntity` runs at max(own redstone, controllers' drives); it saves the drives.
- Tags: `anotherweathermod:siren_wire` (AWM wire + optional `radiocraft:data_wire`, its 16 colours and
  `radiocraft:passthrough`), `siren_wire_joins_all` (AWM wire, passthrough; colours otherwise join only themselves),
  `sirens` (tornado_siren). AWM also adds its controller, siren and wire to `radiocraft:data_blocks`, so RadioCraft's
  data wire draws its link to them; nothing to do on the RadioCraft side.
- Finding sirens: `SirenWiring.flood` (pure), run by `world/SirenWires` only when a powered controller's cache is stale
  (load, or a block turning into/out of siren wire or a siren anywhere in the level, via `MixinServerLevel`); work is
  batched to the end of the tick. Stops at unloaded chunks (retries on chunk load, max once a second) and 4096 wire.
  RadioCraft wire bundles are not followed.
- Choice recorded in AWM `doc/Design.md`: AWM's data wire is **always registered** (registry stays stable when
  RadioCraft is added/removed, laid wire survives and joins RadioCraft's); with RadioCraft loaded it is hidden from
  the creative tab and its recipe/advancement are off by `neoforge:not mod_loaded radiocraft`.
- Recipes need maintainer approval: controller `IRI/RCR/III` (iron, redstone, comparator); AWM wire
  `WWW/CCC/WWW` (wool, copper ingot) makes 16. Art needed: controller (placeholder cabinet + lamp) and wire/coil
  item (placeholder). No new siren types and no audio change. The AWM jar was not copied to `libs/`; no API change.

### Cross-step review
**UNBUILT / UNTESTED** (2026-11-05): a review of Steps 1-12 together, no Gradle command run, nothing committed. Whole-tree
`javac -Xlint:all` type-check (`scratchpad/new.sh all`): **0 errors, no new warning** after the fixes below. All 104
unit test classes ran outside Gradle with a scratch reflection runner (`scratchpad/runner2/RunTests2`, handles
`@BeforeEach`/`@AfterEach`/`@TempDir`; classpath: the type-check output, `src/test|main|generated/resources`, the cached
jars): **552 of 553 pass**; the only failure is `PatchouliBookTest`, every problem in it a recipe or item model that runData
writes (no other book problem).

**Bugs fixed**
- `build.gradle.kts`: jSerialComm was only `implementation` + `jarJar`. It is not a mod, so ModDevGradle's dev runs
  (runClient, runServer, GameTests) only see it through `additionalRuntimeClasspath`; added it there. The shipped jar
  is unchanged (still jar-in-jar).
- `assets/radiocraft/models/block/commercial_mobile.json`, `low_band_mobile.json`, `marine_radio.json` had no `parent`
  and no `display`, and their item models come from datagen (`radio()` -> `simpleBlockItem`), so the items would have
  drawn flat and front-on in the inventory and hand. Added `"parent": "block/block"` (like `real_world_interface`), and
  the same line to `tools/commercial_radio_assets.py` and `tools/marine_radio_assets.py` so a re-run keeps it.
- `RealWorldInterfaceGameTests.cosKeysInAndTimeoutDropsPtt` could never pass: it talked for 60 ticks and expected the
  2 s wall-clock time-out, but the GameTest server does not wait between ticks (60 ticks are milliseconds) and 60
  frames are only 1.2 s of audio anyway. It now talks every tick until `txTimedOut`, then waits for DTR to drop.
  `quietAudioStaysOffTheAir` passed without testing (the bridge had not read the queued frames yet); it now waits for
  the fake mic to drain first. The batch's timeouts were raised (4,000-20,000 ticks) since every bridge test waits on
  wall-clock threads.
- Javadoc only: `Band.getCommercialDefaults` (low band carries further than 2m, not quite as far as 10m: LOS 650 vs
  800) and `RadiocraftLanguageProvider.addFccGear` (had the moderation computer's comment).

**Checked and fine** (so the first build need not look there): every new block, item, BE type, menu (+ screen),
payload (37, all registered on the right direction), data component (persistent and stream codecs agree), recipe
serializer, structure processor and armour material is registered once (no duplicate ids); NeoForge registers armour
materials before items (`GameData.getRegistrationOrder`), so `ArmorItem`'s constructor sees `radiocraft:fcc`. Every
new BE that joins a network is in `RadiocraftCapabilities`' BE_NETWORKS list; the handheld capabilities cover the
commercial and marine handhelds. Common code reaches client classes only from `isClientSide` branches or client packet
handlers (lazy linking, safe on a dedicated server). Lang: a scripted scan of the language provider finds no duplicate
key (1,549 keys), and every literal key the new code uses exists. Datagen: blockstates/models for the 7 datagen blocks
use hand-made block models that exist; ADS-B blockstates and item models are hand-made and not generated (no clash);
all 22 new crafting recipes have `unlockedBy` and pattern keys that match. Hand-written JSON (64 files) is valid for
1.21.1: singular tag folders, `neoforge:add_table`/`loot_table_id`, all 21 vanilla chest ids exist, banner pattern
`asset_id`/`translation_key`, the 1.21 advancement item predicate, the `radiocraft:site` structure and
`rotate_blocks` processor codecs, every referenced model and texture. Item tints return opaque ARGB (1.21 reads the
alpha). GameTests: templates exist, batches pair with their before/after hooks, config flips are restored, no two
tests in one batch share or splatter onto a frequency; the example companion mod's `radiocraft_example` blocks are not
iterated by `BlockDropGameTests` or datagen.

**Suspicious, left alone**
- A commercial channel's encryption key travels to every client in the `commercial_program` component even when it
  is "unreadable" (`keyReadable` false hides it from the CPS only); a modified client could read it.
- Creative pick-block (Ctrl+middle click) on a radio computer copies its `computer` component, so two computers would
  share one disk (same id). Survival is unaffected.
- The server config, including `real_world_interfaces` (sound card and serial port names), is synced to every client,
  like all RadioCraft server settings (Step 11 notes it).
- Production: jSerialComm is reached by reflection from `SerialControlPort`; whether the mod's module can see the nested
  non-mod jar is only known once a serial port opens on a real server (Step 11's fallback: `FMLModType: GAMELIBRARY`).
- `Band.getBand(float)` builds a new `Band` per config entry per call (old pattern, now 7 bands instead of 5); fine
  unless a profiler says otherwise.
- `RadioFaxMenu.quickMoveStack` overwrites `moved` in its supply loop (older code; harmless while each item fits one
  supply slot, which holds with photo paper as its own item).
- Timing-sensitive GameTests: `RadioAppsGameTests.sstvPictureArrives`, `computerFaxesAMachine`,
  `machineFaxesAComputer`, `wefaxChartPrintsOnAReceiver`, the APRS ack test; `FccGameTests.badgeIsWornOnTheBodySlot`
  (Curios inventory on a FakePlayer); the RWI bridge tests if one tick stalls past the 300 ms PTT hang.
- Will fail until runData: `BlockDropGameTests.everyBlockDropsItself` (new loot tables), `FccGameTests.recipesMakeTheGear`,
  `CameraGameTests.photoPaperRecipes`, `AdsbGameTests.antennaOnCoax` (coax tags), `PatchouliBookTest`.
- `field_phone_handset` shows the telephone handset's tooltip (same item class, same key); left as is.

**First build session, in order**
1. `./gradlew compileJava compileTestJava compileGametestJava` (javac said clean; watch `-Xlint:all`).
2. `./gradlew runData`. It must finish without "Duplicate translation key" or a missing model/texture; then review and
   commit `src/generated` (lang, ~25 recipes, loot tables, blockstates and item models for the 7 datagen blocks, tags,
   config names).
3. `./gradlew test` (only `PatchouliBookTest` was failing, on runData output).
4. `./gradlew runGameTestServer`. Expect the timing-sensitive tests above to be the ones to look at first; re-run the
   handheld, repeater, fax, APRS, CW and solar tests touched by Steps 2, 3, 4, 7 and 12.
5. `./gradlew build`, then open the jar: `META-INF/jarjar/jSerialComm-2.11.4.jar` listed in `metadata.json`, no loose
   `com/fazecast/` (verifyModJar checks), then `./gradlew prodLaunchTest` with the feature off.
6. `runClient`: NeedsTesting's 1.12 sections, starting with the commercial/marine radios held in the hand (the item
   transforms fixed above), the computer's text mode and the camera.
7. Commit by folder (`git add src doc tools build.gradle.kts README.md`).

### First build (compile + runData)
2026-11-05, first Gradle run of Steps 1-13 (`compileJava compileTestJava compileGametestJava`, `runData`; no `test`,
GameTests, `build` or client run yet; nothing committed). Done together with two maintainer decisions:

- **ADS-B range** (decision): `AdsbMath.range(y, seaLevel, fullY, min, max)` replaces the horizon-factor model. An
  aircraft at absolute Y >= `adsb_full_range_y` (200) is heard out to `adsb_max_range` (0 = twice the horn,
  `microwave_dish_range` x `microwave_horn_range_factor` x 2 = 18,000); below, min + (max - min) x t^2 with t the height
  above sea level over (fullY - sea level), down to `adsb_min_range` (200) at or under sea level. The antenna's height
  no longer counts. `adsb_horizon_factor` is gone. The picture tag's `horizon` became `reach`/`reachLow` (receiver
  screen draws it half size, the program as "Reach 18 km high, 200 m low"). Display ranges gained 12 and 20 km.
  `AdsbMathTest`, `AdsbTracksTest`, `ConfigNamesTest`, `AdsbGameTests` (`outOfRangeIsNotHeard`, batch `adsb_range` sets
  min and max range to 2), Handbook, release notes and the Step 9 handoff updated.
- **Radio computer power** (decision): `RadioComputerNetworkObject` (extends `AdsbNetworkObjects.Switched`) on the power
  wire: POWER_BLOCKS tag, BE_NETWORKS list, `BENetworkTypes`, `leaveNetworks` in `RadioComputerBlock.onRemove`. It draws
  `computer_power_use` (8, the HF radios' receive draw in the common config) while **in use**: a screen open (checked
  every 10 ticks, set at once in `open`) or a ticking program installed (APRS, fax, pictures, which are also the only
  ones that receive packets). Unpowered: background programs do not tick, `receive` drops packets, the menu refuses
  requests (`err.no_power`), and the screen is dark with "No power: run a power wire to the computer" (menu `DataSlot`,
  `RadioComputerMenu.isPowered`); power back reboots the screen. Free when `radios_need_power` is off.
  `RadioComputerGameTests.computerRunsOffThePowerWire` (batch `computer_power`, power on); `RadioAppsGameTests.aPacketCountsOnce`
  now waits until the computer is powered (it called `receive` on the placement tick). Handbook page "Power", lang,
  release notes, NeedsTesting line.

**What broke**: the type-check had been right; the only compile error was `AdsbTracksTest` calling the old
`AdsbPicture.write` signature (my change). No new `-Xlint` warning in main, test or gametest: the 30 + 2 warnings left are
all on lines already committed before 1.12 (this-escape, deprecations, `ClientSetupEvents` unchecked). `runData`
succeeded first time: 121 files written (lang 2,125 keys, ~32 recipes with their advancements, 10 new loot tables and the
radios' `copy_components`, blockstates and item models for the 7 datagen blocks, power/coax/data/pickaxe tags). A script
check found every model and texture the new generated files reference.

**Left**: `./gradlew test`, `runGameTestServer` (expect the timing-sensitive tests listed in the cross-step review),
`build` + `prodLaunchTest`, runClient (NeedsTesting 1.12 sections; the computer's no-power screen and the ADS-B reach
line are new there), then commit by folder including `src/generated`.

### Playtest fixes 1 (2026-11-05)
The maintainer's first playtest asked for three changes, then one more (the ADS-B tube). Built: `compileJava
compileTestJava compileGametestJava` clean (no new warning), `runData` (lang only), `test` and `runGameTestServer` run
(results below). Nothing committed.

- **MUSIC keeps playing**: the player moved out of `MusicApp` into `client/screens/computer/ComputerMusic` (client
  only, one tune per client, `@EventBusSubscriber` client tick). `MusicApp` is now a view on it. Esc leaves the page
  with the music on; C or the `[ Close ]` button on the "Now playing" box closes the screen with the page left in
  front (the session is kept while its music plays, past `KEEP_MS`). While it plays and another page is in front,
  `RadioComputerScreen` draws a yellow note at the right of the top bar (cols 76-78); a click calls
  `ComputerSession.showMusic()` (brings a running MusicApp forward, or launches one). Every client tick `ComputerMusic`
  fades the volume with distance (`ComputerMidi.updateVolume(fade)`, full within 6 blocks, gain falling with the
  square to 0 at 24) and stops on: no level/player or another level (dimension, logout), the block not a radio
  computer (broken), the client BE not powered, or past 24 blocks. Logout also closes the synth.
  Server: the computer stays **in use** (drawing power) while a player listens: platform request `music` {on}
  (`ComputerServer`) -> `RadioComputerBlockEntity.listen(player, on)`; `SStationPacket` also takes `music` with
  on=false without the menu open (only ever removes the sender). Listeners are pruned at each viewer check (offline,
  other level, past `MUSIC_RANGE` 24). The BE now sends **only `powered`** to clients (`getUpdateTag`/`getUpdatePacket`,
  `handleUpdateTag`/`onDataPacket` read just that) with a block update whenever it changes. New GameTest
  `RadioComputerGameTests.musicListenerKeepsTheComputerOn`. Gotcha: the fade is applied per note-on (velocity), so a
  held note keeps its level; a listener whose list ends while away is dropped only by the range/online pruning (the
  client sends `music` off when it stops, which the server takes without the menu).
- **Solar weather found no radio**: `SolarWeatherHandler.receiver` only looked at radios touching the computer or on
  its data wire (`context.linked`), not those on the radio interface of its station bus, which is where APRS, fax and
  the picture programs find theirs (`RadioStation.radios(bus)`). A VHF radio at the interface was never seen. Now
  `SolarWeatherHandler.radios(context)` = linked + bus radios (any band, VHF included); the Ionospheric Processor path
  and `solar_weather_station_needs_radio` are unchanged, and the processor's "beside a radio" check sees the bus radios
  too. A real antenna is still required (the config's own description says so; a dummy load does not hear). The error
  now says what is missing: `solar_no_radio`, `solar_radio_off`, `solar_no_antenna` (replacing `solar_no_receiver`).
  GameTests: `solarNeedsAReceiver` expects `solar_no_radio`; new `solarHearsThroughTheStationsVhfRadio` (batch
  `computer_solar_radio`, config flipped in @BeforeBatch/@AfterBatch): VHF base station + J-pole reached only through a
  radio interface touching the computer: no radio -> radio off -> heard (`source` radio).
- **40 channels on the upgraded handheld**: capacity is per model, `CommercialRadioModel.channelCount()` (basic 16,
  upgraded 40, both mobiles 16), `zones()`, `hasZones()`, `fit(program)`. **Mobiles stay at 16**: a zone switch on them
  would need a control on their screen, the packet and the in-world display; not trivial, so left. `CommercialProgram`:
  `KNOB_POSITIONS` 16, `MAX_CHANNELS` 40 (codec/stream limit), `MAX_POSITIONS` 48 (3 zones x 16), `selected` = zone x 16
  + knob (`knob()`, `zone()`, `zone(z, zones)`, `zoneLetter`), `turn` stays inside the zone (stops at 1 and 16),
  `fitTo(capacity)` drops channels past it and puts a 16-channel radio in zone A. Old saves (<= 16 channels, selected
  0-15) load unchanged (unit test parses the old component JSON). Zone C knob 9-16 are positions 41-48: blank.
  `CommercialProgramming.read/write/writeChannel` use the model's capacity (blanks past it are fine, a channel past it
  is `too_many_channels` at that position); `select` and every store fit the model. `CommercialHandheldItem.program/
  setProgram` and `CommercialMobileBlockEntity.storeProgram` fit too. `SCommercialRadioPacket` takes positions up to 48.
  `Codeplug` always holds 40 positions (`capacity()` from its model, `beyondCapacity()`); a 16-position `.CPG` loads.
  CPS lists the radio's capacity (16 visible rows, scrolls; A/B/C at the first channel of each zone; "zone, knob"
  line; a 40-channel codeplug opened for a 16-channel radio is cut to fit with a red message). FREQMAN's export uses
  the radio's capacity (`capacity` in `CommercialTargets.describe`). The handheld screen has a "Zone A/B/C" button
  (upgraded only); its display shows `ZB CH20` and the mode. `/rcdebug commercial` takes channels 1-40 and shows zone
  and knob. Unit tests in `CommercialChannelTest` and `CodeplugTest`; GameTests `upgradedHandheldHasZones`, the basic
  handheld's 16 in `commercialHandheldIsChannelized`, 40 rows and channel 40 in `programmerKeepsHiddenKeysOnTheGlass`.
- **ADS-B tube in the world** (maintainer's extra): `AdsbReceiverBlockEntity` sends, at each listen (once a second)
  while powered, `AdsbPicture.compact(picture(), 16)` (16 nearest targets on the scope, 12-point thinned trails) in the
  existing `CAdsbPacket` to every player within `SCOPE_VIEW_RANGE` (32) who does not have its screen open (made once
  per listen for all of them). Client: `client/render/AdsbScopes` keeps the last picture per receiver and a 96 x 96
  `DynamicTexture`: `AdsbScope.paint` into colour indices when a picture arrives, then lit in green phosphor (sweep
  wake, afterglow by bearing from a precomputed table) at most every 100 ms; stale after 5 s, freed after 30 s unused
  or on a level change. `client/render/AdsbScopeRenderer` (registered in `ClientSetupEvents`) draws it over the model's
  `scope` element (x 3-13, y 2-12, z 0.9; noted in `tools/adsb_models.py`) only while `LIT`, view distance 32, turned
  by `FACING` like `ComputerMonitorRenderer`. Unit test `AdsbScopeTest.theCompactPictureKeepsWhatTheTubeDraws`.

New lang keys (in `src/generated` after this runData): `screen.radiocraft.commercial.zone`, `screen.radiocraft.computer.`
`err.solar_no_radio`, `err.solar_radio_off`, `err.solar_no_antenna` (`err.solar_no_receiver` removed),
`cps.knob_zone`, `cps.opened_dropped`, `music.close_button`, `music.elsewhere`; changed `music.keys`,
`freqman.too_many`, `freqman.send`, `message.radiocraft.commercial.too_many_channels`. Handbook: commercial radios,
radio programmer, secret programs (music), ADS-B receiver. Release notes and NeedsTesting ("1.12 playtest fixes 1")
updated.

**Results**: `test`: 561 of 562 pass; the one failure is `AdsbMathTest.theFullRangeIsNeverBelowTheMinimum` (line 31:
`range(100, 63, 40, 300, 9000)` expects 300, gets 9000; `AdsbMath`, not touched here, left as found). GameTests, two
runs: every new and changed test passes (commercial_radios 7, computer_solar_radio, the office and computer tests);
both runs fail `HandMicGameTests.handMicTalksHangsUpAndSnapsBack` ("The keyed radio cannot transmit", an HF 20m
radio) and `RadioAppsGameTests.aprsMessageIsAcked` (B gets the message, A never sees the ack: "SENDING"); the first
run also failed `RealWorldInterfaceGameTests.cosKeysInAndTimeoutDropsPtt` ("more than 8 kHz audio", wall-clock timing),
which passed on the second. None of the three touches code changed here; left for the next session.

### Test fixes (2026-11-05)
The three failures left by "Playtest fixes 1". All three were the tests, not the game; no game code changed.
`compileJava compileTestJava compileGametestJava` clean; ADS-B unit tests (`AdsbMathTest`, `AdsbMotionTest`,
`AdsbScopeTest`, `AdsbTracksTest`, 28) pass in the scratch runner. GameTests not run (client was open).

- `HandMicGameTests.handMicTalksHangsUpAndSnapsBack`: it tuned the 20m radio 400 steps up (14.400 MHz). Before 1.12 the
  dial clamped that to 14.350; since Step 1 it tunes a quarter band past the edge, so the radio sat OUT OF BAND and
  `isTransmitInhibited` rightly refused to key. Now 345 steps (14.345, a step no other test uses). Checked the other
  20m transmit paths (voice `transmitVoice`, CW `canTransmitCW`, data `transmitData`, `mayTransmitOn` ->
  `BandEdges.canTransmit`): all key in band, all refuse out of band without MARS. No player impact.
- `RadioAppsGameTests.aprsMessageIsAcked`: A queued a beacon right behind its message. B acks as the message ends, but
  the beacon (38 air bytes) is longer than the ack (30), so A was still sending when the ack arrived and a transceiver is
  deaf while it sends (`isDeafNow`, unchanged since 1.11). The ack was lost until the 20 s retry, i.e. tick 400, the
  timeout. The dedupe and the power gate were fine. The test now beacons after the ack. In game this is the same
  realistic half-duplex loss the 1.11 APRS terminal has, recovered by the retry (no carrier sense; a possible later
  feature).
- `AdsbMathTest.theFullRangeIsNeverBelowTheMinimum`: `range(100, 63, 40, 300, 9000)` expected 300, but Y 100 is above the
  full-range Y 40, so the rule gives the full range, 9000 (and the next line already expected 9000 at Y 41, so the test
  contradicted itself). Expectation fixed to 9000, and a Y 39 case (below the full-range Y, under sea level) expects
  300. `AdsbMath` unchanged.

Rerun once the client is closed: `runGameTestServer` (at least `HandMicGameTests`, `RadioAppsGameTests` batch
`radio_apps`), and `./gradlew test`.

### Recipe review (maintainer, 2026-11-05)

Approved and applied in `RadiocraftRecipesProvider` (needs `runData`):
- Basic Commercial Handheld: ` L / SCE / IBI` (lightning rod, speaker, encased VHF crystal, microphone element, iron, any button).
- Upgraded Commercial Handheld: ` G / BHB / RCR` (glass pane, buttons, basic handheld, redstone, comparator).
- Commercial Mobile / Low Band Mobile: `III / MCS / #R#` (iron, hand mic, encased VHF or 10m crystal, speaker, wire, redstone).
- Marine Handheld: `WLW / SCE / IBI` (waterproof wire, lightning rod, speaker, encased VHF crystal, microphone element, iron, any button).
- Marine Radio: `III / MCS / #R#` with # = waterproof wire.
- MARS Mod: `WTW / _C_` (wire, redstone torch, any encased crystal of any band); the HF circuit board was dropped.
- Band Plan Sheet (paper + ink sac), Camera (` B / IGI / IRI`, B now any button), Photo Paper and Color Photo Paper: approved as built.
- Radio Computer: `III / PCP / ITI` (iron, repeater, comparator, iron trapdoor). Computer Monitor: buttons now any button. Scanner: as built.
- Blank floppy and the program floppies: approved as built.
- ADS-B Beacon: ` L / ICI / IRI` (C = encased VHF crystal, replacing the HF board). ADS-B Receiver: `IGI / RCR / III` (C = comparator, replacing the HF board). ADS-B Antenna: as built.
- FCC hat, jacket and banner pattern: as built. Real-World Interface: `ICI / #R# / IBI` (iron, comparator, wire, radio interface, HF board; speaker and hand mic dropped). AWM siren controller and data wire: as built.
- Radio Computer made late game: `GXG / QDQ / ITI` (gold ingot, any encased crystal, nether quartz block, diamond, iron, iron trapdoor), replacing `III / PCP / ITI`.
- Radio Cloner: ` W / IDI / ICI` (wire, iron, diamond, comparator): approved as built.
- **Recipe review complete (2026-11-05).**

### Playtest fixes 2 (2026-11-05)
Five maintainer requests, decisions already made. Built: `compileJava compileTestJava compileGametestJava` clean (the
same 32 old warnings, none new), `runData` (lang, the cloner's recipe and advancement, the MARS advancement), `test`
(578, all pass), `runGameTestServer` (results at the end). Nothing committed.

- **MARS mod out of the recipe lists**: its recipe's only unlock criterion is now `minecraft:impossible` (the generated
  advancement keeps `has_the_recipe`, so only `/recipe give` unlocks it); `RadiocraftJeiPlugin.onRuntimeAvailable`
  hides `radiocraft:mars_mod` and `radiocraft:mars_mod_install` from JEI's crafting category (`HIDDEN_RECIPES`, looked
  up in the client level's recipe manager). The item stays in the creative tab and JEI's item list; the Handbook's
  crafting page still shows the recipe (its text says so).
- **Radio Cloner** (`radiocraft:radio_cloner`, `items/RadioClonerItem`, component `radiocraft:clone_image` =
  `datacomponents/CloneImage(family, source, model, program, memory, dial, hasMemories, keysLeft)`, network codec via
  `ByteBufCodecs.fromCodec`). Empty: use reads; loaded: use writes, any number of radios; sneak-use in the air clears;
  sneak-use on a radio block reads that radio. Blocks are caught in `onItemUseFirst` (before the radio's screen
  opens); in the air it works on the radio in the other hand. Families: **commercial** (handhelds, mobile items,
  placed mobiles): `CommercialProgramming.readForClone` (keys marked not readable taken off, so the channel goes clear
  digital; readable keys copied; `hiddenKeys` counts them), written through `CommercialProgramming.write` (all or
  nothing; channels past a smaller model's capacity are cut first and the message says how many), contacts copied,
  **unit ID never copied** (the target keeps or gets its own). **ham_vhf**: a VHF ham handheld's dial
  (`HandheldRadioState` freq, tones, offset) and its `HandheldMemory` (left memory mode); a VHF base station's dial
  only. The request said "VHF base stations (their memory channels)", but `VHFBaseStationBlockEntity` has no memory
  channels, so its dial is what is cloned (targets get it via `setFrequency` + `updateFrequency(0)`, `setCtcssTone`,
  `setOffsetHz`). **Marine**: decided not cloned (a fixed channel table; the only state is channel, HI/LO and dual
  watch); it says so. Feedback on the action bar plus `NOTE_BLOCK_BIT` (done) / `NOTE_BLOCK_BASS` (refused) /
  `UI_BUTTON_CLICK` (cleared); it glints while loaded. Placeholder art from `tools/radio_cloner_assets.py`, on
  ModelDesignBounty. Handbook `radios/radio_cloner`.
  **Recipe proposal (for review)**: shaped ` W / IDI / ICI`: W = wire (the cloning cable), I = iron ingot, D = diamond
  (its memory), C = comparator (its logic). Unlocked by a basic commercial handheld or a VHF handheld.
- **CPS "Program radios" mode** (replaces the scanner glass): `ScannerBlockEntity.isScannable` is back to maps and
  books (`bedChanged` removed); `CommercialTargets` lists only wired mobiles (no `GLASS`). Server:
  `common/computer/apps/HandProgramming` (`request` = PROGRAMMER action `hand` {on, plug}; `onRightClickBlock`, a
  `PlayerInteractEvent.RightClickBlock` listener registered in `Radiocraft`; `use(computer, player, stack, read)`;
  `ended(player, reason)`). State per player on `RadioComputerBlockEntity` (`HandSession(plug, lastUse)`,
  `startHand/stopHand/handSession/hasHandSessions`, `HAND_TIMEOUT_TICKS` 6000): counts as **in use** (draws power),
  pruned at each viewer check (timeout, offline, other dimension), and all ended when the computer is unpowered (each
  player told by the CPS event `hand_end` {reason} and an action bar line). The click: the radio in the main hand, or
  in the off hand with the main hand empty, on the computer or its monitor (`ComputerMonitorBlock.computerUnder` is now
  public). Sneak reads (pushes `hand_read`, the `read` layout; CPS sets the codeplug's unit ID to 0 so a read radio's ID
  is not copied); a plain click writes (`CommercialProgramming.write(target, plug.channels, plug.unitId,
  plug.contacts)`; pushes `hand_written` or `hand_refused` with problems). Hidden keys behave as on the cable. With a
  unit ID in the codeplug it is written and then **advanced by one** (fleet numbering; `next` in the event). Client:
  `HandProgramming.clientComputer` (set by CPS while on, cleared when it ends and in `ComputerSession.release`) makes
  the client cancel its own click too (no screen, no mobile placed). CPS: F9 toggles; a red banner and `[ Close ]`
  (C or a click; closes the screen as MUSIC's Close does); the codeplug is resent on every edit while on; `exit()` and
  the new `DosApp.covered()` hook (called by `ComputerSession.launch`/`showMusic` on the program pushed behind) end
  it. F6/F8 on a radio read by hand point at the mode. Lang under `screen.radiocraft.computer.cps.*` and
  `message.radiocraft.computer.hand.*`; `radio.on_glass` became `radio.in_hand`. FREQMAN now reaches wired mobiles
  only (the Handbook says to use CPS for handhelds).
- **Unit IDs**: `CommercialProgram(channels, selected, unitId, contacts)` (the two-argument constructor kept; codec
  fields `unit`, `contacts`; `UNIT_MAX` 9999, `MAX_CONTACTS` 32; `withUnitId`, `withContacts`, `alias`, `callerText`
  ("ENG 5" or "ID 1205")); `CommercialContact(id, alias)` (8 characters). `CommercialProgramming.write(target,
  channels, unitId, contacts)` (0 keeps, null keeps) **gives a radio left without an ID a random one** (`assignUnitId`;
  so every write, in tests too, now gives an ID); `setUnitId`; `/rcdebug commercial ... unit <id>`, and the show line
  has `unit=` and `lastCaller=`. `Codeplug(model, channels, unitId, contacts)` (+ `forModel`, `putIdentity`,
  `contacts(tag)`; an old .CPG loads with none). CPS F4 unit ID, F7 contact list (DosPickDialog + DosFormDialog).
  On the air: `DigitalVoice.Mode(digital, keyId, unitId)` (two-argument constructor kept, `withUnit`),
  `DigitalVoice.unitHeard(mode, reception)` (read on ANALOG/CLEAR/WARBLE/SCRAMBLED, not BUZZ/DROPPED). Transmit:
  `CommercialHandheldItem.transmitMode` and `CommercialMobileBlockEntity.transmitMode` add the unit; on **analog**
  channels `voice/MdcBurst.Sender` puts a ~147 ms 1200-baud FSK burst (1200/1800 Hz: preamble, MDC sync word,
  scrambled ID bytes) over the first frames of each key-up (a gap over 300 ms), in `PlayerRadio.acceptVoicePacket` and
  `CommercialMobileBlockEntity.acceptVoicePacket`. Synthesized, so no sound bounty; it replaces the voice under it, as
  real radios mute the mic for the ID. Receive: the mobile's `receiveVoice` -> `callerHeard` (volatile; resynced to
  clients every 1.5 s while the unit talks; `getLastCaller()`; tag `caller` in `setupSaveTag`); `displayLine()` shows
  `callerText()` for 4 s (client, `CALLER_SHOW_MS`) on the in-world display and its screen. `PlayerRadio.receive` ->
  `callerHeard` sends `CCallerIdPacket(rxHz, unit)` (server thread, at most every 1.5 s per unit) to the holder; the
  client's `CallerIds` keys it by receive frequency and the upgraded handheld's screen shows it on the name line. The
  brick only sends. Moderation: `ModerationLog.voiceMode` logs "FM ID 1205" or "DIG ENC ID 2001" (from
  `DigitalVoice.current()` when a voice transmission opens). Handheld tooltip "Unit ID n".
- **ADS-B display**: the receiver is now a full block (shape and model) whose whole north face is the screen:
  `tools/adsb_models.py` rewritten for the receiver (a square dark face with a ring when off, a square lit face when
  on, knobs on the side; the screen element at z -0.02 to 0 over x/y 0-16); `AdsbScopeRenderer.SCREEN_*` = 0..16, z
  -0.02; `AdsbScope.paint(..., square)` (no outside, five rings clipped at the edges, targets anywhere in the square,
  so the corners reach past the range); `AdsbScopes` paints it square. The computer program and the GUI screen stay
  round.

Tests: unit `commercial/UnitIdTest` (codec, old JSON, contacts cleaning, alias, identity kept by edits, the random
ID, a refused write gives none, a clone leaves hidden keys), `voice/MdcBurstTest` (length, tones, key-up bursts, no
unit no burst, `unitHeard`), `moderation/VoiceModeTest`, `CodeplugTest.theUnitIdAndContactsAreSaved`,
`AdsbScopeTest.theSquareDisplayFillsTheFaceEdgeToEdge`. GameTests: `OfficeAppsGameTests.programmerProgramsRadiosHeldInTheHand`
(replaces `programmerKeepsHiddenKeysOnTheGlass`: read and write by hand, the hidden key kept and refused elsewhere,
the unit ID advancing, a mobile item, the click on the monitor through the event, the mode off) and
`handModeEndsWhenThePlayerIsGone`; `RadioClonerGameTests` (4: keys left behind and copies, model limits and the cut,
a placed mobile and the sneak read, ham dial/memories/base station, families and marine, the other hand and clearing);
`UnitIdGameTests` (batch `commercial_radios`, 155.1625/155.1875/155.2125 MHz: the analog ID read with its alias, an
encrypted call without the key still shows the ID, programming gives an ID).

**Results**: `test` 578/578. `runGameTestServer` first run: 5 required failures: my own
`programmerProgramsRadiosHeldInTheHand` (the test expected a fresh handheld's channel 6 blank, but the upgraded
handheld's factory program has 2M CALL there; assertion fixed) and four `IonosphericProcessorGameTests` in batch
`ionospheric` (`wefaxAsksOverTheAir`, `teletypeAsksOverTheAir`, `bulletinPrintsAsItComesIn`,
`lostSignalPrintsSpeckle`; pre-1.12 tests on 2m base stations, nothing here touches them). Second run: **all 487
required tests passed**, those four included, so they are timing-flaky; worth watching.

Docs: Handbook (`computer/radio_programmer` rewritten, `radios/commercial_radios` "Who is talking", new
`radios/radio_cloner`, `radios/mars_mod`, `computer/frequency_manager`, `aviation/adsb_receiver`), release notes,
NeedsTesting "1.12 playtest fixes 2" (the older glass items reworded), ModelDesignBounty (cloner; the ADS-B receiver
entry rewritten for the square face).

**Left / gotchas**: a handheld in a desk charger shows no caller ID and sends no burst; FREQMAN cannot reach a handheld
any more. A hand-mode click on the client is cancelled from `clientComputer` alone, so if the server ended the mode
and the CPS missed the event, the first click just opens the screen (self-correcting). Handheld caller IDs are keyed
by receive frequency, not by which handheld heard them.

### Open-question fixes (2026-11-05)
The nine changes in "Answers (maintainer, 2026-11-05, round 2)". Built: `compileJava compileTestJava compileGametestJava`
clean (no new warning), `runData` (lang only), `test`, `runGameTestServer` and `build` (results at the end). Committed
locally in six commits (below); nothing pushed. Website: one commit in `Radiocraft-Website`.

1. **Commercial radios ship blank**: `CommercialRadioModel.factoryProgram()` is now `CommercialProgram.EMPTY` (the MURS,
   2M CALL and LB 1-4 channels are gone). Everything that used it (new items, placed mobiles, `factoryReset`, CPS F8,
   `/rcdebug commercial ... factory`) now gives a blank radio; CPS F8's words say "erase ... back to factory blank".
   Tests that read "MURS 1" off a new radio now expect a blank channel; `commercialHandheldIsChannelized` programs two
   channels first.
2. **7.060 "Server-wide net" removed** from `BandPlan.DEFAULT_MARKERS`. `BandPlan.parse` also skips that exact line
   (`PLACEHOLDER_MARKER`), so server configs written by the 1.12 dev builds drop it without editing; any other marker an
   admin adds at 7.060 still shows. APRS and the rest stay.
3. **Computer password**: `common/computer/ComputerLock` (pure: salted SHA-256, the computer id is the salt; at most 16
   printable ASCII characters; unit test `ComputerLockTest`). Owner and hash live in the computer's disk,
   `ComputerFileStore` (`owner`, `password` tags), so they follow the item like the files. `RadioComputerBlock.setPlacedBy`
   -> `RadioComputerBlockEntity.claim` (only if the disk has no owner yet). `isLockedFor(player)`: password set, not
   unlocked since opening, and not an op (permission 2). `ComputerServer.handle` refuses every request but `sys.unlock`
   with `locked` while locked; `sys.unlock` {password} (`wrong_password`), `sys.password` {password, "" clears} (owner,
   op, or anyone while the disk has no owner: computers placed before this, and GameTest ones, are claimed by the first
   to set a password; `not_owner`, `bad_password`); `open` answers `password` and `owner` too. `ComputerServer.closed`
   relocks. Client: `RadioComputerMenu` has a second data slot, `isLocked()` (per player, server side); while it is set
   `RadioComputerScreen` draws a password box after the boot (masked `DosInput`, new `masked()`), Enter sends
   `sys.unlock`, Escape closes, and the session does not tick; on unlock it refreshes. Shell **F8** sets/clears it
   (`shell.keys_programs` shows F8). **Decision**: the owner is asked too (it is a password); ops bypass, and an op can
   clear a forgotten one with F8. GameTest `RadioComputerGameTests.passwordLocksTheComputer`.
4. **Solar/Forecast**: already tried every radio (Playtest fixes 1) and took a processor on the computer's data wire
   or touching it. Added: `missing()` says `solar_processor_offline` when a processor is linked but has no Ionosonde
   (new lang `err.solar_processor_offline`); Javadoc spells out the rules. GameTests `solarReadsAProcessorWithNoRadio`
   (processor touching the computer, no radio: offline, then `source` processor) and `solarTriesEveryRadio` (a radio
   with no antenna touching the computer does not hide the J-pole station behind the radio interface).
5. **SSTV/WEFAX to everyone**: `PictureHandler.send(context, name)` always sends with an empty `to`, and every station
   that hears a picture header copies it (`forUs` removed, so an addressed picture from an old build is received by
   all too). `PictureApp.send` is a Yes/No confirm ("Everyone in range receives it"). APRS and FAX keep addressing.
6. **Ham shacks found dark**: `tools/ham_shack_templates.py` writes the radio with `wasPowered` 0 and the large battery
   with `energy` 0; all six templates regenerated (site and five villages). `SiteProcessor` only scales a charge above 0,
   so they stay flat. `SiteGameTests.hamShackIsFoundDark` (was `hamShackWorks`: wired up, every radio off, every battery
   empty) and the turned village test now check `powered=false`; `HamShackTemplatesTest` checks the NBT.
7. **FCC badge holders marked**: `common/fcc/FccBadgeHolders` (SavedData `radiocraft_fcc_badges`): `issued` is recorded
   by `FccGear.issueBadge` (the command); `holds(uuid)` = issued, or online and carrying/wearing (Curios,
   `CuriosCompat.wornFccBadges`) a badge issued to them. `ComputerRequests` puts `fcc` on talker, hearer and admin
   entries and appends `ModerationFormat.FCC_MARK` (" [FCC]") to the archive row's talker names; Live, Archive (detail
   talkers and hearers, heard-by line) and Access show `[FCC]` after the name (`ModerationFormat.fccName`). No powers.
   GameTest `FccGameTests.badgeHoldersAreKnown`. Gotcha: an issued badge is never "un-issued" (no revoke command); a
   player who throws theirs away stays marked. Say if a revoke (`/radiocraft fcc revoke`) is wanted.
8. **Real-World Interface**:
   - Power switch: new `RadioBlockEntity.mayTogglePower(player)` (true), overridden by the interface as `mayEdit`;
     `SBlockRadioControlPacket` refuses TOGGLE_POWER with the `op_only` message. The screen greys Power for non-ops.
     Config comment updated.
   - Licence click: `common/realworld/RealWorldLicences` (SavedData `radiocraft_real_world_licences`, a concurrent set;
     the loaded instance is cached in a static for the voice thread, loaded in the block's `onLoad`). New
     `SRealWorldInterfacePacket.Action.LICENCE`; status tag `licensed`; a full-width button under the Power row (the
     status panel moved down 20 px, `imageHeight` 246). **Who is talking**: the sound card gets the mixed receiver
     audio, so the check is made per frame *before* the mix: `RealWorldInterfaceBlockEntity.receiveVoice` drops an
     `AntennaVoicePacket` whose `getSourcePlayer()` is a player who has not clicked (`RealWorldLicences.mayBridge`).
     Every voice frame carries its speaker, and repeaters, parrots and the Everywhere path pass the original speaker on,
     so relaying does not launder it. "A player" = online, or known to the server's profile cache; block voice ids
     (tapes, the world's stations) are not players and pass. GameTests swap that check
     (`setPlayerCheckForTesting`), since a FakePlayer is in neither list.
   - Monitor speaker: `common/realworld/MonitorSpeaker`, a Simple Voice Chat locational channel at the block, 6 blocks,
     gain 0.35, fed from `downlinkFrame` (only frames that actually go to the bridge while it is live); closed on
     shutdown. No voice chat server: it only counts frames (`frames()`, for tests).
   - GameTests `onlyLicensedPlayersGoOut` (new interface `GT Licence`, 147.540: nothing keys or reaches the card or the
     monitor before the click; after it, audio reaches both) and `powerSwitchIsOpOnly`.
9. **Website**: `tools/build_wiki.py` `sidebar()` lists `DEVELOPER_PAGES` ("modding") under a new **FOR DEVELOPERS**
   heading (between HANDBOOK and REFERENCE) instead of BEFORE YOU PLAY; the wiki was rebuilt from this Handbook.

Docs: Handbook (commercial radios, radio programmer, band plan, radio computer "Password" page, SSTV, ham shacks, FCC
gear, Real-World Interface "Your Licence" page), release notes, NeedsTesting "1.12 open-question fixes".

**Left undone / for the playtest**: everything in NeedsTesting "1.12 open-question fixes" (the password box, the RWI
screen layout and button, `[FCC]` on the moderation screens, the monitor speaker by ear, found shacks in a new world).
The monitor speaker and the licence gate are untested against real hardware.

**Results**: `test` 582, all pass. `runGameTestServer`: first run 4 failures, all in the known timing-flaky
`IonosphericProcessorGameTests` batch (teletype/WEFAX asks over the air, bulletin, speckle); the rerun passed all 493
(the new and changed tests included). `build` successful. The first full run before the processor/shack/RWI tests also
failed only `cosKeysInAndTimeoutDropsPtt` (wall-clock, known).

**Commits** (RadioCraft, local only): blank commercial radios + 7.060 marker (with this plan's answers); computer
password; solar processor / pictures to everyone / dark shacks; FCC marks; Real-World Interface; lang, generated
lang, release notes, NeedsTesting and this handoff. Shared files (the language provider, `src/generated`, release notes,
NeedsTesting) are all in the last commit, and `OfficeAppsGameTests` (MURS fix and solar tests) is in the third.

### Computer apps 2 (2026-11-06)
Five new floppy programs for the radio computer, each its own `ComputerApps` entry (CRAFTED), floppy, recipe, screen,
server handler, lang class, Handbook entry, unit tests and GameTests. Built: `compileJava compileTestJava
compileGametestJava` clean (the 32 old warnings only), `runData` (lang + five recipes/advancements), `test` **641/641**,
`runGameTestServer` **all 504 required passed on the first run**, `build` successful. Committed locally per program;
nothing pushed. Lang for each program lives in its own class under `datagen/lang/` (`RttyLang`, `BbsLang`,
`PropagationLang`, `RepeaterLang`, `LicenseStudyLang`), called from `RadiocraftLanguageProvider`. Floppy art is the
shared tinted label (no bounty). Intermediate commits do not compile on their own: the registration (shared files and
`src/generated`) is the last commit.

- **RTTY** (`rtty`, RTTY.EXE): `RttyHandler` (ticks; one `TncPacket.rtty` character per `TeletypeBlockEntity.
  SECONDS_PER_CHAR` through `RadioStation.transmit`, type-ahead up to 4000 characters, half duplex 20 ticks after our last
  character, moderation lines like the Teletype; state: receive window, macros, DX call), pure `common/computer/RttyCopy`
  (a line per sender, 300 lines, macros `{MYCALL}`/`{CALL}`/`\n`), `RttyApp`. Reuses the mod's RTTY end to end, so it
  copies and is copied by the Teletype and the H19. New pure `radio/voice/Baudot` (real ITA2 US-TTY table, LTRS/FIGS,
  USOS, `encode/decode/misprint`); `Rtty.frames` now sends the real codes and `Rtty.garble` misprints by a lost bit (a
  letter one bit away, or the other shift) instead of a random character, for every RTTY receiver (length and newlines
  kept, so the old tests hold). Removed from todo.md. Tests `BaudotTest`, `RttyCopyTest`, `RttyAppGameTests` (145.150,
  145.170, 145.190 MHz).
- **Packet BBS** (`bbs`, BBS.EXE): pure `PacketBbs` (mailbox up to 200 messages of 1000 characters, sessions expiring
  after 10 min, commands `? H L LM LB R n SP SB K n B`, subject prompt, body to `/EX`, `/AB`), `BbsHandler` (ticks;
  answers APRS messages to the bus callsign's base + `-SSID` (default 1), acks numbered ones and dedupes 16 numbers per
  caller, replies unnumbered; needs `AprsHandler.cannotSend`), `BbsApp` (SYSOP page and TERMINAL page). Mailbox on the
  disk as `BBS.DAT`; SSID, online switch, traffic log and terminal in the program state. Sysop =
  `computer.mayChangePassword(player)` (owner, op, or anyone while unowned) for delete and setup. Shared fix:
  `RadioInterfaceBlockEntity.shouldDigipeat` skips messages/acks to its callsign under any SSID (the host's interface
  repeated its own BBS traffic). Tests `PacketBbsTest`, `PacketBbsGameTests` (batch `packet_bbs`, 145.070 MHz).
- **Propagation Predictor** (`propagation`, PREDICT.EXE): pure `PropagationForecast` (strength =
  `BandUtils.getBaseStrength` with a dipole at each end and the condition's skip multiplier; threshold =
  `RadioBlockEntity.copyThreshold` against skip noise or `LOS_SOLAR_NOISE_SHARE` of it, plus aurora static; grades G >= 3,
  F >= 1.5, P >= 1, CW at distance / 1.5 as "c"; path L/S/Z/B; vanilla `isDay` per hour), `PropagationHandler`
  (`predict` by coordinates, callsign (online player, then APRS stations heard) or distance + bearing; `targets`; same
  receiver rules as SOLAR via `SolarWeatherHandler.receiver/missing`; 24 in-game hours: current event, then the
  forecast's next event, then dimmed "uncertain" hours), `PropagationApp`. Terrain and the real antennas left out (said on
  screen). Tests `PropagationForecastTest`, `PropagationGameTests` (batches `propagation_direct`,
  `propagation_needs_radio`).
- **Repeater Directory** (`repeaters`, REPEATER.EXE): `radio/RepeaterRegistry` (server-wide map voiceId -> repeater info,
  updated by `VHFRepeaterBlockEntity` on load, settings and every 100 ticks; removed when broken, not on chunk unload;
  cleared on server stop). `RadioBlockEntity.receiveVoice` records a repeater's own voice id (hang carrier, courtesy beep,
  ID) in a second `StationsHeard`, `getRepeatersHeard()`. Pure `RepeaterDirectory` (merge by position, manual notes kept,
  300 entries, sort, export to `FrequencyList` with dedupe), on the disk as `REPEATER.DAT`. `RepeaterDirectoryHandler`
  (no ticking: collects when the screen asks) and `RepeaterDirectoryApp` (F7 exports to a .FRQ and offers FREQMAN).
  Tests `RepeaterDirectoryTest`, `RepeaterDirectoryGameTests` (batch `repeater_directory`, 147.240/147.840 MHz).
- **License Study** (`license`, STUDY.EXE): `common/computer/license/` `LicensePool` (Gson; answer as index or letter;
  subelement = 2 chars of the id, group = 3), `LicensePools` (reload listener: data packs `data/<ns>/license_pools/*.json`,
  then `config/radiocraft/license_pools/*.json` replacing by id; `/reload`), `StudyDeck` (levels with intervals 1, 3, 8,
  20, 50, 120 cards; wrong back in 3, "I don't know" in 2), `LicenseExam` (one non-figure question per group, pass mark
  rounded up from `pass_percent` 74, aptitude by Poisson-binomial over per-question chances), `LicenseProgress`
  (**SavedData `radiocraft_license_study`, keyed player -> pool -> question**: follows the player to any computer).
  Pools in `data/radiocraft/license_pools/` (technician 409, general 423, extra 599 questions; 35/35/50 groups) made by
  `tools/license_pools.py` from russolsen/ham_radio_question_pool (NCVEC public domain text, Apache-2.0 packaging; see
  `doc/Credits-QuestionPools.md`). The 45 figure questions keep `figure` but are skipped (no figure drawn). Tests
  `LicensePoolTest` (also checks the shipped files), `StudyDeckTest`, `LicenseExamTest`, `LicenseStudyGameTests`.

**Proposed floppy recipes (for the maintainer's review)**, shapeless blank floppy + : RTTY **note block** (mark and space
tones); Packet BBS **chest** (the mailbox); Propagation Predictor **clock** (the hours ahead); Repeater Directory
**redstone repeater**; License Study **lectern** (study). All five craftable, none loot.

**Gotchas**: BBS replies go out back to back and unnumbered (a lost one is not resent; the caller asks again); a caller's
APRS program logs BBS replies too. A repeater is recorded only by a radio whose tone squelch opens for its output; radios
keep the last 8 repeaters heard, collected only while the directory screen asks. PREDICT counts POOR (data/CW only) as
open for "best band now". STUDY keeps the pools' answer order (no shuffle on exams). The digipeat change means an
interface no longer relays messages for its own call's other SSIDs (W1BTR-9 near a W1BTR station).

**Open questions for the maintainer (computer apps 2)**:
- Recipes above.
- BBS: should posting from the sysop console be owner-only too (now anyone at the computer may post; delete and setup
  are owner-only)?
- PREDICT: are the grade thresholds right, should POOR count as open, and should the station's real antenna replace the
  assumed dipoles?
- REPEATER: collect in the background (a ticking handler, so the computer stays in use and draws power) instead of only
  while the screen is open?
- STUDY: draw the 45 figure questions once the figures can be shown (the PNGs are in the source repo); shuffle answers on
  practice exams?
