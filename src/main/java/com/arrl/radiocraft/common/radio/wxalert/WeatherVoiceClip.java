package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The pre-made word clips the Simple Weather Alert Receiver splices into its announcement, as the real automated
 * weather voice does, with each clip's length in milliseconds (from {@code tools/weather_voice.py}, which made them
 * with Piper and the public-domain LJ Speech voice; see {@code doc/Credits-WeatherVoice.md}). Each is the sound
 * {@code radiocraft:weather_alert.word_<name>} in sounds.json.
 */
public enum WeatherVoiceClip {
	INTRO(1812),
	TORNADO_WARNING(1041),
	TORNADO_WATCH(1110),
	SEVERE_THUNDERSTORM_WARNING(1740),
	SEVERE_THUNDERSTORM_WATCH(1778),
	FLASH_FLOOD_WARNING(1258),
	HIGH_WIND_WARNING(989),
	HURRICANE_WARNING(1065),
	TROPICAL_STORM_WARNING(1329),
	BLIZZARD_WARNING(958),
	DUST_STORM_WARNING(1262),
	DENSE_FOG_ADVISORY(1500),
	EXCESSIVE_HEAT_WARNING(1343),
	EXTREME_COLD_WARNING(1501),
	FOR_THE_AREA(780),
	THE_STORM_IS(1044),
	OVER_YOUR_LOCATION(1124),
	BLOCKS(522),
	TO_THE(281),
	MOVING(502),
	CATEGORY(725),
	NORTH(441),
	NORTHEAST(746),
	EAST(441),
	SOUTHEAST(818),
	SOUTH(560),
	SOUTHWEST(884),
	WEST(522),
	NORTHWEST(929),
	TAKE_SHELTER_NOW(1069),
	STAY_INDOORS(1151),
	MOVE_TO_HIGHER_GROUND(1350),
	END_OF_MESSAGE(987),
	ZERO(545),
	ONE(352),
	TWO(454),
	THREE(440),
	FOUR(295),
	FIVE(435),
	SIX(610),
	SEVEN(569),
	EIGHT(232),
	NINE(433),
	TEN(400),
	ELEVEN(545),
	TWELVE(463),
	THIRTEEN(692),
	FOURTEEN(713),
	FIFTEEN(754),
	SIXTEEN(730),
	SEVENTEEN(849),
	EIGHTEEN(636),
	NINETEEN(624),
	TWENTY(467),
	THIRTY(483),
	FORTY(425),
	FIFTY(460),
	SIXTY(590),
	SEVENTY(652),
	EIGHTY(439),
	NINETY(489),
	HUNDRED(514),
	THOUSAND(631);

	/** The attention tone before an announcement (1050 Hz), milliseconds. */
	public static final int TONE_MS = 5000;
	/** A pause after the tone and between sentences, milliseconds. */
	public static final int PAUSE_MS = 350;

	private static final WeatherVoiceClip[] ONES = { ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN,
			ELEVEN, TWELVE, THIRTEEN, FOURTEEN, FIFTEEN, SIXTEEN, SEVENTEEN, EIGHTEEN, NINETEEN };
	private static final WeatherVoiceClip[] TENS = { null, null, TWENTY, THIRTY, FORTY, FIFTY, SIXTY, SEVENTY, EIGHTY, NINETY };
	private static final WeatherVoiceClip[] COMPASS = { NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST };

	private final int millis;

	WeatherVoiceClip(int millis) {
		this.millis = millis;
	}

	/** How long the clip plays, milliseconds. */
	public int millis() {
		return millis;
	}

	/** The sound event's id: {@code radiocraft:weather_alert.word_<name>}. */
	public ResourceLocation soundId() {
		return Radiocraft.id("weather_alert.word_" + name().toLowerCase(java.util.Locale.ROOT));
	}

	/** The attention tone's sound event id. */
	public static ResourceLocation toneId() {
		return Radiocraft.id("weather_alert.tone");
	}

	/** {@code n} (0 to 999,999) as words: 320 is THREE HUNDRED TWENTY. Negative numbers are spoken as 0. */
	public static java.util.List<WeatherVoiceClip> number(int n) {
		java.util.List<WeatherVoiceClip> out = new java.util.ArrayList<>();
		n = Math.max(0, Math.min(999_999, n));
		if(n == 0) {
			out.add(ZERO);
			return out;
		}
		if(n >= 1000) {
			below1000(n / 1000, out);
			out.add(THOUSAND);
			n %= 1000;
		}
		below1000(n, out);
		return out;
	}

	private static void below1000(int n, java.util.List<WeatherVoiceClip> out) {
		if(n >= 100) {
			out.add(ONES[n / 100]);
			out.add(HUNDRED);
			n %= 100;
		}
		if(n >= 20) {
			out.add(TENS[n / 10]);
			n %= 10;
			if(n > 0)
				out.add(ONES[n]);
		}
		else if(n > 0)
			out.add(ONES[n]);
	}

	/** The eight-point compass word for a bearing in degrees (0 north, 90 east). */
	public static WeatherVoiceClip compass(double bearing) {
		int index = (int)Math.floorMod(Math.round(bearing / 45.0D), 8L);
		return COMPASS[index];
	}
}
