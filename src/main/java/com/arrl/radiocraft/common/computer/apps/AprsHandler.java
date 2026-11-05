package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity.Delivery;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity.Heard;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity.Logged;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.WeatherStationBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.DirectionFinding;
import com.arrl.radiocraft.common.radio.data.AprsIcon;
import com.arrl.radiocraft.common.radio.data.AprsMapPainter;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The APRS program's server side (1.12 step 7): the computer as an APRS station, like the H19 terminal
 * ({@code AprsTerminalBlockEntity}, whose packets, acks and retries it follows) but with the computer's screen. It needs a
 * radio interface on its station bus (cabled by touching or data wire) with a VHF radio on it; it hears what the
 * interfaces decode and sends under their callsign. It keeps the stations heard and the messages (per computer, in the
 * computer's save) and beacons on demand or on the server's beacon timer ({@code tnc_beacon_interval_seconds}). New
 * stations and messages are pushed to the screens at the computer as an {@code update} event.
 * <ul>
 * <li>{@code status}: the station ({@link RadioStation#status}), {@code comment}, {@code icon}, {@code auto},
 * {@code interval}, {@code lastBeacon} (seconds ago, -1 never), {@code stations} (each {@code call}, {@code icon},
 * {@code comment}, {@code x}, {@code z}, {@code distance}, {@code bearing}, {@code ago}, {@code first}, {@code hops},
 * {@code position}) and {@code messages} (each {@code from}, {@code to}, {@code text}, {@code out}, {@code delivery},
 * {@code retries}, {@code ago}, {@code unread}).</li>
 * <li>{@code read}: the messages so far are read (no longer {@code unread}).</li>
 * <li>{@code map}: {@code pixels}, a 128 square map of home and the stations (the H19's map style).</li>
 * <li>{@code beacon}, {@code send} ({@code to}, {@code text}), {@code settings} ({@code comment}, {@code icon},
 * {@code auto}), {@code callsign} ({@code callsign}, empty for the player's own), {@code clear} (forgets the
 * stations).</li>
 * </ul>
 */
public final class AprsHandler implements ComputerAppHandler {

	/** Stations and messages each kept, newest first. */
	public static final int HISTORY = 40;
	private static final int CHECK_TICKS = 10;
	/** Ticks before an automatic beacon that found no radio to send it tries again. */
	private static final long RETRY_BEACON_TICKS = 1200L;

	/** One computer's APRS station. */
	public static final class State implements ComputerAppState {
		final List<Heard> stations = new ArrayList<>();
		final List<Logged> messages = new ArrayList<>();
		String comment = "";
		AprsIcon icon = AprsIcon.HOUSE;
		boolean auto = false;
		long lastBeacon = -1L;
		long lastRead = -1L;
		int nextNumber = 1;

		public List<Heard> stations() {
			return List.copyOf(stations);
		}

		public List<Logged> messages() {
			return List.copyOf(messages);
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putString("comment", comment);
			tag.putString("icon", icon.name());
			tag.putBoolean("auto", auto);
			tag.putLong("lastBeacon", lastBeacon);
			tag.putLong("lastRead", lastRead);
			tag.putInt("nextNumber", nextNumber);
			ListTag heard = new ListTag();
			for(Heard station : stations) {
				CompoundTag entry = station.packet().save();
				entry.putLong("heardAt", station.gameTime());
				entry.putLong("firstHeardAt", station.firstHeard());
				heard.add(entry);
			}
			tag.put("stations", heard);
			ListTag logged = new ListTag();
			for(Logged message : messages) {
				CompoundTag entry = message.packet().save();
				entry.putLong("loggedAt", message.gameTime());
				entry.putBoolean("outgoing", message.outgoing());
				entry.putString("delivery", message.delivery().name());
				entry.putInt("retries", message.retries());
				entry.putLong("lastSent", message.lastSent());
				logged.add(entry);
			}
			tag.put("messages", logged);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			comment = TncPacket.sanitizeText(tag.getString("comment"), TncPacket.BEACON_TEXT_MAX);
			icon = AprsIcon.byName(tag.getString("icon"));
			auto = tag.getBoolean("auto");
			lastBeacon = tag.contains("lastBeacon") ? tag.getLong("lastBeacon") : -1L;
			lastRead = tag.contains("lastRead") ? tag.getLong("lastRead") : -1L;
			nextNumber = Math.max(1, tag.getInt("nextNumber"));
			stations.clear();
			for(Tag entry : tag.getList("stations", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag)entry;
				stations.add(new Heard(TncPacket.load(c), c.getLong("heardAt"), c.getLong("firstHeardAt")));
			}
			messages.clear();
			for(Tag entry : tag.getList("messages", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag)entry;
				Delivery delivery;
				try {
					delivery = Delivery.valueOf(c.getString("delivery"));
				}
				catch(IllegalArgumentException e) {
					delivery = Delivery.SENT;
				}
				messages.add(new Logged(TncPacket.load(c), c.getBoolean("outgoing"), c.getLong("loggedAt"), delivery, c.getInt("retries"), c.getLong("lastSent")));
			}
		}
	}

	/** {@code computer}'s APRS station. */
	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.APRS, State::new);
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State state = state(computer);
		switch(action) {
			case "status" -> reply.ok(status(computer, state));
			case "map" -> reply.ok(map(computer, state));
			case "beacon" -> {
				String problem = cannotSend(context.bus());
				if(problem == null && !beacon(computer, state))
					problem = "aprs_not_sent";
				answer(reply, problem);
			}
			case "send" -> answer(reply, send(computer, state, data.getString("to"), data.getString("text")));
			case "settings" -> {
				if(data.contains("comment"))
					state.comment = TncPacket.sanitizeText(data.getString("comment"), TncPacket.BEACON_TEXT_MAX);
				if(data.contains("icon"))
					state.icon = AprsIcon.byName(data.getString("icon"));
				if(data.contains("auto")) {
					state.auto = data.getBoolean("auto");
					if(state.auto)
						state.lastBeacon = -1L; // Switching it on beacons at once (with a radio to send it).
				}
				computer.setChanged();
				reply.ok();
			}
			case "callsign" -> {
				List<RadioInterfaceBlockEntity> interfaces = context.bus().interfaces();
				if(interfaces.isEmpty()) {
					reply.error("station_no_interface");
					return;
				}
				String callsign = TncPacket.sanitizeCallsign(data.getString("callsign"));
				boolean done = true;
				for(RadioInterfaceBlockEntity radioInterface : interfaces) {
					if(callsign.isEmpty())
						done &= radioInterface.claim(context.player());
					else
						radioInterface.setCallsign(callsign, context.player().getGameProfile().getName());
				}
				answer(reply, done ? null : "station_no_own_callsign");
			}
			case "read" -> {
				state.lastRead = context.level().getGameTime();
				computer.setChanged();
				reply.ok();
			}
			case "clear" -> {
				state.stations.clear();
				computer.setChanged();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	private static void answer(ComputerReply reply, String problem) {
		if(problem == null)
			reply.ok();
		else
			reply.error(problem);
	}

	/** Why APRS cannot send: the station's reasons, or no VHF radio on (APRS is on 2m); null if it can. */
	static String cannotSend(StationBus bus) {
		String problem = RadioStation.cannotSend(bus);
		if(problem != null)
			return problem;
		return bus.interfaces().stream().anyMatch(RadioInterfaceBlockEntity::hasVhfRadioOn) ? null : "aprs_no_vhf";
	}

	// -------------------- SENDING --------------------

	/** Where the computer really is (its place in the world on a Sable ship). */
	private static BlockPos here(RadioComputerBlockEntity computer) {
		return Sublevels.world(computer.getLevel(), computer.getBlockPos());
	}

	/** Beacons the station's position and comment (with a weather station in range, its weather). False if nothing took it. */
	public static boolean beacon(RadioComputerBlockEntity computer, State state) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return false;
		StationBus bus = RadioStation.bus(computer);
		if(bus.callsign().isEmpty())
			return false;
		state.lastBeacon = level.getGameTime();
		computer.setChanged();
		WeatherStationBlockEntity station = WeatherStationBlockEntity.nearest(level, computer.getBlockPos());
		String weather = station == null ? "" : station.aprsWeather();
		TncPacket packet = weather.isEmpty() ? TncPacket.beacon(RadioStation.newId(level), bus.callsign(), here(computer), state.comment, state.icon.name())
				: TncPacket.weatherBeacon(RadioStation.newId(level), bus.callsign(), here(computer), weather, state.comment);
		return RadioStation.transmit(computer, bus, packet);
	}

	/** Sends {@code text} to {@code to}; null if it went, else the error key. */
	static String send(RadioComputerBlockEntity computer, State state, String to, String text) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return "aprs_not_sent";
		StationBus bus = RadioStation.bus(computer);
		String problem = cannotSend(bus);
		if(problem != null)
			return problem;
		String target = TncPacket.sanitizeCallsign(to);
		if(target.isEmpty())
			return "aprs_no_recipient";
		if(TncPacket.sanitizeText(text, TncPacket.MESSAGE_TEXT_MAX).isEmpty())
			return "aprs_no_text";
		boolean everyone = TncPacket.isForEveryone(target);
		TncPacket packet = everyone ? TncPacket.message(RadioStation.newId(level), bus.callsign(), target, text)
				: TncPacket.message(RadioStation.newId(level), bus.callsign(), target, text, state.nextNumber);
		if(!RadioStation.transmit(computer, bus, packet))
			return "aprs_not_sent";
		if(!everyone)
			state.nextNumber = state.nextNumber % 99999 + 1;
		long now = level.getGameTime();
		log(state, new Logged(packet, true, now, everyone ? Delivery.SENT : Delivery.SENDING, 0, now));
		computer.setChanged();
		return null;
	}

	private static void log(State state, Logged entry) {
		state.messages.addFirst(entry);
		while(state.messages.size() > HISTORY)
			state.messages.removeLast();
	}

	// -------------------- RECEIVING --------------------

	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return;
		State state = state(computer);
		long now = level.getGameTime();
		StationBus bus = null;
		String news;
		switch(packet.kind()) {
			case BEACON -> {
				Heard before = state.stations.stream().filter(heard -> heard.packet().from().equals(packet.from())).findFirst().orElse(null);
				state.stations.remove(before);
				state.stations.addFirst(new Heard(packet, now, before == null ? now : before.firstHeard()));
				while(state.stations.size() > HISTORY)
					state.stations.removeLast();
				news = before == null ? "station" : "beacon";
			}
			case MESSAGE -> {
				bus = RadioStation.bus(computer);
				if(!bus.isFor(packet.to()))
					return;
				String number = packet.messageNumber();
				if(!number.isEmpty() && !TncPacket.isForEveryone(packet.to())) { // Ack it every time: the last ack may have been lost.
					RadioStation.transmit(computer, bus, TncPacket.ack(RadioStation.newId(level), packet.to(), packet.from(), number));
					boolean again = state.messages.stream().anyMatch(logged -> !logged.outgoing() && logged.packet().from().equals(packet.from())
							&& logged.packet().messageNumber().equals(number));
					if(again)
						return;
				}
				log(state, new Logged(packet, false, now));
				level.playSound(null, computer.getBlockPos(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5F, 1.6F); // The PC speaker's beep.
				news = "message";
			}
			case ACK -> {
				bus = RadioStation.bus(computer);
				if(!bus.isFor(packet.to()) || !acknowledged(state, packet.from(), packet.text()))
					return;
				news = "ack";
			}
			default -> {
				return;
			}
		}
		computer.setChanged();
		CompoundTag event = new CompoundTag();
		event.putString("what", news);
		event.putString("from", packet.from());
		computer.push(ComputerApps.APRS, "update", event);
	}

	/** {@code from} says it got our message {@code number}: that message is SENT. */
	private static boolean acknowledged(State state, String from, String number) {
		for(int i = 0; i < state.messages.size(); i++) {
			Logged logged = state.messages.get(i);
			if(logged.outgoing() && logged.delivery() != Delivery.SENT && logged.packet().to().equals(from) && logged.packet().messageNumber().equals(number)) {
				state.messages.set(i, new Logged(logged.packet(), true, logged.gameTime(), Delivery.SENT, logged.retries(), logged.lastSent()));
				return true;
			}
		}
		return false;
	}

	// -------------------- TIMERS --------------------

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if((gameTime + computer.getBlockPos().asLong()) % CHECK_TICKS != 0)
			return;
		State state = state(computer);
		if(state.auto) {
			long interval = RadiocraftServerConfig.TNC_BEACON_INTERVAL_SECONDS.get() * 20L;
			if(state.lastBeacon < 0 || gameTime - state.lastBeacon >= interval || gameTime < state.lastBeacon) {
				StationBus bus = RadioStation.bus(computer);
				if(cannotSend(bus) == null)
					beacon(computer, state);
				else
					state.lastBeacon = gameTime - interval + Math.min(interval, RETRY_BEACON_TICKS); // No radio yet: try again in a minute.
			}
		}
		retry(computer, state, gameTime);
	}

	/** Each message whose ack has not come sends again under a new id; after the retries it has FAILED. */
	private static void retry(RadioComputerBlockEntity computer, State state, long now) {
		long wait = RadiocraftServerConfig.APRS_MESSAGE_RETRY_SECONDS.get() * 20L;
		StationBus bus = null;
		boolean changed = false;
		for(int i = 0; i < state.messages.size(); i++) {
			Logged logged = state.messages.get(i);
			if(!logged.outgoing() || logged.delivery() != Delivery.SENDING && logged.delivery() != Delivery.RETRYING)
				continue;
			if(now - logged.lastSent() < wait && now >= logged.lastSent())
				continue;
			changed = true;
			if(logged.retries() >= RadiocraftServerConfig.APRS_MESSAGE_RETRIES.get()) {
				state.messages.set(i, new Logged(logged.packet(), true, logged.gameTime(), Delivery.FAILED, logged.retries(), logged.lastSent()));
				continue;
			}
			if(bus == null)
				bus = RadioStation.bus(computer);
			TncPacket again = logged.packet().withId(RadioStation.newId(computer.getLevel()));
			RadioStation.transmit(computer, bus, again);
			state.messages.set(i, new Logged(again, true, logged.gameTime(), Delivery.RETRYING, logged.retries() + 1, now));
		}
		if(changed) {
			computer.setChanged();
			computer.push(ComputerApps.APRS, "update", new CompoundTag());
		}
	}

	// -------------------- FOR THE SCREEN --------------------

	private static CompoundTag status(RadioComputerBlockEntity computer, State state) {
		long now = computer.getLevel() == null ? 0L : computer.getLevel().getGameTime();
		StationBus bus = RadioStation.bus(computer);
		CompoundTag out = RadioStation.status(bus);
		out.putString("comment", state.comment);
		out.putString("icon", state.icon.name());
		out.putBoolean("auto", state.auto);
		out.putInt("interval", RadiocraftServerConfig.TNC_BEACON_INTERVAL_SECONDS.get());
		out.putLong("lastBeacon", state.lastBeacon < 0 ? -1L : RadioStation.secondsSince(now, state.lastBeacon));
		BlockPos home = here(computer);
		ListTag stations = new ListTag();
		for(Heard heard : state.stations) {
			TncPacket packet = heard.packet();
			CompoundTag entry = new CompoundTag();
			entry.putString("call", packet.from());
			entry.putString("icon", packet.icon());
			entry.putString("comment", packet.text());
			entry.putInt("hops", packet.hops());
			entry.putLong("ago", RadioStation.secondsSince(now, heard.gameTime()));
			entry.putLong("first", RadioStation.secondsSince(now, heard.firstHeard()));
			BlockPos at = packet.position();
			entry.putBoolean("position", at != null);
			if(at != null) {
				entry.putInt("x", at.getX());
				entry.putInt("z", at.getZ());
				entry.putInt("distance", (int)Math.round(Math.hypot(at.getX() - home.getX(), at.getZ() - home.getZ())));
				entry.putInt("bearing", (int)Math.round(DirectionFinding.bearing(home.getX() + 0.5D, home.getZ() + 0.5D, at.getX() + 0.5D, at.getZ() + 0.5D)) % 360);
			}
			stations.add(entry);
		}
		out.put("stations", stations);
		ListTag messages = new ListTag();
		for(Logged logged : state.messages) {
			CompoundTag entry = new CompoundTag();
			entry.putString("from", logged.packet().from());
			entry.putString("to", logged.packet().to());
			entry.putString("text", logged.packet().messageBody());
			entry.putBoolean("out", logged.outgoing());
			entry.putString("delivery", logged.delivery().name());
			entry.putInt("retries", logged.retries());
			entry.putLong("ago", RadioStation.secondsSince(now, logged.gameTime()));
			entry.putBoolean("unread", !logged.outgoing() && logged.gameTime() > state.lastRead);
			messages.add(entry);
		}
		out.put("messages", messages);
		return out;
	}

	private static CompoundTag map(RadioComputerBlockEntity computer, State state) {
		CompoundTag out = new CompoundTag();
		if(!(computer.getLevel() instanceof ServerLevel level))
			return out;
		List<AprsMapPainter.Station> stations = new ArrayList<>();
		for(Heard heard : state.stations)
			if(heard.packet().position() != null)
				stations.add(new AprsMapPainter.Station(heard.packet().from(), heard.packet().position()));
		BlockPos home = here(computer);
		AprsMapPainter.Fit fit = AprsMapPainter.fit(home, stations.stream().map(AprsMapPainter.Station::pos).toList(), AprsMapPainter.SIZE, AprsMapPainter.SIZE);
		out.putByteArray("pixels", AprsMapPainter.paint(level, fit, home, stations));
		out.putInt("blocksPerPixel", fit.blocksPerPixel());
		return out;
	}
}
