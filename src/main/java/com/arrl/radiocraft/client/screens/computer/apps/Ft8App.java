package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.radio.ft8.Ft8;
import com.arrl.radiocraft.common.radio.ft8.Ft8Decode;
import com.arrl.radiocraft.common.radio.ft8.Ft8Message;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FT8 (computer batch 3B), in the manner of WSJT-X: the band activity list fills with each slot's decodes (time, SNR,
 * audio offset, message), CQs in green and anything for us in red; our own transmissions in yellow. Click a CQ (or a
 * station calling us), or pick it with the arrows and press Enter, to answer it; F3 calls CQ. The auto-sequencer runs
 * the contact (grid, report, R-report, RR73, 73) and the finished contact is logged to LOGBOOK.LOG by itself. Under the
 * list, the contact in progress, the next transmission and the passband (0-3000 Hz) with this slot's signals and our
 * offset. The slot clock runs on game time: 15 s slots, even and odd. Server side: {@code Ft8Handler}.
 */
public final class Ft8App extends DosApp {

	private static final int LIST_TOP = 3, LIST_ROWS = 12, PANEL_ROW = LIST_TOP + LIST_ROWS + 1, BAND_ROW = 20, BAND_LEFT = 2, BAND_WIDTH = 76;
	private static final double BAND_HZ = 3000.0D;
	private static final long STATUS_MS = 5_000L;

	/** One line of the band activity list: a decode, or one of our transmissions. */
	private record Line(@Nullable Ft8Decode decode, String text, boolean ours) {}

	private CompoundTag station = new CompoundTag();
	private CompoundTag seq = new CompoundTag();
	private final List<Line> lines = new ArrayList<>();
	private final List<String> qsos = new ArrayList<>();
	private final List<Ft8Decode> lastSlot = new ArrayList<>();
	private int selected = -1;
	private int scroll = 0; // Lines up from the bottom.
	private long lastStatus;

	public Ft8App(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		status(true);
	}

	@Override
	public void resumed() {
		status(true);
	}

	@Override
	public void tick() {
		if(System.currentTimeMillis() - lastStatus > STATUS_MS)
			status(false);
	}

