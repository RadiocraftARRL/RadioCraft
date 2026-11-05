package com.arrl.radiocraft.common.radio.commercial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A commercial radio's programming: its channel list (position 0 is channel 1) and the channel position selected.
 * Every radio has a {@link #KNOB_POSITIONS}-position channel knob; a radio holding more channels than that (the upgraded
 * handheld, 40) also has a zone switch, each zone {@link #KNOB_POSITIONS} channels: zone A is channels 1-16, B 17-32,
 * C 33-48 (of which a 40-channel radio has 33-40; the knob's other positions in zone C are unprogrammed). So
 * {@code selected} is {@code zone * 16 + knob}. Positions past the end of the list, or holding
 * {@link CommercialChannel#BLANK}, are unprogrammed. How many channels a radio holds is its model's
 * ({@link CommercialRadioModel#channelCount}, {@link CommercialRadioModel#fit}); this record only enforces the largest. Stored as the {@code radiocraft:commercial_program} data component on handhelds and on the mobile's
 * item, and in the mobile's block entity once placed. Only {@link CommercialProgramming} (the radio computer's
 * programmer, step 6, and {@code /rcdebug commercial}) writes the channels; players only turn the knob.
 *
 * @param channels The channels, at most {@link #MAX_CHANNELS}.
 * @param selected The selected position (zone x 16 + knob), 0 to {@link #MAX_POSITIONS} - 1. Programs saved before
 *                 zones (1.12 playtest) only ever hold 0-15, which is zone A: they load unchanged.
 * @param unitId   The radio's unit ID (MDC-1200 on analog, the P25 unit ID on digital), sent with every key-up:
 *                 1 to {@link #UNIT_MAX}, 0 for none. Each radio's own: written by the programmer, given one at random
 *                 the first time it is programmed, never copied by a clone (1.12 playtest fixes 2).
 * @param contacts The contact list: unit ID to alias, shown by radios with a display while that unit talks. At most
 *                 {@link #MAX_CONTACTS}.
 */
public record CommercialProgram(List<CommercialChannel> channels, int selected, int unitId, List<CommercialContact> contacts) {

	/** Highest unit ID (four digits, as the radios' displays and the CPS show them). */
	public static final int UNIT_MAX = 9999;
	/** Most entries in a contact list. */
	public static final int MAX_CONTACTS = 32;

	/** Positions of the channel knob on every commercial radio here: a 16-position rotary switch. */
	public static final int KNOB_POSITIONS = 16;
	/** The most channels any model holds (the upgraded handheld). */
	public static final int MAX_CHANNELS = 40;
	/** Zones on the largest zone switch. */
	public static final int MAX_ZONES = zonesFor(MAX_CHANNELS);
	/** Selectable positions on the largest radio: every zone's every knob position. */
	public static final int MAX_POSITIONS = MAX_ZONES * KNOB_POSITIONS;

	public static final CommercialProgram EMPTY = new CommercialProgram(List.of(), 0);

	public CommercialProgram {
		channels = List.copyOf(channels.size() > MAX_CHANNELS ? channels.subList(0, MAX_CHANNELS) : channels);
		selected = Math.max(0, Math.min(MAX_POSITIONS - 1, selected));
		unitId = unitId < 0 || unitId > UNIT_MAX ? 0 : unitId;
		contacts = cleanContacts(contacts);
	}

	/** A program with no unit ID and no contacts. */
	public CommercialProgram(List<CommercialChannel> channels, int selected) {
		this(channels, selected, 0, List.of());
	}

	public static final Codec<CommercialProgram> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CommercialChannel.CODEC.listOf().optionalFieldOf("channels", List.of()).forGetter(CommercialProgram::channels),
			Codec.INT.optionalFieldOf("selected", 0).forGetter(CommercialProgram::selected),
			Codec.INT.optionalFieldOf("unit", 0).forGetter(CommercialProgram::unitId),
			CommercialContact.CODEC.listOf().optionalFieldOf("contacts", List.of()).forGetter(CommercialProgram::contacts)
	).apply(instance, CommercialProgram::new));

	public static final StreamCodec<ByteBuf, CommercialProgram> STREAM_CODEC = StreamCodec.composite(
			CommercialChannel.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHANNELS)), CommercialProgram::channels,
			ByteBufCodecs.VAR_INT, CommercialProgram::selected,
			ByteBufCodecs.VAR_INT, CommercialProgram::unitId,
			CommercialContact.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONTACTS)), CommercialProgram::contacts,
			CommercialProgram::new);

	/** The channel at knob position {@code position} (0-based), {@link CommercialChannel#BLANK} if unprogrammed. */
	public CommercialChannel channel(int position) {
		return position >= 0 && position < channels.size() ? channels.get(position) : CommercialChannel.BLANK;
	}

	/** The channel the knob is on, or null if that position is unprogrammed. */
	@Nullable
	public CommercialChannel active() {
		CommercialChannel channel = channel(selected);
		return channel.isBlank() ? null : channel;
	}

	/** The zone switch positions a radio holding {@code channels} channels needs: 1 for 16 or fewer, 3 for 40. */
	public static int zonesFor(int channels) {
		return Math.max(1, (channels + KNOB_POSITIONS - 1) / KNOB_POSITIONS);
	}

	/** The letter of zone {@code zone} (0 = A). */
	public static char zoneLetter(int zone) {
		return (char)('A' + Math.max(0, Math.min(25, zone)));
	}

	/** The knob's position, 0-15. */
	public int knob() {
		return selected % KNOB_POSITIONS;
	}

	/** The zone switch's position, 0 = A. Always 0 on a radio with no zone switch. */
	public int zone() {
		return selected / KNOB_POSITIONS;
	}

	/** Position {@code position} selected (zone x 16 + knob; clamped to the largest radio). */
	public CommercialProgram select(int position) {
		return new CommercialProgram(channels, position, unitId, contacts);
	}

	/**
	 * The knob turned {@code steps} clicks (negative is down), in the zone the zone switch is on. It stops at 1 and 16
	 * like a real rotary switch, rather than wrapping round.
	 */
	public CommercialProgram turn(int steps) {
		return select(zone() * KNOB_POSITIONS + Math.max(0, Math.min(KNOB_POSITIONS - 1, knob() + steps)));
	}

	/** The zone switch on {@code zone} (clamped to {@code zones} positions), the knob where it is. */
	public CommercialProgram zone(int zone, int zones) {
		return select(Math.max(0, Math.min(zones - 1, zone)) * KNOB_POSITIONS + knob());
	}

	/**
	 * This program on a radio holding {@code capacity} channels: channels past it dropped, the zone switch on its
	 * range (a 16-channel radio is always in zone A).
	 */
	public CommercialProgram fitTo(int capacity) {
		int max = Math.max(1, Math.min(MAX_CHANNELS, capacity));
		List<CommercialChannel> kept = channels.size() > max ? channels.subList(0, max) : channels;
		int fitted = Math.min(zone(), zonesFor(max) - 1) * KNOB_POSITIONS + knob();
		if(kept.size() == channels.size() && fitted == selected)
			return this;
		return new CommercialProgram(trim(new ArrayList<>(kept)), fitted, unitId, contacts);
	}

	/** {@code channel} written at {@code position} (padding with blanks), everything else kept. */
	public CommercialProgram with(int position, CommercialChannel channel) {
		if(position < 0 || position >= MAX_CHANNELS)
			return this;
		List<CommercialChannel> out = new ArrayList<>(channels);
		while(out.size() <= position)
			out.add(CommercialChannel.BLANK);
		out.set(position, channel);
		return new CommercialProgram(trim(out), selected, unitId, contacts);
	}

	/** A whole new channel list, the knob where it was. */
	public CommercialProgram withChannels(List<CommercialChannel> newChannels) {
		return new CommercialProgram(trim(new ArrayList<>(newChannels)), selected, unitId, contacts);
	}

	/** This program with unit ID {@code id} (0 for none). */
	public CommercialProgram withUnitId(int id) {
		return new CommercialProgram(channels, selected, id, contacts);
	}

	/** This program with {@code list} as its contact list. */
	public CommercialProgram withContacts(List<CommercialContact> list) {
		return new CommercialProgram(channels, selected, unitId, list);
	}

	/** The alias the contact list gives unit {@code id}, or null if it has none. */
	@Nullable
	public String alias(int id) {
		for(CommercialContact contact : contacts)
			if(contact.id() == id)
				return contact.alias();
		return null;
	}

	/** What a display shows for unit {@code id}: its alias, or "ID 1234". */
	public String callerText(int id) {
		String alias = alias(id);
		return alias != null ? alias : unitText(id);
	}

	/** Unit {@code id} as a display shows it with no alias: "ID 1234". */
	public static String unitText(int id) {
		return "ID " + id;
	}

	/** Valid contacts only (an ID and an alias), one per ID (the first kept), at most {@link #MAX_CONTACTS}. */
	private static List<CommercialContact> cleanContacts(@Nullable List<CommercialContact> list) {
		if(list == null || list.isEmpty())
			return List.of();
		List<CommercialContact> out = new ArrayList<>();
		for(CommercialContact contact : list) {
			if(contact == null || !contact.valid() || out.size() >= MAX_CONTACTS)
				continue;
			boolean seen = false;
			for(CommercialContact kept : out)
				if(kept.id() == contact.id()) {
					seen = true;
					break;
				}
			if(!seen)
				out.add(contact);
		}
		return List.copyOf(out);
	}

	/** How many positions hold a channel. */
	public int programmedCount() {
		int n = 0;
		for(CommercialChannel channel : channels)
			if(!channel.isBlank())
				n++;
		return n;
	}

	/** Trailing blanks dropped. */
	private static List<CommercialChannel> trim(List<CommercialChannel> list) {
		while(!list.isEmpty() && list.getLast().isBlank())
			list.removeLast();
		return list;
	}
}
