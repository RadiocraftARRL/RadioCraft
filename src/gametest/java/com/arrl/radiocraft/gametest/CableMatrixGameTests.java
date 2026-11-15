package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.PowerBENetwork;
import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.PassthroughBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blocks.Strand;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireBundleBlock;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Every kind of cable run a player builds, laid as a player lays them, checked the same way each time (2026-11-14, the
 * maintainer: "a test that tests every possible cable run, like around corners and stuff, up things, as well as
 * variations where there's a different type of cable behind it"). One generated test per case, named for it
 * ({@code cablematrix_<family>_<shape>_<kinds>_r<turn>[_<neighbour>]}), batch {@code cablematrix}: run them all with
 * {@code -Pgametests=cablematrix}. To look at them: {@code ./gradlew runClientTests}, a creative superflat world, then
 * {@code /test runall CableMatrixGameTests 1 false 0 16} (doc/Testing.md).
 * <p>
 * Shapes (laid along +Z at x 8, then turned 0-3 quarter turns about x 8, z 8): straight, corners both ways, a T, a
 * cross, up a step, up a two-high wall, over a ridge and down, down off a ledge, hung under a ceiling between two desk
 * radios, through a passthrough, and up a desk into a radio. Kinds: power, data and coax, plain and dyed, alone and in
 * bundles of two and three. Neighbours: another kind beside the run, other kinds on both sides, the same kind in
 * another colour beside it, another kind crossing it.
 * <p>
 * Checked: (1) {@link WireGeometry#check} (each strand meets itself across every face it links through, and no two
 * strands run through each other); (2) no centre line ends on a face with nothing there (a stub into air) or into a
 * cable it does not join; (3) the networks: the radio at one end reaches each device at the other (a battery on
 * power, a J-pole on coax, a computer on data); (4) a neighbour beside the run changes nothing of it and never links
 * to it, and taking any neighbour away puts the run back exactly; (5) a crosshair on any strand's line picks that
 * strand ({@link WireBundleBlock#strandAt}); (6) the shape of every strand ({@link CableShapes}): no V where it doubles
 * back, no detour or zig-zag in a block, no end sticking out off the run, and a cable meets a radio's body, off its
 * corners, into its back.
 */
@GameTestHolder(Radiocraft.MOD_ID)
public class CableMatrixGameTests {

	private static final String BATCH = "cablematrix";
	private static final String TEMPLATE = Radiocraft.MOD_ID + ":" + GameTestUtil.EMPTY;
	private static final int TICKS = 200;
	/** Set by {@code ./gradlew runClientTests}: the cases are being built in a world to look at. */
	static final String TEST_WORLD = "radiocraft.testWorld";

	// -------------------- KINDS --------------------

	enum Family { POWER, DATA, COAX }

	/** A kind of cable to lay: its family and its block. */
	record Kind(String name, Family family, Supplier<WireBlock> block) {
		WireBlock wire() {
			return block.get();
		}
	}

	private static final Kind POWER = new Kind("power", Family.POWER, () -> RadiocraftBlocks.WIRE.get());
	private static final Kind RED_POWER = new Kind("redpower", Family.POWER, () -> RadiocraftBlocks.COLOURED_WIRE.get(DyeColor.RED).get());
	private static final Kind DATA = new Kind("data", Family.DATA, () -> RadiocraftBlocks.DATA_WIRE.get());
	private static final Kind GREY_DATA = new Kind("greydata", Family.DATA, () -> RadiocraftBlocks.COLOURED_DATA_WIRE.get(DyeColor.LIGHT_GRAY).get());
	private static final Kind COAX = new Kind("coax", Family.COAX, () -> RadiocraftBlocks.COAX_WIRE.get());
	private static final Kind BLUE_COAX = new Kind("bluecoax", Family.COAX, () -> RadiocraftBlocks.COLOURED_COAX.get(DyeColor.BLUE).get());
	private static final Kind YELLOW_POWER = new Kind("yellowpower", Family.POWER, () -> RadiocraftBlocks.COLOURED_WIRE.get(DyeColor.YELLOW).get());
	private static final Kind ORANGE_DATA = new Kind("orangedata", Family.DATA, () -> RadiocraftBlocks.COLOURED_DATA_WIRE.get(DyeColor.ORANGE).get());
	private static final Kind RED_COAX = new Kind("redcoax", Family.COAX, () -> RadiocraftBlocks.COLOURED_COAX.get(DyeColor.RED).get());

	private static final List<Kind> SINGLE_KINDS = List.of(POWER, RED_POWER, DATA, GREY_DATA, COAX, BLUE_COAX);

	/** Another family's plain kind, for a neighbour of a different type. */
	private static Kind other(Family family, int which) {
		Family[] all = Family.values();
		Family f = all[(family.ordinal() + 1 + which) % all.length];
		return switch(f) {
			case POWER -> POWER;
			case DATA -> DATA;
			case COAX -> COAX;
		};
	}

	/** The same family in another colour. */
	private static Kind recoloured(Kind kind) {
		return switch(kind.family()) {
			case POWER -> kind == RED_POWER ? YELLOW_POWER : RED_POWER;
			case DATA -> kind == GREY_DATA ? ORANGE_DATA : GREY_DATA;
			case COAX -> kind == BLUE_COAX ? RED_COAX : BLUE_COAX;
		};
	}

	// -------------------- SHAPES --------------------

	/**
	 * A run laid along +Z at x 8 (before turning): the stone it needs, the radio at one end (on a desk if
	 * {@code radioDesk}), the cable's blocks in laying order, the device blocks its other end(s) feed, and whether it
	 * stays in the plane x 8 (so a cable at x 7 or 9 runs beside it all the way).
	 */
	record Shape(String name, List<BlockPos> stone, BlockPos radio, List<BlockPos> cable, List<BlockPos> ends, BlockPos passthrough, boolean planar,
				 int radioTurn, boolean btv) {
		Shape(String name, List<BlockPos> stone, BlockPos radio, List<BlockPos> cable, List<BlockPos> ends, BlockPos passthrough, boolean planar) {
			this(name, stone, radio, cable, ends, passthrough, planar, 0, false);
		}

		/** The same run with the radio turned {@code turn} quarter turns from its back to the cable (1 its side, 2 its front). */
		Shape radioTurned(String name, int turn) {
			return new Shape(name, stone, radio, cable, ends, passthrough, false, turn, false);
		}

		/** The same run ending at a 5-BTV, for coax. */
		Shape toBtv(String name) {
			return new Shape(name, stone, radio, cable, ends, passthrough, false, 0, true);
		}

		/** Variants of the plain shapes: not run with neighbours or bundles. */
		boolean variant() {
			return radioTurn != 0 || btv;
		}
	}

	private static List<BlockPos> line(int x, int y, int z0, int z1) {
		List<BlockPos> out = new ArrayList<>();
		for(int z = z0; z <= z1; z++)
			out.add(new BlockPos(x, y, z));
		return out;
	}

	private static List<BlockPos> xLine(int x0, int x1, int y, int z) {
		List<BlockPos> out = new ArrayList<>();
		int step = x1 >= x0 ? 1 : -1;
		for(int x = x0; x != x1 + step; x += step)
			out.add(new BlockPos(x, y, z));
		return out;
	}

	/** Stone filling x 6..10 (wide enough for a cable beside the run to have the same ground), y0..y1, z0..z1. */
	private static List<BlockPos> slab(int y0, int y1, int z0, int z1) {
		List<BlockPos> out = new ArrayList<>();
		for(int x = 6; x <= 10; x++)
			for(int y = y0; y <= y1; y++)
				for(int z = z0; z <= z1; z++)
					out.add(new BlockPos(x, y, z));
		return out;
	}

	@SafeVarargs
	private static List<BlockPos> join(List<BlockPos>... parts) {
		List<BlockPos> out = new ArrayList<>();
		for(List<BlockPos> part : parts)
			out.addAll(part);
		return out;
	}

	private static final List<Shape> SHAPES = List.of(
			new Shape("straight", List.of(), new BlockPos(8, 2, 2), line(8, 2, 3, 9), List.of(new BlockPos(8, 2, 10)), null, true),
			new Shape("cornerright", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 6), xLine(9, 11, 2, 6)), List.of(new BlockPos(12, 2, 6)), null, false),
			new Shape("cornerleft", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 6), xLine(7, 5, 2, 6)), List.of(new BlockPos(4, 2, 6)), null, false),
			new Shape("tee", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 6), xLine(9, 10, 2, 6), xLine(7, 6, 2, 6)),
					List.of(new BlockPos(11, 2, 6), new BlockPos(5, 2, 6)), null, false),
			new Shape("cross", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 6), xLine(9, 10, 2, 6), xLine(7, 6, 2, 6), line(8, 2, 7, 8)),
					List.of(new BlockPos(11, 2, 6), new BlockPos(5, 2, 6), new BlockPos(8, 2, 9)), null, false),
			new Shape("stepup", slab(2, 2, 6, 10), new BlockPos(8, 2, 2), join(line(8, 2, 3, 5), line(8, 3, 6, 8)), List.of(new BlockPos(8, 3, 9)), null, true),
			new Shape("wallup2", slab(2, 3, 6, 10), new BlockPos(8, 2, 2), join(line(8, 2, 3, 5), List.of(new BlockPos(8, 3, 5)), line(8, 4, 6, 8)),
					List.of(new BlockPos(8, 4, 9)), null, true),
			new Shape("overridge", slab(2, 2, 6, 6), new BlockPos(8, 2, 2), join(line(8, 2, 3, 5), List.of(new BlockPos(8, 3, 6)), line(8, 2, 7, 9)),
					List.of(new BlockPos(8, 2, 10)), null, true),
			new Shape("ledgedown", slab(2, 2, 1, 5), new BlockPos(8, 3, 2), join(line(8, 3, 3, 5), line(8, 2, 6, 9)), List.of(new BlockPos(8, 2, 10)), null, true),
			new Shape("ceiling", join(slab(4, 4, 3, 9), List.of(new BlockPos(8, 2, 2), new BlockPos(8, 2, 10))), new BlockPos(8, 3, 2), line(8, 3, 3, 9),
					List.of(new BlockPos(8, 3, 10)), null, true),
			new Shape("passthrough", slab(2, 4, 6, 6), new BlockPos(8, 2, 2), join(line(8, 2, 3, 5), line(8, 2, 7, 9)), List.of(new BlockPos(8, 2, 10)),
					new BlockPos(8, 2, 6), false),
			new Shape("deskclimb", List.of(new BlockPos(8, 2, 10)), new BlockPos(8, 3, 10), line(8, 2, 3, 9), List.of(new BlockPos(8, 2, 2)), null, true),
			// Tight turns (2026-11-14, the maintainer saw bundles overshoot a turn and come back in a V): an S, a U and a
			// one-block jog.
			new Shape("sbend", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 5), xLine(9, 10, 2, 5), line(10, 2, 6, 9)), List.of(new BlockPos(10, 2, 10)), null, false),
			// The U three wide, so a bundle's second device (beside its end) does not touch the run's other leg.
			new Shape("uturn", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 7), xLine(9, 11, 2, 7), List.of(new BlockPos(11, 2, 6), new BlockPos(11, 2, 5), new BlockPos(11, 2, 4))),
					List.of(new BlockPos(11, 2, 3)), null, false),
			new Shape("jog", List.of(), new BlockPos(8, 2, 2), join(line(8, 2, 3, 5), List.of(new BlockPos(9, 2, 5)), line(9, 2, 6, 9)), List.of(new BlockPos(9, 2, 10)), null, false));

	private static final List<Shape> ALL_SHAPES = allShapes();

	private static List<Shape> allShapes() {
		List<Shape> out = new ArrayList<>(SHAPES);
		out.add(shape("straight").radioTurned("straightside", 1));
		out.add(shape("straight").radioTurned("straightfront", 2));
		out.add(shape("deskclimb").radioTurned("deskside", 1));
		out.add(shape("deskclimb").radioTurned("deskfront", 2));
		out.add(shape("ceiling").radioTurned("ceilingside", 1));
		out.add(shape("straight").toBtv("straightbtv"));
		return out;
	}

	private static Shape shape(String name) {
		for(Shape s : SHAPES)
			if(s.name().equals(name))
				return s;
		throw new IllegalArgumentException(name);
	}

	// -------------------- CASES --------------------

	enum Neighbour { NONE, BESIDE, BOTH, COLOUR, CROSS }

	/** One case: a shape, the kinds in the run (one, or a bundle), a turn, and what else is laid by it. */
	record Case(Shape shape, List<Kind> kinds, Rotation rotation, Neighbour neighbour, boolean reversed) {
		Case(Shape shape, List<Kind> kinds, Rotation rotation, Neighbour neighbour) {
			this(shape, kinds, rotation, neighbour, false);
		}

		String name() {
			StringBuilder kindNames = new StringBuilder();
			for(Kind k : kinds)
				kindNames.append(kindNames.length() == 0 ? "" : "+").append(k.name());
			return String.format(Locale.ROOT, "cablematrixgametests.cablematrix_%s_%s_%s_r%d%s", kinds.size() > 1 ? "bundle" : neighbour == Neighbour.NONE ? "run" : "beside",
					shape.name(), kindNames.toString().replace('+', '_'), rotation.ordinal(),
					neighbour == Neighbour.NONE ? "" : "_" + neighbour.name().toLowerCase(Locale.ROOT)) + (reversed ? "_rev" : "");
		}
	}

	private static Rotation turn(int i) {
		return Rotation.values()[Math.floorMod(i, 4)];
	}

	@GameTestGenerator
	public static List<TestFunction> cableMatrix() {
		List<Case> cases = new ArrayList<>();
		// Every shape with every kind: power and coax in all four turns, the rest in one (spread over the turns).
		for(int s = 0; s < ALL_SHAPES.size(); s++)
			for(int k = 0; k < SINGLE_KINDS.size(); k++) {
				Shape shape = ALL_SHAPES.get(s);
				Kind kind = SINGLE_KINDS.get(k);
				if(shape.btv() && kind.family() != Family.COAX)
					continue;
				if((kind == POWER || kind == COAX) && !shape.variant())
					for(int r = 0; r < 4; r++)
						cases.add(new Case(shape, List.of(kind), turn(r), Neighbour.NONE));
				else
					cases.add(new Case(shape, List.of(kind), turn(s + k), Neighbour.NONE));
			}
		// Other cables beside the run, along every shape that stays in one plane.
		List<Kind> runs = List.of(POWER, DATA, COAX);
		int n = 0;
		for(Shape shape : SHAPES)
			if(shape.planar())
				for(Kind kind : runs)
					for(Neighbour neighbour : List.of(Neighbour.BESIDE, Neighbour.BOTH, Neighbour.COLOUR))
						cases.add(new Case(shape, List.of(kind), turn(n++), neighbour));
		for(Kind kind : runs)
			for(int r = 0; r < 2; r++)
				cases.add(new Case(shape("straight"), List.of(kind), turn(r), Neighbour.CROSS));
		// Bundles of two and three.
		List<List<Kind>> bundles = List.of(List.of(POWER, DATA), List.of(POWER, COAX), List.of(DATA, COAX), List.of(POWER, DATA, COAX),
				List.of(RED_POWER, GREY_DATA, BLUE_COAX));
		n = 0;
		for(Shape shape : SHAPES)
			if(!shape.variant() && !shape.name().equals("deskclimb") && !shape.name().equals("tee") && !shape.name().equals("cross") && !shape.name().equals("passthrough"))
				for(List<Kind> bundle : bundles)
					{
						cases.add(new Case(shape, bundle, turn(n++), Neighbour.NONE));
						// Devices the other way round from the strands' order: the bundle has to turn over (or cross) to suit.
						if(bundle.size() == 3)
							cases.add(new Case(shape, bundle, turn(n++), Neighbour.NONE, true));
					}
		List<TestFunction> tests = new ArrayList<>();
		for(Case c : cases)
			tests.add(new TestFunction(BATCH, c.name(), TEMPLATE, TICKS, 0, true, helper -> run(helper, c)));
		return tests;
	}

	// -------------------- BUILDING --------------------

	private static BlockPos at(Case c, BlockPos canonical) {
		BlockPos rel = new BlockPos(canonical.getX() - 8, canonical.getY(), canonical.getZ() - 8).rotate(c.rotation());
		return rel.offset(8, 0, 8);
	}

	private static void lay(GameTestHelper helper, BlockPos pos, WireBlock wire) {
		BlockPos abs = helper.absolutePos(pos);
		if(Strand.at(helper.getLevel(), abs).isEmpty())
			GameTestUtil.placeWire(helper, pos, wire);
		else {
			helper.assertTrue(WireBundleBlock.canAdd(helper.getLevel(), abs, wire), "Cannot add " + wire + " at " + pos.toShortString());
			WireBundleBlock.add(helper.getLevel(), abs, wire);
		}
	}

	private static void unlay(GameTestHelper helper, BlockPos pos, WireBlock wire) {
		BlockPos abs = helper.absolutePos(pos);
		List<Strand> strands = Strand.at(helper.getLevel(), abs);
		if(strands.size() > 1)
			WireBundleBlock.remove(helper.getLevel(), abs, wire);
		else if(!strands.isEmpty())
			helper.setBlock(pos, Blocks.AIR);
	}

	/** The device each family's cable ends at. */
	private static void device(GameTestHelper helper, BlockPos pos, Family family, boolean btv) {
		if(btv && family == Family.COAX) {
			for(int i = 0; i < com.arrl.radiocraft.common.blocks.antennas.FiveBtvAntennaBlock.HEIGHT; i++)
				helper.setBlock(pos.above(i), RadiocraftBlocks.FIVE_BTV_ANTENNA.get().defaultBlockState()
						.setValue(com.arrl.radiocraft.common.blocks.antennas.FiveBtvAntennaBlock.SEGMENT, i));
			return;
		}
		switch(family) {
			case POWER -> helper.setBlock(pos, RadiocraftBlocks.LARGE_BATTERY.get());
			case DATA -> helper.setBlock(pos, RadiocraftBlocks.RADIO_COMPUTER.get());
			case COAX -> {
				Block pole = RadiocraftBlocks.J_POLE_ANTENNA.get();
				helper.setBlock(pos, pole.defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
				helper.setBlock(pos.above(), pole.defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
			}
		}
	}

	/** Where each kind's device goes: the shape's end(s) for a single cable; for a bundle, ahead, left and right of its end. */
	private static Map<BlockPos, Family> devices(Case c) {
		Map<BlockPos, Family> out = new HashMap<>();
		Shape shape = c.shape();
		if(c.kinds().size() == 1) {
			for(BlockPos end : shape.ends())
				out.put(at(c, end), c.kinds().getFirst().family());
			return out;
		}
		List<BlockPos> cable = shape.cable();
		BlockPos last = cable.getLast(), before = cable.get(cable.size() - 2);
		Direction ahead = Direction.getNearest(last.getX() - before.getX(), 0, last.getZ() - before.getZ());
		// Three strands fan out in their bundle order (left, ahead, right), so none has to cross another to its device; two
		// go ahead and to one side (the bundle turns over to suit).
		BlockPos[] spots = c.kinds().size() == 3 ? new BlockPos[] {last.relative(ahead.getCounterClockWise()), last.relative(ahead), last.relative(ahead.getClockWise())}
				: new BlockPos[] {last.relative(ahead), last.relative(ahead.getCounterClockWise())};
		for(int i = 0; i < c.kinds().size(); i++)
			out.put(at(c, spots[c.reversed() ? spots.length - 1 - i : i]), c.kinds().get(i).family());
		return out;
	}

	/** The cable laid beside the run (or across it): where, and what. */
	private static Map<BlockPos, Kind> neighbours(Case c) {
		Map<BlockPos, Kind> out = new java.util.LinkedHashMap<>();
		Kind kind = c.kinds().getFirst();
		switch(c.neighbour()) {
			case BESIDE -> c.shape().cable().forEach(p -> out.put(p.east(), other(kind.family(), 0)));
			case BOTH -> c.shape().cable().forEach(p -> {
				out.put(p.east(), other(kind.family(), 0));
				out.put(p.west(), other(kind.family(), 1));
			});
			case COLOUR -> c.shape().cable().forEach(p -> out.put(p.east(), recoloured(kind)));
			case CROSS -> xLine(5, 11, 2, 6).forEach(p -> out.put(p, other(kind.family(), 0)));
			default -> {}
		}
		Map<BlockPos, Kind> turned = new java.util.LinkedHashMap<>();
		out.forEach((p, k) -> turned.put(at(c, p), k));
		return turned;
	}

	/** Which way the radio's side toward the cable faces: the cable comes in from the run's block nearest it. */
	private static Direction toCable(Case c) {
		BlockPos radio = at(c, c.shape().radio()), first = at(c, c.shape().cable().getFirst()), last = at(c, c.shape().cable().getLast());
		if(last.distSqr(radio) < first.distSqr(radio))
			first = last; // The run's other end is the one at the radio (up the desk).
		return Direction.getNearest(first.getX() - radio.getX(), first.getY() - radio.getY(), first.getZ() - radio.getZ());
	}

	/**
	 * The side of the radio the cable should go into: its back, wherever it faces, for a cable on the floor beside it
	 * (turned side or front on, the cable goes round to the back, CableRoutes, 2026-11-14); null where it may meet another
	 * face (up a desk to a turned radio, or down from the ceiling to one).
	 */
	private static Direction radioBack(Case c) {
		Direction toCable = toCable(c);
		if(!toCable.getAxis().isHorizontal())
			return null;
		if(c.shape().radioTurn() == 0)
			return toCable;
		if(c.shape().name().startsWith("desk") || c.shape().name().startsWith("ceiling"))
			return null;
		Direction facing = toCable.getOpposite();
		for(int i = 0; i < c.shape().radioTurn(); i++)
			facing = facing.getClockWise();
		return facing.getOpposite();
	}

	private static void build(GameTestHelper helper, Case c) {
		Shape shape = c.shape();
		for(BlockPos stone : shape.stone())
			helper.setBlock(at(c, stone), Blocks.STONE);
		// The radio, its back to the cable: the cable comes in from the first block of the run.
		BlockPos radio = at(c, shape.radio());
		Direction toCable = toCable(c);
		Direction facing = toCable.getAxis().isHorizontal() ? toCable.getOpposite() : Direction.NORTH;
		for(int i = 0; i < shape.radioTurn(); i++)
			facing = facing.getClockWise(); // Its side, then its front, to the cable.
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get().defaultBlockState().setValue(RadioBlock.HORIZONTAL_FACING, facing));
		devices(c).forEach((pos, family) -> device(helper, pos, family, shape.btv()));
		if(shape.passthrough() != null) {
			BlockPos wall = at(c, shape.passthrough());
			helper.setBlock(wall, RadiocraftBlocks.PASSTHROUGH.get().getBlockState(helper.getLevel(), helper.absolutePos(wall)));
			GameTestUtil.blockEntity(helper, wall, PassthroughBlockEntity.class).setMimic(Blocks.STONE.defaultBlockState());
		}
		for(BlockPos p : shape.cable())
			for(Kind kind : c.kinds())
				lay(helper, at(c, p), kind.wire());
	}

	// -------------------- CHECKS --------------------

	private static BlockPos lo(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(0, 1, 0));
	}

	private static BlockPos hi(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(15, 7, 15));
	}

	/** What is wrong with the networks, or null: the radio reaches every device at the far end, on its own family. */
	private static String networks(GameTestHelper helper, Case c) {
		BlockPos radio = at(c, c.shape().radio());
		RadioNetworkObject radioObject = GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class);
		Map<BlockPos, Family> devices = devices(c);
		int poles = 0, computers = 0;
		for(Map.Entry<BlockPos, Family> device : devices.entrySet())
			switch(device.getValue()) {
				case POWER -> {
					BatteryNetworkObject cell = GameTestUtil.networkObject(helper, device.getKey(), BatteryNetworkObject.class);
					boolean wired = false;
					for(BENetwork network : radioObject.getNetworks().values())
						if(network instanceof PowerBENetwork && network.getNetworkObjects().contains(cell))
							wired = true;
					if(!wired)
						return "the radio is not on the battery at " + device.getKey().toShortString();
				}
				case COAX -> poles++;
				case DATA -> computers++;
			}
		int antennas = radioObject.getAntennas().size();
		if(antennas != poles)
			return "the radio sees " + antennas + " antennas, wanted " + poles;
		int linked = DataWire.linked(helper.getLevel(), helper.absolutePos(radio), RadioComputerBlockEntity.class).size();
		if(linked != computers)
			return "the radio reaches " + linked + " computers on data wire, wanted " + computers;
		return null;
	}

	/** Which faces of the block at {@code pos} the point lies on. */
	private static List<Direction> facesOf(Vec3 p, BlockPos pos) {
		List<Direction> out = new ArrayList<>();
		double e = 0.01D;
		if(Math.abs(p.x - pos.getX()) < e) out.add(Direction.WEST);
		if(Math.abs(p.x - pos.getX() - 1) < e) out.add(Direction.EAST);
		if(Math.abs(p.y - pos.getY()) < e) out.add(Direction.DOWN);
		if(Math.abs(p.y - pos.getY() - 1) < e) out.add(Direction.UP);
		if(Math.abs(p.z - pos.getZ()) < e) out.add(Direction.NORTH);
		if(Math.abs(p.z - pos.getZ() - 1) < e) out.add(Direction.SOUTH);
		return out;
	}

	/**
	 * Centre lines that end on a block face and meet nothing there: no other line of a kind it joins passes through that
	 * end (the next block's, or a climb's overhang reaching over from below), and nothing solid or a device is across
	 * the face. A stub into air, or into a cable of another kind.
	 */
	static List<String> stubs(ServerLevel level, BlockPos a, BlockPos b, BlockPos origin) {
		List<String> out = new ArrayList<>();
		Map<BlockPos, List<WireGeometry.Segment>> cache = new HashMap<>();
		java.util.function.Function<BlockPos, List<WireGeometry.Segment>> at = p -> cache.computeIfAbsent(p.immutable(), q -> WireGeometry.at(level, q));
		for(BlockPos pos : BlockPos.betweenClosed(a, b)) {
			for(WireGeometry.Segment s : at.apply(pos)) {
				if(s.kind().conductsAll())
					continue;
				for(Vec3 end : new Vec3[] {s.from(), s.to()}) {
					List<Direction> faces = facesOf(end, pos);
					if(faces.isEmpty())
						continue;
					// A climb's edge stops at the top of the block it climbs; the cable lying on top runs half its width above
					// that (as the model draws them, overlapping): within half the cable's width counts as meeting.
					boolean met = false;
					for(BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
						for(WireGeometry.Segment t : at.apply(near))
							if(t != s && (t.kind() == s.kind() || s.kind().joins(t.kind())) && distanceToSegment(end, t.from(), t.to()) < s.width() / 2.0D + 0.01D)
								met = true;
					// Or it meets a device or solid block's own shape (a cable into a radio set back in its block reaches in to it).
					for(BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
						if(met || !Strand.at(level, near).isEmpty() || level.getBlockState(near).isAir())
							continue;
						for(net.minecraft.world.phys.AABB box : level.getBlockState(near).getShape(level, near).toAabbs())
							if(box.move(near).inflate(0.01D).contains(end))
								met = true;
					}
					String into = null;
					for(Direction face : faces) {
						BlockPos across = pos.relative(face);
						List<Strand> strands = Strand.at(level, across);
						if(strands.isEmpty() && !level.getBlockState(across).isAir())
							met = true;
						else if(!strands.isEmpty()) {
							boolean joins = false;
							for(Strand strand : strands)
								joins |= strand.kind() != null && (strand.kind() == s.kind() || s.kind().joins(strand.kind()));
							if(joins && strands.stream().anyMatch(st -> st.kind() != null && st.kind().conductsAll()))
								met = true; // Into a passthrough.
							if(!joins)
								into = name(strands.getFirst().kind());
						}
					}
					if(!met)
						out.add(String.format(Locale.ROOT, "%s: %s ends on %s with %s", pos.subtract(origin).toShortString(), name(s.kind()), faces,
								into == null ? "nothing there" : "only " + into + " (not its kind) there"));
				}
			}
		}
		return out;
	}

	/** Strands of different kinds closer than their cables are wide, by more than a quarter pixel (a touch at a bend is let be). */
	static List<String> overlaps(ServerLevel level, BlockPos a, BlockPos b, BlockPos origin, java.util.Set<BlockPos> crossings) {
		List<String> out = new ArrayList<>();
		for(BlockPos pos : BlockPos.betweenClosed(a, b)) {
			if(crossings.contains(pos.subtract(origin)))
				continue;
			List<WireGeometry.Segment> here = WireGeometry.at(level, pos);
			for(int i = 0; i < here.size(); i++)
				for(int j = i + 1; j < here.size(); j++) {
					WireGeometry.Segment s = here.get(i), t = here.get(j);
					if(s.kind() == t.kind())
						continue;
					double d = segmentDistance(s, t), room = (s.width() + t.width()) / 2.0D;
					if(d < room - 0.25D / 16.0D)
						out.add(String.format(Locale.ROOT, "%s: %s runs through %s (%.2f px apart, %.2f needed)", pos.subtract(origin).toShortString(),
								name(s.kind()), name(t.kind()), d * 16.0D, room * 16.0D));
				}
		}
		return out;
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		double t = length < 1.0E-12D ? 0.0D : Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / length));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/** Shortest distance between two segments, by sampling (good to well under a hundredth of a block here). */
	private static double segmentDistance(WireGeometry.Segment s, WireGeometry.Segment t) {
		double best = Double.MAX_VALUE;
		for(int i = 0; i <= 32; i++) {
			Vec3 p = s.from().add(s.to().subtract(s.from()).scale(i / 32.0D));
			best = Math.min(best, distanceToSegment(p, t.from(), t.to()));
		}
		for(int i = 0; i <= 32; i++) {
			Vec3 p = t.from().add(t.to().subtract(t.from()).scale(i / 32.0D));
			best = Math.min(best, distanceToSegment(p, s.from(), s.to()));
		}
		return best;
	}

	private static String name(WireBlock kind) {
		return kind == null ? "?" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(kind).getPath();
	}

	/** The run's centre lines, block by block, as text (to compare before and after). */
	private static Map<String, List<String>> lines(GameTestHelper helper, Case c) {
		Map<String, List<String>> out = new TreeMap<>();
		List<WireBlock> wanted = new ArrayList<>();
		for(Kind kind : c.kinds())
			wanted.add(kind.wire());
		for(BlockPos p : c.shape().cable()) {
			BlockPos pos = at(c, p);
			List<String> segments = new ArrayList<>();
			for(WireGeometry.Segment s : WireGeometry.at(helper.getLevel(), helper.absolutePos(pos)))
				if(wanted.contains(s.kind()))
					segments.add(String.format(Locale.ROOT, "%s %.4f,%.4f,%.4f-%.4f,%.4f,%.4f", name(s.kind()),
							s.from().x - helper.absolutePos(pos).getX(), s.from().y - helper.absolutePos(pos).getY(), s.from().z - helper.absolutePos(pos).getZ(),
							s.to().x - helper.absolutePos(pos).getX(), s.to().y - helper.absolutePos(pos).getY(), s.to().z - helper.absolutePos(pos).getZ()));
			segments.sort(String::compareTo);
			out.put(pos.toShortString(), segments);
		}
		return out;
	}

	private static String difference(Map<String, List<String>> before, Map<String, List<String>> after) {
		for(Map.Entry<String, List<String>> entry : before.entrySet())
			if(!entry.getValue().equals(after.get(entry.getKey())))
				return entry.getKey() + ": " + entry.getValue() + " became " + after.get(entry.getKey());
		return null;
	}

	/** A crosshair on the middle of each strand's line in the run picks that strand. */
	private static String picking(GameTestHelper helper, Case c) {
		for(BlockPos p : c.shape().cable()) {
			BlockPos abs = helper.absolutePos(at(c, p));
			for(WireGeometry.Segment s : WireGeometry.at(helper.getLevel(), abs)) {
				if(s.from().distanceTo(s.to()) < 0.1D)
					continue;
				Vec3 mid = s.from().add(s.to()).scale(0.5D);
				WireBlock picked = WireBundleBlock.strandAt(helper.getLevel(), abs, new BlockHitResult(mid, Direction.UP, abs, false));
				if(picked != s.kind())
					return at(c, p).toShortString() + ": a hit on " + name(s.kind()) + "'s line picked " + name(picked);
			}
		}
		return null;
	}

	/** A run strand linking toward a neighbour's block it does not join, or the other way: they joined. */
	private static String joined(GameTestHelper helper, Case c, Map<BlockPos, Kind> neighbours) {
		for(BlockPos p : c.shape().cable()) {
			BlockPos pos = at(c, p);
			for(Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if(!neighbours.containsKey(next))
					continue;
				for(BlockState state : Strand.statesAt(helper.getLevel(), helper.absolutePos(pos))) {
					WireBlock kind = (WireBlock)state.getBlock();
					if(!state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(dir)))
						continue;
					boolean ok = false;
					for(Strand strand : Strand.at(helper.getLevel(), helper.absolutePos(next)))
						if(strand.kind() == kind || strand.kind() != null && kind.joins(strand.kind()))
							ok = true;
					if(!ok && !c.shape().cable().contains(rotateBack(c, next)))
						return pos.toShortString() + ": " + name(kind) + " links " + dir.getName() + " to the neighbour " + name(neighbours.get(next).wire());
				}
			}
		}
		return null;
	}

	private static BlockPos rotateBack(Case c, BlockPos turned) {
		Rotation back = switch(c.rotation()) {
			case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			default -> c.rotation();
		};
		BlockPos rel = new BlockPos(turned.getX() - 8, turned.getY(), turned.getZ() - 8).rotate(back);
		return rel.offset(8, 0, 8);
	}

	private static void problems(GameTestHelper helper, Case c, String stage) {
		ServerLevel level = helper.getLevel();
		List<String> found = new ArrayList<>();
		for(String problem : WireGeometry.check(level, lo(helper), hi(helper)))
			if(!problem.contains("runs through"))
				found.add(problem); // The joins; overlaps are checked below, with a crossing on purpose let be.
		java.util.Set<BlockPos> crossings = new java.util.HashSet<>();
		if(c.neighbour() == Neighbour.CROSS && stage.startsWith("with"))
			crossings.add(at(c, new BlockPos(8, 2, 6))); // Two kinds crossing in one block have to cross.
		found.addAll(overlaps(level, lo(helper), hi(helper), helper.absolutePos(BlockPos.ZERO), crossings));
		found.addAll(stubs(level, lo(helper), hi(helper), helper.absolutePos(BlockPos.ZERO)));
		found.addAll(CableShapes.check(level, lo(helper), hi(helper), helper.absolutePos(BlockPos.ZERO), helper.absolutePos(at(c, c.shape().radio())), radioBack(c)));
		String pick = picking(helper, c);
		if(pick != null)
			found.add(pick);
		if(!found.isEmpty()) {
			// The centre lines in the log, to see what went wrong without a client.
			StringBuilder lines = new StringBuilder(c.name() + " " + stage + ":");
			for(BlockPos p : BlockPos.betweenClosed(lo(helper), hi(helper)))
				if(!Strand.at(level, p).isEmpty()) {
					lines.append(System.lineSeparator()).append(" ").append(p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
					for(String line : WireGeometry.describe(level, p))
						lines.append(System.lineSeparator()).append("   ").append(line);
				}
			Radiocraft.LOGGER.info(lines.toString());
		}
		if(!found.isEmpty())
			helper.fail(stage + ": " + String.join("; ", found.subList(0, Math.min(4, found.size()))) + (found.size() > 4 ? " (+" + (found.size() - 4) + " more)" : ""));
	}

	// -------------------- THE TEST --------------------

	private static void run(GameTestHelper helper, Case c) {
		build(helper, c);
		Map<BlockPos, Kind> neighbours = neighbours(c);
		java.util.concurrent.atomic.AtomicReference<Map<String, List<String>>> before = new java.util.concurrent.atomic.AtomicReference<>();
		var sequence = helper.startSequence()
				.thenIdle(3)
				.thenWaitUntil(() -> {
					String wrong = networks(helper, c);
					if(wrong != null)
						helper.fail(wrong);
				})
				.thenExecute(() -> {
					problems(helper, c, "laid");
					before.set(lines(helper, c));
				});
		if(c.neighbour() != Neighbour.NONE) {
			sequence = sequence
					.thenExecute(() -> neighbours.forEach((pos, kind) -> {
						if(helper.getBlockState(pos).isAir() || !Strand.at(helper.getLevel(), helper.absolutePos(pos)).isEmpty())
							lay(helper, pos, kind.wire());
					}))
					.thenIdle(3)
					.thenExecute(() -> {
						String wrong = networks(helper, c);
						if(wrong != null)
							helper.fail("with the neighbour: " + wrong);
						problems(helper, c, "with the neighbour");
						String join = joined(helper, c, neighbours);
						if(join != null)
							helper.fail(join);
						if(c.neighbour() != Neighbour.CROSS) {
							String changed = difference(before.get(), lines(helper, c));
							if(changed != null)
								helper.fail("the neighbour moved the run: " + changed);
						}
					})
					.thenExecute(() -> neighbours.forEach((pos, kind) -> unlay(helper, pos, kind.wire())))
					.thenIdle(3)
					.thenExecute(() -> {
						String changed = difference(before.get(), lines(helper, c));
						if(changed != null)
							helper.fail("taking the neighbour away left the run changed: " + changed);
						String wrong = networks(helper, c);
						if(wrong != null)
							helper.fail("after the neighbour went: " + wrong);
					});
		}
		if(c.neighbour() != Neighbour.NONE && Boolean.getBoolean(TEST_WORLD)) // Left laid in the test world, to look at.
			sequence = sequence.thenExecute(() -> neighbours.forEach((pos, kind) -> {
				if(helper.getBlockState(pos).isAir() || !Strand.at(helper.getLevel(), helper.absolutePos(pos)).isEmpty())
					lay(helper, pos, kind.wire());
			})).thenIdle(2);
		sequence.thenSucceed();
	}
}
