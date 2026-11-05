package com.arrl.radiocraft.common.radio.adsb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * An ADS-B receiver's picture as it goes to a screen (the receiver's own, or the radio computer's ADS-B program): the
 * receiver's state and every track, placed relative to the receiver (east +x, south +z, blocks), nearest first.
 * <ul>
 * <li>{@code on}, {@code powered}, {@code antenna}: switched on, running, an antenna found;</li>
 * <li>{@code range}: the range the receiver's own screen is set to, blocks; {@code reach}: how far an aircraft flying
 * high (at {@code adsb_full_range_y} or above) is heard; {@code reachLow}: how far one at sea level is heard;
 * {@code antennaFeet}: the antenna's height;</li>
 * <li>{@code targets}: each {@code icao}, {@code cs} (flight identification, may be empty), {@code sq} (squawk),
 * {@code dx}/{@code dz}, {@code alt} (feet), {@code gs} (knots), {@code trk} (degrees), {@code vs} (feet a minute),
 * {@code age} (seconds since the last report) and {@code trail} (dx, dz pairs, whole blocks, oldest first).</li>
 * </ul>
 */
public final class AdsbPicture {

	/** Most targets sent to a screen. */
	public static final int MAX_TARGETS = 48;

	private AdsbPicture() {}

	/** One aircraft as a screen draws it. */
	public record Contact(int icao, String callsign, int squawk, float dx, float dz, int feet, int knots, int track,
			int feetPerMinute, int age, int[] trail) {

		/** Its flight identification, or its address with none. */
		public String label() {
			return callsign.isEmpty() ? AdsbMath.hex(icao) : callsign;
		}

		/** Distance from the receiver, blocks. */
		public double distance() {
			return Math.sqrt((double)dx * dx + (double)dz * dz);
		}

		/** Bearing from the receiver, compass degrees. */
		public double bearing() {
			return AdsbMath.bearing(dx, dz);
		}

		public boolean emergency() {
			return AdsbMath.isEmergency(squawk);
		}
	}

	/** The receiver's state and its tracks round {@code centreX}, {@code centreZ}, at game time {@code now}. */
	public static CompoundTag write(boolean on, boolean powered, boolean antenna, int range, int reach, int reachLow, int antennaFeet,
			double centreX, double centreZ, AdsbTracks tracks, long now) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("on", on);
		tag.putBoolean("powered", powered);
		tag.putBoolean("antenna", antenna);
		tag.putInt("range", range);
		tag.putInt("reach", reach);
		tag.putInt("reachLow", reachLow);
		tag.putInt("antennaFeet", antennaFeet);
		ListTag list = new ListTag();
		for(AdsbTracks.Track track : tracks.nearest(centreX, centreZ)) {
			if(list.size() >= MAX_TARGETS)
				break;
			AdsbTarget last = track.last();
			CompoundTag t = new CompoundTag();
			t.putInt("icao", last.icao());
			t.putString("cs", last.callsign());
			t.putInt("sq", last.squawk());
			t.putFloat("dx", (float)(last.x() - centreX));
			t.putFloat("dz", (float)(last.z() - centreZ));
			t.putInt("alt", last.feet());
			t.putInt("gs", last.knots());
			t.putInt("trk", (int)Math.round(last.heading()) % 360);
			t.putInt("vs", last.feetPerMinute());
			t.putInt("age", (int)Math.max(0L, (now - track.heardAt()) / 20L));
			int[] trail = new int[track.trailSize() * 2];
			for(int i = 0; i < track.trailSize(); i++) {
				trail[i * 2] = (int)Math.round(track.trailX(i) - centreX);
				trail[i * 2 + 1] = (int)Math.round(track.trailZ(i) - centreZ);
			}
			t.put("trail", new IntArrayTag(trail));
			list.add(t);
		}
		tag.put("targets", list);
		return tag;
	}

	/** Most targets in a {@link #compact} picture. */
	public static final int COMPACT_TARGETS = 16;
	/** Most trail points (dx, dz pairs) a target keeps in a {@link #compact} picture. */
	public static final int COMPACT_TRAIL = 12;

	/**
	 * A smaller copy of {@code picture} for the receiver's tube seen in the world (players near it without its screen
	 * open, 1.12 playtest): the state and range, and only the {@code maxTargets} nearest targets on the scope, each with
	 * its address, flight identification, squawk, place, track, speed and the newest {@link #COMPACT_TRAIL} points of
	 * its trail taken every other point. {@link #contacts} reads it like a full picture.
	 */
	public static CompoundTag compact(CompoundTag picture, int maxTargets) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("on", picture.getBoolean("on"));
		out.putBoolean("powered", picture.getBoolean("powered"));
		out.putBoolean("antenna", picture.getBoolean("antenna"));
		int range = picture.getInt("range");
		out.putInt("range", range);
		double edge = Math.max(1, range) * 1.05D;
		ListTag targets = picture.getList("targets", Tag.TAG_COMPOUND), kept = new ListTag();
		for(int i = 0; i < targets.size() && kept.size() < maxTargets; i++) {
			CompoundTag t = targets.getCompound(i);
			float dx = t.getFloat("dx"), dz = t.getFloat("dz");
			if(Math.sqrt((double)dx * dx + (double)dz * dz) > edge)
				continue; // Off the scope: nothing to draw.
			CompoundTag c = new CompoundTag();
			c.putInt("icao", t.getInt("icao"));
			c.putString("cs", t.getString("cs"));
			c.putInt("sq", t.getInt("sq"));
			c.putFloat("dx", dx);
			c.putFloat("dz", dz);
			c.putInt("gs", t.getInt("gs"));
			c.putInt("trk", t.getInt("trk"));
			int[] trail = t.getIntArray("trail");
			int points = trail.length / 2;
			int[] thin = new int[Math.min(COMPACT_TRAIL, (points + 1) / 2) * 2];
			// Newest first back from the end, every other point, then put oldest first again.
			for(int k = 0, p = points - 1; k < thin.length / 2; k++, p -= 2) {
				int at = thin.length / 2 - 1 - k;
				thin[at * 2] = trail[p * 2];
				thin[at * 2 + 1] = trail[p * 2 + 1];
			}
			c.put("trail", new IntArrayTag(thin));
			kept.add(c);
		}
		out.put("targets", kept);
		return out;
	}

	/** The targets in a picture, nearest first. */
	public static List<Contact> contacts(CompoundTag tag) {
		ListTag list = tag.getList("targets", Tag.TAG_COMPOUND);
		List<Contact> out = new ArrayList<>(list.size());
		for(int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			out.add(new Contact(t.getInt("icao"), t.getString("cs"), t.getInt("sq"), t.getFloat("dx"), t.getFloat("dz"), t.getInt("alt"),
					t.getInt("gs"), t.getInt("trk"), t.getInt("vs"), t.getInt("age"), t.getIntArray("trail")));
		}
		return out;
	}
}
