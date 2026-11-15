package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.network.serverbound.SRadioScrollTunePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The FM broadcast receiver's battery, tone knobs and dial (2026-11-13). */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class FmReceiverGameTests {

	private static final String BATCH = "power_fm_receiver";
	private static final BlockPos RX = new BlockPos(2, 1, 2), BARE = new BlockPos(6, 1, 2);
	private static final int SPAN = 200;

	@BeforeBatch(batch = BATCH)
	public static void powerRequired(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(true);
	}

	@AfterBatch(batch = BATCH)
	public static void powerFree(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(false);
	}

	private static ItemStack full(ItemStack stack) {
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		if(energy != null)
			energy.receiveEnergy(energy.getMaxEnergyStored(), false);
		return stack;
	}

	private static FmBroadcastReceiverBlockEntity rx(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, FmBroadcastReceiverBlockEntity.class);
	}

	/** With an alkaline and no wire it plays from the cell, using it ten times as fast as the weather radio; without one it stays dark. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = SPAN + 60, batch = BATCH)
	public static void receiverRunsOnAnAlkalineTenTimesTheWeatherRadio(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		helper.setBlock(BARE, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		int[] energy = new int[2];
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					double ratio = FmBroadcastReceiverBlockEntity.cellDrainPerTick() / WeatherAlertReceiverBlockEntity.drainPerTick();
					helper.assertTrue(Math.abs(ratio - 10.0D) < 0.01D, "it uses a cell " + ratio + " times as fast as the weather radio, not 10");
					rx(helper, RX).swapCell(full(new ItemStack(RadiocraftItems.SMALL_ALKALINE_BATTERY.get())));
					rx(helper, RX).toggle();
					rx(helper, BARE).toggle();
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(rx(helper, RX).isPowered(), "a receiver with a full alkaline and no wire does not run");
					helper.assertTrue(rx(helper, RX).isOnCell(), "it runs, but not from its cell");
					helper.assertFalse(rx(helper, BARE).isPowered(), "a receiver with no cell and no wire runs");
					energy[0] = rx(helper, RX).getCellEnergy();
				})
				.thenIdle(SPAN)
				.thenExecute(() -> {
					energy[1] = rx(helper, RX).getCellEnergy();
					double expected = SPAN * FmBroadcastReceiverBlockEntity.cellDrainPerTick(), used = energy[0] - energy[1];
					helper.assertTrue(used > 0 && Math.abs(used - expected) <= Math.max(2.0D, expected * 0.1D),
							"it used " + used + " FE over " + SPAN + " ticks, expected about " + expected);
					ItemStack out = rx(helper, RX).swapCell(ItemStack.EMPTY);
					helper.assertTrue(out.is(RadiocraftItems.SMALL_ALKALINE_BATTERY.get()), "a half-used alkaline came out as " + out);
				})
				.thenSucceed();
	}

	/** BASS and TREBLE go from -10 to 10 and stay where they are set. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void toneKnobsClamp(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					FmBroadcastReceiverBlockEntity rx = rx(helper, RX);
					rx.setTone(false, 15);
					rx.setTone(true, -3);
					helper.assertTrue(rx.getBass() == FmBroadcastReceiverBlockEntity.TONE_STEPS, "bass went past the end: " + rx.getBass());
					helper.assertTrue(rx.getTreble() == -3, "treble is " + rx.getTreble());
				})
				.thenSucceed();
	}

	/** Its dial takes the scroll wheel: 200 kHz a notch, stopping at the ends of the band. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void dialScrollsInBroadcastChannels(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					FmBroadcastReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(SRadioScrollTunePacket.tunes(rx), "the scroll wheel does not tune it");
					float start = rx.getFrequency();
					rx.updateFrequency(1);
					helper.assertTrue(Math.abs(rx.getFrequency() - start - FmBroadcastRadioBlockEntity.STEP_HZ) < 1.0F, "one notch moved it to " + rx.getFrequency());
					rx.updateFrequency(500);
					helper.assertTrue(Math.abs(rx.getFrequency() - FmBroadcastRadioBlockEntity.HIGH_HZ) < 1.0F, "it went past 107.9: " + rx.getFrequency());
				})
				.thenSucceed();
	}
}
