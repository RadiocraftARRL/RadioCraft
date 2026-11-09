package com.arrl.radiocraft.common.radio.cb;

/**
 * The CB mobiles (2026-11-08). Both are 40-channel AM, 5 W, and need no licence; they differ in the extras.
 * <ul>
 *     <li>{@link #DELUXE_5875}: the big 1970s base/mobile with a woodgrain lid. A clock, an RX signal / TX power meter, a
 *     channel LED, an ON AIR lamp, and a meter that reads SWR or modulation (MOD/SWR switch), and a speech compressor.
 *     Built with a premium 10m crystal.</li>
 *     <li>{@link #PC68}: a compact chrome-faced mobile: meter, channel LED, RX/TX lamp, and a CH9 switch. Cheaper, with any
 *     10m crystal.</li>
 * </ul>
 */
public enum CbMobileModel {

	DELUXE_5875("cb_mobile_5875", true),
	PC68("cb_mobile_pc68", false);

	private final String id;
	private final boolean deluxe;

	CbMobileModel(String id, boolean deluxe) {
		this.id = id;
		this.deluxe = deluxe;
	}

	public String id() {
		return id;
	}

	/** The speech compressor, the MOD/SWR meter and the clock. */
	public boolean deluxe() {
		return deluxe;
	}
}
