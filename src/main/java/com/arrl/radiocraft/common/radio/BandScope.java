package com.arrl.radiocraft.common.radio;

/**
 * A band scope on one radio's antenna (computer batch 3B, the rig control program's waterfall): every signal that
 * reaches the antenna, on any frequency in the span, at the strength the antenna network already worked out for it
 * (the same voice, CW and data packets the radio picks its own frequency out of). Nothing new is computed per signal:
 * the radio taps each packet it is handed anyway, before it checks the frequency, and keeps the strongest per bin until
 * the scope is read.
 * <p>
 * A radio only has a scope while someone looks at it: {@code RadioBlockEntity#scope} attaches one for a short lease
 * that each read renews, and drops it when the lease runs out, so with nobody watching the cost is one null check per
 * packet. Written on the voice chat thread, read on the server thread: a race only loses a reading for one frame, and
 * the arrays are never resized.
 */
public final class BandScope {

	/** Bins across the span (the waterfall's columns). */
	public static final int BINS = 76;

	private volatile double lowHz, highHz;
	private final float[] peak = new float[BINS];
	private volatile long leaseUntil;

	public BandScope(double lowHz, double highHz, long leaseUntil) {
		span(lowHz, highHz);
		this.leaseUntil = leaseUntil;
	}

	/** Watches {@code lowHz} to {@code highHz} from now on (readings so far are dropped if the span moved). */
	public void span(double lowHz, double highHz) {
		if(lowHz == this.lowHz && highHz == this.highHz)
			return;
		this.lowHz = lowHz;
		this.highHz = Math.max(lowHz + 1.0D, highHz);
		java.util.Arrays.fill(peak, 0.0F);
	}

	public double lowHz() {
		return lowHz;
	}

	public double highHz() {
		return highHz;
	}

	public long leaseUntil() {
		return leaseUntil;
	}

	public void renew(long until) {
		leaseUntil = until;
	}

	/** A signal at {@code hz} reached the antenna at {@code strength} (0 to 1). Voice chat thread or server thread. */
	public void hear(double hz, double strength) {
		int bin = com.arrl.radiocraft.common.computer.Waterfall.bin(hz, lowHz, highHz, BINS);
		if(bin < 0 || !(strength > 0.0D))
			return;
		if(strength > peak[bin])
			peak[bin] = (float)strength;
	}

	/** Copies the strongest signal per bin since the last read into {@code out} and starts over. */
	public void drain(float[] out) {
		for(int i = 0; i < BINS && i < out.length; i++) {
			out[i] = peak[i];
			peak[i] = 0.0F;
		}
	}
}
