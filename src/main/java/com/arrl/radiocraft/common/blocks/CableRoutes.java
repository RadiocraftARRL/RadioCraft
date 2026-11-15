package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The pieces a strand draws beyond its plain look ({@link WireLook#parts}), where cable meets other cable or a radio
 * (2026-11-14, the maintainer's choices):
 * <ul>
 * <li><b>Hop:</b> in a bundle, a strand whose stub to a device would run through another strand lying between it and
 * the device rises over that strand ({@link WireLook.Part#RISER}) and goes on raised, like one cable laid over
 * another on a desk. A power and data bundle past a computer, which takes both, did that.</li>
 * <li><b>Round to the back:</b> a lone cable meeting a radio at its side or front, where the plugs are not, runs on
 * through the gap beside the radio's body and round behind it to the middle of its back ({@link WireLook.Part#SPAN},
 * {@link WireLook.Part#JOINT}), if there is room: the gap at least as wide as the cable, and nothing solid behind the
 * radio. Otherwise it goes into the side as before.</li>
 * </ul>
 * The model, the centre lines ({@link WireGeometry}) and so the tests all draw from here; the hitbox does not (the
 * pieces lie beyond the block, or are a pixel or two off the floor).
 */
public final class CableRoutes {

	private CableRoutes() {}

	/** What strand {@code i} of the {@code strands} at {@code pos} draws ({@code offsets} as {@link Strand#laterals}). */
	public static List<WireLook.Placed> drawn(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, int i) {
		BlockState state = strands.get(i);
		WireBlock wire = (WireBlock)state.getBlock();
		WireLook.Look look = wire.look(level, pos, state);
		List<WireLook.Placed> parts = steps(level, pos, state, look, dropsToConnectors(WireLook.parts(look, wire.lookConnections(level, pos, state)), look, wire.width()), wire.width());
		if(strands.size() > 1)
			return hops(level, pos, strands, offsets, i, parts);
		if(level.getBlockState(pos).getBlock() instanceof WireBlock)
			return roundToTheBack(level, pos, state, wire, offsets[i], parts);
		return parts;
	}

	// -------------------- TURNS --------------------

	/**
	 * Where a strand's middle is drawn: where it lies ({@code own}), except at a turn, a T or a cross on the level, where
	 * it moves to where its stubs' lines cross, each stub straight from the point it meets the next block at
	 * ({@code faces}). A cable kept to a wall it leaves at a turn ran on to that wall and slanted back, a V
	 * (2026-11-14, the maintainer's house); in a bundle each strand turns at its own place, so they nest round the bend.
	 * Where two stubs on one axis meet the next blocks at different places (a T whose run changes lane), that axis keeps
	 * its place. Runs up or over walls, drops and the like keep theirs, and so does a stub to an edge the cable comes up
	 * (not a link: it stays on its wall at the top of the run).
	 */
	public static WireLook.Lateral drawOffset(List<WireLook.Placed> parts, WireLook.Lateral own, WireLook.Faces faces, BlockState state) {
		Double x = null, z = null;
		boolean xConflict = false, zConflict = false, alongX = false, alongZ = false;
		for(WireLook.Placed p : parts) {
			switch(p.part()) {
				case MIDDLE, RISER -> {}
				case HORIZONTAL -> {
					Direction d = p.dir();
					if(d == null || p.reaches() || !state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(d)))
						return own; // A stub reaching into a device's block or to a beam, or to an edge run: as it was.
					double lane = faces.at(d).across(d);
					if(d.getAxis() == Direction.Axis.Z) { // A north or south stub: it sets x.
						alongZ = true;
						xConflict |= x != null && Math.abs(x - lane) > 1.0E-6D;
						x = x == null ? lane : x;
					}
					else {
						alongX = true;
						zConflict |= z != null && Math.abs(z - lane) > 1.0E-6D;
						z = z == null ? lane : z;
					}
				}
				default -> {
					return own;
				}
			}
		}
		if(!alongX || !alongZ)
			return own; // Straight on (or a dead end): it changes lane along its length as before.
		return new WireLook.Lateral(x == null || xConflict ? own.x() : x, z == null || zConflict ? own.z() : z);
	}

	/**
	 * A run up a wall that slides along it (it meets the block below and the block above at different places) as a
	 * square step instead: straight up where it comes in, along the wall halfway up, straight up to where it goes on.
	 */
	static List<WireLook.Placed> squaredWalls(List<WireLook.Placed> parts, WireLook.Faces faces, boolean ceiling, double width, WireLook.Lateral[] lanes, int index) {
		List<WireLook.Placed> out = null;
		Direction top = ceiling ? Direction.DOWN : Direction.UP, bottom = top.getOpposite();
		double h = width / 32.0D; // Half the width, block units.
		for(int k = 0; k < parts.size(); k++) {
			WireLook.Placed p = parts.get(k);
			Direction d = p.dir();
			if((p.part() != WireLook.Part.EDGE && p.part() != WireLook.Part.EDGE_FULL) || d == null || p.foot() != p.head() || p.lifted())
				continue;
			double foot = (p.part() == WireLook.Part.EDGE ? faces.at(d) : faces.at(bottom)).across(d), head = faces.at(top).across(d); // Pixels off the middle.
			if(Math.abs(foot - head) < 0.01D || Math.abs(foot - head) < width)
				continue; // Less than a cable's width: left as it is (a slant too slight to see).
			boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
			double face = positive ? 16.0D + p.head() : -p.head(); // The wall's face, pixels in the block.
			Direction.Axis along = d.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
			double from = p.part() == WireLook.Part.EDGE ? h : 0.0D;
			double middle = (stepRank(lanes, index, d, head - foot) + 1.0D) / (lanes.length + 1.0D); // The step's height, the right way up.
			if(out == null)
				out = new ArrayList<>(parts);
			out.set(k, null);
			// Up at the foot's place to just under the step, along the wall at the step, up at the head's place from just over it.
			out.add(new WireLook.Placed(WireLook.Part.POST, d, 0, p.head(), sixteenths(8.0D + foot), (int)Math.round(from * 256.0D), (int)Math.round((middle - h) * 256.0D), 0));
			out.add(new WireLook.Placed(WireLook.Part.POST, d, 0, p.head(), sixteenths(8.0D + head), (int)Math.round((middle + h) * 256.0D), 256, 0));
			int lo = (int)Math.round((middle - h) * 256.0D), hi = lo + 256; // The floor pieces lifted so their middle is at the step.
			List<Point> path = List.of(Point.of(along, 8.0D + foot, face), Point.of(along, 8.0D + head, face));
			out.addAll(pieces(path, width, true, true, lo, hi));
		}
		if(out == null)
			return parts;
		out.removeIf(java.util.Objects::isNull);
		return List.copyOf(out);
	}

	// -------------------- STAIRS --------------------

	/** Half a block, 1/256 of a block: a step's rise. */
	private static final int STEP = 128;

	/**
	 * Cable up a staircase (2026-11-15, the maintainer: "make wires and bundles cleanly run up stairs"), following the
	 * steps at right angles. A stair is two steps of half a block in one block: the tread at the front, half a block up,
	 * and the top at the back, a whole block up; the wire on a stair lies on the top ({@link WireBlock#stepUnder}), with
	 * its middle over the riser between the two. The wire below climbs the stair ({@link WireBlock#isStepUp}) to it:
	 * <ul>
	 * <li>the climber's run up the wall stops at the tread, half a block up, instead of going up the whole block;</li>
	 * <li>the wire on the stair takes it from there: its stub towards the climber comes down to the tread, and a riser at
	 * its middle goes up the step to its own level.</li>
	 * </ul>
	 * So each step is a riser and a tread, one after another, the whole way up.
	 */
	static List<WireLook.Placed> steps(BlockGetter level, BlockPos pos, BlockState state, WireLook.Look look, List<WireLook.Placed> parts, double width) {
		if(WireBlock.isCeiling(state))
			return parts;
		Direction hug = look.hug(), arrive = look.arrive();
		boolean climbsStep = look.end() == WireLook.End.CLIMB_WIRE && hug != null && WireBlock.isStepUp(level, pos.relative(hug), hug);
		Direction rise = WireBlock.stepUnder(level, pos);
		boolean onStep = arrive != null && arrive.getAxis().isHorizontal() && rise == arrive.getOpposite();
		if(!climbsStep && !onStep)
			return parts;
		List<WireLook.Placed> out = new ArrayList<>(parts.size() + 1);
		boolean edgeDone = false, stubDone = false;
		for(WireLook.Placed p : parts) {
			if(climbsStep && !edgeDone && (p.part() == WireLook.Part.EDGE || p.part() == WireLook.Part.EDGE_FULL) && p.dir() == hug && !p.lifted()) {
				out.add(p.withHeight(0, STEP)); // Up the riser to the tread only.
				edgeDone = true;
			}
			else if(onStep && !stubDone && p.part() == WireLook.Part.HORIZONTAL && p.dir() == arrive && !p.lifted()) {
				out.add(p.withHeight(-STEP, 256 - STEP)); // Down on the tread.
				stubDone = true;
			}
			else
				out.add(p);
		}
		if(stubDone) // Up the step from the tread to the middle.
			out.add(new WireLook.Placed(WireLook.Part.RISER, null).withHeight(-STEP, (int)Math.round(width * 16.0D)));
		return List.copyOf(out);
	}

	// -------------------- DROPS --------------------

	/**
	 * A hung wire's drop down a radio's face ({@link WireLook.Look#dropsTo}) cut short at its connectors
	 * ({@link WireLook.Look#dropEnd}): its top stays at the ceiling stub's line (half the cable's width down), its foot
	 * rises from the floor to the connectors.
	 */
	private static List<WireLook.Placed> dropsToConnectors(List<WireLook.Placed> parts, WireLook.Look look, double width) {
		List<WireLook.Placed> out = null;
		double h = width / 32.0D; // Half the width, block units.
		for(int k = 0; k < parts.size(); k++) {
			WireLook.Placed p = parts.get(k);
			if(p.part() != WireLook.Part.DROP || look.dropEnd(p.dir()) == 0)
				continue;
			// The right way up, the drop runs from h (at the ceiling) to 1 (the floor): to end at 1 - end instead.
			double to = 1.0D - look.dropEnd(p.dir()) / 16.0D, a = (to - h) / (1.0D - h), b = h * (1.0D - a);
			if(out == null)
				out = new ArrayList<>(parts);
			out.set(k, p.withHeight((int)Math.round(b * 256.0D), (int)Math.round((a + b) * 256.0D)));
		}
		return out == null ? parts : List.copyOf(out);
	}

	// -------------------- HOPS --------------------

	/**
	 * Whether {@code state}'s stub towards {@code dir} goes to a device it links to: a block with a block entity (a radio,
	 * a computer, a battery), not cable. A stub towards an edge the cable runs up or arrives on, or towards a wall, is
	 * not one (stairs beside a run up a corner were taken for a device, 2026-11-14).
	 */
	private static boolean linkedDevice(BlockGetter level, BlockPos pos, BlockState state, Direction dir) {
		if(!state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(dir)))
			return false;
		BlockPos at = pos.relative(dir);
		return Strand.at(level, at).isEmpty() && level.getBlockEntity(at) != null;
	}

	private static List<WireLook.Placed> hops(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, int i, List<WireLook.Placed> parts) {
		WireBlock own = (WireBlock)strands.get(i).getBlock();
		List<WireLook.Placed> out = null;
		int riser = 0;
		for(int k = 0; k < parts.size(); k++) {
			WireLook.Placed p = parts.get(k);
			Direction dir = p.dir();
			if(p.part() != WireLook.Part.HORIZONTAL || dir == null || !dir.getAxis().isHorizontal() || p.lifted() || !linkedDevice(level, pos, strands.get(i), dir))
				continue;
			int sign = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
			double mine = offsets[i].along(dir) * sign;
			int lift = 0;
			for(int j = 0; j < strands.size(); j++) {
				if(j == i || offsets[j].along(dir) * sign <= mine + 0.5D)
					continue; // Not between this strand and the device.
				BlockState other = strands.get(j);
				WireBlock kind = (WireBlock)other.getBlock();
				boolean crosses = false;
				for(WireLook.Placed q : WireLook.parts(kind.look(level, pos, other), kind.lookConnections(level, pos, other)))
					if(q.part() == WireLook.Part.HORIZONTAL && q.dir() != null && q.dir().getAxis().isHorizontal() && q.dir().getAxis() != dir.getAxis()
							&& other.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(q.dir())))
						crosses = true; // It runs across this stub's way.
				if(crosses) // Up over it: half of each cable's width, and half a pixel between.
					lift = Math.max(lift, (int)Math.ceil((own.width() + kind.width()) / 2.0D + 0.5D));
			}
			if(lift == 0)
				continue;
			if(out == null)
				out = new ArrayList<>(parts);
			out.set(k, p.withHeight(lift * 16, (16 + lift) * 16));
			riser = Math.max(riser, lift);
		}
		if(out == null)
			return parts;
		// Up from the middle to the raised stub's top, unless the strand already goes up its wall from the middle: the
		// raised stub meets that run instead of standing a second riser beside it (2026-11-15, the maintainer's house).
		if(!climbsFromMiddle(parts, offsets[i], own.width(), WireBlock.isCeiling(strands.get(i))))
			out.add(new WireLook.Placed(WireLook.Part.RISER, null).withHeight(0, (int)Math.round((riser + own.width()) * 16.0D)));
		return List.copyOf(out);
	}

	/** True if {@code parts} have a run up a wall the strand's middle ({@code at}) lies against. */
	private static boolean climbsFromMiddle(List<WireLook.Placed> parts, WireLook.Lateral at, double width, boolean ceiling) {
		if(ceiling)
			return false;
		for(WireLook.Placed p : parts) {
			Direction d = p.dir();
			if((p.part() == WireLook.Part.EDGE || p.part() == WireLook.Part.EDGE_FULL) && d != null && d.getAxis().isHorizontal() && !p.lifted()
					&& at.along(d) * d.getAxisDirection().getStep() >= 8.0D - width)
				return true;
		}
		return false;
	}

	// -------------------- ROUND TO THE BACK --------------------

	/** A point in the wire's block, pixels (and may lie beyond it). */
	private record Point(double x, double z) {
		double on(Direction.Axis axis) {
			return axis == Direction.Axis.X ? x : z;
		}

		static Point of(Direction.Axis axis, double along, double across) {
			return axis == Direction.Axis.X ? new Point(along, across) : new Point(across, along);
		}
	}

	private static List<WireLook.Placed> roundToTheBack(BlockGetter level, BlockPos pos, BlockState state, WireBlock wire, WireLook.Lateral offset, List<WireLook.Placed> parts) {
		if(WireBlock.isCeiling(state))
			return parts;
		WireLook.Faces faces = null;
		for(WireLook.Placed p : parts) {
			Direction d = p.dir();
			if(p.part() != WireLook.Part.HORIZONTAL || d == null || !d.getAxis().isHorizontal() || !linkedDevice(level, pos, state, d))
				continue;
			BlockPos radioPos = pos.relative(d);
			BlockState radio = level.getBlockState(radioPos);
			if(!(radio.getBlock() instanceof RadioBlock) || !radio.hasProperty(RadioBlock.HORIZONTAL_FACING) || WallMount.onWall(radio)
					|| radio.hasProperty(RadioBlock.CEILING) && radio.getValue(RadioBlock.CEILING))
				continue;
			Direction back = radio.getValue(RadioBlock.HORIZONTAL_FACING).getOpposite(), side = d.getOpposite();
			if(side == back)
				continue; // It meets the back already.
			if(faces == null)
				faces = wire.faces(level, pos, offset);
			List<Point> path = path(level, pos, radioPos, radio, d, back, wire.width(), 8.0D + faces.at(d).across(d));
			if(path == null)
				continue;
			List<WireLook.Placed> out = new ArrayList<>(parts);
			out.addAll(pieces(path, wire.width(), true, true, 0, 256));
			return List.copyOf(out);
		}
		return parts;
	}

	/**
	 * The way round from where this block's stub meets the radio's face (across at {@code lane} pixels) to the middle of
	 * its back, as points in this block's pixels, or null if there is no room. {@code d}: from the wire to the radio.
	 */
	private static List<Point> path(BlockGetter level, BlockPos pos, BlockPos radioPos, BlockState radio, Direction d, Direction back, double width, double lane) {
		VoxelShape shape = radio.getShape(level, radioPos);
		if(shape.isEmpty())
			return null;
		double h = width / 2.0D;
		// The radio's body in its own block, pixels.
		double minX = shape.min(Direction.Axis.X) * 16.0D, maxX = shape.max(Direction.Axis.X) * 16.0D;
		double minZ = shape.min(Direction.Axis.Z) * 16.0D, maxZ = shape.max(Direction.Axis.Z) * 16.0D;
		Direction.Axis backAxis = back.getAxis();
		double backPlane = plane(back, minX, maxX, minZ, maxZ);
		double behind = backPlane + (positive(back) ? h : -h); // The cable's centre, lying against the back.
		// Behind the radio must be clear if the cable goes out of its block there.
		boolean outside = behind - h < 0.0D || behind + h > 16.0D;
		BlockPos behindPos = radioPos.relative(back);
		if(outside && (!level.getBlockState(behindPos).getCollisionShape(level, behindPos).isEmpty() || !Strand.at(level, behindPos).isEmpty()))
			return null;
		Direction.Axis across = backAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X; // Along the back.
		double backMiddle = across == Direction.Axis.X ? (minX + maxX) / 2.0D : (minZ + maxZ) / 2.0D;
		Direction side = d.getOpposite(); // The radio's face towards the wire.
		List<Point> radioPath = new ArrayList<>();
		if(side.getAxis() != backAxis) { // Its side: on through the gap beside it, then along the back.
			Double gap = gap(side, width, minX, maxX, minZ, maxZ);
			if(gap == null)
				return null;
			radioPath.add(Point.of(across, gap, lane));
			radioPath.add(Point.of(across, gap, behind));
			radioPath.add(Point.of(across, backMiddle, behind));
		}
		else { // Its front: along the gap in front to a side, down that side, then along the back.
			Double front = gap(side, width, minX, maxX, minZ, maxZ);
			Direction towards = lane >= 8.0D ? Direction.get(Direction.AxisDirection.POSITIVE, across) : Direction.get(Direction.AxisDirection.NEGATIVE, across);
			Double sideGap = gap(towards, width, minX, maxX, minZ, maxZ);
			if(sideGap == null) {
				towards = towards.getOpposite();
				sideGap = gap(towards, width, minX, maxX, minZ, maxZ);
			}
			if(front == null || sideGap == null)
				return null;
			radioPath.add(Point.of(backAxis, front, lane));
			radioPath.add(Point.of(backAxis, front, sideGap));
			radioPath.add(Point.of(backAxis, behind, sideGap));
			radioPath.add(Point.of(backAxis, behind, backMiddle));
		}
		// From the radio's block to this one.
		double dx = (radioPos.getX() - pos.getX()) * 16.0D, dz = (radioPos.getZ() - pos.getZ()) * 16.0D;
		List<Point> out = new ArrayList<>();
		for(Point p : radioPath)
			out.add(new Point(p.x() + dx, p.z() + dz));
		return out;
	}

	private static boolean positive(Direction dir) {
		return dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
	}

	/** Where the body's face towards {@code dir} is, pixels in its block. */
	private static double plane(Direction dir, double minX, double maxX, double minZ, double maxZ) {
		return switch(dir) {
			case NORTH -> minZ;
			case SOUTH -> maxZ;
			case WEST -> minX;
			case EAST -> maxX;
			default -> 0.0D;
		};
	}

	/** The middle of the gap between the body's {@code dir} face and its block's, pixels; null if a cable this wide does not fit. */
	private static Double gap(Direction dir, double width, double minX, double maxX, double minZ, double maxZ) {
		double body = plane(dir, minX, maxX, minZ, maxZ), edge = positive(dir) ? 16.0D : 0.0D;
		return Math.abs(edge - body) + 0.01D < width ? null : (body + edge) / 2.0D;
	}

	/**
	 * Straight runs between the points, with a joint cube at each bend. The runs end against the cubes (and the first
	 * starts against the cube or middle at the first point) rather than overlapping them: two pieces of one cable in the
	 * same place flicker (z-fighting, 2026-11-14). {@code jointFirst}: a cube at the first point (not the strand's own
	 * middle); {@code jointLast}: one at the last (not where it meets the next block at a face, which it ends exactly on).
	 * {@code lo}/{@code hi}: the pieces' height map ({@link WireLook.Placed#withHeight}).
	 */
	private static List<WireLook.Placed> pieces(List<Point> path, double width, boolean jointFirst, boolean jointLast, int lo, int hi) {
		double h = width / 2.0D;
		int length = (int)Math.round((8.0D - h) * 16.0D); // The stub model's own length, face to inner end.
		List<WireLook.Placed> out = new ArrayList<>();
		int last = path.size() - 1;
		for(int k = 0; k <= last; k++) {
			Point p = path.get(k);
			boolean joint = (k > 0 && k < last) || (k == 0 && jointFirst) || (k == last && jointLast);
			if(joint)
				out.add(new WireLook.Placed(WireLook.Part.JOINT, null, sixteenths(p.x()), sixteenths(p.z()), 0, lo, hi, 0));
			if(k == 0)
				continue;
			Point q = path.get(k - 1);
			boolean alongX = Math.abs(p.x() - q.x()) > Math.abs(p.z() - q.z());
			Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
			double a = q.on(axis), b = p.on(axis), sign = Math.signum(b - a);
			double start = a + sign * h, end = (k < last || jointLast) ? b - sign * h : b;
			if((end - start) * sign <= 1.0E-3D)
				continue; // The cubes touch: nothing between.
			double at = alongX ? p.z() : p.x();
			out.add(new WireLook.Placed(WireLook.Part.SPAN, alongX ? Direction.WEST : Direction.NORTH, sixteenths(Math.min(start, end)), sixteenths(Math.max(start, end)),
					sixteenths(at), lo, hi, length));
		}
		return out;
	}

	// -------------------- RIGHT ANGLES --------------------

	/** Everything a strand draws, and where: its pieces, its middle's place, and where it meets each next block. */
	public record Drawn(List<WireLook.Placed> parts, WireLook.Lateral offset, WireLook.Faces faces) {}

	/**
	 * Strand {@code i} of {@code strands} at {@code pos} as the model and the centre lines draw it: its pieces
	 * ({@link #drawn}), its middle where its turns bend ({@link #drawOffset}), and every stub at right angles
	 * ({@link #squared}).
	 */
	public static Drawn layout(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, int i) {
		return layout(level, pos, strands, offsets, i, true);
	}

	/** {@link #layout}, with or without the {@link #bridges} where strands cross. */
	public static Drawn layout(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, int i, boolean bridges) {
		Drawn base = plainLayout(level, pos, strands, offsets, i);
		if(!bridges || strands.size() < 2 || base.parts().stream().noneMatch(p -> p.part() == WireLook.Part.SPAN && !p.lifted()))
			return base; // A lone wire, or no step to move or raise (most strands): the others need not be looked at.
		Drawn[] all = new Drawn[strands.size()];
		for(int j = 0; j < all.length; j++)
			all[j] = j == i ? base : plainLayout(level, pos, strands, offsets, j);
		return bridges(pos, strands, all, i);
	}

	/**
	 * Every strand at {@code pos} as {@link #layout} draws it, each strand's plain layout worked out once and shared by
	 * the others' {@link #bridges} (2026-11-15: one strand at a time worked every other out again, S x S times a block).
	 */
	public static Drawn[] layoutAll(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, boolean bridges) {
		Drawn[] plain = new Drawn[strands.size()];
		for(int j = 0; j < plain.length; j++)
			plain[j] = plainLayout(level, pos, strands, offsets, j);
		if(!bridges || strands.size() < 2)
			return plain;
		Drawn[] out = plain.clone();
		for(int i = 0; i < plain.length; i++)
			if(plain[i].parts().stream().anyMatch(p -> p.part() == WireLook.Part.SPAN && !p.lifted()))
				out[i] = bridges(pos, strands, plain, i);
		return out;
	}

	// -------------------- BRIDGES --------------------

	/**
	 * Where two strands of a bundle must cross on the floor (2026-11-15, the maintainer: "keep on working on all the wire
	 * crossings"): they come into the block in one order and leave it in the other, so one's step across runs through
	 * the other. The step is raised over it instead, a bridge: the piece across lifted half of each cable's width and
	 * half a pixel, the cubes at its two ends stood up into columns to meet it. Only a step between two bends (both ends
	 * inside the block, a {@link WireLook.Part#SPAN} between {@link WireLook.Part#JOINT}s) is raised, so the cable still
	 * meets the next block where it did. Of two steps crossing, the later strand's is raised; a step crossing anything
	 * else (a stub, the middle) is raised over it. Worked out from every strand's plain layout, so each strand's model
	 * agrees with the others' without asking them back. The bundle solver counts crossings without bridges
	 * ({@link WireGeometry#crossings}), so it still turns bundles to need as few as it can.
	 */
	static Drawn bridges(BlockPos pos, List<BlockState> strands, Drawn[] all, int i) {
		Drawn mine = all[i];
		WireBlock own = (WireBlock)strands.get(i).getBlock();
		boolean ceiling = WireBlock.isCeiling(strands.get(i));
		List<WireLook.Placed> parts = new ArrayList<>(mine.parts());
		boolean changed = false;
		// The other strands' centre lines, worked out once.
		Others others = new Others(strands, all, i, ceiling, new ArrayList<>());
		for(int j = 0; j < all.length; j++)
			others.lines().add(j == i || WireBlock.isCeiling(strands.get(j)) != ceiling ? List.of()
					: WireGeometry.segments(pos, (WireBlock)strands.get(j).getBlock(), ceiling, all[j]));
		for(int k = 0; k < parts.size(); k++) {
			WireLook.Placed p = parts.get(k);
			if(p.part() != WireLook.Part.SPAN || p.lifted() || clashes(pos, own, mine, parts, k, others) == 0)
				continue;
			int[] ends = spanEnds(parts, p, own.width(), mine.offset());
			if(ends == null)
				continue; // An end on a block face: moving or raising it would part it from the next block.
			int lift = 0;
			for(int j = 0; j < all.length; j++)
				if(j != i)
					lift = Math.max(lift, (int)Math.ceil((own.width() + ((WireBlock)strands.get(j).getBlock()).width()) / 2.0D + 0.5D));
			// The step slid along a little (nearest first), flat or raised: the first that clears every other strand.
			List<WireLook.Placed> best = null;
			search:
			for(int d : SHIFTS)
				for(boolean raise : new boolean[] { false, true }) {
					List<WireLook.Placed> tried = shifted(parts, k, ends, d, own.width());
					if(tried == null)
						continue;
					if(raise)
						raise(tried, k, ends, lift, own.width(), mine.offset());
					if(total(pos, own, mine, others, tried) == 0 && (!raise || clearOfOwnRuns(pos, own, ceiling, mine, tried))) {
						best = tried;
						break search;
					}
				}
			if(best == null) { // Nothing clears it all: raised where it is, the fewest clashes.
				best = new ArrayList<>(parts);
				raise(best, k, ends, lift, own.width(), mine.offset());
				if(total(pos, own, mine, others, best) >= total(pos, own, mine, others, parts)
						|| !clearOfOwnRuns(pos, own, ceiling, mine, best))
					continue;
			}
			parts = best;
			changed = true;
		}
		return changed ? new Drawn(List.copyOf(parts), mine.offset(), mine.faces()) : mine;
	}

	/** How far a step may slide to clear another strand, 1/256 of a block: none first, then nearest first either way. */
	private static final int[] SHIFTS = { 0, 16, -16, 24, -24, 32, -32, 48, -48, 64, -64 };

	/** {@code parts} with step {@code k} raised {@code lift} pixels and its end joints stood up into columns to meet it. */
	private static void raise(List<WireLook.Placed> parts, int k, int[] ends, int lift, double width, WireLook.Lateral middle) {
		parts.set(k, parts.get(k).withHeight(lift * 16, 256 + lift * 16));
		int column = (int)Math.round(256.0D * (lift + width) / width);
		for(int e : ends) {
			if(e == AT_MIDDLE) // Up from the middle cube: a column standing on it.
				parts.add(new WireLook.Placed(WireLook.Part.JOINT, null, sixteenths(8.0D + middle.x()), sixteenths(8.0D + middle.z()), 0, 0, column, 0));
			else
				parts.set(e, parts.get(e).withHeight(0, column));
		}
	}

	/** In {@link #spanEnds}: the end is at the strand's middle, not a joint. */
	private static final int AT_MIDDLE = -2;

	/**
	 * {@code parts} with step {@code k} slid {@code d} (1/256 of a block) across its length: the step, its two end
	 * joints, and the two pieces running into those joints stretched or cut to meet them. Null if a piece would vanish.
	 */
	private static List<WireLook.Placed> shifted(List<WireLook.Placed> parts, int k, int[] ends, int d, double width) {
		List<WireLook.Placed> out = new ArrayList<>(parts);
		if(d == 0)
			return out;
		if(ends[0] == AT_MIDDLE || ends[1] == AT_MIDDLE)
			return null; // It starts at the middle: it cannot slide.
		WireLook.Placed span = parts.get(k);
		boolean alongX = span.dir() == Direction.WEST;
		int half = sixteenths(width / 2.0D);
		out.set(k, new WireLook.Placed(span.part(), span.dir(), span.foot(), span.head(), span.side() + d, span.lo(), span.hi(), span.length()));
		for(int e : ends) {
			WireLook.Placed joint = parts.get(e);
			// A joint's foot is x, head z; the step runs along x (side z) or along z (side x).
			int jx = joint.foot() + (alongX ? 0 : d), jz = joint.head() + (alongX ? d : 0);
			out.set(e, new WireLook.Placed(joint.part(), joint.dir(), jx, jz, joint.side(), joint.lo(), joint.hi(), joint.length()));
			int old = alongX ? joint.head() : joint.foot(); // The joint's place across the step, before.
			int along = alongX ? joint.foot() : joint.head();
			boolean found = false;
			for(int q = 0; q < out.size(); q++) {
				WireLook.Placed r = out.get(q);
				if(q == k || r.part() != WireLook.Part.SPAN || (r.dir() == Direction.WEST) == alongX || Math.abs(r.side() - along) > 1)
					continue; // Not a piece across the step's way through this joint.
				int foot = r.foot(), head = r.head();
				if(Math.abs(head - (old - half)) <= 1)
					head += d;
				else if(Math.abs(foot - (old + half)) <= 1)
					foot += d;
				else
					continue;
				if(head - foot < 4)
					return null;
				out.set(q, new WireLook.Placed(r.part(), r.dir(), foot, head, r.side(), r.lo(), r.hi(), r.length()));
				found = true;
			}
			if(!found)
				return null; // Something else meets this joint (the middle): leave the step where it is.
		}
		return out;
	}

	/**
	 * True if no column of a bridge stands within a cable's width of the strand's own run up a wall: beside it, the two
	 * read as one cable bulging (a bridge from a strand's middle in a corner, 2026-11-15). Better left crossing flat.
	 */
	private static boolean clearOfOwnRuns(BlockPos pos, WireBlock own, boolean ceiling, Drawn mine, List<WireLook.Placed> parts) {
		List<WireGeometry.Segment> segs = WireGeometry.segments(pos, own, ceiling, new Drawn(parts, mine.offset(), mine.faces()));
		for(int c = 0; c < parts.size(); c++) {
			WireLook.Placed column = parts.get(c);
			if(column.part() != WireLook.Part.JOINT || column.hi() - column.lo() <= 256)
				continue;
			for(int r = 0; r < parts.size(); r++)
				switch(parts.get(r).part()) {
					case EDGE, EDGE_FULL, POST, VERTICAL, RISER, DROP -> {
						if(WireGeometry.distance(segs.get(c), segs.get(r)) < own.width() / 16.0D)
							return false;
					}
					default -> {}
				}
		}
		return true;
	}

	/** How many of the other strands' pieces piece {@code k} of {@code parts} runs through. */
	/** The other strands of a bundle as {@link #bridges} weighs them: their plain layouts and centre lines (empty for this one). */
	private record Others(List<BlockState> strands, Drawn[] all, int i, boolean ceiling, List<List<WireGeometry.Segment>> lines) {}

	private static int clashes(BlockPos pos, WireBlock own, Drawn mine, List<WireLook.Placed> parts, int k, Others others) {
		List<WireGeometry.Segment> segs = WireGeometry.segments(pos, own, others.ceiling(), new Drawn(parts, mine.offset(), mine.faces()));
		return clashesOf(own, segs.get(k), parts.get(k), others);
	}

	/** How many times any piece of {@code parts} runs through another strand's. */
	private static int total(BlockPos pos, WireBlock own, Drawn mine, Others others, List<WireLook.Placed> parts) {
		List<WireGeometry.Segment> segs = WireGeometry.segments(pos, own, others.ceiling(), new Drawn(parts, mine.offset(), mine.faces()));
		int n = 0;
		for(int k = 0; k < segs.size(); k++)
			n += clashesOf(own, segs.get(k), parts.get(k), others);
		return n;
	}

	private static int clashesOf(WireBlock own, WireGeometry.Segment seg, WireLook.Placed piece, Others others) {
		int n = 0, i = others.i();
		for(int j = 0; j < others.all().length; j++) {
			List<WireGeometry.Segment> theirs = others.lines().get(j);
			if(theirs.isEmpty())
				continue;
			WireBlock other = (WireBlock)others.strands().get(j).getBlock();
			for(int q = 0; q < theirs.size(); q++) {
				WireLook.Placed tp = others.all()[j].parts().get(q);
				if(tp.part() == WireLook.Part.SPAN && !tp.lifted() && j > i && piece.part() == WireLook.Part.SPAN)
					continue; // Two steps: the later strand's moves or goes over.
				if(WireGeometry.distance(seg, theirs.get(q)) < (own.width() + other.width()) / 32.0D - 1.0E-4D)
					n++;
			}
		}
		return n;
	}

	/**
	 * The indices of the two {@link WireLook.Part#JOINT}s at {@code span}'s ends ({@link #AT_MIDDLE} for an end at the
	 * strand's {@code middle}), or null unless both ends are inside the block.
	 */
	private static int[] spanEnds(List<WireLook.Placed> parts, WireLook.Placed span, double width, WireLook.Lateral middle) {
		boolean alongX = span.dir() == Direction.WEST;
		int half = sixteenths(width / 2.0D);
		int a = -1, b = -1;
		int mAlong = sixteenths(8.0D + (alongX ? middle.x() : middle.z())), mAcross = sixteenths(8.0D + (alongX ? middle.z() : middle.x()));
		if(Math.abs(mAcross - span.side()) <= 1) {
			if(Math.abs(mAlong - (span.foot() - half)) <= 1)
				a = AT_MIDDLE;
			else if(Math.abs(mAlong - (span.head() + half)) <= 1)
				b = AT_MIDDLE;
		}
		for(int k = 0; k < parts.size(); k++) {
			WireLook.Placed p = parts.get(k);
			if(p.part() != WireLook.Part.JOINT)
				continue;
			int along = alongX ? p.foot() : p.head(), across = alongX ? p.head() : p.foot();
			if(Math.abs(across - span.side()) > 1)
				continue;
			if(Math.abs(along - (span.foot() - half)) <= 1 && a == -1)
				a = k;
			else if(Math.abs(along - (span.head() + half)) <= 1 && b == -1)
				b = k;
		}
		return a == -1 || b == -1 ? null : new int[] { a, b };
	}

	private static Drawn plainLayout(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, int i) {
		BlockState state = strands.get(i);
		WireBlock wire = (WireBlock)state.getBlock();
		WireLook.Lateral offset = strands.size() == 1 && level.getBlockState(pos).getBlock() instanceof WireBlock ? Strand.loneOffset(level, pos, state) : offsets[i];
		WireLook.Lateral[] own = offsets.clone();
		own[i] = offset;
		List<WireLook.Placed> parts = drawn(level, pos, strands, own, i);
		WireLook.Faces faces = wire.faces(level, pos, offset);
		WireLook.Lateral at = drawOffset(parts, offset, faces, state);
		return new Drawn(squaredWalls(squared(parts, at, faces, wire.width(), own, i), faces, WireBlock.isCeiling(state), wire.width(), own, i), at, faces);
	}

	/**
	 * Where among {@code count} steps this strand takes its own: 0 first. Strands of a bundle shifting over together
	 * would cross each other's runs if they all stepped at once; the one furthest the way they shift steps first, and
	 * the rest after it in order, so they nest like stairs (2026-11-14).
	 */
	private static int stepRank(WireLook.Lateral[] lanes, int i, Direction d, double shift) {
		int rank = 0;
		double mine = lanes[i].across(d) * Math.signum(shift);
		for(int j = 0; j < lanes.length; j++) {
			double theirs = lanes[j].across(d) * Math.signum(shift);
			if(j != i && (theirs > mine + 1.0E-6D || Math.abs(theirs - mine) <= 1.0E-6D && j < i))
				rank++;
		}
		return rank;
	}

	/**
	 * Every stub on the level that would slant (its strand's place in this block not where it meets the next block) as
	 * square steps instead (2026-11-14, the maintainer: "right angles only"): straight out from the middle at the
	 * strand's own place, across to the meeting place halfway to the face, then straight on to the face. A stub that
	 * already runs straight stays as it is; one to a horn's feed keeps its slant.
	 */
	static List<WireLook.Placed> squared(List<WireLook.Placed> parts, WireLook.Lateral own, WireLook.Faces faces, double width, WireLook.Lateral[] lanes, int index) {
		List<WireLook.Placed> out = null;
		for(int k = 0; k < parts.size(); k++) {
			WireLook.Placed p = parts.get(k);
			Direction d = p.dir();
			if(p.part() != WireLook.Part.HORIZONTAL || d == null || !d.getAxis().isHorizontal() || p.side() != 0 || p.foot() != p.head())
				continue;
			double mine = own.across(d), meet = faces.at(d).across(d);
			if(Math.abs(mine - meet) < 0.01D)
				continue;
			boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
			double middle = 8.0D + own.along(d), face = positive ? 16.0D + p.head() : -p.head();
			double step = middle + (face - middle) * (stepRank(lanes, index, d, meet - mine) + 1.0D) / (lanes.length + 1.0D);
			// Less than a cable's width over: a step would be two cubes on top of each other, a fold. Straight at the meeting
			// place instead, from the middle cube, which covers the little offset.
			List<Point> path = Math.abs(meet - mine) < width ? List.of(Point.of(d.getAxis(), middle, 8.0D + meet), Point.of(d.getAxis(), face, 8.0D + meet))
					: List.of(Point.of(d.getAxis(), middle, 8.0D + mine), Point.of(d.getAxis(), step, 8.0D + mine),
					Point.of(d.getAxis(), step, 8.0D + meet), Point.of(d.getAxis(), face, 8.0D + meet));
			if(out == null)
				out = new ArrayList<>(parts);
			out.set(k, null);
			out.addAll(pieces(path, width, false, false, p.lo(), p.hi()));
		}
		if(out == null)
			return parts;
		out.removeIf(java.util.Objects::isNull);
		return List.copyOf(out);
	}

	private static int sixteenths(double px) {
		return (int)Math.round(px * 16.0D);
	}
}
