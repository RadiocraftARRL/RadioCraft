package com.arrl.radiocraft.common.radio.satellite.imaging;

import net.minecraft.world.level.material.MapColor;

/**
 * The colours a weather satellite's picture shows precipitation in (2026-11-12): the classic radar scale, light green
 * through greens, yellow, orange and reds to magenta for a severe storm and purple for hail, and blues for snow. All map
 * colours (so pictures print on a map), and all at {@link MapColor.Brightness#HIGH} or the two dark steps at
 * {@link MapColor.Brightness#LOW}: the terrain under them never uses {@code HIGH} ({@link AptPainter}), so a picture can
 * be read back for its weather ({@link #levelOf}). Pure apart from the colour table, unit tested.
 */
public final class RadarScale {

	/** Nothing falling. */
	public static final int NONE = 0;
	/** Rain levels 1 (light) to {@link #HEAVIEST_RAIN}. */
	public static final int HEAVIEST_RAIN = 7;
	/** A severe storm's core, and hail in it. */
	public static final int SEVERE = 8, HAIL = 9;
	/** Snow, light to heavy. */
	public static final int SNOW_LIGHT = 10, SNOW = 11, SNOW_HEAVY = 12;
	public static final int LEVELS = 13;
	/** Where each rain level starts, intensity 0 to 1 (level {@code i + 1} from {@code RAIN[i]}). */
	static final double[] RAIN = { 0.08D, 0.2D, 0.35D, 0.5D, 0.62D, 0.74D, 0.86D };
	/** Where each snow level starts. */
	static final double[] SNOW_FROM = { 0.08D, 0.4D, 0.7D };

	private static final byte[] COLOURS = new byte[LEVELS];

	static {
		COLOURS[1] = MapColor.COLOR_LIGHT_GREEN.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[2] = MapColor.EMERALD.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[3] = MapColor.EMERALD.getPackedId(MapColor.Brightness.LOW);
		COLOURS[4] = MapColor.COLOR_YELLOW.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[5] = MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[6] = MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[7] = MapColor.FIRE.getPackedId(MapColor.Brightness.LOW);
		COLOURS[SEVERE] = MapColor.COLOR_MAGENTA.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[HAIL] = MapColor.COLOR_PURPLE.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[SNOW_LIGHT] = MapColor.ICE.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[SNOW] = MapColor.COLOR_LIGHT_BLUE.getPackedId(MapColor.Brightness.HIGH);
		COLOURS[SNOW_HEAVY] = MapColor.COLOR_BLUE.getPackedId(MapColor.Brightness.HIGH);
	}

	private RadarScale() {}

	/**
	 * The level for rain of {@code intensity} (0 to 1): {@link #NONE} under the first step, {@link #HAIL} or
	 * {@link #SEVERE} in a severe storm's core whatever the rain, else 1 to {@link #HEAVIEST_RAIN}.
	 */
	public static int rain(double intensity, boolean severe, boolean hail) {
		if(hail)
			return HAIL;
		if(severe)
			return SEVERE;
		int level = NONE;
		while(level < RAIN.length && intensity >= RAIN[level])
			level++;
		return level;
	}

	/** The level for snow of {@code intensity}: {@link #NONE}, then {@link #SNOW_LIGHT}, {@link #SNOW}, {@link #SNOW_HEAVY}. */
	public static int snow(double intensity) {
		int step = 0;
		while(step < SNOW_FROM.length && intensity >= SNOW_FROM[step])
			step++;
		return step == 0 ? NONE : SNOW_LIGHT + step - 1;
	}

	/** The map colour of {@code level}; 0 (no colour) for {@link #NONE}. */
	public static byte colour(int level) {
		return level <= NONE || level >= LEVELS ? 0 : COLOURS[level];
	}

	/** The level a picture's pixel {@code id} shows, or {@link #NONE} if it is terrain (or anything else). */
	public static int levelOf(byte id) {
		if(id == 0)
			return NONE;
		for(int level = 1; level < LEVELS; level++)
			if(COLOURS[level] == id)
				return level;
		return NONE;
	}

	/** A level in words, as a printout says it: "LIGHT RAIN" ... "HAIL", "HEAVY SNOW"; "NONE". */
	public static String describe(int level) {
		return switch(level) {
			case 1, 2 -> "LIGHT RAIN";
			case 3, 4 -> "MODERATE RAIN";
			case 5, 6, 7 -> "HEAVY RAIN";
			case SEVERE -> "SEVERE STORM";
			case HAIL -> "HAIL";
			case SNOW_LIGHT -> "LIGHT SNOW";
			case SNOW -> "SNOW";
			case SNOW_HEAVY -> "HEAVY SNOW";
			default -> "NONE";
		};
	}

	/** How bad a level is, to find the worst in a picture: hail over severe over heavy rain; snow below rain. */
	public static int severity(int level) {
		if(isSnow(level))
			return level - SNOW_LIGHT + 1;
		return level == NONE ? 0 : level + 3;
	}

	/**
	 * A picture's weather in a few words, for a printout's text: what share of the pixels with data show precipitation
	 * and the worst of it, or that there was none or no data at all.
	 */
	public static String summary(byte[] pixels) {
		int data = 0, wet = 0, worst = NONE;
		for(byte id : pixels) {
			if(id == 0)
				continue;
			data++;
			int level = levelOf(id);
			if(level == NONE)
				continue;
			wet++;
			if(severity(level) > severity(worst))
				worst = level;
		}
		if(data == 0)
			return "NO DATA";
		if(wet == 0)
			return "NO PRECIPITATION";
		return String.format(java.util.Locale.ROOT, "PRECIP %d%%, WORST %s", Math.max(1, Math.round(wet * 100.0F / data)), describe(worst));
	}

	/** True for snow levels. */
	public static boolean isSnow(int level) {
		return level >= SNOW_LIGHT && level <= SNOW_HEAVY;
	}
}
