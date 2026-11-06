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
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.NetLog;
import com.arrl.radiocraft.common.computer.Schedule;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NETLOG, the Net Control logger (computer batch 3C): the net control station's check-in list. F4 sets up a net (name,
 * frequency, mode, NCS) and opens it; Ins checks a station in (call, name, location or grid, traffic, short time; a
 * station already in is rechecked), Enter edits, Del removes, O checks out (or back in), T and S flag traffic and short
 * time, R counts a recheck, N steps the roll call (short time first, then traffic, then the rest), Ctrl+Up/Down moves
 * a station in the list. F5 prints the net report on the scanner, F6 saves it as a text file, F8 closes the net and
 * offers to log every check-in in the logbook, F9 sets a Scheduler reminder for the next net. The net is saved to its
 * {@code .NET} file at every change ({@code NETLOG.NET} to start; F3 opens another).
 */
public final class NetControlApp extends DosApp {

	public static final String DEFAULT_FILE = "NETLOG.NET";
	public static final String EXTENSION = "NET";
	private static final int LIST_ROW = 4, LIST_ROWS = 18;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm", Locale.ROOT).withZone(ZoneOffset.UTC);

	private NetLog net = new NetLog();
	private String fileName = DEFAULT_FILE;
	private final DosList list = new DosList();
	private boolean loaded = false;
	private int rollCall = -1;

