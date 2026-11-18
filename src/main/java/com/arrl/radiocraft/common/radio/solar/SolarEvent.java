package com.arrl.radiocraft.common.radio.solar;

import com.arrl.radiocraft.Radiocraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * One kind of solar weather, loaded from {@code data/radiocraft/solar_events/*.json}: its noise floor, how long a spell
 * of it lasts, and how well each HF band carries skip by day and by night (see {@link BandCondition}).
 */
public class SolarEvent {

	private final float noise;
	private final int minDuration;
	private final int maxDuration;
	private final Map<String, BandCondition> dayConditions;
	private final Map<String, BandCondition> nightConditions;
	@Nullable
	private final AuroraForecast aurora;
	/** Band name to how far its skip reaches, by day and night ({@link #getReach}); bands left out reach all the way. */
	private Map<String, Double> dayReach = Map.of(), nightReach = Map.of();

	/** An event that leaves every band {@link BandCondition#GOOD}. */
	public SolarEvent(float noise, int minDuration, int maxDuration) {
		this(noise, minDuration, maxDuration, Map.of(), Map.of());
	}

	/**
	 * @param dayConditions   Band name ("20m") to its condition while the sun is up; bands left out are good.
	 * @param nightConditions The same for the night.
	 */
	public SolarEvent(float noise, int minDuration, int maxDuration, Map<String, BandCondition> dayConditions, Map<String, BandCondition> nightConditions) {
		this(noise, minDuration, maxDuration, dayConditions, nightConditions, null);
	}

	/** @param aurora The chance of an aurora some days after this event starts, null for none. */
	public SolarEvent(float noise, int minDuration, int maxDuration, Map<String, BandCondition> dayConditions, Map<String, BandCondition> nightConditions, @Nullable AuroraForecast aurora) {
		this.aurora = aurora;
		this.noise = noise;
		this.minDuration = minDuration;
		this.maxDuration = maxDuration;
		this.dayConditions = Map.copyOf(dayConditions);
		this.nightConditions = Map.copyOf(nightConditions);
	}

	/**
	 * This event with {@code day} and {@code night} reaches (2026-11-18): band name to the share of its skip window,
	 * from the near edge, that skip reaches while this event lasts (0 to 1). A light opening: 10m in average weather.
	 */
	public SolarEvent withReach(Map<String, Double> day, Map<String, Double> night) {
		this.dayReach = Map.copyOf(day);
		this.nightReach = Map.copyOf(night);
		return this;
	}

	/** How far {@code band}'s skip reaches during this event, as a share of its window from the near edge: 1 for all of it. */
	public double getReach(String band, boolean isDay) {
		return (isDay ? dayReach : nightReach).getOrDefault(band, 1.0D);
	}

	/** True if the event data gives {@code band} a reach for that time of day. */
	public boolean hasReach(String band, boolean isDay) {
		return (isDay ? dayReach : nightReach).containsKey(band);
	}

	/** Reads a {@code {"10m": 0.18}} reach object from the event data: each a share from 0 to 1. */
	public static Map<String, Double> parseReach(JsonObject json) {
		Map<String, Double> reach = new HashMap<>();
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			double share = entry.getValue().getAsDouble();
			if(!(share > 0.0D && share <= 1.0D))
				throw new JsonParseException("Skip reach for " + entry.getKey() + " must be above 0 and at most 1, not " + share);
			reach.put(entry.getKey(), share);
		}
		return reach;
	}

	public float getNoise() {
		return noise;
	}

	/** The chance of an aurora following this event, null if it never brings one. */
	@Nullable
	public AuroraForecast getAurora() {
		return aurora;
	}

	/** How {@code band} ("20m") carries skip during this event, by day or by night. Bands the data leaves out are good. */
	public BandCondition getCondition(String band, boolean isDay) {
		return (isDay ? dayConditions : nightConditions).getOrDefault(band, BandCondition.GOOD);
	}

	/** True if the event data names {@code band} for that time of day. */
	public boolean hasCondition(String band, boolean isDay) {
		return (isDay ? dayConditions : nightConditions).containsKey(band);
	}

	/** True unless this event closes {@code band} at that time of day. */
	public boolean isBandOpen(String band, boolean isDay) {
		return getCondition(band, isDay) != BandCondition.CLOSED;
	}

	/**
	 * Reads a {@code {"10m": "closed", "40m": "poor"}} object from the event data. Unknown condition names are an
	 * error, so a typo in a data pack shows up in the log instead of silently meaning "good".
	 */
	public static Map<String, BandCondition> parseConditions(JsonObject json) {
		Map<String, BandCondition> conditions = new HashMap<>();
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			BandCondition condition = BandCondition.byName(entry.getValue().getAsString());
			if(condition == null)
				throw new JsonParseException("Unknown band condition '" + entry.getValue().getAsString() + "' for " + entry.getKey());
			conditions.put(entry.getKey(), condition);
		}
		return conditions;
	}

	/**
	 * Where {@code event} ranks among {@code all}, 1 for the noisiest (a blackout) up to the number of events for the
	 * quietest (great): what the solar weather station puts out to a comparator. Events with the same noise share a rank.
	 */
	public static int conditionLevel(Collection<SolarEvent> all, SolarEvent event) {
		if(event == null)
			return 0;
		int level = 0;
		for(SolarEvent other : all)
			if(other.getNoise() >= event.getNoise())
				level++;
		return Math.max(1, level);
	}

	public SolarEventInstance getInstance() {
		return new SolarEventInstance(this, Radiocraft.RANDOM.nextInt(minDuration, maxDuration+1));
	}



	public static class SolarEventInstance {

		private final SolarEvent event;
		private final int duration;
		private int ticks;

		protected SolarEventInstance(SolarEvent event, int duration) {
			this.event = event;
			this.duration = duration;
		}

		public SolarEvent getEvent() {
			return event;
		}

		public int getDuration() {
			return duration;
		}

		public int getTicks() {
			return ticks;
		}

		public void tick() {
			ticks++;
		}

		public boolean isFinished() {
			return ticks >= getDuration();
		}

	}

}
