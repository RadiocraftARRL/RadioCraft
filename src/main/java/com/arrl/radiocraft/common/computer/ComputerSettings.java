package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * A radio computer's SETUP settings (1.12, launcher and SETUP): how the shell lists the programs ({@link View}), the
 * colour scheme every program is drawn in (an id the client knows, {@code DosScheme}; an unknown one shows as Classic
 * Blue), the computer's name and the clock shown in the top bar. Kept on the computer's disk
 * ({@link ComputerFileStore}, tag {@code settings}), so they follow the computer when it is broken and placed again,
 * like its password. Who may change them: {@code RadioComputerBlockEntity#mayChangePassword} (the owner, an operator,
 * or anyone while the computer has no owner).
 * <p>
 * Pure apart from {@link CompoundTag}, so it is unit tested.
 *
 * @param view   the shell's launcher: the classic list, the Program Manager's icons or the command line
 * @param scheme the colour scheme's id (lower case letters, digits and underscores)
 * @param name   the computer's name in the top bar, "" for none (at most {@link #MAX_NAME} printable ASCII characters)
 * @param clock  which clock the top bar shows, if any
 * @param hour24 24-hour time ("13:05") rather than 12-hour ("1:05p")
 * @param saverMinutes idle minutes before the screensaver comes on (one of {@link #SAVER_MINUTES}; 0 is never), if
 *                     the screensaver program is installed (computer batch 3A)
 * @param saverStyle   the screensaver's style, an id the client knows ({@code Screensaver.STYLES})
 */
public record ComputerSettings(View view, String scheme, String name, Clock clock, boolean hour24, int saverMinutes, String saverStyle) {

	/** Longest computer name. */
	public static final int MAX_NAME = 20;
	public static final String DEFAULT_SCHEME = "classic";
	/** The screensaver waits SETUP offers, minutes (0 = never). */
	public static final int[] SAVER_MINUTES = { 0, 1, 3, 5, 10 };
	public static final int DEFAULT_SAVER_MINUTES = 5;
	public static final String DEFAULT_SAVER_STYLE = "starfield";
	/**
	 * A new computer's: the Program Manager's icons (2026-11-09, was the list), Classic Blue, no name, the in-game clock in
	 * 24-hour time, the starfield after 5 minutes.
	 */
	public static final ComputerSettings DEFAULT = new ComputerSettings(View.ICONS, DEFAULT_SCHEME, "", Clock.GAME, true);

	/** How the shell shows the programs. */
	public enum View {
		/** RC-DOS Shell's two lists: programs and files. */
		LIST,
		/** The Program Manager: a window of icons. */
		ICONS,
		/** The bare RC-DOS prompt: commands typed (DIR, COPY, DEL, TYPE...), programs run by name. */
		COMMAND
	}

	/** The top bar's clock. */
	public enum Clock {
		/** The world's time of day (Minecraft's day: 0 ticks is 6:00). */
		GAME,
		/** The player's own computer's time. */
		REAL,
		/** No clock. */
		OFF
	}

	public ComputerSettings {
		view = view == null ? View.ICONS : view;
		scheme = scheme == null || !scheme.matches("[a-z0-9_]{1,24}") ? DEFAULT_SCHEME : scheme;
		name = cleanName(name);
		clock = clock == null ? Clock.GAME : clock;
		saverMinutes = nearestSaverMinutes(saverMinutes);
		saverStyle = saverStyle == null || !saverStyle.matches("[a-z0-9_]{1,24}") ? DEFAULT_SAVER_STYLE : saverStyle;
	}

	/** Settings with the default screensaver (the launcher's settings before computer batch 3A). */
	public ComputerSettings(View view, String scheme, String name, Clock clock, boolean hour24) {
		this(view, scheme, name, clock, hour24, DEFAULT_SAVER_MINUTES, DEFAULT_SAVER_STYLE);
	}

	/** {@code minutes} as one of {@link #SAVER_MINUTES}: the nearest at or below it (0 for none or less). */
	public static int nearestSaverMinutes(int minutes) {
		int out = 0;
		for(int step : SAVER_MINUTES)
			if(step <= minutes)
				out = step;
		return out;
	}

	/** The next ({@code step} 1) or previous (-1) screensaver wait, going round. */
	public ComputerSettings stepSaver(int step) {
		int at = 0;
		for(int i = 0; i < SAVER_MINUTES.length; i++)
			if(SAVER_MINUTES[i] == saverMinutes)
				at = i;
		return withSaverMinutes(SAVER_MINUTES[Math.floorMod(at + step, SAVER_MINUTES.length)]);
	}

	/** Idle ticks before the screensaver comes on, or -1 for never. */
	public long saverTicks() {
		return saverMinutes <= 0 ? -1L : saverMinutes * 60L * 20L;
	}

	/** {@code name} as a computer name: printable ASCII only, trimmed, at most {@link #MAX_NAME} long. */
	public static String cleanName(String name) {
		if(name == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < name.length() && out.length() < MAX_NAME; i++) {
			char c = name.charAt(i);
			if(c >= ' ' && c <= '~')
				out.append(c);
		}
		return out.toString().trim();
	}

	public ComputerSettings withView(View view) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	public ComputerSettings withScheme(String scheme) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	public ComputerSettings withName(String name) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	public ComputerSettings withClock(Clock clock) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	public ComputerSettings withHour24(boolean hour24) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	public ComputerSettings withSaverMinutes(int saverMinutes) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	public ComputerSettings withSaverStyle(String saverStyle) {
		return new ComputerSettings(view, scheme, name, clock, hour24, saverMinutes, saverStyle);
	}

	// -------------------- THE CLOCK --------------------

	/** The world's time of day ({@code Level#getDayTime}) as hours and minutes: 0 ticks is 6:00, 6000 noon. */
	public static int[] gameTime(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		int hours = (int)((t / 1000L + 6L) % 24L);
		int minutes = (int)(t % 1000L * 60L / 1000L);
		return new int[] { hours, minutes };
	}

	/** {@code hours:minutes} as this computer shows it: "13:05", or "1:05p" in 12-hour time. */
	public String time(int hours, int minutes) {
		return time(hours, minutes, hour24);
	}

	/** {@code hours:minutes} in 24-hour ("13:05", "07:30") or DOS's 12-hour time ("1:05p", "12:00a"). */
	public static String time(int hours, int minutes, boolean hour24) {
		if(hour24)
			return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
		int h = hours % 12 == 0 ? 12 : hours % 12;
		return String.format(Locale.ROOT, "%d:%02d%s", h, minutes, hours < 12 ? "a" : "p");
	}

	// -------------------- SAVED --------------------

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("view", view.name().toLowerCase(Locale.ROOT));
		tag.putString("scheme", scheme);
		tag.putString("name", name);
		tag.putString("clock", clock.name().toLowerCase(Locale.ROOT));
		tag.putBoolean("hour24", hour24);
		tag.putInt("saverMinutes", saverMinutes);
		tag.putString("saverStyle", saverStyle);
		return tag;
	}

	/** The settings in {@code tag}; anything missing or unknown is the default's. */
	public static ComputerSettings fromTag(CompoundTag tag) {
		if(tag == null || tag.isEmpty())
			return DEFAULT;
		return new ComputerSettings(
				parse(View.class, tag.getString("view"), DEFAULT.view),
				tag.contains("scheme") ? tag.getString("scheme") : DEFAULT.scheme,
				tag.getString("name"),
				parse(Clock.class, tag.getString("clock"), DEFAULT.clock),
				!tag.contains("hour24") || tag.getBoolean("hour24"),
				tag.contains("saverMinutes") ? tag.getInt("saverMinutes") : DEFAULT_SAVER_MINUTES,
				tag.contains("saverStyle") ? tag.getString("saverStyle") : DEFAULT_SAVER_STYLE);
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String text, E fallback) {
		for(E value : type.getEnumConstants())
			if(value.name().equalsIgnoreCase(text))
				return value;
		return fallback;
	}
}
