package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.api.weather.StormReport;
import com.arrl.radiocraft.api.weather.WeatherReport;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.common.radio.data.AprsWeather;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Builds a weather alert generator's alerts from what its weather stations report and from the storms of the weather
 * source (Another Weather Mod's, through RadioCraft's {@link WeatherSource}) in the area they cover. What it gives grows
 * with the generator's tier ({@link WeatherNetwork.Check#tier()}, 2026-11-18):
 * <ul>
 * <li>tier 1 and up: a storm over the area now gives a warning for a circle round it and where it will be in
 * {@link #LOOKAHEAD_TICKS}: tornado (once one is down), severe thunderstorm (a tornadic storm before it touches down
 * too), blizzard and dust storm (a supercell over snow or desert), hurricane or tropical storm, high wind, dense fog,
 * excessive heat and extreme cold; a station reporting {@link #FLOOD_RAIN_HUNDREDTHS} of rain in the last hour gives
 * a flash flood warning round it, and one reporting high wind ({@link #HIGH_WIND_MPH} sustained or
 * {@link #HIGH_GUST_MPH} gusts) a high wind warning;</li>
 * <li>tier 2 and up: a storm that will be over the area within {@link #LOOKAHEAD_TICKS} gives its warning already;</li>
 * <li>tier 3: a tornadic or severe storm forecast to reach the area within {@link #WATCH_TICKS} gives a watch for the
 * whole area (tornado watch, severe thunderstorm watch), as does a tornado-watch day there; a tropical disturbance,
 * storm or hurricane forecast to reach the area within two days ({@link #forTropical}) gives a tropical weather
 * statement, then a watch, then a warning (2026-11-15).</li>
 * </ul>
 */
public final class AlertBuilder {

	/** How far ahead a warning's circle reaches along the storm's track, ticks (5 minutes). */
	public static final long LOOKAHEAD_TICKS = 6000L;
	/** How far ahead a forecast storm gives a watch, ticks (15 minutes). */
	public static final long WATCH_TICKS = 18000L;
	/** How long a warning lasts unless it is sent again, ticks (5 minutes; the generator renews it every minute). */
	public static final long WARNING_LIFE = 6000L;
	/** How long a watch lasts unless sent again, ticks (15 minutes). */
	public static final long WATCH_LIFE = 18000L;
	/** Rain in the last hour that floods, hundredths of an inch (1.00 in). */
	public static final int FLOOD_RAIN_HUNDREDTHS = 100;
	public static final int HIGH_WIND_MPH = 40;
	public static final int HIGH_GUST_MPH = 58;
	/** The circle round a station for a flood or wind warning from its report, blocks. */
	public static final int STATION_ALERT_RADIUS = 150;
	/** Most alerts one generator sends at once (the worst). */
	public static final int MAX_ALERTS = 12;

	private AlertBuilder() {}

	/** The alerts for {@code area} (round {@code stations}) at {@code now} with every kind of alert (tier 3), worst first. */
	public static List<WeatherAlert> build(ServerLevel level, WeatherSource source, List<WeatherNetwork.Station> stations, AlertArea area, long now) {
		return build(level, source, stations, area, now, WeatherNetwork.MAX_TIER);
	}

	/** The alerts for {@code area} (round {@code stations}) at {@code now} for a generator at {@code tier}, worst first. */
	public static List<WeatherAlert> build(ServerLevel level, WeatherSource source, List<WeatherNetwork.Station> stations, AlertArea area, long now, int tier) {
		List<WeatherAlert> out = new ArrayList<>();
		BlockPos centre = BlockPos.containing(area.centreX(), level.getSeaLevel(), area.centreZ());
		if(source.isLocal()) {
			for(StormReport storm : source.storms(level, centre, area.radius(), true)) {
				if(storm.worldWide() || storm.track().isEmpty())
					continue;
				WeatherAlert alert = forStorm(level, source, storm, area, now, tier);
				if(alert != null)
					out.add(alert);
			}
			if(tier >= WeatherNetwork.MAX_TIER) { // Tropical statements and watch days need the full network.
				for(com.arrl.radiocraft.api.weather.TropicalReport tropical : source.tropical(level, centre)) {
					WeatherAlert alert = forTropical(tropical, area, now, out);
					if(alert != null)
						out.add(alert);
				}
				if(source.tornadoWatch(level, centre) && out.stream().noneMatch(a -> a.event() == WeatherAlert.Event.TOA))
					out.add(areaAlert(WeatherAlert.Event.TOA, key(WeatherAlert.Event.TOA, now / 24000L), area, now + WATCH_LIFE));
			}
		}
		for(WeatherNetwork.Station station : stations)
			out.addAll(forStation(station, now));
		out.sort(Comparator.comparingInt(alert -> alert.event().ordinal()));
		return out.size() > MAX_ALERTS ? List.copyOf(out.subList(0, MAX_ALERTS)) : out;
	}

	@Nullable
	private static WeatherAlert forStorm(ServerLevel level, WeatherSource source, StormReport storm, AlertArea area, long now, int tier) {
		StormReport.TrackPoint here = storm.at(now);
		boolean formed = storm.formed(now);
		if(formed && (area.touches(here.x(), here.z(), here.radius()) || tier >= 2 && headingIn(storm, area, now))) {
			Set<WeatherReport.Hazard> hazards = source.report(level, BlockPos.containing(here.x(), level.getSeaLevel(), here.z())).hazards();
			WeatherAlert.Event event = warningFor(storm.kind(), WeatherReports.tornadoDown(level, source, storm, now), hazards, storm.category());
			if(event == null)
				return null;
			StormReport.TrackPoint later = storm.at(now + LOOKAHEAD_TICKS);
			double moved = Math.hypot(later.x() - here.x(), later.z() - here.z());
			int heading = moved >= 5.0D ? (int)Math.round(WeatherAlert.bearing(here.x(), here.z(), later.x(), later.z())) % 360 : -1;
			int radius = (int)Math.ceil(Math.max(here.radius(), later.radius()) + moved);
			return new WeatherAlert(event, key(event, storm.id()), (int)Math.round(here.x()), (int)Math.round(here.z()), radius,
					Math.max(1, (int)Math.round(here.radius())), now + WARNING_LIFE, storm.category(), heading);
		}
		if(tier < WeatherNetwork.MAX_TIER)
			return null;
		WeatherAlert.Event watch = watchFor(storm.kind(), storm.category());
		if(watch == null)
			return null;
		for(StormReport.TrackPoint point : storm.track())
			if(point.tick() >= now && point.tick() <= now + WATCH_TICKS && area.touches(point.x(), point.z(), point.radius()))
				return areaAlert(watch, key(watch, storm.id()), area, now + WATCH_LIFE);
		return null;
	}

	/** Whether {@code storm}'s track reaches the area within {@link #LOOKAHEAD_TICKS} of {@code now}. */
	static boolean headingIn(StormReport storm, AlertArea area, long now) {
		StormReport.TrackPoint later = storm.at(now + LOOKAHEAD_TICKS);
		if(area.touches(later.x(), later.z(), later.radius()))
			return true;
		for(StormReport.TrackPoint point : storm.track())
			if(point.tick() > now && point.tick() <= now + LOOKAHEAD_TICKS && area.touches(point.x(), point.z(), point.radius()))
				return true;
		return false;
	}

	/**
	 * The alert a tropical advisory for the area gives (2026-11-15): a disturbance a tropical weather statement with its
	 * chance of developing, a storm a day or two off a hurricane or tropical storm watch (with its category), one within
	 * half a day the warning, unless a warning for a storm is already out ({@link #forStorm} gives those as it nears).
	 */
	@Nullable
	static WeatherAlert forTropical(com.arrl.radiocraft.api.weather.TropicalReport tropical, AlertArea area, long now, List<WeatherAlert> already) {
		int x = (int)Math.round(area.centreX()), z = (int)Math.round(area.centreZ()), radius = (int)Math.ceil(area.radius());
		return switch(tropical.stage()) {
			case DISTURBANCE -> new WeatherAlert(WeatherAlert.Event.HLS, key(WeatherAlert.Event.HLS, tropical.id()), x, z, radius, 0,
					now + WATCH_LIFE, tropical.chancePercent(), -1);
			case OUTLOOK, WATCH -> {
				WeatherAlert.Event event = tropical.hurricane() ? WeatherAlert.Event.HUA : WeatherAlert.Event.TRA;
				yield new WeatherAlert(event, key(event, tropical.id()), x, z, radius, 0, now + WATCH_LIFE, tropical.category(), -1);
			}
			case WARNING -> {
				if(already.stream().anyMatch(a -> a.event() == WeatherAlert.Event.HUW || a.event() == WeatherAlert.Event.TRW))
					yield null;
				WeatherAlert.Event event = tropical.hurricane() ? WeatherAlert.Event.HUW : WeatherAlert.Event.TRW;
				yield new WeatherAlert(event, key(event, tropical.id()), x, z, radius, 0, now + WARNING_LIFE, tropical.category(), -1);
			}
		};
	}

	/** The warning a storm of {@code kind} over the area gives, or null for none (rain, plain thunderstorms). */
	@Nullable
	public static WeatherAlert.Event warningFor(StormReport.Kind kind, boolean tornadoDown, Set<WeatherReport.Hazard> hazards, int category) {
		return switch(kind) {
			case TORNADO -> tornadoDown ? WeatherAlert.Event.TOR : WeatherAlert.Event.SVR;
			case SEVERE_THUNDERSTORM -> WeatherAlert.Event.SVR;
			case SUPERCELL -> hazards.contains(WeatherReport.Hazard.BLIZZARD) ? WeatherAlert.Event.BZW
					: hazards.contains(WeatherReport.Hazard.SANDSTORM) ? WeatherAlert.Event.DSW : WeatherAlert.Event.SVR;
			case HURRICANE -> category >= 1 ? WeatherAlert.Event.HUW : WeatherAlert.Event.TRW;
			case HIGH_WIND -> WeatherAlert.Event.HWW;
			case DENSE_FOG -> WeatherAlert.Event.FGY;
			case HEAT_WAVE -> WeatherAlert.Event.EHW;
			case COLD_SNAP -> WeatherAlert.Event.ECW;
			default -> null;
		};
	}

	/** The watch a storm of {@code kind} forecast to reach the area gives, or null. Hurricanes are warned ahead. */
	@Nullable
	public static WeatherAlert.Event watchFor(StormReport.Kind kind, int category) {
		return switch(kind) {
			case TORNADO -> WeatherAlert.Event.TOA;
			case SEVERE_THUNDERSTORM, SUPERCELL -> WeatherAlert.Event.SVA;
			case HURRICANE -> category >= 1 ? WeatherAlert.Event.HUW : WeatherAlert.Event.TRW;
			default -> null;
		};
	}

	/** Flood and wind warnings from one station's own report. */
	public static List<WeatherAlert> forStation(WeatherNetwork.Station station, long now) {
		List<WeatherAlert> out = new ArrayList<>();
		AprsWeather wx = station.weather();
		long id = station.call().hashCode();
		int x = (int)Math.round(station.x()), z = (int)Math.round(station.z());
		if(wx.rainHour() != null && wx.rainHour() >= FLOOD_RAIN_HUNDREDTHS)
			out.add(new WeatherAlert(WeatherAlert.Event.FFW, key(WeatherAlert.Event.FFW, id), x, z, STATION_ALERT_RADIUS, 0, now + WARNING_LIFE, -1, -1));
		boolean wind = wx.speed() != null && wx.speed() >= HIGH_WIND_MPH;
		boolean gusts = wx.gust() != null && wx.gust() >= HIGH_GUST_MPH;
		if(wind || gusts)
			out.add(new WeatherAlert(WeatherAlert.Event.HWW, key(WeatherAlert.Event.HWW, id), x, z, STATION_ALERT_RADIUS, 0, now + WARNING_LIFE, -1, -1));
		return out;
	}

	private static WeatherAlert areaAlert(WeatherAlert.Event event, long key, AlertArea area, long expires) {
		return new WeatherAlert(event, key, (int)Math.round(area.centreX()), (int)Math.round(area.centreZ()), (int)Math.ceil(area.radius()), 0, expires, -1, -1);
	}

	/** A key for {@code event} about {@code what} (a storm id, a station): the same each time it is worked out. */
	static long key(WeatherAlert.Event event, long what) {
		return what * 31L + event.ordinal() + 1L;
	}
}
