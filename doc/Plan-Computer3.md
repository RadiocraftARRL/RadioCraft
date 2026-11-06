# Next computer batch (decided 2026-11-06, maintainer)

This batch starts after the launcher/SETUP agent finishes. Before starting, read `doc/Plan-1.12.md`:
- the Step 5 handoff, including the "Writing a program" guide;
- the "Computer apps 2", "Music player fixes" and "Launcher and SETUP" handoffs.

## Recipes approved
- Propagation Predictor floppy: blank floppy + clock. Approved as built.
- Repeater Directory floppy: blank floppy + redstone repeater. Approved as built.
- RTTY floppy (note block) and Packet BBS floppy (chest): approved.
- Radio Computer: the diamond becomes a diamond block. The launcher agent is applying this.
- FT8 floppy: blank floppy + **any pressure plate** (`#minecraft:pressure_plates`).
- License Study is now built in, with no floppy. The launcher agent is applying this.

## Programs and features to build
1. **Rig control + waterfall**:
   - tune the connected radio from the keyboard;
   - scan memories;
   - a live waterfall of activity across the band.
2. **FT8** (floppy): an automated weak-signal digital mode in 15 s slots.
   - It decodes below the voice and RTTY copy threshold.
   - Contacts are logged to the logbook automatically.
3. **Scheduler**: timed jobs, such as:
   - automatic station ID;
   - WEFAX broadcasts on a schedule;
   - a beacon every N minutes;
   - net reminders.
4. **Net Control logger**: a check-in list, roll call, and a printable net summary.
5. **Awards tracker**: reads the logbook, such as "Worked All Biomes", worked all dimensions, and distance records.
   - Counts **every logbook contact**; confirmation is not needed.
   - Prints a certificate.
6. **QSL cards**:
   - **Designer program:** makes a **template**:
     - front: callsign (or blank), background and border, a picture from the disk, text lines;
     - back: a layout with blank fields.
   - **Printing:** prints a run of identical blank cards (they stack), one paper or photo paper each.
   - **Filling in:** right-click a blank card in hand to open a form, like a book and quill, with To, band, frequency, mode, signal report and notes.
     - The date and time are filled in automatically.
     - Signing locks the card.
     - A player can design a template for someone else and hand them a stack, so the recipient needs no computer.
   - **The card item:**
     - shows like a map when held;
     - right-click flips it between front and back;
     - its tooltip shows from, to, date and band;
     - an item frame shows the front.
   - **Exchange:** by hand only, by **radio fax** (the card's image is sent and the receiver prints a copy), or as a **Packet BBS** attachment.
     - No QSL bureau, no eQSL service, no confirmation matching ("easy to fake irl paper ones").
   - **QSL album:** an item that holds cards and pages through them.
7. **Data floppies**: copy files to a blank floppy and carry them between computers.
8. **Screensaver**: a starfield or a scrolling callsign after a few idle minutes, which also cuts the power draw.
   - Needs its program installed.
   - The floppy is **found in the world** (loot), not crafted.
9. Skipped for now: the BASIC interpreter with redstone I/O.

## Weather alerts (in-world, no global broadcast)
"This isn't a technological world with cities and infrastructure, the player has to build it out."

- **Simple Weather Alert Generator** (block):
  - It must be attached to an APRS station, an H19 APRS terminal or a computer running APRS.
  - That station must hear **at least 3 APRS beacons carrying weather-station data** (the existing Weather Station block).
  - Those beacons must be **at least 150 blocks apart from each other**.
  - From their data, plus Another Weather Mod storms and tornadoes in the area they cover, it builds alerts.
  - It transmits on **162.500 MHz** into any VHF antenna, through coax as usual.
  - Power: a small steady draw, and a good bit more while transmitting.
- **Simple Weather Alert Receiver** (block):
  - **Station:** it picks up the nearest generator in range.
  - **Antenna:** with nothing attached it uses a small built-in antenna; coax and an antenna can be attached instead.
  - **Battery:** a **Small Alkaline Battery** (the existing item), changed with shift + right-click. A battery lasts weeks of game time.
  - **On an alert for bad weather near the receiver:**
    - it flashes red;
    - it plays an alert beep;
    - it **announces the hazard with a voice**;
    - it gives **one redstone pulse**, not a held signal.
  - **Low battery:** a red flash every 10 s.
  - **Voice:** **pre-made word clips** spliced in game, like the real NOAA voice.
    - Generate them once with a TTS tool whose voice licence allows redistribution, and record the licence in the credits.
    - Vocabulary: hazard names (tornado warning, severe thunderstorm warning, and so on), "for the area", directions, numbers and units.
- Broadcast FM (receivers, transmitter, EAS on FM) is a **separate later section**, not this batch.
