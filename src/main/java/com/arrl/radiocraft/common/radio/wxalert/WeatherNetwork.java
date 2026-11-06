package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.common.radio.data.AprsWeather;

import java.util.ArrayList;
import java.util.List;

/**
 * The weather stations a weather alert generator's APRS station hears, and whether there are enough of them, far
 * enough apart, to issue alerts (maintainer, 2026-11-06): at least {@link #MIN_STATIONS} weather beacons whose
 * positions are each at least {@link #MIN_SPACING} blocks from each other. Pure.
 */
public final class WeatherNetwork {

	public static final int MIN_STATIONS = 3;
	public static final double MIN_SPACING = 150.0D;

	/** A weather beacon heard: who, where and what it reported. */
	public record Station(String call, double x, double z, AprsWeather weather) {}

	/** Why the generator can or cannot issue alerts. */
	public enum Status {
		/** At least three stations far enough apart. */
		READY,
		/** No APRS station attached (an H19 terminal or a radio computer with APRS installed). */
		NO_APRS,
		/** Fewer than three weather beacons heard. */
		TOO_FEW,
		/** Three or more heard, but not three far enough from each other. */
		TOO_CLOSE
	}

	/**
	 * The verdict on {@code stations}: the status, how many weather beacons there are and how many of them the largest
	 * spread-out group found holds.
	 */
	public record Check(Status status, int heard, int spread) {}

	private WeatherNetwork() {}

	public static Check check(List<Station> stations) {
		return check(stations, MIN_STATIONS, MIN_SPACING);
	}

	public static Check check(List<Station> stations, int minStations, double minSpacing) {
		if(stations.size() < minStations)
			return new Check(Status.TOO_FEW, stations.size(), spreadSize(stations, minSpacing));
		int spread = spreadSize(stations, minSpacing);
		return new Check(spread >= minStations ? Status.READY : Status.TOO_CLOSE, stations.size(), spread);
	}

	/** Whether {@code a} and {@code b} are at least {@code minSpacing} blocks apart. */
	public static boolean farApart(Station a, Station b, double minSpacing) {
		double dx = a.x() - b.x(), dz = a.z() - b.z();
		return dx * dx + dz * dz >= minSpacing * minSpacing;
	}

	/**
	 * The size of the largest group of stations each at least {@code minSpacing} from all the others that the search
	 * finds. Exact for groups of three or fewer (all triples are tried), which is what the rule needs; beyond that a
	 * greedy pass from every starting station.
	 */
	public static int spreadSize(List<Station> stations, double minSpacing) {
		int n = stations.size();
		if(n == 0)
			return 0;
		int best = 1;
		for(int i = 0; i < n && best < 2; i++)
			for(int j = i + 1; j < n && best < 2; j++)
				if(farApart(stations.get(i), stations.get(j), minSpacing))
					best = 2;
		if(best < 2)
			return 1;
		boolean triple = false;
		for(int i = 0; i < n && !triple; i++)
			for(int j = i + 1; j < n && !triple; j++) {
				if(!farApart(stations.get(i), stations.get(j), minSpacing))
					continue;
				for(int k = j + 1; k < n; k++)
					if(farApart(stations.get(i), stations.get(k), minSpacing) && farApart(stations.get(j), stations.get(k), minSpacing)) {
						triple = true;
						break;
					}
			}
		if(!triple)
			return 2;
		best = 3;
		for(int start = 0; start < n; start++) {
			List<Station> group = new ArrayList<>();
			group.add(stations.get(start));
			for(int i = 0; i < n; i++) {
				Station candidate = stations.get(i);
				if(i != start && group.stream().allMatch(member -> farApart(member, candidate, minSpacing)))
					group.add(candidate);
			}
			best = Math.max(best, group.size());
		}
		return best;
	}
}
