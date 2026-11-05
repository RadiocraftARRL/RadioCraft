package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.api.weather.DayOutlook;
import com.arrl.radiocraft.api.weather.WeatherReport;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.api.weather.WeatherSources;
import com.arrl.radiocraft.common.be_networks.network_objects.DummyLoadNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.IonosphericProcessorBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.init.RadiocraftData;
import com.arrl.radiocraft.common.radio.solar.Aurora;
import com.arrl.radiocraft.common.radio.solar.BandCondition;
import com.arrl.radiocraft.common.radio.solar.SolarEvent;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.common.radio.solar.SporadicE;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The solar weather program's server side (1.12 step 6): the solar weather and band conditions, and the weather
 * forecast, read where the computer is. Like the Teletype's SOLAR WX it needs a way to hear them (unless the server's
 * {@code solar_weather_station_needs_radio} is off): an online Ionospheric Processor touching the computer or on its
 * data wire (no radio needed), or beside one of its radios; or else a powered radio with a real antenna, copying the
 * WWV-style geophysical alert and weather broadcasts. Every radio it reaches is tried in turn, not just the first:
 * those touching it or on its data wire, and those on a radio interface of its station ({@link #radios}). Without any
 * it answers an error saying what is missing ({@link #missing}).
 * <ul>
 * <li>{@code solar}: {@code source} (direct, processor or radio) and {@code via}; {@code event} (the event's id path,
 * "" for none), {@code level} (1 blackout to 6 great), {@code noise} (percent), {@code day}, {@code minutesLeft};
 * {@code bands} (each {@code band}, {@code now}, {@code day}, {@code night}: condition names); {@code vhf}
 * (normal or aurora), {@code aurora} (active, expected or none) and {@code latitude}, {@code sporadicE};
 * {@code next} and {@code nextMinutes} once the next event is known, {@code blackoutNext}.</li>
 * <li>{@code forecast}: {@code source}, {@code via}, {@code local} (storms have places), {@code lines} (the report,
 * the days ahead and the storms about, as text lines) and {@code now} ({@code temp}, {@code pressure},
 * {@code humidity}, {@code wind}, {@code windFrom}, {@code summary}, {@code hazards}).</li>
 * </ul>
 */
public final class SolarWeatherHandler implements ComputerAppHandler {

	/** The HF bands reported, as the Teletype reports them, then low band. */
	private static final List<String> BANDS = List.of("80m", "40m", "20m", "10m", "low_band");
	/** How far the forecast lists storms, blocks. */
	public static final int STORM_RANGE = 1000;

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		CompoundTag source = receiver(context);
		if(source == null) {
			reply.error(missing(context));
			return;
		}
		switch(action) {
			case "solar" -> reply.ok(solar(context, source));
			case "forecast" -> reply.ok(forecast(context, source));
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * The radios the computer can listen through, each once, nearest first: those touching it or on its data wire, then
	 * those on the radio interfaces of its station bus (where APRS, fax and the picture programs find theirs, see
	 * {@link RadioStation#radios}). Any band counts, VHF included.
	 */
	public static List<RadioBlockEntity> radios(ComputerContext context) {
		List<RadioBlockEntity> out = new ArrayList<>(context.linked(RadioBlockEntity.class));
		for(RadioBlockEntity radio : RadioStation.radios(context.bus()))
			if(!out.contains(radio))
				out.add(radio);
		return out;
	}

	/** How the computer hears the reports ({@code source}, {@code via}), or null if it cannot ({@link #missing}). */
	@Nullable
	public static CompoundTag receiver(ComputerContext context) {
		CompoundTag out = new CompoundTag();
		if(!RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.get()) {
			out.putString("source", "direct");
			out.putString("via", "");
			return out;
		}
		List<RadioBlockEntity> radios = radios(context);
		IonosphericProcessorBlockEntity processor = processor(context, radios);
		if(processor != null) {
			out.putString("source", "processor");
			out.putString("via", processor.sender());
			return out;
		}
		for(RadioBlockEntity radio : radios)
			if(radio.isPowered() && hasAntenna(context.level(), radio.getBlockPos())) {
				out.putString("source", "radio");
				out.putString("via", radio.getBlockState().getBlock().getDescriptionId());
				return out;
			}
		return null;
	}

	/**
	 * Why {@link #receiver} found nothing, as an error key: {@code solar_no_radio} (no radio and no online processor
	 * reachable), {@code solar_processor_offline} (a processor wired to the computer but no Ionosonde on its coax, and
	 * no radio), {@code solar_radio_off} (radios, none switched on) or {@code solar_no_antenna} (a radio on, but only
	 * a dummy load or nothing on its coax).
	 */
	public static String missing(ComputerContext context) {
		List<RadioBlockEntity> radios = radios(context);
		if(radios.isEmpty())
			return context.linked(IonosphericProcessorBlockEntity.class).isEmpty() ? "solar_no_radio" : "solar_processor_offline";
		for(RadioBlockEntity radio : radios)
			if(radio.isPowered())
				return "solar_no_antenna";
		return "solar_radio_off";
	}

	/** An online Ionospheric Processor wired to the computer, or beside it or one of {@code radios}; or null. */
	@Nullable
	private static IonosphericProcessorBlockEntity processor(ComputerContext context, List<RadioBlockEntity> radios) {
		for(IonosphericProcessorBlockEntity wired : context.linked(IonosphericProcessorBlockEntity.class))
			if(wired.isOnline())
				return wired;
		List<BlockPos> positions = new ArrayList<>();
		for(RadioBlockEntity radio : radios)
			positions.add(radio.getBlockPos());
		IonosphericProcessorBlockEntity near = IonosphericProcessorBlockEntity.nearby(context.level(), context.pos(), positions);
		return near != null && near.isOnline() ? near : null;
	}

	/** True if the radio at {@code pos} has a real antenna on its coax (a dummy load radiates and hears nothing). */
	private static boolean hasAntenna(ServerLevel level, BlockPos pos) {
		return IBENetworks.getObject(level, pos) instanceof RadioNetworkObject radio
				&& radio.getAntennas().stream().anyMatch(antenna -> !(antenna instanceof DummyLoadNetworkObject) && antenna.getAntenna() != null);
	}

	// -------------------- SOLAR --------------------

	public static CompoundTag solar(ComputerContext context, CompoundTag source) {
		ServerLevel level = context.level();
		BlockPos pos = Sublevels.world(level, context.pos());
		CompoundTag out = source.copy();
		SolarEvent.SolarEventInstance instance = SolarEventManager.getEvent(level);
		SolarEvent event = instance == null ? null : instance.getEvent();
		boolean day = level.isDay();
		out.putBoolean("day", day);
		if(event == null) {
			out.putString("event", "");
			return out;
		}
		ResourceLocation key = RadiocraftData.SOLAR_EVENTS.getKey(event);
		out.putString("event", key == null ? "" : key.getPath());
		out.putInt("level", RadiocraftData.SOLAR_EVENTS.conditionLevel(event));
		out.putInt("noise", Math.round(event.getNoise() * 100.0F));
		out.putInt("minutesLeft", (int)Math.ceil(Math.max(0, instance.getDuration() - instance.getTicks()) / 1200.0D));
		boolean underAurora = Aurora.affects(level, pos);
		ListTag bands = new ListTag();
		for(String band : BANDS) {
			CompoundTag row = new CompoundTag();
			row.putString("band", band);
			BandCondition now = condition(event, band, day);
			row.putString("now", (underAurora ? now.worse() : now).getSerializedName());
			row.putString("day", condition(event, band, true).getSerializedName());
			row.putString("night", condition(event, band, false).getSerializedName());
			bands.add(row);
		}
		out.put("bands", bands);
		out.putString("vhf", underAurora ? "aurora" : "normal");
		out.putString("aurora", Aurora.isActive(level) ? "active" : Aurora.isExpected(level) ? "expected" : "none");
		out.putInt("latitude", Aurora.latitude());
		out.putBoolean("sporadicE", SporadicE.isActive(level));
		SolarEventManager.Upcoming upcoming = SolarEventManager.getUpcoming(level);
		if(upcoming != null && upcoming.startGameTime() > level.getGameTime()) {
			ResourceLocation next = RadiocraftData.SOLAR_EVENTS.getKey(upcoming.event());
			out.putString("next", next == null ? "" : next.getPath());
			out.putInt("nextMinutes", (int)Math.ceil((upcoming.startGameTime() - level.getGameTime()) / 1200.0D));
			out.putBoolean("blackoutNext", SolarEventManager.isBlackout(upcoming.event()));
		}
		return out;
	}

	/** {@code band}'s condition in {@code event}; low band follows 10m unless the event names it. */
	private static BandCondition condition(SolarEvent event, String band, boolean day) {
		if(band.equals("low_band") && !event.hasCondition(band, day))
			return event.getCondition("10m", day);
		return event.getCondition(band, day);
	}

	// -------------------- FORECAST --------------------

	public static CompoundTag forecast(ComputerContext context, CompoundTag source) {
		ServerLevel level = context.level();
		BlockPos pos = Sublevels.world(level, context.pos());
		WeatherSource weather = WeatherSources.get(level);
		CompoundTag out = source.copy();
		out.putBoolean("local", weather.isLocal());
		List<String> lines = new ArrayList<>(WeatherReports.lines(level, weather, pos, context.bus().callsign()));
		List<String> days = new ArrayList<>();
		String[] names = { "TODAY", "TOMORROW", "DAY 3", "DAY 4" };
		for(int ahead = 0; ahead < names.length; ahead++) {
			DayOutlook outlook = weather.outlook(level, pos, ahead);
			if(outlook.type() == DayOutlook.Type.UNKNOWN)
				break;
			days.add(names[ahead] + ": " + outlook.type().words() + (outlook.tornadoWatch() ? ", TORNADO WATCH" : ""));
		}
		if(!days.isEmpty()) {
			lines.add("");
			lines.add("OUTLOOK");
			lines.addAll(days);
		}
		if(weather.isLocal()) {
			lines.add("");
			lines.add("STORMS WITHIN " + STORM_RANGE);
			lines.addAll(WeatherReports.stormList(level, weather, pos, STORM_RANGE));
		}
		out.put("lines", ComputerServer.stringList(lines));
		WeatherReport now = weather.report(level, pos);
		CompoundTag here = new CompoundTag();
		here.putDouble("temp", now.temperature());
		here.putDouble("pressure", now.pressure());
		here.putDouble("humidity", now.humidity());
		here.putDouble("wind", now.windSpeed());
		here.putDouble("windFrom", now.windFrom());
		here.putString("summary", WeatherReports.brief(now));
		String hazards = WeatherReports.hazards(now);
		here.putString("hazards", hazards == null ? "" : hazards);
		out.put("now", here);
		return out;
	}
}
