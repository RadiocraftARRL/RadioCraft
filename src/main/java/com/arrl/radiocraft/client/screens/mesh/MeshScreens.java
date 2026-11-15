package com.arrl.radiocraft.client.screens.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last status each fire alarm controller and SCADA master sent ({@code CMeshScreenPacket}), for their screens and
 * the green text drawn on their blocks (2026-11-11). Client only; forgotten after a while without news.
 */
public final class MeshScreens {

	private static final long STALE_MS = 10_000L;

	private record Entry(CompoundTag data, long at) {}

	private static final Map<BlockPos, Entry> LAST = new ConcurrentHashMap<>();

	private MeshScreens() {}

	public static void update(BlockPos pos, CompoundTag data) {
		LAST.put(pos.immutable(), new Entry(data, System.currentTimeMillis()));
		LAST.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().at() > STALE_MS * 6L);
	}

	/** The status of the device at {@code pos}, or null if none came lately. */
	@Nullable
	public static CompoundTag get(BlockPos pos) {
		Entry entry = LAST.get(pos);
		return entry == null || System.currentTimeMillis() - entry.at() > STALE_MS ? null : entry.data();
	}

	/** When the device at {@code pos} was first heard of this time round (for the boot), or -1. */
	public static long since(BlockPos pos) {
		Entry entry = LAST.get(pos);
		return entry == null ? -1L : entry.at();
	}
}
