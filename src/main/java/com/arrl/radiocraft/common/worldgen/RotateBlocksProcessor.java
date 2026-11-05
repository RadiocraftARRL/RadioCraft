package com.arrl.radiocraft.common.worldgen;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.init.RadiocraftStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns RadioCraft's blocks with a rotated structure piece (1.12 step 8, the ham shack in villages). Vanilla turns a
 * block by its {@code Block.rotate}, which RadioCraft's blocks do not override, so in a village house (pieces are
 * turned to face their street, and placed with known shapes, so nothing fixes them up afterwards) a radio would face
 * the wall and a wire point the wrong way. This turns, for blocks in the {@code radiocraft} namespace: every
 * horizontal {@link DirectionProperty} (a radio's facing), the four side connections ({@code north}, {@code east},
 * {@code south}, {@code west} booleans: wires, coax, an antenna's coax stubs) and a wire's {@link WireBlock#HUG}.
 * <p>
 * If those blocks ever get their own {@code rotate}, drop this processor from
 * {@code data/radiocraft/worldgen/processor_list/ham_shack_village.json}, or they would be turned twice. The radio
 * sites ({@link SiteStructure}) are never turned and do not use it.
 */
public final class RotateBlocksProcessor extends StructureProcessor {

	public static final RotateBlocksProcessor INSTANCE = new RotateBlocksProcessor();
	public static final MapCodec<RotateBlocksProcessor> CODEC = MapCodec.unit(INSTANCE);

	private static final Direction[] SIDES = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

	private RotateBlocksProcessor() {}

	@Override
	public @Nullable StructureTemplate.StructureBlockInfo process(@NotNull LevelReader level, @NotNull BlockPos offset, @NotNull BlockPos pos,
																 @NotNull StructureTemplate.StructureBlockInfo original, @NotNull StructureTemplate.StructureBlockInfo info,
																 @NotNull StructurePlaceSettings settings, @Nullable StructureTemplate template) {
		Rotation rotation = settings.getRotation();
		if(rotation == Rotation.NONE || !Radiocraft.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).getNamespace()))
			return info;
		BlockState turned = rotate(info.state(), rotation);
		return turned == info.state() ? info : new StructureTemplate.StructureBlockInfo(info.pos(), turned, info.nbt());
	}

	/** {@code state} turned by {@code rotation} (see the class comment for what is turned). */
	public static BlockState rotate(BlockState state, Rotation rotation) {
		if(rotation == Rotation.NONE)
			return state;
		BlockState out = state;
		for(Property<?> property : state.getProperties())
			if(property instanceof DirectionProperty direction) {
				Direction value = state.getValue(direction);
				Direction rotated = rotation.rotate(value);
				if(value.getAxis().isHorizontal() && direction.getPossibleValues().contains(rotated))
					out = out.setValue(direction, rotated);
			}
		BooleanProperty[] sides = new BooleanProperty[4];
		boolean all = true;
		for(int i = 0; i < 4; i++) {
			Property<?> property = state.getBlock().getStateDefinition().getProperty(SIDES[i].getSerializedName());
			if(property instanceof BooleanProperty side)
				sides[i] = side;
			else
				all = false;
		}
		if(all) {
			boolean[] values = new boolean[4];
			for(int i = 0; i < 4; i++)
				values[i] = state.getValue(sides[i]);
			for(int i = 0; i < 4; i++)
				out = out.setValue(sides[index(rotation.rotate(SIDES[i]))], values[i]);
		}
		if(state.hasProperty(WireBlock.HUG)) {
			Direction hug = state.getValue(WireBlock.HUG).direction();
			if(hug != null)
				out = out.setValue(WireBlock.HUG, WireBlock.Edge.of(rotation.rotate(hug)));
		}
		return out;
	}

	private static int index(Direction side) {
		for(int i = 0; i < 4; i++)
			if(SIDES[i] == side)
				return i;
		throw new IllegalArgumentException(side + " is not a side");
	}

	@Override
	protected @NotNull StructureProcessorType<?> getType() {
		return RadiocraftStructures.ROTATE_BLOCKS.get();
	}
}
