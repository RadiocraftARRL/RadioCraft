package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.network.clientbound.CComputerPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * The answer to one request from a radio computer's screen. Answer exactly once, with {@link #ok} or {@link #error};
 * it may be kept and answered later on the server thread (after a slow job), as long as the player is still there.
 * The client gets a "reply" {@link CComputerPacket} holding {@code rid}, the {@code data} and, for an error, its
 * {@code error} key (a lang key suffix under {@code screen.radiocraft.computer.err.}) and {@code arg}.
 */
public final class ComputerReply {

	private final ServerPlayer player;
	private final int rid;
	private final Consumer<CompoundTag> out;
	private boolean answered = false;

	/** An answer sent to {@code player}'s screen. */
	public ComputerReply(ServerPlayer player, int rid) {
		this(player, rid, tag -> CComputerPacket.send(player, "reply", tag));
	}

	/** An answer handed to {@code out} as the packet's tag would hold it (GameTests read it there). */
	public ComputerReply(ServerPlayer player, int rid, Consumer<CompoundTag> out) {
		this.player = player;
		this.rid = rid;
		this.out = out;
	}

	public ServerPlayer player() {
		return player;
	}

	public boolean answered() {
		return answered;
	}

	public void ok(CompoundTag data) {
		send(data, null, "");
	}

	public void ok() {
		ok(new CompoundTag());
	}

	/** Refused: {@code key} names why (see the class comment). */
	public void error(String key) {
		error(key, "");
	}

	public void error(String key, String arg) {
		send(new CompoundTag(), key, arg);
	}

	public void error(ComputerStorage.Problem problem) {
		error(problem.key());
	}

	private void send(CompoundTag data, String error, String arg) {
		if(answered)
			return;
		answered = true;
		CompoundTag tag = new CompoundTag();
		tag.putInt("rid", rid);
		tag.put("data", data);
		if(error != null) {
			tag.putString("error", error);
			tag.putString("arg", arg);
		}
		out.accept(tag);
	}
}
