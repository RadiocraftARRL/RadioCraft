# Future batch: fire alarm boxes, SCADA, commercial licenses (maintainer, 2026-11-10)

**Status (2026-11-10):**
- Section 3 (licenses, the programmer's License Check and the FCC program) is built and tested
  (`CommercialLicenseTest`, `CommercialLicenseGameTests`). Still to do there: the callsign as the codeplug's station ID
  and a commercial repeater's CW ID, OVER LIMIT in the FCC program, and the FCC floppy's recipe.
- The shared layer, section 1 and section 2 are built and tested (`MeshTest`, `RadioMeshGameTests`), with placeholder
  models: `common/radio/mesh` (RadioMesh, MeshLink), `common/radio/firealarm`, `common/radio/scada`, the blocks in
  `common/blocks/mesh`, and the FIRE ALARM and SCADA programs. Until the maintainer's photos come, the controller
  works from the block (right-click STOP, shift NEXT, paper for the printer), RTU faces are set by shift + right-click,
  and the master's rules come from a book and quill or the SCADA program.
- 2026-11-11, from the maintainer's photos: models for the box (wall cabinet), pull station, a new panic button, the
  RTU and the two-block master; the box's inside screen (key: TEST, RESET, SILENCE, DRILL, zone lamps, LINK meter;
  the box rings bells by redstone out of its back); the master's console screen (CRT points/rules, POLL SEC 1-3 s,
  LOCAL I/O toggles). The master's adjustable setting is the poll speed (`POLL n`), not the hop limit, which is the
  server's `mesh_hop_limit` (8) for both systems. The License Check now takes any original license the player
  carries, not only one on the scanner.
- Built 2026-11-13: the box map in the FIRE ALARM program (F4), trends in the SCADA program (F4), power for RTUs and
  masters (`scada_power_use`). The controller's own CRT screen is scrapped (maintainer, 2026-11-13).
- Not built yet:
  data-wire output for bells (redstone only), power for RTUs and masters, a box keeping its name when broken, and
  recipes for all of it. Terrain does not block the mesh (it uses straight-line range, so unloaded links work).

Three systems that share one layer: devices with an address, a controller that hears them, acks and retries
over VHF data packets (`TncPacket`), and a computer app. Build that layer with the fire alarm, then SCADA mostly
reuses it. Licensing comes first or alongside, because both systems run on a licensed frequency.

## Shared layer
- New `TncPacket` kinds, one per system (`FIRE_ALARM`, `SCADA`), on the license's frequency.
- **Sound**: a short, quiet data chirp. Not an APRS-length burst.
- Addressed devices, with acks from the controller and retries until acked.
- **Unloaded chunks**: a device that was working when its chunk was last loaded counts as OK. The controller keeps a
  record of each device's last-known position and antenna setup (as `DataWireMemory` and `CommercialStations` do), so
  check-ins succeed and the mesh still relays *through* unloaded boxes.
- Range: a built-in antenna (as `ReceiverWhipAntenna`) gives poor range. Coax to a VHF antenna gives normal VHF range.

## 1. Vintage radio fire alarm

### Blocks and items
- **Fire alarm box** (radio master box): the transmitter. It has a 4-digit box/account number and a location label
  ("FLORENCE ROCHE SCHOOL / 342 MAIN ST"). It has a built-in antenna and a coax port.
- **Indoor pull station** (wall) and **outdoor pull box** (red street pedestal). Each has a zone number and connects
  to a box over data wire. A redstone input on the box counts as a zone too (smoke detectors from other mods).
