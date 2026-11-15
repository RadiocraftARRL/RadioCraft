package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.antennas.FiveBtvAntennaBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Players cannot walk through the ionosonde or the 5-BTV (2026-11-13): both have collision shapes that follow the model. */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class AntennaHitboxGameTests {

	@GameTest(template = GameTestUtil.EMPTY)
	public static void ionosondeAndFiveBtvAreSolid(GameTestHelper helper) {
		BlockPos ionosonde = new BlockPos(2, 2, 2), btv = new BlockPos(6, 2, 2);
		helper.setBlock(ionosonde, RadiocraftBlocks.IONOSONDE.get());
		helper.setBlock(btv, RadiocraftBlocks.FIVE_BTV_ANTENNA.get());
		for(BlockPos at : new BlockPos[] { ionosonde, btv }) {
			BlockPos abs = helper.absolutePos(at);
			BlockState state = helper.getLevel().getBlockState(abs);
			VoxelShape shape = state.getCollisionShape(helper.getLevel(), abs);
			helper.assertFalse(shape.isEmpty(), state.getBlock() + " has no collision shape");
			helper.assertTrue(shape.max(net.minecraft.core.Direction.Axis.Y) >= 0.99D, state.getBlock() + " is not solid all the way up");
		}
		BlockState radials = RadiocraftBlocks.FIVE_BTV_ANTENNA.get().defaultBlockState().setValue(FiveBtvAntennaBlock.SEGMENT, 4);
		helper.assertTrue(radials.getCollisionShape(helper.getLevel(), helper.absolutePos(btv)).bounds().getXsize() < 0.5D, "the 5-BTV's radials hold players up");
		helper.succeed();
	}
}
