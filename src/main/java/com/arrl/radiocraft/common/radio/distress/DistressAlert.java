package com.arrl.radiocraft.common.radio.distress;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One distress alert as it goes over the air (2026-11-09): a DSC distress call from a marine radio, or a distress
 * beacon's alert. The same record carries the call, its repeats, an acknowledgement and a cancel: each is the alert with
 * another {@link #state}. It is whole in itself ({@link #encode}), so a relay (a satellite, later) can send it on
 * unchanged and every receiver files it under the same {@link #id}.
 *
 * @param id     The alert's own number, the same on every repeat, ack and cancel (a beacon's for its whole life).
 * @param type   A DSC call or a beacon.
 * @param state  {@link State#ACTIVE} on the call and its repeats; ACKNOWLEDGED, CANCELLED or SPENT on the bursts that
 *               end it.
 * @param owner  Who is in distress: the caller's MMSI on a DSC call, the beacon owner's name on a beacon.
 * @param nature The nature of distress (a beacon's is always undesignated).
 * @param x      Where the caller was when this burst went out (world coordinates, live on a Sable ship).
 * @param y      Height.
 * @param z      North-south.
 * @param time   Game time the alert was first raised (not this burst).
 * @param ackBy  The MMSI of the station that acknowledged it, on an acknowledgement; empty otherwise.
 */
public record DistressAlert(long id, Type type, State state, String owner, DistressNature nature, int x, int y, int z, long time, String ackBy) {

	/** What raised the alert. */
	public enum Type {
		DSC("D"), BEACON("B");

		private final String code;

		Type(String code) {
			this.code = code;
		}
	}

	/** Where the alert stands. */
	public enum State {
		ACTIVE("A"), ACKNOWLEDGED("K"), CANCELLED("C"), SPENT("S");

		private final String code;

		State(String code) {
			this.code = code;
		}

		/** True if a burst in this state may replace what a receiver has in {@code was}: it never goes back to ACTIVE. */
		public boolean follows(State was) {
			return switch(was) {
				case ACTIVE -> this != ACTIVE;
				case ACKNOWLEDGED -> this == CANCELLED || this == SPENT;
				case CANCELLED, SPENT -> false;
			};
		}
	}

	/** What {@link #encode} starts with, so a later format can be told apart. */
	public static final String MAGIC = "DA1";
	/** Most characters of the owner's name or MMSI kept (a Minecraft name is at most 16). */
	public static final int OWNER_MAX = 16;

	public DistressAlert {
		owner = clean(owner, OWNER_MAX);
		ackBy = clean(ackBy, 9);
		nature = nature == null ? DistressNature.UNDESIGNATED : nature;
	}

	/** True for a DSC call (which can be acknowledged); false for a beacon. */
	public boolean isDsc() {
		return type == Type.DSC;
	}

	/** The same alert in {@code newState}. */
	public DistressAlert withState(State newState) {
		return new DistressAlert(id, type, newState, owner, nature, x, y, z, time, ackBy);
	}

	/** The acknowledgement of this alert by the station {@code mmsi}. */
	public DistressAlert acknowledgedBy(String mmsi) {
		return new DistressAlert(id, type, State.ACKNOWLEDGED, owner, nature, x, y, z, time, mmsi);
	}

	/** The same alert from {@code nx, ny, nz} (a repeat from a ship that moved). */
	public DistressAlert at(int nx, int ny, int nz) {
		return new DistressAlert(id, type, state, owner, nature, nx, ny, nz, time, ackBy);
	}

	/**
	 * The alert as one line of printable ASCII, '|' between the fields: what goes in the packet's text, and what a
	 * relay sends on unchanged. {@link #decode} reads it back.
	 */
	public String encode() {
		return String.join("|", MAGIC, type.code, state.code, Long.toHexString(id), owner, nature.name(),
				Integer.toString(x), Integer.toString(y), Integer.toString(z), Long.toString(time), ackBy);
	}

	/** {@link #encode}'s line back as an alert; null if it is not one (garbled, another format). */
	@Nullable
	public static DistressAlert decode(String text) {
		if(text == null)
			return null;
		String[] f = text.split("\\|", -1);
		if(f.length != 11 || !f[0].equals(MAGIC))
			return null;
		Type type = null;
		for(Type t : Type.values())
			if(t.code.equals(f[1]))
				type = t;
		State state = null;
		for(State s : State.values())
			if(s.code.equals(f[2]))
				state = s;
		if(type == null || state == null)
			return null;
		try {
			return new DistressAlert(Long.parseUnsignedLong(f[3], 16), type, state, f[4], DistressNature.byName(f[5]),
					Integer.parseInt(f[6]), Integer.parseInt(f[7]), Integer.parseInt(f[8]), Long.parseLong(f[9]), f[10]);
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** "X 120 Y 64 Z -340". */
	public String coordinates() {
		return String.format(Locale.ROOT, "X %d Y %d Z %d", x, y, z);
	}

	/** Printable ASCII without '|', at most {@code max} characters. */
	private static String clean(String text, int max) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(char c : text.toCharArray()) {
			if(c >= 32 && c < 127 && c != '|')
				out.append(c);
			if(out.length() >= max)
				break;
		}
		return out.toString().strip();
	}
}
