package com.arrl.radiocraft.common.radio;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The last few stations a radio heard talking (1.12 step 6), for the radio computer's logbook to fill in a contact:
 * who (the talking player), on what frequency, how strong at best, and when. Written from the voice chat thread once
 * per audio frame, so it is a fixed set of {@link #SLOTS} slots updated in place (no allocation for a station already
 * there); read on the server thread. Pure, so it is unit tested.
 */
public final class StationsHeard {

	/** How many stations it remembers; the one heard longest ago makes room for a new one. */
	public static final int SLOTS = 8;
	/** A gap this long (ticks) between frames starts a new transmission: its best strength starts again. */
	public static final long NEW_OVER_TICKS = 20L * 60L;

	/** One station heard: who, where, best strength in its last transmission, first and last frame (game ticks). */
	public record Heard(UUID player, float frequencyHz, double strength, long firstGameTime, long lastGameTime) {}

	private final UUID[] players = new UUID[SLOTS];
	private final float[] frequencies = new float[SLOTS];
	private final double[] strengths = new double[SLOTS];
	private final long[] first = new long[SLOTS];
	private final long[] last = new long[SLOTS];

	/** A frame from {@code player} on {@code frequencyHz} at {@code strength} (0..1) heard at {@code now}. */
	public synchronized void heard(UUID player, float frequencyHz, double strength, long now) {
		if(player == null)
			return;
		int slot = -1, oldest = 0;
		for(int i = 0; i < SLOTS; i++) {
			if(player.equals(players[i])) {
				slot = i;
				break;
			}
			if(players[i] == null) {
				if(players[oldest] != null)
					oldest = i;
			}
			else if(players[oldest] != null && last[i] < last[oldest])
				oldest = i;
		}
		if(slot < 0) {
			slot = oldest;
			players[slot] = player;
			first[slot] = now;
			strengths[slot] = 0.0D;
		}
		else if(now - last[slot] > NEW_OVER_TICKS) { // A new transmission from a station heard before.
			first[slot] = now;
			strengths[slot] = 0.0D;
		}
		frequencies[slot] = frequencyHz;
		strengths[slot] = Math.max(strengths[slot], strength);
		last[slot] = now;
	}

	/** The stations heard within {@code withinTicks} of {@code now}, most recent first. */
	public synchronized List<Heard> recent(long now, long withinTicks) {
		List<Heard> out = new ArrayList<>();
		for(int i = 0; i < SLOTS; i++)
			if(players[i] != null && now - last[i] <= withinTicks)
				out.add(new Heard(players[i], frequencies[i], strengths[i], first[i], last[i]));
		out.sort((a, b) -> Long.compare(b.lastGameTime(), a.lastGameTime()));
		return out;
	}

	public synchronized void clear() {
		for(int i = 0; i < SLOTS; i++)
			players[i] = null;
	}
}