- **Fire alarm key** (item): a pulled station stays down until reset with the key.
- **Fire alarm controller**: a rack unit in the DMP style (see the maintainer's photo of a DMP-704):
  - Green CRT showing the latest event: type, account, label, zone, receiver line (R02L1), time.
  - NEXT / STOP / FAST keys.
  - A printer that prints a pull alarm report slip on paper. It shows HEAD JAM / PAPER OUT when empty.
  - A buzzer, and redstone or data-wire output for bells and sirens (AWM siren controller).
  - It counts as the license's **base**.

### On the air
- Messages: FIRE, RESTORE, TEST, TAMPER (box broken), and a periodic **check-in**. The controller marks a box MISSING
  when it stops checking in (in the first version). One check-in per in-game day.
- **Mesh**: a box that hears a packet it has not seen sends it on once, after a short random wait, up to a hop limit
  (default 8, server config; the digipeater code, keyed on packet id). The controller's ack goes back the same way. A box repeats its message
  until acked, and its lamp blinks until then.

### FIRE ALARM computer app
On a computer touching the controller or on its data wire:
- Event log.
- Box list with status and last check-in.
- Acknowledge and silence.
- Out-of-service mode (place a box out of or back in service).
- Box map (APRS map painter).

## 2. SCADA (redstone over radio)
Separate from APRS, so beacons and maps stay clean. **A master is required**: RTUs never talk to each other directly.

- **Relaying**: RTUs relay the same way fire alarm boxes do, with a hop limit of 1 to 3, set on the master.
- **RTU** (remote terminal unit): a radio with a built-in antenna or coax port, a unit ID, and the license's
  frequency. Each face is IN (reports its redstone level, 0-15), OUT (puts out what the master sets) or off.
- **SCADA master**: polls the RTUs in turn. It counts as the license's **base**.
  - Rules: `IF RTU 3.north >= 10 THEN RTU 7.east = 15`, and **copy** (mirror one face to another). Rules can also
    drive the master's own redstone faces.
  - The master has a small rule table on its own screen.
- **SCADA / HMI computer app**: point list, live values, trends, the full rule editor.
- **Balance** (this is wireless redstone, so it costs something):
  - The poll cycle adds a few seconds of delay.
  - Radio range and noise apply.
  - It needs power.
  - RTUs on the same frequency share its airtime.

## 3. Commercial licenses

### Getting one
- **Cartographer** trades. It already sells the world map module, and frequency coordination is spectrum mapping.
  - Journeyman: **commercial VHF frequency license** and **commercial low band frequency license**, 16 emeralds +
    paper each.
  - Expert/Master: **commercial license upgrade**, 32 emeralds + a compass.
- Ops: `/radiocraft license grant <players> vhf|lowband [tier]`, plus `info`, `revoke` and `list`. Server config mode: `VILLAGER` (default), `ADMIN` (ops only) or `OFF`.

### Claiming a license
Right-clicking a blank license:
- Picks a random frequency on the 12.5 kHz grid: 150-174 MHz for VHF, 30-50 MHz for low band.
  - It skips frequencies that are already licensed.
  - It skips the reserved ones: marine channels, DSC/AIS, the NOAA weather channels.
- Generates a Part 90 callsign (`WQAB123` style). It must be unique against `Callsigns.isTaken`.
- Records the holder's name.
- A server-side record keeps frequencies unique and gives moderators something to look up.

### Tiers
Both VHF and low band licenses take upgrades and can have bases (low band repeaters may come later).
Crafting a license with an upgrade adds a **base** (repeater, SCADA master or fire alarm controller) and a **paired
input frequency** for a repeater. Each later upgrade doubles both subscribers and bases.

**Subscribers** are mobiles, portables and bases together (a deliberate simplification): bases count toward the
subscriber number too, and fire alarm boxes and RTUs count as subscribers.

| Tier       | Subscribers | Bases | Frequencies       |
|------------|-------------|-------|-------------------|
| Base       | 10          | 0     | 1                 |
| +1 upgrade | 20          | 1     | 2 (output + input) |
| +2         | 40          | 2     | 2                 |
| +3         | 80          | 4     | 2                 |

There are **no per-radio slots**: radios get no serial numbers and there is no release step. Nothing blocks a
radio past the limit; the FCC app counts the units it hears under a license and flags **OVER LIMIT**.

### The license item
- **Based on a filled map**, so it can go in an item frame. It is drawn as a certificate with `PixelCanvas`, as
  `AwardCertificate` is. It shows the callsign, frequency (and input), tier, holder and issue date.
- **Copiable**: craft it with an empty map. The copy has "COPY" stamped in a corner of the picture and says COPY in its
  tooltip. A copy has its own map picture, not the original's map id.
- The original is the authority. Giving it to someone transfers it. Losing it loses the license.

### What it does
- The callsign goes into the codeplug as the station ID. Commercial repeaters send a CW ID with it.
- The callsign shows in the moderation log and the DF app.
- **Programming is never blocked** (honour system, like real CPS). Writing commercial frequencies with no license
  present brings up a new prompt asking the player to confirm they are licensed for them. The **original** license in
  the computer's **scanner** skips the prompt. A **copy does not**: the prompt still appears. Radios programmed before this update
  keep working.
- A ham radio with a MARS mod transmitting in a commercial band is not blocked. It shows as unlicensed in the FCC app
  and the moderation log.
- Ham callsigns stay self-serve.

### FCC computer app
Hooked to direction finders (touching or on the data wire, as the DF app finds them):
- Watches what the direction finders hear in the commercial bands.
- Shows the license (callsign, holder) for each signal it hears.
- Flags **UNLICENSED** for a transmission on a frequency with no license, with the bearing so it can be hunted.

## Open questions
- Recipes for the boxes, pull stations, controller, key, RTU and master.

## Models (to the bounty list when built)
Fire alarm box, indoor pull station, outdoor pull box, fire alarm controller, fire alarm key, RTU, SCADA master,
license (blank item and its framed look).
