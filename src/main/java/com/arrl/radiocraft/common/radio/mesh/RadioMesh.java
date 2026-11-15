package com.arrl.radiocraft.common.radio.mesh;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.TransmitterLog;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * The radio mesh (2026-11-10): every fire alarm box, fire alarm controller, SCADA RTU and SCADA master on the server,
 * kept in the overworld's {@code radiocraft_radio_mesh} data whether their chunks are loaded or not. A burst one sends
 * is heard by every other device of its system on its frequency in reach ({@link MeshLink}); each hears a packet once
 * (by its id), hands it to its system ({@link Listener}), and if it is a field device and the packet is not for it,
 * sends it on once after a short random wait, up to the packet's hop limit. Devices in unloaded chunks relay with
 * what they were like when last loaded, so a message crosses a town whose middle nobody is in.
 * <p>
 * On the air for everyone else: each burst goes into the transmitter log (direction finders and the FCC program hear
 * it) and makes a short quiet chirp at a loaded device. It is not put on the antenna network as an APRS packet, so APRS
 * screens never see it.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class RadioMesh extends SavedData {

	public static final String SAVE_NAME = "radiocraft_radio_mesh";
	/** Shortest and longest wait before a device relays, ticks. */
	public static final int RELAY_MIN_TICKS = 3, RELAY_MAX_TICKS = 10;
	/** How long a device remembers a packet id it has heard, ticks. */
	public static final long HEARD_TICKS = 2400L;
	/** How loud a burst's chirp is (quiet: these are not APRS bursts). */
	public static final float CHIRP_VOLUME = 0.3F;

	/** What a system does with a packet one of its devices hears. */
	public interface Listener {
		/** {@code at} heard {@code packet}. True if it was for {@code at} (an ack to its box), so it is not relayed. */
		boolean heard(MinecraftServer server, MeshNode at, MeshPacket packet, long now);
	}

	/** What {@code system} does with what its devices hear. */
	private static Listener listener(MeshSystem system) {
		return switch(system) {
			case FIRE -> com.arrl.radiocraft.common.radio.firealarm.FireAlarms::heard;
			case SCADA -> com.arrl.radiocraft.common.radio.scada.Scada::heard;
		};
	}

	private record Scheduled(long at, long order, GlobalPos from, MeshPacket packet) {}

	private final Map<GlobalPos, MeshNode> nodes = new LinkedHashMap<>();
	// Not saved: bursts on their way and the ids heard. A restart loses packets mid-relay; the senders try again.
	private final PriorityQueue<Scheduled> queue = new PriorityQueue<>((a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.order, b.order));
	private final Map<Long, Set<GlobalPos>> heard = new HashMap<>();
	private final Map<Long, Long> heardAt = new HashMap<>();
	private final Random random = new Random();
	private long order = 0L;
	private int bursts = 0;

	public static SavedData.Factory<RadioMesh> factory() {
		return new SavedData.Factory<>(RadioMesh::new, RadioMesh::load, null);
	}

	public static RadioMesh of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	// -------------------- DEVICES --------------------

	public Collection<MeshNode> all() {
		return Collections.unmodifiableCollection(nodes.values());
	}

	@Nullable
	public MeshNode get(GlobalPos pos) {
		return nodes.get(pos);
	}

	/** Adds or updates a device. */
	public void put(MeshNode node) {
		if(!node.equals(nodes.get(node.pos()))) {
			nodes.put(node.pos(), node);
			setDirty();
		}
	}

	/** Takes a device off the mesh (its block is gone). */
	@Nullable
	public MeshNode remove(GlobalPos pos) {
		MeshNode old = nodes.remove(pos);
		if(old != null)
			setDirty();
		return old;
	}

	/** The devices with {@code role} on {@code hz}. */
	public List<MeshNode> on(MeshRole role, float hz) {
		List<MeshNode> out = new ArrayList<>();
		for(MeshNode node : nodes.values())
			if(node.role() == role && node.on(hz))
				out.add(node);
		return out;
	}

	/** Every device that counts against license {@code callsign}. */
	public List<MeshNode> under(String callsign) {
		List<MeshNode> out = new ArrayList<>();
		for(MeshNode node : nodes.values())
			if(node.license().equalsIgnoreCase(callsign))
				out.add(node);
		return out;
	}

	/** The device with {@code role} and {@code address} on {@code hz}, or null. */
	@Nullable
	public MeshNode find(MeshRole role, float hz, int address) {
		for(MeshNode node : nodes.values())
			if(node.role() == role && node.address() == address && node.on(hz))
				return node;
		return null;
	}

	/** The lowest address from {@code first} up that no device with {@code role} on {@code hz} has, but {@code self}. */
	public int freeAddress(MeshRole role, float hz, int first, @Nullable GlobalPos self) {
		Set<Integer> used = new HashSet<>();
		for(MeshNode node : nodes.values())
			if(node.role() == role && node.on(hz) && !node.pos().equals(self))
				used.add(node.address());
		int address = Math.max(1, first);
		while(used.contains(address))
			address++;
		return address;
	}

	// -------------------- ON THE AIR --------------------

	/** The share of a band's line of sight a built-in antenna reaches (server config). */
	public static double internalRange() {
		return RadiocraftServerConfig.MESH_INTERNAL_ANTENNA_RANGE.get();
	}

	/** True if {@code a} and {@code b} hear each other. */
	public static boolean hears(MeshNode a, MeshNode b) {
		if(!a.pos().dimension().equals(b.pos().dimension()) || !a.on(b.hz()))
			return false;
		Band band = Band.getBand(a.hz());
		if(band == null)
			return false;
		double distance = Math.sqrt(a.pos().pos().distSqr(b.pos().pos()));
		double internal = internalRange();
		return MeshLink.hears(distance, band.losRange(), MeshLink.reach(a.external(), internal), MeshLink.reach(b.external(), internal));
	}

	/** The strength {@code a} and {@code b} hear each other at, 0 to 1 (0 when on other frequencies or out of range). */
	public static double strength(MeshNode a, MeshNode b) {
		if(!a.pos().dimension().equals(b.pos().dimension()) || !a.on(b.hz()))
			return 0.0D;
		Band band = Band.getBand(a.hz());
		if(band == null)
			return 0.0D;
		double distance = Math.sqrt(a.pos().pos().distSqr(b.pos().pos()));
		double internal = internalRange();
		return MeshLink.strength(distance, band.losRange(), MeshLink.reach(a.external(), internal), MeshLink.reach(b.external(), internal));
	}

	/**
	 * How well the device at {@code pos} is heard on its frequency: the strongest link it has to any other device on it
	 * (a box's LINK meter, the SCADA master's, 2026-11-11). 0 if it is not on the mesh or hears nobody.
	 */
	public double bestLink(GlobalPos pos) {
		MeshNode self = nodes.get(pos);
		if(self == null)
			return 0.0D;
		double best = 0.0D;
		for(MeshNode other : nodes.values())
			if(other != self)
				best = Math.max(best, strength(self, other));
		return best;
	}

	/** A device another one hears, and how well. */
	public record Neighbour(MeshNode node, double strength) {}

	/** The devices of its own system the device at {@code pos} hears, strongest first (a box's STATUS page, 2026-11-11). */
	public List<Neighbour> neighbours(GlobalPos pos) {
		MeshNode self = nodes.get(pos);
		List<Neighbour> out = new ArrayList<>();
		if(self == null)
			return out;
		for(MeshNode other : nodes.values())
			if(other != self && other.system() == self.system() && hears(self, other))
				out.add(new Neighbour(other, strength(self, other)));
		out.sort((x, y) -> Double.compare(y.strength(), x.strength()));
		return out;
	}

	/**
	 * How many relays a burst from the device at {@code pos} takes to reach the nearest device of role {@code target}
	 * (0: heard directly), going only through devices that relay and no further than {@code hopLimit} relays; -1 if it
	 * cannot reach one. A breadth-first walk of who hears whom, as the bursts go (2026-11-11).
	 */
	public int hopsTo(GlobalPos pos, MeshRole target, int hopLimit) {
		MeshNode self = nodes.get(pos);
		if(self == null)
			return -1;
		java.util.Set<MeshNode> seen = new java.util.HashSet<>();
		List<MeshNode> frontier = List.of(self);
		seen.add(self);
		for(int relays = 0; relays <= hopLimit && !frontier.isEmpty(); relays++) {
			List<MeshNode> next = new ArrayList<>();
			for(MeshNode from : frontier)
				for(MeshNode other : nodes.values()) {
					if(seen.contains(other) || other.system() != self.system() || !hears(from, other))
						continue;
					if(other.role() == target)
						return relays;
					seen.add(other);
					if(other.role().relays())
						next.add(other);
				}
			frontier = next;
		}
		return -1;
	}

	/** {@code from} sends {@code packet} now. Nothing happens if {@code from} is not on the mesh. */
	public void send(MinecraftServer server, GlobalPos from, MeshPacket packet) {
		MeshNode node = nodes.get(from);
		if(node != null)
			radiate(server, node, packet, server.overworld().getGameTime());
	}

	/**
	 * {@code from} sends {@code packet} in {@code delayTicks}: for an answer to something just heard (an ack, a poll's
	 * reply), which real radios send once they have turned round, and which must not go out while that burst is still
	 * being handed round.
	 */
	public void sendLater(MinecraftServer server, GlobalPos from, MeshPacket packet, int delayTicks) {
		queue.add(new Scheduled(server.overworld().getGameTime() + Math.max(1, delayTicks), order++, from, packet));
	}

	/** A new packet id. */
	public long newId() {
		long id;
		do
			id = random.nextLong();
		while(id == 0L || heard.containsKey(id));
		return id;
	}

	/** Bursts sent since the server started (GameTests). */
	public int bursts() {
		return bursts;
	}

	/** True while bursts are still on their way through the mesh (GameTests). */
	public boolean busy() {
		return !queue.isEmpty();
	}

	private void radiate(MinecraftServer server, MeshNode from, MeshPacket packet, long now) {
		bursts++;
		markHeard(packet.id(), from.pos(), now);
		ServerLevel level = server.getLevel(from.pos().dimension());
		if(level != null) {
			TransmitterLog.record(from.pos(), level, from.pos().pos(), Band.getBand(from.hz()), from.hz());
			if(level.isLoaded(from.pos().pos()))
				level.playSound(null, from.pos().pos(), RadiocraftSoundEvents.MESH_CHIRP.get(), SoundSource.BLOCKS, CHIRP_VOLUME, 0.9F + random.nextFloat() * 0.2F);
		}
		Listener listener = listener(packet.system());
		for(MeshNode node : new ArrayList<>(nodes.values())) {
			if(node == from || node.system() != packet.system() || !hears(from, node))
				continue;
			if(!markHeard(packet.id(), node.pos(), now))
				continue; // Heard it already.
			boolean mine = listener != null && listener.heard(server, node, packet, now);
			if(!mine && node.role().relays() && packet.mayRelay())
				queue.add(new Scheduled(now + RELAY_MIN_TICKS + random.nextInt(RELAY_MAX_TICKS - RELAY_MIN_TICKS + 1), order++, node.pos(), packet.relayed()));
		}
	}

	/** Notes that {@code pos} heard {@code id}; false if it already had. */
	private boolean markHeard(long id, GlobalPos pos, long now) {
		heardAt.put(id, now);
		return heard.computeIfAbsent(id, k -> new HashSet<>()).add(pos);
	}

	private void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		while(!queue.isEmpty() && queue.peek().at <= now) {
			Scheduled next = queue.poll();
			MeshNode node = nodes.get(next.from);
			if(node != null)
				radiate(server, node, next.packet, now);
		}
		if(now % 200L == 0L && !heardAt.isEmpty()) {
			Iterator<Map.Entry<Long, Long>> it = heardAt.entrySet().iterator();
			while(it.hasNext()) {
				Map.Entry<Long, Long> entry = it.next();
				if(now - entry.getValue() > HEARD_TICKS || now < entry.getValue()) {
					heard.remove(entry.getKey());
					it.remove();
				}
			}
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		of(server).tick(server);
	}

	// -------------------- SAVE --------------------

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag list = new ListTag();
		for(MeshNode node : nodes.values())
			list.add(node.save());
		tag.put("nodes", list);
		return tag;
	}

	public static RadioMesh load(CompoundTag tag, HolderLookup.Provider registries) {
		RadioMesh mesh = new RadioMesh();
		ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			MeshNode node = MeshNode.load(list.getCompound(i));
			if(node != null)
				mesh.nodes.put(node.pos(), node);
		}
		return mesh;
	}
}
