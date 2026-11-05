package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.computer.BookDocument;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import net.minecraft.nbt.CompoundTag;

/**
 * The book editor's server side (1.12 step 6): printing a saved book. A book is printed from its {@code .BOK} file
 * rather than sent up whole, since a long one would not fit in one packet; the screen saves it first.
 * <ul>
 * <li>{@code print} ({@code name} of a {@code .BOK}, {@code volume} 0-based): that volume as a written book on the
 * scanner (a sheet of paper a page, {@link BookDocument#PAGES_PER_VOLUME} pages a volume); answers {@code volumes}.</li>
 * </ul>
 */
public final class BookEditorHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		if(!action.equals("print")) {
			reply.error("unknown_request", action);
			return;
		}
		String name = data.getString("name");
		if(!ComputerFileFormats.BOOK.equals(ComputerStorage.extension(name))) {
			reply.error(ComputerStorage.Problem.BAD_NAME);
			return;
		}
		ScannerBlockEntity scanner = context.scanner();
		if(scanner == null) {
			reply.error("no_scanner");
			return;
		}
		if(!context.files().storage().exists(name)) {
			reply.error(ComputerStorage.Problem.NOT_FOUND);
			return;
		}
		CompoundTag tag = context.files().readNbt(name);
		if(tag == null) {
			reply.error(ComputerStorage.Problem.BAD_CONTENTS);
			return;
		}
		BookDocument book = BookDocument.fromTag(tag);
		int volume = data.getInt("volume");
		if(volume < 0 || volume >= book.volumes()) {
			reply.error("no_such_volume", String.valueOf(volume + 1));
			return;
		}
		String author = book.author().isBlank() ? context.player().getGameProfile().getName() : book.author();
		ScannerBlockEntity.Result result = scanner.printBook(book.volumeTitle(volume), author, book.volumePages(volume));
		if(result != ScannerBlockEntity.Result.OK) {
			reply.error(result.key());
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putInt("volumes", book.volumes());
		reply.ok(out);
	}
}
