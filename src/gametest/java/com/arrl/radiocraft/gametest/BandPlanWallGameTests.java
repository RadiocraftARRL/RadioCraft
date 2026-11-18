package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.BandPlanWallBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Band Plan hung on a wall (maintainer, 2026-11-18): the sheet used on the side of a block hangs there facing out,
 * used on a floor it is only read, and it falls off (dropping the sheet) when its wall is broken.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class BandPlanWallGameTests {

	private static InteractionResult useOn(GameTestHelper helper, Player player, BlockPos block, Direction face) {
		ItemStack sheet = player.getItemInHand(InteractionHand.MAIN_HAND);
		BlockPos abs = helper.absolutePos(block);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5D), face, abs, false);
		return sheet.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void hangsOnAWallFacingOut(GameTestHelper helper) {
		BlockPos wall = new BlockPos(4, 2, 4);
		helper.setBlock(wall, Blocks.STONE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RadiocraftItems.BAND_PLAN.get(), 2));
		for(Direction face : Direction.Plane.HORIZONTAL) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RadiocraftItems.BAND_PLAN.get(), 2));
			helper.assertTrue(useOn(helper, player, wall, face).consumesAction(), "the sheet did not hang on the " + face + " side");
			helper.assertBlockProperty(wall.relative(face), BandPlanWallBlock.FACING, face);
			helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "hanging it did not use the sheet");
		}
		// On the top of the block it is not placed (the item reads it instead).
		helper.assertFalse(useOn(helper, player, wall, Direction.UP).consumesAction(), "the sheet was placed on a floor");
		helper.assertBlockNotPresent(RadiocraftBlocks.BAND_PLAN_WALL.get(), wall.above());
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void fallsWhenItsWallGoes(GameTestHelper helper) {
		BlockPos wall = new BlockPos(4, 2, 4), sheet = wall.north();
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(sheet, RadiocraftBlocks.BAND_PLAN_WALL.get().defaultBlockState().setValue(BandPlanWallBlock.FACING, Direction.NORTH));
		helper.getLevel().destroyBlock(helper.absolutePos(wall), false);
		helper.succeedWhen(() -> {
			helper.assertBlockNotPresent(RadiocraftBlocks.BAND_PLAN_WALL.get(), sheet);
			helper.assertItemEntityPresent(RadiocraftItems.BAND_PLAN.get(), sheet, 2.0D);
		});
	}
}
