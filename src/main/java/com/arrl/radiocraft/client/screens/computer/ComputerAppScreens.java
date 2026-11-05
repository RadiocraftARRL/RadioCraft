package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.client.screens.computer.apps.BandPlanApp;
import com.arrl.radiocraft.client.screens.computer.apps.BookEditorApp;
import com.arrl.radiocraft.client.screens.computer.apps.EditorApp;
import com.arrl.radiocraft.client.screens.computer.apps.FrequencyManagerApp;
import com.arrl.radiocraft.client.screens.computer.apps.InstallApp;
import com.arrl.radiocraft.client.screens.computer.apps.LogbookApp;
import com.arrl.radiocraft.client.screens.computer.apps.PendingApp;
import com.arrl.radiocraft.client.screens.computer.apps.ProgrammerApp;
import com.arrl.radiocraft.client.screens.computer.apps.SolarWeatherApp;
import com.arrl.radiocraft.client.screens.computer.apps.SysInfoApp;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.items.FloppyDiskItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The radio computer programs' screens, client side (1.12 step 5): for each {@link ComputerApp}, the {@link DosApp}
 * that runs it ({@link #register}); which program opens a file extension from the shell's Files list
 * ({@link #registerOpener}); and each floppy's label colour. A program with no screen registered opens as
 * {@link PendingApp} ("not available in this version"). Later steps add their programs in the static block below.
 */
public final class ComputerAppScreens {

	/** Makes a program's screen. */
	@FunctionalInterface
	public interface Factory {
		DosApp create(ComputerSession session, ComputerApp app);
	}

	private static final Map<String, Factory> FACTORIES = new HashMap<>();
	private static final Map<String, ComputerApp> OPENERS = new HashMap<>();
	private static final Map<String, Integer> LABELS = new HashMap<>();

	static {
		register(ComputerApps.EDITOR, EditorApp::new);
		register(ComputerApps.SYSINFO, SysInfoApp::new);
		register(ComputerApps.BAND_PLAN, BandPlanApp::new);
		register(ComputerApps.INSTALL, InstallApp::new);
		registerOpener(ComputerFileFormats.TEXT, ComputerApps.EDITOR);

		// Office programs (step 6).
		register(ComputerApps.BOOK_EDITOR, BookEditorApp::new);
		register(ComputerApps.LOGBOOK, LogbookApp::new);
		register(ComputerApps.FREQUENCY_MANAGER, FrequencyManagerApp::new);
		register(ComputerApps.PROGRAMMER, ProgrammerApp::new);
		register(ComputerApps.SOLAR_WEATHER, SolarWeatherApp::new);
		registerOpener(ComputerFileFormats.BOOK, ComputerApps.BOOK_EDITOR);
		registerOpener(ComputerFileFormats.LOG, ComputerApps.LOGBOOK);
		registerOpener(ComputerFileFormats.FREQUENCIES, ComputerApps.FREQUENCY_MANAGER);
		registerOpener(ComputerFileFormats.CODEPLUG, ComputerApps.PROGRAMMER);
		registerOpener(ComputerFileFormats.ADIF, ComputerApps.EDITOR); // ADIF is text: read it in EDIT.

		// Radio programs (step 7). Pictures (.IMG) stay with the shell's viewer; SSTV and WEFAX list them all.
		register(ComputerApps.APRS, com.arrl.radiocraft.client.screens.computer.apps.AprsApp::new);
		register(ComputerApps.WEFAX, com.arrl.radiocraft.client.screens.computer.apps.WefaxApp::new);
		register(ComputerApps.RADIO_FAX, com.arrl.radiocraft.client.screens.computer.apps.FaxApp::new);
		register(ComputerApps.SSTV, com.arrl.radiocraft.client.screens.computer.apps.SstvApp::new);
		register(ComputerApps.DIRECTION_FINDING, com.arrl.radiocraft.client.screens.computer.apps.DfApp::new);
		register(ComputerApps.ADSB, com.arrl.radiocraft.client.screens.computer.apps.AdsbApp::new); // 1.12 step 9.

		// Secret programs (step 8), from floppies found in chests.
		register(ComputerApps.SNAKE, com.arrl.radiocraft.client.screens.computer.apps.SnakeApp::new);
		register(ComputerApps.FALLING_BLOCKS, com.arrl.radiocraft.client.screens.computer.apps.PileupApp::new);
		register(ComputerApps.MUSIC, com.arrl.radiocraft.client.screens.computer.apps.MusicApp::new);
		register(ComputerApps.BIBLE, com.arrl.radiocraft.client.screens.computer.apps.BibleApp::new);

		// Floppy labels (0xRRGGBB): office programs blue, radio programs red, the secret ones gold, ADS-B green.
		label(ComputerApps.BOOK_EDITOR, 0x3A6FD8);
		label(ComputerApps.LOGBOOK, 0x2E5AAE);
		label(ComputerApps.FREQUENCY_MANAGER, 0x4C8FE0);
		label(ComputerApps.PROGRAMMER, 0x2A3F8F);
		label(ComputerApps.SOLAR_WEATHER, 0xE0B020);
		label(ComputerApps.APRS, 0xC83030);
		label(ComputerApps.WEFAX, 0xD86A2A);
		label(ComputerApps.RADIO_FAX, 0xB02848);
		label(ComputerApps.SSTV, 0xD84A8A);
		label(ComputerApps.DIRECTION_FINDING, 0x9A2A2A);
		label(ComputerApps.SNAKE, 0xE8C040);
		label(ComputerApps.FALLING_BLOCKS, 0xE8A030);
		label(ComputerApps.MUSIC, 0xD8D050);
		label(ComputerApps.BIBLE, 0xC8A060);
		label(ComputerApps.ADSB, 0x30A050);
	}

	private ComputerAppScreens() {}

	/** {@code app} runs in the screen {@code factory} makes. */
	public static void register(ComputerApp app, Factory factory) {
		FACTORIES.put(app.id(), factory);
	}

	/** Files ending {@code .extension} open in {@code app} from the shell (it gets {@link DosApp#openFile}). */
	public static void registerOpener(String extension, ComputerApp app) {
		OPENERS.put(extension.toUpperCase(Locale.ROOT), app);
	}

	/** The colour of {@code app}'s floppy label, 0xRRGGBB. */
	public static void label(ComputerApp app, int rgb) {
		LABELS.put(app.id(), rgb);
	}

	/** True if {@code app} has a screen (otherwise it opens as "not available"). */
	public static boolean isAvailable(ComputerApp app) {
		return FACTORIES.containsKey(app.id());
	}

	/** {@code app}'s screen, for {@code session}. */
	public static DosApp create(ComputerSession session, ComputerApp app) {
		Factory factory = FACTORIES.get(app.id());
		return factory == null ? new PendingApp(session, app) : factory.create(session, app);
	}

	/** The program that opens files with {@code extension}, or null. */
	@Nullable
	public static ComputerApp opener(String extension) {
		return OPENERS.get(extension.toUpperCase(Locale.ROOT));
	}

	/** The label colour (0xFFRRGGBB, for the item tint) of the floppy {@code stack}. */
	public static int labelColour(ItemStack stack) {
		ComputerApp app = FloppyDiskItem.app(stack);
		return 0xFF000000 | (app == null ? 0xF0F0E8 : LABELS.getOrDefault(app.id(), 0xF0F0E8));
	}
}
