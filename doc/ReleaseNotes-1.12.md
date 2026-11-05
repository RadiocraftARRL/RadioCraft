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

- Channel-only radios with a **16-position knob** (16 channels; the upgraded handheld 40), set up by a radio shop: each channel has its receive and transmit
  frequency, analog FM or digital, a CTCSS tone and an encryption key. They need no ham callsign and always transmit
  inside their own band. Program them with the radio computer's **Radio Programmer** (below). Out of the box the VHF
  radios have the five licence-free MURS channels (the upgraded handheld adds 146.520) and the low band mobile four
  simplex channels.
  - **Basic Commercial Handheld**: a 1990s brick for 150-174 MHz, analog FM, RX and TX lights, no display.
  - **Upgraded Commercial Handheld**: the fire-service radio, crafted from the brick: **40 channels**, digital voice
    and encryption, and the 2m ham band too. Like 1990s fire radios it keeps the 16-position knob and adds a **zone
    switch** (A = channels 1-16, B = 17-32, C = 33-40); its display shows the channel name, zone and channel.
  - **Commercial Mobile Radio** (150-174 MHz, analog and digital) and **Low Band Mobile Radio** (30-50 MHz, analog):
    desk, vehicle and ship radios with a hand mic, powered and wired to an antenna like the base stations.
- **Digital voice** has no static: clean until the signal would be 70% static on FM, a short warble, then nothing.
  An **encrypted** channel sounds like garbled digital noise without the key, and any digital signal is a harsh buzz
  on an analog or ham radio. Repeaters pass digital on as it is. The moderation computer still records the clear voice.
- The cases **take dye** like leather armour. A mobile keeps its colour and channels when broken and placed again.
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
  - Built in: **EDIT** (a text editor; Ctrl+S saves), **SYSINFO**, **BANDPLAN** and **INSTALL**.
  - Other programs come on **Floppy Disks**: craft a blank disk (iron nugget, redstone, black dye, paper), then craft
    it with the part that stands for the program. Put it in the drive and install it; the disk is not used up.
  - Files (documents, pictures, logbooks, frequency lists, codeplugs) live on the computer, 1 MB by default
    (`computer_storage_kb`, `computer_max_files`), and go with it when it is broken and placed again. The monitor in
    the world shows the screen you left on it, and programs carry on for ten minutes after you get up.
- **Scanner**: set it beside the computer. It scans maps, photos and books into files, and prints pictures (on color
  photo paper, an empty map or photo paper) and documents (as a written book, a sheet of paper a page).
- **Office programs**:
  - **Book Editor** (BOOKEDIT): books of any length, a page at a time, printed through the scanner as written books in
    volumes of up to 64 pages.
  - **Logbook** (LOGBOOK): contacts with the UTC date and time, call, frequency, mode, reports and notes. F6 fills a
    contact in from a station your radios heard lately; F7 writes the log out as ADIF for real logging programs.
  - **Frequency Manager** (FREQMAN): lists of nets, repeaters and channels with offsets, tones and their band plan use;
    sends marked channels into a commercial mobile wired to the computer, and reads a radio's channels in.
  - **Radio Programmer** (CPS): reads, edits and writes the channels of a commercial radio (16, or 40 on the upgraded
    handheld), its unit ID (F4) and contact list (F7). A mobile wired to the computer is programmed through the cable;
    handhelds (and mobiles still in item form) by hand: **F9 Program radios**, Close the screen with CPS left in that
    mode, then hold the radio and right click the computer or its monitor to write it (sneak to read it). The unit ID
    goes up by one from radio to radio. The mode ends when CPS closes, another program opens, the power goes or after
    five minutes unused. Keys set as not readable show as `****` and never come off the radio. Codeplugs save to
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
  - **SSTV**: colour pictures to everyone or one callsign, about half a minute each, coming in line by line and torn by
    noise where the signal fades. Send them again, caption them, or print them.
  - **WEFAX**: weather charts in greys: the coast station's radar chart and forecast, the Ionospheric Processors'
    bulletins and alerts, and charts other stations send. Draws a chart of the local weather to send as your own.
  - **Direction Finding** (DFIND): the display of a direction finder cabled to the computer: a terrain map with each
    bearing and the fix, a turning 3D view of how good the fix is, saved past searches, and the set's controls.
  - **ADS-B**: a second display for an ADS-B receiver (below).