	public NetControlApp(ComputerSession session, ComputerApp app) {
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
					net = new NetLog();
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
			net = NetLog.fromTag(tag);
			fileName = ComputerStorage.normalise(name);
			loaded = true;
			rollCall = -1;
			rebuild();
		});
	}

	private void save() {
		session.files().write(fileName, ComputerFileFormats.nbt(net.toTag()), response -> {
			if(!response.ok())
				showError(response);
		});
	}

	private void changed() {
		rebuild();
		save();
	}

	private void rebuild() {
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < net.size(); i++)
			lines.add(line(i, net.get(i), i == rollCall));
		int keep = list.selected();
		list.setItems(lines);
		if(keep >= 0 && keep < lines.size())
			list.select(keep);
	}

	private static String line(int index, NetLog.CheckIn c, boolean calling) {
		String flags = (c.traffic() ? "TFC " : "") + (c.shortTime() ? "ST " : "") + (c.rechecks() > 0 ? "R" + c.rechecks() : "");
		return String.format(Locale.ROOT, "%s%2d %-10s %-16s %-20s %4s %4s %s", calling ? TextGrid.ARROW_RIGHT : ' ', index + 1, c.call(), c.name(), c.location(),
				time(c.inUtc()), c.in() ? "" : time(c.outUtc()), flags.strip());
	}

	private static String time(long utc) {
		return utc == 0L ? "" : TIME.format(Instant.ofEpochMilli(utc));
	}

	// -------------------- THE NET --------------------

	private void setup(boolean openAfter) {
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("netlog.name"), new DosInput(net.name(), NetLog.NAME_MAX)),
				new DosFormDialog.Field(Dos.tr("netlog.freq"), new DosInput(net.frequency(), NetLog.FREQ_MAX, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("netlog.mode"), new DosInput(net.mode(), NetLog.MODE_MAX)),
				new DosFormDialog.Field(Dos.tr("netlog.ncs"), new DosInput(net.ncs(), NetLog.CALL_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '/')));
		showDialog(new DosFormDialog(Dos.tr(openAfter ? "netlog.new_title" : "netlog.setup_title"), Dos.tr("netlog.setup_hint"), fields, values -> {
			if(values.get(0).isBlank())
				return Dos.tr("netlog.need_name");
			if(openAfter) {
				net = new NetLog();
				rollCall = -1;
			}
			net.setup(values.get(0), values.get(1), values.get(2), values.get(3));
			if(openAfter) {
				net.open(System.currentTimeMillis());
				String base = values.get(0).replaceAll("[^A-Za-z0-9]", "");
				fileName = ComputerStorage.suggest(base.isEmpty() ? "NETLOG" : base.length() > 8 ? base.substring(0, 8) : base, EXTENSION);
				session.message(Dos.tr("netlog.opened", net.name(), fileName), false);
			}
			changed();
			return null;
		}));
	}

	private void checkIn() {
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("netlog.call"), new DosInput("", NetLog.CALL_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '/')),
				new DosFormDialog.Field(Dos.tr("netlog.op"), new DosInput("", NetLog.OP_MAX)),
				new DosFormDialog.Field(Dos.tr("netlog.location"), new DosInput("", NetLog.LOCATION_MAX)),
				new DosFormDialog.Field(Dos.tr("netlog.traffic"), new DosInput("N", 1, c -> "YNyn".indexOf(c) >= 0)),
				new DosFormDialog.Field(Dos.tr("netlog.short"), new DosInput("N", 1, c -> "YNyn".indexOf(c) >= 0)));
		showDialog(new DosFormDialog(Dos.tr("netlog.checkin_title"), Dos.tr("netlog.checkin_hint"), fields, values -> {
			NetLog.Result result = net.checkIn(values.get(0), values.get(1), values.get(2), values.get(3).equalsIgnoreCase("Y"), values.get(4).equalsIgnoreCase("Y"),
					System.currentTimeMillis());
			if(result == NetLog.Result.NO_CALL)
				return Dos.tr("netlog.need_call");
			if(result == NetLog.Result.FULL)
				return Dos.tr("netlog.full", NetLog.MAX_CHECK_INS);
			changed();
			list.select(net.indexOf(values.get(0)));
			session.message(Dos.tr("netlog.result." + result.name().toLowerCase(Locale.ROOT), values.get(0).toUpperCase(Locale.ROOT), net.size()), false);
			return null;
		}));
	}

	private void edit(int index) {
		if(index < 0 || index >= net.size())
			return;
		NetLog.CheckIn c = net.get(index);
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("netlog.call"), new DosInput(c.call(), NetLog.CALL_MAX, ch -> Character.isLetterOrDigit(ch) && ch < 128 || ch == '/')),
				new DosFormDialog.Field(Dos.tr("netlog.op"), new DosInput(c.name(), NetLog.OP_MAX)),
				new DosFormDialog.Field(Dos.tr("netlog.location"), new DosInput(c.location(), NetLog.LOCATION_MAX)));
		showDialog(new DosFormDialog(Dos.tr("netlog.edit_title"), "", fields, values -> {
			if(values.get(0).isBlank())
				return Dos.tr("netlog.need_call");
			net.edit(index, values.get(0), values.get(1), values.get(2));
			changed();
			return null;
		}));
	}

	private void nextInRollCall() {
		rollCall = net.nextInRollCall(rollCall);
		if(rollCall < 0) {
			session.message(Dos.tr("netlog.nobody_in"), true);
			return;
		}
		rebuild();
		list.select(rollCall);
		NetLog.CheckIn c = net.get(rollCall);
		session.message(Dos.tr("netlog.calling", c.call(), c.name()), false);
	}

	private void printReport() {
		session.scanner().printText(reportTitle(), String.join("\n", net.summary()), response -> {
			if(response.ok())
				session.message(Dos.tr("netlog.printed"), false);
			else
				showError(response);
		});
	}

	private String reportTitle() {
		return net.name().isEmpty() ? Dos.tr("netlog.report") : net.name();
	}

	private void saveReport() {
		String base = fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
		String name = ComputerStorage.suggest(base, ComputerFileFormats.TEXT);
		session.files().writeText(name, String.join("\n", net.summary()) + "\n", response -> {
			if(response.ok())
				session.message(Dos.tr("netlog.saved_report", name), false);
			else
				showError(response);
		});
	}

	private void closeNet() {
		if(!net.isOpen()) {
			session.message(Dos.tr("netlog.not_open"), true);
			return;
		}
		showDialog(DosDialog.choice(Dos.tr("netlog.close_title"), Dos.tr("netlog.close_ask", net.size()),
				new String[] { Dos.tr("netlog.close_log"), Dos.tr("netlog.close_only"), Dos.tr("button.cancel") }, chosen -> {
			if(chosen < 0 || chosen == 2)
				return;
			net.close(System.currentTimeMillis());
			rollCall = -1;
			changed();
			if(chosen == 0 && net.size() > 0) {
				CompoundTag data = new CompoundTag();
				data.put("net", net.toTag());
				session.app(app, "log", data, response -> {
					if(response.ok())
						session.message(Dos.tr("netlog.logged", response.data().getInt("logged")), false);
					else
						showError(response);
				});
			}
			else
				session.message(Dos.tr("netlog.closed"), false);
		}));
	}

	/** A Scheduler reminder for the next net: the scheduler's own reminder job (needs SCHEDULE installed). */
	private void reminder() {
		if(!session.isInstalled(ComputerApps.SCHEDULER)) {
			session.message(Dos.tr("netlog.need_scheduler"), true);
			return;
		}
		showDialog(DosDialog.ask(Dos.tr("netlog.remind_title"), Dos.tr("netlog.remind_prompt"), new DosInput("at 20:00", 24), typed -> {
			Object[] when = Schedule.parseWhen(typed);
			if(when == null) {
				session.message(Dos.tr("netlog.remind_bad"), true);
				return;
			}
			String text = "NET " + (net.name().isEmpty() ? "" : net.name()) + (net.frequency().isEmpty() ? "" : " ON " + net.frequency() + " " + net.mode())
					+ (net.ncs().isEmpty() ? "" : " NCS " + net.ncs());
			Schedule.Job job = new Schedule.Job(0, Schedule.Kind.REMINDER, (Schedule.When)when[0], (Integer)when[1], true, "", text.strip(), "", 0L);
			CompoundTag data = new CompoundTag();
			data.put("job", job.toTag());
			session.app(ComputerApps.SCHEDULER, "save", data, response -> {
				if(response.ok())
					session.message(Dos.tr("netlog.reminder_set", typed), false);
				else
					showError(response);
			});
		}));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		String title = net.name().isEmpty() ? Dos.tr("netlog.title_none", fileName) : Dos.tr("netlog.title", net.name(), fileName);
		Dos.frame(g, title, Dos.tr("netlog.keys"));
		int bg = DosColours.BODY_BG;
		String state = net.isOpen() ? Dos.tr("netlog.state_open", time(net.openedUtc())) : net.closedUtc() != 0L ? Dos.tr("netlog.state_closed", time(net.closedUtc()))
				: Dos.tr("netlog.state_none");
		g.put(1, 1, Dos.fit(Dos.tr("netlog.header", net.frequency().isEmpty() ? "-" : net.frequency(), net.mode(), net.ncs().isEmpty() ? "-" : net.ncs(), state),
				TextGrid.COLS - 2), net.isOpen() ? DosColours.GOOD : DosColours.TEXT_FG, bg);
		g.put(1, 2, String.format(Locale.ROOT, " %2s %-10s %-16s %-20s %4s %4s %s", "#", Dos.tr("netlog.col.call"), Dos.tr("netlog.col.name"),
				Dos.tr("netlog.col.location"), Dos.tr("netlog.col.in"), Dos.tr("netlog.col.out"), Dos.tr("netlog.col.flags")), DosColours.YELLOW, bg);
		g.hline(1, 3, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
		if(!loaded)
			g.put(2, LIST_ROW, Dos.tr("files.reading"), DosColours.TEXT_FG, bg);
		else if(list.isEmpty())
			g.put(2, LIST_ROW, Dos.tr(net.isOpen() ? "netlog.empty_open" : "netlog.empty"), DosColours.TEXT_FG, bg);
		else
			list.render(g, 1, LIST_ROW, TextGrid.COLS - 2, LIST_ROWS, DosColours.TEXT_FG, bg, !hasDialog());
		String message = session.message();
		String status = Dos.tr("netlog.status", net.size(), net.rollCall().size(), net.trafficCount());
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(!loaded)
			return false;
		int at = list.selected();
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		switch(key) {
			case GLFW.GLFW_KEY_INSERT -> checkIn();
			case GLFW.GLFW_KEY_DELETE -> {
				if(at >= 0)
					showDialog(DosDialog.confirm(Dos.tr("netlog.remove_title"), Dos.tr("netlog.remove", net.get(at).call()), () -> {
						net.remove(at);
						rollCall = -1;
						changed();
					}));
			}
			case GLFW.GLFW_KEY_O -> {
				net.toggleOut(at, System.currentTimeMillis());
				changed();
			}
			case GLFW.GLFW_KEY_T -> {
				net.toggleTraffic(at);
				changed();
			}
			case GLFW.GLFW_KEY_S -> {
				if(ctrl)
					save();
				else {
					net.toggleShortTime(at);
					changed();
				}
			}
			case GLFW.GLFW_KEY_R -> {
				net.recheck(at);
				changed();
			}
			case GLFW.GLFW_KEY_N -> nextInRollCall();
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
				if(!ctrl)
					return list.keyPressed(key);
				int to = net.move(at, key == GLFW.GLFW_KEY_UP ? -1 : 1);
				if(rollCall == at)
					rollCall = to;
				changed();
				list.select(to);
			}
			case GLFW.GLFW_KEY_F3 -> showDialog(new DosFilePicker(session, Dos.tr("netlog.open_title"), EXTENSION, name -> load(name, false)));
			case GLFW.GLFW_KEY_F4 -> setup(true);
			case GLFW.GLFW_KEY_F5 -> printReport();
			case GLFW.GLFW_KEY_F6 -> saveReport();
			case GLFW.GLFW_KEY_F7 -> setup(false);
			case GLFW.GLFW_KEY_F8 -> closeNet();
			case GLFW.GLFW_KEY_F9 -> reminder();
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
