package com.arrl.radiocraft.common.radio.ft8;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One FT8 message decoded in a slot (computer batch 3B), a line of the decode list: when, how strong, where in the
 * passband, and what it said.
 *
 * @param slot     The slot it was sent in ({@link Ft8#slot}).
 * @param snr      Its SNR in dB, as the report a station would give it.
 * @param offsetHz Its audio offset above the dial.
 * @param text     The message as sent ({@link Ft8Message#encode}).
 */
public record Ft8Decode(long slot, int snr, int offsetHz, String text) {

	/** The message, or null if the text is not one. */
	@Nullable
	public Ft8Message message() {
		return Ft8Message.parse(text);
	}

	/** The decode list's line: "143015  -12  1234  CQ W1BTR JJ00". */
	public String line() {
		return Ft8.clock(slot) + " " + String.format(java.util.Locale.ROOT, "%4d %5d", snr, offsetHz) + "  " + text;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("slot", slot);
		tag.putInt("snr", snr);
		tag.putInt("offset", offsetHz);
		tag.putString("text", text);
		return tag;
	}

	public static Ft8Decode fromTag(CompoundTag tag) {
		return new Ft8Decode(tag.getLong("slot"), tag.getInt("snr"), tag.getInt("offset"), tag.getString("text"));
	}

	/** Decodes this much stronger than another at the same offset still come through; closer, both are lost. */
	public static final int CAPTURE_DB = 3;

	/**
	 * What a slot's signals decode as: two closer than {@link Ft8#COLLISION_HZ} in offset overlap, and only one
	 * {@link #CAPTURE_DB} stronger than the other comes through (neither, if they are about as strong). A station heard
	 * twice in a slot (through two radios) counts once. Order: strongest first.
	 */
	public static List<Ft8Decode> resolve(List<Ft8Decode> heard) {
		List<Ft8Decode> unique = new ArrayList<>();
		for(Ft8Decode decode : heard) {
			boolean seen = false;
			for(int i = 0; i < unique.size(); i++)
				if(unique.get(i).text.equals(decode.text) && unique.get(i).offsetHz == decode.offsetHz) {
					seen = true;
					if(decode.snr > unique.get(i).snr)
						unique.set(i, decode);
				}
			if(!seen)
				unique.add(decode);
		}
		boolean[] lost = new boolean[unique.size()];
		for(int i = 0; i < unique.size(); i++)
			for(int j = i + 1; j < unique.size(); j++) {
				Ft8Decode a = unique.get(i), b = unique.get(j);
				if(Math.abs(a.offsetHz - b.offsetHz) >= Ft8.COLLISION_HZ)
					continue;
				if(a.snr - b.snr >= CAPTURE_DB)
					lost[j] = true;
				else if(b.snr - a.snr >= CAPTURE_DB)
					lost[i] = true;
				else
					lost[i] = lost[j] = true;
			}
		List<Ft8Decode> out = new ArrayList<>();
		for(int i = 0; i < unique.size(); i++)
			if(!lost[i])
				out.add(unique.get(i));
		out.sort((a, b) -> Integer.compare(b.snr, a.snr));
		return out;
	}
}
