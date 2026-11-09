# Station plans (2026-11-09)

Plans for the maintainer's review: linear amplifier, coax loss, iambic keyer, contests. Nothing here is built yet.

## Linear amplifier

A block in the coax between an HF radio and its antenna (an `ICoaxPassThrough`, like the antenna tuner), on power.

- **Range: a little, not a lot.** Strength out is multiplied by `amplifier_power_gain` (default 1.5), capped at 1 as
  now. Range in this mod falls linearly to 0 at the band's line-of-sight and skip edges, so a stronger signal only
  moves the point where it drops under the noise. With a copy threshold around 0.1, the edge moves from 90% to about
  93% of the band's range. Signals get louder (more S-units), but the band's reach does not change. An antenna two
  blocks higher that clears a hill (the terrain diffraction) still gains more: height is might.
- **Costs:**
  - It draws `amplifier_power_use` (about 4× a radio's transmit draw) while keyed, and a trickle in standby.
  - It doubles the radio's RFI on nearby redstone and receivers (`TransmitInterference`).
  - Its blowout chance on a bad SWR is double the radio's. A blown amplifier passes the signal straight through,
    unamplified, until it is mended.
- **Switch:** an OPERATE/STANDBY switch. In STANDBY, or with no power, it passes the signal through, as real
  amplifiers do. It has a lamp, and a plate-current meter that kicks while keyed.
- **Bands:** HF only (80/40/20/10m). On CB it works, but transmitting through it is logged as an illegal amplifier
  for the moderation tools, as the FCC would see it. VHF is not amplified.
- **Model:** a desk box. Placeholder for now; it goes on the model bounty.
- **Tests:**
  - A GameTest checks that strength at a fixed distance goes up and that the band's range edge does not move.
  - A GameTest checks that STANDBY passes the signal through.
  - A GameTest checks the blowout.

## Coax loss

The loss is mild and depends on frequency and length.

- **Path length:** when a coax network is built, a breadth-first walk counts the coax blocks from each radio to each
  antenna. The count is cached on the network and only changes when the network rebuilds, so no per-packet walk is
  needed. Each packet costs one extra multiply.
- **Loss:**
  - The rate is in dB per 100 blocks, by band: about 0.5 dB on 80m, 1 on 20m, 1.5 on 10m and CB, and 4 on 2m.
  - Fifty blocks of coax on 2m therefore loses about 2 dB, or 37% of the power. On HF, the same run barely matters.
  - The loss applies both ways, transmit and receive, so long VHF runs push players toward putting the radio near the
    antenna or using better cable.
- **Hardline:** one new cable, not a family of types. As you suggested, it is three coax as the core with insulation
  on one side and wire on the other.
  - Its loss is a quarter of coax's. It is stiff: a full block rather than a thin wire, with no diagonal or vertical
    bends. It joins coax through an adapter, or by direct contact if that is simpler.
  - The cost of a second type is small. It is a block and a block entity type that the existing coax network code
    accepts. The network only stores its length in hardline blocks next to its coax length, and that is computed at
    rebuild.
- **SWR and loss interact:** the cable's loss makes the SWR read better at the radio than it really is at the antenna,
  as in real life. This could be a later step; it is not needed for the first version.
- **The analyzer and Jade** show the run's length and loss in dB.
- **Tests:**
  - A unit test checks the loss formula.
  - A GameTest checks that a 64-block 2m run is weaker than a 4-block one, and that hardline beats coax.

## Iambic keyer

The reference is the **Heathkit HD-1410** (about 1974), a single box with the keyer electronics and built-in iambic
paddles on the front. It fits the mod's era, and a single box makes a single block. The paddles are a pair of levers
sticking out of the front, like a Bencher BY-1's (black base, chrome levers). The HD-10 before it had a single lever.

- **Block:** placed next to an HF radio, or on the radio computer's data wire if simpler, it keys that radio's CW.
- **Screen:** opening the keyer shows its front panel:
  - a SPEED knob;
  - a MODE switch with IAMBIC A, IAMBIC B, BUG and STRAIGHT;
  - a sidetone level knob;
  - the two paddles, which light as you press them.
- **Keys:** with the screen open, **A** is dit and **D** is dah. Both are rebindable `KeyMapping`s, like the existing
  CW key, which stays the straight key.
  - **IAMBIC A:** squeezing both alternates dits and dahs, and stops when released.
  - **IAMBIC B:** the same, but on release mid-element it adds one more opposite element.
  - **BUG:** like a Vibroplex: A makes automatic dits, D is a hand-timed dah.
  - **STRAIGHT:** A or D works as a straight key.
- **Speed:** CW is sampled once per tick, so a dit is a whole number of ticks. The speeds are 24, 12, 8 and 6 WPM
  (dits of 1 to 4 ticks). That is coarse but honest. Faster or in-between speeds would need sub-tick buffers, a bigger
  change to `CWBuffer`.
- **Implementation:**
  - The keyer is a small client-side state machine that drives the radio's existing `CWSendBuffer`. This is the same
    path the straight key and chat Morse take, so nothing changes on the server.
  - The keyer block is only how a player gets the screen and how the radio is found.
  - The keyer logic is pure and gets a unit test for each mode's element sequences.
- **Model:** a model based on the HD-1410's photos, either drawn by you or a placeholder box on the bounty.
- **Sources:**
  - [Heathkit HD-1410 (Radio Alpha catalog)](https://hub.catalogit.app/radio-alpha/folder/entry/heathkit-hd-1410)
  - [HD-1410 aftermarket manual (RigPix)](https://rigpix.com/cwgear/heathkit_hd1410_aftermarket_manual.pdf)
  - [Heathkit HD-10](https://hub.catalogit.app/radio-alpha/folder/entry/heathkit-hd-10)
  - [Bencher BY-1 (Vibroplex)](https://www.vibroplex.com/contents/en-us/p199.html)

## Contests (`/contest`)

These are op-only (permission level 2) commands for running a timed contact count.

- **`/contest start <name> [minutes]`:** starts counting, with no limit if the duration is left out. It announces in
  chat and puts a boss bar with the time left on everyone with a radio.
- **What counts:** only the verified two-way contacts the advancement tree already detects (both stations heard each
  other within the window). There is no new detection code, just a listener on that event.
  - **Score:** contacts, with a station worked once per band and mode. Points: 1 for line of sight, 2 by skip, and
    one more for CW.
  - **Multipliers:** distinct grid squares worked.
- **`/contest score`:** anyone can see the live leaderboard. **`/contest stop`:** ends it, posts the final table to
  chat, and writes a CSV log to the world folder.
- **Storage:** the state is saved in the world's saved data, so it survives a restart. One contest at a time.
- **Tests:** a GameTest runs a contest, makes two contacts and checks the scores.

## Bands (not planned)

The maintainer worries that more bands means too much choice on all but big servers. If 6m comes, it could be a
setting on the all-band radio only, with sporadic-E openings as its reason to exist. It would get no new radio,
crystal or antenna recipes.
