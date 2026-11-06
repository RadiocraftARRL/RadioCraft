package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every program the radio computer knows (1.12 step 5), in the order the shell lists them: the ones built in, then the
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
	/** The computer's settings: launcher view, colour scheme, password, name and clock (1.12, launcher and SETUP). */
	public static final ComputerApp SETUP = add("setup", "SETUP", ComputerApp.Kind.BUILT_IN);
	/** About RadioCraft: the mod, its story, version, credits and website (1.12, launcher and SETUP). */
	public static final ComputerApp ABOUT = add("about", "ABOUT", ComputerApp.Kind.BUILT_IN);
	/**
	 * Licence exam study: flash cards and practice exams from the question pools. Built in since 1.12's launcher work
	 * (it was a floppy program, {@code floppy_license}, in the unreleased 1.12 builds before; that item is gone).
	 */
	public static final ComputerApp LICENSE = add("license", "STUDY", ComputerApp.Kind.BUILT_IN);

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

	// Computer apps 2 (2026-11-06).
	/** Live RTTY (45.45 baud Baudot) through the radio interface, with a type-ahead buffer and macros. */
	public static final ComputerApp RTTY = add("rtty", "RTTY", ComputerApp.Kind.CRAFTED);
	/** A packet mailbox: the computer as a BBS other stations reach by APRS message. */
	public static final ComputerApp BBS = add("bbs", "BBS", ComputerApp.Kind.CRAFTED);
	/** HF propagation predictions: which bands open to a target over the next hours, from the live sky. */
	public static final ComputerApp PROPAGATION = add("propagation", "PREDICT", ComputerApp.Kind.CRAFTED);
	/** A directory of repeaters heard and typed in, exported to the frequency manager. */
	public static final ComputerApp REPEATERS = add("repeaters", "REPEATER", ComputerApp.Kind.CRAFTED);

	// Computer batch 3A (2026-11-06).
	/**
	 * Screensavers: after SETUP's idle minutes the screen shows a starfield, the callsign or a waterfall, and the
	 * computer draws less power while it does. Found in the world only (loot), like the games.
	 */
	public static final ComputerApp SCREENSAVER = add("saver", "SAVER", ComputerApp.Kind.SECRET);
	/**
	 * Timed jobs with nobody at the screen: an automatic station ID in Morse, WEFAX broadcasts of a saved chart, an APRS
	 * beacon every so often, net reminders. Runs while the computer has power and its chunk is loaded.
	 */
	public static final ComputerApp SCHEDULER = add("scheduler", "SCHEDULE", ComputerApp.Kind.CRAFTED);

	// Computer batch 3B (2026-11-06).
	/**
	 * Rig control (CAT) through the radio interface: tune, band, mode, power and PTT from the keyboard, scan a range or
	 * the memory channels with squelch-stop, and a live waterfall of the band.
	 */
	public static final ComputerApp RIG_CONTROL = add("rigctl", "RIGCTL", ComputerApp.Kind.CRAFTED);
	/** FT8: the automated weak-signal mode in 15 s slots, auto-sequenced, contacts logged to the logbook. */
	public static final ComputerApp FT8 = add("ft8", "FT8", ComputerApp.Kind.CRAFTED);

	// Computer batch 3C (2026-11-06).
	/** The Net Control logger: run a directed net, its check-ins and roll call, print the net report, log the check-ins. */
	public static final ComputerApp NET_CONTROL = add("netlog", "NETLOG", ComputerApp.Kind.CRAFTED);
	/** Operating awards from the logbook (every contact counts): progress pages and printed certificates. */
	public static final ComputerApp AWARDS = add("awards", "AWARDS", ComputerApp.Kind.CRAFTED);
	/** The QSL Designer: QSL card templates, printed runs of blank cards, cards received by fax or from a BBS. */
	public static final ComputerApp QSL = add("qsl", "QSL", ComputerApp.Kind.CRAFTED);

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
