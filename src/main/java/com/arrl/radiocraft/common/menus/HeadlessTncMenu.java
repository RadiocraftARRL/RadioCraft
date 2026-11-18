package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.HeadlessTncBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The Headless APRS TNC's panels (2026-11-18): the front's ON/OFF rocker, and on the back the callsign, the SSID wheel
 * and the DIGI and BEACON switches. No slots.
 */
public class HeadlessTncMenu extends AbstractContainerMenu implements StationMenu {

	public final HeadlessTncBlockEntity blockEntity;
	private final ContainerLevelAccess access;

	public HeadlessTncMenu(int id, HeadlessTncBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.HEADLESS_TNC.get(), id);
		this.blockEntity = blockEntity;
		this.access = ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos());
	}

	public HeadlessTncMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, HeadlessTncBlockEntity.class));
	}

	@Override
	public BlockPos getBlockPos() {
		return blockEntity.getBlockPos();
	}

	@Override
	public void handleAction(ServerPlayer player, String action, CompoundTag data) {
		String who = player.getGameProfile().getName();
		switch(action) {
			case "power" -> blockEntity.setOn(data.getBoolean("on"));
			case "callsign" -> {
				int ssid = blockEntity.getSsid();
				if(!TncPacket.sanitizeCallsign(data.getString("callsign")).isEmpty())
					blockEntity.setCallsign(data.getString("callsign"), who);
				else if(!blockEntity.claim(player)) // Empty means "mine".
					player.displayClientMessage(Component.translatable("screen.radiocraft.radio_interface.no_callsign"), true);
				if(!data.getString("callsign").contains("-") && ssid > 0) // A new call keeps the SSID wheel where it was.
					blockEntity.setSsid(ssid, who);
			}
			case "ssid" -> blockEntity.setSsid(data.getInt("ssid"), who);
			case "digipeat" -> blockEntity.setDigipeatEnabled(data.getBoolean("digipeat"));
			case "beacon" -> blockEntity.setBeaconEnabled(data.getBoolean("beacon"));
			default -> {}
		}
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return stillValid(access, player, RadiocraftBlocks.HEADLESS_TNC.get());
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
