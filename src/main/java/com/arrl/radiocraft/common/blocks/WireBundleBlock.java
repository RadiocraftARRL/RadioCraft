package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.WireFaults;
import com.arrl.radiocraft.common.be_networks.WireUtils;
import com.arrl.radiocraft.common.blockentities.WireBundleBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Several cables in one block: up to {@link #MAX_STRANDS} cables of different kinds (power wires of different colours,
 * plain coax and any colours of dyed coax) laid together, as behind a radio where its power wire and its coax leave
 * by the same route. Strands sit side by side with a pixel between ({@link Strand#offsets}), thin wire and fat coax. It
 * has no item: using a wire item on a wire of another kind lays the second cable in with the first, and a wire item
 * used on a bundle adds to it. Sneak and right click with an empty hand to take out the cable nearest the crosshair
 * (the one in hand again); breaking the block drops them all. Dye recolours the coax strand nearest the crosshair and
 * its run, as on a lone coax.
 * <p>
 * Each strand is kept as the {@link net.minecraft.world.level.block.state.BlockState} it would have as a
 * {@link WireBlock} of its own here ({@link WireBundleBlockEntity}), so every rule for bends, hugging walls, climbing
 * and what it links to is the wire's own, and the network walk treats a strand exactly as it would that lone wire:
 * two colours of coax in one bundle stay apart, a plain coax beside the bundle joins every coax strand in it. The
 * strands are drawn side by side ({@link Strand#offset}) in a fixed order ({@link #rank}), so a bundle that carries on
 * into the next block lines up with it.
 */
public class WireBundleBlock extends Block implements SimpleWaterloggedBlock, EntityBlock {

	/** Most cables in one block. */
	public static final int MAX_STRANDS = 7;

	@SuppressWarnings("this-escape")
	public WireBundleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.WATERLOGGED);
	}

	/**
	 * The order strands are drawn in from the left: power wire, data wire, then coax, plain before the colours in dye order. Fixed
	 * so the same cables in the next block fall in the same places.
	 */
	public static int rank(WireBlock kind) {
		if(kind.conductsAll())
			return 99;
		if(kind.isPower)
			return kind.isWaterproof ? 1 : kind.colour == null ? 0 : 2 + kind.colour.getId();
		if(kind.isData)
			return kind.colour == null ? 18 : 19 + kind.colour.getId();
		return kind.colour == null ? 35 : 36 + kind.colour.getId();
	}

	@Nullable
	public static WireBundleBlockEntity entity(BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof WireBundleBlockEntity bundle ? bundle : null;
	}

	// -------------------- STRANDS IN AND OUT --------------------

	/**
	 * True if a cable of {@code kind} can be laid in with the cable(s) at {@code pos}: a wire block or a bundle there
	 * with room, no strand of that kind yet, and no power wire that would join the new one (two such in one block would
	 * be one wire shorted to itself; dyed wires of different colours keep apart and may share).
	 */
	public static boolean canAdd(BlockGetter level, BlockPos pos, WireBlock kind) {
		if(kind.conductsAll())
			return false;
		BlockState here = level.getBlockState(pos);
		if(!(here.getBlock() instanceof WireBundleBlock) && !(here.getBlock() instanceof WireBlock wire && !wire.conductsAll()))
			return false;
		List<Strand> strands = Strand.at(level, pos);
		if(strands.size() >= MAX_STRANDS)
			return false;
		for(Strand strand : strands) {
			WireBlock there = strand.kind();
			if(there == kind || (kind.isPower && there != null && there.isPower && kind.joins(there)))
				return false;
		}
		return true;
	}

	/**
	 * Lays a cable of {@code kind} in with the cable(s) at {@code pos} (see {@link #canAdd}): a lone wire becomes a
	 * bundle of two. Server side.
	 */
	public static void add(Level level, BlockPos pos, WireBlock kind) {
		Map<WireBlock, Map<BlockPos, Direction>> before = coaxLinks(level, pos);
		BlockState here = level.getBlockState(pos);
		boolean ceiling = Strand.isCeilingAt(level, pos);
		List<BlockState> strands = new ArrayList<>(Strand.statesAt(level, pos));
		BlockState fresh = kind.getBlockState(level, pos, ceiling);
		if(here.getBlock() instanceof WireBlock) {
			// No neighbour or shape updates yet: the strands go in first, or the neighbours would see an empty bundle.
			// WireBlock#onRemove leaves the networks alone when a bundle takes over.
			BlockState bundle = RadiocraftBlocks.WIRE_BUNDLE.get().defaultBlockState()
					.setValue(BlockStateProperties.WATERLOGGED, here.hasProperty(BlockStateProperties.WATERLOGGED) && here.getValue(BlockStateProperties.WATERLOGGED));
			level.setBlock(pos, bundle, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		WireBundleBlockEntity entity = entity(level, pos);
		if(entity == null)
			return;
		strands.add(fresh);
		entity.setStrands(strands);
		changed(level, pos);
		settleCoax(level, pos, before); // A coax already here may now only run past a device beside it.
		kind.strandPlaced(level, pos, fresh);
	}

	/**
	 * Takes the strand of {@code kind} out of the bundle at {@code pos}, splitting its networks; a bundle left with one
	 * cable becomes that wire block again. Returns the strand's state, or null if there was none. Server side.
	 */
	@Nullable
	public static BlockState remove(Level level, BlockPos pos, WireBlock kind) {
		WireBundleBlockEntity entity = entity(level, pos);
		if(entity == null)
			return null;
		Map<WireBlock, Map<BlockPos, Direction>> before = coaxLinks(level, pos);
		BlockState old = entity.remove(kind);
		if(old == null)
			return null;
		kind.strandRemoved(level, pos, old, Set.of(new Strand(pos, kind)));
		List<BlockState> left = entity.strands();
		if(left.size() == 1)
			level.setBlock(pos, left.getFirst(), Block.UPDATE_ALL); // onRemove sees the strand carrying on; WireBlock#onPlace finds its network already whole.
		else
			changed(level, pos);
		settleCoax(level, pos, before); // A coax left here may take the device beside it again.
		return old;
	}

	/** Recolours the coax strand {@code from} at {@code pos} to {@code to}, unless the bundle already has {@code to}. */
	public static void recolour(Level level, BlockPos pos, WireBlock from, WireBlock to) {
		WireBundleBlockEntity entity = entity(level, pos);
		if(entity == null || entity.strand(to) != null)
			return;
		BlockState old = entity.remove(from);
		if(old == null)
			return;
		from.strandRemoved(level, pos, old, Set.of(new Strand(pos, from)));
		BlockState fresh = to.withPropertiesOf(old);
		entity.put(fresh);
		changed(level, pos);
		to.strandPlaced(level, pos, fresh);
	}

	/**
	 * After the strands changed with the block itself staying put: the clients get the new strands and a redraw, the
	 * wires around are refreshed ({@link WireClimbUpdater}) and the six neighbours follow us ({@code updateShape}).
	 */
	static void changed(Level level, BlockPos pos) {
		BundleOrientation.queue(level, pos); // Its strands' bends may have changed: re-solve the run's turns.
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		level.blockUpdated(pos, state.getBlock());
		state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
	}

	/**
	 * The strand nearest to where the player's crosshair hit the block, or null for an empty bundle. Measured to each
	 * strand's centre line as it is drawn ({@link WireGeometry}, 2026-11-13: after the cable running rework the strands'
	 * hit boxes no longer sat where they are drawn, so shift-right click took the wrong one).
	 */
	@Nullable
	public static WireBlock strandAt(BlockGetter level, BlockPos pos, BlockHitResult hit) {
		Vec3 at = hit.getLocation();
		WireBlock best = null;
		double bestDistance = Double.MAX_VALUE;
		for(WireGeometry.Segment segment : WireGeometry.at(level, pos)) {
			double distance = distanceToSegment(at, segment.from(), segment.to());
			if(distance < bestDistance) {
				bestDistance = distance;
				best = segment.kind();
			}
		}
		if(best != null)
			return best;
		List<BlockState> strands = Strand.statesAt(level, pos);
		return strands.isEmpty() ? null : (WireBlock)strands.getFirst().getBlock();
	}

	/** How far {@code p} is from the segment {@code a}-{@code b}. */
	static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		double t = length < 1.0E-12D ? 0.0D : Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / length));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	// -------------------- KEEPING UP WITH THE NEIGHBOURS --------------------

	/** {@link WireBlock#refresh} for every strand. */
	public void refresh(Level level, BlockPos pos) {
		if(level.isClientSide)
			return;
		WireBundleBlockEntity entity = entity(level, pos);
		if(entity == null)
			return;
		Map<WireBlock, Map<BlockPos, Direction>> before = coaxLinks(level, pos);
		boolean changed = false;
		for(BlockState old : entity.strands())
			changed |= ((WireBlock)old.getBlock()).refreshStrand(level, pos, old, entity::put);
		if(changed) {
			changed(level, pos);
			settleCoax(level, pos, before);
		}
	}

	/**
	 * Each coax strand's links at {@code pos} as the network walk follows them ({@link WireUtils#walkLinks}), to be
	 * handed to {@link #settleCoax} after a change.
	 */
	private static Map<WireBlock, Map<BlockPos, Direction>> coaxLinks(Level level, BlockPos pos) {
		Map<WireBlock, Map<BlockPos, Direction>> out = new HashMap<>();
		for(Strand strand : Strand.at(level, pos)) {
			BlockState state = strand.state(level);
			if(strand.kind() != null && strand.kind().isCoax() && state != null)
				out.put(strand.kind(), WireUtils.walkLinks(level, strand, state));
		}
		return out;
	}

	/**
	 * Joins or splits the networks of each coax strand at {@code pos} whose devices changed since {@code before}
	 * ({@link #coaxLinks}) with nothing placed or removed at the device (2026-11-11): a coax in a bundle with other
	 * coax only takes a device beside it while it ends there ({@link WireUtils#passesDevicesBy}), so another cable
	 * laid in, taken out, or carried on from here can give a strand a device or take one away. Strands that are new
	 * here do their own joining ({@link WireBlock#strandPlaced}).
	 */
	private static void settleCoax(Level level, BlockPos pos, Map<WireBlock, Map<BlockPos, Direction>> before) {
		for(Strand strand : Strand.at(level, pos)) {
			WireBlock kind = strand.kind();
			BlockState state = strand.state(level);
			Map<BlockPos, Direction> old = kind == null ? null : before.get(kind);
			if(old == null || state == null || !kind.isCoax())
				continue;
			Map<BlockPos, Direction> now = WireUtils.walkLinks(level, strand, state);
			boolean gained = false, lost = false;
			for(Map.Entry<BlockPos, Direction> link : now.entrySet())
				gained |= !old.containsKey(link.getKey()) && isDevice(level, link.getKey(), link.getValue());
			for(Map.Entry<BlockPos, Direction> link : old.entrySet())
				lost |= !now.containsKey(link.getKey()) && isDevice(level, link.getKey(), link.getValue());
			if(gained)
				WireUtils.mergeNetworks(level, strand, kind.connectionPredicate(), kind.networkSupplier(), kind.networkWires());
			if(lost) {
				Map<BlockPos, Direction> all = new LinkedHashMap<>(old);
				all.putAll(now);
				WireUtils.splitNetworks(level, strand, Set.of(), all, kind.connectionPredicate(), kind.networkWires());
			}
		}
	}

	/** True if a link into {@code pos} (touching its {@code side}) reaches a network device, not a cable. */
	private static boolean isDevice(Level level, BlockPos pos, Direction side) {
		return Strand.at(level, pos).isEmpty() && IBENetworks.getObject(level, WireUtils.linkTarget(level, pos, side)) != null;
	}

	@Override
	public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbourState,
										   @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
		if(state.getValue(BlockStateProperties.WATERLOGGED))
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		WireBundleBlockEntity entity = entity(level, pos);
		if(entity != null && level instanceof Level realLevel && !realLevel.isClientSide) {
			Map<WireBlock, Map<BlockPos, Direction>> before = coaxLinks(realLevel, pos);
			boolean changed = false;
			for(BlockState old : entity.strands()) {
				WireBlock kind = (WireBlock)old.getBlock();
				if(kind.isPower && realLevel instanceof ServerLevel serverLevel)
					WireFaults.queue(serverLevel, pos);
				changed |= entity.put(kind.followNeighbours(level, pos, old));
			}
			if(changed) {
				realLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
				BundleOrientation.queue(realLevel, pos);
				settleCoax(realLevel, pos, before); // A coax carried on from here no longer ends at the device beside it, or now does.
			}
		}
		return state;
	}

	/** Every strand has its wire's hazards (see WireFaults): the power strand's are looked at now and then. */
	@Override
	protected boolean isRandomlyTicking(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
		if(WireFaults.hasPowerWire(level, pos))
			WireFaults.check(level, pos);
	}

	@Override
	protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		BundleOrientation.queue(level, pos); // A new bundle joins the run: its turns, and its neighbours'.
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
		BundleOrientation.queue(level, pos); // The runs either side may have split.
		if(newState.is(this))
			return;
		if(!level.isClientSide) {
			WireBundleBlockEntity entity = entity(level, pos);
			List<BlockState> strands = entity == null ? List.of() : entity.strands();
			// Down to one cable, which carries on as a wire block: its network is untouched (see remove).
			boolean carriesOn = newState.getBlock() instanceof WireBlock && strands.size() == 1 && strands.getFirst().getBlock() == newState.getBlock();
			if(!carriesOn) {
				Set<Strand> gone = new HashSet<>();
				for(BlockState strand : strands)
					gone.add(new Strand(pos, (WireBlock)strand.getBlock()));
				for(BlockState strand : strands)
					((WireBlock)strand.getBlock()).strandRemoved(level, pos, strand, gone);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	// -------------------- THE PLAYER --------------------

	/** A wire item adds a cable; dye recolours the coax strand nearest the crosshair. */
	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
													  @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		if(stack.getItem() instanceof BlockItem item && item.getBlock() instanceof WireBlock kind) {
			if(!canAdd(level, pos, kind))
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			if(!level.isClientSide) {
				add(level, pos, kind);
				level.playSound(null, pos, getSoundType(state, level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
				if(!player.getAbilities().instabuild)
					stack.shrink(1);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		if(stack.getItem() instanceof DyeItem dye) {
			WireBlock kind = strandAt(level, pos, hit);
			WireBlock target = kind != null && kind.isData ? RadiocraftBlocks.COLOURED_DATA_WIRE.get(dye.getDyeColor()).get()
					: RadiocraftBlocks.COLOURED_COAX.get(dye.getDyeColor()).get();
			if(kind == null || !(kind.isCoax() || kind.isData) || kind == target)
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			if(!level.isClientSide) {
				WireBlock.dyeRun(level, new Strand(pos, kind), target, player.isShiftKeyDown());
				level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				if(!player.getAbilities().instabuild)
					stack.shrink(1);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	/** Sneak and right click with an empty hand: the cable nearest the crosshair comes out, back into the hand. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!player.isShiftKeyDown())
			return InteractionResult.PASS;
		WireBlock kind = strandAt(level, pos, hit);
		if(kind == null)
			return InteractionResult.PASS;
		if(!level.isClientSide) {
			BlockState old = remove(level, pos, kind);
			if(old != null) {
				ItemStack stack = new ItemStack(kind);
				if(!player.getAbilities().instabuild && !player.getInventory().add(stack))
					player.drop(stack, false);
				level.playSound(null, pos, getSoundType(state, level, pos, player).getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.2F);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Breaking a bundle gives every cable in it back (no loot table: the drops depend on the block entity). */
	@Override
	public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
		if(!level.isClientSide && !player.isCreative())
			dropStrands(level, pos);
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void onBlockExploded(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Explosion explosion) {
		if(!level.isClientSide)
			dropStrands(level, pos);
		super.onBlockExploded(state, level, pos, explosion);
	}

	private static void dropStrands(Level level, BlockPos pos) {
		for(BlockState strand : Strand.statesAt(level, pos))
			Block.popResource(level, pos, new ItemStack(strand.getBlock()));
	}

	/** Pick block: the first cable. */
	@Override
	public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos, @NotNull BlockState state) {
		List<BlockState> strands = Strand.statesAt(level, pos);
		return strands.isEmpty() ? ItemStack.EMPTY : new ItemStack(strands.getFirst().getBlock());
	}

	// -------------------- LOOK AND FEEL --------------------

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new WireBundleBlockEntity(pos, state);
	}

	@Override
	public @NotNull FluidState getFluidState(@NotNull BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public boolean hasDynamicShape() {
		return true;
	}

	private record ShapeKey(List<VoxelShape> parts) {}

	private static final Map<ShapeKey, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

	/** The outlines of all the strands, each drawn where it sits in the bundle. */
	/**
	 * Skylight passes a cable (unless it is under water), decided without its shape: the shape reads the neighbours,
	 * and the light engine asks from its own thread, where reading a neighbour in a chunk not loaded yet waits on the
	 * server thread, which may be waiting on the light (a hang, as when a radio site generated).
	 */
	@Override
	protected boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
		return state.getFluidState().isEmpty();
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		List<BlockState> strands = Strand.statesAt(level, pos);
		WireLook.Lateral[] offsets = Strand.laterals(level, pos, strands);
		List<VoxelShape> parts = new ArrayList<>(strands.size());
		for(int i = 0; i < strands.size(); i++)
			parts.add(((WireBlock)strands.get(i).getBlock()).shapeFor(level, pos, strands.get(i), offsets[i]));
		if(parts.isEmpty())
			return Shapes.box(0.375D, 0.0D, 0.375D, 0.625D, 0.1875D, 0.625D);
		return SHAPE_CACHE.computeIfAbsent(new ShapeKey(List.copyOf(parts)), key -> {
			VoxelShape out = Shapes.empty();
			for(VoxelShape part : key.parts())
				out = Shapes.or(out, part);
			return out;
		});
	}
}
