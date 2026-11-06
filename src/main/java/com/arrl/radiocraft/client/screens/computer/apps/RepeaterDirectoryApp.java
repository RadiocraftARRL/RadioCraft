package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.RepeaterDirectory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * REPEATER, the repeater directory (computer apps 2, 2026-11-06): repeaters the computer's radios have heard (their ID
 * or courtesy beep) and ones typed in, with output, offset, tone, distance, bearing and when last heard. F5 collects
 * again (the server side, {@code RepeaterDirectoryHandler}, asks every radio the computer reaches), F3 changes the
 * order, Ins/F4 adds, Enter edits, Del deletes, Space marks, F7 exports the marked ones (or the selected one) to a
 * frequency manager list, F6 prints the directory on the scanner.
 */
public final class RepeaterDirectoryApp extends DosApp {

	private static final int LIST_ROW = 3, LIST_ROWS = 18;

	/** One row as the server listed it. */
	private record Row(int index, String key, String call, String notes, double out, int offset, float tone, boolean hasPos, int x, int z,
	                   boolean elsewhere, int distance, int bearing, boolean heard, long ago) {}

	private final DosList list = new DosList();
	private final List<Row> rows = new ArrayList<>(); // In display order.
	private final Set<String> marked = new LinkedHashSet<>(); // Keys.
	private RepeaterDirectory.Sort sort = RepeaterDirectory.Sort.DISTANCE;
	private boolean loaded = false;
	private int radios = 0;
	private String exportFile = "CHANNELS.FRQ";

