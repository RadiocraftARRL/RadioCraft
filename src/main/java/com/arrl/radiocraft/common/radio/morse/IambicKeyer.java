package com.arrl.radiocraft.common.radio.morse;

/**
 * The iambic keyer's electronics (2026-11-12, after the Heathkit HD-1410): turns the two paddles into a key line, one
 * tick at a time. Pure, so its sequences are unit tested.
 * <ul>
 * <li>{@link Mode#IAMBIC_A}: dit paddle, dits; dah paddle, dahs; both squeezed, they alternate. Let go and it stops
 * after the element it is sending.</li>
 * <li>{@link Mode#IAMBIC_B}: the same, but let go of a squeeze mid-element and it sends one more, the other kind.</li>
 * <li>{@link Mode#BUG}: like a Vibroplex bug: dit paddle, automatic dits; dah paddle, keyed by hand for as long as held.</li>
 * <li>{@link Mode#STRAIGHT}: either paddle is a straight key.</li>
 * </ul>
 * A dit is {@code unit} ticks on, a dah three; every element is followed by a one-unit gap.
 */
public final class IambicKeyer {

	public enum Mode { IAMBIC_A, IAMBIC_B, BUG, STRAIGHT }

	private enum Element { DIT, DAH }

	private Mode mode;
	private int unit;
	private int onLeft = 0, offLeft = 0;
	private Element last = null;
	private boolean squeezed = false;

	public IambicKeyer(Mode mode, int unit) {
		this.mode = mode;
		this.unit = Math.max(1, unit);
	}

	public void set(Mode mode, int unit) {
		this.mode = mode;
		this.unit = Math.max(1, unit);
	}

	/** Words a minute at a dit of {@code unit} ticks: 1200 over the dit in milliseconds (PARIS), 50 ms a tick. */
	public static int wpm(int unit) {
		return Math.round(1200.0F / (Math.max(1, unit) * 50.0F));
	}

	/** One tick: the paddles as they are now. Returns true if the key line is down this tick. */
	public boolean tick(boolean dit, boolean dah) {
		switch(mode) {
			case STRAIGHT -> {
				onLeft = offLeft = 0;
				return dit || dah;
			}
			case BUG -> {
				if(dah) { // The dah side is a plain key.
					onLeft = offLeft = 0;
					return true;
				}
				if(onLeft == 0 && offLeft == 0) {
					if(!dit)
						return false;
					onLeft = unit;
					offLeft = unit;
				}
				return step();
			}
			default -> {
				if(onLeft > 0 || offLeft > 0) { // Mid-element: note a squeeze for mode B.
					if(dit && dah)
						squeezed = true;
					return step();
				}
				Element next = null;
				if(dit && dah)
					next = last == Element.DIT ? Element.DAH : Element.DIT;
				else if(dit)
					next = Element.DIT;
				else if(dah)
					next = Element.DAH;
				else if(mode == Mode.IAMBIC_B && squeezed && last != null)
					next = last == Element.DIT ? Element.DAH : Element.DIT; // The one extra element.
				squeezed = dit && dah;
				if(next == null) {
					last = null;
					return false;
				}
				last = next;
				onLeft = next == Element.DIT ? unit : 3 * unit;
				offLeft = unit;
				return step();
			}
		}
	}

	private boolean step() {
		if(onLeft > 0) {
			onLeft--;
			return true;
		}
		if(offLeft > 0)
			offLeft--;
		return false;
	}
}
