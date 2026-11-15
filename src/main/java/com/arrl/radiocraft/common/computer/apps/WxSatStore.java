package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.radio.satellite.imaging.WxMosaic;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * One radio computer's weather satellite mosaic (2026-11-12, the WXSAT program), in the world save as
 * {@code data/radiocraft_wxsat_<id>.dat} beside its disk, keyed by the computer's id so it goes with the computer.
 * Capped by {@link WxMosaic} (old passes age out). Server only.
 */
public final class WxSatStore extends SavedData {

	public static final String SAVE_PREFIX = "radiocraft_wxsat_";

	private WxMosaic mosaic;
	/** Differs each time the store is loaded, so a version from before a restart never matches one after. */
	private final long session = new java.util.Random().nextInt(1 << 20);

	private WxSatStore(WxMosaic mosaic) {
		this.mosaic = mosaic;
	}

	public static SavedData.Factory<WxSatStore> factory() {
		return new SavedData.Factory<>(() -> new WxSatStore(new WxMosaic(0, 0)), WxSatStore::load, null);
	}

	/** Computer {@code id}'s mosaic (made empty the first time). */
	public static WxSatStore of(MinecraftServer server, UUID id) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_PREFIX + id);
	}

	public WxMosaic mosaic() {
		return mosaic;
	}

	/** Changes whenever the mosaic does (and across restarts). */
	public long version() {
		return session * 1_000_003L + mosaic.version();
	}

	/** Empties it (WXSAT's "clear"). */
	public void clear() {
		mosaic = new WxMosaic(mosaic.stationX(), mosaic.stationZ());
		setDirty();
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		tag.put("mosaic", mosaic.save());
		return tag;
	}

	public static WxSatStore load(CompoundTag tag, HolderLookup.Provider registries) {
		return new WxSatStore(WxMosaic.load(tag.getCompound("mosaic")));
	}
}