	public RepeaterDirectoryApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(i -> edit());
	}

	@Override
	public void opened() {
		refresh(false);
	}

	@Override
	public void resumed() {
		refresh(false);
	}

	// -------------------- DATA --------------------

	private void refresh(boolean say) {
		session.app(app, "list", new CompoundTag(), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			loaded = true;
			radios = response.data().getInt("radios");
			List<Row> got = new ArrayList<>();
			ListTag entries = response.data().getList("entries", Tag.TAG_COMPOUND);
			for(int i = 0; i < entries.size(); i++) {
				CompoundTag e = entries.getCompound(i);
				got.add(new Row(e.getInt("i"), e.getString("key"), e.getString("call"), e.getString("notes"), e.getDouble("out"), e.getInt("offset"),
						e.getFloat("tone"), e.getBoolean("hasPos"), e.getInt("x"), e.getInt("z"), e.getBoolean("elsewhere"), e.getInt("distance"),
						e.getInt("bearing"), e.getLong("ago") >= 0L, e.getLong("ago")));
			}
			rows.clear();
			rows.addAll(got);
			marked.removeIf(key -> rows.stream().noneMatch(row -> row.key().equals(key)));
			resort();
			if(say)
				session.message(Dos.tr("rptdir.collected", response.data().getInt("found"), radios), false);
		});
	}

	private void resort() {
		String selected = selectedRow() == null ? null : selectedRow().key();
		Comparator<Row> byFrequency = Comparator.comparingDouble(Row::out);
		rows.sort(switch(sort) {
			case DISTANCE -> Comparator.<Row>comparingInt(row -> row.distance() < 0 ? Integer.MAX_VALUE : row.distance()).thenComparing(byFrequency);
			case FREQUENCY -> byFrequency;
			case HEARD -> Comparator.<Row>comparingLong(row -> row.heard() ? row.ago() : Long.MAX_VALUE).thenComparing(byFrequency);
		});
		List<String> lines = new ArrayList<>();
		for(Row row : rows)
			lines.add(line(row, marked.contains(row.key())));
		list.setItems(lines);
		for(int i = 0; selected != null && i < rows.size(); i++)
			if(rows.get(i).key().equals(selected))
				list.select(i);
	}

	private Row selectedRow() {
		int i = list.selected();
		return i >= 0 && i < rows.size() ? rows.get(i) : null;
	}

	// -------------------- WORDS --------------------

	private static String tone(float tone) {
		return tone > 0.0F ? String.format(Locale.ROOT, "%.1f", tone) : "";
	}

	/** "5s ago", "12m ago", "3h ago", "2d ago"; "manual" if never heard. */
	static String ago(boolean heard, long seconds) {
		if(!heard)
			return Dos.tr("rptdir.manual");
		if(seconds < 60L)
			return Dos.tr("rptdir.ago_s", seconds);
		if(seconds < 3600L)
			return Dos.tr("rptdir.ago_m", seconds / 60L);
		if(seconds < 86_400L)
			return Dos.tr("rptdir.ago_h", seconds / 3600L);
		return Dos.tr("rptdir.ago_d", seconds / 86_400L);
	}

	private static String distance(Row row) {
		if(row.elsewhere())
			return Dos.tr("rptdir.elsewhere");
		if(row.distance() < 0)
			return "";
		return row.distance() >= 10_000 ? String.format(Locale.ROOT, "%.1fkm", row.distance() / 1000.0D) : row.distance() + "m";
	}

	/** One list line: mark, call, output, offset, tone, distance, bearing, last heard, notes. */
	private static String line(Row row, boolean mark) {
		return String.format(Locale.ROOT, "%s %-10s %9s %7s %5s %7s %4s %-8s %s", mark ? "*" : " ", Dos.fit(row.call(), 10), String.format(Locale.ROOT, "%.4f", row.out()),
				RepeaterDirectory.offsetText(row.offset()), tone(row.tone()), distance(row), row.bearing() < 0 ? "" : String.format(Locale.ROOT, "%03d", row.bearing()),
				Dos.fit(ago(row.heard(), row.ago()), 8), row.notes());
	}

	/** The directory as text, for printing. */
	private String text() {
		StringBuilder out = new StringBuilder();
		for(Row row : rows) {
			out.append(row.call().isEmpty() ? "?" : row.call()).append(' ').append(String.format(Locale.ROOT, "%.4f", row.out())).append(' ')
					.append(RepeaterDirectory.offsetText(row.offset()));
			if(row.tone() > 0.0F)
				out.append(" T").append(tone(row.tone()));
			out.append('\n');
			if(row.distance() >= 0)
				out.append("  ").append(distance(row)).append(' ').append(String.format(Locale.ROOT, "%03d", row.bearing())).append('\n');
			if(!row.notes().isEmpty())
				out.append("  ").append(row.notes()).append('\n');
		}
		return out.toString();
	}

	// -------------------- EDITING --------------------

	private void form(String title, Row before) {
		Row r = before;
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("rptdir.f.call"), new DosInput(r == null ? "" : r.call(), RepeaterDirectory.CALLSIGN_MAX)),
				new DosFormDialog.Field(Dos.tr("rptdir.f.output"), new DosInput(r == null ? "" : FrequencyList.mhz(r.out()), 10, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("rptdir.f.offset"), new DosInput(r == null ? "" : String.valueOf(r.offset()), 8, c -> c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+')),
				new DosFormDialog.Field(Dos.tr("rptdir.f.tone"), new DosInput(r == null ? "" : tone(r.tone()), 6, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("rptdir.f.x"), new DosInput(r == null || !r.hasPos() ? "" : String.valueOf(r.x()), 9, c -> c >= '0' && c <= '9' || c == '-')),
				new DosFormDialog.Field(Dos.tr("rptdir.f.z"), new DosInput(r == null || !r.hasPos() ? "" : String.valueOf(r.z()), 9, c -> c >= '0' && c <= '9' || c == '-')),
				new DosFormDialog.Field(Dos.tr("rptdir.f.notes"), new DosInput(r == null ? "" : r.notes(), RepeaterDirectory.NOTES_MAX)));
		showDialog(new DosFormDialog(title, Dos.tr("rptdir.form_hint"), fields, values -> {
			double out = FrequencyList.parseMhz(values.get(1));
			if(out <= 0.0D)
				return Dos.tr("rptdir.bad_output");
			Integer offset = RepeaterDirectory.parseOffsetKhz(values.get(2));
			if(offset == null)
				return Dos.tr("rptdir.bad_offset");
			float tone;
			try {
				tone = values.get(3).isBlank() ? 0.0F : Float.parseFloat(values.get(3).strip());
			}
			catch(NumberFormatException e) {
				return Dos.tr("rptdir.bad_tone");
			}
			boolean hasPos = !values.get(4).isBlank() || !values.get(5).isBlank();
			int x, z;
			try {
				x = values.get(4).isBlank() ? 0 : Integer.parseInt(values.get(4).strip());
				z = values.get(5).isBlank() ? 0 : Integer.parseInt(values.get(5).strip());
			}
			catch(NumberFormatException e) {
				return Dos.tr("rptdir.bad_place");
			}
			CompoundTag data = new CompoundTag();
			data.putString("call", values.get(0));
			data.putDouble("out", out);
			data.putInt("offset", offset);
			data.putFloat("tone", tone);
			data.putBoolean("hasPos", hasPos);
			data.putInt("x", x);
			data.putInt("z", z);
			data.putString("notes", values.get(6));
			if(r != null) {
				data.putInt("index", r.index());
				data.putString("key", r.key());
			}
			session.app(app, r == null ? "add" : "edit", data, this::changed);
			return null;
		}));
	}

	private void changed(ComputerResponse response) {
		if(!response.ok())
			showError(response);
		refresh(false);
	}

	private void add() {
		form(Dos.tr("rptdir.add_title"), null);
	}

	private void edit() {
		Row row = selectedRow();
		if(row != null)
			form(Dos.tr("rptdir.edit_title"), row);
	}

	private void delete() {
		Row row = selectedRow();
		if(row == null)
			return;
		showDialog(DosDialog.confirm(Dos.tr("rptdir.delete_title"), Dos.tr("rptdir.delete", row.call().isEmpty() ? FrequencyList.mhz(row.out()) : row.call()), () -> {
			CompoundTag data = new CompoundTag();
			data.putInt("index", row.index());
			data.putString("key", row.key());
			session.app(app, "delete", data, this::changed);
		}));
	}

	private void toggleMark() {
		Row row = selectedRow();
		if(row == null)
			return;
		if(!marked.remove(row.key()))
			marked.add(row.key());
		int at = list.selected();
		resort();
		list.select(at + 1);
	}

	// -------------------- EXPORT AND PRINT --------------------

	private void export() {
		List<Integer> chosen = new ArrayList<>();
		for(Row row : rows)
			if(marked.contains(row.key()))
				chosen.add(row.index());
		if(chosen.isEmpty() && selectedRow() != null)
			chosen.add(selectedRow().index());
		if(chosen.isEmpty()) {
			session.message(Dos.tr("rptdir.nothing"), true);
			return;
		}
		showDialog(DosDialog.ask(Dos.tr("rptdir.export_title"), Dos.tr("rptdir.export", chosen.size()), DosInput.fileName(exportFile), typed -> {
			String file = ComputerStorage.suggest(typed, ComputerFileFormats.FREQUENCIES);
			exportFile = file;
			CompoundTag data = new CompoundTag();
			data.putString("file", file);
			data.put("indices", new IntArrayTag(chosen.stream().mapToInt(Integer::intValue).toArray()));
			session.app(app, "export", data, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				CompoundTag done = response.data();
				String said = Dos.tr("rptdir.exported", done.getInt("added"), done.getString("file"), done.getInt("duplicates") + done.getInt("full"));
				marked.clear();
				resort();
				if(session.isInstalled(ComputerApps.FREQUENCY_MANAGER) && done.getInt("added") > 0)
					showDialog(DosDialog.choice(Dos.tr("rptdir.exported_title"), said, new String[] { Dos.tr("rptdir.open_freqman"), Dos.tr("button.ok") },
							choice -> {
								if(choice == 0)
									session.launch(ComputerApps.FREQUENCY_MANAGER, done.getString("file"));
							}));
				else
					session.message(said, false);
			});
		}));
	}

	private void print() {
		if(rows.isEmpty())
			return;
		session.scanner().printText(Dos.tr("rptdir.print_title"), text(), response -> {
			if(response.ok())
				session.message(Dos.tr("edit.printed"), false);
			else
				showError(response);
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("rptdir.title", rows.size()), Dos.tr("rptdir.keys"));
		int bg = DosColours.BODY_BG;
		g.put(1, 1, String.format(Locale.ROOT, "  %-10s %9s %7s %5s %7s %4s %-8s %s", Dos.tr("rptdir.col.call"), Dos.tr("rptdir.col.output"),
				Dos.tr("rptdir.col.offset"), Dos.tr("rptdir.col.tone"), Dos.tr("rptdir.col.distance"), Dos.tr("rptdir.col.bearing"),
				Dos.tr("rptdir.col.heard"), Dos.tr("rptdir.col.notes")), DosColours.YELLOW, bg);
		g.hline(1, 2, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
		if(!loaded)
			g.put(2, LIST_ROW, Dos.tr("rptdir.collecting"), DosColours.TEXT_FG, bg);
		else if(rows.isEmpty()) {
			g.put(2, LIST_ROW, Dos.fit(Dos.tr("rptdir.empty"), TextGrid.COLS - 4), DosColours.TEXT_FG, bg);
			g.put(2, LIST_ROW + 1, Dos.fit(Dos.tr("rptdir.empty_hint"), TextGrid.COLS - 4), DosColours.LIGHT_CYAN, bg);
		}
		else
			list.render(g, 1, LIST_ROW, TextGrid.COLS - 2, LIST_ROWS, DosColours.TEXT_FG, bg, !hasDialog());
		Row row = selectedRow();
		if(row != null) {
			String input = Dos.tr("rptdir.input", FrequencyList.mhz(row.out() + row.offset() / 1000.0D), row.tone() > 0.0F ? Dos.tr("rptdir.with_tone", tone(row.tone())) : Dos.tr("rptdir.no_tone"));
			String where = row.hasPos() ? Dos.tr("rptdir.at", row.x(), row.z()) : "";
			g.putField(1, 22, Dos.fit(input + where, TextGrid.COLS - 2), TextGrid.COLS - 2, DosColours.LIGHT_CYAN, bg);
		}
		String message = session.message();
		String status = Dos.tr("rptdir.status", Dos.tr("rptdir.sort." + sort.name().toLowerCase(Locale.ROOT)), marked.size(), radios);
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(!loaded)
			return false;
		switch(key) {
			case GLFW.GLFW_KEY_INSERT, GLFW.GLFW_KEY_F4 -> add();
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_SPACE -> toggleMark();
			case GLFW.GLFW_KEY_F3 -> {
				sort = sort.next();
				resort();
			}
			case GLFW.GLFW_KEY_F5 -> refresh(true);
			case GLFW.GLFW_KEY_F6 -> print();
			case GLFW.GLFW_KEY_F7 -> export();
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
