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
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LOGBOOK, the station log (1.12 step 6): the contacts in a {@code .LOG} file, newest at the bottom as a paper log
 * runs, each with the UTC date and time, call, frequency, mode, the reports sent and received and notes. Changes are
 * saved at once (a log is never left unsaved). Ins adds a contact (date and time filled in), Enter edits one, Del
 * deletes one, F6 fills a new contact from a station a wired radio heard lately (call, frequency, mode and a report),
 * F3 opens another log, F4 starts a new one, F7 writes the log out as an ADIF file ({@code .ADI}) for real logging
 * programs. Opens {@code LOGBOOK.LOG} to start.
 */
public final class LogbookApp extends DosApp {

	public static final String DEFAULT_FILE = "LOGBOOK.LOG";
	private static final int LIST_ROW = 3, LIST_ROWS = 19;

	private Logbook log = new Logbook();
	private String fileName = DEFAULT_FILE;
	private final DosList list = new DosList();
	private boolean loaded = false;

	public LogbookApp(ComputerSession session, ComputerApp app) {
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

	/** Reads {@code name}; a missing one is a new, empty log if {@code missingIsNew}. */
	private void load(String name, boolean missingIsNew) {
		session.files().read(name, response -> {
			if(!response.ok()) {
				if(missingIsNew && "not_found".equals(response.error())) {
					log = new Logbook();
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
			log = Logbook.fromTag(tag);
			fileName = ComputerStorage.normalise(name);
			loaded = true;
			rebuild();
			list.select(log.size() - 1);
			session.message(Dos.tr("logbook.opened", fileName, log.size()), false);
		});
	}

	private void save() {
		String name = fileName;
		session.files().write(name, ComputerFileFormats.nbt(log.toTag()), response -> {
			if(!response.ok())
				showError(response);
		});
	}

	private void rebuild() {
		List<String> lines = new ArrayList<>();
		for(Logbook.Entry entry : log.entries())
			lines.add(line(entry));
		list.setItems(lines);
	}

	/** One contact as a list row: date, UTC, call, MHz, mode, sent, received, notes. */
	private static String line(Logbook.Entry entry) {
		return String.format(Locale.ROOT, "%-10s %-4s %-10s %10s %-5s %-4s %-4s %s", entry.date(), entry.time(), entry.call(),
				entry.frequencyMhz() > 0.0D ? FrequencyList.mhz(entry.frequencyMhz()) : "", entry.mode(), entry.rstSent(), entry.rstReceived(), entry.notes());
	}

	// -------------------- CONTACTS --------------------

	/** The form for a contact; {@code index} -1 adds it. */
	private void form(String title, int index, Logbook.Entry entry) {
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("logbook.call"), new DosInput(entry.call(), Logbook.CALL_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '/')),
				new DosFormDialog.Field(Dos.tr("logbook.date"), new DosInput(Logbook.utcDate(entry.utcMillis()), 10, c -> c >= '0' && c <= '9' || c == '-')),
				new DosFormDialog.Field(Dos.tr("logbook.time"), new DosInput(Logbook.utcTime(entry.utcMillis()), 5, c -> c >= '0' && c <= '9' || c == ':')),
				new DosFormDialog.Field(Dos.tr("logbook.freq"), new DosInput(entry.frequencyMhz() > 0.0D ? FrequencyList.mhz(entry.frequencyMhz()) : "", 12, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("logbook.mode"), new DosInput(entry.mode(), Logbook.MODE_MAX)),
				new DosFormDialog.Field(Dos.tr("logbook.sent"), new DosInput(entry.rstSent(), Logbook.RST_MAX, c -> c >= '0' && c <= '9')),
				new DosFormDialog.Field(Dos.tr("logbook.rcvd"), new DosInput(entry.rstReceived(), Logbook.RST_MAX, c -> c >= '0' && c <= '9')),
				new DosFormDialog.Field(Dos.tr("logbook.notes"), new DosInput(entry.notes(), Logbook.NOTES_MAX)));
		showDialog(new DosFormDialog(title, Dos.tr("logbook.form_hint"), fields, values -> {
			String call = values.get(0).strip();
			if(call.isEmpty())
				return Dos.tr("logbook.need_call");
			long utc = Logbook.parseUtc(values.get(1), values.get(2));
			if(utc < 0L)
				return Dos.tr("logbook.bad_time");
			double mhz = FrequencyList.parseMhz(values.get(3));
			if(mhz < 0.0D)
				return Dos.tr("logbook.bad_freq");
			Logbook.Entry made = new Logbook.Entry(utc, entry.gameTime(), call, mhz, values.get(4), values.get(5), values.get(6), values.get(7));
			if(index < 0) {
				if(!log.add(made))
					return Dos.tr("logbook.full", Logbook.MAX_ENTRIES);
			}
			else
				log.set(index, made);
			rebuild();
			list.select(log.entries().indexOf(made));
			save();
			int before = log.countWith(made.call()) - 1;
			session.message(before > 0 ? Dos.tr("logbook.worked_before", made.call(), before) : Dos.tr("logbook.logged", made.call()), false);
			return null;
		}));
	}

	/** A new contact now (UTC), with the game's day and time, and {@code call}, {@code mhz}, {@code mode} and report filled in. */
	private Logbook.Entry fresh(String call, double mhz, String mode, String rstReceived) {
		String game = Minecraft.getInstance().level == null ? "" : WeatherReports.timestamp(Minecraft.getInstance().level.getDayTime());
		String sent = mode.equals("CW") ? "599" : "59";
		return new Logbook.Entry(System.currentTimeMillis(), game, call, mhz, mode, sent, rstReceived, "");
	}

	private void add() {
		form(Dos.tr("logbook.add_title"), -1, fresh("", 0.0D, "USB", ""));
	}

	private void edit(int index) {
		if(index >= 0 && index < log.size())
			form(Dos.tr("logbook.edit_title"), index, log.get(index));
	}

	private void delete() {
		int index = list.selected();
		if(index < 0)
			return;
		Logbook.Entry entry = log.get(index);
		showDialog(DosDialog.confirm(Dos.tr("logbook.delete_title"), Dos.tr("logbook.delete", entry.call(), entry.date()), () -> {
			log.remove(index);
			rebuild();
			save();
		}));
	}

	/** Asks the wired radios who they heard, and fills a new contact from the one picked. */
	private void fromRadio() {
		session.app(app, "heard", new CompoundTag(), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			ListTag stations = response.data().getList("stations", Tag.TAG_COMPOUND);
			if(stations.isEmpty()) {
				session.message(response.data().getInt("radios") == 0 ? Dos.tr("logbook.no_radios") : Dos.tr("logbook.nobody_heard"), true);
				return;
			}
			List<String> lines = new ArrayList<>();
			for(int i = 0; i < stations.size(); i++) {
				CompoundTag s = stations.getCompound(i);
				lines.add(String.format(Locale.ROOT, "%-10s %10s %-4s %-3s %s", s.getString("call"), FrequencyList.mhz(s.getDouble("mhz")),
						s.getString("mode"), s.getString("rst"), ago(s.getLong("ago"))));
			}
			showDialog(new DosPickDialog(Dos.tr("logbook.heard_title"), Dos.tr("logbook.heard_hint"), lines, index -> {
				CompoundTag s = stations.getCompound(index);
				String player = s.getString("player");
				Logbook.Entry entry = fresh(s.getString("call"), s.getDouble("mhz"), s.getString("mode"), s.getString("rst"));
				if(!player.isEmpty() && !player.equalsIgnoreCase(s.getString("call")))
					entry = new Logbook.Entry(entry.utcMillis(), entry.gameTime(), entry.call(), entry.frequencyMhz(), entry.mode(), entry.rstSent(),
							entry.rstReceived(), Dos.tr("logbook.op", player) + " " + I18n.get(s.getString("radio")));
				form(Dos.tr("logbook.add_title"), -1, entry);
			}));
		});
	}

	private static String ago(long seconds) {
		return seconds < 90 ? Dos.tr("logbook.ago_s", seconds) : Dos.tr("logbook.ago_m", Math.round(seconds / 60.0D));
	}

	private void exportAdif() {
		String base = fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
		String name = ComputerStorage.suggest(base, ComputerFileFormats.ADIF);
		session.files().writeText(name, log.adif("1.12"), response -> {
			if(response.ok())
				session.message(Dos.tr("logbook.exported", name, log.size()), false);
			else
				showError(response);
		});
	}

	private void newLog() {
		showDialog(DosDialog.ask(Dos.tr("logbook.new_title"), Dos.tr("logbook.new"), DosInput.fileName(""), typed -> {
			String name = ComputerStorage.suggest(typed, ComputerFileFormats.LOG);
			session.files().read(name, response -> {
				if(response.ok()) {
					session.message(Dos.tr("logbook.exists", name), true);
					return;
				}
				log = new Logbook();
				fileName = name;
				rebuild();
				save();
				session.message(Dos.tr("logbook.created", name), false);
			});
		}));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("logbook.title", fileName), Dos.tr("logbook.keys"));
		int bg = DosColours.BODY_BG;
		g.put(1, 1, String.format(Locale.ROOT, "%-10s %-4s %-10s %10s %-5s %-4s %-4s %s", Dos.tr("logbook.col.date"), Dos.tr("logbook.col.utc"),
				Dos.tr("logbook.col.call"), Dos.tr("logbook.col.mhz"), Dos.tr("logbook.col.mode"), Dos.tr("logbook.col.sent"), Dos.tr("logbook.col.rcvd"),
				Dos.tr("logbook.col.notes")), DosColours.YELLOW, bg);
		g.hline(1, 2, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
		if(!loaded)
			g.put(2, LIST_ROW, Dos.tr("files.reading"), DosColours.TEXT_FG, bg);
		else if(list.isEmpty())
			g.put(2, LIST_ROW, Dos.tr("logbook.empty"), DosColours.TEXT_FG, bg);
		else
			list.render(g, 1, LIST_ROW, TextGrid.COLS - 2, LIST_ROWS, DosColours.TEXT_FG, bg, !hasDialog());
		String message = session.message();
		String status = Dos.tr("logbook.status", log.size(), Logbook.MAX_ENTRIES);
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(!loaded)
			return false;
		switch(key) {
			case GLFW.GLFW_KEY_INSERT -> add();
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_F3 -> showDialog(new DosFilePicker(session, Dos.tr("logbook.open_title"), ComputerFileFormats.LOG, name -> load(name, false)));
			case GLFW.GLFW_KEY_F4 -> newLog();
			case GLFW.GLFW_KEY_F6 -> fromRadio();
			case GLFW.GLFW_KEY_F7 -> exportAdif();
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
