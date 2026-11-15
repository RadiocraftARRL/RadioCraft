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
		List<WireLook.Placed> parts = dropsToConnectors(WireLook.parts(look, wire.lookConnections(level, pos, state)), look, wire.width());
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
		// Up from the middle to the raised stub's top.
		out.add(new WireLook.Placed(WireLook.Part.RISER, null).withHeight(0, (int)Math.round((riser + own.width()) * 16.0D)));
		return List.copyOf(out);
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
