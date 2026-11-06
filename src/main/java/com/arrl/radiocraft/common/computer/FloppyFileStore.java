package com.arrl.radiocraft.common.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One data floppy's files in the world save ({@code data/radiocraft_floppy_<id>.dat} in the overworld's data folder),
 * named by the id in the floppy item's {@code radiocraft:floppy_data} component (computer batch 3A). The files follow
 * the item from computer to computer, the way a radio computer's own disk ({@link ComputerFileStore}) follows its
 * item; keeping them here rather than in the item itself keeps a chest or inventory of full floppies small enough for
 * the game's packets. Caps and rules: {@link FloppyDisk}. Reach it through {@link FloppyDrive#volume}, which also
 * keeps the item's summary up to date.
 */
public final class FloppyFileStore extends SavedData {

	public static final String SAVE_PREFIX = "radiocraft_floppy_";

	private final ComputerStorage storage = new ComputerStorage();

	public static SavedData.Factory<FloppyFileStore> factory() {
		return new SavedData.Factory<>(FloppyFileStore::new, FloppyFileStore::load, null);
	}

	/** The files of floppy {@code id} (made empty the first time). */
	public static FloppyFileStore of(MinecraftServer server, UUID id) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_PREFIX + id);
	}

	/** The files, to read. */
	public ComputerStorage storage() {
		return storage;
	}

	/** Writes {@code name} (checked for its kind, then within the floppy's caps); null if written. */
	@Nullable
	public ComputerStorage.Problem write(String name, byte[] data) {
		byte[] checked = ComputerFileFormats.checked(name, data);
		if(checked == null)
			return ComputerStorage.Problem.BAD_CONTENTS;
		ComputerStorage.Problem problem = FloppyDisk.write(storage, name, checked, System.currentTimeMillis());
		if(problem == null)
			setDirty();
		return problem;
	}

	@Nullable
	public ComputerStorage.Problem delete(String name) {
		ComputerStorage.Problem problem = storage.delete(name);
		if(problem == null)
			setDirty();
		return problem;
	}

	/** Renames {@code from} to {@code to}; a program's name is refused as on a write. */
	@Nullable
	public ComputerStorage.Problem rename(String from, String to) {
		if(FloppyDisk.isProgramName(to))
			return ComputerStorage.Problem.PROGRAM;
		ComputerStorage.Problem problem = storage.rename(from, to);
		if(problem == null)
			setDirty();
		return problem;
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag files = new ListTag();
		for(ComputerStorage.FileEntry entry : storage.all()) {
			CompoundTag file = new CompoundTag();
			file.putString("name", entry.name());
			file.putByteArray("data", entry.data());
			file.putLong("modified", entry.modified());
			files.add(file);
		}
		tag.put("files", files);
		return tag;
	}

	public static FloppyFileStore load(CompoundTag tag, HolderLookup.Provider registries) {
		FloppyFileStore store = new FloppyFileStore();
		for(Tag element : tag.getList("files", Tag.TAG_COMPOUND)) {
			CompoundTag file = (CompoundTag)element;
			store.storage.restore(file.getString("name"), file.getByteArray("data"), file.getLong("modified"));
		}
		return store;
	}
}
