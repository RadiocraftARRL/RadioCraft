package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.ft8.Ft8;
import com.arrl.radiocraft.common.radio.ft8.Ft8Decode;
import com.arrl.radiocraft.common.radio.ft8.Ft8Message;
import com.arrl.radiocraft.common.radio.ft8.Ft8Sequencer;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The FT8 program's server side (computer batch 3B). FT8 goes out and comes in through the radio interfaces on the
 * computer's station bus, under the interface's callsign, as every radio program does; each message is a
 * {@link TncPacket.Kind#FT8} packet whose tones ({@code Ft8Audio}) take 12.64 s, sent at the start of a 15 s slot
 * ({@link Ft8}). A radio decodes it far below where voice and RTTY copy and adds the SNR it measured; this handler
 * collects a slot's decodes, makes them at {@link Ft8#DECODE_AT} ({@link Ft8Decode#resolve}: overlapping signals),
 * hands them to the auto-sequencer ({@link Ft8Sequencer}) and sends what it picks at the start of our next slot.
 * <p>
 * <b>Logging.</b> A completed contact is added to {@link #LOG_FILE} on the computer's disk (C:), the Logbook program's
 * own file, as a {@link Logbook.Entry}: the real UTC time and the game day, the other call, the dial frequency plus the
 * audio offset (as WSJT-X logs it), mode {@code FT8}, the reports as sent ("-12", "+05"), and "FT8 grid JJ00" in the
 * notes. A Logbook screen open on that file reloads ({@code reload} event to the Logbook program).
 * <p>
 * Requests: {@code status}, {@code cq}, {@code answer} {slot, snr, offset, text}, {@code halt}, {@code tx} {on},
 * {@code offset} {hz}, {@code parity} {even}, {@code dial} (tune the first radio on to its band's FT8 frequency),
 * {@code clear}. Pushes {@code decodes} (each slot), {@code tx} (what went out), {@code logged} (a contact).
 */
public final class Ft8Handler implements ComputerAppHandler {

	/** The Logbook program's file, where contacts go. */
	public static final String LOG_FILE = "LOGBOOK.LOG";
	/** Decodes kept for the screen. */
	public static final int HISTORY = 60;
	/** Contacts kept in the program's own list (they are all in the logbook). */
	public static final int QSO_LIST = 20;
	/** Signals collected in one slot at most (a busy band). */
	public static final int SLOT_MAX = 40;
	/** Minutes a scheduled CQ (SCHEDULE's FT8 CQ job) calls for. */
	public static final int SCHEDULED_CQ_MINUTES = 10;

	/** One computer's FT8 station. */
	public static final class State implements ComputerAppState {
		final Ft8Sequencer sequencer = new Ft8Sequencer();
		boolean txEnabled;
		boolean txEven;
		int offset = 1200;
		boolean offsetChosen;
		final List<Ft8Decode> heard = new ArrayList<>();
		final List<Ft8Decode> decodes = new ArrayList<>();
		final List<String> qsos = new ArrayList<>();
		int qsoCount;
		String lastTx = "";
		long lastTxSlot = Long.MIN_VALUE;
		long cqUntil;
		String lastProblem = "";

		public Ft8Sequencer sequencer() {
			return sequencer;
		}

		public List<Ft8Decode> decodes() {
			return decodes;
		}

		public int qsoCount() {
			return qsoCount;
		}

		public String lastTx() {
			return lastTx;
		}

		public boolean txEnabled() {
			return txEnabled;
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.put("seq", sequencer.toTag());
			tag.putBoolean("tx", txEnabled);
			tag.putBoolean("even", txEven);
			tag.putInt("offset", offset);
			tag.putBoolean("offsetChosen", offsetChosen);
			ListTag list = new ListTag();
			for(String line : qsos)
				list.add(StringTag.valueOf(line));
			tag.put("qsos", list);
			tag.putInt("qsoCount", qsoCount);
			tag.putLong("cqUntil", cqUntil);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			sequencer.load(tag.getCompound("seq"));
			txEnabled = tag.getBoolean("tx");
			txEven = tag.getBoolean("even");
			offset = Ft8.clampOffset(tag.getInt("offset"));
			offsetChosen = tag.getBoolean("offsetChosen");
			qsos.clear();
			ListTag list = tag.getList("qsos", Tag.TAG_STRING);
			for(int i = 0; i < list.size(); i++)
				qsos.add(list.getString(i));
			qsoCount = tag.getInt("qsoCount");
			cqUntil = tag.getLong("cqUntil");
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		State state = computer.state(ComputerApps.FT8, State::new);
		if(!state.offsetChosen && computer.getLevel() != null) { // Somewhere in the passband of its own, as an operator picks a clear spot.
			state.offset = Ft8.clampOffset(600 + computer.getLevel().getRandom().nextInt(1800) / 25 * 25);
			state.offsetChosen = true;
		}
		return state;
	}

	/** Our station: the bus callsign and the computer's grid square. */
	static void station(RadioComputerBlockEntity computer, State state, StationBus bus) {
		state.sequencer.station(bus.callsign(), Ft8.grid(computer.getBlockPos()));
	}

	// -------------------- REQUESTS --------------------

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State state = state(computer);
		StationBus bus = RadioStation.bus(computer);
		long now = context.level().getGameTime();
		station(computer, state, bus);
		switch(action) {
			case "status" -> {
				reply.ok(status(computer, state, bus, now, true));
				return;
			}
			case "cq" -> {
				String problem = RadioStation.cannotSend(bus);
				if(problem != null) {
					reply.error(problem);
					return;
				}
				callCq(state, now);
			}
			case "answer" -> {
				String problem = RadioStation.cannotSend(bus);
				if(problem != null) {
					reply.error(problem);
					return;
				}
				Ft8Decode decode = new Ft8Decode(data.getLong("slot"), data.getInt("snr"), data.getInt("offset"), data.getString("text"));
				Ft8Message message = decode.message();
				if(message == null || message.from().equals(Ft8Message.cleanCall(bus.callsign()))) {
					reply.error("ft8_cannot_answer");
					return;
				}
				state.sequencer.answer(decode);
				state.txEnabled = true;
				state.txEven = !Ft8.isEven(decode.slot()); // We answer in the other half.
				state.cqUntil = 0L;
			}
			case "halt" -> {
				state.sequencer.halt();
				state.txEnabled = false;
				state.cqUntil = 0L;
			}
			case "tx" -> state.txEnabled = data.getBoolean("on");
			case "offset" -> state.offset = Ft8.clampOffset(data.getInt("hz"));
			case "parity" -> state.txEven = data.getBoolean("even");
			case "clear" -> state.decodes.clear();
			case "dial" -> {
				String problem = dial(bus);
				if(problem != null) {
					reply.error(problem);
					return;
				}
			}
			default -> {
				reply.error("unknown_request", action);
				return;
			}
		}
		computer.setChanged();
		reply.ok(status(computer, state, bus, now, false));
	}

	private static void callCq(State state, long now) {
		state.sequencer.callCq();
		state.txEnabled = true;
		state.txEven = Ft8.isEven(Ft8.slot(now) + 1); // From the next slot.
	}

	/** Tunes the first radio on to its band's FT8 dial frequency, in USB; null if done, else the error key. */
	@Nullable
	static String dial(StationBus bus) {
		RadioBlockEntity radio = RadioStation.firstOn(RadioStation.radios(bus));
		if(!bus.hasInterface())
			return "station_no_interface";
		if(radio == null || radio.getBand() == null)
			return "station_no_radio";
		Band band = radio.getBand();
		float dial = Ft8.dialIn(BandEdges.tuneMin(band), BandEdges.tuneMax(band));
		if(dial <= 0.0F || !RigControlHandler.tune(radio, dial))
			return "ft8_no_dial";
		if(!(radio instanceof HFRadioAllBandBlockEntity) || !radio.isFM())
			RigControlHandler.setMode(radio, "SSB");
		return null;
	}

	/**
	 * SCHEDULE's FT8 CQ job (and an API): calls CQ for {@code minutes} with auto-sequencing, logging what it works.
	 * Null if it started, else the error key.
	 */
	@Nullable
	public static String startCq(RadioComputerBlockEntity computer, int minutes) {
		if(!computer.isInstalled(ComputerApps.FT8))
			return "sched_need_ft8";
		StationBus bus = RadioStation.bus(computer);
		String problem = RadioStation.cannotSend(bus);
		if(problem != null)
			return problem;
		if(computer.getLevel() == null)
			return "station_no_radio";
		State state = state(computer);
		station(computer, state, bus);
		if(state.sequencer.step() != Ft8Sequencer.Step.IDLE && state.sequencer.step() != Ft8Sequencer.Step.CALLING_CQ)
			return null; // In a contact already: let it run.
		long now = computer.getLevel().getGameTime();
		callCq(state, now);
		state.cqUntil = now + minutes * 1200L;
		computer.setChanged();
		return null;
	}

	// -------------------- RECEIVING --------------------

	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(packet.kind() != TncPacket.Kind.FT8 || computer.getLevel() == null)
			return;
		int snr = packet.ft8Snr();
		if(snr == Integer.MIN_VALUE)
			return; // Not from a radio's decoder.
		String text = packet.ft8Message();
		if(Ft8Message.parse(text) == null)
			return;
		State state = state(computer);
		if(state.heard.size() >= SLOT_MAX)
			return;
		long sentIn = Ft8.slot(computer.getLevel().getGameTime() - Ft8.TX_TICKS / 2); // The slot it started in, if it came a little late.
		state.heard.add(new Ft8Decode(sentIn, snr, packet.ft8Offset(), text));
	}

	// -------------------- THE SLOT CLOCK --------------------

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		int inSlot = Ft8.tickInSlot(gameTime);
		if(inSlot != 0 && inSlot != Ft8.DECODE_AT)
			return;
		if(!(computer.existingState(ComputerApps.FT8) instanceof State state))
			return;
		if(inSlot == Ft8.DECODE_AT)
			decode(computer, state, gameTime);
		else
			transmit(computer, state, gameTime);
	}

	/** The slot's decodes: shown, then the sequencer picks our next message. */
	private static void decode(RadioComputerBlockEntity computer, State state, long gameTime) {
		long slot = Ft8.slot(gameTime);
		List<Ft8Decode> decodes = Ft8Decode.resolve(state.heard);
		state.heard.clear();
		StationBus bus = null;
		if(state.cqUntil > 0L && gameTime >= state.cqUntil) { // A scheduled CQ is over (a contact under way still finishes).
			state.cqUntil = 0L;
			if(state.sequencer.step() == Ft8Sequencer.Step.CALLING_CQ) {
				state.sequencer.halt();
				state.txEnabled = false;
			}
		}
		if(!decodes.isEmpty()) {
			state.decodes.addAll(decodes);
			while(state.decodes.size() > HISTORY)
				state.decodes.removeFirst();
			bus = RadioStation.bus(computer);
			station(computer, state, bus);
			Ft8Sequencer.Qso done = state.sequencer.decoded(decodes);
			if(state.sequencer.acted()) { // Answering a station: in the slot after theirs.
				state.txEven = Ft8.isEven(slot + 1);
				state.txEnabled = true;
			}
			if(done != null)
				log(computer, state, done, bus);
			computer.setChanged();
		}
		if(!computer.viewers().isEmpty()) {
			CompoundTag event = new CompoundTag();
			event.putLong("slot", slot);
			ListTag list = new ListTag();
			for(Ft8Decode decode : decodes)
				list.add(decode.toTag());
			event.put("decodes", list);
			event.put("seq", sequencerTag(state));
			computer.push(ComputerApps.FT8, "decodes", event);
		}
	}

	/** The start of a slot: our message goes out if it is our half and TX is enabled. */
	private static void transmit(RadioComputerBlockEntity computer, State state, long gameTime) {
		long slot = Ft8.slot(gameTime);
		Ft8Message next = state.sequencer.next();
		if(!state.txEnabled || next == null || Ft8.isEven(slot) != state.txEven || !(computer.getLevel() instanceof ServerLevel level))
			return;
		StationBus bus = RadioStation.bus(computer);
		station(computer, state, bus);
		next = state.sequencer.next(); // Remade with our call if it changed.
		if(next == null)
			return;
		String problem = RadioStation.cannotSend(bus);
		TncPacket packet = TncPacket.ft8(RadioStation.newId(level), bus.callsign(), next.encode(), state.offset);
		if(problem != null || !RadioStation.transmit(computer, bus, packet)) {
			state.lastProblem = problem == null ? "station_no_radio" : problem;
			CompoundTag event = new CompoundTag();
			event.putString("problem", state.lastProblem);
			computer.push(ComputerApps.FT8, "tx", event);
			return;
		}
		state.lastProblem = "";
		state.lastTx = next.encode();
		state.lastTxSlot = slot;
		state.sequencer.sent();
		if(state.sequencer.next() == null && !state.sequencer.cqMode())
			state.txEnabled = false; // Nothing more to send: as WSJT-X, TX goes off once the contact is done.
		computer.setChanged();
		CompoundTag event = new CompoundTag();
		event.putLong("slot", slot);
		event.putString("text", state.lastTx);
		event.putInt("offset", state.offset);
		event.put("seq", sequencerTag(state));
		computer.push(ComputerApps.FT8, "tx", event);
	}

	// -------------------- LOGGING --------------------

	/** Adds {@code qso} to the logbook file and the program's list, and tells the screens. */
	static void log(RadioComputerBlockEntity computer, State state, Ft8Sequencer.Qso qso, StationBus bus) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return;
		RadioBlockEntity radio = RadioStation.firstOn(RadioStation.radios(bus));
		double mhz = radio == null ? 0.0D : (radio.getFrequency() + state.offset) / 1_000_000.0D;
		Logbook.Entry entry = entry(System.currentTimeMillis(), WeatherReports.timestamp(level.getDayTime()), qso, mhz);
		String problem = appendToLog(computer, entry);
		contactMade(level, computer, qso, radio == null ? 0.0F : radio.getFrequency() + state.offset);
		String line = String.format(java.util.Locale.ROOT, "%s %-10s %-4s %3s %3s", entry.time(), qso.call(), qso.grid(),
				Ft8Message.report(qso.reportSent()), Ft8Message.report(qso.reportReceived()));
		state.qsos.add(line);
		while(state.qsos.size() > QSO_LIST)
			state.qsos.removeFirst();
		state.qsoCount++;
		CompoundTag event = new CompoundTag();
		event.putString("line", line);
		event.putString("call", qso.call());
		event.putString("problem", problem == null ? "" : problem);
		computer.push(ComputerApps.FT8, "logged", event);
		CompoundTag reload = new CompoundTag();
		reload.putString("file", LOG_FILE);
		computer.push(ComputerApps.LOGBOOK, "reload", reload);
	}

	/**
	 * Weak Signal Wizard for the computer's owner, and the FT8 contact as one way of a verified two-way contact (the
	 * other station's computer logs the other way): this station heard the player whose callsign {@code qso} names.
	 */
	public static void contactMade(ServerLevel level, RadioComputerBlockEntity computer, Ft8Sequencer.Qso qso, float hz) {
		java.util.UUID owner = computer.files().owner();
		if(owner == null)
			return;
		com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(level.getServer(), owner, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.WEAK_SIGNAL_WIZARD);
		java.util.UUID talker = com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.playerWithCallsign(level.getServer(), com.arrl.radiocraft.common.computer.PacketBbs.base(qso.call()));
		if(talker != null && !talker.equals(owner))
			com.arrl.radiocraft.common.advancements.ContactTracker.heard(level, owner, talker, com.arrl.radiocraft.compat.sable.Sublevels.world(level, computer.getBlockPos()), hz, "FT8");
	}

	/** The logbook entry for a contact: mode FT8, the reports as sent, the grid in the notes and as where it was. */
	public static Logbook.Entry entry(long utcMillis, String gameTime, Ft8Sequencer.Qso qso, double mhz) {
		return new Logbook.Entry(utcMillis, gameTime, qso.call(), mhz, "FT8", Ft8Message.report(qso.reportSent()), Ft8Message.report(qso.reportReceived()),
				qso.grid().isEmpty() ? "FT8" : "FT8 grid " + qso.grid(), Logbook.Where.grid(qso.grid())); // The grid also for the Awards (batch 3C).
	}

	/** Adds {@code entry} to {@link #LOG_FILE}; null if done, else the error key. */
	@Nullable
	public static String appendToLog(RadioComputerBlockEntity computer, Logbook.Entry entry) {
		CompoundTag tag = computer.files().readNbt(LOG_FILE);
		Logbook book = tag == null ? new Logbook() : Logbook.fromTag(tag);
		if(!book.add(entry))
			return "ft8_log_full";
		ComputerStorage.Problem problem = computer.files().writeNbt(LOG_FILE, book.toTag());
		return problem == null ? null : problem.key();
	}

	// -------------------- FOR THE SCREEN --------------------

	static CompoundTag sequencerTag(State state) {
		CompoundTag out = new CompoundTag();
		Ft8Sequencer seq = state.sequencer;
		out.putString("step", seq.step().name());
		out.putString("dx", seq.dx());
		out.putString("next", seq.next() == null ? "" : seq.next().encode());
		out.putBoolean("cq", seq.cqMode());
		out.putBoolean("tx", state.txEnabled);
		out.putBoolean("even", state.txEven);
		out.putInt("offset", state.offset);
		out.putString("lastTx", state.lastTx);
		out.putLong("lastTxSlot", state.lastTxSlot);
		out.putInt("qsoCount", state.qsoCount);
		out.putLong("cqUntil", state.cqUntil);
		return out;
	}

	static CompoundTag status(RadioComputerBlockEntity computer, State state, StationBus bus, long now, boolean all) {
		CompoundTag out = RadioStation.status(bus);
		String problem = RadioStation.cannotSend(bus);
		out.putString("problem", problem == null ? "" : problem);
		out.putString("myCall", Ft8Message.cleanCall(bus.callsign()));
		out.putString("myGrid", Ft8.grid(computer.getBlockPos()));
		RadioBlockEntity radio = RadioStation.firstOn(RadioStation.radios(bus));
		out.putBoolean("onDial", radio != null && Ft8.isDial(radio.getFrequency()));
		out.putLong("gameTime", now);
		out.put("seq", sequencerTag(state));
		if(all) {
			ListTag decodes = new ListTag();
			for(Ft8Decode decode : state.decodes)
				decodes.add(decode.toTag());
			out.put("decodes", decodes);
			ListTag qsos = new ListTag();
			for(String line : state.qsos)
				qsos.add(StringTag.valueOf(line));
			out.put("qsos", qsos);
		}
		return out;
	}
}
