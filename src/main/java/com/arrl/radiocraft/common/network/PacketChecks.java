package com.arrl.radiocraft.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Checks for positions in serverbound packets (2026-11-09): a block entity lookup at a position in an unloaded chunk
 * loads (or generates) that chunk on the server thread, so a packet naming somewhere far away could force chunks to
 * load anywhere. Packets about a block the player is using check this first.
 */
public final class PacketChecks {

	private PacketChecks() {}

	/** How far from the player a block they work through a screen can be: well past reach, for screens kept open. */
	public static final double MAX_DISTANCE = 64.0D;

	/** True if {@code pos} is loaded and within {@link #MAX_DISTANCE} of {@code player}. */
	public static boolean nearAndLoaded(Player player, BlockPos pos) {
		return player.level().isLoaded(pos) && player.blockPosition().distSqr(pos) <= MAX_DISTANCE * MAX_DISTANCE;
	}
}
