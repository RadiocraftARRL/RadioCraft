package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The radio computer's screen (1.12 step 5). No slots: the screen asks for everything with
 * {@link com.arrl.radiocraft.common.network.serverbound.SStationPacket} requests, carried out by
 * {@link ComputerServer} and answered with {@link com.arrl.radiocraft.common.network.clientbound.CComputerPacket}s.
 * {@link #monitor} is the monitor it is shown on (the client copies the screen onto it). One data slot carries whether
 * the computer has power ({@link #isPowered}); without it the screen is dark and requests are refused.
 */
public final class RadioComputerMenu extends AbstractContainerMenu implements StationMenu {

	public final RadioComputerBlockEntity computer;
	public final BlockPos monitor;

	/** 1 while the computer has no power (0, the client's start value, shows it powered until told otherwise). */
	private final DataSlot starved;
	/** 1 while this player must type the computer's password (1.12 open-question fixes). */
	private final DataSlot locked;

	public RadioComputerMenu(int id, RadioComputerBlockEntity computer, BlockPos monitor) {
		this(id, computer, monitor, null);
	}

	public RadioComputerMenu(int id, RadioComputerBlockEntity computer, BlockPos monitor, @Nullable Player player) {
		super(RadiocraftMenuTypes.RADIO_COMPUTER.get(), id);
		this.computer = computer;
		this.monitor = monitor.immutable();
		if(computer.getLevel() != null && !computer.getLevel().isClientSide)
			starved = addDataSlot(new DataSlot() {
				@Override
				public int get() {
					return computer.isPowered() ? 0 : 1;
				}

				@Override
				public void set(int value) {}
			});
		else
			starved = addDataSlot(DataSlot.standalone());
		if(player instanceof ServerPlayer serverPlayer)
			locked = addDataSlot(new DataSlot() {
				@Override
				public int get() {
					return computer.isLockedFor(serverPlayer) ? 1 : 0;
				}

				@Override
				public void set(int value) {}
			});
		else
			locked = addDataSlot(DataSlot.standalone());
	}

	/** True while the player at it must type its password first (both sides; the client's copy comes from the server). */
	public boolean isLocked() {
		return locked.get() != 0;
	}

	/** True while the computer runs (both sides; the client's copy comes from the server). */
	public boolean isPowered() {
		return starved.get() == 0;
	}

	public RadioComputerMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, RadioComputerBlockEntity.class), data.readBlockPos());
	}

	@Override
	public BlockPos getBlockPos() {
		return computer.getBlockPos();
	}

	@Override
	public void handleAction(ServerPlayer player, String action, CompoundTag data) {
		if(!stillValid(player)) {
			player.closeContainer();
			return;
		}
		if(!computer.isPowered()) {
			new ComputerReply(player, data.getInt("rid")).error("no_power");
			return;
		}
		ComputerServer.handle(computer, player, action, data);
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return !computer.isRemoved() && player.distanceToSqr(computer.getBlockPos().getCenter()) <= RadioComputerBlockEntity.REACH_SQR;
	}

	/** Closing the screen drops a half-written file and tells the programs. */
	@Override
	public void removed(@NotNull Player player) {
		super.removed(player);
		if(player instanceof ServerPlayer serverPlayer)
			ComputerServer.closed(computer, serverPlayer);
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
