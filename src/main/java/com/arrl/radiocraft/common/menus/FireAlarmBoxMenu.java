package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The inside of a radio fire alarm box, opened with the fire alarm key (2026-11-11). The lamps, the LINK meter and
 * the plate come from the box as data slots ({@link FireAlarmBoxBlockEntity}); its keys and toggles come back as menu
 * button clicks.
 */
public final class FireAlarmBoxMenu extends AbstractContainerMenu {

	public static final int BUTTON_TEST = 0, BUTTON_RESET = 1, BUTTON_SILENCE = 2, BUTTON_DRILL = 3;

	public final FireAlarmBoxBlockEntity blockEntity;
	private final ContainerLevelAccess access;
	private final ContainerData data;

	public FireAlarmBoxMenu(int id, FireAlarmBoxBlockEntity blockEntity, ContainerData data) {
		super(RadiocraftMenuTypes.FIRE_ALARM_BOX.get(), id);
		this.blockEntity = blockEntity;
		this.data = data;
		this.access = ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos());
		addDataSlots(data);
	}

	public FireAlarmBoxMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
		this(id, MenuUtils.getBlockEntity(inventory, buf, FireAlarmBoxBlockEntity.class), new SimpleContainerData(FireAlarmBoxBlockEntity.DATA_COUNT));
	}

	public boolean flag(int flag) {
		return (data.get(FireAlarmBoxBlockEntity.DATA_FLAGS) & flag) != 0;
	}

	public boolean zone(int zone) {
		return flag(FireAlarmBoxBlockEntity.FLAG_ZONE_1 << (zone - 1));
	}

	/** The LINK meter, 0 to 1. */
	public double link() {
		return data.get(FireAlarmBoxBlockEntity.DATA_LINK) / 100.0D;
	}

	public int box() {
		return data.get(FireAlarmBoxBlockEntity.DATA_BOX);
	}

	/** The frequency as "151.880", or "" with none. */
	public String mhz() {
		int mhz = data.get(FireAlarmBoxBlockEntity.DATA_MHZ), khz = data.get(FireAlarmBoxBlockEntity.DATA_KHZ);
		return mhz <= 0 ? "" : String.format(java.util.Locale.ROOT, "%d.%03d", mhz, khz);
	}

	@Override
	public boolean clickMenuButton(@NotNull Player player, int id) {
		if(player.level() instanceof ServerLevel level && id >= BUTTON_TEST && id <= BUTTON_DRILL) {
			blockEntity.panelButton(level, id);
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return stillValid(access, player, RadiocraftBlocks.FIRE_ALARM_BOX.get());
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
