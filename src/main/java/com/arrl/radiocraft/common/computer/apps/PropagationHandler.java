package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.weather.SolarSources;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity.Heard;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.PropagationForecast;
import com.arrl.radiocraft.common.init.RadiocraftData;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.Callsigns;
import com.arrl.radiocraft.common.radio.DirectionFinding;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.solar.Aurora;
import com.arrl.radiocraft.common.radio.solar.BandCondition;
import com.arrl.radiocraft.common.radio.solar.SolarEvent;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Propagation Predictor's server side (computer apps 2, 2026-11-06): which HF bands open to a target over the next
 * {@link PropagationForecast#HOURS} in-game hours, from the live solar weather and its forecast, worked out with the
 * mod's propagation ({@link PropagationForecast}). It reads the sky the way the Solar Weather program does
 * ({@link SolarWeatherHandler#receiver}: an Ionospheric Processor, or a radio with a real antenna, unless the server
 * drops that need); without one it answers {@link SolarWeatherHandler#missing}'s error.
 * <ul>
 * <li>{@code predict} {@code mode} ({@code coords} with {@code x}, {@code z}; {@code call} with {@code call}; or
 * {@code bearing} with {@code distance}, {@code bearing}): {@code source}, {@code via}, {@code target} (words),
 * {@code x}, {@code z}, {@code distance}, {@code bearing}, {@code event} (id path, "" for none), {@code noise}
 * (percent), {@code hours} (each {@code hour}, {@code day}, {@code known}), {@code bands} (each {@code band},
 * {@code grades} and {@code cw} (a {@link PropagationForecast.Grade} letter an hour), {@code paths} (a
 * {@link PropagationForecast.Path} letter an hour), {@code best} (hour index, -1 none)) and {@code bestNow} (a band
 * name, "" for none).</li>
 * <li>{@code targets}: {@code targets}, callsigns it can aim at (each {@code call}, {@code kind} player or aprs,
 * {@code distance}, {@code bearing}).</li>
 * </ul>
 */
public final class PropagationHandler implements ComputerAppHandler {

	/** Longest path it predicts, blocks. */
	public static final int MAX_DISTANCE = 100_000;

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		switch(action) {
			case "predict" -> {
				CompoundTag source = SolarWeatherHandler.receiver(context);
				if(source == null) {
					reply.error(SolarWeatherHandler.missing(context));
					return;
				}
				Target target = target(context, data);
				if(target.error() != null) {
					reply.error(target.error(), target.text());
					return;
				}
				reply.ok(predict(context, source, target));
			}
			case "targets" -> reply.ok(targets(context));
			default -> reply.error("unknown_request", action);
		}
	}

	// -------------------- TARGET --------------------

	/** Where the prediction is for, in words, or an error key (with {@code text} its argument). */
	record Target(double x, double z, String text, @Nullable String error) {

		static Target error(String key, String arg) {
			return new Target(0.0D, 0.0D, arg, key);
		}
	}

	private static BlockPos here(ComputerContext context) {
		return Sublevels.world(context.level(), context.pos());
	}

	static Target target(ComputerContext context, CompoundTag data) {
		BlockPos here = here(context);
		switch(data.getString("mode")) {
			case "coords" -> {
				int x = data.getInt("x"), z = data.getInt("z");
				return new Target(x + 0.5D, z + 0.5D, x + ", " + z, null);
			}
			case "bearing" -> {
				int distance = data.getInt("distance");
				if(distance <= 0 || distance > MAX_DISTANCE)
					return Target.error("prop_bad_distance", String.valueOf(MAX_DISTANCE));
				double bearing = DirectionFinding.normalize(data.getDouble("bearing"));
				double[] at = PropagationForecast.offset(here.getX() + 0.5D, here.getZ() + 0.5D, distance, bearing);
				return new Target(at[0], at[1], distance + " @ " + Math.round(bearing) % 360, null);
			}
			case "call" -> {
				String call = TncPacket.sanitizeCallsign(data.getString("call"));
				if(call.isEmpty())
					return Target.error("prop_no_call", "");
				for(Located located : located(context))
					if(located.call().equals(call) || base(located.call()).equals(call))
						return located.level() ? new Target(located.x(), located.z(), call, null) : Target.error("prop_other_level", call);
				return Target.error("prop_unknown_call", call);
			}
			default -> {
				return Target.error("unknown_request", data.getString("mode"));
			}
		}
	}

	/** A callsign without its SSID ("W1BTR-9" is W1BTR). */
	private static String base(String call) {
		int dash = call.indexOf('-');
		return dash < 0 ? call : call.substring(0, dash);
	}

	/** A station it knows where to find: {@code level} false for a player in another dimension. */
	record Located(String call, String kind, double x, double z, boolean level) {}

	/** Online players with a callsign, then the stations the computer's APRS program heard with a position. */
	static List<Located> located(ComputerContext context) {
		List<Located> out = new ArrayList<>();
		ServerLevel level = context.level();
		for(ServerPlayer player : context.server().getPlayerList().getPlayers()) {
			String call = Callsigns.playerCallsign(context.server(), player.getUUID());
			if(call == null || call.isBlank())
				continue;
			out.add(new Located(TncPacket.sanitizeCallsign(call), "player", player.getX(), player.getZ(), player.level() == level));
		}
		ComputerAppState aprs = context.computer().existingState(ComputerApps.APRS);
		if(aprs instanceof AprsHandler.State state)
			for(Heard heard : state.stations()) {
				BlockPos at = heard.packet().position();
				if(at != null && out.stream().noneMatch(located -> located.call().equals(heard.packet().from())))
					out.add(new Located(heard.packet().from(), "aprs", at.getX() + 0.5D, at.getZ() + 0.5D, true));
			}
		return out;
	}

	private static CompoundTag targets(ComputerContext context) {
		BlockPos here = here(context);
		ListTag list = new ListTag();
		for(Located located : located(context)) {
			if(!located.level())
				continue;
			CompoundTag entry = new CompoundTag();
			entry.putString("call", located.call());
			entry.putString("kind", located.kind());
			entry.putInt("distance", (int)Math.round(Math.hypot(located.x() - here.getX() - 0.5D, located.z() - here.getZ() - 0.5D)));
			entry.putInt("bearing", (int)Math.round(DirectionFinding.bearing(here.getX() + 0.5D, here.getZ() + 0.5D, located.x(), located.z())) % 360);
			list.add(entry);
		}
		CompoundTag out = new CompoundTag();
		out.put("targets", list);
		return out;
	}

	// -------------------- PREDICTION --------------------

	/** The HF bands from the server config, lowest first: the amateur bands, then commercial low band. */
	static List<Band> bands() {
		List<Band> amateur = new ArrayList<>(), other = new ArrayList<>();
		for(RadiocraftServerConfig.BandConfig config : RadiocraftServerConfig.BAND_CONFIGS.values()) {
			Band band = config.getBand();
			if(band.isVhf())
				continue;
			(band.isAmateur() ? amateur : other).add(band);
		}
		amateur.sort(Comparator.comparingDouble(Band::minFrequency));
		other.sort(Comparator.comparingDouble(Band::minFrequency));
		amateur.addAll(other);
		return amateur;
	}

	/** Which event rules an hour: the event, whether it is known, whether it is the one going on now. */
	private record Sky(@Nullable SolarEvent event, boolean known, boolean current) {}

	private static CompoundTag predict(ComputerContext context, CompoundTag source, Target target) {
		ServerLevel level = context.level();
		BlockPos here = here(context);
		BlockPos there = BlockPos.containing(target.x(), here.getY(), target.z());
		double distance = Math.hypot(target.x() - here.getX() - 0.5D, target.z() - here.getZ() - 0.5D);
		long gameTime = level.getGameTime();
		long dayTime = level.getDayTime();
		long hourStart = dayTime - Math.floorMod(dayTime, (long)PropagationForecast.TICKS_PER_HOUR);
		SolarEvent.SolarEventInstance instance = SolarEventManager.getEvent(level);
		SolarEventManager.Upcoming upcoming = SolarEventManager.getUpcoming(level);
		boolean auroraOnPath = Aurora.isActive(level) && (Aurora.inZone(here) || Aurora.inZone(there));
		double auroraStatic = Aurora.affects(level, here) ? RadiocraftServerConfig.AURORA_STATIC.get() : 0.0D;
		boolean fixedTime = level.dimensionType().hasFixedTime();

		List<PropagationForecast.Hour> hours = new ArrayList<>();
		List<Sky> skies = new ArrayList<>();
		for(int h = 0; h < PropagationForecast.HOURS; h++) {
			long at = h == 0 ? dayTime : hourStart + (long)h * PropagationForecast.TICKS_PER_HOUR + PropagationForecast.TICKS_PER_HOUR / 2;
			long ahead = at - dayTime;
			boolean day = !fixedTime && PropagationForecast.isDay(level.dimensionType().timeOfDay(at),
					h == 0 ? level.getRainLevel(1.0F) : 0.0D, h == 0 ? level.getThunderLevel(1.0F) : 0.0D);
			Sky sky;
			if(instance == null)
				sky = new Sky(null, h == 0, false);
			else if(ahead < (long)instance.getDuration() - instance.getTicks())
				sky = new Sky(instance.getEvent(), true, true);
			else if(upcoming != null && upcoming.startGameTime() <= gameTime + ahead)
				sky = new Sky(upcoming.event(), true, false);
			else
				sky = new Sky(instance.getEvent(), false, false);
			skies.add(sky);
			double solarNoise = sky.event() == null ? 0.0D : sky.event().getNoise();
			hours.add(new PropagationForecast.Hour(PropagationForecast.hourOfDay(at), day, sky.known(), solarNoise, sky.current() ? auroraStatic : 0.0D));
		}

		PropagationForecast.Settings settings = new PropagationForecast.Settings(RadiocraftServerConfig.TNC_MIN_SIGNAL.get(), RadiocraftServerConfig.TNC_MIN_SNR.get(),
				RadiocraftServerConfig.LOS_SOLAR_NOISE_SHARE.get());
		List<Band> bands = bands();
		PropagationForecast.Result result = PropagationForecast.predict(bands, distance, hours, (band, h) -> {
			PropagationForecast.Hour hour = hours.get(h);
			Sky sky = skies.get(h);
			BandCondition condition = SolarSources.condition(level, band, hour.day()); // Another mod's space weather first.
			if(condition == null)
				condition = condition(sky.event(), band, hour.day());
			if(auroraOnPath && sky.current())
				condition = condition.worse();
			return condition.getSkipMultiplier();
		}, settings);

		CompoundTag out = source.copy();
		out.putString("target", target.text());
		out.putInt("x", (int)Math.floor(target.x()));
		out.putInt("z", (int)Math.floor(target.z()));
		out.putInt("distance", (int)Math.round(distance));
		out.putInt("bearing", (int)Math.round(DirectionFinding.bearing(here.getX() + 0.5D, here.getZ() + 0.5D, target.x(), target.z())) % 360);
		SolarEvent now = instance == null ? null : instance.getEvent();
		ResourceLocation key = now == null ? null : RadiocraftData.SOLAR_EVENTS.getKey(now);
		out.putString("event", key == null ? "" : key.getPath());
		out.putInt("noise", now == null ? 0 : Math.round(now.getNoise() * 100.0F));
		ListTag hourList = new ListTag();
		for(PropagationForecast.Hour hour : hours) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("hour", hour.hourOfDay());
			entry.putBoolean("day", hour.day());
			entry.putBoolean("known", hour.known());
			hourList.add(entry);
		}
		out.put("hours", hourList);
		ListTag rows = new ListTag();
		for(PropagationForecast.Row row : result.rows()) {
			StringBuilder grades = new StringBuilder(), cw = new StringBuilder(), paths = new StringBuilder();
			for(PropagationForecast.Cell cell : row.cells()) {
				grades.append(cell.grade().letter);
				cw.append(cell.cw().letter);
				paths.append(cell.path().letter);
			}
			CompoundTag entry = new CompoundTag();
			entry.putString("band", row.band().name());
			entry.putString("grades", grades.toString());
			entry.putString("cw", cw.toString());
			entry.putString("paths", paths.toString());
			entry.putInt("best", row.bestHour());
			rows.add(entry);
		}
		out.put("bands", rows);
		int best = result.bestNow();
		out.putString("bestNow", best < 0 ? "" : result.rows().get(best).band().name());
		return out;
	}

	/** {@code band}'s condition in {@code event} (good with none); a band the event leaves out follows the one it behaves like. */
	private static BandCondition condition(@Nullable SolarEvent event, Band band, boolean day) {
		if(event == null)
			return BandCondition.GOOD;
		return event.getCondition(event.hasCondition(band.name(), day) ? band.name() : band.conditionName(), day);
	}
}
