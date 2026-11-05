package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.RadiocraftServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One radio computer's disk in the world save ({@code data/radiocraft_computer_<id>.dat} in the overworld's data
 * folder), keyed by the computer's id ({@code RadioComputerBlockEntity#getComputerId}) rather than its position, so the
 * files go with the computer when it is broken and placed again (its item keeps the id). Server only; programs reach it
 * through {@link ComputerContext#files()} or the {@code fs.*} requests ({@link ComputerServer}).
 * <p>
 * The caps come from the server config ({@code computer_storage_kb}, {@code computer_max_files}); lowering them never
 * deletes files, it only stops new writes until there is room.
 */
public final class ComputerFileStore extends SavedData {

	public static final String SAVE_PREFIX = "radiocraft_computer_";
	private static final int DEFAULT_STORAGE_KB = 1024, DEFAULT_MAX_FILES = 256;

	private final ComputerStorage storage = new ComputerStorage();

	public static SavedData.Factory<ComputerFileStore> factory() {
		return new SavedData.Factory<>(ComputerFileStore::new, ComputerFileStore::load, null);
	}

	/** The disk of computer {@code id} (made empty the first time). */
	public static ComputerFileStore of(MinecraftServer server, UUID id) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_PREFIX + id);
	}

	/** Bytes each computer may hold (config). */
	public static long capacity() {
		int kb = DEFAULT_STORAGE_KB;
		try {
			kb = RadiocraftServerConfig.COMPUTER_STORAGE_KB.get();
		}
		catch(IllegalStateException | NullPointerException ignored) {} // Config not loaded (unit tests): the default.
		return kb * 1024L;
	}

	/** Most files each computer may hold (config). */
	public static int maxFiles() {
		try {
			return RadiocraftServerConfig.COMPUTER_MAX_FILES.get();
		}
		catch(IllegalStateException | NullPointerException e) {
			return DEFAULT_MAX_FILES;
		}
	}

	/** The files, to read. Change them only through this class, so they are saved. */
	public ComputerStorage storage() {
		return storage;
	}

	/**
	 * Writes {@code name}: checked for its kind ({@link ComputerFileFormats#checked}), then within the caps. Null if
	 * written, else why not.
	 */
	@Nullable
	public ComputerStorage.Problem write(String name, byte[] data) {
		byte[] checked = ComputerFileFormats.checked(name, data);
		if(checked == null)
			return ComputerStorage.Problem.BAD_CONTENTS;
		ComputerStorage.Problem problem = storage.write(name, checked, System.currentTimeMillis(), capacity(), maxFiles());
		if(problem == null)
			setDirty();
		return problem;
	}

	/** Writes a text file. */
	@Nullable
	public ComputerStorage.Problem writeText(String name, String text) {
		return write(name, ComputerStorage.text(text));
	}

	/** Writes an NBT file ({@code .LOG}, {@code .FRQ}, {@code .BOK}, {@code .DAT}). */
	@Nullable
	public ComputerStorage.Problem writeNbt(String name, CompoundTag tag) {
		return write(name, ComputerFileFormats.nbt(tag));
	}

	@Nullable
	public byte[] read(String name) {
		return storage.read(name);
	}

	/** A text file's text, or null if there is none. */
	@Nullable
	public String readText(String name) {
		byte[] data = storage.read(name);
		return data == null ? null : ComputerStorage.text(data);
	}

	/** An NBT file's compound, or null if there is none or it is not NBT. */
	@Nullable
	public CompoundTag readNbt(String name) {
		byte[] data = storage.read(name);
		return data == null ? null : ComputerFileFormats.nbt(data);
	}

	@Nullable
	public ComputerStorage.Problem delete(String name) {
		ComputerStorage.Problem problem = storage.delete(name);
		if(problem == null)
			setDirty();
		return problem;
	}

	@Nullable
	public ComputerStorage.Problem rename(String from, String to) {
		ComputerStorage.Problem problem = storage.rename(from, to);
		if(problem == null)
			setDirty();
		return problem;
	}

	public List<ComputerStorage.FileEntry> list(@Nullable String extension) {
		return storage.list(extension);
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

	public static ComputerFileStore load(CompoundTag tag, HolderLookup.Provider registries) {
		ComputerFileStore store = new ComputerFileStore();
		for(Tag element : tag.getList("files", Tag.TAG_COMPOUND)) {
			CompoundTag file = (CompoundTag)element;
			store.storage.restore(file.getString("name"), file.getByteArray("data"), file.getLong("modified"));
		}
		return store;
	}
}