	private void status(boolean all) {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			station = response.data();
			seq = station.getCompound("seq");
			if(station.contains("decodes")) {
				lines.clear();
				ListTag list = station.getList("decodes", Tag.TAG_COMPOUND);
				for(int i = 0; i < list.size(); i++) {
					Ft8Decode decode = Ft8Decode.fromTag(list.getCompound(i));
					lines.add(new Line(decode, decode.line(), false));
				}
				qsos.clear();
				ListTag log = station.getList("qsos", Tag.TAG_STRING);
				for(int i = 0; i < log.size(); i++)
					qsos.add(log.getString(i));
				selected = -1;
			}
		});
		if(all)
			scroll = 0;
	}

	@Override
	public void event(String kind, CompoundTag data) {
		switch(kind) {
			case "decodes" -> {
				seq = data.getCompound("seq");
				lastSlot.clear();
				ListTag list = data.getList("decodes", Tag.TAG_COMPOUND);
				for(int i = 0; i < list.size(); i++) {
					Ft8Decode decode = Ft8Decode.fromTag(list.getCompound(i));
					lastSlot.add(decode);
					add(new Line(decode, decode.line(), false));
				}
			}
			case "tx" -> {
				if(data.contains("problem"))
					session.message(Dos.tr("ft8.tx_failed", Dos.tr("err." + data.getString("problem"))), true);
				else {
					seq = data.getCompound("seq");
					add(new Line(null, Ft8.clock(data.getLong("slot")) + "   TX " + String.format(Locale.ROOT, "%5d", data.getInt("offset")) + "  " + data.getString("text"), true));
				}
			}
			case "logged" -> {
				qsos.add(data.getString("line"));
				while(qsos.size() > 20)
					qsos.removeFirst();
				String problem = data.getString("problem");
				session.message(problem.isEmpty() ? Dos.tr("ft8.logged", data.getString("call")) : Dos.tr("ft8.log_failed", Dos.tr("err." + problem)), !problem.isEmpty());
			}
			default -> {}
		}
	}

	private void add(Line line) {
		lines.add(line);
		while(lines.size() > 200) {
			lines.removeFirst();
			if(selected >= 0)
				selected--;
		}
	}

	// -------------------- ACTIONS --------------------

	private void act(String action, CompoundTag data) {
		session.app(app, action, data, this::answered);
	}

	private void answered(ComputerResponse response) {
		if(!response.ok()) {
			session.message(response.errorText(), true);
			return;
		}
		station = response.data();
		seq = station.getCompound("seq");
	}

	/** Answers the decode on line {@code index}, if it is a CQ or a station calling us. */
	private boolean answer(int index) {
		if(index < 0 || index >= lines.size() || lines.get(index).decode() == null)
			return false;
		Ft8Decode decode = lines.get(index).decode();
		Ft8Message message = decode.message();
		if(message == null || message.from().equals(station.getString("myCall")))
			return false;
		CompoundTag data = decode.toTag();
		act("answer", data);
		session.message(Dos.tr("ft8.answering", message.from()), false);
		return true;
	}

	private void offset() {
		DosInput field = new DosInput(Integer.toString(seq.getInt("offset")), 4, Character::isDigit);
		showDialog(DosDialog.ask(Dos.tr("ft8.offset_title"), Dos.tr("ft8.offset_prompt", Ft8.MIN_OFFSET_HZ, Ft8.MAX_OFFSET_HZ), field, value -> {
			CompoundTag data = new CompoundTag();
			try {
				data.putInt("hz", Integer.parseInt(value));
			}
			catch(NumberFormatException e) {
				return;
			}
			act("offset", data);
		}));
	}

	// -------------------- KEYS AND MOUSE --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> act("cq", new CompoundTag());
			case GLFW.GLFW_KEY_F4 -> act("halt", new CompoundTag());
			case GLFW.GLFW_KEY_F5 -> {
				CompoundTag data = new CompoundTag();
				data.putBoolean("on", !seq.getBoolean("tx"));
				act("tx", data);
			}
			case GLFW.GLFW_KEY_F6 -> act("dial", new CompoundTag());
			case GLFW.GLFW_KEY_F7 -> offset();
			case GLFW.GLFW_KEY_F8 -> {
				CompoundTag data = new CompoundTag();
				data.putBoolean("even", !seq.getBoolean("even"));
				act("parity", data);
			}
			case GLFW.GLFW_KEY_F9 -> {
				lines.clear();
				selected = -1;
				act("clear", new CompoundTag());
			}
			case GLFW.GLFW_KEY_UP -> selected = selected < 0 ? lines.size() - 1 : Math.max(0, selected - 1);
			case GLFW.GLFW_KEY_DOWN -> selected = selected < 0 ? lines.size() - 1 : Math.min(lines.size() - 1, selected + 1);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if(!answer(selected))
					session.message(Dos.tr("ft8.pick_first"), false);
			}
			case GLFW.GLFW_KEY_PAGE_UP -> scroll += LIST_ROWS - 1;
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.max(0, scroll - (LIST_ROWS - 1));
			case GLFW.GLFW_KEY_END -> {
				scroll = 0;
				selected = -1;
			}
			default -> {
				return false;
			}
		}
		Dos.click();
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row < LIST_TOP || row >= LIST_TOP + LIST_ROWS)
			return false;
		int index = first() + row - LIST_TOP;
		if(index < 0 || index >= lines.size())
			return false;
		selected = index;
		answer(index); // A CQ or a call to us: answered at once.
		return true;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		scroll = Math.max(0, scroll + (amount > 0 ? 3 : -3));
		return true;
	}

	/** The first list line on screen. */
	private int first() {
		int max = Math.max(0, lines.size() - LIST_ROWS);
		scroll = Math.min(scroll, max);
		if(selected >= 0) { // Keep the selection in view.
			int top = Math.max(0, lines.size() - LIST_ROWS - scroll);
			if(selected < top)
				scroll = lines.size() - LIST_ROWS - selected;
			else if(selected >= top + LIST_ROWS)
				scroll = Math.max(0, lines.size() - selected - 1);
			scroll = Math.max(0, Math.min(scroll, max));
		}
		return Math.max(0, lines.size() - LIST_ROWS - scroll);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		String myCall = station.getString("myCall");
		Dos.frame(g, Dos.tr("ft8.title", myCall.isEmpty() ? "-" : myCall, station.getString("myGrid"),
				String.format(Locale.ROOT, "%.3f", station.getFloat("mhz"))), Dos.tr("ft8.keys"));
		int bg = DosColours.BODY_BG, field = DosColours.FIELD_BG;
		String problem = station.getString("problem");
		if(!problem.isEmpty())
			g.putField(0, 1, " " + Dos.tr("err." + problem), TextGrid.COLS, DosColours.ERROR_FG, DosColours.ERROR_BG);
		else if(!station.getBoolean("onDial") && station.getBoolean("radioOn"))
			g.putField(0, 1, " " + Dos.tr("ft8.off_dial"), TextGrid.COLS, DosColours.BLACK, DosColours.YELLOW);
		else
			g.putField(0, 1, " " + Dos.tr("ft8.listening", station.getString("band").equals("low_band") ? "" : station.getString("band")), TextGrid.COLS, DosColours.BLACK, DosColours.LIGHT_GREY);
		drawClock(g);
		// The band activity list.
		g.putField(0, LIST_TOP - 1, " " + Dos.tr("ft8.columns"), TextGrid.COLS, DosColours.TEXT_FG, bg);
		g.fill(0, LIST_TOP, TextGrid.COLS, LIST_ROWS, ' ', DosColours.TEXT_FG, field);
		int top = first();
		String me = station.getString("myCall");
		for(int i = 0; i < LIST_ROWS && top + i < lines.size(); i++) {
			Line line = lines.get(top + i);
			int fg = DosColours.LIGHT_GREY, back = field;
			if(line.ours())
				fg = DosColours.ACCENT;
			else if(line.decode() != null) {
				Ft8Message message = line.decode().message();
				if(message != null && !me.isEmpty() && message.isFor(me)) {
					fg = DosColours.WHITE;
					back = DosColours.RED;
				}
				else if(message != null && message.type() == Ft8Message.Type.CQ)
					fg = DosColours.LIGHT_GREEN;
			}
			if(top + i == selected) {
				fg = DosColours.HIGHLIGHT_FG;
				back = DosColours.HIGHLIGHT_BG;
			}
			g.putField(1, LIST_TOP + i, line.text(), TextGrid.COLS - 2, fg, back);
		}
		if(lines.isEmpty())
			g.put(2, LIST_TOP + 1, Dos.tr("ft8.empty"), DosColours.DIM, field);
		if(scroll > 0)
			g.put(TextGrid.COLS - 14, LIST_TOP, Dos.tr("rtty.scrolled"), DosColours.BLACK, DosColours.YELLOW);
		drawPanel(g, bg);
		drawPassband(g, field);
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
		else
			g.putField(0, 23, " " + Dos.tr("ft8.hint"), TextGrid.COLS, DosColours.BODY_FG, bg);
	}

	/** The slot clock: which slot, how far into it, and whether it is ours. */
	private void drawClock(TextGrid g) {
		Minecraft minecraft = Minecraft.getInstance();
		long gameTime = minecraft.level == null ? station.getLong("gameTime") : minecraft.level.getGameTime();
		long slot = Ft8.slot(gameTime);
		int in = Ft8.tickInSlot(gameTime);
		boolean even = Ft8.isEven(slot);
		boolean ours = seq.getBoolean("tx") && even == seq.getBoolean("even") && !seq.getString("next").isEmpty();
		String clock = Ft8.clock(slot) + " " + (even ? Dos.tr("ft8.even") : Dos.tr("ft8.odd"));
		int col = TextGrid.COLS - 30;
		g.put(col, 1, " " + clock + " ", DosColours.WHITE, ours ? DosColours.RED : DosColours.BLUE);
		g.bar(col + clock.length() + 3, 1, 10, in / (double)Ft8.SLOT_TICKS, ours ? DosColours.LIGHT_RED : DosColours.LIGHT_CYAN, DosColours.DARK_GREY);
	}

	private void drawPanel(TextGrid g, int bg) {
		int row = PANEL_ROW;
		g.hline(0, row - 1, TextGrid.COLS, TextGrid.H, DosColours.BODY_FG, bg);
		String step = seq.getString("step");
		boolean tx = seq.getBoolean("tx");
		g.put(1, row, Dos.tr("ft8.tx_enable"), DosColours.BODY_FG, bg);
		g.put(12, row, tx ? " ON  " : " OFF ", DosColours.WHITE, tx ? DosColours.RED : DosColours.DARK_GREY);
		g.put(19, row, Dos.tr("ft8.slot_choice", seq.getBoolean("even") ? Dos.tr("ft8.even") : Dos.tr("ft8.odd"), seq.getInt("offset")), DosColours.BODY_FG, bg);
		g.put(48, row, Dos.tr("ft8.qsos", seq.getInt("qsoCount")), DosColours.BODY_FG, bg);
		String dx = seq.getString("dx");
		String doing = step.isEmpty() ? "" : Dos.tr("ft8.step." + step.toLowerCase(Locale.ROOT), dx);
		g.putField(1, row + 1, doing, 46, DosColours.TEXT_FG, bg);
		String next = seq.getString("next");
		g.putField(1, row + 2, Dos.tr("ft8.next", next.isEmpty() ? "-" : next), 46, next.isEmpty() ? DosColours.DIM : DosColours.ACCENT, bg);
		String last = seq.getString("lastTx");
		g.putField(1, row + 3, Dos.tr("ft8.last", last.isEmpty() ? "-" : last), 46, DosColours.DIM, bg);
		// The last contacts, newest last.
		for(int i = 0; i < 3; i++) {
			int at = qsos.size() - 3 + i;
			if(at >= 0)
				g.putField(48, row + 1 + i, qsos.get(at), TextGrid.COLS - 49, DosColours.GOOD, bg);
		}
	}

	/** The audio passband, 0 to 3000 Hz: this slot's signals and where we transmit. */
	private void drawPassband(TextGrid g, int field) {
		g.set(BAND_LEFT - 1, BAND_ROW, '[', DosColours.BODY_FG, DosColours.BODY_BG);
		g.set(BAND_LEFT + BAND_WIDTH, BAND_ROW, ']', DosColours.BODY_FG, DosColours.BODY_BG);
		g.fill(BAND_LEFT, BAND_ROW, BAND_WIDTH, 1, TextGrid.SHADE_LIGHT, DosColours.BLUE, field);
		for(Ft8Decode decode : lastSlot) {
			int col = (int)(decode.offsetHz() / BAND_HZ * BAND_WIDTH);
			if(col >= 0 && col < BAND_WIDTH) {
				int level = decode.snr() >= 0 ? DosColours.YELLOW : decode.snr() >= -12 ? DosColours.LIGHT_CYAN : DosColours.LIGHT_BLUE;
				g.set(BAND_LEFT + col, BAND_ROW, TextGrid.FULL, level, field);
			}
		}
		int ours = (int)(seq.getInt("offset") / BAND_HZ * BAND_WIDTH);
		if(ours >= 0 && ours < BAND_WIDTH)
			g.set(BAND_LEFT + ours, BAND_ROW, TextGrid.ARROW_UP, DosColours.LIGHT_RED, field);
		g.put(BAND_LEFT, BAND_ROW + 1, "0", DosColours.DIM, DosColours.BODY_BG);
		g.put(BAND_LEFT + BAND_WIDTH / 2 - 2, BAND_ROW + 1, "1500 Hz", DosColours.DIM, DosColours.BODY_BG);
		g.put(BAND_LEFT + BAND_WIDTH - 4, BAND_ROW + 1, "3000", DosColours.DIM, DosColours.BODY_BG);
	}
}
