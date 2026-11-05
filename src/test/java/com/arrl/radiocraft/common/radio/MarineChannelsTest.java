package com.arrl.radiocraft.common.radio;

import com.arrl.radiocraft.common.radio.MarineChannels.Channel;
import com.arrl.radiocraft.common.radio.MarineChannels.Region;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The VHF marine channel table against the real one (ITU Appendix 18 and the US channel plan). */
class MarineChannelsTest {

	@Test
	void callingChannelsAreWhereEveryBoaterExpects() {
		for(Region region : Region.values()) {
			Channel sixteen = MarineChannels.byName(region, MarineChannels.DISTRESS);
			assertNotNull(sixteen);
			assertEquals(156_800_000.0D, sixteen.transmitHz(), 1.0D);
			assertFalse(sixteen.isDuplex());
			Channel nine = MarineChannels.byName(region, MarineChannels.HAILING);
			assertNotNull(nine);
			assertEquals(156_450_000.0D, nine.transmitHz(), 1.0D);
		}
	}

	@Test
	void duplexChannelsAreFourPointSixMegahertzApart() {
		Channel twentyEight = MarineChannels.byName(Region.INTERNATIONAL, "28");
		assertNotNull(twentyEight);
		assertEquals(157_400_000.0D, twentyEight.transmitHz(), 1.0D);
		assertEquals(162_000_000.0D, twentyEight.receiveHz(), 1.0D);
		Channel eightyEight = MarineChannels.byName(Region.INTERNATIONAL, "88");
		assertNotNull(eightyEight);
		assertEquals(162_025_000.0D, eightyEight.coastTransmitHz(), 1.0D, "the top of the marine band");
		assertTrue(MarineChannels.byName(Region.US, "26").isDuplex(), "US public correspondence stays duplex");
	}

	@Test
	void usChannelsUseTheShipFrequencySimplex() {
		Channel coastGuard = MarineChannels.byName(Region.US, "22a");
		assertNotNull(coastGuard);
		assertEquals(157_100_000.0D, coastGuard.transmitHz(), 1.0D);
		assertFalse(coastGuard.isDuplex());
		assertNull(MarineChannels.byName(Region.US, "22"), "in the US it is 22A");
		assertNull(MarineChannels.byName(Region.US, "60"), "60 is not used in the US");
		Channel weather = MarineChannels.byName(Region.US, "WX1");
		assertNotNull(weather);
		assertTrue(weather.isReceiveOnly());
		assertEquals(162_550_000.0D, weather.receiveHz(), 1.0D);
		assertTrue(MarineChannels.byName(Region.US, "13").lowPowerOnly(), "13 is bridge to bridge, 1 W");
		assertFalse(MarineChannels.byName(Region.US, "70").voice(), "70 is DSC");
	}

	@Test
	void everyChannelIsInsideCommercialVhfAndNamedOnce() {
		for(Region region : Region.values()) {
			Set<String> names = new HashSet<>();
			for(Channel channel : MarineChannels.table(region)) {
				assertTrue(names.add(channel.name()), "two channels called " + channel.name());
				if(!channel.isReceiveOnly())
					assertTrue(channel.transmitHz() >= 156_025_000.0D && channel.transmitHz() <= 157_425_000.0D, channel.name());
				assertTrue(channel.receiveHz() >= 156_025_000.0D && channel.receiveHz() <= 162_550_000.0D, channel.name());
			}
		}
		assertEquals(57, MarineChannels.table(Region.INTERNATIONAL).size(), "1-28 and 60-88");
	}

	@Test
	void theKnobWrapsRound() {
		Channel first = MarineChannels.table(Region.US).getFirst();
		Channel last = MarineChannels.table(Region.US).getLast();
		assertEquals(last.name(), MarineChannels.step(Region.US, first.name(), -1).name());
		assertEquals("17", MarineChannels.step(Region.US, "16", 1).name());
		assertEquals(MarineChannels.DISTRESS, MarineChannels.step(Region.US, "nonsense", 3).name());
		assertEquals("16", MarineChannels.channelAt(Region.US, 156_800_010.0D, 100.0D).name());
	}
}
