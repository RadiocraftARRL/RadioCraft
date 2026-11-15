package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.common.blocks.Strand;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a cable's centre lines are shaped, beyond whether they join (2026-11-14, the maintainer: "Overshooting,
 * unnecessary loops and bends, and randomly sticking out from walls are all things worth checking, as well as how
 * they connect to radios"). Checked on every strand in an area:
 * <ul>
 * <li><b>Doubling back:</b> two pieces of one strand meeting at a sharp angle (under 70 degrees), the V a cable makes
 * when it overshoots a turn and comes back.</li>
 * <li><b>Detours:</b> in each block, a strand's pieces are no longer than the shortest axis-aligned path between the
 * points where it leaves the block (a loop, or a run past a turn and back, is longer), and bend no more than
 * {@link #MAX_BENDS} times.</li>
 * <li><b>Sticking out:</b> a free end (meeting nothing of its own) in a block the strand runs on through, not at a
 * device: a spur off the run. A free end short of a side the strand links to: a gap.</li>
 * <li><b>Radios:</b> where a cable meets a radio it reaches the radio's body (not buried in it), away from its corners,
 * and, when the radio's back faces the cable, into the back.</li>
 * </ul>
 */
final class CableShapes {

	/** Sharper than this between two pieces of one strand (cosine of 70 degrees) is a V. */
	private static final double SHARP = Math.cos(Math.toRadians(70.0D));
	/** Longer than the shortest path by more than this (a pixel) is a detour. */
	private static final double SLACK = 1.0D / 16.0D;
	/** A straight cable, a turn, a climb and a lane change take a few bends in one block; more is a zig-zag. */
	static final int MAX_BENDS = 4;
	/** How deep into a radio's body an end may reach (a pixel and a bit). */
	private static final double BURIED = 1.25D / 16.0D;
	/** How far out toward a face's edge an end may meet a radio, as a share of the half width. */
	private static final double EDGE = 0.8D;

	private CableShapes() {}

	private static boolean together(WireBlock a, WireBlock b) {
		return a == b || a.joins(b);
	}

	/**
	 * How near two pieces' ends must be to meet: a cable's width. The model's arms stop half a width short of the middle
	 * cube on either side of it (WireGeometry's MIDDLE part, a point), and a climb's overhang lies half a width above.
	 */
	private static double tolerance(WireGeometry.Segment s) {
		return s.width() + 0.01D;
	}

	/**
	 * Everything wrong with the strands in {@code a..b} (positions in messages relative to {@code origin}).
	 * {@code radio} is the radio's position or null; {@code radioBack} the side its back is to the cable on, or null
	 * when it was turned.
	 */
	static List<String> check(ServerLevel level, BlockPos a, BlockPos b, BlockPos origin, BlockPos radio, Direction radioBack) {
		Map<BlockPos, List<WireGeometry.Segment>> byBlock = new HashMap<>();
		for(BlockPos p : BlockPos.betweenClosed(a, b)) {
			List<WireGeometry.Segment> here = new ArrayList<>();
			for(WireGeometry.Segment s : WireGeometry.at(level, p))
				if(!s.kind().conductsAll()) // Points too: the middle cube, or an arm squeezed to nothing at a passthrough's face.
					here.add(s);
			if(!here.isEmpty())
				byBlock.put(p.immutable(), here);
		}
		return shapes(byBlock, origin, (s, e, outward, pos) -> freeEnd(level, s, e, outward, pos, origin, radio, radioBack));
	}

	/** What to say of an end that meets nothing of its own kind (null: nothing wrong). */
	interface FreeEnd {
		String at(WireGeometry.Segment s, Vec3 end, Vec3 outward, BlockPos pos);
	}

	/** The checks on centre lines alone, block by block (the V shapes, detours and zig-zags), free ends to {@code free}. */
	static List<String> shapes(Map<BlockPos, List<WireGeometry.Segment>> byBlock, BlockPos origin, FreeEnd free) {
		List<String> out = new ArrayList<>();
		List<WireGeometry.Segment> all = new ArrayList<>();
		Map<WireGeometry.Segment, BlockPos> home = new java.util.IdentityHashMap<>();
		byBlock.forEach((p, here) -> {
			all.addAll(here);
			here.forEach(s -> home.put(s, p));
		});

		// Ends: V shapes, and free ends.
		for(WireGeometry.Segment s : all) {
			if(point(s))
				continue; // No direction to double back from; an arm reaching it meets it.
			for(int end = 0; end < 2; end++) {
				Vec3 e = end == 0 ? s.from() : s.to(), other = end == 0 ? s.to() : s.from();
				Vec3 away = other.subtract(e).normalize();
				double tol = tolerance(s);
				boolean touched = false;
				for(WireGeometry.Segment t : near(byBlock, home.get(s))) {
					if(t == s || !together(s.kind(), t.kind()) || distanceToSegment(e, t.from(), t.to()) >= tol)
						continue;
					touched = true;
					Vec3 joint = e.distanceTo(t.from()) < tol ? t.from() : e.distanceTo(t.to()) < tol ? t.to() : null;
					if(joint == null || point(t))
						continue; // It meets t partway along (a junction), or a point.
					Vec3 tAway = (joint == t.from() ? t.to() : t.from()).subtract(joint).normalize();
					if(away.dot(tAway) > SHARP) {
						out.add(String.format(Locale.ROOT, "%s: %s doubles back on itself (%.0f degrees)", rel(home.get(s), origin), name(s.kind()),
								Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, away.dot(tAway)))))));
					}
				}
				if(!touched) {
					String loose = free.at(s, e, away.scale(-1.0D), home.get(s));
					if(loose != null)
						out.add(loose);
				}
			}
		}

		// Slants and pieces lying on each other (2026-11-14, the maintainer: "right angles only"; "disconnected pieces,
		// bumps and artifacts": two pieces of one cable in the same place flicker).
		for(WireGeometry.Segment s : all) {
			if(point(s))
				continue;
			Vec3 v = s.to().subtract(s.from());
			double len = v.length();
			if(len > 0.02D && Math.max(Math.abs(v.x), Math.max(Math.abs(v.y), Math.abs(v.z))) / len < 0.999D)
				out.add(String.format(Locale.ROOT, "%s: %s slants (%.0f degrees off straight)", rel(home.get(s), origin), name(s.kind()),
						Math.toDegrees(Math.acos(Math.max(Math.abs(v.x), Math.max(Math.abs(v.y), Math.abs(v.z))) / len))));
			for(WireGeometry.Segment t : near(byBlock, home.get(s))) {
				if(t == s || point(t) || t.kind() != s.kind() || System.identityHashCode(t) < System.identityHashCode(s))
					continue;
				double overlap = sharedLength(s, t);
				if(overlap > 0.02D)
					out.add(String.format(Locale.ROOT, "%s: %s lies on itself for %.1f px", rel(home.get(s), origin), name(s.kind()), overlap * 16.0D));
			}
		}

		// Detours and zig-zags, block by block.
		for(Map.Entry<BlockPos, List<WireGeometry.Segment>> entry : byBlock.entrySet()) {
			Map<WireBlock, List<WireGeometry.Segment>> byKind = new HashMap<>();
			for(WireGeometry.Segment s : entry.getValue())
				byKind.computeIfAbsent(s.kind(), k -> new ArrayList<>()).add(s);
			for(Map.Entry<WireBlock, List<WireGeometry.Segment>> kind : byKind.entrySet())
				for(List<WireGeometry.Segment> piece : components(kind.getValue()))
					detour(piece, entry.getKey(), origin, out);
		}
		return dedupe(out);
	}

	private static boolean point(WireGeometry.Segment s) {
		return s.from().distanceTo(s.to()) < 1.0E-4D;
	}

	/** How far two pieces run along the same line (parallel, within a hundredth of a block), blocks; 0 if they do not. */
	private static double sharedLength(WireGeometry.Segment s, WireGeometry.Segment t) {
		Vec3 u = s.to().subtract(s.from()), v = t.to().subtract(t.from());
		double lu = u.length(), lv = v.length();
		if(lu < 1.0E-6D || lv < 1.0E-6D || Math.abs(u.dot(v)) / (lu * lv) < 0.999D)
			return 0.0D;
		if(distanceToSegment(t.from(), s.from(), s.to()) > 0.01D && distanceToSegment(t.to(), s.from(), s.to()) > 0.01D
				&& distanceToSegment(s.from(), t.from(), t.to()) > 0.01D)
			return 0.0D;
		Vec3 dir = u.scale(1.0D / lu);
		// Off the same line: no overlap.
		Vec3 off = t.from().subtract(s.from());
		if(off.subtract(dir.scale(off.dot(dir))).length() > 0.01D)
			return 0.0D;
		double a0 = 0.0D, a1 = lu, b0 = off.dot(dir), b1 = t.to().subtract(s.from()).dot(dir);
		return Math.max(0.0D, Math.min(a1, Math.max(b0, b1)) - Math.max(a0, Math.min(b0, b1)));
	}

	/** The segments in the block and the 26 around it. */
	private static List<WireGeometry.Segment> near(Map<BlockPos, List<WireGeometry.Segment>> byBlock, BlockPos pos) {
		List<WireGeometry.Segment> out = new ArrayList<>();
		for(BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			List<WireGeometry.Segment> here = byBlock.get(p);
			if(here != null)
				out.addAll(here);
		}
		return out;
	}

	/** What is wrong with an end that meets no piece of its own kind, or null. {@code outward} points off the end. */
	private static String freeEnd(ServerLevel level, WireGeometry.Segment s, Vec3 e, Vec3 outward, BlockPos pos, BlockPos origin, BlockPos radio, Direction radioBack) {
		// At a device (or a passthrough): fine, though a radio is checked more closely. A device counts before the solid
		// block it stands on (an end at a radio's foot touches the desk too).
		String solid = null;
		for(BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(near);
			if(state.isAir())
				continue;
			List<Strand> strands = Strand.at(level, near);
			boolean passthrough = strands.stream().anyMatch(st -> st.kind() != null && st.kind().conductsAll());
			if(!strands.isEmpty() && !passthrough)
				continue;
			boolean device = passthrough || level.getBlockEntity(near) != null;
			for(AABB box : state.getShape(level, near).toAabbs()) {
				AABB world = box.move(near);
				// Lying against a device counts as meeting it; a cable lying against a wall or ceiling is fine, so only an end
				// truly inside a solid block is wrong.
				if(device ? !world.inflate(s.width() / 2.0D + 0.02D).contains(e) : !world.deflate(0.01D).contains(e))
					continue;
				if(!device) {
					solid = String.format(Locale.ROOT, "%s: %s ends inside %s", rel(pos, origin), name(s.kind()), BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
					continue;
				}
				if(near.equals(radio) || radio == null && state.getBlock() instanceof com.arrl.radiocraft.common.blocks.radios.RadioBlock) // In a real world, every radio.
					return radio(s, e, world, pos, origin, radioBack);
				return null;
			}
		}
		if(solid != null)
			return solid;
		// Not at a device: which way does it point, and does the strand link that way?
		Direction dir = Direction.getNearest(outward.x, outward.y, outward.z);
		BlockState own = null;
		for(BlockState state : Strand.statesAt(level, pos))
			if(state.getBlock() == s.kind())
				own = state;
		if(own == null)
			return null; // Reaching over from the block below (a climb's overhang).
		int links = 0;
		for(Direction d : Direction.values())
			if(own.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(d)))
				links++;
		double reach = switch(dir.getAxis()) {
			case X -> e.x - pos.getX();
			case Y -> e.y - pos.getY();
			case Z -> e.z - pos.getZ();
		};
		boolean onFace = reach < 0.01D || reach > 0.99D;
		if(onFace)
			return null; // On a face: CableMatrixGameTests' stub check says what is (or is not) across it.
		if(own.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(dir)))
			return String.format(Locale.ROOT, "%s: %s stops short of its %s link", rel(pos, origin), name(s.kind()), dir.getName());
		if(links >= 2)
			return String.format(Locale.ROOT, "%s: %s sticks out %s off the run", rel(pos, origin), name(s.kind()), dir.getName());
		return null; // The end of a run, stopping in its block.
	}

	/** An end meeting a radio's body: reaching it, not buried, off the corners, into the back if it faces the cable. */
	private static String radio(WireGeometry.Segment s, Vec3 e, AABB box, BlockPos pos, BlockPos origin, Direction radioBack) {
		double[] depths = {e.x - box.minX, box.maxX - e.x, e.y - box.minY, box.maxY - e.y, e.z - box.minZ, box.maxZ - e.z};
		Direction[] faces = {Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};
		int best = 0;
		for(int i = 1; i < 6; i++)
			if(Math.abs(depths[i]) < Math.abs(depths[best]))
				best = i;
		Direction face = faces[best];
		for(int i = 0; i < 6; i++)
			if(faces[i] == radioBack && Math.abs(depths[i]) < Math.abs(depths[best]) + 0.02D)
				face = radioBack; // On the back's edge counts as the back.
		String where = rel(pos, origin) + ": " + name(s.kind());
		int at = java.util.Arrays.asList(faces).indexOf(face);
		if(depths[at] > BURIED)
			return String.format(Locale.ROOT, "%s goes %.1f px into the radio", where, depths[at] * 16.0D);
		// A drop from above plugs in partway up the back, not at the radio's foot (2026-11-14).
		Vec3 other = s.from().distanceTo(e) < 1.0E-6D ? s.to() : s.from();
		boolean fromAbove = other.y - e.y > 0.5D * other.distanceTo(e);
		if(fromAbove && e.y - box.minY < 1.0D / 16.0D)
			return where + " runs down the radio to its foot instead of plugging in";
		if(radioBack != null && face != radioBack)
			return where + " meets the radio's " + face.getName() + " side, not its back (" + radioBack.getName() + ")";
		if(face.getAxis().isHorizontal()) {
			Direction across = face.getClockWise();
			double centre = across.getAxis() == Direction.Axis.X ? (box.minX + box.maxX) / 2.0D : (box.minZ + box.maxZ) / 2.0D;
			double half = (across.getAxis() == Direction.Axis.X ? box.getXsize() : box.getZsize()) / 2.0D;
			double off = Math.abs((across.getAxis() == Direction.Axis.X ? e.x : e.z) - centre);
			if(off > EDGE * half)
				return String.format(Locale.ROOT, "%s meets the radio at its corner (%.1f px from the middle of a %.1f px side)", where, off * 16.0D, half * 32.0D);
		}
		return null;
	}

	/** One strand's pieces in one block, split into connected parts. */
	private static List<List<WireGeometry.Segment>> components(List<WireGeometry.Segment> segments) {
		List<List<WireGeometry.Segment>> out = new ArrayList<>();
		List<WireGeometry.Segment> left = new ArrayList<>(segments);
		while(!left.isEmpty()) {
			List<WireGeometry.Segment> part = new ArrayList<>();
			part.add(left.removeFirst());
			boolean grew = true;
			while(grew) {
				grew = false;
				for(int i = left.size() - 1; i >= 0; i--) {
					WireGeometry.Segment t = left.get(i);
					for(WireGeometry.Segment s : part)
						if(touches(s, t)) {
							part.add(left.remove(i));
							grew = true;
							break;
						}
				}
			}
			out.add(part);
		}
		return out;
	}

	private static boolean touches(WireGeometry.Segment s, WireGeometry.Segment t) {
		double tol = Math.max(tolerance(s), tolerance(t));
		return distanceToSegment(s.from(), t.from(), t.to()) < tol || distanceToSegment(s.to(), t.from(), t.to()) < tol
				|| distanceToSegment(t.from(), s.from(), s.to()) < tol || distanceToSegment(t.to(), s.from(), s.to()) < tol;
	}

	/**
	 * A connected piece of one strand in one block: longer than it needs, or bending too often. A piece reaching more
	 * than a pixel into other blocks is let be: an overhang over a wall's top, or a cable going round a radio to its back
	 * (CableRoutes), takes the long way on purpose.
	 */
	private static void detour(List<WireGeometry.Segment> piece, BlockPos pos, BlockPos origin, List<String> out) {
		net.minecraft.world.phys.AABB home = new net.minecraft.world.phys.AABB(pos).inflate(1.0D / 16.0D + 0.01D);
		for(WireGeometry.Segment s : piece)
			if(!home.contains(s.from()) || !home.contains(s.to()))
				return;
		List<Vec3> terminals = new ArrayList<>();
		double length = 0.0D;
		for(WireGeometry.Segment s : piece) {
			length += s.from().distanceTo(s.to());
			for(Vec3 e : new Vec3[] {s.from(), s.to()}) {
				boolean meets = false;
				for(WireGeometry.Segment t : piece)
					if(t != s && distanceToSegment(e, t.from(), t.to()) < tolerance(s))
						meets = true;
				if(!meets)
					terminals.add(e);
			}
		}
		if(terminals.size() < 2)
			return;
		// The shortest axis-aligned tree: from the middle (each axis's median) out to every terminal. Exact for two and
		// three terminals, and for a cross.
		double[] xs = terminals.stream().mapToDouble(v -> v.x).sorted().toArray();
		double[] ys = terminals.stream().mapToDouble(v -> v.y).sorted().toArray();
		double[] zs = terminals.stream().mapToDouble(v -> v.z).sorted().toArray();
		Vec3 middle = new Vec3(xs[xs.length / 2], ys[ys.length / 2], zs[zs.length / 2]);
		double ideal = 0.0D;
		for(Vec3 t : terminals)
			ideal += Math.abs(t.x - middle.x) + Math.abs(t.y - middle.y) + Math.abs(t.z - middle.z);
		if(terminals.size() == 2)
			ideal = Math.abs(terminals.get(0).x - terminals.get(1).x) + Math.abs(terminals.get(0).y - terminals.get(1).y) + Math.abs(terminals.get(0).z - terminals.get(1).z);
		String kind = name(piece.getFirst().kind());
		if(length > ideal + SLACK)
			out.add(String.format(Locale.ROOT, "%s: %s takes a detour (%.1f px of cable for a %.1f px path)", rel(pos, origin), kind, length * 16.0D, ideal * 16.0D));
		if(terminals.size() == 2) {
			// Each piece's end joins the nearest other piece's end (points left out): a bend where they turn.
			List<WireGeometry.Segment> lines = piece.stream().filter(t -> !point(t)).toList();
			java.util.Set<String> joints = new java.util.HashSet<>();
			int bends = 0;
			for(int i = 0; i < lines.size(); i++)
				for(int end = 0; end < 2; end++) {
					WireGeometry.Segment s = lines.get(i);
					Vec3 e = end == 0 ? s.from() : s.to();
					int nearest = -1;
					Vec3 joint = null;
					double best = tolerance(s);
					for(int j = 0; j < lines.size(); j++) {
						if(j == i)
							continue;
						for(Vec3 q : new Vec3[] {lines.get(j).from(), lines.get(j).to()})
							if(e.distanceTo(q) < best) {
								best = e.distanceTo(q);
								nearest = j;
								joint = q;
							}
					}
					if(nearest < 0 || !joints.add(Math.min(i, nearest) + ":" + Math.max(i, nearest)))
						continue;
					Vec3 u = (end == 0 ? s.to() : s.from()).subtract(e).normalize(), v = far(lines.get(nearest), joint).subtract(joint).normalize();
					if(u.dot(v) > -Math.cos(Math.toRadians(10.0D)))
						bends++;
				}
			if(bends > MAX_BENDS)
				out.add(String.format(Locale.ROOT, "%s: %s bends %d times in one block", rel(pos, origin), kind, bends));
		}
	}

	private static Vec3 far(WireGeometry.Segment s, Vec3 joint) {
		return s.from().distanceTo(joint) < s.to().distanceTo(joint) ? s.to() : s.from();
	}

	static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		double t = length < 1.0E-12D ? 0.0D : Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / length));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	private static String rel(BlockPos pos, BlockPos origin) {
		return pos.subtract(origin).toShortString();
	}

	private static String name(WireBlock kind) {
		return kind == null ? "?" : BuiltInRegistries.BLOCK.getKey(kind).getPath();
	}

	private static List<String> dedupe(List<String> in) {
		return new ArrayList<>(new java.util.LinkedHashSet<>(in));
	}
}
