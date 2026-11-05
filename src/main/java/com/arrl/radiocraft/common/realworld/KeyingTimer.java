package com.arrl.radiocraft.common.realworld;

/**
 * Keys a transmitter from activity, as a repeater controller or a VOX does: it keys at the first activity, stays
 * keyed for a hang time after the activity stops, and drops after a time-out timer if one transmission goes on too long
 * (a stuck VOX, a carrier that never drops, someone who will not stop). Timed out, it stays off until the activity
 * has stopped for {@link #RESET_MS} (the carrier dropping resets a real repeater's timer), then keys again on the next.
 * Used both ways by the Real-World Interface: in-game audio keying the real radio's PTT, and the real radio's audio
 * (VOX) or squelch (COS) keying the in-game transmitter. Times are in milliseconds from any clock. Not thread safe. Pure.
 */
public final class KeyingTimer {

	/** Quiet this long re-arms a timed-out timer. */
	public static final long RESET_MS = 1000L;

	public enum State {
		/** Not keyed. */
		IDLE,
		/** Keyed, activity now. */
		KEYED,
		/** Keyed, in the hang time after activity. */
		HANG,
		/** Dropped by the time-out timer; waiting for the activity to stop. */
		TIMED_OUT
	}

	private final long hangMs;
	private final long timeoutMs;
	private State state = State.IDLE;
	private long keyedSince = -1L;
	private long lastActive = Long.MIN_VALUE / 2;

	/**
	 * @param hangMs    How long it stays keyed after the activity stops.
	 * @param timeoutMs Longest one transmission may last, hang time included; 0 for no limit.
	 */
	public KeyingTimer(long hangMs, long timeoutMs) {
		this.hangMs = Math.max(0L, hangMs);
		this.timeoutMs = Math.max(0L, timeoutMs);
	}

	/** Activity or none at {@code nowMs}; returns true if keyed. */
	public boolean update(boolean active, long nowMs) {
		if(active)
			lastActive = nowMs;
		switch(state) {
			case TIMED_OUT -> {
				if(!active && nowMs - lastActive >= RESET_MS)
					state = State.IDLE;
				return false;
			}
			case IDLE -> {
				if(!active)
					return false;
				state = State.KEYED;
				keyedSince = nowMs;
			}
			case KEYED, HANG -> {
				if(active)
					state = State.KEYED;
				else if(nowMs - lastActive > hangMs) {
					state = State.IDLE;
					keyedSince = -1L;
					return false;
				}
				else
					state = State.HANG;
			}
		}
		if(timeoutMs > 0L && nowMs - keyedSince >= timeoutMs) {
			state = State.TIMED_OUT;
			keyedSince = -1L;
			return false;
		}
		return true;
	}

	public State state() {
		return state;
	}

	public boolean isKeyed() {
		return state == State.KEYED || state == State.HANG;
	}

	public boolean isTimedOut() {
		return state == State.TIMED_OUT;
	}

	/** How long the current transmission has lasted at {@code nowMs}, 0 when not keyed. */
	public long keyedFor(long nowMs) {
		return isKeyed() && keyedSince >= 0L ? nowMs - keyedSince : 0L;
	}

	/** Drops it at once, without timing out (the interface closing). */
	public void reset() {
		state = State.IDLE;
		keyedSince = -1L;
	}
}
