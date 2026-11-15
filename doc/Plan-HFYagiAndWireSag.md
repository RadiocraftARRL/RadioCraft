# Plan: HF yagis and sagging antenna wire

Status: plan only (2026-11-13). Todo items: "Figure out how we'll allow building of HF yagis" (maintainer,
2026-10-21) and "Add sag-varying visuals for long unsupported antenna wire runs". Nothing here is built yet.

## Part 1: HF yagis

### What exists to build on

- `AntennaTypes.match` tries each registered `IAntennaType` from the balun; the Moxon (`MoxonAntennaType`) is
  registered first, then the delta loop, then the dipole. Improvised is the fallback.
- The Moxon already does most of what a wire yagi needs: a 1:1 balun with a straight driven element, plus a
  **parasitic** reflector that is not wired to the balun. The matcher finds the reflector by looking for wires where
  it should be (`AntennaWire.getWires(level, pos)`). Its beam is `MoxonAntennaType.pattern`: 1.5 within 45 degrees,
  falling to 0.15 at 90, 0.15 behind, on transmit and receive, line of sight and skip.
- VHF yagi (`YagiAntennaType`, `YagiAntennaBlock`, `YagiAim`, `YagiAntennaRenderer`): a 2-block item, aimed as
  placed in 16 steps of 22.5 degrees (FACING + TURN). `YagiAim.efficiency` is 1.0 forward, 0.1 at 45 degrees off,
  0.1 or less on the sides and behind. `YagiAntennaRenderer` draws the model turned by the leftover 22.5/45 degrees.
- `DeltaLoopAntennaType.heightAboveGround` / `lowSkipFactor` already penalise an antenna built too low.
- `AntennaSwitchBlock` turns to the next output on a redstone pulse. With two fixed beams it is already a "switched
  beam" setup.
