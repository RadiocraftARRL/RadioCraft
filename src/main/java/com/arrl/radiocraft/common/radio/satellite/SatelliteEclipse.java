package com.arrl.radiocraft.common.radio.satellite;

/**
 * Eclipses on a flat battery (2026-11-12, a server option, off by default; rare). Pure arithmetic, unit tested.
 * <p>
 * Time is cut into slots of {@link #SLOT_TICKS}, about one time round its orbit (a Minecraft day over
 * {@link SatelliteOrbit#WRAPS}). A slot at night has a {@link #CHANCE} chance, fixed by the satellite's seed and the
 * slot's number, of finding the satellite in the Earth's shadow on a battery too flat to run anything: its repeater, its
 * mode and its beacon are silent until the slot ends or the sun comes up. For {@link #RECOVERY_SLOTS} slots after, it
 * runs again but its telemetry shows the battery low and recovering.
 */
public final class SatelliteEclipse {

	private SatelliteEclipse() {}

	/** About one revolution, ticks. */
	public static final long SLOT_TICKS = (long)(SatelliteOrbit.DAY_TICKS / SatelliteOrbit.WRAPS);
	/** The chance a night slot is an eclipse on a flat battery. */
	public static final double CHANCE = 0.05D;
	/** Slots after an eclipse that the battery shows low. */
	public static final int RECOVERY_SLOTS = 3;
	/** Night, in day time ticks: from dusk to dawn. */
	public static final long NIGHT_START = 13_000L, NIGHT_END = 23_000L;

	/** True if {@code dayTime} (the world's time of day, any day) is night. */
	public static boolean night(long dayTime) {
		long t = Math.floorMod(dayTime, (long)SatelliteOrbit.DAY_TICKS);
		return t >= NIGHT_START && t < NIGHT_END;
	}

	/** The slot {@code gameTime} is in. */
	public static long slot(long gameTime) {
		return Math.floorDiv(gameTime, SLOT_TICKS);
	}

	/** True if slot {@code slot} draws an eclipse for the satellite with {@code seed}, if it falls at night. */
	public static boolean unlucky(long seed, long slot) {
		long h = mix(seed * 0x9E3779B97F4A7C15L + slot);
		return (h >>> 11) * 0x1.0p-53 < CHANCE;
	}

	/** True if the satellite with {@code seed} is eclipsed on a flat battery at {@code gameTime} ({@code dayTime} then). */
	public static boolean eclipsed(long seed, long gameTime, long dayTime) {
		return night(dayTime) && unlucky(seed, slot(gameTime));
	}

	/**
	 * Slots since its last eclipse (1 to {@link #RECOVERY_SLOTS}) while the battery is still low after one; 0 if it is
	 * not (or it is eclipsed now).
	 */
	public static int recovering(long seed, long gameTime, long dayTime) {
		if(eclipsed(seed, gameTime, dayTime))
			return 0;
		for(int back = 1; back <= RECOVERY_SLOTS; back++)
			if(eclipsed(seed, gameTime - back * SLOT_TICKS, dayTime - back * SLOT_TICKS))
				return back;
		return 0;
	}

	/** A seed for a satellite: its name and launch time. */
	public static long seed(String name, long launchMillis) {
		return mix(name.hashCode() * 31L + launchMillis);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
