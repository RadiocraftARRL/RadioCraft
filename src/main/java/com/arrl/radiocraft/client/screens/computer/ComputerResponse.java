package com.arrl.radiocraft.client.screens.computer;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * The server's answer to one request from the radio computer's screen ({@link ComputerSession#request}): its data, or
 * why it failed ({@link #error()}, a key under {@code screen.radiocraft.computer.err.}, with {@link #arg()}).
 */
public record ComputerResponse(CompoundTag data, @Nullable String error, String arg) {

	/** No answer came (the screen was closed and opened elsewhere, or the computer is gone). */
	public static final String LOST = "lost";

	public boolean ok() {
		return error == null;
	}

	/** The error as a sentence for the screen, or empty if it worked. */
	public String errorText() {
		return error == null ? "" : Dos.tr("err." + error, arg);
	}

	static ComputerResponse of(CompoundTag reply) {
		CompoundTag data = reply.getCompound("data");
		return reply.contains("error") ? new ComputerResponse(data, reply.getString("error"), reply.getString("arg")) : new ComputerResponse(data, null, "");
	}

	static ComputerResponse lost() {
		return new ComputerResponse(new CompoundTag(), LOST, "");
	}
}
