package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.weather.WeatherReport;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * A Local Weather Station Display (2026-11-18): once a second it reads the nearest Local Weather Station within
 * {@link WeatherStationBlockEntity#RANGE} blocks and sends clients what it reads, in the units a US home weather
 * display shows ({@link #readings}); its renderer steps through them on the red LCD. With no station in range it shows
 * dashes. It needs no power (a battery display).
 */
public class WeatherDisplayBlockEntity extends BlockEntity {

	/** What it shows, in order: temperature °F, humidity %, pressure in hundredths of inHg, wind from (degrees), wind and gust mph, rain in the last hour in hundredths of an inch. */
	public static final int TEMP = 0, HUMIDITY = 1, PRESSURE = 2, WIND_FROM = 3, WIND = 4, GUST = 5, RAIN = 6, COUNT = 7;

	/** Null with no station in range. Synced to clients. */
	@Nullable
	private int[] readings;

	public WeatherDisplayBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.WEATHER_DISPLAY.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WeatherDisplayBlockEntity display) {
		if(level instanceof ServerLevel && (level.getGameTime() + pos.asLong()) % 20L == 0L)
			display.read();
	}

	private void read() {
		if(level == null)
			return;
		WeatherStationBlockEntity station = WeatherStationBlockEntity.nearest(level, worldPosition);
		int[] now = station == null ? null : readingsOf(station.report(), station.gust(), station.rainLastHour());
		if(!Arrays.equals(now, readings)) {
			readings = now;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** {@code report} (°C, m/s, hPa, %), the gust (m/s) and the rain in the last hour (mm) in the display's units. */
	public static int[] readingsOf(WeatherReport report, double gustMs, double rainMm) {
		int[] out = new int[COUNT];
		out[TEMP] = (int)Math.round(report.temperature() * 9.0D / 5.0D + 32.0D);
		out[HUMIDITY] = (int)Math.round(Math.max(0.0D, Math.min(100.0D, report.humidity())));
		out[PRESSURE] = (int)Math.round(report.pressure() * 2.953D); // hPa to hundredths of inHg.
		out[WIND_FROM] = Math.floorMod((int)Math.round(report.windFrom()), 360);
		out[WIND] = (int)Math.round(report.windSpeed() * 2.23694D);
		out[GUST] = (int)Math.round(Math.max(gustMs, report.windSpeed()) * 2.23694D);
		out[RAIN] = (int)Math.round(rainMm / 0.254D); // mm to hundredths of an inch.
		return out;
	}

	/** What it shows, or null with no station in range. */
	@Nullable
	public int[] getReadings() {
		return readings;
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if(readings != null)
			tag.putIntArray("readings", readings);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		int[] read = tag.getIntArray("readings");
		readings = read.length == COUNT ? read : null;
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
