package com.arrl.radiocraft.common.radio.firealarm;

import java.util.Locale;

/**
 * What a location's alarm is (2026-11-11): set per pull station or panic button on its box's programming panel, sent
 * with its alarm and shown and printed by the controller ("ZONE 03 MEDICAL"). Everything else about it is a fire
 * alarm's: the buzzer, the bells, the comparator, the acks.
 */
public enum AlarmKind {
	FIRE, THREAT, DISASTER, MEDICAL, ESCAPE, LOCKDOWN, FLOOD, BREACH, TORNADO;

	/** The kind named {@code name}, FIRE for anything else (an old box, or none). */
	public static AlarmKind of(String name) {
		try {
			return name == null || name.isEmpty() ? FIRE : valueOf(name.trim().toUpperCase(Locale.ROOT));
		}
		catch(IllegalArgumentException e) {
			return FIRE;
		}
	}

	/** The next kind ({@code by} 1) or the one before (-1), round. */
	public AlarmKind step(int by) {
		AlarmKind[] all = values();
		return all[Math.floorMod(ordinal() + by, all.length)];
	}
}
