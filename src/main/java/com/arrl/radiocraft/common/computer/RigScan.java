package com.arrl.radiocraft.common.computer;

/**
 * The rig control program's scanner (computer batch 3B), pure: steps the radio through a list of frequencies (a range
 * at the radio's tuning step, or the memory channels), {@link #DWELL_TICKS} on each, and stops on one where a signal
 * opens the squelch, as a real scanner does; it moves on again {@link #RESUME_TICKS} after the signal drops (carrier
 * delay). The program can hold it on the channel it is on. Unit tested.
 */
public final class RigScan {

	public enum Mode { OFF, RANGE, MEMORY }

	/** Ticks on each channel while nothing is heard (five channels a second). */
	public static final int DWELL_TICKS = 4;
	/** Ticks after the last signal on a channel before the scan moves on. */
	public static final int RESUME_TICKS = 60;
	/** Most channels a range scan steps through. */
	public static final int MAX_CHANNELS = 4000;

	private Mode mode = Mode.OFF;
	private float[] channels = new float[0];
	private int index;
	private long stepAt;
	private long signalAt = -1L;
	private boolean stopped, held;

	public Mode mode() {
		return mode;
	}

	public boolean isScanning() {
		return mode != Mode.OFF;
	}

	/** True while stopped on a signal. */
	public boolean isStopped() {
		return stopped;
	}

	public boolean isHeld() {
		return held;
	}

	/** The channel the scan is on. */
	public float current() {
		return channels.length == 0 ? 0.0F : channels[index];
	}

	public int index() {
		return index;
	}

	public int size() {
		return channels.length;
	}

	/** Game time the scan last moved to a channel: signals from before then were on another channel. */
	public long stepAt() {
		return stepAt;
	}

	/** Starts scanning {@code list} from the channel after the one nearest {@code fromHz}; nothing for an empty list. */
	public void start(Mode scanMode, float[] list, float fromHz, long now) {
		if(scanMode == Mode.OFF || list.length == 0) {
			stop();
			return;
		}
		mode = scanMode;
		channels = list.clone();
		int nearest = 0;
		for(int i = 1; i < channels.length; i++)
			if(Math.abs(channels[i] - fromHz) < Math.abs(channels[nearest] - fromHz))
				nearest = i;
		index = nearest;
		stepAt = now - DWELL_TICKS; // Move on at once.
		stopped = false;
		held = false;
		signalAt = -1L;
	}

	public void stop() {
		mode = Mode.OFF;
		stopped = false;
		held = false;
	}

	/** Holds the scan on its channel, or lets it go on. */
	public void toggleHold(long now) {
		held = !held;
		if(!held) {
			stopped = false;
			stepAt = now - DWELL_TICKS;
		}
	}

	/**
	 * One tick of scanning. {@code heard} is true if a signal over the squelch reached the radio since {@link #stepAt()}.
	 * Returns the frequency to tune the radio to now, or NaN to stay where it is.
	 */
	public float tick(long now, boolean heard) {
		if(mode == Mode.OFF || channels.length == 0 || held)
			return Float.NaN;
		if(heard) {
			stopped = true;
			signalAt = now;
			return Float.NaN;
		}
		if(stopped) {
			if(now - signalAt < RESUME_TICKS)
				return Float.NaN;
			stopped = false;
			return advance(now);
		}
		if(now - stepAt >= DWELL_TICKS)
			return advance(now);
		return Float.NaN;
	}

	private float advance(long now) {
		index = (index + 1) % channels.length;
		stepAt = now;
		return channels[index];
	}

	/** Every {@code stepHz} from {@code lowHz} to {@code highHz} (both ends, at most {@link #MAX_CHANNELS}); empty if none. */
	public static float[] range(double lowHz, double highHz, int stepHz) {
		if(stepHz <= 0 || !(highHz >= lowHz))
			return new float[0];
		long count = Math.min(MAX_CHANNELS, (long)Math.floor((highHz - lowHz) / stepHz) + 1L);
		float[] out = new float[(int)count];
		for(int i = 0; i < out.length; i++)
			out[i] = (float)(lowHz + (double)i * stepHz);
		return out;
	}
}
