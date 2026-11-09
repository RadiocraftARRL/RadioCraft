package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The station bus: radio interfaces and the terminals (APRS, file transfer) joined by touching or by data wire, the way
 * a terminal is cabled to its modem. Touching devices chain (a stack of them is one bus); a data wire joins only the
 * devices at its ends (2026-11-09, maintainer): a device reached over the wire is on the bus, but what touches it at
 * the far end is not, so a computer wired to another computer does not reach the radios beside that one. Only the
 * device the search starts from follows its data wire, so the bus is the same seen from either end of a wire. A terminal works through every radio interface on its bus
 * and sends under the first callsign it finds there. It is searched afresh each time; a bus is only a few blocks.
 */
public record StationBus(List<RadioInterfaceBlockEntity> interfaces, List<StationTerminal> terminals) {

	/** Furthest a bus reaches from where the search starts, in blocks along the grid. */
	public static final int MAX_REACH = 16;
	private static final int MAX_BLOCKS = 64;

	/** Everything on the bus {@code start} is part of, nearest first; empty if nothing is there. */
	public static StationBus find(Level level, BlockPos start) {
		List<RadioInterfaceBlockEntity> interfaces = new ArrayList<>();
		List<StationTerminal> terminals = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> overWire = new HashSet<>(); // Reached over data wire: an end of the wire, not walked on from.
		queue.add(start);
		visited.add(start);
		while(!queue.isEmpty() && visited.size() <= MAX_BLOCKS) {
			BlockPos at = queue.poll();
			BlockEntity be = level.isLoaded(at) ? level.getBlockEntity(at) : null;
			if(be instanceof RadioInterfaceBlockEntity radioInterface)
				interfaces.add(radioInterface);
			else if(be instanceof StationTerminal terminal)
				terminals.add(terminal);
			else if(!at.equals(start))
				continue;
			if(overWire.contains(at))
				continue; // The far end of a data wire: on the bus, but nothing beyond it is.
			for(Direction direction : Direction.values()) {
				BlockPos next = at.relative(direction);
				if(next.distManhattan(start) <= MAX_REACH && visited.add(next))
					queue.add(next);
			}
			if(!at.equals(start))
				continue; // Only the device looking follows its own data wire: the same bus seen from either end.
			for(BlockPos next : com.arrl.radiocraft.common.be_networks.DataWire.reached(level, at)) // However far the wire runs.
				if(visited.add(next)) {
					overWire.add(next);
					queue.add(next);
				}
		}
		return new StationBus(interfaces, terminals);
	}

	public boolean hasInterface() {
		return !interfaces.isEmpty();
	}

	/** The callsign the terminals send under: the first radio interface's that has one, or empty. */
	public String callsign() {
		for(RadioInterfaceBlockEntity radioInterface : interfaces)
			if(!radioInterface.getCallsign().isEmpty())
				return radioInterface.getCallsign();
		return "";
	}

	/** True if a message or file to {@code to} is for this station: any callsign on the bus, or everyone. */
	public boolean isFor(String to) {
		if(TncPacket.isForEveryone(to))
			return true;
		for(RadioInterfaceBlockEntity radioInterface : interfaces)
			if(!radioInterface.getCallsign().isEmpty() && radioInterface.getCallsign().equals(to))
				return true;
		return false;
	}

	/** Sends {@code packet} out through every radio interface on the bus. True if at least one radio takes it. */
	public boolean transmit(TncPacket packet) {
		boolean sent = false;
		for(RadioInterfaceBlockEntity radioInterface : interfaces)
			sent |= radioInterface.transmit(packet);
		return sent;
	}

	/** True if a radio interface on the bus has a radio switched on. */
	public boolean hasRadioOn() {
		return interfaces.stream().anyMatch(radioInterface -> radioInterface.getRadios().stream().anyMatch(radio -> radio.isPowered()));
	}
}
