package com.arrl.radiocraft.common.radio.wxalert;

/**
 * The Simple Weather Alert Receiver's battery, lamp and redstone, as pure arithmetic over game ticks.
 * <ul>
 * <li><b>Battery</b>: a Small Alkaline (or NiCd) Battery lasts {@link #BATTERY_DAYS} in-game days of listening; sounding an alert
 * draws {@link #ALARM_DRAW_FACTOR} times as much while it plays. It is low below {@link #LOW_FRACTION} of a full one.</li>
 * <li><b>Lamp</b>: during an alert it flashes red, {@link #ALERT_FLASH_TICKS} on and off; with a low battery it blinks
 * for {@link #LOW_BLINK_TICKS} every {@link #LOW_BLINK_PERIOD} ticks (10 s).</li>
 * <li><b>Redstone</b>: one pulse of {@link #PULSE_TICKS} for each batch of new alerts, never held.</li>
 * </ul>
 */
public final class ReceiverSignals {

	public static final int BATTERY_DAYS = 21;
	public static final int TICKS_PER_DAY = 24000;
	public static final double LOW_FRACTION = 0.10D;
	public static final int ALARM_DRAW_FACTOR = 20;
	public static final int ALERT_FLASH_TICKS = 10;
	public static final int LOW_BLINK_PERIOD = 200;
	public static final int LOW_BLINK_TICKS = 4;
	public static final int PULSE_TICKS = 4;
	/** How long the lamp keeps flashing after a new alert unless it is silenced, ticks (5 minutes). */
	public static final int ALERT_FLASH_LENGTH = 6000;

	private ReceiverSignals() {}

	/** FE a tick to empty a battery of {@code capacity} in {@code days} game days. */
	public static double drainPerTick(int capacity, int days) {
		return capacity / (double)(Math.max(1, days) * (long)TICKS_PER_DAY);
	}

	/** The whole FE to take at {@code gameTime} for a drain of {@code perTick} (under 1): N ticks add up to N times it. */
	public static int drainAt(double perTick, long gameTime) {
		return (int)(Math.floor((gameTime + 1) * perTick) - Math.floor(gameTime * perTick));
	}

	/** Game days a battery with {@code energy} left lasts at {@code perTick}. */
	public static double daysLeft(int energy, double perTick) {
		return perTick <= 0 ? Double.POSITIVE_INFINITY : energy / perTick / TICKS_PER_DAY;
	}

	/** Whether {@code energy} of a {@code capacity} battery is low (but not flat). */
	public static boolean isLow(int energy, int capacity) {
		return energy > 0 && energy < capacity * LOW_FRACTION;
	}

	/**
	 * Whether the red lamp is lit at {@code gameTime}: flashing while {@code alerting}; otherwise a short blink every 10 s
	 * on a low battery; dark on a flat one.
	 */
	public static boolean lampLit(boolean alerting, boolean low, boolean flat, long gameTime) {
		if(flat)
			return false;
		if(alerting)
			return (gameTime / ALERT_FLASH_TICKS) % 2 == 0;
		return low && Math.floorMod(gameTime, LOW_BLINK_PERIOD) < LOW_BLINK_TICKS;
	}

	/** One redstone pulse: started by {@link #trigger}, high for {@link #PULSE_TICKS} ticks of {@link #tick}, then low. */
	public static final class Pulse {
		private int remaining = 0;

		/** Starts a pulse unless one is already going (two batches in one pulse are one pulse). */
		public void trigger() {
			if(remaining <= 0)
				remaining = PULSE_TICKS;
		}

		/** Advances a tick: true while the pulse is high. */
		public boolean tick() {
			if(remaining <= 0)
				return false;
			remaining--;
			return true;
		}

		public boolean isHigh() {
			return remaining > 0;
		}
	}
}
