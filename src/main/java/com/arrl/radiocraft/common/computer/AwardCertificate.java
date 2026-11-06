package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.data.PixelFont;

import java.util.List;
import java.util.Locale;

/**
 * An award certificate as the Awards program prints it (computer batch 3C): a 128 x 128 map picture in the radio
 * computer's DOS look, a blue screen with a double frame, a grey title bar, "CERTIFICATE", the callsign large, the award
 * and its level, the date, and a gold seal. It prints through the scanner like any picture (a printout or a photo).
 * Pure (no game classes), so it is unit tested.
 */
public final class AwardCertificate {

	public static final int SIZE = 128;

	private AwardCertificate() {}

	/**
	 * The certificate for {@code call}: {@code award} (its name, upper case fits best), {@code level} (the level reached,
	 * "25 BIOMES"), {@code date} ("2026-11-06") and an optional {@code detail} line.
	 */
	public static byte[] render(String award, String level, String call, String date, String detail) {
		PixelCanvas c = new PixelCanvas(SIZE, SIZE, PixelCanvas.BLUE);
		// The DOS window: title bar, double frame, key bar.
		c.fill(0, 0, SIZE, 9, PixelCanvas.LIGHT_GREY);
		c.centred(0, SIZE, 2, "RC-DOS AWARDS", 1, PixelCanvas.BLACK);
		c.rect(2, 11, SIZE - 4, SIZE - 22, 1, PixelCanvas.CYAN);
		c.rect(4, 13, SIZE - 8, SIZE - 26, 1, PixelCanvas.CYAN);
		c.fill(0, SIZE - 9, SIZE, 9, PixelCanvas.LIGHT_GREY);
		c.centred(0, SIZE, SIZE - 7, "73 DE RADIOCRAFT", 1, PixelCanvas.BLACK);

		int y = 17;
		c.centred(0, SIZE, y, "CERTIFICATE", 2, PixelCanvas.YELLOW);
		y += 13;
		c.centred(0, SIZE, y, "THIS CERTIFIES THAT", 1, PixelCanvas.WHITE);
		y += 8;
		String callText = clean(call).isEmpty() ? "-" : clean(call);
		int scale = PixelCanvas.scaleToFit(callText, SIZE - 16, 3);
		c.centred(0, SIZE, y, callText, scale, PixelCanvas.YELLOW);
		y += PixelFont.HEIGHT * scale + 3;
		c.centred(0, SIZE, y, "HAS EARNED", 1, PixelCanvas.WHITE);
		y += 8;
		for(String line : wrap(clean(award), PixelCanvas.charsIn(SIZE - 14, 1), 2)) {
			c.centred(0, SIZE, y, line, 1, PixelCanvas.LIME);
			y += 7;
		}
		String levelText = PixelCanvas.fit(clean(level), SIZE - 14, 1);
		if(!levelText.isEmpty()) {
			c.centred(0, SIZE, y, levelText, 1, PixelCanvas.WHITE);
			y += 7;
		}
		String detailText = PixelCanvas.fit(clean(detail), SIZE - 14, 1);
		if(!detailText.isEmpty())
			c.centred(0, SIZE, y, detailText, 1, PixelCanvas.LIGHT_BLUE);

		// The date bottom left, the seal bottom right.
		c.text(9, SIZE - 21, PixelCanvas.fit(clean(date), 70, 1), 1, PixelCanvas.WHITE);
		c.hline(9, SIZE - 15, 50, PixelCanvas.CYAN);
		int cx = SIZE - 20, cy = SIZE - 21;
		c.disc(cx, cy, 9, PixelCanvas.GOLD);
		c.disc(cx, cy, 6, PixelCanvas.YELLOW);
		c.fill(cx - 1, cy - 4, 3, 9, PixelCanvas.ORANGE); // A star of two bars.
		c.fill(cx - 4, cy - 1, 9, 3, PixelCanvas.ORANGE);
		c.fill(cx - 5, cy + 7, 3, 4, PixelCanvas.RED); // Ribbons.
		c.fill(cx + 3, cy + 7, 3, 4, PixelCanvas.RED);
		return c.pixels();
	}

	/** Upper case, what the font can draw (others become spaces), trimmed. */
	static String clean(String text) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(char ch : text.toUpperCase(Locale.ROOT).toCharArray())
			out.append(PixelFont.has(ch) ? ch : ' ');
		return out.toString().strip();
	}

	/** {@code text} broken at spaces into lines of {@code width} characters, at most {@code lines} of them. */
	static List<String> wrap(String text, int width, int lines) {
		List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for(String word : text.split("\\s+")) {
			if(word.isEmpty())
				continue;
			if(line.length() > 0 && line.length() + 1 + word.length() > width) {
				out.add(line.toString());
				line.setLength(0);
			}
			if(line.length() > 0)
				line.append(' ');
			line.append(word.length() > width ? word.substring(0, width) : word);
		}
		if(line.length() > 0)
			out.add(line.toString());
		return out.size() > lines ? out.subList(0, lines) : out;
	}
}
