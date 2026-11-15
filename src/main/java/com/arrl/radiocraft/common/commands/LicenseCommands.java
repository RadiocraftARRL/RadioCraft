package com.arrl.radiocraft.common.commands;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Random;

/**
 * Commercial frequency licenses for operators (2026-11-10), permission level 2:
 * <ul>
 * <li>{@code /radiocraft license grant <players> vhf|lowband [tier]}: issues each player a license (any licensing mode).</li>
 * <li>{@code /radiocraft license info <callsign>}: what a license grants and to whom.</li>
 * <li>{@code /radiocraft license revoke <callsign>}: the license no longer counts (the FCC app and programmer treat
 * its frequency as unlicensed); the item stays where it is.</li>
 * <li>{@code /radiocraft license list}: every license issued.</li>
 * </ul>
 */
public final class LicenseCommands {

	public static final int PERMISSION = 2;

	public static final LiteralArgumentBuilder<CommandSourceStack> BUILDER = Commands.literal("radiocraft")
			.then(Commands.literal("license")
					.requires(source -> source.hasPermission(PERMISSION))
					.then(Commands.literal("grant")
							.then(Commands.argument("players", EntityArgument.players())
									.then(Commands.literal("vhf")
											.executes(ctx -> grant(ctx, Band.VHF_COMMERCIAL, 0))
											.then(Commands.argument("tier", IntegerArgumentType.integer(0, CommercialLicense.MAX_TIER))
													.executes(ctx -> grant(ctx, Band.VHF_COMMERCIAL, IntegerArgumentType.getInteger(ctx, "tier")))))
									.then(Commands.literal("lowband")
											.executes(ctx -> grant(ctx, Band.LOW_BAND, 0))
											.then(Commands.argument("tier", IntegerArgumentType.integer(0, CommercialLicense.MAX_TIER))
													.executes(ctx -> grant(ctx, Band.LOW_BAND, IntegerArgumentType.getInteger(ctx, "tier")))))))
					.then(Commands.literal("info")
							.then(Commands.argument("callsign", StringArgumentType.word())
									.executes(LicenseCommands::info)))
					.then(Commands.literal("revoke")
							.then(Commands.argument("callsign", StringArgumentType.word())
									.executes(LicenseCommands::revoke)))
					.then(Commands.literal("list")
							.executes(LicenseCommands::list)));

	private LicenseCommands() {}

	private static int grant(CommandContext<CommandSourceStack> ctx, String band, int tier) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
		CommercialLicenses licenses = CommercialLicenses.of(ctx.getSource().getServer());
		int granted = 0;
		for(ServerPlayer player : players) {
			CommercialLicense license = licenses.issue(ctx.getSource().getServer(), band, player.getGameProfile().getName(),
					player.getStringUUID(), tier, new Random(player.getRandom().nextLong()));
			if(license == null) {
				ctx.getSource().sendFailure(Component.translatable(Radiocraft.translationKey("message", "license.band_full")));
				break;
			}
			net.minecraft.world.item.ItemStack item = CommercialLicenseItem.make(player.serverLevel(), license);
			if(!player.getInventory().add(item))
				player.drop(item, false);
			player.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "license.granted"),
					license.callsign(), CommercialLicenseRules.mhz(license.outputHz()), license.subscribers()));
			ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "license.granted"),
					player.getDisplayName(), license.callsign(), CommercialLicenseRules.mhz(license.outputHz())), true);
			granted++;
		}
		return granted;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommercialLicense license = CommercialLicenses.of(ctx.getSource().getServer()).get(StringArgumentType.getString(ctx, "callsign"));
		if(license == null) {
			ctx.getSource().sendFailure(Component.translatable(Radiocraft.translationKey("commands", "license.unknown"), StringArgumentType.getString(ctx, "callsign")));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> describe(license), false);
		java.util.List<com.arrl.radiocraft.common.radio.mesh.MeshNode> devices = com.arrl.radiocraft.common.radio.mesh.RadioMesh.of(ctx.getSource().getServer()).under(license.callsign());
		long bases = devices.stream().filter(node -> !node.role().relays()).count();
		ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "license.devices"), devices.size(), bases), false);
		return 1;
	}

	private static int revoke(CommandContext<CommandSourceStack> ctx) {
		String callsign = StringArgumentType.getString(ctx, "callsign");
		if(!CommercialLicenses.of(ctx.getSource().getServer()).revoke(callsign)) {
			ctx.getSource().sendFailure(Component.translatable(Radiocraft.translationKey("commands", "license.unknown"), callsign));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "license.revoked"), callsign.toUpperCase(java.util.Locale.ROOT)), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		Collection<CommercialLicense> all = CommercialLicenses.of(ctx.getSource().getServer()).all();
		if(all.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "license.none")), false);
			return 0;
		}
		for(CommercialLicense license : all)
			ctx.getSource().sendSuccess(() -> describe(license), false);
		return all.size();
	}

	private static Component describe(CommercialLicense license) {
		return Component.translatable(Radiocraft.translationKey("commands", "license.info"), license.callsign(), license.holder(),
				CommercialLicenseRules.mhz(license.outputHz()), license.inputHz() > 0 ? CommercialLicenseRules.mhz(license.inputHz()) : "-",
				license.subscribers(), license.bases(), license.issued());
	}
}
