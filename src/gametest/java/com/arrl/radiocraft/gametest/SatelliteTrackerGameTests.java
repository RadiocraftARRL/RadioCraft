package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.SatelliteTrackerNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.SatelliteTrackerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.apps.RadioStation;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatellitePattern;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRadio;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The satellite tracking antenna and stealth mode (2026-11-12). A tracker on a radio's coax turns to the satellite up
 * over it and locks on; locked, it hears it as {@link SatellitePattern#TRACKER}. A commercial satellite in stealth
 * mode hears only a locked tracker's uplink (a plain antenna's is dropped), and SATTRACK shows a stranger only its name
 * and where it is, and refuses stealth from a station with no tracker on it. In the satellites' batch: they cover
 * 10,000 blocks.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class SatelliteTrackerGameTests {

	private static final String BATCH = "satellites";
	private static final UUID OWNER = UUID.nameUUIDFromBytes("gametest-tracker-owner".getBytes());
	private static final BlockPos RADIO = new BlockPos(3, 2, 3), TRACKER = RADIO.south(2);

	/** A commercial satellite pinned a little east of the plot, so the tracker has somewhere to turn to. */
	private static Satellite commercialNearby(GameTestHelper helper, long seed) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = Satellites.of(server).launch(server, false, OWNER, "gametest", new Random(seed));
		helper.assertTrue(satellite != null, "no commercial frequency pair left");
		BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8)).east(600);
		Satellites.of(server).pin(server, satellite, centre.getX(), centre.getZ(), 4000L);
		return satellite;
	}

	private static void station(GameTestHelper helper) {
		helper.setBlock(RADIO, RadiocraftBlocks.VHF_BASE_STATION.get());
		GameTestUtil.placeWire(helper, RADIO.south(), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(TRACKER, RadiocraftBlocks.SATELLITE_TRACKER.get());
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void trackerLocksOnAndHearsAStealthSatellite(GameTestHelper helper) {
		station(helper);
		Satellite satellite = commercialNearby(helper, 9101L);
		SatelliteGameTests.Probe plain = new SatelliteGameTests.Probe(helper, 40);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> { // Tuned to it: the tracker follows the radio's satellite, not the highest one up.
					RadioBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class);
					radio.setFrequency(satellite.downlinkHz);
					if(!GameTestUtil.networkObject(helper, RADIO, com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject.class).isPowered)
						radio.toggle();
				})
				.thenWaitUntil(() -> {
					SatelliteTrackerBlockEntity tracker = GameTestUtil.blockEntity(helper, TRACKER, SatelliteTrackerBlockEntity.class);
					helper.assertTrue(tracker.isLocked() && tracker.getTracking().equals(satellite.name),
							"not locked on " + satellite.name + ": following '" + tracker.getTracking() + "' at az " + tracker.getAzimuth() + " el " + tracker.getElevation());
				})
				.thenExecute(() -> {
					SatelliteTrackerBlockEntity tracker = GameTestUtil.blockEntity(helper, TRACKER, SatelliteTrackerBlockEntity.class);
					BlockPos at = helper.absolutePos(TRACKER);
					double el = satellite.look(at.getX() + 0.5D, at.getZ() + 0.5D, helper.getLevel().getGameTime()).elevation();
					helper.assertTrue(el < 85.0D && Math.abs(tracker.getElevation() - el) < 3.0D, "the dish is at " + tracker.getElevation() + ", the satellite at " + el);
					Object antenna = GameTestUtil.networkObject(helper, TRACKER, SatelliteTrackerNetworkObject.class).getAntenna();
					helper.assertTrue(SatelliteRadio.pattern(antenna, satellite) == SatellitePattern.TRACKER, "a locked tracker is not a TRACKER");
					helper.assertTrue(SatelliteRadio.pattern(plain, satellite) != SatellitePattern.TRACKER, "a plain antenna is a TRACKER");
					RadioBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class);
					helper.assertTrue(RadioStation.trackerLockedOn(helper.getLevel(), List.of(radio), satellite.name), "the station does not count as connected");

					MinecraftServer server = helper.getLevel().getServer();
					helper.assertTrue(Satellites.of(server).setStealth(server, satellite, true), "stealth refused");
					helper.assertTrue(SatelliteRadio.heardThrough(satellite, antenna), "the tracker lost a stealth satellite");
					helper.assertFalse(SatelliteRadio.heardThrough(satellite, plain), "a plain antenna works a stealth satellite");
					SatelliteRadio.onVoice(plain, helper.getLevel(), OWNER, SatelliteGameTests.toneFrame(), satellite.uplinkHz, satellite.tone());
					helper.assertTrue(SatelliteRadio.holding(satellite) == 0, "a stealth satellite heard a plain antenna");
					SatelliteRadio.onVoice(antenna, helper.getLevel(), OWNER, SatelliteGameTests.toneFrame(), satellite.uplinkHz, satellite.tone());
					helper.assertTrue(SatelliteRadio.holding(satellite) > 0, "a stealth satellite did not hear the locked tracker");
					plain.remove();
				})
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void stealthHidesDetailsAndNeedsATracker(GameTestHelper helper) {
		Satellite satellite = commercialNearby(helper, 9202L);
		BlockPos at = new BlockPos(4, 2, 4);
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.SATTRACK);
		BlockPos stand = helper.absolutePos(at.south(2));
		FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OWNER, "gametest"));
		owner.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("tracker-stranger".getBytes()), "TrackStranger"));
		stranger.moveTo(owner.getX(), owner.getY(), owner.getZ());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					CompoundTag on = new CompoundTag();
					on.putString("name", satellite.name);
					on.putString("field", "stealth");
					on.putBoolean("on", true);
					CompoundTag refused = SatelliteGameTests.sattrack(computer, owner, "set", on);
					helper.assertTrue("sat_stealth_no_tracker".equals(refused.getString("error")), "stealth without a tracker: " + refused);
					helper.assertFalse(satellite.stealth(), "stealth on anyway");

					MinecraftServer server = helper.getLevel().getServer();
					Satellites.of(server).setStealth(server, satellite, true);
					CompoundTag seen = listed(computer, stranger, satellite);
					helper.assertTrue(seen != null && seen.getBoolean("hidden") && !seen.contains("down") && !seen.contains("owner") && seen.contains("passing"),
							"a stranger sees more than its name and where it is: " + seen);
					CompoundTag mine = listed(computer, owner, satellite);
					helper.assertTrue(mine != null && !mine.getBoolean("hidden") && mine.getBoolean("stealth") && mine.contains("down"), "the owner sees too little: " + mine);

					CompoundTag off = on.copy();
					off.putBoolean("on", false);
					helper.assertFalse(SatelliteGameTests.sattrack(computer, owner, "set", off).contains("error"), "could not turn stealth off");
					helper.assertFalse(satellite.stealth(), "still in stealth");
				})
				.thenSucceed();
	}

	private static CompoundTag listed(RadioComputerBlockEntity computer, FakePlayer player, Satellite satellite) {
		CompoundTag status = SatelliteGameTests.sattrack(computer, player, "status", new CompoundTag()).getCompound("data");
		for(Tag t : status.getList("satellites", Tag.TAG_COMPOUND))
			if(((CompoundTag)t).getString("name").equals(satellite.name))
				return (CompoundTag)t;
		return null;
	}
}
