package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.PassthroughBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireBundleBlock;
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Two computers on light grey data wire in a bundle (with power wire), through two passthroughs (2026-11-13, the
 * maintainer's layout: the computers did not show on a network).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class DataWirePassthroughGameTests {

	private static final BlockPos A = new BlockPos(1, 2, 2), B = new BlockPos(7, 2, 2);
	private static final BlockPos[] BUNDLES = {new BlockPos(2, 2, 2), new BlockPos(4, 2, 2), new BlockPos(6, 2, 2)};
	private static final BlockPos[] WALLS = {new BlockPos(3, 2, 2), new BlockPos(5, 2, 2)};

	private static WireBlock lightGrey() {
		return RadiocraftBlocks.COLOURED_DATA_WIRE.get(DyeColor.LIGHT_GRAY).get();
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		return GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
	}

	private static void bundle(GameTestHelper helper, BlockPos pos) {
		GameTestUtil.placeWire(helper, pos, lightGrey());
		helper.assertTrue(WireBundleBlock.canAdd(helper.getLevel(), helper.absolutePos(pos), RadiocraftBlocks.WIRE.get()), "No power wire into the bundle");
		WireBundleBlock.add(helper.getLevel(), helper.absolutePos(pos), RadiocraftBlocks.WIRE.get());
	}

	/** Turns a stone block into a passthrough the way the item does. */
	private static void passthrough(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, RadiocraftBlocks.PASSTHROUGH.get().getBlockState(helper.getLevel(), helper.absolutePos(pos)));
		GameTestUtil.blockEntity(helper, pos, PassthroughBlockEntity.class).setMimic(Blocks.STONE.defaultBlockState());
	}

	private static void assertLinked(GameTestHelper helper, RadioComputerBlockEntity a, RadioComputerBlockEntity b) {
		helper.assertTrue(ComputerNetwork.link(a, b) != null, "The computers do not reach each other: A sees " + ComputerNetwork.peers(a).size()
				+ " peers, B " + ComputerNetwork.peers(b).size());
	}

	/** Passthroughs in first, then the bundles laid up to them. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void colouredDataThroughPassthroughs(GameTestHelper helper) {
		for(BlockPos wall : WALLS)
			passthrough(helper, wall);
		RadioComputerBlockEntity a = computer(helper, A), b = computer(helper, B);
		for(BlockPos at : BUNDLES)
			bundle(helper, at);
		helper.startSequence().thenWaitUntil(() -> assertLinked(helper, a, b)).thenSucceed();
	}

	/** The run laid through a stone wall first, then the wall blocks made passthroughs (and the computers asked before). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void passthroughsMadeAfterTheRun(GameTestHelper helper) {
		for(BlockPos wall : WALLS)
			helper.setBlock(wall, Blocks.STONE);
		RadioComputerBlockEntity a = computer(helper, A), b = computer(helper, B);
		for(BlockPos at : BUNDLES)
			bundle(helper, at);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(ComputerNetwork.link(a, b) == null, "Linked through solid stone");
					for(BlockPos wall : WALLS)
						passthrough(helper, wall);
				})
				.thenWaitUntil(() -> assertLinked(helper, a, b))
				.thenSucceed();
	}
}
