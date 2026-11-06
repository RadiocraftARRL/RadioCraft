package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.StringUtil;

import java.util.Locale;

/**
 * Small helpers for the radio computer's programs: their words ({@link #tr}, keys under
 * {@code screen.radiocraft.computer.}), sizes as DOS shows them, the standard frame (title bar, body, key bar) and the
 * keyboard's click.
 */
public final class Dos {

	public static final String PREFIX = "screen.radiocraft.computer.";
	/** The rows a program has between its title bar (row 0) and its key bar (row 24). */
	public static final int BODY_TOP = 1, BODY_BOTTOM = TextGrid.ROWS - 2;

	/** What the top bar shows at its right (SETUP's computer name and clock), set by the screen each frame. */
	private static String status = "";
	/** The column after the status's last character. */
	private static int statusEnd = TextGrid.COLS - 1;

	private Dos() {}

	/**
	 * Sets the top bar's right-hand text (the computer's name and clock) for the frames drawn from now, ending before
	 * column {@code end}. The radio computer's screen calls this every frame.
	 */
	public static void setStatus(String text, int end) {
		status = text == null ? "" : text;
		statusEnd = end;
	}

	/** The top bar's right-hand text (name and clock), "" for none. */
	public static String status() {
		return status;
	}

	/** The first column of the status in the top bar ({@link TextGrid#COLS} with none): draw your own words left of it. */
	public static int statusStart() {
		return status.isEmpty() ? TextGrid.COLS : statusEnd - status.length();
	}

	/** {@code screen.radiocraft.computer.<key>}, formatted with {@code args}. */
	public static String tr(String key, Object... args) {
		return I18n.get(PREFIX + key, args);
	}

	/** A program's name. */
	public static String name(ComputerApp app) {
		return I18n.get(app.nameKey());
	}

	/** A program's one-line description. */
	public static String description(ComputerApp app) {
		return I18n.get(app.descriptionKey());
	}

	/** Bytes as DOS shows them: "512 bytes", "16K", "1.2M". */
	public static String size(long bytes) {
		if(bytes < 1024L)
			return bytes + " bytes";
		if(bytes < 1024L * 1024L)
			return (bytes + 1023L) / 1024L + "K";
		return String.format(Locale.ROOT, "%.1fM", bytes / (1024.0D * 1024.0D));
	}

	/** True if {@code c} may be typed into the computer (what chat allows: no formatting sign, no control characters). */
	public static boolean typeable(char c) {
		return StringUtil.isAllowedChatCharacter(c);
	}

	/**
	 * The standard program frame: {@code title} in the grey bar on row 0 (with the computer's name and clock at its
	 * right, {@link #status()}), the blue body, and {@code keys} (a key hint line such as "F2=Save  Esc=Exit") in the
	 * cyan bar on row 24.
	 */
	public static void frame(TextGrid g, String title, String keys) {
		g.clear(DosColours.BODY_FG, DosColours.BODY_BG);
		g.hline(0, 0, TextGrid.COLS, ' ', DosColours.TITLE_FG, DosColours.TITLE_BG);
		g.put(1, 0, title, DosColours.TITLE_FG, DosColours.TITLE_BG);
		if(!status.isEmpty())
			g.put(statusStart(), 0, status, DosColours.TITLE_FG, DosColours.TITLE_BG);
		keyBar(g, keys);
	}

	/**
	 * The cyan key bar on row 24; a key name before "=" shows in red, as EDIT's did. Hints are two spaces apart, or one
	 * if that is what it takes to fit them in the row.
	 */
	public static void keyBar(TextGrid g, String keys) {
		int row = TextGrid.ROWS - 1;
		g.hline(0, row, TextGrid.COLS, ' ', DosColours.KEYS_FG, DosColours.KEYS_BG);
		int col = 1;
		String[] parts = keys.split("  ");
		int gap = 1 + keys.length() <= TextGrid.COLS - 1 ? 2 : 1;
		for(String part : parts) {
			int eq = part.indexOf('=');
			if(eq > 0) {
				col = g.put(col, row, part.substring(0, eq), DosColours.HOTKEY_FG, DosColours.KEYS_BG);
				col = g.put(col, row, part.substring(eq), DosColours.KEYS_FG, DosColours.KEYS_BG);
			}
			else
				col = g.put(col, row, part, DosColours.KEYS_FG, DosColours.KEYS_BG);
			col += gap;
		}
	}

	/** What the computer's floppy drive holds, in words ("Drive empty", "Blank disk", "Logbook (LOGBOOK.EXE)"). */
	public static String drive(ComputerSession session) {
		String floppy = session.floppy();
		if(floppy.isEmpty())
			return tr("drive.empty");
		if(floppy.equals("blank"))
			return tr("drive.blank");
		ComputerApp app = com.arrl.radiocraft.common.computer.ComputerApps.get(floppy);
		return app == null ? floppy : name(app) + " (" + app.fileName() + ")";
	}

	/** {@code text} cut to {@code width}, with "..." if it was longer. */
	public static String fit(String text, int width) {
		if(text.length() <= width)
			return text;
		return width <= 3 ? text.substring(0, Math.max(0, width)) : text.substring(0, width - 3) + "...";
	}

	/**
	 * True for the save key: Ctrl+S, or F2. Minecraft takes F2 for screenshots before a screen ever sees it (and F11
	 * for full screen), so programs show "^S=Save" and take F2 too only for players who moved the screenshot key.
	 */
	public static boolean isSaveKey(int key) {
		return key == org.lwjgl.glfw.GLFW.GLFW_KEY_F2 || key == org.lwjgl.glfw.GLFW.GLFW_KEY_S && ctrl();
	}

	/** True while Ctrl is held. */
	public static boolean ctrl() {
		return net.minecraft.client.gui.screens.Screen.hasControlDown();
	}

	/** A key of the keyboard, quietly (the H19's). */
	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(RadiocraftSoundEvents.H19_TYPE.get(), 0.95F + (float)Math.random() * 0.1F, 0.2F));
	}
}
