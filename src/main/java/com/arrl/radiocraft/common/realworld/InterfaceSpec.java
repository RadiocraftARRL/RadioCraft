package com.arrl.radiocraft.common.realworld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One sound card interface from the server config ({@code real_world_interfaces}), the way a ham's rig interface is
 * wired: an audio input and output, and optionally a serial port whose RTS or DTR line keys the radio's PTT and whose
 * CTS or DSR line can carry the radio's COS (carrier operated squelch, "busy") line. Written in the config as one
 * string of {@code key=value} pairs separated by {@code ;}, for example
 * {@code name=Shack 2m; input=USB Audio CODEC; output=USB Audio CODEC; serial=COM3; ptt=RTS}.
 * <p>
 * Only {@code name} is required; everything else has a default ({@link #DEFAULTS}). Pure: no game classes.
 *
 * @param name           What the block's screen offers ({@link #NAME_MAX} characters at most).
 * @param inputDevice    The sound card input (what the radio receives): part of its name, or "default".
 * @param outputDevice   The sound card output (what the radio transmits): part of its name, or "default".
 * @param sampleRate     The rate the sound card runs at, Hz; converted to and from the game's 48 kHz.
 * @param serialPort     The serial port for PTT and COS (COM3, /dev/ttyUSB0), or "none".
 * @param pttLine        The line that keys the radio, RTS or DTR; null for none (VOX keying in the radio itself).
 * @param pttInvert      True if the line is low to transmit.
 * @param trigger        What opens the way from the radio into the game: the audio's level (VOX) or the COS line.
 * @param voxThresholdDb VOX: the level that opens it, dBFS (-90 to 0).
 * @param voxHangMs      How long it stays open after the audio (VOX) or COS drops, ms.
 * @param cosLine        COS: the line the radio's squelch drives, CTS or DSR.
 * @param cosInvert      True if the line is low while a signal is coming in.
 * @param inputGain      Gain on the sound card's input before it goes into the game.
 * @param outputGain     Gain on the game's audio before it goes to the sound card.
 * @param pttHangMs      How long PTT stays keyed after the last in-game audio, ms (a repeater's hang time).
 * @param pttDelayMs     Silence sent after keying, before the audio, ms (the radio's TX delay).
 * @param timeoutSeconds Longest one transmission may last either way (a repeater's time-out timer); 0 for none.
 */
public record InterfaceSpec(String name, String inputDevice, String outputDevice, int sampleRate, String serialPort,
		AudioBackend.Line pttLine, boolean pttInvert, Trigger trigger, double voxThresholdDb, int voxHangMs,
		AudioBackend.Line cosLine, boolean cosInvert, double inputGain, double outputGain, int pttHangMs, int pttDelayMs,
		int timeoutSeconds) {

	/** What opens the way from the real radio into the game. */
	public enum Trigger { VOX, COS }

	public static final int NAME_MAX = 24;
	public static final String DEFAULT_DEVICE = "default";
	public static final String NO_PORT = "none";

	/** The default of every key, in the order {@link #format} writes them. */
	public static final Map<String, String> DEFAULTS = defaults();

	private static Map<String, String> defaults() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("input", DEFAULT_DEVICE);
		map.put("output", DEFAULT_DEVICE);
		map.put("rate", "48000");
		map.put("serial", NO_PORT);
		map.put("ptt", "RTS");
		map.put("ptt_invert", "false");
		map.put("trigger", "VOX");
		map.put("vox_threshold", "-35");
		map.put("vox_hang", "800");
		map.put("cos", "CTS");
		map.put("cos_invert", "false");
		map.put("input_gain", "1.0");
		map.put("output_gain", "1.0");
		map.put("ptt_hang", "300");
		map.put("ptt_delay", "150");
		map.put("timeout", "180");
		return java.util.Collections.unmodifiableMap(map);
	}

	/** A config line read: the interface, or why it will not do. */
	public record Parsed(InterfaceSpec spec, String error) {
		public boolean ok() {
			return spec != null;
		}
	}

	/** True if a serial port is set. */
	public boolean hasSerial() {
		return !serialPort.isEmpty() && !serialPort.equalsIgnoreCase(NO_PORT);
	}

	/** True if a serial line keys the radio. */
	public boolean keysPtt() {
		return hasSerial() && pttLine != null;
	}

	/** The longest one transmission may last, ms, or 0 for no limit. */
	public long timeoutMs() {
		return timeoutSeconds * 1000L;
	}

	/** Reads one config line; never throws. */
	public static Parsed parse(String line) {
		if(line == null || line.isBlank())
			return new Parsed(null, "empty");
		Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
		String name = null;
		for(String part : line.split(";")) {
			if(part.isBlank())
				continue;
			int eq = part.indexOf('=');
			if(eq <= 0)
				return new Parsed(null, "'" + part.trim() + "' is not key=value");
			String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			String value = part.substring(eq + 1).trim();
			if(key.equals("name"))
				name = value;
			else if(values.containsKey(key))
				values.put(key, value);
			else
				return new Parsed(null, "unknown key '" + key + "'");
		}
		if(name == null || name.isEmpty())
			return new Parsed(null, "no name=");
		if(name.length() > NAME_MAX || !name.matches("[A-Za-z0-9 _.()/-]+"))
			return new Parsed(null, "name '" + name + "': up to " + NAME_MAX + " letters, digits, spaces and _.()/-");
		try {
			String input = device(values.get("input"));
			String output = device(values.get("output"));
			int rate = integer(values, "rate", 8000, 192000);
			String serial = values.get("serial");
			if(serial.isEmpty())
				serial = NO_PORT;
			String ptt = values.get("ptt").toUpperCase(Locale.ROOT);
			AudioBackend.Line pttLine = switch(ptt) {
				case "RTS" -> AudioBackend.Line.RTS;
				case "DTR" -> AudioBackend.Line.DTR;
				case "NONE" -> null;
				default -> throw new IllegalArgumentException("ptt must be RTS, DTR or NONE");
			};
			Trigger trigger = switch(values.get("trigger").toUpperCase(Locale.ROOT)) {
				case "VOX" -> Trigger.VOX;
				case "COS" -> Trigger.COS;
				default -> throw new IllegalArgumentException("trigger must be VOX or COS");
			};
			AudioBackend.Line cos = switch(values.get("cos").toUpperCase(Locale.ROOT)) {
				case "CTS" -> AudioBackend.Line.CTS;
				case "DSR" -> AudioBackend.Line.DSR;
				default -> throw new IllegalArgumentException("cos must be CTS or DSR");
			};
			if(trigger == Trigger.COS && serial.equalsIgnoreCase(NO_PORT))
				throw new IllegalArgumentException("trigger=COS needs a serial port");
			InterfaceSpec spec = new InterfaceSpec(name, input, output, rate, serial, pttLine, bool(values, "ptt_invert"), trigger,
					decimal(values, "vox_threshold", -90.0D, 0.0D), integer(values, "vox_hang", 0, 10000), cos, bool(values, "cos_invert"),
					decimal(values, "input_gain", 0.0D, 20.0D), decimal(values, "output_gain", 0.0D, 20.0D),
					integer(values, "ptt_hang", 0, 10000), integer(values, "ptt_delay", 0, 2000), integer(values, "timeout", 0, 3600));
			return new Parsed(spec, null);
		}
		catch(IllegalArgumentException e) {
			return new Parsed(null, e.getMessage());
		}
	}

	/**
	 * Every good line of {@code lines}, in order; a line with a name already taken is skipped (the first wins). Each
	 * problem goes to {@code problems} as "line N: why".
	 */
	public static List<InterfaceSpec> parseAll(List<? extends String> lines, List<String> problems) {
		List<InterfaceSpec> out = new ArrayList<>();
		for(int i = 0; i < lines.size(); i++) {
			Parsed parsed = parse(lines.get(i));
			if(!parsed.ok()) {
				problems.add("line " + (i + 1) + ": " + parsed.error());
				continue;
			}
			if(out.stream().anyMatch(spec -> spec.name().equalsIgnoreCase(parsed.spec().name()))) {
				problems.add("line " + (i + 1) + ": the name '" + parsed.spec().name() + "' is already used");
				continue;
			}
			out.add(parsed.spec());
		}
		return out;
	}

	/** The interface named {@code name} (any case) in {@code specs}, or null. */
	public static InterfaceSpec find(List<InterfaceSpec> specs, String name) {
		for(InterfaceSpec spec : specs)
			if(spec.name().equalsIgnoreCase(name))
				return spec;
		return null;
	}

	/** This interface as a config line ({@link #parse} reads it back the same). */
	public String format() {
		return "name=" + name + ";input=" + inputDevice + ";output=" + outputDevice + ";rate=" + sampleRate + ";serial=" + serialPort
				+ ";ptt=" + (pttLine == null ? "NONE" : pttLine.name()) + ";ptt_invert=" + pttInvert + ";trigger=" + trigger.name()
				+ ";vox_threshold=" + number(voxThresholdDb) + ";vox_hang=" + voxHangMs + ";cos=" + cosLine.name() + ";cos_invert=" + cosInvert
				+ ";input_gain=" + number(inputGain) + ";output_gain=" + number(outputGain) + ";ptt_hang=" + pttHangMs + ";ptt_delay=" + pttDelayMs
				+ ";timeout=" + timeoutSeconds;
	}

	/** "VOX -35 dBFS" or "COS on CTS", for the screen. */
	public String triggerText() {
		return trigger == Trigger.VOX ? "VOX " + number(voxThresholdDb) + " dBFS" : "COS on " + cosLine.name() + (cosInvert ? " (low)" : "");
	}

	/** "RTS on COM3", "DTR on /dev/ttyUSB0 (low)" or "none", for the screen. */
	public String pttText() {
		return keysPtt() ? pttLine.name() + " on " + serialPort + (pttInvert ? " (low)" : "") : "none";
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long)value) : Double.toString(value);
	}

	private static String device(String value) {
		return value.isEmpty() ? DEFAULT_DEVICE : value;
	}

	private static int integer(Map<String, String> values, String key, int min, int max) {
		try {
			int value = Integer.parseInt(values.get(key).trim());
			if(value < min || value > max)
				throw new IllegalArgumentException(key + " must be " + min + " to " + max);
			return value;
		}
		catch(NumberFormatException e) {
			throw new IllegalArgumentException(key + " must be a whole number");
		}
	}

	private static double decimal(Map<String, String> values, String key, double min, double max) {
		try {
			double value = Double.parseDouble(values.get(key).trim());
			if(!(value >= min && value <= max))
				throw new IllegalArgumentException(key + " must be " + number(min) + " to " + number(max));
			return value;
		}
		catch(NumberFormatException e) {
			throw new IllegalArgumentException(key + " must be a number");
		}
	}

	private static boolean bool(Map<String, String> values, String key) {
		String value = values.get(key).trim().toLowerCase(Locale.ROOT);
		return switch(value) {
			case "true", "yes", "1" -> true;
			case "false", "no", "0" -> false;
			default -> throw new IllegalArgumentException(key + " must be true or false");
		};
	}
}
