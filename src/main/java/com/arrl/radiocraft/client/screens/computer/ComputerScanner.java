package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerServer;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.function.Consumer;

/**
 * A program's way to the scanner beside the computer, from the screen (the {@code scan.*} and {@code print.*}
 * requests). Without a scanner every call answers with the error "no_scanner". Prints land in the scanner's output
 * tray.
 */
public final class ComputerScanner {

	private final ComputerSession session;

	ComputerScanner(ComputerSession session) {
		this.session = session;
	}

	/**
	 * What is on the glass: {@code kind} "image" (with {@code image}, the map colour ids) or "book" (with
	 * {@code pages}, a list of strings), and {@code title}.
	 */
	public void read(Consumer<ComputerResponse> done) {
		session.request("scan.read", done);
	}

	/** Scans the glass into a file named from {@code name} ({@code NAME.IMG} or {@code NAME.TXT}); the answer's {@code name} is the file's. */
	public void save(String name, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		session.request("scan.save", data, done);
	}

	/** Prints the picture file {@code name} ({@code .IMG}) on the tray's photo paper or map. */
	public void printImage(String name, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		session.request("print.image", data, done);
	}

	/** Prints the text file {@code name} as a written book (a sheet of paper a page). */
	public void printTextFile(String name, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		session.request("print.text", data, done);
	}

	/** Prints {@code text} as a written book called {@code title}. */
	public void printText(String title, String text, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("title", title);
		data.putString("text", text);
		session.request("print.text", data, done);
	}

	/** Prints a written book of {@code pages} as they are. */
	public void printBook(String title, List<String> pages, Consumer<ComputerResponse> done) {
		CompoundTag data = new CompoundTag();
		data.putString("title", title);
		data.put("pages", ComputerServer.stringList(pages));
		session.request("print.book", data, done);
	}
}
