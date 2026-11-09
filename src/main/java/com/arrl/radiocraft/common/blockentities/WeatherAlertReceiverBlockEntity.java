package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.api.blockentities.radio.IAntennaReceiver;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.WeatherAlertReceiverBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.wxalert.AlertAnnouncement;
import com.arrl.radiocraft.common.radio.wxalert.AlertMemory;
import com.arrl.radiocraft.common.radio.wxalert.BuiltInAntenna;
import com.arrl.radiocraft.common.radio.wxalert.ReceiverSignals;
import com.arrl.radiocraft.common.radio.wxalert.WeatherAlert;
import com.arrl.radiocraft.common.radio.wxalert.WeatherAlertPacket;
import com.arrl.radiocraft.common.radio.wxalert.WeatherVoiceClip;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Simple Weather Alert Receiver (1.12 weather alerts): a battery weather radio that listens on 162.500 MHz to the
 * nearest Simple Weather Alert Generator it hears, through the antenna on its coax or its own short whip
 * ({@link BuiltInAntenna}). On a new alert for where it stands it flashes red, sounds the 1050 Hz attention tone,
 * announces the alert in spliced word clips ({@link AlertAnnouncement}), shows it in chat to players near it, and gives
 * one redstone pulse. A Small Alkaline (or NiCd) Battery (shift + right-click to change it) lasts about three weeks of game time
 * ({@link ReceiverSignals}); when it runs low the lamp blinks every 10 seconds. Right-click silences an alert, or gives
 * the receiver's status.
 */
public class WeatherAlertReceiverBlockEntity extends BlockEntity implements INetworkObjectProvider, IAntennaReceiver {

	/** A generator heard more recently than this keeps a receiver even if a nearer one is heard less often, ticks. */
	public static final long STATION_TIMEOUT = 3L * WeatherAlertGeneratorBlockEntity.ROUTINE_TICKS;
	/** Players within this many blocks see an alert's text line in chat. */
	public static final int TEXT_RANGE = 24;
	/** How far its voice carries, blocks. */
	public static final float SOUND_RANGE = 24.0F;
	/** Most alerts of one batch spoken (the rest are shown as text only). */
	public static final int MAX_SPOKEN = 2;

	private static final Map<ResourceLocation, SoundEvent> SOUNDS = new ConcurrentHashMap<>();

	private ItemStack battery = ItemStack.EMPTY;
	private final AlertMemory memory = new AlertMemory();
	private final ReceiverSignals.Pulse pulse = new ReceiverSignals.Pulse();
	private long alertingUntil = -1L;
	@Nullable
	private BlockPos station; // The generator it listens to.
	private long stationHeardAt = Long.MIN_VALUE / 2;
	private String lastAlert = "";
	private long lastAlertAt = -1L;
	private int pulses = 0; // Redstone pulses given (for GameTests and Jade).

	// Server only: the announcement playing (sounds, when each starts after it began) and the built-in antenna.
	private final List<ResourceLocation> speech = new ArrayList<>();
	private final List<Integer> speechAt = new ArrayList<>();
	private long speechStart = -1L;
	private int speechNext = 0;
	@Nullable
	private BuiltInAntenna whip;

	/** Take Shelter Now: a tornado this near the receiver after it sounded a tornado warning, blocks. */
	public static final double SHELTER_RANGE = 100.0D;
	/** Take Shelter Now: within this long of the warning, ticks (5 minutes). */
	public static final long SHELTER_TICKS = 5L * 60L * 20L;
	/** How often it looks for the tornado while a warning is fresh, ticks. */
	public static final int SHELTER_CHECK_TICKS = 40;
	// Server only, not saved: the last tornado warning it sounded and who was near to hear it.
	private long tornadoWarnedAt = -1L;
	private final java.util.Set<java.util.UUID> warned = new java.util.HashSet<>();
	@Nullable
	private com.arrl.radiocraft.api.weather.WeatherSource weatherOverride = null;

