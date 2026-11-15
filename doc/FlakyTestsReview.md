# Flaky GameTests Review

Review of the two GameTests that fail intermittently under parallel load during the full suite and pass on rerun.

---

## 1. `VHFReceiverGameTests.scanStepsChannelsAndStopsOnActivity`
* **Test**: `src/gametest/java/com/arrl/radiocraft/gametest/VHFReceiverGameTests.java:144-192`
* **Production code**: `src/main/java/com/arrl/radiocraft/common/blockentities/radio/VHFReceiverBlockEntity.java:356-427`, `src/main/java/com/arrl/radiocraft/common/radio/antenna/ReceiverWhipAntenna.java:35-79`, `src/main/java/com/arrl/radiocraft/common/blockentities/radio/RadioBlockEntity.java:1892-1936`

### Likely Causes (Ranked)

1. **Cross-test RF interference in `"defaultBatch"` via the receiver's whip antenna on default VHF frequencies (High likelihood)**
   * **Evidence**:
     * Unlike the battery tests in the same class (`VHFReceiverGameTests.java:81, 117`, which set `batch = "power_vhf_receiver"`), `scanStepsChannelsAndStopsOnActivity` (`VHFReceiverGameTests.java:144`) has no `batch` attribute and runs in `"defaultBatch"` in parallel with dozens of other GameTests.
     * When `rx.toggle()` switches the receiver on with nothing on its coax (`VHFReceiverGameTests.java:157`), `VHFReceiverBlockEntity.tickWhip` (`VHFReceiverBlockEntity.java:421-427`) registers a `ReceiverWhipAntenna` on the world-global `AntennaNetworkManager.VHF_ID` network (`ReceiverWhipAntenna.java:42`).
     * Unlike a `BareConnectorAntenna` (8-block reach), `ReceiverWhipAntenna` uses `RubberDuckyAntennaType` (`ReceiverWhipAntenna.java:79`), which hears any VHF antenna or handheld in the level out to the 2m band's full line-of-sight range (`losRange = 1,500` blocks).
     * The test scans `first = 146_520_000.0F` (the 2m national simplex calling frequency and default frequency of VHF radios/handhelds) and `second = 156_800_000.0F` (Marine Channel 16, the default frequency of marine radios) (`VHFReceiverGameTests.java:147`).
     * Any concurrent test in `"defaultBatch"` transmitting voice, CW, or data tones on `146.520 MHz` or `156.800 MHz` (or within FM splatter reach) calls `noteCarrier` via `ReceiverWhipAntenna.java:55-70` (`RadioBlockEntity.java:1529-1534`), making `heardSince(dwellStart, now)` return `true` (`VHFReceiverBlockEntity.java:356-361`) and setting `lastHeard = now`.
     * Because `SCAN_HANG_TICKS` is `40` ticks (`VHFReceiverBlockEntity.java:78`) while `scanSpan` is only `6 * 4 = 24` ticks (`VHFReceiverGameTests.java:152`), a single external packet on `146.520 MHz` during the initial scan window locks the receiver on channel 0 for 40 ticks so `seen` never visits `second` (`VHFReceiverGameTests.java:164`: `"the scan did not visit both channels"`). Likewise, an external packet on `held[0]` during the quiet window (`VHFReceiverGameTests.java:175-181`) resets `lastHeard` and trips `"the scan stayed after the signal went quiet"`.

