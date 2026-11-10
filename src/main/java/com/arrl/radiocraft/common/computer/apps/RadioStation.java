package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.network_objects.DummyLoadNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How the radio computer's radio programs (1.12 step 7) reach the air: the way a real shack computer does, through a
 * radio interface (the TNC and sound card in one) cabled to it, by touching or by data wire, with its radios beside it
 * or on its own data wire. That is the station bus ({@link StationBus}): the computer is a terminal on it, it hears what
 * the interfaces decode and sends through them under their callsign. A radio wired straight to the computer without an
 * interface is not enough.
 */
public final class RadioStation {

	private RadioStation() {}

	/** The computer's bus. */
	public static StationBus bus(RadioComputerBlockEntity computer) {
		return computer.getLevel() == null ? new StationBus(List.of(), List.of()) : StationBus.find(computer.getLevel(), computer.getBlockPos());
	}

	/** Every radio on the radio interfaces of {@code bus}, each once. */
	public static List<RadioBlockEntity> radios(StationBus bus) {
		List<RadioBlockEntity> out = new ArrayList<>();
		for(RadioInterfaceBlockEntity radioInterface : bus.interfaces())
			for(RadioBlockEntity radio : radioInterface.getRadios())
				if(!out.contains(radio))
					out.add(radio);
		return out;
	}

	/** The first radio switched on, or null. */
	@Nullable
	public static RadioBlockEntity firstOn(List<RadioBlockEntity> radios) {
		for(RadioBlockEntity radio : radios)
			if(radio.isPowered())
				return radio;
		return null;
	}

	/** True if one of {@code radios} is copying a signal (a modem could follow it) at {@code gameTime}. */
	public static boolean copying(List<RadioBlockEntity> radios, long gameTime) {
		for(RadioBlockEntity radio : radios)
			if(radio.isCopying(gameTime))
				return true;
		return false;
	}

	/** True if a radio of {@code radios} is on with a real antenna (not a dummy load) on its coax. */
	public static boolean hasAntenna(Level level, List<RadioBlockEntity> radios) {
		for(RadioBlockEntity radio : radios)
			if(radio.isPowered() && IBENetworks.getObject(level, radio.getBlockPos()) instanceof RadioNetworkObject network
					&& network.getAntennas().stream().anyMatch(antenna -> !(antenna instanceof DummyLoadNetworkObject) && antenna.getAntenna() != null))
				return true;
		return false;
	}

	/**
	 * What the screens show about the station: {@code interface} (a radio interface on the bus), {@code callsign},
	 * {@code radioOn}, {@code vhf} (a VHF radio on), {@code mhz} and {@code band} of the first radio on.
	 */
	public static CompoundTag status(StationBus bus) {
		CompoundTag out = new CompoundTag();
		List<RadioBlockEntity> radios = radios(bus);
		RadioBlockEntity on = firstOn(radios);
		out.putBoolean("interface", bus.hasInterface());
		out.putString("callsign", bus.callsign());
		out.putBoolean("radioOn", on != null);
		out.putBoolean("vhf", bus.interfaces().stream().anyMatch(RadioInterfaceBlockEntity::hasVhfRadioOn));
		out.putFloat("mhz", on == null ? 0.0F : on.getFrequency() / 1_000_000.0F);
		out.putString("band", on == null || on.getBand() == null ? "" : on.getBand().name());
		return out;
	}

	/** Why the station cannot send now (an error key), or null if it can: no interface, no callsign, no radio on. */
	@Nullable
	public static String cannotSend(StationBus bus) {
		if(!bus.hasInterface())
			return "station_no_interface";
		if(bus.callsign().isEmpty())
			return "station_no_callsign";
		if(firstOn(radios(bus)) == null)
			return "station_no_radio";
		return null;
	}

	/** A packet id. */
	public static long newId(Level level) {
		return level.getRandom().nextLong();
	}

	/**
	 * Sends {@code packet} through every radio interface on the computer's bus; the computer notes it as its own so it
	 * is not news when it comes back. True if a radio took it.
	 */
	public static boolean transmit(RadioComputerBlockEntity computer, StationBus bus, TncPacket packet) {
		computer.sent(packet);
		if(packet.kind() == TncPacket.Kind.FILE)
			computer.sent(packet.header()); // A file goes out with a header of its own id first.
		return bus.transmit(packet);
	}

	/**
	 * As {@link #transmit}, but only through {@code radio} (APRS's picked transmitter, 2026-11-09): the first radio
	 * interface on the bus that has it beside it sends the packet through it alone. True if it took it.
	 */
	public static boolean transmitThrough(RadioComputerBlockEntity computer, StationBus bus, TncPacket packet, RadioBlockEntity radio) {
		computer.sent(packet);
		if(packet.kind() == TncPacket.Kind.FILE)
			computer.sent(packet.header());
		for(RadioInterfaceBlockEntity radioInterface : bus.interfaces())
			if(radioInterface.getRadios().contains(radio))
				return radioInterface.transmitThrough(packet, radio);
		return false;
	}

	/** Seconds from game time {@code then} to {@code now} (0 if in the future). */
	public static long secondsSince(long now, long then) {
		return Math.max(0L, (now - then) / 20L);
	}
}