	public WeatherAlertReceiverBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.WEATHER_ALERT_RECEIVER.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide)
			getNetworkObject(level, worldPosition);
	}

	/** On the coax only, for an antenna to hand it what it hears; it draws nothing from a power wire. */
	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, 0, 0);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(whip != null)
			whip.setActive(false);
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		if(whip != null)
			whip.setActive(false);
	}

	// -------------------- BATTERY --------------------

	public ItemStack getBattery() {
		return battery;
	}

	/** Puts {@code stack} (one Small Alkaline or NiCd Battery, or empty) in and gives back the one that was there. */
	public ItemStack swapBattery(ItemStack stack) {
		ItemStack old = battery;
		battery = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
		setChanged();
		return old;
	}

	@Nullable
	private IEnergyStorage energy() {
		return battery.isEmpty() || !com.arrl.radiocraft.common.items.SmallAlkalineBatteryItem.fitsWeatherRadio(battery) ? null : battery.getCapability(Capabilities.EnergyStorage.ITEM);
	}

	/** FE left in the battery (0 with none). */
	public int getEnergy() {
		IEnergyStorage storage = energy();
		return storage == null ? 0 : storage.getEnergyStored();
	}

	public static int capacity() {
		return CommonConfig.SMALL_BATTERY_CAPACITY.get();
	}

	public static double drainPerTick() {
		return ReceiverSignals.drainPerTick(capacity(), ReceiverSignals.BATTERY_DAYS);
	}

	public boolean isFlat() {
		return getEnergy() <= 0;
	}

	public boolean isLowBattery() {
		return ReceiverSignals.isLow(getEnergy(), capacity());
	}

	// -------------------- TICK --------------------

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState blockState, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof WeatherAlertReceiverBlockEntity receiver)
			receiver.serverTick(serverLevel, blockState);
	}

	private void serverTick(ServerLevel serverLevel, BlockState blockState) {
		long now = serverLevel.getGameTime();
		IEnergyStorage storage = energy();
		boolean flat = storage == null || storage.getEnergyStored() <= 0;
		if(!flat) {
			double perTick = drainPerTick() * (isSpeaking() ? ReceiverSignals.ALARM_DRAW_FACTOR : 1);
			int take = ReceiverSignals.drainAt(perTick, now);
			if(take > 0) {
				storage.extractEnergy(take, false);
				setChanged();
			}
			flat = storage.getEnergyStored() <= 0;
		}
		RadioNetworkObject radio = level != null && getNetworkObject(serverLevel, worldPosition) instanceof RadioNetworkObject r ? r : null;
		boolean coaxAntenna = radio != null && !radio.getAntennas().isEmpty();
		if(whip == null)
			whip = new BuiltInAntenna(serverLevel, worldPosition, (packet, strength) -> hear(packet.getPayload(), packet.getFrequency(), strength));
		whip.setActive(!flat && !coaxAntenna);
		if(flat) {
			speech.clear();
			alertingUntil = -1L;
		}
		tickSpeech(serverLevel, now);
		if(tornadoWarnedAt >= 0L && now % SHELTER_CHECK_TICKS == 0)
			checkShelter(serverLevel, now);
		boolean lit = ReceiverSignals.lampLit(isAlerting(now), ReceiverSignals.isLow(getEnergy(), capacity()), flat, now);
		boolean powered = pulse.tick();
		if(blockState.getBlock() instanceof WeatherAlertReceiverBlock
				&& (blockState.getValue(WeatherAlertReceiverBlock.LIT) != lit || blockState.getValue(WeatherAlertReceiverBlock.POWERED) != powered)) {
			boolean redstoneChanged = blockState.getValue(WeatherAlertReceiverBlock.POWERED) != powered;
			serverLevel.setBlock(worldPosition, blockState.setValue(WeatherAlertReceiverBlock.LIT, lit).setValue(WeatherAlertReceiverBlock.POWERED, powered), Block.UPDATE_ALL);
			if(redstoneChanged)
				serverLevel.updateNeighborsAt(worldPosition, blockState.getBlock());
		}
	}

	public boolean isAlerting(long now) {
		return now < alertingUntil;
	}

	/** Stops the alarm: the lamp, the tone and the voice (a player right-clicked it). */
	public void silence() {
		alertingUntil = -1L;
		speech.clear();
		speechAt.clear();
		setChanged();
	}

	private boolean isSpeaking() {
		return speechNext < speech.size();
	}

	private void tickSpeech(ServerLevel serverLevel, long now) {
		while(speechNext < speech.size() && now - speechStart >= speechAt.get(speechNext)) {
			SoundEvent sound = SOUNDS.computeIfAbsent(speech.get(speechNext), id -> SoundEvent.createFixedRangeEvent(id, SOUND_RANGE));
			serverLevel.playSound(null, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
			speechNext++;
		}
	}

	// -------------------- RECEIVING --------------------

	@Override
	public boolean receiveVoice(AntennaVoicePacket packet) {
		return false; // The voice thread: it only decodes the data bursts.
	}

	@Override
	public boolean receiveData(AntennaDataPacket packet) {
		return hear(packet.getPayload(), packet.getFrequency(), packet.getStrength());
	}

	/**
	 * A packet heard at {@code strength}: a weather alert broadcast on the channel, strong enough to decode, from the
	 * nearest generator it hears (or the one it has, until that goes quiet), is taken. True if it was.
	 */
	public boolean hear(TncPacket payload, float frequency, double strength) {
		if(!(level instanceof ServerLevel serverLevel) || payload.kind() != TncPacket.Kind.WX_ALERT || !WeatherAlertPacket.onChannel(frequency) || isFlat())
			return false;
		if(!RadioBlockEntity.decodes(strength, RadiocraftServerConfig.VHF_NOISE_FLOOR.get()))
			return false;
		BlockPos generator = payload.position();
		if(generator == null)
			return false;
		long now = serverLevel.getGameTime();
		BlockPos here = Sublevels.world(serverLevel, worldPosition);
		if(station != null && !station.equals(generator) && now - stationHeardAt <= STATION_TIMEOUT
				&& here.distSqr(generator) >= here.distSqr(station))
			return false; // A further one, while the nearer one is still on the air.
		station = generator.immutable();
		stationHeardAt = now;
		List<WeatherAlert> mine = new ArrayList<>();
		for(WeatherAlert alert : WeatherAlertPacket.alerts(payload))
			if(alert.affects(here.getX() + 0.5D, here.getZ() + 0.5D))
				mine.add(alert);
		List<WeatherAlert> fresh = memory.fresh(mine, now);
		if(!fresh.isEmpty())
			sound(serverLevel, fresh, here, now);
		setChanged();
		return true;
	}

	/** GameTests: use {@code source} for the weather instead of the level's (null for the level's again). */
	public void setWeatherOverride(@Nullable com.arrl.radiocraft.api.weather.WeatherSource source) {
		weatherOverride = source;
	}

	/**
	 * Take Shelter Now: while a tornado warning it sounded is fresh, a tornado on the ground within {@link #SHELTER_RANGE}
	 * gives the advancement to the players who were near when it sounded and to those near now. A cheap look every
	 * {@link #SHELTER_CHECK_TICKS} only while a warning is fresh (the weather source's storms near it).
	 */
	private void checkShelter(ServerLevel serverLevel, long now) {
		if(now - tornadoWarnedAt > SHELTER_TICKS || now < tornadoWarnedAt) {
			tornadoWarnedAt = -1L;
			warned.clear();
			return;
		}
		com.arrl.radiocraft.api.weather.WeatherSource source = weatherOverride != null ? weatherOverride : com.arrl.radiocraft.api.weather.WeatherSources.get(serverLevel);
		BlockPos here = Sublevels.world(serverLevel, worldPosition);
		boolean near = false;
		for(com.arrl.radiocraft.api.weather.StormReport storm : source.storms(serverLevel, here, SHELTER_RANGE * 2, false)) {
			if(!com.arrl.radiocraft.common.radio.weather.WeatherReports.tornadoDown(serverLevel, source, storm, now))
				continue;
			com.arrl.radiocraft.api.weather.StormReport.TrackPoint at = storm.at(now);
			if(Math.hypot(at.x() - (here.getX() + 0.5D), at.z() - (here.getZ() + 0.5D)) <= SHELTER_RANGE) {
				near = true;
				break;
			}
		}
		if(!near)
			return;
		net.minecraft.server.MinecraftServer server = serverLevel.getServer();
		for(java.util.UUID id : warned)
			com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(server, id, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.TAKE_SHELTER_NOW);
		com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grantNear(serverLevel, worldPosition, TEXT_RANGE, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.TAKE_SHELTER_NOW);
		tornadoWarnedAt = -1L;
		warned.clear();
	}

	/** True while a tornado warning it sounded is being watched for Take Shelter Now (GameTests). */
	public boolean watchingForTornado() {
		return tornadoWarnedAt >= 0L;
	}

	/** New alerts: flash, tone, voice, chat line, one redstone pulse. */
	private void sound(ServerLevel serverLevel, List<WeatherAlert> fresh, BlockPos here, long now) {
		if(fresh.stream().anyMatch(alert -> alert.event() == WeatherAlert.Event.TOR)) { // Take Shelter Now watches for the tornado.
			tornadoWarnedAt = now;
			warned.clear();
			for(ServerPlayer player : com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.near(serverLevel, worldPosition, TEXT_RANGE))
				warned.add(player.getUUID());
		}
		alertingUntil = now + ReceiverSignals.ALERT_FLASH_LENGTH;
		pulse.trigger();
		pulses++;
		speech.clear();
		speechAt.clear();
		speech.add(WeatherVoiceClip.toneId());
		speechAt.add(0);
		int offset = 0;
		boolean first = true;
		double rx = here.getX() + 0.5D, rz = here.getZ() + 0.5D;
		for(int i = 0; i < fresh.size() && i < MAX_SPOKEN; i++) {
			List<WeatherVoiceClip> clips = AlertAnnouncement.clips(fresh.get(i), rx, rz);
			int[] starts = AlertAnnouncement.startTicks(clips, first);
			for(int c = 0; c < clips.size(); c++) {
				speech.add(clips.get(c).soundId());
				speechAt.add(offset + starts[c]);
			}
			offset += AlertAnnouncement.lengthTicks(clips, first) + 10;
			first = false;
		}
		speechStart = now;
		speechNext = 0;
		List<String> lines = new ArrayList<>();
		for(WeatherAlert alert : fresh)
			lines.add(AlertAnnouncement.text(alert, rx, rz));
		lastAlert = String.join(" ", lines);
		lastAlertAt = now;
		for(ServerPlayer player : serverLevel.players())
			if(player.distanceToSqr(worldPosition.getCenter()) <= TEXT_RANGE * TEXT_RANGE)
				for(String line : lines)
					player.sendSystemMessage(Component.translatable("message.radiocraft.weather_alert_receiver.alert", line).withStyle(ChatFormatting.RED));
	}

	// -------------------- STATUS --------------------

	/** Redstone pulses it has given since it was placed or loaded. */
	public int getPulses() {
		return pulses;
	}

	@Nullable
	public BlockPos getStation() {
		return station;
	}

	public String getLastAlert() {
		return lastAlert;
	}

	/** What it says when right-clicked (and in Jade): the battery, the station it hears and the last alert. */
	public List<Component> statusLines() {
		List<Component> out = new ArrayList<>();
		String base = "message.radiocraft.weather_alert_receiver.";
		int energy = getEnergy();
		if(battery.isEmpty())
			out.add(Component.translatable(base + "no_battery").withStyle(ChatFormatting.RED));
		else if(energy <= 0)
			out.add(Component.translatable(base + "flat").withStyle(ChatFormatting.RED));
		else {
			int percent = (int)Math.round(100.0D * energy / capacity());
			int days = (int)Math.floor(ReceiverSignals.daysLeft(energy, drainPerTick()));
			out.add(Component.translatable(base + (isLowBattery() ? "battery_low" : "battery"), percent, days)
					.withStyle(isLowBattery() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		}
		long now = level == null ? 0 : level.getGameTime();
		if(station != null && now - stationHeardAt <= STATION_TIMEOUT && level != null) {
			BlockPos here = Sublevels.world(level, worldPosition);
			out.add(Component.translatable(base + "station", (int)Math.round(Math.sqrt(here.distSqr(station)))));
		}
		else
			out.add(Component.translatable(base + "no_station"));
		if(!lastAlert.isEmpty() && lastAlertAt >= 0)
			out.add(Component.translatable(base + "last", String.format(Locale.ROOT, "%d", Math.max(0, (now - lastAlertAt) / 1200)), lastAlert));
		return out;
	}

	// -------------------- SAVE --------------------

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if(!battery.isEmpty())
			tag.put("battery", battery.save(registries));
		tag.put("alerts", memory.save());
		tag.putLong("alertingUntil", alertingUntil);
		if(station != null) {
			tag.putLong("station", station.asLong());
			tag.putLong("stationHeardAt", stationHeardAt);
		}
		tag.putString("lastAlert", lastAlert);
		tag.putLong("lastAlertAt", lastAlertAt);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		battery = tag.contains("battery") ? ItemStack.parseOptional(registries, tag.getCompound("battery")) : ItemStack.EMPTY;
		memory.load(tag.getList("alerts", net.minecraft.nbt.Tag.TAG_COMPOUND));
		alertingUntil = tag.getLong("alertingUntil");
		station = tag.contains("station") ? BlockPos.of(tag.getLong("station")) : null;
		stationHeardAt = tag.contains("stationHeardAt") ? tag.getLong("stationHeardAt") : Long.MIN_VALUE / 2;
		lastAlert = tag.getString("lastAlert");
		lastAlertAt = tag.contains("lastAlertAt") ? tag.getLong("lastAlertAt") : -1L;
	}
}
