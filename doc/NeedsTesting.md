# Needs Testing

Things that are implemented and pass the automated tests but still need a human at a client (rendering, GUI, audio,
feel). Add to this list whenever a change cannot be fully verified by `test` / `runGameTestServer`.

Work through it with `python tools/needs_testing.py` (a local page: pass, needs work, comments, screenshots), which
writes back here: `- [ ]` not tested, `- [x]` passed, `- [!]` needs work, with `Note (date): ...` comments and
`![caption](testing_shots/...)` screenshots indented under the item. Whoever picks up a `[!]` item fixes it (or moves
it to [todo.md](../todo.md)), then sets it back to `[ ]` for the next playtest.

## Radio fire alarms and SCADA (added 2026-11-10)

`runData` has been run. Everything is in the creative tab after the licenses; nothing has a recipe yet. Get a license
first: `/radiocraft license grant @s vhf 1`. Since 2026-11-11 the box, pull station, panic button, RTU and master have
models from the maintainer's photos (`tools/fire_alarm_assets.py`, `tools/scada_assets.py`); the controller and the
outdoor pull box are still placeholders.
GameTests cover the mesh itself (`RadioMeshGameTests`): what needs eyes and ears is below.

Fire alarm:
- [ ] Place a Fire Alarm Controller and a Radio Fire Alarm Box (rename the box in an anvil first, e.g. "342 MAIN ST").
  Right-click each with the license: chat says the frequency and the box number. Within a few seconds the box checks
  in: right-click the controller shows "Boxes: 1".
- [ ] Put a Pull Station on a wall touching the box (and an Outdoor Pull Box on data wire a few blocks off). Both sit
  right: the wall one flat on the wall, facing out; the pedestal standing.
- [ ] Pull one (right-click): it shows pulled, the box's lamp blinks until the ack, the box gives a short quiet chirp
  (not an APRS burst), and the controller buzzes once a second, turns its screen red and powers redstone next to it.
- [ ] Right-click the controller with paper: a Fire Alarm Report drops out in front, its tooltip "** ZONE 01 FIRE **",
  the day and time, ACCT and R01L1, the box's name. The printer sound is short. No paper: status says PAPER OUT and
  the report prints once paper goes in.
- [ ] Right-click the controller: "Acknowledged", the buzzer and bells stop. Shift + right-click steps back through
  the log in the action bar.
- [ ] Reset the station with the Fire Alarm Key: the controller logs RESTORE and prints it. Break the box: the
  controller logs TAMPER.
- [ ] Models (2026-11-11): the box is a red wall cabinet (Simplex style) flat against the wall it was put on, its
  TROUBLE lamp lit while it waits for an ack; the pull station is small, red with the white PULL DOWN bar and FIRE
  strip, and the bar swings down when pulled. Hit boxes match the models.
- [ ] The Fire Alarm Key on the box opens its inside (square, after the Kidde panel): NORMAL lamp green with a
  frequency, TROUBLE amber blinking while it waits for an ack, ALARM and the zone lamps Z1-Z6 red for pulled stations,
  the LINK meter's needle up when another box or the controller is in range, the plate "BOX 0001  151.xxx". No text
  or control overlaps. TEST sends a test (the controller logs it); RESET puts every pulled station on the box back up. Buttons left to right: DRILL toggle, SILENCE (red), RESET, TEST; labels clear of each other.
- [ ] Bells: with a station pulled, a redstone lamp on the far side of the wall behind the box lights (the box powers
  the wall block). Press the red SILENCE button (2026-11-11, was a toggle; RESET is now the black button beside it): the lamp goes out but the ALARM and zone lamps stay lit until RESET, and the next alarm rings again.
  DRILL lights it with nothing pulled and sends nothing. A lever on any other side of the box is still its zone 0.
- [ ] Panic Button: goes on a wall, on a floor and under a block (under a desk), turned like a vanilla button. Press:
  it stays in, the box sends FIRE for its zone; the key resets it.
- [ ] Range: two boxes on their built-in antennas hear each other out to about 110 blocks; put a box 200 blocks from
  the controller with another halfway: its alarm is relayed (the slip says VIA 1 RELAY). Coax a VHF antenna to one
  end and it reaches about twice as far.
- [ ] Walk away so the middle box's chunk unloads, pull again at the far one: it still gets through.
- [ ] FIRE ALARM program (floppy from the creative tab) on a computer touching the controller or on its data wire:
  boxes with their status, the log newest first, F2 acknowledges, F5 puts the picked box out of service (no bells,
  never MISSING), Del forgets it. The header flashes red while an alarm is unacknowledged.

SCADA:
- [ ] Place a SCADA RTU and a SCADA Master, both set with the license (the RTU says "RTU 1"). The master is two
  blocks: the CRT appears on your left as you face it (it will not place without room); breaking either half breaks
  both and drops one master. The RTU is the low finned data radio. Shift + right-click the RTU's north face: IN; put a
  lever there. Shift + right-click the master's top: OUT; put a lamp on it (redstone comes out of the right-hand
  control panel half only).
- [ ] Right-click either half of the master: its console. The CRT lists the master and each RTU with the six faces,
  the rules on the DISPLAY switch's other position, scrolled with the arrow keys or the wheel; POLL SEC sets 1, 2 or
  3 s an RTU (the PC program's header follows); the six LOCAL I/O toggles set the master's faces OFF/IN/OUT; the POLL,
  TX and COMM FAIL lamps and the POLL OK meter move. Nothing overlaps.
- [ ] Write `COPY 1.N TO 0.U` in a book and quill, use it on the master: "Loaded 1 rules". Flip the lever: the lamp
  follows within a second or two. Quiet chirps from both.
- [ ] `IF 1.N >= 1 THEN 1.S = 15` with the RTU's south face OUT: a lamp on the RTU's south side follows the lever.
- [ ] Break the RTU: after three polls the master's status says COMM FAIL and its top lamp goes out.
- [ ] SCADA program: the master and each RTU with six face columns, values updating, wanted outputs in cyan; Ins adds a
  rule (a bad one gives an error), Del removes the picked one, F3 steps the poll speed 1 to 3 s an RTU (relaying is up to 8 RTUs, the server's
  `mesh_hop_limit`).
- [ ] `/radiocraft license info <callsign>` lists the devices set to it and how many are bases.

## Fire alarm controller terminal, SCADA console TUI, setup without a license (added 2026-11-11)
- [ ] Box programming panel (right-click a box): the cabinet inside with an unlit grey-green 16x2 LCD and five keys (left, up, down, right, ENTER), nothing overlapping. Up/down step through FREQUENCY, CALLSIGN, FACILITY NAME and LOCATION 01, 02... (one per pull station or panic button on its wire). ENTER edits: left/right move the blinking cursor, up/down change the character, typing works, ENTER saves (LCD says SAVED, or BAD FREQUENCY / BAD CALLSIGN), Escape cancels the edit.
- [ ] Shift + right-click a pull station or panic button: "Location 03: KITCHEN" (no number before it is wired to a box); it does not pull it. A named station's alarm shows its name beside ZONE on the controller's screen and on the printed slip.
- [ ] The lit ALARM lamp on the controller and the box's lamp glow in the dark (full bright).
- [ ] The Swan's (20m premium) dial window: the numbers and ticks slide under the red hairline as you tune (14.200 MHz sits on 142).
- [ ] Yellow and White Alarm Pull Stations and Outdoor Pull Boxes: in the creative tab, models in their colours (the bar reads ALERT; the white one has a red handle and bar), wired and pulled like the red ones.
- [ ] Alarm types: on the box's SETUP, each location has a "LOC 03 ALARM" line after its name; ENTER, then up/down (or left/right) steps FIRE, THREAT, DISASTER, MEDICAL, ESCAPE, LOCKDOWN, FLOOD, BREACH, TORNADO; ENTER saves. A pull there shows "ZONE 03 MEDICAL" on the controller and its slip; shift + right-click the station shows the type.
- [ ] Box programming panel: MAIN MENU first (SETUP, STATUS). STATUS shows ROUTE (DIRECT TO CTRL / n HOPS TO CTRL / NO CONTROLLER), SIGNAL (a bar and percent, live) and one NBR line per box or controller it hears (strength, number, location). Left goes back to the menu, Escape too. The LINK meter on this panel moves.
- [ ] LINK meter redrawn (both box panels): cream face, arc scale, red to green band, six ticks, the needle from the pivot.
- [ ] Comparator on a fire alarm box (or behind its wall): 15 while in alarm, still 15 after SILENCE, 0 after RESET.
- [ ] Fire Bell: on a wall, rings (a rapid gong) while powered; a box in alarm rings bells on the wall behind it; SILENCE stops them. Hitbox matches the gong.
- [ ] Wiring into wall devices (box, pull station, panic button on a wall): data wire run up the wall from below, down from above, or up the wall beside it goes straight into the device with no stub sticking out; a wire on the floor beside it runs along the floor and wall. Grey mounting plates behind the box and the pull station (the pull station's, the fire bell's and the wall panic button's cover the whole wall face of their block).
- [ ] Panic button texture is clean on a wall, floor and under a desk (no black specks or smeared texture); data wire meets it on a floor or ceiling along the floor.
- [ ] Outdoor pull box: red head with the indoor station's face and handle, fluted post, base plate, a red lamp on top that glows in the dark; turns to face the player when placed; hitbox fits.
- [ ] Controller: the soft key is SILENCE (was STOP; S still works). It quiets the buzzer and bells; the screen says SILENCED: STILL IN ALARM UNTIL RESET; a new alarm within 5 minutes is logged and printed but does not sound again. Soft keys NEXT, SILENCE, FAST, E line up with their [ ] boxes and are clickable.
- [ ] Controller event screen: TIME SINCE (game clock, HH:MM, or "2D 03:15", JUST NOW) on the line under VIA.
- [ ] SCADA console SETUP page: FREQ, CALL and NAME fields typed straight in (no separate window), Up/Down pick a row, ENTER saves (SAVED, or a BAD FREQ / BAD CALL note), Escape undoes typing; POLL steps with Left/Right or a click; E and the number keys type instead of closing. Tab/Left/Right still change pages.
- [ ] SCADA RTU hitbox matches the case and fins (not a full block).
- [ ] VHF Base Station squelch: a new one comes on SQL 1 (just shut on the hiss); SQL OPEN plays FM hiss that a signal quiets; the knob steps OPEN, 1 (fine), 2 (the old 1) ... 11.
- [ ] J-pole: the feed section on the lower block no longer flickers against the mast.
- [ ] The controller's buzzer is louder (full volume, heard about 24 blocks off (its range is 24 now, was 16)). The SCADA boot logo is the same green as the rest of the text.

- [ ] No license needed: right-click a box or RTU (or E, 3 on the controller; the master's SETUP page): the setup
  screen takes a frequency (150-174 or 30-50 MHz, not the marine channels) and a callsign, and a location name. Bad
  ones are refused in chat. A license used on a device still fills them in.
- [ ] RTU setup: six face buttons, each click OFF > IN > OUT, the level shown; the tooltip explains IN and OUT.
- [ ] Fire alarm controller: the new RC-704 model (cream rack unit, printer door, CRT with blue outline, ALARM lamp
  lit while a box is in alarm); the hit box fits. Its CRT shows the newest event on the block in the world, like a
  computer monitor, within about 24 blocks.
- [ ] Right-click it: the terminal. Event screen as the DMP photo: kind and time boxed top, ACCOUNT boxed, location,
  ZONE boxed, R01L1, NEXT STOP FAST [E] along the bottom (click them on the screen, or N, S, F, E keys), printer
  state and clock bottom right. NEXT steps older, FAST back to newest, STOP acknowledges (the header stops flashing).
  E: the menu, 1-5 (box list with Enter for service mode, setup, clear head jam). Text never runs off the CRT.
- [ ] SCADA console: opening it boots (the logo, then four self-test lines) in about 3 s, then the TUI: tabs POINTS,
  RULES, I/O, SETUP (click a tab, turn DISPLAY, or Tab/arrows). I/O lists the master's six faces with mode and
  level; clicking a row or anywhere on a toggle steps it OFF > IN > OUT (right click back). The block's CRT shows
  the POINTS page in the world.

## Premium HF rigs: the Swan, TS-520R, RC-K2 and RTX-100 (added 2026-11-11)

The Premium (pure crystal) 20m, 40m, 80m and 10m radios have their own model and front panel, after the Swan-350,
TS-520S, Elecraft K2 and Realistic HTX-100 (`tools/hf_gui_proposals/hf_radio_*_pure.py`). Every HF radio now has RIT,
a noise blanker and RF gain in its code (GameTest `PremiumRigGameTests`); only these four panels have the knobs.
- [ ] Each of the four in the world: the model looks like its rig, the frequency is drawn on its screen on the block
  (the Swan's is small: readable?), its TX lamp (and the TS-520R's and RTX-100's RX lamp) lights. Hung from a
  ceiling, it sits right.
- [ ] Each panel: every knob pointer on its knob, switch handles in their slots, the mode and AGC as labelled, no text
  over a control. The K2's and RTX-100's bar meters light segment by segment (S on receive, PO or SWR on the air;
  click the bar or DISPLAY to switch).
- [ ] RIT: switch it on (the RIT key or toggle; its lamp where there is one), turn the RIT knob: the display corner
  shows R+0.35 and a station on the dial moves off pitch while your transmissions stay put (check on a second
  radio). RIT CLR goes back to 0.
- [ ] NB: with redstone sparks or the aurora crackling, the noise blanker cuts most of the crackle. RF GAIN down
  quiets both the band noise and signals.
- [ ] The plain and Cheap 10/20/40/80m radios still open their old panels.

## Commercial frequency licenses and the FCC program (added 2026-11-10)

`runData` has been run. Give yourself licenses with `/radiocraft license grant @s vhf` (or `lowband`, with a tier after
it), or buy a blank one from a journeyman cartographer (expert for the upgrade). The FCC program has no floppy recipe
yet: use the creative tab or the all-apps floppy.

- [ ] Right-click a Blank Commercial VHF License: chat says the callsign, frequency and units, and the blank becomes a
  license in your hand.
- [ ] Hold the license: it shows as a map in the hand? (it is our own item, so it may show as the flat placeholder
  sprite instead). Its tooltip lists callsign, licensee, frequency, units/bases and the grant date.
- [ ] Put it in an item frame: the certificate shows (navy frame, FEDERAL COMMUNICATIONS COMMISSION, the callsign big,
  the lines readable, the seal bottom right).
- [ ] Craft it with an empty map: you get a "(Copy)" with COPY stamped bottom left once it is in your inventory, and
  the original stays in the grid. The cartography table no longer clones a license.
- [ ] Craft an original with a Commercial License Upgrade: once carried it redraws with an INPUT frequency and 20 units,
  1 base. A copy cannot be upgraded.
- [ ] Cartographer trades show the blank licenses (journeyman) and the upgrade (expert).
- [ ] CPS: write a codeplug with a commercial frequency and no license on the scanner: the License Check dialog lists
  the frequencies and asks you to confirm; "I am licensed" goes on to the write. With the original license on the
  scanner glass covering them: no dialog. With a copy on the glass: the dialog says the copy does not authorize.
  Same check when starting F9 program-by-hand and the write-by-hand prompt.
- [ ] FCC program with a direction finder touching the computer (or on data wire): tune with F3 (was F2) or the arrows. A
  licensed frequency in use shows LICENSED and the holder; a commercial frequency with no license that someone
  transmits on flashes UNLICENSED with a beep once, and the bearing shows once the finder locks. The log on the right
  fills with what was heard.
- [ ] Config `commercial_licensing = ADMIN`: a blank license refuses to be claimed; `OFF`: no License Check dialog, the
  FCC program says NOT CHECKED.

## FCC jacket fit and the badge on the player (added 2026-11-09, 1.12)

Run `./gradlew runData` first (the badge tooltip now names the belt slot). Change skins in the game's Skin
Customization (or another account) to try both Steve (wide arms) and Alex (slim arms).

- [ ] FCC jacket on a Steve skin: the body and sleeves sit just over the skin, not a pixel off it like a chestplate; no
  flicker where it meets the skin's own jacket and sleeve layer; the tie and lapels read on the front.
- [ ] FCC jacket on an Alex skin: the sleeves are as slim as the arms (no gap at the sides of the arm), the jacket
  colour all round and the shirt cuff at the bottom of each sleeve.
- [ ] The jacket with leggings on (iron, leather): the leggings' waist does not show through the jacket.
- [ ] Walking, swinging a sword, crouching: the sleeves follow the arms and the jacket the body.
- [ ] The FCC hat no longer covers the eyes: the band sits above them, the brim just over them.
- [ ] FCC badge in the Curios body slot: a small gold shield on the left breast (the wearer's left), upright, not
  mirrored, about 3 pixels across, readable; with no chestplate, with the FCC jacket (over the jacket, not sunk into it)
  and with an iron chestplate. It stays on the chest when crouching and turning.
- [ ] FCC badge in the Curios belt slot: at the front of the waist, just left of the middle on the belt line; over
  leggings if worn; follows crouching.
- [ ] The Curios render toggle for the slot hides the badge.
- [ ] Badge tooltip (after runData) mentions both the body and the belt slot.

## Distress alerting: DSC, DISTRESS and the beacon (added 2026-11-09, 1.12)

- [ ] Marine Radio screen: the new row under the keypad (NAT, ACK, and the red DISTRESS button in its rim) and the top
  line "RADIOCRAFT MARINE"; no text overlaps a key. The DISTRESS cover is shut (red, see-through, DISTRESS readable
  under it); click it: it lifts into the strip above the button; click that strip or right click the button: it shuts.
  Pointing at the button shows your MMSI (nine digits, starting 3).
- [ ] Hold DISTRESS with the cover up: the display shows SEND DISTRESS / HOLD 3, 2, 1 with a beep a second; let go
  early and nothing is sent. Hold the full 3 s: a beep, the radio goes to 16, the display shows DISTRESS SENT, the
  nature and NEXT RPT counting down. With the radio off nothing happens.
- [ ] NAT steps the nature (the display shows "NAT FIRE" for a moment, the tooltip names it); the call carries it.
- [ ] A second marine radio within range (any channel, even 72 or WX1): the two-tone alarm sounds, the display flashes
  DSC DISTRESS, shows the MMSI and nature and X/Z, for about two minutes. Any key stops the alarm; CLR clears it.
- [ ] ACK on the second radio: the first radio's display says ACKNOWLEDGED BY (MMSI) and it stops repeating (wait
  5 min). Holding DISTRESS again on the first sends a cancel: the second shows DSC CANCELLED.
- [ ] Range: with the two radios on antennas far apart the call arrives as far as voice on 16 does; with bare
  connectors only a few blocks. On a Sable ship the coordinates follow the ship.
- [ ] DISTRESS program (craft the floppy, install, cable a marine radio and a commercial VHF mobile by data wire;
  2026-11-09: no picker, it hears through both): the top right reads "DSC: 1 marine radio(s)" and "BEACONS: 1 radio(s)
  on 161.975" (yellow "BEACONS: no commercial radio on 161.975" with the knob on another channel, and the off/none
  wordings); the title shows your MMSI. The map shows your station's
  box and a red flashing diamond for the call (orange ring for a beacon), grey once over; the list columns line up
  under the header; a new alert plays the alarm; F5 acknowledges a call (a beacon gives an error); F6 prints a map
  with the marker and a caption on paper or a map in the scanner.
- [ ] Distress beacon: tooltip says "One use"; right click opens the cover (sound), sneak right click shuts it; hold
  right click 2 s (beeps, bow pull) switches it on: the strobe model lights (other agent's art), the tooltip counts
  down, the bar drains. A commercial VHF mobile on a 161.975 channel hears it (below); a marine radio does not
  (2026-11-09: its DSC is channel 70 only); DISTRESS lists it as BEACON.
- [ ] Handheld direction finder on 161.975 (and the direction finder block on 161.98) points at the beacon in an
  inventory and floating in water. Thrown in water it floats and is still there after 5+ minutes.
- [ ] Let a beacon run out (`/time add` does not drain it: it counts its own ticks; use a short test or wait 20 min):
  it becomes a Spent Distress Beacon and the commercial radio's display shows "<name> SPENT".
- [ ] (2026-11-09) Commercial VHF mobile with a channel on 161.975 (program it in the CPS, knob on it) hears a beacon:
  one short DSC alarm, and its in-world front display shows DISTRESS DETECTED 161.975 big, and under it the beacon's
  X Y Z and "<owner> BEACON" taking turns every 2 s, until cleared: sneak and use the radio with an empty hand clears
  it (the next sneak-use takes the hand mic as usual). A radio on another channel, or the low band set, shows nothing.
- [ ] (2026-11-09) DISTRESS F7 Send SOS: the bottom row shows "F7 SOS  MARINE: ready  PERSONAL: ..." (green when it can
  go, grey with the reason when not: no radio cabled, radio off, one already on). F7 lists MARINE and PERSONAL (one
  that cannot go says why and refuses), then the natures (UNDESIGNATED first), then "SEND MARINE SOS?" Yes/No.
  MARINE: the cabled marine radio goes to 16 and shows DISTRESS SENT with that nature; another marine radio alarms.
  PERSONAL: another commercial VHF mobile on 161.975 shows DISTRESS DETECTED with your name; it repeats every 50 s.
  The bottom row flashes red "... SOS ON THE AIR (nature), repeat in ..."; F8 (asks which if both) cancels after a
  Yes/No and the other radios mark it CANCELLED. Switch the commercial radio off: "PERSONAL SOS STOPPED: its radio
  lost power" within a second.
- [ ] Sounds: dsc_alarm (warble) and dsc_beep are not too loud; subtitles read "DSC distress alarm" / "DSC beep".
- [ ] Handbook: Marine Radios has the three DSC pages; Distress (computer) and Distress Beacon (radios) entries read
  well and their links and recipes work (after `runData`).

## /contest (added 2026-11-09)

- [ ] `/contest start Sprint` (op) announces to everyone; a second start says one is running; a non-op cannot use it.
- [ ] Two players make a two-way voice contact (each hears the other within 5 minutes) on HF SSB and on 2m FM, then
  `/contest stop`: the stopper and both players get a "Sprint" book by RadioCraft. Page 1 has the start/end times,
  duration and counts; the winners page lists the right names (ties all listed); each player's page lists the other
  player, the MHz, the mode (USB/LSB/FM/AM) and "Y.. to Y..". No line runs past the page edge or falls off the bottom.
- [ ] Talking for several minutes is one contact, not one every few seconds. A contact made before `start` does not
  count until the pair talks again after it.
- [ ] Stop with a full inventory: the book drops at your feet. Stop from the server console: nobody gets a book and
  the summary is in the log.
- [ ] Restart the server mid-contest: the contest and its contacts are still there at `stop`.

## Marine radio art (added 2026-11-09, late)

- [ ] Marine Radio screen (use the block): cream case, black front. The red display shows the channel big,
  USA/INT and HI/LO (RX on WX) and DW on the right, and the channel's name under it ("16 DISTRESS", "22A COAST GUARD",
  "WX1 WEATHER"); no text overlaps a knob, key or switch, and every label is readable. PWR, HI/LO and DW switches move;
  TX/RX lamps light; VOL, SQL and CH knob pointers turn (click up, right click down, wheel). The keypad: red 16 goes
  to 16 and again back; 16/9; CH up/down; typing 2 then 2 goes to 22A, 6 then ENT to 6, 3 then 4 shows "NO CHANNEL";
  CLR forgets. SQL shuts the speaker until a signal is strong enough. MIC jack: hold to talk, right click for the
  hand mic.
- [ ] Marine Handheld screen (sneak and use): looks like the old off-white portable (grille, red LED, plate, rubber
  duck, knob and red key on top). The LCD shows the channel number only. VOL knob: its white mark moves; turning it
  down past the lowest switches off, up switches on. Red top key: 16 and back. CH down/up, 16/9, H/L (amber strip
  while on 1 W), DW (green strip while on). Side PTT bar: hold to talk. LED red while transmitting, green on receive.
- [ ] Marine Handheld item: 3D model, the same size in hand (first and third person) and in the inventory as the VHF
  handheld; antenna top left, knob top right, the red key between them. It sits in a desk charger as a 3D radio.
- [ ] Marine Radio block: low cream box with the recessed black front (display left, keypad middle, grille right),
  three knobs standing out, mic hook on the side. The lit channel text sits on the red display and the TX/RX lamps
  light under it, on all four facings and hung from a ceiling.

## Commercial mobile art and panels (added 2026-11-09, late)

- [ ] Commercial VHF Mobile Radio (renamed from "Commercial Mobile Radio": item name, Handbook "Mobile Radios"
  spotlight). Placed, the knob on the head's left shows a grey face with a white mark (it showed a squashed copy of the
  whole front before), and the mic hook on the left side is plain dark grey (same). The head: green LCD in the middle,
  a row of small keys under it (the last two yellow), a yellow stripe top left, TX and RX lamps right of the LCD.
  The channel text sits on the LCD and the TX/RX lamps light over the painted ones (not beside them).
- [ ] Commercial Low Band Radio: 2 px taller than the VHF set, a speaker grille across the top of the head, an orange
  stripe; everything under the grille as on the VHF set, its display text and lamps lined up too. Its outline (hit
  box) is the taller shape. Hung from a ceiling (after runData), its top meets the ceiling.
- [ ] Right click either radio: the new panel, laid out as the block's head (compare with the placed block): the
  badge (VHF / LOW BAND) top left with the MIC jack beside it, the PWR/VOL and CH knobs bottom left, the LCD large in
  the middle with two rows of keys under it (1-8; 9 0 * # A B C D), TX and RX lamps stacked right of it, and under
  them the VHF set's red DISMISS key (low band: "30-50 MHZ FM"); the low band's grille along the top. The case is the
  block's dark grey whatever the dye. No lettering runs into a knob, key or lamp.
- [ ] PWR/VOL: a click turns it up a step, right click down, the wheel over it either way. Down to OFF switches the
  radio off (the LCD goes dark); up from OFF switches it on. 1-10 sets the speaker volume (5 is the old 100%).
- [ ] CH: steps the channel 1-16 and stops at both ends; the wheel anywhere else on the panel steps the channel too.
- [ ] LCD: line 1 the channel name (or who is talking, for a few seconds; the low band shows CH n), line 2 "CH n"
  (low band: LOW BAND) and FM, DIG, ENC or TX on the right, line 3 the frequency. Off: dark glass, no text.
- [ ] * shows the unit ID (or NO ID), # the frequency, for 3 s on line 1. A shows ZONE A; B, C, D show "ZONE A ONLY"
  (a mobile holds 16 channels). Number keys click and do nothing more, as on the handheld.
- [ ] MIC jack: hold to talk (TX lamp, red ring round the jack); right click takes the hand mic off the hook.
- [ ] VHF set on a 161.975 channel hears a distress beacon: the alert shows on
  the LCD in up to four lines (a long line breaks at a space; "DISTRESS DETECTED 161.975" goes over two lines) and
  DISMISS clears it.

## Picking the radio in RIGCTL and APRS (added 2026-11-09)

- [ ] RIGCTL with two radios beside its radio interface: on opening it asks "Which radio to control?" (a radio that is
  off says OFF); the pick is kept after closing and reopening the program and after a reload; R brings the list back.
  With one radio it uses it at once; with none the red line says to put a radio interface and a radio beside it. Not
  asked while connected to a remote host (F6 page and remote driving work as before).
- [ ] APRS with two VHF radios on its radio interface: on opening it asks which radio sends ("All of them still
  receive"); line 3 starts "TX: <radio>." (or "TX: every radio (F9 picks one).") and F9 changes it; the key bar shows
  F9=Radio and fits. A beacon (F5) goes out only on the picked radio's frequency; stations heard on the other radio
  still appear. The picked radio switched off: F5 says it is off.

## Dead air and SSB that follows the voice (added 2026-11-09)

- [ ] Commercial radio module screen: a mode button, SSB/AM on HF (FM shown on VHF, AM forced for dead air). An AM
  module is heard as AM (an SSB rig hears its carrier; a CB hears it), an SSB one as SSB.
- [ ] RIGCTL waterfall, zoomed in: an SSB station is a slab beside its frequency (above for USB, below for LSB) that
  comes and goes with the words, not a solid line; AM shows a steady carrier line with sidebands that swell with the
  voice; FM a carrier that spreads wider when someone talks.

- [ ] A dead air station (FM/VHF) is no longer dead silent: a faint hiss with the odd crackle under its carrier.
- [ ] No dead air stations on SSB bands (random modules pick another program); an SSB one configured by hand sends only
  its kerchunks.
- [ ] Listening to someone on SSB: the S-meter rises and falls with their words; between words the static comes back
  up, and with the squelch set it cuts in and out. AM/FM carriers hold the meter steady.
- [ ] RIGCTL remote: C leaves the host's waterfall scrolling on the client's monitor.

## RIGCTL remote control and network names (added 2026-11-09)

- [ ] A computer left with unsaved work (EDIT text, the book editor, a codeplug in the programmer) keeps its screen
  past the usual 10 minutes, as one playing music does: come back after 15 minutes and the work is still there.

- [ ] A computer that boots plays a short four-note welcome (rising chime) once the boot is done or skipped. New
  computers start on the Program Manager (icons) launcher.
- [ ] RIGCTL banner reads "REMOTE: RADIO@<name> (WIRE)   F6=Connection Menu"; the Remote page says "Connected to ...".

- [ ] Data wire of different colours joins through a junction box (red in, blue out): the devices at either end
  reach each other; the wire visibly connects to the box.

- [ ] While a computer plays a rig (REMOTE audio, or any remote session), the rig itself and its wired speakers are
  silent: voice, static, squelch tail and Morse all come from the computer.
- [ ] On the air through RIGCTL the S-meter shows your voice level; T is hold-to-talk (key repeat does not re-key it)
  and gives a short go-ahead beep when it keys; letting go unkeys.
- [ ] RIGCTL's audio plays only while it runs: Esc out of RIGCTL stops the computer playing the rig (and ends a
  remote session it drove); C closes the screen with RIGCTL left running, audio and waterfall carrying on.
- [ ] Clicking the mode, PWR or SQL on the display changes them (SQL: right click goes down). ? opens the key guide;
  any key or click closes it. The hint line and bottom bar fit.

- [ ] Data wire joins only the devices at its ends: a computer wired to another computer does not reach the radio
  interface (and radios) touching that one. Touching devices still chain.
- [ ] RIGCTL on a computer with no radio of its own says so and opens the Remote page; the host cursor (black on
  cyan) moves with the arrows; LOCAL/REMOTE shows which is set; the help line fits (ends "F6: Remote").

- [ ] Two computers on data wire, same name (or none): opening the second shows NETWORK NAME before the desktop; a
  taken name (any case) says NAME IN USE ON THIS NETWORK; a free one goes to the desktop and shows in the top bar.
  A non-owner sees the "ask its owner" notice and Enter carries on.
- [ ] SETUP's name field refuses a name another computer on the network uses.
- [ ] RIGCTL F6 Remote page: readable in every SETUP colour scheme; L/A/W/C/K/H keys; the host list (name, link,
  code, who, IN USE/OFF); clicking a host twice connects.
- [ ] REMOTE audio at the rig's own computer: the rig is heard at the computer (or its speakers); holding T your
  voice goes out even standing away from the rig; LOCAL as before.
- [ ] Remote session over wire: C drives H's rig (tune, band, mode, scan, waterfall, squelch, memories, PTT); C's
  top line magenta "REMOTE: driving..."; H's red "REMOTE: <player> via <name>"; H's keys refused; F8 at H takes over.
- [ ] Remote audio at C with REMOTE set at C: H's rig heard at C, your voice into H's rig with T; no double audio.
- [ ] Over microwave: MW GOOD/FAIR/POOR in the list; audio slightly delayed, rougher and gappy at FAIR/POOR; the
  session ends at NO SIGNAL.
- [ ] Session ends and the PTT lets go on: D, breaking the wire, H turning remote access off. Closing C's screen keeps
  the session and its audio (only the PTT lets go); the waterfall keeps scrolling on the monitor while you stay within
  16 blocks, with no time limit. The Remote page is F6 (F2 is Minecraft's screenshot key).
- [ ] Handbook: the four new Rig Control pages fit and their links work.

## Repeater loops (added 2026-11-09, late)

- [ ] Two repeaters on each other's frequencies (A in 145.000 out 145.600, B the reverse), a few blocks apart: a call
  through A howls round the pair, louder each time, until the timeout drops them; the server stays smooth. With
  `repeater_loop_protection = true` the call goes through once and both drop after their hang time.

## VHF receiver, power switches, chemical battery (added 2026-11-09, late)

- [ ] VHF receiver scanner, the block (2026-11-09, reworked into an eight-channel scanner; run `runData` first for
  the lang and the ceiling model; previews: `python tools/vhf_receiver_assets.py --preview DIR`): a low wide box,
  black case, chrome front, bail on top; the hitbox fits the case (not the whip), on the floor and hung from a ceiling. No text on it. The
  slide switch handles show up or down as switched, the numeral of the channel it is on lights red (full bright in
  the dark), the SET lamp lights; all line up with the painted slots and window, from every side.
- [ ] VHF receiver scanner, the panel: it matches the block's front (key box with MAN/AUTO, SET and lamp top left,
  VOLUME/SQUELCH/TUNE under it, the window with 1-8 over the eight switches). No text over any control; the knob
  pointers read.
- [ ] VHF receiver scanner, the label above the panel: hovering a switch shows "CH n · frequency"; otherwise the
  channel heard (SCAN while it moves, HOLD with MAN), "SET CH n · frequency" while setting, "No channels on" with all
  down, "No power" on a flat cell. It never covers the panel, at GUI scale 2, 3 and 4 and in a small window.
- [ ] VHF receiver scanner, the scan: click or scroll a switch and it moves; two or more on scan (about 5 channels a
  second), stop on a signal and move on about 2 s after it ends. SQUELCH up: a weak signal no longer stops it. MAN
  holds on the channel; AUTO scans again.
- [ ] VHF receiver scanner, SET: SET in with one channel on lights the lamp, and TUNE (scroll, click, right click;
  shift 25 kHz, control 1 MHz) moves its frequency and you hear the new one; it stops at 144.000 and 174.000. Two
  channels on: lamp dark, TUNE does nothing, the label says why. Try 156.800 (marine), 154.4375 (commercial) and
  146.520 (2 m).
- [ ] VHF receiver scanner, VOLUME and the cell: turning down past the lowest setting clicks OFF (quiet and dark);
  turning up switches on. Only MAN/AUTO and SET have mouse tooltips. Shift right click a cell in and out (a flat alkaline comes out
  dead); the cell drops when the block breaks.
- [ ] VHF receiver scanner, old saves: a receiver from an earlier 1.12 build loads with its memories as the channels,
  the selected ones on (one that was on its dial has that frequency in its first empty channel, on).
- [ ] VHF receiver scanner, the whip: a thin steel rod from the back right of the case up to the top of the block, no
  flicker where the bail's bar meets its arms; hung from a ceiling the whip points down under it instead. With nothing
  coaxed to it, it hears a VHF handheld about as far off as another handheld would (a few hundred blocks in the open),
  not past that; coax a J-pole to it and it hears further (the whip is cut out).
- [ ] Power Switch On/Off and Left/Right: placeholder levers lean the right way; right click and a redstone pulse
  switch them; a radio wired through goes off / moves circuit.
- [ ] Stable Chemical Battery: inert until redstone (fizz), then powers a radio; won't charge; broken while sealed it
  keeps its charge; its screen opens.
- [ ] Antenna switch: a redstone pulse steps to the next output with coax, skipping empty ones.

## Memories, QUICK, handheld art (added 2026-11-09, late)

- [ ] QUICK (every new panel): hold stores the frequency ("QUICK SET"); press jumps there (lit), press again comes back.
  The all-band radio changes band to get there.
- [ ] All-band MEMORY: hold on the VFO, the display shows "MW nn" and what that channel holds (EMPTY or its
  frequency and mode); the dial or UP/DN picks 1-50; press MEMORY to store ("Mnn SET"). Any other key cancels.
- [ ] All-band VFO/MEM: press goes to the memories (corner "Mnn SSB"), the dial steps through stored channels, press
  again back to the VFO where it was; hold on a memory copies it to the VFO ("TO VFO").
- [ ] VHF handheld screen: the battery pack under it is taller (nearer the model); no checkerboard behind VHF FM; no
  dots on the keypad plate.

## Panel fixes, VHF panel, alkaline cells (added 2026-11-09, evening)

- [ ] Squelch (all HF, CB, VHF): it opens on whatever the S-meter reads (band noise or the strongest station) and then
  everything plays; static is never heard without the station under it. The S-meter shows the band noise on receive.
- [ ] Display: the mode marker no longer overlaps the frequency. AM to SSB in one press.
- [ ] Meter on the air: PO/SWR only move while speaking on SSB (or keying CW); AM/FM whenever keyed. No S key.
- [ ] 10X key: the dial steps ten at a time; UP/DN 100 kHz with it, 10 kHz without.
- [ ] 80m lever: clicking walks SSB-CW-AM and back.
- [ ] CW: the static narrows and quiets (the CW filter).
- [ ] VHF Base Station's new panel: offset knob (- S +), TONE up/down, display corner shows offset and tone, squelch.
- [ ] Alkaline cells in the VHF handheld and CB portable (either click direction): the old cell comes back; a flat
  alkaline comes back as a Dead Alkaline Battery (crafts to an iron nugget). The desk charger does not charge a radio
  on an alkaline.

## New HF panels (added 2026-11-09)

- [ ] 10m, 20m, 40m, 80m, all-band: the new panel opens and lines up (display text inside the window, pointers on the
  knobs, handles in their slots, the needle on its pivot and inside the meter face, lamps on their lenses).
- [ ] Knobs (AF, MIC, SQL) turn by scroll and clicks and show their value on hover; the dial tunes by scroll and drag
  and its dimple turns; power, PTT (right click: hand mic), step and band keys work.
- [ ] Mode keys / the 80m lever: SSB, CW, AM (and FM on the all-band) select and light.
- [ ] Meter: S on receive; on the air S/PO shows power, SWR the SWR (high with no antenna).
- [ ] AGC: OFF makes strong stations louder and weak ones quieter, and the static stops ducking; FAST brings the static
  back quicker between words.
- [ ] CB portable extended: the thick section runs 40% up, the black collar, then the thin whip.

## CB controls (added 2026-11-09)

- [ ] 5875 FUNCTION knob: AM / USB / LSB lamps follow it; two 5875s on USB talk; one on LSB hears the other on USB
  only as faint interference; a PC68 on AM hears nothing from a USB 5875.
- [ ] 5875 CLAR: hover shows the offset; an SSB station tuned off by the clarifier sounds pitched up/down.
- [ ] 5875 RF GAIN: turning it down lowers the hiss, weak stations and the RX meter.
- [ ] NB and ANL (5875), NB/ANL (PC68): with redstone switching nearby, the crackle drops (NB more than ANL).
- [ ] PC68 AM/FM switch: FM lamp lights; two PC68s on FM talk; an AM set does not hear the FM one clearly (warble).
- [ ] CB portable in the Curios belt slot: renders on the right hip, antenna up when on.

## Squelch (added 2026-11-09)

- [ ] CB mobiles and the portable: SQUELCH up past what a weak station reads on the RX meter cuts it out (and its
  hiss); back to 0 it plays again. A strong station still opens it.
- [ ] Squelch at or under the band noise (0, or low on a noisy band) hisses on the CB mobiles and the portable (the
  portable's hiss follows you, in hand or on your back); turned past the noise it goes quiet.
- [ ] A CB (or any radio) turns on at once when switched on, no 3 s wait.
- [ ] CB portable in hand: a little bigger than the VHF handheld, held (not floating); third person and dropped too.
- [ ] SSB keyed into no antenna does not blow while silent; talking can blow it. AM (CB) keyed can blow silent (carrier).
- [ ] 20m (bottom-left knob), 40m and HF receiver (top-left knob): hovering the knob shows OPEN / SQL n. Above 0, the
  band static goes silent, and comes up with a station that opens the squelch. The S-meter (comparator) still reads
  stations under the squelch.

## CB rework, CB antenna, batteries, All Applications Floppy (added 2026-11-08, second pass)
- [ ] CB screens are textures now (`textures/gui/cb/`, laid out by `gui_layouts/cb_*.json`, drawn by
  `tools/cb_gui_assets.py`). 5875: less crowded? Knobs, switches and lamps line up with their labels? Needles swing on
  the printed scales? PC68: bigger knobs, the mic plugged in with its cord going round the back, the channel knob in its
  bezel, CH9. Portable: LIGHT (hold it: the channel window lights), PTT bar on the left.
- [ ] Mobiles: the volume knob switches the radio on from OFF and off at OFF, every time, however fast you turn it.
  Turning the channel knob does not switch it on. The 5875's clock shows (screen and block) with a charged battery on
  the wire even while the radio is off, and goes dark with no battery.
- [ ] On the blocks (your models): channel number in the narrow window, clock in the wide one, both needles move (RX
  signal while listening, TX power and SWR/MOD on the air), ON AIR lights the TX_LED cube; PC68 green LED on receive,
  red on transmit.
- [ ] CB portable: click a small NiCd battery onto it in the inventory (survival and creative), or sneak-use with one in
  the other hand: their charge swaps. The creative-tab one comes charged.
- [ ] CB Ground Plane Antenna (placeholder model): placing it puts up five blocks, radials at the foot; breaking any
  part takes it all and drops one. An antenna analyzer or a radio's SWR: about 1.1 on channel 19, 1.8 at 28.0 MHz.
- [ ] Small NiCd Battery is the name everywhere; the Small Alkaline Battery (new texture) shows full, cannot go in a
  charger or charge controller, and powers the weather alert receiver.
- [ ] All Applications Floppy (creative tab): insert it in a radio computer: every program appears, the disk comes back.

## CB radios and grounding (added 2026-11-08)
- [ ] CB portable: the 3D radio in the inventory (does it fit the slot?) and in hand. Sneak and use: the screen shows the radio
  with the channel knob on top, volume and squelch low on the front. Turn the volume up from OFF: in third person
  (F5) the whip pulls out to about three blocks; back to OFF and it goes down. Does it look right in first person,
  third person, on the ground and in an item frame?
- [ ] Two players with CB portables on one channel: hold right click to talk, the other hears it. Both talk at once:
  both voices are heard. Walk apart: the voice gets hissy before it goes, with no background static when nobody talks.
  The portable reaches about half as far as a mobile.
- [ ] 5875 mobile: does the drawn panel read well (clock with AM/PM on the left, meters, red channel LED, ON AIR)? The
  clock shows the game time. Turn the volume to its lowest click: the radio switches off. CHANNEL knob (wheel, click,
  right click). MOD/SWR flips what the right meter shows while transmitting (hold the MIC jack or use the hand mic).
  SPEECH COMPRESSION on MAX: does your voice sound punchier and more even on the far radio?
- [ ] PC68 mobile: panel reads well; CH9 jumps to channel 9; VOL inner knob, SQ outer ring; RX/TX lamp green on
  receive and red on transmit, on the screen and on the block.
- [ ] Block models (placeholders by agy): do the 5875 and PC68 look like the photos at a glance? Is the channel number
  on the block's red window, and the ON AIR / RX-TX lamps in the right place? Ceiling-mounted too.
- [ ] Next channel bleedover: two mobiles on 19 and 20 close together: talking on 20 crackles on 19's audio.
- [ ] Grounding: a 20m radio with a noisy band (redstone near the antenna or a poor solar day): run green wire to a
  ground rod column in mud or clay: the static gets quieter. Use the rod: it says how good the ground is. A rod in
  desert sand reads poor; stacking more rods down improves it. Red wire to the same rod does nothing.
- [ ] Ground fault: put a battery on the green wire run: the rod sparks and the battery drains.
- [ ] Green wire reads "Ground Wire" in the inventory and on Jade.

## Lightning arrestor and battery meter (added 2026-11-07)
- [ ] Lightning arrestor: put one in the coax between a radio and its antenna. The radio still hears and transmits
  through it. In a storm (or `/summon lightning_bolt` on the antenna) the arrestor sparks and cracks, the coax on the
  antenna side can burn, and the radio behind it stays working. Does the placeholder box look acceptable and does coax
  visibly join it on its sides?
- [ ] Battery meter (an analog voltmeter since 2026-11-11): wire it to a battery or two. The dial reads clearly (8 to
  16 V, red/green/amber bands), it faces you when placed, the hit box fits the case. Nothing on its wire: the needle
  rests on 8. A full battery at rest: in the green (12.7). Charge from a solar panel: it eases up into the amber.
  Transmit off the battery: it dips. The needle swings smoothly, not in jumps. Use it: the chat line
  with time to full or empty makes sense? A comparator beside it gives a signal that rises with the charge; Jade shows
  the reading.
- [ ] Handbook: Safety > Lightning has the arrestor page and recipe; Power > Battery Meter reads well.

## 1.12 advancements (added 2026-11-06)
- [ ] The tab: open Advancements (L). A RadioCraft tab with the stone background, Going Ham (20 m radio) at the root
  once a radio is placed. Is the tree readable (getting started, distance under First Contact, modes under Dit Dah,
  computer things under Shack Upgrade, weather under Storm Spotter)? Are the icons right and the titles and
  descriptions clear? Frames: goals (rounded) and challenges (spiky, purple toast) look right? Hidden ones (Moonbounce,
  Out of Band, Bible Study, High Score, Spin the Disk, Numbers Station, The FCC Is Watching) do not show until earned.
- [ ] Two players with handhelds: A talks, B hears it: A gets Is This Thing On? B talks back within five minutes: both
  get First Contact (toast, and a chat line). Far apart on HF (over 1,000 blocks): Skip Day. Through a repeater one of
  you placed: the placer gets Repeater Owner when the other talks through it (offline: at their next login).
- [ ] Single player with a second client (or a friend): Grey Line at dawn or dusk (game time 5:30-7:00 or 17:30-19:30),
  Into the Static during `/rcdebug solar` poor or blackout, Northern Lights hearing anything during `/rcdebug aurora on`.
- [ ] Licensed to Chill on /callsign claim (and at login for a player who already had a callsign). Dit Dah keying CW
  on an HF radio's screen and on the manpack. Picture This when the SSTV program finishes a picture. Bible Study
  opening the Bible (a client request, `sys.secret`). Spin the Disk playing a tune. High Score getting on PILEUP's table.
- [ ] Paper Trail: sign a QSL card, drop it, a second player picks it up: the signer gets it. Card Collector with 20
  cards in an album (adding by right-click in the inventory). Mission Control opening a computer with monitor,
  scanner, radio interface and a speaker beside it.
- [ ] Handbook: Getting Started > Advancements, seven pages: does the text fit each page?

## 1.12 computer batch 3C: net control, awards, QSL cards (added 2026-11-06)
- [ ] Net Control: craft it (blank floppy + lectern), install. F4: name "Tuesday Net", 147.180, FM, your call; the
  header goes green "OPEN since". Ins checks in three stations (one with T=Y, one with Short time=Y); the list shows
  time in, flags TFC/ST. Ins the same call again: "rechecked". O checks one out (OUT time), N steps the roll call
  (arrow; short time first, traffic next), Ctrl+Up/Down moves rows. F5 with paper in the scanner prints a written book
  report; F6 writes NETLOG.TXT (TYPE it). F8 "Close and log": LOGBOOK gains the check-ins (grid column for a typed
  grid). F9 with SCHEDULE installed: "at 20:00" makes a reminder job (open SCHEDULE: it is there).
- [ ] Awards: craft it (blank floppy + item frame). With a LOGBOOK.LOG of a few contacts (FT8 ones, ones filled with
  F6 from a radio, typed grids), the list shows each award with * levels, count/next and a bar; Enter shows worked and
  missing (biome names readable, e.g. "Dark Forest"); F6 records. F5 on an earned award prints a certificate on
  paper: frame the map and look at it (blue DOS window, CERTIFICATE, your call large, the award, the level, a gold
  seal). Does it look good? Try photo paper too.
- [ ] Logbook: the GRID column and the "Grid square" field (bad grids refused). F6 "from a radio" on a station heard:
  the grid fills in from where its antenna was (never coordinates anywhere on screen).
- [ ] QSL Designer: craft it (blank floppy + ink sac). Ins "MYQSL": the editor with the live preview on the right
  (2x). Change each property: callsign, lines, every colour, border styles, a picture from an SSTV/photo .IMG (it is
  shrunk to a small picture, bottom left; side right moves it), back heading, layout table/lines, back lines; Tab
  turns the preview. Esc, F5 "16" with paper in the scanner: 16 cards stack in the output; with photo paper too.
- [ ] QSL card in hand: held like a map one-handed (beside the arm) and two-handed (main hand, empty off hand: big
  in front, both hands showing). Is it readable? Right click a blank card: the fill-in screen with the back preview
  updating as you type; Done keeps it unsigned (tooltip "Not signed yet"); Sign without To complains; Sign locks it.
  A stack of blanks: filling one takes one off the stack. Right click a signed card: it turns over (sneak + right
  click turns a blank one). Tooltip: From, To, date, band and mode, Signed by.
- [ ] Item frame: a card shows its front, the right way up, at each rotation; glow frame bright.
- [ ] A template with a blank callsign, a stack given to a second player with no computer: they write their call in
  ("Your callsign" field) and sign; the front shows it in blue ink.
- [ ] QSL Album: craft it (book, leather, two paper). In the inventory: right click the album with cards on the
  cursor puts them in, with an empty cursor takes the last out; right click it in hand: page with arrows/wheel,
  click the card or Space to turn it, "Take out". Tooltip "N of 48 cards".
- [ ] Fax: a signed card in a radio fax's document slot sent to another fax with paper: it prints a QSL card marked
  COPY on the back. With only empty maps there: a printout of the front. The computer FAX sends a .QSL (scan a card
  with QSL's F6 first) and keeps one received as FAX00001.QSL (Enter opens it in QSL; F5 prints).
- [ ] BBS: a BBS online at W1BTR-1; from another computer QSL F7 sends a signed card to W1BTR-1: the BBS log says
  "QSL CARD SAVED AS MSG n FOR <to>". The station it is for: BBS terminal `R n` shows "[QSL CARD ATTACHED - D n]",
  `D n` sends it; their QSL program says "QSL card received: QSL00001.QSL" and F5 prints it.

## 1.12 computer batch 3B: rig control, waterfall, FT8 (added 2026-11-06)
- [ ] Rig Control: craft it (blank floppy + lever), install it on a computer touching a radio interface with the
  All-Band radio beside it. The display shows the radio's name in the title, the frequency as 28.000.00 MHz, mode,
  band, PWR ON/OFF and RX/TX. Left/Right tune a step, Shift ten, the wheel too; F (or Enter) and "14.074" turns the
  radio's band knob to 20m (look at the radio's own screen); PgUp/PgDn change band; M cycles SSB, CW, FM; P switches
  it off and on; T keys the PTT (TX in red, the radio's TX lamp) and Esc lets it go. A VHF base station says "one
  band" / "one mode"; a commercial mobile says it is a channel radio.
- [ ] Waterfall: with a second station transmitting (a handheld keyed nearby, a CW beacon, an APRS terminal beaconing)
  its column lights up and scrolls down four times a second; stronger is brighter (blue, cyan, green, yellow, red,
  white); the noise shows as faint snow, more in a solar storm. The dial's column has a yellow down arrow on the scale
  and dots down the waterfall; band plan spots (FT8, PSK31, SSTV) show as magenta +. Z zooms (x1, x4, x16, x64) round the
  dial; click a signal to tune to it; Ctrl+Left/Right jump to the next signal. Try every SETUP colour scheme,
  monochrome ones too: the waterfall should still read faint to strong.
- [ ] Walk away from the screen with RIGCTL in front and stay near: the monitor in the world keeps scrolling the
  waterfall (twice a second); 17+ blocks away, or after reopening, it stops pushing.
- [ ] Scan: S with the shown span (or type 145.500 to 145.700 on a VHF radio) steps at five channels a second (SCANNING
  blinks) and stops on a station keyed in that range (STOPPED ON SIGNAL), goes on three seconds after it unkeys; Q
  raises the squelch (S1-S9) so weak stations are skipped; Space holds, X stops. F4 stores the dial in CHANNELS.FRQ
  (open FREQMAN: the entry is there), F5 loads another .FRQ, F3 scans the memories, Up/Down tune them one by one.
- [ ] FT8: craft it (blank floppy + any pressure plate: oak, crimson, stone, gold/iron weighted all work). Two
  computers with HF radios on 14.074 (F6 tunes there) and radio interfaces with callsigns, some distance apart on
  real antennas. The slot clock top right counts each 15 s slot (even/odd; red when it is ours). A: F3 calls CQ; at
  the end of the slot B's list shows "CQ <A> <grid>" in green with its SNR and offset; click it on B. Watch the
  exchange run by itself (grid, report, R-report, RR73, 73), the yellow TX lines, "Logged ..." on both, and both
  LOGBOOK.LOG files gaining the contact (mode FT8, reports like -12, the grid in the notes). Open LOGBOOK on a
  computer while it logs: the list reloads.
- [ ] FT8 sound: a third radio tuned to 14.074 in USB hears the slow eight-tone warble for 12.6 s of each slot, at the
  offsets the stations use (F7 changes ours; the passband strip shows where). Does it sound like FT8?
- [ ] FT8 reach: a station too far for RTTY or SSB (they garble or are lost) still decodes on FT8 at -15 to -20 dB.
- [ ] Scheduler's new FT8 CQ job (FT8 installed): it calls CQ for ten minutes and logs what answers.
- [ ] Band plan: the new FT8 markers on 80/40/20/10/2m in the Band Plan Sheet and BANDPLAN do not crowd 14.070 PSK31
  unreadably (a server with an old config keeps its old marker list; delete `band_plan_markers` to get them).
- [ ] Item art: the Rig Control (red) and FT8 (violet) floppy labels and their Program Manager icons (placeholders,
  see ModelDesignBounty).

## 1.12 weather alert generator and receiver (added 2026-11-06)
- [ ] Models: the generator (grey cabinet) and the receiver (small white radio with a whip) look right on all four
  facings, in the hand and in the inventory; the generator's green READY lamp lights while it is on the air and its
  red TX lamp while it sends; the receiver's red lamp lights when it flashes. Placeholders, see ModelDesignBounty.
- [ ] Generator status: right click it (and look at it with Jade) as you build: no APRS station, "Hears 0 weather
  beacons", too close together (two weather stations near each other), no antenna, then "On the air on 162.500
  MHz". Use three H19s with weather stations at least 150 blocks apart, beaconing, and an H19 (or a computer running
  APRS) touching the generator that hears them.
- [ ] With Another Weather Mod, bring a tornado over the area (`/awm approach tornado 2`, or `/awm spawn`): the receiver
  near it flashes red (every half second), plays the 1050 Hz tone (5 s, not too loud, not clipped), then reads the
  alert: "Radio craft weather alert. Tornado warning for the area. The storm is three hundred twenty blocks to the
  northwest, moving east. Take shelter now. End of message." Listen for gaps or overlaps between the spliced words
  (the timing comes from each clip's length), odd pronunciation, and whether the voice is loud enough at 10-20
  blocks. The chat line "WEATHER ALERT: ..." reaches players within 24 blocks.
- [ ] One redstone pulse per new alert (a redstone lamp or a piston beside it flicks once), not again when the
  generator repeats the alert a minute later; again when a severe storm becomes a tornado warning.
- [ ] Right click a flashing receiver: it stops flashing and talking ("Alert silenced").
- [ ] Battery: shift + right click with a Small Alkaline Battery puts it in (and gives back the old one); shift + right
  click empty-handed takes it out; breaking the receiver drops it. Right click shows the battery %, days left, the
  generator it hears and the last alert. A battery under 10 % blinks the lamp once every 10 seconds; a flat one is
  dark and hears nothing.
- [ ] Antenna: a receiver far from the generator hears nothing on its built-in whip, but does with a J-pole or Ringo
  on coax. Two generators: it follows the nearer one.
- [ ] A marine radio (or any radio that tunes 162.500 MHz, WX6) hears the generator's data burst every two minutes,
  and after a new warning the 1050 Hz tone.

## 1.12 computer batch 3A: data floppies, screensaver, scheduler (added 2026-11-06)
- [ ] Data floppy, list view: a blank floppy in the drive, Tab to the files, F3: the pane says "Files on A:" and
  "No files on the floppy yet". F3 back to C:, F8 on a file: "copied to A:". The drive line reads "Data disk"; eject
  it (sneak + right click): the item is now "Data Floppy" with "1 files, ... of 1.44 MB" in its tooltip. F8 again on
  the same file asks before replacing it.
- [ ] F4 labels the floppy (FIELD DAY): the tooltip shows "Label: FIELD DAY", the drive line "Data disk FIELD DAY".
  Put it in a second computer: its files are there; F8 copies one to that C:. Enter on a picture on A: shows it;
  Enter on a text file on A: says to copy it to C: first. Delete every file and clear the label: it stacks with blank
  floppies again.
- [ ] File Manager (SETUP: Program Manager, then Tab): the same keys; the window title shows A:\ or C:\.
- [ ] Prompt (SETUP: command line): `A:` changes the prompt to `A:\>`; `DIR`, `DIR A:*.TXT`, `COPY C:NOTES.TXT A:`,
  `COPY *.LOG A:` (one line per file, then "n file(s) copied"), `COPY A:NOTES.TXT` (to the current drive), `TYPE
  A:NOTES.TXT`, `DEL A:*.*` (asks Y/N), `REN A:X.TXT Y.TXT`, `LABEL A: SHACK`, `LABEL` (says the label). `COPY
  LOGBOOK.EXE A:` says programs cannot be copied. With no floppy: `A:` says not ready; with a program's disk, `DIR A:`
  shows its program, write-protected.
- [ ] Screensaver: `/give @s radiocraft:floppy_saver`, INSTALL it. SETUP has a Screensaver page: Start after 1 min,
  each of the three styles (Starfield; Callsign: the radio interface's callsign, or the computer's name with none;
  Band waterfall: drifting signals, one keying "CQ DE <call>" in Morse), Preview (Enter). Wait a minute without
  touching anything: it comes on; a key, click, wheel or mouse move wakes it and that key does not type anything.
- [ ] Walk away from a computer whose screensaver shows (or let one with SCHEDULE installed sit a minute after you
  get up): the monitor in the world shows the screensaver moving (about five times a second within 16 blocks).
  Watch its power: it draws 2 FE/t (a quarter of 8) while the screensaver shows (Jade, or a battery's drain).
  With MUSIC playing it never comes on. SAVER (or SAVER.EXE at the prompt) blanks the screen at once.
- [ ] Scheduler: craft it (blank floppy + bell), install it on a computer touching a radio interface with a VHF radio
  on and a callsign. Ins: Station ID, `every 1`: a minute later a handheld on that frequency hears "DE <call>" in
  Morse; the log at the bottom says "done"; the countdown runs down. Space turns it off; F5 runs it now.
- [ ] WEFAX job (WEFAX installed, a chart saved with WEFAX F-keys as WXRADAR.IMG): `at 6:00` world time, then `/time
  set 23900` (5:54): the chart goes out at 6:00 and a second computer with WEFAX receives it. Beacon job `every 2`:
  another station's APRS shows the beacon every two minutes.
- [ ] Reminder `at 20:00` with text "Net on 146.52 tonight": at 20:00 world time players within 16 blocks get
  "[<computer name>] Net on ..." in chat, and the computer's screen (any program) shows "Reminder: ..." on its status
  line. With a callsign in To it goes as an APRS message instead (APRS installed). A guest (not the owner) can look but
  not change the jobs. Copy SCHEDULE.DAT to a floppy and onto another computer with SCHEDULE: the jobs come along.
- [ ] Item art: the Screensaver and Scheduler floppies' label colours (violet, teal) and their Program Manager icons
  (placeholders, see ModelDesignBounty).

## 1.12 live monitor, CPS by hand, MUSIC volume and sound, knobs on the radio, 64 channels (added 2026-11-06, evening)
- [ ] Live monitor: open the computer, start MUSIC, Close (C). Within 16 blocks the monitor in the world keeps moving:
  the top bar's clock, the time and bar, the visualizer (about 5 times a second while music plays, twice a second
  otherwise). Walk past 16 blocks, or look away, and it stops updating (it shows the last frame); come back and it
  moves again. Another player's computer still shows the model's DOS face. No flicker, no wrong colours on the
  monitor or in the GUI/hotbar afterwards; with the debug log on, "Radio computer monitor: ... live redraws" once a
  minute gives the cost (expect well under 1 ms).
- [ ] Live monitor with a SETUP colour scheme (amber, green): the monitor shows the scheme, as the open screen does.
- [ ] CPS read by hand: no mobile wired, F5: a "Read a Radio by Hand" box, Continue closes the screen and the action
  bar says to right click the computer holding the radio. Right click the computer (or monitor) with a Basic
  handheld in the main hand, not sneaking: the screen opens again on CPS with its channels, "Read the ...". The
  handheld's own screen must not open, and nothing is talked on it.
- [ ] Same with the Upgraded handheld (64 positions, zones A-D marked in the list) and with a Commercial Mobile item
  in hand (it must not be placed). Also with the radio in the off hand and the main hand empty.
- [ ] CPS write by hand: edit a channel, F6, Continue, right click with a handheld (sneaking too): CPS opens again,
  "Programmed the ...", the radio has the channel. A refused write (a digital channel into the brick) opens CPS with
  the channel marked.
- [ ] While waiting, the monitor shows the red "WAITING FOR A RADIO" line. Wait 60 s without clicking: the action bar
  says CPS stopped waiting; a click then just opens the computer.
- [ ] With a mobile wired to the computer, F5/F6 offer "A radio in your hand" first, then the wired mobile.
- [ ] F9 "Program radios" (the fleet mode) still works as before (click writes, sneak reads).
- [ ] MUSIC volume: - and + (and keypad - +) change the "Vol" bar and percentage on the Now playing panel, heard at
  once (within a moment); 0% silent but the tune goes on; the key bar fits in one row.
- [ ] MUSIC track list: while a tune plays, scroll the list with the wheel, Up/Down, PgUp/PgDn: it stays where you put
  it; Enter plays the picked tune; the playing tune keeps its mark; when a tune ends, the list moves to the next.
- [ ] MUSIC from the computer: walk round the computer and turn your head: the music comes from it (left/right),
  fades with distance, silent and stopped past 24 blocks. Master and Jukebox/Note Blocks sliders change it at once;
  with either at 0 it is silent and comes back where the tune has got to.
- [ ] MUSIC pause/resume (Space) carries on from the same place; S stops at once; Left/Right change tune at once.
- [ ] MUSIC under lag (a heavy chunk load, /tick freeze is not it: try a big world edit or a slow device): no skips for
  hitches under about a second; a longer stall makes a gap and then it carries on from where it was.
- [ ] MUSIC visualizer and clock follow what is heard (no lead or lag you can see), also after pause.
- [ ] MUSIC through speakers: put a desk speaker (or a large/premium one) against the computer: the music comes from
  the speaker instead (fuller; premium clean and wide). A wall speaker or loudspeaker on the computer's data wire
  plays it where it stands (loudspeaker a loud honky horn heard to 64 blocks). Two speakers play in step (no echo).
  Remove them: within a second it comes from the computer again.
- [ ] Commercial handheld screens: no separate dials above the radio any more. Scroll over the drawn channel knob and
  volume knob, drag them up/down, click and right click: they turn, stop at the ends, the readout ("CH 7", "VOL 6",
  "OFF") shows over the knob and fades 1.5 s after; the mark on the knob moves. Hover shows a short tooltip.
- [ ] Upgraded handheld keypad: A, B, C, D go straight to zones A-D (the LCD shows ZA..ZD and CH 1-64); * shows the
  unit ID and # the frequency for a few seconds. The basic radio is unchanged.
- [ ] A handheld or .CPG programmed with 40 channels before this still works (zone D blank).
- [ ] The low band mobile is called "Commercial Low Band Radio" (item, block, JEI, Handbook) after runData.

## 1.12 commercial handheld models and screens (added 2026-11-06)
- [ ] Basic Commercial Handheld (the maintainer's 3D model): looks as he drew it, undyed grey, in first and third
  person (both hands), in the inventory and hotbar, dropped on the ground, in an item frame and on the head; the
  speaker panel, the three lamps and the bottom seam show without flicker (they are thin parts laid over the front).
- [ ] Upgraded Commercial Handheld (new placeholder, GP300 style, "RC-300" plate): same places; the long antenna stays
  inside the inventory slot; undyed it is black; the knobs, lamp, PTT and belt clip sit right.
- [ ] Both, dyed with each of the 16 dyes (craft with a dye; several dyes mix): only the case changes colour, the
  grille, plate, LCD, keypad, lamps, knobs, antenna and battery stay as drawn. White, light grey, yellow and black are
  the ones to check closely. The preview sheet is in `doc/testing_shots/commercial_handhelds_dyes.png`.
- [ ] Both in a desk charger: they stand in the cradle with their own 3D model and dye colour (no longer the charger's
  own radio); a marine handheld still shows the charger's radio.
- [ ] Sneak-use the basic radio: the screen shows its front (the same art, in its dye colour) with the antenna and the
  two knobs above it as seen from the top. Channel knob 1-16 and power/volume knob OFF-10: left click turns one step,
  right click one back, the wheel turns it, dragging around it points it at the mouse; both stop at their ends (the
  gap at the bottom does nothing). OFF switches the radio off (the blue lamp goes out, nothing heard), up from OFF
  switches it on. TX (red) and RX (green) lamps light when talking (right click outside the screen) and hearing. No
  other buttons (power, +/-, PTT bar and zone button are gone).
- [ ] Sneak-use the upgraded radio: front with the LCD and keypad; the same knobs. The LCD shows channel name (or the
  caller), "ZA CH1" and the mode, TX while talking, dark when off. Keypad: **B** zone down and **C** zone up (stop at
  A and C, the knob kept: ZB CH20 from ZA CH4); **A** shows the unit ID and **D** the frequency for 3 s; the number
  keys only click. Hovering a knob or A-D shows what it does. The lamp on top lights red/green.

## 1.12 performance fixes: music cushion, alphabetical programs (added 2026-11-06)
- [ ] MUSIC: play a tune, then make the game hitch (open a big inventory, fly fast into new chunks, alt-tab): the tune
  carries on without skipping. Short stalls under about 0.4 s are not heard any more.
- [ ] MUSIC visualizer: the bars move with what you hear (a drum hit and its bar together), not ahead of it; at the end
  of a tune the bars follow the last notes out; pause stops the bars and they sink.
- [ ] MUSIC: Stop, Next and starting another tune still cut the old one off at once (the bigger buffer is dropped, not
  played out).
- [ ] Programs are listed alphabetically by their name (case does not matter), built-in and installed ones mixed, in the
  classic list, the Program Manager icons, RC-DOS `DIR` and INSTALL's "installed" list. Installing a program puts it in
  its alphabetical place, not at the end.
- [ ] Opening the computer: the first open after starting the game shows the screen within a second (with `debug.log`,
  look for "Radio computer screen: first frame" and "Radio computer request open took" lines for the timings).

## 1.12 launcher views, SETUP, colour schemes, ABOUT, built-in STUDY (added 2026-11-06)
Needs `runData` first (new lang; the License Study floppy and its recipe are gone; the computer recipe's diamond is a
diamond block).
- [ ] SETUP (in the shell's list, or type SETUP): four pages on the left; Enter or Right opens one, Esc/Tab goes back,
  Esc again exits. Clicks work on the page names and the options.
- [ ] SETUP Launcher: pick Program Manager, Esc out: the shell is a framed "RC-DOS Program Manager" window of icons
  with names under them; arrows and the mouse move the highlight, Enter or a double-click runs, the wheel and PgUp/PgDn
  scroll (26 programs is two screens), Tab shows the File Manager (files list; Del/F7/F5 work), Tab back.
- [ ] Icons: all 26 look right, sit centred above their names, never show through a dialog (Del on a file in the File
  Manager, say), and recolour with the scheme. Placeholder art (`tools/computer_icons.py`).
- [ ] SETUP Launcher: pick the RC-DOS prompt: a black screen, `C:\>`. Try HELP, DIR, DIR *.TXT, DIR/W, TYPE a .TXT,
  COPY A.TXT B.TXT (again: "already exists", then /Y), REN, DEL B.TXT, DEL *.* (asks Y/N), PRINT, TIME, VER, CLS,
  a typo ("Bad command or file name"), an uninstalled program's name, RTTY / EDIT NOTES.TXT (Esc from the program
  comes back to the prompt), up/down history, Tab completion, PgUp scroll back, Esc clears the line, EXIT gets up.
  SETTINGS opens SETUP to switch back.
- [ ] SETUP Colours: moving through the list shows the whole screen in that scheme (the swatches beside each name are
  its real colours); Enter keeps it; Esc without Enter puts the kept scheme back. Open every program in Green Phosphor
  and Amber and check it is readable (hint lines, highlights, red errors, the key bar's red hot keys, input fields,
  games, the band plan bars, the MUSIC visualizer). Pictures (SSTV, WEFAX, photos, the ADS-B scope, DF map) keep their
  colours. The monitor in the world shows the scheme too.
- [ ] SETUP Password: as the shell's old F8 (owner sets; empty removes; another player is told only the owner or an
  operator can). The shell no longer takes F8 and its key bar no longer shows it.
- [ ] SETUP Name and Clock: a name and the clock show at the right of every program's top bar (and the music note
  still fits); world time follows the day, real time your clock, 12-hour shows "1:05p". DIR shows the name as the
  volume label.
- [ ] As a second player (not owner, not op): SETUP shows "Only the owner or an operator..." and every change is
  refused with a message; the owner's changes appear on the second player's open screen at once.
- [ ] ABOUT: the banner, the version, the text scrolls (keys and wheel), the website line.
- [ ] STUDY is in every computer's program list with no floppy; the floppy is gone from JEI/creative; the Handbook's
  License Study entry ends with a "Built In" page; the Radio Computer recipe needs a diamond block.

## 1.12 music player fixes: shuffle, arrow keys, visualizer, speaker sound, 20 new tunes (added 2026-11-06)

Run `./gradlew runData` first (new and changed lang lines: the key bar, "(shuffle)"), then restart the client.

- [ ] MUSIC key bar: `Space=Pause  S=Stop  ← →=Back/Next  R=Repeat  H=Shuffle  C=Close  Esc=Leave` fits on the bottom
  row with room to spare; the two arrows are drawn as thin arrows (shaft and head), not as `?` or boxes, and in red
  like the other keys. Is "← →=Back/Next" the wording wanted (asked for "Next/Back=← →"; the key goes before `=` in
  every other program's bar)?
- [ ] MUSIC: Right arrow (and N) plays the next tune, Left arrow (and B) the one before; with nothing playing they play
  the tune picked. The Up/Down list keys still move the highlight.
- [ ] MUSIC shuffle: H shows `(shuffle)` in cyan at the right of the "Now playing" line (with `(repeat)` beside it when
  both are on). At the end of a tune the next comes from the shuffle, never the same twice in a row, every tune once
  before any repeats, and it goes on round (without shuffle the list still plays down once and stops). Right skips to
  a shuffled tune; Left goes back through the tunes heard. It stays on after Esc, Close and reopening the computer,
  until the game is closed. Repeat (R) still wins over shuffle.
- [ ] MUSIC visualizer: now 24 bars, a spectrum (bass on the left, treble on the right). With ordinary tunes all the
  bars move, not just the left two; they jump with the music and fall back smoothly, sink when paused or stopped,
  and keep up with the sound (about a tenth of a second late at most).
- [ ] MUSIC sound: it should sound like small cheap PC speakers: no deep bass, no sparkle on top, a little forward in
  the middle, peaks a touch rounded, but not harsh, crackly or distorted. Compare a bass-heavy tune (Neon Flames,
  Aftermath) and a piano one (Fur Elise). Is the effect too much or too little? (`SpeakerFilter`: 280 Hz high-pass,
  4.5 kHz low-pass, +3.5 dB at 1.5 kHz, soft clip.) Loud tunes are no louder than before at full volume.
- [ ] MUSIC playback rework: the first tune still starts after a short pause, later ones at once; pause/carry on,
  stop, a new tune while one plays, Esc/Close and walking away all still behave; no clicks between tunes; the time
  and bar match what is heard; game sounds keep working. The volume sliders (Master, Jukebox/Note Blocks) change
  the level within a tick, even in the middle of a held note.
- [ ] MUSIC: the 20 new tunes (Airship to Aftermath, after the classical ones) each sound right on Java's built-in
  instruments: no tune all on piano that should not be, no missing drums, sensible balance. Drop any that sound bad
  (remove its line in `tracks.txt`, its file and its credit).

## 1.12 music: 16 CC BY-SA and CC BY tunes (added 2026-11-06)

No lang change; nothing to regenerate.

- [ ] MUSIC: the list now has 48 tunes. The 16 new ones (Town 03 to Opening 03, after Aftermath) each sound right on
  Java's built-in instruments: Beau Buckley's RPG pieces (Town, Snow, Beach, Desert, Plains, Swamp, Opening), Ideka's
  rock guitar (Backstab, Hellin), five from the Ultimate MIDI Pack (You're Winner, Happy Destruction, Pagliacci's Going
  to Town, World of No Sorrow, A Compact Hell: guitar-heavy, check the distortion guitar is not harsh through the
  speaker filter), Chestnut (lo-fi) and The Adventure Begins (orchestral). Several render near full scale, so listen
  for clipping at full volume. The composer column shows each author (Beau Buckley, Ideka, Lippeth, ...). Drop any that
  sound bad (its line in `tracks.txt`, its file and its credit in `doc/Credits-Music.md`); never edit the files.

## 1.12 computer apps 2: RTTY, Packet BBS, Propagation Predictor, Repeater Directory, License Study (added 2026-11-06)

- [ ] Craft the five floppies (blank floppy + note block / chest / clock / redstone repeater / lectern); each has its
  label colour and installs with INSTALL.
- [ ] RTTY: two computers on HF radios with radio interfaces (14.080 MHz): typing shows in the yellow type-ahead buffer
  and drains at about six a second while the far end prints it; TX/RX lamp on the station line; Backspace takes back
  unsent characters; Ctrl+X drops them; Enter ends a line. F3/F4/F5 macros with F10's DX call; F9 edits them (
 ends
  a line); F6 sends a .TXT; F7 saves RTTY0001.TXT; F8 clears; PgUp/PgDn and the wheel scroll. A Teletype Machine and
  an H19 copy the computer and the computer copies them. Weak signals: misprints look like real RTTY (E/A, UE for 73).
  Listen to the tones: they should still sound like RTTY.
- [ ] Packet BBS SYSOP page: list columns, unread `*`, traffic log colours, read view scrolling, N/B compose form then
  editor (^S posts, Esc asks to discard), Del confirm, F7 setup (SSID, online); a non-owner is refused delete/setup.
- [ ] Packet BBS TERMINAL page: F7 connect dialog, typing and Enter, answers print below, PgUp/PgDn and the wheel,
  F8 clears. Call a hosted BBS from an H19 and from an APRS handheld: acks stop the retries, answers arrive as messages.
- [ ] PREDICT: the grid fits 80 columns: colours, lower-case dimmed hours, the day/night row, the legend on rows 22-23;
  Tab and the tab clicks, the input field and Enter; F5 with a second player online who has a callsign, and with APRS
  stations heard; F6 print and F7 save; the red error lines (no receiver, unknown callsign, bad input); the 20 s
  refresh keeps the grid. Compare a prediction with a real 20m skip contact by day and by night.
- [ ] REPEATER: columns line up at 80 columns, the key bar fits, F3 sort cycle, the Add/Edit form (7 fields) and its
  errors, the delete confirm, Space marks, the status line. In game: tune a VHF radio on a radio interface (or touching
  the computer) to a repeater's output, key the repeater, F5: it appears with callsign, offset and tone; distance and
  bearing match the world. F7 export to CHANNELS.FRQ, then "Open FREQMAN" opens it; F6 prints a readable book.
- [ ] STUDY menu: the columns (Qs, Seen, Known, Pass) and the detail box (mastered bar, aptitude colour). S picks a
  subelement. Card page: long questions and answers wrap clear of the key bar; clicking an answer; A-D and I; green for
  right, red for a wrong pick, the verdict and "comes back after" lines; Enter for the next card; the session counter.
- [ ] STUDY exam (E): Left/Right/PgUp/PgDn and the wheel move between questions; the answer sheet row; F10 confirms
  with the unanswered count; the pass/fail box; the review marks and W jumps to the next wrong one; Esc mid-exam asks.
  Progress (P): the table and bars; R resets after a confirm. Optional: put a custom pool JSON in
  `config/radiocraft/license_pools/`, `/reload`, and it appears in the menu.

## 1.12 open-question fixes (added 2026-11-05)

Data already regenerated (`runData`); restart the client (lang, packets, screen layout, structure templates).

- [ ] Commercial radios ship blank: a new Basic/Upgraded handheld, Commercial Mobile and Commercial Low Band Radio from the
  creative tab have no channels (the display and handheld screen show a blank/dead position, no receive, no TX).
  Program one with CPS and it works. CPS F8 asks to "erase ... back to factory blank" and leaves it empty.
- [ ] Band Plan Sheet and BANDPLAN: no "Server-wide net" tick at 7.060 any more (an old server config that still has
  the line drops it too); APRS 144.390 and the other markers are still there.
- [ ] Radio computer password: in the shell the key bar shows `F8=Password`; F8 asks for a new password (typed as
  stars). Close and reopen the computer: after the boot it shows a "Password" box; a wrong one says "Wrong password"
  and clears the field; the right one opens the shell where you left it. Escape on the box closes the screen. The
  monitor in the world shows the password box while someone is at it. An operator is never asked. A second
  (non-op) player who did not place it gets "Only the computer's owner ..." when they press F8. F8 with an empty
  field removes the password. Break and replace the computer: still asks.
- [ ] Solar Weather with an Ionospheric Processor touching the computer (Ionosonde on its coax) and no radio: works,
  source "processor". With no Ionosonde yet it says the processor is offline.
- [ ] SSTV and WEFAX: F-key send asks "Send NAME on the air? Everyone in range receives it." (Yes/No), no callsign box;
  a second computer in range receives it.
- [ ] Find a new ham shack (`/locate structure radiocraft:ham_shack`, and a village one): the radio is off and the
  large battery empty; charging the battery (or a new power line) and switching on brings it up on 146.520.
- [ ] Moderation computer: a player issued a badge (`/radiocraft fcc badge <player>`) shows `[FCC]` after their name in
  Live (talker), Archive (talkers column, detail talkers and hearers) and Access (admins).
- [ ] Real-World Interface screen: a full-width "I hold an amateur licence and will give my own callsign" button
  under the Power row (tooltip explains); after a click it greys out as "Licence acknowledged: your voice can go out"
  and stays so after reopening and relogging. The status panel, lamps and meters moved down a row and still line up.
- [ ] Real-World Interface, op only (default): a non-op's Power button is greyed, and a forced press says "Only an
  operator can change or switch a Real-World Interface". An op can switch it.
- [ ] Real-World Interface audio (real hardware): a player who has not clicked the licence button talks on a station in
  range: nothing keys the real radio. After the click it does. Standing within 6 blocks of the block you hear what goes
  out to the real radio quietly from the block (the monitor speaker); further away nothing.

## 1.12 playtest fixes 2: MARS recipe hidden, Radio Cloner, CPS by hand, unit IDs, ADS-B display (added 2026-11-05)

Data already regenerated (`runData`); restart the client (new item, packet, lang, recipe, models).

- [ ] Recipe book: the MARS Mod recipe never appears (search "mars", even holding wire and crystals); the install
  recipe neither. JEI: the item is in the list, but "R"/"U" on it shows no crafting recipe for it, and U on an HF radio
  does not list the MARS install. The Handbook's MARS Mod page still shows the recipe.
- [ ] Radio Cloner: in the creative tab after the upgraded handheld; placeholder sprite; it glints once loaded. The
  tooltip says empty, or what it holds (model, source radio, channels and contacts, keys left behind), and how to use it.
- [ ] Cloner on a placed commercial mobile (right click): reads it (green action bar line, a high beep), the mobile's
  screen does not open. Right click a second mobile: "Cloned into ...", its display shows the new channels. Sneak +
  right click a mobile: reads that one instead. Sneak + use in the air: "Cloner cleared".
- [ ] Cloner with a handheld in the off hand (cloner in the main hand, use in the air): reads it; then swap in other
  handhelds and use again: each is written. An upgraded handheld's channels into a basic brick with a digital channel:
  red "cannot take this clone: CH n: ...", low beep, nothing changed.
- [ ] Cloner on a VHF ham handheld and a VHF base station: the dial (frequency, tone, offset) copies; handheld to
  handheld also the memory channels (check the handheld screen's memory mode). A ham clone into a commercial radio
  and the other way round are refused; a marine radio says its channels are fixed.
- [ ] CPS: F9 turns on "Program radios": a red banner line under the list with a `[ Close ]` button. C or the button
  closes the screen. Opening the computer again shows CPS still in the mode.
- [ ] Program radios: holding a commercial handheld, right click the computer (and the monitor): the radio is written,
  action bar "Programmed ...", a beep, and no screen opens. Sneak + right click: the radio is read into CPS (the
  channels show next time the screen opens), no screen opens. Works with the radio in the off hand and an empty main
  hand. A mobile radio held as an item is written, not placed (watch for a ghost block flicker).
- [ ] Program radios ends: Esc out of CPS, or start another program from the shell: right clicking with a radio opens
  the screen again. Cut the computer's power (`radios_need_power` on): yellow "stopped programming" message. Leave it 5
  minutes: same, "5 minutes unused".
- [ ] CPS F4 (unit ID) and F7 (contacts: pick a line, edit ID and alias, blank alias deletes, "(add a contact)"): the
  status line shows the unit ID and contact count. With a unit ID set, programming radios by hand goes 1200, 1201, ...
  (CPS's F4 value follows). Save and reopen a .CPG: ID and contacts kept.
- [ ] Unit IDs on the air: two upgraded handhelds (or mobiles) on an analog channel: keying up starts with a short
  data "brrrp" (about 0.15 s, replacing the first of the voice) heard on the other radio. The receiver's display shows
  "ID 1234", or the alias from its contact list, while the other talks and about 4 s after. On a digital channel no
  burst, the ID still shows; on an encrypted channel a radio without the key shows the ID over the scrambled audio.
  The basic brick shows nothing (no display) but its ID shows on others. Is the burst too loud or too harsh?
- [ ] Moderation computer: a commercial radio's transmission shows its mode as "FM ID 1205" (or "DIG ENC ID ...").
- [ ] Tooltip of a programmed commercial handheld: "Unit ID n".
- [ ] FREQMAN F5 now only lists wired mobiles; the scanner glass no longer takes radios (shift-click a handheld into
  the scanner: it stays in the inventory).
- [ ] ADS-B receiver: now a full block whose whole front is the display, edge to edge, square (dark green with a
  ring when off). Switched on with power: the live picture fills the face, square, with rings clipped at the edges and
  targets out to the corners; not mirrored, right way for all four facings, no z-fighting with the dark face.

## 1.12 playtest fixes 1: music, solar radio, 40 channels, ADS-B tube (added 2026-11-05)

Run `./gradlew runData` first (new lang lines), then restart the client.

- [ ] MUSIC: play a tune, press Esc: back at the shell, the tune plays on and a yellow note shows at the right of the
  top bar (also inside other programs). Click the note: the music page comes back, on the track playing.
- [ ] MUSIC: open another program (EDIT, say) while it plays: the music carries on, the note shows in its title bar,
  and Esc from the music page opened by the note goes back to that program.
- [ ] MUSIC: press C or click `[ Close ]` (top right of the "Now playing" box): the screen closes and the music keeps
  playing. Open the computer again: the music page is in front, on the track playing.
- [ ] MUSIC with the screen closed: walk away; it gets quieter from about 6 blocks and stops at about 24 (coming back
  does not restart it). Changing dimension, logging out, breaking the computer, or cutting its power (with
  `radios_need_power` on) stops it. It plays down the list while closed. The computer draws power while it plays.
- [ ] MUSIC: the volume fade (walking away) and the volume sliders now apply smoothly to the sound itself, held notes
  included (music player fixes, 2026-11-06).
- [ ] SOLAR: the maintainer's setup (computer, radio interface, VHF radio on with an antenna, APRS working): the solar
  page and forecast now come up. Switch the radio off: "The radio is switched off...". Take the antenna off: "The
  radio has no antenna...". Remove the radio: "No radio found...".
- [ ] Upgraded commercial handheld (sneak + use): a "Zone A" button under the knob; clicking it goes A, B, C, A. The
  knob shows 1-16 within the zone; the display shows the name, "ZA CH1" style zone and channel, and the mode on the
  right. Zone C knob 9-16 shows "-- NO CH --" and will not transmit. The basic handheld has no zone button.
- [ ] CPS with an upgraded handheld read by hand (F9, then sneak + right click the computer holding it; the glass is gone since playtest fixes 2): 40 rows (the list scrolls, A/B/C marks at channels 1, 17, 33),
  status "x of 40 channels", the line under the list says the zone and knob. Write channel 40 and select it on the
  radio (zone C, knob 8). A basic handheld or a mobile still shows 16 rows.
- [ ] CPS: open a 40-channel .CPG with a 16-channel radio read: a red message says how many channels were left out.
- [ ] FREQMAN: send 20 channels to a wired mobile: "This radio holds 16 channels: mark fewer". (Handhelds are no
  longer reachable from FREQMAN since playtest fixes 2: open the list's channels in CPS and program by hand.)
- [ ] Tooltips: the upgraded handheld says "x of 40 channels programmed"; the channel line shows 17-40 in zones B/C.
- [ ] ADS-B receiver, switched on with power and an antenna, screen closed, standing within 32 blocks: its tube shows
  the live green scope (rings, steady targets, trails, tags; no sweep). It
  faces the right way for each facing, is not mirrored (east on the right, north up), and sits on the face without
  flicker. Switch it off or cut the power: the tube goes dark (the model's own). Walk past 32 blocks: no scope. Tags
  legible up close? (96 x 96 texture, the computer program's pixel font.)
- [ ] ADS-B tube with its own screen open by another player: both see the scope; no stutter with several receivers.

## 1.12 step 1: bands, band plan, MARS mod, Handbook (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first. Take the Band Plan Sheet and the MARS Mod from the creative tab (after the APRS module).

- [ ] Band Plan Sheet: the item shows its placeholder texture (paper with coloured stripes) and the tooltip "Right click
  to read...". Right click opens a paper-coloured sheet: title, hint, then seven bands lowest first (80m, 40m, 20m,
  10m, Low Band, 2m, VHF Comm) each as a bar of coloured segments with black marker ticks and the marker labels
  wrapped underneath, the band edges in small print, and a two-column legend at the end. The wheel, arrows, Page
  Up/Down, Home and End scroll it; nothing draws outside the sheet. Hovering a bar shows the frequency, its use and
  any marker there. Try GUI scales 2-4 and a small window.
- [ ] Edit `band_plan_markers` in radiocraft-server.toml (add "7.200|Test"), save, reopen the sheet: the marker shows.
  A bad line (e.g. "abc") is corrected by the config loader (check the log for what it did) rather than crashing.
- [ ] MARS Mod: its tooltip has the grey line and the red "Contraband" line. Craft it with an HF radio (any crystal),
  the all-band, a QRP rig, the VHF base station, a VHF handheld and the manpack: the result keeps its settings,
  charge and memory channels and has a red "MARS mod fitted" tooltip line. A receiver or repeater does not craft.
  JEI/EMI may not show the shapeless install recipe (it is a special recipe, like the APRS module's).
- [ ] Place a modded HF radio, break it: it drops with the mod (tooltip still red). A plain one drops plain.
- [ ] HF radio: tune below the band bottom with the dial (20m stops at 13.913, 40m at 6.925): the screen's frequency
  shows, with an orange "OOB" in the top right. Press PTT: the frequency turns into a red "OUT OF BAND", no TX lamp,
  nobody hears you. The world display's status line also reads OUT OF BAND. With a MARS mod: TX works there and the
  corner says "MARS" instead. CW keying out of band without the mod does nothing.
- [ ] VHF base station and handheld: the dial goes from 143.000 to 174.000. On the handheld LCD, PTT out of band shows
  "OOB" in the status box instead of TX, and the radio is not heard. DTMF out of band sends nothing. With a MARS mod
  155.000 works and a second handheld on 155.000 hears it.
- [ ] HF manpack: the FREQ knob tunes past the band edges; out of band the window shows OOB, and holding the screen PTT
  shows OUT OF BAND in red. With a MARS mod it transmits there.
- [ ] Splatter: a MARS-modded radio talking 13.950 MHz is heard as splatter (no voice) on a radio 15 kHz away, which
  an in-band signal would not reach.
- [ ] The Patchouli book is called "ARRL Radiocraft Handbook" (item name, title page, landing text). New pages: Band
  Plan (Propagation) and MARS Mod (Radios); Bands & Range has a Commercial bands page with a working link.
- [ ] The mod list's Config screen names the new settings: the two commercial band sections, "Band Plan" (Band Plan
  Segments / Markers) and "MARS Mod" (MARS Mod Splatter).

## 1.12 step 2: commercial radios (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first. The four radios are in the creative tab after the VHF handhelds. Two players (or two
clients) with Simple Voice Chat for the audio checks.

- [ ] Basic Commercial Handheld: placeholder item (grey brick, antenna, two knobs, LEDs). Tooltip: charge, "Channel 1:
  MURS 1", "5 of 16 channels programmed", the grey description line. Sneak and use opens the top panel: CHANNEL knob
  with < and > (and the mouse wheel), PWR, VOL - and +, RX and TX lights, a PTT bar. No display on this one.
- [ ] Upgraded Commercial Handheld: the same with a green display showing the channel name and "CH1 FM" (or DIG / DIG
  ENC). It shows "-- NO CH --" on an unprogrammed position.
- [ ] Two players on MURS 1 with commercial handhelds hear each other (hold right click to talk; the PTT bar works too);
  a ham VHF handheld on 151.820 hears them too. The TX light is red while talking, RX green while hearing.
- [ ] Turn the knob to 7 (unprogrammed): nothing heard, PTT does nothing.
- [ ] `/rcdebug commercial hand set 2 TAC|155.5|=|DIG|0|42|H` on the upgraded handheld (held). Another upgraded handheld
  with the same line hears clean voice with no static at all, even at the edge of range, then it drops out with a
  short robotic warble (walk away slowly). One with key 43 hears garbled digital noise that follows the syllables. A
  ham handheld or base station on 155.500 hears a harsh buzz. Is the buzz too loud / too soft? Is the scramble
  convincing?
- [ ] `/rcdebug commercial hand` lists the channels; the key on channel 2 reads -1 (hidden).
- [ ] Commercial Mobile Radio (now Commercial VHF Mobile Radio) and Commercial Low Band Radio: placeholder model (tinted box with a front panel, display,
  knob). The display shows the channel name (low band: "CH 1") and "1 FM 151.8200" when on; the TX/RX lamps light
  left of the display (lamp positions are guesses: say if they float). Right click opens the panel (PWR, CH -/+, VOL,
  PTT, display, lamps; replaced 2026-11-09 by the panel in "Commercial mobile art and panels"). Sneak and use takes the hand mic; the cord draws. Sneak and scroll steps channels.
- [ ] Mobiles talk to the handhelds on a VHF antenna (J-pole) with power; the low band mobile on a dipole with long
  arms (30-50 MHz) to another low band mobile.
- [ ] Dye: craft each radio with red dye: the handheld item, the mobile item, and the placed mobile turn red (the
  placed block re-tints at once). Break and place a dyed, reprogrammed mobile: it keeps the colour and its channels.
- [ ] A commercial handheld in a desk charger charges; the charger shows its own placeholder radio (not the handheld's
  model), and with power on it hears its channel.
- [ ] The moderation computer's recording of an encrypted call plays the clear voice.
- [ ] Handbook: the new "Commercial Radios" page in Radios reads well, and its spotlights show the items.

## 1.12 step 3: marine radios (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first. The Marine Handheld and Marine Radio are in the creative tab after the commercial
radios. Two players (or two clients) with Simple Voice Chat for the audio checks.

- [ ] Marine Handheld: placeholder item (dark grey, yellow collar, blue-grey display). Tooltip: charge, "Channel 16:
  Distress, safety and calling", "High power (5 W)", the grey description line. Sneak and use opens its front: a big
  "16", "HI USA DISTRESS", 156.800, CH -/+ (and the mouse wheel), 16/9, H/L, DW, PWR, VOL -/+, TX/RX lamps, PTT bar.
- [ ] The 16/9 key: from 68 it goes 16, then 9, then back to 68. H/L shows LO; DW adds "DW" to the display.
- [ ] Two players on channel 68 with marine handhelds hear each other (hold right click to talk). A ham VHF handheld
  on 156.425 hears them. No "OUT OF BAND" anywhere. On WX1 the radio will not key (TX lamp stays dark).
- [ ] 1 W versus 5 W: walk apart until 5 W gets noisy, switch the talker to LO: it should be clearly weaker (shorter
  range). Is the step right (1 W is set to 0.45 of 5 W strength on the handheld, 0.2 of 25 W on the base)?
- [ ] Dual watch: listener on 72 with DW on, one talker on 72 and one on 16. While 16 talks, the listener hears 16
  only; about a second and a half after 16 stops, 72 comes back.
- [ ] Marine Radio: placeholder block (white box, dark front, display, knob, mic hook). Right click opens the panel
  (PWR, CH -/+, 16/9, H/L, DW, VOL, PTT, display with channel, status line, frequency; "16" in the corner on dual
  watch). The display on the block shows the channel big and "HI USA ..." small; TX/RX lamps light right of the
  display (positions are guesses: say if they float). Sneak and use takes the hand mic; sneak and scroll steps channels.
- [ ] Marine Radio on a J-pole with power talks to the handhelds. Put one on a Sable ship (`-PwithSable`): the hand
  mic and the radio work while the ship moves.
- [ ] Channel 26 (duplex): a marine radio transmits on 157.300 and listens on 161.900; a second ship on 26 does not
  hear it. `/rcdebug marine <pos>` shows rx/tx.
- [ ] Break a marine radio set to 22A on LO and place it again: still 22A, LO.
- [ ] Server config `marine_channel_plan = INTERNATIONAL`: channels lose the "A", WX channels disappear, more duplex.
- [ ] A marine handheld in a desk charger charges, and with power on it hears its channel.
- [ ] Handbook: the new "Marine Radios" page in Radios reads well, and its spotlights show the items.

## 1.12 step 4: camera and photo paper (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first (recipes, lang). Camera, Photo Paper and Color Photo Paper are in the creative tab after
the Band Plan sheet. A second player helps for the shutter sound and the fax check.

- [ ] Placeholder textures: camera (black body, chrome top, big blue lens, red button), photo paper (sheets with a
  grey picture corner), color photo paper (a coloured corner). Tooltips read well.
- [ ] Hold right click with the camera (color paper in the inventory): the hand disappears, the outside of the middle
  square darkens, white corner marks, a centre cross, "1.0x" bottom left, "COLOR" bottom right, the hint on top. In
  third person (F5) the player holds it up like a spyglass.
- [ ] Scroll while aiming: 1.0x, 1.5x ... 3.0x and back; the hotbar does not change. The view narrows smoothly enough?
- [ ] Left click while aiming (changed 2026-11-06: was let go of right click): a click, no arm swing, and within a moment a "Photo" map shows exactly what the viewfinder framed, at that zoom. No
  hand, hotbar or viewfinder marks in it. Right way up, not mirrored, colours right? Letting go of right click only
  lowers the camera (no photo); the viewfinder stays up after a shot, and a second left click after the cooldown takes
  another. Left clicking a mob or block while aiming does not hit or break it. Hint on top reads "left click to shoot".
- [ ] With B&W photo paper: the viewfinder says "B&W" and the photo is greys only. With both kinds, paper in the off
  hand is used first.
- [ ] Try day, night, under water, the Nether, F1, a GUI-scale change and an odd-shaped window: always the middle
  square, never stretched. Fabulous graphics: water, stained glass and clouds still in the photo.
- [ ] No paper: "No photo paper" and the camera does not come up. Straight after a photo: nothing until the cooldown
  (3 s, the cooldown sweep on the item) is over.
- [ ] The photo in an item frame, held (map view), copied at a cartography table (still "Photo", "Taken by ...").
- [ ] Radio fax: the new PHOTO slot (right of MAP; its frame is drawn by code, does it line up with the panel art?)
  takes either photo paper by hand, shift click and hopper. Send a color photo: the LCDs say PHOTO; with color paper
  loaded the far end prints the same photo on it; with B&W paper a grey print; with no photo paper an empty map.
- [ ] Photo cap: set `camera_photo_cap = 2` on a server, take two photos as a non-op survival player; the third is
  refused with a gold chat line and a [Choose photos to delete] link. The link lists the photos oldest first (map
  number, day, date) with [Delete]; Delete asks again, [Yes, delete it] deletes: that photo (and its copies, in frames
  too) goes blank and says "Photo deleted" in red; the next photo works. Creative players and ops are not limited.
- [ ] Multiplayer (dedicated server, `runServer`): another player hears the shutter click; the photo arrives for the
  photographer only. Watch the server log while taking photos quickly: no errors, no kicks.
- [ ] Server config `camera_enabled = false`: "Cameras are switched off on this server", nothing sent.
- [ ] Handbook: Digital > "Camera and Photos" reads well (three text pages, two crafting pages).
- [ ] Shader packs (Iris/Oculus, if installed): does the photo still capture something sensible? (Not required.)

## 1.12 step 5: radio computer, monitor, scanner, floppy disks (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first (blockstates and block item models, lang, recipes, loot tables, tags). The computer,
monitor, scanner, blank floppy and every program's floppy are in the creative tab after the photo paper.

- [ ] Placeholder models: monitor (putty CRT with a blue DOS screen and a keyboard), computer (putty tower with drive
  bays), scanner (flatbed with a paper tray), floppy disks (black disk, label coloured per program, blank one white).
  All turn with the placer, no missing textures, no z-fighting; the monitor sits flush on the computer.
- [ ] Right click the monitor on a computer: the screen opens with a BIOS count, RC-DOS loading, then the shell. Any
  key skips the boot. The fan whirrs. Reopened within ten minutes: no boot, same program and text as left.
- [ ] Text mode looks right: 80 x 25 cells, letters centred in their cells (monospaced), box lines join up at the
  corners (single and double), shading and the scroll bar thumb, the blinking cursor, faint scan lines. Try GUI
  scales 1-4 and a small window: it scales in half steps and never runs off the window.
- [ ] The E key types an "e" (does not close the screen); Escape leaves programs and dialogs, and in the shell closes
  the screen. Ctrl+Y deletes a line in EDIT. Tab moves between the shell's lists. Mouse clicks select, a second
  click opens, the wheel scrolls.
- [ ] EDIT: type, save (Ctrl+S; F2 is the screenshot key, step 6 moved it; asks for a name, adds .TXT), reopen (F3 shows a file list), new (F4), unsaved-changes
  question on Escape and F3/F4. A 30K document saves and reads back whole (it goes up in two parts).
- [ ] SYSINFO shows the serial, disk use bar, floppy, scanner and paper counts, callsign and the devices wired by
  data wire (put a radio and a radio interface on the wire).
- [ ] BANDPLAN: seven bars in the plan's colours, markers under them, the cursor reading out frequency and use,
  legend at the bottom. Compare with the Band Plan sheet.
- [ ] INSTALL: lists the floppies in your inventory; picking one puts it in the drive (it leaves the inventory);
  "Install" shows the copying bar, then the program is in the shell. A programmed-but-unwritten program (any floppy
  for now) opens a "not ready in this version" box. Del removes it. Eject gives the disk back. Using a floppy on the
  monitor or computer also inserts it; sneak-right-click the computer ejects. Breaking the computer drops the disk.
- [ ] Break the computer and place it again (survival, needs the loot table): files and installed programs are back.
- [ ] Scanner: right click opens the lid (glass, paper, out slots, inventory below lines up with the slots). In the
  shell's Files list, F6 with a map on the glass makes a .IMG; Enter opens it in the viewer (right way up, right
  colours); F5 prints it on color photo paper (a "Photo"), on an empty map (a "Printout"), on photo paper (B&W).
  A book on the glass scans to a .TXT; EDIT's F5 prints a document as a written book, one paper per page. A print
  left in the output tray jams the next ("Paper jam").
- [ ] The monitor in the world shows the screen you left on it (stretched over the glass), lit in the dark; another
  player's monitor shows the model's own blue screen.
- [ ] Two players at one computer at once: both work; files saved by one show in the other's list after a refresh.
- [ ] Handbook: the new "The Radio Computer" category (three pages) reads well; icons and crafting pages show.
- [ ] JEI does not draw its item list over the computer screen.
- [ ] Power (added at the first build): with `radios_need_power` on, a computer with no power wire opens to a dark
  screen reading "No power: run a power wire to the computer" and only Escape works. Wire it to a charged battery:
  it boots again and works. Pull the battery while at it: the screen goes dark; put it back: it boots.

## 1.12 step 6: office programs on the radio computer (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first (lang). Craft or take from the creative tab the Book Editor, Logbook, Frequency
Manager, Radio Programmer and Solar Weather floppies and install them with INSTALL. Have a scanner beside the
computer, a commercial handheld or two, a commercial mobile, and a VHF or HF radio with an antenna.

- [ ] F2 no longer does anything on the computer but take a screenshot (Minecraft's key): EDIT, BOOKEDIT and CPS save
  with Ctrl+S, the shell renames with F7. F11 is never used.
- [ ] Every program's key bar fits on row 24 at 80 columns, and the column headings line up over their lists.
- [ ] Forms (logbook contact, frequency, CPS channel, book title): Up/Down/Tab move between fields with the cursor
  showing in the field, a click on a field moves there, Enter is OK, a wrong value shows in red under the fields
  and the form stays up. The "pick one" lists (stations heard, which radio) scroll and pick with Enter or a double
  click.
- [ ] BOOKEDIT: write a few pages (F9 adds, F7/F8 turn, F10 deletes), set the title (F12), save (Ctrl+S, .BOK),
  reopen (F3). The side panel's line count goes red past 14 lines; compare with the printed book (does a page that
  says 14 lines really fit?). F5 with 70 pages asks for volume 1 or 2; volume 1 needs a full stack of 64 paper.
  F6 reads a written book from the glass. Opening a .BOK from the shell's file list opens BOOKEDIT.
- [ ] LOGBOOK: Ins adds a contact with today's UTC date and time filled in; Enter edits; Del deletes; the list stays
  in time order; a second contact with the same call says "worked 1 times before". Talk on a handheld near a base
  station wired to the computer, then F6: the station is listed with your callsign, frequency, FM and a report;
  picking it fills the form. F7 writes LOGBOOK.ADI; open it in EDIT and check it reads as ADIF.
- [ ] FREQMAN: add a repeater (TX 146.34 for 146.94, tone 100.0), a simplex channel and a receive-only one (TX "RX");
  the line under the list names the band plan segment. Space marks, Ctrl+Up/Down moves, F8 sorts. F5 with a
  commercial mobile wired to the computer sends the marked channels from the position you type; the mobile's knob then
  finds them. A USB entry is refused with a message. F6 reads the mobile's channels into the list.
- [ ] CPS: a handheld read by hand (F9 Program radios, sneak + right click the computer; was the glass before playtest fixes 2), 16 rows (factory MURS channels). Edit a channel to DIG with key 321 and
  readable N, F6 writes; F5 again shows ****. Change the name and write: the radio still talks encrypted with radios
  keyed 321 (test with a second handheld programmed with key 321 readable). Ctrl+S saves a .CPG; load it and
  program a different blank handheld by hand: refused for the hidden key. A frequency outside the model is
  refused in the form. A mobile wired by data wire is found (with two radios, a "which radio" list). F8 resets.
- [ ] (Changed in playtest fixes 2.) The scanner lid no longer takes commercial radios on the glass.
- [ ] SOLAR: with nothing wired, "No Receiver"; wire a powered radio with an antenna (or an Ionospheric Processor):
  the solar page shows the event, noise bar, band table in colour (now/day/night), VHF, aurora, sporadic E and the
  next event near the end of a spell (`/solarweather forecast blackout` for the red warning). Tab shows the forecast
  (with Another Weather Mod: outlook and storms). Up/Down scroll, F6 prints, F7 saves SOLAR.TXT / FORECAST.TXT. It
  refreshes on its own every 20 seconds.
- [ ] Handbook: the four new pages in "The Radio Computer" (book editor and logbook, programming commercial radios,
  frequency manager, solar weather) and the link from "Commercial Radios" to the programmer page.

## 1.12 step 7: radio programs on the radio computer (added 2026-11-04, UNBUILT when written)

Run `./gradlew runData` first (lang). Install APRS, WEFAX, Radio Fax, SSTV and Direction Finding from their floppies.
Build two stations a little apart: each a radio computer (monitor on top) touching a radio interface (callsign set),
the interface touching a VHF base station with an antenna, both on the same frequency. Have a scanner beside one
computer, a radio fax machine and a WEFAX receiver for the far end, and a direction finder with modules.

- [ ] Each program's station line (row 1 or 2) is green with the callsign and frequency when the interface and a radio
  are there, yellow naming what is missing otherwise (no interface, no radio on, APRS: no VHF radio).
- [ ] APRS: F5 beacons and the other computer (and an H19 on its station) lists this station with distance and
  bearing; N writes a message (to a call, or ALL), it shows SENDING then SENT when acked; with the far radio off it
  goes RETRY 1, 2... then FAILED. A message in shows NEW and a count on the MESSAGES tab, beeps the PC speaker and
  flashes on the status line. MAP draws the stations; F6 sets comment, symbol, auto (beacons on the server's timer
  with nobody at the computer); F7 sets the interface's callsign; F8 clears. Column headings line up.
- [ ] FAX: F3, pick a picture, type the far machine's callsign: the LCD goes CALLING, SENDING PACKET n/30, SENT; the
  machine prints it on a blank map (or photo paper). A text file and a .BOK print there as written books. From the
  machine fax the computer: it answers (auto answer on), the inbox lists FAX00001.IMG/.TXT; Enter opens a picture
  (F5 prints it) or the text in EDIT; Del deletes. F7 off: calls go unanswered. With a fax machine on the same
  station the computer leaves calls to it ("Fax machine answers"). F4 hangs up. F6 scans the glass and faxes it.
- [ ] SSTV: F3 sends the picture picked to all; the other computer shows it COMING IN line by line (half a minute) and
  keeps it as SSTV0001.IMG. Weaken the signal (move apart, turn an antenna) and lines come out as noise. F4 caption:
  top and bottom text in big outlined capitals (small for long captions), saved as a new file; send it. F6 scans a
  map or photo from the glass; F5 prints. L goes back to the live picture. The picture does not cover a dialog.
- [ ] WEFAX: with a radio with a real antenna on the interface, F4 (WX REPT) brings in the radar chart and then the
  forecast, line by line, with the coast station's tones from the radio; both keep as WFX files. An Ionospheric
  Processor's bulletin and an alert come in too. F7 draws a radar or forecast chart of here into WXRADAR/WXFCST.IMG;
  F3 sends a picture as a grey chart; a WEFAX receiver with AUTO on and paper prints it (line by line, about 20
  seconds), and the other computer's WEFAX keeps it.
- [ ] DFIND: with the direction finder touching the computer (or on its data wire): F5 switches it on, Left/Right
  tune, PgUp/PgDn change band, F7 types a frequency. Key a radio on the frequency: the meter moves, modules lock,
  the bearing shows. F6 types a bearing from elsewhere (F8 removes the last): the fix shows on the left. MAP: the
  terrain with the bearings (solid, dashed spread), our station yellow, typed cyan, microwave green, the fix a red
  cross in its circle. 3D: the scene turns slowly and smoothly; a tight fix is a tall spike, a poor one a low mound;
  with one bearing only the wedge. F3 saves the search; SEARCHES lists it; Enter shows it on the map, Esc returns.
- [ ] Monitor mirror, scaling and the image layout (pictures inside their boxes) at a couple of GUI scales.
- [ ] Handbook: five new pages in "The Radio Computer" (APRS, radio fax, SSTV, WEFAX, direction finding) and the AUTO
  line in the WEFAX receiver page.

## 1.12 step 8: secret programs, loot, the ham shack (added 2026-11-05, UNBUILT when written)

Run `./gradlew runData` first (lang). Take the four secret floppies (Snake, Pileup, Music Player, Bible) from the
creative tab and install them on a radio computer.

- [ ] Floppies: each secret disk's tooltip has its file (SNAKE.EXE, PILEUP.EXE, MUSIC.EXE, BIBLE.EXE), its line and a
  gold "A rare disk" line. The Pileup disk is named "Pileup Floppy Disk" (no "Falling Blocks" left anywhere).
- [ ] SNAKE: Enter starts; the board is 36 x 20 square cells inside a green double frame, the snake green with a
  yellow head, the food blinking red. Arrows and WASD steer (two quick turns between steps both happen; a turn back on
  itself is ignored), typing WASD does not leak anywhere, it speeds up as it eats, the wall and its own body end it.
  GAME OVER shows the score and the high score table; a top-ten score is red in it. P pauses; closing the screen
  pauses. Exit and run it again: the table is still there (SNAKE.DAT in the shell's files). Two players at one
  computer share the table.
- [ ] PILEUP: the band (12 x 20) on a black spectrum display with faint dots, pieces drawn as `[]` in their colours
  and a grey `::` ghost where the piece will land. Left/Right move, Up or X turn clockwise, Z back, Down drops a row,
  Space slams. A full row disappears with a yellow flash of the frame and counts a QSO; 8 QSOs move up a band
  (the frame's title and the log: 160m, 80m, 40m...) and it falls faster. The next caller shows at the top right.
  The pile reaching the top ends it with GAME OVER and the high score table (PILEUP.DAT). Does it look and play like
  its own game, not like Tetris? (Ten pieces of 3 to 5 cells, 12 wide.)
- [ ] MUSIC: twelve tunes listed with composer and time. Enter plays the one picked (a short pause the first time while
  the synthesizer opens); the bar and the time move, the green arrow marks it, the 16 meters jump with the notes.
  Space pauses and carries on, S stops, N and B skip, R repeats one; at the end of a tune the next one plays. The
  loudness follows Options > Music & Sounds: Master and Jukebox/Note Blocks (0 is silent; a change takes effect on the
  next notes). Closing the screen or Esc stops it. Each tune sounds right (instruments, tempo): Entertainer, Maple
  Leaf Rag, Fur Elise, Rondo alla Turca, Mountain King, Arabesques, Greensleeves, the Bach pieces, Belle qui tiens.
  If the computer has no sound device, the "Now playing" box says the sound card does not work (no crash).
- [ ] MUSIC on Linux and macOS (Java's software synthesizer opens its own audio line beside OpenAL): it plays, and
  game sound still works while it does.
- [ ] BIBLE: opens at Matthew 1 under "THE GOSPEL ACCORDING TO MATTHEW, CHAPTER 1", verse numbers yellow in the
  margin, long verses wrapped under their text. Up/Down/PgUp/PgDn/wheel scroll; Left/Right turn chapters, on from
  Matthew 28 to Mark 1 and back. F3 "John 3:16" opens John 3 with verse 16 highlighted near the top; "Mk 4", "luke",
  "jn 1:1" work; "Revelation 1" says there is no such place. F4 picks a book then a chapter. F5 "mustard seed" lists the
  verses (Matthew 13:31...), Enter opens one in its chapter, Esc goes back to reading, F6 shows the list again.
- [ ] Loot: in a new world, open ancient city, stronghold library, mansion, trial chamber vault, end city, igloo,
  shipwreck, dungeon, outpost, desert and jungle temple, mineshaft, buried treasure and village house chests: now and
  then one has a secret disk (about 1 in 8 for the first group, 1 in 20 for the second, 1 in 33 for village houses).
  Holding one unlocks the hidden Handbook page "Disks Never Sold" (Radio Computer category).
- [ ] Ham shack, standalone: `/locate structure radiocraft:ham_shack`, go there. A timber house (cobblestone footing,
  log corners, flat spruce roof) with a J-pole on the roof, a lightning rod, glass windows, a door on the south. Inside:
  battery, VHF base station on 146.520 MHz (on, powered, its coax up the north wall to the J-pole), a radio computer
  with monitor (Logbook and Frequency Manager installed), a bookshelf, a chest of radio parts (sometimes a secret
  disk), a chair, a bed and a pot plant. It sits on the ground, not floating or buried, on flat land in plains, forest,
  taiga, snowy, savanna, meadow and cherry biomes.
- [ ] Ham shack in villages: find villages (plains, taiga, snowy, savanna, desert) until one has the shack (about
  one village in six or seven): the house is in that village's wood (desert in sandstone), its door on a street with a
  doorstep, a villager in it, and whichever way it is turned the radio faces into the room, the coax reaches the J-pole
  (the radio shows an antenna and plays on 146.520) and the wire reaches the battery.
- [ ] Handbook: the Programs page's rumour line mentions old chests and the shack; "Disks Never Sold" is hidden until
  a secret disk is in the inventory, then shows a spotlight page for each disk.

## 1.12 step 9: ADS-B beacon, receiver, antenna and the ADS-B program (added 2026-11-05, UNBUILT when written)

Run `./gradlew runData` first (lang, recipes, tags: the beacon and receiver take power wire, the receiver and antenna
coax, the receiver data wire). Take the ADS-B Beacon, Receiver and Antenna from the creative tab (after the floppy
disks). For the craft tests run `./gradlew runClient -PwithSable` (add `-PwithCreate` and Create Aeronautics if you
have it).

- [ ] Models (placeholders): the beacon is a small grey box with an amber code window and a white blade on top, its
  front the way you looked when placing it; its lamp glows amber while it transmits. The receiver is a dark cabinet
  with a round green CRT; the CRT glows (rings and three dots) while it is on. The antenna is a thin black stub with a
  white tip on a metal plate. All three break with a pickaxe and drop themselves; breaking particles are not purple.
- [ ] Beacon screen: Flight ID box (letters and digits, Enter sets it), Squawk box (only 0-7 typed, Enter sets it;
  7700 shows EMRG in red), Turn on/Turn off, the state (OFF / ON, NO POWER / TRANSMITTING 1090 MHz in amber), the ICAO
  address, and once a second its position, ALT ft and V/S, GS kt and TRK. Typing E in a box does not close it.
- [ ] Receiver screen, on the ground with a beacon nearby: On; NO ANTENNA until an antenna is on top of it (or coaxed
  to it), then RECEIVING 1090. The scope: a dark green disc in a black bezel, four rings with their distances along
  the north line, a dotted cross, an "N", and no sweep (ADS-B is not a radar). The beacon shows as a steady
  green block with its tag (flight ID; FL and knots below it).
  - and + (and the wheel) step the range 250 m - 20 km. Click the target (or its row in the list): it gets a box and
  its details show below the list. Does it fit the window at GUI scales 2-4? Is the half-size text readable?
- [ ] Moving craft (Sable): assemble a ship with a beacon (placed facing the bow) and a large battery wired to it,
  switch it on, fly it around a receiver on the ground. The target moves, leaves a dotted trail, its track line points
  where it is going, the speed in knots is about right (one block a second is about 2 kt), altitude rises as you climb
  (^ in the tag) and V/S on the beacon screen follows. Parked, the beacon reports the bow's heading as TRK, and turning
  the ship turns it. Higher is further: at low altitude it drops off at a few hundred blocks, near the build limit it
  carries on past 1,500. A ship with no name and no flight ID shows its ICAO address; a named one shows its name.
- [ ] Receiver on a ship: put a receiver and antenna on the same ship as its beacon: its own beacon is not shown; a
  second ship's is, placed right as the ship turns and moves (the scope stays north up).
- [ ] Squawk 7700 on a beacon: the target and its tag go red with EMRG, on the receiver and in the ADS-B program.
- [ ] A beacon switched off or broken disappears from the receiver within about 10 s (it shows LOST n S AGO first).
- [ ] Lightning storm and a solar event (`/rcdebug` solar): ADS-B carries on the same.
- [ ] Computer: install the ADS-B floppy (blank floppy + feather) on a radio computer touching the receiver (or on its
  data wire): ADSB.EXE shows the scope as a picture on the left (rings, trails, targets, tags in the little pixel font,
  emergency in red, a yellow box round the picked one), the traffic table on the right (FLIGHT FL KT TRK KM SQWK),
  details of the picked one at the bottom. F5 switches the receiver on and off (the receiver's lamp follows), +/-
  /PgUp/PgDn and the wheel on the scope change its own range, Up/Down pick, T hides the tags. With no receiver cabled
  it says so. The picture updates once a second without flicker.
- [ ] Handbook: the new "Aviation & ADS-B" category (beacon, receiver) and "ADS-B on the Computer" in the Radio
  Computer category; links work.
- [ ] Busy server: with a dozen beacons flying and a few receivers on, `/forge tps` (or spark) shows no cost worth
  seeing; a receiver switched off costs nothing.

## 1.12 step 10: FCC field gear (added 2026-11-05, UNBUILT when written)

Run `./gradlew runData` first (lang, three recipes). Take the FCC hat, jacket, banner pattern and the ready-made FCC
banner from the creative tab (after the ADS-B blocks). The badge is not in the tab: run `/radiocraft fcc badge @s` as
an operator. Test once with Curios (the dev runtime has it) and once in a pack without it (or `prodLaunchTest`).

- [ ] Item sprites (placeholders): a brown felt fedora with a black band, a mustard jacket with a white shirt V and a
  maroon tie, a paper pattern with a blue ring and "FC", a gold shield badge with a blue centre on a chain loop.
- [ ] Worn: the hat covers the top half of the head (brown, black band, a darker brim line round it); the face shows
  below. The jacket covers body and arms (mustard, shirt V and tie at the front, white cuffs, a gold lapel pin). No
  purple-black missing texture, in first person (arm) too. The armour bar shows 1 for the hat and 3 for the jacket;
  equipping sounds like leather; an anvil mends them with brown wool; they take Protection, Unbreaking, Mending.
- [ ] Loom: a banner plus the FCC Banner Pattern shows "FCC Seal" in the pattern list; the preview and the placed
  banner show a ring with a bolt over the letters FCC and an underline, in the chosen dye; the back of the banner shows
  it mirrored. On a shield (banner on a shield at a crafting table) the small seal and FCC show. The banner's tooltip
  lists "White FCC Seal" (or the colour used).
- [ ] The ready-made FCC Banner in the creative tab: blue with a yellow border and the white seal, named "FCC Banner".
- [ ] `/radiocraft fcc badge <player>`: a non-operator is not offered `fcc` after `/radiocraft` and gets an error
  typing it; an operator gets "Issued an FCC badge to ...", the player gets the badge and "You have been issued an
  FCC badge". Tooltip: "Field Agent <name>", "Badge No. nnnn" (the same number every time for the same player), in
  epic purple. A selector (`@a`) issues one each and says how many.
- [ ] Right click the badge: you and players within 8 blocks read "<you> shows FCC badge No. nnnn: Field Agent,
  Federal Communications Commission" (gold) and hear a soft chain clink; a player 20 blocks away reads nothing. Give
  the badge to another player: when they show it, it reads in red "... shows an FCC badge issued to <you>". Two
  seconds between shows. The badge survives lava.
- [ ] Curios: the badge goes in the body (chest) slot (and only there among Curios slots); the hat and jacket do not go in
  Curios slots. Without Curios: the badge works from the main hand and off hand, no errors in the log.
- [ ] Handbook: "FCC Field Gear" in the Server Admins category (badge spotlight, the hat and jacket recipes, the
  pattern recipe); the category description mentions it.

## 1.12 step 11: Real-World Interface (added 2026-11-05, UNBUILT when written; needs real hardware)

Run `./gradlew runData` first (lang, the recipe, blockstate and item model, tags, loot). Then **check the shipped jar**
before anything else: `./gradlew build` (verifyModJar now fails if jSerialComm is not a nested jar, or if its classes
were unpacked or relocated into the mod jar), and `./gradlew prodLaunchTest`, which must start with the feature off and
log no jSerialComm or native-library line at all. You need a server PC with a sound card (a USB rig interface such as a
SignaLink, DigiRig or a CM108 dongle is ideal), a real radio on a dummy load or a frequency you are licensed for, and
for the PTT tests a USB serial adapter (DigiRig, FTDI) or the interface's own serial port. **Hold the licence and
identify**: this transmits for real.

- [ ] With `real_world_interface_enabled = false` (default): the block places, its screen shows the red licence
  warning and "Turned off in the server config", the interface arrows are greyed; nothing in the log about sound or
  serial ports; `/radiocraft realworld devices` says it is off. The Config screen in the mod list shows no
  `real_world_interface*` settings (the section may show empty).
- [ ] Turn it on in `radiocraft-server.toml` (dedicated server, or the world's serverconfig in single player), restart.
  `/radiocraft realworld devices` (op level 3) lists the sound card inputs, outputs and serial ports. The first serial
  listing unpacks jSerialComm's native library (to the temp folder): no error, and the server keeps ticking.
- [ ] Add an interface, e.g. `"name=Shack 2m;input=USB Audio;output=USB Audio;serial=COM3;ptt=RTS"`, restart (or let
  the file watcher reload). `/radiocraft realworld status` lists it; a deliberately bad line is logged and listed in red.
- [ ] Place the block, power it, pick "Shack 2m" with the arrows, tune 146.520 FM. The status reads "Linked to the real
  radio"; In, Out and PTT each read FOUND. Unplug the USB interface: it reads NOT FOUND or WON'T OPEN within a few
  seconds; plug it back: FOUND again within about ten seconds (the retry), without breaking the block.
- [ ] Opening the serial port does **not** key the radio (the PTT line is set unkeyed at once). `ptt_invert=true`
  inverts it. `ptt=DTR` keys on DTR instead. `ptt=NONE` with the radio's own VOX works too.
- [ ] Downlink: a player with a VHF base station or handheld on 146.520 talks. The real radio keys (RTS) within about
  0.2 s, transmits the voice clearly (listen on a second radio: no clicks, not too fast or slow, no hum), with the TX
  delay (`ptt_delay`) before it, and unkeys about `ptt_hang` after the player stops. The Out level bar moves; the red
  PTT lamp lights. Try `rate=44100` and `rate=8000` on a card that takes them: the pitch must stay right.
- [ ] Time-out: hold PTT in game for longer than `timeout` (set `timeout=20` to test): the real radio unkeys at 20 s,
  TOT lights, and keys again only after the player lets go for a second and talks again.
- [ ] Uplink (VOX): someone talks to the real radio from another radio. The block's green TX lamp lights, the In level
  bar moves, and players near radios on 146.520 hear them clearly (at the right pitch), for `vox_hang` after they stop.
  Raise `vox_threshold` until band noise alone does not open it. `input_gain` and `output_gain` change the levels.
- [ ] Uplink (COS): `trigger=COS;cos=CTS` with the radio's squelch/busy line on CTS (or DSR): audio goes in only
  while the radio's squelch is open, whatever the level; `cos_invert` flips it.
- [ ] Simplex: while the real radio is transmitting game audio nothing goes back in (no echo loop), and while the real
  radio's audio is going out in game the block does not key the real radio.
- [ ] Linked to an antenna instead: coax and a J-pole; a station 100 blocks away is heard and hears it, as a normal
  radio would. With nothing on the coax only a few blocks away hear it (the bare connector).
- [ ] Everywhere: press Antenna/Everywhere. A station 2,000 blocks away hears it at full strength and is heard (one in
  another dimension is not); HF on 7.200 LSB works the same with no antenna at all.
- [ ] One block per interface: a second block picking "Shack 2m" is refused ("in use by the interface at ...").
  Breaking the first frees it. A non-op cannot pick an interface, tune or change the mode while
  `real_world_interface_op_only` is true (message over the hotbar), and can with it false.
- [ ] Stop the server while the radio is keyed: it unkeys, and the sound card and serial port are released (another
  program can open them at once). Same when the block is broken, the chunk unloads, or the interface is renamed in the
  config.
- [ ] Moderation: with `moderation_record_radio` on, the real radio's audio is logged as "Real radio" from the
  `real_world_interface` block, with its hearers; in-game players heard by the block list it as a hearer.
- [ ] Handbook: "Real-World Interface" in Server Admins, with the licence warning and the recipe.
- [ ] Busy server: with the feature on and a link running, the server's tick time does not change (all audio is on
  the interface's own threads).

## 1.12 step 12: modding API (added 2026-11-05, UNBUILT when written)

Dev client only (the example blocks are in the GameTest source set, which every dev run includes). They have no item,
model or name, so use `/setblock ~ ~ ~ radiocraft_example:low_band_radio` and `radiocraft_example:ground_plane`.

- [ ] The game starts with the example registered: no registry or "unknown namespace" error for `radiocraft_example`
  in the log, and joining a world syncs (client and server both have the blocks).
- [ ] An example low band radio (missing-texture cube) next to a Commercial Low Band Radio on the same channel: hold the PTT on
  the mobile and talk; nothing is heard yet from the example (it has no screen), but `/rcdebug radio <pos>` on it shows
  it heard the signal. Static: the example is FM, so no background hiss.
- [ ] Coax and power wire join the example radio and the example ground plane without any tag (the wire draws its
  link to both); the antenna analyzer on the ground plane reads SWR 1.2 on low band.
- [ ] Stock radios still behave: coax to a J-pole, CW between two HF radios, APRS/data bursts, a handheld on 2m
  (the antenna's voice, CW and data now reach radios through the new API interfaces).
- [ ] Website: `wiki/modding.html` in the website repo reads well, the code blocks scroll sideways on a phone and are
  readable in the site's colours.


## Radio Moderation Computer (added 2026-11-04)

Turn on `moderation_record_radio` (and `moderation_record_proximity` for the Proximity tab) in radiocraft-server.toml
and restart. As an operator, take the Radio Moderation Computer from the creative tab (after the DTMF decoder) and
place it. Simple Voice Chat must be connected for listening and playback. Try GUI scales 2, 3 and 4, and a small
window at scale 4.

- [ ] The block: a beige PC (desktop case, CRT with a teal desktop, keyboard) facing the player; it breaks with a
  pickaxe and drops itself; the creative tab has it and there is no recipe. Its tooltip says what it is for.
- [ ] Placing it as an operator then right clicking opens the screen; the H19 fan spins up quietly while it is open
  and stops when it closes. A second player (not added) right clicking gets "Locked: this moderation computer belongs
  to ..." over the hotbar. A non-operator placing one is told it has no owner; they cannot open it.
- [ ] The window: grey Windows 98 panels with bevels, a navy-to-blue title bar with the title in white, a close box
  that presses in and closes it, tabs along the top (the picked one stands taller and joins the panel), a status bar
  (REC lamp and "REC: radio on, voice chat off", storage "x MB of 5.0 GB, kept 7 days", messages). It fits the window
  at every GUI scale and is drawn smaller rather than cut off on a small window; JEI's item list does not cover it.
- [ ] Controls: buttons press in while held and act on release; text boxes are sunken white with a blinking caret,
  take typing (the inventory key does not close the screen while typing), paste, arrows, home/end; each key clicks
  quietly (H19 key). Dropdowns open a list (upwards near the bottom), close on a click away. Lists have raised column
  headers, a navy selection, a scrollbar with buttons and a draggable thumb, and scroll with the wheel.
- [ ] Live: a player talking on a handheld shows within half a second (talker and callsign, Voice, 146.5200 FM, radio,
  position, how many hear, time on the air) and drops off a second after they stop. Pick the row and Listen to talker:
  their voice comes through clean, the lamp and "Listening to X" show, the hotbar says "On air: ...", and the H19 bell
  rings (quietly, at most every few seconds) each time they key up again. Stop listening ends it.
- [ ] Live, "Hear what they hear": pick a player who holds a handheld (or stands by a base station) and press it; you
  hear what their radios receive, clean, with "Heard by X: Speaker, 146.520 MHz" over the hotbar; nothing they do not
  receive. Leaving the screen keeps listening; Stop listening (or logging out) ends it.
- [ ] Archive: Search lists the last day's transmissions, newest first, 30 a page with < > paging and "1-30 of N".
  Filters each narrow it: player as talker/heard/either, words (name, callsign, text), MHz from/to, kind, time range,
  flagged only, radio x, y, z, world; Clear resets them. Bad MHz or position says so in the status bar. Clicking a
  column header sorts the page; flagged rows are red.
- [ ] Archive detail: picking a row shows time, kind, talkers, frequency/mode/band/tone, radio and position, antenna,
  every hearer with strength %, text or CW text, data packets, repeats #id, tape; the wheel scrolls it. Play plays the
  over through voice chat with the progress bar and time filling; Stop stops it; closing the screen stops it. "As heard
  by" a weak FM hearer sounds hissy and drops out; an SSB hearer sounds band-limited. Flag turns the row red and the
  button into Unflag; Export shows "Exported 1 file(s): <path>" and the .opus plays; Export flagged exports them all.
- [ ] Map: radios that sent (red) and only heard (blue) on the air in the world and time range, over the H19-style
  terrain in the world you are in (a grid elsewhere), your position as a white cross. Hover shows the radio, position,
  last talker, frequency, counts and last time; drag pans (the terrain repaints on release), the wheel zooms, Fit fits;
  pick one and Show in Archive lists its transmissions.
- [ ] Links: after a few overs between players, the graph shows them round a circle (players navy, lone radios grey)
  with lines thicker for busier links and a small square at the hearing end; hovering a node shows only its links in
  red. The table sorts by From, To or Times. "Voice chat instead of radio" switches to proximity links.
- [ ] Tapes: a tape recorded on a tape player and aired lists with when, length, where, speakers, times aired and when
  last; Play plays the tape through voice chat with the progress bar.
- [ ] Proximity: "On now" lists players talking in plain voice chat; Listen to speaker hears their voice chat wherever
  you are. "Archive" lists and plays past voice chat (no MHz or kind filters). With proximity recording off it says so.
- [ ] Access: owner and admins listed; the owner adds an admin by name (online or known) who can then open it, removes
  them, and hands it over (staying on as an admin); an admin sees the buttons greyed with "Only the owner can change who
  has access". An admin removed while the screen is open is shut out at the next request. The audit log lists opens,
  listens, plays, exports, flags and admin changes with who and when.
- [ ] `/radio moderation unlock x y z` (op 3) says it unlocked the computer and whose it was; the old owner is locked
  out and the next operator to use it owns it.
- [ ] REC on radio screens: with `moderation_record_radio` on, every block radio screen shows a small red "REC" with a
  dot in its top right corner, and the handheld's LCD shows REC in its top line (left of the APRS marks, the memory
  name shortened to fit); with it off, none.

## Moderation log, server side (added 2026-11-04; the moderation computer's screen comes later)

Turn on `moderation_record_radio` (and for the last items `moderation_record_proximity`) in radiocraft-server.toml
and restart. The log is under `<world>/radiocraft/moderation`; `/radio moderation status|list|flag|unflag|export|purge`
(op level 3) looks at it until the computer has a screen.

- [ ] Joining with recording on shows the gold notice ("This server records voice sent over radios for moderation
  (kept 7 days)."); with `moderation_notice = false`, or recording off, nothing.
- [ ] Two players with real microphones: one talks on a handheld, the other hears on a handheld and at a base station
  (stand within 16 blocks of it). `/radio moderation list` shows one player_voice line for the over, heard by the
  handheld's holder, the base station and the player standing at it. Talking into a desk mic or a base station's hand
  mic gives one line per radio keyed (each is its own transmission).
- [ ] `/radio moderation export <id>` writes an .opus file to the exports folder: it plays in VLC/foobar/a browser,
  sounds like the player (clean, not radio-processed) and keeps the pauses inside the over.
- [ ] Through a repeater: the talker's line (heard by the repeater), plus a "repeated" line linked to it, heard by the
  stations on the output. The repeater's beep, hang time and ID make no lines.
- [ ] A player tape played into a radio makes a "tape" line (no audio of its own; playback reads the tape file);
  recording over that tape afterwards leaves a copy in moderation/tapes. A server tape makes a "server_tape" line
  naming the file. Recording a tape makes a "tape_recording" line naming who spoke onto it.
- [ ] RTTY from a teletype, APRS from a handheld and a fax each make a "data" line with the packets, not audio; the
  RTTY line names the typist. CW keyed from a radio screen makes a "cw" line with the decoded text and the keyer.
- [ ] A commercial station tuned in makes a "commercial" line (program and callsign); one nobody listens to makes none.
- [ ] `moderation_metadata_only = true`: lines still appear with hearers, but `list` shows no frames for voice.
- [ ] Proximity: two players talking without radios make "proximity" lines (not in `list`, which shows radio only;
  check the index file in moderation/index), with the other player as a hearer.
- [ ] No audible change anywhere with recording on: radio voice, handhelds and voice chat sound and time exactly as
  before, also with ten players talking at once (the log only copies packets; watch for "writer is behind" in the log).

## New sounds: H19, power buttons, FM static, redstone RFI (added 2026-11-04, maintainer's and S0lver's sounds)

- [ ] H19 whirr (`h19_whirr_2`) loops at the terminal whenever it has power, screen open or not; it spins up as power
  comes on, runs down when power goes, and fades with distance (about 12 blocks). The screen no longer plays its own;
  with the screen open the whirr rises to twice its room volume, and eases back down when it closes.
- [ ] H19 bell rings at the start of the boot, and at the terminal when a message comes in (message buzzer on). The
  contact buzzer still beeps.
- [ ] H19 keys use `h19_type`; characters coming up on the screen (boot printing, a page drawing in) use it much
  quieter. Check it is not a wall of clicks during the boot's fast ASCII art.
- [ ] SETUP page: a dim "SOUNDS BY S0LVER" in the bottom right; hovering lights it, clicking rings the bell.
- [ ] Power buttons latch in (`power_in`) turning on and pop out (`power_out`) turning off, instead of the menu click:
  every HF/QRP/VHF radio, receivers, the all-band, the handheld, the repeater, the manpack.
- [ ] FM static is the recording (`fm_static`) now: the squelch tail on VHF radios, handhelds, the all-band on 2m FM,
  and the hiss and dropouts of a fading FM signal. Same loudness as before; two radios side by side do not hiss in step.
  A blown VHF radio keyed roars with it too.
- [ ] Redstone RFI: each burst plays one of four recordings (`redstone_rfi` to `_4`); flip a lever a few times, a
  second apart, and hear them change. Variants 3 and 4 are turned down to match the first two.

## VHF handheld: memory channels, APRS module, held model, no receive while transmitting (added 2026-11-04)

- [ ] Memory store: hold MW (about half a second) and "STORE M0" shows (the first free slot), with "UNUSED" or the
  stored channel's frequency and markers in small text under it. The arrows or a number key change the slot; ENTER
  stores the frequency, tones and offset ("STORED M3"); tapping MW backs out without storing.
- [ ] Channel names (added 2026-11-04): after ENTER on the store page, NAME M3 opens with the old name; type up to 7
  characters on the keyboard (E types an E), Backspace or the down arrow deletes, ENTER saves ("M3 HOME"), MW backs
  out. * opens SETUP: DISPLAY steps NAME, FREQ, CH NO; NAME CH renames the channel in use ("MEM MODE ONLY" on VFO). On
  a memory channel the main display shows big the name (falling back to the frequency if it has none), the frequency
  (the name small at the top) or "CH 3" (the frequency small at the top). The offset and tone marks now sit on the
  bottom line beside TX/RX.
- [ ] Memory mode: tap MW. With nothing stored it shows "NO MEM"; otherwise the radio goes to a stored channel and
  the second line shows "M3" before the offset/tone markers. The arrows step through the stored channels only, a
  number key jumps to that channel ("M5 UNUSED" if empty). Tap MW again: "VFO", and the frequency, tones and offset
  you had before come back. Close and reopen the screen, and log out and in: the channels and the mode stay.
- [ ] APRS without the module: APRS shows "NO APRS". Craft a handheld with an APRS module (shapeless, any slots): the
  handheld keeps its charge, frequency and memory channels, and its tooltip says "APRS module installed". JEI shows
  no recipe for that combination (it is a special recipe): check that is acceptable, or ask for a JEI info page.
- [ ] LCD (redrawn 2026-11-04 as a monochrome dot-matrix LCD; the old text screen is backed up in
  `E:\Radiocraft-backups\2026-11-04-handheld-text-screen`): grey-green glass, dark 3x5 pixel text, the frequency in
  big seven-segment digits with the unlit segments faintly showing, headings as inverted bars. The top line shows VFO
  or a boxed M3, offset and tone letters, a B while beaconing, a blinking envelope for an unread message and a battery
  icon; the bottom line a boxed TX or RX, and APRS when the module is in. Any key or click lights the backlight for 5
  seconds. Check every page for text running into other text, at GUI scales 2, 3 and 4.
- [ ] APRS menu: APRS opens it; three lines show at a time, the chosen one inverted, small arrows when there are more
  above or below; ENTER opens or toggles; APRS goes back up a level and out.
- [ ] Beacon settings: APRS > BEACON lists BEACON (on/off), ICON (cycles house, portable, car, ...), CALL (your
  callsign with an SSID: ENTER steps -1 to -15 and none; default -7), EVERY (30S, 1M, 2M, 5M, 10M, 30M) and TEXT.
  TEXT opens a comment editor: type on the keyboard (E types an E, it does not close the screen), Backspace deletes,
  ENTER saves, APRS cancels. An H19 then shows your beacon with that icon, comment and callsign. A message from an H19
  to your plain callsign (no SSID) still reaches the handheld. CALL shows NONE until you have a callsign.
- [ ] Stations: with an H19 station on 144.390 beaconing near you, it appears under STATIONS (nearest first, distance
  and n/N in small text). ENTER: the callsign, X Y Z in small text, the distance, and a compass on the right whose
  needle points at the station as you turn (up is straight ahead). Walk round it and check the needle follows.
- [ ] Messages: send the handheld's callsign a message from an H19. The holder hears a chime, the DATA lamp blinks,
  MESSAGES says "1 NEW", the entry shows "<CALL*". ENTER opens it: the text scrolls along the second line again and
  again, and the lamp stops blinking. The H19 shows the message as SENT (acked).
- [ ] Send: SEND MSG, pick a canned text with the arrows, ENTER, pick ALL or a station, ENTER: "MSG SENT". The H19
  gets it; for a station the entry later shows ACK. The burst is heard on 144.390 and the mic is cut while it goes.
- [ ] Beacon: BEACON ON sends a beacon right away and then at the chosen interval (never faster than the server's
  `handheld_aprs_min_beacon_seconds`, 30 by default); the H19 lists you at your position.
- [ ] Mute: with MUTE ON and the handheld tuned to 144.390, packet bursts are silent but people talking on 144.390
  still come through. MUTE OFF: the bursts are heard again.
- [ ] Held model (`models/item/vhf_handheld_3d.json`, placeholder): in first and third person, the radio stands
  upright with the keypad, screen and speaker toward you, the antenna on top left and the ribbed battery underneath.
  The hotbar, inventory and item frames still show the flat icon. Tune the `display` rotations if it sits badly in
  the hand.
- [ ] Inventory icon: the handheld shows the 3D model in the hotbar, inventory and item frames (slightly turned, the
  antenna inside the slot). The poor and pure crystal handhelds now look the same as the good one there. In the hand
  it is a quarter smaller than before.
- [ ] Desk charger as a radio (added 2026-11-04): a switched-on handheld in the cradle still uses its battery (on a
  charger with no power its charge goes down and it switches off when flat; on a powered one it charges faster than
  it drains). It hears its frequency through a speaker at the charger (about 12 blocks), with its RX tone and the
  APRS mute; its screen backlight comes on with a boxed RX while it hears something. Its APRS module beacons from the
  charger under the callsign it last had in your hands, and messages sent to you while it sits there are on it when
  you pick it up. It cannot be keyed to talk there.
- [ ] Live screen and RX LED on the model (added 2026-11-04): a switched-on handheld shows its LCD (the main display)
  on its model in your hand (first and third person), on your belt, in the inventory and hotbar icon, and in a desk
  charger; switched off, the screen is plain. While it hears something the screen's backlight comes on, RX shows
  bottom left and the green LED on its face lights, glowing in the dark (NeoForge emissive quads,
  `models/item/vhf_handheld_rx_led.json`). In the charger the status reads CHG while charging. Check many handhelds
  in one inventory do not slow the game, and that the screen and LED do not flicker or z-fight.
- [ ] Desk charger by hand: sneak and use it holding a handheld: the handheld goes into the cradle (one already there
  comes back to your hand). Sneak and use it with an empty hand: the radio comes out into your hand. Plain use still
  opens its screen. Sneak-use with a handheld anywhere else still opens the handheld's screen.
- [ ] Desk charger and power wire: power wire placed beside a desk charger now joins it (draws it towards the charger)
  and the charger fills its store from the wire.
- [ ] Desk charger: a handheld in the charger stands in the cradle as its 3D model (80% size), keypad and screen facing
  out of the charger's front like its LED, for all four facings. Switched on, its LCD shows the main display (frequency
  or channel, battery, a boxed CHG until full); switched off, the screen is dark glass. Other items in the slot still
  show the old radio shape.
- [ ] Handheld screen: a ribbed battery pack is drawn under the radio, as on the model; the screen is centred with it.
- [ ] Held lower: in first person the radio sits lower, running off the bottom of the screen instead of floating.
- [ ] Belt (Curios, moved 2026-11-04): a handheld in the belt slot shows clipped to the back of the belt over where
  the left back pocket would be (halfway between the spine and the side), standing straight with its face to the back, about half size, against the
  body; it follows the body turning and sneaking. Check it does not clip into the leg or a cape.
- [ ] Hand mic in the hand (2026-11-04): the hand mic, the corded mic and the manpack mic show a small 3D speaker mic
  (grille on the face, PTT on the side, clip on the back, cord out of the bottom) in first and third person; the
  inventory still shows the flat icon. Tune the `display` in `tools/hand_microphone_model.py` if it sits badly.
- [ ] Repeater autopatch screen: the extension and activation code labels fit inside the panel.
- [ ] No receive while transmitting: hold PTT on a handheld while someone else talks on the frequency: you hear
  nothing until you let go. Same for a base station or mobile with its PTT, and an HF radio sending CW does not hear
  morse while keyed. A repeater still repeats.

## Repeater Autopatch Controller (added 2026-11-04)

GameTests cover dialling out with code, number and *, # hanging up and cancelling, an unknown number, answering a call
in, turning calls away with no repeater, and phone audio held off the air while the repeater's input is busy. Set up:
a repeater with duplexer and antenna, the autopatch touching it (or on its data wire), a powered PABX touching the
autopatch, a telephone on the PABX, and a handheld on the repeater's input/output. These need a client.

- [ ] Looks like a PABX but dark green; its lamp lights while armed or on a call. Right click: plain panel with
  Extension, Activation code (only 0-9 and A-D type), "Repeater: linked", what it is doing (Idle, Armed dialing...,
  Calling..., In call with...), Save and a status line that fits.
- [ ] From the handheld's DTMF page send the code: a second of dial tone comes back on the repeater. Send the phone's
  extension and *: ringback (2 s on, 4 s off) until the phone is answered, then one beep. A wrong number: fast busy.
  # while armed, or 10 s with no key: two falling beeps.
- [ ] On the call: the handheld's voice comes out of the telephone at phone quality. The phone's voice comes out of the
  handheld only while nobody is transmitting on the repeater input; talking over it cuts the phone off (nothing is
  saved up and played after). The repeater's hang time and courtesy beep still sound right around it.
- [ ] # on the air hangs up (two beeps on the air, the phone shows CALL ENDED); hanging up the telephone ends it too.
  Three minutes with nobody on the radio side hangs up by itself (server config autopatch_inactivity_seconds).
- [ ] Call the autopatch's extension from the telephone: a short double ring on the air, then it answers by itself.

## DTMF: handheld page, encoder and decoder (added 2026-11-04)

GameTests cover encoder to decoder over the air, through a repeater, the lamps, pulse and toggle, a code that starts
wrong, a signal too weak to copy, and receivers or radios that are off not sending. These need a client.

- [ ] Handheld (changed 2026-11-04): # opens the DTMF page ("DTMF", then a small "KEYS, ENTER SENDS"). The keypad
  keys, * and # (both clickable now), and 0-9, *, #, A-D typed on the keyboard add to the string on the second line;
  the down arrow or Backspace deletes the last one. ENTER sends the whole string ("DTMF SENT") and goes back to the
  frequency; ENTER with nothing typed just goes back. Another player's radio on the frequency hears real touch tones
  (they should sound like a phone), and the sender's mic is cut while they go out. ENTER from the frequency now opens
  only the offset page (ENTER again goes back).
- [ ] Encoder resend (2026-11-04): with nothing keyed in, SEND sends the last number again (the one shown). A redstone
  signal coming on beside the encoder (lever, button, pressure plate) sends the last number again; holding it on
  sends once. GameTest `redstoneResendsLastNumber` covers the redstone.
- [ ] Encoder screen: twelve keys in phone order, DEL, CLR, REDIAL, SEND; typing works, Enter sends, Backspace
  deletes, typing E does not close it. With no radio on beside it, SEND says so above the hotbar.
- [ ] Decoder screen: the code box takes only DTMF keys; Enter or SAVE sets it. The mode button switches pulse and
  toggle. The two lamps on the screen and on the block light together; HEARD shows the keys copied.
- [ ] Block models (placeholders): a grey box each, the decoder's red and green lamps lit full bright on its front
  when on, the encoder's keypad on top. Floor cable drawn towards a radio beside them. Redstone dust beside the
  decoder lights on a match; the radio beside it does not crackle with redstone interference when it does.
- [ ] Weak signal: walk a handheld out to the edge of range and send a code to a decoder. Some keys should go
  missing (the HEARD line shows gaps) and the code then does not match.

## TA-43 field phone, data wire through unloaded chunks (added 2026-11-04)

GameTests cover ringing, answering, the 6/12 phone limit and the wire memory. These need a client.

- [ ] Looks like a TA-43: olive drab case, door and round window on the front, binding posts, knob and handset on top,
  close to a full block. The handset goes from the model when picked up and the cord runs from its right hand end.
- [ ] Right click with an empty hand: the handset comes up to the ear in third person and in the hand in first person,
  about the size of the telephone's. Right click the phone with it to hang up; walking 5 blocks away snaps it back.
  With an item in hand, right click places it (data wire laid against the phone).
- [ ] Picking up rings the other phones on the line with a fast, low bell in bursts; it stops when anyone answers or
  the caller hangs up. With power on one phone the bell and voices are louder.
- [ ] Three handsets up: everyone hears everyone, two talking at once both come through.
- [ ] Seven phones on an unpowered line: picking up says the line is dead, nothing rings.
- [ ] Two field phones (or two telephones on PABXes) far apart, e.g. 600 blocks of data wire: with players only at each
  end, the call still goes through and voice works. Lay the wire once with the phones loaded first.

## Phone PA (added 2026-11-02)

GameTests cover answering, numbering, local calls and finding speakers. These need a client.

- [ ] Looks like a PABX but blue grey with a grille on the front; its lamp lights during a call.
- [ ] Right click it: plain panel with Extension, Speakers connected, a working "Allow within local PABX only" checkbox
  with readable text, Save, and a status line that fits. With JEI installed its list sits beside the panel and covers
  nothing.
- [ ] Call it and talk: your voice comes out of a desk speaker touching it, and desk, wall and PA speakers on its data
  wire, each sounding like that speaker (the horn loud and far). Data wire visibly connects to desk and premium speakers.
- [ ] A PABX with many phones on a battery runs it down faster, and with too little supply every phone loses its line.
- [ ] Manpack, weak local signal in a solar event: as clear as a radio in the same spot (it used full solar noise before).


## Linked PABXes, All-Call, SSB mix (added 2026-11-02)

GameTests cover the calling logic and unit tests the levels; these need a client.

- [ ] PABX screen: a taller panel with a fourth box, All-Call extension (or none), between First extension and PABX
  name; nothing overlaps, and the status line and the flashing red "EXT IN USE: 100" at the bottom both fit.
- [ ] Two PABXes with one master number, wired or over microwave: each phone dials the other's bare extension. Give
  them clashing extensions before linking: "EXT IN USE" flashes in the top right of both phones' LCDs and on the PABX
  screens, and dialing that number flashes it on the caller's bottom line. Changing one phone clears it within 2 s.
- [ ] All-Call: every phone rings; the first to answer talks and the others show ANSWERED ELSEWHERE.
- [ ] SSB, a manpack keyed beside a 20m radio with a commercial module on the same frequency 20 blocks off: on the
  radio your voice is a little over the module, both are clear, and the second keying up does not jump, crackle or
  drop out. `SSB_MIX_POWER`, `SSB_MIX_FLOOR`, `MIX_AGC_*` in `VoiceMix`, `NEAR_BLOCKS` in `ReceiverMix`.
- [ ] SSB, two voices at once: the weaker lifts in the stronger's pauses without pumping unpleasantly, and the small
  pitch offset (10 to 40 Hz, only while mixed) helps tell them apart without sounding detuned when it starts.
- [ ] SSB, a transmitter 1 to 3 blocks from the receiver: only faintly rough, never harsh (`OVERLOAD_SPLATTER`).
- [ ] Desense: two radios with antennas within 5 blocks, one transmitting: the other hears a far station weaker (FM:
  hiss or dropouts), less so with more distance or frequency separation, not at all from 2 MHz apart
  (`desense_range`, `desense_separation_mhz`, `desense_depth`).
- [ ] HF skip voice sounds hollow and slowly watery; local voice stays clean (`skip_multipath_depth`, default 0.6).
- [ ] SSB, a weak station on a noisy band: the voice stays near full volume with the static plainly louder around it,
  rather than the voice going quiet (`VOICE_LEVEL_POWER` in `ReceiverMix`). The static does not flutter while a
  strong and a weak station talk together.
- [ ] RF through a roof: a handheld or an antenna under wood planks, stairs or slabs, glass, wool or leaves works as well as
  outside; under stone or logs it is weaker as before. The atlas re-reads a chunk in turn, or at once with a reload.
- [ ] SSB static under a signal: none at S3 or better on a great, good or average band, including a manpack a few
  blocks from a 5B on 20m. On fair, poor and blackout the signal must beat the noise floor's meter reading to clear it.
  Below that the static comes back gently, then faster (`noiseDuck`, `QUIET_SIGNAL` in `ReceiverMix`).


## PABX rework (added 2026-10-31)

PABXes have no "behind" setting any more: all PABXes on one data wire or across microwave links are peers, reached
by master number + extension (a local extension wins). GameTests pass; these need a client.

- [ ] Right click a PABX: a plain grey panel at normal GUI size, "Phone PABX" and Powered / No power on top, three
  text boxes (Master number (3 digits, or none), First extension, PABX name (up to 10)) and Save. Typing E in a box
  does not close it; ENTER saves; the name comes out upper case. The line under Save says Saved, or why not (a
  2-digit master number).
- [ ] With JEI installed, its item list sits beside the PABX screen, and no longer covers the telephone, H19, fax,
  microwave radio or other scaled-up station screens (it moves aside or hides the items that would land on them).
- [ ] Two PABXes on one data wire (or across a microwave link), 602 and 603, 603 named OFFICE: on 602's phone, keying
  603 shows "PABX 603: OFFICE" on the LCD (just "PABX 603" with no name); keying on to 603100 shows the number again
  and rings extension 100 there. A phone's NO. line shows its master number + extension.


## Dyed data wire, creative search by number words (added 2026-10-31)

- [ ] Sixteen dyed data wires in the creative tab after plain data wire: each looks its colour with the dark flecks
  still showing (tells apart from dyed power wire); 3D item; laid in a bundle beside other cables. A dye used on a
  plain data wire run recolours it; red and blue runs side by side do not join.
- [ ] Creative search: "twenty" finds the 20m radios, "twenty meter" too, "five" the 5-BTV, "nine" the 9:1 balun.
  Hovering those items shows no extra lines.

## Weather fax on the air, blackout range, /solarweather (added 2026-10-31)

- [ ] SEND NOW on an Ionospheric Processor with a radio: the radio shows TX for the whole bulletin (about 20 s), and a
  radio on the same frequency nearby shows RX and plays the weather fax warble (a start buzz, a tick-tock, the picture
  sliding between two tones, a stop buzz). Does it sound right?
- [ ] SOLR WX on a far WEFAX: its radio shows TX for the short request, then the processor's radio TX and the WEFAX's
  radio RX; the sheet rises line by line while the tones play, with a soft stylus tick, not all at the end.
- [ ] Switch the processor's radio off half way through: the rest of the sheet prints as speckle.
- [ ] WX REPT: the radio beside the WEFAX shows RX and plays the fax tones while each chart prints.
- [ ] `/solarweather` shows the conditions (no error); `/solarweather set blackout`, then two HF stations 10 blocks
  apart still talk and still answer SOLR WX, while far stations cannot; their voices flutter (`blackout_los_distortion`
  0.4) - rough enough to feel the blackout, still easy to understand? `/solarweather set average` puts it back.

## Antenna analyzer, item tooltips (added 2026-10-31)

- [ ] Antenna analyzer no longer crashes the game: right click grass, a radio with nothing on it, bare coax, a
  balun, a J-pole, the radio of a working station. Each gives a message, never a crash.
- [ ] Analyzer on a 20m dipole (5 blocks each arm), its balun, its coax and its radio: "Resonant on the 20m band",
  then the SWR by band line. Shorten one arm to 4: it says too short and to lengthen it; make both arms 6: too long.
- [ ] Analyzer on a J-pole: resonant on 2m. On a random wire (never better than SWR 2): it suggests a tuner, or a length change when that would help.
- [ ] Hover over a crystal, an encased crystal, insulation, a tower beam, the duplexer and the desk charger: each has a
  grey line saying what it is for (the desk charger used to show a raw `tooltip.radiocraft.desk_charger`). Items
  that already had a tooltip (radios, wires, speakers) show it once, not twice.

## Data wire, splitter, WEFAX alarms, lightning on carried radios (added 2026-10-30)

- [ ] Data wire looks right: light grey, thinner than coax, the darker flecks visible; tells apart from light grey
  dyed power wire; in a bundle it sits between the power wires and the coax.
- [ ] A radio with a teletype five blocks away on data wire: RTTY typed there goes out, and what comes in prints.
  The same for a radio fax, a WEFAX receiver and a radio interface with an H19.
- [ ] A wall speaker on data wire from a radio plays it; on plain wire it no longer does.
- [ ] A microwave radio serves a telephone and an H19 over data wire.
- [ ] Antenna splitter: two radios behind it both hear the antenna, a little weaker; neither can transmit (nothing
  heard at the far end). The placeholder box looks acceptable until the model is drawn.
- [ ] Antenna switch: the model and the knob's pointer face the right ways as the input turns to the radio's coax
  and the knob is turned (right click, "Antenna 2" message, lever click); the coax meets the connectors; the radio
  hears only the selected antenna.
- [ ] A radio on a dummy load is nearly silent: a faint hiss instead of full static (5%).
- [ ] WEFAX redstone: a lamp beside the receiver lights when a message prints and goes out when the sheet is taken;
  the redstone signal strength is 2 / 3 / 4 for a severe thunderstorm, a tornado watch, a hurricane.
- [ ] Weather reports say "TODAY ... TOMORROW ..." and "TORNADO WATCH" with Another Weather Mod 1.9.3.
- [ ] Struck by lightning (`/summon lightning_bolt` on yourself in survival) with a handheld and a manpack: both
  fall apart into parts with a message; with the manpack switched on it hurts noticeably more.

## Two stations at once, and smoother signal changes (added 2026-10-29)

Stations heard at the same time are now mixed into the radio's one audio stream (`VoiceMix`) instead of their frames
being sent one after another. SSB adds them up; FM captures the stronger (at twice the other's strength), roughened
as the other comes close, and flips between them in garbled bursts when neither captures. Received volume and FM hiss
now glide to a new signal strength over `voice_level_smoothing` (0.15 s; 0 turns it off). Unit tests cover the mixing
and smoothing; GameTests have no voice chat, so none of this is heard in them. Needs two players (or a player and a
parrot station) talking at once.

- [ ] SSB, two players talking at once on one frequency: both voices heard over each other, clean (no choppy
  alternation, no crackle), the weaker one quieter.
- [ ] FM (2m base or handheld), one station much stronger: only it is heard, slightly rough; the weaker one is gone.
- [ ] FM, two stations about as strong: a garbled mess that flips between them every tenth of a second or so.
- [ ] One station alone sounds exactly as before, with no added delay.
- [ ] Walk a handheld out to the edge of range: the hiss swells in instead of stepping up every few blocks; the voice
  does not lag noticeably behind. Compare with `voice_level_smoothing = 0`.
- [ ] Wired speakers play the same mix as the radio.
- [ ] The handheld's signal meter reads sensibly: it now takes the RMS of the whole frame (it was using one sample).

## Radio sites and modules on the air (added 2026-10-28, evening)

Six sites generate in normal worlds (`radio_sites`), placed unturned; `SiteGameTests` checks every station in each
works as found. A commercial radio module is sent from `CommercialStations` whether its chunk is loaded or not, and
one at a site nobody has found yet is on the air once a player is within `commercial_station_range` (6000) of it.

- [ ] In a new normal world, `/locate structure radiocraft:repeater_site` (and the others): each site sits on its
  ground, not floating or buried (the buried bunker excepted), with the terrain blended round it; Long Lines and the
  repeater site only on high ground (y 130 and up).
- [ ] Copies of Long Lines have 1 to 4 horns, each still with its microwave radio inside; no stray horn parts.
- [ ] Some observatories and DF stations have a commercial radio module (a third of them); the bunker never does.
- [ ] Before going to a site with a module, tune to it from far off (`/rcdebug stations` lists the nearest, predicted ones
  marked): its program is heard, and is the same one the module plays once found.
- [ ] Fly away from a module until its chunk unloads: it is still heard (HF skip, or 2m within range).
- [ ] No lag spike while flying fast over new ground (the sites ahead are worked out off the server thread).
- [ ] Wires and bundles still look lit right (no dark patches under or beside cables, outdoors and in caves): skylight
  now passes them without asking their shape, which fixed a hang when a site with wires generated.

## Tower beam bends without flicker (added 2026-10-28)

Reported: where beams bend, the overlapping parts' textures glitch (z-fighting). The diagonal arms now sit 0.01 px
apart in layers (`tools/tower_beam_models.py`, `LAYER`): edge arms grow 0.01-0.04 px, corner arms 0.01-0.08 px.

- [ ] Thin, normal and thick beams: an L bend (east + north), a diagonal off a straight run (east + north_east), two
  diagonals from one block (north_east + north_west), an up-and-over bend (up + up_east) and a corner diagonal: no
  flickering stripes where the parts cross, from near and from a few blocks back.
- [ ] The diagonal arms do not look visibly fatter than the straight ones.

## Wire Junction (added 2026-10-28)

- [ ] A half-height galvanised steel box (creative tab, next to the duplexer): red wire into one side and blue out
  another power a radio from a battery as one circuit; any colour, any side, up and down too.
- [ ] Wire meets its sides with no gap; coax laid beside it does not connect.
- [ ] Looks right: lid rim and corner screws on top, seam and knockout on the sides.


## Bundles round bends, up lattice legs, power through a bundle (added 2026-10-28, afternoon)

Checked on a copy of the Structure Design world with the new `/rcdebug wires <from> <to>` (98 problems before, 9
after: all where a strand joins or leaves across others, now 0.01 px apart so they cross without flicker).

- [ ] A bundle laid round bends of both hands (north then east, north then west, and the rest): the strands keep their
  order round every bend, no crossing over.
- [ ] Strands peeling off one by one to a row of radios leave from the near side, without crossing the others.
- [ ] A bundle up the side of a lattice tower leg (beam, gap, beam): one straight line against the steel, no zigzag,
  no gaps between blocks; where the beams end it eases back to the block edge over one block.
- [ ] Where a strand has to cross others (it joins from the far side), it crosses without flickering.
- [ ] Microwave radios on a run of wire bundled with coax to their dishes power on (they were stuck on the coax only).
- [ ] Your existing bundles look right after loading (their turns are worked out when the chunk loads).
- [ ] Strands climbing a wall onto the floor above (the hole at the top of the tower) arrive in the same order they
  went up, no crossing at the lip. (Fixed later the same day: climbs now count in the turns.)
- [ ] A cable coming in from the side to a bundle running up an edge (the black coax halfway up the tower) meets that
  strand's run straight on, no slanting stub.
- [ ] (Later fix) The black coax halfway up the tower: the bundle's black strand turns onto the beam top and meets the
  wire lying there, no piece up at the top of the block. A bundle that was hanging from the ceiling there settles to the
  floor by itself when the chunk loads.
- [ ] Orange coming up through the hole at the top of the tower runs straight up and over, not slanting across.
- [ ] Down the radio row the strands peel off to their radios without crossing: a run now picks its own colour order
  (green first there). Bundles may show their colours in a different order from before; that is expected.
- [ ] The Jade tooltip on coax into a horn's other base blocks lists the horn (it showed "Connected Antennas: 0").
- [ ] Microwave radios wired from one battery of a bank turn on and draw on the whole bank: a battery's sides are now
  one circuit, so every wire touching it joins (before, the radios only had the one battery they touched, and it was flat).
- [ ] A charge controller now passes up to 180 FE a tick (20 panels in full sun; was 45). Several controllers set
  against one array split its power; the panels' tooltip still shows what the whole row makes.
- [ ] Holding a foothold, click the front of a foothold with open air behind it: the new one goes on top of the stack,
  in line with the beam, and again on the next click. Clicking a foothold's side still places one beside it on a wall.


## Microwave sites, beacon controller, horn feed and turning, stacked footholds (added 2026-10-28, late morning)

Needs a client restart and `./gradlew runData` (the beacon controller's name, loot and pickaxe tag).

- [ ] Three sites in a row (A, B, C), B with two microwave radios touching or on one power wire, one linked to A and
  one to C: a phone at A rings a phone at C, an H19 at A's beacon shows on C's H19, a fax goes A to C, and a direction
  finder at C gets A's bearing. `microwave_max_hops` set to 1 stops it.
- [ ] Tower Beacon Controller (creative tab, no recipe): looks like a thin beam, joins beams, and a redstone lamp or
  dust beside it blinks 2 s on, 2 s off; several controllers blink together.
- [ ] Coax laid against the outside of any of the horn's four base blocks connects (the radio finds the horn) and is
  drawn running across that base block's floor to the middle where the four meet; coax beside the horn higher up does
  not connect.
- [ ] Turning a horn left or right on the radio's ALIGN page turns the whole horn about the middle of its base; up and
  down stop at +/-45 degrees and do not tilt it. Its hitboxes stay where it was placed. It stays drawn when the block
  it was placed from is off screen, and breaking any part still takes it down.
- [ ] A foothold placed on top of another stays up with nothing behind it, in line with the one below (against a beam
  too); breaking the bottom one brings the ones standing on it down.
- [ ] No flicker on a beam just under a foothold's bottom rung (the rail now stops 0.01 px short of the steel).
- [ ] A redstone torch (or lamp, lantern) on top of a beam or the beacon controller: the beam reaches up into it, no gap.
- [ ] Back away from a horn: it stays drawn out to 256 blocks (or your render distance), not popping out at 64.
- [ ] Tower Beacon (shapeless: redstone torch, any tower beam, redstone lamp): a thin red light like a 300 mm TWR beacon. On a Tower Beacon Controller it blinks
  2 s on, 2 s off, glass glowing full bright at night, lighting the area (light 11); dark with no signal; the beam
  below reaches up into it. Looks right next to the thin beam (same width)?


## Tower beams: ground, walls, braces, footholds and coax (added 2026-10-28)

Needs a client restart (Java changes) and `./gradlew runData` first (the foothold's new `mount` blockstate).

- [ ] A beam on the ground reaches down to it (no gap under the core); one against a wall or under a ceiling reaches
  out to it, as iron bars do; a beam on its own in the air is still a straight post.
- [ ] A beam reaches up into an antenna, balun or pole mounted on top of it, and down into one hung under it.
- [ ] A diagonal brace is not drawn when any block (a wall, a fence, a wire, a foothold, a torch) stands beside the edge
  or corner it would run through; another beam there is fine.
- [ ] Footholds can be placed on the side of a beam (all three thicknesses), sit right against the steel, climb like
  a ladder there, and the outline matches; breaking the beam drops the foothold; on a full block they are as before.
- [ ] Coax (and power wire, and a bundle) laid up the side of a beam column hugs it, drawn against the steel, not at
  the block edge; the stub from the floor run to it reaches the run with no gap.
- [ ] At the top it climbs over the beam's top onto an antenna or balun standing on it (the radio finds the antenna).
- [ ] A run that carries on up past the top of the beams eases back to the block edge over one block, no step.
- [ ] Clicking the cable on a beam still selects it (its hitbox stays at the block edge, inside its own block).


## Radios on Sable ships (added 2026-10-28; GameTests pass without Sable)

Run `./gradlew runClient -PwithSable` (Sable 2.0.6 with Veil and its Rapier physics; add `-PwithCreate` for Create).
Build a small station on a platform (HF radio, battery, coax, a dipole on two antenna poles, a speaker), then make it
a ship with `/sable assemble area <from> <to>`. Another station stays on the ground for the far end.
`/rcdebug sable <pos>` on a ship block says where it really is. Checked on the dev server (2026-10-28): assembly
carries a wire strung between two poles, it survives aboard, and `/rcdebug sable` follows the ship through a teleport
and a 90 degree turn.

- [ ] The game loads with Sable, and with it absent (Sable Companion is bundled in our jar either way).
- [ ] After assembling, the station still works: power, coax, the radio hears the ground station and is heard.
- [ ] The dipole's antenna wires came along with the ship (both poles aboard) and still count as the antenna.
- [ ] Move or fly the ship away: signal strength follows the ship's real distance (not the far-away plot); past line
  of sight it goes to skip as on the ground.
- [ ] The radio's speaker audio comes from the ship as it moves, and a player talking next to the radio's mic on the
  ship still keys it.
- [ ] Radio, fax, tape player, telephone and microwave screens stay open while standing on the moving ship.
- [ ] A Yagi (or Moxon) on the ship: turn the ship and the far station's signal changes as the beam turns away.
- [ ] A direction finder on the ground points at the ship; one on the ship points at the ground station.
- [ ] An H19's APRS beacon from the ship shows the ship's real position on the ground station's map.
- [ ] A WEFAX receiver or weather station on the ship reports the weather where the ship is.
- [ ] A microwave dish on the ship: aimed at a ground dish it links, and turning the ship breaks the link.
- [ ] Sable's mass: batteries and tower footholds weigh the ship down, wires and poles hardly at all.


Reported 2026-10-27: two radios on 6275 MHz, dishes at AZ 179.8 / 359.0, both "NONE IN VIEW" with no RSSI. The meter
only listened for a radio of the other role (one end had to be Broadcast Master first) and said nothing about it.

- [x] Two powered radios on one frequency, neither made master: the ALIGN page already shows the far end's signal and,
  once it decodes, its callsign, so the dishes can be peaked before choosing a master.
- [x] The screens only say what the meter knows (maintainer, 2026-10-27 evening): `NO SIGNAL`, `TOO WEAK` (shows but
  will not link), `POOR`, `FAIR`, `GOOD`, on the ALIGN page and the STATUS page's RSSI line; a failed search or lost
  link says `NO SIGNAL: CHECK DISHES` or `SIGNAL TOO WEAK: ALIGN`. The why (no radio on the frequency, out of range,
  not facing, path blocked, each dish pair's distance and off-axis angles) is `/rcdebug microwave <pos>`.
- [ ] A dish just out of range, aimed right, still shows on the align graph as a weak signal (`TOO WEAK`) and fades
  out by a quarter past the range (`microwave_dish_range` 1500 by default; set it to 20 to try: at 20 blocks -70 dBm,
  22 still decodes (POOR), 23 too weak, 25 gone). Walking a pair apart, the grade goes GOOD, FAIR, POOR, TOO WEAK, NO SIGNAL.
- [x] The dish model now turns to where the maths points it: a dish set to AZ 0 looks north, 90 east, 180 south, 270
  west (it was drawn 180 off, so a pair "pointing at each other" by eye read NOT FACING). Two dishes aimed at each
  other by eye now show a signal. The horn is a plain block model and was not affected.
- [ ] The path is now checked block by block (`MicrowavePaths`), not on the HF terrain atlas, which blocked your two
  dishes over a one-block step: the yard layout (dishes on two-block poles, a dozen blocks apart over stepped snow)
  links. A tree trunk or leaves in the beam, or a wall, still blocks it (the beam leaves each dish from 0.75 blocks
  above its block; the four blocks nearest each dish never count).

## J-pole with coax beside it (added 2026-10-27, evening)

Reported 2026-10-27: a J-pole lost its texture transparency (the mount drew as a solid pink block) once coax was laid
beside it. The coax run-in model (`antenna_coax`) and the J-pole's upper half had no render type, so the multipart
mixed solid and cutout parts; both are cutout now, like the lower half.

- [x] A J-pole with and without coax beside it looks the same: see-through where the texture is, on all four facings.
  The Ionosonde and the 5-BTV with coax beside their base too (they share the run-in model).

## Thin wire, coloured wire, spaced bundles (added 2026-10-27, late; GameTests pass)

Maintainer's design: power wire is 1 px square now (coax stays 2 px), so wire and coax tell apart however dyed, and
seven strands fit in a bundle with a pixel between them. Sixteen dyed wires like dyed coax (`tools/coax_colours.py`
tints `wire_<colour>.png`; `tools/wire_assets.py` makes every part model, the dynamic models, blockstates and the 3D
wire items).

- [x] Plain wire: a 1 px line on the floor, up walls, over steps, hanging, through a passthrough; corners and stubs
  meet with no gaps; the hitbox still picks it up easily. The held/inventory wire is the 1 px cross.
  - ![junction between the two has a 1 px gap](testing_shots/2026-10-27-193801-junction-between-the-two-has-a-1-px-gap.png)
  - Note (2026-10-27): where the two wires meet, I did notice a little cube on its own that doesnt look like it's touching the rest
- [x] Coloured wire: the whole cable takes the dye (not a stripe); items in the Power section of the tab; dye a laid
  run; `8 wire + dye` recipe; red beside blue stays apart, plain joins both (`/rcdebug network`).
- [ ] Bundles: strands sit with a 1 px gap (wire 1 px, coax 2 px); red and blue wire may share a block, plain + any
  colour of wire may not (one shorted conductor); a 7-strand bundle fits; the join to a lone wire still eases over.
- [x] Faults on a coloured strand follow only its colour: a red loop shorts (and burns the red strand out of the
  bundle) while the blue beside it carries on.

## H19 needs power (added 2026-10-27, late)

- [ ] (Changed 2026-10-31: no wire of its own needed.) An H19 on a radio interface beside a powered VHF radio boots
  when opened, with no power wire to it. Switch the radio off: dark glass, no boot, keys do nothing (Esc closes); back
  on, it boots afresh. The block's READY / CONTACT / MSG lamp follows. Beside an HF radio it shows nothing (VHF only).
  A microwave radio serving it (touching or on its wire) counts as its radio when that is on. An H19 cabled to the
  interface only by data wire stays dark until a power wire with a charged battery reaches it.
- [ ] The redstone RFI sound comes from the radio only, mixing with its static; lightning crackle no longer sets it off.

## Redstone RFI sound (added 2026-10-27, late)

- [ ] `redstone_rfi.ogg` plays at an HF radio when redstone switches near its antenna, coax or power wire: a lever flip
  is a short burst that dies away, a clock keeps it going (the file loops), louder the more switching; silent on a
  quiet station and with the receiver muted. It sits on top of the static as the old placeholder hum did.
- [ ] (2026-10-30) Under a storm by a station with Another Weather Mod instruments (wind vane, rain gauge...) nearby:
  no more "pbbt pbbt" from the radio as their readings change. The RFI noise follows the radio's volume knob (silent
  at 0) and fades with distance like the static (the file is mono now); flying off, it is gone within about 16 blocks.
- [ ] (2026-10-30) The teletype and WEFAX printing chatter also fades with distance (mono now).

## Phone PABX, small fixes (added 2026-10-30, evening)

- [ ] Phone PABX: the size of a direction finder module, faces you when placed, the pabx.png front with a dark lamp
  that turns green once it has power. Every phone needs one now: a phone on a microwave radio alone says NO LINE.
  Recipe: iron, data wire, a comparator, redstone.
- [ ] Changing a PABX's first extension renumbers its phones within a second (the keypad screen is gone: see
  "PABX rework" above).
- [ ] Two sites linked over microwave, a PABX touching each radio (master numbers 601 and 602), a phone on each:
  601's phone dials 602 + the far extension and it rings; a bare extension does not. The phone screen shows NO. and
  its full number; the far end shows who is calling.
- [ ] The call timer counts up during a call (it stuck at 0:00).
- [ ] A repeater site's red tower beacon blinks (2 s on, 2 s off) in a freshly generated site, and in an already
  generated one it starts blinking within a minute or two of the chunk loading (it stood solid red).
- [ ] Breaking a WEFAX receiver (and a charge controller that is on) throws its own coloured particles, not the
  purple and black "no texture" ones.
- [ ] Antenna switch: the knob hub's octagon no longer flickers (z-fighting) from any angle.
- [ ] HF manpack screen: no overlap; with the handset out, the empty bay lines up with the divider and no handset
  edge shows; the BAT reading sits against the right edge of the window.

## WEFAX: two sheets, plain names, fax-tone bulletins (added 2026-10-27, late)

Maintainer's notes from testing: the chart was hard to read (which blob is what, where am I), and the solar bulletin
went out as an APRS burst.

- [ ] A printed sheet stays up on the screen until you click it (tooltip "Click to take the sheet"); it goes into your
  inventory. On the block, the maintainer's new model shows the "Message Ready" sheet sticking out of the tray while
  one waits and the "No message Ready" lip otherwise (`tools/wefax_models.py` splits the model; blockstate `ready`).
  Printing again with a sheet still in the tray pushes it out of the front as before. Breaking the block drops it.
- [ ] WX REPT now prints two sheets: the RADAR (rain screens, each storm "S3 SVR T-STORM" with its arrow, "YOU" at the
  station's ring, legend "LGT..HVY RAIN  N UP") and then a FORECAST sheet: the report lines, then every storm within
  1500 blocks nearest first ("S3 SVR T-STORM 420 NE" / "MOVING SE, ARR 12 MIN", or PASSES CLEAR, or FORMING). A
  tornadic storm reads SVR T-STORM until a tornado is actually down, then "TORNADO EF2". AUTO prints the same pair.
- [ ] A solar bulletin (processor answering SOLR WX, or a timed one) sounds like a fax going out, `wefax_bulletin_seconds`
  (20 s) of picture tones on every radio on the frequency, not a packet chirp; the WEFAX waits up to 45 s for it.
- [ ] Over the air, two radios on 7.050 MHz a few blocks apart, each with its own antenna, the WEFAX beside one and
  the processor (SOUNDER ONLINE, its radio on) beside the other: SOLR WX prints the bulletin FROM the processor's
  call sign. If not, `/rcdebug radio` on each and tell me what the processor's terminal says.


- [x] Right click a laid coax with a power wire in hand: both cables sit in the block, side by side, each a pixel or
  two off the middle, and the stubs still meet the block faces (no gaps at the joins to the next block or a device).
  A third and fourth kind (dyed coax) spread the ribbon further. The same coax colour, or sneaking, places next to it as before.
- [x] Runs through a bundle: straight, round a corner, up a wall (hug), over a step (climb), hanging from a ceiling.
  Each strand bends like a lone wire would; a lone wire of the same kind beside the bundle lines up except for the
  strand's sideways offset (expected jog).
  - Note (2026-10-27): the 1 px step where a strand leaves a bundle (the screenshot). Fixed the same evening: both blocks now meet a crossing at the same place, halfway between their offsets, and each stub slants to it (`WireLook.Faces`, a shear in `WireLook.Affine`); same for a strand entering a device (it eases to the centre) and for runs up a wall. Check the join in the screenshot, a bundle into a radio, a bundle up a wall into a lone wire, and a hanging bundle.
- [x] The hitbox outline is the union of the strands. Sneak + right click with an empty hand takes out the strand
  nearest the crosshair and hands it back; with two left and one taken, the block is a plain wire again. Breaking
  drops every cable. Pick-block gives the first strand.
- [x] Dye on a bundle recolours only the coax strand under the crosshair, along its run. Jade on a bundle lists the
  radios/antennas on its coax strands.
- [x] The strands' faces render in both light and dark, no z-fighting between strands, no missing faces where a stub was stretched.
  - Note (2026-10-27): though I wish they were separated by 1 px of space when they can fit

## Direction finder and commercial repeater on power (added 2026-10-27; GameTests pass)

- [x] A direction finder with no power wire: POWER does nothing visible (screen dark, lamp off). With a charged
  battery wired to the terminal, or to any module touching it, it boots and works as before; pulling the wire darkens it.
- [x] The commercial repeater's PWR lamp stays dark until a power wire reaches any of its four cells (the wire draws a
  stub into that cell); it then repeats as before. Wired straight to solar panels (no charge controller) it runs in
  daylight and on into the night on its battery, the beacon on cell 3 blinking while nothing comes in; it goes dark
  once the battery is flat (`/rcdebug radio` on the bottom cell shows the stored energy).

## Telephone handset, ring and wall phone (added 2026-10-27; GameTests pass)

The maintainer's `block/telephone.json`, `block/telephone_wallmount.json` and `item/phone_item.json` are in.
`tools/telephone_models.py` builds the off-hook models, the handset item's display slots and the blockstate from them;
rerun it after any re-export. (`phone_Item.json` was renamed to `phone_item.json`: resource names must be lower case.)

- [ ] Sneak + right click a phone with an empty hand: the handset (earpiece, handle, mouthpiece) leaves the model, the
  handset is in your hand, and a cord runs from the phone to it. A ringing phone answers.
- [ ] Third person (F5): the arm comes up to the face and the handset sits along the cheek by the ear, following the
  head up and down. Tune it in `SLOTS["head"]` of `tools/telephone_models.py` (+x is the player's right, -z the face),
  then rerun the script. First person, inventory and dropped: the handset centred and the right size (the other slots
  there). Left-handed holders still get it by the right ear.
- [ ] In a call with the handset up only the holder is heard at the far end, and the far end is heard at the holder's
  ear (about 3 blocks around them) rather than from the phone. With the handset down it is a speakerphone as before.
- [ ] Sneak + right click the phone with the handset: it goes back, the model shows it again, the call ends. A plain
  right click with it opens the keypad (to dial with the handset up). Walking more than 5 blocks off, or putting it
  away, snaps it back and ends the call.
- [ ] The ring: note-block bell gongs alternating every tick for 2 seconds, then 4 seconds quiet. Sounds like an old
  phone bell, not a buzz?
- [ ] Placing a phone against the side of a block hangs it on the wall: the wallmount model stood up (blockstate x 90),
  backplate on the wall, earpiece end at the top, on all four sides; the hitbox is the backplate plus the phone; the
  cord leaves the bottom of it. Off the hook, the handset goes from the wall phone too.

## Ionospheric Processor: SOLAR WX over the air, blackout alerts, terminal (added 2026-10-27; GameTests pass)

- [ ] Right click the processor: an amber terminal with the call sign (the placer's), SOUNDER ONLINE / NO IONOSONDE,
  the radio's frequency, any blackout forecast, what it has sent, and three controls (click or keys 1, 2, 3): timed
  bulletins (default off), blackout alerts (default on), send a bulletin now. A low terminal hum while open.
  - Note (2026-10-27): need to make a more obvious screen on the moel. Also, it's not finding a radio with a tuner in between it and the radio.. Also jei blocking it
  - Note (2026-10-27): fixed the same evening: a radio on the far side of a tuner or duplexer touching the rack now counts; the terminal is registered with JEI like the other station screens; the screen face is on the model bounty list.
- [ ] WEFAX SOLR WX far away, its radio on the processor's radio's frequency: "Asking for the solar weather...", packet
  tones out and back, then the bulletin prints with "FROM <call sign>". With no processor answering: after 15 seconds a
  low beep and "no Ionospheric Processor answered".
- [ ] Teletype SOLAR WX far away: prints "QRU SOLAR?", then the report under the processor's call sign, or NO ANSWER.
- [ ] A WEFAX or teletype touching the processor, or touching one of its radios, prints the report straight away.
  With the processor's ionosonde gone, the WEFAX says so.
- [ ] `/rcdebug solar forecast blackout`: the processor's screen shows BLACKOUT IN n MIN; a WEFAX hearing it (or beside
  it) beeps three times and prints SOLAR ALERT; a second alert within two minutes only beeps. With alerts switched off
  on the terminal nothing goes out. `/rcdebug solar` shows the next event once it is picked (5 minutes ahead,
  `solar_forecast_minutes`), and reports print "Blackout expected in about n min".
- [ ] With timed bulletins on, a WEFAX on AUTO prints each bulletin it hears.

## Finished items released to the creative tab (added 2026-10-27)

- [ ] A release build (not `runClient`) shows the speakers, tower beams, microwave dish, microwave radio, telephone,
  direction finders and their modules, the handheld DF, the HF manpack, the Ionospheric Processor and the microwave
  horn in the RadioCraft tab: nothing is dev-only now.
- [ ] In `runClient`, nothing shows the blue "dev" tile; batteries, solar panels, the repeater, duplexer,
  radio interface, H19, all-band radio and microphone no longer do.

## VHF antennas deaf after a restart (fixed 2026-10-27)

- [ ] After restarting the game (or leaving and rejoining the world), J-poles, slim jims and Yagis placed before the
  restart still hear VHF: base stations, direction finders and the commercial module all work without re-placing
  anything. `/rcdebug antenna <pos>` shows `antennaNetwork=radiocraft:vhf onTheAir=true radiosOnCoax=1`; the radio's
  `/rcdebug radio <pos>` shows the same line under each antenna. A J-pole from an old save that was on the HF network
  moves itself back within two seconds (a WARN line in the log says so).
## Batteries share charge evenly (added 2026-10-27; GameTest written, not yet run)

- [ ] Two or three large batteries on one wire with a charge controller: they all fill together, each slower than
  one alone, instead of one after another. Under a load they all drain together too (open each one's screen).
- [ ] Charge levels are the same after leaving and rejoining the world.

## Hand mic click, VHF base station lamps, COAX SHARED (added 2026-10-27)

- [ ] Hand mic (sneak + right click a radio with an empty hand): a click when you start talking, a softer one on
  letting go, heard at the player.
- [ ] VHF base station (all three grades): the red TX lamp lights while keyed, the green RX lamp while a signal comes
  in; both line up with the dark lamps painted on the front, on all four facings and hanging from a ceiling.
- [ ] A radio whose antenna coax also reaches another radio or a DF module shows COAX SHARED on its readout (it hears
  nothing then); it clears when the other is broken or the coax between them is cut. New GameTest
  `CoaxSharedGameTests` (compiled, not yet run).

## Commercial VHF antennas: Ringo and folded dipole (added 2026-10-27; GameTests written, not yet run)

- [ ] VHF Ringo (`tools/commercial_vhf_antenna_models.py`): two blocks, white radiator, band clamp, the open matching
  ring near the foot with the coax coming off it. Looks right from all four facings; item icon reads in the hand and
  in the creative tab.
- [ ] VHF folded dipole: mast on the side the player faced when placing it (against a tower), two arms with tall
  narrow folded loops standing off away from it, coax down the mast; the two blocks join up without a seam.
- [ ] Both hear and talk on 2m like a J-pole, a bit further; the Ringo crafts from ` I `/` A `/`DBC` (iron ingot,
  antenna wire, diamond, iron block, coax). Needs `./gradlew runData` for the recipe, loot, tags and names.
- [ ] Lightning on them does damage about one strike in four.

## Solar panels under severe weather, WEFAX through station gear (added 2026-10-27)

- [ ] With Another Weather Mod, panels under hail, a blizzard or sandstorm, damaging wind, a tornado or a hurricane
  make half again what they make in rain (`solar_panel_severe_multiplier`, 0.5). Checked every 2 seconds.
- [ ] A WEFAX receiver works with a radio, TNC and H-19 in a row beside it (it reaches the radio through the station
  gear); GameTest `receivesThroughTheStationGear` covers this, not yet run.

## FM squelch tails (added 2026-10-27)

- [ ] When an FM signal drops (someone lets go of PTT, a repeater unkeys after its hang time), VHF base stations,
  all-band radios in FM, handhelds and wired speakers give a short burst of static: about 120 ms and crisp after a
  strong signal, up to 200 ms and louder after a weak one. Not mid-sentence on a steady PTT; with voice activation a
  pause between phrases may tail (as VOX does).
- [ ] A repeater holds its carrier through its hang time (its TX lamp stays lit), beeps, then drops, and listeners hear
  the tail only then. GameTest `holdsTheCarrierForItsHangTime` covers the hang; not yet run.

## FM fade on VHF (added 2026-10-27)

- [ ] Walking a handheld (or moving a base station) away from a VHF transmitter: the voice stays at full volume, hiss
  rises under it past about 70% of the range, then it cuts in and out to static, then only static, then silence.
  Block radios, wired speakers and handhelds alike. No separate client hiss any more.

## Handheld TXT / RXT tone keys (added 2026-10-27; unit tests written but not yet run)

- [ ] On the VHF handheld (all three grades) the TXT and RXT keys light on hover and open "TX Tone" / "RX Tone";
  the arrows step the tone there, and pressing the same key again (or ENTER) goes back to the frequency.
- [ ] ENTER from the frequency opens the Offset page, ENTER again goes back; typed frequency entry still ends on ENTER.
- [ ] A TX tone alone opens a toned repeater while the handheld still hears untoned stations; an RX tone shuts it to
  stations without that tone. The frequency line shows T and R markers. Retuning keeps tones and offset (it used to
  reset them).

## Direction finder lock sounds (added 2026-10-27)

- [ ] A beep when the signal is first heard; each module then takes about 2.5 seconds to lock, with a low beep and a
  lock click as it does; once every module has locked, a DING ding DING chime (high, low, high). Heard at the block,
  screen open or not.

## Microwave dishes placed level (added 2026-10-27)

- [ ] A freshly placed satellite dish faces the way you face and sits level (elevation 0), not tilted down where you
  looked; two placed facing each other link without touching them. Hovering the coax (Jade) now lists the microwave
  radio and each dish with its azimuth and elevation.

## Microwave alignment rework (added 2026-10-27; unit tests pass, GameTests compiled but not run)

- [ ] Dishes are drawn the way the link maths sees them: azimuth 0 faces north, elevation 0 is level (before, the
  model was drawn turned 180 degrees and tilted 40 degrees up from what the maths used). Check a dish at AZ 090 faces
  east, EL +20 looks up, EL -20 down. Dishes already in a world keep their numbers, so they may now be seen pointing
  away or down: turn them on SETUP or ALIGN.
- [ ] A newly placed dish faces back at the player who placed it, level: standing between two spots and placing a dish
  at each makes them face each other.
- [ ] Rough aim (each dish within 90 degrees of the other) reads something on ALIGN, as low as -118 dBm, with FAR END
  "SIGNAL, NOT DECODED". Peaking one dish, then the other, raises it; past -90 the far end's callsign shows (DECODES)
  and a search finds it; past -75 it reads GOOD. Two dishes a dozen blocks apart link even with one roughly aimed.
  A master's ALIGN page reads a would-be subscriber on its frequency too, before any link.
- [ ] A search is one sweep (about `microwave_search_seconds`), its line shows the real RSSI with a bar and DECODES /
  TOO WEAK rather than made-up numbers. An established link holds down to -93 before dropping.
- [ ] Ticks as the boot screen prints each line and a beep at READY; soft ticks as each page draws in; two rising
  beeps when switched on with the screen open.
- [ ] Switch a radio on, leave and rejoin the world: it comes back on, and a confirmed subscriber finds its master
  again by itself (it keeps retrying while the master is not up yet).
- [ ] A link below -75 dBm (marginal) makes telephone calls over it break up, like interference does.

## Dyed coax (added 2026-10-26, late)

- [ ] Sixteen dyed coaxial wires in the creative tab (a coloured stripe along the jacket; the coil icon striped).
  Craft eight coax round a dye for eight dyed. Use a dye on laid coax: the whole run that joins it takes the colour
  (sneak: only that block).
- [ ] Runs of different colours laid side by side are drawn apart and stay separate networks: the direction finder
  layout with one colour per module run now shows each module READY with its own antenna. Plain coax still joins
  every colour (use it to tee a dyed run off on purpose). Coax climbing walls and hanging from ceilings follow the
  same rule. Jade's coax readout covers dyed coax.

## WEFAX receiver, creative module setup, fixes (added 2026-10-26, late)

- [ ] WEFAX receiver: its screen is the machine from tools/wefax_gui.py. Paper used on it fills the supply (the window
  shows empty to full in five steps; hover for the count). With a radio with an antenna beside it the power lamp
  lights. WX REPT: a sheet rises out of the slot behind the drum over twelve seconds, the chart appearing on it top
  first, the drum motor running, then a ding and the chart (a map) drops out of the front; pressing again prints the
  next chart (radar, forecast, surface analysis). SOLR WX prints the solar report as a text sheet. AUTO stays down
  while on, and prints a forecast by itself when a hazard is here or forecast. JEI moves aside for it.
- [ ] The WEFAX receiver's block model still has rotations Minecraft 1.21.1 cannot load (see todo): it may show as
  missing until it is re-exported.
- [ ] Commercial radio module in creative: right click opens a setup screen (program button, frequency in MHz, SET);
  in survival it still says it is sealed.
- [ ] Commercial repeater: right click no longer disconnects you (the menu refused the repeater's subclass).
- [ ] H19: no snare during boot; typing keys tick softly, the side keys keep the stone click.

## Commercial repeater, commercial radio module, tapes (added 2026-10-26, night)

- [ ] Commercial repeater (creative only): places as a four-block rack facing you, the parts stacked right (batteries,
  radio with lamps, cavities, controller). Right click any part: the VHF repeater's screen. Switched on it runs with no
  battery or duplexer; the PWR lamp and the controller beacon light, RX while someone comes in, TX while it repeats.
  Heard from farther off and cleaner than a VHF repeater at the same spot. Breaking any part takes the rack down and
  drops one.
- [ ] Commercial radio module: a four-block cabinet; right click says it is sealed; its PWR lamp lights and TX
  flickers as it sends. `/rcdebug module <x y z> <program> <MHz>` sets it (numbers, static_beacon, dead_air,
  df_beeper, cw_beacon); `/rcdebug module <x y z>` says what it is doing. Tune a radio to it and listen: the numbers
  station's chime then Morse figure groups every five minutes, static bursts, a dead carrier with the odd kerchunk,
  a beep every three seconds (a direction finder homes in on it), or VVV DE and a long dash. HF modules are heard far
  off on skip. Broken in survival it gives one rare part (a pure encased crystal, the all-band board or the
  ionospheric processor), never itself.
- [ ] Tapes: put .ogg files in config/radiocraft/tapes (and numbers/ inside it); `/rcdebug tapes` lists them,
  `/rcdebug tapes give <file>` hands you one as found, `/rcdebug tapes reload` after changing files. A numbers
  station module then reads out a numbers/ recording instead of Morse (Morse the first time while it decodes).
- [ ] Tape reels: twelve reel colours in the creative tab, sixty colourways in all (reel and label tinted).
  Tooltip: Blank, the found tape's name, or Recorded m:ss.
- [ ] Tape player: use a reel on it to load it, sneak with an empty hand to take it out, empty hand for the controls.
  PLAY plays it out loud nearby and into any radio touching it (the radio keys up and transmits it); the reels turn
  smoothly while it plays or records and stop after. MODE: MANUAL, LOOP (again a second after it ends), INTERVAL
  (every n minutes, - and +, shift for tens), REDSTONE (a pulse plays it once). REC on a blank or recorded tape:
  talk within six blocks, STOP (or a minute) saves it; it plays back telephone-quality. A found tape will not record.
- [ ] The tape player's screen is plain (wood, a dark window, vanilla buttons) until it has panel art.

## Playtest fixes (added 2026-10-26, evening)

- [ ] JEI no longer covers the microwave radio, telephone or direction finder screens (it moves aside as it does for
  the H19).
- [ ] Weather station: the vane swings round smoothly to point into the wind (`/awm here` gives the bearing) and holds
  still in a calm; the cups spin smoothly, faster in stronger wind, spinning up and coasting down rather than
  jumping. Check it from all sides and that it renders at a distance.
- [ ] H19: a quiet fan-and-transformer whirr while its screen is open (spinning up over a second, gone on close).
  Page changes and station details tick softly instead of clicking; the map's own refresh every ten seconds gives a
  quiet beep and a couple of ticks, nothing louder. Typing still clicks.
- [ ] Direction finder: no button clicks or drum; keys tick softly, the power switch gives two quiet tones, and its
  whirr is louder than the H19's. Microwave radio: the same soft ticks and a whirr; STATUS and SETUP give a falling
  and a rising two-tone, power a low two-tone, a rising blip sweep while searching, a slow low pulse while
  broadcasting, a chime when a master is found, a rising chirp when linked, falling tones when lost or not found.

- [ ] Volume on every radio: the 10m radio's top-right knob, the all-band radio's knob under the frequency buttons
  (and its lower-right knob is now mic gain), and the VHF receiver's right-hand knob turn the speaker volume, showing
  the percentage while turned or hovered, 0 to 200 %, as the other radios' gain dials do. The VHF repeater: scroll
  over its SPEAKER button (the tooltip shows "volume N% (scroll)"). Each setting survives closing the screen and a
  restart, and changes how loud the radio's own speaker (and wired speakers) plays.

## Alternator, with Create (added 2026-10-26)

Run with Create: `./gradlew runClient -PwithCreate` (Create Crafts & Additions not installed).

- [ ] The alternator is in the RadioCraft creative tab; it looks like Crafts & Additions' alternator (orange coil),
  facing the way it was placed, with half shafts out of both ends that turn with the network.
- [ ] Driven by a creative motor or water wheels next to a large battery, the battery fills; Create's goggles show
  "Energy Production" in FE/t at the current speed (360 FE/t at 256 RPM by default) and the stress it puts on the
  network. The recipe shows in JEI as mechanical crafting (andesite alloy, iron sheets, antenna wire, a shaft).
- [ ] With Crafts & Additions installed too, only its alternator exists (no second one from RadioCraft).

## Weather station and APRS WX (added 2026-10-26)

- [ ] Weather station (APRS tab in creative; copper, quartz slabs, a comparator, iron bars): the model shows, the cups spin faster in stronger wind
  (`/awm hazard severe` nearby) and stop in a calm. Right click: wind, gusts, temperature, humidity, pressure, rain
  in the last hour.
- [ ] An H19 within 64 blocks of it: SETUP's SYMBOL line reads `WX (WEATHER STATION n BLOCKS AWAY)`. Its beacons
  show at another H19 with the WX symbol (a circled W on the map) and a `WX 270/12G18 64F 85% 1004MB` line in
  STATIONS HEARD; the station's details add WIND and WEATHER lines above the buttons, and the comment shows on its own.

## Microwave links, telephone, HF manpack, handheld direction finder (added 2026-10-26, second round)

- [ ] Microwave dish (the old satellite dish, recipe III / IC_ / IWI): placed, it points where you look, up and down
  included. Check the model's bowl really faces that way and tilts the right way for elevation (the renderer turns it
  by the exact azimuth and tilts the `lnb_arm` bone); the setup page turns and tilts it a degree at a time.
- [ ] Microwave radio: green terminal behind the beige rack front. POWER boots it; SETUP sets callsign, hex ID,
  frequency (5925-7125 MHz, FREQ knob scrolls) and the dishes; STATUS has BROADCAST MASTER LINK, SEARCH FOR MASTER LINK
  (spinner, progress bar, sweeps) and the confirm prompt with both callsigns. A second dish within 30 MHz in view shows
  INTERFERENCE. The block's screen glows while on.
- [ ] Over a link: an H19 at each end shares APRS (the far end also re-sends on its radio with hops reset); direction
  finders on the same frequency get each other's bearings (FIX page, L rows) and fix automatically.
- [ ] Telephone: EXT shows its extension; key another extension and CALL: the far phone rings (bell), CALL answers,
  voices near each phone come out of the other clean and a little fuller than SSB; HANG ends it. EXT + number + CALL
  changes the extension. Phones work touching a microwave radio or on its power wire.
- [ ] HF manpack (recipe: bundle, leather chestplate, 20m QRP, any 10m radio): sneak right click wears it (chest; or the
  Curios back slot), the pack draws on the back (check its position), right click in hand or the J key opens the
  olive panel: PWR, SSB/CW, BAND 10/20, FREQ knob (wheel or click halves), AF, MIC, PTT, TX lamp, battery %. Static
  follows you while it is on; voice from a station on frequency comes out where you stand; CW sends with PTT or the CW
  key and plays back. Click the handset: the panel closes and you hold it; hold right click to talk; sneak right click
  hangs it up.
  - Note (2026-10-27): shift right click doesnt open ui while on curios backpack slot.
  - Note (2026-10-27): by design the key (J, Open Worn Manpack) opens a worn pack and sneak + right click with it in hand wears it. Added the same evening: sneak + right click on nothing with an empty hand while wearing it (chest or Curios) opens the panel too. In hand, a plain right click opens it; if that does nothing in creative, say so, I could not reproduce it from the code.
- [ ] Handheld direction finder: right click sets a frequency; held while a station transmits there within line of
  sight range, it beeps and blinks, faster as you face the station. No audio comes through.
- [ ] Wired speakers play the radio's static as well as its voice (the loudspeaker's carries further).

## Direction finder, speakers, tower beams, microwave horn (added 2026-10-26)

All in the development creative list. The GameTests cover the server side (locking on, bearing, module states, speaker
lookup, horn placement); these need eyes and ears.

- [ ] Direction finder: place one, stack 3 to 8 Direction Finder Modules against it (or against each other), run
  coax from each module to an antenna of the same type, open it. POWER on the side panel: the amber screen boots with
  the DF art and a module and antenna report; the PWR lamp lights; the block's own screen shows the amber prompt.
- [ ] DF page: arrows (or the scroll wheel, or clicking the < > marks) tune, up and down change band, F types a
  frequency in MHz. Have someone transmit on it: *RX* flashes, the meter moves, the modules lock one a second
  (their lamps go green, a module with no antenna or a different one is red), and from three locked the compass
  needle appears with its error wedge, narrowing as more lock. The block's screen switches to the compass.
- [ ] FIX page: A types another station's X, Z, bearing and accuracy; D deletes the selected one. With two stations
  at least 48 blocks apart and bearings crossing well, a blinking point in a box marks the fix, with its X, Z, box
  size and range on the left; otherwise the reason it cannot fix.
- [ ] Map Enabled Direction Finder (a direction finder crafted with a World Map Module): the boot loads the map off
  the floppy with a progress bar and disk noises, and the FIX page draws an amber map (as the H19's, recoloured)
  under the stations, lines and box. Journeyman cartographers sell the module (12 emeralds and a compass); its item
  texture is a placeholder floppy.
- [ ] Speakers: a radio sounds thinner on its own than before (80% of the passband), a little fuller with a desk or
  large speaker touching it, and as it used to with a premium speaker touching it. Wall speakers and the loudspeaker
  on the radio's power wire each play the voice where they stand: the wall speaker a bit rough, the loudspeaker loud,
  distorted and audible out to 64 blocks. Static still only plays at the radio.
- [ ] Tower beams (thin, normal, thick): beams join every beam they touch, edges and corners included; a 2x2 of
  columns braces itself; a lone beam is a straight post. The hit box covers the core and the straight arms.
- [ ] Microwave horn: placing it puts up the whole 2x2x4 horn facing where you look (clicked block is its back left
  corner); breaking any part takes it all down and drops one. The item shows the whole horn at half size.

## Weather and WEFAX charts (added 2026-10-25)

Dev runs have Another Weather Mod (from `libs/`); its own checklist is in its repository's doc/NeedsTesting.md.

- [ ] `/rcdebug wefax radar` (and `forecast`, `surface`) gives a map: hold it and put it in an item frame. It reads as
  a fax print: white paper, black ink, a header with the chart and time, a dotted grid, coastlines near the sea, the
  station's ring in the middle. Its tooltip is the text report. A cartography table copies it.
- [ ] With storms about (`/awm approach thunderstorm 5`, `/awm spawn supercell` a few hundred blocks off): the radar
  shows dot screens getting darker toward each storm's core with its symbol, label and a short motion arrow; the
  forecast shows tracks with ticks every 15 minutes and dashed outlines where the storms will be; the surface chart
  shows rings of isobars round each storm with an L and its pressure.
- [ ] `/rcdebug wx` reads well; `/rcdebug wx aprs` gives a `_MMDDHHMMc...` packet.
- [ ] With a hurricane, a tornadic storm and fog about (`/awm spawn hurricane_4`, `/awm spawn tornado_3`,
  `/awm spawn dense_fog` some way off): charts label them "S12 C4", "S13 EF3" with a funnel symbol, three bars for
  fog; `/rcdebug wx` names them HURCN 4, TOR EF3, FOG and lists TORNADO, DENSE FOG, HEAT WAVE or COLD SNAP hazards.
- [ ] Solar panels make less power only where it rains, and the slim jim only cuts out under a thunderstorm.
- [ ] `/awm hazard hurricane` beside an antenna on poles: over a few minutes some parts blow down and drop as
  items; the antenna stops working until they are put back.

## Teletype machine screen (added 2026-10-24)

- [ ] The screen is `teletype_machine.png` with the paper (`teletype_machine_paper.png`) coming up out of the platen
  under the paper guide, which covers its edges and the slot. Text prints on the paper in even columns, 32 to a
  line (the callsign included), and lines up with the sprocket holes' spacing well enough.
- [ ] Each line feed slides the paper up a line (no jump) with a "ssht" (book page turn) and the bell on a new line.
  A fresh roll shows its torn top edge; once the paper reaches the top of the copy holder the fold shows it going over
  and behind, and it keeps feeding. Every fifth page has the crease.
- [ ] Typing: characters go out about 6 a second (two of the 15 keystroke sounds each, so they run together) and
  print as they go (type fast and watch it catch up). Enter (even on an empty line) rings the bell; a long line
  wrapping does not. The mouse wheel pulls the paper back down to read older lines. Hovering the machine shows the
  paper left. No callsign is needed: operators type theirs.
- [ ] A listener on the frequency (voice mode) hears the RTTY tones, quieter than before. The receiving teletype
  runs its motor (teletype_background, looped) and clacks as it prints, screen open or not. Opening the screen
  plays a few power-up relay clicks, then loops the motor: twice as loud at
  each keypress, fading back over 5 s.
- [ ] Two players typing at once on one frequency: the stations hearing both print mostly garbage or nothing. A
  teletype being typed on prints nothing it hears.
- [ ] `/radio moderateall`: each line typed shows as `[RTTY 14.080] Player (CALL): text` when it ends or the typist
  pauses, not character by character.

## H19 terminal screen (added 2026-10-24)

- [ ] Text is neon green, in a fixed 64 by 25 grid, inside the dark glass of `h19_gui.png` and nowhere else; the
  scanlines and green cast sit on the glass only. Characters line up in columns (the APRS art is not ragged).
- [ ] Turning on a radio on the H19's bus, then opening the H19, boots it: bell, ROM lines, `> APRS` typed at the
  prompt with key clicks, disk seek clicks, the APRS art, then TNC/radio/frequency/callsign/message lines and READY.
  Opening it again does not boot. Enter or Space skips; Escape mid-boot closes and it boots again next time.
- [ ] INBOX: arrows, Page Up/Down, Home/End, scroll wheel and clicks move the selection; Enter or a click opens a
  message; new ones (since the last opening) have a `*`. READ: REPLY fills MSG's TO, SHOW ON MAP centres the sender
  (or says no position heard), BACK; up/down step through messages.
- [ ] MAP: green terrain draws in top to bottom with clicks; contours, brighter index contours, water dots, bright
  shoreline, tree stipple, dotted grid, faint dots where nothing is loaded. Each station has its symbol's icon (house,
  person, car, boat, horse, tent, tower, digi), home has a ring. Arrows and dragging pan, +/- and the wheel zoom, C
  fits, Tab selects the next station, Enter or a click opens its details box (MESSAGE / MAP / CLOSE).
- [ ] LIST: stations heard with symbol, distance, bearing, age, path and comment; Enter or a click opens the details
  box; M shows on the map, S messages it.
- [ ] MSG: typing in TO and TEXT (the E key types, it does not close), Tab/Enter move on, Enter in TEXT sends with a
  modem chirp; CLEAR; sent messages are listed. SETUP: beacon on/off, beacon now, symbol (left/right), comment edit,
  buzzers, digipeat, REBOOT.
- [ ] It opens on MAP. Number keys 1 to 5 (MAP, INBOX, LIST, MSG, SETUP) and the top-row tabs change pages, as do the
  side buttons (MAP, INBX, LIST, MSG from `h19_gui_buttons.png`): grey, white while hovered or showing, and flashing
  red for news: INBX for a message since the inbox was last up, MAP and LIST for a newly heard station.
- [ ] S opens SETUP from any page (not while typing). SETUP: CALLSIGN (type one, or blank and Enter for your own)
  sets it on the bus's radio interfaces; REMOVE STATIONS AFTER (10/30/60/120/240 MIN, NEVER; default 30) drops
  stations not heard for that long. A new H19 beacons and a new radio interface digipeats by default
  (`aprs_beacon_default`, `aprs_digipeat_default`); placing or first opening an H19 gives an interface on its bus
  with no callsign yours.
- [ ] With no callsign on the bus, the boot stops at CALLSIGN with a CALLSIGN> prompt: typing sets it, Enter alone
  uses yours; it then carries on booting.
- [ ] Message acks: a message to a station that is on shows SENDING then SENT once its ack comes back (direct or
  through digipeaters); to one that is off, RETRYING 1/5 ... 5/5 every 20 s, then FAILED (dim).

## Proposal batch: gains, CW keys, callsigns, moderation, sporadic E (added 2026-10-24)

- [ ] Gain and mic gain dials on every block radio: hover shows the percent; gain turns voice and static up and down,
  mic gain how loud you go out. Which dial is which on the 10m and the VHF receiver (unlabelled, left alone)?
- [ ] HF radio in CW: hold Left Alt in the screen to key. With `cw_keying = CHAT_MORSE`, type in the box under the
  radio and press Enter: it goes out as Morse at about 24 wpm, "Sending..." while it does.
- [ ] A new player right-clicking a radio gets the "Choose your callsign" screen; Later lets them through; a taken or
  malformed callsign shows why. `/callsign claim K1ABC` works too.
- [ ] Place a repeater: it gets an X#XX callsign (digit 0 near spawn). In its CW ID panel, "Make one up" and "Mine".
- [ ] `/radio moderateall`: hear players on radios clean, with "On air: name (call), freq" over the hotbar; RTTY and
  APRS packets in chat. `/radio moderate <player>`, `/radio moderate off`.
- [ ] In a blackout the radio's S-meter jumps about instead of sitting at S9+.
- [ ] `/rcdebug solar sporadic_e on`: 2m reaches stations 600-3000 blocks away.

## Electrical hazards, battery fires, lightning rod antennas (added 2026-10-24)

- [ ] A 2x2 square of power wire on a charged battery: smoke, flames, the wire burns away after about 4 s. Is a
  sloppy 2x2 of wire shorting too easy to do by accident? (Any closed loop of power wire counts.)
- [ ] Power wire into water: a mob or you in the water nearby gets hurt each second; the battery drains fast.
- [ ] An open end of power wire in the rain: sparks and a faint crackle; standing on it zaps you. Are the particles
  and the redstone-torch burnout sound right, or does it want its own sound (bounty)?
- [ ] Set a large battery alight (flint and steel, fire or lava beside it): flames, fire around it, it spreads to
  touching batteries, gone after 45 s. Pour water on it: steam and hissing; take the water away: flames again.
  Four or more touching batteries: an explosion when the first burns out.
- [ ] `/summon lightning_bolt` next to power wire that runs to a battery: the battery catches.
- [ ] Coax from a VHF radio to a lightning rod: it works as a poor antenna (SWR 6, short range). In a thunderstorm,
  strikes now and then land on antennas.

## Desk microphone (added 2026-10-24)

- [ ] Beside a radio: hold right click on the mic to talk (a click on press), let go to stop. Sneak and right click
  locks talking on (a message says for how long); again to release. After 30 s it lets go by itself with a low blip.
- [ ] Your voice is full level within a block or two of the mic, half at 3 blocks, gone past 16: walk away while
  locked and listen on another radio. Someone else standing by the mic is picked up too.

## Controller switch, Curios, repeater sleep, parrot pick, FM hiss, delta loop and Moxon (added 2026-10-24)

- [ ] Charge controller: the switch on its screen now works. Off, the panels stop charging the batteries (the green
  lamp goes out); on, they charge again. New controllers start on. Controllers placed in older worlds were off and
  did nothing before: they now need switching on once.
- [ ] Curios: a VHF handheld (or a small battery) goes in the belt slot. A worn handheld plays what it hears, drains
  its battery, and talks while the "Talk on worn radio" key (B, Controls under RadioCraft) is held, with a click.
- [ ] Repeater: asleep until used; after use it IDs every interval even with nobody talking, then after an interval
  of silence IDs once more and goes quiet. Try it with a short ID interval in its settings.
- [ ] Parrot controller between two radios: sneak and right click with an empty hand links the other one (the cord
  moves, a message names the side).
- [ ] FM (VHF radios, all-band in FM): silent between signals and on a strong one; a weak, distant station has hiss
  under it that clears as you get closer. Needs ears.
- [ ] Delta loop: a closed triangle of wire on a 4:1 balun, about 21 blocks round on 20m, 4+ blocks up (Jade names it
  delta_loop). Lower than 4 it still works with a worse SWR. Try the guide's flat build.
- [ ] Moxon: the guide's 20m build (7 x 3, 1:1 balun, tails, 1 block gap, reflector) is recognized as moxon and is
  much louder in front than behind.

## Guide fixes, duplexer on top, tuners beside radios (added 2026-10-24)

- [ ] Every station picture in the guide now has solar power; flip through them (first station, HF radios, VHF base,
  repeater, APRS, teletype, antennas) and check they read well. Desk Gear lists the interface, H19, teletype and WEFAX.
  The new Solar Weather section holds the weather, the Ionospheric Processor and WEFAX.
- [ ] J-pole: coax laid beside its foot shows a run in to the mast, like the ionosonde.
- [ ] Repeater with the duplexer on top: coax from the duplexer's side at its height, or on the ground straight behind
  the repeater (under the duplexer's draped cable), both reach the antenna. The repeater page shows the second.
- [ ] A new duplexer's screws show 146.940 / 146.340 and turn straight away, even before it sits on a repeater.
- [ ] A manual or automatic tuner placed against a radio (no coax between) reaches the antenna on its other side.
- [ ] The tuner's state line and antenna SWR now sit centred above the panel; "out of range" reads "Antenna too far off
  to match: tuned as close as it goes".

## Tower footholds and the guide on first join (added 2026-10-24)

- [ ] Place footholds up a wall like ladders (both kinds): the rungs face out from the wall, climbing works, and you
  can stand on the top one. The item looks right in the hand and inventory.
- [ ] Set `tower_foothold_break_chance` to 1 and climb old ones: each gives way as you reach it with a high metal
  clang, the rung shows bent, you drop through, and mining it gives nothing. Crafted ones never break.
- [ ] A new player (or a fresh world) gets the RadioCraft guide on first join, once only (not again on rejoin or after
  death). `/radiocraft guide` and `/radio guide` give another.

## Terrain (added 2026-10-24)

- [ ] Two VHF stations or handhelds either side of a real hill: the signal is much weaker than on open ground, and
  comes back when one climbs the hill. `/rcdebug terrain path <from> <to> 2m` shows the loss in dB.
- [ ] The same hill on 80m barely matters; on 10m and 20m a cliff or mountain wall on the path costs a few dB.
- [ ] A 20m station at the bottom of a deep valley loses skip across the valley walls but not along the valley; 80m
  keeps working. Compare against a hilltop station.
- [ ] A handheld indoors or in a cave is weaker than outside (about 10 dB on VHF).
- [ ] Builds show up: a wall built between two stations weakens them within a few seconds (chunks are re-read in
  turn), and breaking it down brings the signal back.
- [ ] `/rcdebug terrain` on a busy server: average microseconds per signal path stays low (a few us), and the map's
  memory matches the explored area. `terrain_enabled = false` in the server config: no difference from before.

## Hand mic on a cord, 10m skip, duplexer desense (added 2026-10-24)

- [ ] Sneak-right-click a radio (HF, QRP, all-band, VHF base station) with an empty hand: a Hand Microphone appears
  in your hand with a thin dark cord sagging from the radio to it, in first and third person. Another player sees
  the cord too. With your hand full you get "Empty your hand to take the microphone".
- [ ] Hold right click (looking away from the radio): the radio keys up, your voice goes out, and the radio's own
  receiver goes quiet. Let go and it unkeys.
- [ ] Walk more than 5 blocks away, switch the mic out of your hands, or log out: a snap sound and the mic is gone.
  Right click the radio with the mic: it hangs up. Q to drop it: it vanishes. A second player sneak-using the radio
  while it is out: "Someone else has this radio's microphone".
- [ ] 10m: on a great or good day a 10m station 2500-8000 blocks away comes in by skip; on an average day it does
  not. (Delete `10mm_*Skip*` from old server configs or set them: they keep zeros.)
- [ ] Repeater: turn a TX screw onto the input frequency; while the repeater transmits, users get much weaker in.

## Patchouli guide book (added 2026-10-23)

- [ ] In a dev client (Patchouli is in the dev runs), craft a book and wire, or take the "Radiocraft Handbook" from
  the creative tab. Every category and entry opens without "error" pages; links jump to the right entry.
- [ ] Each build page renders the structure (radios facing their coax, coax drawn connected, the 5-BTV's segments
  stacked, two-tall antennas whole) and Visualize shows ghost blocks you can build into until it completes.
- [ ] Read the text for anything wrong or unclear; the book is in
  `src/main/resources/assets/radiocraft/patchouli_books/guide/en_us`.

## Manual tuner, automatic tuner, duplexer screws (added 2026-10-23)

- [ ] Antenna tuner screen: three dials (drag or scroll), each with its reading above it. With a radio on and an
  off-resonance antenna, the lamp in the middle window blinks red, faster as the dials get closer; below SWR 3 it
  blinks yellow-green (tinted: the GUI texture has no green lamp sprites yet) and at the right readings it is solid
  green and the readout says Tuned. Moving far up the band or changing the antenna needs a retune.
- [ ] Automatic tuner (your model): on the coax with power, switching the radio on gives relay clicks speeding up
  ("click... click... clickclickclick") for a second and TUNING then TUNED on its display. A 2+2 dipole on 40m
  (SWR 10) gives slow clicks for 6 s, FAIL on its display and TUNE FAIL on the radio's screen. Check the text sits
  on the DISPLAY cube for each facing.
- [ ] Duplexer screen: each screw shows its frequency (factory tuned to its repeater, TX on the left, RX on the
  right); dragging or scrolling a screw moves it 5 kHz a step. One screw 25 kHz off: the repeater is noticeably
  weaker. Changing the repeater's frequency after turning a screw leaves the duplexer behind.
- [ ] VHF antennas (J-pole, Slim Jim, Yagi, dish) now really show SWR 1 on 2m: they showed 10 before (a bug), so
  check the static and S-meter on a VHF base station look right.

## Blown radios (added 2026-10-23)

- [ ] Keying a radio into a bad match (SWR 3 or worse, e.g. a 2+2 dipole on 20m, no tuner) for a while blows it:
  a fizz and a puff of smoke, and the model stays the same. Jade shows "Transmitter: Blown" in red. With the GUI open
  and PTT held, the radio's speaker roars with full static instead of sending (HF and VHF base station); released,
  it hears stations as before. Faster with a worse SWR; never with an antenna tuner matching it or a matched antenna.
- [ ] A Cheap (poor crystal) radio sometimes goes up like TNT when it blows (`radio_explode_chance`, 0.1).
- [ ] Mining a blown radio gives back its crafting parts (no crystal; for the all-band radio no all-band board) instead
  of the radio. Server config `[Blown Radios]`: `radio_blow_swr`, `radio_blow_chance` (0 turns it off).

## Radio screen readouts and the aurora setting (added 2026-10-23)

- [ ] Within 8 blocks of a radio that is on, its model's screen (the maintainer's DISPLAY box) shows the frequency in
  amber, and under it smaller the mode (USB/LSB/CW/FM) and an S-meter (S0 to S9, S9+). Blank when the radio is off;
  nothing beyond 8 blocks. Check every radio (10/20/40/80m, all-band, VHF base station, Premium and Cheap ones too),
  each facing, and one hung from a ceiling; the text should sit on the screen and read left to right. Setting
  `show_radio_screens = false` in `config/radiocraft-client.toml` hides them.
- [ ] The S-meter rises with a station's signal, and on HF sits near the top in a blackout
  (`/rcdebug solar set blackout`) or under an aurora; VHF stays low without a signal.
- [ ] `allow_aurora_at_any_z_coordinate` (server config, off by default): on, the aurora's sky, crackle and static
  reach a station at any Z; off, only past `aurora_latitude`.

## Teletype Machine and the 5-BTV's coax (added 2026-10-23)

- [ ] The solar weather station is now the "Teletype Machine" (same model). Placing it takes your callsign. Right
  click opens the old RTTY screen: the callsign across the top (click to change; the green lamp claims yours), the
  paper roll, typing that goes out live through the radio it touches (no Radio Interface), Enter/SEND to end a line.
- [ ] The blue lamp (SOLAR WX) prints the solar report on the roll as WX lines, with the printing sound and bell; the
  red lamp blinks when out of paper. Paper goes in by right click or hopper and drops when the teletype is broken.
- [ ] Two teletypes on radios a few blocks apart on one frequency print each other's RTTY; weak copy prints garbled.
- [ ] Coax laid next to the 5-BTV's base runs in to its mast, like the ionosonde.

## APRS Map, microphone element, desk mic (added 2026-10-23)

- [ ] Right-clicking an H19 with an empty map gives an "APRS Map" (the empty map is used up). In hand and in an item
  frame it looks like an old terminal map: black land with grey contour lines every 8 blocks of height, water as a
  dark dither with a bright shoreline, a dotted grid, a box at the H19, and a white cross at each station heard with
  its callsign beside it in a small pixel font (on the left near the right edge). It re-centres and zooms out to fit
  as stations are heard, every 5 seconds. Unloaded ground is black. A cartography table copy stays live.
- [ ] Sneaking and right-clicking with 4 or more empty maps gives a 2 x 2 wall (not a vanilla map: the H19 takes the
  sneak-use), 9 or more a 3 x 3: each map's tooltip
  gives its row and column (top left is north west). Framed in that order, the picture, crosses and callsigns run
  across the joins.
- [ ] Microphone Element: texture in the inventory; ` P `/`NCN` makes 2; the hand and desk mic recipes use it.
- [ ] Desk mic: the maintainer's smaller model stands on the side of the block nearest its radio (left or right as
  seen from the front) and moves when a radio is placed on the other side. Its outline fits the mic.

## 5-BTV Multiband HF Antenna (added 2026-10-23)

- [ ] Placing it puts up all seven segments (it needs seven free blocks); breaking any one brings the whole mast
  down and drops one item. The segments line up into the maintainer's model, with the trap radials on the fifth
  and sixth.
- [ ] On coax from an HF radio or a VHF base station the radio shows SWR 1.3 on every band.
- [ ] On HF a nearby station by line of sight fades out at half the usual distance; skip contacts reach a quarter
  further than on a dipole.
- [ ] The item (the base segment) looks right in the inventory.

## Two-tall items (added 2026-10-23)

- [ ] The Ionosonde Antenna and Ionospheric Processor items fit their inventory slot and look right in the hand, on
  the ground and in a frame (they had the J-pole's display settings and spilled over neighbouring slots).

## Floor cables and the ionosonde's coax (added 2026-10-23)

- [ ] A short grey cable runs along the floor from the Radio Interface to each radio, H19 and Transfer Interface
  beside it, from the H19 and Transfer Interface back to it, and from a desk microphone or the teletype (solar
  weather station) to a radio beside it. It appears and goes as blocks are placed and broken, and it hides under the
  device's body except between the body and the block edge. A radio hung from a ceiling gets no floor cable.
- [ ] Coax laid next to the bottom of the Ionosonde Antenna runs in to the foot of its mast on that side.
- [ ] The Ionospheric Processor now faces 270 degrees round from its export (180, then 90 more clockwise).

## Model fixes (added 2026-10-23, second pass)

- [ ] The H19 and the Ionosonde Antenna now show (they failed to load: rotations Minecraft 1.21.1 cannot read). The
  H19 screen's 2.5 degree tilt is flattened.
- [ ] The Ionospheric Processor faces the other way round from before, its top half lights the area (level 7), and
  the bottom half is no longer translucent.

## Ionospheric Processor (added 2026-10-23)

- [ ] Placed and broken as one two-tall block; it faces the player when placed (check the front is the side you
  meant: the model is turned so its north side faces you).
- [ ] The three valves on top glow softly in the dark (light 10 on the glass and the filaments) and the glass is see
  through with the filaments visible inside. No odd sorting against water or stained glass behind it.
- [ ] Item in hand and inventory (borrows the J-pole's display settings).

## Ionosonde Antenna (added 2026-10-23)

- [ ] Placing it puts down both halves; breaking either half removes both and drops one item. The model sits right
  across the two blocks (the maintainer's export was split at y=16 into `ionosonde_lower` and `ionosonde_upper`), with
  both textures showing (no purple and black).
- [ ] The item looks right in the hand, the inventory and an item frame (it borrows the J-pole's display settings).
- [ ] On coax to a VHF base station the radio shows SWR 3; to an HF radio, SWR 8, and keying up wastes most of the
  power. Receiving on VHF is about as good as a J-pole; HF is faint.

## Radio Fax and the teletype roll (added 2026-10-27)

The Digital Transfer Interface is gone; the **Radio Fax** (one per log, in the dev tab and craftable from that log,
glass, wire, an HF board and paper) replaces it. Audio needs voice chat.

- [ ] Model: a plank-clad desk fax (each wood's planks, 2026-10-30; it was the log) facing the player, a cream deck with a keypad and LCD at the front, a sheet in the
  front tray and one standing in the feeder at the back; the floor cable runs to a Radio Interface or a radio beside
  it. All eleven woods look right.
- [ ] Screen: a cream fascia with the document slot and SEND key on the left, PAPER / BOOK / MAP supplies and the
  four-slot RECEIVED tray along the bottom, the grey LCD top right, and the vanilla inventory below. Hover tips on
  each.
- [ ] LCD: typing goes into TO (upper case, callsign characters only, a blinking cursor); TAB or a click on the MY
  row moves to MY (ENTER there sets the machine's own callsign, empty claims yours); ENTER in TO or the SEND key
  sends. 'e' does not close the screen; ESC does. Key clicks on each character.
- [ ] RECENT: up to six callsigns, newest first, two a row; clicking one fills TO.
- [ ] Sending a two-page book to the other station on VHF: the LCD reads CALLING, then SENDING PACKET x/30 with the
  bar filling, about 20 seconds in all; the counter ticks faintly as it moves. The far machine shows RECEIVING FROM
  and its own count, prints a copy (generation up one) into its tray on one of its blank books, dings; the sender
  shows SENT BOOK ... for ten seconds, then READY.
- [ ] On the air (any radio on the frequency): the CNG beep, the long CED answer, then the warbling carrier in bursts
  with a short beep after each; nothing on the air over a microwave link.
- [ ] Standing by either machine: the same tones quietly from the machine itself (its monitor speaker), while the fax
  runs; silence when idle.
- [ ] Far apart or with a high noise floor (a storm): RETRY appears on the status, R<n> at the right of it, the bar
  stalls and goes on; a hopeless signal ends in FAILED x/30 and the receiver in LOST.
- [ ] A fax to a callsign nobody has ends in NO ANSWER after three calls (about 15 s); to a station with no blank of
  the right kind, "<CALL>: NO BOOK" (or PAPER / MAP); with a full tray, TRAY FULL; while it is busy, BUSY.
- [ ] A map takes about 20 s on VHF, an enchanted book 40 s and is gone from the sender's slot when done; on 10m a map
  takes about 40 s, on 80m about 160 s; over a microwave link about 2 s.
- [ ] A fax touching the radio with no Radio Interface works under the callsign set on its MY row.
- [ ] Hoppers: one on top or beside stocks paper, books and maps; one underneath takes the copies.
- [ ] Teletype: the wheel, Page Up / Page Down, Home and End scroll back up the roll (a "-n" tag at the top right of
  the paper shows how far); typing snaps back to the head. 400 lines are kept.
- [ ] Sneak-right-click the teletype with an empty hand: the printed roll comes off as a Teletype Roll item (tooltip:
  how many lines and the first), with a paper rustle, and the machine shows a fresh roll. Nothing happens if nothing
  was printed.
- [ ] Right click the roll in hand: a scrollable strip of the paper, opened at its end; wheel, Page Up / Down, Home,
  End, arrows. ESC closes.
- [ ] Right click a teletype whose roll is empty with the roll: it feeds back in and the item is used up; on a machine
  with lines on it the roll opens to read instead.
- [ ] JEI's item list stays clear of the fax screen.

## Radio Interface, H19 and Transfer Interface (added 2026-10-23; the Transfer Interface is now the Radio Fax)

The digital interface is now three blocks (dev tab): the **Radio Interface** touching a VHF radio, and the **H19
APRS Terminal** and **Digital Transfer Interface** touching it, or touching each other in a chain back to it.

- [ ] H19 model: renders facing the player, with the hitbox over the keyboard and case. Its screen is blank with the
  radio off, shows READY within half a second of switching a VHF radio on, CONTACT when a new station's beacon
  arrives, MSG when a message for this station arrives (MSG wins over CONTACT). The screen icons glow in the dark.
- [ ] Opening the H19 clears CONTACT and MSG back to READY. CONTACT also goes out by itself after 10 minutes
  (`aprs_contact_lamp_minutes`); MSG stays until opened.
- [ ] H19 screen: the old APRS and MSG tabs. The callsign field shows the Radio Interface's callsign (not editable
  here), the green lamp is lit while joined to a Radio Interface, and the digipeat lamp switches the Radio
  Interface's digipeating.
- [ ] Radio Interface screen: a plain panel with a callsign box (Enter saves; empty claims your own) and a TX ON / TX
  OFF button. With TX off, beacons, messages, files and digipeats don't go out; listening still works.
- [ ] Transfer Interface: the old FILES tab with the inventory; hoppers stock it and one underneath takes the copies.
  Its placeholder model is a stone box with a loom front and a cartography table top.
- [ ] JEI's item list stays clear of both scaled-up screens.
- [ ] A chain: radio, Radio Interface, Transfer Interface, H19 in a row works end to end; breaking the middle one
  greys the H19's green lamp and stops it sending.

## Maintainer's art: WEFAX receiver, static loop, 4:1 balun (added 2026-10-23)

- [ ] WEFAX receiver (dev tab): the model renders with its texture, faces the player when placed, turns with each
  facing, and the outline/collision box fits it (full base, printer body behind the front bar). It drops itself.
- [ ] Static loops with no hiccup every few seconds (new `static.ogg`), on HF and on a weak VHF signal.
- [ ] The 4:1 balun shows its own texture, different from the 2:1.

## Forge Energy and the power rebalance (added 2026-10-23)

With Create and Create: Crafts & Additions installed (NeoForge 1.21.1):
- [ ] An alternator next to a large battery charges it (Jade shows the charge rising); FE cables from other mods work too.
- [ ] Two small water wheels on one shaft (or a large wheel geared up to 8 RPM) into an alternator, feeding a large
  battery wired to a VHF base station: the battery holds steady with the radio transmitting most of the time.
- [ ] Delete old `radiocraft-common.toml` files first, or they keep the old (about 15x larger) numbers.
- [ ] A handheld's battery bar barely moves over a few minutes idle (it should last 12 in-game days).
- [ ] The desk charger fills a flat handheld in about 50 minutes (slowed to an eighth, 2026-11-04: 1 FE a tick).

## Solar weather station needs a radio (added 2026-10-23)

- [ ] On its own, right click prints "No signal: ..."; Jade shows the same line; a comparator reads 0.
- [ ] Put a VHF base station (or any radio) next to it with a J-pole on coax: the full report prints and the comparator
  lights up within half a second. Swap the J-pole for a dummy load: back to "No signal".
- [ ] The item tooltip mentions the radio.

## Block drops (added 2026-10-23)

Every block now has a loot table; before, breaking any RadioCraft block lost it. GameTests check the tables.

- [ ] In survival, mine a radio, a battery, a balun and the tuner with a pickaxe: each drops itself. By hand: nothing.
- [ ] Wire, waterproof wire and coax break instantly by hand and drop themselves.
- [ ] A J-pole broken from its top half drops one antenna, not two (creative drops none).
- [ ] The passthrough still gives back itself and the wall block; the desk charger still drops itself and its radio.

## Crystal qualities (added 2026-10-22)

Every crystal radio (10/20/40/80m, VHF base station, VHF handheld) is now three blocks/items: Premium (pure), plain
(good, the old id) and Cheap (poor). Textures are copies for now. The old Radio Crystal item is gone.

- [ ] Premium and Cheap radios place, render (floor and ceiling) and show their names and tooltip line; their GUIs open
  with their own (copied) GUI textures, including the Premium/Cheap VHF handheld screen.
- [ ] Raw crystals show the maintainer's pure/good/poor crystal textures (same for every band), encased crystals the old crystal texture; all are in the creative tab.
- [ ] Digging sand drops a crystal about 1 in 80 blocks, mostly poor.
- [ ] JEI shows the encased crystal, radio and crystal-swap recipes; the swap works in a crafting table.
- [ ] By ear: a Cheap HF radio slowly pulls a steady SSB voice up and down in pitch over a few minutes (drift up to
  1 kHz); a Premium radio's static is a touch quieter than the plain one's.
- [ ] Handhelds of every quality still charge in the desk charger and swap batteries.

## VHF repeater, duplexer, CTCSS and offsets (added 2026-10-21)

The repeater is now a working 2m repeater (`VHFRepeaterBlockEntity`). It needs a duplexer on its back, top or bottom,
with coax from the duplexer to the antenna. Handhelds and the VHF base station gained a CTCSS tone and a ±600 kHz
repeater offset. Every radio with nothing on its coax now works through its bare connector over a few blocks
(`bare_connector_range`, default 8). Run `./gradlew runData` first: the repeater screen's text is new.

- [ ] Repeater face: POWER toggles it; the RX/TX windows show input/output MHz; the four bottom windows show
  ON/OFF/PWR, DUP / NO DUP / NO ANT, RX (red TOT when timed out), TX (amber while transmitting).
- [ ] PRT RX / PRT TX open a panel over the face: type MHz, Enter or Done applies, Esc or Cancel leaves it without
  closing the repeater. "OUT -600 / +600" fill in the usual input. The RX/TX knobs step 20 kHz.
- [ ] CW ID: callsign (letters, digits, /) and "ID every" (off/5/10/15/30 min). After an over the repeater sends the
  courtesy beep, then the callsign in Morse tone if an ID is due.
- [ ] CTC RX / CTC TX: < > through the standard tones. With an access tone set, a handheld without that tone gets
  no repeat; with it, it does.
- [ ] MIC: with TX on, talking next to the repeater goes out on the output. TX off: it listens but does not repeat.
  SPEAKER: plays the input at the block.
- [ ] Knobs under the buttons: hang time (0-5 s) and timeout (off to 10 min), shown in a tooltip while turning.
  Talking longer than the timeout drops the repeat until you let go.
- [ ] Duplexer behind / on top of / under the repeater: coax wire draws a connection to it and the repeater works
  through it. A duplexer on the front or side does nothing (NO DUP).
- [ ] Antenna on the repeater but no duplexer, TX on: someone talking into it gives "psht ... psht ... psht" on
  the output instead of their voice. With TX off, receiving works normally.
- [ ] No antenna at all: the repeater (and any radio) works only within about 8 blocks.
- [ ] Handheld: ENTER from the frequency screen pages Tone, then Offset, then back; the arrows change the value; "-"
  / "+" and "T" show on the main screen when set. Handheld on 146.940 with -600 and the repeater's tone talks
  through a repeater on 146.340 in / 146.940 out.
- [ ] VHF base station: the right-hand display shows OFS and TONE; its three soft buttons (offset, tone down, tone
  up) and the two big knobs (tone, offset) change them.
- [ ] Simplex repeat controller (parrot): after it finishes echoing an over it sends a courtesy beep.
- [ ] The settings panel is a plain dark box (placeholder): say if it needs art.

## Off-frequency signals and the 2m tuning fix (added 2026-10-21)

The all-band on 144 MHz was stuck at 144.000: frequencies are floats, 144.001 MHz does not fit one exactly, and the
"not on a step" check reset every step. Steps are now counted from the band bottom. Off-frequency voice
(`Detuning`, `RadioVoiceProcessor`) is new: SSB is heard up to 10 kHz off, weaker, pitch-shifted (0.15 Hz per Hz off,
so 1.5 kHz at 10 kHz off) with the top or bottom of the voice cut, and splatter from 7 kHz out. FM is heard as usual
up to 2 kHz off (weaker). From 3 to 10 kHz off, or one VHF channel step (20 kHz) off, FM stays squelched but
crackles over a signal you are hearing on frequency.

- [ ] All-band on 2m: the frequency knob steps 144.001, 144.002 ... and the display follows; reload keeps it.
- [ ] SSB 1-2 kHz off (two 20m radios): voice is clear but a little higher or lower; tuning above on 20m sounds
  lower, on 40m/80m (LSB) higher.
- [ ] SSB 5-10 kHz off: "Donald Duck" voice, thin (bass or treble cut off), quieter; garbled splatter rises near
  10 kHz. 11 kHz off: nothing.
- [ ] FM (all-band FM, VHF base station, handhelds) 1-2 kHz off: sounds normal, a bit quieter. 3+ kHz off: silent,
  no squelch opening.
- [ ] FM splatter: listen to a station on frequency while a second one talks 5 kHz (HF all-band FM) or one 20 kHz
  step (handhelds/VHF) away: bursts of crackle over the wanted voice, gone when the second one stops.
- [ ] Tune the numbers if needed: constants at the top of `Detuning` (strengths, shift per Hz, splatter levels).

## Mode buttons and the all-band radio (added 2026-10-21)

Mode buttons now work like a radio's selector: the active mode is pressed in, the others are out, and pressing the
active one does nothing (the button art rows were swapped before, so "on" looked popped out).

- [ ] Every HF radio (10m, 20m, 40m, 80m, all-band): exactly one of SSB / CW (/ FM) is pressed in at all times, and it
  is the one the display shows. Clicking the pressed one changes nothing; clicking another swaps them instantly.
- [ ] All-band: the square button bottom-left switches it on and off (faint highlight on hover); the lamp by the light
  bulb comes on with power.
- [ ] All-band band knob: left click or scroll up turns it clockwise 3 > 7 > 14 > 28 > 144, right click or scroll down
  back; it stops at both ends. The knob art points at the label each time (sprites were lined up on a copy of the
  panel, so check them at real size), the frequency jumps to the bottom of the new band and the band survives a
  reload.
- [ ] All-band FM: pressed in alone, display shows "FM", voice works, and there is no background static (FM is
  squelched). SSB and CW still have static.
- [ ] All-band lamps under the brand plate: left green = a signal coming in, middle green = antenna matched (SWR 2 or
  better), right amber = transmitting.
- [ ] All-band on 144 (2m) in FM with a J-pole or slim jim on its coax: talks to a VHF base station and handhelds on the
  same frequency.
- [ ] MIC / SPK buttons and the two small knobs still do nothing (as on the other radios).

## Improvised antennas (added 2026-10-21)

Any wires on any balun now work: when no proper antenna type recognizes the build, it becomes an "Improvised" antenna
(`ImprovisedAntennaType`, -3 dBi) whose SWR grows with how far it is from ideal (arm lengths off a quarter wave, bent
or sloping arms, extra or branching arms, a balun of the wrong ratio). Clean builds still match their proper type.

- [ ] Rebuild the playtest dipole with one arm dropping a block: Jade shows "Antenna Type: Improvised", the two arms,
  "Shape off ideal: +0.2 SWR" and an SWR on 20m around 1.4; the radio works on it.
- [ ] Bend it, add a third wire, put it on a 9:1: the SWR climbs but it keeps working; a tuner brings it back.
- [ ] Right-clicking a balun shows "Antenna: Dipole" (or Improvised, ...) above the hotbar instead of a class name.

## Radio-sounding voice and static ducking (added 2026-10-21)

Received voice now goes through `RadioVoiceProcessor` on the server (band-pass 300-2700 Hz, AGC, soft overdrive) and
plays at `ReceiverMix.voiceLevel(S, N)`; the client's static is multiplied by `ReceiverMix.noiseDuck(S, N)`, so a strong
signal pushes the static down and a weak one stays buried. Skip signals fade in and out slowly (QSB), VHF heard off an
aurora gets a raspy buzz. Settings: "Receiver Audio" in `radiocraft-server.toml` (band edges, drive, VHF noise, fading
depth, an off switch). Cost measured at 35-55 microseconds per 20 ms frame per receiving radio.

- [ ] Voice sounds like a radio: thin, no deep bass or airy highs, steady loudness whether the talker whispers or
  shouts, a little crunch on peaks. Compare with `voice_processing_enabled = false`.
- [ ] Strong nearby station on a quiet band: the static drops away while they talk and comes back up within about half
  a second after they stop, smoothly (no clicks, no pumping on every word).
- [ ] Weak or distant station, or a blackout (`/rcdebug solar set blackout`): the voice is quieter and the static stays
  loud over it; hard to copy, as on a real rig.
- [ ] Skip contact (HF beyond line of sight): the voice swells and fades over several seconds, and the static rises as it
  fades.
- [ ] Handhelds (VHF): same band-pass and AGC; a handheld at the edge of range is quieter but not buried (no static on
  VHF). Off-hotbar handhelds are still half volume.
- [ ] Aurora VHF (see Aurora below): buzzy, raspy voice. Too much / too little?
- [ ] Tune by ear: `voice_band_low/high`, `voice_drive`, `skip_fading_depth`, `vhf_noise_floor`. Say which numbers
  sound right.

## Aurora (added 2026-10-21)

A blackout has a 30% chance (`aurora` in `solar_events/blackout.json`) of an aurora three days after it starts, lasting
a day. It only reaches stations with |Z| >= 10000 (`aurora_latitude` in the server config). There VHF skips 500-4000
blocks off the aurora, every HF band drops one condition step and HF static rises by `aurora_static`. Force one with
`/rcdebug solar aurora on` (`off` ends it), `/rcdebug solar` shows its state, `/tp @s ~ ~ 10500` to get under it, and
`/time set midnight`.

Graphics (procedural, `AuroraRenderer`, no textures):
- [ ] At night past Z 10000 (or -10000): green curtains low over the pole side of the sky (north at -Z, south at +Z),
  bright lower edge fading up through teal to violet, vertical rays drifting slowly, the curtains folding over time.
- [ ] It fades in over Z 9000-10000 (walk across), with dusk, and over a few seconds when it starts or ends. Gone by day,
  in rain, underwater and in the Nether/End.
- [ ] Looks right with shaders off at several render distances (2, 8, 16+ chunks), does not show through terrain or
  draw over the moon oddly, and does not hurt the frame rate.
- [ ] Say how it should look instead: colours, height, how much of the sky, how fast it moves. All are numbers in
  `AuroraRenderer#curtain`.

Radio:
- [ ] Two VHF stations (or handhelds) 2000 blocks apart, both past Z 10000: they hear each other only while the aurora
  is on. One of them below Z 10000: nothing.
- [ ] HF past Z 10000: the station's bands read one step worse than the same event further south; the static is louder.
- [ ] Weather station anywhere: "Aurora expected: storm on the way..." after a lucky blackout, "Aurora: active beyond
  Z +/-10000" while on (purple / green line). VHF reads "Aurora" for a station past the latitude, "Normal" elsewhere.

## Solar weather shapes HF skip (added 2026-10-21)

Each solar event now rates every HF band Good / Fair / Poor / Closed, separately by day and night
(`data/radiocraft/solar_events/*.json`, `bands.day` / `bands.night`). The rating scales the sky-wave (skip) signal:
Good 1.0, Fair 0.6, Poor 0.3, Closed 0.05. Line of sight is not touched. A weaker signal plays quieter against the same
static, so a poor band is hard to copy. Force an event with `/rcdebug solar set blackout` (`/rcdebug solar` shows
the band ratings), then check the station.

- [ ] Blackout by day: the station reads Closed on 10m, 20m, 40m and 80m. Two stations at skip distance (20m by day:
  2000-6000 blocks apart) hear each other only as a faint voice buried in the static.
- [ ] Blackout at night: 40m and 80m read Poor and still work, weak and noisy; 10m and 20m read Closed.
- [ ] Average and better: 20m, 40m and 80m read Good both day and night, as before; skip sounds like it did.
- [ ] Is the balance right? Closed at 0.05 might still be too audible (or Poor at 0.3 too easy) next to the static.
- [ ] Note: stations within line of sight (a few hundred blocks) are not affected by the band rating, only by the
  louder static.

## Radio static comes back after the sound engine drops it (added 2026-10-21)

A radio's static, morse and hum loops are now restarted within a second if Minecraft's sound engine drops them.

- [ ] Power an HF radio in SSB with a matched antenna and hear the static. Then each of these, and the static should
  come back within about a second: drag Voice/Speech to 0 and back; F3+T; switch the Windows output device (or plug
  in headphones); leave the area until the chunk unloads and walk back.
- [ ] No doubled static (louder than before) after any of the above.

## Simplex Repeat Controller ("parrot", added 2026-10-20)

Place one next to an HF radio with a working antenna, and a second station in earshot.

- [ ] Model looks right in world: body, top plate, bar and nubs sit where they do in the Blockbench file, for all four
  facings. The item in hand / inventory renders the same model.
- [ ] LEDs: dark red and dark green "off" pixels visible on the front face; green pixel glows fully bright while the
  radio receives, red while it sends the echo, both still visible in a dark room. No black square around the lit pixel
  (cutout rendering).
- [ ] Wire stub: a short coax-textured wire runs from the unit to the radio on the linked side, for every combination
  of block facing and link side, and never pokes through the LEDs. Placing or breaking the radio updates the wire.
- [ ] Name and tooltip: "Simplex Repeat Controller", tooltip text as written by the maintainer.
- [ ] Right click shows "Repeat delay: Ns" above the hotbar and cycles 1, 2, 3, 5, 10 s then back to 1. The chosen
  delay survives leaving and rejoining the world.
- [ ] By ear: speak into the far radio and hear yourself back after the delay; key CW and hear the morse back.
  Compare loudness with a direct contact at the same distance.
- [ ] Breaking the controller stops the echo. Breaking the radio and placing it back re-links.
- [ ] Recipe shows up in the crafting table / JEI (placeholder recipe, to be reviewed with all recipes).

## Redstone interference, receive side (added 2026-10-20)

Power up an HF radio in SSB with an antenna, stand where you can hear its static.

- [ ] Flip a lever or place a redstone block next to the radio or its coax: the static pops up for a quarter second.
  Do the same 3 blocks from a dipole arm: a smaller pop. 10 blocks away: nothing.
- [ ] Build a repeater clock next to the coax: the static stays louder and a low hum (the placeholder tone, pitched
  down) fades in. Break the clock: both die away within a second.
- [ ] Compare an 80m radio and a 20m radio with the same clock: 80m should be twice as affected, 10m barely.
- [ ] A redstone lamp or a door being toggled (not a signal source) should be a much smaller pop than a lever.
- [ ] Does the placeholder hum sound acceptable until a real buzz loop exists? Is the 0.25 threshold right, i.e. does
  a slow clock (1 s) stay hum-free while a fast one hums?
- [ ] Server config: `redstone_interference_enabled = false` silences it; `redstone_interference_only_bad_swr = true`
  silences a matched station but not one tuned off its antenna.

## Redstone interference, transmit side (added 2026-10-20)

Full-power HF rig with a matched dipole, redstone dust and a repeater or lamp within 6 blocks of the wire.

- [ ] Key up (PTT or CW): dust near the wire lights to level 4, the lamp or repeater fed by it reacts. Let go: it
  goes dark within a tick. Dust 8 blocks away does nothing.
- [ ] Which dust blocks react is the same every key-up. Shorten or lengthen a dipole arm (SWR changes): a different
  set, and with SWR 3 or worse the level is 8 or more and dust within 10 blocks of the radio or 5 of the coax joins in.
- [ ] A QRP radio with the same antenna only reaches 2 blocks.
- [ ] Levers and buttons within reach flip when you key up (about a third of them), torches blink off for two ticks.
  A button on a command block never does.
- [ ] A quarter wave vertical: dust 3 blocks above its base is unaffected, dust 3 blocks beside it is.
- [ ] Does 0.35 feel right as the fraction of components hit? Does level 4 read as "a glitch" rather than a working
  circuit? (Both are config values.)

## Comparator outputs (added 2026-10-20)

- [ ] Comparator against a radio, lamp or dust chain behind it: the output follows the strength of what the radio is
  receiving (a nearby strong station lights a long run, a weak one a short run) and goes dark about a second after
  the other station stops talking or keying. Works for SSB voice and CW, and on VHF base stations.
- [ ] Comparator against the Simplex Repeat Controller: reads 1, 2, 3, 5, 10 as the delay is cycled.
- [ ] Right clicking the controller makes a button click; is the volume and pitch right for a desk unit?
- [ ] Comparator against the Solar Weather Station: reads 1 to 6 (blackout to great) and changes when the event
  changes (`/rcdebug solar` shows the event; `/solarweather` too). A strongly powered chain with the station at the
  end makes a cheap conditions indicator.

## Solar weather station (added 2026-10-20)

- [ ] Right click: a chat line like "Solar conditions: Good (noise floor 20%). 10m band: Fair". The event name reads
  as a word, not a key (needs `./gradlew runData` first). Tooltip replaced the "not implemented" one.
- [ ] The block still renders and faces the right way (it became a block entity; existing placed ones keep working).

## Lightning static (added 2026-10-20)

Powered HF radio in SSB, a thunderstorm (`/weather thunder`) or `/summon lightning_bolt` a few blocks away.

- [ ] Each strike: a sharp crash in the static, gone within a second. Compare 80m and 20m radios: 80m twice as loud,
  10m barely. A strike 200 blocks away is still a small crash; beyond 512 blocks nothing.
- [ ] Does the crash read as lightning? The proposal says 40% for a second; the default is 1.2 (doubled on
  2026-10-21 at the maintainer's request, so nearby strikes saturate the static) on top of the solar noise floor,
  decaying with the same 0.8/tick as redstone pops. If it should rumble longer, say so.
- [ ] `lightning_static_enabled = false` silences it.

## Jade (added 2026-10-20)

- [ ] Radio: "Signal: n/15" appears while receiving, "Interference: n%" while redstone or lightning noise is up,
  "Simplex repeat: Ns delay" when a controller is linked.
- [ ] Simplex Repeat Controller: delay, "Radio: linked to the west" (or none), "State: Idle/Receiving/Repeating".
- [ ] Solar Weather Station: conditions with noise floor and level, then each HF band's Good/Fair/Poor/Closed.

## Antenna tuner (added 2026-10-20)

HF radio, coax, Antenna Tuner, coax, 1:1 balun with a dipole one or two blocks short per arm (SWR 2 to 3).

- [ ] With the tuner in the run the radio's static comes up to full (as with a matched dipole) and `/rcdebug radio`
  shows `swr=1.00 (tuner: antenna swr=3.00, power to antenna=94%)`. Break the tuner and bridge with coax: back to
  SWR 3 and quieter static.
- [ ] Tuner GUI: right click shows "Matched", "Antenna SWR: 3.0", "Radio sees: 1.0" in the panel right of the meter;
  the readout follows the radio being switched off ("Radio is off"), the antenna being broken ("No antenna
  recognized") and a 2+2 dipole on 40m ("Out of range: no match", SWR 10). Is the text placed and coloured
  acceptably on the texture? The dials still do nothing; say if the tuner should become manual.
- [ ] Jade on the coax lists "Antenna Tuner: at x, y, z" and still lists the antenna beyond the tuner.
- [ ] Tooltips: the tuner and the three baluns now have a sentence each instead of "Not Implemented".
- [ ] Redstone interference: with the tuner matching a SWR 3 dipole, the bad-SWR dust reach around the radio and
  coax goes away (the station counts as matched). Is that the right call, or should the coax after the tuner still
  count as badly matched?
- [ ] `antenna_tuner_enabled = false`: the readout says "Tuners disabled on this server" and the SWR is the raw one.
- [ ] Side effect of a bug fix (Testing.md audit item 13): breaking a radio, balun or tuner now really drops it from
  its coax network. Break a radio next to live coax and place it back: it should re-link and keep its settings; break
  a balun: the far station should stop hearing it at once (no ghost antenna).

## Inverted V and random wire (added 2026-10-20)

- [ ] Inverted V: 1:1 balun on a pole 3 high, two wires to poles on the ground 4 blocks out either side (each arm is
  5 along the wire). Right clicking the balun names `radiocraft:inverted_v`; Jade shows arms and drops; a 20m radio
  sees SWR 1. Raise one end above the balun: no longer recognized. Compare the strength heard at a far station with
  a flat 5+5 dipole: the V should be 10% weaker (25% on 40m).
- [ ] Random wire: Balun (9:1) (texture by the maintainer, 2026-10-21), one wire to a pole,
  a second wire on from that pole (12 blocks total, any shape). Right click names `radiocraft:random_wire`; Jade
  shows the length and "Needs an antenna tuner". A 20m radio without a tuner sees SWR 2.6, with a tuner 1.0; a
  wire shorter than 11 blocks on 20m is SWR 10. Fork the wire at the pole: not recognized.
- [ ] Both new types need `./gradlew runData` first (9:1 balun tags, name and tooltips, tuner readout text).

## Radio screen fixes (added 2026-10-21, from the first playtest)

- [ ] Frequency readout: every HF screen (10m, 20m, 40m, 80m, all band, receiver) and the VHF base station shows
  "14.000 MHz" (or the band's bottom) in its display window while powered, nothing while off. The box positions were
  measured off the textures, not seen in game: say where it sits wrong or is the wrong size (the 40m and receiver
  LCDs are tiny, so the text there is half size). Dial and buttons move it.
- [ ] A freshly placed radio comes up in SSB: static as soon as it is powered, no button press.
- [ ] PTT: hold it and the static (and any interference hum) stops for everyone near the radio, the readout turns
  orange-red and larger displays show "TX" top right. Let go: static back. In CW the readout follows your own key.
- [ ] Is the TX colour and marker enough feedback, or do you want proper TX/RX lights drawn on the panels?
- [ ] 80m: the readout now sits on the tan panel above the brown box, in dark red (maintainer's choice, 2026-10-21).
- [ ] VHF handheld: while entering a frequency the battery percentage moves up next to "Set Freq" instead of
  overprinting "MHz". The item is held upright now (plain item pose instead of the sword grip); first person too.
- [ ] Lightning: reported silent once, then fine after a client restart (maintainer, 2026-10-21; "audio and decay
  sounded good"). If it goes quiet again, note what happened just before (world reload, far travel, config change)
  and whether a lever next to the coax still pops, since both use the same client path.

## Solar weather station teletype (added 2026-10-21, maintainer request; sounds by the maintainer)

- [ ] Right click: the teletype starts at the block and the report prints in chat one line at a time over about
  3 seconds (header, Overall, Noise Floor, VHF, 10m, 20m, 40m, 80m), with the bell at the end. Does the spacing feel
  right against the sound? Two players clicking at once each get their own lines.
- [ ] Placing the station rings the bell once.
- [ ] Band words: VHF is always "Normal"; the HF bands read Good, Fair, Poor or Closed for the current event and time
  of day (see "Solar weather shapes HF skip" below). Say if the words should differ.
- [ ] `teletypefadeout.ogg` is registered but unused; say where you want it.
- [ ] Wire across a one-wide trench: coax on the trench floor climbs both walls and joins the wire on each side,
  no bridge needed (a bridge plus floor wire makes a real loop, which draws as a rectangle).
- [ ] Solar weather station outline and collision follow the model (body plus the paper roll at the back) for all
  four facings.
- [ ] Wire: at the foot of a trench or pit the run now goes up the wall on the side the wire above continues over,
  so it meets that wire head on instead of going up the nearest wall and across. Check the screenshot build.

## Solar panels join by touching (added 2026-10-21, maintainer request)

- [ ] A row of solar panels with a charge controller touching the end panel and a battery wired to the controller:
  the battery charges in daylight with no wire between the panels. Panels placed before or after the controller
  both work. A battery touching the controller with no wire does not charge (batteries need wire).
- [ ] Jade on a panel: "Making: N FE/tick" (0 with a reason at night or under a roof), the row's panel count and
  total, and whether a charge controller is connected.
- [ ] Known gap: breaking a panel in the middle of a row leaves the two halves on one network until something
  re-links (networks are not split when a device goes, only when wire does). Say if that matters.

## Wire Passthrough (added 2026-10-21, maintainer request; item model and texture by the maintainer)

Use the Wire Passthrough item on a plain solid block (stone, planks, bricks; not a device, wire or block with a
block entity). The block keeps its look but now conducts power wire and coax between all six sides.

- [ ] Click a wall block with the item: no visible change except the sound; wire or coax on either side now draws its
  stub into the wall face and the run connects through (`/rcdebug radio` sees the antenna, a battery feeds a radio).
- [ ] The converted block renders exactly like its neighbours in daylight and in the dark (it is lit from its
  brightest neighbour, since there is no light inside a wall). Check at a chunk border and with a glass or leaves
  block converted (transparent blocks are not allowed; the item should do nothing on them).
- [ ] Breaking it in survival drops the passthrough and the original block; creative drops nothing. Middle click
  gives the passthrough item.
- [ ] Jade on the coax lists what is beyond the wall. Jade on the passthrough itself shows the wall block's name
  (Jade reads the block state, which is the passthrough; say if it should show the mimicked block instead).
- [ ] Both kinds at once: a power wire and a coax into the same passthrough from different sides stay separate
  networks (the radio on the coax side does not end up on the battery's wire).

## Radios draw power (added 2026-10-21, maintainer request)

Block radios now need power (`radios_need_power`, server config, default on). A radio with no power, or on a
network that cannot keep up, is "browned out": no static, hears nothing, and its panel cycles frequency (1 s), "PWR"
(1 s), dark (1 s), then tries the power again.

- [ ] Radio with no battery at all: switch it on and watch the panel cycle; no static. `/rcdebug radio` says
  `switch=true powered=false (browned out ...)`.
- [ ] Solar panel -> charge controller -> large battery -> wire -> radio: the radio runs (draw 125 FE/tick listening,
  375 transmitting, in the common config). `/rcenergy blockinfo` on the battery (look at it and press tab for the
  coordinates) shows it draining; `/rcenergy fillblock 1500000` on it fills it. When it runs dry the radio browns
  out mid-session; once charged again it comes back at the next cycle.
- [ ] Is a 3 s cycle right, and should "PWR" be a different colour than the TX red? The power toggle stays "on"
  during a brown-out, since that is the switch position.
- [ ] `/rcenergy` positions now tab-complete to the block you are looking at (they were plain integers before).
  `/rcenergy infinite` is only for the desk charger; `fillblock` is the way to feed a battery or a radio directly.
- [ ] A world saved with a radio on comes back with the switch on; it runs only if the battery still has charge.
- [ ] Battery charge survives leaving and rejoining the world (it did not until 2026-10-21 midday: every relaunch
  emptied the batteries, which is what made a "charged" battery show PWR). Jade on a battery shows its own charge
  and the total over every battery on the same wire.
- [ ] With no battery on the wire at all the panel stays dark when switched on (no PWR cycle); PWR is only for a
  battery that cannot keep up.
- [ ] Display corners: the mode (SSB, CW, or FM on the VHF base station) top left, TX top right while sending, both
  in the same small size. Does the mode read well on each radio's display texture?
- [ ] "Going Ham" advancement: placing any block radio pops a toast with the icon of the 20m radio and a reminder
  that it needs power and an antenna; it gets its own RadioCraft tab in the advancements screen. Is the wording OK?

## 4:1 balun, off-centre-fed dipole, release tab (added 2026-10-21)

- [ ] New "Balun (4:1)" (`radiocraft:balun_four_to_one`) next to the 2:1 in the tab; its texture is the 2:1 art
  until the bounty is drawn. The 2:1 still feeds the quad loops (horizontal and vertical squares); try one.
- [ ] OCFD: 4:1 balun, arms 4 and 7 on the same level. Right click names `radiocraft:off_center_fed_dipole`; a 20m
  radio sees SWR 1, a 10m radio also 1, a 15m... there is no 15m radio, so check with `/rcdebug antenna`: 21 MHz
  is above 2. A 5 + 5 on the 4:1 is not recognized (that is a dipole, use the 1:1). A 40m OCFD is 7 + 14.
- [ ] Creative tab: the HF station items are in the main section without the blue "dev" watermark (see the
  2026-10-27 release entry at the top for the rest).

## Tooltips (added 2026-10-21)

- [ ] The blanket "Not Implemented (Decorative Only)" line is gone from everything that works: the HF radios, QRP
  radios, both receivers, the VHF base station, the three wires, poles and connectors, the VHF antennas, solar panel,
  charge controller and large battery each have a sentence instead (needs `./gradlew runData`). Only the microphone
  block and the satellite dish still say not implemented. Read the sentences: anything wrong or missing?

## Digital interface (TNC): APRS (added 2026-10-21; now split, see "Radio Interface, H19 and Transfer Interface")

Two VHF base stations a few blocks apart (or with antennas), both on 144.390, each with a digital interface touching
it. `/rcdebug` is not needed; GameTests cover delivery, digipeating and the command, so this is about the screen and
the sound.

- [ ] Placing the interface sets it to your callsign (green lamp tooltip shows who set it). Clicking CALLSIGN opens
  an edit box over the map; ENTER saves, ESC cancels without closing the screen, typing 'e' does not close it, and
  saving it empty claims your own callsign again.
- [ ] The blue lamp beacons: the radio sends a short burst of packet tones (about half a second) that any radio on
  the frequency plays, then the other interface lists the station on the paper (callsign, distance, bearing) and
  draws its icon on the grey map, north up, with a hover tooltip (coordinates, comment, age, digipeaters). Is the
  burst too loud or too long? Do the tiny half-scale letters read OK on the paper and the fields?
- [ ] BCN (comment) and ICON (click to cycle: door, compass, minecart, boat, saddle, campfire, lightning rod, ender
  eye) show up in the far station's tooltip and map. The map zooms out to fit the furthest station.
- [ ] Beacon enable (on either tab) beacons right away and then every 10 minutes (server config
  `tnc_beacon_interval_seconds`); a redstone pulse into the interface beacons too. New-contact buzzer: a high note
  block blip the first time a station is heard.
- [ ] MSG tab: TO box and message box (100 characters), SEND MESSAGE. The far interface buzzes (low blip), the red
  lamp blinks on its screen, red dust flickers on top of the block, and a comparator reads 15 until someone opens the
  MSG tab. The message shows on both papers (yours as "> CALL: text"); long ones wrap and the paper scrolls.
- [ ] A message to ALL lands on every interface that hears it.
- [ ] Digipeating: the blue lamp under RPT on the APRS tab toggles it (lit = on, off by default). With it on, a
  packet heard goes back out a second later on every radio the interface touches; the red RPT lamp lights from
  hearing it until it has gone. Works with one radio (same frequency) or two (crosses over). Nothing past 8 hops.
- [ ] Dummy load (dev tab): touching a radio or on its coax, the radio shows SWR 1.0 on every band and reaches only
  about 10 blocks; an all-band radio switched from HF to 2m follows within half a second. Model and hitbox line up?
- [ ] Duplexer on top of the repeater: the coax legs run down the repeater's back and the duplexer turns to face it
  whichever way the repeater faces; take the repeater away and it goes back to the plain model.
- [ ] J-pole and duplexer: no more z-fighting (maintainer's model fixes).
- [ ] Aurora crackle: during an aurora (`/rcdebug` aurora), past the aurora latitude, in a snowy biome, faint pops
  every several seconds from a few blocks away (vanilla campfire crackle, 4 to 9% volume, high pitch). Should be
  near inaudible over rain, music or a radio's static. Nothing in a warm biome, or when the aurora ends.
  `/auroracrackle [pops]` (client command) plays one burst right now at the same volume and spread, anywhere.
- [ ] `/aprs beacon 144.390 W1AW Net tonight` and `/aprs broadcast all ALL hello` reach interfaces anywhere.
- [ ] Radios on ceilings: click the underside of a block with a radio. It hangs the right way up, its top against
  the block (a raised copy of its model; the repeater rises 4 px). Panel, display and lights read normally; the
  hitbox matches. Every radio, all four facings.
- [ ] Wires on ceilings: click the underside of a block with wire, coax or waterproof wire. It runs along the
  ceiling, joins hanging radios and devices, and carries on sideways when you extend it from a hanging wire (or
  where there is a ceiling but no floor). Runs down a wall hug it; at a beam hanging lower, the cable runs down
  the beam's face to a wire or device under it (the floor climb, upside down). Floor-to-ceiling columns line up on
  one edge. Check the pieces look right flipped (no gaps, textures not inside out) and hitboxes follow the cable.
- [ ] TNC screen size: it scales up to at most 80% of the window, in whole window pixels (crisp art). Try a few
  window sizes and GUI scales; clicks, slots, item dragging, scrolling and tooltips must all still line up.
- [ ] APRS map terrain: the grey screen shows the land around the interface like a vanilla map (shaded by slope,
  dimmed under the icons), north up, and zooms out with the stations heard. Past render distance it is dark.
  Does it repaint when you walk/build (every 3 s)? Any stutter opening the tab?
- [ ] RTTY (stations on an HF band too, say 14.080): whatever you type on the RTTY tab goes out as you type it
  (upper case only, no backspace: it is already on the air), and Enter or SEND ends the line. Both radios play the
  warbling RTTY tones, about 6 characters a second. The far interface prints "CALL: TEXT" with a blinking cursor,
  starts the teletype sound and rings the bell at the end of the line; yours prints its own copy. Does typing 'e'
  or a number key do anything it should not? Is the 0.6 scale text readable?
- [ ] RTTY paper: one sheet does 20 lines (top right of the paper shows lines left, PAPER OUT in red). With no
  paper nothing prints, but you can still send. Paper goes in by right clicking the interface with it, from a hopper
  on top or side, or the FILES tab's PAPER slot.
- [ ] RTTY at the edge of range prints with some characters garbled; a bit further and nothing at all.
- [ ] FILES: the tab shows a vanilla inventory panel under the interface (stand-in art) and the item slots: SEND,
  four RECEIVED, PAPER / BOOKS / MAPS. Other tabs hide them. Shift click sends paper, books and maps to their
  supply slots and written books, books and quills, paper and filled maps to SEND.
- [ ] Send a written book to the far callsign (TO), or to everyone (TO empty): the six LEDs fill up on both
  interfaces as it goes (a few seconds, plus half a second a page), the radios play packet tones, and the far one
  prints a "copy of" into RECEIVED using one of its blank books. The sender keeps the original. A copy of a copy is
  refused. The paper strip on the right logs TX/RX lines, NO BLANK BOOK, TRAY FULL.
- [ ] Send a filled map: SSTV tones (the classic "beep, bloop" start, then about 26 s of scanning) and the far
  side prints the same map on an empty map. Does it sound like SSTV? A hopper under the interface pulls the copies
  out of RECEIVED.
- [ ] Break the interface: its supplies, the SEND item and the received copies drop.

## Wires climb walls (added 2026-10-21)

Lay coax (or power wire) along the ground up to a one-block step with more wire on top of the step.

- [ ] The wire at the foot runs up the face of the step and meets the wire on top at its edge; no floating vertical
  piece, no gap at the top edge. Same for all four directions and for wire, waterproof wire and coax. Does the
  climb piece look right against the horizontal stub (no z-fighting at the bottom corner)?
- [ ] Order does not matter: foot first then top, or top first then foot, and the radio sees the antenna either way
  (`/rcdebug radio`). Break the step: the climb vanishes and the radio loses the antenna; put it back: it returns.
- [ ] A climb straight into a device on the step (battery on a ledge, balun on a post): the cable runs up the post,
  over its top edge and along the top to the middle of the balun's block, ending just under the balun's body. That
  last piece is drawn by the climbing wire outside its own block, so check it against the post's top texture and in
  the dark, and at chunk borders (x or z a multiple of 16).
- [ ] Balun one block above the post top, coax on the post under it (maintainer's build, 2026-10-21): the run comes
  up the post face, carries straight on up the same edge through the block on the post (no wall there, no jog) and
  turns in to the balun's middle only at the ceiling. Nothing extends to the middle at floor level any more. A
  second coax beside that one is a real branch and will draw a stub; leave it out.
- [ ] A wire directly above the foot wire wins: with coax at the foot, on the step and also floating above the foot,
  the foot does not climb (the column carries the run) but still hugs the step, and the piece above arrives at the
  step's face before turning onto the step. Removing the floating piece makes it climb; both look the same.
- [ ] A wire column straight up beside a post or wall (maintainer's screenshot, 2026-10-21): every piece hugs the
  post, the bottom piece turns from its floor run to the post, the top piece arrives on the post's face and turns
  towards its own connection. Where the post has a gap the run jogs back to the middle for that block.
- [ ] Waterproof wire now has a blockstate and renders like wire (it had none and showed the missing-model box).
- [ ] Coax has its own darker texture (maintainer, 2026-10-21, `coaxial_wire.png`) on every piece: floor, edge
  runs, corners, overhangs. Power wire and waterproof wire keep the light one.

## From the 2026-10-19 work (CW restored, transmission tests)

- [ ] CW by ear between two stations: tone starts and stops cleanly, no stuck tone, sidetone on the sending radio.
- [ ] `/rcdebug parrot <pos> <seconds>` on a radio with no controller still echoes voice and CW.

## Power wire: a short forgotten when the wire is replaced (fixed 2026-11-04)

- [ ] Make a loop of power wire on a charged battery so it shorts and smokes; break the shorted wire and open the loop
  somewhere else, then put the wire back: it no longer smokes or burns. Put back with the loop still closed, it shorts
  again from the start. GameTest `replacedWireForgetsShort` covers it.

## Scroll tuning (added 2026-11-04)

- [ ] Sneak, look at a radio that is on (HF, QRP, VHF base station or mobile, receivers, the all-band) and turn the
  mouse wheel: each notch moves one channel step, a soft click plays, and the new frequency shows above the hotbar
  ("146.535 MHz"); the hotbar slot does not change. A radio that is off does not tune (the wheel still does not move
  the hotbar while you look at it). Not sneaking, or looking elsewhere, the wheel changes the hotbar as usual. The
  repeater is left out. A trackpad's small scrolls add up to whole steps. Client setting `scroll_tuning` (in
  radiocraft-client.toml) set to false turns it off.

## Moderation computer boot (added 2026-11-04)

- [ ] The first time a computer is opened in a session it boots in full inside its window (about 5.5 s): a BIOS
  screen (memory count ticking up to 65536K, drives detected one by one, "Starting Radio Moderation 98..." with a
  cursor), then a navy-to-blue splash with four waving panes, "Radio Moderation 98" and a sliding blue progress
  strip while the disk clicks, then a rising bell and chime as the desktop fades in. Reopening it shows only the
  splash (about 1.4 s). Any key or click skips to the desktop (the key does not type into a box); Escape still closes.
  Check the sounds are not too loud and the text fits at GUI scales 2-4.
- [ ] (Changed 2026-11-04, evening.) The only boot sounds are the memory count ticking and the chime: no disk clicks
  during the drive detection or the splash. The splash's flag has a violet pane where the green one was.

## Moderation computer sorting, Listen, Reboot; config names (added 2026-11-04, evening)

- [ ] Click a column header on Live, Archive (radio and proximity), Tapes, Proximity Live and both Access lists: the
  rows sort by it with an arrow on the header; again reverses. Numbers sort as numbers (frequency, hearing, time,
  plays), the selection stays on its row, and the order holds as Live refreshes. Archive sorts the page shown.
- [ ] Live: a Listen button after "Listen to talker". Pick a row (a player, a tape, a repeater, a commercial station
  with "Include system/world stations" on) and press Listen, or double-click the row: you hear everything on that
  frequency, clean, and the line top right says "Tuned to 146.5200 MHz". Stop listening ends it. Proximity Live's
  double-click still listens to that speaker's voice chat. The audit log shows "listen frequency ...".
- [ ] The screen is now never laid out narrower than 540: at GUI scale 4 on a small window it is drawn a little
  smaller rather than the Live bottom row overlapping.
- [ ] Access: Reboot (left side, beside Remove selected) runs the full BIOS boot again, stops listening and playback,
  and every tab starts afresh (filters cleared, Live tab).
- [ ] Mod list, RadioCraft, Config: every setting and section has a readable name (HF Frequency Step, Line of Sight
  Range under 10m Band, Alternator FE at 256 RPM...), no raw keys, no "( * = Restart" in section names; hovering
  still shows the description. Toggles, number boxes and enums edit as before.

## Moderation computer left on, its monitor, Freeze (added 2026-11-04, evening)

- [ ] Open a computer, change things (another tab, a filter, a selected row, scroll, the map dragged and zoomed),
  close it, open it again within 10 minutes: everything is as you left it and it does not boot. Leave it closed over
  10 minutes (or go to another dimension and back): it boots and starts afresh. Playback stops on closing as before;
  live listening carries on, and the Live tab still shows who you are listening to.
- [ ] Its monitor in the world shows the window as you left it (title bar, tabs, the list), lit at night, on the
  glass above the taskbar, the right way round on all four facings, and not flickering against the glass. Another
  player (or the same computer after 10 minutes) sees the plain desktop. Opening a second computer leaves the first
  one's monitor showing its own window. Watch for a stutter while the screen is open (it is copied every 4 frames).
- [ ] Live tab: a Freeze checkbox at the right end of the bottom row, never overlapping "Hear what they hear" (shrink
  the window: the player dropdown narrows first, then the Freeze label). Ticked, the list stops updating so you can
  scroll and read it (the durations stop too); the bell still rings; unticked, it catches up at once. Same on the
  Proximity tab's Live view.

## Small fixes (2026-11-04, afternoon)

- [ ] Handheld volume: 100% is as loud as before; lower settings drop off much faster (10% is about a hundredth of
  full, 50% a quarter). Also for a handheld in the desk charger.
- [ ] Handheld direction finder: the beep is about a third as loud; its lamp now flashes on the lamp on top of the
  case, not beside it.
- [ ] Mod list: RadioCraft has a Config button again, opening NeoForge's settings screen (client and common settings;
  server settings while in a world). Settings show their raw names, descriptions on hover.
- [ ] Moderation computer: the Live tab shows who is on the air even with recording off (nothing is written to disk);
  the Archive says "Logging is disabled" while recording is off; "Include system/world stations" sits between the
  heading and the listening line and shows commercial modules, server tapes and repeats when ticked.


## Battery meter UVs, floppy names (added 2026-11-11)
- [ ] Battery meter: the voltmeter front, black case and red/black posts show on every face, with no fire alarm
  or other block art bleeding onto it.
- [ ] All Applications Floppy used on the computer or monitor: "Every program installed (N new)", and it stays in
  your hand (before, it went into the drive and INSTALL said the disk was blank). Picked from INSTALL it still
  installs everything.
- [ ] The FCC Monitor, Fire Alarm and SCADA floppies are named "... Floppy Disk", not "null Floppy Disk".

## Outdoor sirens (added 2026-11-11; moved to Another Weather Mod 2026-11-13: test them there, `anotherweathermod:omni_siren` etc.)
- [ ] Omnidirectional Siren and Horn Array Siren: two blocks tall, placed on a fence post or log; the seven grey cells
  (or four black horns a side) look right from all sides, with no seam at the join between the halves. Breaking
  either half takes both and drops one.
- [ ] Their item icons show the whole siren, shrunk, in the hotbar, in the hand and on the ground.
- [ ] Pole-Mounted Omnidirectional / Horn Array Siren: placing it stands a cabinet, nine blocks of pole with a conduit up
  the side and the head on top (twelve blocks); the head's collar or box sits on the pole top.
- [ ] Right click the cabinet: "Siren switched on" and its lamp lights (glows in the dark); again to switch off. Redstone at
  the cabinet lights it too.
- [ ] FCC Monitor icon: whole, even red signal arcs over the gold shield, no stray red bits above it or under the bar
  (added 2026-11-11).
- [ ] Computer, scanner, commercial radio in a row (scanner in the middle): the FCC Monitor, Distress and the other
  programs find the radio as if it were cabled (added 2026-11-11).

## CPS menus and read/write feedback (added 2026-11-11)
- [ ] CPS: File / Program / Edit in the title bar; click one (or F10, then arrows and Enter) and every item works
  (Open, Save, Save as, Exit; Read, Write, By hand, Factory; Edit, Clear, Copy, Paste, Unit ID, Contacts). The F keys
  still work. Esc or a click elsewhere closes the menu.
- [ ] Reading or writing a radio (cabled or by hand) shows a "Reading radio..." / "Writing radio..." box whose bar
  fills over about a second, then the result.
- [ ] Write by hand (Continue, right click the computer with the radio): back at the computer a "Write Successful"
  box says which radio; closing the CPS then does not warn that the codeplug is unsaved. Same for a cabled write.
- [ ] Saving (^S or File, Save) clears the "*" and the unsaved warning; editing a channel brings both back.
- [ ] Two coax runs of different colours (red and black, or red and plain) bundled together for a few blocks, with
  a bundle block against the other run's radio or antenna: each radio shows only its own antenna, no COAX SHARED, and
  it stays so after leaving and rejoining the world. The radio or antenna beside a bundle takes only the coax that
  ends there, not one that runs on past it; a lone coax block still takes everything it touches. Taking the second
  strand out of that bundle block (sneak + empty hand) lets the passing coax take the device again. (added 2026-11-11)
- [ ] CPS Edit, Import from Frequency Manager (or ^I): pick a .FRQ file, then an FM or digital entry; it lands in the
  selected channel (or the first blank one) with its name, RX/TX and tone. SSB/CW/AM entries are not offered.
- [ ] Red and black coax (or plain and a colour) through the same passthrough, each to its own radio and antenna:
  neither radio says COAX SHARED, also after rejoining the world and after breaking and replacing the passthrough
  (added 2026-11-11). A radio placed directly against the passthrough still takes every colour through it.

## Wires hug walls (added 2026-11-11)
- [ ] Coax (and power, data, dyed) laid on a floor beside a wall: it lies tucked into the floor/wall corner, touching
  the wall, only along the stretch with a wall; past the wall's end it eases back to the middle of the floor. Wire laid
  before this change moves there as soon as the chunk redraws.
- [ ] A run along a wall with nothing under it (a ledge-less wall over a drop): the cable sits against the wall at the
  bottom of each block, in a straight line, and carries on along that line over a one-block gap in the wall.
- [ ] A run round the outside corner of a pillar with nothing under it: the cable wraps the pillar's corner, no
  shortcut through the air.
- [ ] An inside corner (two walls meeting) on a floor: the cable sits in the corner; a run up the wall there goes up
  the corner itself.
- [ ] A corridor one block wide with walls both sides: cable keeps to the wall the cable it joins is on. Lay a run from
  each end on opposite walls (easiest: start one end against a radio or turn on one side), then join them: the block
  laid last crosses the floor from one wall to the other, and the cable already laid does not move.
- [ ] A run up a wall where the wall ends but another side has a wall higher up: the run goes over to the other wall
  instead of on up through the air.
- [ ] Cable up a wall to a ceiling and on under it (across a room): the run under the ceiling hangs from it, and the
  top of the run up turns onto the ceiling cleanly (no gap at the turn).
- [ ] A passthrough set in a wall at head height with cable running along the wall to it on both sides, no floor
  under: the cable stays against the wall right up to the passthrough on both sides.
- [ ] A one-block-wide wall climbed from both sides to a wire on top: both climbs are drawn joined to the wire on top,
  and both sides are on the same network (a radio on one side sees an antenna on the other).
- [ ] A wall-hugging run ending at a radio, antenna or other device: the cable jogs out to meet the device at the
  middle of its face. A bundle against a wall: the whole bundle moves over, its outer strand touching the wall, and
  its strands still spread into the devices as before.
- [ ] Hitboxes still follow the cable after it moves against a wall (look at a tucked run and its corner pieces).

## Launcher search (added 2026-11-11)
- [ ] In the list and icon views, start typing: a Search box opens with matching programs first (names starting with
  what you typed first), then files on the drive shown. Arrows pick, Enter (or a click) runs the program or opens the
  file, Backspace edits (empty closes), Esc or a click outside closes. The command line view still takes typing as
  before; Space alone does not start a search.

## Satellites (added 2026-11-11)
See doc/SatellitePlan.md. Quickest start: `/radiocraft satellite add amateur` (papers go to you), then
`/radiocraft satellite overhead RO-1 10` hangs it over you for ten minutes.
- [ ] A master cartographer may offer an Amateur or a Commercial Satellite Contract for 64 emeralds and a diamond (one
  use before restocking). The contract, satellite license and SATTRACK floppy icons look right in the inventory and
  creative tab (placeholder art from tools/satellite_items.py).
- [ ] Right-clicking a contract: a rocket launch sound, a chat line with the name, uplink, downlink and tone, and three
  items: a commercial frequency license (its picture shows the downlink, with the uplink as its input), a Satellite
  License (name in its title; tooltip with both frequencies, tone, licensee and launch day) and the SATTRACK floppy.
- [ ] Sky at night with a satellite passing (use `overhead` or wait for a pass SATTRACK lists): a tiny green dot (red
  for a commercial one), a pixel or two, faint between flashes and flashing brighter about every 6 s, each flash a
  different brightness. It sits where SATTRACK says (elevation and compass point) and moves across the sky over a few
  minutes on a real pass. Nothing by day, nothing below the horizon, fading out in rain, nothing under water or in the
  Nether. Near dusk and dawn an occasional grey flash halfway between the coloured ones. Check it costs nothing
  noticeable in frame time.
- [ ] A dot is the same size at different FOV settings and window sizes (about 1.5 px, 2 px in a flash), and is hidden
  behind blocks and hills in front of it.
- [ ] Voice through the satellite: a VHF radio (or handheld) with an antenna under open sky, tuned to the downlink with
  the -600 offset and the satellite's tone, talks; another radio on the downlink far away (or right next to it) hears
  it. Without the tone, from indoors, or with the radio's bare connector (no antenna) nothing goes up. After an over
  the satellite sends its ID text in Morse on the downlink (at most every 5 minutes). A commercial satellite works the
  same from a commercial radio programmed with the pair (CPS, with the license on you).
- [ ] SATTRACK (install from the floppy): every satellite listed, green or red; the picked one with frequencies, tone,
  ID, owner, allowed list, launch day and days to decay, "Passing now" with elevation and direction while it is up, and
  its next passes (rises in, max elevation, from, up for) counting down. As owner while it passes: F2 on/off, F3 ID
  text, F4 tone, F5/F6 allowed list all work; while it is below the horizon they refuse with a message; as another
  player they refuse. The icon shows in the launcher.
- [ ] A distress beacon set off under open sky while a satellite passes is heard by a commercial radio on 161.975 MHz
  far beyond line of sight (a few thousand blocks), and a DSC distress call likewise by a far marine radio; the alert
  shows once (copies merge).
- [ ] `/radiocraft satellite list` from different places shows each satellite's elevation and direction (or distance)
  from there; `remove` takes one off the sky at once for every player.
- [ ] Handbook: Repeaters > Satellites and Computer > SATTRACK read right, their links work, and the contract
  spotlight shows its item (added 2026-11-11).

## DISTRESS as a background service (added 2026-11-11)
- [ ] With DISTRESS installed and no one at the computer, a new beacon alert (commercial VHF on 161.975) or DSC distress
  call (marine radio) heard by a cabled radio sounds the DSC alarm at the computer, again every 20 s until someone
  opens DISTRESS. Opening the computer then opens straight on DISTRESS with the alert listed.
- [ ] At the computer with another program open, a new alert brings DISTRESS to the front.
- [ ] DISTRESS left open keeps running after the screen is closed for more than 10 minutes (it is not dropped).
- [ ] Your own outgoing personal SOS does not set off your own computer's alarm. Alerts already logged when DISTRESS is
  installed are not announced.
- [ ] DISTRESS F6 prints the map round an alert on plain paper (as well as an empty map or photo paper). Elsewhere, a
  picture print with only plain paper in the tray now says it needs an empty map or photo paper, not "not enough
  paper" (added 2026-11-11).

## Passive distress receiver (added 2026-11-11)
- [ ] A powered commercial VHF mobile on an ordinary channel (not 161.975) hears a distress beacon a few blocks away:
  no audio and nothing on its display beforehand, then the DSC alarm and "DISTRESS SOS RX x, y, z" (the beacon's
  coordinates) scrolling across the in-world display, a character at a time, with the owner and BEACON on the small
  line. The scrolling text keeps one size (it does not grow and shrink) and stays inside the display.
- [ ] The same alert on the mobile's screen (open it): the top LCD line scrolls, whose beacon under it; the DISMISS key
  and sneak-use both clear it, on the screen and in the world. A repeat of the same beacon does not bring it back.
- [ ] A low band mobile never shows a beacon alert.
- [ ] Upgraded commercial handheld (powered, any channel) in the inventory near an active beacon: the alarm plays to
  the holder, and sneak-use opens a screen whose LCD scrolls "DISTRESS SOS RX x, y, z" with whose beacon under it.
  A DISMISS button shows beside the radio; it clears the alert, the button goes away, and the alert stays dismissed
  after closing and reopening the screen and through the beacon's repeats.
- [ ] Basic commercial handheld (no LCD): the alert scrolls across a green plate to the right of the radio, with the
  same DISMISS button. The plate and button do not overlap the radio or its knob readouts at the usual GUI scales.
- [ ] Close the handheld's screen with an alert up and reopen it (and relog): the alert is still there until dismissed.
- [ ] A commercial handheld that is switched off hears nothing; your own beacon does not set off your own handheld.
- [ ] A commercial handheld in a desk charger hears a beacon: the alarm sounds at the charger, and taking the radio out
  and opening its screen shows the alert.
- [ ] DISTRESS on a computer cabled to a commercial VHF mobile on an ordinary channel: the top right reads
  "BEACONS: 1 radio(s) listening on 161.975" in green, and a beacon's alert is listed and announced by the service.
- [ ] Handbook: Commercial Radios, Distress Beacon, Marine Radios and Computer > DISTRESS read right with the new
  any-channel wording, and the Commercial Radios page does not overflow.
- [ ] Satellite in the night sky: a faint grey dot between flashes, the green or red only in the flash (added 2026-11-11).
- [ ] SATTRACK: F7 turns your passing satellite on and off (F2 took a screenshot); the key bar says F7.
- [ ] Launcher search: no program icons show through the search box in the icon view.
- [ ] Icons: the CPS icon is a handheld (antenna, knobs, LCD, keypad, PTT, cable); the Packet BBS icon has no stray
  pixel in its bottom right corner.

## Plain paper prints, MURS, fire alarm key (added 2026-11-12)

- [ ] Scanner with plain paper in the tray: DISTRESS F6, an Awards certificate (F5) and a picture file each print a sheet of
  paper named for the print, with its details in the tooltip (and "Printed by"); an empty map or photo paper still prints the picture.
- [ ] FCC Monitor on a MURS channel (151.820, 151.880, 151.940, 154.570, 154.600) says MURS: NO LICENSE NEEDED, and a radio
  transmitting there is not flagged unlicensed.
- [ ] Fire Alarm Key has its own texture (square stippled bow with a triangle hole and number plate, tubular barrel), not the trial key.
- [ ] Panic button item: in the inventory its red button faces you (it showed its underside, tipped back); held, it sits like
  a vanilla pressure plate. Ground and item frame unchanged.

## Satellite modes (added 2026-11-12)
See doc/SatellitePlan.md, "Modes, telemetry, Doppler and eclipses". Quickest start: `/radiocraft satellite add amateur`,
then `/radiocraft satellite overhead RO-1 10`. Satellites from 2026-11-11 should get a telemetry frequency on load.
- [ ] A new contract's chat line and Satellite License tooltip show three frequencies (uplink, downlink, telemetry); an
  old license (from before) still shows its two without a blank line.
- [ ] SATTRACK detail panel: "Up / Down / Tlm" line, the mode line, owner and allowed list on one line, decay, passing
  line, telemetry lines and the passes all fit inside the box without overlapping the bottom line, for an amateur and a
  commercial satellite. The key bar fits ("F7=On/Off ... F10=Picture").
- [ ] As owner while it passes: F8 steps REPEATER > APRS > MAILBOX > SSTV > REPEATER; F9 sets the APRS frequency
  (144.390 shows "NOT RECOMMENDED (terrestrial APRS)" in yellow, 150 refuses with a message, blank goes back to the
  downlink); F10 uploads an .IMG and the mode line shows its name. On a commercial satellite F8-F10 refuse with "only
  repeats". Below the horizon they refuse as F3-F7 do.
- [ ] APRS mode: an APRS terminal or the APRS program on the satellite's downlink, far apart (a few thousand blocks),
  both under open sky: each sees the other's beacons on its map and messages go through during a pass, not after it
  sets. A radio on the downlink hears no voice repeat in this mode.
- [ ] Mailbox mode: in the Packet BBS program's terminal page, call the satellite by its name (RO-1) with a radio on its
  downlink: the greeting, `SP <call> subject`, the body, `/EX` ("MSG 1 SAVED"). On a later pass from somewhere else
  (another station or another callsign) `L`/`LM` lists it and `R 1` reads it. After a server restart the message is
  still there.
- [ ] SSTV mode with a picture: the SSTV program on a computer with a radio on the downlink receives it line by line
  during the pass (sounding like SSTV through the radio), keeps it as SSTV0001.IMG labelled with the satellite's name,
  and it prints like any SSTV picture. Another comes about 30 s after the last ends while you are under it.
- [ ] Telemetry: a radio on the telemetry frequency chirps a packet burst every 10 s while the satellite is up, and
  sends the satellite's name in Morse about once a minute. SATTRACK (radio on the computer's station tuned to the
  telemetry frequency, SATTRACK open) shows "Telemetry, heard 0:0x ago" with volts, amps, temperature, SUN/SHADOW, mode
  and days; the values look plausible and change between day and night. "No telemetry heard: tune a radio to ..." before.
- [ ] Doppler (set `satellite_doppler = true` in the server config): SATTRACK shows "Doppler +3.x kHz" as it rises,
  near 0 overhead and negative as it sets; an FM radio left on the downlink loses the satellite near the ends of a pass
  until retuned 2-3 kHz, and hears it fine overhead.
- [ ] Eclipse: `/radiocraft satellite eclipse RO-1 3` while it passes: SATTRACK shows the blinking ECLIPSE line, the
  repeater and its mode go quiet (a distress beacon is still relayed), and the telemetry radio hears only one packet
  burst a minute, no Morse name; SATTRACK's telemetry shows "POWER SAVING: eclipsed, battery flat". After it ends the
  next telemetry shows a low battery and SAFE, and SATTRACK shows "BATTERY LOW after an eclipse". With `satellite_eclipses = true`
  they happen rarely by themselves, at night only.
- [ ] Owner warning: with `satellite_decay_days` set so the satellite is in its last week, SATTRACK shows "OWNER: its
  orbit decays in N days" under the telemetry, for the owner only.
- [ ] Commercial satellite: its frequency license item's tooltip shows "Satellite telemetry: ... MHz" and its picture a
  TLM line that does not run into the seal or the COPY box; the FCC Monitor hearing its telemetry beacon says LICENSED
  with its callsign. A satellite launched on 2026-11-11 gets the line on its license after a restart (carry the item).
- [ ] Handbook: Repeaters > Satellites (new pages Modes, APRS, Mailbox, SSTV, Telemetry, Doppler and eclipses) and
  Computer > SATTRACK (Controlling yours, Its mode with F8-F10, Telemetry) read right, no page overflows, and the links
  to Packet BBS and SSTV work.

## Weather imaging and display (added 2026-11-12)
See doc/SatellitePlan.md, "Weather imaging". Quickest start: `/radiocraft satellite add commercial`, then in SATTRACK
(while it passes: `/radiocraft satellite overhead CS-1 10`) F8 to IMAGING. Put a VHF receiver (one channel on, set to
the satellite's downlink) or a commercial radio touching a radio computer with WXSAT installed, under open sky.
- [ ] SATTRACK on a commercial satellite: the mode line reads "Mode: REPEATER (F8: weather IMAGING)"; F8 while passing
  switches to "Mode: IMAGING, weather pictures on its downlink" and back; the bottom line says "F3-F8". On an amateur
  satellite F8 still steps REPEATER > APRS > MAILBOX > SSTV and never IMAGING.
- [ ] The downlink sounds like NOAA APT on the radio: a steady tone with a tick-tock twice a second, its loudness
  changing with the picture. A repeater user on the uplink is not repeated while it images.
- [ ] WXSAT live view (F5): the strip fills in from the bottom as the pass comes in (256 px wide), with RECEIVING and
  the signal bar in the right panel. High in the sky the lines are clean; low near the horizon they get snowy and some
  go black (lost). The panel lists the imaging satellites with their next pass or elevation (and Doppler if on).
- [ ] The picture looks right: seas blue, land in biome colours (deserts sand, forests green, snow white, badlands
  orange) and real blocks where chunks are loaded; rain in the radar colours (light green to red); under AWM a severe
  storm's core magenta and hail purple, snow blue in cold biomes; with vanilla rain, drifting rain cells, not a flat
  green sheet. Check orientation: a known coastline or lake near the station is where the map says it is.
- [ ] Mosaic (F6): the pass is stitched round the station with a white cross on it; arrows pan, F8 / +/- zoom 8, 16,
  32, 64 blocks a pixel, Home comes back. A second pass lays over the first (newest on top); gaps show the older one.
- [ ] Loop (F7): steps through the passes a second each, "Pass n/m CS-1 D3 0640" at the bottom; Space pauses ("PAUSED"),
  PgUp/PgDn step. F10 opens File/View menus that do the same as the keys. The key bar fits on one line.
- [ ] F3 saves WX0001.IMG (opens in the image viewer); F4 prints on the scanner: a map or photo paper gives the
  picture (no-data black), plain paper a sheet whose tooltip has the area, the pass and time, and "PRECIP n%, WORST ..."
  or "NO PRECIPITATION".
- [ ] Background: with WXSAT installed and nobody at the screen, a pass is still recorded (open WXSAT afterwards); the
  computer draws power meanwhile. The mosaic survives a server restart; old passes (more than 8, or days old) go.
- [ ] Program Manager icon: the WXSAT icon (satellite with dish over a radar sweep) looks right in each colour scheme.
- [ ] External CRT Monitor: placed, it faces you; beside the computer (or beside another linked display) it lights while the
  computer is on and shows the mosaic centred on where it stands, north up, east to the right. Check all four facings:
  the picture is never mirrored and a known landmark is in the right place.
- [ ] Right-click cycles LIVE MOSAIC > LOOP > OFF (action bar message) for the whole wall. OFF: the picture collapses to
  a bright line, then a dot that fades. Back on: a bright line opens into the picture.
- [ ] A wall of displays (e.g. 3 x 2, all facing the same way) shows one picture across all tubes, the bezels hiding the
  strips between (straight lines and coastlines continue across the bezels without a jump or squash). Breaking one
  splits the wall into smaller screens within a second; placing it back rejoins them. A wall bigger than 8 x 6 splits.
- [ ] CRT faults are subtle, not distracting: faint scanlines, darker corners, a faint glow, a slight colour fringe at
  the edges, a very slight flicker, a little noise, now and then a faint bar rolling up. The picture is smoothly scaled
  (no blocky pixels at close range) and full bright at night. No z-fighting with the glass from a distance.
- [ ] Performance: a full 8 x 6 wall in LOOP during a pass does not stutter the client or the server.
- [ ] Handbook: Computer > WXSAT and Computer > External CRT Monitor (spotlight pages show the floppy and the display), and
  the new "Weather imaging" page in Repeaters > Satellites read right; the links work.

## EB-VHF eggbeater antenna (added 2026-11-12)

- [ ] Placed, it stands two blocks tall: mast, eight radials round its foot (reaching about a block out each side), a
  black balun at the top of the lower block, and two crossed loops (copper and aluminium) on top. The loops are round,
  with no zigzag segments or gaps (each loop is 16 turned rods; a wrong turn direction would show as a star shape).
- [ ] All four facings look the same apart from turning; coax laid beside the lower half shows the run in to the mast.
- [ ] Item in the inventory, hand and item frame: the whole antenna fits and is upright.
- [ ] On a satellite pass (`/radiocraft satellite overhead`): with an eggbeater the downlink is strong all pass and best
  overhead. Swap to a J-pole: fine low in the pass, fading past about 40 degrees, weak straight overhead, and right
  under the satellite (within about 5 degrees of overhead) the satellite stops repeating you.
- [ ] Handbook: Repeaters > Satellites has an "Antennas" spotlight page on the eggbeater.

## External CRT Monitor rename, radio waterfall, background services switch (added 2026-11-12)

The Weather Display is now the External CRT Monitor (`radiocraft:external_crt_monitor`; worlds that had the old block
lose it, it was never released). The items above under "Weather imaging and display" still apply to it.

- [ ] The block and item are named "External CRT Monitor" in JEI, the creative tab and the handbook; the model and
  texture are the same as before. The tooltip has two lines: the WXSAT mosaic use and the radio waterfall use.
- [ ] Recipe: iron ingots across the top and down the sides of a glass pane, then wire, copper ingot, wire (III / IGI /
  WCW) makes one. A blank floppy and a lightning rod (shapeless) make the WXSAT floppy. Both show in JEI and on the
  handbook pages (External CRT Monitor's last page, WXSAT's last page).
- [ ] Radio waterfall: one monitor touching a premium radio (e.g. The Swan, or the premium VHF base station), nothing
  else touching it: with the radio on it lights and a waterfall scrolls down, newest at the top, signals blue to
  white, a faint line down the dial. Talk on SSB from another station nearby: the voice shows as a block beside the
  dial line (upper or lower side), gone between words. CW shows as a thin line; FM on VHF as a wide band.
- [ ] Right-click on it cycles NARROW WATERFALL > WIDE WATERFALL > OFF (action bar). WIDE shows the same signals
  narrower; OFF does the collapse-to-a-dot animation. Retuning the radio moves the picture (it starts over).
- [ ] The same tube effects as the mosaic (scanlines, glow, vignette, flicker, hum bar); the waterfall is smooth, not
  smeared or upside down, on all four facings.
- [ ] Not on a plain or cheap radio: the monitor stays dark. Not when a second monitor is beside it (a wall). Not when a
  WXSAT computer is also touching it (the mosaic wins). Switching the radio off darkens it.
- [ ] Rig control's waterfall on the same radio still works while the monitor shows (both at once, each its own span).
- [ ] SETUP has a sixth page, Services: "Background services  On", the installed services listed (DISTRESS, WXSAT).
  Enter on it asks "While background services are off, this computer cannot receive distress alerts, weather
  satellite passes and the like unless you are in that program. ... Turn them off?" Yes turns it Off; No leaves it.
  Enter again turns it back On with no question. Only the owner may change it. Text fits the panel.
- [ ] With services Off and nobody at the computer, it goes dark (no power drawn) and a CRT monitor on it goes dark
  too; sitting down at it lights the monitor again. A WXSAT pass while WXSAT is closed is not recorded; with WXSAT open
  it is. A distress alert while DISTRESS is closed does not sound the alarm or pop DISTRESS up; it is in DISTRESS's list
  when you open it. Sending a personal SOS and closing DISTRESS does not stop it (2026-11-12, maintainer): it keeps
  repeating and the computer stays on until you end it; then, with DISTRESS closed, the computer goes dark.
- [ ] With services Off, leaving DISTRESS open and closing the screen does not keep the computer's session (it boots
  again after a while), unlike with services On.

## Yagi aimed in 16 directions (added 2026-11-12)

- [ ] Place VHF yagis facing between the compass points (e.g. NNE, NE, ENE): the beam points where you faced, in
  22.5 degree steps. A turned yagi is drawn turned about its mast as one piece (boom, elements and the diagonal brace
  together, nothing detached or doubled), lit like the blocks around it, and doesn't vanish when its block is just off
  screen. Straight N/E/S/W yagis look exactly as before.
- [ ] Breaking either half of a turned yagi breaks both (particles show the yagi texture).
- [ ] Existing yagis in an old world load unchanged (straight along their facing).
- [ ] Signals: a yagi aimed at a station 22.5 or 45 degrees off a compass point now hears it at full strength. (Before
  this every yagi beamed north for the signal maths, whichever way it was drawn.)

## Satellite transit delay, geostationary satellites, decommissioning, burnout (added 2026-11-12)

`runData` has been run. Get satellites with `/radiocraft satellite add amateur` (or `commercial`), and geostationary
ones with `/radiocraft satellite add geostationary amateur|commercial` (they point where you stand). Use
`/radiocraft satellite overhead <name>` to bring an orbiting one up now; `/time set night` for the sky. GameTests cover
the delays, the geostationary coverage and decommissioning on the server (`SatelliteGeoGameTests`); what needs eyes
and ears is below.

Transit delay:
- [ ] Two players (or two radios) on an amateur satellite's downlink with the -600 kHz offset and its tone. Talk: you
  hear yourself and each other come back about 0.3 s late, smooth, with no gaps, stutters or reordered words. A
  normal back-and-forth still feels usable.
- [ ] The same through a geostationary one: the echo is shorter (about 0.15 s).
- [ ] The Morse ID after an over starts only after the delayed voice has finished (it never talks over your last words).
- [ ] APRS mode: a beacon comes back as a digipeat about 2 s later (1 s via a geostationary one); MAILBOX: answers
  start about 4 s after each line (2 s geostationary) and the conversation still works from the Packet BBS terminal.

Geostationary satellites:
- [ ] Right-click a geostationary contract (creative tab, after the satellite contracts): chat says it is parked and
  pointing at your X, Z. The item shows the contract icon (placeholder) and its tooltip reads well. A master
  cartographer's trades include both geostationary contracts for 64 emeralds and a block of diamonds.
- [ ] At night it is a dot that stays put in the sky (not straight up where you used it: 50 to 80 degrees up, off to
  one side) and flashes green/red about every 18 s, less often than an orbiting one (6 s). Two bought in one place sit
  at different spots.
- [ ] Walk or `/tp` 9,000 blocks away: still up, lower in the sky. 11,000 blocks away: gone, and a radio there does not
  hear it.
- [ ] SATTRACK: it is marked GEO; the list says "UP el dir" from inside its coverage and "NEVER UP" outside; the detail
  page says "Geostationary: always up here, el X to the Y" (or "never up from here") and "No passes", no passes table.
  Its decay date is about 45 days out (with the default 90).
- [ ] A commercial geostationary one set to IMAGING (F8): WXSAT receives lines that build a strip through where the
  contract was used (along one direction, then starting over), not a moving pass.

Decommissioning:
- [ ] SATTRACK on a satellite of yours while it passes: the bottom line now says "DEL ends it". DEL asks a Yes/No
  question; Yes shows a FINAL WARNING whose buttons are No / Yes with No picked (Enter alone cancels); Escape or No at
  either step leaves it working. Yes twice: the message "... is decommissioned" and it is listed DEAD.
- [ ] A player on its allowed list (not the owner) presses DEL: nothing happens (the line does not offer DEL).
- [ ] After: no repeater, no telemetry on its telemetry frequency, no relay; its detail page shows DECOMMISSIONED in
  red, days to decay, where it is; F3-F10 give "decommissioned: it takes no more commands". A commercial one's
  frequency license shows as not valid / the FCC Monitor no longer calls its frequencies LICENSED.
- [ ] In the sky it no longer flashes or shows the grey dot. Only near dusk and dawn, an occasional faint, irregular
  greyish-white glint as it tumbles in the sunlight; in full night nothing (it should look dead, not like a working
  satellite).

Burnout:
- [ ] At night, bring one overhead (`/radiocraft satellite overhead <name>`), then `/radiocraft satellite decay <name>`
  (it comes down as a decaying orbit does; chat says how many players saw it). A tiny 1-pixel orange-white streak with a short faint trail runs a little way across the sky and
  sinks, fading out in 3 seconds. Barely noticeable, not a firework. A player far away (where it was below the
  horizon) sees nothing.

## Menu bars in every computer program (added 2026-11-12)
- [ ] Every program (EDIT, BOOKEDIT, LOGBOOK, FREQ, RIGCTL, FT8, RTTY, APRS, BBS, FAX, SSTV, WEFAX, DFIND, DISTRESS,
  NETCTL, AWARDS, QSL, PROPAG, SOLAR, REPEATER, SCHED, SETUP, INSTALL, FIRE, SCADA, FCC, ADSB, MUSIC, BIBLE, STUDY,
  BANDPLAN, ABOUT, SYSINFO, SNAKE, PILEUP, the shell's file list and icon views) shows File / Edit / View-style menu
  names at the left of its title bar, with the title right of them, not covered and not running into the clock.
- [ ] Click a menu name: it drops down with each item's key at the right ("Send   F5"). Clicking an item does exactly
  what its key does. F10 (or a click) opens the menus, arrows move, Enter picks, Esc or a click elsewhere closes
  without leaving the program. BOOKEDIT, RTTY and STUDY keep F10 for their own job; their menus open by click only.
- [ ] Items that only work on one page or in one mode are grey there and clicking them does nothing (STUDY's Grade
  outside an exam, RIGCTL's Remote items off the Remote page, BBS mailbox items on the terminal page, SOLAR's PgUp/PgDn
  on the solar page, etc.).
- [ ] Pictures and icons (shell icons, FAX/SSTV/WEFAX pictures, DFIND map and 3D view, DISTRESS map, QSL card, ADSB
  scope) do not cover an open menu.
- [ ] Typing while a menu is open types nothing into the program; the program still works by keyboard alone with the
  menus never opened.
- [ ] RIGCTL: the PTT item toggles (first click keys, second unkeys), as T held does. FIRE: Acknowledge (F2) works from
  the menu even though Minecraft takes F2 for screenshots. Shell: Exit leaves the shell as Esc does; the DOS prompt has
  no bar.

## Satellite tracking antenna and stealth mode (added 2026-11-12)

- [ ] The Satellite Tracking Antenna (in the creative tab after the eggbeater) looks like the microwave dish on a wide
  plinth and turntable, in hand, in the GUI and placed. Placed with nothing up, it stows pointing straight up.
- [ ] Coax to a VHF radio and a power wire from a battery: with a satellite up it turns (the turntable rotates, the
  plinth stays still) and tilts smoothly to it, then follows it across the sky. Using it says "turning to" then
  "locked on <name> (az, el)". Tuned to a satellite's downlink or uplink it follows that one; otherwise the highest up.
- [ ] Take the power wire away (or flatten the battery): it stops where it is and says "no power for its motors".
  The battery drains a little faster while it tracks (8 a tick) and not at all once stowed.
- [ ] Under an aurora (`/rcdebug` or a forecast) or in a thunderstorm, a satellite through a J-pole or eggbeater is
  weaker and low in the pass the uplink fails; through a locked tracker it stays full strength.
- [ ] Stealth: on a commercial satellite you own, while it passes and your tracker is locked on it, S in SATTRACK (or
  Satellite > Stealth on/off) turns stealth on; the list shows STL. Without a tracker: "Stealth needs a satellite
  tracking antenna...". In stealth, a station with only a J-pole or eggbeater hears nothing from it and is not
  repeated; the tracker station is. Another player's SATTRACK shows only its name, STEALTH, where it is and its passes.
- [ ] SATTRACK has a menu bar (File, View, Satellite), each item doing its key; items you may not use are greyed.

## Moonbounce, CW keying, sidetone and the iambic keyer (added 2026-11-12)

- [ ] SATTRACK lists MOON first (white, EME). After dusk it shows UP and an elevation; by day "below". With a tracking
  antenna on the station's coax, T on MOON makes it turn to the Moon: `>` then `*` when locked. **Check the dish points
  at the Moon you can see** (east in the evening, straight up at midnight, west before dawn), not the opposite side.
- [ ] All-band radio on 2m in CW, tracker locked on the Moon, at night: send some CW. About 2.5 s later your own
  echo comes back, faint, under the static, fading in and out over a minute or so. A second station with its own
  tracker on the Moon anywhere in the world hears it too. A J-pole or eggbeater station hears nothing. By day,
  nothing comes back.
- [ ] New CW default: no text box under the radio; the screen's PTT button or Space keys CW. Typing in a radio
  screen's other boxes is not broken by Space. With `cw_keying = CHAT_MORSE` the text box is back, and Space types in it.
  (Existing config files keep their old value: delete the line to get the new default.)
- [ ] While keying (Space, PTT, typed Morse or the keyer) you hear the sidetone and **no static**; the static comes
  back about half a second after you stop. A second player standing by the radio hears the sidetone too.
- [ ] Iambic Keyer (creative tab, after the tracking antenna): looks like the Bencher paddle (black base, round pivot,
  springs, amber finger pieces). Placed beside the all-band radio and used, its panel opens. Hold A for dits and D for
  dahs (rebindable in Controls), or hold the paddles with the mouse: the paddles light and the KEY lamp blinks. IAMBIC
  A/B (squeeze for alternating dits and dahs; B adds one more after releasing mid-element), BUG (automatic dits,
  hand dahs), STRAIGHT. SPEED 6/8/12/24 WPM, TONE off/low/med/high.
- [ ] Craft the keyer with a dye, glass pane, honeycomb, iron/gold/copper/netherite ingot, diamond, emerald, lapis,
  amethyst shard or quartz: the finger pieces change (amber and clear see-through), in hand, in the GUI and placed;
  breaking it keeps the finish. The tooltip names the finish.

## Linear amplifier, coax loss, hitboxes, fire alarm map, SCADA trends and power, LAN programs (added 2026-11-13)

- [ ] Linear Amplifier (creative tab after the automatic tuner; placeholder desk box): on the coax between an HF radio
  and its antenna, and on a power wire. Use it: OPERATE / STANDBY in the action bar. In OPERATE with power, its lamp
  lights; transmitting through it, the meter needle kicks. A station 1.5 times louder on the far end's S-meter, but the
  band's reach the same. Into a bad SWR it smokes and blows (STANDBY look, smoke puffs); a gold ingot mends it. Nearby
  redstone gets RF from twice as far while it amplifies. 2m through it: no gain. Without power: no gain, no lamp.
  CB through it: the FCC program on a direction finder tuned to that channel shows CB AMPLIFIED: ILLEGAL in red, beeps,
  and logs it; the same station in STANDBY shows plain CB.
- [ ] Coax loss: a 2m station with the radio 60 blocks from the antenna reads a little weaker both ways than one with
  the radio beside it (about 1 dB, a fifth); on HF hardly any difference. `coax_loss = 0` in the server config turns it off.
- [ ] You can no longer walk through the ionosonde (its corner posts and mast) or the 5-BTV (its mast), and you are not
  held up by the 5-BTV's radials.
- [ ] FIRE ALARM program, F4: the right pane shows a map round the controller with each box as a square in its status
  colour (green normal, red alarm flashing, yellow trouble/missing, grey out of service), its number beside it, the
  picked box ringed in white. F4 again: back to the log. A box the mesh has not heard: counted under the map.
- [ ] SCADA program, F4: trends. Up/Down picks the unit (0 is the master), Left/Right the face; a graph of its last
  readings (level 0 to 15 up the side, newest on the right). An OFF face says it has no readings.
- [ ] SCADA RTUs and masters now need power: on a power wire they work as before; with `radios_need_power` on and no
  power, an RTU's OUT faces drop and it stops answering (COMM FAIL), and the master stops polling (NO POWER in red at
  the top of the SCADA program). Existing ones pick up the new draw by themselves.
- [ ] Computer Redstone Block (creative tab after the radio computer): touching a computer (or on its data wire). The
  REDSTONE program lists it; F6 names it; Ins "Door Open=4" names level 4; Enter gives out the picked level, P pulses
  it, O turns it off, L sets any level. While off it reads redstone coming in, shown by the level names. Its lamp
  lights while it gives out. Use the block: its name, out and in in the action bar.
- [ ] LAN REDSTONE from another computer (both named in SETUP, joined by data wire or a microwave link): the other
  computer's blocks show only once F3 there lists your player name or your computer's name; then Enter/P/O work from
  here. Only the owner of the block's computer can name it or change who may set it.
- [ ] LAN RELAY: F6 sets your username (kept per computer). F5 on a named computer with background services on makes
  it a relay server; every computer on the LAN then sees its room and can type into it (Enter); who is in the room
  shows on the right. With two servers, F3 steps between their rooms. Every key above is also in the menu bar. Background services off on the server: the room
  is gone for everyone. The history (200 lines) stays with the server computer through a restart; F8 clears it.

## Siren public address, LAN EMAIL, Moxon reflector strung last (added 2026-11-13)

- [ ] Siren public address: a commercial mobile with data wire running to an Another Weather Mod omni or horn siren
  (the head alone or the pole version; the wire may touch the pole, the cabinet or the head) plays what the radio
  hears out of the siren's top: narrow, hard-driven, heard about 190 blocks away, with an echo. An amateur radio on
  the same wire does not. Several sirens on the wire each play it.
- [ ] LAN EMAIL (new built-in program, envelope icon): F6 sets your username (the same one LAN RELAY uses). N writes a
  mail: To (`user@COMPUTER`), Subject, then the body in the editor, Ctrl+S sends. Enter reads, R replies, Del deletes,
  F3 shows mail still waiting to go. Every key is also in the menu bar. Mail to a computer nobody is at goes at once
  if its background services are on; otherwise it waits (the yellow line at the bottom) and goes when that computer
  is back. Unread mail has a `*`.
- [ ] Moxon: string the driven element first, wait for the balun to settle, then string the reflector: within about
  5 seconds it is a Moxon (beam). Breaking the reflector wire turns it back into a plain antenna.

## Airband radio and aircraft antenna, FM broadcast transmitter and receiver (added 2026-11-13)

- [ ] Airband Radio (creative tab, after the CB mobiles): the model faces you; with it on, its two amber windows show
  the USE (121.500) and STBY (122.750) frequencies. The screen: PWR, VOL-/+, the kHz and MHz buttons and the mouse
  wheel (shift for MHz) tune STBY; `<->` swaps; PTT (hold) to talk. Two airband radios on one frequency hear each
  other in AM (no hiss between overs, both voices when two talk), one 25 kHz off does not.
- [ ] Aircraft VHF Antenna: placed on top of a block it stands with the whip raked back; placed against the underside
  of a block it hangs upside down. Right click: "Antenna: Airband Antenna". Coax to an airband radio: low SWR on
  airband; a J-pole on the airband reads about 2.2:1.
- [ ] FM Broadcast Transmitter: placing it puts the top half on; breaking either half takes both down and drops one
  item. Its screen: PWR, << >> (0.2 MHz) and ON AIR (hold). Held, the cabinet's lamps and bar graph light (both halves).
  A desk microphone beside it also puts you on the air.
- [ ] FM Broadcast Receiver: a wood-sided table radio. Its screen shows a dial 88-108 with a red pointer; << >> and the
  mouse wheel tune 0.2 MHz, VOL-/+ the volume. On the transmitter's channel it plays the station; a channel off, not.
- [ ] Look of all four models and the screens (first art from the reference photos; tell me what to change).

## Airband ground plane, airband and keyer screens, tracker flicker (added 2026-11-13)

- [ ] Airband Ground Plane Antenna (after the aircraft antenna): two blocks tall, a galvanised mast, four radials
  drooping from the clamp, a white whip. Right click the foot: "Antenna: Airband Ground Plane". Coax to an airband radio.
- [ ] Airband Radio screen: its faceplate, as on the block, four times as big. Amber USE / STBY windows; click the
  flip-flop key to swap; the big knob on the right tunes STBY (outer ring MHz, inner knob 25 kHz: click a half or
  scroll); top-left knob volume (scroll) and power (click); the knob under it squelch (scroll). Buttons: PWR, MON
  (squelch open, lit green), PTT (hold, lit red), VOL-, VOL+.
- [ ] Iambic keyer screen: the keyer from above, drawn from its model (your rescaled one), finger pieces in the keyer's
  finish, lighting while pressed (A / D or click either half of the front). MODE, SPEED and SIDETONE plates below.
- [ ] Satellite tracking antenna: no flicker on the plinth's ring, the turntable or the dish hub.

## Airband screen redrawn; SAT TRACK Close / Exit (added 2026-11-13)

- [ ] Airband Radio screen, redrawn after the first look (the block texture blown up showed its blank label bars and
  unlit cells): a clean plate, round knurled knobs with pointers (volume, squelch, and the tuner showing the band and
  the kHz), unlit 8s behind the amber readings, legends in place, a tooltip on each control.
- [ ] SAT TRACK: File > Close (Esc) quits the program as before; File > Exit (X) shuts the computer's screen with the
  tracker left up: reopen it (even after 10 minutes) and the tracker is still on screen.

## SAT TRACK views: Details, Image, Location (added 2026-11-13)

- [ ] View menu (or Tab; D, I, L): Details is the old page and the default. The right-hand box title follows the view.
- [ ] Image: a half-block picture of the satellite on starry black, its kind picked at random at launch (polished
  sphere with four whips, faceted drum, spinner with gold cone, small sphere with six rods, box with two solar wings,
  cubesat); the Moon for MOON. Satellites launched before today get a kind from their name. Caption under it.
- [ ] Location: the square world (grass on dirt, the station on top), the satellite going round it; the slice in view
  lighter (blue), the rest dark. The slice is widest when the track passes overhead, narrower as it passes further
  off, gone when out of range. The satellite turns yellow inside the slice exactly when the list says UP. GEO and the
  Moon: the sky above the horizon, the satellite in it or below the world.

## SAT TRACK pictures in full colour; airband hiss (added 2026-11-13, second round)

- [ ] Image view: the satellite as a small 3D model, ray traced, tumbling slowly (a turn in 24 s) on a starfield; the
  Moon still. The first turn is made in the background: for a moment it may show one frame or "reading".
- [ ] Location view: a square globe (sea, continents, ice caps, cloud, a blue glow of air) with the station mast on
  top, the dotted orbit, the lighter slice in view, the satellite glowing yellow while up.
- [ ] Airband radio with the squelch open (or MON): a clear AM hiss, as a CB on a quiet band; a little even with no
  antenna.

## QRP radios: the home-built panel (added 2026-11-13)

After the maintainer's photo of a home-built 40m QRP transceiver (no cigar box branding): a glossy panel in a pine box,
red on the 40m radio, blue on the 20m one, drawn in code (`QrpPanelScreen`). No display: two band edge lamps.
- [ ] Look: pine frame, glossy panel with screws; SUPPLY and PHONES jacks, ANTENNA socket, a clear label plate
  ("QRP", "40m QRP Transceiver"), the CRYSTAL can (7030 KC on 40m, 14060 on 20m).
- [ ] TUNING: the big black knob on its silver 0-10 skirt; the skirt turns so the marker above reads where you are
  in the band. Scroll (anywhere on the panel, too), drag up / down, or click its left (down) or right (up) half.
  Shift or the x10 switch: ten steps.
- [ ] LOW EDGE / HIGH EDGE lamps: lit near each end of the band, blinking past it; dark when off.
- [ ] OFF / RECEIVE / TRANSMIT pointer knob: click a legend or the knob's side, or scroll. OFF switches the radio
  off, RECEIVE on; TRANSMIT holds it keyed on SSB (talk), and on CW leaves keying to the KEY.
- [ ] VOLUME, MIC, SQUELCH, ATTEN (RF gain down) knobs: scroll, or left click up / right click down; tooltips show
  the setting; pointers follow.
- [ ] MODE (SSB / CW), AGC (OFF / SLOW / FAST, a three way bat) and x10 toggles; QUICK: press to go to the stored
  frequency and back, hold to store this one.
- [ ] KEY (the big knob, bottom right): hold to send CW or to talk on SSB; right click for the hand mic. Keyboard CW
  key and chat Morse still work.

## Radios with no antenna hiss faintly (added 2026-11-13)

- [ ] A radio switched on with nothing on its coax: a faint hiss with the squelch open (it used to be silent), the
  S-meter barely off zero. AGC on (slow or fast) makes the hiss a little louder than AGC off.
- [ ] Two bare radios a few blocks apart on one frequency hear each other weakly (the bare connector, as before);
  13 blocks apart they do not.
- [ ] An airband radio with no antenna and the squelch open: a faint hiss (its loud AM hiss needs an antenna).

## Wire climbing into a device meets its middle (added 2026-11-13)

From the 2026-11-13 playtest: a data wire laid beside a desk climbed it at the desk's corner and went into the
commercial radio on it at the radio's corner. A run up a wall into a device now comes out at the top across the middle
of the device's face (a lone wire dead centre, a bundle's strands side by side about it), slanting over from where it
runs on the floor.
- [ ] Data wire along a desk, kept to a wall beside it, climbing into a radio on the desk: it rises slanting to the
  middle of the face and goes into the radio there, not at its corner.
- [ ] A bundle (data, power, coax) climbing into a radio: the strands end side by side about the middle, not on top of
  one another.
- [ ] Wire running straight up an edge to a device directly above it (UP_DEVICE) still turns into its middle.

## FM broadcast receiver redrawn, its screen, battery and dial; transmitter depth (added 2026-11-13)

- [ ] FM Broadcast Transmitter: the header, the silver meter strip and its knobs, the control panel, the exciter (with
  handles), the power box, the door (with its handle) and the breaker now stand out of the cabinet. Both halves, on and
  off air, and the item in the hand and the inventory.
- [ ] FM Broadcast Receiver, after the walnut table radio photo: walnut case with a lip round the front, a woven tan
  grille in chrome on the left, a chrome-framed panel on the right with a tall orange dial and TUNING (big), VOLUME,
  BASS and TREBLE knobs and two small switches. All four facings.
- [ ] Look at it and scroll (no sneak): it tunes 0.2 MHz a notch (it needs to be on). Other radios still want sneak.
- [ ] Right click: its front as the screen. Click the dial to jump there; TUNING, VOLUME, BASS, TREBLE: scroll or click
  a side; POWER and MUTE switches; scrolling elsewhere tunes (shift: 2 MHz). Tooltips on each part. The status line
  shows the station, volume, tone and the battery.
- [ ] BASS and TREBLE are heard: turn BASS up and TREBLE down on a broadcast, and back.
- [ ] Battery: shift + right-click a Small Alkaline Battery in; with no power wire it plays from it and the status line
  counts it down (about 2 game days, a tenth of the weather alert radio's 21). Shift + right-click empty-handed takes
  it out; a flat one comes out dead. A receiver placed before this needs placing again to run from a battery.

## FM table radio: tunes while off, switches, pointer on the block (added 2026-11-13)

- [ ] Tune it while it is off: by its screen (TUNING knob, the dial, scrolling) and by looking at it and scrolling.
- [ ] POWER and MUTE are bat-handle toggles: chrome plate with bevel and screws, a nut, a tapered lever up or down
  with a ball end and a shadow, small ON/OFF marks.
- [ ] The block's dial shows an orange pointer at the tuned frequency (88 at the bottom, 108 at the top), moving as
  you tune; bright while on, dull while off; right on any facing and on a ceiling.

## Coax into a J-pole: no flicker (added 2026-11-13)

Today's data-wire fix (a run up an edge into a device bends in to the middle of its face) also bent coax running up
to an antenna into the middle, inside the J-pole's mast, where it flickered. Antennas (the antenna_blocks tag) are left
out of it again: coax keeps its line up to their own feed point.
- [ ] Coax up the side of a block to a J-pole (and a 5-BTV) on top: no flicker where it meets it.
- [ ] Data wire up a desk into a radio still goes into the middle of the radio's back.

## Storm damage to a station, with AWM (added 2026-11-13)

- [ ] Wire antennas: from a category 3 hurricane's wind a wire now and then snaps off its holder (the holder stays).
- [ ] Mounted antennas (J-pole, BTV, yagi, ground planes, dishes, tracker, airband): stand through category 4; come
  down only rarely in a category 5 or EF4+ tornado. Coax: only in a category 5 or EF5, rarely.

## Antenna switch and power switches on a wall (added 2026-11-13)

Click the side of a block to put one on the wall (the top of a block puts it on the floor as before). On a wall it is
the floor box tipped up against the wall, as a lever: its terminal side at the bottom, where a cable comes up the wall.
- [ ] On/Off on a wall: terminals at the bottom and top; throwing it parts and joins them (wire up to its bottom, wire
  from its top).
- [ ] Left/Right on a wall: input at the bottom, outputs left and right as you face the wall.
- [ ] Antenna switch on a wall: the radio's coax up the wall to its bottom; outputs 1, 2, 3 to the left, top and right;
  the pointer points at the live output on every facing; it does not turn to the radio (run the radio's coax to its
  bottom).
- [ ] Models look right on all four walls (tipped up, not sideways), hit boxes match, wires meet the terminals.
- [ ] Switches already placed in a world are still on the floor and unchanged.

## SAT TRACK: mouse in the list; bundle strand picking (added 2026-11-14)

- [ ] SAT TRACK: click a satellite in the list to pick it (any view); the mouse wheel over the list steps up and down;
  with a long list scrolled down, clicking still picks the row clicked.
- [ ] Wire bundle: shift-right click on a strand takes the strand under the crosshair, on straight runs, corners,
  climbs and strands bent toward a device.

## VHF radios: tuning STEP (added 2026-11-14)

From the playtest: a satellite on 164.4875 could not be tuned (20 kHz steps; 164.480 is 7.5 kHz off, too far to hear).
- [ ] VHF base station: click the knob at the bottom left (tooltip STEP) to step 5, 6.25, 10, 12.5, 15, 20, 25 kHz; the
  second display shows OFS, TONE and STEP on three lines (all readable?). Changing the step leaves the frequency; one
  dial click then lands on the new grid: 164.480 on 12.5 kHz, up one click is 164.4875, shown with four decimals on the
  screen and on the block.
- [ ] Tune to the satellite on 164.4875 and hear it clearly.
- [ ] VHF handheld: ENTER opens OFFSET, ENTER again opens STEP (arrows change it, KHZ), ENTER back. The arrows then tune
  by that step; the LCD shows 164.4875. The step stays with the radio after relogging and in a dock.
- [ ] A repeater and the scanner are unchanged (no STEP; the scanner keeps its 2.5 kHz steps).

## Coax into a wall switch; cable under a block drops to a desk radio (added 2026-11-13)

- [ ] Antenna switch on a wall, coax run up the wall into it from below: the cable keeps to the wall and goes
  straight into the bottom connector, with no stub out under the switch to the middle of the block.
- [ ] Coax on the switch's top (output 2) meets the top connector against the wall.
- [ ] Known: the left and right connectors sit halfway up the wall switch, while a cable beside it lies at the
  bottom of its block, so side cables still meet the switch low. They work; they just look low.
- [ ] Coax for an output laid right beside the run up to the input joins it (same-kind wire links to its neighbour),
  bypassing the switch: run each output out at least one block clear of the input run.
- [ ] A K2 (or any low radio) on a desk with a block over the space beside it: coax and power wire laid there hang
  from that block, then drop down the radio's face to its back instead of running in at the ceiling.

## Cable test world (added 2026-11-14)

- [ ] `./gradlew runClientTests`, a creative superflat world, `/test runall CableMatrixGameTests 1 false 0 16`: every cable case builds in a grid and goes lime (pass). Walk the rows and note any case that looks wrong though it passed (an overshoot, a V, a loop, a cable sticking out of a wall, a cable missing a radio's back): its name is on its structure block.
- [ ] The tight turns (`sbend`, `uturn`, `jog`) and the three-strand bundles with devices in reverse order (`_rev`) look right.
- [ ] Known bug kept as an optional test (`bundlePastADeviceBothStrandsFeed`): a power + data bundle passing a computer, both strands branch into it and one crosses the other. Look at it with `/test run bundlepastadevicebothstrandsfeed`.

## Cables: hop over, round to a radio's back, drop to its connectors (added 2026-11-14)

- [ ] A power + data bundle laid past a computer (on the floor beside the run): the far strand's branch rises a couple of pixels and passes over the near strand into the computer, no cable through another. Same hung under a ceiling (it hops downward).
- [ ] A lone cable laid up to a radio's side (VHF base station turned side-on): it runs on through the gap beside the radio and round behind it to the middle of its back. Laid at the radio's front, it goes along the front gap, down a side and round to the back. No gaps at the bends.
- [ ] Put a block behind that radio: the cable goes back to plugging into the side. (If the block behind is across a chunk-section border from the cable, the cable may keep the old look until something next to it changes.)
- [ ] A cable under a ceiling block beside a desk radio drops down the radio's back and stops about halfway up it (at its connectors), not on the desk. To an antenna or battery it still drops to the floor.

## Cable fixes from the house in Demo World (added 2026-11-14)

- [ ] A cable turning a corner on the floor (or a bundle turning) bends where its two runs meet: no running on to a wall it leaves and slanting back in a V. Look at -36 86 546/547 and the bundles round -33 86 547.
- [ ] A ceiling cable dropping to a battery meter or other desk device goes straight down its face (no diagonal) and plugs in halfway up it, like a radio. To an antenna it still drops to the floor.
- [ ] A cable lying on top of a battery stays on it when cable beside it is broken and laid again (it used to turn over onto the ceiling for good): try -41 87 534.
- [ ] Still to do (CableHouseGameTests lists 56): strands of a bundle that climb a wall to a device and also carry on along the run cross each other (-32 86 547); a run up an inside corner swaps walls block to block by under a pixel.

## SAT TRACK: blocky satellites (added 2026-11-14)

- [ ] (After `./gradlew runData`: the art names changed.) SAT TRACK's Image view: every kind is built of cubes and square beams, lit like Minecraft blocks (top brightest, bottom darkest) with a 16x16 pixel grain on each face, still tumbling smoothly: polished cube with four whips, square drum of solar cells, spinner with a stepped copper cone, small cube with six rods, gold box with two wings, cubesat. The Moon (moonbounce) is a still cube. The Location view's little satellite icon still reads at its size.

## VHF base station: memory channels (added 2026-11-14)

- [ ] The 2M FM TRANSCEIVER panel has two new keys under STEP: MW (memory write) and V/M (VFO/MEM), drawn like the other light keys, labels not touching STEP or 10X/QUICK, tooltips on both.
- [ ] Hold MW: the display shows "MW 01" (top left) and what channel 1 holds (EMPTY, or its frequency with its offset and tone); turn the dial or press STEP up/down to pick a channel 1-50; press MW to store. "M01 SET" flashes. MW stays lit while picking.
- [ ] Store two channels with different offsets/tones/STEPs. Press V/M: it goes to the last channel stored (V/M lit), the top line reads "M03 -600 T100.0 12.5K" style, and the frequency, offset, tone and step are the channel's. The dial and STEP keys go from stored channel to stored channel (skipping empty ones, wrapping).
- [ ] Press V/M again: back on the VFO exactly where it was (frequency, offset, tone, step). Hold V/M on a memory: "TO VFO", the VFO takes that channel.
- [ ] Break and replace the radio / leave and rejoin: the memories and MEM/VFO state stay (they live in the block's NBT; a broken radio drops without them, as before, unless the radio item keeps its settings).
- [ ] Radio Cloner: read a base station, write a VHF handheld: the handheld gets the base's channels 1-10. Write that image to another base station: its channels 1-10 are replaced, 11-50 kept.

## WXSAT: storms, map, menus, monitor, wider swath, geostationary frames (added 2026-11-14)

- [ ] Storms on the mosaic have ragged, irregular outlines (lobes, stretched along their motion), the same shape from line to line and pass to pass; the green/yellow/red rings follow the outline and stay nested.
- [ ] A storm reaching over cold (snowy) ground still shows red/magenta returns there, not the light snow colours.
- [ ] The station is a red ring with a white dot (shows over black too), "Red ring: this station" above the map; off the view, a red arrow at the edge points to it.
- [ ] Mouse wheel over the map zooms in/out about the mouse; left-drag pans; arrows, + / -, F8 and Home still work.
- [ ] File and View menus open over the map (not behind it). File has Close (Esc, back to the PC's menu) and "Exit, WXSAT left up" (X: the screen closes with WXSAT still on it).
- [ ] With an imager passing and nothing coming in, the panel says why: no radio linked, the radio on another frequency (shows both), the satellite off, or "up but not heard" (weak, roofed, radio off).
- [ ] An External CRT Monitor placed beside the PC's screen (the monitor block on top of the computer) shows the mosaic; the panel's bottom line says so.
- [ ] The swath is wider (8,192 blocks, 16 blocks a pixel): a pass covers much more ground; the live strip shows the whole swath at half scale; zooms are 16/32/64/128 blocks a pixel.
- [ ] A geostationary commercial imager: WXSAT shows "Frame N, row r of R", the new frame replacing the old one row by row from the north, "Frame N complete" at the end; the loop (F7) and a CRT monitor in LOOP step through frames.

## FM broadcast station (added 2026-11-14)

- [ ] Switch the FM transmitter on with nothing linked: an FM receiver tuned to it hears a quiet carrier, not static, and the cabinet lamps stay lit without holding MIC.
- [ ] With a tape player on its data wire playing, hold MIC (or a desk mic): the music drops to about 20% while you talk and comes back over about 1.5 s. ON AIR redstone lights a lamp while the mic is live.
- [ ] A desk mic on a data wire to a radio, beside no radio: holding it talks through that radio.
- [ ] With a weather alert receiver on the wire, a new warning goes out after the tone in the station's voice; if the mic is live it waits until you stop.
- [ ] Forecasts (every 10 min by default) and the hourly station ID come between tapes, never cutting one off; on a stream, the stream ducks under them. The voice: forecast (period, outlook, tomorrow, wind km/h, current sky and degrees C), station ID (callsign letters, frequency), relayed alert (tone, then word by word) with natural gaps.
- [ ] Stream program (with server config fm_internet_streams on): a direct MP3, an Ogg stream, an .m3u and a .pls each play with the song title shown. An HLS .m3u8, a 127.0.0.1 or 192.168.x link and an AAC stream each show a clear error. Stop the stream: the tape player takes over after about 5 s, and the stream returns when it is back. Pulling the network cable reconnects without freezing the server. With the setting off a link cannot be set.
- [ ] The station panel's buttons and fields all take effect.
- [ ] Tape player in QUEUE mode: a hopper on top feeds tapes into the queue (QUEUE n/9), each plays in turn with a short break, played tapes drop into a hopper below (OUT n/9); a comparator reads the queue. Right-clicking a tape on a playing QUEUE player queues it. Breaking it drops the playing tape, the queue and the out tray.
- [ ] A tape player touching or data-wired to an FM transmitter no longer keys it (the station pulls it); it still keys other radios.


## WXSAT over the LAN, CRT waiting message, cable up stairs (added 2026-11-15)

- [ ] Two computers with WXSAT on one LAN (touching, data wire or microwave), only one with a radio: after a pass, the other's mosaic has the same pictures, as one pass (not two). Its panel says "LAN: sharing with 1 computer(s)".
- [ ] WXSAT Options > Block LAN sync (or L): the menu item shows [x], the panel says "LAN sync blocked"; that computer neither gets the other's passes nor gives its own. Off again, new passes are shared (old ones are not copied over).
- [ ] An External CRT Monitor on a computer with no pictures yet (or a fresh world) shows WAITING FOR SATELLITE PASS in phosphor green, centred, larger on a wall of monitors and lined up across them; it goes once a picture comes.
- [ ] Cable up a staircase (straight oak/stone stairs, the right way up): lay wire from the floor up each stair and onto a landing. Each step shows a riser and a tread at right angles hugging the stairs, nothing floating over the treads, and Jade shows it as one run. Same with a power + data bundle. Corner stairs and upside-down stairs keep the old look.
