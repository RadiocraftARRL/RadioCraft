**RadioCraft 1.13.1 is out!** 📻 (1.13.0 + a quick fix) Plus **Another Weather Mod 1.13.0**.

**⚠️ BREAKING CHANGES**
- **Verticals**: the old vertical on a 1:1 balun no longer works. Build one on the new **Vertical Antenna Base** instead: <https://radiocraft.bigtimeradio.us/wiki/antennas.html#vertical>
- **Poles don't hold wire anymore.** Put an **Antenna Wire Isolator** (the old Antenna Connector) or a balun on top of the pole, then restring your wire antennas.
- **Band ranges changed.** Every HF band now has its own job, so your usual band for a path may not work anymore. Servers: old worlds keep the old ranges until you delete the band settings in `radiocraft-server.toml`. Try it here: <https://radiocraft.bigtimeradio.us/wiki/propagation.html#how-it-works>
- **CW** is now keyed by hand by default, and the CW key moved to **Space**.

**Propagation rework**
- 80m: close and regional, no dead zone. 40m: daytime workhorse, goes long at night. 20m: far by day. 10m: a short opening on normal days, far when the sun is lively.
- Higher antennas reach further by line of sight.
- Skip can take 2-3 hops when a band is open (weaker each hop).
- Valleys block long, low skip. Bands shift gradually at sunrise and sunset.
- New server setting `band_range_multiplier` scales every range at once.

**Satellites**
- Buy a satellite contract from a master cartographer: FM repeater, APRS, mailbox or SSTV in orbit.
- SATTRACK, Eggbeater and tracking antennas, a Handheld Yagi, and moonbounce.
- **WXSAT**: weather pictures from space, and CRT video walls. <https://radiocraft.bigtimeradio.us/wiki/repeaters.html#satellites>

**Fire alarms and SCADA**
- Pull stations, radio alarm boxes that relay to each other, a fire station controller that prints slips.
- SCADA: redstone over the radio. <https://radiocraft.bigtimeradio.us/wiki/alarms.html>

**Weather network**
- The weather station is now the Local Weather Station.
- Alert generator works from 1 station up.
- New Weather Observer, Headless APRS TNC (H19 only) and a weather display.
- Hurricane watches and warnings on the weather radio. <https://radiocraft.bigtimeradio.us/wiki/sky.html>

**Also new**
- Commercial licenses for business frequencies (MURS and marine are license-free), and an FCC Monitor program.
- Airband radio and antennas.
- FM broadcast transmitter and receiver, with tapes, alerts, forecasts and internet streams.
- Iambic keyer, linear amplifier, and light coax loss.
- LAN programs on the computer: chat, email, and redstone control.
- VHF memories and tuning steps.
- Band Plan sheet on the wall.
- Computer menu bars, and Shift+Esc to walk away with a program still running.

**1.13.1**: the FIRE ALARM, SCADA and FCC floppies can be crafted now.

**Another Weather Mod 1.13.0**
- Storms belong to the world now, not to players, and weather fronts form.
- Hurricanes start as tropical storms and change category as they strengthen or weaken.
- Warning sirens.
- Much better frame rate in storms, plus a Rain Detail setting.

**Mods**: there's a new Supported Mods list. Bug reports need to happen with only those mods, plus at most one extra if that mod is the cause: <https://radiocraft.bigtimeradio.us/wiki/supported_mods.html>

**Downloads**
RadioCraft: <https://github.com/RadiocraftARRL/RadioCraft/releases/tag/1.13.1> (also on Modrinth)
Another Weather Mod: <https://github.com/RadiocraftARRL/AnotherWeatherMod/releases/tag/1.13.0>
