package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.api.weather.WeatherSources;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
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
public class WeatherAlertGeneratorBlockEntity extends BlockEntity implements INetworkObjectProvider {

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
	public enum State { NO_POWER, NO_APRS, TOO_FEW, TOO_CLOSE, NO_ANTENNA, TWO_ANTENNAS, READY }

	// Server only.
	private State state = State.NO_APRS;
	private int heard = 0, spread = 0;
	private List<WeatherNetwork.Station> stations = List.of();
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
		boolean aprs = heardStations(serverLevel, now, found);
		WeatherNetwork.Check check = WeatherNetwork.check(found);
		heard = check.heard();
		spread = check.spread();
		stations = List.copyOf(found);
		State before = state;
		if(radio == null || !radio.isPowered)
			state = State.NO_POWER;
		else if(!aprs)
			state = State.NO_APRS;
		else if(check.status() == WeatherNetwork.Status.TOO_FEW)
			state = State.TOO_FEW;
		else if(check.status() == WeatherNetwork.Status.TOO_CLOSE)
			state = State.TOO_CLOSE;
		else {
			List<AntennaNetworkObject> antennas = radio.getAntennas();
			state = antennas.isEmpty() ? State.NO_ANTENNA : antennas.size() > 1 ? State.TWO_ANTENNAS : State.READY;
		}
		if(state == State.READY) {
			List<double[]> points = new ArrayList<>();
			for(WeatherNetwork.Station station : stations)
				points.add(new double[] { station.x(), station.z() });
			area = AlertArea.around(points, AlertArea.DEFAULT_MARGIN);
			if(before != State.READY)
				build(serverLevel, now);
		}
		else {
			area = null;
			alerts = List.of();
		}
	}

	/**
	 * Fills {@code out} with the weather beacons the APRS stations on this generator's bus have heard in the last
	 * {@link #STATION_MAX_AGE} ticks, one per callsign. False if there is no APRS station on the bus.
	 */
	private boolean heardStations(ServerLevel serverLevel, long now, List<WeatherNetwork.Station> out) {
		StationBus bus = StationBus.find(serverLevel, worldPosition);
		boolean any = false;
		Map<String, WeatherNetwork.Station> byCall = new LinkedHashMap<>();
		for(StationTerminal terminal : bus.terminals()) {
			List<AprsTerminalBlockEntity.Heard> heardList;
			if(terminal instanceof AprsTerminalBlockEntity h19)
				heardList = h19.getBeacons();
			else if(terminal instanceof RadioComputerBlockEntity computer && computer.isInstalled(ComputerApps.APRS))
				heardList = AprsHandler.state(computer).stations();
			else
				continue;
			any = true;
			for(AprsTerminalBlockEntity.Heard heardBeacon : heardList) {
				TncPacket packet = heardBeacon.packet();
				if(now - heardBeacon.gameTime() > STATION_MAX_AGE || packet.position() == null)
					continue;
				AprsWeather weather = AprsWeather.parse(packet.text());
				if(weather != null)
					byCall.putIfAbsent(packet.from(), new WeatherNetwork.Station(packet.from(), packet.position().getX(), packet.position().getZ(), weather));
			}
		}
		out.addAll(byCall.values());
		return any;
	}

	private void build(ServerLevel serverLevel, long now) {
		if(area == null)
			return;
		WeatherSource source = weatherOverride != null ? weatherOverride : WeatherSources.get(serverLevel);
		alerts = AlertBuilder.build(serverLevel, source, stations, area, now);
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

	public List<WeatherAlert> getAlerts() {
		return alerts;
	}

	public boolean isTransmitting() {
		return frames != null;
	}

	/** What it says when right-clicked (and in Jade): why it is not on the air, or what it is sending. */
	public List<Component> statusLines() {
		List<Component> out = new ArrayList<>();
		String base = "message.radiocraft.weather_alert_generator.";
		switch(state) {
			case NO_POWER -> out.add(Component.translatable(base + "no_power").withStyle(ChatFormatting.RED));
			case NO_APRS -> out.add(Component.translatable(base + "no_aprs").withStyle(ChatFormatting.RED));
			case TOO_FEW -> out.add(Component.translatable(base + "too_few", heard, WeatherNetwork.MIN_STATIONS, (int)WeatherNetwork.MIN_SPACING).withStyle(ChatFormatting.YELLOW));
			case TOO_CLOSE -> out.add(Component.translatable(base + "too_close", heard, spread, WeatherNetwork.MIN_STATIONS, (int)WeatherNetwork.MIN_SPACING).withStyle(ChatFormatting.YELLOW));
			case NO_ANTENNA -> out.add(Component.translatable(base + "no_antenna").withStyle(ChatFormatting.RED));
			case TWO_ANTENNAS -> out.add(Component.translatable(base + "two_antennas").withStyle(ChatFormatting.RED));
			case READY -> {
				out.add(Component.translatable(base + "ready", heard, area == null ? 0 : (int)Math.round(area.radius() * 2)).withStyle(ChatFormatting.GREEN));
				if(alerts.isEmpty())
					out.add(Component.translatable(base + "no_alerts"));
				else
					for(WeatherAlert alert : alerts) {
						MutableComponent line = Component.literal(alert.event().words());
						out.add(line.withStyle(alert.event().isWarning() ? ChatFormatting.RED : ChatFormatting.GOLD));
					}
			}
		}
		return out;
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		long[] keys = sentKeys.stream().mapToLong(Long::longValue).toArray();
		tag.putLongArray("sentKeys", keys);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		sentKeys.clear();
		for(long key : tag.getLongArray("sentKeys"))
			sentKeys.add(key);
	}
}
