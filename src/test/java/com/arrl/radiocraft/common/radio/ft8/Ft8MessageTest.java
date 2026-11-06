package com.arrl.radiocraft.common.radio.ft8;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FT8's structured messages (computer batch 3B): encoding, parsing, and the decode list's collisions. */
class Ft8MessageTest {

	@Test
	void theStandardExchangeEncodes() {
		assertEquals("CQ W1BTR FN31", Ft8Message.cq("w1btr", "FN31").encode());
		assertEquals("W1BTR K1ABC JJ00", Ft8Message.reply("W1BTR", "K1ABC", "JJ00").encode());
		assertEquals("K1ABC W1BTR -12", Ft8Message.report("K1ABC", "W1BTR", -12).encode());
		assertEquals("K1ABC W1BTR +05", Ft8Message.report("K1ABC", "W1BTR", 5).encode());
		assertEquals("W1BTR K1ABC R-08", Ft8Message.rReport("W1BTR", "K1ABC", -8).encode());
		assertEquals("K1ABC W1BTR RR73", Ft8Message.rr73("K1ABC", "W1BTR").encode());
		assertEquals("W1BTR K1ABC 73", Ft8Message.signoff("W1BTR", "K1ABC").encode());
	}

	@Test
	void everyMessageParsesBack() {
		List<Ft8Message> all = List.of(Ft8Message.cq("W1BTR", "FN31"), Ft8Message.cq("W1BTR", ""), Ft8Message.reply("W1BTR", "K1ABC", "JJ00"),
				Ft8Message.report("K1ABC", "W1BTR", -12), Ft8Message.report("K1ABC", "W1BTR", 0), Ft8Message.rReport("W1BTR", "K1ABC", -8),
				Ft8Message.rReport("W1BTR", "K1ABC", 15), Ft8Message.rr73("K1ABC", "W1BTR"), Ft8Message.signoff("W1BTR", "K1ABC"),
				Ft8Message.cq("VE3/W1BTR", "AA00"));
		for(Ft8Message message : all)
			assertEquals(message, Ft8Message.parse(message.encode()), message.encode());
	}

	@Test
	void readsWhatOperatorsType() {
		assertEquals(Ft8Message.Type.RR73, Ft8Message.parse("k1abc w1btr RRR").type(), "RRR is taken as RR73");
		assertEquals(Ft8Message.Type.GRID, Ft8Message.parse("W1BTR K1ABC").type(), "a call with no grid");
		assertEquals(-24, Ft8Message.parse("K1ABC W1BTR -30").report(), "reports are clamped");
		assertNull(Ft8Message.parse("HELLO THERE"));
		assertNull(Ft8Message.parse("CQ"));
		assertNull(Ft8Message.parse("CQ W1BTR ZZ99"));
		assertNull(Ft8Message.parse("W1BTR K1ABC -1"), "one digit is not a report");
		assertNull(Ft8Message.parse("W1BTR K1ABC R"));
		assertNull(Ft8Message.parse(""));
		assertNull(Ft8Message.parse(null));
	}

	@Test
	void callsignsLoseTheirSsid() {
		assertEquals("W1BTR", Ft8Message.cleanCall("w1btr-9"));
		assertTrue(Ft8Message.reply("W1BTR-1", "K1ABC", "").isFor("W1BTR"));
		assertFalse(Ft8Message.cq("W1BTR", "").isFor("W1BTR"), "a CQ is for nobody in particular");
		assertEquals("W1BTR+05", "W1BTR" + Ft8Message.report(5));
	}

	@Test
	void overlappingSignalsCollide() {
		Ft8Decode strong = new Ft8Decode(4L, -5, 1000, "CQ W1AAA JJ00");
		Ft8Decode weak = new Ft8Decode(4L, -15, 1020, "CQ W1BBB JJ01");
		Ft8Decode alike = new Ft8Decode(4L, -6, 1030, "CQ W1CCC JJ02");
		Ft8Decode clear = new Ft8Decode(4L, -18, 2000, "CQ W1DDD JJ03");
		assertEquals(List.of(strong), Ft8Decode.resolve(List.of(strong, weak)), "the much stronger one captures");
		assertEquals(List.of(clear), Ft8Decode.resolve(List.of(strong, alike, clear)), "two about as strong are both lost");
		assertEquals(List.of(strong, clear), Ft8Decode.resolve(List.of(clear, strong)), "strongest first");
		Ft8Decode twice = new Ft8Decode(4L, -9, 1000, "CQ W1AAA JJ00");
		assertEquals(List.of(strong), Ft8Decode.resolve(List.of(twice, strong)), "one station heard by two radios counts once, at its best");
	}

	@Test
	void decodesKeepTheirLine() {
		Ft8Decode decode = new Ft8Decode(1L, -12, 1234, "CQ W1BTR FN31");
		assertEquals("000015  -12  1234  CQ W1BTR FN31", decode.line());
		assertEquals(decode, Ft8Decode.fromTag(decode.toTag()));
	}
}
