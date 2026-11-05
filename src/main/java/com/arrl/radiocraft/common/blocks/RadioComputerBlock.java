package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.items.FloppyDiskItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The radio computer (1.12 step 5): the PC cabinet that goes under its {@link ComputerMonitorBlock}, with the disk and
 * the floppy drive. Use a floppy disk on it (or on the monitor) to put the disk in the drive; sneak-use with an empty
 * hand to eject it. Using it otherwise sits you at it, if a monitor is on top. A scanner beside it scans and prints.
 * It takes power from the power wire ({@link RadioComputerBlockEntity#isPowered}).
 */
public class RadioComputerBlock extends BaseEntityBlock {

	public static final MapCodec<RadioComputerBlock> CODEC = simpleCodec(RadioComputerBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	@SuppressWarnings("this-escape")
	public RadioComputerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	/** Whoever places it first owns it: they may set its password from the shell. */
	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, @NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if(!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer)
			computer.claim(player);
	}

	/** {@code message.radiocraft.computer.<key>}. */
	static MutableComponent message(String key, Object... args) {
		return Component.translatable(Radiocraft.translationKey("message", "computer." + key), args);
	}

	/** Puts one disk of the floppy {@code stack} into {@code computer}'s drive, telling the player how it went. Server side. */
	static void insert(RadioComputerBlockEntity computer, Player player, ItemStack stack) {
		if(!computer.getFloppy().isEmpty()) {
			player.displayClientMessage(message("drive_full").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		if(computer.insertFloppy(stack)) {
			stack.consume(1, player);
			ComputerApp app = FloppyDiskItem.app(computer.getFloppy());
			player.displayClientMessage(app == null ? message("floppy_blank") : message("floppy_inserted", Component.translatable(app.nameKey())), true);
			player.level().playSound(null, computer.getBlockPos(), SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 0.6F, 1.6F);
		}
	}

	/** Sits {@code player} at {@code computer} on the monitor above it, or says why not. Server side. */
	static void open(RadioComputerBlockEntity computer, ServerPlayer player) {
		if(!computer.hasMonitor()) {
			player.displayClientMessage(message("no_monitor").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		computer.open(player, computer.monitorPos());
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
													   @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		if(!FloppyDiskItem.isFloppy(stack) || !(level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if(!level.isClientSide)
			insert(computer, player, stack);
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!(level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer))
			return InteractionResult.PASS;
		if(!level.isClientSide) {
			if(player.isShiftKeyDown()) {
				ItemStack disk = computer.ejectFloppy();
				if(!disk.isEmpty()) {
					if(!player.getInventory().add(disk))
						player.drop(disk, false);
					level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.6F, 1.6F);
				}
			}
			else if(player instanceof ServerPlayer serverPlayer)
				open(computer, serverPlayer);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
		if(!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer) {
			ItemStack disk = computer.ejectFloppy();
			if(!disk.isEmpty())
				Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, disk);
		}
		if(!state.is(newState.getBlock()))
			AbstractNetworkBlock.leaveNetworks(level, pos); // Off the power wire.
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/** Ticks on the server for the programs that need it (1.12 step 7: APRS beacons, faxes, pictures coming in). */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : RadioComputerBlockEntity::tick;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new RadioComputerBlockEntity(pos, state);
	}
}
