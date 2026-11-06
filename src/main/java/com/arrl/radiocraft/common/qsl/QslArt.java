package com.arrl.radiocraft.common.qsl;

import com.arrl.radiocraft.common.computer.PixelCanvas;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.radio.data.PixelFont;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws a QSL card (computer batch 3C), front and back, as {@link #WIDTH} x {@link #HEIGHT} packed map colour ids
 * from its {@link QslCard} data, the same on every side: the client makes its texture from it (in the hand, in a
 * frame, in the album and the designer), the server its printout and the SSTV tones a faxed card goes out as
 * ({@link #onMap}). Pure (no game classes), so it is unit tested.
 * <p>
 * Front: the callsign large across the top (the sender's own writing if the template left it blank), the picture
 * bottom left or right, the text lines beside it. Back: the heading and "DE call", "TO RADIO", the date, UTC, band, MHz,
 * mode and report in a table or as lines, the notes, the template's back lines and the signature. Whatever was
 * written by hand is in blue ink; blanks are dotted.
 */
public final class QslArt {

	public static final int WIDTH = 128, HEIGHT = 80;
	/** Handwriting. */
	public static final byte INK = PixelCanvas.BLUE;
	private static final byte PRINT = PixelCanvas.BLACK, BLANK_LINE = PixelCanvas.LIGHT_GREY, PAPER = PixelCanvas.CREAM;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm", Locale.ROOT).withZone(ZoneOffset.UTC);

	/** The colours a template can use, by name ({@code screen.radiocraft.computer.qsl.colour.<name>}). */
	public static final Map<String, Byte> COLOURS = colours();

	private QslArt() {}

	private static Map<String, Byte> colours() {
		Map<String, Byte> out = new LinkedHashMap<>();
		out.put("white", PixelCanvas.WHITE);
		out.put("cream", PixelCanvas.CREAM);
		out.put("tan", PixelCanvas.TAN);
		out.put("light_grey", PixelCanvas.LIGHT_GREY);
		out.put("grey", PixelCanvas.GREY);
		out.put("black", PixelCanvas.BLACK);
		out.put("red", PixelCanvas.RED);
		out.put("bright_red", PixelCanvas.BRIGHT_RED);
		out.put("orange", PixelCanvas.ORANGE);
		out.put("yellow", PixelCanvas.YELLOW);
		out.put("gold", PixelCanvas.GOLD);
		out.put("lime", PixelCanvas.LIME);
		out.put("green", PixelCanvas.GREEN);
		out.put("cyan", PixelCanvas.CYAN);
		out.put("light_blue", PixelCanvas.LIGHT_BLUE);
		out.put("blue", PixelCanvas.BLUE);
		out.put("navy", PixelCanvas.NAVY);
		out.put("purple", PixelCanvas.PURPLE);
		out.put("magenta", PixelCanvas.MAGENTA);
		out.put("pink", PixelCanvas.PINK);
		out.put("brown", PixelCanvas.BROWN);
		return java.util.Collections.unmodifiableMap(out);
	}

	/** The colour called {@code name}, or black. */
	public static byte colour(String name) {
		return COLOURS.getOrDefault(name, PixelCanvas.BLACK);
	}

	/** The name of {@code colour}, or "". */
	public static String colourName(byte colour) {
		for(Map.Entry<String, Byte> entry : COLOURS.entrySet())
			if(entry.getValue() == colour)
				return entry.getKey();
		return "";
	}

	public static boolean isColour(byte colour) {
		return COLOURS.containsValue(colour);
	}

	// -------------------- THE FRONT --------------------

	public static byte[] front(QslCard card) {
		QslCard.Design d = card.design();
		PixelCanvas c = new PixelCanvas(WIDTH, HEIGHT, d.background());
		switch(d.borderStyle()) {
			case QslCard.BORDER_THIN -> c.rect(2, 2, WIDTH - 4, HEIGHT - 4, 1, d.border());
			case QslCard.BORDER_DOUBLE -> {
				c.rect(2, 2, WIDTH - 4, HEIGHT - 4, 1, d.border());
				c.rect(4, 4, WIDTH - 8, HEIGHT - 8, 1, d.border());
			}
			case QslCard.BORDER_THICK -> c.rect(0, 0, WIDTH, HEIGHT, 3, d.border());
			default -> {}
		}
		String call = d.call().isEmpty() ? card.fill().from() : d.call();
		byte callColour = d.call().isEmpty() ? INK : d.callColour();
		if(!call.isEmpty()) {
			int scale = PixelCanvas.scaleToFit(call, WIDTH - 16, 4);
			c.centred(0, WIDTH, 8 + (4 - scale) * 2, call, scale, callColour);
		}
		else
			c.dots(32, 26, 64, BLANK_LINE); // Room to write a callsign in.
		int textLeft = 8, textRight = WIDTH - 8;
		if(d.hasPicture()) {
			int px = d.picturePlace() == QslCard.PICTURE_RIGHT ? WIDTH - 8 - QslCard.PICTURE_W : 8;
			c.blit(d.pictureView(), QslCard.PICTURE_W, QslCard.PICTURE_H, px, 36);
			c.rect(px - 1, 35, QslCard.PICTURE_W + 2, QslCard.PICTURE_H + 2, 1, d.border());
			if(d.picturePlace() == QslCard.PICTURE_RIGHT)
				textRight = px - 3;
			else
				textLeft = px + QslCard.PICTURE_W + 3;
		}
		int y = 37;
		for(String line : d.lines()) {
			c.centred(textLeft, textRight, y, PixelCanvas.fit(line, textRight - textLeft, 1), 1, d.textColour());
			y += 8;
		}
		return c.pixels();
	}

	// -------------------- THE BACK --------------------

	public static byte[] back(QslCard card) {
		QslCard.Design d = card.design();
		QslCard.Fill f = card.fill();
		PixelCanvas c = new PixelCanvas(WIDTH, HEIGHT, PAPER);
		c.rect(1, 1, WIDTH - 2, HEIGHT - 2, 1, d.border());
		c.text(5, 4, PixelCanvas.fit(d.backHeader(), 76, 1), 1, PRINT);
		String from = card.from();
		String de = "DE " + (from.isEmpty() ? "" : from);
		int deX = WIDTH - 5 - PixelFont.width(de, 1);
		c.text(deX, 4, "DE", 1, PRINT);
		if(!from.isEmpty())
			c.text(deX + 12, 4, from, 1, d.call().isEmpty() ? INK : PRINT);
		c.text(5, 13, "TO RADIO", 1, PRINT);
		field(c, 40, 13, 82, f.to());
		String date = f.utc() == 0L ? "" : DATE.format(Instant.ofEpochMilli(f.utc()));
		String utc = f.utc() == 0L ? "" : TIME.format(Instant.ofEpochMilli(f.utc()));
		if(d.backLayout() == QslCard.BACK_LINES_LAYOUT) {
			labelled(c, 5, 22, "DATE", date);
			labelled(c, 66, 22, "UTC", utc);
			labelled(c, 5, 30, "BAND", f.band());
			labelled(c, 66, 30, "MHZ", f.frequency());
			labelled(c, 5, 38, "MODE", f.mode());
			labelled(c, 66, 38, "RST", f.report());
		}
		else {
			int x0 = 5, w = 118, cell = w / 3;
			c.rect(x0, 21, w, 26, 1, PRINT);
			c.hline(x0, 33, w, PRINT);
			c.fill(x0 + cell, 21, 1, 26, PRINT);
			c.fill(x0 + 2 * cell, 21, 1, 26, PRINT);
			String[] labels = { "DATE", "UTC", "BAND", "MHZ", "MODE", "RST" };
			String[] values = { date, utc, f.band(), f.frequency(), f.mode(), f.report() };
			for(int i = 0; i < 6; i++) {
				int cx = x0 + (i % 3) * cell, cy = i < 3 ? 22 : 34;
				c.text(cx + 2, cy, labels[i], 1, PixelCanvas.GREY);
				c.text(cx + 2, cy + 6, PixelCanvas.fit(values[i], cell - 3, 1), 1, INK);
			}
		}
		c.text(5, 50, "NOTES", 1, PRINT);
		List<String> notes = wrap(f.notes(), PixelCanvas.charsIn(WIDTH - 32, 1), 2);
		if(notes.isEmpty()) {
			c.dots(27, 55, WIDTH - 32, BLANK_LINE);
			c.dots(27, 62, WIDTH - 32, BLANK_LINE);
		}
		for(int i = 0; i < notes.size(); i++)
			c.text(27, 50 + i * 7, notes.get(i), 1, INK);
		List<String> lines = d.backLines();
		if(!lines.isEmpty())
			c.text(5, 66, PixelCanvas.fit(lines.get(0), WIDTH - 10, 1), 1, PRINT);
		if(lines.size() > 1)
			c.text(5, 73, PixelCanvas.fit(lines.get(1), 60, 1), 1, PRINT);
		if(f.signed() && !f.signer().isEmpty()) {
			String sig = PixelCanvas.fit(f.signer().toUpperCase(Locale.ROOT), 56, 1);
			c.text(WIDTH - 5 - PixelFont.width(sig, 1), 73, sig, 1, INK);
			c.hline(WIDTH - 62, 79 - 1, 57, INK);
		}
		else
			c.dots(WIDTH - 62, 78, 57, BLANK_LINE);
		if(card.copy())
			c.text(WIDTH - 5 - PixelFont.width("COPY", 1), 66, "COPY", 1, PixelCanvas.RED);
		return c.pixels();
	}

	private static void field(PixelCanvas c, int x, int y, int w, String value) {
		if(value.isEmpty())
			c.dots(x, y + 5, w, BLANK_LINE);
		else
			c.text(x, y, PixelCanvas.fit(value, w, 1), 1, INK);
	}

	private static void labelled(PixelCanvas c, int x, int y, String label, String value) {
		c.text(x, y, label, 1, PRINT);
		field(c, x + 20, y, 36, value);
	}

	static List<String> wrap(String text, int width, int lines) {
		List<String> out = new ArrayList<>();
		String rest = text == null ? "" : text.strip();
		while(!rest.isEmpty() && out.size() < lines) {
			if(rest.length() <= width) {
				out.add(rest);
				break;
			}
			int cut = rest.lastIndexOf(' ', width);
			if(cut <= 0)
				cut = width;
			out.add(rest.substring(0, cut).strip());
			rest = rest.substring(cut).strip();
		}
		return out;
	}

	// -------------------- AS A PICTURE --------------------

	/** {@code side} ({@link #WIDTH} x {@link #HEIGHT}) on a 128 x 128 map picture, grey round it: a printout or SSTV of the card. */
	public static byte[] onMap(byte[] side) {
		PixelCanvas c = new PixelCanvas(128, 128, PixelCanvas.LIGHT_GREY);
		c.blit(side, WIDTH, HEIGHT, 0, (128 - HEIGHT) / 2);
		return c.pixels();
	}

	/**
	 * A 128 x 128 picture ({@code .IMG}) shrunk to a card's picture ({@link QslCard#PICTURE_W} x {@link QslCard#PICTURE_H}):
	 * the middle band of the right shape, each pixel the average of the pixels it covers, put back in {@code palette}'s
	 * nearest colour. Null if {@code image} is not 128 x 128.
	 */
	public static byte[] picture(byte[] image, PhotoPalette palette) {
		if(image == null || image.length != 128 * 128)
			return null;
		int w = QslCard.PICTURE_W, h = QslCard.PICTURE_H;
		double sx = 128.0D / w, sy = sx; // Same scale both ways: a band of 128 x 85 from the middle.
		double top = (128 - h * sy) / 2.0D;
		byte[] out = new byte[w * h];
		for(int y = 0; y < h; y++)
			for(int x = 0; x < w; x++) {
				int x0 = (int)(x * sx), x1 = Math.max(x0 + 1, (int)((x + 1) * sx));
				int y0 = (int)(top + y * sy), y1 = Math.max(y0 + 1, (int)(top + (y + 1) * sy));
				long r = 0, g = 0, b = 0, n = 0;
				for(int j = y0; j < Math.min(128, y1); j++)
					for(int i = x0; i < Math.min(128, x1); i++) {
						int rgb = palette.rgbOf(image[j * 128 + i]);
						if(rgb < 0)
							continue;
						r += (rgb >> 16) & 0xFF;
						g += (rgb >> 8) & 0xFF;
						b += rgb & 0xFF;
						n++;
					}
				out[y * w + x] = n == 0 ? palette.darkest() : palette.id(palette.nearest((int)(r / n), (int)(g / n), (int)(b / n)));
			}
		return out;
	}
}
