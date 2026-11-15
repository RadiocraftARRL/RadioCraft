package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.PassthroughBlockEntity;
import com.arrl.radiocraft.common.blocks.Strand;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cable keeps to a block face (maintainer, 2026-11-11: "Coax should always try and be hugging a block's wall. It should
 * never be floating in space"): along a wall over a gap, tucked into the corner of a floor and a wall, round the
 * outside corner of a pillar, up a column whose wall changes side, up a wall and on under a ceiling, through a
 * passthrough, over a one-wide wall from both sides, and into a device at the middle of its face. Checked on the
 * cable's centre lines ({@link WireGeometry#at}): every point must be within half the cable's width (and a little) of a
 * solid block.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WireHugGameTests {

	/** How far past touching a centre line may be from a solid block, blocks: a cable round an outside corner is w/2 * sqrt 2 off it. */
	private static final double SLACK = 0.04D;

	private static WireBlock coax() {
		return RadiocraftBlocks.COAX_WIRE.get();
	}

	private static void lay(GameTestHelper helper, BlockPos... path) {
		for(BlockPos pos : path)
			GameTestUtil.placeWire(helper, pos, coax());
	}

	private static void fill(GameTestHelper helper, BlockPos from, BlockPos to) {
		for(BlockPos pos : BlockPos.betweenClosed(from, to))
			helper.setBlock(pos.immutable(), Blocks.STONE);
	}

	/** Points of cable in the box that touch nothing solid, with how far off they are. */
	static List<String> floating(GameTestHelper helper, BlockPos from, BlockPos to) {
		List<String> out = new ArrayList<>();
		var level = helper.getLevel();
		for(BlockPos rel : BlockPos.betweenClosed(from, to)) {
			BlockPos pos = helper.absolutePos(rel);
			for(WireGeometry.Segment s : WireGeometry.at(level, pos)) {
				for(int i = 0; i <= 4; i++) {
					Vec3 p = s.from().lerp(s.to(), i / 4.0D);
					double best = Double.MAX_VALUE;
					BlockPos centre = BlockPos.containing(p);
					for(BlockPos near : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
						BlockState state = level.getBlockState(near);
						if(Strand.isCable(state) && !(state.getBlock() instanceof WireBlock wire && wire.conductsAll()))
							continue;
						for(AABB box : state.getCollisionShape(level, near).toAabbs())
							best = Math.min(best, distance(box.move(near), p));
					}
					if(best > s.width() / 2.0D + SLACK)
						out.add(String.format(Locale.ROOT, "%s at (%.2f %.2f %.2f) is %.2f px from anything", rel.toShortString(),
								(p.x - pos.getX()) * 16, (p.y - pos.getY()) * 16, (p.z - pos.getZ()) * 16, best * 16));
				}
			}
		}
		return out;
	}

	private static double distance(AABB box, Vec3 p) {
		double dx = Math.max(Math.max(box.minX - p.x, 0.0D), p.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - p.y, 0.0D), p.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - p.z, 0.0D), p.z - box.maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static void assertHugs(GameTestHelper helper, BlockPos from, BlockPos to) {
		List<String> problems = floating(helper, from, to);
		helper.assertTrue(problems.isEmpty(), problems.size() + " floating: " + String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		List<String> joins = WireGeometry.check(helper.getLevel(), helper.absolutePos(from), helper.absolutePos(to));
		helper.assertTrue(joins.isEmpty(), joins.size() + " problems: " + String.join("; ", joins.subList(0, Math.min(4, joins.size()))));
	}

	private static BlockState state(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	private static WireBlock.Edge hug(GameTestHelper helper, BlockPos pos) {
		return state(helper, pos).getValue(WireBlock.HUG);
	}

	/** Where the lone wire at {@code pos} lies across its block, sixteenths off the middle. */
	private static com.arrl.radiocraft.common.blocks.WireLook.Lateral lie(GameTestHelper helper, BlockPos pos) {
		return Strand.loneOffset(helper.getLevel(), helper.absolutePos(pos), state(helper, pos));
	}

	/** A run along a wall with nothing under it lies against the wall at the bottom of the block. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void runAlongAWallOverAGapHugsTheWall(GameTestHelper helper) {
		fill(helper, new BlockPos(2, 2, 5), new BlockPos(9, 5, 5));
		List<BlockPos> path = new ArrayList<>();
		for(int x = 2; x <= 9; x++)
			path.add(new BlockPos(x, 4, 6));
		lay(helper, path.toArray(BlockPos[]::new));
		helper.runAfterDelay(2, () -> {
			for(BlockPos pos : path) {
				helper.assertTrue(hug(helper, pos) == WireBlock.Edge.NORTH, "The run at " + pos.toShortString() + " keeps to " + hug(helper, pos));
				helper.assertFalse(WireBlock.isCeiling(state(helper, pos)), "Nothing over it: it does not hang");
			}
			assertHugs(helper, new BlockPos(2, 4, 6), new BlockPos(9, 4, 6));
			helper.succeed();
		});
	}

	/** A floor run beside a wall tucks into the corner, and only beside the wall. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void floorRunBesideAWallTucksIn(GameTestHelper helper) {
		fill(helper, new BlockPos(1, 1, 3), new BlockPos(9, 1, 7));
		fill(helper, new BlockPos(3, 2, 4), new BlockPos(6, 2, 4));
		List<BlockPos> path = new ArrayList<>();
		for(int x = 2; x <= 7; x++)
			path.add(new BlockPos(x, 2, 5));
		lay(helper, path.toArray(BlockPos[]::new));
		helper.runAfterDelay(2, () -> {
			for(int x = 3; x <= 6; x++)
				helper.assertTrue(lie(helper, new BlockPos(x, 2, 5)).z() == -7.0D, "Not tucked at x " + x + ": " + lie(helper, new BlockPos(x, 2, 5)));
			helper.assertTrue(lie(helper, new BlockPos(2, 2, 5)).isZero() && lie(helper, new BlockPos(7, 2, 5)).isZero(), "Moved over with no wall beside it");
			assertHugs(helper, new BlockPos(2, 2, 5), new BlockPos(7, 2, 5));
			helper.succeed();
		});
	}

	/**
	 * In a corridor with a wall each side, cable follows the cable it joins; laid from both ends on different walls, the
	 * block where they meet crosses over and the cable already laid stays where it was.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void corridorLaidFromBothEndsCrossesWhereTheyMeet(GameTestHelper helper) {
		fill(helper, new BlockPos(1, 1, 3), new BlockPos(9, 1, 7));
		fill(helper, new BlockPos(1, 2, 4), new BlockPos(9, 2, 4));
		fill(helper, new BlockPos(1, 2, 6), new BlockPos(9, 2, 6));
		BlockPos east = new BlockPos(8, 2, 5);
		lay(helper, east);
		helper.setBlock(east, state(helper, east).setValue(WireBlock.HUG, WireBlock.Edge.SOUTH)); // As if it came in along the south wall.
		lay(helper, new BlockPos(7, 2, 5), new BlockPos(6, 2, 5)); // Follow it on the south wall.
		lay(helper, new BlockPos(2, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5)); // From the other end: the north wall.
		helper.runAfterDelay(1, () -> {
			for(int x = 6; x <= 8; x++)
				helper.assertTrue(hug(helper, new BlockPos(x, 2, 5)) == WireBlock.Edge.SOUTH, "x " + x + " should follow the south wall");
			for(int x = 2; x <= 4; x++)
				helper.assertTrue(hug(helper, new BlockPos(x, 2, 5)) == WireBlock.Edge.NORTH, "x " + x + " should be on the north wall");
			lay(helper, new BlockPos(5, 2, 5)); // Where they meet.
		});
		helper.runAfterDelay(3, () -> {
			for(int x = 6; x <= 8; x++)
				helper.assertTrue(hug(helper, new BlockPos(x, 2, 5)) == WireBlock.Edge.SOUTH, "x " + x + " moved off the south wall");
			for(int x = 2; x <= 4; x++)
				helper.assertTrue(hug(helper, new BlockPos(x, 2, 5)) == WireBlock.Edge.NORTH, "x " + x + " moved off the north wall");
			helper.assertTrue(lie(helper, new BlockPos(3, 2, 5)).z() == -7.0D && lie(helper, new BlockPos(7, 2, 5)).z() == 7.0D, "Not tucked to their walls");
			assertHugs(helper, new BlockPos(2, 2, 5), new BlockPos(8, 2, 5));
			helper.succeed();
		});
	}

	/** Round the outside corner of a pillar with nothing under it: the cable keeps to the pillar's corner. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void runRoundAnOutsideCornerHugsTheCorner(GameTestHelper helper) {
		fill(helper, new BlockPos(5, 2, 5), new BlockPos(5, 6, 5));
		lay(helper, new BlockPos(3, 4, 4), new BlockPos(4, 4, 4), new BlockPos(5, 4, 4), new BlockPos(6, 4, 4),
				new BlockPos(6, 4, 5), new BlockPos(6, 4, 6), new BlockPos(6, 4, 7));
		helper.runAfterDelay(2, () -> {
			com.arrl.radiocraft.common.blocks.WireLook.Lateral corner = lie(helper, new BlockPos(6, 4, 4));
			helper.assertTrue(corner.x() == -7.0D && corner.z() == 7.0D, "The corner piece is at " + corner);
			assertHugs(helper, new BlockPos(5, 4, 4), new BlockPos(6, 4, 5)); // The lead-ins beyond touch nothing: allowed, drawn on the line.
			helper.succeed();
		});
	}

	/** A run up a north wall that ends, with an east wall above it: the run goes over to the east wall, not on up the air. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void runUpChangesWallWhereItsWallEnds(GameTestHelper helper) {
		fill(helper, new BlockPos(3, 1, 3), new BlockPos(7, 1, 7));
		fill(helper, new BlockPos(5, 2, 4), new BlockPos(5, 3, 4)); // North wall, two high.
		fill(helper, new BlockPos(6, 4, 5), new BlockPos(6, 7, 5)); // East wall above it.
		lay(helper, new BlockPos(5, 2, 6));
		for(int y = 2; y <= 7; y++)
			lay(helper, new BlockPos(5, y, 5));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(WireBlock.runEdge(state(helper, new BlockPos(5, 3, 5))) == Direction.NORTH, "Not up the north wall");
			for(int y = 4; y <= 6; y++)
				helper.assertTrue(WireBlock.runEdge(state(helper, new BlockPos(5, y, 5))) == Direction.EAST,
						"At y " + y + " the run is up " + WireBlock.runEdge(state(helper, new BlockPos(5, y, 5))));
			assertHugs(helper, new BlockPos(5, 2, 5), new BlockPos(5, 7, 6));
			helper.succeed();
		});
	}

	/** Up a wall to the ceiling and on under it: the run under the ceiling hangs from it, and the top of the run turns down the wall. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void runUpAWallCarriesOnUnderTheCeiling(GameTestHelper helper) {
		fill(helper, new BlockPos(3, 1, 3), new BlockPos(7, 1, 6));
		fill(helper, new BlockPos(5, 2, 4), new BlockPos(5, 5, 4)); // The wall.
		fill(helper, new BlockPos(4, 5, 5), new BlockPos(6, 5, 9)); // The ceiling, over the gap south of the wall.
		lay(helper, new BlockPos(5, 2, 6));
		for(int y = 2; y <= 4; y++)
			lay(helper, new BlockPos(5, y, 5));
		lay(helper, new BlockPos(5, 4, 6), new BlockPos(5, 4, 7), new BlockPos(5, 4, 8));
		helper.runAfterDelay(3, () -> {
			for(int z = 6; z <= 8; z++)
				helper.assertTrue(WireBlock.isCeiling(state(helper, new BlockPos(5, 4, z))), "The run at z " + z + " does not hang from the ceiling");
			helper.assertTrue(WireBlock.isCeiling(state(helper, new BlockPos(5, 4, 5))), "The top of the run up does not turn onto the ceiling");
			assertHugs(helper, new BlockPos(5, 2, 5), new BlockPos(5, 4, 8));
			helper.succeed();
		});
	}

	/** A run along a wall with no floor that goes through a passthrough set in the wall: it keeps to the wall on both sides. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void runThroughARaisedPassthroughHugsTheWall(GameTestHelper helper) {
		fill(helper, new BlockPos(2, 1, 5), new BlockPos(8, 4, 5));
		BlockPos through = new BlockPos(5, 3, 5);
		helper.setBlock(through, RadiocraftBlocks.PASSTHROUGH.get().getBlockState(helper.getLevel(), helper.absolutePos(through)));
		GameTestUtil.blockEntity(helper, through, PassthroughBlockEntity.class).setMimic(Blocks.STONE.defaultBlockState());
		lay(helper, new BlockPos(3, 3, 4), new BlockPos(4, 3, 4), new BlockPos(5, 3, 4), new BlockPos(5, 3, 6), new BlockPos(6, 3, 6), new BlockPos(7, 3, 6));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(lie(helper, new BlockPos(5, 3, 4)).z() == 7.0D, "North of the passthrough: not against the wall, " + lie(helper, new BlockPos(5, 3, 4)));
			helper.assertTrue(lie(helper, new BlockPos(5, 3, 6)).z() == -7.0D, "South of the passthrough: not against the wall, " + lie(helper, new BlockPos(5, 3, 6)));
			assertHugs(helper, new BlockPos(3, 3, 4), new BlockPos(7, 3, 4));
			assertHugs(helper, new BlockPos(3, 3, 6), new BlockPos(7, 3, 6));
			helper.succeed();
		});
	}

	/** A one-wide wall climbed from both sides to the wire on top: both climbers are linked and drawn. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void oneWideWallClimbedFromBothSides(GameTestHelper helper) {
		fill(helper, new BlockPos(3, 1, 2), new BlockPos(7, 1, 8));
		BlockPos wall = new BlockPos(5, 2, 5), top = wall.above();
		helper.setBlock(wall, Blocks.STONE);
		lay(helper, top, new BlockPos(5, 2, 3), new BlockPos(5, 2, 4), new BlockPos(5, 2, 7), new BlockPos(5, 2, 6));
		helper.runAfterDelay(3, () -> {
			for(BlockPos climber : List.of(new BlockPos(5, 2, 4), new BlockPos(5, 2, 6)))
				helper.assertTrue(state(helper, climber).getValue(WireBlock.CLIMB) != WireBlock.Climb.NONE, climber.toShortString() + " does not climb");
			var links = coax().getLinks(helper.getLevel(), helper.absolutePos(top), state(helper, top)).keySet();
			helper.assertTrue(links.contains(helper.absolutePos(new BlockPos(5, 2, 4))) && links.contains(helper.absolutePos(new BlockPos(5, 2, 6))),
					"The wire on top links to " + links.size() + " climbers");
			var drawn = coax().lookConnections(helper.getLevel(), helper.absolutePos(top), state(helper, top));
			helper.assertTrue(drawn.contains(Direction.NORTH) || drawn.contains(Direction.SOUTH), "No stub to the second climber: " + drawn);
			assertHugs(helper, new BlockPos(5, 2, 3), new BlockPos(5, 3, 7));
			helper.succeed();
		});
	}

	/** A run tucked against a wall meets a device at the middle of its face. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void runAlongAWallMeetsADeviceAtItsCentre(GameTestHelper helper) {
		fill(helper, new BlockPos(1, 1, 3), new BlockPos(8, 1, 7));
		fill(helper, new BlockPos(1, 2, 4), new BlockPos(8, 2, 4));
		BlockPos radio = new BlockPos(6, 2, 5), last = radio.west();
		helper.setBlock(radio, RadiocraftBlocks.HF_RADIO_20M.get());
		lay(helper, new BlockPos(2, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5), last);
		helper.runAfterDelay(2, () -> {
			BlockPos abs = helper.absolutePos(last);
			Vec3 end = null;
			for(WireGeometry.Segment s : WireGeometry.at(helper.getLevel(), abs))
				for(Vec3 p : new Vec3[] { s.from(), s.to() })
					if(Math.abs(p.x - (abs.getX() + 1)) < 1.0E-6D)
						end = p;
			helper.assertTrue(end != null, "No cable reaches the radio's face");
			helper.assertTrue(Math.abs(end.z - (abs.getZ() + 0.5D)) < 1.0E-6D, String.format(Locale.ROOT, "Meets the radio %.2f px off its middle", (end.z - abs.getZ() - 0.5D) * 16));
			helper.assertTrue(lie(helper, new BlockPos(3, 2, 5)).z() == -7.0D, "The run is not against the wall");
			assertHugs(helper, new BlockPos(2, 2, 5), last);
			helper.succeed();
		});
	}

	/** A bundle of three coax along a wall over a gap moves over as one until its outer strand touches the wall. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void bundleAlongAWallTouchesIt(GameTestHelper helper) {
		fill(helper, new BlockPos(2, 2, 5), new BlockPos(8, 5, 5));
		List<WireBlock> colours = List.of(RadiocraftBlocks.COLOURED_COAX.get(net.minecraft.world.item.DyeColor.ORANGE).get(),
				RadiocraftBlocks.COLOURED_COAX.get(net.minecraft.world.item.DyeColor.LIGHT_BLUE).get(), RadiocraftBlocks.COLOURED_COAX.get(net.minecraft.world.item.DyeColor.GREEN).get());
		for(int x = 3; x <= 7; x++) {
			BlockPos pos = new BlockPos(x, 4, 6);
			GameTestUtil.placeWire(helper, pos, colours.getFirst());
			for(WireBlock colour : colours.subList(1, colours.size()))
				com.arrl.radiocraft.common.blocks.WireBundleBlock.add(helper.getLevel(), helper.absolutePos(pos), colour);
		}
		helper.runAfterDelay(3, () -> {
			for(int x = 3; x <= 7; x++) {
				BlockPos abs = helper.absolutePos(new BlockPos(x, 4, 6));
				var strands = Strand.statesAt(helper.getLevel(), abs);
				double nearest = Double.MAX_VALUE;
				for(var lateral : Strand.laterals(helper.getLevel(), abs, strands))
					nearest = Math.min(nearest, lateral.z());
				helper.assertTrue(Math.abs(nearest + 7.0D) < 1.0E-6D, "At x " + x + " the strand nearest the wall is " + (nearest + 8.0D - 1.0D) + " px off it");
			}
			List<String> joins = WireGeometry.check(helper.getLevel(), helper.absolutePos(new BlockPos(3, 4, 6)), helper.absolutePos(new BlockPos(7, 4, 6)));
			helper.assertTrue(joins.isEmpty(), joins.size() + " problems: " + String.join("; ", joins.subList(0, Math.min(4, joins.size()))));
			helper.succeed();
		});
	}
}
