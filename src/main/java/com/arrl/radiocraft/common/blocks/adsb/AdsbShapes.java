package com.arrl.radiocraft.common.blocks.adsb;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Shapes for the ADS-B blocks. */
final class AdsbShapes {

	private AdsbShapes() {}

	/** One box ({x0, y0, z0, x1, y1, z1} in pixels) facing north, turned for each facing. */
	static Map<Direction, VoxelShape> turned(double[] b) {
		Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
		out.put(Direction.NORTH, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
		out.put(Direction.EAST, Block.box(16.0D - b[5], b[1], b[0], 16.0D - b[2], b[4], b[3]));
		out.put(Direction.SOUTH, Block.box(16.0D - b[3], b[1], 16.0D - b[5], 16.0D - b[0], b[4], 16.0D - b[2]));
		out.put(Direction.WEST, Block.box(b[2], b[1], 16.0D - b[3], b[5], b[4], 16.0D - b[0]));
		return out;
	}
}
