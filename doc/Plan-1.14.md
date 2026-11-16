# Plan: 1.14, performance

1.14 is a performance release (maintainer, 2026-11-15): "In general, I'd like for cpu/gpu/memory consumption to be
optimized as possible." No new features unless they fall out of the work. Every step is measured before and after,
and a player's world from 1.12/1.13 must load and look the same afterwards.

Status: **in progress.** Step 2a done 2026-11-16 (the maintainer: "Both, mixin first"); step 2 next.

## Baseline (2026-11-15)

Dev client at the title screen, no FerriteCore, `jcmd <pid> GC.class_histogram` (a full GC first):

| What | Value |
|---|---|
| Live heap | 850 MB |
| BlockStates, all mods | 243,013 |
| RadioCraft wire states | 199,680 (82%): 52 wire blocks x 3,840 states |
| Cost per state | ~1-1.7 KB (mostly each state's neighbour table) |
| Wire states' share | roughly 170-340 MB |

Already done in 1.13 (2026-11-15, from agy's review `build/agy-notes/performance.md`, each claim checked in the code):
CRT pictures repaint their texture in place; a bundle's strands are laid out once a block, not once per strand; the
wire quad/outline caches are capped; voice audio is not copied for antennas out of reach; CRT walls pass group
changes on instead of every block polling twice a second.

## How we measure

Each step records its numbers in this file (a "Measured" line under the step).

- **Memory:** title-screen class histogram as above (`jcmd GC.class_histogram`), plus the same in Demo World after
  a minute standing at the house (-34 87 547).
- **Server tick:** a GameTest-server or `tools/devserver.sh` run with the house fixture loaded and a few transmitters
  keyed; mean and worst tick time from `/rcdebug` (add a `/rcdebug perf` that reports RadioCraft's own per-tick time
  by system: radios, networks, propagation, satellites, CRT, LAN) or the spark profiler as a dev-only runtime mod.
- **Client frame:** spark's client profiler (dev-only) at the house and in front of a CRT wall during a pass; chunk
  rebuild time for wire-heavy chunks (WireModel getModelData + getQuads).
- **Startup:** time from launch to the title screen (`build/client.log`), since every blockstate and its model are
  built then.

## Steps

### 1. Measurement tools (small)
- `/rcdebug perf`: RadioCraft's own server tick time by system (cheap nanoTime around each ticker family), last
  minute's mean and worst.
- spark as a `-Pwithspark` dev runtime mod (like `-PwithCreate`), never shipped.
- A note in doc/Testing.md on taking a heap histogram.

### 2a. Shared neighbour tables for the wire states (done 2026-11-16)
Every block state keeps a table of the states one property away (vanilla `StateHolder#populateNeighbours`: a Guava
`ArrayTable` with two maps of its own and a 2-D array, ~1 KB a state). `mixin/MixinStateHolder` gives `WireBlock`
states a 24-byte `SharedStateNeighbours$Neighbours` instead: the state's index among its block's states, looked up in
one array per block by arithmetic. No gameplay or world change. Off when FerriteCore is installed (it does the same
for every block). `WireStateGameTests` checks every wire state's neighbours and that the shared table is in use.

**Measured** (GameTest server, `jcmd GC.class_histogram` after start, same tests, with and without the mixin): live
heap 610 MB -> 323 MB (-287 MB); ArrayTable 242,662 -> 42,918 (the rest are other blocks); ImmutableMapEntry
2.97 M -> 0.58 M; Object[] 173 MB -> 57 MB. 199,744 wire states now hold 4.8 MB of tables.

### 2. Dyed wire colours as data, not 48 extra blocks (after 2a: the states themselves, ~60-80 MB, and FerriteCore users)
Today every dyed power wire, data wire and coax colour is its own registered `WireBlock` (`COLOURED_WIRE`,
`COLOURED_DATA_WIRE`, `COLOURED_COAX`: 48 blocks), each with all 3,840 states. Keep the four base wire blocks (power,
data, coax, ground) and store a wire's colour as data: wire states 199,680 -> 15,360 (-92%).

Design questions for the maintainer before starting:
- **Where the colour lives.** (a) A small block entity on dyed lone wires (bundles already have one and store their
  strands); (b) keep colour as the "kind" in code (an unregistered `WireKind` object per colour) with the block entity
  only for storage. Most code compares `WireBlock` identity (`joins`, `isSameKind`, `Strand.kind()`, the bundle's
  `List<BlockState> strands`), so (b) keeps that code readable: 11 files use the coloured maps today.
- **Items stay as they are.** The 48 dyed items can stay separate items (items cost no blockstates), so recipes, JEI,
  dye-a-run (`WireBlock.dyeRun`) and creative tabs stay; placing one sets the colour.
- **Old worlds.** The 48 old block ids stay registered as legacy blocks with **no properties** (1 state each, 48 in
  all) that turn into the base wire plus colour: on chunk load (a cheap palette check, `LevelChunkSection.maybeHas`),
  in bundle block entity loading (strands saved by block id), and in structure placement (`RotateBlocksProcessor`, or
  regenerate the 12 templates with `tools/ham_shack_templates.py` and friends). Connections, hug and climb are worked
  out again on conversion (they are what placement would give anyway).
- **Rendering.** Tint from the colour in model data (`WireModel` already reads the level for its layout) instead of
  the block; the quad cache key gains the colour.
- **Tests.** A GameTest that loads a saved 1.13 structure with dyed wires and dyed strands in bundles and checks each
  converts with its colour, network separation and look; the whole cable suite; the house fixture.

### 3. Stored climb (optional, decide after step 2: a further ~10 MB at 15,360 states)
`climb` (none/one/both) triples the states but is game logic (support, links up the wall, keeping a run's line).
After step 2 the saving is small; only worth it if measurement says so.

### 4. Server tick work (from the 2026-11-15 review, not yet done)
- `BundleOrientation.solve`: every trial flip re-reads every node's centre lines (`WireGeometry.crossings`); recompute
  only the flipped component and its neighbours.
- Radios work out their antenna SWR every tick (`RadioBlockEntity`, `AntennaNetworkObject.getSWR`): cache it and
  recompute when frequency, band, tuner, duplexer or the antenna network changes.
- `RedstoneInterference.buildZone` runs a coax BFS and an entity search every 5 s per powered radio: invalidate on
  network changes instead.
- Satellite and moonbounce passes, propagation per packet (`StaticAntenna.transmitAudioPacket` strength per
  antenna), AWM weather lookups per radio: measure with step 1 first, then decide.

### 5. Client rendering
- Wire chunk rebuilds: profile `WireModel` getModelData + getQuads at the house; the layout is per block and per
  strand (bridges, steps), and outlines now use it too (`WireGeometry.outline`).
- CRT monitors: one texture per screen now; check the render types kept per texture name (`CrtMonitorRenderer`
  memoizes by id) stay bounded.
- Block entity renderers that draw every frame when nothing changes (radio screens on the block, meters,
  waterfalls): only redraw on change where possible.

### 6. AnotherWeatherMod
agy's AWM review (`build/agy-notes/awm-performance.md`, 2026-11-15), checked claim by claim: three of its findings
were false and several quotes invented. Done 2026-11-15 (AWM d4ba4c6): slanted rain reads the heightmap once a
column; the storm wobble's terms are worked out once; wind push only syncs entities it moved; puddle tints found
with the spots. Still to do, measure first with spark:
- `StormGenerator.topUp` (every 40 ticks) re-rolls every square, maker and front over the whole forecast horizon,
  and only drops the ones already known at the end: the likeliest real server hotspot.
- `spacing.crowded` runs before the near-a-viewer filter in `scatter`, and fronts roll every cell first.
- StormRain checks the biome of every column every frame (cache per block moved or per tick).
- Fog: eight full-screen blended shells round the camera (`FogShells`): fewer, or one shader pass.
- The tornado siren's 512 states (DIRECTION in the blockstate; a renderer instead) and `setBlock` twice a second
  while it turns (chunk mesh rebuilds).
- Small ones: `StormField.sample` skipping pending storms before the first birth, per-player samples every
  5 ticks, CloudRenderer resampling every tile on one frame.
AWM is released separately, so its fixes go out as an AWM version that 1.14 depends on (optional, as planned).

## Out of scope / separate
- The security review of RadioCraft (`build/agy-notes/security.md`): fixes go into whichever release is next, not
  held for 1.14.
- Porting to 26.1 (todo.md): after 1.14.
