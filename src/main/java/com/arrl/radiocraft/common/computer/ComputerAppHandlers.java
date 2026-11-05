package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server sides of the radio computer's programs, by app id (see {@link ComputerAppHandler}). Register during mod
 * setup (or from a static initialiser the server loads, such as {@link ComputerServer}'s); a program without one
 * answers its app requests with "no_handler".
 */
public final class ComputerAppHandlers {

	private static final Map<String, ComputerAppHandler> HANDLERS = new ConcurrentHashMap<>();

	private ComputerAppHandlers() {}

	public static void register(ComputerApp app, ComputerAppHandler handler) {
		HANDLERS.put(app.id(), handler);
	}

	@Nullable
	public static ComputerAppHandler get(ComputerApp app) {
		return HANDLERS.get(app.id());
	}
}
