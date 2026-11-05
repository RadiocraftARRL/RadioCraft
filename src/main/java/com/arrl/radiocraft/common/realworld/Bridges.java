package com.arrl.radiocraft.common.realworld;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The Real-World Interfaces the server config lists ({@code real_world_interfaces}) and who is using each: one block
 * per configured interface at a time ({@link #claim}), and at most one {@link Bridge} per interface, so two blocks never
 * fight over one sound card. Nothing here opens a device unless {@code real_world_interface_enabled} is on; the sound
 * and serial code ({@link JavaSoundBackend}) is not even loaded until a bridge starts or an operator lists the
 * devices. Every bridge is closed when the server stops ({@link #closeAll}).
 */
public final class Bridges {

	/** Interface name (lower case) to the block using it. */
	private static final Map<String, Object> OWNERS = new ConcurrentHashMap<>();
	/** Interface name (lower case) to its latest bridge, running or closing. */
	private static final Map<String, Bridge> BRIDGES = new ConcurrentHashMap<>();
	@Nullable
	private static volatile AudioBackend testBackend = null;
	@Nullable
	private static volatile AudioBackend systemBackend = null;
	// The config list last read, and what it came to.
	private static volatile List<? extends String> readFrom = null;
	private static volatile List<InterfaceSpec> specs = List.of();
	private static volatile List<String> problems = List.of();

	private Bridges() {}

	/** True if the server config turns the feature on. */
	public static boolean enabled() {
		return RadiocraftServerConfig.REAL_WORLD_INTERFACE_ENABLED.get();
	}

	/** The interfaces the config lists that parse; bad lines are logged once each time the list changes. */
	public static List<InterfaceSpec> specs() {
		List<? extends String> lines = RadiocraftServerConfig.REAL_WORLD_INTERFACES.get();
		if(lines != readFrom) {
			List<String> found = new ArrayList<>();
			List<InterfaceSpec> parsed = InterfaceSpec.parseAll(lines, found);
			for(String problem : found)
				Radiocraft.LOGGER.warn("real_world_interfaces in the server config, {}", problem);
			specs = List.copyOf(parsed);
			problems = List.copyOf(found);
			readFrom = lines;
		}
		return specs;
	}

	/** What is wrong with the config's lines, for the operator's status command. */
	public static List<String> problems() {
		specs();
		return problems;
	}

	/** The configured interface named {@code name}, or null. */
	@Nullable
	public static InterfaceSpec spec(String name) {
		return InterfaceSpec.find(specs(), name);
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * Makes {@code owner} the one block using interface {@code name}; false if another (still there, see
	 * {@code stillThere}) has it. A block may claim again what it already has.
	 */
	public static boolean claim(String name, Object owner, java.util.function.Predicate<Object> stillThere) {
		Object result = OWNERS.compute(key(name), (k, held) -> held == null || held == owner || !stillThere.test(held) ? owner : held);
		if(result != owner)
			return false;
		OWNERS.entrySet().removeIf(entry -> entry.getValue() == owner && !entry.getKey().equals(key(name))); // One interface per block.
		return true;
	}

	/** The block using interface {@code name}, or null. */
	@Nullable
	public static Object owner(String name) {
		return OWNERS.get(key(name));
	}

	/** Lets go of every interface {@code owner} holds. */
	public static void release(Object owner) {
		OWNERS.values().removeIf(held -> held == owner);
	}

	/**
	 * Starts a bridge for {@code spec}, after closing any other for that interface (the new one waits for the old to
	 * let go of the devices, on its own thread). Returns at once.
	 */
	public static Bridge open(InterfaceSpec spec, Consumer<short[]> uplink) {
		Bridge[] made = new Bridge[1];
		BRIDGES.compute(key(spec.name()), (k, old) -> {
			if(old != null)
				old.close();
			made[0] = Bridge.start(spec, backend(), uplink, old);
			return made[0];
		});
		return made[0];
	}

	/** Stops {@code bridge} (it closes its devices on its own thread). */
	public static void close(@Nullable Bridge bridge) {
		if(bridge == null)
			return;
		bridge.close();
		BRIDGES.remove(key(bridge.spec().name()), bridge);
	}

	/** The bridges running now, by interface name, for the status command. */
	public static Map<String, Bridge> running() {
		return Map.copyOf(BRIDGES);
	}

	/** Server stopping: stops every bridge, unkeys every radio and closes every device, waiting up to {@code ms} each. */
	public static void closeAll(long ms) {
		List<Bridge> all = new ArrayList<>(BRIDGES.values());
		BRIDGES.clear();
		OWNERS.clear();
		all.forEach(Bridge::close);
		for(Bridge bridge : all)
			if(!bridge.awaitClosed(ms))
				Radiocraft.LOGGER.warn("Real-World Interface {} did not close its devices in time", bridge.spec().name());
		readFrom = null;
	}

	/** The devices: the server's own, unless a GameTest put in a stand-in ({@link #setBackendForTesting}). */
	public static AudioBackend backend() {
		AudioBackend test = testBackend;
		if(test != null)
			return test;
		AudioBackend system = systemBackend;
		if(system == null)
			systemBackend = system = new JavaSoundBackend();
		return system;
	}

	/** GameTests: devices that are not the server's (null for the real ones again). */
	public static void setBackendForTesting(@Nullable AudioBackend backend) {
		testBackend = backend;
	}
}
