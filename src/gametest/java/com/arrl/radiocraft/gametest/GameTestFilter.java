package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs only some of the GameTests (2026-11-09): {@code ./gradlew runGameTestServer -Pgametests=RepeaterGameTests,SiteGameTests}
 * runs the tests in those classes (simple names, in this package), or a test's own name
 * ({@code -Pgametests=squelchShutsUnderTheMeter}). Without it every test runs, as before. For quick checks while
 * working; the whole suite still runs before a commit.
 * <p>
 * The GameTest server copies the registered tests when it is made and batches them once its world is up; this trims
 * that copy before then.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public class GameTestFilter {

	/** The system property the Gradle run passes {@code -Pgametests} in as. */
	public static final String PROPERTY = "radiocraft.gametests";

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		String wanted = System.getProperty(PROPERTY, "").trim();
		if(wanted.isEmpty() || !(event.getServer() instanceof GameTestServer server))
			return;
		Set<String> names = new HashSet<>();
		for(String entry : wanted.split(",")) {
			String name = entry.trim();
			if(name.isEmpty())
				continue;
			names.add(name.toLowerCase(Locale.ROOT)); // A test's own name.
			try { // A class: every test in it.
				Class<?> type = Class.forName(GameTestFilter.class.getPackageName() + "." + name);
				for(Method method : type.getDeclaredMethods())
					if(method.isAnnotationPresent(GameTest.class))
						names.add(method.getName().toLowerCase(Locale.ROOT));
			}
			catch(ClassNotFoundException ignored) {
			}
		}
		List<TestFunction> tests = ObfuscationReflectionHelper.getPrivateValue(GameTestServer.class, server, "testFunctions");
		if(tests == null)
			return;
		int before = tests.size();
		tests.removeIf(test -> !names.contains(test.testName().toLowerCase(Locale.ROOT)));
		Radiocraft.LOGGER.info("GameTest filter {}: running {} of {} tests", wanted, tests.size(), before);
	}
}
