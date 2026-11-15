package com.arrl.radiocraft.common.radio.station;

import com.arrl.radiocraft.api.weather.DayOutlook;
import com.arrl.radiocraft.api.weather.WeatherReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an FM broadcast station says, as word ids (2026-11-14): each id is a clip {@code word_<id>.ogg} of the weather
 * voice, "," a short pause and "." a longer one. Pure (no game classes), so it is unit tested; {@link StationVoice}
 * turns the ids into sound.
 */
public final class StationSentences {

	/** A short pause (between clauses). */
	public static final String COMMA = ",";
	/** A longer pause (between sentences). */
	public static final String STOP = ".";

	private StationSentences() {}

	/** The part of the day a forecast is for, by the day's time in ticks (0 is 6 am). */
	public static String period(long dayTime) {
		long t = Math.floorMod(dayTime, 24_000L);
		if(t < 6_000L)
			return "this_morning";
		if(t < 11_000L)
			return "this_afternoon";
		if(t < 13_000L)
			return "this_evening";
		return "tonight";
	}

	/**
	 * A spoken forecast: "The forecast for this afternoon: chance of rain. Tomorrow: rain likely. Tornado watch. Winds from
	 * the west at twenty kilometers per hour. Currently cloudy, eighteen degrees."
	 *
	 * @param period       {@link #period}'s word
	 * @param today        today's outlook (UNKNOWN: today's sentence says what is falling now instead)
	 * @param tomorrow     tomorrow's outlook (UNKNOWN: left out)
	 * @param tornadoWatch a tornado is possible near
	 * @param now          the weather now: wind, temperature, what is falling (the station's instruments if any)
	 */
	public static List<String> forecast(String period, DayOutlook today, DayOutlook tomorrow, boolean tornadoWatch, WeatherReport now) {
		List<String> out = new ArrayList<>();
		out.add("the_forecast_for");
		out.add(period);
		out.add(COMMA);
		List<String> day = outlook(today.type(), period);
		out.addAll(day.isEmpty() ? sky(now, period) : day);
		out.add(STOP);
		List<String> next = outlook(tomorrow.type(), "today");
		if(!next.isEmpty()) {
			out.add("tomorrow");
			out.add(COMMA);
			out.addAll(next);
			out.add(STOP);
		}
		if(tornadoWatch || today.tornadoWatch()) {
			out.add("tornado_watch");
			out.add(STOP);
		}
		int kmh = (int)Math.round(now.windSpeed() * 3.6D);
		if(kmh < 2) {
			out.add("winds_calm");
			out.add(STOP);
		}
		else {
			out.add("winds_from_the");
			out.add(compass(now.windFrom()));
			out.add("at");
			out.addAll(number(kmh));
			out.add("kilometers_per_hour");
			out.add(STOP);
		}
		out.add("currently");
		out.addAll(sky(now, period));
		out.add(COMMA);
		int temperature = (int)Math.round(now.temperature());
		if(temperature < 0)
			out.add("minus");
		out.addAll(number(Math.abs(temperature)));
		out.add("degrees");
		out.add(STOP);
		return out;
	}

	/** A day's outlook in words, or none for UNKNOWN. {@code period} decides "sunny" or "clear" for a dry day. */
	static List<String> outlook(DayOutlook.Type type, String period) {
		return switch(type) {
			case UNKNOWN -> List.of();
			case DRY -> List.of(daylight(period) ? "sunny" : "clear");
			case CHANCE -> List.of("chance_of", "rain");
			case RAINY -> List.of("rain", "likely");
			case CLOUDY -> List.of("cloudy", COMMA, "no_storms");
		};
	}

	/** What the sky is doing now: "heavy rain", "thunderstorms", "snow", "cloudy", "sunny"... */
	static List<String> sky(WeatherReport now, String period) {
		if(now.has(WeatherReport.Hazard.DENSE_FOG))
			return List.of("fog");
		if(now.thunder() > 0.3F)
			return List.of("thunderstorms");
		List<String> out = new ArrayList<>();
		switch(now.precipitation()) {
			case RAIN, SNOW, HAIL -> {
				if(now.rain() > 0.75F)
					out.add("heavy");
				else if(now.rain() < 0.25F)
					out.add("light");
				out.add(now.precipitation().name().toLowerCase(Locale.ROOT));
			}
			case DUST -> out.add("blowing_dust");
			case NONE -> out.add(now.rain() > 0.2F ? "cloudy" : daylight(period) ? "sunny" : "clear");
		}
		if(now.windSpeed() > 10.7D && out.size() == 1 && (out.getFirst().equals("sunny") || out.getFirst().equals("clear") || out.getFirst().equals("cloudy"))) {
			out.add(COMMA);
			out.add("windy");
		}
		return out;
	}

