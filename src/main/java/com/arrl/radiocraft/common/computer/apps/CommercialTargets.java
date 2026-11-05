package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.computer.Codeplug;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The commercial radios a radio computer reaches through a cable (1.12 step 6), for the radio programmer (CPS) and the
 * frequency manager's export: any placed commercial mobile touching the computer or on its data wire (the programming
 * cable). Looked up afresh for every request, so a radio unwired is gone at once, and a client can only name a radio
 * the computer really reaches. Handhelds and mobiles still in item form are programmed by hand instead: the CPS's
 * "Program radios" mode, the radio held and the computer right clicked ({@link HandProgramming}; 1.12 playtest fixes 2
 * replaced the scanner glass).
 * <p>
 * Requests name a radio by its id: {@code pos:<BlockPos.asLong>} for a placed mobile. The shared actions
 * ({@link #radios}, {@link #read}, {@link #write}) answer in the layout {@link Codeplug} uses.
 */
public final class CommercialTargets {

	/** Most wired mobiles listed. */
	private static final int MAX_LINKED = 8;

	/**
	 * A radio the computer reaches.
	 *
	 * @param id         what requests call it
	 * @param nameKey    its item or block's translation key
	 * @param customName its name from an anvil, or ""
	 * @param where      its position "x y z", or "hand" for a radio held by the player
	 * @param target     the radio to program
	 * @param saved      run after it was written
	 */
	public record Found(String id, String nameKey, String customName, String where, CommercialProgramming.Target target, Runnable saved) {}

	private CommercialTargets() {}

	/** Every commercial radio the computer reaches: wired mobiles, nearest first. */
	public static List<Found> list(ComputerContext context) {
		List<Found> out = new ArrayList<>();
		for(CommercialMobileBlockEntity mobile : context.linked(CommercialMobileBlockEntity.class)) {
			if(out.size() > MAX_LINKED)
				break;
			BlockPos pos = mobile.getBlockPos();
			out.add(new Found("pos:" + pos.asLong(), mobile.getBlockState().getBlock().getDescriptionId(), "",
					pos.getX() + " " + pos.getY() + " " + pos.getZ(), mobile, () -> {}));
		}
		return out;
	}

	/** The radio called {@code id} that the computer reaches now, or null. */
	@Nullable
	public static Found find(ComputerContext context, String id) {
		for(Found found : list(context))
			if(found.id().equals(id))
				return found;
		return null;
	}

	static String customName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	/**
	 * What the screen shows of a radio: its names, where it is, and what its model can be programmed with
	 * ({@code capacity}: how many channels it holds, 16 or 40).
	 */
	public static CompoundTag describe(Found found) {
		CompoundTag out = new CompoundTag();
		out.putString("id", found.id());
		out.putString("nameKey", found.nameKey());
		out.putString("custom", found.customName());
		out.putString("where", found.where());
		CommercialRadioModel model = found.target().model();
		out.putString("model", model.id());
		out.putInt("capacity", model.channelCount());
		out.putBoolean("digital", model.digital());
		out.putBoolean("encryption", model.encryption());
		out.putBoolean("display", model.hasDisplay());
		ListTag bands = new ListTag();
		for(Band band : model.bands())
			bands.add(StringTag.valueOf(String.format(Locale.ROOT, "%.3f-%.3f", band.minFrequency() / 1e6, band.maxFrequency() / 1e6)));
		out.put("bands", bands);
		return out;
	}

	// -------------------- SHARED ACTIONS --------------------

	/** "radios": {@code radios}, a list of {@link #describe} compounds. */
	public static CompoundTag radios(ComputerContext context) {
		ListTag list = new ListTag();
		for(Found found : list(context))
			list.add(describe(found));
		CompoundTag out = new CompoundTag();
		out.put("radios", list);
		return out;
	}

	/**
	 * "read": the radio described, its channel positions ({@code channels}, as many as it holds, keys hidden where they
	 * may not be read) and the selected position.
	 */
	public static CompoundTag read(Found found) {
		CompoundTag out = describe(found);
		CommercialProgram program = found.target().program();
		out.put("channels", Codeplug.channelsTag(CommercialProgramming.read(found.target())));
		out.putInt("selected", program.selected());
		Codeplug.putIdentity(out, program.unitId(), program.contacts());
		return out;
	}

	/**
	 * Writes {@code channels} (every position; blanks past the radio's capacity are ignored) into {@code found}. The answer is {@link #read} after the write, with
	 * {@code problems} (a list of {@code pos} and {@code key}, keys under {@code message.radiocraft.commercial.}) and
	 * nothing written if any channel was refused.
	 */
	public static CompoundTag write(Found found, List<CommercialChannel> channels) {
		return write(found, channels, 0, null);
	}

	/**
	 * {@link #write} with the radio's identity: {@code unitId} (0 keeps the radio's, or gives it one) and
	 * {@code contacts} (null keeps the radio's list).
	 */
	public static CompoundTag write(Found found, List<CommercialChannel> channels, int unitId, @Nullable List<com.arrl.radiocraft.common.radio.commercial.CommercialContact> contacts) {
		CommercialProgramming.Result result = CommercialProgramming.write(found.target(), channels, unitId, contacts);
		if(result.ok())
			found.saved().run();
		CompoundTag out = read(found);
		out.put("problems", problems(result));
		return out;
	}

	public static ListTag problems(CommercialProgramming.Result result) {
		ListTag list = new ListTag();
		for(CommercialProgramming.Problem problem : result.problems()) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("pos", problem.position());
			entry.putString("key", problem.key());
			list.add(entry);
		}
		return list;
	}

	/** The {@link CommercialProgram#MAX_CHANNELS} positions of a request's {@code channels} list (missing ones blank). */
	public static List<CommercialChannel> channels(CompoundTag data) {
		List<CommercialChannel> channels = new ArrayList<>(Codeplug.channels(data.getList("channels", Tag.TAG_COMPOUND)));
		while(channels.size() < CommercialProgram.MAX_CHANNELS)
			channels.add(CommercialChannel.BLANK);
		return channels;
	}
}
