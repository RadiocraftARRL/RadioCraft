# Weather network rework (maintainer, 2026-11-18)

The weather alert generator needed three APRS weather stations, each with its own H19, radio interface and radio, 150
blocks apart, before it did anything, and one player's stations under one callsign counted once without saying why.
This reworks it so one station already does something, adds a cheap way to report weather, and shows what is missing.

## Local Weather Station (renamed)

- The Weather Station becomes the **Local Weather Station**. Same block and readings (wind, gusts over 10 minutes,
  temperature, humidity, pressure, rain in the last hour).
- Any number of devices read it at once: everything within 64 blocks that wants weather takes the nearest one (H19,
  APRS computer, Weather Observer, Weather Alert Generator, Local Weather Station Display, FM broadcast transmitter on
  its data wire). This already worked for H19s; it now works the same for all of them.

## Local Weather Station Display (new)

- A desk/wall display in a wood case with a red seven-segment (clock-style) LCD.
- With a Local Weather Station within 64 blocks it scrolls through the readings: temperature, humidity, pressure,
  wind direction (compass points and degrees), wind speed, gusts, rain last hour. Without one it shows `----`.
- No power needed (a battery display). Placeholder model until the maintainer's (ModelDesignBounty).

## Weather Observer (new)

- A small box that reads the nearest Local Weather Station (64 blocks) and sends its readings through the radio it
  touches or is data-wired to, on that radio's frequency: a short AFSK burst every few minutes, at different tones and
  rhythm from APRS so it sounds distinct (heard as data on a radio tuned there).
- No callsign of its own needed beyond one: observers are told apart by where they are, so one player's observers all
  count. (APRS still needs separate SSIDs, as in real life.)
- A cheaper outpost: Local Weather Station + Weather Observer + radio + antenna.

## Headless APRS TNC (new)

- A cheaper H19 without a screen: a radio interface and APRS terminal in one box on a radio (touching or data wire).
- Beacons (with the nearest Local Weather Station's weather, like the H19), optional digipeater, and the APRS side of a
  Weather Alert Generator. No messages, no map, no stations list.
- Next to an H19 (touching or data wire) it can stand in for the Radio Interface: the H19 sends and hears through it
  under the TNC's callsign. Only for an H19: a radio computer, fax, teletype or anything else still needs a real
  Radio Interface.
- Model and screen after a PacComm TINY-2: a grey ribbed case, blue front with a white swoosh, **PACKET TNC** logo,
  **RC-TNC** model name, DCD / PTT / STA / CON / PWR lamps and an ON/OFF rocker. The screen is the front panel (lamps
  live, rocker works) with a rear-panel view for callsign + SSID, DIGI on/off and BEACON on/off.

## Weather Alert Generator

- Sources, counted together:
  - APRS weather beacons heard by an APRS station on its bus (H19, APRS computer or Headless TNC), one per
    callsign-SSID.
  - Weather Observer bursts heard by a radio on its bus, on that radio's frequency, one per observer position.
- Tiers by how many spread-out stations (150 blocks apart) it hears:

  | Stations | Covers | Alerts |
  |---|---|---|
  | 1 | 150 blocks round it | what is at the station now: flash flood, high wind, a storm over it (TOR/SVR/BZW/...) |
  | 2 | both + 200 block margin | + warnings for storms heading into the area (5 minute lookahead) |
  | 3+ | hull of all + 200 margin | + watches (tornado/severe 15 minutes ahead, tornado-watch days), tropical statements and watches (what it does today) |

- Its status (right click, Jade) lists every station heard: name, kind (APRS/observer), distance, age, and why one
  does not count (too old, too close to another, same callsign as another APRS station: give it its own SSID), and
  what the next tier needs ("a third station 150 blocks from both would add watches").

## Order

1. Generator tiers + status (pure logic, unit tests), APRS source unchanged.
2. Rename to Local Weather Station.
3. Weather Observer + the generator's observer source (GameTests).
4. Headless APRS TNC (block, front/rear screen, digipeat, generator source).
5. Local Weather Station Display.
6. Guide, wiki, NeedsTesting, ModelDesignBounty; recipes proposed three at a time.
