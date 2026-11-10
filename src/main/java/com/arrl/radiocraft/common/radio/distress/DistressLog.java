package com.arrl.radiocraft.common.radio.distress;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The distress alerts a marine radio's DSC receiver (or a commercial VHF radio on 161.975, beacons) has heard (2026-11-09), newest first, at most {@link #MAX}: one
 * entry per alert, kept up to date by its repeats, acknowledgement and cancel (a cancelled call stays listed, marked so).
 * The radio shows one of them on its display for a while ({@link #shown}); the DISTRESS program reads them all.
 * Server thread only; pure, so it is unit tested.
 */
public final class DistressLog {

	/** Alerts kept; the oldest goes first. */
	public static final int MAX = 16;

	/** One alert heard: its latest burst and when it came. */
	public record Entry(DistressAlert alert, long heardAt) {}

	/** What a burst did to the log. */
	public enum Change {
		/** An alert not heard before. */
		NEW,
		/** A known alert moved on: acknowledged, cancelled, spent. */
		STATE,
		/** A repeat of what we have (its position may have moved). */
		REPEAT,
		/** Nothing: an old state arriving late (a repeat after the acknowledgement). */
		NONE
	}

	private final List<Entry> entries = new ArrayList<>();
	private long shownId = 0L;
	private long shownUntil = 0L;

	/** Every alert, newest heard first. */
	public List<Entry> entries() {
		return List.copyOf(entries);
	}

	/** The entry for alert {@code id}, or null. */
	@Nullable
	public Entry get(long id) {
		for(Entry entry : entries)
			if(entry.alert().id() == id)
				return entry;
		return null;
	}

	/** Files {@code alert}, heard at {@code now}, and says what changed. */
	public Change hear(DistressAlert alert, long now) {
		for(int i = 0; i < entries.size(); i++) {
			Entry had = entries.get(i);
			if(had.alert().id() != alert.id() || had.alert().type() != alert.type())
				continue;
			DistressAlert.State was = had.alert().state();
			if(alert.state() == was) {
				entries.remove(i);
				entries.addFirst(new Entry(alert, now));
				return Change.REPEAT;
			}
			if(!alert.state().follows(was))
				return Change.NONE;
			// An ack or cancel carries the caller's last position; keep the nature the call had.
			entries.set(i, new Entry(alert, now));
			return Change.STATE;
		}
		entries.addFirst(new Entry(alert, now));
		while(entries.size() > MAX)
			entries.removeLast();
		return Change.NEW;
	}

	/** Marks alert {@code id} as {@code state} (we acknowledged it ourselves), if it may move there. */
	public void setState(long id, DistressAlert.State state, String ackBy) {
		for(int i = 0; i < entries.size(); i++) {
			Entry had = entries.get(i);
			if(had.alert().id() == id && state.follows(had.alert().state())) {
				DistressAlert a = had.alert();
				entries.set(i, new Entry(new DistressAlert(a.id(), a.type(), state, a.owner(), a.nature(), a.x(), a.y(), a.z(), a.time(), ackBy), had.heardAt()));
			}
		}
	}

	/** Shows alert {@code id} on the display until game time {@code until}. */
	public void show(long id, long until) {
		shownId = id;
		shownUntil = until;
	}

	/** The alert on the display at {@code now}, or null. */
	@Nullable
	public Entry shown(long now) {
		return now < shownUntil ? get(shownId) : null;
	}

	/** Clears the display (CLR). */
	public void dismiss() {
		shownId = 0L;
		shownUntil = 0L;
	}

	public long shownId() {
		return shownId;
	}

	public long shownUntil() {
		return shownUntil;
	}

	/**
	 * Several radios' logs as one list, newest heard first (2026-11-09: the DISTRESS program listens through every marine
	 * and commercial VHF radio cabled to it): one entry per alert, the one furthest on (an alert never goes back to
	 * ACTIVE once one radio has it acknowledged or cancelled), else the latest heard; heard at the latest any radio
	 * heard it.
	 */
	public static List<Entry> merge(List<List<Entry>> logs) {
		List<Entry> out = new ArrayList<>();
		for(List<Entry> log : logs)
			for(Entry entry : log) {
				int at = -1;
				for(int i = 0; i < out.size(); i++)
					if(out.get(i).alert().id() == entry.alert().id() && out.get(i).alert().type() == entry.alert().type())
						at = i;
				if(at < 0) {
					out.add(entry);
					continue;
				}
				Entry had = out.get(at);
				long heard = Math.max(had.heardAt(), entry.heardAt());
				DistressAlert.State was = had.alert().state(), now = entry.alert().state();
				DistressAlert keep;
				if(now == was)
					keep = entry.heardAt() > had.heardAt() ? entry.alert() : had.alert();
				else
					keep = now.follows(was) ? entry.alert() : had.alert();
				out.set(at, new Entry(keep, heard));
			}
		out.sort((a, b) -> Long.compare(b.heardAt(), a.heardAt()));
		return out;
	}

	/** Forgets everything. */
	public void clear() {
		entries.clear();
		dismiss();
	}

	/** Puts back what {@link #entries}, {@link #shownId} and {@link #shownUntil} gave (loading a save). */
	public void restore(List<Entry> saved, long id, long until) {
		entries.clear();
		for(Entry entry : saved)
			if(entries.size() < MAX)
				entries.add(entry);
		shownId = id;
		shownUntil = until;
	}
}