#### Disks never sold, and ham shacks

- Four rare floppies for the radio computer turn up in old chests (ancient cities, stronghold libraries, mansions,
  trial chamber vaults, shipwrecks, dungeons, temples, village houses and more) and in ham shacks:
  - **Snake**, with a high score table on each computer.
  - **Pileup**: RadioCraft's own falling-blocks game. Callers pile into the band; fill a row to work them (a QSO).
    Every 8 QSOs you move up a band, 160m to 70cm, and it gets faster.
  - **Music Player**: twelve public-domain tunes (Joplin, Beethoven, Mozart, Grieg, Debussy, Bach and more). The
    music plays on in other programs (a note in the top bar goes back to it) and after **Close**, which leaves the
    player open for next time; only you hear it, fading as you walk away, and it stops past 24 blocks, in another
    dimension, or when the computer is broken or loses power.
  - **Bible**: the four gospels of the World English Bible (public domain): read by chapter, go to a verse, search.
- **Ham shacks**: a little timber house with a J-pole on the roof, a VHF base station on 146.520 MHz running on its
  battery, a radio computer and a chest of radio parts, out in the country, and now and then in a plains, taiga,
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

#### FCC field gear

- The **FCC Field Agent Hat** and **Jacket** (1970s felt and corduroy, with a wide tie) are armour that protects like
  leather and mends with brown wool.
- The **FCC Banner Pattern** (paper and a lightning rod) puts the FCC seal on a banner or shield at a loom. A
  ready-made blue FCC banner is in the creative menu.
- The **FCC Badge** cannot be crafted: an operator issues it with `/radiocraft fcc badge <player>`, stamped with the
  player's name and a badge number. Right click shows it to the players around you. With Curios it is worn in the
  chest (body) slot.

#### Real-World Interface

- For server operators, **off by default**: links the game's radios to a real radio on the server's sound card. What
  is said in game on its frequency goes out of the sound card with the real radio keyed (PTT on a serial port's RTS or
  DTR line); what the real radio hears, when it passes the VOX threshold or its squelch (COS on CTS or DSR) opens,
  goes on the air in game from the block. A time-out timer (3 minutes by default) drops a transmission that runs too
  long, either way.
- Set up **only in the server config file** (`real_world_interface_enabled`, `real_world_interfaces`): sound card input
  and output, sample rate, serial port and lines, VOX or COS, gains, hang and time-out. The block's screen only picks
  one interface by name and shows whether each device was found, PTT, VOX/COS and the audio levels. One block per
  interface; by default only operators may change it (`real_world_interface_op_only`).
  `/radiocraft realworld devices` lists the sound cards and serial ports; `/radiocraft realworld status` shows each
  interface.
- In game it is a radio on any band, FM or SSB, with an antenna on its coax. **Everywhere** mode needs no antenna and
  ignores distance: it hears every voice transmission on its frequency in its dimension and is heard by every antenna
  there. Its transmissions are in the moderation log ("Real radio").
- **Licence warning**: everything said through it goes out on a real transmitter. Every player who talks through it
  must hold a real amateur licence and use their own callsign; the server operator is the control operator and is
  responsible for the station.

### Changes

- The guide book is now the **ARRL Radiocraft Handbook**, with new chapters for the radio computer, aviation and the
  server admin gear, and a page on ham shacks.
- WEFAX receivers with AUTO on now print the charts other stations send (on paper), as a real recorder starts on the
  start tone.
- APRS maps draw spaces as spaces and more punctuation (they used to draw a dash).

### Not finished yet

- Most new blocks and items have **placeholder art** (plain boxes and simple textures) until the models are drawn; the
  FCC hat looks like a cap, not yet a fedora, and the badge is not drawn on the player when worn.
- **Real-World Interface**: not yet tried with real sound cards and serial ports. It carries voice only (no CW or
  data), and it does not hear commercial radio modules' broadcasts or the WEFAX coast station in Everywhere mode.
- **ADS-B**: hills and buildings do not block 1090 MHz yet (only the craft's height sets the range). It is built on Sable;
  Create Aeronautics craft, which are built on Sable too, have not been tried yet.
- **Marine radios**: no DSC distress button, and nothing broadcasts NOAA weather on the WX channels yet.
- The **Music Player** is heard only by the player at the screen, and stops when the screen closes.
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
