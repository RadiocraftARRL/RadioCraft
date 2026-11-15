package com.arrl.radiocraft.common.blocks.antennas;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The aircraft VHF antenna (2026-11-13, {@link com.arrl.radiocraft.common.radio.antenna.types.vhf.AirbandAntennaType}):
 * a small white fin with a raked whip, one block tall, on the VHF antenna network. Placed on top of a block it stands
 * up ({@link #FACE} floor); placed against the underside of one it hangs down (ceiling), as on an aircraft's belly. Coax
 * goes into its block.
 */
public class AirbandAntennaBlock extends VHFAntennaCenterBlock {

	public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape FLOOR = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 15.0D, 13.0D);
	private static final VoxelShape CEILING = Block.box(3.0D, 1.0D, 3.0D, 13.0D, 16.0D, 13.0D);

	public AirbandAntennaBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(AirbandAntennaBlock::new);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		AttachFace face = context.getClickedFace() == Direction.DOWN ? AttachFace.CEILING : AttachFace.FLOOR;
		return defaultBlockState().setValue(FACE, face).setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	public void appendHoverText(@NotNull net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.@NotNull TooltipContext context,
	                            @NotNull java.util.List<net.minecraft.network.chat.Component> tooltip, @NotNull net.minecraft.world.item.TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(net.minecraft.network.chat.Component.translatable(com.arrl.radiocraft.Radiocraft.translationKey("tooltip", "airband_antenna"))
				.withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return state.getValue(FACE) == AttachFace.CEILING ? CEILING : FLOOR;
	}
}