2. **Strict `>` timestamp comparison in `heardSince` combined with `GameTestSequence` vs. block-entity tick order (Medium likelihood)**
   * **Evidence**:
     * `VHFReceiverBlockEntity.heardSince` (`VHFReceiverBlockEntity.java:356-361`) requires `signalAt > since` and `carrierAt > since` (strict inequality, where `since` is `dwellStart`).
     * In `ServerLevel.tick`, block entities (`tickScan`, `VHFReceiverBlockEntity.java:376-397`) tick *before* `GameTestTicker` runs `GameTestSequence` callbacks at the end of the tick.
     * In `VHFReceiverGameTests.java:165-169`, `.thenExecuteFor(4, () -> rx(helper, RX).playBroadcast(helper, frame, 0.8D))` runs for only `4` ticks (`SCAN_DWELL_TICKS`), and `.thenExecute` (line 166) has `expectedDelay == null` in `GameTestSequence`, so it runs synchronously on the **4th tick** right after the 4th `playBroadcast` call—before `tickScan` has ticked for that 4th frame.
     * Consequently, `tickScan` only evaluates 3 of the 4 `playBroadcast` ticks before the assertion at line 168. On the tick where `tickScan` steps channels (`stepTo`, `VHFReceiverBlockEntity.java:363-369`), it sets `dwellStart = now` during `tickBlockEntities()`. When `playBroadcast` then runs later in that *same* tick `now`, `recordSignal` sets `lastSignalGameTime = now` (`RadioBlockEntity.java:1892-1895`). On the next tick, `signalAt > dwellStart` (`now > now`) is `false`, discarding that frame. That leaves only a 2-tick margin for `tickScan` to observe `playBroadcast` before `isScanStopped()` is asserted at line 168.
     * In addition, `dwellStart` is initialized to `0L` in `VHFReceiverBlockEntity` (`VHFReceiverBlockEntity.java:93`) and is not reset when `rx.toggle()` powers the radio on (`VHFReceiverGameTests.java:157`), so on the first tick after power-on in a long-running suite (`now >> 4`), `now - dwellStart >= SCAN_DWELL_TICKS` is immediately true and `tickScan` steps off channel 0 without giving channel 0 a full 4-tick dwell.

### Suggested Change (Prose Only)
* Give `scanStepsChannelsAndStopsOnActivity` (or the non-power scanner tests in `VHFReceiverGameTests`) a dedicated `batch` name, or tune `first` and `second` to unused commercial VHF frequencies (e.g., `151.1375 MHz` and `171.825 MHz`) instead of `146.520 MHz` and `156.800 MHz` so concurrent tests in `"defaultBatch"` cannot trigger the whip's carrier detector.
* In `VHFReceiverGameTests.java:165`, run `playBroadcast` for `VHFReceiverBlockEntity.SCAN_DWELL_TICKS + 2` (6 ticks) instead of `4` so `tickScan` always observes multiple frames after any same-tick `stepTo`.
* In `VHFReceiverBlockEntity`, record a monotonic sequence counter (or reset `lastSignalGameTime` / `carrierGameTime` or use `>= dwellStart` after clearing stale signal timestamps inside `stepTo`) so a signal arriving later in the same game tick that `stepTo` set `dwellStart = now` is not ignored by `signalAt > since`, and initialize `dwellStart = now` when transitioning from unpowered to powered.

---

## 2. `DistressGameTests.programWatchesForAlertsWithNoOneAtIt`
* **Test**: `src/gametest/java/com/arrl/radiocraft/gametest/DistressGameTests.java:462-486`
* **Production code**: `src/main/java/com/arrl/radiocraft/common/computer/apps/DistressHandler.java:418-478`, `src/main/java/com/arrl/radiocraft/common/blockentities/RadioComputerBlockEntity.java:782-811`, `src/main/java/com/arrl/radiocraft/common/blockentities/radio/RadioBlockEntity.java:210-211, 1612-1623`

### Likely Causes (Ranked)

