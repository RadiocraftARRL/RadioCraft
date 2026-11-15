package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmControllerBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import com.arrl.radiocraft.common.radio.firealarm.ControllerState;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The fire alarm controller's terminal (2026-11-11). What the CRT shows comes in {@code CMeshScreenPacket}s; its keys
 * come back as menu button clicks: SILENCE, clear the head jam, SETUP (frequency and callsign), and a box placed out of
 * service or back in ({@link #serviceButton}).
 */
public final class FireAlarmControllerMenu extends AbstractContainerMenu {

	public static final int BUTTON_SILENCE = 0, BUTTON_CLEAR_JAM = 1, BUTTON_SETUP = 2;
	private static final int SERVICE_FIRST = 1000;

	public final FireAlarmControllerBlockEntity blockEntity;
	private final ContainerLevelAccess access;

	public FireAlarmControllerMenu(int id, FireAlarmControllerBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.FIRE_ALARM_CONTROLLER.get(), id);
		this.blockEntity = blockEntity;
		this.access = ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos());
	}

	public FireAlarmControllerMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
		this(id, MenuUtils.getBlockEntity(inventory, buf, FireAlarmControllerBlockEntity.class));
	}

	public BlockPos getBlockPos() {
		return blockEntity.getBlockPos();
	}

	/** The button that places box {@code box} out of service ({@code out}) or back in. */
	public static int serviceButton(int box, boolean out) {
		return SERVICE_FIRST + box * 2 + (out ? 1 : 0);
	}

	@Override
	public boolean clickMenuButton(@NotNull Player player, int id) {
		if(!(player.level() instanceof ServerLevel level))
			return false;
		switch(id) {
			case BUTTON_SILENCE -> blockEntity.acknowledge();
			case BUTTON_CLEAR_JAM -> {
				if(blockEntity.clearJam())
					level.playSound(null, blockEntity.getBlockPos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4F, 1.6F);
			}
			case BUTTON_SETUP -> {
				if(player instanceof ServerPlayer serverPlayer)
					blockEntity.openSetup(serverPlayer);
			}
			default -> {
				if(id < SERVICE_FIRST)
					return false;
				ControllerState state = blockEntity.state();
				if(state == null)
					return false;
				state.setOutOfService((id - SERVICE_FIRST) / 2, (id - SERVICE_FIRST) % 2 == 1);
				FireAlarms.of(level.getServer()).setDirty();
			}
		}
		return true;
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return stillValid(access, player, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
