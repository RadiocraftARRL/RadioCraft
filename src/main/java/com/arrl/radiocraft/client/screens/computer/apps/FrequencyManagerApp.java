package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.Codeplug;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.radio.bandplan.BandPlan;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * FREQMAN, the frequency manager (1.12 step 6): lists of saved channels in {@code .FRQ} files (name, receive and
 * transmit MHz, mode, CTCSS tone, notes), with what the band plan says each frequency is for. Changes are saved at once.
 * Ins adds, Enter edits, Del deletes, Space marks entries, Ctrl+Up/Down moves one, F8 sorts by frequency. F5 sends the
 * marked entries (or the selected one) into a commercial mobile wired to the computer, from a
 * knob position you choose, leaving its other channels (and their keys) as they were; F6 reads a radio's channels into
 * the list. F3 opens another list, F4 starts a new one. Opens {@code CHANNELS.FRQ} to start.
 */
public final class FrequencyManagerApp extends DosApp {

	public static final String DEFAULT_FILE = "CHANNELS.FRQ";
	private static final int LIST_ROW = 3, LIST_ROWS = 18;

	private FrequencyList channels = new FrequencyList();
	private String fileName = DEFAULT_FILE;
	private final DosList list = new DosList();
	private final TreeSet<Integer> marked = new TreeSet<>();
	private boolean loaded = false;

