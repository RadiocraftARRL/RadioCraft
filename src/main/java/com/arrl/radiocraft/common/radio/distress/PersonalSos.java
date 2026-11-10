package com.arrl.radiocraft.common.radio.distress;

/**
 * A personal SOS sent from the DISTRESS program (2026-11-09): a beacon-style alert ({@link DistressAlert.Type#BEACON},
 * the player's name as owner) on 161.975 MHz through a commercial VHF mobile cabled to the computer. Unlike a real
 * beacon it carries a nature of distress, and it is not used up: it repeats every {@link BeaconBattery#BURST_TICKS}
 * while the program has it on, until it is cancelled from the program (a cancel burst goes out) or stops because the
 * radio lost its power or its cable (nothing more can go out then). Immutable; pure, so it is unit tested.
 *
 * @param state    Where it stands.
 * @param alertId  The alert's number, the same on every repeat and the cancel; 0 while idle.
 * @param owner    Who sent it (the player's name).
 * @param nature   The nature of distress picked in the program.
 * @param radioPos The radio it goes out through ({@link net.minecraft.core.BlockPos#asLong}).
 * @param sentAt   Game time it was first sent.
 * @param nextAt   Game time the next repeat is due (only while {@link State#ACTIVE}).
 * @param reason   Why it stopped (an error key: no_power, unlinked), once {@link State#STOPPED}; empty otherwise.
 */
public record PersonalSos(State state, long alertId, String owner, DistressNature nature, long radioPos, long sentAt, long nextAt, String reason) {

	public enum State { IDLE, ACTIVE, CANCELLED, STOPPED }

	public static final PersonalSos IDLE = new PersonalSos(State.IDLE, 0L, "", DistressNature.UNDESIGNATED, 0L, 0L, 0L, "");

	public PersonalSos {
		owner = owner == null ? "" : owner;
		reason = reason == null ? "" : reason;
		nature = nature == null ? DistressNature.UNDESIGNATED : nature;
		state = state == null ? State.IDLE : state;
	}

	/** A new SOS {@code id} from {@code who} through the radio at {@code radio}, first sent at {@code now}. */
	public static PersonalSos start(long id, String who, DistressNature nature, long radio, long now) {
		return new PersonalSos(State.ACTIVE, id, who, nature, radio, now, now + BeaconBattery.BURST_TICKS, "");
	}

	/** True while it is on the air (repeating). */
	public boolean active() {
		return state == State.ACTIVE;
	}

	/** True if a repeat is due at {@code now}. */
	public boolean repeatDue(long now) {
		return state == State.ACTIVE && now >= nextAt;
	}

	/** After a repeat went out at {@code now}. */
	public PersonalSos repeated(long now) {
		return state != State.ACTIVE ? this : new PersonalSos(state, alertId, owner, nature, radioPos, sentAt, now + BeaconBattery.BURST_TICKS, reason);
	}

	/** Cancelled from the program (the cancel burst goes out); unchanged if it is not on. */
	public PersonalSos cancelled() {
		return state != State.ACTIVE ? this : new PersonalSos(State.CANCELLED, alertId, owner, nature, radioPos, sentAt, nextAt, "");
	}

	/** Stopped for {@code why} (the radio lost power or its cable); unchanged if it is not on. */
	public PersonalSos stopped(String why) {
		return state != State.ACTIVE ? this : new PersonalSos(State.STOPPED, alertId, owner, nature, radioPos, sentAt, nextAt, why);
	}

	/** Ticks until the next repeat at {@code now} (0 if due or not repeating). */
	public long ticksToRepeat(long now) {
		return state == State.ACTIVE ? Math.max(0L, nextAt - now) : 0L;
	}

	/** The alert it puts on the air now from {@code x, y, z}: active, or cancelled on the cancel burst. */
	public DistressAlert alert(int x, int y, int z) {
		DistressAlert.State on = state == State.CANCELLED ? DistressAlert.State.CANCELLED : DistressAlert.State.ACTIVE;
		return new DistressAlert(alertId, DistressAlert.Type.BEACON, on, owner, nature, x, y, z, sentAt, "");
	}
}
