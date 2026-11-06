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
public final class ComputerFileStore extends SavedData implements ComputerVolume {

	public static final String SAVE_PREFIX = "radiocraft_computer_";
	private static final int DEFAULT_STORAGE_KB = 1024, DEFAULT_MAX_FILES = 256;

	private final ComputerStorage storage = new ComputerStorage();
	/** Who placed the computer first (may set its password), or null if nobody is known yet. */
	@Nullable
	private UUID owner;
	/** The password's hash ({@link ComputerLock#hash}), or "" for none. */
	private String passwordHash = "";
	/** SETUP's settings (launcher view, colour scheme, name, clock): they follow the disk like the password. */
	private ComputerSettings settings = ComputerSettings.DEFAULT;

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
	@Override
	public ComputerStorage storage() {
		return storage;
	}

	/**
	 * Writes {@code name}: checked for its kind ({@link ComputerFileFormats#checked}), then within the caps. Null if
	 * written, else why not.
	 */
	@Override
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

	@Override
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

	@Override
	@Nullable
	public ComputerStorage.Problem delete(String name) {
		ComputerStorage.Problem problem = storage.delete(name);
		if(problem == null)
			setDirty();
		return problem;
	}

	@Override
	@Nullable
	public ComputerStorage.Problem rename(String from, String to) {
		ComputerStorage.Problem problem = storage.rename(from, to);
		if(problem == null)
			setDirty();
		return problem;
	}

	@Override
	public List<ComputerStorage.FileEntry> list(@Nullable String extension) {
		return storage.list(extension);
	}

	@Override
	public long totalBytes() {
		return capacity();
	}

	@Override
	public int fileLimit() {
		return maxFiles();
	}

	/** C:'s label is the computer's name (SETUP). */
	@Override
	public String label() {
		return settings.name();
	}

	@Override
	public char drive() {
		return DrivePath.COMPUTER;
	}

	// -------------------- OWNER AND PASSWORD --------------------

	@Nullable
	public UUID owner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		setDirty();
	}

	public boolean hasPassword() {
		return !passwordHash.isEmpty();
	}

	/** True if {@code password} opens computer {@code id} (always, with no password set). */
	public boolean checkPassword(UUID id, String password) {
		return ComputerLock.matches(id, passwordHash, password);
	}

	/** Sets the password of computer {@code id}; "" removes it. */
	public void setPassword(UUID id, String password) {
		passwordHash = ComputerLock.hash(id, password);
		setDirty();
	}

	// -------------------- SETUP --------------------

	public ComputerSettings settings() {
		return settings;
	}

	public void setSettings(ComputerSettings settings) {
		this.settings = settings == null ? ComputerSettings.DEFAULT : settings;
		setDirty();
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
		if(owner != null)
			tag.putUUID("owner", owner);
		if(!passwordHash.isEmpty())
			tag.putString("password", passwordHash);
		if(!settings.equals(ComputerSettings.DEFAULT))
			tag.put("settings", settings.toTag());
		return tag;
	}

	public static ComputerFileStore load(CompoundTag tag, HolderLookup.Provider registries) {
		ComputerFileStore store = new ComputerFileStore();
		for(Tag element : tag.getList("files", Tag.TAG_COMPOUND)) {
			CompoundTag file = (CompoundTag)element;
			store.storage.restore(file.getString("name"), file.getByteArray("data"), file.getLong("modified"));
		}
		store.owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		store.passwordHash = tag.getString("password");
		store.settings = ComputerSettings.fromTag(tag.getCompound("settings"));
		return store;
	}
}
