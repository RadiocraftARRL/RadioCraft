package com.arrl.radiocraft.common.realworld;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a stream of samples arriving in blocks of any size into frames of one fixed size: the voice chat's 20 ms of 48
 * kHz audio (960 samples) that every radio sends and receives. What is left over waits for the next block. Pure.
 */
public final class FrameChunker {

	private final int frameSize;
	private short[] pending;
	private int filled = 0;

	public FrameChunker(int frameSize) {
		if(frameSize <= 0)
			throw new IllegalArgumentException("frame size must be positive");
		this.frameSize = frameSize;
		pending = new short[frameSize];
	}

	/** Adds {@code samples}; returns the frames now complete, in order (maybe none). */
	public List<short[]> push(short[] samples) {
		List<short[]> out = new ArrayList<>(samples.length / frameSize + 1);
		int at = 0;
		while(at < samples.length) {
			int take = Math.min(frameSize - filled, samples.length - at);
			System.arraycopy(samples, at, pending, filled, take);
			filled += take;
			at += take;
			if(filled == frameSize) {
				out.add(pending);
				pending = new short[frameSize];
				filled = 0;
			}
		}
		return out;
	}

	/** Samples waiting for a frame. */
	public int pending() {
		return filled;
	}
}
