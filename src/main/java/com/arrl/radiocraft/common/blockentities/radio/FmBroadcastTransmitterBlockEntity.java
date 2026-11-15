package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.WeatherStationBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.station.FmStation;
import com.arrl.radiocraft.common.radio.station.InternetStream;
import com.arrl.radiocraft.common.radio.station.ProgramSource;
import com.arrl.radiocraft.common.radio.station.StationSpeech;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The FM broadcast transmitter (2026-11-13): a broadcast station's two-block cabinet. Transmit only, three times as
 * strong as a radio, for FM broadcast receivers to hear; it draws a lot of power on the air, and its lamps light while
 * it is ({@link FmBroadcastTransmitterBlock#ON_AIR}).
 * <p>
 * <b>A station</b> (2026-11-14, the maintainer): switched on, it is on the air all the time, a steady carrier under
 * whatever it sends, mixed by its {@link FmStation}:
 * <ul>
 * <li>its program: the tape player on its data wire (or touching it), or an internet stream
 * ({@link InternetStream}, where the server allows them: {@code fm_internet_streams}), the tape player standing in
 * while a stream is dead;</li>
 * <li>the live mic (its screen's PTT, a desk mic beside it or on its data wire, a hand mic): the program ducks under
 * it, and while it is keyed the cabinet gives a redstone signal (an ON AIR sign);</li>
 * <li>weather warnings from a weather alert receiver beside it or on its data wire (its alert decoder), relayed in the
 * station's voice once the mic is free;</li>
 * <li>a spoken forecast every few minutes, and its station ID every in-game hour, at a break in the program; a
 * weather station on its data wire adds the conditions there.</li>
 * </ul>
 */
public class FmBroadcastTransmitterBlockEntity extends FmBroadcastRadioBlockEntity {

	/** Power a tick standing by and on the air: a transmitter's worth. */
	public static final int STANDBY_TICK = 20, TRANSMIT_TICK = 120;
	/** How much stronger than a standard radio it is. */
	public static final float POWER = 3.0F;
	/** Ticks between looks for its tape player, alert receiver and weather station. */
	private static final int LOOK_TICKS = 20;
	/** An in-game hour, ticks: the station ID comes round this often. */
	public static final long HOUR_TICKS = 1000L;
	/** Longest name or callsign it takes. */
	public static final int NAME_LENGTH = 24, URL_LENGTH = 512;

	private final FmStation station = new FmStation();
	/** The station's own voice on the air (its mix), not a player's. */
	private final UUID stationVoice;
	/** The station's name or callsign, for its ID. */
	private String callsign = "";
	/** An internet stream's address ("" for none). */
	private String streamUrl = "";
	@Nullable
	private UUID owner;
	@Nullable
	private InternetStream stream;
	/** The last alert relayed: its time at the decoder, so one alert goes out once. */
	private long relayedAlertAt = -1L;
	/** Game time of the next forecast, -1 until the first look. */
	private long nextForecastAt = -1L;
	private long lastIdHour = -1L;
	@Nullable
	private ProgramSource tape;
	@Nullable
	private BlockPos weatherStation;
	@Nullable
	private WeatherAlertReceiverBlockEntity decoder;
	private boolean onAirSignal;

	// Synced for the screen.
	private boolean syncMicLive, syncFallback;
	private int syncSpeaking, syncAlertsWaiting, syncForecastIn;
	private String syncNowPlaying = "", syncStreamStatus = "";
	private boolean syncTape, syncDecoder, syncWeatherStation;

	public FmBroadcastTransmitterBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.FM_BROADCAST_TRANSMITTER.get(), pos, state);
		stationVoice = UUID.nameUUIDFromBytes(("radiocraft-fm-station-" + pos.asLong()).getBytes());
	}

	@Override
	protected float transmitPower() {
		return POWER;
	}

	// -------------------- ON THE AIR --------------------

	/** True while the station is on the air: switched on and powered, and not blown or inhibited. */
	public boolean onAir() {
		return isPowered() && !isBlown() && !isTransmitInhibited();
	}

	/** Its tick: the station's frames (3 then 2, 50 a second), its lamps and ON AIR signal, and what it looks after. */
	@Override
	protected void additionalTick() {
		super.additionalTick();
		if(!(level instanceof ServerLevel serverLevel))
			return;
		long now = serverLevel.getGameTime();
		boolean on = onAir();
		station.setMicKeyed(isPTTDown());
		if(now % LOOK_TICKS == Math.floorMod(worldPosition.asLong(), LOOK_TICKS))
			look(serverLevel);
		if(on) {
			schedule(serverLevel, now);
			ProgramSource selected = station.program() == FmStation.Program.STREAM ? stream : tape;
			de.maxhenkel.voicechat.api.ServerLevel voiceLevel = () -> serverLevel;
			FmStation.Voice voice = voice(serverLevel);
			int frames = now % 2L == 0L ? 3 : 2;
			for(int i = 0; i < frames; i++)
				super.transmitVoice(voiceLevel, station.frame(selected, tape, voice), getTransmitFrequency(), stationVoice, 0.0F);
			recordTransmit(); // On the air: it draws a transmitter's power and lights the cabinet.
		}
		boolean keyed = on && station.micLive();
		if(keyed != onAirSignal) { // The ON AIR sign.
			onAirSignal = keyed;
			serverLevel.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
		if(now % 4L == 0L)
			FmBroadcastTransmitterBlock.setOnAir(serverLevel, worldPosition, on);
		if(now % 10L == 0L)
			syncStatus(now);
	}

	/** A mic frame on its way to the air goes to the station's mix while it is on; else on the air as any radio's. */
	@Override
	protected void transmitVoice(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, float txHz, UUID sourcePlayer, float ctcssTone) {
		if(onAir() && !stationVoice.equals(sourcePlayer)) {
			station.offerMic(rawAudio);
			return;
		}
		super.transmitVoice(level, rawAudio, txHz, sourcePlayer, ctcssTone);
	}

	/** Its spoken items, from {@link StationSpeech}. */
	private FmStation.Voice voice(ServerLevel serverLevel) {
		return new FmStation.Voice() {
			@Override
			public short[] alert(String text) {
				return StationSpeech.alert(text, true);
			}

			@Override
			public short[] forecast() {
				return StationSpeech.forecast(serverLevel, worldPosition, weatherStation);
			}

			@Override
			public short[] stationId() {
				return StationSpeech.stationId(callsign, getFrequency());
			}
		};
	}

	/** The forecast every few minutes and the ID every in-game hour come due. */
	private void schedule(ServerLevel serverLevel, long now) {
		int minutes = station.forecastMinutes();
		if(minutes > 0) {
			if(nextForecastAt < 0L || nextForecastAt > now + minutes * 1200L)
				nextForecastAt = now + minutes * 1200L;
			if(now >= nextForecastAt) {
				station.forecastDue();
				nextForecastAt = now + minutes * 1200L;
			}
		}
		else
			nextForecastAt = -1L;
		long hour = Math.floorDiv(serverLevel.getDayTime(), HOUR_TICKS);
		if(lastIdHour < 0L)
			lastIdHour = hour;
		else if(hour != lastIdHour) {
			lastIdHour = hour;
			station.stationIdDue();
		}
	}

	/** Finds its tape player, alert decoder and weather station, and takes any new alert from the decoder. */
	private void look(ServerLevel serverLevel) {
		List<ProgramSource> sources = DataWire.linked(serverLevel, worldPosition, ProgramSource.class);
		tape = null;
		for(ProgramSource source : sources)
			if(!(source instanceof InternetStream)) {
				tape = source;
				break;
			}
		List<WeatherStationBlockEntity> stations = DataWire.linked(serverLevel, worldPosition, WeatherStationBlockEntity.class);
		weatherStation = stations.isEmpty() ? null : stations.getFirst().getBlockPos();
		List<WeatherAlertReceiverBlockEntity> decoders = DataWire.linked(serverLevel, worldPosition, WeatherAlertReceiverBlockEntity.class);
		decoder = decoders.isEmpty() ? null : decoders.getFirst();
		if(decoder != null) {
			long at = decoder.getLastAlertAt();
			if(relayedAlertAt < 0L)
				relayedAlertAt = Math.max(0L, at); // A decoder found (or the station loaded): only what comes after.
			else if(at > relayedAlertAt) {
				relayedAlertAt = at;
				if(onAir())
					station.queueAlert(decoder.getLastAlert());
			}
		}
		// The stream follows the program choice and the server's rule.
		boolean wantStream = onAir() && station.program() == FmStation.Program.STREAM && !streamUrl.isBlank() && streamsAllowed() && listened(serverLevel);
		if(wantStream && stream == null) {
			stream = new InternetStream(streamUrl, owner);
			stream.start();
		}
		else if(!wantStream && stream != null)
			stopStream();
	}

	/** A minute's grace after the last listener goes, so tuning past it or stepping away does not drop and refetch the stream. */
	private static final long LISTEN_GRACE_TICKS = 1200L;
	/** Tuned this close counts as on the way to it: the stream is buffering by the time they land on it. */
	private static final float LISTEN_WINDOW_HZ = 1_000_000.0F;
	private long lastListened = Long.MIN_VALUE / 2;

	/**
	 * True while someone may hear the station (2026-11-15, the maintainer: a stream holds one of the server's few
	 * slots, so only open it while a radio in range is on and tuned near it): a radio within reach tuned within a
	 * megahertz, or one that was in the last minute.
	 */
	private boolean listened(ServerLevel serverLevel) {
		long now = serverLevel.getGameTime();
		double reach = com.arrl.radiocraft.common.radio.BandUtils.maxReach(com.arrl.radiocraft.common.radio.Band.getBand(com.arrl.radiocraft.common.radio.Band.FM_BROADCAST));
		if(com.arrl.radiocraft.common.radio.station.FmListeners.anyNear(serverLevel, worldPosition, getFrequency(), LISTEN_WINDOW_HZ, reach, now))
			lastListened = now;
		return now - lastListened <= LISTEN_GRACE_TICKS;
	}

	private void stopStream() {
		if(stream != null) {
			stream.stop();
			stream = null;
		}
	}

	/** Whether the server lets stations play internet streams ({@code fm_internet_streams}, off by default). */
	public static boolean streamsAllowed() {
		return com.arrl.radiocraft.RadiocraftServerConfig.FM_INTERNET_STREAMS.get();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		stopStream();
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		stopStream();
	}

	/** The ON AIR signal: 15 while the mic is live (redstone from the cabinet). */
	public int onAirSignal() {
		return onAirSignal ? 15 : 0;
	}

	@Override
	public boolean shouldPlayStatic() {
		return false;
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, TRANSMIT_TICK, STANDBY_TICK);
	}

	// -------------------- SETTINGS (from its screen) --------------------

	public FmStation station() {
		return station;
	}

	public String getCallsign() {
		return callsign;
	}

	public String getStreamUrl() {
		return streamUrl;
	}

	@Nullable
	public UUID getOwner() {
		return owner;
	}

	/** Who placed it: the one who may set its stream. */
	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		setChanged();
	}

	public void setCallsign(String value) {
		String clean = value == null ? "" : value.strip();
		callsign = clean.length() > NAME_LENGTH ? clean.substring(0, NAME_LENGTH) : clean;
		changed();
	}

	/** Sets the stream's address: http(s) only (the stream itself checks the rest); "" for none. */
	public void setStreamUrl(String value) {
		String clean = value == null ? "" : value.strip();
		if(clean.length() > URL_LENGTH)
			clean = clean.substring(0, URL_LENGTH);
		if(!clean.isEmpty() && !clean.regionMatches(true, 0, "http://", 0, 7) && !clean.regionMatches(true, 0, "https://", 0, 8))
			return;
		if(!clean.equals(streamUrl)) {
			streamUrl = clean;
			stopStream(); // Starts again on the new address at the next look.
		}
		changed();
	}

	public void setProgram(FmStation.Program program) {
		station.setProgram(program);
		if(program != FmStation.Program.STREAM)
			stopStream();
		changed();
	}

	public void setForecastMinutes(int minutes) {
		station.setForecastMinutes(minutes);
		nextForecastAt = -1L;
		changed();
	}

	public void setStationIdOn(boolean on) {
		station.setStationIdOn(on);
		changed();
	}

	public void setDuckLevel(float level) {
		station.setDuckLevel(level);
		changed();
	}

	private void changed() {
		setChanged();
		updateBlock();
	}

	// -------------------- STATUS (synced for its screen) --------------------

	private void syncStatus(long now) {
		boolean micLive = onAir() && station.micLive();
		int speaking = station.speaking().ordinal(), waiting = station.alertsWaiting();
		int forecastIn = nextForecastAt < 0L ? -1 : (int)Math.max(0L, (nextForecastAt - now) / 20L);
		String playing = station.nowPlaying() == null ? "" : station.nowPlaying();
		String streamStatus = stream == null ? "" : stream.status() == InternetStream.Status.ERROR && !stream.reason().isEmpty()
				? stream.status() + ": " + stream.reason() : String.valueOf(stream.status());
		boolean fallback = station.onFallback();
		if(micLive != syncMicLive || speaking != syncSpeaking || waiting != syncAlertsWaiting || Math.abs(forecastIn - syncForecastIn) >= 5 || forecastIn < 0 != syncForecastIn < 0
				|| !playing.equals(syncNowPlaying) || !streamStatus.equals(syncStreamStatus) || fallback != syncFallback
				|| (tape != null) != syncTape || (decoder != null) != syncDecoder || (weatherStation != null) != syncWeatherStation) {
			syncMicLive = micLive;
			syncSpeaking = speaking;
			syncAlertsWaiting = waiting;
			syncForecastIn = forecastIn;
			syncNowPlaying = playing;
			syncStreamStatus = streamStatus;
			syncFallback = fallback;
			syncTape = tape != null;
			syncDecoder = decoder != null;
			syncWeatherStation = weatherStation != null;
			updateBlock();
		}
	}

	public boolean isMicLive() {
		return syncMicLive;
	}

	public FmStation.Speaking speakingNow() {
		FmStation.Speaking[] all = FmStation.Speaking.values();
		return all[Math.max(0, Math.min(all.length - 1, syncSpeaking))];
	}

	public int alertsWaiting() {
		return syncAlertsWaiting;
	}

	/** Seconds to the next forecast, -1 for none. */
	public int forecastIn() {
		return syncForecastIn;
	}

	public String nowPlaying() {
		return syncNowPlaying;
	}

	public String streamStatus() {
		return syncStreamStatus;
	}

	public boolean onFallback() {
		return syncFallback;
	}

	public boolean hasTape() {
		return syncTape;
	}

	public boolean hasDecoder() {
		return syncDecoder;
	}

	public boolean hasWeatherStation() {
		return syncWeatherStation;
	}

	// -------------------- SAVED AND SYNCED --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		CompoundTag s = new CompoundTag();
		station.save(s);
		s.putString("callsign", callsign);
		s.putString("url", streamUrl);
		if(owner != null)
			s.putUUID("owner", owner);
		s.putLong("relayed", relayedAlertAt);
		s.putBoolean("micLive", syncMicLive);
		s.putInt("speaking", syncSpeaking);
		s.putInt("alerts", syncAlertsWaiting);
		s.putInt("forecastIn", syncForecastIn);
		s.putString("playing", syncNowPlaying);
		s.putString("streamStatus", syncStreamStatus);
		s.putBoolean("fallback", syncFallback);
		s.putBoolean("tape", syncTape);
		s.putBoolean("decoder", syncDecoder);
		s.putBoolean("weatherStation", syncWeatherStation);
		nbt.put("station", s);
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		if(!nbt.contains("station"))
			return;
		CompoundTag s = nbt.getCompound("station");
		station.load(s);
		callsign = s.getString("callsign");
		streamUrl = s.getString("url");
		owner = s.hasUUID("owner") ? s.getUUID("owner") : null;
		relayedAlertAt = s.contains("relayed") ? s.getLong("relayed") : -1L;
		syncMicLive = s.getBoolean("micLive");
		syncSpeaking = s.getInt("speaking");
		syncAlertsWaiting = s.getInt("alerts");
		syncForecastIn = s.contains("forecastIn") ? s.getInt("forecastIn") : -1;
		syncNowPlaying = s.getString("playing");
		syncStreamStatus = s.getString("streamStatus");
		syncFallback = s.getBoolean("fallback");
		syncTape = s.getBoolean("tape");
		syncDecoder = s.getBoolean("decoder");
		syncWeatherStation = s.getBoolean("weatherStation");
	}
}
