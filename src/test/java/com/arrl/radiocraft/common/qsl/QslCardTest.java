package com.arrl.radiocraft.common.qsl;

import com.arrl.radiocraft.common.computer.PixelCanvas;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QSL cards (computer batch 3C): the data, its size cap, the signing rules, and the drawing. */
class QslCardTest {

	private static byte[] picture(byte colour) {
		byte[] p = new byte[QslCard.PICTURE_W * QslCard.PICTURE_H];
		java.util.Arrays.fill(p, colour);
		return p;
	}

	private static QslCard.Design design(String call) {
		return new QslCard.Design(call, List.of("Ana Smith", "Lake Town"), QslArt.colour("cream"), QslArt.colour("navy"), QslCard.BORDER_DOUBLE,
				QslArt.colour("red"), QslArt.colour("black"), picture(PixelCanvas.GREEN), QslCard.PICTURE_LEFT, "CONFIRMING OUR QSO", QslCard.BACK_TABLE,
				List.of("TNX FER QSO", "73"));
	}

	private static int nbtBytes(CompoundTag tag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.write(tag, new DataOutputStream(bytes));
		return bytes.size();
	}

	@Test
	void roundTripsAndIdenticalCardsAreEqual() {
		QslCard card = new QslCard(design("W1BTR"), QslCard.Fill.EMPTY, false);
		QslCard back = QslCard.fromTag(card.toTag());
		assertEquals(card, back, "a blank card survives its tag");
		assertEquals(card.hashCode(), back.hashCode());
		assertEquals(new QslCard(design("W1BTR"), QslCard.Fill.EMPTY, false), card, "two prints of one template are equal, so they stack");
		assertFalse(card.equals(new QslCard(design("W1BTR").withPicture(picture(PixelCanvas.RED)), QslCard.Fill.EMPTY, false)), "another picture is another card");
		assertEquals(QslCard.BLANK, QslCard.fromTag(new CompoundTag()), "no data is the plain blank");
	}

	@Test
	void theLargestCardStaysSmall() throws IOException {
		String x = "W".repeat(64);
		QslCard.Design big = new QslCard.Design(x, Collections.nCopies(9, x), (byte)0, (byte)0, 9, (byte)0, (byte)0, picture((byte)34), 5, x, 5,
				Collections.nCopies(9, x));
		QslCard.Fill fill = new QslCard.Fill(x, x, x, x, x, x, x.repeat(4), Long.MAX_VALUE, x, x, true);
		QslCard card = new QslCard(big, fill, true);
		assertEquals(QslCard.LINES, card.design().lines().size(), "lines are capped");
		assertEquals(QslCard.CALL_MAX, card.design().call().length());
		assertEquals(QslCard.NOTES_MAX, card.fill().notes().length());
		int size = nbtBytes(card.toTag());
		assertTrue(size < QslCard.MAX_BYTES, "a card is " + size + " bytes");
		assertEquals(0, new QslCard.Design("", List.of(), (byte)0, (byte)0, 0, (byte)0, (byte)0, new byte[100], 0, "", 0, List.of()).picture().length,
				"a picture of the wrong size is dropped");
	}

	@Test
	void signingLocksTheCard() {
		QslCard blank = new QslCard(design("W1BTR"), QslCard.Fill.EMPTY, false);
		QslCard.Fill written = new QslCard.Fill("", "k2xyz", "20m", "14.225", "usb", "59", "Thanks for the contact", 0L, "", "", false);
		assertEquals("need_to", blank.problem(new QslCard.Fill("", "", "", "", "", "", "", 0L, "", "", false), true));
		QslCard draft = blank.filled(written, 1000L, "D3 1200", "Ana", false);
		assertNotNull(draft);
		assertFalse(draft.signed());
		assertEquals("", draft.fill().signer(), "not signed: no signer");
		assertEquals("K2XYZ", draft.fill().to());
		assertEquals("USB", draft.fill().mode());
		QslCard signed = draft.filled(written, 2000L, "D3 1201", "Ana", true);
		assertNotNull(signed);
		assertTrue(signed.signed());
		assertEquals("Ana", signed.fill().signer());
		assertEquals(2000L, signed.fill().utc(), "the time is the game's, not the player's");
		assertEquals("signed", signed.problem(written, false));
		assertNull(signed.filled(written, 3000L, "", "Bob", true), "a signed card cannot be written on again");
	}

