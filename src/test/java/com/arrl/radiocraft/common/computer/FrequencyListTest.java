package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The frequency manager's list (1.12 step 6): entries, their file, and turning them into commercial channels and back. */
class FrequencyListTest {

	@Test
	void entriesKnowSimplexSplitAndReceiveOnly() {
		FrequencyList.Entry simplex = FrequencyList.Entry.simplex("CALL", 146.52D, FrequencyList.Mode.FM);
		assertFalse(simplex.isSplit());
		FrequencyList.Entry repeater = new FrequencyList.Entry("CLUB RPT", 146.94D, 146.34D, FrequencyList.Mode.FM, 100.0F, "");
		assertTrue(repeater.isSplit());
		assertEquals(-0.6D, repeater.offsetMhz(), 1e-9);
		FrequencyList.Entry weather = new FrequencyList.Entry("WX1", 162.55D, 0.0D, FrequencyList.Mode.FM, 0.0F, "");
		assertTrue(weather.isReceiveOnly());
	}

	@Test
	void becomesACommercialChannelWithNoKey() {
		CommercialChannel channel = new FrequencyList.Entry("FIRE DISPATCH", 154.28D, 154.28D, FrequencyList.Mode.FM, 100.0F, "x").toChannel();
		assertNotNull(channel);
		assertEquals("FIRE DIS", channel.name(), "cut to the display's 8 characters");
		assertEquals(154_280_000.0F, channel.rxHz(), 1.0F);
		assertEquals(100.0F, channel.ctcssTone(), 0.01F);
		assertEquals(0, channel.keyId());
		CommercialChannel digital = new FrequencyList.Entry("TAC", 155.5D, 155.5D, FrequencyList.Mode.DIG, 100.0F, "").toChannel();
		assertNotNull(digital);
		assertTrue(digital.digital());
		assertEquals(0.0F, digital.ctcssTone(), "digital channels carry no tone");
		assertNull(FrequencyList.Entry.simplex("NET", 7.26D, FrequencyList.Mode.LSB).toChannel(), "commercial radios have no SSB");
		CommercialChannel receiveOnly = new FrequencyList.Entry("WX1", 162.55D, 0.0D, FrequencyList.Mode.FM, 0.0F, "").toChannel();
		assertNotNull(receiveOnly);
		assertTrue(receiveOnly.isReceiveOnly());
	}

	@Test
	void comesBackFromARadioWithoutItsKey() {
		CommercialChannel secret = new CommercialChannel("TAC 2", 155_500_000.0F, 155_500_000.0F, true, 0.0F, 321, true);
		FrequencyList.Entry entry = FrequencyList.Entry.of(secret);
		assertNotNull(entry);
		assertEquals(FrequencyList.Mode.DIG, entry.mode());
		assertEquals("encrypted", entry.notes());
		assertEquals(0, entry.toChannel().keyId(), "a frequency list holds no keys");
		assertNull(FrequencyList.Entry.of(CommercialChannel.BLANK));
	}

	@Test
	void movesSortsAndSaves() {
		FrequencyList list = new FrequencyList();
		list.add(FrequencyList.Entry.simplex("B", 147.0D, FrequencyList.Mode.FM));
		list.add(FrequencyList.Entry.simplex("A", 146.0D, FrequencyList.Mode.FM));
		list.add(FrequencyList.Entry.simplex("C", 7.2D, FrequencyList.Mode.LSB));
		assertEquals(0, list.move(1, -5));
		assertEquals("A", list.get(0).name());
		list.sortByFrequency();
		assertEquals("C", list.get(0).name());
		FrequencyList back = FrequencyList.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(list.toTag())));
		assertEquals(list.entries(), back.entries());
	}

	@Test
	void readsTypedFrequenciesAndModes() {
		assertEquals(146.52D, FrequencyList.parseMhz(" 146.52 "), 1e-9);
		assertEquals(0.0D, FrequencyList.parseMhz(""));
		assertEquals(-1.0D, FrequencyList.parseMhz("abc"));
		assertEquals(-1.0D, FrequencyList.parseMhz("-3"));
		assertEquals(FrequencyList.Mode.DIG, FrequencyList.Mode.parse("p25"));
		assertEquals(FrequencyList.Mode.USB, FrequencyList.Mode.parse("SSB"));
		assertNull(FrequencyList.Mode.parse("RTTY"));
	}
}
