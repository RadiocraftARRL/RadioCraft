package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.RepeaterDirectory;
import com.arrl.radiocraft.common.radio.RepeaterRegistry;
import com.arrl.radiocraft.common.radio.StationsHeard;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The Repeater Directory's server side (computer apps 2, 2026-11-06). The directory is the file
 * {@link RepeaterDirectory#FILE} on the computer's disk. Each {@code list} first collects: every radio the computer
 * reaches (touching it, on its data wire, or on a radio interface of its station: {@link SolarWeatherHandler#radios})
 * remembers the last repeaters it heard send their own beep, carrier or ID ({@code RadioBlockEntity#getRepeatersHeard}),
 * and each is looked up in {@link RepeaterRegistry} and merged in. Nothing ticks: radios keep their last 8.
 * <ul>
 * <li>{@code list}: {@code radios} (how many were asked), {@code found} (new or updated entries), {@code entries}
 * (each {@code i}, {@code call}, {@code notes}, {@code out}, {@code offset} kHz, {@code tone}, {@code hasPos}, {@code x},
 * {@code z}, {@code distance} and {@code bearing} from the computer (-1 unknown), {@code source}, {@code ago} seconds
 * since last heard (-1 never)).</li>
 * <li>{@code add} / {@code edit} ({@code index}, {@code key}): {@code call}, {@code notes}, {@code out} MHz,
 * {@code offset} kHz, {@code tone}, {@code hasPos}, {@code x}, {@code z}.</li>
 * <li>{@code delete} ({@code index}, {@code key}).</li>
 * <li>{@code export} ({@code file}, {@code indices}): appended to that {@code .FRQ} (made if missing); answers
 * {@code added}, {@code duplicates}, {@code full}, {@code file}.</li>
 * </ul>
 * {@code key} is the entry's {@link #key} as last listed, so a list another player changed meanwhile is caught
 * ({@code rptdir_changed}).
 */
public final class RepeaterDirectoryHandler implements ComputerAppHandler {

	/** The frequency list an export goes to unless another is named. */
	public static final String DEFAULT_EXPORT = "CHANNELS.FRQ";

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RepeaterDirectory directory = RepeaterDirectory.fromTag(context.files().readNbt(RepeaterDirectory.FILE));
		switch(action) {
			case "list" -> {
				int[] radios = new int[1];
				int found = collect(context, directory, radios);
				if(found > 0) {
					ComputerStorage.Problem problem = save(context, directory);
					if(problem != null) {
						reply.error(problem);
						return;
					}
				}
				CompoundTag out = list(context, directory);
				out.putInt("radios", radios[0]);
				out.putInt("found", found);
				reply.ok(out);
			}
			case "add" -> {
				RepeaterDirectory.Entry entry = entry(data, null);
				if(entry == null) {
					reply.error("rptdir_bad_frequency");
					return;
				}
				if(!directory.add(entry)) {
					reply.error("rptdir_full");
					return;
				}
				answer(reply, save(context, directory));
			}
			case "edit" -> {
				int index = checkedIndex(directory, data);
				if(index < 0) {
					reply.error("rptdir_changed");
					return;
				}
				RepeaterDirectory.Entry entry = entry(data, directory.get(index));
				if(entry == null) {
					reply.error("rptdir_bad_frequency");
					return;
				}
				directory.set(index, entry);
				answer(reply, save(context, directory));
			}
			case "delete" -> {
				int index = checkedIndex(directory, data);
				if(index < 0) {
					reply.error("rptdir_changed");
					return;
				}
				directory.remove(index);
				answer(reply, save(context, directory));
			}
			case "export" -> export(context, directory, data, reply);
			default -> reply.error("unknown_request", action);
		}
	}

	private static void answer(ComputerReply reply, ComputerStorage.Problem problem) {
		if(problem == null)
			reply.ok();
		else
			reply.error(problem);
	}

	private static ComputerStorage.Problem save(ComputerContext context, RepeaterDirectory directory) {
		return context.files().writeNbt(RepeaterDirectory.FILE, directory.toTag());
	}

	/** What an entry is known by between a list and a change: callsign and output frequency. */
	public static String key(RepeaterDirectory.Entry entry) {
		return entry.callsign() + "@" + Math.round(entry.outputMhz() * 10_000.0D);
	}

	/** The index named by {@code data} if it still holds the entry {@code key} names, else -1. */
	private static int checkedIndex(RepeaterDirectory directory, CompoundTag data) {
		int index = data.getInt("index");
		if(index < 0 || index >= directory.size() || !key(directory.get(index)).equals(data.getString("key")))
			return -1;
		return index;
	}

	/**
	 * The entry typed in {@code data}; with {@code before}, an edit of it (when and where it was heard kept). Null if
	 * there is no output frequency.
	 */
	private static RepeaterDirectory.Entry entry(CompoundTag data, RepeaterDirectory.Entry before) {
		double out = data.getDouble("out");
		if(!(out > 0.0D) || out > 3000.0D)
			return null;
		boolean hasPos = data.getBoolean("hasPos");
		if(before == null)
			return RepeaterDirectory.Entry.manual(data.getString("call"), data.getString("notes"), out, data.getInt("offset"), data.getFloat("tone"),
					hasPos, data.getInt("x"), data.getInt("z"));
		boolean moved = hasPos && (!before.hasPos() || before.x() != data.getInt("x") || before.z() != data.getInt("z"));
		return new RepeaterDirectory.Entry(data.getString("call"), data.getString("notes"), out, data.getInt("offset"), data.getFloat("tone"), hasPos,
				before.dimension(), hasPos ? data.getInt("x") : 0, moved ? 0 : before.y(), hasPos ? data.getInt("z") : 0, before.source(),
				before.firstHeard(), before.lastHeard());
	}

	// -------------------- COLLECTING --------------------

	/** Merges what the computer's radios heard into {@code directory}; how many entries are new or changed. */
	public static int collect(ComputerContext context, RepeaterDirectory directory, int[] radioCount) {
		ServerLevel level = context.level();
		long now = level.getGameTime();
		List<RadioBlockEntity> radios = SolarWeatherHandler.radios(context);
		radioCount[0] = radios.size();
		int changed = 0;
		for(RadioBlockEntity radio : radios) {
			for(StationsHeard.Heard heard : radio.getRepeatersHeard().recent(now, Long.MAX_VALUE / 4L)) {
				RepeaterRegistry.Info info = RepeaterRegistry.get(heard.player());
				if(info == null)
					continue; // Broken since.
				if(directory.merge(heard(info, heard.lastGameTime())))
					changed++;
			}
		}
		return changed;
	}

	/** {@code info} heard at {@code gameTime}, as the directory takes it. */
	public static RepeaterDirectory.Heard heard(RepeaterRegistry.Info info, long gameTime) {
		double output = Math.round(info.outputHz() / 100.0D) / 10_000.0D; // To 100 Hz: a float's crumbs dropped.
		int offset = (int)Math.round((info.inputHz() - info.outputHz()) / 1000.0D);
		return new RepeaterDirectory.Heard(info.callsign(), output, offset, info.accessTone(), info.dimension().location().toString(),
				info.pos().getX(), info.pos().getY(), info.pos().getZ(), gameTime);
	}

	// -------------------- ANSWERS --------------------

	private static CompoundTag list(ComputerContext context, RepeaterDirectory directory) {
		ServerLevel level = context.level();
		long now = level.getGameTime();
		BlockPos here = Sublevels.world(level, context.pos());
		String dimension = level.dimension().location().toString();
		ListTag entries = new ListTag();
		for(int i = 0; i < directory.size(); i++) {
			RepeaterDirectory.Entry entry = directory.get(i);
			CompoundTag row = new CompoundTag();
			row.putInt("i", i);
			row.putString("key", key(entry));
			row.putString("call", entry.callsign());
			row.putString("notes", entry.notes());
			row.putDouble("out", entry.outputMhz());
			row.putInt("offset", entry.offsetKhz());
			row.putFloat("tone", entry.tone());
			row.putBoolean("hasPos", entry.hasPos());
			row.putInt("x", entry.x());
			row.putInt("z", entry.z());
			boolean elsewhere = !entry.dimension().isEmpty() && !entry.dimension().equals(dimension);
			row.putBoolean("elsewhere", elsewhere);
			row.putInt("distance", elsewhere ? -1 : (int)Math.round(entry.distanceFrom(here.getX() + 0.5D, here.getZ() + 0.5D)));
			row.putInt("bearing", elsewhere ? -1 : (int)Math.round(entry.bearingFrom(here.getX() + 0.5D, here.getZ() + 0.5D)) % 360);
			row.putString("source", entry.source().name());
			row.putLong("ago", entry.wasHeard() ? RadioStation.secondsSince(now, entry.lastHeard()) : -1L);
			entries.add(row);
		}
		CompoundTag out = new CompoundTag();
		out.put("entries", entries);
		return out;
	}

	private static void export(ComputerContext context, RepeaterDirectory directory, CompoundTag data, ComputerReply reply) {
		String typed = data.getString("file").isBlank() ? DEFAULT_EXPORT : data.getString("file");
		String file = ComputerStorage.normalise(typed);
		if(file == null || !ComputerStorage.extension(file).equals(ComputerFileFormats.FREQUENCIES)) {
			reply.error("rptdir_bad_file");
			return;
		}
		List<RepeaterDirectory.Entry> chosen = new ArrayList<>();
		if(data.get("indices") instanceof IntArrayTag indices)
			for(int index : indices.getAsIntArray())
				if(index >= 0 && index < directory.size())
					chosen.add(directory.get(index));
		if(chosen.isEmpty()) {
			reply.error("rptdir_nothing_chosen");
			return;
		}
		FrequencyList list;
		byte[] existing = context.files().read(file);
		if(existing == null)
			list = new FrequencyList();
		else {
			CompoundTag tag = ComputerFileFormats.nbt(existing);
			if(tag == null) {
				reply.error("rptdir_bad_file");
				return;
			}
			list = FrequencyList.fromTag(tag);
		}
		RepeaterDirectory.ExportResult result = RepeaterDirectory.exportInto(list, chosen);
		if(result.added() > 0) {
			ComputerStorage.Problem problem = context.files().writeNbt(file, list.toTag());
			if(problem != null) {
				reply.error(problem);
				return;
			}
		}
		CompoundTag out = new CompoundTag();
		out.putString("file", file);
		out.putInt("added", result.added());
		out.putInt("duplicates", result.duplicates());
		out.putInt("full", result.full());
		reply.ok(out);
	}
}
