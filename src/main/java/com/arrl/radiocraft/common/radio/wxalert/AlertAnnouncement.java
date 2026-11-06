package com.arrl.radiocraft.common.radio.wxalert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a Simple Weather Alert Receiver says for an alert, from where it stands: the word clips to splice
 * ({@link #clips}) and the same in a text line ({@link #text}). A storm warning gives the storm's distance and
 * direction from the receiver and the way it is moving: "RadioCraft weather alert. Tornado warning for the area. The
 * storm is three hundred twenty blocks to the northwest, moving east. Take shelter now. End of message." Pure.
 */
public final class AlertAnnouncement {

	private AlertAnnouncement() {}

	/** The distance as spoken: to the nearest 10 blocks, or 100 from 1,000 up. */
	public static int spokenDistance(double blocks) {
		int step = blocks >= 1000 ? 100 : 10;
		return (int)(Math.round(blocks / step) * step);
	}

	/** Whether the receiver is under the storm: within its own radius, or so near that a distance would mislead. */
	public static boolean overhead(WeatherAlert alert, double rx, double rz) {
		return Math.hypot(rx - alert.x(), rz - alert.z()) <= Math.max(alert.core(), 24);
	}

	/** The clips for one alert, heard at ({@code rx}, {@code rz}), from the intro to "end of message". */
	public static List<WeatherVoiceClip> clips(WeatherAlert alert, double rx, double rz) {
		List<WeatherVoiceClip> out = new ArrayList<>();
		out.add(WeatherVoiceClip.INTRO);
		out.add(alert.event().clip());
		out.add(WeatherVoiceClip.FOR_THE_AREA);
		if(alert.hasStorm()) {
			out.add(WeatherVoiceClip.THE_STORM_IS);
			if(overhead(alert, rx, rz))
				out.add(WeatherVoiceClip.OVER_YOUR_LOCATION);
			else {
				out.addAll(WeatherVoiceClip.number(spokenDistance(Math.hypot(alert.x() - rx, alert.z() - rz))));
				out.add(WeatherVoiceClip.BLOCKS);
				out.add(WeatherVoiceClip.TO_THE);
				out.add(WeatherVoiceClip.compass(WeatherAlert.bearing(rx, rz, alert.x(), alert.z())));
			}
			if(alert.heading() >= 0) {
				out.add(WeatherVoiceClip.MOVING);
				out.add(WeatherVoiceClip.compass(alert.heading()));
			}
		}
		if(alert.event() == WeatherAlert.Event.HUW && alert.category() > 0) {
			out.add(WeatherVoiceClip.CATEGORY);
			out.addAll(WeatherVoiceClip.number(alert.category()));
		}
		if(alert.event().advice() != null)
			out.add(alert.event().advice());
		out.add(WeatherVoiceClip.END_OF_MESSAGE);
		return out;
	}

	/** The same as a line of text: "TORNADO WARNING: STORM 320 BLOCKS NW, MOVING E. TAKE SHELTER NOW." */
	public static String text(WeatherAlert alert, double rx, double rz) {
		StringBuilder out = new StringBuilder(alert.event().words());
		if(alert.event() == WeatherAlert.Event.HUW && alert.category() > 0)
			out.append(" (CATEGORY ").append(alert.category()).append(')');
		if(alert.hasStorm()) {
			if(overhead(alert, rx, rz))
				out.append(": STORM OVER YOUR LOCATION");
			else
				out.append(String.format(Locale.ROOT, ": STORM %d BLOCKS %s", spokenDistance(Math.hypot(alert.x() - rx, alert.z() - rz)),
						abbreviation(WeatherAlert.bearing(rx, rz, alert.x(), alert.z()))));
			if(alert.heading() >= 0)
				out.append(", MOVING ").append(abbreviation(alert.heading()));
		}
		else
			out.append(" FOR THE AREA");
		out.append('.');
		if(alert.event().advice() != null)
			out.append(' ').append(alert.event().advice().name().replace('_', ' ')).append('.');
		return out.toString();
	}

	/** N, NE, E... for a bearing. */
	public static String abbreviation(double bearing) {
		return switch(WeatherVoiceClip.compass(bearing)) {
			case NORTH -> "N";
			case NORTHEAST -> "NE";
			case EAST -> "E";
			case SOUTHEAST -> "SE";
			case SOUTH -> "S";
			case SOUTHWEST -> "SW";
			case WEST -> "W";
			default -> "NW";
		};
	}

	/**
	 * When each clip starts, in ticks after the announcement begins: the attention tone first ({@code withTone}), a pause,
	 * then the clips one after another. Worked out from the running total of milliseconds, so rounding never adds up.
	 */
	public static int[] startTicks(List<WeatherVoiceClip> clips, boolean withTone) {
		int[] out = new int[clips.size()];
		long ms = withTone ? WeatherVoiceClip.TONE_MS + WeatherVoiceClip.PAUSE_MS : 0;
		for(int i = 0; i < clips.size(); i++) {
			out[i] = (int)Math.round(ms / 50.0D);
			ms += clips.get(i).millis();
			if(clips.get(i) == WeatherVoiceClip.INTRO || clips.get(i) == WeatherVoiceClip.FOR_THE_AREA)
				ms += WeatherVoiceClip.PAUSE_MS; // A breath after a sentence.
		}
		return out;
	}

	/** Ticks the whole announcement lasts. */
	public static int lengthTicks(List<WeatherVoiceClip> clips, boolean withTone) {
		long ms = withTone ? WeatherVoiceClip.TONE_MS + WeatherVoiceClip.PAUSE_MS : 0;
		for(WeatherVoiceClip clip : clips)
			ms += clip.millis() + (clip == WeatherVoiceClip.INTRO || clip == WeatherVoiceClip.FOR_THE_AREA ? WeatherVoiceClip.PAUSE_MS : 0);
		return (int)Math.ceil(ms / 50.0D);
	}
}
