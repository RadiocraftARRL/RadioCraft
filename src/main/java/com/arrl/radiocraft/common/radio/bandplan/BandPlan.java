package com.arrl.radiocraft.common.radio.bandplan;

import com.arrl.radiocraft.common.radio.Band;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The band plan: what each part of each band is for ({@link Segment}s with a use and a colour) and single frequencies
 * worth knowing ({@link Marker}s, such as 144.390 APRS). The defaults are a simplified ARRL band plan for the ham
 * bands and a simplified US allocation for commercial low band and VHF.
 * <p>
 * It lives in the server config ({@code band_plan_segments}, {@code band_plan_markers}) so admins can edit it; NeoForge
 * syncs server configs to clients on login, so the client reads the same lists. {@link #current()} parses them (cached
 * until they change). The text format, one entry per string:
 * <ul>
 *     <li>segment: {@code "<from MHz>-<to MHz>|<use>|<colour>"}, e.g. {@code "144.300-144.600|Data (APRS, packet)|data"}</li>
 *     <li>marker: {@code "<MHz>|<label>"}, e.g. {@code "144.390|APRS"}</li>
 * </ul>
 * A colour is one of {@link #COLOURS} (a category, also used for the legend) or {@code #RRGGBB}.
 * <p>
 * No game classes except {@link Band}, so it unit tests without Minecraft. Meant to be reused by the radio computer's
 * band plan app.
 */
public record BandPlan(List<Segment> segments, List<Marker> markers) {

	/** Part of a band and what it is for. Frequencies in Hz. */
	public record Segment(double fromHz, double toHz, String use, String colour) {
		public boolean contains(double hz) {
			return hz >= fromHz && hz < toHz;
		}

		/** 0xRRGGBB of this segment's colour. */
		public int rgb() {
			return BandPlan.rgb(colour);
		}
	}

	/** One frequency with a label. Frequency in Hz. */
	public record Marker(double hz, String label) {}

	/** A band as the sheet draws it: the band, and the segments (clipped to it) and markers that fall inside. */
	public record Row(Band band, List<Segment> segments, List<Marker> markers) {}

	private record Category(int rgb, String legend) {}

	/** Colour categories, with their colour and a legend name. */
	private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

	static {
		category("cw", 0x3B6FD8, "CW");
		category("digital", 0x8E44C9, "Digital (RTTY, PSK, packet)");
		category("image", 0xE08A1E, "SSTV / fax");
		category("phone", 0x2E9E4F, "Phone (voice)");
		category("weak_signal", 0x1E9AA8, "Weak signal / satellite");
		category("data", 0xB8559B, "Data (APRS, packet)");
		category("simplex", 0x8BC34A, "FM simplex");
		category("repeater_in", 0xD8B92A, "Repeater inputs");
		category("repeater_out", 0xC9762A, "Repeater outputs");
		category("business", 0x6D8BA6, "Business (licence required)");
		category("public_safety", 0xD23C3C, "Public safety");
		category("federal", 0x77774A, "Military / federal");
		category("murs", 0x4CB8E0, "MURS (licence free)");
		category("marine", 0x2A55A0, "Marine VHF");
		category("other", 0x9A9A9A, "Other");
	}

	private static void category(String name, int rgb, String legend) {
		CATEGORIES.put(name, new Category(rgb, legend));
	}

	/** The colour names a segment may use (besides {@code #RRGGBB}). */
	public static final List<String> COLOURS = List.copyOf(CATEGORIES.keySet());

	/** 0xRRGGBB for a colour name or {@code #RRGGBB}; grey for anything unknown. */
	public static int rgb(String colour) {
		Category category = CATEGORIES.get(colour);
		if(category != null)
			return category.rgb();
		Integer hex = hex(colour);
		return hex == null ? CATEGORIES.get("other").rgb() : hex;
	}

	/** Legend text for a colour category, or the colour itself for a {@code #RRGGBB} one. */
	public static String legend(String colour) {
		Category category = CATEGORIES.get(colour);
		return category == null ? colour : category.legend();
	}

	@Nullable
	private static Integer hex(String colour) {
		if(colour == null || colour.length() != 7 || colour.charAt(0) != '#')
			return null;
		try {
			return Integer.parseInt(colour.substring(1), 16);
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** True if {@code colour} is a category or a {@code #RRGGBB}. */
	public static boolean isColour(String colour) {
		return CATEGORIES.containsKey(colour) || hex(colour) != null;
	}

	// -------------------- DEFAULTS --------------------

	/** The default segments: a simplified ARRL band plan, and the commercial bands' main users. */
	public static final List<String> DEFAULT_SEGMENTS = List.of(
			// 80m
			"3.500-3.570|CW|cw", "3.570-3.600|Digital (RTTY, data)|digital", "3.600-3.840|Phone|phone",
			"3.840-3.850|SSTV / fax|image", "3.850-4.000|Phone|phone",
			// 40m
			"7.000-7.070|CW|cw", "7.070-7.125|Digital (RTTY, data)|digital", "7.125-7.165|Phone|phone",
			"7.165-7.175|SSTV / fax|image", "7.175-7.300|Phone|phone",
			// 20m
			"14.000-14.070|CW|cw", "14.070-14.112|Digital (RTTY, PSK, packet)|digital", "14.112-14.150|CW|cw",
			"14.150-14.225|Phone|phone", "14.225-14.235|SSTV / fax|image", "14.235-14.350|Phone|phone",
			// 10m
			"28.000-28.070|CW|cw", "28.070-28.300|Digital and beacons|digital", "28.300-28.670|Phone|phone",
			"28.670-28.690|SSTV / fax|image", "28.690-29.300|Phone|phone", "29.300-29.510|Satellite|weak_signal",
			"29.510-29.590|Repeater inputs|repeater_in", "29.590-29.610|FM simplex|simplex", "29.610-29.700|Repeater outputs|repeater_out",
			// Commercial low band (30-50 MHz)
			"30.000-30.560|Military / federal|federal", "30.560-32.000|Business (licence required)|business",
			"32.000-33.000|Military / federal|federal", "33.000-34.000|Public safety (fire)|public_safety",
			"34.000-35.000|Military / federal|federal", "35.000-36.000|Business (licence required)|business",
			"36.000-37.000|Military / federal|federal", "37.000-38.000|Public safety|public_safety",
			"38.000-39.000|Military / federal|federal", "39.000-40.000|Public safety (state police)|public_safety",
			"40.000-42.000|Military / federal|federal", "42.000-43.000|Public safety|public_safety",
			"43.000-44.000|Business (licence required)|business", "44.000-46.600|Public safety (highway, fire)|public_safety",
			"46.600-47.000|Military / federal|federal", "47.000-49.600|Business and industrial (licence required)|business",
			"49.600-50.000|Military / federal|federal",
			// 2m
			"144.000-144.100|CW / weak signal|weak_signal", "144.100-144.300|SSB weak signal|weak_signal",
			"144.300-144.600|Data (APRS, packet)|data", "144.600-144.900|Repeater inputs|repeater_in",
			"144.900-145.100|FM simplex and packet|simplex", "145.100-145.500|Repeater outputs|repeater_out",
			"145.500-145.800|FM simplex|simplex", "145.800-146.000|Satellite|weak_signal",
			"146.000-146.400|Repeater inputs|repeater_in", "146.400-146.600|FM simplex|simplex",
			"146.600-147.400|Repeater outputs|repeater_out", "147.400-147.600|FM simplex|simplex",
			"147.600-148.000|Repeater inputs|repeater_in",
			// Commercial VHF (150-174 MHz)
			"150.000-150.800|Military / federal|federal", "150.800-151.490|Public safety (highway, forestry)|public_safety",
			"151.490-151.810|Business (licence required)|business", "151.810-151.950|MURS (licence free)|murs",
			"151.950-153.740|Business and paging (licence required)|business", "153.740-154.460|Public safety (fire)|public_safety",
			"154.460-154.565|Business (licence required)|business", "154.565-154.605|MURS (licence free)|murs",
			"154.605-154.650|Business (licence required)|business", "154.650-156.000|Public safety (police)|public_safety",
			"156.000-157.450|Marine VHF|marine", "157.450-158.100|Business and paging (licence required)|business",
			"158.100-159.480|Public safety (police)|public_safety", "159.480-160.600|Business (licence required)|business",
			"160.600-161.600|Railroad and business (licence required)|business", "161.600-162.025|Marine VHF (coast stations)|marine",
			"162.025-174.000|Military / federal|federal");

	/** The default markers. 144.390 is the APRS frequency the mod uses ({@code handheld_aprs_frequency_mhz}). */
	public static final List<String> DEFAULT_MARKERS = List.of(
			"3.573|FT8", "3.845|SSTV", "7.074|FT8", "7.171|SSTV", "14.070|PSK31", "14.074|FT8", "14.230|SSTV calling",
			"28.074|FT8", "144.174|FT8", // The FT8 dial frequencies (computer batch 3B, Ft8.DIAL_HZ).
			"14.300|Emergency / maritime net", "28.680|SSTV", "29.600|10m FM simplex calling",
			"144.200|SSB calling", "144.390|APRS", "146.520|National simplex calling",
			"151.820|MURS 1", "151.880|MURS 2", "151.940|MURS 3", "154.570|MURS 4", "154.600|MURS 5",
			"156.450|Marine ch 9 (hailing)", "156.800|Marine ch 16 (distress, calling)", "162.550|NOAA weather radio");

	/** The plan from {@link #DEFAULT_SEGMENTS} and {@link #DEFAULT_MARKERS}. */
	public static BandPlan defaults() {
		return parse(DEFAULT_SEGMENTS, DEFAULT_MARKERS);
	}

	// -------------------- PARSING --------------------

	/**
	 * A placeholder marker the 1.12 development builds wrote into server configs; left out so those configs drop it
	 * without hand editing.
	 */
	private static final String PLACEHOLDER_MARKER = "7.060|Server-wide net";

	/** Parses the config lists; entries that cannot be read are left out. Segments and markers come out sorted. */
	public static BandPlan parse(Collection<? extends String> segments, Collection<? extends String> markers) {
		List<Segment> outSegments = new ArrayList<>();
		for(String line : segments) {
			Segment segment = parseSegment(line);
			if(segment != null)
				outSegments.add(segment);
		}
		List<Marker> outMarkers = new ArrayList<>();
		for(String line : markers) {
			if(PLACEHOLDER_MARKER.equals(line))
				continue;
			Marker marker = parseMarker(line);
			if(marker != null)
				outMarkers.add(marker);
		}
		outSegments.sort(Comparator.comparingDouble(Segment::fromHz));
		outMarkers.sort(Comparator.comparingDouble(Marker::hz));
		return new BandPlan(List.copyOf(outSegments), List.copyOf(outMarkers));
	}

	/** {@code "144.300-144.600|Data|data"} as a segment, or null if it cannot be read. */
	@Nullable
	public static Segment parseSegment(String line) {
		if(line == null)
			return null;
		String[] parts = line.split("\\|", -1);
		if(parts.length != 3)
			return null;
		String[] range = parts[0].trim().split("-");
		if(range.length != 2)
			return null;
		Double from = mhz(range[0]), to = mhz(range[1]);
		String use = parts[1].trim(), colour = parts[2].trim().toLowerCase(Locale.ROOT);
		if(from == null || to == null || !(to > from) || use.isEmpty() || !isColour(colour))
			return null;
		return new Segment(from, to, use, colour);
	}

	/** {@code "144.390|APRS"} as a marker, or null if it cannot be read. */
	@Nullable
	public static Marker parseMarker(String line) {
		if(line == null)
			return null;
		int bar = line.indexOf('|');
		if(bar < 0)
			return null;
		Double hz = mhz(line.substring(0, bar));
		String label = line.substring(bar + 1).trim();
		if(hz == null || label.isEmpty())
			return null;
		return new Marker(hz, label);
	}

	/** MHz text as Hz, rounded to the Hz; null if not a positive number. */
	@Nullable
	private static Double mhz(String text) {
		try {
			double value = Double.parseDouble(text.trim());
			if(!(value > 0.0D) || Double.isInfinite(value))
				return null;
			return (double)Math.round(value * 1_000_000.0D);
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	// -------------------- QUERIES --------------------

	/** The segment {@code hz} is in, or null. */
	@Nullable
	public Segment segmentAt(double hz) {
		for(Segment segment : segments)
			if(segment.contains(hz))
				return segment;
		return null;
	}

	/** What {@code hz} is for ("Phone"), or null outside every segment. */
	@Nullable
	public String useAt(double hz) {
		Segment segment = segmentAt(hz);
		return segment == null ? null : segment.use();
	}

	/** The markers within {@code toleranceHz} of {@code hz}. */
	public List<Marker> markersNear(double hz, double toleranceHz) {
		List<Marker> out = new ArrayList<>();
		for(Marker marker : markers)
			if(Math.abs(marker.hz() - hz) <= toleranceHz)
				out.add(marker);
		return out;
	}

	/** One row per band, lowest band first, each with the segments (clipped to the band) and markers inside it. */
	public List<Row> rows(Collection<Band> bands) {
		List<Band> sorted = new ArrayList<>(bands);
		sorted.sort(Comparator.comparingDouble(Band::minFrequency));
		List<Row> out = new ArrayList<>();
		for(Band band : sorted) {
			double low = band.minFrequency(), high = band.maxFrequency();
			List<Segment> inside = new ArrayList<>();
			for(Segment segment : segments)
				if(segment.toHz() > low && segment.fromHz() < high)
					inside.add(new Segment(Math.max(low, segment.fromHz()), Math.min(high, segment.toHz()), segment.use(), segment.colour()));
			List<Marker> marks = new ArrayList<>();
			for(Marker marker : markers)
				if(marker.hz() >= low && marker.hz() <= high)
					marks.add(marker);
			out.add(new Row(band, List.copyOf(inside), List.copyOf(marks)));
		}
		return out;
	}

	/** The colours used by any segment, in legend order (custom {@code #RRGGBB} colours last). */
	public List<String> legendColours() {
		List<String> out = new ArrayList<>();
		for(String name : COLOURS)
			for(Segment segment : segments)
				if(segment.colour().equals(name)) {
					out.add(name);
					break;
				}
		for(Segment segment : segments)
			if(!out.contains(segment.colour()))
				out.add(segment.colour());
		return out;
	}

	// -------------------- CURRENT --------------------

	private static volatile BandPlan cached;
	private static volatile List<?> cachedSegments;
	private static volatile List<?> cachedMarkers;

	/**
	 * The band plan in the server config (on a client: the server's, synced at login), or the defaults if the config is
	 * not loaded. Parsed again only when the config lists change.
	 */
	public static BandPlan current() {
		List<? extends String> segments, markers;
		try {
			segments = com.arrl.radiocraft.RadiocraftServerConfig.BAND_PLAN_SEGMENTS.get();
			markers = com.arrl.radiocraft.RadiocraftServerConfig.BAND_PLAN_MARKERS.get();
		}
		catch(IllegalStateException | NullPointerException e) {
			return defaults();
		}
		BandPlan plan = cached;
		if(plan == null || segments != cachedSegments || markers != cachedMarkers) {
			plan = parse(segments, markers);
			cachedSegments = segments;
			cachedMarkers = markers;
			cached = plan;
		}
		return plan;
	}

	/** The bands the sheet shows: every configured band (ham and commercial), lowest first. */
	public static List<Band> sheetBands() {
		List<Band> out = new ArrayList<>();
		for(Band band : Band.getAllDefaults()) {
			Band live = null;
			try {
				live = Band.getBand(band.name());
			}
			catch(IllegalStateException | NullPointerException ignored) {} // Config not loaded: the defaults.
			out.add(live != null ? live : band);
		}
		out.sort(Comparator.comparingDouble(Band::minFrequency));
		return out;
	}
}
