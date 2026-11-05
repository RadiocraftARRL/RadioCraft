package com.arrl.radiocraft.client;

import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import org.jetbrains.annotations.Nullable;

/**
 * The unit IDs this player's commercial handhelds heard last (1.12 playtest fixes 2), for their displays: one per
 * receive frequency, kept {@link #SHOW_MS} after the last word from the server (it repeats while the unit talks).
 */
public final class CallerIds {

	/** How long a caller stays on the display after it was last heard, ms. */
	public static final long SHOW_MS = 4000L;
	private static final int SLOTS = 4;

	private static final float[] HZ = new float[SLOTS];
	private static final int[] UNIT = new int[SLOTS];
	private static final long[] AT = new long[SLOTS];

	private CallerIds() {}

	/** Unit {@code unitId} heard on {@code rxHz} now. */
	public static void heard(float rxHz, int unitId) {
		long now = System.currentTimeMillis();
		int slot = 0;
		for(int i = 0; i < SLOTS; i++) {
			if(Math.abs(HZ[i] - rxHz) < 1.0F) {
				slot = i;
				break;
			}
			if(AT[i] < AT[slot])
				slot = i;
		}
		HZ[slot] = rxHz;
		UNIT[slot] = unitId;
		AT[slot] = now;
	}

	/** The unit heard lately on {@code rxHz}, or 0. */
	public static int unitOn(float rxHz) {
		long now = System.currentTimeMillis();
		for(int i = 0; i < SLOTS; i++)
			if(Math.abs(HZ[i] - rxHz) < 1.0F && now - AT[i] <= SHOW_MS)
				return UNIT[i];
		return 0;
	}

	/** What a handheld programmed with {@code program}, on {@code channel}, shows for its caller: alias or "ID 1234", or null. */
	@Nullable
	public static String callerText(CommercialProgram program, @Nullable CommercialChannel channel) {
		if(channel == null)
			return null;
		int unit = unitOn(channel.rxHz());
		return unit <= 0 ? null : program.callerText(unit);
	}
}
