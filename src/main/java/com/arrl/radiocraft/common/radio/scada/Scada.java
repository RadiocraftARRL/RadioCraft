package com.arrl.radiocraft.common.radio.scada;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshPacket;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.mesh.MeshSystem;
import com.arrl.radiocraft.common.radio.mesh.RadioMesh;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SCADA (2026-11-10): RTUs that report their redstone faces to a master over the radio mesh, and a master that runs
 * rules on what they read and drives RTU faces (and its own) in return. Kept in the overworld's {@code radiocraft_scada}
 * data so it runs whatever is loaded: an RTU's faces in an unloaded chunk cannot change, so its last reading stands,
 * and outputs set while it is unloaded are there when it loads.
 * <p>
 * The master polls one RTU at a time, every 1 to 3 seconds as the master is set ({@link Master#pollSeconds}): the POLL carries the
 * outputs the rules want from that RTU, and the RTU's REPLY carries its IN faces. A cycle through n RTUs takes n polls,
 * so this is wireless redstone with a few seconds' delay that range, relaying and airtime all cost. Three polls
 * unanswered in a row is COMM FAIL: that RTU's points are unknown and no rule fires on them.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class Scada extends SavedData {

	public static final String SAVE_NAME = "radiocraft_scada";
	public static final String POLL = "POLL", REPLY = "REPLY";
	/** Missed polls in a row before an RTU is COMM FAIL. */
	public static final int COMM_FAIL_MISSES = 3;
	/** Ticks an RTU waits before it answers a poll. */
	public static final int REPLY_DELAY_TICKS = 3;

	public enum FaceMode {
		OFF, IN, OUT;

		public FaceMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/**
	 * A face-by-face history of levels for the SCADA program's trends (2026-11-13): the last {@link #SIZE} readings,
	 * each with its game time; -1 where a face was not an input.
	 */
	public static final class Trend {
		public static final int SIZE = 120;
		private final long[] times = new long[SIZE];
		private final byte[][] levels = new byte[SIZE][6];
		private int count = 0, next = 0;

		public void add(long time, int[] values) {
			times[next] = time;
			for(int i = 0; i < 6; i++)
				levels[next][i] = (byte)(i < values.length ? Math.max(-1, Math.min(15, values[i])) : -1);
			next = (next + 1) % SIZE;
			count = Math.min(SIZE, count + 1);
		}

		public int size() {
			return count;
		}

		/** The {@code i}th reading, oldest first. */
		public long time(int i) {
			return times[(next - count + i + SIZE * 2) % SIZE];
		}

		public int level(int i, int face) {
			return levels[(next - count + i + SIZE * 2) % SIZE][face];
		}

		/** One face's readings, oldest first, as hex digits ('-' for none). */
		public String face(int face) {
			StringBuilder out = new StringBuilder(count);
			for(int i = 0; i < count; i++) {
				int v = level(i, face);
				out.append(v < 0 ? '-' : Character.toUpperCase(Character.forDigit(v, 16)));
			}
			return out.toString();
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			long[] t = new long[count];
			byte[] l = new byte[count * 6];
			for(int i = 0; i < count; i++) {
				t[i] = time(i);
				for(int f = 0; f < 6; f++)
					l[i * 6 + f] = (byte)level(i, f);
			}
			tag.putLongArray("t", t);
			tag.putByteArray("l", l);
			return tag;
		}

		void load(CompoundTag tag) {
			long[] t = tag.getLongArray("t");
			byte[] l = tag.getByteArray("l");
			int[] values = new int[6];
			for(int i = 0; i < t.length && (i + 1) * 6 <= l.length; i++) {
				for(int f = 0; f < 6; f++)
					values[f] = l[i * 6 + f];
				add(t[i], values);
			}
		}
	}

	/** An RTU's faces: what each is set to, what it last read, and what the master last told it to put out. */
	public static final class Rtu {
		public final FaceMode[] modes = new FaceMode[6];
		public final int[] inputs = new int[6];
		public final int[] outputs = new int[6];
		/** On a powered power wire (2026-11-13); kept while it is unloaded. An unpowered RTU does not answer. */
		public boolean powered = true;

		Rtu() {
			Arrays.fill(modes, FaceMode.OFF);
		}

		/** Its IN faces' levels for a reply, -1 for the rest. */
		int[] reported() {
			int[] out = new int[6];
			for(int i = 0; i < 6; i++)
				out[i] = modes[i] == FaceMode.IN ? inputs[i] : -1;
			return out;
		}
	}

	/** One RTU as the master sees it. */
	public static final class Seen {
		public int[] inputs = {-1, -1, -1, -1, -1, -1};
		public long lastReply = -1L;
		public int misses = 0;
		public boolean commFail = false;
		public int polls = 0, replies = 0;
		/** Its readings over time, for trends (2026-11-13). */
		public final Trend trend = new Trend();
	}

	/** A master: its rules, poll speed and own faces, and what it knows of its RTUs. */
	public static final class Master {
		public final List<String> ruleText = new ArrayList<>();
		public List<ScadaRules.Rule> rules = List.of();
		public List<String> ruleErrors = List.of();
		/** Seconds a poll of one RTU takes, 1 to 3: set on the master's panel, or by a POLL line. */
		public int pollSeconds = ScadaRules.DEFAULT_POLL;
		public final FaceMode[] modes = new FaceMode[6];
		public final int[] inputs = new int[6];
		public final int[] outputs = new int[6];
		public final Map<Integer, Seen> rtus = new TreeMap<>();
		/** On a powered power wire (2026-11-13); kept while it is unloaded. An unpowered master does not poll. */
		public boolean powered = true;
		/** Its own IN faces over time, for trends (unit 0). */
		public final Trend trend = new Trend();
		/** What the rules last wanted: a point to its level. */
		public Map<ScadaRules.Point, Integer> wanted = Map.of();
		int seq = 0;
		int pollIndex = 0;
		int pendingRtu = -1, pendingSeq = -1;
		long pendingAt = 0L, nextPollAt = 0L;

		Master() {
			Arrays.fill(modes, FaceMode.OFF);
		}

		/** True while a poll waits for its RTU's reply (the console's POLL lamp). */
		public boolean polling() {
			return pendingRtu >= 0;
		}

		/** The game time the poll now waiting went out. */
		public long pollStartedAt() {
			return pendingAt;
		}

		/** Sets the rule book: rules, and the poll speed if the book has a POLL line. */
		public ScadaRules.Parsed setRules(List<String> lines) {
			ruleText.clear();
			ruleText.addAll(lines);
			ScadaRules.Parsed parsed = ScadaRules.parse(lines);
			rules = parsed.rules();
			ruleErrors = parsed.errors();
			if(parsed.pollSeconds() > 0)
				pollSeconds = parsed.pollSeconds();
			return parsed;
		}

		/** What {@code point} reads, -1 if unknown. */
		public int read(ScadaRules.Point point) {
			if(point.unit() == 0)
				return modes[point.face()] == FaceMode.IN ? inputs[point.face()] : -1;
			Seen seen = rtus.get(point.unit());
			return seen == null || seen.commFail ? -1 : seen.inputs[point.face()];
		}

		/** Runs the rules on what is known: sets {@link #wanted} and its own OUT faces. */
		void evaluate() {
			wanted = ScadaRules.evaluate(rules, this::read);
			for(int i = 0; i < 6; i++)
				outputs[i] = wanted.getOrDefault(new ScadaRules.Point(0, i), 0);
		}

		/** The six levels to send RTU {@code unit} in a poll. */
		int[] outputsFor(int unit) {
			int[] out = new int[6];
			for(int i = 0; i < 6; i++)
				out[i] = wanted.getOrDefault(new ScadaRules.Point(unit, i), 0);
			return out;
		}
	}

	private final Map<GlobalPos, Rtu> rtus = new LinkedHashMap<>();
	private final Map<GlobalPos, Master> masters = new LinkedHashMap<>();

	public static SavedData.Factory<Scada> factory() {
		return new SavedData.Factory<>(Scada::new, Scada::load, null);
	}

	public static Scada of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	/** Ticks a master spends on each RTU's poll. */
	public static int pollTicks(Master master) {
		return 20 * Math.max(ScadaRules.MIN_POLL, Math.min(ScadaRules.MAX_POLL, master.pollSeconds));
	}

	/** RTUs a poll or reply may be relayed through: the mesh's hop limit, as the fire alarms have it. */
	public static int hopLimit() {
		return RadiocraftServerConfig.MESH_HOP_LIMIT.get();
	}

	public Rtu rtu(GlobalPos pos) {
		return rtus.computeIfAbsent(pos, p -> {
			setDirty();
			return new Rtu();
		});
	}

	public Master master(GlobalPos pos) {
		return masters.computeIfAbsent(pos, p -> {
			setDirty();
			return new Master();
		});
	}

	@Nullable
	public Master masterIfAny(GlobalPos pos) {
		return masters.get(pos);
	}

	public void removed(MinecraftServer server, GlobalPos pos) {
		RadioMesh.of(server).remove(pos);
		if(rtus.remove(pos) != null | masters.remove(pos) != null)
			setDirty();
	}

	/** Something changed that should be saved (a face's mode, a reading). */
	public void changed() {
		setDirty();
	}

	// -------------------- ON THE AIR --------------------

	/** What a SCADA device does with a burst it hears ({@link RadioMesh.Listener}). */
	public static boolean heard(MinecraftServer server, MeshNode at, MeshPacket packet, long now) {
		Scada scada = of(server);
		if(at.role() == MeshRole.RTU) {
			if(!packet.type().equals(POLL) || packet.to() != at.address())
				return false;
			Rtu rtu = scada.rtu(at.pos());
			if(!rtu.powered)
				return true; // No power: it hears nothing and says nothing.
			int[] levels = ScadaRules.decodeLevels(packet.field(1));
			for(int i = 0; i < 6; i++)
				rtu.outputs[i] = Math.max(0, levels[i]);
			scada.setDirty();
			RadioMesh mesh = RadioMesh.of(server);
			MeshPacket reply = new MeshPacket(mesh.newId(), MeshSystem.SCADA, REPLY, at.address(), 0,
					packet.field(0) + "|" + ScadaRules.encodeLevels(rtu.reported()), 0, packet.hopLimit());
			mesh.sendLater(server, at.pos(), reply, REPLY_DELAY_TICKS);
			return true;
		}
		if(at.role() != MeshRole.MASTER || !packet.type().equals(REPLY))
			return true;
		Master master = scada.master(at.pos());
		if(packet.from() != master.pendingRtu || packet.intField(0, -2) != master.pendingSeq)
			return true; // Late, or for another master.
		Seen seen = master.rtus.computeIfAbsent(packet.from(), k -> new Seen());
		seen.inputs = ScadaRules.decodeLevels(packet.field(1));
		seen.lastReply = now;
		seen.misses = 0;
		seen.commFail = false;
		seen.replies++;
		seen.trend.add(now, seen.inputs);
		master.pendingRtu = -1;
		master.evaluate();
		scada.setDirty();
		return true;
	}

	private void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		RadioMesh mesh = RadioMesh.of(server);
		for(Map.Entry<GlobalPos, Master> entry : masters.entrySet()) {
			MeshNode node = mesh.get(entry.getKey());
			if(node == null || node.role() != MeshRole.MASTER)
				continue;
			Master master = entry.getValue();
			if(!master.powered)
				continue; // No power: no polls (its RTUs go COMM FAIL in the program's eyes once it is back).
			int poll = pollTicks(master);
			if(master.pendingRtu >= 0 && now - master.pendingAt >= poll) { // No answer.
				Seen seen = master.rtus.computeIfAbsent(master.pendingRtu, k -> new Seen());
				seen.misses++;
				if(seen.misses >= COMM_FAIL_MISSES && !seen.commFail) {
					seen.commFail = true;
					master.evaluate();
				}
				master.pendingRtu = -1;
				setDirty();
			}
			if(master.pendingRtu >= 0 || now < master.nextPollAt)
				continue;
			List<MeshNode> units = new ArrayList<>(mesh.on(MeshRole.RTU, node.hz()));
			master.nextPollAt = now + poll;
			if(units.isEmpty())
				continue;
			units.sort(Comparator.comparingInt(MeshNode::address));
			master.pollIndex = Math.floorMod(master.pollIndex, units.size());
			MeshNode unit = units.get(master.pollIndex);
			master.pollIndex = (master.pollIndex + 1) % units.size();
			master.evaluate(); // Its own inputs may have changed.
			int[] own = new int[6];
			for(int i = 0; i < 6; i++)
				own[i] = master.modes[i] == FaceMode.IN ? master.inputs[i] : -1;
			master.trend.add(now, own);
			master.seq = (master.seq + 1) & 0xFFFF;
			master.pendingRtu = unit.address();
			master.pendingSeq = master.seq;
			master.pendingAt = now;
			master.rtus.computeIfAbsent(unit.address(), k -> new Seen()).polls++;
			mesh.send(server, node.pos(), new MeshPacket(mesh.newId(), MeshSystem.SCADA, POLL, 0, unit.address(),
					master.seq + "|" + ScadaRules.encodeLevels(master.outputsFor(unit.address())), 0, hopLimit()));
			setDirty();
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		of(event.getServer()).tick(event.getServer());
	}

	// -------------------- SAVE --------------------

	private static void putModes(CompoundTag tag, FaceMode[] modes) {
		StringBuilder s = new StringBuilder();
		for(FaceMode mode : modes)
			s.append(mode.name().charAt(1)); // F (OFF), N (IN), U (OUT).
		tag.putString("modes", s.toString());
	}

	private static void getModes(CompoundTag tag, FaceMode[] modes) {
		String s = tag.getString("modes");
		for(int i = 0; i < 6; i++) {
			char c = i < s.length() ? s.charAt(i) : 'F';
			modes[i] = c == 'N' ? FaceMode.IN : c == 'U' ? FaceMode.OUT : FaceMode.OFF;
		}
	}

	private static void copyInto(int[] from, int[] to) {
		for(int i = 0; i < to.length && i < from.length; i++)
			to[i] = from[i];
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag rtuList = new ListTag();
		for(Map.Entry<GlobalPos, Rtu> entry : rtus.entrySet()) {
			CompoundTag t = new CompoundTag();
			MeshNode.savePos(t, entry.getKey());
			putModes(t, entry.getValue().modes);
			t.putIntArray("inputs", entry.getValue().inputs);
			t.putIntArray("outputs", entry.getValue().outputs);
			t.putBoolean("unpowered", !entry.getValue().powered);
			rtuList.add(t);
		}
		tag.put("rtus", rtuList);
		ListTag masterList = new ListTag();
		for(Map.Entry<GlobalPos, Master> entry : masters.entrySet()) {
			Master m = entry.getValue();
			CompoundTag t = new CompoundTag();
			MeshNode.savePos(t, entry.getKey());
			putModes(t, m.modes);
			t.putIntArray("inputs", m.inputs);
			t.putIntArray("outputs", m.outputs);
			t.putInt("poll", m.pollSeconds);
			t.putBoolean("unpowered", !m.powered);
			t.put("trend", m.trend.save());
			ListTag rules = new ListTag();
			for(String line : m.ruleText)
				rules.add(StringTag.valueOf(line));
			t.put("rules", rules);
			ListTag seenList = new ListTag();
			for(Map.Entry<Integer, Seen> seen : m.rtus.entrySet()) {
				CompoundTag s = new CompoundTag();
				s.putInt("unit", seen.getKey());
				s.putIntArray("inputs", seen.getValue().inputs);
				s.putLong("lastReply", seen.getValue().lastReply);
				s.putInt("misses", seen.getValue().misses);
				s.putBoolean("commFail", seen.getValue().commFail);
				s.put("trend", seen.getValue().trend.save());
				seenList.add(s);
			}
			t.put("seen", seenList);
			masterList.add(t);
		}
		tag.put("masters", masterList);
		return tag;
	}

	public static Scada load(CompoundTag tag, HolderLookup.Provider registries) {
		Scada scada = new Scada();
		ListTag rtuList = tag.getList("rtus", Tag.TAG_COMPOUND);
		for(int i = 0; i < rtuList.size(); i++) {
			CompoundTag t = rtuList.getCompound(i);
			GlobalPos pos = MeshNode.loadPos(t);
			if(pos == null)
				continue;
			Rtu rtu = new Rtu();
			getModes(t, rtu.modes);
			copyInto(t.getIntArray("inputs"), rtu.inputs);
			copyInto(t.getIntArray("outputs"), rtu.outputs);
			rtu.powered = !t.getBoolean("unpowered");
			scada.rtus.put(pos, rtu);
		}
		ListTag masterList = tag.getList("masters", Tag.TAG_COMPOUND);
		for(int i = 0; i < masterList.size(); i++) {
			CompoundTag t = masterList.getCompound(i);
			GlobalPos pos = MeshNode.loadPos(t);
			if(pos == null)
				continue;
			Master m = new Master();
			getModes(t, m.modes);
			copyInto(t.getIntArray("inputs"), m.inputs);
			copyInto(t.getIntArray("outputs"), m.outputs);
			List<String> lines = new ArrayList<>();
			ListTag rules = t.getList("rules", Tag.TAG_STRING);
			for(int j = 0; j < rules.size(); j++)
				lines.add(rules.getString(j));
			m.setRules(lines);
			m.powered = !t.getBoolean("unpowered");
			m.trend.load(t.getCompound("trend"));
			m.pollSeconds = Math.max(ScadaRules.MIN_POLL, Math.min(ScadaRules.MAX_POLL, t.contains("poll") ? t.getInt("poll") : ScadaRules.DEFAULT_POLL));
			ListTag seenList = t.getList("seen", Tag.TAG_COMPOUND);
			for(int j = 0; j < seenList.size(); j++) {
				CompoundTag s = seenList.getCompound(j);
				Seen seen = new Seen();
				int[] inputs = s.getIntArray("inputs");
				if(inputs.length == 6)
					seen.inputs = inputs;
				seen.lastReply = s.getLong("lastReply");
				seen.misses = s.getInt("misses");
				seen.commFail = s.getBoolean("commFail");
				seen.trend.load(s.getCompound("trend"));
				m.rtus.put(s.getInt("unit"), seen);
			}
			m.evaluate();
			scada.masters.put(pos, m);
		}
		return scada;
	}
}
