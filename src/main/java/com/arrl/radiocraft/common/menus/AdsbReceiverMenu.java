package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.adsb.AdsbReceiverBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The ADS-B receiver's radar screen: power and range. The picture arrives once a second in a {@code CAdsbPacket};
 * no slots.
 */
public class AdsbReceiverMenu extends AbstractContainerMenu implements StationMenu {

	public final AdsbReceiverBlockEntity blockEntity;

	public AdsbReceiverMenu(int id, AdsbReceiverBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.ADSB_RECEIVER.get(), id);
		this.blockEntity = blockEntity;
	}

	public AdsbReceiverMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, AdsbReceiverBlockEntity.class));
	}

	@Override
	public BlockPos getBlockPos() {
		return blockEntity.getBlockPos();
	}

	@Override
	public void handleAction(ServerPlayer player, String action, CompoundTag data) {
		switch(action) {
			case "power" -> blockEntity.togglePower();
			case "range" -> blockEntity.stepRange(data.getInt("step"));
			default -> {
				return;
			}
		}
		blockEntity.sendPicture(player);
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return !blockEntity.isRemoved() && blockEntity.getLevel() != null
				&& stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, blockEntity.getBlockState().getBlock());
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
