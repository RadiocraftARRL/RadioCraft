package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every program the radio computer knows (1.12 step 5), in the order the shell lists them: the four built in, then the
 * ones on floppy disks. All of them are registered here at once, including those written by later steps, so the floppy
 * disk items ({@code RadiocraftItems.FLOPPIES}), their recipes, lang and models exist from the start and the registry
 * never changes shape. A program whose client screen is not written yet opens as a "not available in this version"
 * page (see {@code client.screens.computer.ComputerAppScreens}).
 * <p>
 * Pure (no game classes), so it is unit tested. The server side of a program, if it has one, is registered in
 * {@link ComputerAppHandlers}; its screen in {@code ComputerAppScreens}.
 * <p>
 * {@link #add} must run before the item registry is built (from a static initialiser, as here), because each floppy is
 * an item registered from this list.
 */
public final class ComputerApps {

	private static final Map<String, ComputerApp> APPS = new LinkedHashMap<>();

	// Built in (step 5).
	public static final ComputerApp EDITOR = add("edit", "EDIT", ComputerApp.Kind.BUILT_IN);
	public static final ComputerApp SYSINFO = add("sysinfo", "SYSINFO", ComputerApp.Kind.BUILT_IN);
	public static final ComputerApp BAND_PLAN = add("bandplan", "BANDPLAN", ComputerApp.Kind.BUILT_IN);
	public static final ComputerApp INSTALL = add("install", "INSTALL", ComputerApp.Kind.BUILT_IN);

	// Office programs (step 6).
	public static final ComputerApp BOOK_EDITOR = add("bookedit", "BOOKEDIT", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp LOGBOOK = add("logbook", "LOGBOOK", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp FREQUENCY_MANAGER = add("freqman", "FREQMAN", ComputerApp.Kind.CRAFTED);
	/** The commercial radio programmer: CPS, "customer programming software", as the radio shops call it. */
	public static final ComputerApp PROGRAMMER = add("programmer", "CPS", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp SOLAR_WEATHER = add("solar", "SOLAR", ComputerApp.Kind.CRAFTED);

	// Radio programs (step 7).
	public static final ComputerApp APRS = add("aprs", "APRS", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp WEFAX = add("wefax", "WEFAX", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp RADIO_FAX = add("radiofax", "FAX", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp SSTV = add("sstv", "SSTV", ComputerApp.Kind.CRAFTED);
	public static final ComputerApp DIRECTION_FINDING = add("df", "DFIND", ComputerApp.Kind.CRAFTED);

	// Secret programs, found in structures (step 8).
	public static final ComputerApp SNAKE = add("snake", "SNAKE", ComputerApp.Kind.SECRET);
	/**
	 * The falling-blocks game, "Pileup" (RadioCraft's own; a pileup is a crowd of stations calling one). Its id stays
	 * {@code falling_blocks} (the floppy item {@code floppy_falling_blocks} was registered under it in step 5).
	 */
	public static final ComputerApp FALLING_BLOCKS = add("falling_blocks", "PILEUP", ComputerApp.Kind.SECRET);
	public static final ComputerApp MUSIC = add("music", "MUSIC", ComputerApp.Kind.SECRET);
	public static final ComputerApp BIBLE = add("bible", "BIBLE", ComputerApp.Kind.SECRET);

	// ADS-B (step 9).
	public static final ComputerApp ADSB = add("adsb", "ADSB", ComputerApp.Kind.CRAFTED);

	private ComputerApps() {}

	/**
	 * Adds a program. Its id and command must be new. Call it before the item registry is built (see the class
	 * comment). Returns it.
	 */
	public static synchronized ComputerApp add(String id, String command, ComputerApp.Kind kind) {
		ComputerApp app = new ComputerApp(id, command, kind);
		if(APPS.containsKey(id))
			throw new IllegalArgumentException("Computer app " + id + " is already registered");
		for(ComputerApp other : APPS.values())
			if(other.command().equals(command))
				throw new IllegalArgumentException("Computer app command " + command + " is already " + other.id() + "'s");
		APPS.put(id, app);
		return app;
	}

	/** The program with {@code id}, or null. */
	@Nullable
	public static ComputerApp get(String id) {
		return id == null ? null : APPS.get(id);
	}

	/** Every program, in shell order. */
	public static List<ComputerApp> all() {
		return Collections.unmodifiableList(new ArrayList<>(APPS.values()));
	}

	/** The programs every computer has. */
	public static List<ComputerApp> builtIn() {
		return all().stream().filter(ComputerApp::builtIn).toList();
	}

	/** The programs that come on a floppy disk, crafted or secret. */
	public static List<ComputerApp> onFloppies() {
		return all().stream().filter(ComputerApp::hasFloppy).toList();
	}

	/**
	 * The programs a computer has: the built-in ones, then {@code installed} (ids; unknown and built-in ones are
	 * skipped), all in shell order.
	 */
	public static List<ComputerApp> available(java.util.Collection<String> installed) {
		List<ComputerApp> out = new ArrayList<>();
		for(ComputerApp app : APPS.values())
			if(app.builtIn() || installed.contains(app.id()))
				out.add(app);
		return out;
	}
}
