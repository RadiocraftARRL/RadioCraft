package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import com.arrl.radiocraft.common.blocks.WireLook;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A data wire climbing a desk into the radio on it goes in at the middle of the radio's face, not at its corner
 * (2026-11-13 playtest: a wire kept to a wall beside the desk climbed at the desk's corner and met the radio there).
 *
 * <pre>
 *   z:     3       4 (FOOT)    5
 *   x 7  wire    wire
 *   x 8          FOOT        [desk]   y = 2, the radio on the desk at y = 3
 *   x 9          [stone]                (a wall beside the foot, which the wire keeps to)
 * </pre>
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WireIntoDeviceGameTests {

	private static final BlockPos FOOT = new BlockPos(8, 2, 4);
	private static final BlockPos DESK = new BlockPos(8, 2, 5);
	private static final BlockPos RADIO = new BlockPos(8, 3, 5);

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void dataWireClimbsIntoTheMiddleOfTheRadio(GameTestHelper helper) {
		helper.setBlock(DESK, Blocks.OAK_PLANKS);
		helper.setBlock(RADIO, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(FOOT.east(), Blocks.STONE);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.placeWire(helper, FOOT.west(2), RadiocraftBlocks.DATA_WIRE.get());
					GameTestUtil.placeWire(helper, FOOT.west(), RadiocraftBlocks.DATA_WIRE.get());
					GameTestUtil.placeWire(helper, FOOT, RadiocraftBlocks.DATA_WIRE.get());
				})
				.thenWaitUntil(() -> {
					BlockState state = helper.getBlockState(FOOT);
					helper.assertTrue(state.getBlock() instanceof WireBlock, "no wire at the foot");
					WireLook.Look look = ((WireBlock)state.getBlock()).look(helper.getLevel(), helper.absolutePos(FOOT), state);
					helper.assertTrue(look.end() == WireLook.End.CLIMB_DEVICE, "the foot should climb into the radio, ends " + look.end());
					// The top of the climb: the highest point of the wire's centre line, where it goes into the radio.
					Vec3 top = null;
					for(WireGeometry.Segment s : WireGeometry.at(helper.getLevel(), helper.absolutePos(FOOT)))
						for(Vec3 p : new Vec3[] {s.from(), s.to()})
							if(top == null || p.y > top.y)
								top = p;
					helper.assertTrue(top != null, "no wire geometry");
					BlockPos radio = helper.absolutePos(RADIO);
					double across = top.x - (radio.getX() + 0.5D);
					helper.assertTrue(Math.abs(across) <= 1.5D / 16.0D, String.format("the wire meets the radio %.2f px off the middle of its face", across * 16.0D));
					helper.assertTrue(top.y >= radio.getY() - 0.01D, "the climb does not reach the radio: top at y " + top.y);
				})
				.thenSucceed();
	}
}
