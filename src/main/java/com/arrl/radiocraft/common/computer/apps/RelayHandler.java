package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.computer.ComputerReply;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * LAN RELAY (2026-11-13, doc/Plan-Computer4.md): a chat room for the computers of a LAN. A computer set as a relay
 * server keeps the room: its history (the last {@link #HISTORY} lines, saved with the computer) and who is in it. It
 * needs a SETUP name and SETUP's background services on (RELAY is a background service), and it serves while it is
 * powered. With more than one server on the LAN the player picks which room to enter. Each player's username is
 * remembered on the computer they use.
 * <ul>
 * <li>{@code status} ({@code server}: the room wanted, "" for the only one): {@code me} (this player's username here),
 * {@code serving}, {@code services} and {@code named} (this computer), {@code servers} (the LAN's rooms, by computer name),
 * {@code room} (the room shown, "" for none), {@code lines} and {@code online}.</li>
 * <li>{@code name} ({@code name}): sets this player's username on this computer.</li>
 * <li>{@code serve} ({@code on}): makes this computer a relay server, or stops it (its owner, anyone if none, or an op).</li>
 * <li>{@code say} ({@code server}, {@code text}): a line into a room.</li>
 * <li>{@code clear}: empties this computer's room history (as for {@code serve}).</li>
 * </ul>
 */
public final class RelayHandler implements ComputerAppHandler {

	/** Lines a server keeps. */
	public static final int HISTORY = 200;
	/** Lines sent to the screen. */
	public static final int SHOWN = 100;
	public static final int MAX_TEXT = 200, MAX_NAME = 16;
	/** A user is online while their screen asked within this many ticks. */
	public static final long ONLINE_TICKS = 100L;

	/** One line in a room. */
	public record Line(String clock, String user, String computer, String text) {}

	/** What RELAY keeps on one computer: usernames, whether it serves, and (serving) its room. */
	public static final class State implements ComputerAppState {
		final Map<UUID, String> names = new HashMap<>();
		boolean serving = false;
		final Deque<Line> lines = new ArrayDeque<>();
		/** Who is in the room: username to the game time they were last seen and where. Not saved. */
		final Map<String, long[]> seen = new LinkedHashMap<>();
		final Map<String, String> seenAt = new HashMap<>();

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag n = new CompoundTag();
			names.forEach((id, name) -> n.putString(id.toString(), name));
			tag.put("names", n);
			tag.putBoolean("serving", serving);
			ListTag list = new ListTag();
			for(Line line : lines) {
				CompoundTag t = new CompoundTag();
				t.putString("c", line.clock());
				t.putString("u", line.user());
				t.putString("p", line.computer());
				t.putString("t", line.text());
				list.add(t);
			}
			tag.put("lines", list);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag n = tag.getCompound("names");
			for(String key : n.getAllKeys()) {
				try {
					names.put(UUID.fromString(key), n.getString(key));
				}
				catch(IllegalArgumentException ignored) {}
			}
			serving = tag.getBoolean("serving");
			ListTag list = tag.getList("lines", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompound(i);
				lines.add(new Line(t.getString("c"), t.getString("u"), t.getString("p"), t.getString("t")));
			}
		}

		void add(Line line) {
			lines.addLast(line);
			while(lines.size() > HISTORY)
				lines.removeFirst();
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.RELAY, State::new);
	}

	/** True when {@code computer} keeps a room now: set to serve, named, its background services on and powered. */
	public static boolean serves(RadioComputerBlockEntity computer) {
		return state(computer).serving && !ComputerNetwork.name(computer).isEmpty() && computer.backgroundServicesOn() && computer.isPowered();
	}

	/** The LAN's rooms as this computer sees them: itself and every computer it reaches that serves, by name. */
	public static Map<String, RadioComputerBlockEntity> servers(RadioComputerBlockEntity computer) {
		Map<String, RadioComputerBlockEntity> out = new LinkedHashMap<>();
		if(serves(computer))
			out.put(ComputerNetwork.name(computer), computer);
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(computer))
			if(peer.link().up() && serves(peer.computer()))
				out.putIfAbsent(ComputerNetwork.name(peer.computer()), peer.computer());
		return out;
	}

	@Nullable
	private static RadioComputerBlockEntity room(Map<String, RadioComputerBlockEntity> servers, String wanted) {
		if(wanted.isEmpty())
			return servers.size() == 1 ? servers.values().iterator().next() : null;
		for(Map.Entry<String, RadioComputerBlockEntity> e : servers.entrySet())
			if(ComputerNetwork.sameName(e.getKey(), wanted))
				return e.getValue();
		return null;
	}

	private static boolean mayRun(ComputerContext context) {
		UUID owner = context.files().owner();
		return owner == null || owner.equals(context.player().getUUID()) || context.player().hasPermissions(2);
	}

	/** True for a username: 1 to 16 letters, digits, '-' or '_'. */
	public static boolean validName(String name) {
		return name.length() >= 1 && name.length() <= MAX_NAME && name.chars().allMatch(c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_');
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State mine = state(computer);
		switch(action) {
			case "status" -> reply.ok(status(context, data.getString("server")));
			case "name" -> {
				String name = data.getString("name").trim();
				if(!validName(name)) {
					reply.error("relay_bad_name");
					return;
				}
				mine.names.put(context.player().getUUID(), name);
				computer.setChanged();
				reply.ok();
			}
			case "serve" -> {
				if(!mayRun(context)) {
					reply.error("relay_not_owner");
					return;
				}
				boolean on = data.getBoolean("on");
				if(on && ComputerNetwork.name(computer).isEmpty()) {
					reply.error("relay_needs_name");
					return;
				}
				mine.serving = on;
				computer.setChanged();
				reply.ok();
			}
			case "clear" -> {
				if(!mayRun(context)) {
					reply.error("relay_not_owner");
					return;
				}
				mine.lines.clear();
				computer.setChanged();
				reply.ok();
			}
			case "say" -> {
				String me = mine.names.getOrDefault(context.player().getUUID(), "");
				if(me.isEmpty()) {
					reply.error("relay_no_name");
					return;
				}
				RadioComputerBlockEntity server = room(servers(computer), data.getString("server"));
				if(server == null) {
					reply.error("relay_no_server");
					return;
				}
				String text = data.getString("text").trim();
				if(text.isEmpty()) {
					reply.ok();
					return;
				}
				if(text.length() > MAX_TEXT)
					text = text.substring(0, MAX_TEXT);
				StringBuilder clean = new StringBuilder();
				for(char c : text.toCharArray())
					clean.append(c >= 32 && c != 127 ? c : ' ');
				state(server).add(new Line(clock(context.level().getDayTime()), me, ComputerNetwork.name(computer), clean.toString()));
				server.setChanged();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** "HH:MM", Minecraft time (0 = 06:00). */
	public static String clock(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		return String.format(Locale.ROOT, "%02d:%02d", (t / 1000L + 6L) % 24L, (t % 1000L) * 60L / 1000L);
	}

	private static CompoundTag status(ComputerContext context, String wanted) {
		RadioComputerBlockEntity computer = context.computer();
		State mine = state(computer);
		CompoundTag out = new CompoundTag();
		String me = mine.names.getOrDefault(context.player().getUUID(), "");
		out.putString("me", me);
		out.putBoolean("serving", mine.serving);
		out.putBoolean("services", computer.backgroundServicesOn());
		out.putBoolean("named", !ComputerNetwork.name(computer).isEmpty());
		Map<String, RadioComputerBlockEntity> servers = servers(computer);
		ListTag names = new ListTag();
		servers.keySet().forEach(n -> names.add(StringTag.valueOf(n)));
		out.put("servers", names);
		RadioComputerBlockEntity server = room(servers, wanted);
		out.putString("room", server == null ? "" : ComputerNetwork.name(server));
		ListTag lines = new ListTag();
		ListTag online = new ListTag();
		if(server != null) {
			State room = state(server);
			long now = context.level().getGameTime();
			if(!me.isEmpty()) {
				room.seen.put(me, new long[] { now });
				room.seenAt.put(me, ComputerNetwork.name(computer));
			}
			for(Iterator<Map.Entry<String, long[]>> it = room.seen.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, long[]> e = it.next();
				if(now - e.getValue()[0] > ONLINE_TICKS || now < e.getValue()[0]) {
					it.remove();
					room.seenAt.remove(e.getKey());
				}
				else
					online.add(StringTag.valueOf(e.getKey() + "@" + room.seenAt.getOrDefault(e.getKey(), "")));
			}
			List<Line> all = new ArrayList<>(room.lines);
			for(Line line : all.subList(Math.max(0, all.size() - SHOWN), all.size())) {
				CompoundTag t = new CompoundTag();
				t.putString("clock", line.clock());
				t.putString("user", line.user());
				t.putString("computer", line.computer());
				t.putString("text", line.text());
				lines.add(t);
			}
		}
		out.put("lines", lines);
		out.put("online", online);
		return out;
	}

	/** Keeps a serving computer on (RELAY is a background service, so only with SETUP's switch on). */
	@Override
	public boolean ticks() {
		return true;
	}

	/** Only a computer set to serve stays on for RELAY. */
	@Override
	public boolean keepsOn(RadioComputerBlockEntity computer) {
		return state(computer).serving;
	}
}
