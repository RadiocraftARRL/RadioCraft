package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.PowerBENetwork;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.StableChemicalBatteryNetworkObject;
import com.arrl.radiocraft.common.blockentities.StableChemicalBatteryBlockEntity;
import com.arrl.radiocraft.common.blocks.power.LeftRightPowerSwitchBlock;
import com.arrl.radiocraft.common.blocks.power.OnOffPowerSwitchBlock;
import com.arrl.radiocraft.common.blocks.power.PowerSwitchBlock;
import com.arrl.radiocraft.common.datacomponents.ChemicalCharge;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The power switches and the Stable Chemical Battery (maintainer, 2026-11-09): an On/Off switch parts a radio from its
 * battery, a Left/Right switch moves a radio from one battery's circuit to another's, both thrown by hand or by a rising
 * redstone signal; the chemical battery gives nothing until a redstone signal, then powers a radio, and is never charged.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowerSwitchGameTests {

	private static final BlockPos RADIO = new BlockPos(5, 2, 2), SWITCH = new BlockPos(5, 2, 5);

	/** True if the radio at {@code radio} and the battery at {@code battery} share a power network. */
	private static boolean wiredTo(GameTestHelper helper, BlockPos radio, BlockPos battery) {
		BatteryNetworkObject cell = GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class);
		for(BENetwork network : GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getNetworks().values())
			if(network instanceof PowerBENetwork && network.getNetworkObjects().contains(cell))
				return true;
		return false;
	}

	private static void wire(GameTestHelper helper, BlockPos... positions) {
		for(BlockPos pos : positions)
			GameTestUtil.placeWire(helper, pos, RadiocraftBlocks.WIRE.get());
	}

	/** A radio wired through an On/Off switch to a battery loses the battery when a redstone signal switches it off. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 80)
	public static void onOffSwitchPartsTheRadioFromItsBattery(GameTestHelper helper) {
		BlockPos battery = new BlockPos(5, 2, 8), redstone = SWITCH.above();
		helper.setBlock(RADIO, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
		helper.setBlock(SWITCH, RadiocraftBlocks.POWER_SWITCH_ON_OFF.get().defaultBlockState()
				.setValue(PowerSwitchBlock.FACING, Direction.NORTH).setValue(OnOffPowerSwitchBlock.ON, true));
		wire(helper, RADIO.south(), RADIO.south(2), SWITCH.south(), SWITCH.south(2));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(wiredTo(helper, RADIO, battery), "Switched on, the radio is not on its battery's circuit"))
				.thenExecute(() -> helper.setBlock(redstone, Blocks.REDSTONE_BLOCK)) // Rising edge: off.
				.thenExecuteAfter(1, () -> {
					helper.assertFalse(helper.getBlockState(SWITCH).getValue(OnOffPowerSwitchBlock.ON), "A redstone signal did not switch it off");
					helper.assertFalse(wiredTo(helper, RADIO, battery), "Switched off, the radio still has its battery");
					helper.assertFalse(GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).hasBattery(), "Switched off, the radio sees a battery");
					helper.setBlock(redstone, Blocks.AIR); // Falling edge: nothing.
				})
				.thenExecuteAfter(1, () -> {
					helper.assertFalse(helper.getBlockState(SWITCH).getValue(OnOffPowerSwitchBlock.ON), "The signal going off threw the switch");
					helper.setBlock(redstone, Blocks.REDSTONE_BLOCK); // Rising again: on.
				})
				.thenExecuteAfter(1, () -> {
					helper.assertTrue(helper.getBlockState(SWITCH).getValue(OnOffPowerSwitchBlock.ON), "A second signal did not switch it back on");
					helper.assertTrue(wiredTo(helper, RADIO, battery), "Switched back on, the radio is not on its battery's circuit");
				})
				.thenSucceed();
	}

	/** A Left/Right switch moves a radio from the battery on its left output to the one on its right. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 80)
	public static void leftRightSwitchMovesTheLoad(GameTestHelper helper) {
		// Input north, toward the radio; left is east and right is west, as seen from the radio.
		BlockPos left = new BlockPos(8, 2, 5), right = new BlockPos(2, 2, 5);
		helper.setBlock(RADIO, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(left, RadiocraftBlocks.LARGE_BATTERY.get());
		helper.setBlock(right, RadiocraftBlocks.LARGE_BATTERY.get());
		helper.setBlock(SWITCH, RadiocraftBlocks.POWER_SWITCH_LEFT_RIGHT.get().defaultBlockState().setValue(PowerSwitchBlock.FACING, Direction.NORTH));
		wire(helper, RADIO.south(), RADIO.south(2), SWITCH.east(), SWITCH.east(2), SWITCH.west(), SWITCH.west(2));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(wiredTo(helper, RADIO, left), "On the left, the radio is not on the left battery's circuit"))
				.thenExecute(() -> {
					helper.assertTrue(LeftRightPowerSwitchBlock.left(helper.getBlockState(SWITCH)) == Direction.EAST, "Left is not east for a switch facing north");
					helper.assertFalse(wiredTo(helper, RADIO, right), "On the left, the radio is on the right battery's circuit too");
					PowerSwitchBlock.toggle(helper.getLevel(), helper.absolutePos(SWITCH));
					helper.assertTrue(helper.getBlockState(SWITCH).getValue(LeftRightPowerSwitchBlock.RIGHT), "Throwing it did not select the right");
					helper.assertTrue(wiredTo(helper, RADIO, right), "On the right, the radio is not on the right battery's circuit");
					helper.assertFalse(wiredTo(helper, RADIO, left), "On the right, the radio is still on the left battery's circuit");
				})
				.thenSucceed();
	}

	/**
	 * The Stable Chemical Battery is made full and gives a radio nothing until a redstone signal; then it powers it. It
	 * never takes charge, before or after, and its item keeps it sealed and full.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 80)
	public static void chemicalBatteryWaitsForRedstoneAndIsNeverCharged(GameTestHelper helper) {
		BlockPos radio = new BlockPos(3, 2, 5), battery = new BlockPos(3, 2, 8), redstone = battery.east();
		int capacity = CommonConfig.LARGE_BATTERY_CAPACITY.get();
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(battery, RadiocraftBlocks.STABLE_CHEMICAL_BATTERY.get());
		wire(helper, radio.south(), radio.south(2));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(wiredTo(helper, radio, battery), "The radio is not on the chemical battery's circuit"))
				.thenIdle(5)
				.thenExecute(() -> {
					StableChemicalBatteryNetworkObject cell = GameTestUtil.networkObject(helper, battery, StableChemicalBatteryNetworkObject.class);
					helper.assertFalse(cell.isActive(), "It is active before any redstone");
					helper.assertTrue(cell.getStorage().getEnergyStored() == capacity, "Not made full: " + cell.getStorage().getEnergyStored());
					helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getStorage().getEnergyStored() == 0,
							"The sealed battery fed the radio");
					ChemicalCharge carried = GameTestUtil.blockEntity(helper, battery, StableChemicalBatteryBlockEntity.class).collectComponents()
							.get(RadiocraftDataComponent.CHEMICAL_CHARGE.get());
					helper.assertTrue(carried != null && !carried.active() && carried.energy() == capacity, "Its item would not be sealed and full: " + carried);
					cell.getStorage().setEnergy(capacity / 2);
					assertRefusesCharge(helper, radio, cell);
					cell.getStorage().setEnergy(capacity);
					helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getStorage().getEnergyStored() > 0,
						"After a redstone signal the battery still does not feed the radio"))
				.thenExecute(() -> {
					StableChemicalBatteryNetworkObject cell = GameTestUtil.networkObject(helper, battery, StableChemicalBatteryNetworkObject.class);
					helper.assertTrue(cell.isActive() && GameTestUtil.blockEntity(helper, battery, StableChemicalBatteryBlockEntity.class).isActive(), "Not active after a signal");
					helper.assertTrue(cell.getStorage().getEnergyStored() < capacity, "It fed the radio but its charge did not go down");
					helper.setBlock(redstone, Blocks.AIR);
				})
				.thenExecuteAfter(1, () -> {
					StableChemicalBatteryNetworkObject cell = GameTestUtil.networkObject(helper, battery, StableChemicalBatteryNetworkObject.class);
					helper.assertTrue(cell.isActive(), "Taking the signal away sealed it again");
					cell.getStorage().setEnergy(capacity / 2);
					assertRefusesCharge(helper, radio, cell);
				})
				.thenSucceed();
	}

	/** Nothing charges it: not Forge Energy, not the power network (a charge controller's push), not its storage. */
	private static void assertRefusesCharge(GameTestHelper helper, BlockPos radio, StableChemicalBatteryNetworkObject cell) {
		int before = cell.getStorage().getEnergyStored();
		helper.assertTrue(cell.getStorage().receiveEnergy(1000, false) == 0, "Its storage took charge");
		for(BENetwork network : GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getNetworks().values())
			if(network instanceof PowerBENetwork power)
				helper.assertTrue(power.pushPower(1000, false, true, false) == 0, "The network charged it, as a charge controller would");
		helper.assertTrue(helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
				cell.getPos(), Direction.UP) == null, "It offers Forge Energy");
		helper.assertTrue(cell.getStorage().getEnergyStored() == before, "Its charge went up: " + before + " to " + cell.getStorage().getEnergyStored());
	}
}
