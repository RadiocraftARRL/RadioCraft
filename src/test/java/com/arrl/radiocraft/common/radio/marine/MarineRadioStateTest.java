package com.arrl.radiocraft.common.radio.marine;

import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.MarineChannels.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The marine radio's controls: channel knob, 16/9 key, HI/LO, dual watch, and what each channel lets it do. */
class MarineRadioStateTest {

	private static final Region US = Region.US;

	@Test
	void freshRadioIsOnSixteenHighPower() {
		MarineRadioState state = MarineRadioState.DEFAULT;
		assertEquals("16", state.displayLine(US));
		assertEquals(156_800_000.0D, state.receiveHz(US), 1.0D);
		assertEquals(156_800_000.0D, state.transmitHz(US), 1.0D);
		assertTrue(state.mayTransmit(US));
		assertTrue(state.transmitsHigh(US));
		assertEquals(1.0F, state.basePower(US));
		assertEquals(0.0D, state.watchHz(US), "no dual watch on 16 itself");
	}

	@Test
	void sixteenNineKeyCyclesBackToTheWorkingChannel() {
		MarineRadioState working = MarineRadioState.DEFAULT.select(US, "68");
		MarineRadioState sixteen = working.sixteenNine(US);
		assertEquals("16", sixteen.channel());
		assertEquals("68", sixteen.previous());
		MarineRadioState nine = sixteen.sixteenNine(US);
		assertEquals("9", nine.channel());
		MarineRadioState back = nine.sixteenNine(US);
		assertEquals("68", back.channel());
		assertEquals("", back.previous());
	}

	@Test
	void sixteenNineFromNineGoesSixteenAndBack() {
		MarineRadioState nine = MarineRadioState.DEFAULT.select(US, "9");
		assertEquals("16", nine.sixteenNine(US).channel());
		assertEquals("9", nine.sixteenNine(US).sixteenNine(US).channel());
		assertEquals("16", nine.sixteenNine(US).sixteenNine(US).sixteenNine(US).channel());
	}

	@Test
	void knobWrapsAndForgetsTheSixteenNineCycle() {
		MarineRadioState sixteen = MarineRadioState.DEFAULT.select(US, "68").sixteenNine(US);
		MarineRadioState turned = sixteen.turn(US, 1);
		assertEquals("", turned.previous());
		MarineRadioState first = MarineRadioState.DEFAULT.select(US, MarineChannels.table(US).getFirst().name());
		assertEquals(MarineChannels.table(US).getLast().name(), first.turn(US, -1).channel(), "the knob wraps round");
	}

	@Test
	void unknownChannelIsRefusedAndAStaleOneFallsBackToSixteen() {
		MarineRadioState state = MarineRadioState.DEFAULT.select(US, "22A");
		assertSame(state, state.select(US, "99"));
		assertEquals("16", state.channel(Region.INTERNATIONAL).name(), "22A is not an international channel");
	}

	@Test
	void duplexChannelListensToTheCoastStation() {
		MarineRadioState state = MarineRadioState.DEFAULT.select(US, "26");
		assertEquals(157_300_000.0D, state.transmitHz(US), 1.0D);
		assertEquals(161_900_000.0D, state.receiveHz(US), 1.0D);
		assertTrue(state.displayStatus(US).contains("DUP"));
	}

	@Test
	void weatherAndDscChannelsDoNotKey() {
		MarineRadioState wx = MarineRadioState.DEFAULT.select(US, "WX1");
		assertFalse(wx.mayTransmit(US));
		assertEquals(162_550_000.0D, wx.receiveHz(US), 1.0D);
		assertFalse(MarineRadioState.DEFAULT.select(US, "70").mayTransmit(US));
	}

	@Test
	void lowPowerChannelsAndTheSwitch() {
		assertFalse(MarineRadioState.DEFAULT.select(US, "13").transmitsHigh(US), "13 is bridge to bridge, 1 W");
		MarineRadioState low = MarineRadioState.DEFAULT.togglePower();
		assertFalse(low.transmitsHigh(US));
		assertEquals(MarineRadioState.BASE_LOW_POWER, low.basePower(US));
		assertEquals(MarineRadioState.HANDHELD_LOW_POWER, low.handheldPower(US));
		assertTrue(low.togglePower().transmitsHigh(US));
	}

	@Test
	void dualWatchListensToSixteen() {
		MarineRadioState dw = MarineRadioState.DEFAULT.select(US, "68").toggleDualWatch();
		assertEquals(MarineRadioState.SIXTEEN_HZ, dw.watchHz(US), 1.0D);
		assertTrue(dw.displayStatus(US).contains("DW"));
		assertEquals(0.0D, dw.toggleDualWatch().watchHz(US));
	}

	@Test
	void priorityHoldsForAMoment() {
		MarineRadioState.PriorityWatch watch = new MarineRadioState.PriorityWatch();
		assertFalse(watch.prioritySpeaking(0L));
		watch.heardPriority(1_000L);
		assertTrue(watch.prioritySpeaking(1_000L + MarineRadioState.PRIORITY_HOLD_NANOS - 1));
		assertFalse(watch.prioritySpeaking(1_000L + MarineRadioState.PRIORITY_HOLD_NANOS));
		watch.heardPriority(5_000L);
		watch.reset();
		assertFalse(watch.prioritySpeaking(5_001L));
	}

	@Test
	void packetKeysPressTheRightThing() {
		MarineRadioState state = MarineRadioState.DEFAULT.select(US, "68");
		assertEquals("16", state.press(US, MarineRadioState.Key.SIXTEEN_NINE.ordinal(), 0).channel());
		assertFalse(state.press(US, MarineRadioState.Key.HIGH_LOW.ordinal(), 0).highPower());
		assertTrue(state.press(US, MarineRadioState.Key.DUAL_WATCH.ordinal(), 0).dualWatch());
		assertEquals("69", state.press(US, MarineRadioState.Key.TURN.ordinal(), 1).channel());
		assertSame(state, state.press(US, 99, 0), "an unknown key does nothing");
	}
}
