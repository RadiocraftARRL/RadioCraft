package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.blockentities.MicrowaveRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.StationTerminal;
import com.arrl.radiocraft.common.radio.microwave.MicrowaveMath;
import com.arrl.radiocraft.common.radio.microwave.MicrowaveRoutes;
import com.arrl.radiocraft.common.radio.voice.LinkAudio;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The radio computers a computer reaches (2026-11-09): its network. Over data wire (touching, on its wire, or on its
 * station bus through a radio interface) and over microwave: the microwave radios serving the computer or a radio
 * interface on its bus carry it to every station served at the far end ({@link MicrowaveRoutes}, as the fax and the
 * APRS terminal reach across), a computer there or a radio interface with computers on its bus. A computer that reaches
 * another is "on a network".
 * <p>
 * <b>Cost.</b> The answer is kept for {@link #CACHE_TICKS} or until a data wire changes anywhere in the level
 * ({@link DataWire#generation}); the walks it is built on are the data wire's own cached walks. Nothing here runs per
 * tick: computers look now and then ({@link #CHECK_TICKS}), and requests from the screen read the cache.
 * <p>
 * <b>Names.</b> On a network every computer needs a name the others do not use, case aside (SETUP's computer name,
 * {@link ComputerSettings#name}). Uniqueness is per network, not server-wide. A computer that boots onto a network
 * with no name, or with a name a reachable computer has, asks for one (the NETWORK NAME prompt,
 * {@link RadioComputerBlockEntity#needsNetworkName}); when two already running are joined and clash, the one that
 * notices first keeps its name and the other asks.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.arrl.radiocraft.Radiocraft.MOD_ID)
public final class ComputerNetwork {

	/** How long a computer's reach is kept, ticks. */
	public static final int CACHE_TICKS = 40;
	/** How often a running computer looks at its network for names, ticks (5 seconds). */
	public static final int CHECK_TICKS = 100;

	private ComputerNetwork() {}

	/**
	 * How two computers are joined: data wire ({@link #WIRE}) or a microwave route, by the worst grade of the links it
	 * crosses ({@link MicrowaveMath.Grade}).
	 */
	public record Link(@Nullable MicrowaveMath.Grade grade) {

		public static final Link WIRE = new Link(null);

		public boolean wire() {
			return grade == null;
		}

		/** "WIRE", or "MW GOOD" / "MW FAIR" / "MW POOR"... as RIGCTL lists it. */
		public String label() {
			return grade == null ? "WIRE" : "MW " + grade.label;
		}

		/** False once a microwave link on the way shows NO SIGNAL. */
		public boolean up() {
			return grade != MicrowaveMath.Grade.NONE;
		}

		/** What it does to remote audio. */
		public LinkAudio.Impairment impairment() {
			return LinkAudio.of(grade);
		}

		/** The better of this and {@code other}: wire beats microwave, a better grade beats a worse one. */
		public Link best(@Nullable Link other) {
			if(other == null)
				return this;
			if(wire() || other.wire())
				return WIRE;
			return grade.ordinal() >= other.grade.ordinal() ? this : other;
		}
	}

	/** A computer reached, and how. */
	public record Peer(RadioComputerBlockEntity computer, Link link) {}

	private record Entry(long tick, long generation, List<Peer> peers) {}

	private static final Map<Level, Map<BlockPos, Entry>> CACHE = new WeakHashMap<>();

	/** A level unloads: its computers go with it (the cache holds them, and they hold the level). */
	@net.neoforged.bus.api.SubscribeEvent
	public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
		if(event.getLevel() instanceof Level level)
			CACHE.remove(level);
	}

	/** The server stops: no session, so no remote microphone, outlives it. */
	@net.neoforged.bus.api.SubscribeEvent
	public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
		CACHE.clear();
		com.arrl.radiocraft.common.radio.voice.RemoteMics.clear();
	}

	// -------------------- REACH --------------------

	/** The computers {@code computer} reaches (itself left out), from the cache when it is fresh. Server thread. */
	public static List<Peer> peers(RadioComputerBlockEntity computer) {
		Level level = computer.getLevel();
		if(level == null || level.isClientSide)
			return List.of();
		Entry entry = CACHE.computeIfAbsent(level, l -> new HashMap<>()).get(computer.getBlockPos());
		long now = level.getGameTime();
		if(entry != null && entry.generation() == DataWire.generation(level) && now - entry.tick() < CACHE_TICKS && now >= entry.tick()) {
			for(Peer peer : entry.peers())
				if(peer.computer().isRemoved())
					return peersNow(computer);
			return entry.peers();
		}
		return peersNow(computer);
	}

	/** {@link #peers}, worked out afresh (and kept). */
	public static List<Peer> peersNow(RadioComputerBlockEntity computer) {
		Level level = computer.getLevel();
		if(level == null || level.isClientSide)
			return List.of();
		List<Peer> peers = find(level, computer);
		Map<BlockPos, Entry> cache = CACHE.computeIfAbsent(level, l -> new HashMap<>());
		if(cache.size() > 1024)
			cache.clear(); // Computers long gone pile up otherwise.
		cache.put(computer.getBlockPos().immutable(), new Entry(level.getGameTime(), DataWire.generation(level), peers));
		return peers;
	}

	/** How {@code from} reaches {@code to} (from the cache), or null if it does not. */
	@Nullable
	public static Link link(RadioComputerBlockEntity from, RadioComputerBlockEntity to) {
		for(Peer peer : peers(from))
			if(peer.computer() == to)
				return peer.link();
		return null;
	}

	/** True if {@code computer} reaches another radio computer. */
	public static boolean onNetwork(RadioComputerBlockEntity computer) {
		return !peers(computer).isEmpty();
	}

	private static List<Peer> find(Level level, RadioComputerBlockEntity self) {
		BlockPos pos = self.getBlockPos();
		Map<RadioComputerBlockEntity, Link> found = new LinkedHashMap<>();
		for(RadioComputerBlockEntity other : DataWire.linked(level, pos, RadioComputerBlockEntity.class))
			found.put(other, Link.WIRE);
		StationBus bus = StationBus.find(level, pos);
		for(StationTerminal terminal : bus.terminals())
			if(terminal instanceof RadioComputerBlockEntity other)
				found.put(other, Link.WIRE);
		// Over microwave: from the computer, and from each radio interface on its bus.
		List<BlockPos> starts = new ArrayList<>();
		starts.add(pos);
		for(RadioInterfaceBlockEntity radioInterface : bus.interfaces())
			starts.add(radioInterface.getBlockPos());
		Map<BlockPos, StationBus> farBuses = new HashMap<>();
		for(BlockPos start : starts)
			for(MicrowaveRoutes.Route route : MicrowaveRoutes.from(level, start)) {
				Link link = route.hops() == 0 ? Link.WIRE : new Link(worst(route));
				if(route.station() instanceof RadioComputerBlockEntity other)
					found.put(other, link.best(found.get(other)));
				else if(route.station() instanceof RadioInterfaceBlockEntity far) {
					StationBus farBus = farBuses.computeIfAbsent(far.getBlockPos(), at -> StationBus.find(level, at));
					for(StationTerminal terminal : farBus.terminals())
						if(terminal instanceof RadioComputerBlockEntity other)
							found.put(other, link.best(found.get(other)));
				}
			}
		found.remove(self);
		List<Peer> out = new ArrayList<>();
		for(Map.Entry<RadioComputerBlockEntity, Link> entry : found.entrySet())
			if(!entry.getKey().isRemoved())
				out.add(new Peer(entry.getKey(), entry.getValue()));
		return List.copyOf(out);
	}

	/** The worst grade of the links a route crosses (both ends of each). */
	private static MicrowaveMath.Grade worst(MicrowaveRoutes.Route route) {
		MicrowaveMath.Grade worst = MicrowaveMath.Grade.GOOD;
		for(MicrowaveRadioBlockEntity radio : route.ends()) {
			MicrowaveMath.Grade grade = MicrowaveMath.grade(radio.getRssi());
			if(grade.ordinal() < worst.ordinal())
				worst = grade;
		}
		return worst;
	}

	// -------------------- NAMES --------------------

	/** The computer's name (SETUP's), "" for none. Server. */
	public static String name(RadioComputerBlockEntity computer) {
		return computer.files().settings().name();
	}

	/** True if {@code a} and {@code b} are the same name, case aside (and neither empty). */
	public static boolean sameName(String a, String b) {
		return !a.isEmpty() && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
	}

	/** The computers among {@code peers} named {@code name} (case aside). */
	public static List<RadioComputerBlockEntity> named(List<Peer> peers, String name) {
		List<RadioComputerBlockEntity> out = new ArrayList<>();
		for(Peer peer : peers)
			if(sameName(name(peer.computer()), name))
				out.add(peer.computer());
		return out;
	}

	/** True if a computer {@code computer} reaches already has {@code name}. */
	public static boolean nameTaken(RadioComputerBlockEntity computer, String name) {
		return !named(peersNow(computer), ComputerSettings.cleanName(name)).isEmpty();
	}

	/**
	 * Why {@code name} cannot be this computer's name on its network (an error key), or null if it can: a computer on a
	 * network needs one ({@code netname_needed}), and not one another reachable computer has ({@code netname_in_use}).
	 */
	@Nullable
	public static String nameRefused(RadioComputerBlockEntity computer, String name) {
		String clean = ComputerSettings.cleanName(name);
		List<Peer> peers = peersNow(computer);
		if(peers.isEmpty())
			return null;
		if(clean.isEmpty())
			return "netname_needed";
		return named(peers, clean).isEmpty() ? null : "netname_in_use";
	}

	/** A number for the set of computers reached: when it changes the computer looks at its name again. */
	private static int hash(List<Peer> peers) {
		int hash = 1;
		for(Peer peer : peers)
			hash += peer.computer().getBlockPos().hashCode() * 31 + 7; // Order does not matter.
		return hash * 31 + peers.size();
	}

	/**
	 * {@code computer} boots (someone opens its screen): on a network with no name, or with a name a computer there
	 * has that is not itself asking, it asks for one. Booting later, it is the one that asks.
	 */
	public static void boot(RadioComputerBlockEntity computer) {
		List<Peer> peers = peersNow(computer);
		computer.setPeerHash(hash(peers));
		if(peers.isEmpty()) {
			computer.setNeedsNetworkName(false);
			return;
		}
		String name = name(computer);
		if(name.isEmpty()) {
			computer.setNeedsNetworkName(true);
			return;
		}
		boolean clash = false;
		for(RadioComputerBlockEntity other : named(peers, name))
			if(!other.needsNetworkName())
				clash = true;
		computer.setNeedsNetworkName(clash);
	}

	/**
	 * Now and then while {@code computer} runs ({@link #CHECK_TICKS}): if what it reaches changed, or it is asking, it
	 * looks at its name again. On joining a network with no name it asks; with a clash, the computer that notices first
	 * keeps its name and the others there with it ask (they notice later).
	 */
	public static void check(RadioComputerBlockEntity computer) {
		List<Peer> peers = peers(computer);
		int hash = hash(peers);
		boolean changed = hash != computer.peerHash();
		computer.setPeerHash(hash);
		if(!changed && !computer.needsNetworkName())
			return;
		if(peers.isEmpty()) {
			computer.setNeedsNetworkName(false);
			return;
		}
		String name = name(computer);
		if(name.isEmpty()) {
			computer.setNeedsNetworkName(true);
			return;
		}
		List<RadioComputerBlockEntity> clashing = named(peers, name);
		if(clashing.isEmpty()) {
			computer.setNeedsNetworkName(false);
			return;
		}
		if(computer.needsNetworkName())
			return; // Another noticed first: this one asks.
		for(RadioComputerBlockEntity other : clashing)
			other.setNeedsNetworkName(true); // We noticed first: they ask.
	}
}