1. **`DistressHandler.state(computer).primed()` becomes `true` before radio `C` is powered on, causing `DistressBeacons.alert` to fire on the same tick as `powerOn(helper, C)` while `C`'s `BareConnectorAntenna` is still `null` (Very high likelihood — exact ~10% race)**
   * **Evidence**:
     * Every other radio test in `DistressGameTests` waits for the radio's bare connector to register before transmitting: `.thenWaitUntil(() -> helper.assertTrue(bare(helper, ...), "No bare connectors yet"))` (`DistressGameTests.java:99, 140, 161, 209, 305, 356, 389`). `programWatchesForAlertsWithNoOneAtIt` (`DistressGameTests.java:466-476`) omits `bare(helper, C)` and only waits for `DistressHandler.state(computer).primed()`.
     * However, `computer` is placed at `K` on tick 0 (`DistressGameTests.java:464`) with `ComputerApps.DISTRESS` already installed (`DistressGameTests.java:270`), while radio `C` is not switched on until after `.thenIdle(2)` (`DistressGameTests.java:466-470`).
     * In `RadioComputerBlockEntity.serverTick` (`RadioComputerBlockEntity.java:782-811`), the computer updates its power when `(gameTime + worldPosition.asLong()) % 10 == 0` and ticks `DistressHandler.tick` when `(gameTime + computer.getBlockPos().asLong()) % 20 == 0` (`DistressHandler.java:419`).
     * `DistressHandler.watch` (`DistressHandler.java:461-467`) sets `state.primed = true` on its very first 20-tick boundary **even when no cabled radio is powered on yet** (`entries` is empty).
     * When `(gameTime + K.asLong()) % 20 == 0` falls on tick 1 or tick 2 during `.thenIdle(2)` (a 2-in-20 = 10% chance depending on the `gameTime` at which the test starts), `state.primed` is **already `true`** before `.thenIdle(2)` finishes.
     * In Minecraft's `GameTestSequence.tick`, events without an expected delay (`.thenExecute` and a `.thenWaitUntil` whose assertion succeeds on the first try) execute synchronously in the same `while` loop on the same tick, *after* `ServerLevel.tickBlockEntities()` has already run for that tick:
       1. At the end of tick 2, `.thenExecute` calls `powerOn(helper, C)` (`DistressGameTests.java:469`). `powerOn` sets `isPowered = true` and `setOn(true)` (`DistressGameTests.java:244-248`), but **does not create `C`'s `BareConnectorAntenna`**—that only happens inside `RadioBlockEntity.tick` -> `tickBareConnector` (`RadioBlockEntity.java:211`).
       2. In the **same tick**, `.thenWaitUntil(... primed())` (`DistressGameTests.java:471`) sees `primed() == true` and succeeds immediately without waiting a tick.
       3. In the **same tick**, `.thenExecute` (`DistressGameTests.java:472-475`) calls `DistressBeacons.alert(...)`. Because `C.tick()` has not run since `powerOn(helper, C)`, `C.getBareConnector()` is still `null` (not registered in `AntennaNetworkManager.VHF_ID`), so the single beacon burst is lost and `unread() == 1` (`DistressGameTests.java:476`) times out after 200 ticks.
     * Even when `primed()` was not set during `thenIdle(2)`, if `computer` precedes `C` in the chunk's block-entity tick list and `(gameTime + K.asLong()) % 20 == 0` lands on tick 3, `computer` sets `primed = true` during `tickBlockEntities()` on tick 3 before `C` has heard anything; if `C` was also not yet primed with existing entries, `primed` does not guarantee `C` has ticked as a powered receiver before `watch` primed its baseline.

2. **Cross-plot receiver desense from parallel tests in `batch = "distress"` (Low likelihood)**
   * **Evidence**:
     * All 9 tests in `DistressGameTests` share `batch = "distress"` (`DistressGameTests.java:53`) and transmit simultaneously on `156.525 MHz` and `161.975 MHz`, recording entries in the global `TransmitterLog.ENTRIES` (`TransmitterLog.java:34`). `DESENSE_RANGE` defaults to `5.0` blocks (`RadiocraftServerConfig.java:590`), which is normally smaller than the spacing between adjacent GameTest plots, so it only matters if `DESENSE_RANGE` is raised or plots are placed within 5 blocks.

### Suggested Change (Prose Only)
* In `DistressGameTests.programWatchesForAlertsWithNoOneAtIt` (`DistressGameTests.java:471`), wait for `bare(helper, C)` alongside `primed()`—e.g., `.thenWaitUntil(() -> helper.assertTrue(bare(helper, C) && DistressHandler.state(computer).primed(), ...))`—matching every other test in `DistressGameTests`, and/or reset `DistressHandler.state(computer)` inside the `.thenExecute` block that powers on `C` so `primed()` can only become `true` on a 20-tick boundary *after* `C` is powered on and its `BareConnectorAntenna` is registered.