	@Test
	void aCardForAnotherPlayerNeedsTheirCallWrittenIn() {
		QslCard blank = new QslCard(design(""), QslCard.Fill.EMPTY, false); // Made for someone with no computer.
		QslCard.Fill noFrom = new QslCard.Fill("", "K2XYZ", "", "", "", "", "", 0L, "", "", false);
		assertEquals("need_from", blank.problem(noFrom, true));
		QslCard.Fill withFrom = new QslCard.Fill("n3zz", "K2XYZ", "", "", "", "", "", 0L, "", "", false);
		QslCard signed = blank.filled(withFrom, 1L, "", "Zed", true);
		assertNotNull(signed);
		assertEquals("N3ZZ", signed.from());
		QslCard printed = new QslCard(design("W1BTR"), QslCard.Fill.EMPTY, false).filled(withFrom, 1L, "", "Ana", true);
		assertNotNull(printed);
		assertEquals("W1BTR", printed.from(), "a printed callsign wins over a written one");
		assertEquals("", printed.fill().from());
	}

	@Test
	void drawsBothSides() {
		QslCard card = new QslCard(design("W1BTR"), new QslCard.Fill("", "K2XYZ", "20M", "14.225", "USB", "59", "TNX", 1000L, "", "Ana", true), false);
		byte[] front = QslArt.front(card);
		byte[] back = QslArt.back(card);
		assertEquals(QslArt.WIDTH * QslArt.HEIGHT, front.length);
		assertEquals(QslArt.WIDTH * QslArt.HEIGHT, back.length);
		assertEquals(PixelCanvas.GREEN, front[40 * QslArt.WIDTH + 20], "the picture is drawn bottom left");
		int ink = 0;
		for(byte b : back)
			if(b == QslArt.INK)
				ink++;
		assertTrue(ink > 50, "what was written by hand is in ink");
		assertEquals(128 * 128, QslArt.onMap(front).length);
		assertEquals(front[0], QslArt.onMap(front)[24 * 128], "the card sits in the middle of the map picture");
	}

	@Test
	void picturesShrinkIntoThePalette() {
		PhotoPalette palette = new PhotoPalette(new int[] { 0x000000, 0xFFFFFF, 0xFF0000 }, new byte[] { 1, 2, 3 });
		byte[] image = new byte[128 * 128];
		for(int y = 0; y < 128; y++)
			for(int x = 0; x < 128; x++)
				image[y * 128 + x] = (byte)(x < 64 ? 2 : 3);
		byte[] small = QslArt.picture(image, palette);
		assertNotNull(small);
		assertEquals(QslCard.PICTURE_W * QslCard.PICTURE_H, small.length);
		assertEquals(2, small[0]);
		assertEquals(3, small[QslCard.PICTURE_W - 1]);
		assertNull(QslArt.picture(new byte[10], palette));
	}

	@Test
	void theColoursAreTheMapsOwn() {
		for(Map.Entry<String, Byte> colour : QslArt.COLOURS.entrySet())
			assertTrue(MapPalettes.vanilla().contains(colour.getValue()), colour.getKey() + " is no map colour");
		assertEquals(MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.WHITE);
		assertEquals(MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.BLACK);
		assertEquals(MapColor.COLOR_BLUE.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.BLUE);
		assertEquals(MapColor.COLOR_YELLOW.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.YELLOW);
		assertEquals(MapColor.GOLD.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.GOLD);
		assertEquals(MapColor.QUARTZ.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.CREAM);
		assertEquals(MapColor.COLOR_LIGHT_GRAY.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.LIGHT_GREY);
		assertEquals(MapColor.COLOR_CYAN.getPackedId(MapColor.Brightness.HIGH), PixelCanvas.CYAN);
		assertEquals("navy", QslArt.colourName(QslArt.colour("navy")));
		assertArrayEquals(QslArt.front(QslCard.BLANK), QslArt.front(QslCard.fromTag(QslCard.BLANK.toTag())));
	}
}
