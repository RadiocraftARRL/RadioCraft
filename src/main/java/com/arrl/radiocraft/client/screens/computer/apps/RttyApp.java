package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.RttyCopy;
import com.arrl.radiocraft.common.radio.voice.Rtty;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RTTY (Computer apps 2): a radioteletype terminal in the style of the old DOS RTTY programs. The receive window fills
 * the screen, a line per station, our own copy in yellow; underneath, the type-ahead buffer in yellow on black: what
 * is typed goes into it at once and out on the air at 45.45 baud, about six characters a second, so a fast typist
 * runs ahead of the transmitter. Backspace takes back a character not sent yet, Ctrl+X drops the whole buffer. F3, F4
 * and F5 send the macros (CQ, the callsign exchange, the sign-off), F9 edits them, F10 sets the other station's call
 * ({CALL} in the macros), F6 sends a text file, F7 saves the receive window as {@code RTTY0001.TXT}, F8 clears it. The
 * server side is {@code RttyHandler}; the program keeps sending while the screen is closed.
 */
public final class RttyApp extends DosApp {

	private static final int TOP = 2, RX_ROWS = 16, BUFFER_ROW = 19, BUFFER_ROWS = 3;
	private static final long STATUS_MS = 5_000L;

	private final RttyCopy copy = new RttyCopy();
	private final StringBuilder outbox = new StringBuilder(); // Typed this tick, not sent to the server yet.
	private String pending = "";
	private CompoundTag station = new CompoundTag();
	private List<String> macros = new ArrayList<>(RttyCopy.DEFAULT_MACROS);
	private String call = "";
	private int scroll = 0; // Lines up from the bottom.
	private long lastStatus = 0L;
	private boolean loaded = false;

