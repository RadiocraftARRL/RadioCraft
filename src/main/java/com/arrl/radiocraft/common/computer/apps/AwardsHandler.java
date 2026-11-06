package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.computer.AwardCertificate;
import com.arrl.radiocraft.common.computer.Awards;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.radio.Callsigns;
import com.arrl.radiocraft.common.radio.ft8.Ft8;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Awards program's server side (computer batch 3C): reads a log file from the computer's disk ({@code LOGBOOK.LOG}
 * unless told another), works out every contact's biome, dimension and distance, and answers each award's standing
 * ({@link Awards}). Every contact counts; nothing is confirmed.
 * <p>
 * <b>Where a contact was</b> comes only from coarse data in the log, never from anyone's live position: the
 * {@link Logbook.Where} the logbook stored (the grid square, dimension and biome the server worked out from the
 * snapshot taken when the station was heard, or the grid FT8 sent, or one typed in). With a grid but no biome the biome
 * is the one at the grid square's centre (read from the biome source, no chunks loaded); with no dimension it is this
 * computer's (radio does not cross dimensions, and a log is kept at its station); with no distance it is from this
 * computer to the grid square's centre, rounded to 100 blocks. A contact with none of these counts for every award but
 * biomes, grids and distance.
 * <ul>
 * <li>{@code status} {@code file}: {@code awards} (each {@code id}, {@code count}, {@code tiers}, {@code level},
 * {@code worked}, {@code missing}), {@code records} ({@code key}, {@code call}, {@code distance}, {@code date}),
 * {@code contacts}, {@code file}.</li>
 * <li>{@code certificate} {@code file}, {@code award}: prints the award's certificate for its level reached on the
 * scanner (paper, an empty map or photo paper).</li>
 * </ul>
 */
public final class AwardsHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		String file = data.getString("file").isBlank() ? LogbookHandler.LOG_FILE : ComputerStorage.normalise(data.getString("file"));
		if(file == null || !ComputerFileFormats.LOG.equals(ComputerStorage.extension(file))) {
			reply.error("awards_not_a_log");
			return;
		}
		CompoundTag tag = context.files().readNbt(file);
		Logbook log = tag == null ? new Logbook() : Logbook.fromTag(tag);
		List<Awards.Contact> contacts = contacts(log, context.server(), context.level(), context.pos());
		List<Awards.Progress> awards = Awards.compute(contacts, universe(context.server()));
		grantEarned(context.player(), awards);
		switch(action) {
			case "status" -> {
				CompoundTag out = new CompoundTag();
				out.putString("file", file);
				out.putInt("contacts", contacts.size());
				ListTag list = new ListTag();
				for(Awards.Progress p : awards)
					list.add(progressTag(p));
				out.put("awards", list);
				ListTag records = new ListTag();
				for(Awards.Record r : Awards.records(contacts)) {
					CompoundTag rt = new CompoundTag();
					rt.putString("key", r.key());
					rt.putString("call", r.call());
					rt.putInt("distance", r.distance());
					rt.putString("date", r.date());
					records.add(rt);
				}
				out.put("records", records);
				reply.ok(out);
			}
			case "certificate" -> {
				Awards.Progress progress = awards.stream().filter(p -> p.id().equals(data.getString("award"))).findFirst().orElse(null);
				if(progress == null || progress.level() == 0) {
					reply.error("awards_not_earned");
					return;
				}
				ScannerBlockEntity scanner = context.scanner();
				if(scanner == null) {
					reply.error("no_scanner");
					return;
				}
				String call = Callsigns.playerCallsign(context.server(), context.player().getUUID());
				if(call == null || call.isBlank())
					call = context.player().getGameProfile().getName();
				byte[] picture = certificate(progress, call, Logbook.utcDate(System.currentTimeMillis()));
				ScannerBlockEntity.Result result = scanner.printImage(picture, Awards.title(progress.id()), context.player().getGameProfile().getName(), true);
				if(result != ScannerBlockEntity.Result.OK) {
					reply.error(result.key());
					return;
				}
				reply.ok(new CompoundTag());
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * The advancements an award earns: Worked All Biomes once the award of that name is complete. Shared with the
	 * program, so earning the award is earning the advancement.
	 */
	public static void grantEarned(net.minecraft.server.level.ServerPlayer player, List<Awards.Progress> awards) {
		for(Awards.Progress p : awards)
			if(p.id().equals("biomes") && p.complete())
				com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(player, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.WORKED_ALL_BIOMES);
	}

	/** The certificate picture for {@code progress}'s level reached. */
	public static byte[] certificate(Awards.Progress progress, String call, String yyyymmdd) {
		String date = yyyymmdd.length() == 8 ? yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6) : yyyymmdd;
		String detail = progress.id().equals("distance") ? "LONGEST " + progress.count() + " BLOCKS" : progress.count() + " SO FAR";
		return AwardCertificate.render(Awards.title(progress.id()), Awards.levelText(progress), call, date, detail);
	}

	private static CompoundTag progressTag(Awards.Progress p) {
		CompoundTag t = new CompoundTag();
		t.putString("id", p.id());
		t.putLong("count", p.count());
		ListTag tiers = new ListTag();
		for(int tier : p.tiers())
			tiers.add(IntTag.valueOf(tier));
		t.put("tiers", tiers);
		t.putInt("level", p.level());
		t.put("worked", strings(p.worked()));
		t.put("missing", strings(p.missing()));
		return t;
	}

	private static ListTag strings(List<String> list) {
		ListTag out = new ListTag();
		for(String s : list)
			out.add(StringTag.valueOf(s));
		return out;
	}

	/** Every logbook entry as the awards see it (see the class comment). */
	public static List<Awards.Contact> contacts(Logbook log, MinecraftServer server, ServerLevel here, BlockPos pos) {
		String hereDim = here.dimension().location().toString();
		List<Awards.Contact> out = new ArrayList<>();
		for(Logbook.Entry e : log.entries()) {
			Logbook.Where w = e.where();
			String dim = w.dimension().isEmpty() ? hereDim : w.dimension();
			String biome = w.biome();
			if(biome.isEmpty() && !w.grid().isEmpty()) {
				ServerLevel level = LogbookHandler.levelOf(server, dim);
				if(level != null)
					biome = LogbookHandler.biomeAtGrid(level, w.grid());
			}
			int distance = w.distance();
			if(distance < 0 && !w.grid().isEmpty() && dim.equals(hereDim)) {
				int[] centre = Ft8.gridCentre(w.grid());
				if(centre != null)
					distance = Logbook.Where.coarse(Math.sqrt(Math.pow(centre[0] - pos.getX(), 2) + Math.pow(centre[1] - pos.getZ(), 2)));
			}
			out.add(new Awards.Contact(e.call(), e.band(), Awards.modeClass(e.mode()), biome, dim, w.grid(), distance, Awards.gameHour(e.gameTime()), e.date()));
		}
		return out;
	}

	/** Every biome any dimension of the server can make, and every dimension. */
	public static Awards.Universe universe(MinecraftServer server) {
		Set<String> biomes = new LinkedHashSet<>();
		List<String> dims = new ArrayList<>();
		for(ResourceKey<Level> key : server.levelKeys()) {
			dims.add(key.location().toString());
			ServerLevel level = server.getLevel(key);
			if(level != null)
				level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()
						.forEach(holder -> holder.unwrapKey().ifPresent(k -> biomes.add(k.location().toString())));
		}
		List<String> sorted = new ArrayList<>(biomes);
		sorted.sort(null);
		dims.sort(null);
		return new Awards.Universe(sorted, dims);
	}
}
