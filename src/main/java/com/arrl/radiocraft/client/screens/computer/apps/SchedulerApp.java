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
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.computer.Schedule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SCHEDULE, the scheduler (computer batch 3A): the computer's timed jobs, which the server runs with nobody at the
 * screen ({@code SchedulerHandler}). Ins adds a job (pick its kind, then its time and details), Enter edits, Del
 * deletes, Space switches one on or off, F5 runs it now. The bottom of the screen is the log of the last runs. Only
 * the computer's owner (or an operator) may change the jobs; anyone can look.
 * <p>
 * A job's time is typed: {@code every 30} (minutes, or {@code every 2h}), {@code at 20:00} (the world's clock, as
 * SETUP's in-game clock shows it), {@code at 20:00 real} (the server's clock).
 */
public final class SchedulerApp extends DosApp {

	private static final int LIST_ROW = 4, LIST_ROWS = 10, LOG_ROW = 16, LOG_ROWS = 6;

	private final DosList list = new DosList();
	private final List<Schedule.Job> jobs = new ArrayList<>();
	private final List<Long> next = new ArrayList<>();
	private final List<CompoundTag> log = new ArrayList<>();
	private CompoundTag station = new CompoundTag();
	private boolean loaded = false, owner = false, aprs = false, wefax = false;
	/** When the status came, ms: the "next" times count down from it. */
	private long statusAt = 0L;

