package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.datacomponents.FloppyData;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.items.FloppyDiskItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A radio computer's floppy drive as drive {@code A:} (computer batch 3A). What is in it decides what A: is:
 * <ul>
 * <li>nothing: no drive ({@link ComputerStorage.Problem#NO_DISK});</li>
 * <li>a blank floppy: an empty disk; the first file written to it gives the item an id
 * ({@link FloppyData}) and makes it a Data Floppy;</li>
 * <li>a data floppy: its files ({@link FloppyFileStore});</li>
 * <li>a program's disk: write-protected and empty of files (DIR shows the program; INSTALL installs it).</li>
 * </ul>
 * Programs are never copied onto a floppy ({@link FloppyDisk#isProgramName}): they install only from their own disks.
 * Every change updates the item's summary (label, files, bytes used) for its tooltip, and an emptied, unlabelled floppy
 * turns back into a plain blank one (it stacks with the others again). Server side.
 */
public final class FloppyDrive {

	/** What a floppy is, as drive A: sees it. */
	public enum Kind {
		NONE, BLANK, DATA, PROGRAM;

		/** As the {@code floppy} field of the platform's answers has it: "", "blank", "data" (a program's disk is its id). */
		public String word() {
			return switch(this) {
				case NONE -> "";
				case BLANK -> "blank";
				case DATA -> "data";
				case PROGRAM -> "program";
			};
		}
	}

	private FloppyDrive() {}

	public static Kind kind(ItemStack floppy) {
		if(floppy.isEmpty() || !FloppyDiskItem.isFloppy(floppy))
			return Kind.NONE;
		if(FloppyDiskItem.app(floppy) != null)
			return Kind.PROGRAM;
		return floppy.has(RadiocraftDataComponent.FLOPPY_DATA.value()) ? Kind.DATA : Kind.BLANK;
	}

	/** The floppy's data component, or null (a blank disk, a program's disk, no floppy). */
	@Nullable
	public static FloppyData data(ItemStack floppy) {
		return floppy.get(RadiocraftDataComponent.FLOPPY_DATA.value());
	}

	/** The floppy's volume label, "" for none. */
	public static String label(ItemStack floppy) {
		FloppyData data = data(floppy);
		return data == null ? "" : data.label();
	}

	/** Drive A: of {@code computer}, or null if it is not on a server. Each call looks at the drive afresh. */
	@Nullable
	public static ComputerVolume volume(RadioComputerBlockEntity computer) {
		return computer.getLevel() instanceof ServerLevel server ? new Volume(computer, server.getServer()) : null;
	}

	/** The files on {@code floppy} (a copy's view: an empty disk for a blank or program disk). */
	public static ComputerStorage files(MinecraftServer server, ItemStack floppy) {
		FloppyData data = data(floppy);
		return data == null ? new ComputerStorage() : FloppyFileStore.of(server, data.id()).storage();
	}

	/**
	 * Sets the label of the floppy in {@code computer}'s drive ({@link FloppyDisk#cleanLabel}; "" removes it). Null if
	 * done, else why not.
	 */
	@Nullable
	public static ComputerStorage.Problem setLabel(RadioComputerBlockEntity computer, String label) {
		ItemStack floppy = computer.getFloppy();
		Kind kind = kind(floppy);
		if(kind == Kind.NONE)
			return ComputerStorage.Problem.NO_DISK;
		if(kind == Kind.PROGRAM)
			return ComputerStorage.Problem.WRITE_PROTECTED;
		FloppyData data = data(floppy);
		FloppyData changed = data == null ? new FloppyData(UUID.randomUUID(), label, 0, 0L)
				: new FloppyData(data.id(), label, data.files(), data.used());
		keep(computer, floppy, changed);
		return null;
	}

	/** Puts {@code data} on the floppy (or takes it off when empty), and saves the computer that holds it. */
	private static void keep(RadioComputerBlockEntity computer, ItemStack floppy, FloppyData data) {
		if(data.isEmpty())
			floppy.remove(RadiocraftDataComponent.FLOPPY_DATA.value());
		else
			floppy.set(RadiocraftDataComponent.FLOPPY_DATA.value(), data);
		computer.setChanged();
	}

	/** Drive A:, looking at what is in the drive at each call. */
	private static final class Volume implements ComputerVolume {

		private final RadioComputerBlockEntity computer;
		private final MinecraftServer server;

		Volume(RadioComputerBlockEntity computer, MinecraftServer server) {
			this.computer = computer;
			this.server = server;
		}

		/** The files on the disk in the drive, made (with an id on the item) if {@code create} and it has none yet. */
		@Nullable
		private FloppyFileStore store(boolean create) {
			ItemStack floppy = computer.getFloppy();
			FloppyData data = data(floppy);
			if(data == null) {
				if(!create || kind(floppy) != Kind.BLANK)
					return null;
				data = new FloppyData(UUID.randomUUID(), "", 0, 0L);
				floppy.set(RadiocraftDataComponent.FLOPPY_DATA.value(), data);
			}
			return FloppyFileStore.of(server, data.id());
		}

		/** Why the disk in the drive cannot be changed, or null. */
		@Nullable
		private ComputerStorage.Problem unwritable() {
			return switch(kind(computer.getFloppy())) {
				case NONE -> ComputerStorage.Problem.NO_DISK;
				case PROGRAM -> ComputerStorage.Problem.WRITE_PROTECTED;
				default -> null;
			};
		}

		/** After a change: the item's summary follows the files. */
		private void changed(FloppyFileStore store) {
			ItemStack floppy = computer.getFloppy();
			FloppyData data = data(floppy);
			if(data != null)
				keep(computer, floppy, new FloppyData(data.id(), data.label(), store.storage().count(), store.storage().used()));
		}

		@Override
		public ComputerStorage storage() {
			FloppyFileStore store = store(false);
			return store == null ? new ComputerStorage() : store.storage();
		}

		@Override
		@Nullable
		public ComputerStorage.Problem write(String name, byte[] data) {
			ComputerStorage.Problem problem = unwritable();
			if(problem == null && FloppyDisk.isProgramName(name))
				problem = ComputerStorage.Problem.PROGRAM; // Before the item gets an id: a refused program changes nothing.
			if(problem != null)
				return problem;
			FloppyFileStore store = store(true);
			if(store == null)
				return ComputerStorage.Problem.NO_DISK;
			problem = store.write(name, data);
			changed(store);
			return problem;
		}

		@Override
		@Nullable
		public ComputerStorage.Problem delete(String name) {
			ComputerStorage.Problem problem = unwritable();
			if(problem != null)
				return problem;
			FloppyFileStore store = store(false);
			if(store == null)
				return ComputerStorage.isValidName(name) ? ComputerStorage.Problem.NOT_FOUND : ComputerStorage.Problem.BAD_NAME;
			problem = store.delete(name);
			changed(store);
			return problem;
		}

		@Override
		@Nullable
		public ComputerStorage.Problem rename(String from, String to) {
			ComputerStorage.Problem problem = unwritable();
			if(problem != null)
				return problem;
			FloppyFileStore store = store(false);
			if(store == null)
				return ComputerStorage.isValidName(from) ? ComputerStorage.Problem.NOT_FOUND : ComputerStorage.Problem.BAD_NAME;
			return store.rename(from, to);
		}

		@Override
		public long totalBytes() {
			return kind(computer.getFloppy()) == Kind.PROGRAM ? 0L : FloppyDisk.CAPACITY;
		}

		@Override
		public int fileLimit() {
			return FloppyDisk.MAX_FILES;
		}

		@Override
		public String label() {
			return FloppyDrive.label(computer.getFloppy());
		}

		@Override
		public char drive() {
			return DrivePath.FLOPPY;
		}
	}
}
