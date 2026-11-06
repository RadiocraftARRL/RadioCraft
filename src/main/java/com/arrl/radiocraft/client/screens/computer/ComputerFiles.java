package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.DrivePath;
import com.arrl.radiocraft.common.computer.ComputerUploads;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A program's way to one of the computer's drives from the screen (the {@code fs.*} requests of
 * {@link com.arrl.radiocraft.common.computer.ComputerServer}): {@link ComputerSession#files()} is C:, the computer's
 * disk, and {@link ComputerSession#floppyFiles()} is A:, the floppy in its drive (computer batch 3A; every request then
 * fails with {@code no_disk} while the drive is empty, and writes with {@code write_protected} on a program's disk and
 * {@code program} for a program's name). Every call answers once, on the client thread, with the server's
 * {@link ComputerResponse}. Writes go up in parts ({@link ComputerUploads}), one after the other; the answer is the
 * last part's.
 */
public final class ComputerFiles {

	/** One file in a listing. */
	public record Info(String name, int size, long modified) {}

	private final ComputerSession session;
	/** {@link DrivePath#COMPUTER} or {@link DrivePath#FLOPPY}. */
	private final char drive;

	ComputerFiles(ComputerSession session, char drive) {
		this.session = session;
		this.drive = drive;
	}

	/** This drive's letter. */
	public char drive() {
		return drive;
	}

	/** {@code data} with this drive named in it (C: is the default, so only A: says so). */
	private CompoundTag on(CompoundTag data) {
		if(drive == DrivePath.FLOPPY)
			data.putString("drive", "A");
		return data;
	}

	/** Lists the files with {@code extension} (all if empty); read them with {@link #infos}. */
	public void list(String extension, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("ext", extension);
		session.request("fs.list", on(data), done);
	}

	/** The files in an {@link #list} answer. */
	public static List<Info> infos(ComputerResponse response) {
		List<Info> out = new ArrayList<>();
		ListTag files = response.data().getList("files", Tag.TAG_COMPOUND);
		for(int i = 0; i < files.size(); i++) {
			CompoundTag file = files.getCompound(i);
			out.add(new Info(file.getString("name"), file.getInt("size"), file.getLong("modified")));
		}
		return out;
	}

	/** Reads {@code name}; its bytes are {@code response.data().getByteArray("data")}. */
	public void read(String name, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		session.request("fs.read", on(data), done);
	}

	/** Reads a text file; {@code done} gets its text, or null and the answer if it failed. */
	public void readText(String name, java.util.function.BiConsumer<String, ComputerResponse> done) {
		read(name, response -> done.accept(response.ok() ? ComputerStorage.text(response.data().getByteArray("data")) : null, response));
	}

	/** Writes (creates or replaces) {@code name} with {@code bytes}. */
	public void write(String name, byte[] bytes, Consumer<ComputerResponse> done) {
		writePart(name, bytes, 0, ComputerUploads.parts(bytes.length), done);
	}

	public void writeText(String name, String text, Consumer<ComputerResponse> done) {
		write(name, ComputerStorage.text(text), done);
	}

	private void writePart(String name, byte[] bytes, int part, int parts, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		data.putInt("part", part);
		data.putInt("parts", parts);
		data.putByteArray("data", ComputerUploads.part(bytes, part));
		session.request("fs.write", on(data), response -> {
			if(response.ok() && part + 1 < parts)
				writePart(name, bytes, part + 1, parts, done);
			else
				done.accept(response);
		});
	}

	public void delete(String name, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		session.request("fs.delete", on(data), done);
	}

	public void rename(String from, String to, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("from", from);
		data.putString("to", to);
		session.request("fs.rename", on(data), done);
	}

	/**
	 * Copies {@code from} to {@code to} on the server: paths such as {@code A:NOTES.TXT} or {@code NOTES.TXT} (this
	 * drive's), {@code to} may be a drive alone ({@code A:}). Refused if {@code to} exists unless {@code overwrite}.
	 */
	public void copy(String from, String to, boolean overwrite, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("from", path(from));
		data.putString("to", path(to));
		data.putBoolean("overwrite", overwrite);
		session.request("fs.copy", data, done);
	}

	/** {@code name} with this drive's letter if it has none. */
	private String path(String name) {
		return name.length() >= 2 && name.charAt(1) == ':' ? name : drive + ":" + name;
	}

	/** Sets the floppy's volume label ("" removes it); the answer says what is in the drive now. */
	public void label(String label, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("label", label);
		session.request("fs.label", data, done);
	}
}
