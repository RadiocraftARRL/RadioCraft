package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.PictureReception;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side the SSTV and WEFAX programs share (1.12 step 7): pictures broadcast over the radio to everyone in
 * range (no callsign addressing, as real SSTV and weather fax), through the radio interfaces on the computer's bus. A picture goes out as a file packet
 * ({@link TncPacket#file}) carrying a radio computer's picture ({@link FileTransfer#image}): the radio sends its header
 * as the tones start, the tones (SSTV or weather fax), then the packet. A computer that hears the header shows the
 * picture coming in line by line ({@link PictureReception}: noise where its radios stop copying the signal) and keeps it
 * on its disk when it is done ("digital unless printed"), as {@link #prefix()}{@code 0001.IMG}, listed in the program.
 * <ul>
 * <li>{@code status}: the station ({@link RadioStation#status}), {@code receiving} (with {@code from}, {@code label},
 * {@code rows}, {@code lost}, {@code pixels} while a picture comes in) and {@code received} (each {@code name},
 * {@code from}, {@code label}, {@code utc}, {@code lost}), plus what the program adds ({@link #addStatus}).</li>
 * <li>{@code send} ({@code name} of an {@code .IMG}): to everyone in range.</li>
 * <li>{@code forget} ({@code name}: off the list of pictures received).</li>
 * </ul>
 * Events to the screens: {@code rx} (a picture coming in, now and then while it does: {@code from}, {@code label},
 * {@code rows}, {@code pixels}) and {@code received} ({@code name}, {@code from}, or {@code error}).
 */
public abstract class PictureHandler implements ComputerAppHandler {

	public static final int RECEIVED_MAX = 50;
	/** Ticks between pictures pushed to the screens while one comes in. */
	private static final int PUSH_TICKS = 40;
	/** Ticks past the end of the tones a picture whose last packet was not heard is kept waiting for it. */
	private static final int OVERDUE_TICKS = 60;
	/** Ticks between looking the station's radios up again while a picture comes in. */
	private static final int RADIO_TICKS = 40;

	/** One picture received and kept. */
	public record Received(String name, String from, String label, long utc, int lost) {}

	/** One computer's pictures, for one program. */
	public static class State implements ComputerAppState {
		@Nullable
		PictureReception reception;
		/** Lines of the picture coming in that need no radio (the coast station's broadcast, a processor beside it). */
		boolean clean;
		List<RadioBlockEntity> radios = List.of();
		long radiosAt = -1L;
		final List<Received> received = new ArrayList<>();

		@Nullable
		public PictureReception reception() {
			return reception;
		}

		public List<Received> received() {
			return List.copyOf(received);
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for(Received r : received) {
				CompoundTag entry = new CompoundTag();
				entry.putString("name", r.name());
				entry.putString("from", r.from());
				entry.putString("label", r.label());
				entry.putLong("utc", r.utc());
				entry.putInt("lost", r.lost());
				list.add(entry);
			}
			tag.put("received", list);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			received.clear();
			for(Tag entry : tag.getList("received", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag)entry;
				received.add(new Received(c.getString("name"), c.getString("from"), c.getString("label"), c.getLong("utc"), c.getInt("lost")));
			}
		}
	}

	/** The program this serves. */
	protected abstract ComputerApp app();

	/** The name the pictures it receives are kept under: {@code PREFIX0001.IMG}. */
	protected abstract String prefix();

	/** How a picture this program sends goes ({@link FileTransfer#MODE_SSTV} or {@link FileTransfer#MODE_WEFAX}). */
	protected abstract String mode();

	/** The picture as this program sends it (WEFAX in greys). */
	protected byte[] outgoing(byte[] pixels) {
		return pixels;
	}

	/** Makes the program's state. */
	protected State makeState() {
		return new State();
	}

	/** Adds the program's own lines to {@code status}. */
	protected void addStatus(ComputerContext context, State state, CompoundTag out) {}

	/** The program's own requests; false if {@code action} is not one. */
	protected boolean handleMore(ComputerContext context, State state, String action, CompoundTag data, ComputerReply reply) {
		return false;
	}

	/** The program's own packets (beyond the file headers {@link #wants} takes). */
	protected void receiveMore(RadioComputerBlockEntity computer, State state, TncPacket packet) {}

	/** True if a picture coming as {@code packet}'s header is this program's. */
	protected abstract boolean wants(TncPacket packet);

	/** How long a picture of this program's takes on the air, ticks. */
	protected abstract int airTicks();

	protected abstract PictureReception.Clock clock();

	/** The colour lines not come yet show as (black for SSTV, paper for WEFAX). */
	protected abstract int blankRgb();

	public State state(RadioComputerBlockEntity computer) {
		return computer.state(app(), this::makeState);
	}

	// -------------------- REQUESTS --------------------

	@Override
	public final void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		State state = state(context.computer());
		switch(action) {
			case "status" -> {
				CompoundTag out = RadioStation.status(context.bus());
				PictureReception reception = state.reception;
				out.putBoolean("receiving", reception != null);
				if(reception != null)
					putReception(out, reception);
				ListTag list = new ListTag();
				for(Received r : state.received) {
					if(!context.files().storage().exists(r.name()))
						continue; // Deleted from the shell.
					CompoundTag entry = new CompoundTag();
					entry.putString("name", r.name());
					entry.putString("from", r.from());
					entry.putString("label", r.label());
					entry.putLong("utc", r.utc());
					entry.putInt("lost", r.lost());
					list.add(entry);
				}
				out.put("received", list);
				addStatus(context, state, out);
				reply.ok(out);
			}
			case "send" -> {
				String problem = send(context, data.getString("name"));
				if(problem == null)
					reply.ok();
				else
					reply.error(problem);
			}
			case "forget" -> {
				state.received.removeIf(r -> r.name().equals(data.getString("name")));
				context.computer().setChanged();
				reply.ok();
			}
			default -> {
				if(!handleMore(context, state, action, data, reply))
					reply.error("unknown_request", action);
			}
		}
	}

	private static void putReception(CompoundTag out, PictureReception reception) {
		out.putString("from", reception.from());
		out.putString("label", reception.label());
		out.putInt("rows", reception.rowsDone());
		out.putInt("lost", reception.rowsLost());
		out.putByteArray("pixels", reception.shown());
	}

	/** Sends the picture {@code name} to everyone in range; null if it went, else an error key. */
	@Nullable
	protected String send(ComputerContext context, String name) {
		return send(context.computer(), name);
	}

	/**
	 * Sends the picture {@code name} on {@code computer}'s disk to everyone in range, with nobody at the screen (the
	 * scheduler's WEFAX broadcasts, computer batch 3A); null if it went, else an error key.
	 */
	@Nullable
	public String send(RadioComputerBlockEntity computer, String name) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return "station_no_radio";
		String file = ComputerStorage.normalise(name);
		byte[] pixels = file == null ? null : computer.files().read(file);
		if(pixels == null || !ComputerFileFormats.isImage(pixels))
			return "picture_not_image";
		StationBus bus = RadioStation.bus(computer);
		String problem = RadioStation.cannotSend(bus);
		if(problem != null)
			return problem;
		TncPacket packet = TncPacket.file(RadioStation.newId(level), bus.callsign(), "",
				FileTransfer.image(outgoing(pixels), file.substring(0, file.indexOf('.')), mode()));
		return RadioStation.transmit(computer, bus, packet) ? null : "station_no_radio";
	}

	// -------------------- RECEIVING --------------------

	@Override
	public final void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return;
		State state = state(computer);
		if(packet.kind() == TncPacket.Kind.FILE_HEADER && wants(packet)) { // Every station in range copies a picture.
			byte[] pixels = FileTransfer.imagePixels(packet.item(), level);
			if(pixels != null)
				start(computer, state, packet.id(), packet.from(), packet.text(), outgoing(pixels), level.getGameTime(), airTicks(), false);
		}
		else if(packet.kind() == TncPacket.Kind.FILE && state.reception != null && state.reception.id() == (packet.id() ^ 0x5DEECE66DL)) {
			state.reception.finish(true); // The last packet decoded: the end of the picture was copied.
			done(computer, state);
		}
		else
			receiveMore(computer, state, packet);
	}

	/**
	 * Starts showing a picture coming in: from {@code from}, its tones from game time {@code start} for {@code ticks};
	 * {@code clean} for one that needs no radio to copy. One coming while another does takes over (the receiver locks
	 * on to the newest start tone); the one before is kept as far as it came.
	 */
	protected void start(RadioComputerBlockEntity computer, State state, long id, String from, String label, byte[] pixels, long start, int ticks, boolean clean) {
		if(state.reception != null && state.reception.id() == id)
			return;
		if(state.reception != null)
			done(computer, state);
		PhotoPalette palette = MapPalettes.vanilla();
		state.reception = new PictureReception(id, from, label, pixels, start, ticks, clock(), palette.id(palette.nearest(blankRgb())),
				palette.id(palette.nearest(0x303030)), palette.id(palette.nearest(0xC8C8C8)));
		state.clean = clean;
		state.radiosAt = -1L;
		CompoundTag event = new CompoundTag();
		putReception(event, state.reception);
		computer.push(app(), "rx", event);
	}

	/** The picture coming in is over: kept on the disk and listed, and the screens told. */
	protected void done(RadioComputerBlockEntity computer, State state) {
		PictureReception reception = state.reception;
		state.reception = null;
		if(reception == null || !(computer.getLevel() instanceof ServerLevel level))
			return;
		reception.finish(false); // Anything never heard is noise.
		ComputerFileStore files = computer.files();
		String name = ComputerStorage.nextName(prefix(), ComputerFileFormats.IMAGE, files.storage()::exists);
		CompoundTag event = new CompoundTag();
		event.putString("from", reception.from());
		if(name == null || files.write(name, reception.shown()) != null) {
			event.putString("error", "disk_full");
			computer.push(app(), "received", event);
			return;
		}
		state.received.addFirst(new Received(name, reception.from(), reception.label(), System.currentTimeMillis(), reception.rowsLost()));
		while(state.received.size() > RECEIVED_MAX)
			state.received.removeLast();
		computer.setChanged();
		level.playSound(null, computer.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4F, 1.2F);
		event.putString("name", name);
		computer.push(app(), "received", event);
	}

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if(!(computer.existingState(app()) instanceof State state) || state.reception == null)
			return;
		tickReception(computer, state, gameTime);
	}

	/** Brings the picture coming in up to now, and finishes it when it is over. */
	protected void tickReception(RadioComputerBlockEntity computer, State state, long gameTime) {
		PictureReception reception = state.reception;
		if(reception == null)
			return;
		if(!state.clean && (state.radiosAt < 0 || gameTime - state.radiosAt >= RADIO_TICKS)) {
			state.radios = RadioStation.radios(RadioStation.bus(computer));
			state.radiosAt = gameTime;
		}
		boolean copying = state.clean || RadioStation.copying(state.radios, gameTime);
		reception.advance(gameTime, copying);
		if(reception.complete() && (state.clean || reception.overdue(gameTime, 0)) || reception.overdue(gameTime, OVERDUE_TICKS)) {
			done(computer, state);
			return;
		}
		if(gameTime % PUSH_TICKS == 0) {
			CompoundTag event = new CompoundTag();
			putReception(event, reception);
			computer.push(app(), "rx", event);
		}
	}
}
