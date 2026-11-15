package com.arrl.radiocraft.common.radio.station;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.weather.DayOutlook;
import com.arrl.radiocraft.api.weather.WeatherReport;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.api.weather.WeatherSources;
import com.arrl.radiocraft.common.blockentities.WeatherStationBlockEntity;
import com.arrl.radiocraft.common.radio.tape.OggDecoder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The FM broadcast station's voice (2026-11-14): the weather voice's word clips (Piper, LJ Speech, public domain;
 * {@code tools/weather_voice.py}, its WORDS and STATION_WORDS), read from the mod's own resources on the server, decoded
 * to 48 kHz mono PCM ({@link OggDecoder}) off the server thread on first use, and spliced into sentences
 * ({@link StationSentences}) with short gaps between words and longer ones at commas and full stops. Until the clips
 * have loaded, every sentence is null: the station asks again a tick later.
 */
public final class StationVoice {

	/** Every clip there is ({@code word_<id>.ogg} in {@code assets/radiocraft/sounds/weather_alert}). */
	public static final Set<String> VOCABULARY = Set.of(
			"intro", "tornado_warning", "tornado_watch", "severe_thunderstorm_warning", "severe_thunderstorm_watch", "flash_flood_warning",
			"high_wind_warning", "hurricane_warning", "tropical_storm_warning", "blizzard_warning", "dust_storm_warning", "dense_fog_advisory",
			"excessive_heat_warning", "extreme_cold_warning", "for_the_area", "the_storm_is", "over_your_location", "blocks", "to_the", "moving",
			"category", "north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest", "take_shelter_now", "stay_indoors",
			"move_to_higher_ground", "end_of_message", "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
			"eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty", "thirty", "forty",
			"fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand",
			"this_is", "fm", "point", "percent", "degrees", "minus", "the_forecast_for", "today", "tonight", "tomorrow", "this_morning",
			"this_afternoon", "this_evening", "chance_of", "likely", "rain", "snow", "hail", "thunderstorms", "showers", "clear", "sunny",
			"partly_cloudy", "cloudy", "fog", "windy", "heavy", "light", "blowing_dust", "dry", "no_storms", "winds_from_the", "at",
			"kilometers_per_hour", "calm", "winds_calm", "currently", "high", "low", "and", "then", "warning", "watch", "advisory", "tornado",
			"severe", "thunderstorm", "storm", "hurricane", "flood", "wind", "heat", "cold", "blizzard", "the", "for", "area", "is", "in", "your",
			"of", "a", "possible", "now",
			"letter_a", "letter_b", "letter_c", "letter_d", "letter_e", "letter_f", "letter_g", "letter_h", "letter_i", "letter_j", "letter_k",
			"letter_l", "letter_m", "letter_n", "letter_o", "letter_p", "letter_q", "letter_r", "letter_s", "letter_t", "letter_u", "letter_v",
			"letter_w", "letter_x", "letter_y", "letter_z");

	/** Samples a second. */
	static final int RATE = 48_000;
	/** Silence between words, after a comma, after a full stop: milliseconds. */
	static final int WORD_GAP_MS = 60, COMMA_MS = 250, STOP_MS = 450;
	/** The attention tone before a relayed alert: 1050 Hz, as the weather alert generator's, this long. */
	static final int TONE_MS = 3_000;
	/** How loud the voice goes out, of full scale (the clips peak at 0.85). */
	static final float LEVEL = 0.8F;