	private static boolean daylight(String period) {
		return period.equals("this_morning") || period.equals("this_afternoon") || period.equals("today");
	}

	/** The eight points of the compass the wind comes from, as the weather voice's words. */
	public static String compass(double degrees) {
		String[] points = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
		return points[(int)Math.floorMod(Math.round(degrees / 45.0D), 8L)];
	}

	/** "This is K D nine X Y Z, ninety nine point nine F M." {@code callsign} may be empty. */
	public static List<String> stationId(String callsign, float hz) {
		List<String> out = new ArrayList<>();
		String sign = callsign == null ? "" : callsign.trim().toUpperCase(Locale.ROOT);
		if(!sign.isEmpty()) {
			out.add("this_is");
			for(char c : sign.toCharArray()) {
				if(c >= 'A' && c <= 'Z')
					out.add("letter_" + Character.toLowerCase(c));
				else if(c >= '0' && c <= '9')
					out.addAll(number(c - '0'));
			}
			out.add(COMMA);
		}
		int tenths = Math.round(hz / 100_000.0F); // 99.9 MHz: 999
		out.addAll(number(tenths / 10));
		out.add("point");
		out.addAll(number(tenths % 10));
		out.add("fm");
		out.add(STOP);
		return out;
	}

	/** A number 0 to 999 999 in words: "one hundred twenty three". */
	public static List<String> number(int n) {
		List<String> out = new ArrayList<>();
		if(n < 0) {
			out.add("minus");
			n = -n;
		}
		if(n >= 1_000_000)
			n = 999_999;
		if(n >= 1000) {
			out.addAll(number(n / 1000));
			out.add("thousand");
			n %= 1000;
			if(n == 0)
				return out;
		}
		if(n >= 100) {
			out.addAll(number(n / 100));
			out.add("hundred");
			n %= 100;
			if(n == 0)
				return out;
		}
		String[] small = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
				"thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
		String[] tens = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
		if(n < 20)
			out.add(small[n]);
		else {
			out.add(tens[n / 10]);
			if(n % 10 != 0)
				out.add(small[n % 10]);
		}
		return out;
	}

	/**
	 * A relayed alert's text read word by word: each run of words matched to the longest clip it can be (so "tornado
	 * warning" is one clip), numbers spoken, commas and full stops kept as pauses, and words with no clip left out.
	 *
	 * @param known the clip ids there are (see {@link StationVoice#VOCABULARY})
	 */
	public static List<String> alert(String text, java.util.Set<String> known) {
		List<String> tokens = new ArrayList<>();
		for(String raw : text.toLowerCase(Locale.ROOT).replace("'", "").split("(?=[,.;:!?])|(?<=[,.;:!?])|\\s+")) {
			String t = raw.strip();
			if(t.isEmpty())
				continue;
			tokens.add(t.equals(",") || t.equals(";") || t.equals(":") ? COMMA : t.equals(".") || t.equals("!") || t.equals("?") ? STOP : t.replaceAll("[^a-z0-9]", ""));
		}
		List<String> out = new ArrayList<>();
		for(int i = 0; i < tokens.size(); ) {
			String t = tokens.get(i);
			if(t.equals(COMMA) || t.equals(STOP)) {
				if(!out.isEmpty() && !out.getLast().equals(COMMA) && !out.getLast().equals(STOP))
					out.add(t);
				i++;
				continue;
			}
			if(!t.isEmpty() && t.chars().allMatch(Character::isDigit)) {
				out.addAll(number(Integer.parseInt(t.length() > 6 ? t.substring(0, 6) : t)));
				i++;
				continue;
			}
			int matched = 0;
			for(int len = Math.min(5, tokens.size() - i); len >= 1 && matched == 0; len--) {
				StringBuilder id = new StringBuilder();
				boolean words = true;
				for(int k = 0; k < len; k++) {
					String w = tokens.get(i + k);
					if(w.equals(COMMA) || w.equals(STOP) || w.isEmpty()) {
						words = false;
						break;
					}
					id.append(k == 0 ? "" : "_").append(w);
				}
				if(words && known.contains(id.toString())) {
					out.add(id.toString());
					matched = len;
				}
			}
			i += Math.max(1, matched); // An unknown word is left out.
		}
		return out;
	}
}
