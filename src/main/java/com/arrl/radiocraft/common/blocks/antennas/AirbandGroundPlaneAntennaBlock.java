package com.arrl.radiocraft.common.blocks.antennas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The airband ground plane (2026-11-13, from the maintainer's photo): a ground station's antenna for the aircraft band,
 * two blocks tall, a zinc mast with four radials drooping from a clamp and a white whip on top. Matched like the
 * aircraft antenna ({@link com.arrl.radiocraft.common.radio.antenna.types.vhf.AirbandAntennaType}) and, up on its mast,
 * reaching a little further. Coax goes to the lower half.
 */
public class AirbandGroundPlaneAntennaBlock extends DoubleVHFAntennaBlock {

	/** The mast; the radials reach out further but are thin enough to walk under. */
	public static final VoxelShape SHAPE = Block.box(6.5D, 0.0D, 6.5D, 9.5D, 16.0D, 9.5D);

	public AirbandGroundPlaneAntennaBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.airband_ground_plane"));
	}
}
