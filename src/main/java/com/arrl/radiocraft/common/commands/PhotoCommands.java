package com.arrl.radiocraft.common.commands;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.photo.PhotoAlbums;
import com.arrl.radiocraft.common.photo.PhotoLedger;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * A player's own camera photos, in chat, oldest first, with a Delete link on each: {@code /radiocraft photos [page]}.
 * Delete asks first ({@code /radiocraft photos delete <map>}), and only {@code ... delete <map> confirm} deletes (see
 * {@link PhotoLedger#delete}). A player at {@code camera_photo_cap} is sent {@link #offerDeletion}. Any player may use
 * it, on their own photos only.
 */
public final class PhotoCommands {

	/** Photos listed per page of chat. */
	public static final int PAGE = 8;
	private static final String COMMAND = "/radiocraft photos";

	public static final LiteralArgumentBuilder<CommandSourceStack> BUILDER = Commands.literal("radiocraft")
			.then(Commands.literal("photos")
					.executes(ctx -> list(ctx, 1))
					.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
					.then(Commands.literal("delete")
							.then(Commands.argument("map", IntegerArgumentType.integer(0))
									.executes(ctx -> delete(ctx, false))
									.then(Commands.literal("confirm").executes(ctx -> delete(ctx, true))))));

	private PhotoCommands() {}

	/** Tells {@code player} they are at the cap, with a link to their photos. */
	public static void offerDeletion(ServerPlayer player, int count, int cap) {
		player.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "camera.cap"), count, cap).withStyle(ChatFormatting.GOLD)
				.append(" ").append(link(Component.translatable(Radiocraft.translationKey("message", "camera.cap.choose")), COMMAND)));
	}

	private static int list(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<PhotoAlbums.Photo> photos = PhotoLedger.get(ctx.getSource().getServer()).albums().photos(player.getUUID());
		if(photos.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "photos.none")), false);
			return 0;
		}
		int pages = (photos.size() + PAGE - 1) / PAGE;
		int shown = Math.min(page, pages);
		ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "photos.header"), photos.size(), shown, pages), false);
		for(int i = (shown - 1) * PAGE; i < Math.min(photos.size(), shown * PAGE); i++) {
			PhotoAlbums.Photo photo = photos.get(i);
			MutableComponent line = describe(photo).append(" ")
					.append(link(Component.translatable(Radiocraft.translationKey("commands", "photos.delete")), COMMAND + " delete " + photo.mapId()));
			ctx.getSource().sendSuccess(() -> line, false);
		}
		if(shown < pages)
			ctx.getSource().sendSuccess(() -> link(Component.translatable(Radiocraft.translationKey("commands", "photos.more")), COMMAND + " " + (shown + 1)), false);
		return photos.size();
	}

	private static int delete(CommandContext<CommandSourceStack> ctx, boolean confirmed) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int mapId = IntegerArgumentType.getInteger(ctx, "map");
		PhotoLedger ledger = PhotoLedger.get(ctx.getSource().getServer());
		PhotoAlbums.Photo photo = ledger.albums().find(player.getUUID(), mapId);
		if(photo == null) {
			ctx.getSource().sendFailure(Component.translatable(Radiocraft.translationKey("commands", "photos.not_yours"), mapId));
			return 0;
		}
		if(!confirmed) { // Never without asking.
			ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "photos.confirm"), describe(photo)).withStyle(ChatFormatting.YELLOW)
					.append(" ").append(link(Component.translatable(Radiocraft.translationKey("commands", "photos.confirm.yes")).withStyle(ChatFormatting.RED), COMMAND + " delete " + mapId + " confirm")), false);
			return 0;
		}
		ledger.delete(player.serverLevel(), player.getUUID(), mapId);
		int left = ledger.albums().count(player.getUUID());
		ctx.getSource().sendSuccess(() -> Component.translatable(Radiocraft.translationKey("commands", "photos.deleted"), mapId, left), false);
		return 1;
	}

	/** "#12, day 34 (2026-11-04 18:20)". */
	private static MutableComponent describe(PhotoAlbums.Photo photo) {
		String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(photo.takenAt()));
		return Component.translatable(Radiocraft.translationKey("commands", "photos.entry"), photo.mapId(), photo.day(), when);
	}

	/** {@code text} in brackets, clickable, running {@code command}. */
	private static MutableComponent link(MutableComponent text, String command) {
		return Component.literal("[").append(text).append("]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
	}
}
