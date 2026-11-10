package com.arrl.radiocraft.common.contest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.advancements.ContactBook;
import com.arrl.radiocraft.common.advancements.RadiocraftAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Running a {@code /contest} (2026-11-09). While one runs, every verified two-way contact of the advancements'
 * {@link ContactBook} whose both hearings came after the start goes in its log ({@link #contact}), with both players'
 * names, the biome at each station and how each end sent. There is no detector of its own: the contact tracker calls
 * here at the moment it verifies a contact (a few times a minute at most, on the server thread), and with no contest
 * running that costs one saved-data lookup. Stopping it makes the report book ({@link ContestReport}).
 */
public final class Contests {

	private Contests() {}

	/** Starts contest {@code name} ("" for none); false if one is running already. */
	public static boolean start(MinecraftServer server, String name) {
		return ContestData.of(server).start(name, System.currentTimeMillis(), server.overworld().getGameTime());
	}

	/** The verified contact {@code contact} was made: logged if a contest runs and it is not the same contact still going on. Server thread. */
	public static void contact(MinecraftServer server, ContactBook.Contact contact) {
		ContestData data = ContestData.of(server);
		if(!data.running())
			return;
		ContactBook.Heard later = contact.later(); // A heard B.
		ContactBook.Heard earlier = contact.earlier(); // B heard A.
		if(earlier.time() < data.startGameTime() || later.time() < data.startGameTime())
			return; // Begun before the contest: it counts when they verify it again.
		UUID a = contact.a();
		UUID b = contact.b();
		if(ContestReport.isRepeat(data.qsos(), a, b, later.band(), later.time()))
			return;
		data.add(new ContestReport.Qso(a, name(server, a), b, name(server, b), later.hz(), earlier.hz(), later.y(), earlier.y(),
				earlier.mode(), later.mode(), biome(server, later), biome(server, earlier),
				contact.sameDimension() ? contact.distance() : -1.0D, later.band(), later.time()));
	}

	/**
	 * Ends the contest and builds its report; null if none was running. The book goes to {@code stopper} (if a player)
	 * and to every participant online; the summary goes to the server log, and the whole report too if the book is cut
	 * short.
	 */
	@Nullable
	public static ContestReport.Report stop(MinecraftServer server, @Nullable ServerPlayer stopper) {
		ContestData data = ContestData.of(server);
		if(!data.running())
			return null;
		String name = data.name();
		List<ContestReport.Qso> qsos = List.copyOf(data.qsos());
		ContestReport.Report report = ContestReport.build(name, data.startMillis(), System.currentTimeMillis(), ZoneId.systemDefault(), qsos);
		data.stop();

		Set<ServerPlayer> readers = new LinkedHashSet<>();
		if(stopper != null)
			readers.add(stopper);
		for(UUID id : participants(qsos)) {
			ServerPlayer online = server.getPlayerList().getPlayer(id); // Online players only, not GameTest stand-ins.
			if(online != null)
				readers.add(online);
		}
		for(ServerPlayer reader : readers) {
			give(reader, book(name, report));
			reader.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "contest.book")));
		}
		Radiocraft.LOGGER.info("Contest report{}:\n{}", name.isBlank() ? "" : " (" + name + ")", String.join("\n", report.cutShort() ? report.fullText() : report.summary()));
		return report;
	}

	/** Every player in {@code qsos}, in order of first appearance. */
	public static Set<UUID> participants(List<ContestReport.Qso> qsos) {
		Set<UUID> out = new LinkedHashSet<>();
		for(ContestReport.Qso q : qsos) {
			out.add(q.a());
			out.add(q.b());
		}
		return out;
	}

	/** The written book of {@code report}: titled with the contest's name, or "Contest Report". */
	public static ItemStack book(String name, ContestReport.Report report) {
		String title = name.isBlank() ? ContestReport.DEFAULT_TITLE : name.strip();
		if(title.length() > WrittenBookContent.TITLE_MAX_LENGTH)
			title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		List<Filterable<Component>> pages = new ArrayList<>();
		for(String page : report.pages())
			pages.add(Filterable.passThrough(Component.literal(page)));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), ContestReport.AUTHOR, 0, pages, true));
		return book;
	}

	/** Puts {@code stack} in {@code player}'s inventory, or at their feet if it is full. */
	private static void give(ServerPlayer player, ItemStack stack) {
		if(!player.getInventory().add(stack) && !stack.isEmpty())
			player.drop(stack, false);
	}

	/** A player's name: online (or a GameTest's), from the server's profile cache, else the start of their id. */
	public static String name(MinecraftServer server, UUID id) {
		ServerPlayer online = RadiocraftAdvancements.player(server, id);
		if(online != null)
			return online.getGameProfile().getName();
		var cache = server.getProfileCache();
		if(cache != null) {
			var profile = cache.get(id);
			if(profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isBlank())
				return profile.get().getName();
		}
		return id.toString().substring(0, 8);
	}

	/**
	 * The biome id at the station of {@code heard} ("" if its dimension is gone). The level answers from the chunk if it
	 * is loaded, else from the world generator, so no chunk is loaded for it.
	 */
	public static String biome(MinecraftServer server, ContactBook.Heard heard) {
		ResourceLocation dimension = ResourceLocation.tryParse(heard.dimension());
		if(dimension == null)
			return "";
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
		if(level == null)
			return "";
		BlockPos pos = new BlockPos(heard.x(), heard.y(), heard.z());
		if(!Level.isInSpawnableBounds(pos))
			return "";
		return level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
	}
}
