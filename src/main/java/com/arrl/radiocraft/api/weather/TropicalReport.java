package com.arrl.radiocraft.api.weather;

import java.util.List;

/**
 * A tropical advisory for a place (2026-11-15): a tropical disturbance, storm or hurricane whose forecast cone (its track,
 * plus or minus twice its width) reaches the place within about two days. Some are false alarms: a disturbance may
 * not develop, and a storm in the cone may pass by. From the {@link WeatherSource}; Another Weather Mod gives them.
 *
 * @param id            the storm, the same for all its reports
 * @param stage         a disturbance, a storm a day or two off, a watch (within a day) or a warning (within half a day)
 * @param hurricane     a hurricane (or forecast to peak as one); false for a tropical storm, and for a disturbance
 * @param category      its forecast peak category 1 to 5, 0 a tropical storm, -1 a disturbance
 * @param chancePercent a disturbance's chance of developing into a hurricane, percent
 * @param formsAt       game time it forms (or formed)
 * @param arrivalTick   when its cone reaches the place, game time
 * @param coneRadius    the cone's half width, blocks
 * @param track         its forecast track from now: time, centre and radius
 */
public record TropicalReport(long id, Stage stage, boolean hurricane, int category, int chancePercent, long formsAt,
		long arrivalTick, double coneRadius, List<TrackPoint> track) {

	public enum Stage { DISTURBANCE, OUTLOOK, WATCH, WARNING }

	public record TrackPoint(long tick, double x, double z, double radius) {}

	/** Whether it has formed (a storm), rather than a disturbance that may yet come to nothing. */
	public boolean formed() {
		return stage != Stage.DISTURBANCE;
	}
}
