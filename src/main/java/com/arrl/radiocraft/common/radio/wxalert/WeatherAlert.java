package com.arrl.radiocraft.common.radio.wxalert;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One alert a Simple Weather Alert Generator puts out: what it is, the circle it covers and until when. A storm's alert
 * also carries where the storm is ({@code x}, {@code z}, its own radius {@code core}) and the way it moves
 * ({@code heading}), so a receiver can say how far off it is and in which direction. An area alert (a watch, or a
 * flood or wind warning from a weather station's report) has {@code core} 0. Pure.
 *
 * @param event   what kind of alert
 * @param key     stays the same while the same hazard goes on (the storm's id with the event), so a receiver sounds
 *                once for it however often it is sent
 * @param x       the centre of the circle (a storm's centre), block x
 * @param z       block z
 * @param radius  blocks: a receiver this close to the centre is in the alert
 * @param core    the storm's own radius, blocks; 0 for an area alert
 * @param expires game tick the alert ends unless it is sent again
 * @param category a hurricane's category, a tornado's EF rating, or -1
 * @param heading compass degrees the storm moves toward (0 north, 90 east), or -1 if it is not moving
 */
public record WeatherAlert(Event event, long key, int x, int z, int radius, int core, long expires, int category, int heading) {

	/** How far past an alert's circle a receiver still counts as in it, blocks. */
	public static final int MARGIN = 32;

	/**
	 * The kinds of alert, after the real event codes, worst first. {@code clip} is how the voice names it, {@code advice}
	 * what it tells people to do (or null).
	 */
	public enum Event {
		TOR(true, WeatherVoiceClip.TORNADO_WARNING, WeatherVoiceClip.TAKE_SHELTER_NOW, "TORNADO WARNING"),
		HUW(true, WeatherVoiceClip.HURRICANE_WARNING, WeatherVoiceClip.TAKE_SHELTER_NOW, "HURRICANE WARNING"),
		SVR(true, WeatherVoiceClip.SEVERE_THUNDERSTORM_WARNING, WeatherVoiceClip.STAY_INDOORS, "SEVERE THUNDERSTORM WARNING"),
		BZW(true, WeatherVoiceClip.BLIZZARD_WARNING, WeatherVoiceClip.STAY_INDOORS, "BLIZZARD WARNING"),
		FFW(true, WeatherVoiceClip.FLASH_FLOOD_WARNING, WeatherVoiceClip.MOVE_TO_HIGHER_GROUND, "FLASH FLOOD WARNING"),
		TRW(true, WeatherVoiceClip.TROPICAL_STORM_WARNING, WeatherVoiceClip.STAY_INDOORS, "TROPICAL STORM WARNING"),
		HWW(true, WeatherVoiceClip.HIGH_WIND_WARNING, WeatherVoiceClip.STAY_INDOORS, "HIGH WIND WARNING"),
		DSW(true, WeatherVoiceClip.DUST_STORM_WARNING, WeatherVoiceClip.STAY_INDOORS, "DUST STORM WARNING"),
		TOA(false, WeatherVoiceClip.TORNADO_WATCH, null, "TORNADO WATCH"),
		SVA(false, WeatherVoiceClip.SEVERE_THUNDERSTORM_WATCH, null, "SEVERE THUNDERSTORM WATCH"),
		EHW(true, WeatherVoiceClip.EXCESSIVE_HEAT_WARNING, WeatherVoiceClip.STAY_INDOORS, "EXCESSIVE HEAT WARNING"),
		ECW(true, WeatherVoiceClip.EXTREME_COLD_WARNING, WeatherVoiceClip.STAY_INDOORS, "EXTREME COLD WARNING"),
		FGY(false, WeatherVoiceClip.DENSE_FOG_ADVISORY, null, "DENSE FOG ADVISORY");

		private final boolean warning;
		private final WeatherVoiceClip clip;
		@Nullable
		private final WeatherVoiceClip advice;
		private final String words;

		Event(boolean warning, WeatherVoiceClip clip, @Nullable WeatherVoiceClip advice, String words) {
			this.warning = warning;
			this.clip = clip;
			this.advice = advice;
			this.words = words;
		}

		/** A warning (the hazard is here or nearly), rather than a watch or advisory. */
		public boolean isWarning() {
			return warning;
		}

		public WeatherVoiceClip clip() {
			return clip;
		}

		@Nullable
		public WeatherVoiceClip advice() {
			return advice;
		}

		/** Its name in capitals, as a text line shows it: "TORNADO WARNING". */
		public String words() {
			return words;
		}

		/** The event with this code ("TOR"), or null. */
		@Nullable
		public static Event byCode(String code) {
			for(Event event : values())
				if(event.name().equals(code))
					return event;
			return null;
		}
	}

	/** Whether this alert has a storm with a centre to give a distance and direction to. */
	public boolean hasStorm() {
		return core > 0;
	}

	/** Whether a receiver at ({@code px}, {@code pz}) is in it: within its radius plus {@link #MARGIN}. */
	public boolean affects(double px, double pz) {
		double reach = radius + MARGIN;
		double dx = px - x, dz = pz - z;
		return dx * dx + dz * dz <= reach * reach;
	}

	/** Whether it has run out by {@code now}. */
	public boolean expired(long now) {
		return now >= expires;
	}

	/** One line on the air: {@code TOR 1a2b 1250 -340 260 120 18000 2 90}. */
	public String encode() {
		return String.format(Locale.ROOT, "%s %s %d %d %d %d %d %d %d", event.name(), Long.toHexString(key), x, z, radius, core, expires, category, heading);
	}

	/** A line as {@link #encode} writes it, or null if it is not one (or is for a kind this version does not know). */
	@Nullable
	public static WeatherAlert decode(String line) {
		String[] f = line.strip().split(" ");
		if(f.length != 9)
			return null;
		Event event = Event.byCode(f[0]);
		if(event == null)
			return null;
		try {
			return new WeatherAlert(event, Long.parseUnsignedLong(f[1], 16), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
					Math.max(0, Integer.parseInt(f[4])), Math.max(0, Integer.parseInt(f[5])), Long.parseLong(f[6]), Integer.parseInt(f[7]), Integer.parseInt(f[8]));
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** Compass degrees from ({@code fromX}, {@code fromZ}) to ({@code toX}, {@code toZ}): 0 north (-z), 90 east (+x). */
	public static double bearing(double fromX, double fromZ, double toX, double toZ) {
		double degrees = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
		return degrees < 0 ? degrees + 360.0D : degrees;
	}
}