	public FrequencyManagerApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(this::edit);
	}

	@Override
	public void opened() {
		load(DEFAULT_FILE, true);
	}

	@Override
	public void openFile(String name) {
		load(name, false);
	}

	// -------------------- THE FILE --------------------

	private void load(String name, boolean missingIsNew) {
		session.files().read(name, response -> {
			if(!response.ok()) {
				if(missingIsNew && "not_found".equals(response.error())) {
					channels = new FrequencyList();
					fileName = ComputerStorage.normalise(name);
					loaded = true;
					rebuild();
				}
				else
					showError(response);
				return;
			}
			CompoundTag tag = ComputerFileFormats.nbt(response.data().getByteArray("data"));
			if(tag == null) {
				session.message(Dos.tr("err.bad_contents"), true);
				return;
			}
			channels = FrequencyList.fromTag(tag);
			fileName = ComputerStorage.normalise(name);
			loaded = true;
			marked.clear();
			rebuild();
			session.message(Dos.tr("freqman.opened", fileName, channels.size()), false);
		});
	}

	private void save() {
		session.files().write(fileName, ComputerFileFormats.nbt(channels.toTag()), response -> {
			if(!response.ok())
				showError(response);
		});
	}

	private void rebuild() {
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < channels.size(); i++)
			lines.add(line(channels.get(i), marked.contains(i)));
		list.setItems(lines);
	}

	/** One entry as a list row: mark, name, RX, TX (or offset), mode, tone, notes. */
	private static String line(FrequencyList.Entry entry, boolean mark) {
		String tx = entry.isReceiveOnly() ? Dos.tr("freqman.rx_only") : entry.isSplit() ? FrequencyList.mhz(entry.txMhz()) : Dos.tr("freqman.simplex");
		String tone = entry.tone() > 0.0F ? String.format(Locale.ROOT, "%.1f", entry.tone()) : "";
		return String.format(Locale.ROOT, "%s %-16s %10s %10s %-4s %5s %s", mark ? "*" : " ", entry.name(), FrequencyList.mhz(entry.rxMhz()), tx,
				entry.mode().name(), tone, entry.notes());
	}

	// -------------------- ENTRIES --------------------

	private void form(String title, int index, FrequencyList.Entry entry) {
		String tx = entry.isReceiveOnly() ? "RX" : entry.isSplit() ? FrequencyList.mhz(entry.txMhz()) : "";
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("freqman.name"), new DosInput(entry.name(), FrequencyList.NAME_MAX)),
				new DosFormDialog.Field(Dos.tr("freqman.rx"), new DosInput(entry.rxMhz() > 0.0D ? FrequencyList.mhz(entry.rxMhz()) : "", 12, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("freqman.tx"), new DosInput(tx, 12, c -> c >= '0' && c <= '9' || c == '.' || c == 'R' || c == 'r' || c == 'X' || c == 'x')),
				new DosFormDialog.Field(Dos.tr("freqman.mode"), new DosInput(entry.mode().name(), 4)),
				new DosFormDialog.Field(Dos.tr("freqman.tone"), new DosInput(entry.tone() > 0.0F ? String.format(Locale.ROOT, "%.1f", entry.tone()) : "", 6, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("freqman.notes"), new DosInput(entry.notes(), FrequencyList.NOTES_MAX)));
		showDialog(new DosFormDialog(title, Dos.tr("freqman.form_hint"), fields, values -> {
			double rx = FrequencyList.parseMhz(values.get(1));
			if(rx <= 0.0D)
				return Dos.tr("freqman.bad_rx");
			String txText = values.get(2).strip();
			double txMhz = txText.equalsIgnoreCase("RX") ? 0.0D : txText.isEmpty() ? rx : FrequencyList.parseMhz(txText);
			if(txMhz < 0.0D)
				return Dos.tr("freqman.bad_tx");
			FrequencyList.Mode mode = FrequencyList.Mode.parse(values.get(3));
			if(mode == null)
				return Dos.tr("freqman.bad_mode");
			float tone;
			try {
				tone = values.get(4).isBlank() ? 0.0F : Float.parseFloat(values.get(4).strip());
			}
			catch(NumberFormatException e) {
				return Dos.tr("freqman.bad_tone");
			}
			FrequencyList.Entry made = new FrequencyList.Entry(values.get(0), rx, txMhz, mode, tone, values.get(5));
			if(index < 0) {
				if(!channels.add(made))
					return Dos.tr("freqman.full", FrequencyList.MAX_ENTRIES);
			}
			else
				channels.set(index, made);
			rebuild();
			list.select(index < 0 ? channels.size() - 1 : index);
			save();
			return null;
		}));
	}

	private void add() {
		form(Dos.tr("freqman.add_title"), -1, new FrequencyList.Entry("", 0.0D, 0.0D, FrequencyList.Mode.FM, 0.0F, ""));
	}

	private void edit(int index) {
		if(index >= 0 && index < channels.size())
			form(Dos.tr("freqman.edit_title"), index, channels.get(index));
	}

	private void delete() {
		int index = list.selected();
		if(index < 0)
			return;
		showDialog(DosDialog.confirm(Dos.tr("freqman.delete_title"), Dos.tr("freqman.delete", channels.get(index).name()), () -> {
			channels.remove(index);
			marked.clear();
			rebuild();
			save();
		}));
	}

	private void move(int by) {
		int index = list.selected();
		if(index < 0)
			return;
		int to = channels.move(index, by);
		if(to != index) {
			marked.clear();
			rebuild();
			list.select(to);
			save();
		}
	}

	private void toggleMark() {
		int index = list.selected();
		if(index < 0)
			return;
		if(!marked.remove(index))
			marked.add(index);
		rebuild();
		list.select(index + 1);
	}

	// -------------------- RADIOS --------------------

	/** The entries to send: the marked ones in list order, else the selected one. */
	private List<FrequencyList.Entry> toSend() {
		List<FrequencyList.Entry> out = new ArrayList<>();
		if(marked.isEmpty()) {
			if(list.selected() >= 0)
				out.add(channels.get(list.selected()));
		}
		else
			for(int index : marked)
				if(index < channels.size())
					out.add(channels.get(index));
		return out;
	}

	private void send() {
		List<FrequencyList.Entry> entries = toSend();
		if(entries.isEmpty()) {
			session.message(Dos.tr("freqman.nothing"), true);
			return;
		}
		List<CommercialChannel> sent = new ArrayList<>();
		for(FrequencyList.Entry entry : entries) {
			CommercialChannel channel = entry.toChannel();
			if(channel == null) {
				session.message(Dos.tr("freqman.not_commercial", entry.name(), entry.mode().name()), true);
				return;
			}
			sent.add(channel);
		}
		if(sent.size() > CommercialProgram.MAX_CHANNELS) {
			session.message(Dos.tr("freqman.too_many", CommercialProgram.MAX_CHANNELS), true);
			return;
		}
		RadioPicker.pick(this, session, app, radio -> {
			int capacity = radio.contains("capacity") ? radio.getInt("capacity") : CommercialProgram.KNOB_POSITIONS;
			if(sent.size() > capacity) { // 16 on most radios, 64 on the upgraded handheld.
				session.message(Dos.tr("freqman.too_many", capacity), true);
				return;
			}
			showDialog(DosDialog.ask(Dos.tr("freqman.send_title"),
				Dos.tr("freqman.send", sent.size(), RadioPicker.name(radio), capacity - sent.size() + 1),
				new DosInput("1", 2, c -> c >= '0' && c <= '9'), typed -> {
					int start;
					try {
						start = Integer.parseInt(typed.strip()) - 1;
					}
					catch(NumberFormatException e) {
						start = -1;
					}
					CompoundTag data = new CompoundTag();
					data.putString("radio", radio.getString("id"));
					data.putInt("start", start);
					data.put("channels", Codeplug.channelsTag(sent));
					int first = start + 1;
					session.app(app, "export", data, response -> {
						if(!response.ok()) {
							showError(response);
							return;
						}
						ListTag problems = response.data().getList("problems", Tag.TAG_COMPOUND);
						if(problems.isEmpty())
							session.message(Dos.tr("freqman.sent", response.data().getInt("written"), RadioPicker.name(radio), first), false);
						else
							showDialog(DosDialog.message(Dos.tr("freqman.refused_title"), ProgrammerApp.problemsText(problems)));
					});
				}));
		});
	}

	/** Reads a radio's channels into the list (no keys: a frequency list keeps none). */
	private void readRadio() {
		RadioPicker.pick(this, session, app, radio -> {
			CompoundTag data = new CompoundTag();
			data.putString("radio", radio.getString("id"));
			session.app(app, "read", data, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				int added = 0;
				for(CommercialChannel channel : Codeplug.channels(response.data().getList("channels", Tag.TAG_COMPOUND))) {
					FrequencyList.Entry entry = FrequencyList.Entry.of(channel);
					if(entry != null && channels.add(entry))
						added++;
				}
				rebuild();
				save();
				session.message(Dos.tr("freqman.read", added, RadioPicker.name(radio)), false);
			});
		});
	}

	private void newList() {
		showDialog(DosDialog.ask(Dos.tr("freqman.new_title"), Dos.tr("freqman.new"), DosInput.fileName(""), typed -> {
			String name = ComputerStorage.suggest(typed, ComputerFileFormats.FREQUENCIES);
			session.files().read(name, response -> {
				if(response.ok()) {
					session.message(Dos.tr("logbook.exists", name), true);
					return;
				}
				channels = new FrequencyList();
				fileName = name;
				marked.clear();
				rebuild();
				save();
				session.message(Dos.tr("logbook.created", name), false);
			});
		}));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("freqman.title", fileName), Dos.tr("freqman.keys"));
		int bg = DosColours.BODY_BG;
		g.put(1, 1, String.format(Locale.ROOT, "  %-16s %10s %10s %-4s %5s %s", Dos.tr("freqman.col.name"), Dos.tr("freqman.col.rx"), Dos.tr("freqman.col.tx"),
				Dos.tr("freqman.col.mode"), Dos.tr("freqman.col.tone"), Dos.tr("freqman.col.notes")), DosColours.YELLOW, bg);
		g.hline(1, 2, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
		if(!loaded)
			g.put(2, LIST_ROW, Dos.tr("files.reading"), DosColours.TEXT_FG, bg);
		else if(list.isEmpty())
			g.put(2, LIST_ROW, Dos.tr("freqman.empty"), DosColours.TEXT_FG, bg);
		else
			list.render(g, 1, LIST_ROW, TextGrid.COLS - 2, LIST_ROWS, DosColours.TEXT_FG, bg, !hasDialog());

		// Under the list: what the band plan says the selected frequency is for.
		int index = list.selected();
		if(index >= 0 && index < channels.size()) {
			FrequencyList.Entry entry = channels.get(index);
			String use;
			try {
				BandPlan.Segment segment = BandPlan.current().segmentAt(entry.rxMhz() * 1e6);
				use = segment == null ? Dos.tr("bandplan.unplanned") : segment.use();
			}
			catch(RuntimeException e) { // No band plan in the config yet.
				use = Dos.tr("bandplan.unplanned");
			}
			String offset = entry.isSplit() ? Dos.tr("freqman.offset", String.format(Locale.ROOT, "%+.4f", entry.offsetMhz())) : "";
			g.putField(1, 22, Dos.fit(Dos.tr("freqman.plan", FrequencyList.mhz(entry.rxMhz()), use) + offset, TextGrid.COLS - 2), TextGrid.COLS - 2, DosColours.LIGHT_CYAN, bg);
		}
		String message = session.message();
		String status = Dos.tr("freqman.status", channels.size(), marked.size());
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(!loaded)
			return false;
		if(Dos.ctrl() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
			move(key == GLFW.GLFW_KEY_UP ? -1 : 1);
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_INSERT -> add();
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_SPACE -> toggleMark();
			case GLFW.GLFW_KEY_F3 -> showDialog(new DosFilePicker(session, Dos.tr("freqman.open_title"), ComputerFileFormats.FREQUENCIES, name -> load(name, false)));
			case GLFW.GLFW_KEY_F4 -> newList();
			case GLFW.GLFW_KEY_F5 -> send();
			case GLFW.GLFW_KEY_F6 -> readRadio();
			case GLFW.GLFW_KEY_F8 -> {
				channels.sortByFrequency();
				marked.clear();
				rebuild();
				save();
			}
			default -> {
				return list.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return list.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return list.mouseScrolled(col, row, amount);
	}
}
