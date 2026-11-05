package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.ComputerUploads;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A program's way to the computer's disk from the screen (the {@code fs.*} requests of
 * {@link com.arrl.radiocraft.common.computer.ComputerServer}). Every call answers once, on the client thread, with the
 * server's {@link ComputerResponse}. Writes go up in parts ({@link ComputerUploads}), one after the other; the answer
 * is the last part's.
 */
public final class ComputerFiles {

	/** One file in a listing. */
	public record Info(String name, int size, long modified) {}

	private final ComputerSession session;

	ComputerFiles(ComputerSession session) {
		this.session = session;
	}

	/** Lists the files with {@code extension} (all if empty); read them with {@link #infos}. */
	public void list(String extension, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("ext", extension);
		session.request("fs.list", data, done);
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
		session.request("fs.read", data, done);
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
		session.request("fs.write", data, response -> {
			if(response.ok() && part + 1 < parts)
				writePart(name, bytes, part + 1, parts, done);
			else
				done.accept(response);
		});
	}

	public void delete(String name, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		session.request("fs.delete", data, done);
	}

	public void rename(String from, String to, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("from", from);
		data.putString("to", to);
		session.request("fs.rename", data, done);
	}
}
