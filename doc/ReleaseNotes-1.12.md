# RadioCraft alpha 1.12

Minecraft 1.21.1, NeoForge. Needs Simple Voice Chat and GeckoLib;
[Another Weather Mod](https://github.com/RadiocraftARRL/AnotherWeatherMod) is optional.
Everything below is explained in the in-game ARRL Radiocraft Handbook and on the [wiki](https://radiocraft.bigtimeradio.us/wiki/).

## 1.12.0

### New

#### Bands, band edges and the MARS mod

- **Two commercial bands**: **low band** (30-50 MHz, just above 10m) and **commercial VHF** (150-174 MHz, with the
  marine channels). Low band sits between 10m and 2m: by line of sight it reaches further than VHF but not quite as far
  as 10m, and it skips by day only when 10m does, never at night. Commercial VHF travels like 2m. Each has its own
  section in the server config.
- **Radios tune past their band**, like a real general coverage receiver: HF a quarter of the band's width each side
  (20m tunes 13.9125-14.4375 MHz), VHF base stations and handhelds from 143 up to 174 MHz. They receive there, but key
  up out of band and the display reads **OUT OF BAND** and nothing goes out (voice, CW or data).
- **MARS Mod** (contraband!): craft it (two wires, a redstone torch and an encased crystal of any band), then craft it together
  with an HF radio, the all-band, a QRP rig, the VHF base station, a VHF handheld or the manpack, and that radio
  transmits anywhere it tunes. Its tooltip says it is fitted. Out-of-band signals **splatter** onto the frequencies
  next to them twice as far as usual (`mars_mod_splatter`). Being contraband, its recipe never shows up in the recipe
  book or in JEI (the item does); only the Handbook's MARS Mod page shows how to make it.
- **Band Plan Sheet** (paper and an ink sac): right click to see every band as a coloured bar (CW, digital, SSTV/fax,
  phone, APRS, simplex, repeaters; business, public safety, military, MURS, marine), with markers such as 144.390 APRS
  and a legend. Server admins can rewrite the whole plan in the server config (`band_plan_segments`,
  `band_plan_markers`).

#### Commercial radios

- Channel-only radios with a **16-position knob** (16 channels; the upgraded handheld 64), set up by a radio shop: each channel has its receive and transmit
  frequency, analog FM or digital, a CTCSS tone and an encryption key. They need no ham callsign and always transmit
  inside their own band. Program them with the radio computer's **Radio Programmer** (below). They come **blank**, with no
  channels: nothing works until they are programmed.
  - **Basic Commercial Handheld**: a 1990s brick for 150-174 MHz, analog FM, RX and TX lights, no display.
  - **Upgraded Commercial Handheld**: the fire-service radio, crafted from the brick: **64 channels**, digital voice
    and encryption, and the 2m ham band too. Like 1990s fire radios it keeps the 16-position knob and adds **four
    zones** picked with its **A**, **B**, **C** and **D** keys (A = channels 1-16, B = 17-32, C = 33-48, D = 49-64);
    its display shows the channel name, zone and channel.
  - **Commercial Mobile Radio** (150-174 MHz, analog and digital) and **Commercial Low Band Radio** (30-50 MHz, analog):
    desk, vehicle and ship radios with a hand mic, powered and wired to an antenna like the base stations.
- **Digital voice** has no static: clean until the signal would be 70% static on FM, a short warble, then nothing.
  An **encrypted** channel sounds like garbled digital noise without the key, and any digital signal is a harsh buzz
  on an analog or ham radio. Repeaters pass digital on as it is. The moderation computer still records the clear voice.
- The cases **take dye** like leather armour. A mobile keeps its colour and channels when broken and placed again.
- The handhelds are **3D** in the hand, on the ground, in frames and in the desk charger: the basic brick is the
  maintainer's own model, the upgraded radio a tall 1990s professional handheld with a keypad and LCD. Only the case
  takes the dye. Their screens show the radio's front; turn the two knobs on top of it with the mouse wheel, by
  dragging them up or down, or by clicking (right click turns back): the **channel knob** (1-16, stops at both ends)
  and the **power/volume knob** (turn it to OFF to switch the radio off). A small readout over the knob shows where it
  is while it turns. On the upgraded radio the keypad's **A** to **D** keys pick the zone, **\*** shows its unit ID and
  **#** the channel's frequency.
- **Unit IDs**: every commercial radio has a unit ID (1-9999, set in CPS, or given one the first time it is programmed)
  and sends it at every key-up, as fire and business radios do with MDC-1200 and P25: on FM a short data burst
  ("brrrp") at the start of the transmission, on digital inside the data. The upgraded handheld and the mobiles show
  the caller's ID, or the alias their **contact list** gives it ("ENG 5"), while the unit talks and for a few seconds
  after; encrypted calls show the ID even without the key. The basic brick only sends. The moderation computer logs
  the unit ID with each transmission (mode "FM ID 1205").
- **Radio Cloner** (a wire, iron, a diamond and a comparator): use it on a radio to read its programming, then on as
  many radios as you like to write it; sneak-use in the air clears it. Works on placed radios and on a handheld in
  your other hand. Commercial radios clone channels and contact lists to commercial radios (each model's limits
  apply; a smaller radio takes what fits), leaving behind keys marked not readable (those channels arrive clear), and
  never copy unit IDs. 2m ham handhelds and base stations clone their dial and (handhelds) memory channels. Marine
  radios are not cloned.

#### Marine radios

- Channel-only VHF marine radios on the real marine channel table (the US plan by default, with the "A" simplex
  channels, the duplex marine operator channels and the NOAA weather channels; `marine_channel_plan` switches to the
  international plan). Channel 16 is distress and calling, 9 hailing. No callsign needed.
  - **Marine Handheld**: 5 W or 1 W. Charges in a desk charger like the other handhelds.
  - **Marine Radio**: a fixed-mount 25 W / 1 W radio with a hand mic for a boat's console or a harbour office. It
    works on Sable ships.
- The **16/9 key** jumps to 16, then 9, then back to your channel. **Dual watch** (DW) listens to 16 as well and gives
  it priority while it is busy. Duplex channels hear the coast station 4.6 MHz up, the weather channels only receive,
  channel 70 (DSC) carries no voice, and channels 13, 15, 17 and 67 are always 1 W.

#### Camera and photos

- **Camera**: hold right click to raise it: a viewfinder shows the square you will get, and the scroll wheel zooms up
  to 3x. Let go to take the photo (without your hand or the HUD). It prints on **Photo Paper** (black and white) or
  **Color Photo Paper** from your other hand or inventory, as a locked map: frame it, copy it, or send it by radio
  fax. Eight paper around an empty map make sixteen sheets; red, green and blue dye in the bottom row make color
  paper.
- The **radio fax** has a fourth supply slot, **PHOTO**: with photo paper in it, received photos print on that (a color
  photo on black and white paper prints grey), otherwise on an empty map as before. Its display says PHOTO for photos.
- Server settings: the time between photos (`camera_cooldown_seconds`, 3 s), cameras on or off (`camera_enabled`), and
  how many photos each player may keep (`camera_photo_cap`, no limit by default). A player at the limit is offered
  their old photos to delete with `/radiocraft photos`; nothing is ever deleted without asking, and a deleted photo
  goes blank everywhere.

#### The radio computer

- A 1990s PC for the shack. Put a **Computer Monitor** on a **Radio Computer** and right click it: it boots RC-DOS, a
  colour text screen, into the **RC-DOS Shell** (programs on the left, files on the right). It runs off the power wire,
  8 FE/t while in use (someone at it, or a background program such as APRS or fax installed), about an HF radio
  listening (`computer_power_use`); with no power the screen goes dark and its programs stop.
  - Built in: **EDIT** (a text editor; Ctrl+S saves), **SYSINFO**, **BANDPLAN**, **INSTALL**, **SETUP**, **STUDY**
    (License Study) and **ABOUT** (About RadioCraft: the mod's story, version, credits and website).
  - **Three launchers**, chosen in SETUP: the **RC-DOS Shell** list, the **Program Manager** (a window of 16-colour
    program icons; arrow keys or the mouse, Enter or a double-click runs, Tab shows the files) and the bare **RC-DOS
    prompt** (`C:\>`: DIR with wildcards and /W, TYPE, COPY, DEL/ERASE, REN, PRINT, CLS, TIME, VER, HELP, EXIT; run a
    program by its name, `EDIT NOTES.TXT` opens a file; up/down history, Tab completion; SETTINGS opens SETUP).
  - **SETUP**: the launcher, **colour schemes** that recolour every program (Classic Blue, RC-DOS Teal, High
    Contrast, and the monochrome Green Phosphor, Amber, Paper White and Gas Plasma; the screen previews a scheme as you
    move through the list), the password, and a computer name and clock (world or real time, 12 or 24 hours) in the
    top bar. Only the owner or an operator may change SETUP (anyone while the computer has no owner); it goes with the
    computer when it is broken and placed again.
  - Other programs come on **Floppy Disks**: craft a blank disk (iron nugget, redstone, black dye, paper), then craft
    it with the part that stands for the program. Put it in the drive and install it; the disk is not used up.
  - Files (documents, pictures, logbooks, frequency lists, codeplugs) live on the computer, 1 MB by default
    (`computer_storage_kb`, `computer_max_files`), and go with it when it is broken and placed again. The monitor in
    the world shows your programs as they are (the clock and MUSIC keep moving while you are within 16 blocks), and
    programs carry on for ten minutes after you get up.
  - **Password**: whoever places the computer owns it and can set a password in SETUP (empty removes
    it). Everyone who sits at it is then asked for it, owner included; server operators are never asked. The password
    goes with the computer when it is broken and placed again.
  - **Data floppies**: a blank floppy in the drive is drive **A:**. Copy files onto it and it becomes a **Data
    Floppy** (1.44 MB, up to 64 files, with a label in its tooltip) that carries them to another computer. In the
    shell's files list and the File Manager, F3 shows A: or C:, F8 copies a file to the other drive, F4 labels the
    floppy; at the prompt, `A:`/`C:`, `DIR A:`, `COPY X A:`, `COPY A:*.LOG C:`, `TYPE A:X`, `DEL A:X`, `LABEL A: NAME`.
    Data only: programs never go on a floppy (they install only from their own write-protected disks).
  - **Scheduler** (SCHEDULE, a floppy): timed jobs with nobody at the screen while the computer has power: an
    automatic station ID (the callsign in Morse through the radio), a WEFAX chart broadcast, an APRS beacon every N
    minutes, a net reminder (in chat to players nearby and on the screen, or as an APRS message). Times are typed:
    `every 30`, `at 20:00` (the world's clock) or `at 20:00 real`. Jobs go out under the station's usual rules
    (radio interface, transmit switch, WEFAX or APRS installed); only the owner edits them; they are kept on the disk
    as SCHEDULE.DAT. A fifth job, **FT8 CQ**, calls CQ on FT8 for ten minutes and logs what it works.
  - **Rig Control** (RIGCTL, a floppy: blank floppy + lever): drives the radio on the computer's radio interface from
    the keyboard, as CAT software does: tune a step or ten, type a frequency (the all-band radio turns its band knob
    for you), change band, mode (SSB/CW, FM on the all-band), power and PTT. A **live waterfall** of the band scrolls
    under the display four times a second, brighter for stronger signals, with the band plan's spots and the dial
    marked; zoom in up to 64 times round the dial, click a signal or Ctrl+arrow to tune to it. **Scan** a range
    or the **memory channels** (the Frequency Manager's CHANNELS.FRQ: store the dial with F4, load another .FRQ with
    F5): the scan stops on a signal over the squelch and goes on three seconds after it drops. The waterfall shows what
    the antenna really hears, and costs nothing while nobody watches it.
  - **FT8** (a floppy: blank floppy + any pressure plate): the weak-signal digital mode. 15 second slots on game time,
    even and odd; short structured messages (CQ with a grid square, reply, signal report, R-report, RR73, 73) on the
    band's FT8 frequency (3.573, 7.074, 14.074, 28.074, 144.174 MHz, now on the band plan too), each station at its own
    audio offset. It decodes **12 dB below** where RTTY and voice copy; the decode list shows time, SNR, offset and
    message for every station heard in a slot, CQs green and calls to you red. Click a CQ to answer, or call CQ
    yourself: the program sequences the contact by itself, sends what was missed again, and logs the finished contact
    in LOGBOOK.LOG with both reports and the grid. Your grid square comes from where the computer stands. A radio tuned
    to FT8 plays the real eight-tone warble.
  - **Net Control** (NETLOG, a floppy: blank floppy + lectern): run a directed net as net control. Set up the net
    (name, frequency, mode, NCS), check stations in (call, name, location or grid, traffic and short-time flags, time
    in and out; a station already in is rechecked), step the **roll call** (short time first, then traffic, then the
    rest), print the **net report** on the scanner or save it as a text file, and on closing put every check-in in the
    logbook as a contact. F9 sets a Scheduler reminder for the next net.
  - **Awards** (AWARDS, a floppy: blank floppy + item frame): operating awards from the logbook, **every contact
    counting** (no confirmation needed): Worked All Biomes, Worked All Dimensions, Worked All Bands, Worked All Modes
    (phone, CW, RTTY, FT8, SSTV), a contact ladder, stations worked, grid squares, Long Haul (the longest contact),
    Night Owl (contacts at night) and a ladder per mode, each with levels and a page of what is worked and what is
    still missing, plus distance records per band and mode. Prints a **certificate** in the computer's DOS look on
    paper, an empty map or photo paper. Where a station was comes from its grid square only (the one it was heard
    from, FT8's, or a typed one), never an exact position.
  - **QSL Designer** (QSL, a floppy: blank floppy + ink sac) and **QSL cards**: design card templates (your callsign
    large, or blank for a card anyone writes their call on; colours, border, four lines, a picture from any .IMG file;
    the back's heading, layout and lines), print **runs of identical blank cards** on the scanner (one paper or photo
    paper each; they stack). Right click a blank card to fill it in like a book and quill (to, band, frequency, mode,
    report, notes; the date and time go in by themselves) and **sign** it, which locks it. Cards are held like a map,
    turn over with a right click, show the front in an item frame and say from, to, date and band in the tooltip.
    Exchange them by hand, by **radio fax** (the far machine prints a copy on paper, or a printout of the front with
    only maps), or through a **Packet BBS** (the card waits as a message for its station, who types D and the number;
    their computer keeps it to print). A **QSL Album** (book, leather, two paper) holds 48 cards and pages through
    them. No bureau and no confirmation matching, by design.
  - The **Logbook** has a grid square column; contacts filled in from a radio note the grid square, dimension and
    biome the station was heard from (coarse only, never coordinates), and ADIF exports carry GRIDSQUARE.
- **Scanner**: set it beside the computer. It scans maps, photos and books into files, and prints pictures (on color
  photo paper, an empty map or photo paper) and documents (as a written book, a sheet of paper a page).
- **Office programs**:
  - **Book Editor** (BOOKEDIT): books of any length, a page at a time, printed through the scanner as written books in
    volumes of up to 64 pages.
  - **Logbook** (LOGBOOK): contacts with the UTC date and time, call, frequency, mode, reports and notes. F6 fills a
    contact in from a station your radios heard lately; F7 writes the log out as ADIF for real logging programs.
  - **Frequency Manager** (FREQMAN): lists of nets, repeaters and channels with offsets, tones and their band plan use;
    sends marked channels into a commercial mobile wired to the computer, and reads a radio's channels in.
  - **Radio Programmer** (CPS): reads, edits and writes the channels of a commercial radio (16, or 64 on the upgraded
    handheld), its unit ID (F4) and contact list (F7). A mobile wired to the computer is programmed through the cable.
    A handheld (or a mobile still in item form) is read or written by hand: **F5** or **F6** asks you to press
    **Continue**, the screen closes, and right clicking the computer or its monitor with the radio in your hand reads
    or writes it; CPS opens again with the result (it waits 60 seconds). For a fleet, **F9 Program radios** keeps the
    mode on: each click writes a radio (sneak to read one) and the unit ID goes up by one from radio to radio, until
    CPS closes, another program opens, the power goes or five minutes pass unused. Keys set as not readable show as `****` and never come off the radio. Codeplugs save to
    files for the rest of a fleet.
  - **Solar Weather** (SOLAR): the solar weather, each HF band by day and night, the aurora, sporadic E and the next
    spell, and a forecast page (the days ahead, and the storms about with Another Weather Mod). The computer needs a
    radio that is on with an antenna (of any band, VHF too; wired to the computer or at a radio interface on its
    station, as the radio programs find theirs), or an Ionospheric Processor, to hear them. Without one it says what
    is missing: no radio, the radio switched off, or no antenna.
- **Radio programs**: these go on the air the way a shack computer does, through a **radio interface** cabled to the
  computer (touching it or on its data wire) and the radios beside that interface, under the interface's callsign.
  - **APRS**: the computer as an APRS station on a big screen: stations heard with distance and bearing, messages with
    acks and retries, a map, beacons by hand or on the timer. Needs a VHF radio.
  - **Radio Fax** (FAX): faxes pictures, text files and books to fax machines and other computers; what comes in is
    kept on the disk until you print it. A fax machine on the same station answers calls instead.
  - **SSTV**: colour pictures to everyone in range (no addressing, as on the air), about half a minute each, coming in line by line and torn by
    noise where the signal fades. Send them again, caption them, or print them.
  - **WEFAX**: weather charts in greys: the coast station's radar chart and forecast, the Ionospheric Processors'
    bulletins and alerts, and charts other stations send. Draws a chart of the local weather to send as your own.
  - **Direction Finding** (DFIND): the display of a direction finder cabled to the computer: a terrain map with each
    bearing and the fix, a turning 3D view of how good the fix is, saved past searches, and the set's controls.
  - **ADS-B**: a second display for an ADS-B receiver (below).
  - **RTTY**: live radioteletype at 45.45 baud through the radio interface, the same RTTY as the Teletype Machine
    and the H19. What you type goes into a type-ahead buffer and out at about six characters a second; the receive
    window prints a line per station. Weak signals misprint the way a real teleprinter does (a letter one bit away,
    figures for letters when a shift is lost). Macros for CQ, the exchange and the sign-off ({MYCALL}, {CALL}), send a
    text file, save what was received to the disk.
  - **Packet BBS**: run a packet mailbox on VHF. With a radio interface and a VHF radio, the computer answers your
    callsign with an SSID of its own (W1BTR-1). Stations call in with APRS messages from an H19, the APRS program, an
    APRS handheld or another computer, and list, read and send personal messages and bulletins with the classic BBS
    commands (L, R, SP, SB, K, B; /EX ends a message). The mailbox is kept on the computer's disk (BBS.DAT). The
    owner, as sysop, watches the traffic, posts, deletes and sets the SSID; a terminal page calls other mailboxes. A new mailbox starts off the air until
    the owner switches it on (F7).
  - **Propagation Predictor** (PREDICT): which HF bands should open to a place over the next 24 in-game hours, a
    band-by-hour grid like VOACAP. Aim it at coordinates, a callsign (an APRS station the computer heard with a
    position; online players are not looked up, so it cannot find a player) or a distance and bearing. It uses the same skip ranges, solar conditions and noise floor as your radios,
    hears the sky as the Solar Weather program does, and dims the hours past what the solar forecast knows. Line of
    sight and CW-only openings are marked; print it or save it as PREDICT.TXT.
  - **Repeater Directory** (REPEATER): the repeaters you know, with output, offset, tone, distance and bearing, when
    each was last heard, and your notes. Tune a radio wired to the computer to a repeater's output: its hang carrier,
    courtesy beep and ID tell the radio which repeater it is, and F5 adds it. Add repeaters by hand too. Mark some and
    F7 adds them to a Frequency Manager list, skipping ones already there. Kept on the disk as REPEATER.DAT.
  - **License Study** (STUDY, built into every computer, no floppy): flash cards for the amateur exams, like hamstud.org, with the current US NCVEC
    Technician (2026-2030), General (2023-2027) and Extra (2024-2028) pools built in. Answer A-D or "I don't know":
    missed cards come back within a few cards, known ones further and further apart. Progress per subelement, your
    expected score and pass likelihood, and practice exams drawn one question per group (35/35/50, 74% to pass).
    Progress is yours on any computer. Admins can add or update pools without a mod update: JSON files in a data pack
    (`data/<namespace>/license_pools/`) or in `config/radiocraft/license_pools/`, picked up by `/reload`. Questions
    that need a figure are left out for now.

#### Disks never sold, and ham shacks

- Five rare floppies for the radio computer turn up in old chests (ancient cities, stronghold libraries, mansions,
  trial chamber vaults, shipwrecks, dungeons, temples, village houses and more) and in ham shacks:
  - **Snake**, with a high score table on each computer.
  - **Pileup**: RadioCraft's own falling-blocks game. Callers pile into the band; fill a row to work them (a QSO).
    Every 8 QSOs you move up a band, 160m to 70cm, and it gets faster.
  - **Music Player**: 43 tunes: twelve public-domain classics (Joplin, Beethoven, Mozart, Grieg, Debussy, Bach and
    more) and thirty-one free tunes from OpenGameArt.org (chiptune, jazz, ragtime, synth and guitar rock, RPG
    orchestral, lo-fi, ambient), each credited to its author in the list. It
    sounds like a pair of small PC speakers, has a spectrum visualizer, a shuffle (H), a volume control (- and +,
    on top of the game's sliders), and the arrow keys go back and on. It comes **from the computer** through
    Minecraft's sound engine, so you hear where it is as you walk and turn, or from the **speakers connected to the
    computer** (desk, large and premium speakers touching it; wall speakers and loudspeakers touching it or on its data
    wire), each in its own sound: a premium speaker clean and full, a loudspeaker a loud horn heard from 64 blocks. The
    music plays on in other programs (a note in the top bar goes back to it) and after **Close**, which leaves the
    player open for next time; only you hear it, and it stops out of earshot, in another dimension, or when the
    computer is broken or loses power. The track list can be scrolled while a tune plays.
  - **Bible**: the four gospels of the World English Bible (public domain): read by chapter, go to a verse, search.
  - **Screensaver**: after a few idle minutes (SETUP: never, 1, 3, 5 or 10) the screen shows a starfield, your
    callsign in big letters or a band waterfall keying your call in Morse, also on the monitor in the world, and the
    computer draws a quarter of its power until a key, click or mouse move wakes it.
- **Ham shacks**: a little timber house with a J-pole on the roof, found dark: a VHF base station on 146.520 MHz,
  switched off, its battery flat (charge it or run a new power line), a radio computer and a chest of radio parts, out in the country, and now and then in a plains, taiga,
  snowy, savanna or desert village (in that village's wood, with a villager living in it).

#### ADS-B

- The **ADS-B Beacon** is a 1090 MHz transponder for anything that flies. On a Sable ship (or fixed on the ground) it
  sends its position, flight ID, squawk, altitude, track, ground speed and climb once a second. Place it facing the
  nose; it needs a little power. Squawk 7700, 7600 or 7500 shows in red.
- The **ADS-B Receiver** shows what its **ADS-B Antenna** hears on a round green radar screen: range rings, each craft
  with its track line, trail and data tag, ranges from 250 m to 20 km. Click a craft for its details. A receiver on a
  ship leaves its own ship off the screen. While it is on, its whole front is a square display, edge to edge like a
  modern ATC screen, showing the live picture in the world (rings, trails, targets and tags; no sweep, since ADS-B is
  not a radar) to anyone within 32 blocks.
- 1090 MHz is line of sight, so the higher a craft flies, the further it is heard: from Y 200 up, out to twice the
  large microwave horn's range (18,000 blocks with the defaults); below that the range falls off steeply, down to 200
  blocks at sea level (`adsb_full_range_y`, `adsb_min_range`, `adsb_max_range`, 0 = twice the horn). Lightning and
  solar weather do not touch it.

#### Weather alerts

- There is no weather service until players build one. The **Simple Weather Alert Generator** touches an APRS
  station (an H19, or a radio computer running APRS; or is cabled to one by data wire) that hears weather beacons from
  **at least three weather stations, each 150 blocks or more from the others**. It covers the area round them (200
  blocks past the outer ones) and, with Another Weather Mod, issues **tornado, severe thunderstorm, hurricane, tropical
  storm, blizzard, dust storm, high wind, dense fog, heat and cold** alerts for the storms there, and **tornado and
  severe thunderstorm watches** for storms forecast to arrive; a station reporting an inch of rain in an hour gives a
  flash flood warning. It broadcasts them on **162.500 MHz** (marine WX6) through a VHF antenna on its coax, so range
  and antennas matter like any other station. It needs a little power, more while sending. Right click it (or Jade)
  to see what it is missing.
- The **Simple Weather Alert Receiver** listens to the nearest generator it hears, on its built-in whip or an antenna
  on coax. On an alert for where it stands it **flashes red, sounds the 1050 Hz alert tone, reads the alert out**
  (what it is, how far and which way the storm is and where it is heading, spliced from recorded words like the real
  weather radio voice), shows it in chat nearby and gives **one redstone pulse**. It runs about three weeks of game
  time on a **Small Alkaline Battery** (shift + right click to change it) and blinks every 10 seconds when the battery
  is low. Right click silences it.

#### Advancements

- RadioCraft has its own **advancements tab** (Going Ham, placing your first radio, opens it), with over thirty
  advancements: getting on the air (**Is This Thing On?**, **First Contact**, **Licensed to Chill**, **Ground Plane
  Theory**), distance and propagation (**Skip Day**, **DX Hunter**, **Grey Line**, **Into the Static**, **Northern
  Lights**), modes (**Dit Dah**, **Typewriter Thunder**, **Chart Topper**, **Packet Rat**, **Weak Signal Wizard**,
  **Picture This**), the station (**Repeater Owner**, **Shack Upgrade**, **Mission Control**, **Digital Native**),
  public service (**Fleet Manager**, **Clone Wars**, **Storm Spotter**, **Take Shelter Now**), logging (**Net
  Control**, **Paper Trail**, **Card Collector**, **Worked All Biomes**, **Contest Station**) and a few secret ones.
- Contacts are **verified**: you heard another player on the radio and they heard you, both within five minutes (your
  voice reaching their radio while they hold it or stand within 16 blocks of its speaker), or an FT8 contact the
  computer completed. Logbook entries typed by hand never count. The distance is between the two stations.
- Earning the Awards program's **Worked All Biomes** award gives the advancement of the same name. A player who is
  offline when they earn one (the signer of a QSL card someone else picked up, the owner of a repeater someone talked
  through) gets it at their next login.
- Repeaters and Weather Alert Generators now remember who placed them (ones placed before this version do not: break
  and place them again to be credited).
- The Handbook's Getting Started chapter lists them.

#### FCC field gear

- The **FCC Field Agent Hat** and **Jacket** (1970s felt and corduroy, with a wide tie) are armour that protects like
  leather and mends with brown wool.
- The **FCC Banner Pattern** (paper and a lightning rod) puts the FCC seal on a banner or shield at a loom. A
  ready-made blue FCC banner is in the creative menu.
- The **FCC Badge** cannot be crafted: an operator issues it with `/radiocraft fcc badge <player>`, stamped with the
  player's name and a badge number. Right click shows it to the players around you. With Curios it is worn in the
  chest (body) slot. It gives no powers, but the **Radio Moderation Computer marks badge holders** with `[FCC]` after
  their name (talkers, hearers and admins).

#### Real-World Interface

- For server operators, **off by default**: links the game's radios to a real radio on the server's sound card. What
  is said in game on its frequency goes out of the sound card with the real radio keyed (PTT on a serial port's RTS or
  DTR line); what the real radio hears, when it passes the VOX threshold or its squelch (COS on CTS or DSR) opens,
  goes on the air in game from the block. A time-out timer (3 minutes by default) drops a transmission that runs too
  long, either way.
- Set up **only in the server config file** (`real_world_interface_enabled`, `real_world_interfaces`): sound card input
  and output, sample rate, serial port and lines, VOX or COS, gains, hang and time-out. The block's screen only picks
  one interface by name and shows whether each device was found, PTT, VOX/COS and the audio levels. One block per
  interface; by default only operators may change it or switch it on and off (`real_world_interface_op_only`).
  `/radiocraft realworld devices` lists the sound cards and serial ports; `/radiocraft realworld status` shows each
  interface.
- In game it is a radio on any band, FM or SSB, with an antenna on its coax. **Everywhere** mode needs no antenna and
  ignores distance: it hears every voice transmission on its frequency in its dimension and is heard by every antenna
  there. Its transmissions are in the moderation log ("Real radio").
- **Licence warning**: everything said through it goes out on a real transmitter. Every player who talks through it
  must hold a real amateur licence and use their own callsign; the server operator is the control operator and is
  responsible for the station. Each player clicks **"I hold an amateur licence"** on its screen once (kept for every
  interface on the server); until they do, nothing they say on the air goes out through any interface (their voice is
  dropped as it reaches the interface, however it got there: direct, through a repeater or Everywhere).
- A quiet **monitor speaker** on the block plays what goes out to the real radio for players standing by it (6 blocks).

#### CB radio

- **Citizens Band** (26.965-27.405 MHz, the 40 US channels): no licence needed. CB is **AM**: a new receive mode where
  there is no background static, a weak signal turns hissy before it fades, a strong station on the next channel
  bleeds over, and two people talking at once are both heard (as on SSB). It carries a little less far than 10m,
  being 5 W: line of sight is a quarter shorter and skip about a third shorter. Any 10m antenna works on CB.
- **TRC-217 CB Portable**: a walkie-talkie with its own cells (swap charge with a small battery). Turn the volume up
  from OFF and its whip pulls out about three blocks; hold right click to talk; sneak and use for the channel knob on
  top, volume and squelch. Half a mobile's range, both ways.
- **5875 CB Mobile**: the deluxe set with a woodgrain lid: a clock, RX signal / TX power meter, red channel LED, ON AIR
  lamp, and a meter that reads SWR or modulation (MOD/SWR switch). Its **speech compressor** makes your voice punchier
  and gives a little more talk power. Built with a pure 10m crystal.
- **PC68 CB Mobile**: a compact chrome mobile with a meter, channel LED, RX/TX lamp and a CH9 switch. Any 10m crystal.
- **CB Ground Plane Antenna**: a vertical five blocks tall with radials at its foot, matched across all 40 channels.
  It works on 10m at a push (about 1.8:1 at the bottom of the band, worse higher up).
- On the mobiles the **volume knob is the power switch** (its lowest click is OFF), and the 5875's clock runs whenever
  the radio has a charged battery on its wire, switched on or not. The block shows the channel, the clock, both meters'
  needles and the ON AIR / RX-TX lamps.
- The mobiles draw an SSB rig's power while listening and only a little more on the air. Their hand mic comes off on a
  cord (sneak and use), as on the marine radio. The squelch knobs are there but do nothing yet.

#### Modes meet: SSB, AM and FM

- Every voice transmission now carries its mode. An **SSB** receiver hears an **AM** station clean when tuned on its
  carrier, and off it with the carrier's whistle and the voice shifted; it hears **FM** as a garbled warble. An **AM**
  receiver (CB) never hears SSB on its own (no carrier), only as garble over an AM station it lands on; FM's carrier
  opens it, but as a warble with no words. FM receivers do not hear AM.
- **10m radios tune down through the CB channels** (the "freeband", to 26.9 MHz). Listening is free; transmitting
  there needs a MARS mod, as anywhere out of band.
- The **all-band radio** has **AM**: press its FM button again.
- **No antenna** on a transmitter counts as an SWR of 6: transmit into an empty connector and it can blow, like any bad
  match. The CB meters show it.
- The 5875's AWI lamp lights amber while a signal comes in. Right click a radio screen's PTT to take the hand mic.

#### Squelch

- The **CB radios' SQUELCH knobs** work: turned up, the speaker stays shut until the RX meter reads past the setting
  (weak stations and their hiss are cut out). At 0 it is open.
- The **20m and 40m radios and the HF receiver** have a squelch on a spare knob (20m: bottom left; 40m and the
  receiver: top left). Hover it to see OPEN or SQL 1-10. Set above 0, the static goes quiet too, and comes back under
  a signal that opens it. The S-meter still moves for a signal under the squelch.
- A squelch set at or under the band's noise is held open by it: the CB radios (portable too) hiss on the band
  noise with the squelch down, and go quiet once it is turned past.
- Radios switch on at once (they could wait 3 seconds, paying for their first tick of power twice).
- SSB has no carrier: keyed with no antenna it only risks blowing while you talk (or send CW or data). AM and FM
  carry a carrier whenever keyed.

#### New HF radio panels

- The **10m, 20m, 40m, 80m and all-band radios** have new front panels that match their blocks: an analog meter,
  a display window, the tuning dial, AF, MIC and SQL knobs, an AGC switch, mode keys and PTT. The old panels are kept
  (as `_old` textures) for now.
  - Knobs: scroll, or left click up and right click down. The dial: scroll, or drag up and down. Switches and the
    80m's mode lever: click a position, or scroll.
  - The **meter** reads S on receive; on the air S and PO read power out, SWR the SWR. Its selector is remembered.
- **AM** on every HF radio (10m AM talks to CB on the freeband).
- **AGC**: SLOW is how radios always sounded. FAST lets the static back quickly between words. OFF levels nothing:
  strong stations blast, weak ones are faint, and the static never ducks.
- Every HF radio has a **squelch** knob.

#### Remote rig control

- RIGCTL remote audio: on the new Remote page (F6) switch the audio to REMOTE and the computer becomes the rig's
  speaker, and RIGCTL's PTT takes your voice into the rig, wherever the rig is.
- RIGCTL remote control: a computer can let others on its network drive its rig (REMOTE ACCESS, OWNER or ANYONE WITH
  CODE, an optional access code). Another computer reached over data wire or a microwave link lists it by name and
  link quality, connects, and drives the rig as if it sat there, with its own speaker and mic. One remote user at a
  time; the host shows who and can take over. Microwave links add a little delay and, as the link weakens, a rough
  voice and dropped words. The session ends if the link drops; closing the screen leaves it (and the waterfall on the monitor) running.
- Data wire joins only the devices at its ends: what touches the far device is not on the line (touching devices
  still chain). A junction box joins data wire of different colours, as it does power wire.
- Network names: computers that reach each other form a network, and each needs a name the others there do not use.
  A computer with no name there, or a taken one, asks for one before the desktop.

#### Signals that sound and look real

- SSB is only there while someone speaks: the S-meter and the squelch follow the voice, and between words the static
  comes back. AM and FM hold a carrier.
- Dead air stations carry a faint hiss and crackle, not digital silence, and are never on SSB.
- Commercial radio modules have a mode: SSB or AM on HF, FM on VHF.
- The RIGCTL waterfall draws signals by mode: SSB as the voice beside its frequency, coming and going with the words;
  AM as a carrier with sidebands that swell; FM as a carrier that spreads as someone talks.

#### Stability

- **Repeater loops** no longer crash the server. Two repeaters that hear each other (one listening where the other
  transmits, both ways) lock each other up as real ones do: the transmission goes round one relay a tick under a
  rising feedback howl, until their timeout timers drop them. The new server setting `repeater_loop_protection`
  (off by default) stops loops from starting at all.
- Fixed a crash when a block radio's antenna transmitted while players or antennas were joining the band.
- Radio control packets no longer look up blocks the player is nowhere near (which could force chunks to load).
- A wire fault no longer keeps the chunks along its whole power line loaded.

#### Memories, QUICK, switches and cells

- **QUICK** on every new radio panel: hold to store the frequency, press to jump there, press again to come back.
- The **all-band radio** has **50 memories**: hold MEMORY to store (pick the channel with the dial, press MEMORY),
  VFO/MEM to switch, hold VFO/MEM on a memory to copy it to the VFO.
- The **VHF receiver** works properly now: a frequency display, six memory buttons that store, select and **scan**,
  a mute button, tooltips on every control, and it runs on a small NiCd or alkaline cell (shift right click) with no
  power wire.
- **Power Switch - On/Off** and **Power Switch - Left/Right**: switch power circuits by hand or with a redstone pulse.
- The **Stable Chemical Battery**: not rechargeable, inert until its first redstone signal, then powers the circuit
  until flat. Stores full and sealed: an emergency reserve.
- A redstone pulse turns the **antenna switch** to the next output with coax on it.
- Small **alkaline cells** run the VHF handhelds and the CB portable; a flat one comes out as a **Dead Alkaline
  Battery**, which crafts back into an iron nugget.

#### CB controls

- The **5875's FUNCTION knob** picks **AM, USB or LSB**. Sideband carries further than AM (12 W PEP against a 4 W
  carrier). Two sets on the same sideband talk; USB and LSB on one channel only put a little interference on each
  other, and an AM set does not hear SSB on its own.
- **CLAR** (the clarifier) tunes the receiver up to 1 kHz either side of the channel, to bring an off-tune SSB voice
  back to its own pitch. **RF GAIN** turns the receiver down, static and weak stations with it.
- **NB** (noise blanker) and **ANL** (automatic noise limiter) cut impulse noise (redstone sparks, aurora crackle):
  the blanker most, the limiter some, both together the most. The PC68's NB/ANL switch works both.
- The **PC68's AM/FM switch** puts it on FM.
- The CB portable also fits the **belt** Curios slot, on the right hip.

#### Batteries and floppies

- The rechargeable small battery is now the **Small NiCd Battery** (it was called alkaline; nothing changes in worlds).
- New **Small Alkaline Battery**: cheap, full when made, and **not rechargeable** (no charger takes it, and the
  rechargeable radios want the NiCd one). The weather alert receiver runs on either.
- **All Applications Floppy** (creative only): put it in a radio computer's drive and every floppy program is installed
  at once; the disk comes straight back out.

#### Grounding

- **Ground rods**: drive them into the ground (stack them down a hole to go deeper) and run **ground wire** to a radio
  or to a lightning arrestor's ground lug. Green wire is now called **Ground Wire**, and only it bonds a rod. A
  grounded arrestor grounds every radio on its coax. Not required: a good ground lowers an HF station's noise (most of
  the local redstone and lightning hash, some of the band noise). Damp clay and mud are best; sand, rock and frozen
  ground need deeper or more rods; rain and water nearby help. Use a rod to measure it.
- A lightning arrestor needs a good ground to stop every strike: without one, half of them arc past it.
- Power on a ground run (a battery, solar panel or charge controller on the green wire) is a **ground fault**: the rod
  sparks and the batteries drain into the earth.

#### Lightning arrestor and battery meter
- **Lightning arrestor**: an in-line coax block. The signal passes straight through it. When lightning strikes the
  antenna, the surge stops there: the coax between the antenna and the arrestor can still burn out, but radios behind
  it do not blow, the coax on their side is not burnt, and their batteries do not catch fire.
- **Battery meter**: put it on a power wire. Its face shows the batteries on that wire as a 12 V bank: voltage
  (11.8 V empty to 12.7 V full at rest, up to 14.4 V while charging, sagging under load), charge, and an arrow while
  they charge or discharge. Using it prints the full reading with the time to full or empty. A comparator reads the
  charge (0 to 15). Power passes through it like a wire junction.

### Changes
- **Terrain** in chunks nobody has loaded is no longer flat: a signal's path through them gets the natural ground from
  the world generator (no trees or buildings) until the chunk loads. Server config `terrain_worldgen_fill`,
  `terrain_worldgen_chunks_per_tick`.
- The **teletype** no longer prints the solar report (its SOLAR WX code is gone): use the WEFAX receiver's SOLR WX.
  Its comparator still reads the solar conditions.
- The guide book is now the **ARRL Radiocraft Handbook**, with new chapters for the radio computer, aviation and the
  server admin gear, and a page on ham shacks.
- WEFAX receivers with AUTO on now print the charts other stations send (on paper), as a real recorder starts on the
  start tone.
- APRS maps draw spaces as spaces and more punctuation (they used to draw a dash).
- RTTY sends the real ITA2 (Baudot) codes with letters and figures shifts, and a weak signal misprints like a real
  teleprinter instead of printing random characters.
- A radio interface no longer digipeats messages addressed to its own callsign under another SSID (W1BTR-1 at a
  W1BTR station), so a BBS's own station does not repeat its traffic.
- The radio computer lists its programs alphabetically (in every launcher, `DIR` and INSTALL), built-in and installed
  ones together.
- MUSIC renders its tunes a few seconds ahead and the sound engine keeps over a second queued, so the world lagging
  no longer makes tunes skip, and the visualizer's bars follow what you hear.
- Commercial CW beacons, beepers and numbers stations no longer make the server stutter every 30 seconds when they all
  start a transmission together (their Morse and tones are made far faster and the fixed ones only once).

### Not finished yet

- Most new blocks and items have **placeholder art** (plain boxes and simple textures) until the models are drawn; the
  FCC hat looks like a cap, not yet a fedora, and the badge is not drawn on the player when worn.
- **Real-World Interface**: not yet tried with real sound cards and serial ports. It carries voice only (no CW or
  data), and it does not hear commercial radio modules' broadcasts or the WEFAX coast station in Everywhere mode.
- **ADS-B**: hills and buildings do not block 1090 MHz yet (only the craft's height sets the range). It is built on Sable;
  Create Aeronautics craft, which are built on Sable too, have not been tried yet.
- **Marine radios**: no DSC distress button. A weather alert generator's data and alert tone can be heard on WX6, but
  no spoken weather broadcast yet.
- The **Music Player** is heard only by the player who started it, not by other players.
- Another Weather Mod's new sirens and recorded siren sounds are still to come.

### For mod developers

- The radio API (`com.arrl.radiocraft.api`, API version 1.0) now covers every band, including low band, commercial VHF
  and the marine channels: `RadioBands` reads the bands and the tune/transmit rules, and a mod can **register a band**
  of its own. A whole radio is a `CompanionRadioBlock` + `CompanionRadioBlockEntity` described by a `RadioSpec`; an
  antenna can be a `SimpleAntennaType` (SWR and gain per band) on a `CompanionAntennaBlock`. Also new: `IRadio` for
  any radio, `IHandheldRadio`, `IAntennaReceiver`, `IWireConnectable`, `SolarSource`, and `RadiocraftApi` for the
  capability registration. Nothing was removed.
- Limits: handhelds stay on the VHF network (143-174 MHz), a companion radio block has no CW and no screen of its own,
  and bands other mods register are not on the band plan sheet or in `/bandcalculate`.
- The guide is on the wiki: [Modding API](https://radiocraft.bigtimeradio.us/wiki/modding.html). An example companion
  mod lives in the GameTest sources. RadioCraft has no Maven of its own yet; the guide shows how to depend on it
  through Modrinth's Maven.
