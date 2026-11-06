package com.arrl.radiocraft.common.computer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The MUSIC program's shuffle (1.12 music player fixes): a shuffled bag of the tracks, so every tune comes round once
 * before any plays again and the same tune never plays twice in a row, and a history so Back goes to the tune heard
 * before. Pure (no game classes), so it is unit tested; {@code ComputerMusic} keeps one for the client.
 */
public final class MusicShuffle {

	/** Tunes kept for Back. */
	private static final int HISTORY = 64;

	private final Deque<Integer> bag = new ArrayDeque<>();
	private final Deque<Integer> history = new ArrayDeque<>();

	/**
	 * The tune to play after {@code current} (-1 if none is playing) out of {@code size}: the next from the bag,
	 * refilled and shuffled with {@code random} when empty, never {@code current} unless it is the only tune. -1 if
	 * there are no tunes. Does not record anything: call {@link #played} when it starts.
	 */
	public int next(int current, int size, RandomGenerator random) {
		if(size <= 0)
			return -1;
		if(size == 1)
			return 0;
		bag.removeIf(i -> i >= size || i == current);
		if(bag.isEmpty()) {
			List<Integer> all = new ArrayList<>(size);
			for(int i = 0; i < size; i++)
				if(i != current)
					all.add(i);
			for(int i = all.size() - 1; i > 0; i--) { // Fisher-Yates.
				int j = random.nextInt(i + 1);
				Integer swap = all.get(i);
				all.set(i, all.get(j));
				all.set(j, swap);
			}
			bag.addAll(all);
		}
		return bag.peekFirst();
	}

	/**
	 * Tune {@code index} has started after {@code previous} (-1 if none): it leaves the bag, and {@code previous} goes on
	 * the history for {@link #back}.
	 */
	public void played(int previous, int index) {
		bag.remove(index);
		if(previous >= 0 && previous != index) {
			history.push(previous);
			while(history.size() > HISTORY)
				history.removeLast();
		}
	}

	/**
	 * The tune heard before {@code current} (taken off the history, so pressing Back again goes further back), or -1
	 * if there is none left.
	 */
	public int back(int current, int size) {
		while(!history.isEmpty()) {
			int i = history.pop();
			if(i < size && i != current)
				return i;
		}
		return -1;
	}

	/** Forgets the bag and the history (shuffle switched on afresh). */
	public void reset() {
		bag.clear();
		history.clear();
	}
}