	public SchedulerApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(i -> edit());
	}

	@Override
	public void opened() {
		refresh();
	}

	@Override
	public void resumed() {
		refresh();
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if("ran".equals(kind))
			refresh();
	}

	private void refresh() {
		session.app(app, "status", new CompoundTag(), this::took);
	}

	/** Takes a status answer (every request answers one). */
	private void took(ComputerResponse response) {
		if(!response.ok()) {
			showError(response);
			return;
		}
		CompoundTag data = response.data();
		loaded = true;
		statusAt = System.currentTimeMillis();
		station = data;
		owner = data.getBoolean("owner");
		aprs = data.getBoolean("aprs");
		wefax = data.getBoolean("wefax");
		int selected = list.selected();
		jobs.clear();
		next.clear();
		ListTag entries = data.getList("jobs", Tag.TAG_COMPOUND);
		for(int i = 0; i < entries.size(); i++) {
			jobs.add(Schedule.Job.fromTag(entries.getCompound(i)));
			next.add(entries.getCompound(i).getLong("next"));
		}
		log.clear();
		ListTag ran = data.getList("log", Tag.TAG_COMPOUND);
		for(int i = 0; i < ran.size(); i++)
			log.add(ran.getCompound(i));
		List<String> lines = new ArrayList<>();
		for(Schedule.Job job : jobs)
			lines.add("");
		list.setItems(lines);
		if(selected >= 0)
			list.select(Math.min(selected, jobs.size() - 1));
		if(data.contains("result")) {
			String result = data.getString("result");
			session.message(result.isEmpty() ? Dos.tr("sched.ran_now") : Dos.tr("err." + result), !result.isEmpty());
		}
	}

	// -------------------- WORDS --------------------

	private static String kindName(Schedule.Kind kind) {
		return Dos.tr("sched.kind." + kind.id());
	}

	/** "every 30 min", "20:00 world", "20:00 real". */
	private static String when(Schedule.Job job) {
		int m = job.minutes();
		return switch(job.when()) {
			case EVERY -> m % 60 == 0 ? Dos.tr("sched.every_h", m / 60) : Dos.tr("sched.every_min", m);
			case DAILY_GAME -> Dos.tr("sched.daily_game", ComputerSettings.time(m / 60, m % 60, true));
			case DAILY_REAL -> Dos.tr("sched.daily_real", ComputerSettings.time(m / 60, m % 60, true));
		};
	}

	/** "4m 10s", "2h 05m", "now". */
	private static String countdown(long seconds) {
		if(seconds <= 0L)
			return Dos.tr("sched.now");
		if(seconds < 3600L)
			return String.format(Locale.ROOT, "%dm %02ds", seconds / 60L, seconds % 60L);
		return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600L, seconds / 60L % 60L);
	}

	private static String details(Schedule.Job job) {
		return switch(job.kind()) {
			case STATION_ID -> Dos.tr("sched.details.id");
			case WEFAX -> job.file();
			case BEACON -> Dos.tr("sched.details.beacon");
			case REMINDER -> (job.to().isEmpty() ? "" : job.to() + ": ") + job.text();
			case FT8_CQ -> Dos.tr("sched.details.ft8", com.arrl.radiocraft.common.computer.apps.Ft8Handler.SCHEDULED_CQ_MINUTES);
		};
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("sched.title", jobs.size(), Schedule.MAX_JOBS), Dos.tr("sched.keys"));
		int bg = DosColours.BODY_BG;
		// The station, as the jobs will find it.
		String call = station.getString("callsign");
		String where = !station.getBoolean("interface") ? Dos.tr("sched.no_interface")
				: Dos.tr("sched.station", call.isEmpty() ? "?" : call,
				station.getBoolean("radioOn") ? String.format(Locale.ROOT, "%.3f MHz", station.getFloat("mhz")) : Dos.tr("sched.radio_off"));
		g.put(2, 1, Dos.fit(where, TextGrid.COLS - 4), station.getBoolean("radioOn") ? DosColours.GOOD : DosColours.ACCENT, bg);
		if(!owner && loaded)
			g.put(TextGrid.COLS - 2 - Dos.tr("sched.look_only").length(), 1, Dos.tr("sched.look_only"), DosColours.DIM, bg);
		g.put(1, 2, String.format(Locale.ROOT, " %-2s %-3s %-12s %-16s %-9s %s", "#", Dos.tr("sched.col.on"), Dos.tr("sched.col.what"),
				Dos.tr("sched.col.when"), Dos.tr("sched.col.next"), Dos.tr("sched.col.details")), DosColours.YELLOW, bg);
		g.hline(1, 3, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
		long gone = (now - statusAt) / 1000L;
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < jobs.size(); i++) {
			Schedule.Job job = jobs.get(i);
			long left = next.get(i) - gone;
			if(job.when() != Schedule.When.EVERY && left < 0)
				left += 24L * 3600L; // Past it: tomorrow's (a world day is 20 minutes; near enough until the next refresh).
			lines.add(String.format(Locale.ROOT, " %-2d %-3s %-12s %-16s %-9s %s", job.id(), job.enabled() ? Dos.tr("sched.on") : Dos.tr("sched.off"),
					Dos.fit(kindName(job.kind()), 12), Dos.fit(when(job), 16), job.enabled() ? countdown(Math.max(0L, left)) : "-", details(job)));
		}
		if(lines.size() == list.size())
			list.setItems(lines);
		if(!loaded)
			g.put(3, LIST_ROW, Dos.tr("sched.reading"), DosColours.TEXT_FG, bg);
		else if(jobs.isEmpty()) {
			g.put(3, LIST_ROW, Dos.tr("sched.empty"), DosColours.TEXT_FG, bg);
			g.put(3, LIST_ROW + 1, Dos.fit(Dos.tr("sched.empty_hint"), TextGrid.COLS - 6), DosColours.LIGHT_CYAN, bg);
		}
		else
			list.render(g, 1, LIST_ROW, TextGrid.COLS - 2, LIST_ROWS, DosColours.TEXT_FG, bg, !hasDialog());

		g.box(1, LOG_ROW - 1, TextGrid.COLS - 2, LOG_ROWS + 2, false, Dos.tr("sched.log"), DosColours.TEXT_FG, bg);
		if(log.isEmpty())
			g.put(3, LOG_ROW, Dos.tr("sched.log_empty"), DosColours.DIM, bg);
		for(int i = 0; i < log.size() && i < LOG_ROWS; i++) {
			CompoundTag ran = log.get(i);
			java.time.LocalTime at = java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(ran.getLong("utc")), java.time.ZoneId.systemDefault());
			String result = ran.getString("result");
			String text = String.format(Locale.ROOT, "%s  #%-2d %-12s %s", ComputerSettings.time(at.getHour(), at.getMinute(), session.settings().hour24()),
					ran.getInt("job"), Dos.fit(kindName(Schedule.Kind.byId(ran.getString("kind"))), 12), result.isEmpty() ? Dos.tr("sched.done") : Dos.tr("err." + result));
			g.put(3, LOG_ROW + i, Dos.fit(text, TextGrid.COLS - 6), result.isEmpty() ? DosColours.GOOD : DosColours.BAD, bg);
		}
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, TextGrid.ROWS - 2, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.ERROR_FG : DosColours.DIALOG_FG,
					session.messageIsError() ? DosColours.ERROR_BG : DosColours.DIALOG_BG);
	}

	// -------------------- EDITING --------------------

	private Schedule.Job selected() {
		int i = list.selected();
		return i >= 0 && i < jobs.size() ? jobs.get(i) : null;
	}

	private boolean mayChange() {
		if(!owner)
			session.message(Dos.tr("err.not_owner_sched"), true);
		return owner;
	}

	private void add() {
		if(!mayChange())
			return;
		if(jobs.size() >= Schedule.MAX_JOBS) {
			session.message(Dos.tr("err.sched_full", Schedule.MAX_JOBS), true);
			return;
		}
		Schedule.Kind[] kinds = Schedule.Kind.values();
		String[] buttons = new String[kinds.length];
		for(int i = 0; i < kinds.length; i++)
			buttons[i] = kindName(kinds[i]);
		showDialog(DosDialog.choice(Dos.tr("sched.add_title"), Dos.tr("sched.add_which"), buttons, i -> {
			if(i >= 0 && i < kinds.length)
				form(new Schedule.Job(0, kinds[i], kinds[i] == Schedule.Kind.REMINDER ? Schedule.When.DAILY_GAME : Schedule.When.EVERY,
						kinds[i] == Schedule.Kind.REMINDER ? 20 * 60 : kinds[i] == Schedule.Kind.STATION_ID ? 10 : kinds[i] == Schedule.Kind.FT8_CQ ? 60 : 30, true, "", "", "", 0L));
		}));
	}

	private void edit() {
		Schedule.Job job = selected();
		if(job != null && mayChange())
			form(job);
	}

	/** The job's form: its time, and what that kind needs. */
	private void form(Schedule.Job job) {
		List<DosFormDialog.Field> fields = new ArrayList<>();
		fields.add(new DosFormDialog.Field(Dos.tr("sched.f.when"), new DosInput(Schedule.typed(job.when(), job.minutes()), 20)));
		if(job.kind() == Schedule.Kind.WEFAX)
			fields.add(new DosFormDialog.Field(Dos.tr("sched.f.file"), DosInput.fileName(job.file().isEmpty() ? "CHART.IMG" : job.file())));
		if(job.kind() == Schedule.Kind.REMINDER) {
			fields.add(new DosFormDialog.Field(Dos.tr("sched.f.text"), new DosInput(job.text(), Schedule.TEXT_MAX, c -> c >= ' ' && c <= '~')));
			fields.add(new DosFormDialog.Field(Dos.tr("sched.f.to"), new DosInput(job.to(), 12)));
		}
		String hint = Dos.tr("sched.form_hint") + switch(job.kind()) {
			case WEFAX -> wefax ? "" : " " + Dos.tr("err.sched_need_wefax");
			case BEACON -> aprs ? "" : " " + Dos.tr("err.sched_need_aprs");
			default -> "";
		};
		String title = Dos.tr(job.id() == 0 ? "sched.add_kind" : "sched.edit_kind", kindName(job.kind()));
		showDialog(new DosFormDialog(title, hint, fields, values -> {
			Object[] when = Schedule.parseWhen(values.getFirst());
			if(when == null)
				return Dos.tr("sched.bad_when");
			String file = job.kind() == Schedule.Kind.WEFAX ? values.get(1) : "";
			String text = job.kind() == Schedule.Kind.REMINDER ? values.get(1) : "";
			String to = job.kind() == Schedule.Kind.REMINDER ? values.get(2) : "";
			Schedule.Job made = new Schedule.Job(job.id(), job.kind(), (Schedule.When)when[0], (Integer)when[1], job.enabled(), file, text, to, 0L);
			String problem = made.problem();
			if(problem != null)
				return Dos.tr("err." + problem);
			CompoundTag data = new CompoundTag();
			data.put("job", made.toTag());
			session.app(app, "save", data, this::took);
			return null;
		}));
	}

	private void delete() {
		Schedule.Job job = selected();
		if(job == null || !mayChange())
			return;
		showDialog(DosDialog.confirm(Dos.tr("sched.delete_title"), Dos.tr("sched.delete", job.id(), kindName(job.kind())), () -> withId("delete", job)));
	}

	private void withId(String action, Schedule.Job job) {
		CompoundTag data = new CompoundTag();
		data.putInt("id", job.id());
		session.app(app, action, data, this::took);
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		Schedule.Job job = selected();
		switch(key) {
			case GLFW.GLFW_KEY_INSERT, GLFW.GLFW_KEY_F4 -> add();
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_SPACE -> {
				if(job != null && mayChange())
					withId("toggle", job);
			}
			case GLFW.GLFW_KEY_F5 -> {
				if(job != null && mayChange())
					withId("run", job);
			}
			default -> {
				return list.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return list.contains(col, row) && list.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return list.mouseScrolled(col, row, amount);
	}
}
