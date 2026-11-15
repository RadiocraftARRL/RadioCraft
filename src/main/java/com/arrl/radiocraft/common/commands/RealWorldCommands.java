package com.arrl.radiocraft.common.commands;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.realworld.AudioBackend;
import com.arrl.radiocraft.common.realworld.Bridge;
import com.arrl.radiocraft.common.realworld.Bridges;
import com.arrl.radiocraft.common.realworld.InterfaceSpec;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /radiocraft realworld devices} and {@code status} (operators, permission level 3) for setting up Real-World
 * Interfaces in the server config: the sound card inputs and outputs and serial ports this server has, by the names
 * the config takes, and how each configured interface is doing. Both need {@code real_world_interface_enabled}; the
 * device list is gathered off the server thread (sound systems and serial drivers can be slow to answer).
 */
public final class RealWorldCommands {

	public static final int PERMISSION = 3;

	public static final LiteralArgumentBuilder<CommandSourceStack> BUILDER = Commands.literal("radiocraft")
			.then(Commands.literal("realworld")
					.requires(source -> source.hasPermission(PERMISSION))
					.then(Commands.literal("devices").executes(RealWorldCommands::devices))
					.then(Commands.literal("status").executes(RealWorldCommands::status))
					.then(Commands.literal("licence")
							.then(Commands.argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
									.then(Commands.literal("grant").executes(ctx -> licence(ctx, true)))
									.then(Commands.literal("revoke").executes(ctx -> licence(ctx, false))))));

	/** An operator confirms (or takes back) players' amateur licences for the Real-World Interface (2026-11-15). */
	private static int licence(CommandContext<CommandSourceStack> ctx, boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int count = 0;
		for(com.mojang.authlib.GameProfile profile : net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(ctx, "player")) {
			if(grant)
				com.arrl.radiocraft.common.realworld.RealWorldLicences.acknowledge(ctx.getSource().getServer(), profile.getId());
			else
				com.arrl.radiocraft.common.realworld.RealWorldLicences.revoke(ctx.getSource().getServer(), profile.getId());
			String name = profile.getName();
			ctx.getSource().sendSuccess(() -> tr(grant ? "licence.granted" : "licence.revoked", name), true);
			count++;
		}
		return count;
	}

	private RealWorldCommands() {}

	private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
		return Component.translatable(Radiocraft.translationKey("commands", "realworld." + key), args);
	}

	private static int devices(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if(!Bridges.enabled()) {
			source.sendFailure(tr("disabled"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> tr("listing"), false);
		Thread lister = new Thread(() -> {
			List<Component> lines = new ArrayList<>();
			try {
				AudioBackend backend = Bridges.backend();
				lines.add(tr("inputs").withStyle(ChatFormatting.GOLD));
				backend.inputs().forEach(name -> lines.add(Component.literal("  " + name)));
				lines.add(tr("outputs").withStyle(ChatFormatting.GOLD));
				backend.outputs().forEach(name -> lines.add(Component.literal("  " + name)));
				lines.add(tr("ports").withStyle(ChatFormatting.GOLD));
				backend.ports().forEach(name -> lines.add(Component.literal("  " + name)));
			}
			catch(RuntimeException | LinkageError e) {
				lines.add(Component.literal(String.valueOf(e)).withStyle(ChatFormatting.RED));
			}
			server.execute(() -> lines.forEach(line -> source.sendSuccess(() -> line, false)));
		}, "RadioCraft RWI device list");
		lister.setDaemon(true);
		lister.start();
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if(!Bridges.enabled()) {
			source.sendFailure(tr("disabled"));
			return 0;
		}
		List<InterfaceSpec> specs = Bridges.specs();
		if(specs.isEmpty())
			source.sendSuccess(() -> tr("none"), false);
		Map<String, Bridge> running = Bridges.running();
		for(InterfaceSpec spec : specs) {
			Object owner = Bridges.owner(spec.name());
			Bridge bridge = running.get(spec.name().toLowerCase(Locale.ROOT));
			String where = owner instanceof RealWorldInterfaceBlockEntity block ? block.getBlockPos().toShortString() : "-";
			String state = bridge == null ? "idle" : bridge.status().input() + " / " + bridge.status().output() + " / " + bridge.status().serial()
					+ (bridge.isPttKeyed() ? " PTT" : "") + (bridge.isUplinkOpen() ? " RX" : "");
			source.sendSuccess(() -> tr("line", spec.name(), where, state), false);
		}
		for(String problem : Bridges.problems())
			source.sendSuccess(() -> Component.literal(problem).withStyle(ChatFormatting.RED), false);
		return specs.size();
	}
}
