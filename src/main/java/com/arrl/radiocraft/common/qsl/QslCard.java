package com.arrl.radiocraft.common.qsl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A QSL card (computer batch 3C): the card a ham sends to confirm a contact. Its {@link Design} comes from a template
 * made in the QSL Designer program (the front: the callsign large or left blank, colours, a border, a small picture and
 * text lines; the back: a header, a layout of fields to fill in, a line or two of text). The printer prints a run of
 * identical blank cards (they stack). A player fills one in by hand ({@link Fill}: to, band, frequency, mode, report,
 * notes; the date and time are put in by the game) and signs it, which locks it.
 * <p>
 * <b>Kept small</b>, as it rides on an item: the template's data, not pixels (the card is drawn from it on each side,
 * {@link QslArt}), and the one picture downscaled to {@link #PICTURE_W} x {@link #PICTURE_H} map colour ids
 * (1,536 bytes) embedded in it, so the card shows the same to every player with nothing else to sync. A whole card is
 * under 2.5 KB ({@link #MAX_BYTES}, checked by a unit test). Pure (NBT only), so it is unit tested.
 */
public record QslCard(Design design, Fill fill, boolean copy) {

	public static final int PICTURE_W = 48, PICTURE_H = 32;
	public static final int CALL_MAX = 10, LINE_MAX = 30, LINES = 4, BACK_LINES = 2, HEADER_MAX = 30;
	public static final int TO_MAX = 12, BAND_MAX = 6, FREQ_MAX = 10, MODE_MAX = 8, REPORT_MAX = 7, NOTES_MAX = 60, SIGNER_MAX = 16;
	/** The most a card's NBT may be (a unit test checks the largest card stays under). */
	public static final int MAX_BYTES = 2560;

	/** Border styles. */
	public static final int BORDER_NONE = 0, BORDER_THIN = 1, BORDER_DOUBLE = 2, BORDER_THICK = 3;
	/** Picture places on the front. */
	public static final int PICTURE_LEFT = 0, PICTURE_RIGHT = 1;
	/** Back layouts: a table of boxes, or a line per field. */
	public static final int BACK_TABLE = 0, BACK_LINES_LAYOUT = 1;

	public static final QslCard BLANK = new QslCard(Design.DEFAULT, Fill.EMPTY, false);

	public QslCard {
		design = design == null ? Design.DEFAULT : design;
		fill = fill == null ? Fill.EMPTY : fill;
	}

	/**
	 * A template: everything printed on the card before it is filled in.
	 *
	 * @param call         the callsign printed large on the front, "" for a card anyone can write their call on
	 * @param lines        up to {@link #LINES} lines of text on the front (name, place, rig...)
	 * @param background   the front's colour (a packed map colour id, {@link QslArt#COLOURS})
	 * @param border       the border's colour
	 * @param borderStyle  {@link #BORDER_NONE} to {@link #BORDER_THICK}
	 * @param callColour   the callsign's colour
	 * @param textColour   the text lines' colour
	 * @param picture      {@link #PICTURE_W} x {@link #PICTURE_H} map colour ids, or empty for none
	 * @param picturePlace {@link #PICTURE_LEFT} or {@link #PICTURE_RIGHT}
	 * @param backHeader   the back's heading ("CONFIRMING OUR QSO")
	 * @param backLayout   {@link #BACK_TABLE} or {@link #BACK_LINES_LAYOUT}
	 * @param backLines    up to {@link #BACK_LINES} lines printed on the back ("TNX FER QSO", "PSE QSL")
	 */
	public record Design(String call, List<String> lines, byte background, byte border, int borderStyle, byte callColour, byte textColour, byte[] picture,
			int picturePlace, String backHeader, int backLayout, List<String> backLines) {

		public static final Design DEFAULT = new Design("", List.of(), QslArt.colour("cream"), QslArt.colour("navy"), BORDER_DOUBLE,
				QslArt.colour("red"), QslArt.colour("black"), new byte[0], PICTURE_LEFT, "CONFIRMING OUR QSO", BACK_TABLE, List.of("TNX FER QSO 73"));

		public Design {
			call = callsign(call, CALL_MAX);
			lines = cleanLines(lines, LINES, LINE_MAX);
			borderStyle = Math.max(BORDER_NONE, Math.min(BORDER_THICK, borderStyle));
			picture = picture == null || picture.length != PICTURE_W * PICTURE_H ? new byte[0] : picture.clone();
			picturePlace = picturePlace == PICTURE_RIGHT ? PICTURE_RIGHT : PICTURE_LEFT;
			backHeader = text(backHeader, HEADER_MAX);
			backLayout = backLayout == BACK_LINES_LAYOUT ? BACK_LINES_LAYOUT : BACK_TABLE;
			backLines = cleanLines(backLines, BACK_LINES, LINE_MAX);
		}

		public boolean hasPicture() {
			return picture.length > 0;
		}

		/** The picture (a copy). */
		@Override
		public byte[] picture() {
			return picture.clone();
		}

		/** The picture without copying, for drawing (do not change it). */
		byte[] pictureView() {
			return picture;
		}

		public Design withCall(String call) {
			return new Design(call, lines, background, border, borderStyle, callColour, textColour, picture, picturePlace, backHeader, backLayout, backLines);
		}

		public Design withPicture(byte[] picture) {
			return new Design(call, lines, background, border, borderStyle, callColour, textColour, picture, picturePlace, backHeader, backLayout, backLines);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Design d && call.equals(d.call) && lines.equals(d.lines) && background == d.background && border == d.border
					&& borderStyle == d.borderStyle && callColour == d.callColour && textColour == d.textColour && Arrays.equals(picture, d.picture)
					&& picturePlace == d.picturePlace && backHeader.equals(d.backHeader) && backLayout == d.backLayout && backLines.equals(d.backLines);
		}

		@Override
		public int hashCode() {
			int h = java.util.Objects.hash(call, lines, background, border, borderStyle, callColour, textColour, picturePlace, backHeader, backLayout, backLines);
			return 31 * h + Arrays.hashCode(picture);
		}

		@Override
		public String toString() {
			return "Design[" + call + ", " + lines + ", picture " + picture.length + "]";
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("call", call);
			tag.put("lines", list(lines));
			tag.putByte("bg", background);
			tag.putByte("border", border);
			tag.putByte("style", (byte)borderStyle);
			tag.putByte("callColour", callColour);
			tag.putByte("textColour", textColour);
			if(picture.length > 0)
				tag.putByteArray("picture", picture);
			tag.putByte("place", (byte)picturePlace);
			tag.putString("header", backHeader);
			tag.putByte("layout", (byte)backLayout);
			tag.put("backLines", list(backLines));
			return tag;
		}

		public static Design fromTag(CompoundTag tag) {
			if(tag.isEmpty())
				return DEFAULT;
			return new Design(tag.getString("call"), strings(tag.getList("lines", Tag.TAG_STRING)), colourOr(tag, "bg", DEFAULT.background),
					colourOr(tag, "border", DEFAULT.border), tag.getByte("style"), colourOr(tag, "callColour", DEFAULT.callColour),
					colourOr(tag, "textColour", DEFAULT.textColour), tag.getByteArray("picture"), tag.getByte("place"), tag.getString("header"),
					tag.getByte("layout"), strings(tag.getList("backLines", Tag.TAG_STRING)));
		}
	}

	/**
	 * What is written on the back by hand.
	 *
	 * @param from    the sender's callsign when the design has none printed, else ""
	 * @param to      the station the card is for
	 * @param utc     when it was filled in (real time, ms), 0 while blank
	 * @param game    the game day and time ("D12 1430"), "" while blank
	 * @param signer  the player who signed, "" until signed
	 * @param signed  signed cards are locked
	 */
	public record Fill(String from, String to, String band, String frequency, String mode, String report, String notes, long utc, String game, String signer,
			boolean signed) {

		public static final Fill EMPTY = new Fill("", "", "", "", "", "", "", 0L, "", "", false);

		public Fill {
			from = callsign(from, CALL_MAX);
			to = callsign(to, TO_MAX);
			band = text(band, BAND_MAX).toUpperCase(Locale.ROOT);
			frequency = text(frequency, FREQ_MAX);
			mode = text(mode, MODE_MAX).toUpperCase(Locale.ROOT);
			report = text(report, REPORT_MAX);
			notes = text(notes, NOTES_MAX);
			game = text(game, 16);
			signer = text(signer, SIGNER_MAX);
			utc = Math.max(0L, utc);
		}

		public boolean isBlank() {
			return from.isEmpty() && to.isEmpty() && band.isEmpty() && frequency.isEmpty() && mode.isEmpty() && report.isEmpty() && notes.isEmpty() && !signed;
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			putIf(tag, "from", from);
			putIf(tag, "to", to);
			putIf(tag, "band", band);
			putIf(tag, "freq", frequency);
			putIf(tag, "mode", mode);
			putIf(tag, "rst", report);
			putIf(tag, "notes", notes);
			if(utc != 0L)
				tag.putLong("utc", utc);
			putIf(tag, "game", game);
			putIf(tag, "signer", signer);
			if(signed)
				tag.putBoolean("signed", true);
			return tag;
		}

		public static Fill fromTag(CompoundTag tag) {
			return new Fill(tag.getString("from"), tag.getString("to"), tag.getString("band"), tag.getString("freq"), tag.getString("mode"),
					tag.getString("rst"), tag.getString("notes"), tag.getLong("utc"), tag.getString("game"), tag.getString("signer"), tag.getBoolean("signed"));
		}
	}

	// -------------------- RULES --------------------

	public boolean signed() {
		return fill.signed();
	}

	/** True for a card straight off the printer: nothing written on it. */
	public boolean blank() {
		return fill.isBlank();
	}

	/** Whose card it is: the printed callsign, else the one written in. */
	public String from() {
		return design.call().isEmpty() ? fill.from() : design.call();
	}

	/**
	 * The card filled in by hand with {@code written} (its {@code from}, {@code to}, band, frequency, mode, report and
	 * notes; nothing else of it is read) at {@code utc} and game time {@code game}, and signed by {@code signer} if
	 * {@code sign}. Null when that is not allowed ({@link #problem}).
	 */
	@Nullable
	public QslCard filled(Fill written, long utc, String game, String signer, boolean sign) {
		if(problem(written, sign) != null)
			return null;
		Fill made = new Fill(design.call().isEmpty() ? written.from() : "", written.to(), written.band(), written.frequency(), written.mode(),
				written.report(), written.notes(), utc, game, sign ? signer : "", sign);
		return new QslCard(design, made, copy);
	}

	/**
	 * Why {@code written} cannot go on this card: "signed" (a signed card is locked), "need_to" (signing needs the
	 * station it is for), "need_from" (a card with no printed callsign needs the sender's written in to be signed);
	 * null if it can.
	 */
	@Nullable
	public String problem(Fill written, boolean sign) {
		if(signed())
			return "signed";
		if(sign && written.to().isEmpty())
			return "need_to";
		if(sign && design.call().isEmpty() && written.from().isEmpty())
			return "need_from";
		return null;
	}

	/** This card marked as a copy (a fax print or a print of a received card file). */
	public QslCard asCopy() {
		return new QslCard(design, fill, true);
	}

	/** A blank card of this card's design (what the printer prints from a template file). */
	public QslCard blankOfDesign() {
		return new QslCard(design, Fill.EMPTY, false);
	}

	// -------------------- NBT --------------------

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.put("design", design.toTag());
		CompoundTag fillTag = fill.toTag();
		if(!fillTag.isEmpty())
			tag.put("fill", fillTag);
		if(copy)
			tag.putBoolean("copy", true);
		return tag;
	}

	public static QslCard fromTag(CompoundTag tag) {
		return new QslCard(Design.fromTag(tag.getCompound("design")), Fill.fromTag(tag.getCompound("fill")), tag.getBoolean("copy"));
	}

	// -------------------- HELPERS --------------------

	private static byte colourOr(CompoundTag tag, String key, byte fallback) {
		return tag.contains(key) && QslArt.isColour(tag.getByte(key)) ? tag.getByte(key) : fallback;
	}

	private static void putIf(CompoundTag tag, String key, String value) {
		if(!value.isEmpty())
			tag.putString(key, value);
	}

	private static ListTag list(List<String> lines) {
		ListTag out = new ListTag();
		for(String line : lines)
			out.add(StringTag.valueOf(line));
		return out;
	}

	private static List<String> strings(ListTag list) {
		List<String> out = new ArrayList<>();
		for(int i = 0; i < list.size(); i++)
			out.add(list.getString(i));
		return out;
	}

	/** Printable ASCII, trimmed, at most {@code max}. */
	static String text(String text, int max) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < text.length() && out.length() < max; i++) {
			char c = text.charAt(i);
			if(c >= ' ' && c <= '~')
				out.append(c);
		}
		return out.toString().strip();
	}

	static String callsign(String text, int max) {
		String t = text(text, 64).toUpperCase(Locale.ROOT).replace(" ", "");
		return t.length() > max ? t.substring(0, max) : t;
	}

	/** At most {@code count} lines of at most {@code max}, trailing empty lines dropped. */
	static List<String> cleanLines(List<String> lines, int count, int max) {
		List<String> out = new ArrayList<>();
		if(lines != null)
			for(String line : lines) {
				if(out.size() >= count)
					break;
				out.add(text(line, max));
			}
		while(!out.isEmpty() && out.getLast().isEmpty())
			out.removeLast();
		return List.copyOf(out);
	}
}
