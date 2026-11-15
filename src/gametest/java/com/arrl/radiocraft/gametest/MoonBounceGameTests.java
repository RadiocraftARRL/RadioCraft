package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.SatelliteTrackerNetworkObject;
import com.arrl.radiocraft.common.blockentities.SatelliteTrackerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.satellite.Moon;
import com.arrl.radiocraft.common.radio.satellite.MoonBounce;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * Moonbounce (2026-11-12): an all-band radio on 2m in CW with a tracking antenna told to follow the Moon. At midnight
 * the tracker turns to it and locks on; CW sent through it comes back to the station 2.5 seconds later, not before,
 * very weak. Its own batch: it sets the time to midnight and puts it back after.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class MoonBounceGameTests {

	private static final String BATCH = "moonbounce";
	private static final BlockPos RADIO = new BlockPos(3, 2, 3), TRACKER = RADIO.south(2);
	private static long dayTimeBefore = -1L;

	@BeforeBatch(batch = BATCH)
	public static void midnight(ServerLevel level) {
		dayTimeBefore = level.getDayTime();
		level.setDayTime(18000L); // Midnight: the Moon overhead.
	}

	@AfterBatch(batch = BATCH)
	public static void putTheTimeBack(ServerLevel level) {
		if(dayTimeBefore >= 0L)
			level.setDayTime(dayTimeBefore);
		MoonBounce.clear();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void cwComesBackFromTheMoon(GameTestHelper helper) {
		helper.setBlock(RADIO, RadiocraftBlocks.ALL_BAND_RADIO.get());
		GameTestUtil.placeWire(helper, RADIO.south(), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(TRACKER, RadiocraftBlocks.SATELLITE_TRACKER.get());
		long[] sentAt = { -1L };
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					HFRadioAllBandBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, HFRadioAllBandBlockEntity.class);
					radio.setBandIndex(HFRadioAllBandBlockEntity.BANDS.indexOf("2m"));
					radio.setCWEnabled(true);
					if(!GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).isPowered)
						radio.toggle();
					GameTestUtil.networkObject(helper, TRACKER, SatelliteTrackerNetworkObject.class).setChosen(Moon.NAME);
				})
				.thenWaitUntil(() -> {
					SatelliteTrackerBlockEntity tracker = GameTestUtil.blockEntity(helper, TRACKER, SatelliteTrackerBlockEntity.class);
					helper.assertTrue(tracker.isLocked() && tracker.getTracking().equals(Moon.NAME),
							"not on the Moon: '" + tracker.getTracking() + "' el " + tracker.getElevation());
				})
				.thenExecute(() -> {
					HFRadioAllBandBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, HFRadioAllBandBlockEntity.class);
					helper.assertTrue(radio.getMeterReading() == 0, "a signal before anything was sent");
					boolean[] keyDown = new boolean[CWBuffer.BUFFER_LENGTH];
					Arrays.fill(keyDown, true);
					Object antenna = GameTestUtil.networkObject(helper, TRACKER, SatelliteTrackerNetworkObject.class).getAntenna();
					MoonBounce.onCW(antenna, helper.getLevel(), List.of(new CWBuffer(0, keyDown)), radio.getFrequency());
					sentAt[0] = helper.getLevel().getGameTime();
				})
				.thenIdle(Moon.ECHO_TICKS - 10)
				.thenExecute(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, RADIO, HFRadioAllBandBlockEntity.class).getMeterReading() == 0,
						"the echo came back early"))
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, RADIO, HFRadioAllBandBlockEntity.class).getMeterReading() > 0,
						"no echo " + (helper.getLevel().getGameTime() - sentAt[0]) + " ticks after sending"))
				.thenExecute(() -> {
					long after = helper.getLevel().getGameTime() - sentAt[0];
					helper.assertTrue(after >= Moon.ECHO_TICKS, "the echo took only " + after + " ticks");
					int meter = GameTestUtil.blockEntity(helper, RADIO, HFRadioAllBandBlockEntity.class).getMeterReading();
					helper.assertTrue(meter <= 3, "the echo is not weak: S" + meter);
				})
				.thenSucceed();
	}
}
