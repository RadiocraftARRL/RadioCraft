package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.PacketBbs;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Packet BBS program's server side (computer apps 2): the computer as a packet mailbox on VHF. Like APRS it needs a
 * radio interface on the computer's station bus with a VHF radio on and a callsign; the mailbox answers APRS messages
 * addressed to the station's base callsign with the BBS's SSID ({@link PacketBbs#bbsCall}, W1BTR-1 by default), so the
 * H19, the APRS program, APRS handhelds and other computers all reach it. A numbered message is acked at once (and a
 * repeat of the same number only acked again); the answers go back as unnumbered messages. The messages live on the
 * computer's disk in {@link #FILE} (they follow the computer item like its other files); the SSID, the online switch,
 * the activity log and the terminal page live in the program's state (in the computer block's save). It also works
 * the other way: the terminal page talks to someone else's BBS from this computer.
 * <ul>
 * <li>{@code status}: the station ({@link RadioStation#status}), {@code bbs} (its callsign), {@code ssid},
 * {@code online}, {@code problem} (why it cannot send, "" if it can), {@code sysop}, {@code messages} (each
 * {@code n}, {@code bulletin}, {@code from}, {@code to}, {@code subject}, {@code read}, {@code ago} seconds),
 * {@code log} and {@code termLog} (strings) and {@code termTo}.</li>
 * <li>{@code read} {@code n}: {@code lines}. {@code post} {@code to}, {@code subject}, {@code body},
 * {@code bulletin}. {@code delete} {@code n} and {@code settings} {@code ssid}, {@code online}: sysop only
 * ({@code bbs_not_sysop}).</li>
 * <li>{@code term} {@code to}, {@code text}: sets the terminal's BBS and sends a line to it. {@code term_clear}.</li>
 * </ul>
 * Pushes {@code update} ({@code what}: {@code log} or {@code term}) to the screens at the computer.
 */
public final class BbsHandler implements ComputerAppHandler {

	/** The mailbox file on the computer's disk. */
	public static final String FILE = "BBS.DAT";
	/** Activity lines kept for the sysop, and terminal lines kept. */
	public static final int LOG_MAX = 50, TERM_MAX = 100;
	/** Message numbers remembered per caller, so a retried message is not done twice. */
	private static final int NUMBERS_KEPT = 16;
	private static final int CHECK_TICKS = 20;

	/** One computer's BBS. */
	public static final class State implements ComputerAppState {
		@Nullable
		PacketBbs bbs;
		int ssid = 1;
		/** Off until the sysop switches the mailbox on (maintainer, 2026-11-06). */
		boolean online = false;
		final List<String> log = new ArrayList<>();
		String termTo = "";
		final List<String> termLog = new ArrayList<>();
		final Map<String, ArrayDeque<String>> numbers = new HashMap<>();

		public List<String> log() {
			return List.copyOf(log);
		}

		public List<String> termLog() {
			return List.copyOf(termLog);
		}

		public boolean online() {
			return online;
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("ssid", ssid);
			tag.putBoolean("online", online);
			tag.putString("termTo", termTo);
			tag.put("log", strings(log));
			tag.put("termLog", strings(termLog));
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			ssid = tag.contains("ssid") ? Math.max(PacketBbs.SSID_MIN, Math.min(PacketBbs.SSID_MAX, tag.getInt("ssid"))) : 1;
			online = tag.getBoolean("online");
			termTo = TncPacket.sanitizeCallsign(tag.getString("termTo"));
			read(tag.getList("log", Tag.TAG_STRING), log);
			read(tag.getList("termLog", Tag.TAG_STRING), termLog);
		}

		private static ListTag strings(List<String> lines) {
			ListTag list = new ListTag();
			for(String line : lines)
				list.add(StringTag.valueOf(line));
			return list;
		}

		private static void read(ListTag list, List<String> into) {
			into.clear();
			for(int i = 0; i < list.size(); i++)
				into.add(list.getString(i));
		}
	}

	/** {@code computer}'s BBS state. */
	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.BBS, State::new);
	}

	/** {@code computer}'s mailbox, read from its disk the first time. */
	public static PacketBbs mailbox(RadioComputerBlockEntity computer) {
		State state = state(computer);
		if(state.bbs == null) {
			MinecraftServer server = computer.getLevel() == null ? null : computer.getLevel().getServer();
			state.bbs = PacketBbs.fromTag(server == null ? null : ComputerFileStore.of(server, computer.getComputerId()).readNbt(FILE));
		}
		return state.bbs;
	}

	/** The BBS's callsign on {@code computer}'s station, "" with no callsign yet. */
	public static String bbsCall(RadioComputerBlockEntity computer) {
		return PacketBbs.bbsCall(RadioStation.bus(computer).callsign(), state(computer).ssid);
	}

	/** Writes the mailbox to the disk if it changed; the problem if it could not (disk full). */
	@Nullable
	private static ComputerStorage.Problem persist(RadioComputerBlockEntity computer) {
		PacketBbs bbs = mailbox(computer);
		if(!bbs.takeModified() || computer.getLevel() == null || computer.getLevel().getServer() == null)
			return null;
		ComputerStorage.Problem problem = ComputerFileStore.of(computer.getLevel().getServer(), computer.getComputerId()).writeNbt(FILE, bbs.toTag());
		if(problem != null)
			log(computer, state(computer), "*** DISK: " + problem.name() + " - MAILBOX NOT SAVED");
		return problem;
	}

	private static void log(RadioComputerBlockEntity computer, State state, String line) {
		state.log.add(line);
		while(state.log.size() > LOG_MAX)
			state.log.removeFirst();
		computer.setChanged();
	}

	private static void push(RadioComputerBlockEntity computer, String what) {
		CompoundTag event = new CompoundTag();
		event.putString("what", what);
		computer.push(ComputerApps.BBS, "update", event);
	}

	// -------------------- REQUESTS --------------------

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State state = state(computer);
		PacketBbs bbs = mailbox(computer);
		switch(action) {
			case "status" -> reply.ok(status(context, state, bbs));
			case "read" -> {
				PacketBbs.Message message = bbs.find(data.getInt("n"));
				if(message == null) {
					reply.error("bbs_no_message");
					return;
				}
				CompoundTag out = new CompoundTag();
				ListTag lines = new ListTag();
				for(String line : PacketBbs.readLines(message))
					lines.add(StringTag.valueOf(line));
				out.put("lines", lines);
				reply.ok(out);
			}
			case "post" -> {
				boolean bulletin = data.getBoolean("bulletin");
				String to = TncPacket.sanitizeCallsign(data.getString("to"));
				if(to.isEmpty() && !bulletin) {
					reply.error("bbs_no_recipient");
					return;
				}
				String from = RadioStation.bus(computer).callsign();
				PacketBbs.Message saved = bbs.post(from.isEmpty() ? "SYSOP" : from, to, data.getString("subject"), data.getString("body"), bulletin, context.level().getGameTime());
				if(saved == null) {
					reply.error("bbs_full");
					return;
				}
				log(computer, state, "SYSOP POSTED MSG " + saved.number() + " TO " + saved.to());
				ComputerStorage.Problem problem = persist(computer);
				if(problem != null)
					reply.error(problem);
				else
					reply.ok();
			}
			case "delete" -> {
				if(!computer.mayChangePassword(context.player())) {
					reply.error("bbs_not_sysop");
					return;
				}
				if(!bbs.delete(data.getInt("n"))) {
					reply.error("bbs_no_message");
					return;
				}
				log(computer, state, "SYSOP KILLED MSG " + data.getInt("n"));
				ComputerStorage.Problem problem = persist(computer);
				if(problem != null)
					reply.error(problem);
				else
					reply.ok();
			}
			case "settings" -> {
				if(!computer.mayChangePassword(context.player())) {
					reply.error("bbs_not_sysop");
					return;
				}
				if(data.contains("ssid"))
					state.ssid = Math.max(PacketBbs.SSID_MIN, Math.min(PacketBbs.SSID_MAX, data.getInt("ssid")));
				if(data.contains("online"))
					state.online = data.getBoolean("online");
				computer.setChanged();
				reply.ok();
			}
			case "term" -> {
				String to = TncPacket.sanitizeCallsign(data.getString("to"));
				if(to.isEmpty()) {
					reply.error("bbs_no_recipient");
					return;
				}
				if(!to.equals(state.termTo)) {
					state.termTo = to;
					computer.setChanged();
				}
				String text = TncPacket.sanitizeText(data.getString("text"), TncPacket.MESSAGE_TEXT_MAX);
				if(text.isBlank()) {
					reply.ok();
					return;
				}
				StationBus bus = context.bus();
				String problem = AprsHandler.cannotSend(bus);
				if(problem == null && !RadioStation.transmit(computer, bus, TncPacket.message(RadioStation.newId(context.level()), bus.callsign(), to, text)))
					problem = "aprs_not_sent";
				if(problem != null) {
					reply.error(problem);
					return;
				}
				term(computer, state, "> " + text);
				reply.ok();
			}
			case "term_clear" -> {
				state.termLog.clear();
				computer.setChanged();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	private static void term(RadioComputerBlockEntity computer, State state, String line) {
		state.termLog.add(line);
		while(state.termLog.size() > TERM_MAX)
			state.termLog.removeFirst();
		computer.setChanged();
	}

	private static CompoundTag status(ComputerContext context, State state, PacketBbs bbs) {
		RadioComputerBlockEntity computer = context.computer();
		StationBus bus = context.bus();
		long now = context.level().getGameTime();
		CompoundTag out = RadioStation.status(bus);
		out.putString("bbs", PacketBbs.bbsCall(bus.callsign(), state.ssid));
		out.putInt("ssid", state.ssid);
		out.putBoolean("online", state.online);
		String problem = AprsHandler.cannotSend(bus);
		out.putString("problem", problem == null ? "" : problem);
		out.putBoolean("sysop", computer.mayChangePassword(context.player()));
		ListTag messages = new ListTag();
		List<PacketBbs.Message> all = bbs.messages();
		for(int i = all.size() - 1; i >= 0; i--) { // Newest first.
			PacketBbs.Message message = all.get(i);
			CompoundTag entry = new CompoundTag();
			entry.putInt("n", message.number());
			entry.putBoolean("bulletin", message.bulletin());
			entry.putString("from", message.from());
			entry.putString("to", message.to());
			entry.putString("subject", message.subject());
			entry.putBoolean("read", message.read());
			entry.putLong("ago", RadioStation.secondsSince(now, message.time()));
			messages.add(entry);
		}
		out.put("messages", messages);
		out.put("log", State.strings(state.log));
		out.put("termLog", State.strings(state.termLog));
		out.putString("termTo", state.termTo);
		return out;
	}

	// -------------------- ON THE AIR --------------------

	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return;
		if(packet.kind() == TncPacket.Kind.FILE) {
			card(computer, packet, level);
			return;
		}
		if(packet.kind() != TncPacket.Kind.MESSAGE)
			return;
		State state = state(computer);
		StationBus bus = RadioStation.bus(computer);
		String bbsCall = PacketBbs.bbsCall(bus.callsign(), state.ssid);
		if(state.online && !bbsCall.isEmpty() && packet.to().equals(bbsCall)) {
			serve(computer, state, bus, bbsCall, packet, level.getGameTime());
			return;
		}
		if(!state.termTo.isEmpty() && packet.from().equals(state.termTo) && !TncPacket.isForEveryone(packet.to()) && bus.isFor(packet.to())) {
			term(computer, state, packet.messageBody());
			push(computer, "term");
		}
	}

	/**
	 * A QSL card sent to the mailbox as a file (computer batch 3C): kept as a message to the station the card is for
	 * (its To), with the card attached, and the sender told its number.
	 */
	private static void card(RadioComputerBlockEntity computer, TncPacket packet, ServerLevel level) {
		com.arrl.radiocraft.common.qsl.QslCard card = com.arrl.radiocraft.common.qsl.QslCards.card(packet.item());
		State state = state(computer);
		StationBus bus = RadioStation.bus(computer);
		String bbsCall = PacketBbs.bbsCall(bus.callsign(), state.ssid);
		if(card == null || !state.online || bbsCall.isEmpty() || !packet.to().equals(bbsCall))
			return;
		PacketBbs bbs = mailbox(computer);
		String to = card.fill().to().isEmpty() ? "ALL" : card.fill().to();
		String about = "QSL card from " + card.from() + " to " + to + (card.fill().band().isEmpty() ? "" : " " + card.fill().band())
				+ (card.fill().mode().isEmpty() ? "" : " " + card.fill().mode());
		PacketBbs.Message saved = bbs.postCard(packet.from(), to, card.toTag(), about, level.getGameTime());
		String answer = saved == null ? "MAILBOX FULL - QSL CARD NOT SAVED" : "QSL CARD SAVED AS MSG " + saved.number() + " FOR " + to;
		log(computer, state, packet.from() + ": [QSL CARD] " + answer);
		RadioStation.transmit(computer, bus, TncPacket.message(RadioStation.newId(level), bbsCall, packet.from(), answer));
		persist(computer);
		push(computer, "log");
	}

	/** A caller's line for the mailbox: ack it if numbered, answer it once. */
	private static void serve(RadioComputerBlockEntity computer, State state, StationBus bus, String bbsCall, TncPacket packet, long now) {
		ServerLevel level = (ServerLevel)computer.getLevel();
		String from = packet.from();
		String number = packet.messageNumber();
		if(!number.isEmpty()) { // Ack every copy: the last ack may have been lost.
			RadioStation.transmit(computer, bus, TncPacket.ack(RadioStation.newId(level), bbsCall, from, number));
			ArrayDeque<String> seen = state.numbers.computeIfAbsent(from, call -> new ArrayDeque<>());
			if(seen.contains(number))
				return;
			seen.addLast(number);
			while(seen.size() > NUMBERS_KEPT)
				seen.removeFirst();
		}
		PacketBbs bbs = mailbox(computer);
		String text = packet.messageBody();
		log(computer, state, from + ": " + text);
		for(String line : bbs.handle(bbsCall, from, text, now)) {
			RadioStation.transmit(computer, bus, TncPacket.message(RadioStation.newId(level), bbsCall, from, line));
			log(computer, state, "  " + line);
		}
		for(PacketBbs.Download download : bbs.takeDownloads()) { // D n: the attached QSL card goes to the caller as a file.
			com.arrl.radiocraft.common.qsl.QslCard card = com.arrl.radiocraft.common.qsl.QslCard.fromTag(download.message().attachment());
			RadioStation.transmit(computer, bus, TncPacket.file(RadioStation.newId(level), bbsCall, download.to(), com.arrl.radiocraft.common.qsl.QslCards.stack(card, 1)));
		}
		persist(computer);
		push(computer, "log");
	}

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if((gameTime + computer.getBlockPos().asLong()) % CHECK_TICKS != 0)
			return;
		State state = state(computer);
		if(state.bbs == null)
			return; // Nobody has called since it loaded: no sessions to time out.
		for(String call : state.bbs.expire(gameTime)) {
			state.numbers.keySet().removeIf(from -> PacketBbs.base(from).equals(call));
			log(computer, state, "*** " + call + " TIMED OUT");
		}
	}
}
