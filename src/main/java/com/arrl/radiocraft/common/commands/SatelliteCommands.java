package com.arrl.radiocraft.common.commands;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.items.SatelliteContractItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Satellites for operators (2026-11-11), permission level 2, for testing and moderation:
 * <ul>
 * <li>{@code /radiocraft satellite list}: every satellite, its frequencies, tone, owner, days left and where it is now
 * from where the command is run.</li>
 * <li>{@code /radiocraft satellite add amateur|commercial [owner]}: launches one as a contract does, the papers going
 * to the owner (the player running it if none is named). {@code add geostationary amateur|commercial [owner]}
 * (2026-11-12) launches a geostationary one pointing where the owner stands.</li>
 * <li>{@code /radiocraft satellite remove <name>}: takes it off orbit (its licenses stay).</li>
 * <li>{@code /radiocraft satellite decay <name>} (2026-11-12): its orbit decays now: it burns up (the streak in the
 * sky of the players it is over) and is gone.</li>
 * <li>{@code /radiocraft satellite overhead <name> [minutes]}: hangs it over the player for a while (default 5), to
 * try it out at once; it goes back to its orbit after.</li>
 * <li>{@code /radiocraft satellite eclipse <name> [minutes]}: puts it in the Earth's shadow on a flat battery for a while
 * (default 3; 2026-11-12), to try the silence and the low battery telemetry after without waiting for a rare one.</li>
 * </ul>
 */
public final class SatelliteCommands {

	public static final int PERMISSION = 2;

