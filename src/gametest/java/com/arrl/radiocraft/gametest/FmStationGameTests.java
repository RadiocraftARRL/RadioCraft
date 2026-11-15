package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterBlock;
import com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterTopBlock;
import com.arrl.radiocraft.common.blocks.radios.MicrophoneBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The FM broadcast station (2026-11-14, the maintainer's FM station): switched on it is on the air with no PTT held (a
 * steady carrier); its cabinet gives a redstone signal while the mic is live (an ON AIR sign); and a desk mic away from
 * any radio talks through the radio on its data wire. The mixer's rules (the duck, warnings waiting for the mic,
 * forecasts at breaks, the fallback) are unit tested ({@code FmStationTest}).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class FmStationGameTests {

	private static final BlockPos CABINET = new BlockPos(3, 2, 3);

	/** The two-block cabinet, switched on. */
	private static FmBroadcastTransmitterBlockEntity station(GameTestHelper helper) {
		helper.setBlock(CABINET, RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get().defaultBlockState());
		helper.setBlock(CABINET.above(), RadiocraftBlocks.FM_BROADCAST_TRANSMITTER_TOP.get().defaultBlockState()
				.setValue(FmBroadcastTransmitterTopBlock.FACING, helper.getBlockState(CABINET).getValue(FmBroadcastTransmitterBlock.HORIZONTAL_FACING)));
		return GameTestUtil.blockEntity(helper, CABINET, FmBroadcastTransmitterBlockEntity.class);
	}

	private static void switchOn(GameTestHelper helper, FmBroadcastTransmitterBlockEntity station) {
		if(!station.isPowered())
			station.toggle();
		helper.assertTrue(station.isPowered(), "the transmitter did not switch on");
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = "fm_station") // Alone: a station is on the air all the time.
	public static void onTheAirWithoutPtt(GameTestHelper helper) {
		FmBroadcastTransmitterBlockEntity station = station(helper);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> switchOn(helper, station))
				.thenWaitUntil(() -> {
					helper.assertTrue(!station.isPTTDown(), "PTT is down");
					helper.assertTrue(station.onAir(), "switched on but not on the air");
					helper.assertTrue(helper.getBlockState(CABINET).getValue(FmBroadcastTransmitterBlock.ON_AIR), "the cabinet's lamps are dark with the station on");
				})
				.thenExecute(() -> station.toggle())
				.thenWaitUntil(() -> helper.assertTrue(!helper.getBlockState(CABINET).getValue(FmBroadcastTransmitterBlock.ON_AIR), "switched off and still on the air"))
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = "fm_station") // Alone: a station is on the air all the time.
	public static void onAirSignWhileTheMicIsLive(GameTestHelper helper) {
		FmBroadcastTransmitterBlockEntity station = station(helper);
		BlockPos abs = helper.absolutePos(CABINET);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> switchOn(helper, station))
				.thenIdle(5)
				.thenExecute(() -> helper.assertTrue(signal(helper, abs) == 0, "a signal with the mic closed"))
				.thenExecute(() -> station.setPTTDown(true))
				.thenWaitUntil(() -> helper.assertTrue(signal(helper, abs) == 15, "no ON AIR signal with the mic live: " + signal(helper, abs)))
				.thenExecute(() -> station.setPTTDown(false))
				.thenWaitUntil(() -> helper.assertTrue(signal(helper, abs) == 0, "the ON AIR signal stayed after the mic closed"))
				.thenSucceed();
	}

	private static int signal(GameTestHelper helper, BlockPos abs) {
		BlockState state = helper.getLevel().getBlockState(abs);
		return state.getSignal(helper.getLevel(), abs, Direction.NORTH);
	}

	/** A desk mic beside no radio talks through the one on its data wire (any radio, 2026-11-14). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60, batch = "fm_station") // Alone: a station is on the air all the time.
	public static void deskMicOnDataWire(GameTestHelper helper) {
		BlockPos radioAt = new BlockPos(2, 2, 2), micAt = new BlockPos(7, 2, 2);
		helper.setBlock(radioAt, RadiocraftBlocks.VHF_BASE_STATION.get());
		for(int x = 3; x < micAt.getX(); x++)
			GameTestUtil.placeWire(helper, new BlockPos(x, 2, 2), RadiocraftBlocks.DATA_WIRE.get());
		helper.setBlock(micAt, RadiocraftBlocks.MICROPHONE.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					RadioBlockEntity found = MicrophoneBlock.radio(helper.getBlockState(micAt), helper.getLevel(), helper.absolutePos(micAt));
					helper.assertTrue(found != null && found.getBlockPos().equals(helper.absolutePos(radioAt)), "the desk mic on the data wire found " + (found == null ? "no radio" : found.getBlockPos().toShortString()));
				})
				.thenSucceed();
	}
}
