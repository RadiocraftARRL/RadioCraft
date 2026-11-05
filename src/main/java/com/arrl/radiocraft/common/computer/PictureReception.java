package com.arrl.radiocraft.common.computer;

import java.util.Random;

/**
 * One picture coming in over the radio to the computer, line by line (1.12 step 7: SSTV and WEFAX). The sender's
 * picture is known when its tones start (the header carries it); each line is shown as its tones arrive, clean while a
 * radio of the station is copying the signal and as noise while it is not, the way a weak or fading signal tears a real
 * SSTV or fax picture. Lines not come yet are {@code blank}. Pure (no game classes), so it is unit tested.
 */
public final class PictureReception {

	/** How the lines are timed. */
	public enum Clock {
		/** SSTV: the lines one after another across the whole transmission. */
		SSTV,
		/** Weather fax: a start tone and phasing first, then the lines, then the stop tone (as {@code WefaxAudio}). */
		WEFAX
	}

	/** A picture's width and height. */
	public static final int SIZE = 128;
	// The shares of a weather fax transmission (WefaxAudio.START_SHARE, PHASING_SHARE, IMAGE_SHARE).
	private static final double START_SHARE = 0.08D, PHASING_SHARE = 0.07D, IMAGE_SHARE = 0.80D;

	private final long id;
	private final String from;
	private final String label;
	private final byte[] picture;
	private final byte[] shown;
	private final long start;
	private final int ticks;
	private final Clock clock;
	private final byte dark, light;
	private int rowsDone = 0;
	private int rowsLost = 0;

	/**
	 * A picture from {@code from} (what it is: {@code label}) whose tones started at game time {@code start} and last
	 * {@code ticks}. {@code blank} fills lines not come yet; lost lines are noise of {@code dark} and {@code light}.
	 */
	public PictureReception(long id, String from, String label, byte[] picture, long start, int ticks, Clock clock, byte blank, byte dark, byte light) {
		if(picture.length != SIZE * SIZE)
			throw new IllegalArgumentException("A picture is " + SIZE * SIZE + " pixels");
		this.id = id;
		this.from = from;
		this.label = label;
		this.picture = picture.clone();
		this.shown = new byte[SIZE * SIZE];
		java.util.Arrays.fill(shown, blank);
		this.start = start;
		this.ticks = Math.max(1, ticks);
		this.clock = clock;
		this.dark = dark;
		this.light = light;
	}

	/** Lines of a {@code ticks}-long transmission that have come in {@code elapsed} ticks after its start. */
	public static int rowsAt(Clock clock, long elapsed, int ticks, int size) {
		double at = elapsed / (double)Math.max(1, ticks);
		if(clock == Clock.WEFAX)
			at = (at - START_SHARE - PHASING_SHARE) / IMAGE_SHARE;
		return (int)Math.max(0, Math.min(size, Math.floor(at * size)));
	}

	/**
	 * Brings the picture up to game time {@code now}: each new line clean if {@code copying} (a radio copying the signal
	 * this tick), else noise. True if a line came.
	 */
	public boolean advance(long now, boolean copying) {
		int rows = rowsAt(clock, now - start, ticks, SIZE);
		if(rows <= rowsDone)
			return false;
		while(rowsDone < rows) {
			if(copying)
				System.arraycopy(picture, rowsDone * SIZE, shown, rowsDone * SIZE, SIZE);
			else {
				noise(rowsDone);
				rowsLost++;
			}
			rowsDone++;
		}
		return true;
	}

	/** Every line not come yet, as it is now ({@code copying}): when the last packet arrives a little before the clock. */
	public void finish(boolean copying) {
		while(rowsDone < SIZE) {
			if(copying)
				System.arraycopy(picture, rowsDone * SIZE, shown, rowsDone * SIZE, SIZE);
			else {
				noise(rowsDone);
				rowsLost++;
			}
			rowsDone++;
		}
	}

	private void noise(int row) {
		Random random = new Random(id * 31L + row);
		for(int x = 0; x < SIZE; x++) {
			boolean keep = random.nextInt(4) == 0; // A little of the picture shows through the noise.
			shown[row * SIZE + x] = keep ? picture[row * SIZE + x] : random.nextBoolean() ? dark : light;
		}
	}

	/** True once every line has come. */
	public boolean complete() {
		return rowsDone >= SIZE;
	}

	/** True once the transmission's time is over (and some slack), whether or not its end was heard. */
	public boolean overdue(long now, int slack) {
		return now - start > ticks + slack;
	}

	/** The packet id the picture came with (its header's). */
	public long id() {
		return id;
	}

	public String from() {
		return from;
	}

	public String label() {
		return label;
	}

	/** The picture as it has come so far (do not change it). */
	public byte[] shown() {
		return shown;
	}

	public int rowsDone() {
		return rowsDone;
	}

	/** Lines that came in as noise. */
	public int rowsLost() {
		return rowsLost;
	}

	/** How far along, 0 to 1. */
	public double progress() {
		return rowsDone / (double)SIZE;
	}
}
