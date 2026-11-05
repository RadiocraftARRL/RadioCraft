package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.nbt.CompoundTag;

/**
 * The server side of one radio computer program (register it with {@link ComputerAppHandlers#register}). Its screen
 * sends it requests with {@code session.app(action, data, callback)}; they arrive here as {@code action} with
 * {@code data}, only while the program is installed on that computer and the player is at it. Answer each with
 * {@code reply} (once). Most programs need no handler at all: the file requests ({@code fs.*}), the scanner
 * ({@code scan.*}, {@code print.*}) and the system requests ({@code sys.*}) are the platform's (see
 * {@link ComputerServer}).
 */
public interface ComputerAppHandler {

	/** A request from the program's screen. Server thread. */
	void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply);

	/**
	 * A packet a radio interface on the computer's station bus decoded (APRS, fax...), on the server thread, whether
	 * anyone is at the computer or not; only for programs installed on it. Reach the players at the computer with
	 * {@link RadioComputerBlockEntity#push}.
	 */
	default void receive(RadioComputerBlockEntity computer, TncPacket packet) {}

	/** {@code context}'s player closed the computer's screen (stop what was running for them). */
	default void closed(ComputerContext context) {}

	/**
	 * Every server tick, for each computer it is installed on (1.12 step 7: timers, retries, pictures coming in line by
	 * line). Keep it cheap: most ticks should return at once. Computers whose programs all leave this alone cost
	 * nothing.
	 */
	default void tick(RadioComputerBlockEntity computer, long gameTime) {}

	/** True if {@link #tick} does anything, so the computer calls it (checked once per program, when installed or loaded). */
	default boolean ticks() {
		return false;
	}
}
