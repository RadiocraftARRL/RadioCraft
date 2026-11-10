package com.arrl.radiocraft.common.radio.distress;

/**
 * A marine radio's own DSC distress call (2026-11-09): sent when the DISTRESS button has been held, repeated every
 * 3.5 to 4.5 minutes (a different wait each time, as real radios do so two callers do not keep colliding) until another
 * station acknowledges it or the caller holds the button again to cancel. Immutable; pure, so it is unit tested.
 *
 * @param state   Where the call stands.
 * @param alertId The alert's number (the same on every repeat), 0 while idle.
 * @param mmsi    Who sent it.
 * @param nature  The nature of distress it carries.
 * @param sentAt  Game time it was first sent.
 * @param nextAt  Game time the next repeat is due (only while {@link State#ACTIVE}).
 * @param ackBy   The MMSI that acknowledged it, once {@link State#ACKNOWLEDGED}.
 */
public record DscCall(State state, long alertId, String mmsi, DistressNature nature, long sentAt, long nextAt, String ackBy) {

	public enum State { IDLE, ACTIVE, ACKNOWLEDGED, CANCELLED }

	/** Shortest and longest wait between repeats, in ticks: 3.5 and 4.5 minutes. */
	public static final int REPEAT_MIN_TICKS = 4200, REPEAT_MAX_TICKS = 5400;

	public static final DscCall IDLE = new DscCall(State.IDLE, 0L, "", DistressNature.UNDESIGNATED, 0L, 0L, "");

	public DscCall {
		mmsi = mmsi == null ? "" : mmsi;
		ackBy = ackBy == null ? "" : ackBy;
		nature = nature == null ? DistressNature.UNDESIGNATED : nature;
	}

	/** The wait before the next repeat for {@code random} in [0, 1): 3.5 to 4.5 minutes. */
	public static int repeatTicks(double random) {
		double r = Math.max(0.0D, Math.min(1.0D, random));
		return REPEAT_MIN_TICKS + (int)Math.round(r * (REPEAT_MAX_TICKS - REPEAT_MIN_TICKS));
	}

	/** True while the button sends a new call when held: nothing on, or the last one is over. */
	public boolean canSend() {
		return state != State.ACTIVE && state != State.ACKNOWLEDGED;
	}

	/** True while holding the button cancels: a call is on the air or acknowledged and not yet cancelled. */
	public boolean canCancel() {
		return state == State.ACTIVE || state == State.ACKNOWLEDGED;
	}

	/** A new call {@code id} from {@code fromMmsi} at {@code now}; the first repeat after {@code waitTicks}. */
	public static DscCall send(long id, String fromMmsi, DistressNature nature, long now, int waitTicks) {
		return new DscCall(State.ACTIVE, id, fromMmsi, nature, now, now + waitTicks, "");
	}

	/** True if a repeat is due at {@code now}. */
	public boolean repeatDue(long now) {
		return state == State.ACTIVE && now >= nextAt;
	}

	/** The call after a repeat went out at {@code now}, the next one {@code waitTicks} later. */
	public DscCall repeated(long now, int waitTicks) {
		return state != State.ACTIVE ? this : new DscCall(state, alertId, mmsi, nature, sentAt, now + waitTicks, ackBy);
	}

	/** Ticks until the next repeat at {@code now} (0 if due, or not repeating). */
	public long ticksToRepeat(long now) {
		return state == State.ACTIVE ? Math.max(0L, nextAt - now) : 0L;
	}

	/** An acknowledgement of alert {@code id} by {@code by} heard: the call stops repeating if it is ours. */
	public DscCall acknowledged(long id, String by) {
		if(state != State.ACTIVE || id != alertId)
			return this;
		return new DscCall(State.ACKNOWLEDGED, alertId, mmsi, nature, sentAt, nextAt, by);
	}

	/** Cancelled by the caller (the button held again); unchanged if there is nothing to cancel. */
	public DscCall cancelled() {
		return canCancel() ? new DscCall(State.CANCELLED, alertId, mmsi, nature, sentAt, nextAt, ackBy) : this;
	}

	/** The alert this call puts on the air now, from {@code x, y, z}. */
	public DistressAlert alert(int x, int y, int z) {
		DistressAlert.State on = switch(state) {
			case ACKNOWLEDGED -> DistressAlert.State.ACKNOWLEDGED;
			case CANCELLED -> DistressAlert.State.CANCELLED;
			default -> DistressAlert.State.ACTIVE;
		};
		return new DistressAlert(alertId, DistressAlert.Type.DSC, on, mmsi, nature, x, y, z, sentAt, ackBy);
	}
}
