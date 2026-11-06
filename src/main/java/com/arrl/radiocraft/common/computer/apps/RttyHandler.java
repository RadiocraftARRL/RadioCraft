package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.TeletypeBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.RttyCopy;
import com.arrl.radiocraft.common.radio.Callsigns;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.voice.Rtty;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * The RTTY program's server side (Computer apps 2): live radioteletype through the radio interfaces on the computer's
 * station bus, as the Teletype Machine and the H19 send it: 45.45 baud Baudot ({@link Rtty}), one character at a time
 * every {@link TeletypeBlockEntity#SECONDS_PER_CHAR} (about six a second), each character a {@link TncPacket#rtty}
 * packet, so every RTTY station in the mod copies the computer and the computer copies them. What is typed waits in a
 * type-ahead buffer and goes out at that pace; a weak or noisy signal prints garbled (the radio's receive side does
 * that, {@code RadioBlockEntity#receiveData}), and half duplex: nothing prints while we are sending. The receive window
 * ({@link RttyCopy}), the macros and the other station's call are kept per computer in its save.
 * <ul>
 * <li>{@code status}: the station ({@link RadioStation#status}), {@code problem} (why it cannot send, or ""),
 * {@code lines} (each {@code from}, {@code text}, {@code own}), {@code pending} (typed, not sent yet), {@code macros}
 * (three strings), {@code call} (the other station), {@code myCall}.</li>
 * <li>{@code type} {@code text}: adds to the buffer (error if the station cannot send). {@code macro} {@code n}: the
 * macro, expanded. {@code unsend}: takes back the last character not sent yet. {@code abort}: drops what has not gone out. {@code clear}: clears the receive window.
 * {@code settings} {@code macros}, {@code call}. {@code save}: writes the window to {@code RTTY0001.TXT} (the next free
 * number), answers {@code name}.</li>
 * </ul>
 * Pushes {@code rx} ({@code from}, {@code text}, {@code own}) for each character printed, and {@code sent}
 * ({@code pending}) as the buffer drains, to every player at the computer.
 */
public final class RttyHandler implements ComputerAppHandler {

	/** Characters the type-ahead buffer holds (a text file sent is cut to this). */
	public static final int TYPE_AHEAD = 4000;
	/** Ticks after our last character before we print what we hear again (the transmitter's tail, half duplex). */
	public static final int RX_BLOCKED_TICKS = 20;

	/** One computer's RTTY station. */
	public static final class State implements ComputerAppState {
		final RttyCopy copy = new RttyCopy();
		final StringBuilder pending = new StringBuilder();
		final String[] macros = RttyCopy.DEFAULT_MACROS.toArray(new String[0]);
		String call = "";
		long lastSent = -1000L;
		@Nullable UUID typist;
		String typistName = "";
		final StringBuilder sentLine = new StringBuilder(); // For the moderators: the line being sent.
		float sentHz = 0.0F;

		public RttyCopy copy() {
			return copy;
		}

		public String pending() {
			return pending.toString();
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.put("lines", copy.toTag());
			ListTag list = new ListTag();
			for(String macro : macros)
				list.add(StringTag.valueOf(macro));
			tag.put("macros", list);
			tag.putString("call", call);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			copy.load(tag.getList("lines", Tag.TAG_COMPOUND));
			ListTag list = tag.getList("macros", Tag.TAG_STRING);
			for(int i = 0; i < macros.length && i < list.size(); i++)
				macros[i] = cleanMacro(list.getString(i));
			call = TncPacket.sanitizeCallsign(tag.getString("call"));
		}
	}

	/** {@code computer}'s RTTY station. */
	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.RTTY, State::new);
	}

	private static String cleanMacro(String macro) {
		return macro.length() > RttyCopy.MACRO_MAX ? macro.substring(0, RttyCopy.MACRO_MAX) : macro;
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State state = state(computer);
		switch(action) {
			case "status" -> reply.ok(status(computer, state));
			case "type" -> answer(reply, type(computer, state, data.getString("text"), context));
			case "macro" -> {
				int n = data.getInt("n");
				if(n < 0 || n >= state.macros.length) {
					reply.error("unknown_request", action);
					return;
				}
				String text = RttyCopy.expand(state.macros[n], RadioStation.bus(computer).callsign(), state.call);
				answer(reply, type(computer, state, text, context));
			}
			case "unsend" -> { // Backspace: the last character typed, if it has not gone out yet.
				if(!state.pending.isEmpty())
					state.pending.setLength(state.pending.length() - 1);
				reply.ok(pendingTag(state));
			}
			case "abort" -> {
				state.pending.setLength(0);
				reply.ok(pendingTag(state));
			}
			case "clear" -> {
				state.copy.clear();
				computer.setChanged();
				reply.ok();
			}
			case "settings" -> {
				if(data.contains("macros")) {
					ListTag list = data.getList("macros", Tag.TAG_STRING);
					for(int i = 0; i < state.macros.length && i < list.size(); i++)
						state.macros[i] = cleanMacro(list.getString(i));
				}
				if(data.contains("call"))
					state.call = TncPacket.sanitizeCallsign(data.getString("call"));
				computer.setChanged();
				reply.ok();
			}
			case "save" -> {
				if(state.copy.isEmpty()) {
					reply.error("rtty_nothing_to_save");
					return;
				}
				String name = ComputerStorage.nextName("RTTY", ComputerFileFormats.TEXT, context.files().storage()::exists);
				if(name == null) {
					reply.error("too_many_files");
					return;
				}
				ComputerStorage.Problem problem = context.files().writeText(name, state.copy.text());
				if(problem != null) {
					reply.error(problem.key());
					return;
				}
				CompoundTag out = new CompoundTag();
				out.putString("name", name);
				reply.ok(out);
			}
			default -> reply.error("unknown_request", action);
		}
	}

	private static void answer(ComputerReply reply, @Nullable String problem) {
		if(problem == null)
			reply.ok();
		else
			reply.error(problem);
	}

	private static CompoundTag pendingTag(State state) {
		CompoundTag out = new CompoundTag();
		out.putString("pending", state.pending.toString());
		return out;
	}

	/** Puts {@code text} in the buffer; null if it will go, else the error key. */
	@Nullable
	static String type(RadioComputerBlockEntity computer, State state, String text, @Nullable ComputerContext context) {
		String problem = RadioStation.cannotSend(RadioStation.bus(computer));
		if(problem != null)
			return problem;
		String clean = Rtty.sanitize(text, TYPE_AHEAD);
		if(clean.isEmpty())
			return "rtty_nothing_to_send";
		int room = TYPE_AHEAD - state.pending.length();
		if(room <= 0)
			return "rtty_buffer_full";
		state.pending.append(clean, 0, Math.min(room, clean.length()));
		if(context != null) {
			state.typist = context.player().getUUID();
			state.typistName = context.player().getGameProfile().getName();
		}
		return null;
	}

	/** Test hook and API: types {@code text} at {@code computer} as nobody in particular; null if it will go, else the error key. */
	@Nullable
	public static String type(RadioComputerBlockEntity computer, String text) {
		return type(computer, state(computer), text, null);
	}

	// -------------------- SENDING --------------------

	@Override
	public boolean ticks() {
		return true;
	}

	private static long charSlot(long gameTime) {
		return (long)Math.floor(gameTime / 20.0D / TeletypeBlockEntity.SECONDS_PER_CHAR);
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if(charSlot(gameTime) == charSlot(gameTime - 1))
			return;
		ComputerAppState existing = computer.existingState(ComputerApps.RTTY);
		if(!(existing instanceof State state) || state.pending.isEmpty())
			return;
		sendNext(computer, state, gameTime);
	}

	/** Sends the next character, prints our local copy, and tells the moderators each line as it ends. */
	private static void sendNext(RadioComputerBlockEntity computer, State state, long now) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return;
		StationBus bus = RadioStation.bus(computer);
		String c = state.pending.substring(0, 1);
		state.pending.deleteCharAt(0);
		TncPacket packet = TncPacket.rtty(RadioStation.newId(level), bus.callsign(), c);
		if(RadioStation.cannotSend(bus) != null || !RadioStation.transmit(computer, bus, packet)) { // The radio went off: the rest is lost.
			state.pending.setLength(0);
			lineSent(level, state);
			computer.push(ComputerApps.RTTY, "sent", pendingTag(state));
			return;
		}
		state.lastSent = now;
		RadioBlockEntity radio = RadioStation.firstOn(RadioStation.radios(bus));
		if(radio != null)
			state.sentHz = radio.getFrequency();
		print(computer, state, bus.callsign(), c, true);
		if(c.charAt(0) == '\n' || state.sentLine.length() >= RttyCopy.LINE_MAX)
			lineSent(level, state);
		if(c.charAt(0) != '\n')
			state.sentLine.append(c);
		if(state.pending.isEmpty())
			lineSent(level, state); // A pause in the typing: what there is so far.
		computer.push(ComputerApps.RTTY, "sent", pendingTag(state));
	}

	private static void lineSent(ServerLevel level, State state) {
		if(state.sentLine.isEmpty())
			return;
		com.arrl.radiocraft.common.moderation.ModerationHooks.rttyTypist(level, state.sentHz, state.typist); // Names the typist on the logged RTTY.
		com.arrl.radiocraft.common.radio.voice.RadioModeration.rttyLine(level, state.sentHz, state.typist, state.typistName,
				state.typist == null ? "" : Objects.requireNonNullElse(Callsigns.playerCallsign(level.getServer(), state.typist), ""), state.sentLine.toString());
		state.sentLine.setLength(0);
	}

	// -------------------- RECEIVING --------------------

	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(packet.kind() != TncPacket.Kind.RTTY || computer.getLevel() == null)
			return;
		State state = state(computer);
		long now = computer.getLevel().getGameTime();
		if(!state.pending.isEmpty() || now - state.lastSent < RX_BLOCKED_TICKS)
			return; // Half duplex: our own transmitter is up.
		print(computer, state, packet.from(), packet.text(), false);
		if(computer.getLevel() instanceof net.minecraft.server.level.ServerLevel server) // Typewriter Thunder: RTTY copied here.
			com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grantStation(server, computer.getBlockPos(), computer.files().owner(), com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.TYPEWRITER_THUNDER);
	}

	private static void print(RadioComputerBlockEntity computer, State state, String from, String text, boolean own) {
		state.copy.print(from, text, own);
		computer.setChanged();
		CompoundTag event = new CompoundTag();
		event.putString("from", from);
		event.putString("text", text);
		event.putBoolean("own", own);
		computer.push(ComputerApps.RTTY, "rx", event);
	}

	// -------------------- FOR THE SCREEN --------------------

	private static CompoundTag status(RadioComputerBlockEntity computer, State state) {
		StationBus bus = RadioStation.bus(computer);
		CompoundTag out = RadioStation.status(bus);
		String problem = RadioStation.cannotSend(bus);
		out.putString("problem", problem == null ? "" : problem);
		out.putString("myCall", bus.callsign());
		ListTag lines = state.copy.toTag();
		out.put("lines", lines);
		out.putString("pending", state.pending.toString());
		ListTag macros = new ListTag();
		for(String macro : state.macros)
			macros.add(StringTag.valueOf(macro));
		out.put("macros", macros);
		out.putString("call", state.call);
		return out;
	}
}
