package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioFaxBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.BookDocument;
import com.arrl.radiocraft.common.computer.BookPages;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The radio fax program's server side (1.12 step 7): the computer as a fax machine with no paper, through the radio
 * interfaces on its station bus ({@link FaxModem}, the machine's own conversation, so it faxes the radio fax machines and
 * they fax it). It sends a picture ({@code .IMG}, which the far machine prints on a blank map) or a text or book file
 * ({@code .TXT}, {@code .BOK}, which prints as a written book) or a QSL card ({@code .QSL}, computer batch 3C, which
 * prints as a card); a QSL card received is kept as {@code FAX00001.QSL}; what it receives it keeps as files, {@code FAX00001.IMG}
 * or {@code .TXT}, listed in its inbox ("digital unless printed": print them through the scanner). It answers calls for
 * any callsign on its bus unless a radio fax machine is on the same bus (the machine answers then) or auto answer is
 * off. An enchanted book cannot be taken in ("CANNOT STORE").
 * <ul>
 * <li>{@code status}: the station ({@link RadioStation#status}), {@code phase}, {@code status} (the LCD's words),
 * {@code peer}, {@code label}, {@code packets}, {@code of}, {@code retries}, {@code auto}, {@code machine} (a fax
 * machine on the bus answers), {@code log}, {@code recent}, {@code inbox} (each {@code name}, {@code from},
 * {@code label}, {@code utc}).</li>
 * <li>{@code send} ({@code name}, {@code to}), {@code cancel}, {@code auto} ({@code on}), {@code forget}
 * ({@code name}: off the inbox list).</li>
 * </ul>
 * A change on the line is pushed to the screens at the computer as an {@code update} event.
 */
public final class FaxHandler implements ComputerAppHandler {

	public static final int LOG_LINES = 8, RECENT_MAX = 6, INBOX_MAX = 50;

	/** One received document in the inbox. */
	public record Received(String name, String from, String label, long utc) {}

	/** One computer's fax. */
	public static final class State implements ComputerAppState, FaxModem.Line {
		private final RadioComputerBlockEntity computer;
		final FaxModem modem = new FaxModem(this);
		boolean auto = true;
		final List<String> log = new ArrayList<>();
		final List<String> recent = new ArrayList<>();
		final List<Received> inbox = new ArrayList<>();
		/** The bus this tick (looked up once per packet or request). */
		@Nullable
		private StationBus bus;

		State(RadioComputerBlockEntity computer) {
			this.computer = computer;
		}

		public FaxModem modem() {
			return modem;
		}

		public List<Received> inbox() {
			return List.copyOf(inbox);
		}

		StationBus bus() {
			if(bus == null)
				bus = RadioStation.bus(computer);
			return bus;
		}

		void forgetBus() {
			bus = null;
		}

		@Override
		public boolean transmit(TncPacket packet) {
			return RadioStation.transmit(computer, bus(), packet);
		}

		@Override
		public String callsign() {
			return bus().callsign();
		}

		@Override
		public boolean isForMe(String to) {
			return !to.isEmpty() && !TncPacket.isForEveryone(to) && bus().isFor(to);
		}

		@Override
		@Nullable
		public Band band() {
			RadioBlockEntity on = RadioStation.firstOn(RadioStation.radios(bus()));
			return on == null ? null : on.getBand() == null ? Band.getBand(on.getFrequency()) : on.getBand();
		}

		@Override
		@Nullable
		public String refuse(ItemStack document) {
			if(!(computer.getLevel() instanceof ServerLevel))
				return "CANNOT COPY";
			if(document.is(Items.ENCHANTED_BOOK))
				return "CANNOT STORE";
			ComputerFileStore files = computer.files();
			int size = document.is(Items.FILLED_MAP) ? FileTransfer.MAP_SIZE * FileTransfer.MAP_SIZE : 4096;
			if(files.storage().count() >= ComputerFileStore.maxFiles() || files.storage().used() + size > ComputerFileStore.capacity())
				return "DISK FULL";
			return null;
		}

		@Override
		@Nullable
		public String delivered(String from, String label, ItemStack document) {
			if(!(computer.getLevel() instanceof ServerLevel level))
				return "CANNOT COPY";
			byte[] contents;
			String extension;
			com.arrl.radiocraft.common.qsl.QslCard card = com.arrl.radiocraft.common.qsl.QslCards.card(document);
			if(card != null) { // A QSL card is kept as a card file: the QSL Designer prints it (computer batch 3C).
				contents = com.arrl.radiocraft.common.qsl.QslCards.file(card.asCopy());
				extension = com.arrl.radiocraft.common.qsl.QslCards.EXTENSION;
			}
			else if(document.is(Items.FILLED_MAP)) {
				contents = FileTransfer.imagePixels(document, level);
				extension = ComputerFileFormats.IMAGE;
				if(contents == null)
					return "CANNOT COPY";
			}
			else {
				contents = ComputerStorage.text(documentText(document));
				extension = ComputerFileFormats.TEXT;
			}
			ComputerFileStore files = computer.files();
			String name = ComputerStorage.nextName("FAX", extension, files.storage()::exists);
			if(name == null || files.write(name, contents) != null)
				return "DISK FULL";
			inbox.addFirst(new Received(name, from, label, System.currentTimeMillis()));
			while(inbox.size() > INBOX_MAX)
				inbox.removeLast();
			level.playSound(null, computer.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
			computer.setChanged();
			CompoundTag event = new CompoundTag();
			event.putString("name", name);
			event.putString("from", from);
			computer.push(ComputerApps.RADIO_FAX, "received", event);
			return null;
		}

		@Override
		public void log(String line) {
			log.addFirst(line);
			while(log.size() > LOG_LINES)
				log.removeLast();
			computer.setChanged();
		}

		void remember(String callsign) {
			recent.remove(callsign);
			recent.addFirst(callsign);
			while(recent.size() > RECENT_MAX)
				recent.removeLast();
			computer.setChanged();
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putBoolean("auto", auto);
			tag.put("log", ComputerServer.stringList(log));
			tag.put("recent", ComputerServer.stringList(recent));
			ListTag list = new ListTag();
			for(Received received : inbox) {
				CompoundTag entry = new CompoundTag();
				entry.putString("name", received.name());
				entry.putString("from", received.from());
				entry.putString("label", received.label());
				entry.putLong("utc", received.utc());
				list.add(entry);
			}
			tag.put("inbox", list);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			auto = !tag.contains("auto") || tag.getBoolean("auto");
			log.clear();
			log.addAll(ComputerServer.strings(tag.getList("log", Tag.TAG_STRING)));
			recent.clear();
			recent.addAll(ComputerServer.strings(tag.getList("recent", Tag.TAG_STRING)));
			inbox.clear();
			for(Tag entry : tag.getList("inbox", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag)entry;
				inbox.add(new Received(c.getString("name"), c.getString("from"), c.getString("label"), c.getLong("utc")));
			}
		}
	}

	/** {@code computer}'s fax. */
	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.RADIO_FAX, () -> new State(computer));
	}

	/** True if this computer answers calls: auto answer on, and no radio fax machine on its bus to answer them. */
	static boolean answers(State state) {
		return state.auto && state.bus().terminals().stream().noneMatch(terminal -> terminal instanceof RadioFaxBlockEntity);
	}

	// -------------------- REQUESTS --------------------

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		State state = state(context.computer());
		state.forgetBus();
		long now = context.level().getGameTime();
		switch(action) {
			case "status" -> reply.ok(status(context, state));
			case "send" -> {
				ItemStack document = document(context, data.getString("name"));
				if(document == null) {
					reply.error("fax_cannot_send");
					return;
				}
				String problem = state.bus().hasInterface() ? state.modem.send(now, data.getString("to"), document, RadioStation.newId(context.level())) : "station_no_interface";
				if(problem != null) {
					reply.error(problem);
					return;
				}
				state.remember(TncPacket.sanitizeCallsign(data.getString("to")));
				reply.ok();
			}
			case "cancel" -> {
				state.modem.cancel();
				reply.ok();
			}
			case "auto" -> {
				state.auto = data.getBoolean("on");
				context.computer().setChanged();
				reply.ok();
			}
			case "forget" -> {
				state.inbox.removeIf(received -> received.name().equals(data.getString("name")));
				context.computer().setChanged();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * The document a file faxes as: a picture for {@code .IMG}, a written book (a page per book page, by
	 * {@code player}) for {@code .TXT} and {@code .BOK}; null for anything else or a missing file.
	 */
	@Nullable
	static ItemStack document(ComputerContext context, String name) {
		String file = ComputerStorage.normalise(name);
		if(file == null)
			return null;
		ComputerFileStore files = context.files();
		String base = file.substring(0, file.indexOf('.'));
		String author = context.player().getGameProfile().getName();
		switch(ComputerStorage.extension(file)) {
			case ComputerFileFormats.IMAGE -> {
				byte[] pixels = files.read(file);
				return pixels == null || !ComputerFileFormats.isImage(pixels) ? null : FileTransfer.image(pixels, base, FileTransfer.MODE_PICTURE);
			}
			case com.arrl.radiocraft.common.qsl.QslCards.EXTENSION -> { // A QSL card (computer batch 3C).
				com.arrl.radiocraft.common.qsl.QslCard card = com.arrl.radiocraft.common.qsl.QslCards.fromFile(files.read(file));
				return card == null ? null : com.arrl.radiocraft.common.qsl.QslCards.stack(card, 1);
			}
			case ComputerFileFormats.TEXT -> {
				String text = files.readText(file);
				return text == null ? null : book(base, author, BookPages.paginate(text));
			}
			case ComputerFileFormats.BOOK -> {
				CompoundTag tag = files.readNbt(file);
				if(tag == null)
					return null;
				BookDocument document = BookDocument.fromTag(tag);
				return book(document.title().isBlank() ? base : document.title(), document.author().isBlank() ? author : document.author(), document.volumePages(0));
			}
			default -> {
				return null;
			}
		}
	}

	/** A written book of {@code pages} (at most a book's 100). */
	static ItemStack book(String title, String author, List<String> pages) {
		List<Filterable<Component>> bookPages = new ArrayList<>();
		for(int i = 0; i < pages.size() && i < BookPages.MAX_PAGES; i++) {
			String page = pages.get(i);
			bookPages.add(Filterable.passThrough(Component.literal(page.length() > BookPages.PAGE_CHARS ? page.substring(0, BookPages.PAGE_CHARS) : page)));
		}
		String cleanTitle = title.strip().isEmpty() ? "Fax" : title.strip();
		if(cleanTitle.length() > WrittenBookContent.TITLE_MAX_LENGTH)
			cleanTitle = cleanTitle.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(cleanTitle), author, 0, bookPages, true));
		return book;
	}

	/** A document received as text: a book's pages (form feeds between them), or a sheet of paper's name. */
	static String documentText(ItemStack document) {
		List<String> pages = new ArrayList<>();
		WrittenBookContent written = document.get(DataComponents.WRITTEN_BOOK_CONTENT);
		WritableBookContent writable = document.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if(written != null) {
			for(Filterable<Component> page : written.pages())
				pages.add(page.raw().getString());
			if(!pages.isEmpty())
				pages.set(0, written.title().raw() + " (" + written.author() + ")\n\n" + pages.getFirst());
		}
		else if(writable != null)
			for(Filterable<String> page : writable.pages())
				pages.add(page.raw());
		else
			pages.add(FileTransfer.describe(document));
		return BookPages.join(pages);
	}

	private static CompoundTag status(ComputerContext context, State state) {
		CompoundTag out = RadioStation.status(state.bus());
		FaxModem modem = state.modem;
		out.putString("phase", modem.phase().name());
		out.putString("status", modem.status());
		out.putString("peer", modem.peer());
		out.putString("label", modem.label());
		out.putInt("packets", modem.packetsDone());
		out.putInt("of", com.arrl.radiocraft.common.radio.data.FaxRates.PACKETS);
		out.putInt("retries", modem.retries());
		out.putBoolean("auto", state.auto);
		out.putBoolean("machine", state.bus().terminals().stream().anyMatch(terminal -> terminal instanceof RadioFaxBlockEntity));
		out.put("log", ComputerServer.stringList(state.log));
		out.put("recent", ComputerServer.stringList(state.recent));
		ListTag inbox = new ListTag();
		ComputerStorage storage = context.files().storage();
		for(Received received : state.inbox) {
			if(!storage.exists(received.name()))
				continue; // Deleted from the shell.
			CompoundTag entry = new CompoundTag();
			entry.putString("name", received.name());
			entry.putString("from", received.from());
			entry.putString("label", received.label());
			entry.putLong("utc", received.utc());
			inbox.add(entry);
		}
		out.put("inbox", inbox);
		return out;
	}

	// -------------------- THE LINE --------------------

	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(!packet.isFax() || computer.getLevel() == null)
			return;
		State state = state(computer);
		state.forgetBus();
		state.modem.receive(computer.getLevel().getGameTime(), packet, answers(state), () -> RadioStation.newId(computer.getLevel()));
		if(state.modem.takeChanged())
			computer.push(ComputerApps.RADIO_FAX, "update", new CompoundTag());
	}

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if(!(computer.existingState(ComputerApps.RADIO_FAX) instanceof State state))
			return; // Nothing has happened since it loaded: no fax going on.
		state.forgetBus();
		state.modem.tick(gameTime, () -> RadioStation.newId(computer.getLevel()));
		if(state.modem.takeChanged())
			computer.push(ComputerApps.RADIO_FAX, "update", new CompoundTag());
	}
}
