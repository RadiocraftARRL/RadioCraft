package com.arrl.radiocraft.common.radio.distress;

import java.util.UUID;

/**
 * A player's MMSI (Maritime Mobile Service Identity, 2026-11-09): the nine-digit number a DSC radio calls with. Real
 * ones are issued per ship; here each player has one, worked out from their UUID so it never changes and needs no
 * saving. US style: the first three digits are a US MID (338, 366 to 369), so the number starts with a 3 and never
 * with 0 (a leading 0 is a group or coast station call). Pure, so it is unit tested.
 */
public final class Mmsi {

	private Mmsi() {}

	/** The US Maritime Identification Digits a ship MMSI starts with. */
	private static final int[] US_MIDS = { 338, 366, 367, 368, 369 };

	/** {@code uuid}'s MMSI: nine digits, the same every time. */
	public static String of(UUID uuid) {
		long h = mix(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 29));
		int mid = US_MIDS[(int)Long.remainderUnsigned(h, US_MIDS.length)];
		long rest = Long.remainderUnsigned(mix(h + 0x9E3779B97F4A7C15L), 1_000_000L);
		return String.format(java.util.Locale.ROOT, "%03d%06d", mid, rest);
	}

	/** True for nine digits that do not start with 0 (a ship's MMSI). */
	public static boolean isShip(String mmsi) {
		if(mmsi == null || mmsi.length() != 9 || mmsi.charAt(0) == '0')
			return false;
		for(int i = 0; i < 9; i++)
			if(!Character.isDigit(mmsi.charAt(i)))
				return false;
		return true;
	}

	/** SplitMix64's finaliser: spreads the bits so close UUIDs get unrelated numbers. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
