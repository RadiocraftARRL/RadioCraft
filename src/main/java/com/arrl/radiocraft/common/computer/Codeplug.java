package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialContact;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A commercial radio's programming as the radio programmer (CPS, 1.12 step 6) handles it: the model it was read from
 * and its channel positions, blanks included: always {@link CommercialProgram#MAX_CHANNELS} (64) here, of which the
 * radio holds {@link #capacity} (16 on every model but the upgraded handheld). A {@code .CPG} saved with 16 positions
 * (before zoned radios; and one of 40 from before the upgraded handheld held 64) loads with the rest blank. A key the radio would not let the programmer read is
 * {@link CommercialChannel#KEY_HIDDEN} here, as {@code CommercialProgramming.read} gave it: written back to the same
 * radio it keeps that radio's key; written to another it is refused. So a codeplug never holds a key that was not
 * readable. Saved as a {@code .CPG} file ({@link ComputerFileFormats#CODEPLUG}) and sent between the screen and the
 * server in the same layout ({@link #channelTag}). Pure (only NBT), so it is unit tested.
 * <p>
 * Layout: {@code model} (the model id, may be ""), {@code channels} (a list of up to 64 compounds: {@code name}, {@code rx},
 * {@code tx} (floats, Hz), {@code dig}, {@code tone}, {@code key} (int), {@code readable}), {@code unit} (the unit ID,
 * 0 for none) and {@code contacts} (a list of {@code id} and {@code alias}), both 1.12 playtest fixes 2 (older files
 * have neither: no ID, no contacts).
 *
 * @param model    The model id it was read from or meant for ({@code CommercialRadioModel#id}), or "".
 * @param channels Exactly {@link CommercialProgram#MAX_CHANNELS} positions.
 * @param unitId   The unit ID to write (0: the radio keeps its own, or is given one).
 * @param contacts The contact list (unit ID to alias).
 */
public record Codeplug(String model, List<CommercialChannel> channels, int unitId, List<CommercialContact> contacts) {

	public Codeplug {
		model = model == null ? "" : model;
		List<CommercialChannel> padded = new ArrayList<>(CommercialProgram.MAX_CHANNELS);
		for(int i = 0; i < CommercialProgram.MAX_CHANNELS; i++)
			padded.add(i < channels.size() && channels.get(i) != null ? channels.get(i) : CommercialChannel.BLANK);
		channels = List.copyOf(padded);
		unitId = unitId < 0 || unitId > CommercialProgram.UNIT_MAX ? 0 : unitId;
		contacts = new CommercialProgram(List.of(), 0, 0, contacts == null ? List.of() : contacts).contacts(); // Cleaned as a radio would keep them.
	}

	/** A codeplug with no unit ID and no contacts. */
	public Codeplug(String model, List<CommercialChannel> channels) {
		this(model, channels, 0, List.of());
	}

	/** This codeplug with unit ID {@code id}. */
	public Codeplug withUnitId(int id) {
		return new Codeplug(model, channels, id, contacts);
	}

	/** This codeplug with the contact list {@code list}. */
	public Codeplug withContacts(List<CommercialContact> list) {
		return new Codeplug(model, channels, unitId, list);
	}

	/** This codeplug meant for model {@code newModel}, everything else kept. */
	public Codeplug forModel(String newModel) {
		return new Codeplug(newModel, channels, unitId, contacts);
	}

	/**
	 * How many channels the radio this codeplug is for holds: its model's {@link CommercialRadioModel#channelCount},
	 * or {@link CommercialProgram#MAX_CHANNELS} for no or an unknown model.
	 */
	public int capacity() {
		CommercialRadioModel radio = CommercialRadioModel.byId(model);
		return radio == null ? CommercialProgram.MAX_CHANNELS : radio.channelCount();
	}

	/** How many positions past {@link #capacity} hold a channel (they would be refused by that radio). */
	public int beyondCapacity() {
		int n = 0;
		for(int i = capacity(); i < channels.size(); i++)
			if(!channels.get(i).isBlank())
				n++;
		return n;
	}

	/** An empty codeplug for {@code model}. */
	public static Codeplug blank(String model) {
		return new Codeplug(model, List.of());
	}

	public CommercialChannel channel(int position) {
		return position >= 0 && position < channels.size() ? channels.get(position) : CommercialChannel.BLANK;
	}

	/** This codeplug with {@code channel} at {@code position}. */
	public Codeplug with(int position, CommercialChannel channel) {
		if(position < 0 || position >= CommercialProgram.MAX_CHANNELS)
			return this;
		List<CommercialChannel> out = new ArrayList<>(channels);
		out.set(position, channel);
		return new Codeplug(model, out, unitId, contacts);
	}

	/** How many positions hold a channel. */
	public int programmedCount() {
		int n = 0;
		for(CommercialChannel channel : channels)
			if(!channel.isBlank())
				n++;
		return n;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("model", model);
		tag.put("channels", channelsTag(channels));
		putIdentity(tag, unitId, contacts);
		return tag;
	}

	public static Codeplug fromTag(CompoundTag tag) {
		return new Codeplug(tag.getString("model"), channels(tag.getList("channels", Tag.TAG_COMPOUND)), tag.getInt("unit"), contacts(tag));
	}

	/** Puts {@code unit} and {@code contacts} into {@code tag} (the file's and the requests' layout). */
	public static void putIdentity(CompoundTag tag, int unitId, List<CommercialContact> contacts) {
		tag.putInt("unit", unitId);
		ListTag list = new ListTag();
		for(CommercialContact contact : contacts) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("id", contact.id());
			entry.putString("alias", contact.alias());
			list.add(entry);
		}
		tag.put("contacts", list);
	}

	/** The contact list in {@code tag}'s {@code contacts} (empty if it has none). */
	public static List<CommercialContact> contacts(CompoundTag tag) {
		ListTag list = tag.getList("contacts", Tag.TAG_COMPOUND);
		List<CommercialContact> out = new ArrayList<>();
		for(int i = 0; i < list.size() && i < CommercialProgram.MAX_CONTACTS; i++) {
			CompoundTag entry = list.getCompound(i);
			out.add(new CommercialContact(entry.getInt("id"), entry.getString("alias")));
		}
		return out;
	}

	/** A list of channels as tags (what the programmer's requests carry). */
	public static ListTag channelsTag(List<CommercialChannel> channels) {
		ListTag list = new ListTag();
		for(CommercialChannel channel : channels)
			list.add(channelTag(channel));
		return list;
	}

	/** The channels in a list of tags (at most {@link CommercialProgram#MAX_CHANNELS} read). */
	public static List<CommercialChannel> channels(ListTag list) {
		List<CommercialChannel> out = new ArrayList<>();
		for(int i = 0; i < list.size() && i < CommercialProgram.MAX_CHANNELS; i++)
			out.add(channel(list.getCompound(i)));
		return out;
	}

	public static CompoundTag channelTag(CommercialChannel channel) {
		CompoundTag tag = new CompoundTag();
		if(channel.isBlank())
			return tag;
		tag.putString("name", channel.name());
		tag.putFloat("rx", channel.rxHz());
		tag.putFloat("tx", channel.txHz());
		tag.putBoolean("dig", channel.digital());
		tag.putFloat("tone", channel.ctcssTone());
		tag.putInt("key", channel.keyId());
		tag.putBoolean("readable", channel.keyReadable());
		return tag;
	}

	/** A channel from its tag; an empty tag is a blank position. Not checked: the radio checks it when written. */
	public static CommercialChannel channel(CompoundTag tag) {
		if(!tag.contains("rx"))
			return CommercialChannel.BLANK;
		boolean readable = !tag.contains("readable") || tag.getBoolean("readable");
		return new CommercialChannel(tag.getString("name"), tag.getFloat("rx"), tag.getFloat("tx"), tag.getBoolean("dig"),
				tag.getFloat("tone"), tag.getInt("key"), readable);
	}
}
