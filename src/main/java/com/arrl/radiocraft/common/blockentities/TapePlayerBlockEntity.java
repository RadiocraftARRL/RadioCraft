package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.blockentities.radio.IVoiceTransmitter;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.TapePlayerBlock;
import com.arrl.radiocraft.common.datacomponents.TapeContents;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.items.TapeItem;
import com.arrl.radiocraft.common.menus.TapePlayerMenu;
import com.arrl.radiocraft.common.radio.VoiceTransmitters;
import com.arrl.radiocraft.common.radio.station.ProgramSource;
import com.arrl.radiocraft.common.radio.tape.TapeLibrary;
import com.arrl.radiocraft.common.radio.tape.TapeRecordings;
import com.arrl.radiocraft.common.radio.voice.RadioVoiceProcessor;
import com.arrl.radiocraft.common.radio.voice.RadiocraftVoicePlugin;
import com.arrl.radiocraft.common.radio.voice.RepeaterAudio;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A reel-to-reel tape player. It plays the tape in it out loud and into any radio touching it (keying the radio, as a
 * microphone would): once on PLAY, over and over, every so many minutes, or on a redstone pulse. On a blank or
 * recorded tape it records the voices of players near it, up to {@link TapeRecordings#MAX_SECONDS}.
 * <p>
 * Hoppers (2026-11-14, the maintainer's FM station): tapes put in from the top or a side wait in a queue of
 * {@link #QUEUE_SIZE}; in {@link Mode#QUEUE} it plays the next one whenever it has none, and a played tape goes to the
 * out tray ({@link #OUT_SIZE}), which a hopper below empties. A comparator reads how full the queue is.
 * <p>
 * It is a {@link ProgramSource} for an FM broadcast station on its data wire: while the station pulls it
 * ({@link #markPulled}, every tick), the station's pulls play the tape, a frame each, and it keys no FM transmitter;
 * other radios on its wire still hear it as before.
 */
public class TapePlayerBlockEntity extends BlockEntity implements MenuProvider, IVoiceTransmitter, ProgramSource {

	/** QUEUE (2026-11-14): plays the queued tapes one after another, each to the out tray when done. */
	public enum Mode { MANUAL, LOOP, INTERVAL, REDSTONE, QUEUE }

	/** Tapes waiting to play, and played ones waiting to be taken. */
	public static final int QUEUE_SIZE = 9, OUT_SIZE = 9;
	/** Ticks after one queued tape ends before the next starts: the break a station may talk in. */
	public static final int BREAK_TICKS = 40;
	/** Ticks a station's pull lasts: unpulled longer, the player plays by itself again. */
	public static final int PULL_TICKS = 10;

	public static final int STATE_IDLE = 0, STATE_PLAYING = 1, STATE_RECORDING = 2, STATE_LOADING = 3;
	/** How far it hears a voice to record, blocks. */
	public static final double RECORD_RANGE = 6.0D;
	public static final int MAX_INTERVAL_MINUTES = 60;
	private static final int FRAME = RepeaterAudio.FRAME;
	private static final long FRAME_NANOS = 20_000_000L;

	private ItemStack tape = ItemStack.EMPTY;
	private Mode mode = Mode.MANUAL;
	private int intervalMinutes = 5;
	private int state = STATE_IDLE;
	private int position; // Frames played.
	private long nextPlay = -1L;
	private boolean wasPowered;
	private final List<ItemStack> queue = new ArrayList<>();
	private final List<ItemStack> out = new ArrayList<>();
	/** When a queued tape ended: the break before the next lasts {@link #BREAK_TICKS}. */
	private long breakFrom = Long.MIN_VALUE / 2;
	/** When an FM station last pulled a frame ({@link #markPulled}). */
	private long pulledAt = Long.MIN_VALUE / 2;

	// Recording, filled on the voice chat thread: each player's frames land in the 20 ms slot they were spoken in, mixed.
	private final short[] recordBuffer = new short[TapeRecordings.MAX_SECONDS * (int)RadioVoiceProcessor.SAMPLE_RATE];
	private volatile boolean recording;
	private long recordStart;
	private int recordedFrames;
	/** Who spoke onto the tape, for the moderation log. Under recordBuffer's lock. */
	private final java.util.LinkedHashSet<UUID> recordSpeakers = new java.util.LinkedHashSet<>();

	@Nullable
	private LocationalAudioChannel channel;
	@Nullable
	private OpusEncoder encoder;
	private final List<RadioBlockEntity> keyed = new ArrayList<>();
	private final UUID voiceId;

	public TapePlayerBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.TAPE_PLAYER.get(), pos, state);
		voiceId = UUID.nameUUIDFromBytes(("radiocraft-tape-" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
	}

	// -------------------- THE TAPE --------------------

	public ItemStack getTape() {
		return tape;
	}

	/** Puts {@code stack} in, if the player is empty. True if it went in. */
	public boolean insert(ItemStack stack) {
		if(!tape.isEmpty() || !(stack.getItem() instanceof TapeItem))
			return false;
		tape = stack.copyWithCount(1);
		position = 0;
		nextPlay = -1L;
		changed();
		return true;
	}

	/** Takes the tape out (stopping it), or empty if there is none. */
	public ItemStack eject() {
		stop();
		ItemStack out = tape;
		tape = ItemStack.EMPTY;
		changed();
		return out;
	}

	private TapeContents contents() {
		return TapeItem.contents(tape);
	}

	/** The tape's audio: null while it loads, empty if there is nothing on it. */
	@Nullable
	private short[] audio(ServerLevel level) {
		TapeContents contents = contents();
		if(tape.isEmpty() || contents.isBlank())
			return new short[0];
		if(contents.isFound())
			return TapeLibrary.audio(level.getServer(), contents.track());
		UUID id = contents.recordingId();
		return id == null ? new short[0] : TapeRecordings.audio(level.getServer(), id);
	}

	// -------------------- QUEUE AND OUT TRAY --------------------

	/** The tapes waiting to play (a copy). */
	public List<ItemStack> getQueue() {
		return List.copyOf(queue);
	}

	/** The played tapes waiting to be taken (a copy). */
	public List<ItemStack> getOut() {
		return List.copyOf(out);
	}

	/** Queues one tape from {@code stack} (a hopper's way in). True if there was room. */
	public boolean enqueue(ItemStack stack) {
		if(queue.size() >= QUEUE_SIZE || !(stack.getItem() instanceof TapeItem))
			return false;
		queue.add(stack.copyWithCount(1));
		changed();
		return true;
	}

	/** Takes the played tape at {@code index} of the out tray, or empty. */
	public ItemStack takeOut(int index) {
		if(index < 0 || index >= out.size())
			return ItemStack.EMPTY;
		ItemStack taken = out.remove(index);
		changed();
		return taken;
	}

	/** Every tape in it (playing, queued, played), emptying it: for when it is broken. */
	public List<ItemStack> emptyAll() {
		List<ItemStack> all = new ArrayList<>();
		ItemStack playing = eject();
		if(!playing.isEmpty())
			all.add(playing);
		all.addAll(queue);
		all.addAll(out);
		queue.clear();
		out.clear();
		changed();
		return all;
	}

	/** 0 to 15 for a comparator: how full the queue is. */
	public int comparatorSignal() {
		return queue.isEmpty() ? 0 : 1 + (int)Math.floor(queue.size() * 14.0D / QUEUE_SIZE);
	}

	/** The hopper face of the tape player: in through the top and sides, out through the bottom. */
	public net.neoforged.neoforge.items.IItemHandler itemHandler(@Nullable Direction side) {
		return side == Direction.DOWN ? outHandler : inHandler;
	}

	private final net.neoforged.neoforge.items.IItemHandler inHandler = new net.neoforged.neoforge.items.IItemHandler() {
		@Override public int getSlots() { return QUEUE_SIZE; }
		@Override public @NotNull ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < queue.size() ? queue.get(slot) : ItemStack.EMPTY; }
		@Override public int getSlotLimit(int slot) { return 1; }
		@Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return stack.getItem() instanceof TapeItem; }
		@Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

		@Override
		public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
			if(stack.isEmpty() || !(stack.getItem() instanceof TapeItem) || queue.size() >= QUEUE_SIZE || slot < queue.size())
				return stack; // Only into the next free place, one tape at a time.
			if(!simulate)
				enqueue(stack);
			return stack.copyWithCount(stack.getCount() - 1);
		}
	};

	private final net.neoforged.neoforge.items.IItemHandler outHandler = new net.neoforged.neoforge.items.IItemHandler() {
		@Override public int getSlots() { return OUT_SIZE; }
		@Override public @NotNull ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < out.size() ? out.get(slot) : ItemStack.EMPTY; }
		@Override public int getSlotLimit(int slot) { return 1; }
		@Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }
		@Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return stack; }

		@Override
		public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
			if(amount <= 0 || slot < 0 || slot >= out.size())
				return ItemStack.EMPTY;
			return simulate ? out.get(slot).copy() : takeOut(slot);
		}
	};

	/** QUEUE mode with no tape in: the next queued one goes in. */
	private void loadNext() {
		if(mode != Mode.QUEUE || !tape.isEmpty() || queue.isEmpty())
			return;
		tape = queue.removeFirst();
		position = 0;
		changed();
	}

	/** QUEUE mode, a tape played through: to the out tray if there is room (else it stays in, and the queue waits). */
	private void putAway(long now) {
		if(mode != Mode.QUEUE || tape.isEmpty() || out.size() >= OUT_SIZE)
			return;
		out.add(tape);
		tape = ItemStack.EMPTY;
		breakFrom = now;
		changed();
	}

	// -------------------- CONTROLS --------------------

	public void play() {
		if(tape.isEmpty() || recording || contents().isBlank())
			return;
		state = STATE_LOADING;
		position = 0;
		changed();
	}

	public void stop() {
		if(recording)
			finishRecording();
		state = STATE_IDLE;
		position = 0;
		releaseRadios();
		changed();
	}

	/** Records over the tape (a found tape cannot be recorded on). */
	public void record() {
		if(tape.isEmpty() || contents().isFound() || level == null)
			return;
		stop();
		synchronized(recordBuffer) {
			java.util.Arrays.fill(recordBuffer, (short)0);
			recordedFrames = 0;
			recordStart = System.nanoTime();
			recordSpeakers.clear();
		}
		recording = true;
		state = STATE_RECORDING;
		changed();
	}

	public void setMode(Mode value) {
		mode = value;
		nextPlay = -1L;
		changed();
	}

	public void setIntervalMinutes(int minutes) {
		intervalMinutes = Math.max(1, Math.min(MAX_INTERVAL_MINUTES, minutes));
		nextPlay = -1L;
		changed();
	}

	public Mode getMode() {
		return mode;
	}

	public int getIntervalMinutes() {
		return intervalMinutes;
	}

	// -------------------- TICK --------------------

	public static void tick(Level level, BlockPos pos, BlockState blockState, TapePlayerBlockEntity player) {
		if(level instanceof ServerLevel serverLevel)
			player.serverTick(serverLevel);
	}

	// Client side: the reels, this tick and the last, in degrees, and how fast they turn.
	private float reels, reelsBefore, reelSpeed;

	/** Client side, each tick: the reels spin up while it runs and coast to a stop when it does not. */
	public static void clientTick(Level level, BlockPos pos, BlockState blockState, TapePlayerBlockEntity player) {
		player.reelsBefore = player.reels;
		float target = blockState.getValue(TapePlayerBlock.RUNNING) ? 9.0F : 0.0F;
		player.reelSpeed += (target - player.reelSpeed) * 0.15F;
		player.reels = (player.reels + player.reelSpeed) % 360.0F;
		if(player.reels < player.reelsBefore)
			player.reelsBefore -= 360.0F;
	}

	/** How far round the reels are, degrees, between the last tick and this one. */
	public float reelAngle(float partialTick) {
		return net.minecraft.util.Mth.lerp(partialTick, reelsBefore, reels);
	}

	private void serverTick(ServerLevel level) {
		long now = level.getGameTime();
		boolean powered = level.hasNeighborSignal(worldPosition);
		boolean pulse = powered && !wasPowered;
		wasPowered = powered;
		if(mode == Mode.QUEUE && state == STATE_IDLE) {
			if(!tape.isEmpty() && contents().isBlank())
				putAway(now); // Nothing to play on it: on with the next.
			if(tape.isEmpty() && now - breakFrom >= BREAK_TICKS)
				loadNext();
		}
		if(state == STATE_IDLE && !tape.isEmpty() && !contents().isBlank()) {
			boolean start = switch(mode) {
				case MANUAL -> false;
				case LOOP -> nextPlay < 0 || now >= nextPlay;
				case INTERVAL -> nextPlay < 0 || now >= nextPlay;
				case REDSTONE -> pulse;
				case QUEUE -> true;
			};
			if(start) {
				if(mode == Mode.INTERVAL)
					nextPlay = now + intervalMinutes * 1200L;
				play();
			}
		}
		if(state == STATE_RECORDING) {
			if(recordedSlots() >= TapeRecordings.MAX_SECONDS * 50)
				stop();
			return;
		}
		if(state != STATE_LOADING && state != STATE_PLAYING)
			return;
		if(pulled(now))
			return; // A station plays it, a frame a pull (nextFrame).
		int count = now % 2 == 0 ? 3 : 2;
		for(int i = 0; i < count; i++)
			if(advance(level) == null)
				return;
	}

	/**
	 * The next frame of the tape, sent out loud and into the radios it keys: null while it loads, or once it has played
	 * through (then it stops, and in QUEUE mode goes to the out tray).
	 */
	@Nullable
	private short[] advance(ServerLevel level) {
		if(state != STATE_LOADING && state != STATE_PLAYING)
			return null;
		short[] audio = audio(level);
		if(audio == null)
			return null; // Still loading.
		if(state == STATE_LOADING) {
			state = STATE_PLAYING;
			keyRadios(level);
			changed();
		}
		int from = position * FRAME;
		if(from >= audio.length) {
			finished(level.getGameTime());
			return null;
		}
		short[] frame = new short[FRAME];
		System.arraycopy(audio, from, frame, 0, Math.min(FRAME, audio.length - from));
		position++;
		send(level, frame);
		return frame;
	}

	private void finished(long now) {
		state = STATE_IDLE;
		position = 0;
		releaseRadios();
		if(mode == Mode.LOOP)
			nextPlay = now + 20; // A second's pause, then round again.
		putAway(now);
		changed();
	}

	// -------------------- PROGRAM SOURCE (an FM station on its data wire) --------------------

	/**
	 * An FM broadcast station is playing this tape player as its program: call every tick it does. While pulled
	 * (within {@link #PULL_TICKS}), its own tick sends nothing; each {@link #nextFrame} plays a frame.
	 */
	public void markPulled(long gameTime) {
		pulledAt = gameTime;
	}

	private boolean pulled(long now) {
		return now - pulledAt <= PULL_TICKS;
	}

	@Override
	@Nullable
	public short[] nextFrame() {
		if(!(level instanceof ServerLevel serverLevel))
			return null;
		markPulled(serverLevel.getGameTime());
		return advance(serverLevel);
	}

	@Override
	public boolean live() {
		if(state == STATE_PLAYING || state == STATE_LOADING)
			return true;
		boolean playable = !tape.isEmpty() && !contents().isBlank();
		return switch(mode) {
			case QUEUE -> playable || !queue.isEmpty();
			case LOOP -> playable;
			default -> false;
		};
	}

	@Override
	public String nowPlaying() {
		if(tape.isEmpty())
			return "";
		TapeContents contents = contents();
		if(contents.isFound())
			return TapeItem.label(contents.track());
		return tape.getHoverName().getString();
	}

	@Override
	public boolean atBreak() {
		return level != null && state != STATE_PLAYING && mode == Mode.QUEUE && level.getGameTime() - breakFrom < BREAK_TICKS + 20;
	}

	/** One frame out loud, and into every radio touching it. */
	private void send(ServerLevel level, short[] frame) {
		if(RadiocraftVoicePlugin.API == null)
			return;
		if(channel == null) {
			channel = RadiocraftVoicePlugin.API.createLocationalAudioChannel(UUID.randomUUID(), RadiocraftVoicePlugin.API.fromServerLevel(level),
					RadiocraftVoicePlugin.API.createPosition(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D));
			if(channel != null)
				channel.setDistance(16.0F);
			encoder = RadiocraftVoicePlugin.API.createEncoder();
		}
		RadiocraftVoicePlugin.followBlock(channel, level, worldPosition);
		if(channel != null && encoder != null)
			channel.send(encoder.encode(frame.clone()));
		de.maxhenkel.voicechat.api.ServerLevel voiceLevel = RadiocraftVoicePlugin.API.fromServerLevel(level);
		for(RadioBlockEntity radio : keyed)
			if(!radio.isRemoved() && radio.canTransmitVoice())
				radio.acceptVoicePacket(voiceLevel, frame.clone(), voiceId);
	}

	/** Keys every radio touching the player or on its data wire, as its push-to-talk would. */
	private void keyRadios(ServerLevel level) {
		releaseRadios();
		TapeContents contents = contents(); // The moderation log keeps a pointer to a player tape, a note for a server tape.
		com.arrl.radiocraft.common.moderation.ModerationHooks.tapePlaying(voiceId, worldPosition, contents.recordingId(), contents.track());
		for(RadioBlockEntity radio : com.arrl.radiocraft.common.be_networks.DataWire.linked(level, worldPosition, RadioBlockEntity.class)) {
			if(radio instanceof com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity)
				continue; // An FM station pulls its program (ProgramSource); it is not keyed like a radio.
			radio.setPTTDown(true);
			keyed.add(radio);
		}
	}

	private void releaseRadios() {
		for(RadioBlockEntity radio : keyed)
			if(!radio.isRemoved())
				radio.setPTTDown(false);
		keyed.clear();
	}

	// -------------------- RECORDING --------------------

	private int recordedSlots() {
		synchronized(recordBuffer) {
			return recordedFrames;
		}
	}

	@Override
	public boolean canTransmitVoice() {
		return recording;
	}

	@Override
	public Vec3 getPos() {
		return com.arrl.radiocraft.compat.sable.Sublevels.worldCenter(level, worldPosition);
	}

	@Override
	public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer) {
		if(!recording)
			return;
		synchronized(recordBuffer) {
			int slot = (int)((System.nanoTime() - recordStart) / FRAME_NANOS);
			int from = slot * FRAME;
			if(from + FRAME > recordBuffer.length)
				return;
			for(int i = 0; i < FRAME && i < rawAudio.length; i++)
				recordBuffer[from + i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, recordBuffer[from + i] + rawAudio[i]));
			recordedFrames = Math.max(recordedFrames, slot + 1);
			if(sourcePlayer != null && recordSpeakers.size() < 32)
				recordSpeakers.add(sourcePlayer);
		}
	}

	@Override
	public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer, Vec3 speakerPos) {
		if(speakerPos == null || speakerPos.distanceTo(getPos()) <= RECORD_RANGE)
			acceptVoicePacket(level, rawAudio, sourcePlayer);
	}

	/** Saves what was recorded onto the tape (nothing changes if nobody spoke). */
	private void finishRecording() {
		recording = false;
		short[] audio;
		List<UUID> speakers;
		synchronized(recordBuffer) {
			audio = java.util.Arrays.copyOf(recordBuffer, recordedFrames * FRAME);
			speakers = List.copyOf(recordSpeakers);
		}
		if(audio.length == 0 || !(level instanceof ServerLevel serverLevel) || tape.isEmpty())
			return;
		UUID id = contents().recordingId() != null ? contents().recordingId() : UUID.randomUUID();
		TapeRecordings.save(serverLevel.getServer(), id, audio);
		int seconds = Math.round(audio.length / (float)RadioVoiceProcessor.SAMPLE_RATE);
		tape.set(RadiocraftDataComponent.TAPE_CONTENTS.get(), contents().withRecording(id, seconds));
		com.arrl.radiocraft.common.moderation.ModerationHooks.tapeRecorded(serverLevel, worldPosition, id, seconds, speakers); // For the moderation log's tape search.
	}

	// -------------------- STATUS --------------------

	public int getState() {
		return state;
	}

	/** Seconds into the tape (or recorded so far). */
	public int getSeconds() {
		return state == STATE_RECORDING ? recordedSlots() / 50 : position / 50;
	}

	/** 0 no tape, 1 blank, 2 a found tape, 3 recorded. */
	public int getTapeKind() {
		if(tape.isEmpty())
			return 0;
		TapeContents contents = contents();
		return contents.isFound() ? 2 : contents.isRecorded() ? 3 : 1;
	}

	public static final int DATA_MODE = 0, DATA_INTERVAL = 1, DATA_STATE = 2, DATA_SECONDS = 3, DATA_TAPE = 4, DATA_QUEUE = 5, DATA_OUT = 6, DATA_COUNT = 7;

	public ContainerData dataSlots() {
		return new ContainerData() {
			@Override
			public int get(int index) {
				return switch(index) {
					case DATA_MODE -> mode.ordinal();
					case DATA_INTERVAL -> intervalMinutes;
					case DATA_STATE -> state;
					case DATA_SECONDS -> getSeconds();
					case DATA_TAPE -> getTapeKind();
					case DATA_QUEUE -> queue.size();
					case DATA_OUT -> out.size();
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
	}

	/** Marks the change, tells clients (the tape's label), and turns the reels on the block while it plays or records. */
	private void changed() {
		setChanged();
		if(level != null && !level.isClientSide) {
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); // The comparator reads the queue.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			BlockState blockState = getBlockState();
			boolean running = state == STATE_PLAYING || state == STATE_RECORDING;
			if(blockState.hasProperty(TapePlayerBlock.RUNNING) && blockState.getValue(TapePlayerBlock.RUNNING) != running)
				level.setBlock(worldPosition, blockState.setValue(TapePlayerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
		}
	}

	// -------------------- LIFE --------------------

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide)
			VoiceTransmitters.addListener(level, this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(level != null && !level.isClientSide) {
			VoiceTransmitters.removeListener(level, this);
			releaseRadios();
		}
		if(encoder != null) {
			encoder.close();
			encoder = null;
		}
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		setRemoved();
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		if(!tape.isEmpty())
			tag.put("tape", tape.save(registries));
		tag.putString("mode", mode.name().toLowerCase(Locale.ROOT));
		tag.putInt("interval", intervalMinutes);
		tag.put("queue", saveStacks(queue, registries));
		tag.put("out", saveStacks(out, registries));
	}

	private static net.minecraft.nbt.ListTag saveStacks(List<ItemStack> stacks, HolderLookup.Provider registries) {
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for(ItemStack stack : stacks)
			list.add(stack.save(registries));
		return list;
	}

	private static void loadStacks(CompoundTag tag, String key, List<ItemStack> into, int max, HolderLookup.Provider registries) {
		into.clear();
		net.minecraft.nbt.ListTag list = tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size() && into.size() < max; i++) {
			ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
			if(!stack.isEmpty())
				into.add(stack);
		}
	}

	/**
	 * Also how a structure presets one: {@code tape} (an item), {@code mode} (manual, loop, interval, redstone, queue),
	 * {@code interval} in minutes, {@code queue} and {@code out} (lists of items).
	 */
	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		tape = tag.contains("tape") ? ItemStack.parseOptional(registries, tag.getCompound("tape")) : ItemStack.EMPTY;
		try {
			mode = Mode.valueOf(tag.getString("mode").toUpperCase(Locale.ROOT));
		}
		catch(IllegalArgumentException e) {
			mode = Mode.MANUAL;
		}
		intervalMinutes = tag.contains("interval") ? Math.max(1, tag.getInt("interval")) : 5;
		loadStacks(tag, "queue", queue, QUEUE_SIZE, registries);
		loadStacks(tag, "out", out, OUT_SIZE, registries);
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable("container.radiocraft.tape_player");
	}

	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new TapePlayerMenu(id, this, dataSlots());
	}
}
