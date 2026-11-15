package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.WireBundleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One cable at a position: a kind of wire (power wire, waterproof wire, plain or dyed coax, or the passthrough) at
 * {@code pos}. A {@link WireBlock} is one strand; a {@link WireBundleBlock} holds up to {@link WireBundleBlock#MAX_STRANDS}
 * of them, each with the state (connections, hugged edge, climb) it would have as a wire block of its own there. The
 * network walk ({@code WireUtils}) moves between strands, so two colours of coax in one bundle stay as far apart as
 * they would in two blocks. A {@code null} kind stands for a device at {@code pos} in the walk's visited set.
 */
public record Strand(BlockPos pos, @Nullable WireBlock kind) {

	/** The walk's marker for a device (network object) at {@code pos}. */
	public static Strand device(BlockPos pos) {
		return new Strand(pos.immutable(), null);
	}

	public Strand {
		pos = pos.immutable();
	}

	/** The strands at {@code pos}: a wire block is itself, a bundle its cables, anything else none. */
	public static List<Strand> at(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(state.getBlock() instanceof WireBlock wire)
			return List.of(new Strand(pos, wire));
		if(state.getBlock() instanceof WireBundleBlock && level.getBlockEntity(pos) instanceof WireBundleBlockEntity bundle)
			return bundle.strands().stream().map(s -> new Strand(pos, (WireBlock)s.getBlock())).toList();
		return List.of();
	}

	/** The strand states at {@code pos} (see {@link #at}): each a state of its kind's block. */
	public static List<BlockState> statesAt(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(state.getBlock() instanceof WireBlock)
			return List.of(state);
		if(state.getBlock() instanceof WireBundleBlock && level.getBlockEntity(pos) instanceof WireBundleBlockEntity bundle)
			return bundle.strands();
		return List.of();
	}

	/** The state of the strand of exactly {@code kind} at {@code pos}, or null if there is none. */
	@Nullable
	public static BlockState stateOf(BlockGetter level, BlockPos pos, WireBlock kind) {
		BlockState state = level.getBlockState(pos);
		if(state.getBlock() == kind)
			return state;
		if(state.getBlock() instanceof WireBundleBlock && level.getBlockEntity(pos) instanceof WireBundleBlockEntity bundle)
			return bundle.strand(kind);
		return null;
	}

	/** The state of this strand, or null if it is gone. */
	@Nullable
	public BlockState state(BlockGetter level) {
		return kind == null ? null : stateOf(level, pos, kind);
	}

	/** What strand {@code i} of {@code strands} at {@code pos} draws: its look's pieces, and its hops and turns ({@link CableRoutes}). */
	public static List<WireLook.Placed> drawn(BlockGetter level, BlockPos pos, List<BlockState> strands, WireLook.Lateral[] offsets, int i) {
		return CableRoutes.drawn(level, pos, strands, offsets, i);
	}

	/** True if there is a cable of any kind at {@code pos} (a wire block or a bundle). */
	public static boolean isCable(BlockState state) {
		return state.getBlock() instanceof WireBlock || state.getBlock() instanceof WireBundleBlock;
	}

	/** True if the cable at {@code pos} hangs from the ceiling (a bundle's strands all hang together). */
	public static boolean isCeilingAt(BlockGetter level, BlockPos pos) {
		List<BlockState> states = statesAt(level, pos);
		return !states.isEmpty() && WireBlock.isCeiling(states.getFirst());
	}

	/**
	 * Where the strands of a bundle are drawn relative to a lone wire, in sixteenths of a block, for the strand states
	 * in display order: side by side with a pixel between, centred ({@link WireLook#layout}), so one cable sits where
	 * it always did and two sit either side of the middle.
	 */
	public static double[] offsets(List<BlockState> strands) {
		double[] widths = new double[strands.size()];
		for(int i = 0; i < widths.length; i++)
			widths[i] = ((WireBlock)strands.get(i).getBlock()).width();
		return WireLook.layout(widths);
	}

	/**
	 * Where the cable {@code kind} would meet at {@code pos} sits laterally (see {@link #offsets}): a lone wire or a
	 * device at 0, a bundle's strand of that kind (or of a kind it runs on as) at its place in the bundle.
	 */
	public static WireLook.Lateral offsetOf(BlockGetter level, BlockPos pos, WireBlock kind) {
		BlockState state = level.getBlockState(pos);
		if(state.getBlock() instanceof WireBlock wire)
			return wire.isSameKind(kind) ? loneOffset(level, pos, state) : WireLook.Lateral.ZERO;
		if(!(state.getBlock() instanceof WireBundleBlock) || !(level.getBlockEntity(pos) instanceof WireBundleBlockEntity bundle))
			return WireLook.Lateral.ZERO;
		List<BlockState> strands = bundle.strands();
		double[] offsets = offsets(strands);
		int fallback = -1;
		for(int i = 0; i < strands.size(); i++) {
			WireBlock there = (WireBlock)strands.get(i).getBlock();
			if(there == kind) {
				fallback = i;
				break;
			}
			if(fallback < 0 && kind.isSameKind(there))
				fallback = i;
		}
		return fallback < 0 ? WireLook.Lateral.ZERO : plus(bundle.lateral(offsets[fallback]), bundleShift(level, pos, strands, offsets));
	}

	/**
	 * How far the cable at {@code pos} (a lone wire or a bundle) is moved over to keep to its walls
	 * ({@link WireBlock#wallShift}, 2026-11-11): a bundle moves as a whole, until its outer strand touches the wall.
	 */
	public static WireLook.Lateral shiftAt(BlockGetter level, BlockPos pos) {
		List<BlockState> strands = statesAt(level, pos);
		if(strands.isEmpty())
			return WireLook.Lateral.ZERO;
		if(strands.size() == 1 && level.getBlockState(pos).getBlock() instanceof WireBlock wire)
			return WireBlock.wallShift(level, pos, strands, wire.width() / 2.0D);
		return bundleShift(level, pos, strands, offsets(strands));
	}

	private static WireLook.Lateral bundleShift(BlockGetter level, BlockPos pos, List<BlockState> strands, double[] offsets) {
		double half = 0.0D;
		for(int i = 0; i < offsets.length; i++)
			half = Math.max(half, Math.abs(offsets[i]) + ((WireBlock)strands.get(i).getBlock()).width() / 2.0D);
		return WireBlock.wallShift(level, pos, strands, half);
	}

	private static WireLook.Lateral plus(WireLook.Lateral a, WireLook.Lateral b) {
		return b.isZero() ? a : new WireLook.Lateral(a.x() + b.x(), a.z() + b.z());
	}

	/**
	 * Where the lone wire in {@code state} at {@code pos} is drawn: in the middle, unless it only joins bundle strands
	 * that all sit at the same place (one block of cable on its own between two bundles, say, where a strand comes up
	 * through a hole and turns), when it keeps their line instead of jinking to the middle and back.
	 */
	public static WireLook.Lateral loneOffset(BlockGetter level, BlockPos pos, BlockState state) {
		if(!(state.getBlock() instanceof WireBlock wire) || !state.hasProperty(WireBlock.NORTH))
			return WireLook.Lateral.ZERO;
		// Kept to its walls (2026-11-11) on each axis that has one; else as below.
		WireLook.Lateral shift = WireBlock.wallShift(level, pos, List.of(state), wire.width() / 2.0D);
		WireLook.Lateral line = betweenBundles(level, pos, state, wire);
		WireLook.Lateral end = endOfBundleRun(level, pos, state, wire);
		if(end != null)
			return end;
		return shift.isZero() ? line : new WireLook.Lateral(shift.x() != 0.0D ? shift.x() : line.x(), shift.z() != 0.0D ? shift.z() : line.z());
	}

	/**
	 * Where a lone wire that is only the last block of a bundle's strand, straight above or below it (a strand come up a
	 * wall out of a bundle, ending on the floor and turning into a device), sits: on the strand's line, so the run comes
	 * straight up into it instead of jinking over to the wire's own corner (2026-11-15, the maintainer's house). Null
	 * if it is not that.
	 */
	@org.jetbrains.annotations.Nullable
	private static WireLook.Lateral endOfBundleRun(BlockGetter level, BlockPos pos, BlockState state, WireBlock wire) {
		net.minecraft.core.Direction only = null;
		for(net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
			if(!state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(dir)))
				continue;
			if(only != null)
				return null;
			only = dir;
		}
		if(only == null || only.getAxis() != net.minecraft.core.Direction.Axis.Y || !(level.getBlockState(pos.relative(only)).getBlock() instanceof WireBundleBlock))
			return null;
		return offsetOf(level, pos.relative(only), wire);
	}

	/** The line of the bundle strands a lone wire only joins (see {@link #loneOffset}), else the middle. */
	private static WireLook.Lateral betweenBundles(BlockGetter level, BlockPos pos, BlockState state, WireBlock wire) {
		WireLook.Lateral found = null;
		int links = 0;
		for(net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
			if(!state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(dir)))
				continue;
			BlockPos next = pos.relative(dir);
			if(!(level.getBlockState(next).getBlock() instanceof WireBundleBlock))
				return WireLook.Lateral.ZERO; // Only between bundles (which never ask a lone wire back, so no loop).
			WireLook.Lateral there = offsetOf(level, next, wire);
			if(found != null && !found.equals(there))
				return WireLook.Lateral.ZERO;
			found = there;
			links++;
		}
		return links >= 2 ? found : WireLook.Lateral.ZERO;
	}

	/**
	 * Where each strand at {@code pos} sits ({@link #offsets}), turned round on either axis as its bundle is
	 * ({@code WireBundleBlockEntity#lateral}); a lone wire at the middle.
	 */
	public static WireLook.Lateral[] laterals(BlockGetter level, BlockPos pos, List<BlockState> strands) {
		double[] offsets = offsets(strands);
		WireLook.Lateral[] out = new WireLook.Lateral[offsets.length];
		WireBundleBlockEntity bundle = level.getBlockState(pos).getBlock() instanceof WireBundleBlock
				&& level.getBlockEntity(pos) instanceof WireBundleBlockEntity entity ? entity : null;
		WireLook.Lateral shift = bundle != null ? bundleShift(level, pos, strands, offsets) : WireLook.Lateral.ZERO;
		for(int i = 0; i < offsets.length; i++)
			out[i] = bundle != null ? plus(bundle.lateral(offsets[i]), shift)
					: offsets.length == 1 ? loneOffset(level, pos, strands.get(i)) : WireLook.Lateral.of(offsets[i]);
		return out;
	}
}
