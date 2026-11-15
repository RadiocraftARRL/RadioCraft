package com.arrl.radiocraft.common.blocks.antennas;

import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.network_objects.SatelliteTrackerNetworkObject;
import com.arrl.radiocraft.common.blockentities.SatelliteTrackerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The satellite tracking antenna (2026-11-12): the microwave dish's dish on a powered az/el mount (GeckoLib, drawn by
 * the block entity). Coax to a VHF radio and a power wire to its mount; it follows the satellite its radio is tuned to
 * (or the highest one up) and is locked on once it points at it. Using it says what it is doing.
 */
public class SatelliteTrackerBlock extends VHFAntennaCenterBlock {

	public static final MapCodec<SatelliteTrackerBlock> CODEC = simpleCodec(SatelliteTrackerBlock::new);
	private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

	public SatelliteTrackerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED; // GeckoLib draws it from the block entity.
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new SatelliteTrackerBlockEntity(pos, state);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof SatelliteTrackerBlockEntity tracker) {
			boolean powered = IBENetworks.getObject(level, pos) instanceof SatelliteTrackerNetworkObject object && object.isPowered();
			Component line;
			if(!powered)
				line = Component.translatable("message.radiocraft.satellite_tracker.no_power").withStyle(ChatFormatting.RED);
			else if(tracker.getTracking().isEmpty())
				line = Component.translatable("message.radiocraft.satellite_tracker.stowed").withStyle(ChatFormatting.GRAY);
			else
				line = Component.translatable(tracker.isLocked() ? "message.radiocraft.satellite_tracker.locked" : "message.radiocraft.satellite_tracker.slewing",
						tracker.getTracking(), Math.round(tracker.getAzimuth()), Math.round(tracker.getElevation())).withStyle(tracker.isLocked() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
			player.displayClientMessage(line, true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.satellite_tracker").withStyle(ChatFormatting.GRAY));
	}
}