	private static final String DIR = "/assets/" + Radiocraft.MOD_ID + "/sounds/weather_alert/word_";
	private static volatile Map<String, short[]> clips;
	private static volatile boolean loading;
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "RadioCraft station voice");
		t.setDaemon(true);
		return t;
	});

	private StationVoice() {}

	/** The clips, or null while they load (the first call starts them loading). */
	@Nullable
	static Map<String, short[]> clips() {
		Map<String, short[]> loaded = clips;
		if(loaded != null)
			return loaded;
		synchronized(StationVoice.class) {
			if(!loading) {
				loading = true;
				LOADER.submit(StationVoice::load);
			}
		}
		return null;
	}

	private static void load() {
		Map<String, short[]> out = new HashMap<>();
		for(String id : VOCABULARY) {
			try(InputStream in = StationVoice.class.getResourceAsStream(DIR + id + ".ogg")) {
				if(in != null)
					out.put(id, OggDecoder.decode(in));
			}
			catch(Exception e) {
				Radiocraft.LOGGER.warn("Station voice clip {} would not load: {}", id, e.toString());
			}
		}
		Radiocraft.LOGGER.debug("Station voice: {} of {} clips", out.size(), VOCABULARY.size());
		clips = Map.copyOf(out);
	}

	/** The words of {@code ids} ({@link StationSentences}) spoken, or null while the clips load. Unknown ids are left out. */
	@Nullable
	public static short[] speak(List<String> ids) {
		return speak(ids, false);
	}

	@Nullable
	static short[] speak(List<String> ids, boolean tone) {
		Map<String, short[]> voice = clips();
		if(voice == null)
			return null;
		List<short[]> parts = new ArrayList<>();
		if(tone) {
			parts.add(tone(TONE_MS));
			parts.add(silence(STOP_MS));
		}
		for(String id : ids) {
			if(id.equals(StationSentences.COMMA))
				parts.add(silence(COMMA_MS));
			else if(id.equals(StationSentences.STOP))
				parts.add(silence(STOP_MS));
			else {
				short[] clip = voice.get(id);
				if(clip == null)
					continue; // No clip: left out.
				parts.add(clip);
				parts.add(silence(WORD_GAP_MS));
			}
		}
		return join(parts, LEVEL);
	}

	/** The parts one after another, at {@code level} of their loudness. */
	static short[] join(List<short[]> parts, float level) {
		int total = 0;
		for(short[] part : parts)
			total += part.length;
		short[] out = new short[total];
		int at = 0;
		for(short[] part : parts)
			for(short s : part)
				out[at++] = (short)Math.round(s * level);
		return out;
	}

	private static int ms(int ms) {
		return RATE * ms / 1000;
	}

	static short[] silence(int ms) {
		return new short[ms(ms)];
	}

	/** {@code ms} of the 1050 Hz attention tone, with 5 ms ends so it does not click. */
	static short[] tone(int ms) {
		short[] out = new short[ms(ms)];
		int ramp = ms(5);
		for(int i = 0; i < out.length; i++) {
			double env = Math.min(1.0D, Math.min(i, out.length - 1 - i) / (double)ramp);
			out[i] = (short)Math.round(Math.sin(2.0D * Math.PI * 1050.0D * i / RATE) * 12_000.0D * env);
		}
		return out;
	}

	/** {@link StationSpeech#forecast}: the forecast for the transmitter's place, the weather station's readings if any. */
	@Nullable
	static short[] forecast(ServerLevel level, BlockPos pos, @Nullable BlockPos station) {
		if(clips() == null)
			return null;
		WeatherSource source = WeatherSources.get(level);
		WeatherReport now = source.report(level, pos);
		if(station != null && level.isLoaded(station) && level.getBlockEntity(station) instanceof WeatherStationBlockEntity instruments)
			now = instruments.report(); // The instruments' own readings: wind, temperature.
		DayOutlook today = source.outlook(level, pos, 0), tomorrow = source.outlook(level, pos, 1);
		return speak(StationSentences.forecast(StationSentences.period(level.getDayTime()), today, tomorrow, source.tornadoWatch(level, pos), now));
	}

	/** {@link StationSpeech#stationId}. */
	@Nullable
	static short[] stationId(String callsign, float hz) {
		return speak(StationSentences.stationId(callsign, hz));
	}

	/** {@link StationSpeech#alert}. */
	@Nullable
	static short[] alert(String text, boolean tone) {
		return speak(StationSentences.alert(text, VOCABULARY), tone);
	}
}
