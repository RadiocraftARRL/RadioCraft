package com.arrl.radiocraft.common.radio.station;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * What an FM broadcast station says in its own voice (2026-11-14), as mono 16-bit PCM at 48 kHz ready to go on the air:
 * the pre-rendered word clips of the weather radio's voice (Piper, LJ Speech, public domain; {@code tools/weather_voice.py}),
 * read on the server from the mod's own resources and joined into sentences. Only words that have a clip can be said.
 * <p>
 * CONTRACT for the station work (2026-11-14): the speech part fills these in; the station part only calls them.
 * Each returns null when it cannot (clips not loaded yet: they load off the server thread on first use; try again next
 * tick).
 */
public final class StationSpeech {

	private StationSpeech() {}

	/** The weather forecast for {@code pos} (the transmitter's place), with current conditions from {@code station} if not null. */
	@Nullable
	public static short[] forecast(ServerLevel level, BlockPos pos, @Nullable BlockPos station) {
		return StationVoice.forecast(level, pos, station);
	}

	/** The station's identification: "This is {callsign}, {frequency} FM." {@code callsign} may be empty (frequency only). */
	@Nullable
	public static short[] stationId(String callsign, float hz) {
		return StationVoice.stationId(callsign, hz);
	}

	/**
	 * A weather alert to relay, the attention tone first: {@code text} is the alert as the weather alert receiver has it
	 * (its words are matched to clips; unknown words are skipped).
	 */
	@Nullable
	public static short[] alert(String text, boolean tone) {
		return StationVoice.alert(text, tone);
	}
}
