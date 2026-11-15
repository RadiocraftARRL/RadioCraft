package com.arrl.radiocraft.common.blocks;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small box with terminals round its sides that sits on the floor or on a wall (2026-11-13: the antenna switch and
 * the power switches). On the floor {@link #FACING} is its terminal side, as it always was. On a wall it is the floor
 * box tipped up against the wall, as a lever's model is ({@code x = 90} and {@code y} by facing in the blockstate):
 * {@link #FACING} then points out of the wall, and the box's own sides map to the world as
 * <ul>
 * <li>its north (the floor box's terminal side when it faces north) to <b>down</b>;</li>
 * <li>its south to <b>up</b>;</li>
 * <li>its east and west to the wall's horizontal sides (east and west turned as north is turned to {@link #FACING});</li>
 * <li>its bottom to the wall behind it, its top out of the wall.</li>
 * </ul>
 * So on a wall the input (the terminal side) is always at the bottom, where a cable comes up the wall to it. The blocks
 * work out their sides in the floor box's frame, facing north on a wall ({@link #frameFacing}), and turn them into world
 * sides with {@link #toWorld}; {@link #toFrame} goes back.
 */
public final class WallMount {

	/** Floor (as it always was) or on a wall: only these two (no ceiling). */
	public static final EnumProperty<AttachFace> FACE = EnumProperty.create("face", AttachFace.class, AttachFace.FLOOR, AttachFace.WALL);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	private WallMount() {}

	public static boolean onWall(BlockState state) {
		return state.hasProperty(FACE) && state.getValue(FACE) == AttachFace.WALL;
	}

	/** The facing the box's sides are worked out from: its own on the floor, north (as the model is) on a wall. */
	public static Direction frameFacing(BlockState state) {
		return onWall(state) ? Direction.NORTH : state.getValue(FACING);
	}

	/** Quarter turns clockwise (seen from above) from north to {@code dir}. */
	private static int turns(Direction dir) {
		return switch(dir) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	private static Direction turn(Direction dir, int quarterTurns) {
		for(int i = 0; i < quarterTurns; i++)
			dir = dir.getClockWise();
		return dir;
	}

	/** The world side of the box's side {@code frameSide} (worked out from {@link #frameFacing}). */
	public static Direction toWorld(BlockState state, Direction frameSide) {
		if(!onWall(state))
			return frameSide;
		Direction out = state.getValue(FACING);
		return switch(frameSide) {
			case NORTH -> Direction.DOWN;
			case SOUTH -> Direction.UP;
			case UP -> out;
			case DOWN -> out.getOpposite();
			default -> turn(frameSide, turns(out)); // East and west, turned with the wall.
		};
	}

	/** The box's side (in the frame of {@link #frameFacing}) that world side {@code worldSide} is. */
	public static Direction toFrame(BlockState state, Direction worldSide) {
		if(!onWall(state))
			return worldSide;
		Direction out = state.getValue(FACING);
		if(worldSide == Direction.DOWN)
			return Direction.NORTH;
		if(worldSide == Direction.UP)
			return Direction.SOUTH;
		if(worldSide == out)
			return Direction.UP;
		if(worldSide == out.getOpposite())
			return Direction.DOWN;
		return turn(worldSide, (4 - turns(out)) % 4);
	}

	/**
	 * The state to place: on the wall clicked (pointing out of it) when a block's side is clicked, else {@code floor}
	 * (the block's own floor placement, made {@link AttachFace#FLOOR}).
	 */
	public static BlockState place(BlockPlaceContext context, BlockState floor) {
		Direction clicked = context.getClickedFace();
		if(clicked.getAxis().isHorizontal())
			return floor.setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
		return floor.setValue(FACE, AttachFace.FLOOR);
	}

	/**
	 * {@code floor} (a shape of the box on the floor facing north, in pixels from {@code x0..z1}) for {@code state}: as it
	 * is turned to its facing on the floor, or tipped up against the wall.
	 */
	public static VoxelShape shape(BlockState state, double x0, double y0, double z0, double x1, double y1, double z1) {
		double[] a, b;
		if(onWall(state)) { // x = 90: (x, y, z) to (x, z, 16 - y).
			a = new double[] {x0, z0, 16.0D - y1};
			b = new double[] {x1, z1, 16.0D - y0};
		}
		else {
			a = new double[] {x0, y0, z0};
			b = new double[] {x1, y1, z1};
		}
		int n = turns(state.getValue(FACING));
		for(int i = 0; i < n; i++) { // y = 90 a turn: (x, z) to (16 - z, x).
			double ax = 16.0D - b[2], bx = 16.0D - a[2];
			double az = a[0], bz = b[0];
			a[0] = ax;
			b[0] = bx;
			a[2] = az;
			b[2] = bz;
		}
		return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
	}

	/** {@link #shape} for every state the block can have, looked up by state. */
	public static VoxelShape cached(java.util.Map<BlockState, VoxelShape> cache, BlockState state, double x0, double y0, double z0, double x1, double y1, double z1) {
		return cache.computeIfAbsent(state, s -> shape(s, x0, y0, z0, x1, y1, z1));
	}
}
