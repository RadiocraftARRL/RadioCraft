package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.common.blocks.WireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A device mounted on a wall that data wire runs to (2026-11-11): the fire alarm box, the pull station and the panic
 * button. Data wire is drawn meeting it cleanly: a run up or down the same wall goes straight on into it instead of
 * turning out to the block's middle, and a run up the wall beside it drops its stub ({@link WireBlock}). The device
 * draws the last bit of cable itself, in its own model, from where the wire stops to its backplate: which pieces show is
 * in its block state ({@link #WIRE_UP}, {@link #WIRE_DOWN}, {@link #WIRE_CW}, {@link #WIRE_CCW}), kept up to date from
 * its neighbours ({@link #wires}).
 */
public interface WallWired {

	/** The wall the device is on, as a direction from it; null when it is not on a wall (a panic button on a floor). */
	@Nullable
	Direction wallBehind(BlockState state);

	/** Data wire comes down from above (or, on a floor or ceiling, in from the front). */
	BooleanProperty WIRE_UP = BooleanProperty.create("wire_up");
	/** Data wire comes up from below (or, on a floor or ceiling, in from the back). */
	BooleanProperty WIRE_DOWN = BooleanProperty.create("wire_down");
	/** Data wire comes in from the side that is clockwise of the device's facing (its model's +x side). */
	EnumProperty<Side> WIRE_CW = EnumProperty.create("wire_cw", Side.class);
	/** And from the other side. */
	EnumProperty<Side> WIRE_CCW = EnumProperty.create("wire_ccw", Side.class);

	/** How a wire beside the device meets it: none, running up the same wall, or along the floor. */
	enum Side implements StringRepresentable {
		NONE, WALL, FLOOR;

		@Override
		public @NotNull String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** True if the block at {@code pos} is data wire linked towards {@code towards}. */
	static boolean dataWireTowards(BlockGetter level, BlockPos pos, Direction towards) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof WireBlock wire && wire.isData && state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(towards));
	}

	/** True if the data wire at {@code pos} runs up or comes up the edge on side {@code wall}. */
	static boolean onWall(BlockGetter level, BlockPos pos, Direction wall) {
		BlockState state = level.getBlockState(pos);
		if(!(state.getBlock() instanceof WireBlock) || !state.hasProperty(WireBlock.HUG))
			return false;
		if(WireBlock.runEdge(state) == wall) // HUG alone is only the wall a flat run keeps to (2026-11-11).
			return true;
		BlockState below = level.getBlockState(pos.below());
		return state.getValue(WireBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN)) && below.getBlock() instanceof WireBlock
				&& WireBlock.runEdge(below) == wall;
	}

	/**
	 * {@code state} with its wire pieces set from the data wire around it: on a wall ({@code wall}, with {@code cw} the
	 * world side its model's +x side faces), wire above and below and each side; on a floor or ceiling ({@code wall}
	 * null), {@code front}, {@code back}, {@code cw} and {@code ccw} are the world sides its model's four sides face.
	 */
	static BlockState wires(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction wall, Direction front, Direction cw) {
		Direction ccw = cw.getOpposite();
		if(wall != null) {
			state = state.setValue(WIRE_UP, dataWireTowards(level, pos.above(), Direction.DOWN))
					.setValue(WIRE_DOWN, dataWireTowards(level, pos.below(), Direction.UP));
			return state.setValue(WIRE_CW, side(level, pos.relative(cw), cw.getOpposite(), wall))
					.setValue(WIRE_CCW, side(level, pos.relative(ccw), ccw.getOpposite(), wall));
		}
		Direction back = front.getOpposite();
		return state.setValue(WIRE_UP, dataWireTowards(level, pos.relative(front), back))
				.setValue(WIRE_DOWN, dataWireTowards(level, pos.relative(back), front))
				.setValue(WIRE_CW, dataWireTowards(level, pos.relative(cw), ccw) ? Side.FLOOR : Side.NONE)
				.setValue(WIRE_CCW, dataWireTowards(level, pos.relative(ccw), cw) ? Side.FLOOR : Side.NONE);
	}

	private static Side side(BlockGetter level, BlockPos at, Direction towards, Direction wall) {
		if(!dataWireTowards(level, at, towards))
			return Side.NONE;
		return onWall(level, at, wall) ? Side.WALL : Side.FLOOR;
	}
}
