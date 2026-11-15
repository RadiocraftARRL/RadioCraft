package com.arrl.radiocraft.common.radio.satellite.imaging;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pixels of the scan lines weather satellites have just sent (2026-11-12), by packet id: the packet on the air only
 * says where its line lies ({@link AptLine.Geometry}); a station that copies it takes the pixels from here, through its
 * own noise ({@link AptLine#degrade}). Lines are sent and copied within a tick; they are kept a few seconds. Server
 * thread.
 */
public final class AptLines {

	/** How long a line is kept, ticks. */
	public static final int KEEP_TICKS = 100;
	private static final int MAX = 4096;

	private record Kept(byte[] pixels, long at) {}

	private static final Map<Long, Kept> LINES = new LinkedHashMap<>();

	private AptLines() {}

	/** Keeps {@code pixels} as packet {@code id}'s line, sent at game time {@code now}; drops old ones. */
	public static synchronized void put(long id, byte[] pixels, long now) {
		LINES.put(id, new Kept(pixels, now));
		Iterator<Kept> it = LINES.values().iterator();
		while(it.hasNext()) {
			Kept kept = it.next();
			if(now - kept.at() <= KEEP_TICKS && LINES.size() <= MAX)
				break;
			it.remove();
		}
	}

	/** Packet {@code id}'s line (a copy), or null if none is kept. */
	@Nullable
	public static synchronized byte[] get(long id) {
		Kept kept = LINES.get(id);
		return kept == null ? null : kept.pixels().clone();
	}

	/** Server stopped. */
	public static synchronized void clear() {
		LINES.clear();
	}
}
