package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.blocks.AbstractNetworkBlock;
import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * A radio mesh device's block (2026-11-10): a fire alarm box or controller, a SCADA RTU or master. Coax for an antenna
 * and data wire join it. Using a commercial license on it sets it to the license's frequency (an original or a copy:
 * programming is never blocked); what it does when right-clicked otherwise is each device's own.
 */
public abstract class MeshDeviceBlock extends AbstractNetworkBlock implements IWireConnectable {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	protected MeshDeviceBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public boolean acceptsCoax(@NotNull BlockState state) {
		return true;
	}

	@Override
	public boolean acceptsData(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
	                                                   @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		CommercialLicense license = CommercialLicenseItem.license(stack);
		if(license == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if(level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof MeshDeviceBlockEntity device) {
			if(device.setLicense(serverLevel.getServer(), license)) {
				level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
				player.displayClientMessage(Component.translatable("message.radiocraft.mesh.set", CommercialLicenseRules.mhz(license.outputHz()), license.callsign(),
						addressText(device)).withStyle(ChatFormatting.GREEN), false);
			}
			else
				player.displayClientMessage(Component.translatable("message.radiocraft.mesh.already", CommercialLicenseRules.mhz(license.outputHz())), true);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** How its address reads after setting its frequency: "box 0003", "RTU 2", or nothing for a controller or master. */
	protected Component addressText(MeshDeviceBlockEntity device) {
		return Component.empty();
	}

	/** Sends {@code device}'s status lines to {@code player}, under the block's name. */
	protected static void status(Player player, BlockState state, MeshDeviceBlockEntity device) {
		Component name = device.getCustomName() != null ? device.getCustomName() : state.getBlock().getName();
		player.sendSystemMessage(name.copy().withStyle(ChatFormatting.BOLD));
		for(Component line : device.statusLines())
			player.sendSystemMessage(line);
	}

	/** A shape given facing north (x1..z2 in pixels) for each horizontal facing, turned about the block's centre. */
	public static java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> shapes(double x1, double y1, double z1, double x2, double y2, double z2) {
		java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> out = new java.util.EnumMap<>(Direction.class);
		out.put(Direction.NORTH, Block.box(x1, y1, z1, x2, y2, z2));
		out.put(Direction.SOUTH, Block.box(16.0D - x2, y1, 16.0D - z2, 16.0D - x1, y2, 16.0D - z1));
		out.put(Direction.WEST, Block.box(z1, y1, 16.0D - x2, z2, y2, 16.0D - x1));
		out.put(Direction.EAST, Block.box(16.0D - z2, y1, x1, 16.0D - z1, y2, x2));
		return out;
	}

	/** Which face of the block {@code hit} is on (for setting an RTU's faces). */
	protected static Direction face(BlockHitResult hit) {
		return hit.getDirection();
	}
}
