package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppHandlers;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.Schedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The scheduler's server side (computer batch 3A): runs the computer's timed jobs ({@link Schedule}, kept on its disk
 * as {@link Schedule#FILE}) with nobody at the screen, while the computer has power and its chunk is loaded. It looks
 * once a second (staggered by the computer's position), so it costs next to nothing; a job runs when its time comes
 * round ({@link Schedule#occurrence}), at most once per time however long the computer was away.
 * <p>
 * The jobs go out the way the station's other programs do, under the same rules:
 * <ul>
 * <li><b>Station ID</b>: the station's callsign in Morse ({@link RadioBlockEntity#sendMorseId}) from the radios on a
 * radio interface on the computer's bus whose transmit switch is on (the callsign is the interface's).</li>
 * <li><b>WEFAX</b>: a saved chart sent through the WEFAX program ({@link PictureHandler#send(RadioComputerBlockEntity,
 * String)}), which must be installed.</li>
 * <li><b>Beacon</b>: an APRS position beacon through the APRS program ({@link AprsHandler#beacon}), which must be
 * installed, with its comment and icon.</li>
 * <li><b>Reminder</b>: a line in chat to players within {@link #REMINDER_RANGE} blocks and on the screens at the
 * computer, or an APRS message to a callsign (APRS installed).</li>
 * </ul>
 * Requests (editing is the computer's owner's or an operator's, like SETUP: {@code not_owner_sched}):
 * {@code status} (the jobs, each with {@code next} seconds; {@code log}; {@code owner}; {@code aprs} and {@code wefax}
 * installed; the station), {@code save} ({@code job}: id 0 adds), {@code delete} / {@code toggle} / {@code run}
 * ({@code id}; run answers {@code result}, "" when it went). Pushes {@code ran} to the screens when a job runs.
 */
public final class SchedulerHandler implements ComputerAppHandler {

	/** Runs kept in the log. */
	public static final int LOG_MAX = 20;
	/** How near the computer a player hears a reminder in chat, blocks. */
	public static final double REMINDER_RANGE = 16.0D;
	private static final long CHECK_TICKS = 20L;

	/** One run: when (ms, real time), which job, what it was, and how it went ("" for done, else an error key). */
	public record Ran(long utc, int job, String kind, String result) {}

	/** One computer's scheduler: the jobs as last read from the disk, when each last ran, and the log. */
	public static final class State implements ComputerAppState {
		List<Schedule.Job> jobs = List.of();
		/** The file the jobs were read from (a new entry object whenever it is written). */
		@Nullable
		ComputerStorage.FileEntry read;
		boolean everRead = false;
		final Map<Integer, Long> last = new HashMap<>();
		final ArrayDeque<Ran> log = new ArrayDeque<>();

		public List<Ran> log() {
			return List.copyOf(log);
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag times = new CompoundTag();
			last.forEach((id, occurrence) -> times.putLong(String.valueOf(id), occurrence));
			tag.put("last", times);
			ListTag list = new ListTag();
			for(Ran ran : log) {
				CompoundTag entry = new CompoundTag();
				entry.putLong("utc", ran.utc());
				entry.putInt("job", ran.job());
				entry.putString("kind", ran.kind());
				entry.putString("result", ran.result());
				list.add(entry);
			}
			tag.put("log", list);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			last.clear();
			CompoundTag times = tag.getCompound("last");
			for(String key : times.getAllKeys())
				try {
					last.put(Integer.parseInt(key), times.getLong(key));
				}
				catch(NumberFormatException ignored) {}
			log.clear();
			for(Tag element : tag.getList("log", Tag.TAG_COMPOUND)) {
				CompoundTag entry = (CompoundTag)element;
				log.addLast(new Ran(entry.getLong("utc"), entry.getInt("job"), entry.getString("kind"), entry.getString("result")));
			}
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.SCHEDULER, State::new);
	}

	/** The jobs on {@code computer}'s disk (read again only when the file changed: the shell, a copy, a floppy). */
	public static List<Schedule.Job> jobs(RadioComputerBlockEntity computer, State state) {
		ComputerStorage.FileEntry entry = computer.files().storage().entry(Schedule.FILE);
		if(!state.everRead || entry != state.read) {
			state.everRead = true;
			state.read = entry;
			state.jobs = entry == null ? List.of() : List.copyOf(Schedule.fromTag(ComputerFileFormats.nbt(entry.data())));
		}
		return state.jobs;
	}

	/** Keeps {@code jobs} on the disk; null if written, else the error key. */
	@Nullable
	private static String keep(RadioComputerBlockEntity computer, State state, List<Schedule.Job> jobs) {
		ComputerStorage.Problem problem = computer.files().writeNbt(Schedule.FILE, Schedule.toTag(jobs));
		if(problem != null)
			return problem.key();
		state.jobs = List.copyOf(jobs);
		state.read = computer.files().storage().entry(Schedule.FILE);
		state.everRead = true;
		return null;
	}

	/** The server's own clock: minutes since the epoch, in its time zone. */
	static long realMinutes() {
		return Math.floorDiv(realSeconds(), 60L);
	}

	static long realSeconds() {
		long now = System.currentTimeMillis();
		int offset = java.util.TimeZone.getDefault().getOffset(now);
		return Math.floorDiv(now + offset, 1000L);
	}

	// -------------------- REQUESTS --------------------

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State state = state(computer);
		List<Schedule.Job> jobs = jobs(computer, state);
		if(!"status".equals(action) && !computer.mayChangePassword(context.player())) {
			reply.error("not_owner_sched");
			return;
		}
		switch(action) {
			case "status" -> reply.ok(status(context, state, jobs));
			case "save" -> {
				Schedule.Job job = Schedule.Job.fromTag(data.getCompound("job")).withAnchor(context.level().getGameTime());
				String problem = job.problem();
				if(problem != null) {
					reply.error(problem);
					return;
				}
				List<Schedule.Job> changed = Schedule.put(jobs, job);
				if(changed == null) {
					reply.error("sched_full", String.valueOf(Schedule.MAX_JOBS));
					return;
				}
				state.last.remove(job.id()); // A new time: the first look only notes it.
				String kept = keep(computer, state, changed);
				if(kept != null)
					reply.error(kept);
				else
					reply.ok(status(context, state, state.jobs));
			}
			case "delete" -> {
				state.last.remove(data.getInt("id"));
				String kept = keep(computer, state, Schedule.remove(jobs, data.getInt("id")));
				if(kept != null)
					reply.error(kept);
				else
					reply.ok(status(context, state, state.jobs));
			}
			case "toggle" -> {
				Schedule.Job job = find(jobs, data.getInt("id"));
				if(job == null) {
					reply.error("sched_no_job");
					return;
				}
				state.last.remove(job.id());
				List<Schedule.Job> changed = Schedule.put(jobs, job.withEnabled(!job.enabled()));
				String kept = changed == null ? "sched_no_job" : keep(computer, state, changed);
				if(kept != null)
					reply.error(kept);
				else
					reply.ok(status(context, state, state.jobs));
			}
			case "run" -> {
				Schedule.Job job = find(jobs, data.getInt("id"));
				if(job == null) {
					reply.error("sched_no_job");
					return;
				}
				String result = runAndLog(computer, state, job);
				CompoundTag out = status(context, state, state.jobs);
				out.putString("result", result == null ? "" : result);
				reply.ok(out);
			}
			default -> reply.error("unknown_request", action);
		}
	}

	@Nullable
	private static Schedule.Job find(List<Schedule.Job> jobs, int id) {
		for(Schedule.Job job : jobs)
			if(job.id() == id)
				return job;
		return null;
	}

	private static CompoundTag status(ComputerContext context, State state, List<Schedule.Job> jobs) {
		CompoundTag out = RadioStation.status(context.bus());
		ServerLevel level = context.level();
		long realSeconds = realSeconds();
		ListTag list = new ListTag();
		for(Schedule.Job job : jobs) {
			CompoundTag entry = job.toTag();
			entry.putLong("next", Schedule.secondsToNext(job, level.getGameTime(), level.getDayTime(), realSeconds));
			list.add(entry);
		}
		out.put("jobs", list);
		ListTag log = new ListTag();
		for(Ran ran : state.log) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("utc", ran.utc());
			entry.putInt("job", ran.job());
			entry.putString("kind", ran.kind());
			entry.putString("result", ran.result());
			log.add(entry);
		}
		out.put("log", log);
		out.putBoolean("owner", context.computer().mayChangePassword(context.player()));
		out.putBoolean("aprs", context.computer().isInstalled(ComputerApps.APRS));
		out.putBoolean("wefax", context.computer().isInstalled(ComputerApps.WEFAX));
		return out;
	}

	// -------------------- RUNNING --------------------

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if(Math.floorMod(gameTime + computer.getBlockPos().asLong(), CHECK_TICKS) != 0L || !(computer.getLevel() instanceof ServerLevel level))
			return;
		State state = state(computer);
		List<Schedule.Job> jobs = jobs(computer, state);
		if(jobs.isEmpty())
			return;
		long dayTime = level.getDayTime(), real = realMinutes();
		for(Schedule.Job job : jobs) {
			if(!job.enabled())
				continue;
			Schedule.Step step = Schedule.step(state.last.getOrDefault(job.id(), Schedule.UNSET), Schedule.occurrence(job, gameTime, dayTime, real));
			state.last.put(job.id(), step.last());
			if(step.run())
				runAndLog(computer, state, job);
		}
	}

	/** Runs {@code job} now, logs it and tells the screens; null if it went, else the error key. */
	@Nullable
	public static String runAndLog(RadioComputerBlockEntity computer, State state, Schedule.Job job) {
		String result = run(computer, job);
		state.log.addFirst(new Ran(System.currentTimeMillis(), job.id(), job.kind().id(), result == null ? "" : result));
		while(state.log.size() > LOG_MAX)
			state.log.removeLast();
		computer.setChanged();
		CompoundTag event = new CompoundTag();
		event.putInt("job", job.id());
		event.putString("result", result == null ? "" : result);
		computer.push(ComputerApps.SCHEDULER, "ran", event);
		return result;
	}

	/** Does {@code job}; null if it went, else why not (an error key under {@code screen.radiocraft.computer.err.}). */
	@Nullable
	static String run(RadioComputerBlockEntity computer, Schedule.Job job) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return "station_no_radio";
		return switch(job.kind()) {
			case STATION_ID -> stationId(RadioStation.bus(computer));
			case WEFAX -> {
				if(!computer.isInstalled(ComputerApps.WEFAX))
					yield "sched_need_wefax";
				yield ComputerAppHandlers.get(ComputerApps.WEFAX) instanceof PictureHandler wefax ? wefax.send(computer, job.file()) : "sched_need_wefax";
			}
			case BEACON -> {
				if(!computer.isInstalled(ComputerApps.APRS))
					yield "sched_need_aprs";
				String problem = AprsHandler.cannotSend(RadioStation.bus(computer));
				if(problem != null)
					yield problem;
				yield AprsHandler.beacon(computer, AprsHandler.state(computer)) ? null : "station_no_radio";
			}
			case REMINDER -> reminder(computer, level, job);
			case FT8_CQ -> Ft8Handler.startCq(computer, Ft8Handler.SCHEDULED_CQ_MINUTES); // Computer batch 3B.
		};
	}

	/** The station's callsign in Morse from the first radio on a transmitting interface; null if it went. */
	@Nullable
	static String stationId(StationBus bus) {
		String problem = RadioStation.cannotSend(bus);
		if(problem != null)
			return problem;
		for(RadioInterfaceBlockEntity radioInterface : bus.interfaces()) {
			if(!radioInterface.isTransmitEnabled())
				continue;
			for(RadioBlockEntity radio : radioInterface.getRadios())
				if(radio.sendMorseId(bus.callsign()))
					return null;
		}
		return "sched_no_transmit";
	}

	@Nullable
	private static String reminder(RadioComputerBlockEntity computer, ServerLevel level, Schedule.Job job) {
		if(!job.to().isEmpty()) { // An APRS message.
			if(!computer.isInstalled(ComputerApps.APRS))
				return "sched_need_aprs";
			return AprsHandler.send(computer, AprsHandler.state(computer), job.to(), job.text());
		}
		String name = computer.files().settings().name();
		Component line = Component.translatable(Radiocraft.translationKey("message", "computer.reminder"),
				name.isEmpty() ? Component.translatable(Radiocraft.translationKey("container", "radio_computer")) : name, job.text());
		List<ServerPlayer> told = new ArrayList<>();
		for(ServerPlayer player : level.players())
			if(player.distanceToSqr(computer.getBlockPos().getCenter()) <= REMINDER_RANGE * REMINDER_RANGE || computer.viewers().contains(player)) {
				player.sendSystemMessage(line);
				told.add(player);
			}
		CompoundTag notice = new CompoundTag();
		notice.putString("text", job.text());
		computer.push(ComputerApps.SCHEDULER, "notice", notice); // On the screens too, whatever program is in front.
		return told.isEmpty() ? "sched_nobody" : null;
	}
}
