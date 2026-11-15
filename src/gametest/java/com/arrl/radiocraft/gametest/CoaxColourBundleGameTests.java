package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.PassthroughBlockEntity;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireBundleBlock;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Two coax colours sharing a bundle and a passthrough stay two networks while things round them are built and broken
 * (2026-11-14, the maintainer: Jade showed two antennas on a VHF base station with only black coax to its eggbeater;
 * grey coax in the same bundle and passthrough went to a VHF dipole, and breaking that antenna took it back to one;
 * after a reload the radio had one).
 * <p>
 * Radio A on black coax at the west end; radio B on grey coax that joins the bundle beside it; the bundle runs east
 * through a passthrough in a stone wall and splits: black to an eggbeater, grey to a J-pole.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CoaxColourBundleGameTests {

	private static final BlockPos RADIO_A = new BlockPos(1, 2, 3), RADIO_B = new BlockPos(3, 2, 1), WALL = new BlockPos(6, 2, 3);
	private static final BlockPos EGGBEATER = new BlockPos(10, 2, 3), JPOLE = new BlockPos(9, 2, 5);

	private static WireBlock black() {
		return RadiocraftBlocks.COLOURED_COAX.get(DyeColor.BLACK).get();
	}

	private static WireBlock grey() {
		return RadiocraftBlocks.COLOURED_COAX.get(DyeColor.LIGHT_GRAY).get();
	}

	private static void lay(GameTestHelper helper, BlockPos pos, WireBlock wire) {
		BlockPos abs = helper.absolutePos(pos);
		if(com.arrl.radiocraft.common.blocks.Strand.at(helper.getLevel(), abs).isEmpty())
			GameTestUtil.placeWire(helper, pos, wire);
		else
			WireBundleBlock.add(helper.getLevel(), abs, wire);
	}

	private static void antenna(GameTestHelper helper, BlockPos at, Block block) {
		helper.setBlock(at, block.defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(at.above(), block.defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static void passthrough(GameTestHelper helper) {
		helper.setBlock(WALL, RadiocraftBlocks.PASSTHROUGH.get().getBlockState(helper.getLevel(), helper.absolutePos(WALL)));
		GameTestUtil.blockEntity(helper, WALL, PassthroughBlockEntity.class).setMimic(Blocks.STONE.defaultBlockState());
	}

	/** The whole station: radios, the runs (black then grey), the passthrough, the antennas. */
	private static void build(GameTestHelper helper, boolean antennasFirst) {
		for(int y = 2; y <= 4; y++)
			for(int z = 2; z <= 4; z++)
				if(!(y == 2 && z == 3))
					helper.setBlock(new BlockPos(WALL.getX(), y, z), Blocks.STONE);
		helper.setBlock(RADIO_A, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(RADIO_B, RadiocraftBlocks.VHF_BASE_STATION.get());
		if(antennasFirst) {
			antenna(helper, EGGBEATER, RadiocraftBlocks.EGGBEATER_ANTENNA.get());
			antenna(helper, JPOLE, RadiocraftBlocks.J_POLE_ANTENNA.get());
		}
		passthrough(helper);
		for(int x = 2; x <= 9; x++)
			if(x != WALL.getX())
				lay(helper, new BlockPos(x, 2, 3), black());
		lay(helper, new BlockPos(3, 2, 2), grey()); // From B down into the bundle.
		for(int x = 3; x <= 9; x++)
			if(x != WALL.getX())
				lay(helper, new BlockPos(x, 2, 3), grey());
		lay(helper, new BlockPos(9, 2, 4), grey()); // Off south to the J-pole.
		if(!antennasFirst) {
			antenna(helper, EGGBEATER, RadiocraftBlocks.EGGBEATER_ANTENNA.get());
			antenna(helper, JPOLE, RadiocraftBlocks.J_POLE_ANTENNA.get());
		}
	}

	private static List<BlockPos> antennas(GameTestHelper helper, BlockPos radio) {
		List<BlockPos> out = new ArrayList<>();
		for(AntennaNetworkObject a : GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getAntennas())
			out.add(a.getPos().subtract(helper.absolutePos(BlockPos.ZERO)));
		return out;
	}

	private static void eachOnItsOwn(GameTestHelper helper, String when) {
		List<BlockPos> a = antennas(helper, RADIO_A), b = antennas(helper, RADIO_B);
		helper.assertTrue(a.size() == 1 && a.contains(EGGBEATER), when + ": radio A (black) sees " + a + ", wanted only the eggbeater " + EGGBEATER.toShortString());
		helper.assertTrue(b.size() == 1 && b.contains(JPOLE), when + ": radio B (grey) sees " + b + ", wanted only the J-pole " + JPOLE.toShortString());
	}

	private static void run(GameTestHelper helper, boolean antennasFirst) {
		build(helper, antennasFirst);
		helper.startSequence()
				.thenIdle(5)
				.thenWaitUntil(() -> eachOnItsOwn(helper, "built"))
				.thenExecute(() -> { // Break the J-pole and put it back.
					helper.setBlock(JPOLE.above(), Blocks.AIR);
					helper.setBlock(JPOLE, Blocks.AIR);
				})
				.thenIdle(5)
				.thenExecute(() -> antenna(helper, JPOLE, RadiocraftBlocks.J_POLE_ANTENNA.get()))
				.thenIdle(5)
				.thenWaitUntil(() -> eachOnItsOwn(helper, "J-pole broken and put back"))
				.thenExecute(() -> { // Take the grey strand out of a bundle beside the passthrough and lay it again.
					WireBundleBlock.remove(helper.getLevel(), helper.absolutePos(WALL.east()), grey());
				})
				.thenIdle(5)
				.thenExecute(() -> lay(helper, WALL.east(), grey()))
				.thenIdle(5)
				.thenWaitUntil(() -> eachOnItsOwn(helper, "grey strand taken out and laid again"))
				.thenExecute(() -> { // Break the passthrough and make it again.
					helper.setBlock(WALL, Blocks.STONE);
				})
				.thenIdle(5)
				.thenExecute(() -> passthrough(helper))
				.thenIdle(5)
				.thenWaitUntil(() -> eachOnItsOwn(helper, "passthrough made again"))
				.thenExecute(() -> { // Break the eggbeater and put it back.
					helper.setBlock(EGGBEATER.above(), Blocks.AIR);
					helper.setBlock(EGGBEATER, Blocks.AIR);
				})
				.thenIdle(5)
				.thenExecute(() -> antenna(helper, EGGBEATER, RadiocraftBlocks.EGGBEATER_ANTENNA.get()))
				.thenIdle(5)
				.thenWaitUntil(() -> eachOnItsOwn(helper, "eggbeater broken and put back"))
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void coaxColoursStayApartAntennasLast(GameTestHelper helper) {
		run(helper, false);
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void coaxColoursStayApartAntennasFirst(GameTestHelper helper) {
		run(helper, true);
	}
}
