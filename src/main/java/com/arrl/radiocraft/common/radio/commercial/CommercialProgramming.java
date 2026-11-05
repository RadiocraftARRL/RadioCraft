package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The programming interface of the commercial radios: what a radio shop's programmer (the radio computer's programmer
 * app, step 6) and {@code /rcdebug commercial} read from and write to a radio. Nothing else changes a commercial
 * radio's channels; players only turn its knob ({@link #select}).
 * <ul>
 *     <li>Find the radio with {@link #of(ItemStack)} (a handheld, or a mobile's item before it is placed) or
 *     {@link #of(Level, BlockPos)} (a placed mobile).</li>
 *     <li>{@link #read} gives the radio's channel positions (16, or 40 on the upgraded handheld), with every key the radio marks as not readable hidden
 *     ({@link CommercialChannel#KEY_HIDDEN}).</li>
 *     <li>{@link #write} (or {@link #writeChannel} for one position) checks every channel against the radio's model and
 *     writes all or nothing. A channel written back with a hidden key keeps the key the radio has at that position.</li>
 * </ul>
 * Server side: write on the server, then the radio syncs itself to clients.
 */
public final class CommercialProgramming {

	private CommercialProgramming() {}

	/** A radio that can be programmed. */
	public interface Target {
		/** What the radio can do. */
		CommercialRadioModel model();

		/** Its programming as stored (keys and all: use {@link #read} for what a programmer may see). */
		CommercialProgram program();

		/** Stores {@code program} as it is (already checked) and puts the radio on the knob's channel. */
		void storeProgram(CommercialProgram program);
	}

	/** Something {@link #write} refused: the knob position (0-based) and a translation key under {@code message.radiocraft.commercial.}. */
	public record Problem(int position, String key) {}

	/** What a write did: nothing if there are problems, everything otherwise. */
	public record Result(List<Problem> problems) {
		public static final Result OK = new Result(List.of());

		public boolean ok() {
			return problems.isEmpty();
		}
	}

	/** The radio {@code stack} as a programming target (a commercial handheld or mobile item), or null. */
	@Nullable
	public static Target of(ItemStack stack) {
		if(stack.getItem() instanceof CommercialHandheldItem handheld)
			return new HandheldTarget(stack, handheld.getModel());
		if(stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CommercialMobileBlock mobile)
			return new MobileItemTarget(stack, mobile.getModel());
		return null;
	}

	/** The commercial radio placed at {@code pos} as a programming target, or null. */
	@Nullable
	public static Target of(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CommercialMobileBlockEntity mobile ? mobile : null;
	}

	/** True if {@code stack} is a radio the programmer can work on. */
	public static boolean isProgrammable(ItemStack stack) {
		return of(stack) != null;
	}

	/**
	 * Every channel position the radio holds ({@link CommercialRadioModel#channelCount}: 16, or 40 on the upgraded
	 * handheld), blanks included, keys hidden where they may not be read.
	 */
	public static List<CommercialChannel> read(Target target) {
		CommercialProgram program = target.program();
		int capacity = target.model().channelCount();
		List<CommercialChannel> out = new ArrayList<>(capacity);
		for(int i = 0; i < capacity; i++)
			out.add(program.channel(i).withKeyHidden());
		return out;
	}

	/**
	 * Checks {@code channels} (position 0 first, at most the model's {@link CommercialRadioModel#channelCount}; fewer
	 * leaves the rest blank; blanks past the capacity are ignored) against {@code target}'s model and, if every one
	 * passes, writes them all. The knob and zone switch stay where they were.
	 */
	public static Result write(Target target, List<CommercialChannel> channels) {
		return write(target, channels, 0, null);
	}

	/**
	 * {@link #write} with the radio's identity (1.12 playtest fixes 2): {@code unitId} 1-9999 becomes its unit ID
	 * (0 keeps the one it has), {@code contacts} its contact list (null keeps it). A radio left with no unit ID is
	 * given one at random ({@link #assignUnitId}), as the first program of a fleet radio gets its number.
	 */
	public static Result write(Target target, List<CommercialChannel> channels, int unitId, @Nullable List<CommercialContact> contacts) {
		CommercialProgram old = target.program();
		int capacity = target.model().channelCount();
		List<Problem> problems = new ArrayList<>();
		List<CommercialChannel> resolved = new ArrayList<>();
		for(int i = capacity; i < channels.size(); i++)
			if(!channels.get(i).isBlank()) {
				problems.add(new Problem(i, "too_many_channels"));
				break;
			}
		for(int i = 0; i < Math.min(channels.size(), capacity); i++) {
			CommercialChannel channel = resolve(old.channel(i), channels.get(i), i, problems);
			if(channel == null)
				continue;
			String problem = target.model().check(channel);
			if(problem != null)
				problems.add(new Problem(i, problem));
			resolved.add(channel);
		}
		if(!problems.isEmpty())
			return new Result(List.copyOf(problems));
		CommercialProgram made = old.withChannels(resolved);
		if(unitId > 0 && unitId <= CommercialProgram.UNIT_MAX)
			made = made.withUnitId(unitId);
		if(contacts != null)
			made = made.withContacts(contacts);
		target.storeProgram(target.model().fit(assignUnitId(made)));
		return Result.OK;
	}

	/** {@code program} with a unit ID: its own, or one at random (1-9999) if it has none. */
	public static CommercialProgram assignUnitId(CommercialProgram program) {
		if(program.unitId() > 0)
			return program;
		return program.withUnitId(java.util.concurrent.ThreadLocalRandom.current().nextInt(1, CommercialProgram.UNIT_MAX + 1));
	}

	/** Sets the radio's unit ID ({@code 0} gives it one at random) and keeps everything else. */
	public static void setUnitId(Target target, int unitId) {
		CommercialProgram program = target.program().withUnitId(unitId);
		target.storeProgram(target.model().fit(assignUnitId(program)));
	}

	/**
	 * The radio's channels as a cloning cable copies them (1.12 playtest fixes 2): every position it holds, a key the
	 * radio marks as not readable left behind (that channel comes out with no key: clear), readable keys copied.
	 */
	public static List<CommercialChannel> readForClone(Target target) {
		CommercialProgram program = target.program();
		int capacity = target.model().channelCount();
		List<CommercialChannel> out = new ArrayList<>(capacity);
		for(int i = 0; i < capacity; i++)
			out.add(withoutHiddenKey(program.channel(i)));
		return out;
	}

	/** {@code channel} with a key that may not be read taken off (clear digital); anything else as it is. */
	public static CommercialChannel withoutHiddenKey(CommercialChannel channel) {
		if(channel.isBlank() || channel.keyId() == 0 || channel.keyReadable() && channel.keyId() != CommercialChannel.KEY_HIDDEN)
			return channel;
		return new CommercialChannel(channel.name(), channel.rxHz(), channel.txHz(), channel.digital(), channel.ctcssTone(), 0, true);
	}

	/** How many of {@code target}'s channels carry a key that a clone leaves behind. */
	public static int hiddenKeys(Target target) {
		CommercialProgram program = target.program();
		int n = 0;
		for(int i = 0; i < target.model().channelCount(); i++) {
			CommercialChannel channel = program.channel(i);
			if(!channel.isBlank() && channel.keyId() != 0 && !channel.keyReadable())
				n++;
		}
		return n;
	}

	/** {@link #write} for the one knob position {@code position} (0-based), the others kept. */
	public static Result writeChannel(Target target, int position, CommercialChannel channel) {
		int capacity = target.model().channelCount();
		if(position < 0 || position >= capacity)
			return new Result(List.of(new Problem(position, "no_such_channel")));
		List<CommercialChannel> channels = new ArrayList<>();
		CommercialProgram old = target.program();
		for(int i = 0; i < capacity; i++)
			channels.add(i == position ? channel : old.channel(i));
		return write(target, channels);
	}

	/** Clears knob position {@code position} (0-based). */
	public static Result clear(Target target, int position) {
		return writeChannel(target, position, CommercialChannel.BLANK);
	}

	/**
	 * Selects channel position {@code position} (0-based; on a radio with a zone switch, the zone is position / 16 and
	 * the knob position % 16), as the user would.
	 */
	public static void select(Target target, int position) {
		target.storeProgram(target.model().fit(target.program().select(position)));
	}

	/** Back to the factory channels ({@link CommercialRadioModel#factoryProgram}). */
	public static void factoryReset(Target target) {
		target.storeProgram(target.model().factoryProgram());
	}

	/**
	 * {@code wanted} made valid, with a hidden key taken from {@code old} (what the radio has at that position); null
	 * (and a problem) if the key is hidden and the radio has none there to keep.
	 */
	@Nullable
	private static CommercialChannel resolve(CommercialChannel old, CommercialChannel wanted, int position, List<Problem> problems) {
		CommercialChannel channel = wanted.sanitized();
		if(channel.keyId() != CommercialChannel.KEY_HIDDEN)
			return channel;
		if(old.isBlank() || old.keyId() <= 0) {
			problems.add(new Problem(position, "hidden_key_unknown"));
			return null;
		}
		// The kept key stays as unreadable as it was: a programmer cannot make a key it may not see readable without
		// typing the key in again (step 6 fix: the readable flag used to come from the request).
		return new CommercialChannel(channel.name(), channel.rxHz(), channel.txHz(), channel.digital(), channel.ctcssTone(),
				old.keyId(), old.keyReadable() && channel.keyReadable());
	}

	/** A handheld's programming, on its stack. */
	private record HandheldTarget(ItemStack stack, CommercialRadioModel model) implements Target {
		@Override
		public CommercialProgram program() {
			return CommercialHandheldItem.program(stack);
		}

		@Override
		public void storeProgram(CommercialProgram program) {
			CommercialHandheldItem.setProgram(stack, program);
		}
	}

	/** A mobile's programming while it is an item: the block entity takes it when placed. */
	private record MobileItemTarget(ItemStack stack, CommercialRadioModel model) implements Target {
		@Override
		public CommercialProgram program() {
			CommercialProgram program = stack.get(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value());
			return program == null ? model.factoryProgram() : model.fit(program);
		}

		@Override
		public void storeProgram(CommercialProgram program) {
			stack.set(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value(), program);
		}
	}
}
