package com.arrl.radiocraft.common.commands;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.fcc.FccGear;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /radiocraft fcc badge <players>} (operators, permission level 2): gives each player an FCC badge with their
 * name and badge number on it ({@link FccGear#issueBadge}). The badge has no recipe; this is the only way to get one
 * outside creative tools.
 */
public final class FccCommands {

	/** Permission level needed: the game-master level of /give. */
	public static final int PERMISSION = 2;

	public static final LiteralArgumentBuilder<CommandSourceStack> BUILDER = Commands.literal("radiocraft")
			.then(Commands.literal("fcc")
					.requires(source -> source.hasPermission(PERMISSION))
					.then(Commands.literal("badge")
							.then(Commands.argument("players", EntityArgument.players())
									.executes(FccCommands::badge))));

	private FccCommands() {}

	private static int badge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
		for(ServerPlayer player : players) {
			FccGear.issueBadge(player);
			player.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "fcc_badge.issued")));
		}
		if(players.size() == 1) {
			Component name = players.iterator().next().getDisplayName();
			ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "fcc.badge.one"), name), true);
		}
		else
			ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "fcc.badge.many"), players.size()), true);
		return players.size();
	}
}
