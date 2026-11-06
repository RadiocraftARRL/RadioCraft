package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The scheduler's jobs (computer batch 3A): what to do and when, kept on the computer's disk as {@code SCHEDULE.DAT}
 * ({@link #FILE}, compressed NBT, {@link #toTag}), so the jobs follow the computer when it is broken and placed again,
 * and can be copied to a floppy and onto another computer. Pure apart from {@link CompoundTag}, so it is unit tested;
 * {@code SchedulerHandler} runs the jobs, {@code SchedulerApp} edits them.
 * <p>
 * <b>When</b> a job runs is counted in <i>occurrences</i> ({@link #occurrence}): a job runs when the occurrence number
 * goes up since it last looked ({@link #step}), so a computer whose chunk was unloaded for hours runs a missed job once
 * when it is back, not once per missed time, and setting the world's clock back never runs anything.
 * <ul>
 * <li>{@link When#EVERY}: every {@code minutes} real minutes (1200 game ticks each), counted from when the job was
 * saved ({@link Job#anchor}), so two computers' beacons do not all fire on the same tick.</li>
 * <li>{@link When#DAILY_GAME}: once a Minecraft day, when the world's clock (SETUP's in-game clock: 0 ticks is 6:00)
 * passes {@code minutes} after midnight.</li>
 * <li>{@link When#DAILY_REAL}: once a real day, when the server's own clock passes that time.</li>
 * </ul>
 */
public final class Schedule {

	/** The jobs' file on C:. */
	public static final String FILE = "SCHEDULE.DAT";
	public static final int MAX_JOBS = 16;
	/** Longest reminder text (an APRS message's). */
	public static final int TEXT_MAX = 100;
	public static final int MAX_EVERY_MINUTES = 24 * 60;
	public static final long TICKS_PER_MINUTE = 1200L;
	/** "Never looked yet": the first look only notes the occurrence. */
	public static final long UNSET = Long.MIN_VALUE;

	/** What a job does. */
	public enum Kind {
		/** Sends the station's callsign in Morse through the radio (an automatic station ID). */
		STATION_ID,
		/** Broadcasts a saved chart ({@code .IMG}) by weather fax, through the WEFAX program. */
		WEFAX,
		/** Sends an APRS position beacon, through the APRS program. */
		BEACON,
		/** A reminder: on the screen and in chat to players near the computer, or as an APRS message to a callsign. */
		REMINDER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byId(String id) {
			for(Kind kind : values())
				if(kind.id().equalsIgnoreCase(id))
					return kind;
			return STATION_ID;
		}
	}

	/** How a job's time is given. */
	public enum When {
		EVERY, DAILY_GAME, DAILY_REAL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static When byId(String id) {
			for(When when : values())
				if(when.id().equalsIgnoreCase(id))
					return when;
			return EVERY;
		}
	}

	/**
	 * One job.
	 *
	 * @param id      its number on this computer (1 up)
	 * @param kind    what it does
	 * @param when    how its time is given
	 * @param minutes {@link When#EVERY}: the interval, 1 to {@link #MAX_EVERY_MINUTES}; daily: minutes after midnight
	 * @param enabled false to keep it without running it
	 * @param file    a {@link Kind#WEFAX} job's chart, an {@code .IMG} on C:
	 * @param text    a {@link Kind#REMINDER}'s words
	 * @param to      a reminder's APRS callsign, or "" to tell the players at and near the computer
	 * @param anchor  game time it was saved: {@link When#EVERY} counts from here
	 */
	public record Job(int id, Kind kind, When when, int minutes, boolean enabled, String file, String text, String to, long anchor) {

		public Job {
			kind = kind == null ? Kind.STATION_ID : kind;
			when = when == null ? When.EVERY : when;
			minutes = when == When.EVERY ? Math.max(1, Math.min(MAX_EVERY_MINUTES, minutes)) : Math.floorMod(minutes, 24 * 60);
			file = file == null ? "" : file.trim().toUpperCase(Locale.ROOT);
			text = text == null ? "" : clean(text, TEXT_MAX);
			to = to == null ? "" : clean(to, 12).toUpperCase(Locale.ROOT).replace(" ", "");
		}

		public Job withEnabled(boolean enabled) {
			return new Job(id, kind, when, minutes, enabled, file, text, to, anchor);
		}

		public Job withId(int id) {
			return new Job(id, kind, when, minutes, enabled, file, text, to, anchor);
		}

		public Job withAnchor(long anchor) {
			return new Job(id, kind, when, minutes, enabled, file, text, to, anchor);
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("id", id);
			tag.putString("kind", kind.id());
			tag.putString("when", when.id());
			tag.putInt("minutes", minutes);
			tag.putBoolean("enabled", enabled);
			tag.putString("file", file);
			tag.putString("text", text);
			tag.putString("to", to);
			tag.putLong("anchor", anchor);
			return tag;
		}

		public static Job fromTag(CompoundTag tag) {
			return new Job(tag.getInt("id"), Kind.byId(tag.getString("kind")), When.byId(tag.getString("when")), tag.getInt("minutes"),
					!tag.contains("enabled") || tag.getBoolean("enabled"), tag.getString("file"), tag.getString("text"), tag.getString("to"),
					tag.getLong("anchor"));
		}

		/** Why the job cannot be kept as it is (an error key under {@code screen.radiocraft.computer.err.}), or null. */
		@Nullable
		public String problem() {
			if(kind == Kind.WEFAX && (ComputerStorage.normalise(file) == null || !ComputerFileFormats.IMAGE.equals(ComputerStorage.extension(file))))
				return "sched_need_image";
			if(kind == Kind.REMINDER && text.isBlank())
				return "sched_need_text";
			return null;
		}
	}

	private Schedule() {}

	/** Printable ASCII only, trimmed, at most {@code max} long. */
	static String clean(String text, int max) {
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < text.length() && out.length() < max; i++) {
			char c = text.charAt(i);
			if(c >= ' ' && c <= '~')
				out.append(c);
		}
		return out.toString().trim();
	}

	// -------------------- WHEN --------------------

	/** The world's clock, {@code Level#getDayTime}, as minutes since the world's first midnight (0 ticks is 6:00). */
	public static long gameMinutes(long dayTime) {
		return Math.floorDiv((dayTime + 6000L) * 60L, 1000L);
	}

	/**
	 * Which occurrence of {@code job}'s time it is now: a number that goes up by one each time the job is due.
	 *
	 * @param gameTime    {@code Level#getGameTime} (ticks, never set back)
	 * @param dayTime     {@code Level#getDayTime} (the world's clock)
	 * @param realMinutes the server's local time, minutes since the epoch (with its time zone's offset)
	 */
	public static long occurrence(Job job, long gameTime, long dayTime, long realMinutes) {
		return switch(job.when()) {
			case EVERY -> Math.floorDiv(gameTime - job.anchor(), job.minutes() * TICKS_PER_MINUTE);
			case DAILY_GAME -> Math.floorDiv(gameMinutes(dayTime) - job.minutes(), 24L * 60L);
			case DAILY_REAL -> Math.floorDiv(realMinutes - job.minutes(), 24L * 60L);
		};
	}

	/** What one look at a job's time decides: run it or not, and the occurrence to remember. */
	public record Step(boolean run, long last) {}

	/**
	 * A look at a job whose occurrence is {@code now} and was {@code last} ({@link #UNSET} the first time): it runs
	 * when the number went up. The first look and a clock set back only remember the number.
	 */
	public static Step step(long last, long now) {
		if(last == UNSET || now <= last)
			return new Step(false, now);
		return new Step(true, now);
	}

	/** Seconds until {@code job} is next due (EVERY by game ticks; daily by its clock). */
	public static long secondsToNext(Job job, long gameTime, long dayTime, long realSeconds) {
		return switch(job.when()) {
			case EVERY -> {
				long period = job.minutes() * TICKS_PER_MINUTE;
				long into = Math.floorMod(gameTime - job.anchor(), period);
				yield (period - into + 19L) / 20L;
			}
			case DAILY_GAME -> { // A Minecraft day is 24000 ticks from 6:00; midnight is 18000.
				long now = Math.floorMod(dayTime + 6000L, 24000L); // Ticks since midnight.
				long at = job.minutes() * 1000L / 60L;
				long left = Math.floorMod(at - now - 1L, 24000L) + 1L;
				yield (left + 19L) / 20L;
			}
			case DAILY_REAL -> {
				long day = 24L * 3600L;
				long now = Math.floorMod(realSeconds, day);
				yield Math.floorMod(job.minutes() * 60L - now - 1L, day) + 1L;
			}
		};
	}

	/**
	 * A job's time as typed: {@code 30} or {@code every 30} (minutes; {@code 2h} for hours), {@code at 20:00} (the
	 * world's clock), {@code at 20:00 real} (the server's clock); {@code 8:05p} works too. Null if it is none of those.
	 * Answers {when, minutes}.
	 */
	@Nullable
	public static Object[] parseWhen(String typed) {
		if(typed == null)
			return null;
		String t = typed.trim().toLowerCase(Locale.ROOT);
		boolean real = t.endsWith("real");
		if(real)
			t = t.substring(0, t.length() - 4).trim();
		if(t.startsWith("every"))
			t = t.substring(5).trim();
		boolean at = t.startsWith("at");
		if(at)
			t = t.substring(2).trim();
		if(t.contains(":")) {
			Integer minute = clockMinutes(t);
			return minute == null ? null : new Object[] { real ? When.DAILY_REAL : When.DAILY_GAME, minute };
		}
		if(at || real)
			return null;
		int scale = 1;
		if(t.endsWith("h")) {
			scale = 60;
			t = t.substring(0, t.length() - 1).trim();
		}
		else if(t.endsWith("min"))
			t = t.substring(0, t.length() - 3).trim();
		else if(t.endsWith("m"))
			t = t.substring(0, t.length() - 1).trim();
		try {
			int minutes = Integer.parseInt(t) * scale;
			return minutes < 1 || minutes > MAX_EVERY_MINUTES ? null : new Object[] { When.EVERY, minutes };
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** "20:00", "8:05p", "12:00a" as minutes after midnight, or null. */
	@Nullable
	static Integer clockMinutes(String t) {
		String s = t.trim().toLowerCase(Locale.ROOT);
		int half = 0; // 0 for 24-hour time, 1 for a.m., 2 for p.m.
		if(s.endsWith("am") || s.endsWith("pm"))
			s = s.substring(0, s.length() - 1);
		if(s.endsWith("a") || s.endsWith("p")) {
			half = s.endsWith("a") ? 1 : 2;
			s = s.substring(0, s.length() - 1).trim();
		}
		int colon = s.indexOf(':');
		if(colon < 1)
			return null;
		try {
			int hours = Integer.parseInt(s.substring(0, colon).trim()), minutes = Integer.parseInt(s.substring(colon + 1).trim());
			if(minutes < 0 || minutes > 59)
				return null;
			if(half != 0) {
				if(hours < 1 || hours > 12)
					return null;
				hours = hours % 12 + (half == 2 ? 12 : 0);
			}
			if(hours < 0 || hours > 23)
				return null;
			return hours * 60 + minutes;
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** {@code when} and {@code minutes} as they would be typed: "every 30", "at 20:00", "at 20:00 real". */
	public static String typed(When when, int minutes) {
		return switch(when) {
			case EVERY -> minutes % 60 == 0 && minutes >= 60 ? "every " + minutes / 60 + "h" : "every " + minutes;
			case DAILY_GAME -> "at " + ComputerSettings.time(minutes / 60, minutes % 60, true);
			case DAILY_REAL -> "at " + ComputerSettings.time(minutes / 60, minutes % 60, true) + " real";
		};
	}

	// -------------------- THE LIST --------------------

	/** The jobs in {@code tag} (a {@link #FILE}'s), at most {@link #MAX_JOBS}, ids made unique. */
	public static List<Job> fromTag(@Nullable CompoundTag tag) {
		List<Job> out = new ArrayList<>();
		if(tag == null)
			return out;
		ListTag list = tag.getList("jobs", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size() && out.size() < MAX_JOBS; i++) {
			Job job = Job.fromTag(list.getCompound(i));
			int id = job.id();
			while(id <= 0 || idTaken(out, id))
				id = nextId(out);
			out.add(job.withId(id));
		}
		return out;
	}

	public static CompoundTag toTag(List<Job> jobs) {
		ListTag list = new ListTag();
		for(Job job : jobs)
			list.add(job.toTag());
		CompoundTag tag = new CompoundTag();
		tag.put("jobs", list);
		return tag;
	}

	private static boolean idTaken(List<Job> jobs, int id) {
		for(Job job : jobs)
			if(job.id() == id)
				return true;
		return false;
	}

	/** The lowest id no job has. */
	public static int nextId(List<Job> jobs) {
		int id = 1;
		while(idTaken(jobs, id))
			id++;
		return id;
	}

	/**
	 * {@code jobs} with {@code job} put in: replacing the one with its id, or added with a new id (for id 0). Null if
	 * that would be more than {@link #MAX_JOBS}.
	 */
	@Nullable
	public static List<Job> put(List<Job> jobs, Job job) {
		List<Job> out = new ArrayList<>(jobs);
		for(int i = 0; i < out.size(); i++)
			if(out.get(i).id() == job.id() && job.id() > 0) {
				out.set(i, job);
				return out;
			}
		if(out.size() >= MAX_JOBS)
			return null;
		out.add(job.withId(job.id() > 0 && !idTaken(out, job.id()) ? job.id() : nextId(out)));
		return out;
	}

	/** {@code jobs} without job {@code id}. */
	public static List<Job> remove(List<Job> jobs, int id) {
		List<Job> out = new ArrayList<>(jobs);
		out.removeIf(job -> job.id() == id);
		return Collections.unmodifiableList(out);
	}
}
