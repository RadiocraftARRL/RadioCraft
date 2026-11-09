package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
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

/**
 * The VHF receiver (2026-11-09): it runs from a small cell with no power wire, draining about twice as fast as the
 * weather alert receiver while it listens and faster still while it plays a signal; the cell comes out as it went in,
 * a flat alkaline as a dead one. Its memories select, scan, and let go when the dial is turned. The cell tests switch
 * power draw on for their batch ({@link GameTestDefaults} runs with it off).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class VHFReceiverGameTests {

	private static final String BATCH = "power_vhf_receiver";
	private static final BlockPos RX = new BlockPos(2, 1, 2), BARE = new BlockPos(6, 1, 2);
	/** Ticks each drain is measured over. */
	private static final int SPAN = 200;

	@BeforeBatch(batch = BATCH)
	public static void powerRequired(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(true);
	}

	@AfterBatch(batch = BATCH)
	public static void powerFree(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(false);
	}

	private static ItemStack cell(ItemStack stack, int fraction100) {
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		if(energy != null) {
			energy.extractEnergy(energy.getMaxEnergyStored(), false);
			energy.receiveEnergy(energy.getMaxEnergyStored() * fraction100 / 100, false);
		}
		return stack;
	}

	private static VHFReceiverBlockEntity rx(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, VHFReceiverBlockEntity.class);
	}

	/** With a cell and no wire it switches on; one without either stays dark. Hearing a signal drains the cell faster. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 2 * SPAN + 60, batch = BATCH)
	public static void receiverRunsOnACellAndDrainsFasterWhileHearing(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.setBlock(BARE, RadiocraftBlocks.VHF_RECEIVER.get());
		int[] energy = new int[3];
		short[] frame = new short[960];
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					double ratio = VHFReceiverBlockEntity.idleDrainPerTick() / WeatherAlertReceiverBlockEntity.drainPerTick();
					helper.assertTrue(Math.abs(ratio - 2.0D) < 0.01D, "a cell lasts " + (1.0D / ratio) + " of the weather radio's time, not half");
					rx(helper, RX).swapCell(cell(new ItemStack(RadiocraftItems.SMALL_BATTERY.get()), 100));
					rx(helper, RX).toggle();
					rx(helper, BARE).toggle();
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(rx(helper, RX).isPowered(), "a receiver with a full cell and no wire does not run");
					helper.assertTrue(rx(helper, RX).isOnCell(), "it runs, but not from its cell");
					helper.assertFalse(rx(helper, BARE).isPowered(), "a receiver with no cell and no wire runs");
					energy[0] = rx(helper, RX).getCellEnergy();
				})
				.thenIdle(SPAN)
				.thenExecute(() -> energy[1] = rx(helper, RX).getCellEnergy())
				.thenExecuteFor(SPAN, () -> rx(helper, RX).playBroadcast(helper, frame, 0.8D)) // A station on the air the whole time.
				.thenExecute(() -> {
					energy[2] = rx(helper, RX).getCellEnergy();
					int idle = energy[0] - energy[1], hearing = energy[1] - energy[2];
					helper.assertTrue(idle > 0, "listening drew nothing from the cell");
					helper.assertTrue(hearing >= 2 * idle, "hearing a signal drew " + hearing + " FE, listening " + idle + ": not faster");
					helper.assertTrue(rx(helper, RX).isOnCell(), "it stopped running from the cell");
				})
				.thenSucceed();
	}

	/** A NiCd comes back with its charge; a flat alkaline comes back dead, and the receiver goes dark. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void cellComesOutAsItIsAndAFlatAlkalineDead(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					rx.swapCell(cell(new ItemStack(RadiocraftItems.SMALL_BATTERY.get()), 50));
					int before = rx.getCellEnergy();
					ItemStack out = rx.swapCell(ItemStack.EMPTY);
					helper.assertTrue(out.is(RadiocraftItems.SMALL_BATTERY.get()), "a NiCd came out as " + out);
					IEnergyStorage storage = out.getCapability(Capabilities.EnergyStorage.ITEM);
					helper.assertTrue(storage != null && storage.getEnergyStored() == before, "the NiCd lost or gained charge in the receiver");
					helper.assertFalse(rx.hasCell(), "the cell is still in");
					rx.swapCell(cell(new ItemStack(RadiocraftItems.SMALL_ALKALINE_BATTERY.get()), 0));
					rx.toggle();
				})
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertFalse(rx(helper, RX).isPowered(), "it runs from a flat alkaline");
					ItemStack out = rx(helper, RX).swapCell(ItemStack.EMPTY);
					helper.assertTrue(out.is(RadiocraftItems.DEAD_ALKALINE_BATTERY.get()), "a flat alkaline came out as " + out);
				})
				.thenSucceed();
	}

	/** An empty memory stores the dial and selects it; two selected scan; turning the dial lets them go. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 120)
	public static void memoriesSelectScanAndLetGoOnTheDial(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		float first = 146_520_000.0F, second = 146_940_000.0F, third = 147_000_000.0F;
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					rx.toggle();
					rx.tuneTo(first);
					rx.pressMemory(0);
					helper.assertTrue(rx.getMemory(0) == rx.getFrequency() && rx.isSelected(0), "memory 1 did not store and select the dial");
					rx.tuneTo(second);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(rx.selectedCount() == 0, "turning the dial kept memory 1 selected");
					rx.pressMemory(1);
					rx.pressMemory(0);
					helper.assertTrue(rx.isScanning(), "two memories pressed in do not scan");
				})
				.thenIdle(VHFReceiverBlockEntity.SCAN_HANG_TICKS + 2 * VHFReceiverBlockEntity.SCAN_DWELL_TICKS + 4)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(rx.isScanning(), "the scan stopped by itself");
					rx.tuneTo(third);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(rx.selectedCount() == 0, "turning the dial did not leave the scan");
					helper.assertTrue(rx.getMemory(0) != 0.0F && rx.getMemory(1) != 0.0F, "leaving the scan forgot the memories");
				})
				.thenSucceed();
	}
}