- Antenna wire holders (`RadiocraftTags.Blocks.ANTENNA_WIRE_HOLDERS`): pole, connector, the four baluns.
- **Gap found, fixed 2026-11-13:** a parasitic element did not cause the balun to match again (`AntennaWire.updateAntennas`
  only marked the `AntennaBlockEntity` at the wire's own ends), so a Moxon reflector strung last was not seen. Now
  stringing a wire or taking one down (`AntennaWire.strung`, `AntennaWire.remove`) has every antenna within 24 blocks
  check again (`DeltaLoopMoxonGameTests.moxonReflectorStrungLastIsSeen`). The "Re-match hook" below is this.

### Sizes in blocks (1 block = 1 m, wavelengths as in BandUtils: 40m 41, 20m 21, 10m 10.5)

| Band | Driven (balun arms) | Reflector span | Director span | Reflector spacing | Director spacing | 3-el footprint |
|---|---|---|---|---|---|---|
| 10m | 3 + 3 = 6 | 7 (6 to 8) | 5 (4 to 6) | 2 | 2 | 7 x 5 |
| 20m | 5 + 5 = 10 | 11 (10 to 12) | 9 (8 to 10) | 4 | 3 to 4 | 11 x 9 |
| 40m | 10 + 10 = 20 | 21 (20 to 22) | 19 (18 to 20) | 8 | 6 to 8 | 21 x 17 |

The driven element is a dipole, using the dipole's arm rule `round(lambda / 4)`. The reflector is about 5% longer
and the director about 5% shorter, so at 10m the difference is one block and the tolerance has to be a range. Spacing
is about 0.2 lambda for the reflector and 0.15 to 0.2 lambda for each director.

### Option A: wire yagi between supports (recommended)

**Build:** the same 1:1 balun dipole as now, on poles, plus 1 to 4 parasitic wires strung parallel to it at the same
height: one reflector behind it, and up to three directors in front. Each parasitic element is a single wire from
holder to holder, or two wires in a line that meet at a pole or connector in the middle (the boom support). No new
blocks, no art, works on every HF band, and builds the way the Moxon already does.

**Match rules (`HfYagiAntennaType#match`, registered before the Moxon and the dipole):**
1. The balun is `BALUN_ONE_TO_ONE` with exactly 2 wires that run straight across through it, level (the dipole's
   driven-element check, the same as the start of `MoxonAntennaType#match`). The axis is `across`.
2. Make one query, `level.getEntitiesOfClass(AntennaWire.class, box)`, over the boom box: the driven span plus 2 on
   each side, `+-0.3 lambda` along the boom (at most 13 blocks at 40m), at the balun's Y only. Do not make one
   `getWires` call per candidate block.
3. Keep only the wires that are level, at the balun's Y, and parallel to `across`. Group them by their offset `k`
   along the boom. Join wires in the same line that touch at a holder into one element. An element counts when its
   midpoint is within 1 block of the boom line and its span is within 3 blocks of the driven span.
4. Walk outwards from the driven element on each side. A gap of more than `0.3 lambda` ends the beam. The longer
   side's first element is the reflector, so the beam points the other way. With only one element, a longer one is a
   reflector and a shorter one is a director. Directors after that are elements further out on the front side, up to 3.
5. If no parasitic element is found, it is not a yagi: return null so the dipole picks it up.
6. Store `HfYagiAntennaData(elements, drivenSpan, reflectorSpan, directorSpans[], spacings[], facing, height)`,
   with NBT like `MoxonAntennaData`.

**Direction:** the beam is fixed and faces one of 4 directions, along the boom. Diagonal booms are not allowed, as
with the Moxon. To work more directions, build two or more beams and pick one with the antenna switch (redstone).
Real wire beams for 40m and lower are fixed too.

**Pattern:** move Moxon's `pattern` into a pure `BeamPattern.efficiency(facing, dx, dz, forward, behind,
fullAngle)`, used by both. Forward gain is flat out to `fullAngle`, falls linearly to `behind` at
`fullAngle + 45` degrees, and stays at `behind` past that. The same values apply on transmit and receive, line of
sight and skip (the same as the Moxon decision of 2026-10-24).

| Elements | forward | behind | fullAngle | front/back ratio |
|---|---|---|---|---|
| 2 (driven + reflector or director) | 1.4 | 0.25 | 50 | 5.6 |
| Moxon (for comparison) | 1.5 | 0.15 | 45 | 10 |
| 3 | 1.8 | 0.12 | 40 | 15 |
| 4 | 2.1 | 0.10 | 35 | 21 |
| 5 | 2.3 | 0.08 | 30 | 29 |

A 2-element yagi loses to the Moxon on front-to-back, as it does in real life. More elements give more gain and a
narrower beam, which makes aiming worth it.

**SWR:**
- Start from the dipole rule: 1 + 0.5 for every block each arm is off `round(lambda / 4)`.
- +0.5 if the reflector is not longer than the driven element, or if a director is not shorter.
- +0.25 for every block a spacing falls outside `0.1 to 0.25 lambda` (10m: 1 to 3, 20m: 2 to 5, 40m: 4 to 10).
- A gamma or hairpin match is not modelled. The 1:1 balun is the match.

**Height:** below `round(lambda / 4)` blocks above ground (10m 3, 20m 5, 40m 10), skip is multiplied by
`DeltaLoopAntennaType.lowSkipFactor`, SWR goes up by 0.5, and Jade shows a "too low" note. This copies the delta
loop decision of 2026-10-24.

**Recipes:** none new: wire, poles, connectors and a 1:1 balun.

**Code cost:** small, about 400 lines plus tests.
- New: `HfYagiAntennaType`, `HfYagiAntennaData`, `BeamPattern` (pure, in `common/radio/antenna/`).
- Touch: `RadiocraftAntennaTypes` (register before `MOXON`), `MoxonAntennaType` (use `BeamPattern`),
  `AntennaWire`/`AntennaWirePart` (the re-match hook), `AntennaInfoProvider` (Jade), the balun right-click and
  analyzer names, lang datagen, Patchouli entry, wiki.

### Option B: rotatable beam on a rotator (follow-up, needs art)

**Build:** a craftable `hf_beam_20m` / `hf_beam_10m` item (a fixed 3-element design) placed on top of a new
`antenna_rotator` block, which sits on a mast or a tower (`TowerBeamBlock`). Coax goes into the rotator. It is one
block in the world; a `HfBeamRenderer` (a BER, like `YagiAntennaRenderer`) draws the boom and the elements as thin
boxes made in code, so only the rotator and the hub need models (bounty, with placeholder boxes until then). No
entity or block collision along the elements.

**Aiming:** reuse `YagiAim`'s 16 steps exactly:
- Right click steps the bearing by 22.5 degrees.
- A redstone signal strength of 0 to 15 sets bearing step 0 to 15. With no signal it keeps its last bearing.
- It turns at 1 step per second (a real rotator takes about 60 s per turn), with a motor sound.
- The `RigControlApp` computer app can set the bearing later.

**Clearance:** the elements sweep a circle of half the reflector span (20m 5.5, 10m 3.5 blocks) at the beam's Y.
Check it again when the beam turns or a block nearby changes, at most once a second. A solid block in the circle
means the beam is "fouled": efficiency x0.3 and a Jade note.

**Numbers:** the 3-element row above. SWR 1.2 on its band and 10 elsewhere, as the VHF yagi does. 40m is not offered
here (a 21-block rotating beam is silly in Minecraft); build Option A for 40m.

**Recipe ideas** (need recipe review): rotator `I R I / I P I / I O I` (iron, redstone, piston, coax). Beam
`L I L / L B L / L I L` (lightning rods as tubing, iron, a 1:1 balun). The 10m one is smaller: the same pattern with
copper ingots instead of lightning rods.

**Code cost:** medium to large.
- New: `AntennaRotatorBlock` + its BE (bearing, target, turning), `HfBeamBlock` + `HfBeamAntennaType` + data,
  `HfBeamRenderer`, items, recipes, a sound, models (bounty), a clearance check.
- Touch: `RadiocraftBlocks`/`Items`/`BlockEntities`, `ClientSetupEvents`, `RadiocraftAntennaTypes`, Jade,
  Patchouli, `doc/ModelDesignBounty.md`.

### Option C: tube element blocks (not recommended)

This means aluminium tube blocks laid out as the elements, with a boom block, matched as a multiblock like the 5-BTV.
It is rejected for these reasons:
- 20 to 40 placed blocks for one antenna.
- Collision with everything.
- It cannot turn without being broken and placed again.
- It needs new art for each tube piece.

Option A gives the same "I built it" feeling with blocks players already have.

### Recommendation

Build **Option A** now: wire yagis on every HF band, fixed in one direction, with the antenna switch for more than one
direction. It reuses the Moxon's matcher and pattern, needs no art, and fixes the parasitic re-match gap for the Moxon
as well. Keep **Option B** for a later batch, once the maintainer has drawn the rotator and hub. It would be the
"bought" monoband beam on a tower, which hams will recognise.

### Re-match hook (needed by Option A, and probably already by the Moxon)

When an antenna wire is placed or removed, mark every `AntennaBlockEntity` whose block is a 1:1 balun within 24
blocks horizontally and 2 vertically of either end (`markAntennaChanged`). To find them, iterate the block entity
maps of the few chunks in that box; do not scan blocks. This runs only on wire changes, not on ticks.

## Part 2: sagging antenna wire

### How wire is drawn today

- `AntennaWireEntityRenderer.renderAntennaWire` draws one leash-style ribbon (`RenderType.leash()`), 0.025 wide,
  with `length * 3` steps. Its height curve is vanilla's leash curve
  (`y = dy*f*f` going up, `dy - dy*(1-f)^2` going down), so a **level wire has no sag at all**, and a sloped wire
  bows the wrong way for a hanging wire.
- `AntennaWire.shouldRenderAtSqrDistance` is `< 1024`, so a wire stops being drawn 32 blocks away even though it is
  tracked to 10 chunks. `noCulling = true`, so every wire within 32 blocks is drawn each frame.
- `CordRenderer` (the hand mic cord) already sags by a fixed 0.25 at the middle (`4 f (1-f)` parabola), and draws two
  ribbons at right angles so it reads from any side. The antenna wire has only one flat ribbon, which disappears when
  seen edge-on.
- Each wire entity is one span between two holders, so **supports already reset the sag**. A run with a pole or
  connector in the middle is two spans, each with its own sag. No extra logic is needed.

### Proposal

- **Shape:** a parabola in the chord frame, `y(f) = dy*f - 4*s*f*(1-f)`, with x and z linear in `f`. At the sag
  ratios used here (`s/L` at most 0.08), it differs from the true catenary `a*cosh(x/a)` by under 1% of the sag, so
  `cosh` is not worth it.
- **Sag from the horizontal span H:** `s = min(H^2 / 400, 2.5)`. This is the real `s = wH^2 / 8T` at a fixed
  tension. Measured from the straight chord, that gives about:
  - 5 blocks: 0.06
  - 10 blocks: 0.25 (the same as the mic cord)
  - 20 blocks: 1.0
  - 30 blocks: 2.25
  - 32 blocks and longer: 2.5 (the cap)

  Using H rather than the full length means a vertical drop wire stays straight. A steep sloped wire sags a little,
  and its low point can drop below the lower end, as a real slack wire does.
- **Held wire** (the end is a player): keep today's leash curve and 24 steps. Sag only applies to fixed wires.
- **Segments and LOD:** `n = clamp(ceil(L * 1.5), 8, 48)`. When the camera is more than 24 blocks from the midpoint,
  use `max(8, n / 2)`. A 30-block wire goes from 90 steps today to 45 (or 22 at a distance), so this costs less than
  now even with a second ribbon.
- **Second ribbon:** draw an upright ribbon as well as the flat one, as `CordRenderer` does, so the wire does not
  vanish edge-on. That doubles the vertices, and the step cut above pays for it.
- **Ground clamp (optional, cheap):** keep the low point at least 0.3 above the ground at the midpoint
  (`level.getHeight(MOTION_BLOCKING, mid)`). Compute it once per wire and cache it in a client-only transient field
  on `AntennaWire`, recomputed when `CWireEndPosUpdatePacket` arrives or every 100 frames. This stops a long, low
  span from cutting through grass.
- **Light:** keep lerping the end lights. Sampling light at the low point too is not worth it.
- **Render distance:** consider raising `shouldRenderAtSqrDistance` to 64 blocks (`4096`), scaled by
  `Entity.getViewScale()`, now that far wires use fewer steps (open question).
- **Client-only:** server code, the hitbox and the matcher do not change.

### Gameplay effect

Recommend **none**. The matchers already measure straight-line end-to-end distances (the DeltaLoop plan says so), so
sag never changes SWR. Possible later "light" effects, not proposed now: ice storms adding sag, or wind sway from
AnotherWeatherMod's wind (a small sideways `sin` term on the curve, client only).

### Files

- New: `client/render/WireSag.java`, pure maths (`sag(h)`, `steps(length, cameraDist)`, `point(f, dx, dy, dz, s)`),
  with no Minecraft render classes so it can be unit tested.
- `client/entity/AntennaWireEntityRenderer.java`: the new curve, the step count and LOD, the second ribbon, and the
  held-wire branch kept as it is.
- `common/entities/AntennaWire.java`: render distance, and the transient ground-clamp cache (client only).
- `client/render/CordRenderer.java`: optionally call `WireSag.point` with its fixed 0.25, so there is one curve.

## Tests

- Unit (`src/test`):
  - `BeamPatternTest`: forward, behind and the 45-degree ramp for each element count. The Moxon numbers do not
    change.
  - `WireSagTest`: sag is 0 at H = 0, grows with H, caps at 2.5, both end points are exact, and the step count stays
    within 8 to 48.
- GameTests (`HfYagiGameTests`):
  - A 20m 3-element beam facing north matches `hf_yagi` with SWR 1.
  - Removing the director leaves 2 elements; removing both parasitics leaves a dipole.
  - A station in front hears it 15x louder than one behind (1.8 / 0.12).
  - A 10m 3-element beam works.
  - A parasitic element 2 blocks off the boom line is ignored.
  - At height 2, it matches with the low penalty.
  - **The reflector strung last still matches, and the same for the Moxon** (the re-match hook).
  - The antenna switch flips between two beams.
- `doc/NeedsTesting.md`:
  - Wire sag at spans of 5, 10, 20 and 40, sloped and vertical, with the held wire unchanged and the wire not
    vanishing edge-on.
  - Frame time with about 100 wires in view.
  - Building a 20m and a 40m wire yagi by hand (is stringing the parasitics easy?).

## Open questions for the maintainer

1. Option A now and Option B later, or go straight to the rotator beam?
2. Wire yagis fixed in 4 directions only (as the Moxon is), or also diagonal booms?
3. At most 5 elements (3 directors)? Are the gain table numbers right next to the Moxon's 1.5 / 0.15?
4. Should Option B's rotator take a redstone level 0 to 15 as the bearing, as proposed?
5. Is the sag cap of 2.5 blocks right, and should the far-wire render distance go from 32 to 64 blocks?
6. Wind sway from AnotherWeatherMod: wanted, or keep wires still?
