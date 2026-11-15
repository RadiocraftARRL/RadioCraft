package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.ComputerRedstoneBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.computer.ComputerReply;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * LAN REDSTONE (2026-11-13, doc/Plan-Computer4.md): this computer's Computer Redstone blocks, and the ones on other
 * computers of the LAN this player or this computer may set.
 * <ul>
 * <li>{@code status}: {@code name} (this computer's LAN name), {@code editable}, {@code local} and {@code remote} lists of
 * blocks ({@code computer} "" for this one, {@code pos}, {@code name}, {@code out}, {@code in}, {@code outText},
 * {@code inText}, {@code entries} of {@code label}/{@code level}; local ones also {@code players} and {@code computers}).</li>
 * <li>{@code set} ({@code computer}, {@code pos}, {@code level}, {@code pulse}): sets a block: on at a level, off (0), or
 * a pulse.</li>
 * <li>{@code rename} ({@code pos}, {@code name}), {@code entry} ({@code pos}, {@code label}, {@code level}),
 * {@code unentry} ({@code pos}, {@code index}), {@code access} ({@code pos}, {@code players}, {@code computers}): this
 * computer's blocks, by its owner (anyone if it has none, and ops).</li>
 * </ul>
 * Who may set a block on another computer: its computer's owner, an op, a player on its player list, or anyone at a
 * computer on its computer list. At its own computer anyone who can use the computer may.
 */
public final class RedstoneHandler implements ComputerAppHandler {

	/** The Computer Redstone blocks a computer drives: touching it or on its data wire, the first ten. */
	public static List<ComputerRedstoneBlockEntity> blocks(RadioComputerBlockEntity computer) {
		Level level = computer.getLevel();
		if(level == null)
			return List.of();
		List<ComputerRedstoneBlockEntity> found = ComputerContext.linked(level, computer.getBlockPos(), ComputerRedstoneBlockEntity.class);
		return found.size() > ComputerRedstoneBlockEntity.PER_COMPUTER ? found.subList(0, ComputerRedstoneBlockEntity.PER_COMPUTER) : found;
	}

	/** True if {@code player}, at {@code from}, may set {@code block}, which {@code owner} drives. */
	public static boolean maySet(ServerPlayer player, RadioComputerBlockEntity from, RadioComputerBlockEntity owner, ComputerRedstoneBlockEntity block) {
		if(from == owner || player.hasPermissions(2))
			return true;
		UUID ownerId = owner.files().owner();
		if(ownerId != null && ownerId.equals(player.getUUID()))
			return true;
		return block.allows(player.getGameProfile().getName(), ComputerNetwork.name(from));
	}

	private static boolean mayEdit(ComputerContext context) {
		UUID owner = context.files().owner();
		return owner == null || owner.equals(context.player().getUUID()) || context.player().hasPermissions(2);
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		switch(action) {
			case "status" -> reply.ok(status(context));
			case "set" -> {
				String target = data.getString("computer");
				RadioComputerBlockEntity owner = target.isEmpty() ? computer : peer(computer, target);
				if(owner == null) {
					reply.error("redstone_no_computer");
					return;
				}
				ComputerRedstoneBlockEntity block = find(owner, data.getLong("pos"));
				if(block == null) {
					reply.error("redstone_no_block");
					return;
				}
				if(!maySet(context.player(), computer, owner, block)) {
					reply.error("redstone_not_allowed");
					return;
				}
				int level = Math.max(0, Math.min(15, data.getInt("level")));
				if(data.getBoolean("pulse"))
					block.pulse(level);
				else
					block.set(level);
				reply.ok();
			}
			case "rename", "entry", "unentry", "access" -> {
				if(!mayEdit(context)) {
					reply.error("redstone_not_owner");
					return;
				}
				ComputerRedstoneBlockEntity block = find(computer, data.getLong("pos"));
				if(block == null) {
					reply.error("redstone_no_block");
					return;
				}
				switch(action) {
					case "rename" -> block.setName(data.getString("name"));
					case "entry" -> {
						if(!block.putEntry(data.getString("label"), data.getInt("level"))) {
							reply.error("redstone_bad_entry");
							return;
						}
					}
					case "unentry" -> block.removeEntry(data.getInt("index"));
					default -> block.setAccess(data.getString("players"), data.getString("computers"));
				}
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	@Nullable
	private static RadioComputerBlockEntity peer(RadioComputerBlockEntity computer, String name) {
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(computer))
			if(peer.link().up() && ComputerNetwork.sameName(ComputerNetwork.name(peer.computer()), name))
				return peer.computer();
		return null;
	}

	@Nullable
	private static ComputerRedstoneBlockEntity find(RadioComputerBlockEntity computer, long pos) {
		for(ComputerRedstoneBlockEntity block : blocks(computer))
			if(block.getBlockPos().asLong() == pos)
				return block;
		return null;
	}

	private static CompoundTag status(ComputerContext context) {
		RadioComputerBlockEntity computer = context.computer();
		CompoundTag out = new CompoundTag();
		out.putString("name", ComputerNetwork.name(computer));
		out.putBoolean("editable", mayEdit(context));
		ListTag local = new ListTag();
		for(ComputerRedstoneBlockEntity block : blocks(computer)) {
			CompoundTag b = block(block, "");
			ListTag players = new ListTag();
			block.getPlayers().forEach(p -> players.add(StringTag.valueOf(p)));
			b.put("players", players);
			ListTag computers = new ListTag();
			block.getComputers().forEach(c -> computers.add(StringTag.valueOf(c)));
			b.put("computers", computers);
			local.add(b);
		}
		out.put("local", local);
		ListTag remote = new ListTag();
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(computer)) {
			String name = ComputerNetwork.name(peer.computer());
			if(!peer.link().up() || name.isEmpty())
				continue; // Only named computers can be reached by name.
			for(ComputerRedstoneBlockEntity block : blocks(peer.computer()))
				if(maySet(context.player(), computer, peer.computer(), block))
					remote.add(block(block, name));
		}
		out.put("remote", remote);
		return out;
	}

	private static CompoundTag block(ComputerRedstoneBlockEntity block, String computer) {
		CompoundTag b = new CompoundTag();
		b.putString("computer", computer);
		b.putLong("pos", block.getBlockPos().asLong());
		b.putString("name", block.getName());
		b.putInt("out", block.getOutput());
		b.putInt("in", block.getInput());
		b.putString("outText", block.describe(block.getOutput()));
		b.putString("inText", block.describe(block.getInput()));
		ListTag entries = new ListTag();
		for(ComputerRedstoneBlockEntity.Entry e : block.getEntries()) {
			CompoundTag t = new CompoundTag();
			t.putString("label", e.label());
			t.putInt("level", e.level());
			entries.add(t);
		}
		b.put("entries", entries);
		return b;
	}
}
