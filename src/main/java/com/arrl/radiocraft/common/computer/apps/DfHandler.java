package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.DirectionFinderBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.DfSearches;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.DirectionFinding;
import com.arrl.radiocraft.common.radio.data.AprsMapPainter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The direction finding program's server side (1.12 step 7): the computer as the display of a direction finder cabled
 * to it (touching or on its data wire; the first one found). It reads the set's bearing and the bearings typed into it
 * or coming over microwave, works out the fix ({@link DirectionFinding#triangulate}) and paints a terrain map of the
 * search for the screen; it can switch the set on, tune it and type bearings in, as its own panel does; and it keeps
 * past searches in {@link DfSearches#FILE} on the disk.
 * <ul>
 * <li>{@code status}: {@code finder} (one is cabled), {@code on}, {@code switchedOn}, {@code powered},
 * {@code frequency} (Hz), {@code band}, {@code modules}, {@code usable}, {@code locked}, {@code hearing},
 * {@code meter}, {@code bearing}/{@code sigma} (with {@code hasBearing}), {@code x}/{@code z} (the set),
 * {@code bearings} (each {@code x}, {@code z}, {@code bearing}, {@code sigma}, {@code source}) and the fix
 * ({@code fix}: {@code found}, {@code problem}, {@code x}, {@code z}, {@code radius}).</li>
 * <li>{@code map} ({@code search}: a saved search's index, or -1 for now): {@code pixels} (128 square terrain),
 * {@code cx}, {@code cz}, {@code bpp} (the picture's middle and blocks per pixel), and the bearings and fix drawn on it
 * ({@code bearings}, {@code fix}, as in status).</li>
 * <li>{@code power}, {@code tune} ({@code steps}), {@code band} ({@code direction}), {@code frequency}
 * ({@code mhz}), {@code add} ({@code x}, {@code z}, {@code bearing}, {@code sigma}), {@code remove}
 * ({@code index} of a typed bearing).</li>
 * <li>{@code save} ({@code note}): the bearings now as a search; {@code searches}: each {@code utc},
 * {@code frequency}, {@code note}, {@code count} and its fix; {@code delete} ({@code index}).</li>
 * </ul>
 */
public final class DfHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		DirectionFinderBlockEntity finder = finder(context);
		switch(action) {
			case "status" -> reply.ok(status(finder));
			case "searches" -> reply.ok(searchesTag(load(context)));
			case "delete" -> {
				DfSearches searches = load(context);
				if(!searches.remove(data.getInt("index"))) {
					reply.error("df_no_search");
					return;
				}
				answer(reply, store(context, searches));
			}
			case "map" -> map(context, finder, data.getInt("search"), reply);
			default -> {
				if(finder == null) {
					reply.error("df_no_finder");
					return;
				}
				control(context, finder, action, data, reply);
			}
		}
	}

	private static void answer(ComputerReply reply, @Nullable ComputerStorage.Problem problem) {
		if(problem == null)
			reply.ok();
		else
			reply.error(problem);
	}

	/** The direction finder cabled to the computer (touching or on its data wire), or null. */
	@Nullable
	public static DirectionFinderBlockEntity finder(ComputerContext context) {
		List<DirectionFinderBlockEntity> found = context.linked(DirectionFinderBlockEntity.class);
		return found.isEmpty() ? null : found.getFirst();
	}

	private static void control(ComputerContext context, DirectionFinderBlockEntity finder, String action, CompoundTag data, ComputerReply reply) {
		switch(action) {
			case "power" -> {
				finder.togglePower();
				reply.ok();
			}
			case "tune" -> {
				finder.tune(Math.max(-1000, Math.min(1000, data.getInt("steps"))));
				reply.ok();
			}
			case "band" -> {
				finder.changeBand(data.getInt("direction") < 0 ? -1 : 1);
				reply.ok();
			}
			case "frequency" -> {
				if(finder.setFrequency(data.getFloat("mhz") * 1_000_000.0F))
					reply.ok();
				else
					reply.error("df_out_of_band");
			}
			case "add" -> {
				DirectionFinderBlockEntity.Entry entry = new DirectionFinderBlockEntity.Entry(data.getInt("x"), data.getInt("z"), data.getDouble("bearing"), data.getDouble("sigma"));
				if(finder.addEntry(entry))
					reply.ok();
				else
					reply.error("df_entries_full");
			}
			case "remove" -> {
				finder.removeEntry(data.getInt("index"));
				reply.ok();
			}
			case "save" -> {
				List<DfSearches.Bearing> bearings = bearings(finder);
				if(bearings.isEmpty()) {
					reply.error("df_nothing_to_save");
					return;
				}
				DfSearches searches = load(context);
				searches.add(new DfSearches.Search(System.currentTimeMillis(), context.level().getGameTime(), finder.getFrequency(), data.getString("note"), bearings));
				answer(reply, store(context, searches));
			}
			default -> reply.error("unknown_request", action);
		}
	}

	// -------------------- READING THE SET --------------------

	/** Every bearing the set has: its own, those typed in, those over microwave. */
	public static List<DfSearches.Bearing> bearings(DirectionFinderBlockEntity finder) {
		List<DfSearches.Bearing> out = new ArrayList<>();
		if(finder.hasBearing()) {
			BlockPos here = finder.here();
			out.add(new DfSearches.Bearing(here.getX() + 0.5D, here.getZ() + 0.5D, finder.getBearing(), finder.getSigma(), DfSearches.Source.OWN));
		}
		for(DirectionFinderBlockEntity.Entry entry : finder.getEntries())
			out.add(new DfSearches.Bearing(entry.x() + 0.5D, entry.z() + 0.5D, entry.bearing(), entry.sigma(), DfSearches.Source.TYPED));
		for(DirectionFinderBlockEntity.Linked linked : finder.getLinked())
			out.add(new DfSearches.Bearing(linked.line().x(), linked.line().z(), linked.line().bearing(), linked.line().sigma(), DfSearches.Source.LINKED));
		return out;
	}

	private static CompoundTag status(@Nullable DirectionFinderBlockEntity finder) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("finder", finder != null);
		if(finder == null)
			return out;
		out.putBoolean("on", finder.isOn());
		out.putBoolean("switchedOn", finder.isSwitchedOn());
		out.putBoolean("powered", finder.isPowered());
		out.putFloat("frequency", finder.getFrequency());
		Band band = DirectionFinderBlockEntity.bandOf(finder.getFrequency());
		out.putString("band", band.name());
		out.putInt("modules", finder.getModules().size());
		out.putInt("usable", finder.usableModules());
		out.putInt("locked", finder.getLocked());
		out.putBoolean("hearing", finder.isHearing());
		out.putDouble("meter", finder.getMeter());
		out.putBoolean("hasBearing", finder.hasBearing());
		if(finder.hasBearing()) {
			out.putDouble("bearing", finder.getBearing());
			out.putDouble("sigma", finder.getSigma());
		}
		BlockPos here = finder.here();
		out.putInt("x", here.getX());
		out.putInt("z", here.getZ());
		List<DfSearches.Bearing> bearings = bearings(finder);
		out.put("bearings", bearingsTag(bearings));
		out.put("fix", fixTag(fix(bearings)));
		ListTag typed = new ListTag();
		for(DirectionFinderBlockEntity.Entry entry : finder.getEntries())
			typed.add(StringTag.valueOf(String.format(Locale.ROOT, "%d %d %.1f %.1f", entry.x(), entry.z(), entry.bearing(), entry.sigma())));
		out.put("typed", typed);
		return out;
	}

	private static DirectionFinding.Fix fix(List<DfSearches.Bearing> bearings) {
		List<DirectionFinding.Line> lines = new ArrayList<>();
		for(DfSearches.Bearing bearing : bearings)
			lines.add(bearing.line());
		return DirectionFinding.triangulate(lines);
	}

	static ListTag bearingsTag(List<DfSearches.Bearing> bearings) {
		ListTag list = new ListTag();
		for(DfSearches.Bearing bearing : bearings) {
			CompoundTag tag = new CompoundTag();
			tag.putDouble("x", bearing.x());
			tag.putDouble("z", bearing.z());
			tag.putDouble("bearing", bearing.bearing());
			tag.putDouble("sigma", bearing.sigma());
			tag.putString("source", bearing.source().name());
			list.add(tag);
		}
		return list;
	}

	static CompoundTag fixTag(DirectionFinding.Fix fix) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("found", fix.found());
		tag.putString("problem", fix.problem().name());
		if(fix.found()) {
			tag.putDouble("x", fix.x());
			tag.putDouble("z", fix.z());
			tag.putDouble("radius", fix.radius());
		}
		return tag;
	}

	// -------------------- THE MAP --------------------

	/** The terrain under a search (now, or saved search {@code index}), fitted round its stations and its fix. */
	private static void map(ComputerContext context, @Nullable DirectionFinderBlockEntity finder, int index, ComputerReply reply) {
		List<DfSearches.Bearing> bearings;
		BlockPos home;
		if(index >= 0) {
			DfSearches searches = load(context);
			if(index >= searches.size()) {
				reply.error("df_no_search");
				return;
			}
			bearings = searches.all().get(index).bearings();
			home = bearings.isEmpty() ? context.pos() : BlockPos.containing(bearings.getFirst().x(), 0, bearings.getFirst().z());
		}
		else {
			if(finder == null) {
				reply.error("df_no_finder");
				return;
			}
			bearings = bearings(finder);
			home = finder.here();
		}
		DirectionFinding.Fix fix = fix(bearings);
		List<BlockPos> points = new ArrayList<>();
		for(DfSearches.Bearing bearing : bearings)
			points.add(BlockPos.containing(bearing.x(), 0, bearing.z()));
		if(fix.found()) { // The fix and its box in the picture.
			points.add(BlockPos.containing(fix.x() - fix.radius(), 0, fix.z() - fix.radius()));
			points.add(BlockPos.containing(fix.x() + fix.radius(), 0, fix.z() + fix.radius()));
		}
		else if(bearings.size() == 1) { // One bearing: show a good way along it.
			DfSearches.Bearing only = bearings.getFirst();
			double reach = 300.0D;
			points.add(BlockPos.containing(only.x() + Math.sin(Math.toRadians(only.bearing())) * reach, 0, only.z() - Math.cos(Math.toRadians(only.bearing())) * reach));
		}
		ServerLevel level = context.level();
		AprsMapPainter.Fit fit = AprsMapPainter.fit(home, points, AprsMapPainter.SIZE, AprsMapPainter.SIZE);
		CompoundTag out = new CompoundTag();
		out.putByteArray("pixels", AprsMapPainter.paint(level, fit, home, List.of()));
		out.putInt("cx", fit.centerX());
		out.putInt("cz", fit.centerZ());
		out.putInt("bpp", fit.blocksPerPixel());
		out.put("bearings", bearingsTag(bearings));
		out.put("fix", fixTag(fix));
		reply.ok(out);
	}

	// -------------------- SAVED SEARCHES --------------------

	private static DfSearches load(ComputerContext context) {
		CompoundTag tag = context.files().readNbt(DfSearches.FILE);
		return tag == null ? new DfSearches() : DfSearches.fromTag(tag);
	}

	@Nullable
	private static ComputerStorage.Problem store(ComputerContext context, DfSearches searches) {
		return context.files().write(DfSearches.FILE, ComputerFileFormats.nbt(searches.toTag()));
	}

	private static CompoundTag searchesTag(DfSearches searches) {
		ListTag list = new ListTag();
		for(DfSearches.Search search : searches.all()) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("utc", search.utcMillis());
			tag.putFloat("frequency", search.frequencyHz());
			tag.putString("note", search.note());
			tag.putInt("count", search.bearings().size());
			tag.put("fix", fixTag(search.fix()));
			list.add(tag);
		}
		CompoundTag out = new CompoundTag();
		out.put("searches", list);
		return out;
	}
}
