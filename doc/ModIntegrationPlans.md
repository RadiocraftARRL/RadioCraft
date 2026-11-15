# Mod integration plans

Plans for working with other mods, kept apart from the feature plans (maintainer, 2026-11-13). Optional: every one is
a soft dependency, and RadioCraft works the same without the other mod.

## Map mods: JourneyMap and Xaero's World Map / Minimap

- **Satellite mapping:** a satellite pass marks the area under it explored in JourneyMap or Xaero's, when present
  (from `doc/SatellitePlan.md`). Commanded from SATTRACK on a pass, like the other satellite commands.
- **APRS map terrain:** the TNC's APRS map (and the other computer maps, which use `AprsMapPainter`) could read the
  player's explored map from these mods for terrain beyond render distance, instead of the terrain atlas.
- **Waypoints:** APRS stations, distress positions and fire alarm boxes as waypoints, when the player asks for them.
- Both mods have public APIs; each integration goes in `compat/` behind a `ModList.isLoaded` check.

## Elsewhere in todo.md

- Backpacks: Traveler's Backpack and Sophisticated Backpacks versions of the manpack.
- Serene Seasons: seasonal propagation, solar output and weather.
- Create: crystals from the mechanical press; PonderLib scenes.
- Another Weather Mod: ball lightning.
