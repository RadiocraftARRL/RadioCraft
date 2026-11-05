package com.arrl.radiocraft.common.radio.adsb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an ADS-B receiver has heard: one track per aircraft (by ICAO address), its latest report and a trail of where
 * it was at the reports before, newest last. A track that has not been heard for {@link #COAST_TICKS} is dropped, as a
 * display drops a target it has lost. Pure, so it is unit tested; the receiver block keeps one.
 */
public final class AdsbTracks {

	/** Trail points kept per track: one a report, so about half a minute. */
	public static final int TRAIL = 30;
	/** How long (ticks) a track stays after its last report. */
	public static final long COAST_TICKS = 200L;
	/** Most aircraft one receiver tracks. */
	public static final int MAX_TRACKS = 64;

	/** One aircraft. */
	public static final class Track {
		private AdsbTarget last;
		private long heardAt;
		private final double[] trailX = new double[TRAIL], trailZ = new double[TRAIL];
		private int count = 0, head = 0;

		Track(AdsbTarget first, long now) {
			last = first;
			heardAt = now;
		}

		void heard(AdsbTarget target, long now) {
			// The place it was at the report before goes on the trail, unless it has not moved since.
			double dx = target.x() - last.x(), dz = target.z() - last.z();
			if(dx * dx + dz * dz >= 0.25D) {
				trailX[head] = last.x();
				trailZ[head] = last.z();
				head = (head + 1) % TRAIL;
				count = Math.min(TRAIL, count + 1);
			}
			last = target;
			heardAt = now;
		}

		public AdsbTarget last() {
			return last;
		}

		/** Game time of the latest report. */
		public long heardAt() {
			return heardAt;
		}

		/** Trail points kept. */
		public int trailSize() {
			return count;
		}

		/** Trail point {@code i}, oldest first: x. */
		public double trailX(int i) {
			return trailX[index(i)];
		}

		/** Trail point {@code i}, oldest first: z. */
		public double trailZ(int i) {
			return trailZ[index(i)];
		}

		private int index(int i) {
			return ((head - count + i) % TRAIL + TRAIL) % TRAIL;
		}
	}

	private final Map<Integer, Track> tracks = new LinkedHashMap<>();

	/** A report came in at game time {@code now}. False if it was a new aircraft and the display is full. */
	public boolean heard(AdsbTarget target, long now) {
		Track track = tracks.get(target.icao());
		if(track != null) {
			track.heard(target, now);
			return true;
		}
		if(tracks.size() >= MAX_TRACKS)
			return false;
		tracks.put(target.icao(), new Track(target, now));
		return true;
	}

	/** Drops the tracks not heard since {@code now - COAST_TICKS} (or heard "after" now: a new world's clock). */
	public void prune(long now) {
		tracks.values().removeIf(track -> track.heardAt < now - COAST_TICKS || track.heardAt > now);
	}

	public void clear() {
		tracks.clear();
	}

	public boolean isEmpty() {
		return tracks.isEmpty();
	}

	public int size() {
		return tracks.size();
	}

	/** The track of {@code icao}, or null. */
	public Track get(int icao) {
		return tracks.get(icao);
	}

	/** Every track, read only. */
	public Collection<Track> tracks() {
		return Collections.unmodifiableCollection(tracks.values());
	}

	/** The tracks nearest {@code x}, {@code z} first. */
	public List<Track> nearest(double x, double z) {
		List<Track> out = new ArrayList<>(tracks.values());
		out.sort((a, b) -> Double.compare(distanceSqr(a.last, x, z), distanceSqr(b.last, x, z)));
		return out;
	}

	private static double distanceSqr(AdsbTarget target, double x, double z) {
		double dx = target.x() - x, dz = target.z() - z;
		return dx * dx + dz * dz;
	}
}
