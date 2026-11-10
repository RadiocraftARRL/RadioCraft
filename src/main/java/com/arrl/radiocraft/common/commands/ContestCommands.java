package com.arrl.radiocraft.common.commands;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.contest.ContestData;
import com.arrl.radiocraft.common.contest.ContestReport;
import com.arrl.radiocraft.common.contest.Contests;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /contest start [name]} and {@code /contest stop} (operators, permission level 2; 2026-11-09). Start announces
 * the contest to everyone; from then on every verified two-way contact counts ({@link Contests}). Stop announces the
 * end and gives the report book to whoever stopped it and to every participant online (from the console, the summary
 * goes to the server log).
 */
public final class ContestCommands {

	public static final int PERMISSION = 2;
	/** The longest name a contest may have. */
	public static final int MAX_NAME = 64;

	public static final LiteralArgumentBuilder<CommandSourceStack> BUILDER = Commands.literal("contest")
			.requires(source -> source.hasPermission(PERMISSION))
			.then(Commands.literal("start")
					.executes(ctx -> start(ctx, ""))
					.then(Commands.argument("name", StringArgumentType.greedyString())
							.executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(Commands.literal("stop")
					.executes(ContestCommands::stop));

	private ContestCommands() {}

	private static int start(CommandContext<CommandSourceStack> ctx, String name) {
		MinecraftServer server = ctx.getSource().getServer();
		String clean = name.strip();
		if(clean.length() > MAX_NAME)
			clean = clean.substring(0, MAX_NAME);
		ContestData data = ContestData.of(server);
		if(data.running()) {
			ctx.getSource().sendFailure(data.name().isBlank()
					? Component.translatable(key("already_running_unnamed"))
					: Component.translatable(key("already_running"), data.name()));
			return 0;
		}
		Contests.start(server, clean);
		server.getPlayerList().broadcastSystemMessage(clean.isBlank()
				? Component.translatable(key("started_unnamed"))
				: Component.translatable(key("started"), clean), false);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		String name = ContestData.of(server).name();
		ContestReport.Report report = Contests.stop(server, ctx.getSource().getPlayer());
		if(report == null) {
			ctx.getSource().sendFailure(Component.translatable(key("not_running")));
			return 0;
		}
		int contacts = report.contacts();
		int stations = report.stations();
		server.getPlayerList().broadcastSystemMessage(name.isBlank()
				? Component.translatable(key("stopped_unnamed"), contacts, stations)
				: Component.translatable(key("stopped"), name, contacts, stations), false);
		if(ctx.getSource().getPlayer() == null)
			ctx.getSource().sendSuccess(() -> Component.translatable(key("logged")), false);
		return Math.max(1, contacts);
	}

	private static String key(String suffix) {
		return Radiocraft.translationKey("commands", "contest." + suffix);
	}
}
