package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The frequency manager's list of saved channels (1.12 step 6): a name, the receive and transmit frequencies, the mode,
 * a CTCSS tone and notes, for reference (nets, repeaters, the club's simplex) and for programming commercial radios
 * ({@link Entry#toChannel()}; FM and digital entries only, with no encryption keys: keys are the programmer's). Stored
 * as a {@code .FRQ} file. Pure (only NBT and the channel record), so it is unit tested.
 * <p>
 * File layout (compressed NBT, {@link ComputerFileFormats#FREQUENCIES}): {@code channels}, a list of compounds with
 * {@code name}, {@code rx} and {@code tx} (double, MHz; tx 0 for receive only), {@code mode}, {@code tone} (float, Hz)
 * and {@code notes}.
 */
public final class FrequencyList {

	/** Most entries one list holds. */
	public static final int MAX_ENTRIES = 500;
	public static final int NAME_MAX = 16, NOTES_MAX = 60;

	/** How the frequency is used. Commercial radios take {@link #FM} and {@link #DIG} only. */
	public enum Mode {
		FM, DIG, USB, LSB, CW, AM;

		/** True if a commercial radio can be programmed with it. */
		public boolean commercial() {
			return this == FM || this == DIG;
		}

		/** {@code text} as a mode (any case; "P25" and "DIGITAL" are DIG, "SSB" is USB), or null. */
		@Nullable
		public static Mode parse(String text) {
			String t = text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
			return switch(t) {
				case "FM", "NFM", "ANALOG" -> FM;
				case "DIG", "DIGITAL", "P25" -> DIG;
				case "USB", "SSB" -> USB;
				case "LSB" -> LSB;
				case "CW" -> CW;
				case "AM" -> AM;
				default -> null;
			};
		}
	}

	/**
	 * One saved frequency.
	 *
	 * @param name  What it is ("CLUB RPT", "NET 40M").
	 * @param rxMhz Receive (listen) frequency, MHz.
	 * @param txMhz Transmit frequency, MHz: the same for simplex, the repeater input for a repeater, 0 for receive only.
	 * @param mode  The mode.
	 * @param tone  CTCSS tone, Hz; 0 for none.
	 * @param notes Anything else.
	 */
	public record Entry(String name, double rxMhz, double txMhz, Mode mode, float tone, String notes) {

		public Entry {
			name = cut(clean(name), NAME_MAX);
			rxMhz = Double.isFinite(rxMhz) && rxMhz > 0.0D ? Math.min(rxMhz, 3000.0D) : 0.0D;
			txMhz = Double.isFinite(txMhz) && txMhz > 0.0D ? Math.min(txMhz, 3000.0D) : 0.0D;
			mode = mode == null ? Mode.FM : mode;
			tone = Float.isFinite(tone) && tone > 0.0F ? Math.min(tone, 300.0F) : 0.0F;
			notes = cut(clean(notes), NOTES_MAX);
		}

		/** A simplex entry. */
		public static Entry simplex(String name, double mhz, Mode mode) {
			return new Entry(name, mhz, mhz, mode, 0.0F, "");
		}

		public boolean isReceiveOnly() {
			return txMhz <= 0.0D;
		}

		public boolean isSplit() {
			return !isReceiveOnly() && Math.abs(txMhz - rxMhz) > 0.000_001D;
		}

		/** The repeater offset in MHz (tx minus rx), 0 for simplex or receive only. */
		public double offsetMhz() {
			return isSplit() ? txMhz - rxMhz : 0.0D;
		}

		/**
		 * This entry as a commercial radio channel (its name cut to the display's 8 characters, no encryption), or null
		 * for a mode commercial radios do not have (SSB, CW, AM).
		 */
		@Nullable
		public CommercialChannel toChannel() {
			if(!mode.commercial() || rxMhz <= 0.0D)
				return null;
			return new CommercialChannel(name, (float)(rxMhz * 1e6), (float)(txMhz * 1e6), mode == Mode.DIG, mode == Mode.DIG ? 0.0F : tone, 0, true).sanitized();
		}

		/** A radio's channel as an entry (the key is left behind: a frequency list holds no keys). Null for a blank. */
		@Nullable
		public static Entry of(CommercialChannel channel) {
			if(channel.isBlank())
				return null;
			return new Entry(channel.name(), channel.rxHz() / 1e6, channel.isReceiveOnly() ? 0.0D : channel.txHz() / 1e6,
					channel.digital() ? Mode.DIG : Mode.FM, channel.digital() ? 0.0F : channel.ctcssTone(),
					channel.encrypted() ? "encrypted" : "");
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("name", name);
			tag.putDouble("rx", rxMhz);
			tag.putDouble("tx", txMhz);
			tag.putString("mode", mode.name());
			tag.putFloat("tone", tone);
			tag.putString("notes", notes);
			return tag;
		}

		public static Entry fromTag(CompoundTag tag) {
			Mode mode = Mode.parse(tag.getString("mode"));
			return new Entry(tag.getString("name"), tag.getDouble("rx"), tag.getDouble("tx"), mode == null ? Mode.FM : mode,
					tag.getFloat("tone"), tag.getString("notes"));
		}
	}

	private final List<Entry> entries = new ArrayList<>();

	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public int size() {
		return entries.size();
	}

	public Entry get(int index) {
		return entries.get(index);
	}

	/** Adds at the end; false if the list is full. */
	public boolean add(Entry entry) {
		if(entries.size() >= MAX_ENTRIES)
			return false;
		entries.add(entry);
		return true;
	}

	public void set(int index, Entry entry) {
		if(index >= 0 && index < entries.size())
			entries.set(index, entry);
	}

	public void remove(int index) {
		if(index >= 0 && index < entries.size())
			entries.remove(index);
	}

	/** Moves entry {@code index} by {@code by} places (clamped); its new index. */
	public int move(int index, int by) {
		if(index < 0 || index >= entries.size())
			return index;
		int to = Math.max(0, Math.min(entries.size() - 1, index + by));
		if(to != index)
			entries.add(to, entries.remove(index));
		return to;
	}

	/** Sorts by receive frequency, lowest first. */
	public void sortByFrequency() {
		entries.sort(java.util.Comparator.comparingDouble(Entry::rxMhz));
	}

	public CompoundTag toTag() {
		ListTag list = new ListTag();
		for(Entry entry : entries)
			list.add(entry.toTag());
		CompoundTag tag = new CompoundTag();
		tag.put("channels", list);
		return tag;
	}

	public static FrequencyList fromTag(CompoundTag tag) {
		FrequencyList out = new FrequencyList();
		ListTag list = tag.getList("channels", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++)
			out.add(Entry.fromTag(list.getCompound(i)));
		return out;
	}

	/**
	 * A frequency typed in MHz ("146.52", "7.060"), or -1 if it is not one. Empty is 0 (none).
	 */
	public static double parseMhz(String text) {
		String t = text == null ? "" : text.strip();
		if(t.isEmpty())
			return 0.0D;
		try {
			double mhz = Double.parseDouble(t);
			return Double.isFinite(mhz) && mhz > 0.0D && mhz <= 3000.0D ? mhz : -1.0D;
		}
		catch(NumberFormatException e) {
			return -1.0D;
		}
	}

	/** "146.5200": a frequency as the lists show it. */
	public static String mhz(double mhz) {
		return String.format(Locale.ROOT, "%.4f", mhz);
	}

	private static String clean(String text) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c >= 32 && c < 127)
				out.append(c);
		}
		return out.toString().strip();
	}

	private static String cut(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
	}
}
