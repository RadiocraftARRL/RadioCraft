package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.blockentities.GroundRodBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.ground.Grounding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * HF grounding (maintainer, 2026-11-08): ground rods bonded by ground wire (green) to a radio, or to a lightning
 * arrestor on its coax; only green wire bonds; power on a ground run is a fault.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class GroundingGameTests {

	/** A column of {@code height} ground rods standing on {@code bottom}, packed round with mud (a fine ground). */
	static void rodInMud(GameTestHelper helper, BlockPos bottom, int height) {
		for(int y = 0; y < height; y++) {
			BlockPos at = bottom.above(y);
			for(Direction direction : Direction.Plane.HORIZONTAL)
				if(helper.getBlockState(at.relative(direction)).isAir())
					helper.setBlock(at.relative(direction), Blocks.MUD);
			helper.setBlock(at, RadiocraftBlocks.GROUND_ROD.get());
		}
	}

	private static void wire(GameTestHelper helper, BlockPos pos, DyeColor colour) {
		GameTestUtil.placeWire(helper, pos, RadiocraftBlocks.COLOURED_WIRE.get(colour).get());
	}

	/** An HF radio on ground wire to a rod in mud is grounded; one on red wire to the same kind of rod is not. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void onlyGroundWireGroundsARadio(GameTestHelper helper) {
		BlockPos green = new BlockPos(2, 2, 2), red = new BlockPos(2, 2, 7);
		helper.setBlock(green, RadiocraftBlocks.HF_RADIO_10M.get());
		helper.setBlock(red, RadiocraftBlocks.HF_RADIO_10M.get());
		wire(helper, green.east(), DyeColor.GREEN);
		wire(helper, red.east(), DyeColor.RED);
		rodInMud(helper, green.east(2).below(), 4);
		rodInMud(helper, red.east(2).below(), 4);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					RadioBlockEntity grounded = GameTestUtil.blockEntity(helper, green, RadioBlockEntity.class);
					RadioBlockEntity floating = GameTestUtil.blockEntity(helper, red, RadioBlockEntity.class);
					grounded.updateGround(helper.getLevel());
					floating.updateGround(helper.getLevel());
					helper.assertTrue(grounded.getGroundQuality() >= 0.3D, "Four rods in mud on ground wire should be a good ground, was " + grounded.getGroundQuality());
					helper.assertTrue(floating.getGroundQuality() == 0.0D, "Red wire does not bond a ground rod, was " + floating.getGroundQuality());
				})
				.thenSucceed();
	}

	/** A lightning arrestor on the radio's coax, with ground wire to a rod, grounds the radio too. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void arrestorGroundsTheRadioOnItsCoax(GameTestHelper helper) {
		BlockPos radio = new BlockPos(2, 2, 2), arrestor = radio.south(2);
		helper.setBlock(radio, RadiocraftBlocks.HF_RADIO_10M.get());
		GameTestUtil.placeWire(helper, radio.south(), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(arrestor, RadiocraftBlocks.LIGHTNING_ARRESTOR.get());
		wire(helper, arrestor.east(), DyeColor.GREEN);
		rodInMud(helper, arrestor.east(2).below(), 4);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(Grounding.arrestorGrounded(helper.getLevel(), helper.absolutePos(arrestor)), "The arrestor is not grounded");
					RadioBlockEntity be = GameTestUtil.blockEntity(helper, radio, RadioBlockEntity.class);
					be.updateGround(helper.getLevel());
					helper.assertTrue(be.getGroundQuality() >= 0.3D, "The radio is not grounded through the arrestor, was " + be.getGroundQuality());
				})
				.thenSucceed();
	}

	/** A battery on a ground run is a ground fault: the rod sparks and the battery drains into the earth. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void powerOnAGroundRunIsAFault(GameTestHelper helper) {
		BlockPos battery = new BlockPos(2, 2, 2), rod = battery.east(2);
		helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
		wire(helper, battery.east(), DyeColor.GREEN);
		rodInMud(helper, rod.below(), 2);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class).getStorage().setEnergy(100_000))
				.thenWaitUntil(() -> {
					helper.assertTrue(GameTestUtil.blockEntity(helper, rod, GroundRodBlockEntity.class).isFaulted(), "No ground fault yet");
					helper.assertTrue(GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class).getStorage().getEnergyStored() < 100_000,
							"The battery on the ground run did not drain");
				})
				.thenSucceed();
	}
}
