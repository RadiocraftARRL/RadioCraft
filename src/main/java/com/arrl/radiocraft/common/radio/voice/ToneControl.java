package com.arrl.radiocraft.common.radio.voice;

/**
 * A table radio's BASS and TREBLE knobs (2026-11-13, the FM broadcast receiver): a low shelf under {@link #BASS_HZ}
 * and a high shelf over {@link #TREBLE_HZ}, each from {@link #RANGE_DB} down to as much up, flat at the middle of the
 * knob. Biquads after the RBJ cookbook, carrying their state from frame to frame (one per receiver). Pure, so it is
 * unit tested.
 */
public final class ToneControl {

	public static final double BASS_HZ = 250.0D, TREBLE_HZ = 2500.0D;
	/** How far a knob turned all the way boosts or cuts, dB. */
	public static final double RANGE_DB = 10.0D;

	private final double sampleRate;
	private double bass = 0.0D, treble = 0.0D;
	private final Biquad low = new Biquad(), high = new Biquad();

	public ToneControl(double sampleRate) {
		this.sampleRate = sampleRate;
		set(0.0D, 0.0D);
	}

	/** True if both knobs sit at the middle: nothing to do. */
	public boolean flat() {
		return Math.abs(bass) < 1e-3 && Math.abs(treble) < 1e-3;
	}

	/** The knobs, each -1 (cut) to 1 (boost), 0 flat. */
	public void set(double bass, double treble) {
		bass = Math.max(-1.0D, Math.min(1.0D, bass));
		treble = Math.max(-1.0D, Math.min(1.0D, treble));
		if(bass == this.bass && treble == this.treble && low.ready)
			return;
		this.bass = bass;
		this.treble = treble;
		low.shelf(false, BASS_HZ, bass * RANGE_DB, sampleRate);
		high.shelf(true, TREBLE_HZ, treble * RANGE_DB, sampleRate);
	}

	/** {@code pcm} through both shelves, as a new frame (the input is left alone). */
	public short[] process(short[] pcm) {
		if(flat())
			return pcm;
		short[] out = new short[pcm.length];
		for(int i = 0; i < pcm.length; i++) {
			double s = high.next(low.next(pcm[i]));
			out[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s)));
		}
		return out;
	}

	private static final class Biquad {
		double b0 = 1, b1, b2, a1, a2, x1, x2, y1, y2;
		boolean ready;

		void shelf(boolean highShelf, double hz, double gainDb, double rate) {
			double a = Math.pow(10.0D, gainDb / 40.0D);
			double w = 2.0D * Math.PI * hz / rate, cos = Math.cos(w), sin = Math.sin(w);
			double alpha = sin / 2.0D * Math.sqrt(2.0D); // Shelf slope 1.
			double sq = 2.0D * Math.sqrt(a) * alpha;
			double n0, n1, n2, d0, d1, d2;
			if(highShelf) {
				n0 = a * ((a + 1) + (a - 1) * cos + sq);
				n1 = -2 * a * ((a - 1) + (a + 1) * cos);
				n2 = a * ((a + 1) + (a - 1) * cos - sq);
				d0 = (a + 1) - (a - 1) * cos + sq;
				d1 = 2 * ((a - 1) - (a + 1) * cos);
				d2 = (a + 1) - (a - 1) * cos - sq;
			}
			else {
				n0 = a * ((a + 1) - (a - 1) * cos + sq);
				n1 = 2 * a * ((a - 1) - (a + 1) * cos);
				n2 = a * ((a + 1) - (a - 1) * cos - sq);
				d0 = (a + 1) + (a - 1) * cos + sq;
				d1 = -2 * ((a - 1) + (a + 1) * cos);
				d2 = (a + 1) + (a - 1) * cos - sq;
			}
			b0 = n0 / d0;
			b1 = n1 / d0;
			b2 = n2 / d0;
			a1 = d1 / d0;
			a2 = d2 / d0;
			ready = true;
		}

		double next(double x) {
			double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
			x2 = x1;
			x1 = x;
			y2 = y1;
			y1 = y;
			return y;
		}
	}
}
