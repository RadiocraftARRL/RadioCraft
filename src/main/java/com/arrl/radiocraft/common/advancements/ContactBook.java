package com.arrl.radiocraft.common.advancements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verified two-way contacts (the advancements batch, pure logic): A and B have a contact when A heard B and B heard A
 * within {@link #WINDOW_TICKS} (5 minutes). What was heard comes from the server: a player's voice reaching another
 * player's radio, or an FT8 contact a radio computer completed (each side logs having heard the other). Hand-typed
 * logbook entries never come here.
 * <p>
 * Each hearing keeps who heard whom, where the hearer's station was (a server-side snapshot, never sent anywhere), the
 * frequency, the band and the game time. A contact is the newest hearing and the one the other way; its distance is
 * between the two hearers' stations, so it is the distance between the two stations whatever path the signal took.
 * <p>
 * Memory is bounded: at most {@link #MAX_HEARD} hearings (one per hearer and talker, the newest), dropped once older
 * than the window, and the pair bookkeeping is pruned the same way. Not thread safe: the server thread owns it.
 */
public final class ContactBook {

	/** A and B must have heard each other within this many ticks: 5 minutes. */
	public static final long WINDOW_TICKS = 5L * 60L * 20L;
	/** A pair already found in contact is looked at again after this many ticks at the soonest. */
	public static final long RECHECK_TICKS = 10L * 20L;
	/** A pair counts once per band per game day towards Contest Station (a contest's dupe rule). */
	public static final long DUPE_TICKS = 24_000L;
	/** The most hearings kept. */
	public static final int MAX_HEARD = 4096;
	/** Below this the signal was on HF. */
	public static final double HF_MAX_HZ = 30_000_000.0D;
	/** Skip Day: an HF contact over this many blocks. */
	public static final double SKIP_DAY_BLOCKS = 1000.0D;
	/** DX Hunter: a contact over this many blocks. */
	public static final double DX_HUNTER_BLOCKS = 5000.0D;
	/** Moonbounce: both stations above this height. */
	public static final int MOONBOUNCE_Y = 200;

	/**
	 * {@code hearer} heard {@code talker} at game time {@code time}, the hearer's station at ({@code x}, {@code y},
	 * {@code z}) in {@code dimension}, on {@code hz} in band {@code band} ("" if none).
	 */
	public record Heard(UUID hearer, UUID talker, String dimension, int x, int y, int z, float hz, String band, long time) {

		public boolean hf() {
			return hz > 0.0F && hz < HF_MAX_HZ;
		}
	}

	/**
	 * A verified contact: {@code earlier} and {@code later} are the two hearings, one each way. {@code counts} is true
	 * the first time this pair worked this band today (it counts towards Contest Station).
	 */
	public record Contact(Heard earlier, Heard later, boolean counts) {

		public UUID a() {
			return later.hearer();
		}

		public UUID b() {
			return later.talker();
		}

		public boolean sameDimension() {
			return earlier.dimension().equals(later.dimension());
		}

		/** Blocks between the two stations, level only; infinite between dimensions (radio does not cross them). */
		public double distance() {
			if(!sameDimension())
				return Double.POSITIVE_INFINITY;
			return Math.hypot(earlier.x() - later.x(), earlier.z() - later.z());
		}

		/** Both ways on HF. */
		public boolean hf() {
			return earlier.hf() && later.hf();
		}

		/** The lower of the two stations' heights. */
		public int lowestY() {
			return Math.min(earlier.y(), later.y());
		}
	}

	private record Way(UUID hearer, UUID talker) {}

	private record Pair(UUID low, UUID high) {
		static Pair of(UUID a, UUID b) {
			return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
		}
	}

	private record Dupe(Pair pair, String band) {}

	private final LinkedHashMap<Way, Heard> heard = new LinkedHashMap<>();
	private final Map<Pair, Long> checked = new HashMap<>();
	private final Map<Dupe, Long> counted = new HashMap<>();
	private long lastPrune = Long.MIN_VALUE;

	/** Notes {@code h}; the contact it makes, or null. */
	public Contact heard(Heard h) {
		if(h.hearer().equals(h.talker()))
			return null;
		prune(h.time());
		Way way = new Way(h.hearer(), h.talker());
		heard.remove(way); // To the end: the map is kept oldest first.
		heard.put(way, h);
		while(heard.size() > MAX_HEARD)
			heard.remove(heard.keySet().iterator().next());
		Heard back = heard.get(new Way(h.talker(), h.hearer()));
		if(back == null || h.time() - back.time() > WINDOW_TICKS || back.time() > h.time())
			return null;
		Pair pair = Pair.of(h.hearer(), h.talker());
		Long last = checked.get(pair);
		if(last != null && h.time() - last < RECHECK_TICKS && h.time() >= last)
			return null;
		checked.put(pair, h.time());
		Dupe dupe = new Dupe(pair, h.band());
		Long countedAt = counted.get(dupe);
		boolean counts = countedAt == null || h.time() - countedAt >= DUPE_TICKS || h.time() < countedAt;
		if(counts)
			counted.put(dupe, h.time());
		return new Contact(back, h, counts);
	}

	/** The advancements a contact earns both stations (Contest Station is counted apart). */
	public static List<String> earned(Contact contact, long dayTime, boolean solarStorm) {
		List<String> out = new ArrayList<>();
		out.add(RadiocraftAdvancements.FIRST_CONTACT);
		double distance = contact.distance();
		if(contact.hf() && distance > SKIP_DAY_BLOCKS && distance != Double.POSITIVE_INFINITY)
			out.add(RadiocraftAdvancements.SKIP_DAY);
		if(distance > DX_HUNTER_BLOCKS && distance != Double.POSITIVE_INFINITY)
			out.add(RadiocraftAdvancements.DX_HUNTER);
		if(isGreyLine(dayTime))
			out.add(RadiocraftAdvancements.GREY_LINE);
		if(contact.lowestY() > MOONBOUNCE_Y)
			out.add(RadiocraftAdvancements.MOONBOUNCE);
		if(solarStorm)
			out.add(RadiocraftAdvancements.INTO_THE_STATIC);
		return out;
	}

	/**
	 * True at dawn or dusk on the game's clock (day time 0 is 6:00, sunrise): from 5:30 to 7:00 (22500 to 1000) and from
	 * 17:30 to 19:30 (11500 to 13500), the grey line round the sun rising and setting.
	 */
	public static boolean isGreyLine(long dayTime) {
		long t = Math.floorMod(dayTime, 24_000L);
		return t >= 22_500L || t < 1_000L || (t >= 11_500L && t < 13_500L);
	}

	/** Hearings kept now (tests). */
	public int size() {
		return heard.size();
	}

	/** Forgets everything (the server stopped). */
	public void clear() {
		heard.clear();
		checked.clear();
		counted.clear();
		lastPrune = Long.MIN_VALUE;
	}

	/** Drops what is too old to matter, at most once a minute of game time. */
	private void prune(long now) {
		if(lastPrune != Long.MIN_VALUE && now - lastPrune < 1200L && now >= lastPrune && heard.size() < MAX_HEARD)
			return;
		lastPrune = now;
		for(Iterator<Heard> it = heard.values().iterator(); it.hasNext(); ) {
			Heard h = it.next();
			if(now - h.time() > WINDOW_TICKS || h.time() > now)
				it.remove();
		}
		checked.values().removeIf(t -> now - t >= RECHECK_TICKS || t > now);
		counted.values().removeIf(t -> now - t >= DUPE_TICKS || t > now);
	}
}
