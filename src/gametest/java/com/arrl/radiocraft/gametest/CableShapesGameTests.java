package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link CableShapes} itself, on made-up centre lines in one block (2026-11-14): a check that never fails proves
 * nothing, so each bad shape here must be caught and the good one let be.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableShapesGameTests {

	private static final double Y = 1.0D / 32.0D, W = 1.0D / 16.0D;

	private static List<String> problems(double[]... points) {
		WireBlock wire = RadiocraftBlocks.WIRE.get();
		List<WireGeometry.Segment> segments = new ArrayList<>();
		for(int i = 0; i + 1 < points.length; i++)
			segments.add(new WireGeometry.Segment(wire, new Vec3(points[i][0], Y, points[i][1]), new Vec3(points[i + 1][0], Y, points[i + 1][1]), W));
		return CableShapes.shapes(Map.of(BlockPos.ZERO, segments), BlockPos.ZERO, (s, e, outward, pos) -> null);
	}

	private static void expect(GameTestHelper helper, List<String> found, String what, String shape) {
		helper.assertTrue(found.stream().anyMatch(p -> p.contains(what)), shape + ": not caught as '" + what + "', got " + found);
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void cableShapeChecksCatchBadShapes(GameTestHelper helper) {
		List<String> straight = problems(new double[] {0.5D, 0.0D}, new double[] {0.5D, 0.5D}, new double[] {0.5D, 1.0D});
		helper.assertTrue(straight.isEmpty(), "A straight cable was flagged: " + straight);
		List<String> corner = problems(new double[] {0.5D, 0.0D}, new double[] {0.5D, 0.5D}, new double[] {1.0D, 0.5D});
		helper.assertTrue(corner.isEmpty(), "A plain turn was flagged: " + corner);
		List<String> lane = problems(new double[] {0.4D, 0.0D}, new double[] {0.4D, 0.5D}, new double[] {0.6D, 0.5D}, new double[] {0.6D, 1.0D});
		helper.assertTrue(lane.isEmpty(), "A square lane change was flagged: " + lane);
		// A lane change on a slant: right angles only (2026-11-14).
		expect(helper, problems(new double[] {0.4D, 0.0D}, new double[] {0.4D, 0.3D}, new double[] {0.6D, 0.5D}, new double[] {0.6D, 1.0D}), "slants", "slanted lane change");

		// Past the turn and back: the V.
		List<String> v = problems(new double[] {0.5D, 0.0D}, new double[] {0.5D, 0.8D}, new double[] {1.0D, 0.5D});
		expect(helper, v, "doubles back", "overshoot");
		expect(helper, v, "detour", "overshoot");
		// A wiggle side to side on a straight run.
		expect(helper, problems(new double[] {0.5D, 0.0D}, new double[] {0.3D, 0.3D}, new double[] {0.7D, 0.6D}, new double[] {0.5D, 1.0D}), "detour", "wiggle");
		// A staircase: never longer than it must be, but bending all the way.
		expect(helper, problems(new double[] {0.2D, 0.0D}, new double[] {0.2D, 0.2D}, new double[] {0.35D, 0.2D}, new double[] {0.35D, 0.4D}, new double[] {0.5D, 0.4D},
				new double[] {0.5D, 0.6D}, new double[] {0.65D, 0.6D}, new double[] {0.65D, 0.8D}, new double[] {0.8D, 0.8D}, new double[] {0.8D, 1.0D}), "bends", "staircase");
		helper.succeed();
	}

	/**
	 * A power + data bundle running past a computer, which takes both (2026-11-14, found by the cable matrix's U-turn):
	 * each strand branches into it, and the far lane's branch used to cross the near lane's run in the same plane. It
	 * now hops over it (CableRoutes).
	 */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void bundlePastADeviceBothStrandsFeed(GameTestHelper helper) {
		helper.setBlock(new BlockPos(3, 2, 5), RadiocraftBlocks.RADIO_COMPUTER.get());
		for(int z = 2; z <= 8; z++) {
			BlockPos pos = new BlockPos(2, 2, z);
			GameTestUtil.placeWire(helper, pos, RadiocraftBlocks.WIRE.get());
			com.arrl.radiocraft.common.blocks.WireBundleBlock.add(helper.getLevel(), helper.absolutePos(pos), RadiocraftBlocks.DATA_WIRE.get());
		}
		helper.startSequence().thenIdle(3).thenExecute(() -> {
			BlockPos lo = helper.absolutePos(new BlockPos(0, 1, 0)), hi = helper.absolutePos(new BlockPos(6, 4, 10));
			List<String> problems = new ArrayList<>(WireGeometry.check(helper.getLevel(), lo, hi));
			problems.addAll(CableShapes.check(helper.getLevel(), lo, hi, helper.absolutePos(BlockPos.ZERO), null, null));
			helper.assertTrue(problems.isEmpty(), "The strands cross into the computer: " + problems);
		}).thenSucceed();
	}

	/**
	 * A cable coming at a radio's side goes on round to the middle of its back, where the plugs are (2026-11-14, the
	 * maintainer's choice), and into the side when something stands behind the radio.
	 */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void cableGoesRoundToTheRadiosBack(GameTestHelper helper) {
		BlockPos radio = new BlockPos(3, 2, 3);
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.radios.RadioBlock.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST)); // Back to the west.
		helper.setBlock(new BlockPos(3, 2, 8), RadiocraftBlocks.LARGE_BATTERY.get());
		for(int z = 4; z <= 7; z++)
			GameTestUtil.placeWire(helper, new BlockPos(3, 2, z), RadiocraftBlocks.WIRE.get()); // Up to its south side.
		BlockPos absRadio = helper.absolutePos(radio);
		// Some end of the cable lies against the middle of the back (west face, x 0), behind the radio.
		java.util.function.BooleanSupplier atTheBack = () -> {
			for(WireGeometry.Segment s : WireGeometry.at(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 4))))
				for(Vec3 e : new Vec3[] {s.from(), s.to()})
					if(e.x < absRadio.getX() + 0.01D && Math.abs(e.z - (absRadio.getZ() + 0.5D)) < 0.1D)
						return true;
			return false;
		};
		helper.startSequence().thenIdle(3).thenExecute(() -> {
			helper.assertTrue(atTheBack.getAsBoolean(), "The cable does not reach the middle of the radio's back: "
					+ WireGeometry.describe(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 4))));
			helper.setBlock(radio.west(), net.minecraft.world.level.block.Blocks.STONE); // Something behind it now.
		}).thenIdle(3).thenExecute(() -> {
			helper.assertTrue(!atTheBack.getAsBoolean(), "With stone behind the radio the cable still goes round");
		}).thenSucceed();
	}
}
