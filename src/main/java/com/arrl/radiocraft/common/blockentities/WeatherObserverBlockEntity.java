package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.capabilities.PlayerCallsignData;
import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.WeatherObserverBlock;
import com.arrl.radiocraft.common.data.PlayerCallsignSavedData;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Weather Observer (2026-11-18): every {@link #INTERVAL_TICKS}, and on a redstone pulse, it reads the nearest Local
 * Weather Station within {@link WeatherStationBlockEntity#RANGE} blocks and sends its readings as a
 * {@link TncPacket.Kind#WX_OBS} report through the first powered radio it touches or is data-wired to, on that radio's
 * frequency. It goes out under its owner's callsign (one is enough for any number of observers: generators tell them
 * apart by where they are). It takes no power of its own.
 */
public class WeatherObserverBlockEntity extends BlockEntity {

	/** Ticks between reports (5 minutes). */
	public static final int INTERVAL_TICKS = 6000;
	/** Ticks the lamp stays lit after a report goes out. */
	public static final int LAMP_TICKS = 40;

	private String callsign = "";
	private long lastSent = Long.MIN_VALUE / 2;
	private boolean powered = false;
	/** The first report goes out soon after it loads, staggered by where it is. */
	private long firstAt = -1L;

	public WeatherObserverBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.WEATHER_OBSERVER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WeatherObserverBlockEntity observer) {
		if(level instanceof ServerLevel serverLevel)
			observer.serverTick(serverLevel, state);
	}

	private void serverTick(ServerLevel level, BlockState state) {
		long now = level.getGameTime();
		if(firstAt < 0L)
			firstAt = now + 100L + Math.floorMod(worldPosition.asLong(), 200L);
		boolean due = now >= firstAt && now - lastSent >= INTERVAL_TICKS;
		if(due && (now + worldPosition.asLong()) % 20L == 0L)
			send(level);
		boolean lit = now - lastSent < LAMP_TICKS;
		if(state.getBlock() instanceof WeatherObserverBlock && state.getValue(WeatherObserverBlock.SENDING) != lit)
			level.setBlock(worldPosition, state.setValue(WeatherObserverBlock.SENDING, lit), Block.UPDATE_CLIENTS);
	}

	/** A redstone signal coming on sends a report now. */
	public void redstone(boolean on) {
		if(on && !powered && level instanceof ServerLevel serverLevel)
			send(serverLevel);
		powered = on;
	}

	/**
	 * Sends a report now if it can: a callsign, a Local Weather Station in range and a powered radio. True if one went
	 * out. If it cannot, it tries again a minute later.
	 */
	public boolean send(ServerLevel level) {
		long now = level.getGameTime();
		String weather = weather();
		RadioBlockEntity radio = radio();
		if(callsign.isEmpty() || weather.isEmpty() || radio == null) {
			lastSent = Math.max(lastSent, now - INTERVAL_TICKS + 1200L); // Look again in a minute.
			return false;
		}
		TncPacket packet = TncPacket.weatherObservation(level.getRandom().nextLong(), callsign, worldPosition, weather);
		if(!radio.sendData(packet)) {
			lastSent = Math.max(lastSent, now - INTERVAL_TICKS + 1200L);
			return false;
		}
		lastSent = now;
		setChanged();
		return true;
	}

	/** The nearest Local Weather Station's APRS weather data, or empty with none in range. */
	private String weather() {
		WeatherStationBlockEntity station = level == null ? null : WeatherStationBlockEntity.nearest(level, worldPosition);
		return station == null ? "" : station.aprsWeather();
	}

	/** The first powered radio it touches or is data-wired to, else the first one, else null. */
	@Nullable
	public RadioBlockEntity radio() {
		if(level == null)
			return null;
		List<RadioBlockEntity> radios = DataWire.linked(level, worldPosition, RadioBlockEntity.class);
		for(RadioBlockEntity radio : radios)
			if(radio.isPowered())
				return radio;
		return radios.isEmpty() ? null : radios.get(0);
	}

	/** Takes {@code player}'s callsign. False (and nothing changes) if they have none. */
	public boolean claim(ServerPlayer player) {
		PlayerCallsignData data = PlayerCallsignSavedData.get(player.serverLevel().getServer().overworld()).getCallsignData(player.getUUID());
		if(data == null || data.callsign() == null || data.callsign().isBlank())
			return false;
		setCallsign(data.callsign());
		return true;
	}

	public void setCallsign(String value) {
		callsign = TncPacket.sanitizeCallsign(value);
		setChanged();
	}

	public String getCallsign() {
		return callsign;
	}

	/** Game time of the last report sent. */
	public long getLastSent() {
		return lastSent;
	}

	/** What right clicking it says: what stops it, or where its reports go and when. */
	public List<Component> statusLines() {
		List<Component> out = new ArrayList<>();
		String base = "message.radiocraft.weather_observer.";
		WeatherStationBlockEntity station = level == null ? null : WeatherStationBlockEntity.nearest(level, worldPosition);
		RadioBlockEntity radio = radio();
		if(callsign.isEmpty())
			out.add(Component.translatable(base + "no_callsign").withStyle(ChatFormatting.RED));
		if(station == null)
			out.add(Component.translatable(base + "no_station", WeatherStationBlockEntity.RANGE).withStyle(ChatFormatting.RED));
		if(radio == null)
			out.add(Component.translatable(base + "no_radio").withStyle(ChatFormatting.RED));
		else if(!radio.isPowered())
			out.add(Component.translatable(base + "radio_off").withStyle(ChatFormatting.YELLOW));
		if(out.isEmpty()) {
			long now = level.getGameTime();
			String mhz = String.format(Locale.ROOT, "%.3f", radio.getSimplexTransmitHz() / 1_000_000.0D);
			int stationDistance = (int)Math.round(Math.sqrt(station.getBlockPos().distSqr(worldPosition)));
			out.add(Component.translatable(base + "ready", callsign, mhz, stationDistance).withStyle(ChatFormatting.GREEN));
			if(now - lastSent < INTERVAL_TICKS * 4L)
				out.add(Component.translatable(base + "last", (now - lastSent) / 1200L, INTERVAL_TICKS / 1200).withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString("callsign", callsign);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		callsign = tag.getString("callsign");
	}
}
