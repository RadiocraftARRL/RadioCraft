package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The SCADA master's console (2026-11-11). What the CRT shows comes in {@code CMeshScreenPacket}s; the POLL SEC switch and
 * the LOCAL I/O toggles come back as menu button clicks: {@link #pollButton} and {@link #faceButton}.
 */
public final class ScadaMasterMenu extends AbstractContainerMenu {

	private static final int POLL_FIRST = 1, FACE_FIRST = 10;
	/** Opens the master's setup (frequency and callsign). */
	public static final int BUTTON_SETUP = 40;

	public final ScadaMasterBlockEntity blockEntity;
	private final ContainerLevelAccess access;

	public ScadaMasterMenu(int id, ScadaMasterBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.SCADA_MASTER.get(), id);
		this.blockEntity = blockEntity;
		this.access = ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos());
	}

	public ScadaMasterMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
		this(id, MenuUtils.getBlockEntity(inventory, buf, ScadaMasterBlockEntity.class));
	}

	public BlockPos getBlockPos() {
		return blockEntity.getBlockPos();
	}

	/** The button id that sets the poll to {@code seconds} (1 to 3). */
	public static int pollButton(int seconds) {
		return POLL_FIRST + seconds - ScadaRules.MIN_POLL;
	}

	/** The button id that sets the master's face {@code face} (3D data value) to {@code mode}. */
	public static int faceButton(int face, Scada.FaceMode mode) {
		return FACE_FIRST + face * 3 + mode.ordinal();
	}

	@Override
	public boolean clickMenuButton(@NotNull Player player, int id) {
		if(!(player.level() instanceof ServerLevel level))
			return false;
		Scada scada = Scada.of(level.getServer());
		Scada.Master master = blockEntity.master(level.getServer());
		if(id >= POLL_FIRST && id < POLL_FIRST + ScadaRules.MAX_POLL) {
			master.pollSeconds = ScadaRules.MIN_POLL + id - POLL_FIRST;
			scada.changed();
			return true;
		}
		if(id == BUTTON_SETUP) {
			if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
				blockEntity.openSetup(serverPlayer);
			return true;
		}
		if(id >= FACE_FIRST && id < FACE_FIRST + 18) {
			int face = (id - FACE_FIRST) / 3;
			master.modes[face] = Scada.FaceMode.values()[(id - FACE_FIRST) % 3];
			scada.changed();
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return stillValid(access, player, RadiocraftBlocks.SCADA_MASTER.get());
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
