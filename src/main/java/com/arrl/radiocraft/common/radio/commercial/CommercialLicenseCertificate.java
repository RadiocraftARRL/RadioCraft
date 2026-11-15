package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.computer.PixelCanvas;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.data.PixelFont;

import java.util.Locale;

/**
 * The picture on a commercial license (2026-11-10): a 128 x 128 map picture of a radio station authorization on cream
 * paper, with the callsign large, the licensee, the frequencies, what it allows and the grant date, and a seal. A copy
 * has a red COPY stamp in the top right corner. Pure (no game classes), so it is unit tested.
 */
public final class CommercialLicenseCertificate {

	public static final int SIZE = 128;

	private CommercialLicenseCertificate() {}

	public static byte[] render(CommercialLicense license) {
		PixelCanvas c = new PixelCanvas(SIZE, SIZE, PixelCanvas.CREAM);
		c.rect(2, 2, SIZE - 4, SIZE - 4, 1, PixelCanvas.NAVY);
		c.rect(4, 4, SIZE - 8, SIZE - 8, 1, PixelCanvas.NAVY);

		int y = 8;
		c.centred(0, SIZE, y, "FEDERAL COMMUNICATIONS", 1, PixelCanvas.NAVY);
		y += 7;
		c.centred(0, SIZE, y, "COMMISSION", 1, PixelCanvas.NAVY);
		y += 8;
		c.centred(0, SIZE, y, "RADIO STATION AUTHORIZATION", 1, PixelCanvas.BLACK);
		y += 7;
		c.hline(8, y, SIZE - 16, PixelCanvas.NAVY);
		y += 4;

		c.centred(0, SIZE, y, "CALL SIGN", 1, PixelCanvas.DARK_GREY);
		y += 7;
		String call = clean(license.callsign());
		int scale = PixelCanvas.scaleToFit(call.isEmpty() ? "-" : call, SIZE - 20, 3);
		c.centred(0, SIZE, y, call.isEmpty() ? "-" : call, scale, PixelCanvas.BLACK);
		y += PixelFont.HEIGHT * scale + 4;

		int left = 9, width = SIZE - 18;
		y = line(c, left, width, y, "LICENSEE " + clean(license.holder()));
		y = line(c, left, width, y, Band.LOW_BAND.equals(license.band()) ? "SERVICE IG LOW BAND" : "SERVICE IG VHF");
		y = line(c, left, width, y, "FREQ  " + CommercialLicenseRules.mhz(license.outputHz()) + " MHZ");
		y = line(c, left, width, y, "INPUT " + (license.inputHz() > 0 ? CommercialLicenseRules.mhz(license.inputHz()) + " MHZ" : "NONE"));
		if(license.telemetryHz() > 0) // A commercial satellite's telemetry beacon (2026-11-12).
			y = line(c, left, width, y, "TLM   " + CommercialLicenseRules.mhz(license.telemetryHz()) + " MHZ");
		y = line(c, left, width, y, "UNITS " + license.subscribers() + "  BASES " + license.bases());
		line(c, left, width, y, "GRANTED " + clean(license.issued()));

		int cx = SIZE - 19, cy = SIZE - 19;
		c.disc(cx, cy, 8, PixelCanvas.NAVY);
		c.disc(cx, cy, 6, PixelCanvas.GOLD);
		c.disc(cx, cy, 3, PixelCanvas.YELLOW);

		if(license.copy()) {
			int w = ("COPY".length() * PixelFont.ADVANCE - 1) * 2 + 6;
			c.rect(8, 106, w, 16, 1, PixelCanvas.RED); // Bottom left, under the last line and clear of the seal.
			c.text(11, 109, "COPY", 2, PixelCanvas.RED);
		}
		return c.pixels();
	}

	private static int line(PixelCanvas c, int left, int width, int y, String text) {
		c.text(left, y, PixelCanvas.fit(text, width, 1), 1, PixelCanvas.BLACK);
		return y + 8;
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
}
