package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.be_networks.DataWire;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a program's server side works with for one request: the computer, the player at it, and the ways to the rest
 * of the station. Server thread only.
 * <ul>
 * <li>{@link #files()}: the computer's disk.</li>
 * <li>{@link #scanner()}: the scanner beside it or on its data wire, or null (no scanning or printing then).</li>
 * <li>{@link #linked(Class)}: any block entity touching the computer or on its data wire, nearest first: a radio
 * ({@code RadioBlockEntity}), a direction finder, an ADS-B receiver... ({@link DataWire#linked}).</li>
 * <li>{@link #bus()}: the station bus (radio interfaces and terminals), for packet modes such as APRS.</li>
 * </ul>
 */
public record ComputerContext(RadioComputerBlockEntity computer, ServerPlayer player) {

	public ServerLevel level() {
		return (ServerLevel)computer.getLevel();
	}

	public MinecraftServer server() {
		return player.server;
	}

	public BlockPos pos() {
		return computer.getBlockPos();
	}

	public ComputerFileStore files() {
		return ComputerFileStore.of(server(), computer.getComputerId());
	}

	@Nullable
	public ScannerBlockEntity scanner() {
		return computer.findScanner();
	}

	public <T> List<T> linked(Class<T> type) {
		return DataWire.linked(level(), pos(), type);
	}

	public StationBus bus() {
		return StationBus.find(level(), pos());
	}

	/** Sends {@code app} an event on this player's screen (see {@link RadioComputerBlockEntity#push}). */
	public void push(ComputerApp app, String kind, CompoundTag data) {
		RadioComputerBlockEntity.push(player, app, kind, data);
	}
}