	public RttyApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		refresh(true);
	}

	@Override
	public void resumed() {
		refresh(false);
	}

	@Override
	public void tick() {
		if(!outbox.isEmpty()) {
			String text = outbox.toString();
			outbox.setLength(0);
			type(text);
		}
		if(System.currentTimeMillis() - lastStatus > STATUS_MS)
			refresh(false);
	}

	/** Asks for the station; with {@code all} also the receive window, macros and call (once, when opened). */
	private void refresh(boolean all) {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			CompoundTag data = response.data();
			station = data;
			pending = data.getString("pending");
			if(all || !loaded) {
				loaded = true;
				copy.load(data.getList("lines", Tag.TAG_COMPOUND));
				macros = new ArrayList<>();
				ListTag list = data.getList("macros", Tag.TAG_STRING);
				for(int i = 0; i < list.size(); i++)
					macros.add(list.getString(i));
				call = data.getString("call");
			}
		});
	}

	@Override
	public void event(String kind, CompoundTag data) {
		switch(kind) {
			case "rx" -> copy.print(data.getString("from"), data.getString("text"), data.getBoolean("own"));
			case "sent" -> pending = data.getString("pending");
			default -> {}
		}
	}

	private void type(String text) {
		session.app(app, "type", tag("text", text), response -> {
			if(!response.ok()) {
				session.message(response.errorText(), true);
				refresh(false);
			}
		});
	}

	private void macro(int n) {
		session.app(app, "macro", intTag("n", n), response -> {
			if(!response.ok())
				session.message(response.errorText(), true);
			refresh(false);
		});
	}

	private static CompoundTag tag(String key, String value) {
		CompoundTag tag = new CompoundTag();
		tag.putString(key, value);
		return tag;
	}

	private static CompoundTag intTag(String key, int value) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(key, value);
		return tag;
	}

	// -------------------- KEYS --------------------

	@Override
	protected boolean onChar(char c) {
		char upper = Character.toUpperCase(c);
		if(!Rtty.isPrintable(upper) || upper == '\n')
			return false;
		outbox.append(upper);
		pending += upper; // Shown at once; the server's answer puts it right.
		scroll = 0;
		Dos.click();
		return true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_X && Dos.ctrl()) {
			outbox.setLength(0);
			session.app(app, "abort", new CompoundTag(), response -> pending = response.data().getString("pending"));
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				outbox.append('\n');
				pending += "\n";
				Dos.click();
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if(!outbox.isEmpty())
					outbox.setLength(outbox.length() - 1);
				else
					session.app(app, "unsend", new CompoundTag(), response -> pending = response.data().getString("pending"));
				if(!pending.isEmpty())
					pending = pending.substring(0, pending.length() - 1);
			}
			case GLFW.GLFW_KEY_F3 -> macro(0);
			case GLFW.GLFW_KEY_F4 -> macro(1);
			case GLFW.GLFW_KEY_F5 -> macro(2);
			case GLFW.GLFW_KEY_F6 -> sendFile();
			case GLFW.GLFW_KEY_F7 -> save();
			case GLFW.GLFW_KEY_F8 -> showDialog(DosDialog.confirm(Dos.tr("rtty.clear_title"), Dos.tr("rtty.clear"), () -> {
				copy.clear();
				session.app(app, "clear", new CompoundTag(), response -> {});
			}));
			case GLFW.GLFW_KEY_F9 -> editMacros();
			case GLFW.GLFW_KEY_F10 -> editCall();
			case GLFW.GLFW_KEY_PAGE_UP -> scroll += RX_ROWS - 1;
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.max(0, scroll - (RX_ROWS - 1));
			case GLFW.GLFW_KEY_END -> scroll = 0;
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		scroll = Math.max(0, scroll + (amount > 0 ? 3 : -3));
		return true;
	}

	private void sendFile() {
		showDialog(new DosFilePicker(session, Dos.tr("rtty.send_title"), ComputerFileFormats.TEXT, name -> session.files().readText(name, (text, response) -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			type(text);
			session.message(Dos.tr("rtty.sending_file", name), false);
		})));
	}

	private void save() {
		session.app(app, "save", new CompoundTag(), response -> {
			if(response.ok())
				session.message(Dos.tr("edit.saved", response.data().getString("name")), false);
			else
				showError(response);
		});
	}

	/** A macro as the form shows it: a newline as \n, which the macro turns back into one. */
	private static String shown(String macro) {
		return macro.replace("\n", "\\n");
	}

	private void editMacros() {
		List<DosFormDialog.Field> fields = new ArrayList<>();
		String[] labels = { Dos.tr("rtty.macro_cq"), Dos.tr("rtty.macro_call"), Dos.tr("rtty.macro_73") };
		for(int i = 0; i < 3; i++)
			fields.add(new DosFormDialog.Field(labels[i], new DosInput(shown(i < macros.size() ? macros.get(i) : ""), RttyCopy.MACRO_MAX)));
		showDialog(new DosFormDialog(Dos.tr("rtty.macros_title"), Dos.tr("rtty.macros_help"), fields, values -> {
			ListTag list = new ListTag();
			macros = new ArrayList<>();
			for(String value : values) {
				list.add(StringTag.valueOf(value));
				macros.add(value);
			}
			CompoundTag data = new CompoundTag();
			data.put("macros", list);
			session.app(app, "settings", data, response -> {});
			return null;
		}));
	}

	private void editCall() {
		DosInput field = new DosInput(call, 12, c -> Character.isLetterOrDigit(c) && c < 128 || c == '/' || c == '-');
		showDialog(DosDialog.ask(Dos.tr("rtty.call_title"), Dos.tr("rtty.call_prompt"), field, value -> {
			call = value.toUpperCase(Locale.ROOT);
			session.app(app, "settings", tag("call", call), response -> {});
		}));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("rtty.title"), Dos.tr("rtty.keys"));
		int bg = DosColours.BODY_BG;
		// The station line: who we are, where, and whether we are sending.
		String problem = station.getString("problem");
		if(!problem.isEmpty())
			g.putField(0, 1, " " + Dos.tr("err." + problem), TextGrid.COLS, DosColours.ERROR_FG, DosColours.ERROR_BG);
		else {
			String band = station.getString("band");
			String line = Dos.tr("rtty.station", station.getString("myCall"), String.format(Locale.ROOT, "%.3f", station.getFloat("mhz")),
					band.equals("low_band") ? "" : band);
			g.putField(0, 1, " " + line, TextGrid.COLS, DosColours.BLACK, DosColours.LIGHT_GREY);
			boolean sending = !pending.isEmpty();
			String mode = sending ? " TX " : " RX ";
			g.put(TextGrid.COLS - 12, 1, mode, DosColours.WHITE, sending ? DosColours.RED : DosColours.GREEN);
		}
		if(!call.isEmpty())
			g.put(TextGrid.COLS - 7 - call.length() - 1, 1, call, DosColours.BLUE, DosColours.LIGHT_GREY);
		// The receive window, newest at the bottom.
		List<String> rows = new ArrayList<>();
		List<Boolean> own = new ArrayList<>();
		for(RttyCopy.Line line : copy.lines()) {
			rows.add(Dos.fit(line.from(), 10) + ": " + line.text());
			own.add(line.own());
		}
		int max = Math.max(0, rows.size() - RX_ROWS);
		scroll = Math.min(scroll, max);
		int first = Math.max(0, rows.size() - RX_ROWS - scroll);
		g.fill(0, TOP, TextGrid.COLS, RX_ROWS, ' ', DosColours.TEXT_FG, DosColours.BLACK);
		for(int i = 0; i < RX_ROWS && first + i < rows.size(); i++)
			g.putField(1, TOP + i, rows.get(first + i), TextGrid.COLS - 2, own.get(first + i) ? DosColours.YELLOW : DosColours.LIGHT_GREEN, DosColours.BLACK);
		if(rows.isEmpty())
			g.put(2, TOP + 1, Dos.tr("rtty.listening"), DosColours.DARK_GREY, DosColours.BLACK);
		if(scroll > 0)
			g.put(TextGrid.COLS - 14, TOP, Dos.tr("rtty.scrolled"), DosColours.BLACK, DosColours.YELLOW);
		// The type-ahead buffer.
		g.hline(0, BUFFER_ROW - 1, TextGrid.COLS, TextGrid.H, DosColours.BODY_FG, bg);
		g.put(2, BUFFER_ROW - 1, " " + Dos.tr("rtty.buffer", pending.length()) + " ", DosColours.WHITE, bg);
		g.fill(0, BUFFER_ROW, TextGrid.COLS, BUFFER_ROWS, ' ', DosColours.YELLOW, DosColours.BLACK);
		List<String> wrapped = wrap(pending, TextGrid.COLS - 2);
		int from = Math.max(0, wrapped.size() - BUFFER_ROWS);
		for(int i = 0; i + from < wrapped.size(); i++)
			g.put(1, BUFFER_ROW + i, wrapped.get(from + i), DosColours.YELLOW, DosColours.BLACK);
		int cursorRow = BUFFER_ROW + Math.max(0, Math.min(BUFFER_ROWS - 1, wrapped.size() - 1 - from));
		String last = wrapped.isEmpty() ? "" : wrapped.getLast();
		if(!hasDialog())
			g.cursor(Math.min(TextGrid.COLS - 1, 1 + last.length()), cursorRow);
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
		else
			g.putField(0, 23, " " + Dos.tr("rtty.hint"), TextGrid.COLS, DosColours.BODY_FG, bg);
	}

	/** {@code text} broken at newlines and at {@code width} characters (the buffer as it will print). */
	static List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c == '\n') {
				out.add(line.toString());
				line.setLength(0);
				continue;
			}
			if(line.length() >= width) {
				out.add(line.toString());
				line.setLength(0);
			}
			line.append(c);
		}
		out.add(line.toString());
		return out;
	}
}
