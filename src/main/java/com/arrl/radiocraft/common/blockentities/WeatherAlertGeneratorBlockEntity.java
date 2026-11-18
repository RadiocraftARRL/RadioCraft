package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.api.weather.WeatherSources;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.WeatherAlertGeneratorBlock;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.apps.AprsHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.data.AprsWeather;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import com.arrl.radiocraft.common.radio.voice.Afsk;
import com.arrl.radiocraft.common.radio.voice.RadioModeration;
import com.arrl.radiocraft.common.radio.wxalert.AlertArea;
import com.arrl.radiocraft.common.radio.wxalert.AlertBuilder;
import com.arrl.radiocraft.common.radio.wxalert.WeatherAlert;
import com.arrl.radiocraft.common.radio.wxalert.WeatherAlertPacket;
import com.arrl.radiocraft.common.radio.wxalert.WeatherNetwork;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Simple Weather Alert Generator (1.12 weather alerts): attached to an APRS station (an H19 APRS terminal, or a
 * radio computer with the APRS program installed, on its station bus: touching, or by data wire), it reads the
 * weather beacons that station has heard. With at least three weather stations each 150 blocks or more from the
 * others ({@link WeatherNetwork}) it covers the area round them ({@link AlertArea}) and builds alerts from their
 * reports and the weather source's storms there ({@link AlertBuilder}). It broadcasts them on 162.500 MHz through the
 * VHF antenna on its coax: an AFSK burst (heard as data on a radio tuned there) carrying a {@link TncPacket.Kind#WX_ALERT}
 * packet, then for a new warning the 1050 Hz attention tone. New alerts go out at once, the rest again every minute,
 * and with no alerts a routine broadcast every two minutes so receivers know their nearest generator.
 *
 * <p>Power: {@link #IDLE_USE} FE a tick, {@link #TRANSMIT_USE} while sending.
 */
public class WeatherAlertGeneratorBlockEntity extends BlockEntity implements INetworkObjectProvider, StationTerminal {

	public static final int IDLE_USE = 1;
	public static final int TRANSMIT_USE = 12;
	/** Ticks between checks of the station, the weather beacons and the antenna. */
	public static final int CHECK_TICKS = 20;
	/** Ticks between rebuilding the alerts. */
	public static final int BUILD_TICKS = 100;
	/** Ticks between broadcasts of active alerts. */
	public static final int REPEAT_TICKS = 1200;
	/** Ticks between routine broadcasts with no alerts. */
	public static final int ROUTINE_TICKS = 2400;
	/** A weather beacon older than this no longer counts, ticks (30 minutes: three beacon intervals). */
	public static final long STATION_MAX_AGE = 36000L;
	/** The attention tone sent over the air after a new warning, ticks. */
	public static final int AIR_TONE_TICKS = 60;

	/** Why it is or is not on the air. */
	/** Why it is or is not on the air. NO_SOURCE: nothing on its bus to hear weather stations with (no APRS station, radio interface or radio). */
	public enum State { NO_POWER, NO_SOURCE, TOO_FEW, NO_ANTENNA, TWO_ANTENNAS, READY }

	/** Whether a station heard counts towards the tier, and if not why (2026-11-18). */
	public enum Standing {
		/** In the spread-out group: it counts. */
		COUNTS,
		/** Heard, but within {@link WeatherNetwork#MIN_SPACING} of one that counts: it adds no reach (its reports still give flood and wind warnings). */
		TOO_CLOSE,
		/** Not heard for {@link #STATION_MAX_AGE}. */
		STALE,
		/** One APRS callsign heard from two places: only one counts until each has its own SSID. */
		SAME_CALL
	}

	/**
	 * A station on the status list: its key (an APRS callsign, or an observer's place), its callsign, whether it is a
	 * Weather Observer, where, how long ago and whether it counts.
	 */
	public record HeardStation(String key, String call, boolean observer, double x, double z, long ageTicks, Standing standing) {

		HeardStation with(Standing standing) {
			return new HeardStation(key, call, observer, x, z, ageTicks, standing);
		}
	}

	/** A Weather Observer's report as it came in. */
	private record Observation(TncPacket packet, long gameTime) {}

	/** Blocks an APRS station can move between beacons and still be the same station. */
	public static final double SAME_PLACE = 16.0D;

	// Server only.
	private State state = State.NO_SOURCE;
	private int heard = 0, spread = 0, tier = 0;
	private List<WeatherNetwork.Station> stations = List.of();
	private List<HeardStation> heardList = List.of();
	/** Where each APRS callsign was last heard from, and until when it counts as heard from two places. */
	private final Map<String, double[]> lastPlace = new HashMap<>();
	private final Map<String, Long> sameCallUntil = new HashMap<>();
	/** Weather Observers heard through a radio on its bus, by where they are (2026-11-18). */
	private final Map<Long, Observation> observations = new HashMap<>();
	@Nullable
	private AlertArea area;
	private List<WeatherAlert> alerts = List.of();
	private final Set<Long> sentKeys = new HashSet<>();
	private long lastSent = Long.MIN_VALUE / 2;
	private boolean sendPending = false;
	// A broadcast going out: its frames, the next one, the packet sent when they are done, and the tone after.
	@Nullable
	private List<short[]> frames;
	private int frame;
	@Nullable
	private TncPacket sending;
	private int toneFrames;
	private boolean newWarning;
	/** Who placed it (saved): Storm Spotter goes to them when it sends its first warning. */
	@Nullable
	private UUID placer = null;
	private UUID dataVoiceId, toneVoiceId;
	/** For GameTests: the weather to use instead of the level's. */
	@Nullable
	private WeatherSource weatherOverride;

	public WeatherAlertGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.WEATHER_ALERT_GENERATOR.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide)
			getNetworkObject(level, worldPosition);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, TRANSMIT_USE, IDLE_USE);
	}

	@Override
	public void onNetworkObjectCreated(Level level, BlockPos pos, BENetworkObject networkObject) {
		if(networkObject instanceof RadioNetworkObject radio)
			radio.setWantsOn(true); // No switch: it runs whenever it has power.
	}

	@Nullable
	private RadioNetworkObject radioObject() {
		return level != null && getNetworkObject(level, worldPosition) instanceof RadioNetworkObject radio ? radio : null;
	}

	/** GameTests: use {@code source} for the weather instead of the level's (null for the level's again). */
	public void setWeatherOverride(@Nullable WeatherSource source) {
		weatherOverride = source;
	}

	// -------------------- TICK --------------------

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState blockState, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof WeatherAlertGeneratorBlockEntity generator)
			generator.serverTick(serverLevel, blockState);
	}

	private void serverTick(ServerLevel serverLevel, BlockState blockState) {
		long now = serverLevel.getGameTime();
		RadioNetworkObject radio = radioObject();
		if(radio != null && !radio.wantsOn)
			radio.setWantsOn(true);
		if((now + worldPosition.asLong()) % CHECK_TICKS == 0L)
			refresh(serverLevel, radio, now);
		if(state == State.READY && (now + worldPosition.asLong()) % BUILD_TICKS == 0L)
			build(serverLevel, now);
		if(frames == null && state == State.READY && (sendPending || now - lastSent >= (alerts.isEmpty() ? ROUTINE_TICKS : REPEAT_TICKS)))
			startBroadcast(serverLevel, now);
		if(frames != null)
			tickBroadcast(serverLevel, radio, now);
		boolean ready = state == State.READY, transmitting = frames != null;
		if(blockState.getBlock() instanceof WeatherAlertGeneratorBlock
				&& (blockState.getValue(WeatherAlertGeneratorBlock.READY) != ready || blockState.getValue(WeatherAlertGeneratorBlock.TRANSMITTING) != transmitting))
			serverLevel.setBlock(worldPosition, blockState.setValue(WeatherAlertGeneratorBlock.READY, ready).setValue(WeatherAlertGeneratorBlock.TRANSMITTING, transmitting), Block.UPDATE_CLIENTS);
	}

	/** {@link #refresh} now (a player asking for the status, GameTests). */
	public void refreshNow(ServerLevel serverLevel) {
		refresh(serverLevel, radioObject(), serverLevel.getGameTime());
	}

	/** Checks power, the APRS station and its weather beacons, and the antenna; the result is {@link #getState}. */
	private void refresh(ServerLevel serverLevel, @Nullable RadioNetworkObject radio, long now) {
		List<WeatherNetwork.Station> found = new ArrayList<>();
		List<HeardStation> listed = new ArrayList<>();
		boolean source = heardStations(serverLevel, now, found, listed);
		WeatherNetwork.Check check = WeatherNetwork.check(found);
		int tierBefore = tier;
		heard = check.heard();
		spread = check.spread();
		tier = check.tier();
		stations = List.copyOf(found);
		List<WeatherNetwork.Station> group = WeatherNetwork.spreadGroup(found, WeatherNetwork.MIN_SPACING);
		for(int i = 0; i < listed.size(); i++) {
			HeardStation h = listed.get(i);
			if(h.standing() == Standing.COUNTS && group.stream().noneMatch(member -> member.call().equals(h.key())))
				listed.set(i, h.with(Standing.TOO_CLOSE));
		}
		heardList = List.copyOf(listed);
		State before = state;
		if(radio == null || !radio.isPowered)
			state = State.NO_POWER;
		else if(!source)
			state = State.NO_SOURCE;
		else if(check.status() == WeatherNetwork.Status.TOO_FEW)
			state = State.TOO_FEW;
		else {
			List<AntennaNetworkObject> antennas = radio.getAntennas();
			state = antennas.isEmpty() ? State.NO_ANTENNA : antennas.size() > 1 ? State.TWO_ANTENNAS : State.READY;
		}
		if(state == State.READY) {
			List<double[]> points = new ArrayList<>();
			for(WeatherNetwork.Station station : stations)
				points.add(new double[] { station.x(), station.z() });
			area = AlertArea.around(points, AlertArea.marginFor(tier));
			if(before != State.READY || tierBefore != tier)
				build(serverLevel, now);
		}
		else {
			area = null;
			alerts = List.of();
		}
	}

	/**
	 * Fills {@code out} with the weather stations heard in the last {@link #STATION_MAX_AGE} ticks: the weather beacons
	 * the APRS stations on this generator's bus have heard, one per callsign, and the Weather Observers its radios have
	 * heard, one per place. {@code listed} gets every one for the status (stale ones and callsigns heard from two places
	 * included). False if there is nothing on the bus to hear them with.
	 */
	private boolean heardStations(ServerLevel serverLevel, long now, List<WeatherNetwork.Station> out, List<HeardStation> listed) {
		StationBus bus = StationBus.find(serverLevel, worldPosition);
		boolean any = bus.hasInterface() || !DataWire.linked(serverLevel, worldPosition, RadioBlockEntity.class).isEmpty();
		Map<String, WeatherNetwork.Station> byKey = new LinkedHashMap<>();
		Map<String, HeardStation> listedByKey = new LinkedHashMap<>();
		for(StationTerminal terminal : bus.terminals()) {
			List<AprsTerminalBlockEntity.Heard> beacons;
			if(terminal instanceof AprsTerminalBlockEntity h19)
				beacons = h19.getBeacons();
			else if(terminal instanceof RadioComputerBlockEntity computer && computer.isInstalled(ComputerApps.APRS))
				beacons = AprsHandler.state(computer).stations();
			else
				continue;
			any = true;
			for(AprsTerminalBlockEntity.Heard heardBeacon : beacons) {
				TncPacket packet = heardBeacon.packet();
				if(packet.position() == null)
					continue;
				AprsWeather weather = AprsWeather.parse(packet.text());
				if(weather == null)
					continue;
				String call = packet.from();
				double x = packet.position().getX(), z = packet.position().getZ();
				long age = now - heardBeacon.gameTime();
				if(age > STATION_MAX_AGE) {
					listedByKey.putIfAbsent(call, new HeardStation(call, call, false, x, z, age, Standing.STALE));
					continue;
				}
				double[] last = lastPlace.get(call);
				if(last != null && Math.hypot(last[0] - x, last[1] - z) > SAME_PLACE)
					sameCallUntil.put(call, now + STATION_MAX_AGE); // The same callsign from somewhere else: two stations.
				lastPlace.put(call, new double[] { x, z });
				boolean twoPlaces = sameCallUntil.getOrDefault(call, Long.MIN_VALUE) > now;
				if(byKey.putIfAbsent(call, new WeatherNetwork.Station(call, x, z, weather)) == null) // The first fresh one heard.
					listedByKey.put(call, new HeardStation(call, call, false, x, z, age, twoPlaces ? Standing.SAME_CALL : Standing.COUNTS));
			}
		}
		observations.values().removeIf(o -> now - o.gameTime() > STATION_MAX_AGE * 2L); // Long gone: off the list.
		for(Observation observation : observations.values()) {
			TncPacket packet = observation.packet();
			AprsWeather weather = AprsWeather.parse(packet.text());
			if(weather == null || packet.position() == null)
				continue;
			String key = "OBS@" + packet.position().asLong();
			double x = packet.position().getX(), z = packet.position().getZ();
			long age = now - observation.gameTime();
			if(age > STATION_MAX_AGE)
				listedByKey.put(key, new HeardStation(key, packet.from(), true, x, z, age, Standing.STALE));
			else {
				byKey.put(key, new WeatherNetwork.Station(key, x, z, weather));
				listedByKey.put(key, new HeardStation(key, packet.from(), true, x, z, age, Standing.COUNTS));
			}
		}
		out.addAll(byKey.values());
		listed.addAll(listedByKey.values());
		return any;
	}

	/**
	 * A packet from a radio on its bus (through a radio interface, or a radio touching it or on its data wire): a
	 * Weather Observer's report is kept, by where the observer is. Anything else is not for it.
	 */
	@Override
	public void receive(TncPacket packet) {
		if(level == null || packet.kind() != TncPacket.Kind.WX_OBS || packet.position() == null)
			return;
		observations.put(packet.position().asLong(), new Observation(packet, level.getGameTime()));
	}

	private void build(ServerLevel serverLevel, long now) {
		if(area == null)
			return;
		WeatherSource source = weatherOverride != null ? weatherOverride : WeatherSources.get(serverLevel);
		alerts = AlertBuilder.build(serverLevel, source, stations, area, now, tier);
		for(WeatherAlert alert : alerts)
			if(!sentKeys.contains(alert.key()))
				sendPending = true; // Something new: on the air at once.
	}

	/** Sends the alerts now (GameTests, and a redstone pulse would do the same). False unless it is ready. */
	public boolean broadcastNow() {
		if(!(level instanceof ServerLevel serverLevel) || state != State.READY || frames != null)
			return false;
		startBroadcast(serverLevel, serverLevel.getGameTime());
		return true;
	}

	private void startBroadcast(ServerLevel serverLevel, long now) {
		sendPending = false;
		newWarning = alerts.stream().anyMatch(alert -> alert.event().isWarning() && !sentKeys.contains(alert.key()));
		String from = StationBus.find(serverLevel, worldPosition).callsign();
		sending = WeatherAlertPacket.packet(serverLevel.getRandom().nextLong(), from.isEmpty() ? "WXALERT" : from, Sublevels.world(serverLevel, worldPosition), alerts);
		frames = Afsk.burst(sending.airBytes(), sending.id());
		frame = 0;
		toneFrames = 0;
		sentKeys.clear();
		for(WeatherAlert alert : alerts)
			sentKeys.add(alert.key());
		lastSent = now;
	}

	private void tickBroadcast(ServerLevel serverLevel, @Nullable RadioNetworkObject radio, long now) {
		List<AntennaNetworkObject> antennas = radio == null ? List.of() : radio.getAntennas();
		if(radio == null || !radio.isPowered || antennas.size() != 1 || frames == null || sending == null) {
			endBroadcast(radio);
			return;
		}
		radio.setTransmitting(true);
		AntennaNetworkObject antenna = antennas.getFirst();
		Band band = MarineRadios.band();
		int perTick = now % 2 == 0 ? 3 : 2;
		if(frame < frames.size()) {
			if(dataVoiceId == null)
				dataVoiceId = Afsk.markDataSource(UUID.nameUUIDFromBytes(("radiocraft-wxalert-" + worldPosition.asLong()).getBytes(StandardCharsets.UTF_8)));
			for(int i = 0; i < perTick && frame < frames.size(); i++)
				antenna.transmitAudioPacket(() -> serverLevel, frames.get(frame++), band, WeatherAlertPacket.FREQUENCY_HZ, dataVoiceId);
			if(frame >= frames.size()) { // The burst is done: the packet decodes at the far end.
				RadioModeration.data(serverLevel, sending, WeatherAlertPacket.FREQUENCY_HZ);
				antenna.transmitDataPacket(serverLevel, sending, band, WeatherAlertPacket.FREQUENCY_HZ);
				if(newWarning && placer != null) // Storm Spotter: a warning went out.
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(serverLevel.getServer(), placer, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.STORM_SPOTTER);
				if(!newWarning)
					endBroadcast(radio);
			}
			return;
		}
		if(toneVoiceId == null)
			toneVoiceId = UUID.nameUUIDFromBytes(("radiocraft-wxalert-tone-" + worldPosition.asLong()).getBytes(StandardCharsets.UTF_8));
		for(int i = 0; i < perTick; i++)
			antenna.transmitAudioPacket(() -> serverLevel, toneFrame(toneFrames++), band, WeatherAlertPacket.FREQUENCY_HZ, toneVoiceId);
		if(toneFrames >= AIR_TONE_TICKS * 5 / 2)
			endBroadcast(radio);
	}

	private void endBroadcast(@Nullable RadioNetworkObject radio) {
		frames = null;
		sending = null;
		if(radio != null)
			radio.setTransmitting(false);
	}

	/** Frame {@code n} (20 ms at 48 kHz) of the 1050 Hz attention tone. */
	static short[] toneFrame(int n) {
		short[] out = new short[960];
		for(int i = 0; i < out.length; i++) {
			double t = (n * 960L + i) / 48000.0D;
			out[i] = (short)(Math.sin(2.0D * Math.PI * 1050.0D * t) * 12000.0D);
		}
		return out;
	}

	// -------------------- STATUS --------------------

	public State getState() {
		return state;
	}

	/** Weather beacons heard (fresh, one per station). */
	public int getHeard() {
		return heard;
	}

	/** The most of them found each 150 blocks or more from the others. */
	public int getSpread() {
		return spread;
	}

	/** What its stations let it do: 0 nothing, 1 what is at them now, 2 storms heading in, 3 watches too. */
	public int getTier() {
		return tier;
	}

	/** Every weather station it has heard, counting or not, for the status. */
	public List<HeardStation> getHeardStations() {
		return heardList;
	}

	public List<WeatherAlert> getAlerts() {
		return alerts;
	}

	public boolean isTransmitting() {
		return frames != null;
	}

	/** Most stations listed in the status. */
	public static final int STATUS_STATIONS = 8;

	/**
	 * What it says when right-clicked (and in Jade): why it is not on the air or what it is sending, its tier and what
	 * the next one needs, and each station heard with whether it counts.
	 */
	public List<Component> statusLines() {
		List<Component> out = new ArrayList<>();
		String base = "message.radiocraft.weather_alert_generator.";
		switch(state) {
			case NO_POWER -> out.add(Component.translatable(base + "no_power").withStyle(ChatFormatting.RED));
			case NO_SOURCE -> out.add(Component.translatable(base + "no_source").withStyle(ChatFormatting.RED));
			case TOO_FEW -> out.add(Component.translatable(base + "too_few", (int)WeatherNetwork.MIN_SPACING).withStyle(ChatFormatting.YELLOW));
			case NO_ANTENNA -> out.add(Component.translatable(base + "no_antenna").withStyle(ChatFormatting.RED));
			case TWO_ANTENNAS -> out.add(Component.translatable(base + "two_antennas").withStyle(ChatFormatting.RED));
			case READY -> {
				out.add(Component.translatable(base + "ready", Component.translatable(base + "tier." + tier),
						area == null ? 0 : (int)Math.round(area.radius() * 2)).withStyle(ChatFormatting.GREEN));
				if(alerts.isEmpty())
					out.add(Component.translatable(base + "no_alerts"));
				else
					for(WeatherAlert alert : alerts) {
						MutableComponent line = Component.literal(alert.event().words());
						out.add(line.withStyle(alert.event().isWarning() ? ChatFormatting.RED : ChatFormatting.GOLD));
					}
			}
		}
		if(state != State.NO_SOURCE && tier < WeatherNetwork.MAX_TIER)
			out.add(Component.translatable(base + "next." + (tier + 1), (int)WeatherNetwork.MIN_SPACING).withStyle(ChatFormatting.GRAY));
		int shown = 0;
		for(HeardStation station : heardList) {
			if(shown++ >= STATUS_STATIONS) {
				out.add(Component.translatable(base + "more", heardList.size() - STATUS_STATIONS).withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			out.add(stationLine(base, station));
		}
		return out;
	}

	private Component stationLine(String base, HeardStation station) {
		double dx = station.x() - worldPosition.getX(), dz = station.z() - worldPosition.getZ();
		int distance = (int)Math.round(Math.hypot(dx, dz));
		String where = distance < 8 ? "" : " " + compass(dx, dz);
		long minutes = station.ageTicks() / 1200L;
		Component who = station.observer() ? Component.translatable(base + "observer", station.call()) : Component.literal(station.call());
		MutableComponent line = Component.literal("  ").append(Component.translatable(base + "station", who, distance, where, minutes));
		return switch(station.standing()) {
			case COUNTS -> line.withStyle(ChatFormatting.GRAY);
			case TOO_CLOSE -> line.append(Component.translatable(base + "station.too_close", (int)WeatherNetwork.MIN_SPACING)).withStyle(ChatFormatting.DARK_GRAY);
			case STALE -> line.append(Component.translatable(base + "station.stale", STATION_MAX_AGE / 1200L)).withStyle(ChatFormatting.DARK_GRAY);
			case SAME_CALL -> line.append(Component.translatable(base + "station.same_call")).withStyle(ChatFormatting.YELLOW);
		};
	}

	/** N, NE, E... for a direction {@code dx}, {@code dz} (north is -z). */
	static String compass(double dx, double dz) {
		String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0D) % 360.0D;
		return points[(int)Math.round(bearing / 45.0D) % 8];
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		long[] keys = sentKeys.stream().mapToLong(Long::longValue).toArray();
		tag.putLongArray("sentKeys", keys);
		if(placer != null)
			tag.putUUID("placer", placer);
	}

	/** Notes who placed it (the placing event, {@code common/advancements/AdvancementEvents}). */
	public void setPlacer(@Nullable UUID placer) {
		this.placer = placer;
		setChanged();
	}

	@Nullable
	public UUID getPlacer() {
		return placer;
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		sentKeys.clear();
		for(long key : tag.getLongArray("sentKeys"))
			sentKeys.add(key);
		if(tag.hasUUID("placer"))
			placer = tag.getUUID("placer");
	}
}