	public static final LiteralArgumentBuilder<CommandSourceStack> BUILDER = Commands.literal("radiocraft")
			.then(Commands.literal("satellite")
					.requires(source -> source.hasPermission(PERMISSION))
					.then(Commands.literal("list").executes(SatelliteCommands::list))
					.then(Commands.literal("add")
							.then(Commands.literal("amateur")
									.executes(ctx -> add(ctx, true, null))
									.then(Commands.argument("owner", EntityArgument.player()).executes(ctx -> add(ctx, true, EntityArgument.getPlayer(ctx, "owner")))))
							.then(Commands.literal("commercial")
									.executes(ctx -> add(ctx, false, null))
									.then(Commands.argument("owner", EntityArgument.player()).executes(ctx -> add(ctx, false, EntityArgument.getPlayer(ctx, "owner")))))
							.then(Commands.literal("geostationary") // 2026-11-12: parked, pointing where the owner stands.
									.then(Commands.literal("amateur")
											.executes(ctx -> add(ctx, true, true, null))
											.then(Commands.argument("owner", EntityArgument.player()).executes(ctx -> add(ctx, true, true, EntityArgument.getPlayer(ctx, "owner")))))
									.then(Commands.literal("commercial")
											.executes(ctx -> add(ctx, false, true, null))
											.then(Commands.argument("owner", EntityArgument.player()).executes(ctx -> add(ctx, false, true, EntityArgument.getPlayer(ctx, "owner")))))))
					.then(Commands.literal("remove")
							.then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
									.executes(SatelliteCommands::remove)))
					.then(Commands.literal("decay") // 2026-11-12: brings it down now, burning up as an orbit's decay does.
							.then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
									.executes(SatelliteCommands::decay)))
					.then(Commands.literal("overhead")
							.then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
									.executes(ctx -> overhead(ctx, 5))
									.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
											.executes(ctx -> overhead(ctx, IntegerArgumentType.getInteger(ctx, "minutes"))))))
					.then(Commands.literal("eclipse")
							.then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
									.executes(ctx -> eclipse(ctx, 3))
									.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
											.executes(ctx -> eclipse(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))))));

	private SatelliteCommands() {}

	private static List<String> names(MinecraftServer server) {
		return Satellites.of(server).all().stream().map(s -> s.name).toList();
	}

	private static String key(String key) {
		return Radiocraft.translationKey("commands", "satellite." + key);
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		var all = Satellites.of(server).all();
		if(all.isEmpty()) {
			source.sendSuccess(() -> Component.translatable(key("none")), false);
			return 0;
		}
		Vec3 at = source.getPosition();
		long now = server.overworld().getGameTime();
		for(Satellite s : all) {
			SatelliteOrbit.Look look = s.look(at.x, at.z, now);
			long days = s.daysLeft(System.currentTimeMillis());
			String where = look.up() ? String.format(java.util.Locale.ROOT, "%.0f/%s", look.elevation(), SatelliteOrbit.compass(look.azimuth()))
					: String.format(java.util.Locale.ROOT, "%.0f km", look.distance() / 1000.0D);
			source.sendSuccess(() -> Component.translatable(key("line"), s.name, CommercialLicenseRules.mhz(s.uplinkHz), CommercialLicenseRules.mhz(s.downlinkHz),
					CommercialLicenseRules.mhz(s.telemetryHz()), Ctcss.format(s.tone()), s.mode().name() + (s.geostationary() ? " GEO" : ""), s.ownerName, days,
					s.decommissioned() ? "DECOMMISSIONED" : s.isOn() ? "ON" : "OFF", where), false);
		}
		return all.size();
	}

	private static int add(CommandContext<CommandSourceStack> ctx, boolean amateur, ServerPlayer owner) throws CommandSyntaxException {
		return add(ctx, amateur, false, owner);
	}

	private static int add(CommandContext<CommandSourceStack> ctx, boolean amateur, boolean geostationary, ServerPlayer owner) throws CommandSyntaxException {
		ServerPlayer player = owner != null ? owner : ctx.getSource().getPlayerOrException();
		var items = SatelliteContractItem.launch(player.serverLevel(), player, amateur, geostationary);
		if(items == null) {
			ctx.getSource().sendFailure(Component.translatable(Radiocraft.translationKey("message", "satellite.no_pair")));
			return 0;
		}
		for(var item : items)
			if(!player.getInventory().add(item))
				player.drop(item, false);
		ctx.getSource().sendSuccess(() -> Component.translatable(key("added"), player.getDisplayName()), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		if(!Satellites.of(ctx.getSource().getServer()).remove(ctx.getSource().getServer(), name)) {
			ctx.getSource().sendFailure(Component.translatable(key("unknown"), name));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(key("removed"), name), true);
		return 1;
	}

	private static int decay(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		MinecraftServer server = ctx.getSource().getServer();
		Satellite satellite = Satellites.of(server).get(name);
		if(satellite == null) {
			ctx.getSource().sendFailure(Component.translatable(key("unknown"), name));
			return 0;
		}
		int told = Satellites.of(server).decayNow(server, satellite);
		ctx.getSource().sendSuccess(() -> Component.translatable(key("decayed"), satellite.name, told), true);
		return 1;
	}

	private static int overhead(CommandContext<CommandSourceStack> ctx, int minutes) {
		String name = StringArgumentType.getString(ctx, "name");
		MinecraftServer server = ctx.getSource().getServer();
		Satellite satellite = Satellites.of(server).get(name);
		if(satellite == null) {
			ctx.getSource().sendFailure(Component.translatable(key("unknown"), name));
			return 0;
		}
		Vec3 at = ctx.getSource().getPosition();
		Satellites.of(server).playPass(server, satellite, at.x, at.z, minutes * 1200L);
		ctx.getSource().sendSuccess(() -> Component.translatable(key("overhead"), satellite.name, minutes), true);
		return 1;
	}

	private static int eclipse(CommandContext<CommandSourceStack> ctx, int minutes) {
		String name = StringArgumentType.getString(ctx, "name");
		MinecraftServer server = ctx.getSource().getServer();
		Satellite satellite = Satellites.of(server).get(name);
		if(satellite == null) {
			ctx.getSource().sendFailure(Component.translatable(key("unknown"), name));
			return 0;
		}
		Satellites.of(server).eclipse(server, satellite, minutes * 1200L);
		ctx.getSource().sendSuccess(() -> Component.translatable(key("eclipse"), satellite.name, minutes), true);
		return 1;
	}
}
