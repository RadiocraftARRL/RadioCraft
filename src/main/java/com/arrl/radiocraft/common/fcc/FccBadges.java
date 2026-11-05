package com.arrl.radiocraft.common.fcc;

import java.util.UUID;

/**
 * Pure rules for the FCC badge (1.12): the number stamped on it and the name it carries. No game classes, so unit
 * tests can use it.
 */
public final class FccBadges {

	/** Lowest and highest badge number. */
	public static final int FIRST_NUMBER = 1000, LAST_NUMBER = 9999;
	/** Longest agent name kept on a badge (a player name is at most 16). */
	public static final int NAME_MAX = 32;
	/** How far away players see a badge being shown, in blocks. */
	public static final double SHOW_RANGE = 8.0;
	/** Ticks before the same badge can be shown again. */
	public static final int SHOW_COOLDOWN = 40;

	private FccBadges() {}

	/**
	 * The badge number for an agent: always the same for the same player, so a lost badge is reissued with its old
	 * number, and spread over {@link #FIRST_NUMBER}..{@link #LAST_NUMBER}.
	 */
	public static int numberFor(UUID agent) {
		long mixed = agent.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ agent.getLeastSignificantBits();
		mixed ^= mixed >>> 31;
		int span = LAST_NUMBER - FIRST_NUMBER + 1;
		return FIRST_NUMBER + (int)Math.floorMod(mixed, (long)span);
	}

	/** {@code name} trimmed and cut to {@link #NAME_MAX}; never null. */
	public static String cleanName(String name) {
		if(name == null)
			return "";
		String trimmed = name.strip();
		return trimmed.length() > NAME_MAX ? trimmed.substring(0, NAME_MAX) : trimmed;
	}

	/** True when the badge was issued to {@code holder} (its own agent is showing it). */
	public static boolean isOwnBadge(UUID agent, UUID holder) {
		return agent != null && agent.equals(holder);
	}
}
