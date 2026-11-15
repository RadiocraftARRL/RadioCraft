package com.arrl.radiocraft.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * Where cable is drawn, as centre lines, worked out the way the client model places its pieces ({@link WireLook#parts},
 * {@link WireLook#affine}): for checking by eye-free means that cable meets cable across block faces and that the
 * strands of a bundle do not run through each other ({@code /rcdebug wires}, GameTests). Server safe: no models.
 */
public final class WireGeometry {

	private WireGeometry() {}

	/** A straight piece of a strand's centre line, world coordinates, and how wide the cable is (blocks). */
	public record Segment(WireBlock kind, Vec3 from, Vec3 to, double width) {}

	/** The cable with this much either side, pixels: enough to click, tight enough to read as the cable. */
	private static final double OUTLINE_PADDING = 1.0D;

	private static final java.util.Map<List<Long>, net.minecraft.world.phys.shapes.VoxelShape> OUTLINES = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The outline of the cable at {@code pos} (a lone wire or a bundle) built from the pieces its model draws
	 * ({@link CableRoutes#layout}, bridges, steps, stairs and all), so what the crosshair picks (sneak and right click
	 * takes out the strand nearest it, {@code WireBundleBlock#strandAt}) is what is on screen (2026-11-15, the maintainer:
	 * keep it in step with every change to how cable renders). Each piece's box padded a pixel, cut to the block. Null
	 * if no cable is there.
	 */
	@org.jetbrains.annotations.Nullable
	public static net.minecraft.world.phys.shapes.VoxelShape outline(BlockGetter level, BlockPos pos) {
		List<BlockState> strands = Strand.statesAt(level, pos);
		if(strands.isEmpty())
			return null;
		WireLook.Lateral[] offsets = Strand.laterals(level, pos, strands);
		List<Long> key = new ArrayList<>();
		CableRoutes.Drawn[] drawnAll = CableRoutes.layoutAll(level, pos, strands, offsets, true);
		for(int i = 0; i < strands.size(); i++) {
			BlockState state = strands.get(i);
			WireBlock wire = (WireBlock)state.getBlock();
			for(Segment seg : segments(pos, wire, WireBlock.isCeiling(state), drawnAll[i])) {
				double pad = (seg.width() * 16.0D / 2.0D + OUTLINE_PADDING) / 16.0D;
				double[] lo = { Math.min(seg.from().x, seg.to().x) - pos.getX() - pad, Math.min(seg.from().y, seg.to().y) - pos.getY() - pad, Math.min(seg.from().z, seg.to().z) - pos.getZ() - pad };
				double[] hi = { Math.max(seg.from().x, seg.to().x) - pos.getX() + pad, Math.max(seg.from().y, seg.to().y) - pos.getY() + pad, Math.max(seg.from().z, seg.to().z) - pos.getZ() + pad };
				long packed = 0L;
				boolean empty = false;
				for(int a = 0; a < 3; a++) { // Each side in 1/64ths of a block, cut to the block: 7 bits each.
					long l = Math.round(Math.clamp(lo[a], 0.0D, 1.0D) * 64.0D), h = Math.round(Math.clamp(hi[a], 0.0D, 1.0D) * 64.0D);
					empty |= h <= l;
					packed = packed << 14 | l << 7 | h;
				}
				if(!empty)
					key.add(packed);
			}
		}
		if(key.isEmpty())
			return null;
		key.sort(null);
		if(OUTLINES.size() > 4096)
			OUTLINES.clear(); // Bounded: a cache, not a record.
		return OUTLINES.computeIfAbsent(List.copyOf(key), k -> {
			net.minecraft.world.phys.shapes.VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
			for(long packed : k) {
				double[] l = new double[3], h = new double[3];
				for(int a = 2; a >= 0; a--) {
					h[a] = (packed & 127L) / 64.0D;
					l[a] = (packed >> 7 & 127L) / 64.0D;
					packed >>= 14;
				}
				out = net.minecraft.world.phys.shapes.Shapes.or(out, net.minecraft.world.phys.shapes.Shapes.box(l[0], l[1], l[2], h[0], h[1], h[2]));
			}
			return out.optimize();
		});
	}

	/** Every strand's centre line at {@code pos}. */
	public static List<Segment> at(BlockGetter level, BlockPos pos) {
		return at(level, pos, true);
	}

	/**
	 * Every strand's centre line at {@code pos}; {@code bridges} false leaves out where strands are raised over each other
	 * ({@link CableRoutes#bridges}): the crossings as they would be, which the bundle solver keeps as few as it can.
	 */
	public static List<Segment> at(BlockGetter level, BlockPos pos, boolean bridges) {
		List<Segment> out = new ArrayList<>();
		List<BlockState> strands = Strand.statesAt(level, pos);
		WireLook.Lateral[] offsets = Strand.laterals(level, pos, strands);
		CableRoutes.Drawn[] drawnAll = CableRoutes.layoutAll(level, pos, strands, offsets, bridges);
		for(int i = 0; i < strands.size(); i++) {
			BlockState state = strands.get(i);
			WireBlock wire = (WireBlock)state.getBlock();
			CableRoutes.Drawn drawn = drawnAll[i]; // As the model draws it.
			boolean ceiling = WireBlock.isCeiling(state);
			double w = wire.width() / 16.0D;
			int first = out.size();
			Vec3 arriveEnd = null, middle = null;
			WireLook.Look look = wire.look(level, pos, state);
			List<WireLook.Placed> parts = drawn.parts();
			List<Segment> segs = segments(pos, wire, ceiling, drawn);
			for(int k = 0; k < segs.size(); k++) {
					Segment seg = segs.get(k);
					WireLook.Placed placed = parts.get(k); // One line a piece.
					out.add(seg);
					if(placed.part() == WireLook.Part.MIDDLE)
						middle = seg.from();
					Direction arrive = look.arrive();
					if(arrive != null && arrive.getAxis().isHorizontal() && !placed.lifted())
						for(Vec3 e : new Vec3[] { seg.from(), seg.to(), acrossFace(seg, pos, arrive) })
							if(e != null && onFace(e, pos, arrive))
								arriveEnd = e; // Where the cable meets the edge it comes up on (a stub, or a squared one's last piece).
			}
			footOfRun(out, first, wire, state, pos, ceiling, arriveEnd != null ? arriveEnd : middle, w);
		}
		return out;
	}

	/**
	 * A run from below that ends at this block's floor (2026-11-15): the cable turns onto the floor here, drawn as the
	 * middle cube (or the stub to the edge it comes up on) lying on the floor, whose cube reaches down to the floor and
	 * meets the run's top. Its centre line sits half the cable's width up, so the line is carried down to the floor where
	 * the cube already is; without it the checks saw a half-pixel gap that is not on screen.
	 */
	private static boolean onFace(Vec3 p, BlockPos pos, Direction dir) {
		return switch(dir) {
			case NORTH -> Math.abs(p.z - pos.getZ()) < 1.0E-4D;
			case SOUTH -> Math.abs(p.z - pos.getZ() - 1.0D) < 1.0E-4D;
			case WEST -> Math.abs(p.x - pos.getX()) < 1.0E-4D;
			case EAST -> Math.abs(p.x - pos.getX() - 1.0D) < 1.0E-4D;
			default -> false;
		};
	}

	/**
	 * Where {@code seg} passes through this block's {@code dir} face, if it reaches on beyond it (a stub into a beam's
	 * block, up whose face the run comes: 2026-11-15, the lattice leg); else null.
	 */
	@org.jetbrains.annotations.Nullable
	private static Vec3 acrossFace(Segment seg, BlockPos pos, Direction dir) {
		double plane = switch(dir) {
			case NORTH -> pos.getZ();
			case SOUTH -> pos.getZ() + 1.0D;
			case WEST -> pos.getX();
			case EAST -> pos.getX() + 1.0D;
			default -> Double.NaN;
		};
		if(Double.isNaN(plane))
			return null;
		double a = dir.getAxis() == Direction.Axis.Z ? seg.from().z : seg.from().x, b = dir.getAxis() == Direction.Axis.Z ? seg.to().z : seg.to().x;
		if((a - plane) * (b - plane) >= 0.0D)
			return null;
		return seg.from().lerp(seg.to(), (plane - a) / (b - a));
	}

	private static void footOfRun(List<Segment> out, int first, WireBlock wire, BlockState state, BlockPos pos, boolean ceiling, Vec3 from, double w) {
		Direction toRun = ceiling ? Direction.UP : Direction.DOWN;
		if(from == null || !state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(toRun)))
			return;
		double floor = ceiling ? pos.getY() + 1.0D : pos.getY();
		for(int k = first; k < out.size(); k++)
			if(Math.abs(out.get(k).from().y - floor) < 1.0E-4D || Math.abs(out.get(k).to().y - floor) < 1.0E-4D)
				return; // A piece of its own reaches the floor: the run carries on through it.
		if(Math.abs(from.y - floor) > w)
			return; // Not lying on the floor: something else.
		out.add(new Segment(wire, from, new Vec3(from.x, floor, from.z), w));
	}

	/** The centre lines of one strand's pieces as {@code drawn}, one a piece, in order. No level needed. */
	static List<Segment> segments(BlockPos pos, WireBlock wire, boolean ceiling, CableRoutes.Drawn drawn) {
		List<Segment> out = new ArrayList<>(drawn.parts().size());
		double w = wire.width() / 16.0D, h = w / 2.0D;
		for(WireLook.Placed placed : drawn.parts()) {
			Vec3[] line = local(placed.part(), w, h).getFirst();
			double stretch = (placed.hi() - placed.lo()) / 256.0D;
			if(placed.part() == WireLook.Part.JOINT && stretch > 1.0D + 1.0E-6D) // A joint stood up into a column (a bridge's end).
				line = new Vec3[] { new Vec3(0.5D, h / stretch, 0.5D), new Vec3(0.5D, w - h / stretch, 0.5D) };
			WireLook.Affine affine = WireLook.affine(placed, drawn.offset(), drawn.faces(), ceiling);
			out.add(new Segment(wire, world(pos, line[0], placed, ceiling, affine), world(pos, line[1], placed, ceiling, affine), w));
		}
		return out;
	}

	/** A part's centre line(s) as its model draws it facing north, block units. */
	private static List<Vec3[]> local(WireLook.Part part, double w, double h) {
		double lo = 0.5D - h;
		return switch(part) {
			case MIDDLE, JOINT -> line(new Vec3(0.5D, h, 0.5D), new Vec3(0.5D, h, 0.5D));
			case HORIZONTAL, SPAN -> line(new Vec3(0.5D, h, lo), new Vec3(0.5D, h, 0.0D));
			case CORNER -> line(new Vec3(0.5D, h, 0.0D), new Vec3(0.5D, h, 0.0D));
			case VERTICAL, RISER -> line(new Vec3(0.5D, w, 0.5D), new Vec3(0.5D, 1.0D, 0.5D)); // A riser is squashed by its height map.
			case EDGE, DROP -> line(new Vec3(0.5D, h, 0.0D), new Vec3(0.5D, 1.0D, 0.0D)); // Starts on the floor stub: from its centre.
			case EDGE_FULL, POST -> line(new Vec3(0.5D, 0.0D, 0.0D), new Vec3(0.5D, 1.0D, 0.0D));
			case CLIMB_OVERHANG -> line(new Vec3(0.5D, 1.0D + h, h), new Vec3(0.5D, 1.0D + h, -7.0D / 16.0D));
			case UP_OVERHANG -> line(new Vec3(0.5D, 1.0D + h, -h), new Vec3(0.5D, 1.0D + h, lo));
		};
	}

	private static List<Vec3[]> line(Vec3 from, Vec3 to) {
		List<Vec3[]> out = new ArrayList<>(1);
		out.add(new Vec3[] { from, to });
		return out;
	}

	/** Turned to its edge, flipped for a ceiling, moved by the strand's affine, then into the world at {@code pos}. */
	private static Vec3 world(BlockPos pos, Vec3 p, WireLook.Placed placed, boolean ceiling, WireLook.Affine affine) {
		Direction dir = placed.dir();
		double x = p.x, z = p.z;
		switch(dir == null ? Direction.NORTH : dir) {
			case EAST -> { x = 1.0D - p.z; z = p.x; }
			case SOUTH -> { x = 1.0D - p.x; z = 1.0D - p.z; }
			case WEST -> { x = p.z; z = 1.0D - p.x; }
			default -> {}
		}
		double y = ceiling ? 1.0D - placed.height(p.y) : placed.height(p.y); // Raised or squashed first, the right way up.
		return new Vec3(pos.getX() + affine.x(x, y, z), pos.getY() + y, pos.getZ() + affine.z(x, y, z));
	}

	/** Every strand at {@code pos}: its offset, look, pieces and centre line (relative to the block), for /rcdebug. */
	public static List<String> describe(BlockGetter level, BlockPos pos) {
		List<String> out = new ArrayList<>();
		List<BlockState> strands = Strand.statesAt(level, pos);
		WireLook.Lateral[] offsets = Strand.laterals(level, pos, strands);
		if(level instanceof net.minecraft.world.level.Level real && level.getBlockEntity(pos) instanceof com.arrl.radiocraft.common.blockentities.WireBundleBlockEntity bundle)
			out.add("bundle flipX=" + bundle.flipX() + " flipZ=" + bundle.flipZ() + " bend=" + BundleOrientation.bend(real, pos, strands));
		for(int i = 0; i < strands.size(); i++) {
			BlockState state = strands.get(i);
			WireBlock wire = (WireBlock)state.getBlock();
			WireLook.Look look = wire.look(level, pos, state);
			out.add(String.format(Locale.ROOT, "%s offset %s links %s look hug=%s arrive=%s end=%s reach=%d/%d", name(wire), offsets[i],
					WireBlock.connected(state), look.hug(), look.arrive(), look.end(), look.hugReach(), look.arriveReach()));
			out.add("  parts " + Strand.drawn(level, pos, strands, offsets, i) + " faces " + wire.faces(level, pos, offsets[i]));
		}
		for(Segment s : at(level, pos))
			out.add(String.format(Locale.ROOT, "  %s (%.2f %.2f %.2f) to (%.2f %.2f %.2f)", name(s.kind()),
					(s.from().x - pos.getX()) * 16, (s.from().y - pos.getY()) * 16, (s.from().z - pos.getZ()) * 16,
					(s.to().x - pos.getX()) * 16, (s.to().y - pos.getY()) * 16, (s.to().z - pos.getZ()) * 16));
		return out;
	}

	/**
	 * Problems in the box from {@code a} to {@code b}: cable of one kind that does not meet across a face where its
	 * strands link, and two strands whose centre lines come closer than their cables are wide (they run through each
	 * other), with where. Empty when all is well.
	 */
	public static List<String> check(BlockGetter level, BlockPos a, BlockPos b) {
		List<String> out = new ArrayList<>();
		for(BlockPos pos : BlockPos.betweenClosed(a, b)) {
			List<Segment> here = at(level, pos);
			if(here.isEmpty())
				continue;
			// Overlaps: segments of different strands in this block.
			for(int i = 0; i < here.size(); i++)
				for(int j = i + 1; j < here.size(); j++) {
					Segment s = here.get(i), t = here.get(j);
					if(s.kind() == t.kind())
						continue;
					double d = distance(s, t), room = (s.width() + t.width()) / 2.0D;
					if(d < room - 1e-4)
						out.add(String.format(Locale.ROOT, "%s: %s runs through %s (%.2f px apart, %.2f needed)", pos.toShortString(),
								name(s.kind()), name(t.kind()), d * 16.0D, room * 16.0D));
				}
			// Joins: each strand's ends on a face must meet the same kind's ends in the block across it.
			for(Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				List<Segment> there = at(level, next);
				for(BlockState state : Strand.statesAt(level, pos)) {
					WireBlock kind = (WireBlock)state.getBlock();
					if(!state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(dir)) || kind.sameKindState(level, next) == null)
						continue;
					if(kind.conductsAll() || ((WireBlock)kind.sameKindState(level, next).getBlock()).conductsAll())
						continue; // A passthrough draws no cable of its own (2026-11-14): nothing to meet.
					Vec3 mine = endOnFace(here, kind, pos, dir), theirs = endOnFace(there, ((WireBlock)kind.sameKindState(level, next).getBlock()), next, dir.getOpposite());
					if(mine == null || theirs == null)
						continue;
					double gap = mine.distanceTo(theirs);
					if(gap > 0.02D)
						out.add(String.format(Locale.ROOT, "%s %s: %s does not meet the next block's (%.2f px gap)", pos.toShortString(), dir.getName(), name(kind), gap * 16.0D));
				}
			}
		}
		return out;
	}

	/**
	 * How many pairs of strands run through each other in {@code blocks} (each pair counted once a block): what the
	 * bundle solver ({@link BundleOrientation}) keeps as low as it can.
	 */
	public static int crossings(BlockGetter level, Iterable<BlockPos> blocks) {
		int count = 0;
		for(BlockPos pos : blocks) {
			List<Segment> here = at(level, pos, false); // Crossings as they are, not as they are bridged.
			Set<Long> pairs = new HashSet<>();
			for(int i = 0; i < here.size(); i++)
				for(int j = i + 1; j < here.size(); j++) {
					Segment s = here.get(i), t = here.get(j);
					if(s.kind() == t.kind())
						continue;
					if(distance(s, t) < (s.width() + t.width()) / 2.0D - 1e-4) {
						int a = System.identityHashCode(s.kind()), b = System.identityHashCode(t.kind());
						if(pairs.add(((long)Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL)))
							count++;
					}
				}
		}
		return count;
	}

	/** The end of {@code kind}'s centre line nearest the {@code dir} face of the block at {@code pos}, if one is on it. */
	private static Vec3 endOnFace(List<Segment> segments, WireBlock kind, BlockPos pos, Direction dir) {
		Vec3 best = null;
		double bestDist = 0.05D;
		for(Segment s : segments) {
			if(s.kind() != kind)
				continue;
			for(Vec3 p : new Vec3[] { s.from(), s.to() }) {
				double face = switch(dir) {
					case DOWN -> p.y - pos.getY();
					case UP -> pos.getY() + 1 - p.y;
					case NORTH -> p.z - pos.getZ();
					case SOUTH -> pos.getZ() + 1 - p.z;
					case WEST -> p.x - pos.getX();
					case EAST -> pos.getX() + 1 - p.x;
				};
				if(Math.abs(face) < bestDist) {
					bestDist = Math.abs(face);
					best = p;
				}
			}
		}
		return best;
	}

	private static String name(WireBlock kind) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(kind).getPath();
	}

	/** Shortest distance between two segments (a point counts as a segment of no length). */
	static double distance(Segment s, Segment t) {
		Vec3 p1 = s.from(), q1 = s.to(), p2 = t.from(), q2 = t.to();
		Vec3 d1 = q1.subtract(p1), d2 = q2.subtract(p2), r = p1.subtract(p2);
		double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r), sc, tc;
		if(a < 1e-12 && e < 1e-12)
			return p1.distanceTo(p2);
		if(a < 1e-12) {
			sc = 0.0D;
			tc = clamp(f / e);
		}
		else {
			double c = d1.dot(r);
			if(e < 1e-12) {
				tc = 0.0D;
				sc = clamp(-c / a);
			}
			else {
				double bb = d1.dot(d2), denom = a * e - bb * bb;
				sc = denom > 1e-12 ? clamp((bb * f - c * e) / denom) : 0.0D;
				tc = (bb * sc + f) / e;
				if(tc < 0.0D) {
					tc = 0.0D;
					sc = clamp(-c / a);
				}
				else if(tc > 1.0D) {
					tc = 1.0D;
					sc = clamp((bb - c) / a);
				}
			}
		}
		return p1.add(d1.scale(sc)).distanceTo(p2.add(d2.scale(tc)));
	}

	private static double clamp(double v) {
		return Math.max(0.0D, Math.min(1.0D, v));
	}
}
