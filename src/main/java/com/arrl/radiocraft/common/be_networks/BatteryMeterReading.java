package com.arrl.radiocraft.common.be_networks;

/**
 * What a battery meter reads off the batteries on its power wire: how many, their charge, and the net flow in or out.
 * Shown as a 12 V lead-acid bank would show it. At rest it reads 11.8 V empty to 12.7 V full. Charging lifts it
 * towards 14.4 V, the faster the charge the higher. A heavy draw sags it by up to half a volt.
 *
 * @param batteries The batteries on the wire.
 * @param energy Their charge together, FE.
 * @param capacity Their capacity together, FE.
 * @param flow The smoothed net flow into them, FE per tick (negative while they discharge).
 */
public record BatteryMeterReading(int batteries, long energy, long capacity, double flow) {

	/** No batteries on the wire. */
	public static final BatteryMeterReading NONE = new BatteryMeterReading(0, 0, 0, 0);

	/** Resting voltage of an empty bank. */
	public static final double EMPTY_VOLTS = 11.8D;
	/** Resting voltage of a full bank. */
	public static final double FULL_VOLTS = 12.7D;
	/** What a charger holds a bank at while it charges hard. */
	public static final double CHARGE_VOLTS = 14.4D;
	/** The most a heavy load pulls the voltage down. */
	public static final double MAX_SAG = 0.5D;
	/** Draw per battery that gives the full sag: a 20m radio transmitting (26 FE/t) off one battery. */
	public static final double FULL_LOAD_PER_BATTERY = 26.0D;
	/** Charge per battery that lifts it all the way to {@link #CHARGE_VOLTS}. */
	public static final double FULL_CHARGE_PER_BATTERY = 20.0D;
	/** Flow below this (either way) reads as resting. */
	public static final double IDLE_FLOW = 0.05D;

	public boolean hasBatteries() {
		return batteries > 0 && capacity > 0;
	}

	/** State of charge, 0 to 1. */
	public double charge() {
		return hasBatteries() ? Math.max(0.0D, Math.min(1.0D, (double)energy / capacity)) : 0.0D;
	}

	/** State of charge, whole percent. */
	public int percent() {
		return (int)Math.round(charge() * 100.0D);
	}

	/** The voltage the meter shows: resting voltage from the charge, lifted while charging, sagged under load. */
	public double volts() {
		if(!hasBatteries())
			return 0.0D;
		double rest = EMPTY_VOLTS + (FULL_VOLTS - EMPTY_VOLTS) * charge();
		if(flow > IDLE_FLOW)
			return rest + (CHARGE_VOLTS - rest) * Math.min(1.0D, flow / (FULL_CHARGE_PER_BATTERY * batteries));
		if(flow < -IDLE_FLOW)
			return rest - MAX_SAG * Math.min(1.0D, -flow / (FULL_LOAD_PER_BATTERY * batteries));
		return rest;
	}

	/** Charging, discharging or resting. */
	public Trend trend() {
		if(!hasBatteries())
			return Trend.REST;
		return flow > IDLE_FLOW ? Trend.CHARGING : flow < -IDLE_FLOW ? Trend.DISCHARGING : Trend.REST;
	}

	/**
	 * Comparator output: 0 with no charge (or no batteries), else 1 to 15 with the charge, as vanilla containers count
	 * (any charge at all gives 1, only a full bank gives 15).
	 */
	public int comparator() {
		if(!hasBatteries() || energy <= 0)
			return 0;
		return 1 + (int)Math.floor(charge() * 14.0D);
	}

	/**
	 * Ticks until the bank is empty at this draw, or until it is full at this charge; -1 while resting or with nothing
	 * to show.
	 */
	public long ticksToGo() {
		if(trend() == Trend.DISCHARGING)
			return (long)Math.ceil(energy / -flow);
		if(trend() == Trend.CHARGING)
			return (long)Math.ceil((capacity - energy) / flow);
		return -1;
	}

	/** One step of the meter's smoothing of the flow: an average of about the last second (20 ticks). */
	public static double smooth(double previous, double latest) {
		return previous + (latest - previous) * 0.1D;
	}

	public enum Trend { CHARGING, DISCHARGING, REST }
}
