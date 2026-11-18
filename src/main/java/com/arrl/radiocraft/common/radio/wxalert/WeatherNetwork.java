package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.common.radio.data.AprsWeather;

import java.util.ArrayList;
import java.util.List;

/**
 * The weather stations a weather alert generator hears, and what they let it do (maintainer, 2026-11-06; tiers
 * 2026-11-18). Stations count towards a tier ({@link Check#tier()}) when their positions are each at least {@link #MIN_SPACING}
 * blocks from the others: one station gives warnings for what is at it now, two add warnings for storms heading in,
 * {@link #MIN_STATIONS} or more add watches and tropical statements. Pure.
 */
public final class WeatherNetwork {

	/** Spread-out stations for the full set of alerts (watches and tropical statements). */
	public static final int MIN_STATIONS = 3;
	public static final double MIN_SPACING = 150.0D;
	/** The highest tier. */
	public static final int MAX_TIER = MIN_STATIONS;

	/** A weather report heard: who, where and what it reported. */
	public record Station(String call, double x, double z, AprsWeather weather) {}

	/** Why the generator can or cannot issue alerts. */
	public enum Status {
		/** At least one station: alerts at {@link Check#tier()}. */
		READY,
		/** No weather station heard. */
		TOO_FEW
	}

	/**
	 * The verdict on {@code stations}: the status, how many weather reports there are, how many of them the largest
	 * spread-out group found holds, and the tier (0 to {@link #MAX_TIER}) that gives.
	 */
	public record Check(Status status, int heard, int spread, int tier) {}

	private WeatherNetwork() {}

	public static Check check(List<Station> stations) {
		return check(stations, MIN_SPACING);
	}

	public static Check check(List<Station> stations, double minSpacing) {
		int spread = spreadSize(stations, minSpacing);
		int tier = Math.min(MAX_TIER, spread);
		return new Check(tier > 0 ? Status.READY : Status.TOO_FEW, stations.size(), spread, tier);
	}

	/** Whether {@code a} and {@code b} are at least {@code minSpacing} blocks apart. */
	public static boolean farApart(Station a, Station b, double minSpacing) {
		double dx = a.x() - b.x(), dz = a.z() - b.z();
		return dx * dx + dz * dz >= minSpacing * minSpacing;
	}

	/** The size of {@link #spreadGroup}. */
	public static int spreadSize(List<Station> stations, double minSpacing) {
		return spreadGroup(stations, minSpacing).size();
	}

	/**
	 * The largest group of stations each at least {@code minSpacing} from all the others that the search finds. Exact
	 * for groups of three or fewer (all pairs and triples are tried), which is what the tiers need; beyond that a greedy
	 * pass from every starting station.
	 */
	public static List<Station> spreadGroup(List<Station> stations, double minSpacing) {
		int n = stations.size();
		if(n == 0)
			return List.of();
		List<Station> pair = null;
		for(int i = 0; i < n && pair == null; i++)
			for(int j = i + 1; j < n && pair == null; j++)
				if(farApart(stations.get(i), stations.get(j), minSpacing))
					pair = List.of(stations.get(i), stations.get(j));
		if(pair == null)
			return List.of(stations.get(0));
		List<Station> triple = null;
		for(int i = 0; i < n && triple == null; i++)
			for(int j = i + 1; j < n && triple == null; j++) {
				if(!farApart(stations.get(i), stations.get(j), minSpacing))
					continue;
				for(int k = j + 1; k < n; k++)
					if(farApart(stations.get(i), stations.get(k), minSpacing) && farApart(stations.get(j), stations.get(k), minSpacing)) {
						triple = List.of(stations.get(i), stations.get(j), stations.get(k));
						break;
					}
			}
		if(triple == null)
			return pair;
		List<Station> best = triple;
		for(int start = 0; start < n; start++) {
			List<Station> group = new ArrayList<>();
			group.add(stations.get(start));
			for(int i = 0; i < n; i++) {
				Station candidate = stations.get(i);
				if(i != start && group.stream().allMatch(member -> farApart(member, candidate, minSpacing)))
					group.add(candidate);
			}
			if(group.size() > best.size())
				best = group;
		}
		return best;
	}
}
